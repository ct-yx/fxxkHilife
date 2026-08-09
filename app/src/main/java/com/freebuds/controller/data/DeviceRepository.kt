package com.freebuds.controller.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.core.content.FileProvider
import com.freebuds.controller.adapter.huawei.HuaweiOpenFreebudsAdapter
import com.freebuds.controller.adapter.huawei.protocol.HuaweiCommandExchange
import com.freebuds.controller.adapter.huawei.protocol.HuaweiHandlerInitializationPolicy
import com.freebuds.controller.core.adapter.EarbudAdapter
import com.freebuds.controller.core.adapter.EarbudAdapterCallbacks
import com.freebuds.controller.core.session.EarbudSession
import com.freebuds.controller.core.session.LegacySppEarbudSession
import com.freebuds.controller.core.transport.EndpointSource
import com.freebuds.controller.core.transport.RfcommTransportConfigProvider
import com.freebuds.controller.i18n.I18n
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val RFCOMM_RECONNECT_GUARD_MS = 250L

/**
 * Result of an automatic connection request.
 *
 * The boolean alone is not sufficient for callers that need to observe a particular attempt:
 * a periodic Service check can start a different attempt between the request and the caller's
 * next read of [connectionState]. Returning the id together with acceptance keeps attribution
 * deterministic without exposing repository internals to the UI.
 */
data class ConnectionRequestResult(
    val accepted: Boolean,
    val attemptId: String? = null,
)

/** Phase timings captured for one regression sample; null means that phase was not reached. */
data class ConnectionTimingSnapshot(
    val transportConnectMs: Long? = null,
    val transportToCoreReadyMs: Long? = null,
    val coreToReadyMs: Long? = null,
    val coreToDegradedMs: Long? = null,
    val finalStage: ControlChannelStage? = null,
)

data class ListeningStats(
    val totalMs: Long = 0L,
    val todayMs: Long = 0L,
    val activeDays: Int = 0,
    val streakDays: Int = 0,
    val dailyMs: Map<String, Long> = emptyMap(),
)

data class DualConnectDevice(
    val address: String,
    val name: String,
    val autoConnect: Boolean?,
    val preferred: Boolean,
    val connected: Boolean,
    val playing: Boolean,
)

data class SavedDeviceConnection(
    val address: String,
    val name: String,
    val isBonded: Boolean,
    val isSystemConnected: Boolean,
    val isControlConnected: Boolean,
)

/** 设备属性快照（UI 消费） */
data class DeviceProps(
    val batteryGlobal: Int? = null,
    val batteryLeft: Int? = null,
    val batteryRight: Int? = null,
    val batteryCase: Int? = null,
    val isCharging: Boolean? = null,
    val ancMode: String? = null,
    val ancModeOptions: List<String> = emptyList(),
    val ancLevel: String? = null,
    val ancLevelOptions: List<String> = emptyList(),
    val autoPause: Boolean? = null,
    val lowLatency: Boolean? = null,
    val soundQuality: String? = null,
    val soundQualityOptions: List<String> = emptyList(),
    val equalizerPreset: String? = null,
    val equalizerPresetOptions: List<String> = emptyList(),
    val equalizerPresetCreateOptions: List<String> = emptyList(),
    val equalizerRows: List<Int> = emptyList(),
    val equalizerSaved: Boolean? = null,
    val equalizerMaxCustomModes: Int = 0,
    val dualConnectEnabled: Boolean? = null,
    val dualConnectDevices: List<DualConnectDevice> = emptyList(),
    val dualConnectPreferredDevice: String? = null,
    val doubleTapLeft: String? = null,
    val doubleTapRight: String? = null,
    val doubleTapOptions: List<String> = emptyList(),
    val tripleTapLeft: String? = null,
    val tripleTapRight: String? = null,
    val tripleTapOptions: List<String> = emptyList(),
    val longTap: String? = null,
    val longTapOptions: List<String> = emptyList(),
    val swipeGesture: String? = null,
    val swipeGestureOptions: List<String> = emptyList(),
    val inEar: Boolean? = null,
    val deviceModel: String? = null,
    val firmwareVersion: String? = null,
    val pendingInitHandlers: List<String> = emptyList(),
    // 连接时刻 ( = 未连接)，用于计算佩戴时长
    val connectedSince: Long? = null,
)

/**
 * 连接与属性的单一数据源。
 * 由 HilifeApplication 持有单例，ViewModel 注入使用。
 */
class DeviceRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val adapters: List<EarbudAdapter> = listOf(HuaweiOpenFreebudsAdapter)

    /** Typed command boundary used by UI, Service, Tile and regression entry points. */
    val connectionManager: EarbudConnectionManager = EarbudConnectionManager(this)

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

    private val stateStore = EarbudStateStore()
    /** Typed SystemLink/Transport/Core/Deferred state for the upcoming UI migration. */
    val controlChannelState: StateFlow<ControlChannelState> = stateStore.controlChannelState

    val props: StateFlow<DeviceProps> = stateStore.props

    private val _listeningStats = MutableStateFlow(ListeningStats())
    val listeningStats: StateFlow<ListeningStats> = _listeningStats.asStateFlow()

    private val _savedDeviceConnections = MutableStateFlow<List<SavedDeviceConnection>>(emptyList())
    val savedDeviceConnections: StateFlow<List<SavedDeviceConnection>> = _savedDeviceConnections.asStateFlow()

    fun isCoreStateReady(): Boolean {
        val p = stateStore.props.value
        val hasBattery = p.batteryGlobal != null || p.batteryLeft != null || p.batteryRight != null || p.batteryCase != null
        return p.ancMode != null && p.lowLatency != null && hasBattery
    }

    private val session: EarbudSession?
        get() = connectionManager.currentSession
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private val failedHandlers = mutableSetOf<String>()
    // A failed optional probe is not equivalent to a probe that is still running.  Keeping these
    // states separate prevents the details screen from showing "background sync" forever.
    private val pendingInitHandlers = ConcurrentHashMap.newKeySet<String>()
    private var listeningStatsJob: Job? = null
    private var lastListeningTick: Long = 0L
    private var sppQuietUntil: Long = 0L
    private var foregroundMode: Boolean = false
    /** Prevents service/profile probes from racing the debug hardware regression matrix. */
    @Volatile
    private var hardwareRegressionActive: Boolean = false
    private val activeConnectionAttempt: ConnectionAttemptTimeline?
        get() = connectionManager.currentAttempt

    // 设备信息是否已获取（本次进程生命周期内）
    private var deviceInfoFetched: Boolean = false

    // ── 前后台感知 ───────────────────────────────────────────────────────────
    fun setAppInForeground(foreground: Boolean) {
        foregroundMode = foreground
        if (session?.isConnected == true) {
            if (foreground) {
                startFastPolling()
            } else {
                stopFastPolling()
            }
        }
    }

    // ── 初始化 SharedPreferences ─────────────────────────────────────────

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences("fxxk_device", Context.MODE_PRIVATE)
        refreshListeningStats()
        deviceInfoFetched = false // 每次进程启动重置
        refreshSavedDeviceConnections()
    }

    // ── 连接 ─────────────────────────────────────────────────────────────────

    /**
     * Requests one control-channel attempt and returns the attempt id that owns the request.
     * Returning the existing id for a deduplicated request lets Service/Tile/regression callers
     * wait for the same attempt instead of accidentally attributing a later background attempt.
     */
    fun connect(device: BluetoothDevice, trigger: ConnectionTrigger = ConnectionTrigger.UserAction): String? {
        val reservation = connectionManager.withLifecycleLock {
            val result = connectionManager.reserveAttempt(
                address = device.address,
                trigger = trigger,
                busy = connectionManager.isConnectionBusy(),
            )
            if (result?.ownsAttempt == true) {
                connectionManager.setConnectionState(
                    ConnectionState.Connecting(
                        device.name ?: device.address,
                        result.attempt.attemptId,
                    ),
                )
                stateStore.beginAttempt(
                    systemLink = systemLinkState(device.address),
                    deviceName = device.name ?: device.address,
                    address = device.address,
                    attemptId = result.attempt.attemptId,
                    trigger = trigger,
                )
            }
            result?.also { if (it.ownsAttempt) markConnectionPhase(it.attempt, ConnectionPhase.Requested) }
        } ?: return null
        val attempt = reservation.attempt

        // A duplicate request above returns the currently active timeline without launching a
        // second job. Only the owner of a newly-created timeline reaches the body below.
        if (!reservation.ownsAttempt) return attempt.attemptId

        val newConnectionJob = scope.launch {
            var targetSession: EarbudSession? = null
            try {
            if (!isCurrentConnectionAttempt(attempt)) return@launch
            val adapter = adapters.firstOrNull { it.canHandle(device) } ?: HuaweiOpenFreebudsAdapter
            LogBuffer.putMetadata("earbudName", device.name ?: "unknown")
            LogBuffer.putMetadata("earbudAddress", device.address)
            LogBuffer.putMetadata("adapter", adapter.id)
            LogBuffer.putMetadata("connectionAttemptId", attempt.attemptId)
            LogBuffer.putMetadata("connectionTrigger", trigger.name)
            LogBuffer.i(
                "Session",
                "Connect requested attempt=${attempt.attemptId} trigger=${trigger.name} " +
                    "device=${device.name ?: "unknown"} address=${device.address} adapter=${adapter.id}"
            )
            if (connectionManager.isSystemConnected(device.address)) {
                markConnectionPhase(attempt, ConnectionPhase.SystemLinkObserved)
            } else {
                LogBuffer.d("ConnPhase", "attempt=${attempt.attemptId} phase=${ConnectionPhase.SystemLinkObserved} observed=false")
            }
            val d = LegacySppEarbudSession(device, adapter, attempt.attemptId)
            targetSession = d
            val driver = d.legacyDriverOrNull()
            LogBuffer.putMetadata(
                "connectionEndpoint",
                driver.transportConfig.endpointDescription()
            )
            stateStore.updateEndpoint(
                address = device.address,
                endpoint = driver.transportConfig.endpointDescription(),
            )
            driver.onDiscoveryChecked = { wasDiscovering ->
                markConnectionPhase(
                    attempt,
                    ConnectionPhase.DiscoveryStopped,
                    "active=$wasDiscovering",
                )
            }
            d.setPropertyChangedListener {
                scope.launch {
                    if (connectionManager.isCurrentSession(d)) syncProps()
                }
            }
            d.setDisconnectedListener {
                scope.launch { handleRemoteDisconnected(d, "SPP receive loop ended") }
            }
            registerHandlers(adapter, d, device.name ?: "")
            if (!isCurrentConnectionAttempt(attempt)) {
                d.disconnect()
                return@launch
            }
            val installed = connectionManager.withLifecycleLock {
                if (!isCurrentConnectionAttempt(attempt)) {
                    false
                } else {
                    // Install the session and validate the attempt under the same lock.  A
                    // replacement connection can otherwise slip in between the check above and
                    // this assignment, leaving the old session in the repository after a rapid
                    // disconnect/reconnect.
                    connectionManager.installSession(attempt.attemptId, device.address, d)
                }
            }
            if (!installed) {
                d.disconnect()
                return@launch
            }
            markConnectionPhase(attempt, ConnectionPhase.TransportConnecting)
            updateControlChannelStage(ControlChannelStage.ConnectingTransport, attempt)
            val reconnectDelay = connectionManager.withLifecycleLock {
                connectionManager.reconnectDelayMs(SystemClock.elapsedRealtime())
            }
            if (reconnectDelay > 0L) {
                LogBuffer.d("Transport", "Waiting ${reconnectDelay}ms for RFCOMM reconnect window")
                delay(reconnectDelay)
            }
            if (!isCurrentConnectionAttempt(attempt)) {
                d.disconnect()
                return@launch
            }
            if (d.connect()) {
                val accepted = connectionManager.withLifecycleLock {
                    if (!isCurrentConnectionAttempt(attempt) || !connectionManager.isCurrentSession(d)) {
                        false
                    } else {
                        markConnectionPhase(attempt, ConnectionPhase.TransportReady)
                        updateControlChannelStage(ControlChannelStage.TransportReady, attempt)
                        connectionManager.setConnectionState(
                            ConnectionState.Connected(device.name ?: device.address, attempt.attemptId),
                        )
                        connectionManager.setConnectedAt(System.currentTimeMillis())
                        connectionManager.setConnectedAddress(device.address)
                        connectionManager.markSystemConnected(device.address)
                        connectionManager.clearManualDisconnectSuppression()
                        true
                    }
                }
                if (!accepted) {
                    d.disconnect()
                    return@launch
                }
                failedHandlers.clear()
                pendingInitHandlers.clear()
                saveDeviceAddress(device.address)
                refreshSavedDeviceConnections()
                deviceInfoFetched = false // 新连接：允许重新获取设备信息
                syncProps()
                startPolling()
                startListeningStatsTicker()
                if (foregroundMode) startFastPolling()
                startHandlerInitialization(d, attempt)
            } else {
                val ownsFailure = connectionManager.withLifecycleLock {
                    if (!isCurrentConnectionAttempt(attempt) || !connectionManager.isCurrentSession(d)) {
                        false
                    } else {
                        connectionManager.detachSessionIfCurrent(d)
                        connectionManager.setConnectedAddress(null)
                        markConnectionPhase(attempt, ConnectionPhase.Failed, "transport connect returned false")
                        updateControlChannelStage(
                            ControlChannelStage.Failed,
                            attempt,
                            reason = "transport connect returned false",
                        )
                        connectionManager.setConnectionState(
                            ConnectionState.Failed(
                                I18n.t("scan.connection_failed_short"),
                                attempt.attemptId,
                            ),
                        )
                        true
                    }
                }
                if (ownsFailure) {
                    LogBuffer.w("Session", "Connection attempt returned false attempt=${attempt.attemptId}")
                } else {
                    d.disconnect()
                }
            }
            } catch (e: CancellationException) {
                // disconnect() owns state cleanup. A cancelled old attempt must not clear or
                // overwrite the state of a replacement attempt.
                throw e
            } catch (e: Exception) {
                val ownsFailure = connectionManager.withLifecycleLock {
                    if (!isCurrentConnectionAttempt(attempt) ||
                        targetSession == null ||
                        !connectionManager.isCurrentSession(targetSession)
                    ) {
                        false
                    } else {
                        connectionManager.detachSessionIfCurrent(targetSession)
                        connectionManager.setConnectedAddress(null)
                        markConnectionPhase(attempt, ConnectionPhase.Failed, "${e.javaClass.simpleName}:${e.message}")
                        updateControlChannelStage(
                            ControlChannelStage.Failed,
                            attempt,
                            reason = "${e.javaClass.simpleName}:${e.message}",
                        )
                        connectionManager.setConnectionState(
                            ConnectionState.Failed(
                                I18n.t("scan.connection_failed_short"),
                                attempt.attemptId,
                            ),
                        )
                        true
                    }
                }
                targetSession?.disconnect()
                if (ownsFailure) {
                    LogBuffer.e("Session", "Connection attempt failed: ${e.message}")
                }
            }
        }
        val installedJob = connectionManager.withLifecycleLock {
            if (isCurrentConnectionAttempt(attempt)) {
                connectionManager.installConnectionJob(newConnectionJob)
                true
            } else {
                false
            }
        }
        if (!installedJob) newConnectionJob.cancel()
        return attempt.attemptId
    }

    fun disconnect() {
        var oldSession: EarbudSession? = null
        connectionManager.withLifecycleLock {
            connectionManager.cancelConnectionWork()
            connectionManager.suppressManualDisconnect(System.currentTimeMillis() + 10 * 60_000L)
            connectionManager.armReconnectGuard(
                SystemClock.elapsedRealtime() + RFCOMM_RECONNECT_GUARD_MS,
            )
            activeConnectionAttempt?.let {
                markConnectionPhase(it, ConnectionPhase.Disconnecting)
                updateControlChannelStage(ControlChannelStage.Disconnecting, it)
            }
            oldSession = connectionManager.detachSession()
            val oldAddress = connectionManager.connectedAddress
            connectionManager.setConnectedAddress(null)
            activeConnectionAttempt?.let { markConnectionPhase(it, ConnectionPhase.Disconnected) }
            connectionManager.clearAttempt()
            connectionManager.setConnectionState(ConnectionState.Disconnected)
            stateStore.replaceProps(DeviceProps())
            connectionManager.setConnectedAt(0L)
            stateStore.resetControlChannel(systemLinkState(oldAddress))
        }
        stopListeningStatsTicker()
        sppQuietUntil = 0L
        oldSession?.disconnect()
        deviceInfoFetched = false
        failedHandlers.clear()
        pendingInitHandlers.clear()
        refreshSavedDeviceConnections()
    }

    /**
     * Test-only entry point used by the in-app hardware regression runner.
     * It executes the same cleanup path as ACTION_ACL_DISCONNECTED while leaving the real
     * broadcast receiver intact for normal operation. The report labels this as synthetic.
     */
    fun regressionSimulateAclDisconnect() {
        val source = session
        val address = connectionManager.connectedAddress
        if (source == null && address == null) {
            LogBuffer.w("HwTest", "Synthetic ACL disconnect skipped: no active session")
            return
        }
        LogBuffer.i("HwTest", "Synthetic ACL disconnect requested")
        handleRemoteDisconnected(source, "Bluetooth ACL disconnected (regression runner)")
        address?.let {
            connectionManager.markSystemDisconnected(it)
            if (stateStore.controlChannelState.value.address == it) {
                updateControlChannelState { state -> state.copy(systemLink = SystemLinkState.Disconnected) }
            }
        }
        refreshSavedDeviceConnections()
    }

    /**
     * The debug runner owns the connection slot for the duration of its matrix. Only background
     * periodic/profile probes are paused; explicit test commands still traverse production code.
     */
    internal fun setHardwareRegressionActive(active: Boolean) {
        hardwareRegressionActive = active
        LogBuffer.i("HwTest", "background_auto_connect_paused=$active")
    }

    /** Device selected by the one-click real-device regression runner. */
    fun getRegressionDevice(): BluetoothDevice? {
        val address = connectionManager.connectedAddress ?: getSavedAddress() ?: return null
        return runCatching { BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address) }.getOrNull()
    }

    fun getRegressionAttemptId(): String? = activeConnectionAttempt?.attemptId

    /**
     * Returns the active attempt only when it belongs to [address].  Entry-point code must not
     * use a global "current attempt" as proof that a request for another saved device was
     * accepted; the single-session invariant is address-scoped.
     */
    fun getActiveConnectionAttemptId(address: String? = null): String? {
        val attempt = activeConnectionAttempt ?: return null
        if (address != null && !attempt.address.equals(address, ignoreCase = true)) return null
        return attempt.attemptId
    }

    fun getRegressionTiming(attemptId: String?): ConnectionTimingSnapshot? {
        if (attemptId == null) return null
        val attempt = activeConnectionAttempt ?: return null
        if (attempt.attemptId != attemptId) return null
        return ConnectionTimingSnapshot(
            transportConnectMs = attempt.elapsed(
                ConnectionPhase.TransportConnecting,
                ConnectionPhase.TransportReady,
            ),
            transportToCoreReadyMs = attempt.elapsed(
                ConnectionPhase.TransportReady,
                ConnectionPhase.CoreReady,
            ),
            coreToReadyMs = attempt.elapsed(
                ConnectionPhase.CoreReady,
                ConnectionPhase.Ready,
            ),
            coreToDegradedMs = attempt.elapsed(
                ConnectionPhase.CoreReady,
                ConnectionPhase.Degraded,
            ),
            finalStage = stateStore.controlChannelState.value
                .takeIf { it.attemptId == attemptId }
                ?.stage,
        )
    }

    fun getRegressionCommandExchange(operation: String): HuaweiCommandExchange? =
        session?.legacyDriverOrNull()?.getLastCommandExchange(operation)

    fun clearRegressionCommandExchange(operation: String) {
        session?.legacyDriverOrNull()?.clearLastCommandExchange(operation)
    }

    private fun isCurrentConnectionAttempt(attempt: ConnectionAttemptTimeline): Boolean =
        connectionManager.isCurrentAttempt(attempt)

    fun getRegressionEndpoint(): String {
        session?.legacyDriverOrNull()?.transportConfig?.endpointDescription()?.let { return it }

        // The runner builds its report after the final disconnect. Keep endpoint/channel/source
        // observable then as well; otherwise the report would misleadingly say "unavailable"
        // even though every attempt used the model adapter's endpoint.
        val device = getRegressionDevice() ?: return "unavailable"
        val adapter = adapters.firstOrNull { it.canHandle(device) } ?: HuaweiOpenFreebudsAdapter
        return (adapter as? RfcommTransportConfigProvider)
            ?.rfcommTransportConfig(runCatching { device.name }.getOrNull())
            ?.endpointDescription()
            ?: "unavailable"
    }

    fun clearManualDisconnectSuppression() {
        connectionManager.clearManualDisconnectSuppression()
    }

    /**
     * Handles the single system ACL-disconnect command emitted by [SystemBluetoothMonitor].
     * Keeping this in the connection boundary is important: merely clearing the system-link
     * flag leaves the RFCOMM reader and an in-flight initialization job alive, which was the
     * failure mode seen in the F regression scenario.
     */
    internal fun handleSystemBluetoothDisconnected(address: String) {
        connectionManager.markSystemDisconnected(address)
        if (stateStore.controlChannelState.value.address == address) {
            updateControlChannelState { state -> state.copy(systemLink = SystemLinkState.Disconnected) }
        }
        val sourceSession = session?.takeIf {
            connectionManager.connectedAddress.equals(address, ignoreCase = true)
        }
        val activeAttemptMatches = activeConnectionAttempt?.address?.equals(address, ignoreCase = true) == true
        if (sourceSession != null || activeAttemptMatches) {
            // Cleanup is deliberately synchronous at the command boundary. An ACL reconnect can
            // arrive immediately after the broadcast; launching cleanup asynchronously lets it
            // observe the old session and incorrectly deduplicate the new ACL attempt into it.
            handleRemoteDisconnected(sourceSession, "Bluetooth ACL disconnected")
        } else {
            refreshSavedDeviceConnections()
        }
    }

    private fun handleRemoteDisconnected(sourceSession: EarbudSession?, reason: String) {
        var oldSession: EarbudSession? = null
        var oldAddress: String? = null
        connectionManager.withLifecycleLock {
            if (sourceSession != null && !connectionManager.isCurrentSession(sourceSession)) return
            if (connectionState.value !is ConnectionState.Connected &&
                session == null &&
                activeConnectionAttempt == null
            ) return
            LogBuffer.w("SPP", "Remote disconnected: $reason")
            connectionManager.cancelConnectionWork()
            oldSession = connectionManager.detachSession()
            oldAddress = connectionManager.connectedAddress
            connectionManager.armReconnectGuard(
                SystemClock.elapsedRealtime() + RFCOMM_RECONNECT_GUARD_MS,
            )
            connectionManager.setConnectedAddress(null)
            connectionManager.setConnectionState(ConnectionState.Disconnected)
            stateStore.replaceProps(DeviceProps())
            connectionManager.setConnectedAt(0L)
            activeConnectionAttempt?.let { markConnectionPhase(it, ConnectionPhase.Disconnected, reason) }
            connectionManager.clearAttempt()
            stateStore.resetControlChannel(systemLinkState(oldAddress), reason)
        }
        stopListeningStatsTicker()
        sppQuietUntil = 0L
        deviceInfoFetched = false
        failedHandlers.clear()
        pendingInitHandlers.clear()
        // ACL loss and the regression hook can arrive before the transport read loop notices the
        // closed stream. Close the old session explicitly so its RFCOMM reader cannot survive
        // into the next attempt and contend with the new socket.
        oldSession?.disconnect()
        refreshSavedDeviceConnections()
    }

    // ── 持久化设备地址（集合）───────────────────────────────────────────

    private fun saveDeviceAddress(address: String) {
        val p = prefs ?: return
        val set = p.getStringSet("saved_devices", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(address)
        val ordered = getSavedAddresses().filterNot { it == address } + address
        p.edit()
            .putStringSet("saved_devices", set)
            .putString("saved_devices_order", ordered.joinToString(","))
            .apply()
        refreshSavedDeviceConnections()
    }

    fun getSavedAddresses(): List<String> {
        val p = prefs ?: return emptyList()
        val set = p.getStringSet("saved_devices", emptySet())?.toSet() ?: emptySet()
        val ordered = p.getString("saved_devices_order", null)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() && it in set }
            ?: emptyList()
        return ordered + set.filterNot { it in ordered }.sorted()
    }

    fun getSavedAddress(): String? = getSavedAddresses().lastOrNull()

    fun refreshSavedDeviceConnections() {
        val addresses = getSavedAddresses()
        val adapter = BluetoothAdapter.getDefaultAdapter()
        _savedDeviceConnections.value = addresses.map { address ->
            val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
            val name = runCatching { device?.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: address
            val bonded = runCatching { device?.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
            SavedDeviceConnection(
                address = address,
                name = name,
                isBonded = bonded,
                isSystemConnected = getSystemConnectedDevice(address) != null,
                isControlConnected = address == connectionManager.connectedAddress &&
                    session?.isConnected == true,
            )
        }
    }

    private fun getSystemConnectedDevice(address: String): BluetoothDevice? {
        val adapterDevice = runCatching {
            BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)
        }.getOrNull()

        if (connectionManager.isSystemConnected(address)) return adapterDevice

        val isConnectedByDevice = adapterDevice?.let { device ->
            runCatching {
                val method = device.javaClass.getMethod("isConnected")
                method.invoke(device) as? Boolean
            }.getOrNull()
        } == true
        if (isConnectedByDevice) return adapterDevice

        val context = appContext ?: return null
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return null
        val connectedDevices = buildList {
            addAll(runCatching { manager.getConnectedDevices(BluetoothProfile.HEADSET) }.getOrDefault(emptyList()))
            addAll(runCatching { manager.getConnectedDevices(BluetoothProfile.A2DP) }.getOrDefault(emptyList()))
        }
        return connectedDevices.firstOrNull { it.address == address }
    }

    fun autoConnectSaved(
        address: String,
        logMisses: Boolean = true,
        trigger: ConnectionTrigger = ConnectionTrigger.UserAction,
    ): Boolean = autoConnectSavedRequest(address, logMisses, trigger).accepted

    internal fun autoConnectSavedRequest(
        address: String,
        logMisses: Boolean = true,
        trigger: ConnectionTrigger = ConnectionTrigger.UserAction,
    ): ConnectionRequestResult {
        if (isBackgroundAutoConnectSuppressed(trigger, hardwareRegressionActive)) {
            if (logMisses) LogBuffer.d("AutoConnect", "Skip $address: hardware regression owns connection slot")
            return ConnectionRequestResult(accepted = false)
        }
        val suppressRemaining = connectionManager.manualDisconnectSuppressionRemaining(
            System.currentTimeMillis(),
        )
        if (suppressRemaining > 0) {
            if (logMisses) {
                LogBuffer.i("AutoConnect", "Skip $address: manual disconnect suppression ${suppressRemaining / 1000}s left")
            }
            return ConnectionRequestResult(accepted = false)
        }
        if (connectionManager.isConnectionBusy()) {
            val currentAttempt = activeConnectionAttempt
            val sameAddressAttempt = currentAttempt?.takeIf {
                it.address.equals(address, ignoreCase = true)
            }
            return if (sameAddressAttempt != null) {
                ConnectionRequestResult(true, sameAddressAttempt.attemptId)
            } else {
                LogBuffer.d("AutoConnect", "Skip $address: another device control channel is active")
                ConnectionRequestResult(accepted = false)
            }
        }

        val device = getSystemConnectedDevice(address) ?: run {
            if (logMisses) {
                LogBuffer.d("AutoConnect", "Skip $address: system Bluetooth is not connected")
            }
            return ConnectionRequestResult(accepted = false)
        }
        return autoConnectKnownSystemConnectedRequest(device, logMisses, trigger)
    }

    fun autoConnectKnownSystemConnected(
        device: BluetoothDevice,
        logMisses: Boolean = true,
        trigger: ConnectionTrigger = ConnectionTrigger.ServiceCommand,
    ): Boolean = autoConnectKnownSystemConnectedRequest(device, logMisses, trigger).accepted

    internal fun autoConnectKnownSystemConnectedRequest(
        device: BluetoothDevice,
        logMisses: Boolean = true,
        trigger: ConnectionTrigger = ConnectionTrigger.ServiceCommand,
    ): ConnectionRequestResult {
        val address = device.address
        if (isBackgroundAutoConnectSuppressed(trigger, hardwareRegressionActive)) {
            if (logMisses) LogBuffer.d("AutoConnect", "Skip $address: hardware regression owns connection slot")
            return ConnectionRequestResult(accepted = false)
        }
        if (address !in getSavedAddresses()) {
            if (logMisses) LogBuffer.d("AutoConnect", "Ignore unsaved system-connected device $address")
            return ConnectionRequestResult(accepted = false)
        }
        val suppressRemaining = connectionManager.manualDisconnectSuppressionRemaining(
            System.currentTimeMillis(),
        )
        if (suppressRemaining > 0) {
            if (logMisses) {
                LogBuffer.i("AutoConnect", "Skip $address: manual disconnect suppression ${suppressRemaining / 1000}s left")
            }
            return ConnectionRequestResult(accepted = false)
        }
        if (connectionManager.isConnectionBusy()) {
            val currentAttempt = activeConnectionAttempt
            val sameAddressAttempt = currentAttempt?.takeIf {
                it.address.equals(address, ignoreCase = true)
            }
            return if (sameAddressAttempt != null) {
                ConnectionRequestResult(true, sameAddressAttempt.attemptId)
            } else {
                if (logMisses) {
                    LogBuffer.d(
                        "AutoConnect",
                        "Skip $address: another device control channel is active",
                    )
                }
                ConnectionRequestResult(accepted = false)
            }
        }

        connectionManager.markSystemConnected(address)
        LogBuffer.i("AutoConnect", "System Bluetooth connected for ${device.name ?: address}; opening app SPP control channel")
        val attemptId = connect(device, trigger)
        return ConnectionRequestResult(accepted = attemptId != null, attemptId = attemptId)
    }

    fun autoConnectLastSaved(trigger: ConnectionTrigger = ConnectionTrigger.ServiceCommand): Boolean {
        return autoConnectLastSavedRequest(trigger).accepted
    }

    internal fun autoConnectLastSavedRequest(
        trigger: ConnectionTrigger = ConnectionTrigger.ServiceCommand,
    ): ConnectionRequestResult {
        val address = getSavedAddress() ?: return ConnectionRequestResult(accepted = false)
        return autoConnectSavedRequest(address, trigger = trigger)
    }

    fun autoConnectSystemConnectedSaved(
        logMisses: Boolean = true,
        trigger: ConnectionTrigger = ConnectionTrigger.PeriodicCheck,
    ): Boolean = autoConnectSystemConnectedSavedRequest(logMisses, trigger).accepted

    internal fun autoConnectSystemConnectedSavedRequest(
        logMisses: Boolean = true,
        trigger: ConnectionTrigger = ConnectionTrigger.PeriodicCheck,
    ): ConnectionRequestResult {
        val addresses = getSavedAddresses().asReversed()
        if (addresses.isEmpty()) {
            if (logMisses) LogBuffer.d("AutoConnect", "No saved devices for background control connect")
            return ConnectionRequestResult(accepted = false)
        }
        for (address in addresses) {
            val result = autoConnectSavedRequest(address, logMisses, trigger)
            if (result.accepted) return result
        }
        return ConnectionRequestResult(accepted = false)
    }

    private fun isAutoLowLatencyEnabled(): Boolean = appContext
        ?.getSharedPreferences("settings", Context.MODE_PRIVATE)
        ?.getBoolean("auto_low_latency", true) ?: true

    /**
     * The automatic mode is a connection action, not a consequence of a successful state read.
     * FreeBuds 6i can ignore its initial 2b6c read while still accepting a 2b6c write, so
     * waiting for `lowLatency != null` made the automatic path unreachable.
     */
    /**
     * Retry automatic low latency on the same initialization coroutine as every other command.
     * The old implementation launched a second job while Deferred handlers were running; both
     * jobs shared the response-id lane and a 2b6c ACK could be mistaken for the read-back result.
     */
    private suspend fun recoverAutoLowLatencyIfEnabled() {
        if (!isAutoLowLatencyEnabled()) return

        val maxAttempts = 3
        LogBuffer.i("SPP", "Auto low latency recovery start current=${stateStore.props.value.lowLatency}")
        repeat(maxAttempts) { index ->
            if (connectionState.value !is ConnectionState.Connected) return
            syncProps()
            if (stateStore.props.value.lowLatency == true) {
                LogBuffer.i("SPP", "Auto low latency confirmed")
                failedHandlers.remove("low_latency")
                return
            }

            val attempt = index + 1
            applyAutoLowLatencyAttempt(
                attempt = attempt,
                phase = if (attempt == 1) "post-core" else "recovery",
            )
            syncProps()
            if (stateStore.props.value.lowLatency == true) {
                LogBuffer.i("SPP", "Auto low latency confirmed attempt=$attempt")
                failedHandlers.remove("low_latency")
                return
            }
            if (attempt < maxAttempts) delay(900L)
        }

        LogBuffer.w("SPP", "Auto low latency was not confirmed after $maxAttempts serialized attempts")
    }

    private suspend fun applyAutoLowLatencyAttempt(attempt: Int, phase: String) {
        LogBuffer.i(
            "SPP",
            "Auto low latency $phase apply attempt=$attempt current=${stateStore.props.value.lowLatency}"
        )
        setSppQuietUntil(System.currentTimeMillis() + 1_500L)
        try {
            setProperty("config", "low_latency", "true")
        } catch (e: Exception) {
            LogBuffer.w(
                "SPP",
                "Auto low latency $phase failed attempt=$attempt reason=${e.javaClass.simpleName}:${e.message}"
            )
        }
    }

    private fun setSppQuietUntil(untilMs: Long) {
        sppQuietUntil = untilMs
        session?.legacyDriverOrNull()?.setCommandQuietUntil(untilMs)
    }

    fun removeSavedDevice(address: String) {
        val p = prefs ?: return
        val set = p.getStringSet("saved_devices", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.remove(address)
        val ordered = getSavedAddresses().filterNot { it == address }
        p.edit()
            .putStringSet("saved_devices", set)
            .putString("saved_devices_order", ordered.joinToString(","))
            .apply()
        refreshSavedDeviceConnections()
    }

    // ── OpenFreebuds-style init degradation ────────────────────────────────

    private val coreRetryOrder = listOf(
        "anc_global",
        "battery",
        "config_sound_quality",
        "low_latency",
        "tws_in_ear",
    )

    private fun logDegradedHandlersOnce() {
        if (failedHandlers.isEmpty()) return
        val core = failedHandlers.filter { it in coreRetryOrder }
        val optional = failedHandlers.filterNot { it in coreRetryOrder }
        LogBuffer.w(
            "SPP",
            "OpenFreebuds-style init degraded; skipped core=$core optional=$optional. " +
                "Optional handlers are initialized after the control channel is usable to avoid blocking connect."
        )
    }

    private fun startHandlerInitialization(
        targetSession: EarbudSession,
        attempt: ConnectionAttemptTimeline,
    ) {
        connectionManager.cancelInitializationJobs()
        val newInitializationJob = scope.launch {
            try {
                // Android's RFCOMM socket can report connected before the earbud command loop is
                // ready. Keep the verified-model first pass short; failed handlers are recovered
                // later with a longer budget so a slow optional response does not delay the
                // normal connection path.
                val coreHandlers = targetSession.handlerIds.filter { it in coreRetryOrder }
                pendingInitHandlers.clear()
                pendingInitHandlers.addAll(coreHandlers)
                syncProps()

                markConnectionPhase(attempt, ConnectionPhase.Settling)
                updateControlChannelStage(ControlChannelStage.Settling, attempt)
                delay(HuaweiHandlerInitializationPolicy.INITIAL_SETTLE_DELAY_MS)
                markConnectionPhase(attempt, ConnectionPhase.CoreInitializing)
                updateControlChannelStage(ControlChannelStage.InitializingCore, attempt)
                if (!connectionManager.isCurrentSession(targetSession) || targetSession.isConnected != true) return@launch
                val initialCoreTimeoutMs = HuaweiHandlerInitializationPolicy.initialCoreTimeoutMs(
                    targetSession.legacyDriverOrNull()?.transportConfig?.source
                        ?: EndpointSource.CompatibilityFallback,
                )
                LogBuffer.i(
                    "SPP",
                    "CORE first pass timeout=${initialCoreTimeoutMs}ms " +
                        "endpoint=${targetSession.legacyDriverOrNull()?.transportConfig?.endpointDescription() ?: "unknown"}",
                )
                targetSession.initializeCoreHandlers(timeoutMs = initialCoreTimeoutMs, maxAttempts = 1)
                if (!connectionManager.isCurrentSession(targetSession) || targetSession.isConnected != true) return@launch
                failedHandlers.clear()
                failedHandlers.addAll(targetSession.failedHandlerIds)
                syncProps()

                markConnectionPhase(attempt, ConnectionPhase.CoreReady)
                updateControlChannelStage(ControlChannelStage.CoreReady, attempt)
                if (failedHandlers.any { it in coreRetryOrder }) {
                    markConnectionPhase(attempt, ConnectionPhase.Degraded)
                    updateControlChannelStage(ControlChannelStage.Degraded, attempt)
                }
                pendingInitHandlers.clear()
                pendingInitHandlers.addAll(failedHandlers.filter { it in coreRetryOrder })
                syncProps()

                // Recovery and optional capability probing stay on the same serialized command
                // lane, but run in the background after the fast core result is published.
                // User actions can therefore reach the scheduler without waiting for a failed
                // 3-second probe or automatic low-latency retry.
                startBackgroundInitialization(targetSession, attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (connectionManager.isCurrentSession(targetSession)) {
                    LogBuffer.w("SPP", "Handler initialization stopped: ${e.message}")
                    markConnectionPhase(attempt, ConnectionPhase.Failed, "initialization ${e.javaClass.simpleName}")
                    updateControlChannelStage(
                        ControlChannelStage.Failed,
                        attempt,
                        reason = "initialization ${e.javaClass.simpleName}",
                    )
                    failedHandlers.clear()
                    failedHandlers.addAll(targetSession.failedHandlerIds)
                    pendingInitHandlers.clear()
                    syncProps()
                }
            }
        }
        connectionManager.withLifecycleLock {
            if (connectionManager.isCurrentSession(targetSession) &&
                connectionManager.isCurrentAttempt(attempt)
            ) {
                connectionManager.installInitializationJob(newInitializationJob)
            } else {
                newInitializationJob.cancel()
            }
        }
    }

    private fun startBackgroundInitialization(
        targetSession: EarbudSession,
        attempt: ConnectionAttemptTimeline,
    ) {
        connectionManager.cancelBackgroundInitializationJob()
        val newBackgroundInitializationJob = scope.launch {
            try {
                if (!connectionManager.isCurrentSession(targetSession) || targetSession.isConnected != true) return@launch

                // Recover failed core reads before Deferred work takes the request lane. This is
                // still serialized; it is simply no longer part of the first usable result.
                recoverCoreHandlers(
                    targetSession = targetSession,
                    rounds = 1,
                )
                if (!connectionManager.isCurrentSession(targetSession) || targetSession.isConnected != true) return@launch

                // Automatic low latency is an action, not a prerequisite for the control channel.
                recoverAutoLowLatencyIfEnabled()
                if (!connectionManager.isCurrentSession(targetSession) || targetSession.isConnected != true) return@launch

                // The low-latency write/read-back path has its own settle delay. A short handoff
                // gap is enough before optional probes and avoids the old fixed one-second stall.
                delay(HuaweiHandlerInitializationPolicy.BACKGROUND_HANDOFF_DELAY_MS)
                pendingInitHandlers.addAll(
                    targetSession.handlerIds.filterNot { it in coreRetryOrder || it == "drop_logs" }
                )
                syncProps()
                markConnectionPhase(attempt, ConnectionPhase.DeferredInitializing)
                updateControlChannelStage(ControlChannelStage.InitializingDeferred, attempt)
                targetSession.initializeDeferredHandlers()
                if (!connectionManager.isCurrentSession(targetSession) || targetSession.isConnected != true) return@launch
                failedHandlers.clear()
                failedHandlers.addAll(targetSession.failedHandlerIds)

                // Optional probes are best-effort. Failed core handlers receive the remaining
                // bounded recovery rounds without blocking the first connection result.
                recoverCoreHandlers(
                    targetSession = targetSession,
                    rounds = HuaweiHandlerInitializationPolicy.CORE_RECOVERY_ROUNDS - 1,
                )
                if (!connectionManager.isCurrentSession(targetSession) || targetSession.isConnected != true) return@launch

                pendingInitHandlers.clear()
                syncProps()
                logDegradedHandlersOnce()
                if (failedHandlers.isEmpty()) {
                    markConnectionPhase(attempt, ConnectionPhase.Ready)
                    updateControlChannelStage(ControlChannelStage.Ready, attempt)
                } else {
                    markConnectionPhase(attempt, ConnectionPhase.Degraded)
                    updateControlChannelStage(ControlChannelStage.Degraded, attempt)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (connectionManager.isCurrentSession(targetSession)) {
                    LogBuffer.w("SPP", "Background initialization stopped: ${e.message}")
                    pendingInitHandlers.clear()
                    syncProps()
                    logDegradedHandlersOnce()
                    markConnectionPhase(attempt, ConnectionPhase.Degraded, "background initialization")
                    updateControlChannelStage(
                        ControlChannelStage.Degraded,
                        attempt,
                        reason = "background initialization ${e.javaClass.simpleName}",
                    )
                }
            }
        }
        connectionManager.withLifecycleLock {
            if (connectionManager.isCurrentSession(targetSession) &&
                connectionManager.isCurrentAttempt(attempt)
            ) {
                connectionManager.installBackgroundInitializationJob(newBackgroundInitializationJob)
            } else {
                newBackgroundInitializationJob.cancel()
            }
        }
    }

    private suspend fun recoverCoreHandlers(
        targetSession: EarbudSession,
        rounds: Int,
    ) {
        repeat(rounds.coerceAtLeast(0)) { index ->
            val failedCore = failedHandlers.filter { it in coreRetryOrder }
            if (failedCore.isEmpty()) return

            pendingInitHandlers.clear()
            pendingInitHandlers.addAll(failedCore)
            syncProps()
            delay(HuaweiHandlerInitializationPolicy.CORE_RECOVERY_ROUND_DELAY_MS)
            LogBuffer.i(
                "SPP",
                "CORE recovery round=${index + 1}/$rounds handlers=$failedCore " +
                    "timeout=${HuaweiHandlerInitializationPolicy.CORE_RECOVERY_TIMEOUT_MS}ms"
            )
            targetSession.initializeCoreHandlers(
                timeoutMs = HuaweiHandlerInitializationPolicy.CORE_RECOVERY_TIMEOUT_MS,
                maxAttempts = 1,
            )
            failedHandlers.clear()
            failedHandlers.addAll(targetSession.failedHandlerIds)
            syncProps()
        }
    }

    // ── 定时轮询（后台 5s 基础属性，前台 fastPoll 800ms）─────────────────

    private fun startPolling() {
        connectionManager.cancelPollingJob()
        val newPollingJob = scope.launch {
            while (isActive && session?.isConnected == true) {
                delay(5_000) // 后台每 5s 轮询一次
                if (!isActive || session?.isConnected != true) break
                if (!foregroundMode) {
                    syncProps()
                }
            }
        }
        connectionManager.withLifecycleLock {
            if (session?.isConnected == true) {
                connectionManager.installPollingJob(newPollingJob)
            } else {
                newPollingJob.cancel()
            }
        }
    }

    private fun startFastPolling() {
        connectionManager.cancelFastPollingJob()
        val newFastPollingJob = scope.launch {
            var tick = 0
            while (isActive && session?.isConnected == true && foregroundMode) {
                delay(800) // 前台 800ms 高频刷新
                if (!isActive || session?.isConnected != true || !foregroundMode) break
                // 设备信息仅在首次连接时获取一次；核心状态/自动低延迟稳定前不抢 SPP 通道
                if (!deviceInfoFetched &&
                    !connectionManager.isInitializationActive() &&
                    System.currentTimeMillis() >= sppQuietUntil &&
                    failedHandlers.none { it in coreRetryOrder }
                ) {
                    val d = session ?: return@launch
                    val legacyDriver = d.legacyDriverOrNull() ?: return@launch
                    val infoHandler = d.getHandlerById("device_info")
                    if (infoHandler != null) {
                        try {
                            withTimeout(1500) { infoHandler.onInit(legacyDriver) }
                            deviceInfoFetched = true
                            failedHandlers.remove("device_info")
                        } catch (_: Exception) { }
                    } else {
                        deviceInfoFetched = true
                    }
                }
                syncProps()
                tick++
            }
        }
        connectionManager.withLifecycleLock {
            if (session?.isConnected == true && foregroundMode) {
                connectionManager.installFastPollingJob(newFastPollingJob)
            } else {
                newFastPollingJob.cancel()
            }
        }
    }

    private fun stopFastPolling() {
        connectionManager.cancelFastPollingJob()
    }

    // ── 属性写入（UI → 设备）──等待响应后重新同步 ─────────────────

    suspend fun setProperty(group: String, prop: String, value: String) {
        val d = session ?: return

        // ========== v2.7.0 根因修复：源头乐观更新 ==========
        // 让 UI、Tile、Notification 全部瞬间看到预期值，彻底解决跳变问题
        when {
            group == "anc" && prop == "mode" -> {
                stateStore.updateProps { it.copy(ancMode = value) }
            }
            group == "config" && prop == "low_latency" -> {
                stateStore.updateProps { it.copy(lowLatency = value.toBooleanStrictOrNull()) }
            }
            group == "sound" && prop == "quality_preference" -> {
                stateStore.updateProps { it.copy(soundQuality = value) }
            }
            group == "sound" && prop == "equalizer_preset" -> {
                stateStore.updateProps { it.copy(equalizerPreset = value) }
            }
            group == "dual_connect" && prop == "enabled" -> {
                stateStore.updateProps { it.copy(dualConnectEnabled = value.toBooleanStrictOrNull()) }
            }
        }

        d.setProperty(group, prop, value)
        delay(150)
        syncProps()
    }

    private fun ensureDefaultAncOptions() {
        if (stateStore.props.value.ancModeOptions.isEmpty()) {
            stateStore.updateProps {
                it.copy(
                ancModeOptions = listOf("normal", "cancellation", "awareness")
                )
            }
        }
    }

    // ── 听音统计 ─────────────────────────────────────────────────────────

    private fun todayKey(time: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = time }
        return String.format(Locale.US, "%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    private fun readDailyListening(): MutableMap<String, Long> {
        val p = prefs ?: return mutableMapOf()
        val raw = p.getString("listening_daily_ms", "") ?: ""
        return raw.split(";")
            .mapNotNull { item ->
                val parts = item.split("=")
                if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0L) else null
            }
            .filter { it.first.isNotBlank() && it.second > 0L }
            .toMap()
            .toMutableMap()
    }

    private fun writeDailyListening(map: Map<String, Long>) {
        val p = prefs ?: return
        val compact = map.entries
            .sortedBy { it.key }
            .takeLast(180)
            .joinToString(";") { "${it.key}=${it.value}" }
        p.edit().putString("listening_daily_ms", compact).apply()
    }

    private fun refreshListeningStats(extraTodayMs: Long = 0L) {
        val daily = readDailyListening().toMutableMap()
        if (extraTodayMs > 0L) {
            val key = todayKey()
            daily[key] = (daily[key] ?: 0L) + extraTodayMs
            writeDailyListening(daily)
        }
        val today = todayKey()
        var streak = 0
        val cal = Calendar.getInstance()
        while (true) {
            val key = todayKey(cal.timeInMillis)
            if ((daily[key] ?: 0L) <= 0L) break
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        _listeningStats.value = ListeningStats(
            totalMs = daily.values.sum(),
            todayMs = daily[today] ?: 0L,
            activeDays = daily.count { it.value > 0L },
            streakDays = streak,
            dailyMs = daily.toMap(),
        )
    }

    private fun startListeningStatsTicker() {
        listeningStatsJob?.cancel()
        lastListeningTick = System.currentTimeMillis()
        listeningStatsJob = scope.launch {
            while (isActive) {
                delay(60_000)
                val now = System.currentTimeMillis()
                if (connectionState.value is ConnectionState.Connected) {
                    val delta = (now - lastListeningTick).coerceIn(0L, 5 * 60_000L)
                    refreshListeningStats(delta)
                }
                lastListeningTick = now
            }
        }
    }

    private fun stopListeningStatsTicker() {
        val now = System.currentTimeMillis()
        val delta = if (lastListeningTick > 0L) (now - lastListeningTick).coerceIn(0L, 5 * 60_000L) else 0L
        if (delta > 0L && connectionState.value is ConnectionState.Connected) {
            refreshListeningStats(delta)
        }
        listeningStatsJob?.cancel()
        listeningStatsJob = null
        lastListeningTick = 0L
    }

    // ── 分享日志 ─────────────────────────────────────────────────────────

    fun shareLog(context: Context, chooserTitle: String = I18n.t("settings.share_log_chooser")) {
        scope.launch {
            try {
                val dir = File(context.cacheDir, "logs")
                dir.mkdirs()
                dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 7 * 24 * 60 * 60_000L }
                    ?.forEach { it.delete() }
                val file = File(dir, "fxxkHilife_diagnostic_${System.currentTimeMillis()}.txt")
                file.writeText(LogBuffer.getDiagnosticReport())
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(intent, chooserTitle))
                }
            } catch (e: Exception) {
                LogBuffer.e("Share", "Failed to share diagnostic log: ${e.message}")
            }
        }
    }
    private fun markConnectionPhase(
        attempt: ConnectionAttemptTimeline,
        phase: ConnectionPhase,
        detail: String? = null,
    ) {
        val mark = attempt.mark(phase)
        val suffix = buildString {
            append(" elapsed=${mark.elapsedSinceStartMs}ms")
            if (!mark.firstMark) append(" duplicate=true")
            detail?.takeIf { it.isNotBlank() }?.let { append(" detail=${it.replace(' ', '_')}") }
        }
        LogBuffer.i(
            "ConnPhase",
            "attempt=${attempt.attemptId} trigger=${attempt.trigger.name} " +
                "phase=${phase.name}$suffix"
        )
        LogBuffer.putMetadata("connectionPhase", phase.name)
        LogBuffer.putMetadata(
            "connectionPhase_${phase.name}",
            mark.elapsedSinceStartMs.toString(),
        )
        val phaseAtKey = when (phase) {
            ConnectionPhase.Requested -> "requestedAtElapsedMs"
            ConnectionPhase.SystemLinkObserved -> "systemLinkObservedAtElapsedMs"
            ConnectionPhase.DiscoveryStopped -> "discoveryStoppedAtElapsedMs"
            ConnectionPhase.TransportConnecting -> "transportConnectingAtElapsedMs"
            ConnectionPhase.TransportReady -> "transportReadyAtElapsedMs"
            ConnectionPhase.Settling -> "settlingAtElapsedMs"
            ConnectionPhase.CoreInitializing -> "coreInitializingAtElapsedMs"
            ConnectionPhase.CoreReady -> "coreReadyAtElapsedMs"
            ConnectionPhase.DeferredInitializing -> "deferredInitializingAtElapsedMs"
            ConnectionPhase.Ready -> "readyAtElapsedMs"
            ConnectionPhase.Degraded -> "degradedAtElapsedMs"
            ConnectionPhase.Failed -> "failedAtElapsedMs"
            ConnectionPhase.Disconnecting -> "disconnectingAtElapsedMs"
            ConnectionPhase.Disconnected -> "disconnectedAtElapsedMs"
        }
        LogBuffer.putMetadata(phaseAtKey, mark.elapsedSinceStartMs.toString())

        when (phase) {
            ConnectionPhase.TransportReady -> {
                attempt.elapsed(ConnectionPhase.TransportConnecting, ConnectionPhase.TransportReady)
                    ?.let { LogBuffer.putMetadata("transportConnectElapsedMs", it.toString()) }
            }
            ConnectionPhase.CoreReady -> {
                attempt.elapsed(ConnectionPhase.TransportReady, ConnectionPhase.CoreReady)
                    ?.let { LogBuffer.putMetadata("transportToCoreReadyElapsedMs", it.toString()) }
            }
            ConnectionPhase.Ready,
            ConnectionPhase.Degraded -> {
                attempt.elapsed(ConnectionPhase.CoreReady, phase)
                    ?.let { LogBuffer.putMetadata("coreTo${phase.name}ElapsedMs", it.toString()) }
            }
            else -> Unit
        }
    }

    private fun systemLinkState(address: String?): SystemLinkState =
        if (address != null && connectionManager.isSystemConnected(address)) {
            SystemLinkState.Connected
        } else {
            SystemLinkState.Disconnected
        }

    private fun updateControlChannelState(transform: (ControlChannelState) -> ControlChannelState) {
        stateStore.updateControlChannel(transform)
    }

    private fun updateControlChannelStage(
        stage: ControlChannelStage,
        attempt: ConnectionAttemptTimeline? = activeConnectionAttempt,
        reason: String? = null,
    ) {
        val current = stateStore.controlChannelState.value
        val address = attempt?.address ?: current.address ?: connectionManager.connectedAddress
        stateStore.updateStage(
            stage = stage,
            attempt = attempt,
            addressOverride = address,
            systemLink = address?.let { systemLinkState(it) } ?: current.systemLink,
            pendingHandlers = pendingInitHandlers.toSet(),
            failedHandlers = failedHandlers.toSet(),
            reason = reason,
        )
    }

    // ── 内部：从 session 状态映射同步到 StateFlow ─────────────────────────────

    private suspend fun syncProps() {
        val d = session ?: return
        stateStore.replaceProps(d.mapState(
            failedHandlers = pendingInitHandlers.toSet(),
            connectedSince = connectionManager.connectedAt.takeIf { it > 0 },
        ))
        val current = stateStore.controlChannelState.value
        val address = current.address ?: connectionManager.connectedAddress
        val linked = if (address != null) {
            current.copy(systemLink = systemLinkState(address))
        } else {
            current
        }
        stateStore.updateControlChannel { linked }
        stateStore.updateHandlers(
            activeAttemptId = activeConnectionAttempt?.attemptId,
            pendingHandlers = pendingInitHandlers.toSet(),
            failedHandlers = failedHandlers.toSet(),
        )
        ensureDefaultAncOptions()
    }

    // ── 内部：注册 Handler ─────────────────────────────────────────────────────

    private fun registerHandlers(adapter: EarbudAdapter, d: EarbudSession, name: String) {
        val legacyDriver = d.legacyDriverOrNull() ?: return
        adapter.registerHandlers(
            driver = legacyDriver,
            deviceName = name,
            callbacks = EarbudAdapterCallbacks(
                onStateChanged = { syncProps() }
            )
        )
    }


    fun getDriver() = session?.legacyDriverOrNull()
}
