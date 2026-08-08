package com.freebuds.controller.bluetooth

import android.bluetooth.BluetoothDevice
import com.freebuds.controller.adapter.huawei.protocol.HuaweiHandlerInitializer
import com.freebuds.controller.adapter.huawei.protocol.HuaweiHandlerInitializationPolicy
import com.freebuds.controller.adapter.huawei.protocol.HuaweiHandlerRegistry
import com.freebuds.controller.adapter.huawei.protocol.HuaweiCommandClient
import com.freebuds.controller.adapter.huawei.protocol.HuaweiCommandExchange
import com.freebuds.controller.adapter.huawei.protocol.HuaweiCommandPriority
import com.freebuds.controller.adapter.huawei.protocol.HuaweiPropertyStore
import com.freebuds.controller.adapter.huawei.protocol.HuaweiSppProtocol
import com.freebuds.controller.core.transport.RfcommTransportConfig
import com.freebuds.controller.core.transport.RfcommSppTransport
import com.freebuds.controller.protocol.HuaweiSppPackage
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.*

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
    private val connectionAttemptId: String? = null,
) {

    val isConnected: Boolean get() = transport.isConnected

    private val transport = RfcommSppTransport(
        device = device,
        config = transportConfig,
        onDiscoveryChecked = { wasDiscovering -> onDiscoveryChecked?.invoke(wasDiscovering) },
        connectionAttemptId = connectionAttemptId,
    )
    private val protocolSession = HuaweiSppProtocol.createSession(transport)
    private val commandClient = HuaweiCommandClient(protocolSession)

    private val handlerRegistry = HuaweiHandlerRegistry()
    private val handlerInitializer = HuaweiHandlerInitializer(handlerRegistry)
    private val propertyStore = HuaweiPropertyStore()
    @Volatile private var lastCommandExchange: HuaweiCommandExchange? = null
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
                transportConfig.endpointDescription() +
                " attemptId=${connectionAttemptId ?: "unknown"}..."
        )
        try {
            val connected = protocolSession.connect()
            if (!connected) return@withContext false
            LogBuffer.i(
                "SPP",
                "Connected to ${device.name} attemptId=${connectionAttemptId ?: "unknown"}",
            )
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
        priority: HuaweiCommandPriority = HuaweiCommandPriority.BACKGROUND,
        operation: String = "request-${pkg.commandKey}",
    ): HuaweiSppPackage? = commandClient.sendPackage(pkg, timeout, responsePredicate, priority, operation)

    /**
     * Sends a packet without waiting for its own response.
     *
     * It still waits for an in-flight request/response exchange to complete. Otherwise a
     * fire-and-forget write could overtake an initialization read and reproduce the exact
     * request-loss race that the serialized request lane prevents.
     */
    suspend fun sendNowait(
        pkg: HuaweiSppPackage,
        priority: HuaweiCommandPriority = HuaweiCommandPriority.BACKGROUND,
        operation: String = "send-${pkg.commandKey}",
    ) {
        commandClient.sendNowait(pkg, priority, operation)
    }

    suspend fun writeAndReadBack(
        operation: String,
        write: HuaweiSppPackage,
        read: HuaweiSppPackage,
        writeTimeoutMs: Long = HuaweiCommandClient.DEFAULT_TIMEOUT_MS,
        readTimeoutMs: Long = HuaweiCommandClient.DEFAULT_TIMEOUT_MS,
        settleDelayMs: Long = 0L,
        writeResponsePredicate: (HuaweiSppPackage) -> Boolean = { true },
        readResponsePredicate: (HuaweiSppPackage) -> Boolean = { true },
        readBackPredicate: (HuaweiSppPackage) -> Boolean,
        priority: HuaweiCommandPriority = HuaweiCommandPriority.USER_ACTION,
    ): HuaweiCommandExchange {
        val result = commandClient.writeAndReadBack(
            operation = operation,
            write = write,
            read = read,
            writeTimeoutMs = writeTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            settleDelayMs = settleDelayMs,
            writeResponsePredicate = writeResponsePredicate,
            readResponsePredicate = readResponsePredicate,
            readBackPredicate = readBackPredicate,
            priority = priority,
        )
        lastCommandExchange = result
        return result
    }

    /** Debug regression hook for verifying the write ACK separately from state read-back. */
    fun getLastCommandExchange(operation: String): HuaweiCommandExchange? =
        lastCommandExchange?.takeIf { it.operation == operation }

    fun clearLastCommandExchange(operation: String) {
        if (lastCommandExchange?.operation == operation) lastCommandExchange = null
    }

    /** Processes typed packets emitted by [HuaweiSppProtocol]'s incremental framer. */
    private suspend fun handlePackage(pkg: HuaweiSppPackage) {
        val cmdKey = pkg.commandKey
        val paramsKeys = pkg.parameters.keys.joinToString(",") { it.toString() }
        LogBuffer.d("SPP", "RX packet cmd=$cmdKey params=[$paramsKeys]")

        if (commandClient.complete(pkg)) {
            LogBuffer.d("SPP", "RX → pendingResponses consumed cmd=$cmdKey")
            return
        }

        if (handlerRegistry.hasCommand(cmdKey)) {
            val handlers = handlerRegistry.handlersForCommand(cmdKey)
            for (handler in handlers) {
                LogBuffer.d("SPP", "RX → onDriverPackage handler=${handler.id} cmd=$cmdKey")
                handler.onDriverPackage(this, pkg)
            }
        } else {
            LogBuffer.d("SPP", "No handler for cmd=$cmdKey")
        }
    }

    fun disconnect() {
        commandClient.cancelAll()
        protocolSession.disconnect()
        LogBuffer.i("SPP", "Disconnected")
    }

    /**
     * Makes the repository's post-write quiet window a real command-lane gate instead of a
     * polling-only hint.
     */
    fun setCommandQuietUntil(untilMs: Long) = commandClient.setQuietUntil(untilMs)
}
