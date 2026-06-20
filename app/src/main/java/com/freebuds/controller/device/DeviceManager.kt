package com.freebuds.controller.device

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import com.freebuds.controller.FreeBudsApp
import com.freebuds.controller.bluetooth.ConnectionEvent
import com.freebuds.controller.bluetooth.ConnectionState
import com.freebuds.controller.bluetooth.SppClient
import com.freebuds.controller.device.handler.*
import com.freebuds.controller.util.DebugLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

class DeviceManager(private val bluetoothAdapter: BluetoothAdapter?) {

    companion object {
        const val TAG = "DeviceManager"

        // Exponential-backoff bounds for auto-reconnect
        private const val RECONNECT_BASE_DELAY_MS = 1_000L       // 1s
        private const val RECONNECT_MAX_DELAY_MS = 60_000L      // 60s
        private const val RECONNECT_MAX_ATTEMPTS = 10            // give up after 10 attempts
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var client: SppClient? = null
    private var refreshJob: Job? = null
    private var eventJob: Job? = null
    private var reconnectJob: Job? = null
    private val connectMutex = Mutex()

    var lastConnectedDevice: BluetoothDevice? = null
    private var autoConnectDone = false
    private var reconnectAttempt = 0

    private val _state = MutableStateFlow(DeviceState())
    val state: StateFlow<DeviceState> get() = _state

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> get() = _connectionState

    var currentProfile: DeviceProfile = DeviceProfile.GENERIC

    private val handlers: List<Handler> = listOf(
        BatteryHandler(),
        AncModeHandler(),
        LowLatencyHandler(),
        SoundQualityHandler(),
        EqPresetHandler(),
        GestureHandler(),
        DeviceInfoHandler(),
        DualConnectHandler()
    )

    fun findPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.filter { device ->
                device.name?.contains("FreeBuds", ignoreCase = true) == true ||
                device.name?.contains("FreeLace", ignoreCase = true) == true ||
                device.name?.contains("Honor", ignoreCase = true) == true ||
                device.name?.contains("HUAWEI", ignoreCase = true) == true
            } ?: emptyList()
        } catch (e: SecurityException) {
            DebugLogger.w(TAG, "BLUETOOTH_CONNECT permission denied in findPairedDevices()")
            emptyList()
        }
    }

    suspend fun connect(device: BluetoothDevice, profile: DeviceProfile = DeviceProfile.GENERIC) {
        connectMutex.withLock {
            // Reset reconnect state on explicit user connect
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempt = 0

            if (_connectionState.value == ConnectionState.CONNECTED &&
                lastConnectedDevice?.address == device.address) {
                DebugLogger.w(TAG, "Already connected to ${device.name}, but reconnecting (socket may have been killed externally)")
                disconnect()
            }
            if (_connectionState.value == ConnectionState.CONNECTING) {
                DebugLogger.w(TAG, "Already connecting, ignoring duplicate connect request")
                return
            }
            disconnect()
            lastConnectedDevice = device
            currentProfile = profile
            val c = SppClient(device)
            client = c
            _connectionState.value = ConnectionState.CONNECTING

            DebugLogger.i(TAG, "Starting connect to ${device.name} (${device.address}) profile=${profile.modelName}")

            eventJob = scope.launch {
                try {
                    c.events.collect { event ->
                        when (event) {
                            is ConnectionEvent.Connected -> {
                                _connectionState.value = ConnectionState.CONNECTED
                                reconnectAttempt = 0
                                _state.value = _state.value.copy(
                                    connected = true,
                                    lastDeviceName = device.name ?: "Unknown",
                                    lastDeviceAddress = device.address
                                )
                                DebugLogger.i(TAG, "✅ Connected! Initializing ${handlers.size} handlers...")

                                // Register as companion device to improve auto-reconnect
                                try {
                                    FreeBudsApp.instance.companionDeviceHelper.register(device)
                                } catch (_: Exception) { }

                                // Phase 1: Register event handlers (always succeeds)
                                for (handler in handlers) {
                                    try {
                                        handler.init(c)
                                    } catch (e: Exception) {
                                        DebugLogger.e(TAG, "Handler ${handler::class.simpleName}.init() failed", e)
                                    }
                                }

                                // Phase 2: Actively query initial state for each handler (upstream on_init pattern)
                                // Each handler sends its read command, parses response, and returns state delta.
                                // Retry up to 3 times with 2s timeout, skip failures (matching generic.py init logic)
                                var initialState = _state.value.copy(connected = true)
                                for (handler in handlers) {
                                    var success = false
                                    for (attempt in 1..3) {
                                        try {
                                            val delta = withTimeout(2000) { handler.onInit(c, initialState) }
                                            if (delta != null) {
                                                initialState = delta
                                                success = true
                                                DebugLogger.i(TAG, "Handler ${handler.id} onInit: OK (attempt=$attempt)")
                                            }
                                            break
                                        } catch (e: Exception) {
                                            DebugLogger.w(TAG, "Handler ${handler.id} onInit attempt $attempt/3 failed: ${e.message}")
                                        }
                                    }
                                    if (!success) {
                                        DebugLogger.w(TAG, "Handler ${handler.id} onInit: all attempts failed, using fallback")
                                    }
                                }
                                _state.value = initialState

                                // Phase 3: Full state refresh to catch any missed updates
                                refreshState()
                                scope.launch {
                                    val autoLatency = FreeBudsApp.instance.preferences.lowLatencyAutoOn.first()
                                    if (autoLatency) {
                                        delay(500)
                                        setProperty("low_latency", "true")
                                    }
                                }
                            }
                            is ConnectionEvent.Disconnected -> {
                                _connectionState.value = ConnectionState.DISCONNECTED
                                _state.value = _state.value.copy(connected = false)
                                DebugLogger.w(TAG, "Disconnected: ${event.reason}")
                                refreshJob?.cancel()
                                if (event.reason != "Destroyed" &&
                                    event.reason != "User requested" &&
                                    lastConnectedDevice != null) {
                                    scope.launch { startReconnectBackoff() }
                                }
                            }
                            is ConnectionEvent.PackageReceived -> {
                                refreshState()
                            }
                            is ConnectionEvent.HeartbeatLost -> {
                                DebugLogger.w(TAG, "Heartbeat lost after ${event.attempt} attempts — initiating reconnect")
                                _connectionState.value = ConnectionState.DISCONNECTED
                                _state.value = _state.value.copy(connected = false)
                                disconnect()
                                if (lastConnectedDevice != null) {
                                    scope.launch { startReconnectBackoff() }
                                }
                            }
                            is ConnectionEvent.Error -> {
                                _connectionState.value = ConnectionState.DISCONNECTED
                                _state.value = _state.value.copy(connected = false)
                                DebugLogger.e(TAG, "Connection error: ${event.error.message}", event.error)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    DebugLogger.d(TAG, "eventJob cancelled (normal)")
                } catch (e: Exception) {
                    DebugLogger.e(TAG, "eventJob unhandled exception", e)
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }

            val connected = c.connect()
            if (!connected) {
                eventJob?.cancel()
                eventJob = null
                _connectionState.value = ConnectionState.DISCONNECTED
                DebugLogger.w(TAG, "❌ connect() returned false for ${device.name}")
                throw RuntimeException("Failed to connect to ${device.name} — SPP handshake failed or timed out")
            }
        }
    }

    /**
     * Exponential-backoff auto-reconnect loop.
     * Delays: 1s → 2s → 4s → 8s → … → 60s max, up to RECONNECT_MAX_ATTEMPTS.
     * Cancelled on explicit user connect() or disconnect().
     * Shows a Toast on exhaustion using PermissionHelper.
     */
    private suspend fun startReconnectBackoff() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val device = lastConnectedDevice ?: return@launch
            while (reconnectAttempt < RECONNECT_MAX_ATTEMPTS && isActive) {
                reconnectAttempt++
                val delayMs = computeBackoffDelay(reconnectAttempt)
                DebugLogger.i(TAG, "Auto-reconnect attempt $reconnectAttempt/$RECONNECT_MAX_ATTEMPTS in ${delayMs}ms")

                delay(delayMs)
                if (!isActive) break
                if (_connectionState.value == ConnectionState.CONNECTED) break

                if (bluetoothAdapter?.isEnabled == false) continue

                DebugLogger.i(TAG, "Auto-reconnect attempt $reconnectAttempt/$RECONNECT_MAX_ATTEMPTS → connecting to ${device.name}...")
                try {
                    connectMutex.withLock {
                        if (_connectionState.value != ConnectionState.DISCONNECTED) return@withLock
                        val c = SppClient(device)
                        client = c
                        _connectionState.value = ConnectionState.CONNECTING

                        eventJob?.cancel()
                        eventJob = scope.launch {
                            try {
                                c.events.collect { event ->
                                    handleEvent(event, device)
                                }
                            } catch (_: CancellationException) { }
                        }

                        val ok = c.connect()
                        if (ok) {
                            reconnectAttempt = 0
                            // Re-register companion association after reconnect
                            try {
                                FreeBudsApp.instance.companionDeviceHelper.register(device)
                            } catch (_: Exception) { }
                            DebugLogger.i(TAG, "✅ Auto-reconnect succeeded")
                        } else {
                            eventJob?.cancel()
                            eventJob = null
                            _connectionState.value = ConnectionState.DISCONNECTED
                            DebugLogger.w(TAG, "Auto-reconnect attempt $reconnectAttempt failed")
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "Auto-reconnect attempt $reconnectAttempt error: ${e.message}")
                }
            }

            if (reconnectAttempt >= RECONNECT_MAX_ATTEMPTS) {
                DebugLogger.e(TAG, "Auto-reconnect exhausted after $RECONNECT_MAX_ATTEMPTS attempts — giving up")
                // Show user-friendly toast via PermissionHelper
                val ctx = FreeBudsApp.instance
                com.freebuds.controller.util.PermissionHelper.onAutoReconnectExhausted(
                    ctx,
                    device.name ?: device.address
                )
                // Also suggest battery optimization as a possible cause
                if (!com.freebuds.controller.util.PermissionHelper.isBatteryOptimizationIgnored(ctx)) {
                    com.freebuds.controller.util.PermissionHelper.showToastAndOpenBatteryOptimizationSettings(ctx)
                }
            }
            reconnectJob = null
        }
    }

    private fun computeBackoffDelay(attempt: Int): Long {
        // 1s, 2s, 4s, 8s, 16s, 32s, 60s, 60s, ...
        val exp = (1L shl (attempt - 1)) * RECONNECT_BASE_DELAY_MS
        return min(exp, RECONNECT_MAX_DELAY_MS)
    }

    private fun handleEvent(event: ConnectionEvent, device: BluetoothDevice) {
        when (event) {
            is ConnectionEvent.Connected -> {
                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempt = 0
                _state.value = _state.value.copy(
                    connected = true,
                    lastDeviceName = device.name ?: "Unknown",
                    lastDeviceAddress = device.address
                )
                DebugLogger.i(TAG, "✅ Reconnected! Initializing ${handlers.size} handlers...")
                val c = client ?: return
                scope.launch {
                    // Phase 1: Register event handlers
                    for (handler in handlers) {
                        try { handler.init(c) } catch (e: Exception) {
                            DebugLogger.e(TAG, "Handler ${handler::class.simpleName}.init() failed", e)
                        }
                    }
                    // Phase 2: Actively query initial state (upstream on_init pattern)
                    var initialState = _state.value.copy(connected = true)
                    for (handler in handlers) {
                        var success = false
                        for (attempt in 1..3) {
                            try {
                                val delta = withTimeout(2000) { handler.onInit(c, initialState) }
                                if (delta != null) {
                                    initialState = delta
                                    success = true
                                }
                                break
                            } catch (e: Exception) {
                                DebugLogger.w(TAG, "Handler ${handler.id} onInit attempt $attempt/3 failed: ${e.message}")
                            }
                        }
                        if (!success) {
                            DebugLogger.w(TAG, "Handler ${handler.id} onInit: all attempts failed")
                        }
                    }
                    _state.value = initialState
                    refreshState()
                }
            }
            is ConnectionEvent.Disconnected -> {
                _connectionState.value = ConnectionState.DISCONNECTED
                _state.value = _state.value.copy(connected = false)
                DebugLogger.w(TAG, "Reconnect attempt disconnected: ${event.reason}")
            }
            is ConnectionEvent.HeartbeatLost -> { }
            is ConnectionEvent.Error -> {
                DebugLogger.e(TAG, "Reconnect error: ${event.error.message}")
            }
            is ConnectionEvent.PackageReceived -> {
                refreshState()
            }
        }
    }

    /**
     * Re-read all handler states from device and update [_state].
     * Each handler read is wrapped in a per-handler try-catch with a
     * configurable timeout, so a single slow handler doesn't block the
     * entire refresh (and consequently the UI).
     */
    private fun refreshState() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val c = client ?: return@launch
            var s = _state.value
            for (handler in handlers) {
                try {
                    s = withTimeout(2000) { handler.applyToState(c, s) }
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "Handler ${handler.id} applyToState timeout/fail: ${e.message}")
                    // Keep previous state for this handler, continue with others
                }
            }
            _state.value = s.copy(connected = true)
        }
    }

    /**
     * Set a device property. Writes via handler, then reads back after a short delay.
     * Each handler now performs its own post-write re-read (matching upstream pattern:
     * write → on_init → re-parse). After all handlers are done, a unified [refreshState]
     * ensures all UI state is consistent.
     */
    suspend fun setProperty(prop: String, value: String) {
        val c = client ?: return
        for (handler in handlers) {
            handler.setProperty(c, prop, value)
        }
        // Short delay then full state sync via refreshState
        delay(300)
        refreshState()
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        client?.destroy()
        client = null
        refreshJob?.cancel()
        eventJob?.cancel()
        eventJob = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _state.value = _state.value.copy(
            connected = false,
            deviceName = "",
            deviceAddress = "",
            batteryLeft = null,
            batteryRight = null,
            batteryCase = null,
            ancMode = AncMode.OFF,
            lowLatency = false,
            soundQuality = "",
            eqPreset = "",
            eqPresetOptions = emptyList(),
            eqRows = emptyList(),
            autoPause = false,
            doubleTapLeft = "",
            doubleTapRight = "",
            longTapAction = "",
            swipeGesture = "",
            tripleTapLeft = "",
            tripleTapRight = "",
            dualConnectEnabled = false,
            dualConnectPreferred = "",
            firmwareVersion = "",
            serialNumber = "",
            hardwareVersion = ""
        )
    }

    fun resetState() {
        disconnect()
        _state.value = DeviceState()
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    suspend fun tryAutoConnect(lastAddress: String?) {
        if (autoConnectDone) return
        autoConnectDone = true
        if (lastAddress.isNullOrEmpty()) return
        val devices = findPairedDevices()
        val device = devices.find { it.address == lastAddress } ?: return
        try {
            connect(device)
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Auto-connect failed: ${e.message}")
        }
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
