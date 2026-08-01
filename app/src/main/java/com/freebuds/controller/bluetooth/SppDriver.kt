package com.freebuds.controller.bluetooth

import android.bluetooth.BluetoothDevice
import android.os.SystemClock
import com.freebuds.controller.adapter.huawei.protocol.HuaweiHandlerInitializer
import com.freebuds.controller.adapter.huawei.protocol.HuaweiHandlerInitializationPolicy
import com.freebuds.controller.adapter.huawei.protocol.HuaweiHandlerRegistry
import com.freebuds.controller.adapter.huawei.protocol.HuaweiPendingResponseManager
import com.freebuds.controller.adapter.huawei.protocol.HuaweiPropertyStore
import com.freebuds.controller.adapter.huawei.protocol.HuaweiSppProtocol
import com.freebuds.controller.core.transport.RfcommTransportConfig
import com.freebuds.controller.core.transport.RfcommSppTransport
import com.freebuds.controller.protocol.HuaweiSppPackage
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Huawei command/session driver above the generic RFCOMM SPP transport.
 * The socket lifecycle and raw stream reads live in [RfcommSppTransport]; this class only owns
 * Huawei framing, response matching, properties and Handler dispatch.
 *
 * 通过 Android RFCOMM Socket 连接耳机，读写 5A 封包。
 * SPP 端口号由型号配置决定（FreeBuds 6i 使用 port=1）。
 */
class SppDriver(
    private val device: BluetoothDevice,
    val transportConfig: RfcommTransportConfig = RfcommTransportConfig.compatibilityFallback(),
) {

    val isConnected: Boolean get() = transport.isConnected

    private val transport = RfcommSppTransport(
        device = device,
        config = transportConfig,
        onDiscoveryChecked = { wasDiscovering -> onDiscoveryChecked?.invoke(wasDiscovering) },
    )
    private val protocolSession = HuaweiSppProtocol.createSession(transport)

    private val pendingResponses = HuaweiPendingResponseManager()
    // The device accepts only one request/response exchange reliably. This mutex keeps every
    // later command (including a fire-and-forget write) from overtaking a reply. Raw byte writes
    // are serialized by RfcommSppTransport itself.
    private val requestMutex = Mutex()

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

    init {
        protocolSession.setPacketListener { packageData ->
            handlePackage(packageData)
        }
        protocolSession.setDisconnectListener {
            onDisconnected?.invoke()
        }
    }

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

        LogBuffer.i(
            "SPP",
            "Connecting to ${device.name} (${device.address}) via RFCOMM " +
                transportConfig.endpointDescription() + "..."
        )
        try {
            val connected = protocolSession.connect()
            if (!connected) return@withContext false
            LogBuffer.i("SPP", "Connected to ${device.name}")
            true
        } catch (e: CancellationException) {
            protocolSession.disconnect()
            throw e
        } catch (e: Exception) {
            LogBuffer.e("SPP", "Connection failed: ${e.message}")
            protocolSession.disconnect()
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
            protocolSession.send(pkg)
        } catch (e: Exception) {
            LogBuffer.e("SPP", "TX failed: ${e.message}")
            throw e
        }
    }

    /** Processes typed packets emitted by [HuaweiSppProtocol]'s incremental framer. */
    private suspend fun handlePackage(pkg: HuaweiSppPackage) {
        val cmdKey = pkg.commandId.toHex()
        val paramsKeys = pkg.parameters.keys.joinToString(",") { it.toString() }
        LogBuffer.d("SPP", "RX packet cmd=$cmdKey params=[$paramsKeys]")

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
        pendingResponses.cancelAll()
        protocolSession.disconnect()
        LogBuffer.i("SPP", "Disconnected")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
