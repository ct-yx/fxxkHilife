package com.freebuds.controller.bluetooth

import android.bluetooth.BluetoothDevice
import com.freebuds.controller.protocol.HuaweiSppPackage
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method
import kotlin.coroutines.resume

/**
 * RFCOMM SPP 蓝牙驱动
 * 严格对照 OpenFreebuds OfbDriverSppGeneric + OfbDriverHuaweiGeneric
 *
 * 通过 Android RFCOMM Socket 连接耳机，读写 5A 封包。
 * SPP 端口号由型号配置决定（FreeBuds 6i 使用 port=1）。
 */
class SppDriver(private val device: BluetoothDevice) {

    companion object {
        /** SPP 端口号，对照 OpenFreebuds 型号配置的 _spp_service_port */
        const val SPP_SERVICE_PORT = 1
    }

    var isConnected: Boolean = false
        private set

    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var socket: Any? = null  // BluetoothSocket
    private var closeMethod: Method? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 待响应映射: responseId (hex) -> CompletableDeferred
    private val pendingResponses = mutableMapOf<String, CompletableDeferred<HuaweiSppPackage>>()
    private val pendingMutex = Mutex()

    // 已注册的 Handler
    private val handlers = mutableListOf<HuaweiDeviceHandler>()
    private val packageHandlers = mutableMapOf<String, HuaweiDeviceHandler>()

    fun registerHandler(handler: HuaweiDeviceHandler) {
        handlers.add(handler)
        for (cmd in handler.commandIds) {
            packageHandlers[cmd.toHex()] = handler
        }
    }

    /** 发起 RFCOMM 连接（对照 OfbDriverSppGeneric.start） */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (isConnected) {
            LogBuffer.i("SPP", "Already connected")
            return@withContext true
        }

        LogBuffer.i("SPP", "Connecting to ${device.name} (${device.address}) via RFCOMM port=$SPP_SERVICE_PORT...")
        try {
            // 使用端口号连接（对照 OpenFreebuds _spp_service_port）
            val sockClass = Class.forName("android.bluetooth.BluetoothSocket")
            val createMethod = device.javaClass.getMethod(
                "createRfcommSocket", Int::class.javaPrimitiveType
            )
            socket = createMethod.invoke(device, SPP_SERVICE_PORT)

            val connectMethod = sockClass.getMethod("connect")
            closeMethod = sockClass.getMethod("close")
            val getInputStream = sockClass.getMethod("getInputStream")
            val getOutputStream = sockClass.getMethod("getOutputStream")

            connectMethod.invoke(socket)
            inputStream = getInputStream.invoke(socket) as InputStream
            outputStream = getOutputStream.invoke(socket) as OutputStream

            isConnected = true
            LogBuffer.i("SPP", "Connected to ${device.name}")

            // 启动接收循环（对照 _loop_recv）
            job = scope.launch { recvLoop() }

            // 初始化 Handler（对照 _start_all_handlers）
            initHandlers()

            true
        } catch (e: Exception) {
            LogBuffer.e("SPP", "Connection failed: ${e.message}")
            closeSocket()
            false
        }
    }

    /** 初始化所有 Handler（对照 OfbDriverHuaweiGeneric._start_all_handlers） */
    private suspend fun initHandlers() {
        for (handler in handlers) {
            try {
                withTimeout(5000) {
                    handler.onInit(this@SppDriver)
                }
                LogBuffer.i("SPP", "Init ${handler.id} success")
            } catch (e: Exception) {
                LogBuffer.w("SPP", "Init ${handler.id} failed: ${e.message}")
            }
        }
    }

    /** 发送包并等响应（对照 send_package） */
    suspend fun sendPackage(pkg: HuaweiSppPackage, timeout: Long = 5000): HuaweiSppPackage? {
        val respId = pkg.responseId.toHex()
        if (respId.isEmpty()) {
            sendNowait(pkg)
            return null
        }

        val deferred = CompletableDeferred<HuaweiSppPackage>()
        pendingMutex.withLock {
            pendingResponses[respId]?.cancel()
            pendingResponses[respId] = deferred
        }

        try {
            sendNowait(pkg)
            return withTimeout(timeout) { deferred.await() }
        } catch (e: Exception) {
            LogBuffer.w("SPP", "Timeout waiting for response to cmd=${pkg.commandId.toHex()}")
            return null
        } finally {
            pendingMutex.withLock { pendingResponses.remove(respId) }
        }
    }

    /** 发送包不等待（对照 _send_nowait） */
    suspend fun sendNowait(pkg: HuaweiSppPackage) = withContext(Dispatchers.IO) {
        val bytes = pkg.toBytes()
        LogBuffer.d("SPP", "TX: ${bytes.toHex()}")
        try {
            outputStream?.write(bytes)
            outputStream?.flush()
        } catch (e: Exception) {
            LogBuffer.e("SPP", "TX failed: ${e.message}")
            throw e
        }
    }

    /** 接收循环：批量读取，无效包头直接丢弃重同步 */
    private suspend fun recvLoop() {
        try {
            val input = inputStream ?: return
            while (currentCoroutineContext().isActive) {
                // 批量读取 4 字节头部
                val head = ByteArray(4)
                var off = 0
                while (off < 4) {
                    val n = input.read(head, off, 4 - off)
                    if (n == -1) throw java.io.EOFException("Stream closed")
                    off += n
                }

                // 魔数校验: 5a 00
                if (head[0] != 0x5A.toByte() || head[1] != 0x00.toByte()) {
                    continue
                }

                // 解析长度（双字节大端: head[1:3]）
                val length = ((head[1].toInt() and 0xFF) shl 8) or (head[2].toInt() and 0xFF)
                if (length < 4) {
                    val discard = ByteArray(length)
                    var dOff = 0
                    while (dOff < length) {
                        val n = input.read(discard, dOff, length - dOff)
                        if (n == -1) throw java.io.EOFException("Stream closed")
                        dOff += n
                    }
                    continue
                }

                // 读取包体（含CRC）
                val bodyLen = length + 2
                val body = ByteArray(bodyLen)
                off = 0
                while (off < bodyLen) {
                    val n = input.read(body, off, bodyLen - off)
                    if (n == -1) throw java.io.EOFException("Stream closed")
                    off += n
                }

                val pkgBytes = head + body
                LogBuffer.d("SPP", "RX: ${pkgBytes.toHex()}")
                handlePackage(pkgBytes)
            }
        } catch (e: java.io.EOFException) {
            LogBuffer.i("SPP", "Recv loop: connection closed")
        } catch (e: Exception) {
            if (e !is CancellationException) {
                LogBuffer.e("SPP", "Recv loop error: ${e.message}")
            }
        }
        LogBuffer.i("SPP", "Recv loop ended")
    }

    /** 处理收到的包（对照 _handle_raw_pkg） */
    private suspend fun handlePackage(data: ByteArray) {
        val pkg = HuaweiSppPackage.fromBytes(data) ?: return
        val cmdKey = pkg.commandId.toHex()
        LogBuffer.d("SPP", "RX: ${data.toHex()}")

        pendingMutex.withLock {
            val deferred = pendingResponses[cmdKey]
            if (deferred != null && !deferred.isCompleted) {
                deferred.complete(pkg)
                return
            }
        }

        val handler = packageHandlers[cmdKey]
        if (handler != null) {
            handler.onPackage(pkg)
        } else {
            LogBuffer.d("SPP", "No handler for cmd=${pkg.commandId.toHex()}")
        }
    }

    fun disconnect() {
        job?.cancel()
        scope.cancel()
        closeSocket()
        isConnected = false
        LogBuffer.i("SPP", "Disconnected")
    }

    private fun closeSocket() {
        try {
            inputStream?.close()
        } catch (_: Exception) {}
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        try {
            closeMethod?.invoke(socket)
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
