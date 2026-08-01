package com.freebuds.controller.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import androidx.core.content.FileProvider
import com.freebuds.controller.adapter.huawei.HuaweiOpenFreebudsAdapter
import com.freebuds.controller.adapter.huawei.protocol.HuaweiHandlerInitializationPolicy
import com.freebuds.controller.core.adapter.EarbudAdapter
import com.freebuds.controller.core.adapter.EarbudAdapterCallbacks
import com.freebuds.controller.core.session.EarbudSession
import com.freebuds.controller.core.session.LegacySppEarbudSession
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
import java.util.concurrent.atomic.AtomicLong

/** 连接状态 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Failed(val reason: String) : ConnectionState()
}

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

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _props = MutableStateFlow(DeviceProps())
    val props: StateFlow<DeviceProps> = _props.asStateFlow()

    private val _listeningStats = MutableStateFlow(ListeningStats())
    val listeningStats: StateFlow<ListeningStats> = _listeningStats.asStateFlow()

    private val _savedDeviceConnections = MutableStateFlow<List<SavedDeviceConnection>>(emptyList())
    val savedDeviceConnections: StateFlow<List<SavedDeviceConnection>> = _savedDeviceConnections.asStateFlow()

    fun isCoreStateReady(): Boolean {
        val p = _props.value
        val hasBattery = p.batteryGlobal != null || p.batteryLeft != null || p.batteryRight != null || p.batteryCase != null
        return p.ancMode != null && p.lowLatency != null && hasBattery
    }

    private var session: EarbudSession? = null
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private val failedHandlers = mutableSetOf<String>()
    // A failed optional probe is not equivalent to a probe that is still running.  Keeping these
    // states separate prevents the details screen from showing "background sync" forever.
    private val pendingInitHandlers = ConcurrentHashMap.newKeySet<String>()
    private var pollJob: Job? = null
    private var fastPollJob: Job? = null
    private var initJob: Job? = null
    private var listeningStatsJob: Job? = null
    private var lastListeningTick: Long = 0L
    private var sppQuietUntil: Long = 0L
    private var foregroundMode: Boolean = false
    private var connectedAddress: String? = null
    private var aclReceiver: BroadcastReceiver? = null
    private var connectingJob: Job? = null
    private var manualDisconnectSuppressUntil: Long = 0L
    private val systemConnectedAddresses = ConcurrentHashMap.newKeySet<String>()
    private val connectionAttemptSequence = AtomicLong(0L)
    private var activeConnectionAttempt: ConnectionAttemptTimeline? = null

    // 设备信息是否已获取（本次进程生命周期内）
    private var deviceInfoFetched: Boolean = false

    // 连接建立时刻（>0 表示已连接），用于计算佩戴时长
    private var connectedAt: Long = 0

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
        registerAclDisconnectReceiver(context.applicationContext)
        refreshSavedDeviceConnections()
    }

    // ── 连接 ─────────────────────────────────────────────────────────────────

    fun connect(device: BluetoothDevice, trigger: ConnectionTrigger = ConnectionTrigger.UserAction) {
        if (connectingJob?.isActive == true ||
            _connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected) return

        connectingJob = scope.launch {
            val attempt = ConnectionAttemptTimeline(
                attemptId = "attempt-${System.currentTimeMillis()}-${connectionAttemptSequence.incrementAndGet()}",
                address = device.address,
                trigger = trigger,
                nowMs = { SystemClock.elapsedRealtime() },
            )
            activeConnectionAttempt = attempt
            markConnectionPhase(attempt, ConnectionPhase.Requested)
            _connectionState.value = ConnectionState.Connecting(device.name ?: device.address)
            val adapter = adapters.firstOrNull { it.canHandle(device) } ?: HuaweiOpenFreebudsAdapter
            LogBuffer.putMetadata("earbudName", device.name ?: "unknown")
            LogBuffer.putMetadata("earbudAddress", device.address)
            LogBuffer.putMetadata("adapter", adapter.id)
            LogBuffer.putMetadata("connectionAttemptId", attempt.attemptId)
            LogBuffer.putMetadata("connectionTrigger", trigger.name)
            LogBuffer.putMetadata("connectionEndpoint", "rfcomm-port=${com.freebuds.controller.bluetooth.SppDriver.SPP_SERVICE_PORT}")
            LogBuffer.i(
                "Session",
                "Connect requested attempt=${attempt.attemptId} trigger=${trigger.name} " +
                    "device=${device.name ?: "unknown"} address=${device.address} adapter=${adapter.id}"
            )
            if (device.address in systemConnectedAddresses) {
                markConnectionPhase(attempt, ConnectionPhase.SystemLinkObserved)
            } else {
                LogBuffer.d("ConnPhase", "attempt=${attempt.attemptId} phase=${ConnectionPhase.SystemLinkObserved} observed=false")
            }
            val d = LegacySppEarbudSession(device, adapter)
            d.legacyDriverOrNull()?.onDiscoveryChecked = { wasDiscovering ->
                markConnectionPhase(
                    attempt,
                    ConnectionPhase.DiscoveryStopped,
                    "active=$wasDiscovering",
                )
            }
            d.setPropertyChangedListener {
                scope.launch {
                    if (session === d) syncProps()
                }
            }
            d.setDisconnectedListener {
                scope.launch { handleRemoteDisconnected(d, "SPP receive loop ended") }
            }
            registerHandlers(adapter, d, device.name ?: "")
            session = d
            markConnectionPhase(attempt, ConnectionPhase.TransportConnecting)
            if (d.connect()) {
                markConnectionPhase(attempt, ConnectionPhase.TransportReady)
                _connectionState.value = ConnectionState.Connected(device.name ?: device.address)
                connectedAt = System.currentTimeMillis()
                connectedAddress = device.address
                systemConnectedAddresses.add(device.address)
                manualDisconnectSuppressUntil = 0L
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
                session = null
                connectedAddress = null
                markConnectionPhase(attempt, ConnectionPhase.Failed, "transport connect returned false")
                _connectionState.value = ConnectionState.Failed(I18n.t("scan.connection_failed_short"))
                activeConnectionAttempt = null
            }
        }
    }

    fun disconnect() {
        manualDisconnectSuppressUntil = System.currentTimeMillis() + 10 * 60_000L
        activeConnectionAttempt?.let { markConnectionPhase(it, ConnectionPhase.Disconnecting) }
        initJob?.cancel()
        pollJob?.cancel()
        fastPollJob?.cancel()
        stopListeningStatsTicker()
        sppQuietUntil = 0L
        session?.disconnect()
        session = null
        connectedAddress = null
        deviceInfoFetched = false
        failedHandlers.clear()
        pendingInitHandlers.clear()
        _connectionState.value = ConnectionState.Disconnected
        _props.value = DeviceProps()
        connectedAt = 0
        activeConnectionAttempt?.let { markConnectionPhase(it, ConnectionPhase.Disconnected) }
        activeConnectionAttempt = null
        refreshSavedDeviceConnections()
    }

    fun clearManualDisconnectSuppression() {
        manualDisconnectSuppressUntil = 0L
    }

    private fun handleRemoteDisconnected(sourceSession: EarbudSession?, reason: String) {
        if (sourceSession != null && session !== sourceSession) return
        if (_connectionState.value !is ConnectionState.Connected && session == null) return
        LogBuffer.w("SPP", "Remote disconnected: $reason")
        initJob?.cancel()
        pollJob?.cancel()
        fastPollJob?.cancel()
        stopListeningStatsTicker()
        sppQuietUntil = 0L
        session = null
        connectedAddress = null
        deviceInfoFetched = false
        failedHandlers.clear()
        pendingInitHandlers.clear()
        _connectionState.value = ConnectionState.Disconnected
        _props.value = DeviceProps()
        connectedAt = 0
        activeConnectionAttempt?.let { markConnectionPhase(it, ConnectionPhase.Disconnected, reason) }
        activeConnectionAttempt = null
        refreshSavedDeviceConnections()
    }

    private fun registerAclDisconnectReceiver(context: Context) {
        if (aclReceiver != null) return
        aclReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val address = device?.address ?: return
                if (address == connectedAddress) {
                    scope.launch { handleRemoteDisconnected(session, "Bluetooth ACL disconnected") }
                }
                systemConnectedAddresses.remove(address)
                refreshSavedDeviceConnections()
            }
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(aclReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(aclReceiver, filter)
        }
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
                isControlConnected = address == connectedAddress && session?.isConnected == true,
            )
        }
    }

    private fun getSystemConnectedDevice(address: String): BluetoothDevice? {
        val adapterDevice = runCatching {
            BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)
        }.getOrNull()

        if (address in systemConnectedAddresses) return adapterDevice

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
    ): Boolean {
        val suppressRemaining = manualDisconnectSuppressUntil - System.currentTimeMillis()
        if (suppressRemaining > 0) {
            if (logMisses) {
                LogBuffer.i("AutoConnect", "Skip $address: manual disconnect suppression ${suppressRemaining / 1000}s left")
            }
            return false
        }
        if (_connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected ||
            connectingJob?.isActive == true
        ) return true

        val device = getSystemConnectedDevice(address) ?: run {
            if (logMisses) {
                LogBuffer.d("AutoConnect", "Skip $address: system Bluetooth is not connected")
            }
            return false
        }
        return autoConnectKnownSystemConnected(device, logMisses, trigger)
    }

    fun autoConnectKnownSystemConnected(
        device: BluetoothDevice,
        logMisses: Boolean = true,
        trigger: ConnectionTrigger = ConnectionTrigger.ServiceCommand,
    ): Boolean {
        val address = device.address
        if (address !in getSavedAddresses()) {
            if (logMisses) LogBuffer.d("AutoConnect", "Ignore unsaved system-connected device $address")
            return false
        }
        val suppressRemaining = manualDisconnectSuppressUntil - System.currentTimeMillis()
        if (suppressRemaining > 0) {
            if (logMisses) {
                LogBuffer.i("AutoConnect", "Skip $address: manual disconnect suppression ${suppressRemaining / 1000}s left")
            }
            return false
        }
        if (_connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected ||
            connectingJob?.isActive == true
        ) return true

        systemConnectedAddresses.add(address)
        LogBuffer.i("AutoConnect", "System Bluetooth connected for ${device.name ?: address}; opening app SPP control channel")
        connect(device, trigger)
        return true
    }

    fun noteSystemBluetoothDisconnected(address: String) {
        systemConnectedAddresses.remove(address)
        refreshSavedDeviceConnections()
    }

    fun autoConnectLastSaved(trigger: ConnectionTrigger = ConnectionTrigger.ServiceCommand): Boolean {
        val address = getSavedAddress() ?: return false
        return autoConnectSaved(address, trigger = trigger)
    }

    fun autoConnectSystemConnectedSaved(
        logMisses: Boolean = true,
        trigger: ConnectionTrigger = ConnectionTrigger.PeriodicCheck,
    ): Boolean {
        val addresses = getSavedAddresses().asReversed()
        if (addresses.isEmpty()) {
            if (logMisses) LogBuffer.d("AutoConnect", "No saved devices for background control connect")
            return false
        }
        for (address in addresses) {
            if (autoConnectSaved(address, logMisses, trigger)) return true
        }
        return false
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
        LogBuffer.i("SPP", "Auto low latency recovery start current=${_props.value.lowLatency}")
        repeat(maxAttempts) { index ->
            if (_connectionState.value !is ConnectionState.Connected) return
            syncProps()
            if (_props.value.lowLatency == true) {
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
            if (_props.value.lowLatency == true) {
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
            "Auto low latency $phase apply attempt=$attempt current=${_props.value.lowLatency}"
        )
        sppQuietUntil = System.currentTimeMillis() + 1_500L
        try {
            setProperty("config", "low_latency", "true")
        } catch (e: Exception) {
            LogBuffer.w(
                "SPP",
                "Auto low latency $phase failed attempt=$attempt reason=${e.javaClass.simpleName}:${e.message}"
            )
        }
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
        initJob?.cancel()
        initJob = scope.launch {
            try {
                // Android's RFCOMM socket can report connected before the earbud command loop is
                // ready. The real-device baseline showed that a one-second outer timeout loses
                // the first ANC/low-latency reads while later battery/sound reads succeed.
                val coreHandlers = targetSession.handlerIds.filter { it in coreRetryOrder }
                pendingInitHandlers.clear()
                pendingInitHandlers.addAll(coreHandlers)
                syncProps()

                markConnectionPhase(attempt, ConnectionPhase.Settling)
                delay(HuaweiHandlerInitializationPolicy.INITIAL_SETTLE_DELAY_MS)
                markConnectionPhase(attempt, ConnectionPhase.CoreInitializing)
                if (session !== targetSession || targetSession.isConnected != true) return@launch
                targetSession.initializeCoreHandlers()
                if (session !== targetSession || targetSession.isConnected != true) return@launch
                failedHandlers.clear()
                failedHandlers.addAll(targetSession.failedHandlerIds)
                syncProps()

                markConnectionPhase(attempt, ConnectionPhase.CoreReady)
                if (failedHandlers.any { it in coreRetryOrder }) {
                    markConnectionPhase(attempt, ConnectionPhase.Degraded)
                }

                // Recover failed core reads before Deferred work takes the request lane. This is
                // intentionally serialized in initJob; the previous retry job competed with
                // Deferred handlers and made 2b6c/2b2a responses nondeterministic.
                recoverCoreHandlers(
                    targetSession = targetSession,
                    rounds = 1,
                )
                if (session !== targetSession || targetSession.isConnected != true) return@launch
                pendingInitHandlers.clear()
                pendingInitHandlers.addAll(failedHandlers.filter { it in coreRetryOrder })
                syncProps()

                // Automatic low latency is an action, not a prerequisite for TransportReady.
                // Apply it only after the first core recovery and keep all confirmation attempts
                // on this same command lane.
                recoverAutoLowLatencyIfEnabled()
                if (session !== targetSession || targetSession.isConnected != true) return@launch

                delay(1_000)

                pendingInitHandlers.addAll(
                    targetSession.handlerIds.filterNot { it in coreRetryOrder || it == "drop_logs" }
                )
                syncProps()
                markConnectionPhase(attempt, ConnectionPhase.DeferredInitializing)
                targetSession.initializeDeferredHandlers()
                if (session !== targetSession || targetSession.isConnected != true) return@launch
                failedHandlers.clear()
                failedHandlers.addAll(targetSession.failedHandlerIds)

                // Optional probes are best-effort, but an ANC/battery/low-latency read that was
                // lost during the warm-up window deserves further bounded recovery. This keeps
                // initialization progressing after the first ten seconds instead of dropping
                // the failed handlers permanently.
                recoverCoreHandlers(
                    targetSession = targetSession,
                    rounds = HuaweiHandlerInitializationPolicy.CORE_RECOVERY_ROUNDS - 1,
                )
                if (session !== targetSession || targetSession.isConnected != true) return@launch

                pendingInitHandlers.clear()
                syncProps()
                logDegradedHandlersOnce()
                if (failedHandlers.isEmpty()) {
                    markConnectionPhase(attempt, ConnectionPhase.Ready)
                } else {
                    markConnectionPhase(attempt, ConnectionPhase.Degraded)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (session === targetSession) {
                    LogBuffer.w("SPP", "Handler initialization stopped: ${e.message}")
                    markConnectionPhase(attempt, ConnectionPhase.Failed, "initialization ${e.javaClass.simpleName}")
                    failedHandlers.clear()
                    failedHandlers.addAll(targetSession.failedHandlerIds)
                    pendingInitHandlers.clear()
                    syncProps()
                }
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
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && session?.isConnected == true) {
                delay(5_000) // 后台每 5s 轮询一次
                if (!isActive || session?.isConnected != true) break
                if (!foregroundMode) {
                    syncProps()
                }
            }
        }
    }

    private fun startFastPolling() {
        fastPollJob?.cancel()
        fastPollJob = scope.launch {
            var tick = 0
            while (isActive && session?.isConnected == true && foregroundMode) {
                delay(800) // 前台 800ms 高频刷新
                if (!isActive || session?.isConnected != true || !foregroundMode) break
                // 设备信息仅在首次连接时获取一次；核心状态/自动低延迟稳定前不抢 SPP 通道
                if (!deviceInfoFetched &&
                    initJob?.isActive != true &&
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
    }

    private fun stopFastPolling() {
        fastPollJob?.cancel()
        fastPollJob = null
    }

    // ── 属性写入（UI → 设备）──等待响应后重新同步 ─────────────────

    suspend fun setProperty(group: String, prop: String, value: String) {
        val d = session ?: return

        // ========== v2.7.0 根因修复：源头乐观更新 ==========
        // 让 UI、Tile、Notification 全部瞬间看到预期值，彻底解决跳变问题
        when {
            group == "anc" && prop == "mode" -> {
                _props.value = _props.value.copy(ancMode = value)
            }
            group == "config" && prop == "low_latency" -> {
                _props.value = _props.value.copy(lowLatency = value.toBooleanStrictOrNull())
            }
            group == "sound" && prop == "quality_preference" -> {
                _props.value = _props.value.copy(soundQuality = value)
            }
            group == "sound" && prop == "equalizer_preset" -> {
                _props.value = _props.value.copy(equalizerPreset = value)
            }
            group == "dual_connect" && prop == "enabled" -> {
                _props.value = _props.value.copy(dualConnectEnabled = value.toBooleanStrictOrNull())
            }
        }

        d.setProperty(group, prop, value)
        delay(150)
        syncProps()
    }

    private fun ensureDefaultAncOptions() {
        if (_props.value.ancModeOptions.isEmpty()) {
            _props.value = _props.value.copy(
                ancModeOptions = listOf("normal", "cancellation", "awareness")
            )
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
                if (_connectionState.value is ConnectionState.Connected) {
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
        if (delta > 0L && _connectionState.value is ConnectionState.Connected) {
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

    // ── 内部：从 session 状态映射同步到 StateFlow ─────────────────────────────

    private suspend fun syncProps() {
        val d = session ?: return
        _props.value = d.mapState(
            failedHandlers = pendingInitHandlers.toSet(),
            connectedSince = connectedAt.takeIf { it > 0 },
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
