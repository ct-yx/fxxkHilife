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
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 待响应映射: responseId (hex) -> CompletableDeferred
    private val pendingResponses = mutableMapOf<String, CompletableDeferred<HuaweiSppPackage>>()
    private val pendingMutex = Mutex()

    // 已注册的 Handler
    private val handlers = mutableListOf<HuaweiDeviceHandler>()
    val failedHandlerIds = mutableSetOf<String>()
    private val packageHandlers = mutableMapOf<String, HuaweiDeviceHandler?>()
    private val propertyHandlers = mutableMapOf<String, HuaweiDeviceHandler>()
    private val store = mutableMapOf<String, MutableMap<String, String>>()
    private val storeMutex = Mutex()

    fun registerHandler(handler: HuaweiDeviceHandler) {
        handlers.add(handler)
        for (cmd in handler.commandIds) {
            packageHandlers[cmd.toHex()] = handler
        }
        for (cmd in handler.ignoreCommandIds) {
            packageHandlers[cmd.toHex()] = null
        }
        for ((group, prop) in handler.properties) {
            propertyHandlers["$group//$prop"] = handler
        }
    }

    fun getHandlerById(id: String): HuaweiDeviceHandler? = handlers.find { it.id == id }

    suspend fun putProperty(group: String, prop: String?, value: String?, extendGroup: Boolean = false) {
        storeMutex.withLock {
            if (prop == null) {
                val incoming = parseObject(value)
                store[group] = if (extendGroup) {
                    (store[group] ?: mutableMapOf()).apply { putAll(incoming) }
                } else {
                    incoming.toMutableMap()
                }
            } else {
                val target = store.getOrPut(group) { mutableMapOf() }
                if (value == null) target.remove(prop) else target[prop] = value
            }
        }
        LogBuffer.i("Prop", if (prop == null) "$group=*" else "$group.$prop=${value ?: "null"}")
    }

    suspend fun getProperty(group: String? = null, prop: String? = null, fallback: String? = null): String? {
        return storeMutex.withLock {
            when {
                group == null -> store.entries.joinToString("\n") { (g, props) ->
                    props.entries.joinToString("\n") { (p, v) -> "$g.$p=$v" }
                }
                prop == null -> store[group]?.entries?.joinToString("\n") { (p, v) -> "$group.$p=$v" } ?: fallback
                else -> store[group]?.get(prop) ?: fallback
            }
        }
    }

    suspend fun setProperty(group: String, prop: String, value: String) {
        val handler = propertyHandlers["$group//$prop"] ?: propertyHandlers["$group//"]
        if (handler == null) {
            LogBuffer.w("Prop", "No handler for $group.$prop")
            return
        }
        handler.setProperty(this, group, prop, value)
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
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
            scope.cancel()
            false
        }
    }

    /** 初始化所有 Handler（交错发射：每个间隔 80ms，每个 Handler 最多 3 次重试 × 1.5s 超时，全局超时 10s） */
    private suspend fun initHandlers() {
        try {
            withTimeout(10000) {
                LogBuffer.i("SPP", "Starting staggered init for ${handlers.size} handlers (gap=80ms, perHandler=1.5s×3, timeout=10s)")
                coroutineScope {
                    handlers.mapIndexed { index, handler ->
                        launch {
                            if (index > 0) delay(index * 80L)
                            var success = false
                            for (attempt in 0 until 3) {
                                try {
                                    withTimeout(1500) {
                                        handler.onInit(this@SppDriver)
                                    }
                                    success = true
                                    LogBuffer.i("SPP", "Init ${handler.id} success (attempt=${attempt + 1})")
                                    break
                                } catch (e: TimeoutCancellationException) {
                                    LogBuffer.w("SPP", "Init ${handler.id} timeout (attempt=${attempt + 1})")
                                } catch (e: Exception) {
                                    LogBuffer.w("SPP", "Init ${handler.id} failed (attempt=${attempt + 1}): ${e.message}")
                                }
                            }
                            if (!success) {
                                LogBuffer.w("SPP", "Can't initialize ${handler.id}. Skipping.")
                                failedHandlerIds.add(handler.id)
                            }
                        }
                    }.joinAll()
                }
                LogBuffer.i("SPP", if (failedHandlerIds.isEmpty()) "All handlers initialized" 
                else "Staggered init completed, ${failedHandlerIds.size} failed: $failedHandlerIds")
            }
        } catch (e: TimeoutCancellationException) {
            LogBuffer.w("SPP", "Staggered init global timeout reached, proceeding with partial results")
        }
    }

    /** 发送包并等响应（对照 send_package） */
    suspend fun sendPackage(pkg: HuaweiSppPackage, timeout: Long = 1500): HuaweiSppPackage? {
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
            LogBuffer.w("SPP", "Timeout waiting for response to cmd=${pkg.commandId.toHex()} (respId=$respId, pending=[${pendingResponses.keys.joinToString(",")}])")
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

                // 对照上游 __recv_pacakge: length 使用 heading[2]，读取 heading 后面的 length 字节。
                val length = head[2].toInt() and 0xFF
                if (length < 4) {
                    readFully(input, ByteArray(length))
                    continue
                }

                val bodyLen = length
                val body = ByteArray(bodyLen)
                readFully(input, body)

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
        // recv loop 退出，标记断开
        isConnected = false
        LogBuffer.i("SPP", "Recv loop ended")
    }

    /** 处理收到的包（对照 _handle_raw_pkg） */
    private suspend fun handlePackage(data: ByteArray) {
        val pkg = HuaweiSppPackage.fromBytes(data) ?: return
        val cmdKey = pkg.commandId.toHex()
        val paramsKeys = pkg.parameters.keys.joinToString(",") { it.toString() }
        LogBuffer.d("SPP", "RX: ${data.toHex()} | cmd=$cmdKey resp=${pkg.responseId.toHex()} params=[$paramsKeys]")

        pendingMutex.withLock {
            val deferred = pendingResponses[cmdKey]
            if (deferred != null && !deferred.isCompleted) {
                LogBuffer.d("SPP", "RX → pendingResponses consumed cmd=$cmdKey")
                deferred.complete(pkg)
                return
            }
        }

        if (packageHandlers.containsKey(cmdKey)) {
            val handler = packageHandlers[cmdKey]
            if (handler != null) {
                LogBuffer.d("SPP", "RX → onDriverPackage handler=${handler.id} cmd=$cmdKey")
                handler.onDriverPackage(this, pkg)
            }
        } else {
            LogBuffer.d("SPP", "No handler for cmd=$cmdKey (pending=${pendingResponses.keys.joinToString(",")})")
        }
    }

    fun disconnect() {
        job?.cancel()
        closeSocket()
        isConnected = false
        LogBuffer.i("SPP", "Disconnected")
    }

    private fun closeSocket() {
        try {
            inputStream?.close()
        } catch (e: Exception) {
            LogBuffer.w("SPP", "Input close failed: ${e.message}")
        }
        try {
            outputStream?.close()
        } catch (e: Exception) {
            LogBuffer.w("SPP", "Output close failed: ${e.message}")
        }
        try {
            closeMethod?.invoke(socket)
        } catch (e: Exception) {
            LogBuffer.w("SPP", "Socket close failed: ${e.message}")
        }
        socket = null
        inputStream = null
        outputStream = null
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var off = 0
        while (off < buffer.size) {
            val n = input.read(buffer, off, buffer.size - off)
            if (n == -1) throw java.io.EOFException("Stream closed")
            off += n
        }
    }

    private fun parseObject(value: String?): Map<String, String> {
        if (value.isNullOrBlank()) return emptyMap()
        return value.lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
            }
            .toMap()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
