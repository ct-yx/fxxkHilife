package com.freebuds.controller.bluetooth

import android.bluetooth.BluetoothDevice
import com.freebuds.controller.protocol.HuaweiSppPackage
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * SPP 蓝牙驱动核心
 * 对照 OpenFreebuds: OfbDriverHuaweiGeneric + spp.py
 *
 * 职责: 蓝牙连接、包收发、响应等待、包分发
 */
class SppDriver(private val device: BluetoothDevice, private val sppPort: Int = 1) {

    companion object {
        private const val SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb"
    }

    var isConnected: Boolean = false
        private set

    private var job: Job? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 待响应映射: responseId -> CompletableDeferred
    private val pendingResponses = mutableMapOf<String, CompletableDeferred<HuaweiSppPackage>>()
    private val pendingMutex = Mutex()

    // 已注册的 Handler
    private val handlers = mutableListOf<HuaweiDeviceHandler>()
    // 按 commandId hex 映射处理器（用于收到包时快速分发）
    private val packageHandlers = mutableMapOf<String, HuaweiDeviceHandler>()

    fun registerHandler(handler: HuaweiDeviceHandler) {
        handlers.add(handler)
        for (cmd in handler.commandIds) {
            val key = cmd.toHex()
            packageHandlers[key] = handler
        }
    }

    /** 发起蓝牙 RFCOMM 连接 */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 如果设备已系统连接，直接复用，不再创建新 RFCOMM 连接
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && adapter.getConnectionState(device) == android.bluetooth.BluetoothAdapter.STATE_CONNECTED) {
                LogBuffer.i("SPP", "${device.name} already connected via system BT, skipping RFCOMM")
                // 尝试已有 RFCOMM 通道：用 createRfcommSocket… 会失败，改用 createInsecureRfcommSocketToServiceRecord
                val socket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID))
                try {
                    socket.connect()
                } catch (_: Exception) {
                    // 如果还是连不上，尝试一种备选方案：createRfcommSocket 并传入 SPP UUID
                    try { socket.close() } catch (_: Exception) {}
                    return@withContext tryFallbackConnect()
                }
                inputStream = socket.inputStream
                outputStream = socket.outputStream
                isConnected = true
                job = scope.launch { recvLoop() }
                initHandlers()
                return@withContext true
            }

            LogBuffer.i("SPP", "Connecting to ${device.name} (${device.address}) on port $sppPort...")
            val socket = device.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID))
            socket.connect()
            inputStream = socket.inputStream
            outputStream = socket.outputStream
            isConnected = true
            LogBuffer.i("SPP", "Connected")

            job = scope.launch { recvLoop() }
            initHandlers()

            true
        } catch (e: Exception) {
            LogBuffer.e("SPP", "Connection failed: ${e.message}")
            isConnected = false
            false
        }
    }

    /** 备选连接方案：尝试非加密 RFCOMM */
    private suspend fun tryFallbackConnect(): Boolean {
        try {
            val socket = device.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID))
            socket.connect()
            inputStream = socket.inputStream
            outputStream = socket.outputStream
            isConnected = true
            job = scope.launch { recvLoop() }
            initHandlers()
            return true
        } catch (e: Exception) {
            LogBuffer.e("SPP", "Fallback connect also failed: ${e.message}")
            isConnected = false
            return false
        }
    }

    /** 初始化所有已注册的 Handler（对照 OpenFreebuds _start_all_handlers） */
    private suspend fun initHandlers() {
        for (handler in handlers) {
            try {
                withTimeout(5000) {
                    handler.onInit(this@SppDriver)
                }
                LogBuffer.i("SppDriver", "Init ${handler.id} success")
            } catch (e: Exception) {
                LogBuffer.w("SppDriver", "Init ${handler.id} failed: ${e.message}")
            }
        }
    }

    /** 发送包并等响应（对照 OpenFreebuds send_package） */
    suspend fun sendPackage(pkg: HuaweiSppPackage, timeout: Long = 5000): HuaweiSppPackage? {
        val respId = pkg.responseId.toHex()
        if (respId.isEmpty()) {
            sendNowait(pkg)
            return null
        }

        // 注册等待
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

    /** 发送包不等待（对照 OpenFreebuds _send_nowait） */
    suspend fun sendNowait(pkg: HuaweiSppPackage) = withContext(Dispatchers.IO) {
        val bytes = pkg.toBytes()
        LogBuffer.d("SPP", "TX: cmd=${pkg.commandId.toHex()}")
        try {
            outputStream?.write(bytes)
            outputStream?.flush()
        } catch (e: Exception) {
            LogBuffer.e("SPP", "Send failed: ${e.message}")
            throw e
        }
    }

    /** 接收循环（对照 OpenFreebuds __recv_package） */
    private suspend fun recvLoop() {
        try {
            val buffer = mutableListOf<Byte>()
            while (currentCoroutineContext().isActive) {
                val input = inputStream ?: break
                val byte = input.read()
                if (byte == -1) break

                buffer.add(byte.toByte())

                // 尝试解析包：最短 7 字节（5A+len+len+00+cmd+cmd+crc+crc）
                while (buffer.size >= 7) {
                    if (buffer[0] != 0x5A.toByte()) {
                        buffer.removeAt(0)
                        continue
                    }
                    val len = ((buffer[1].toInt() and 0xFF) shl 8) or (buffer[2].toInt() and 0xFF)
                    val pktSize = len + 4 // 5A + len(2) + 00 + body + crc(2)
                    if (buffer.size < pktSize) break

                    val pktBytes = buffer.take(pktSize).toByteArray()
                    buffer.removeAll(buffer.take(pktSize))

                    handlePackage(pktBytes)
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                LogBuffer.e("SPP", "Recv loop error: ${e.message}")
            }
        } finally {
            isConnected = false
            LogBuffer.i("SPP", "Disconnected")
        }
    }

    /** 处理收到的包（对照 OpenFreebuds _handle_raw_pkg） */
    private suspend fun handlePackage(data: ByteArray) {
        val pkg = HuaweiSppPackage.fromBytes(data) ?: return
        val cmdKey = pkg.commandId.toHex()
        LogBuffer.d("SPP", "RX: $pkg")

        // 先检查是否有等待响应的
        pendingMutex.withLock {
            val deferred = pendingResponses[cmdKey]
            if (deferred != null && !deferred.isCompleted) {
                deferred.complete(pkg)
                return
            }
        }

        // 分发给 Handler
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
        try { inputStream?.close() } catch (_: Exception) {}
        try { outputStream?.close() } catch (_: Exception) {}
        isConnected = false
        LogBuffer.i("SPP", "Disconnected")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
