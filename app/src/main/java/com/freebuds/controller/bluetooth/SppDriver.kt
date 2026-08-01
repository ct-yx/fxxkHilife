package com.freebuds.controller.bluetooth

import android.bluetooth.BluetoothDevice
import android.os.SystemClock
import com.freebuds.controller.adapter.huawei.protocol.HuaweiHandlerInitializer
import com.freebuds.controller.adapter.huawei.protocol.HuaweiHandlerInitializationPolicy
import com.freebuds.controller.adapter.huawei.protocol.HuaweiHandlerRegistry
import com.freebuds.controller.adapter.huawei.protocol.HuaweiPendingResponseManager
import com.freebuds.controller.adapter.huawei.protocol.HuaweiPropertyStore
import com.freebuds.controller.core.transport.RfcommSocketBridge
import com.freebuds.controller.protocol.HuaweiSppPackage
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method

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

    private val pendingResponses = HuaweiPendingResponseManager()
    // The device accepts only one request/response exchange reliably. txMutex protects bytes
    // from interleaving; this mutex additionally keeps every later command (including a
    // fire-and-forget write) from overtaking a reply.
    private val requestMutex = Mutex()
    private val txMutex = Mutex()

    private val handlerRegistry = HuaweiHandlerRegistry()
    private val handlerInitializer = HuaweiHandlerInitializer(handlerRegistry)
    private val propertyStore = HuaweiPropertyStore()
    val failedHandlerIds: MutableSet<String> get() = handlerRegistry.failedHandlerIds

    /** Called whenever a handler updates the property store. */
    var onPropertyChanged: (() -> Unit)? = null

    /** Called when RFCOMM receive loop ends unexpectedly. */
    var onDisconnected: (() -> Unit)? = null

    /** Diagnostic hook used by BT-0.2 to bind discovery timing to a connection attempt. */
    var onDiscoveryChecked: ((wasDiscovering: Boolean) -> Unit)? = null

    fun registerHandler(handler: HuaweiDeviceHandler) {
        handlerRegistry.register(handler)
    }

    fun getHandlerById(id: String): HuaweiDeviceHandler? = handlerRegistry.findById(id)

    val handlerIds: List<String>
        get() = handlerRegistry.allHandlers().map { it.id }

    suspend fun putProperty(group: String, prop: String?, value: String?, extendGroup: Boolean = false) {
        propertyStore.put(group, prop, value, extendGroup)
        LogBuffer.i("Prop", if (prop == null) "$group=*" else "$group.$prop=${value ?: "null"}")
        onPropertyChanged?.invoke()
    }

    suspend fun getProperty(group: String? = null, prop: String? = null, fallback: String? = null): String? =
        propertyStore.get(group, prop, fallback)

    suspend fun setProperty(group: String, prop: String, value: String) {
        val handler = handlerRegistry.handlerForProperty(group, prop)
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
            scope.cancel()
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val connectedSocket = RfcommSocketBridge.connect(
                device = device,
                port = SPP_SERVICE_PORT,
                logTag = "SPP",
                onDiscoveryChecked = onDiscoveryChecked,
            )
            socket = connectedSocket.socket
            closeMethod = connectedSocket.closeMethod
            inputStream = connectedSocket.inputStream
            outputStream = connectedSocket.outputStream

            isConnected = true
            LogBuffer.i("SPP", "Connected to ${device.name}")

            // 启动接收循环（对照 _loop_recv）
            job = scope.launch { recvLoop() }

            true
        } catch (e: Exception) {
            LogBuffer.e("SPP", "Connection failed: ${e.message}")
            closeSocket()
            scope.cancel()
            false
        }
    }

    // Handler initialization is delegated to HuaweiHandlerInitializer and is intentionally
    // kept outside connect(), so UI/control state can become connected as soon as RFCOMM is ready.
    suspend fun initializeCoreHandlers(
        timeoutMs: Long = HuaweiHandlerInitializationPolicy.CORE_HANDLER_TIMEOUT_MS,
        maxAttempts: Int = 1,
    ) {
        handlerInitializer.initializeCore(this, device.name, timeoutMs, maxAttempts)
    }

    suspend fun initializeDeferredHandlers() {
        handlerInitializer.initializeDeferred(this, device.name)
    }

    /** 发送包并等响应（对照 send_package） */
    suspend fun sendPackage(
        pkg: HuaweiSppPackage,
        timeout: Long = 5_000,
        responsePredicate: (HuaweiSppPackage) -> Boolean = { true },
    ): HuaweiSppPackage? {
        val queuedAt = SystemClock.elapsedRealtime()
        return requestMutex.withLock {
            val queueWaitMs = SystemClock.elapsedRealtime() - queuedAt
            val respId = pkg.responseId.toHex()
            if (respId.isEmpty()) {
                sendNowaitLocked(pkg)
                return@withLock null
            }

            val startedAt = SystemClock.elapsedRealtime()
            val deferred = pendingResponses.register(respId, timeout, responsePredicate)
            val slotWaitMs = SystemClock.elapsedRealtime() - startedAt
            if (deferred == null) {
                LogBuffer.w(
                    "SPP",
                    "REQ slot timeout cmd=${pkg.commandId.toHex()} resp=$respId timeout=${timeout}ms"
                )
                return@withLock null
            }
            LogBuffer.d(
                "SPP",
                "REQ start cmd=${pkg.commandId.toHex()} resp=$respId timeout=${timeout}ms " +
                    "queueWait=${queueWaitMs}ms slotWait=${slotWaitMs}ms"
            )

            try {
                sendNowaitLocked(pkg)
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val remaining = (timeout - elapsed).coerceAtLeast(1L)
                val response = withTimeoutOrNull(remaining) { deferred.await() }
                if (response != null) {
                    LogBuffer.d(
                        "SPP",
                        "REQ success cmd=${pkg.commandId.toHex()} resp=$respId elapsed=${SystemClock.elapsedRealtime() - startedAt}ms"
                    )
                    return@withLock response
                }
                val pendingKeys = pendingResponses.keys().joinToString(",")
                LogBuffer.w(
                    "SPP",
                    "REQ response timeout cmd=${pkg.commandId.toHex()} resp=$respId elapsed=${SystemClock.elapsedRealtime() - startedAt}ms pending=[$pendingKeys]"
                )
                return@withLock null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogBuffer.w("SPP", "Failed waiting for response to cmd=${pkg.commandId.toHex()} (respId=$respId): ${e.message}")
                return@withLock null
            } finally {
                pendingResponses.remove(respId, deferred)
            }
        }
    }

    /**
     * Sends a packet without waiting for its own response.
     *
     * It still waits for an in-flight request/response exchange to complete. Otherwise a
     * fire-and-forget write could overtake an initialization read and reproduce the exact
     * request-loss race that the serialized request lane prevents.
     */
    suspend fun sendNowait(pkg: HuaweiSppPackage) {
        requestMutex.withLock {
            sendNowaitLocked(pkg)
        }
    }

    /** Caller must hold [requestMutex] when a response exchange is in progress. */
    private suspend fun sendNowaitLocked(pkg: HuaweiSppPackage) = withContext(Dispatchers.IO) {
        val bytes = pkg.toBytes()
        LogBuffer.frame("TX", bytes)
        try {
            txMutex.withLock {
                outputStream?.write(bytes)
                outputStream?.flush()
            }
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
                LogBuffer.frame("RX", pkgBytes)
                handlePackage(pkgBytes)
                handleEmbeddedPackages(pkgBytes)
            }
            } catch (e: java.io.EOFException) {
            LogBuffer.i("SPP", "Recv loop: connection closed")
        } catch (e: Exception) {
            if (e !is CancellationException) {
                LogBuffer.e("SPP", "Recv loop error: ${e.message}")
            }
        }
        // recv loop 退出，标记断开并通知上层刷新连接状态
        val wasConnected = isConnected
        isConnected = false
        LogBuffer.i("SPP", "Recv loop ended")
        if (wasConnected) onDisconnected?.invoke()
    }

    private suspend fun handleEmbeddedPackages(data: ByteArray) {
        var pos = 4
        while (pos + 4 <= data.size) {
            if (data[pos] == 0x5A.toByte() && data[pos + 1] == 0x00.toByte()) {
                val len = data[pos + 2].toInt() and 0xFF
                val end = pos + 4 + len
                if (len >= 4 && end <= data.size) {
                    val child = data.copyOfRange(pos, end)
                    LogBuffer.frame("RX embedded", child)
                    handlePackage(child)
                    pos = end
                    continue
                }
            }
            pos++
        }
    }

    /** 处理收到的包（对照 _handle_raw_pkg） */
    private suspend fun handlePackage(data: ByteArray) {
        val pkg = HuaweiSppPackage.fromBytes(data) ?: return
        val cmdKey = pkg.commandId.toHex()
        val paramsKeys = pkg.parameters.keys.joinToString(",") { it.toString() }
        LogBuffer.d("SPP", "RX packet cmd=$cmdKey params=[$paramsKeys] bytes=${data.size}")

        if (pendingResponses.complete(cmdKey, pkg)) {
            LogBuffer.d("SPP", "RX → pendingResponses consumed cmd=$cmdKey")
            return
        }

        if (handlerRegistry.hasCommand(cmdKey)) {
            val handler = handlerRegistry.handlerForCommand(cmdKey)
            if (handler != null) {
                LogBuffer.d("SPP", "RX → onDriverPackage handler=${handler.id} cmd=$cmdKey")
                handler.onDriverPackage(this, pkg)
            }
        } else {
            LogBuffer.d("SPP", "No handler for cmd=$cmdKey (pending=${pendingResponses.keys().joinToString(",")})")
        }
    }

    fun disconnect() {
        job?.cancel()
        pendingResponses.cancelAll()
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

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
