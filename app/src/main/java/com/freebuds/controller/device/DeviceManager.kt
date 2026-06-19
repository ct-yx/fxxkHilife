package com.freebuds.controller.device

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.util.Log
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

class DeviceManager(private val bluetoothAdapter: BluetoothAdapter?) {

    companion object {
        const val TAG = "DeviceManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var client: SppClient? = null
    private var refreshJob: Job? = null
    private var eventJob: Job? = null
    private val connectMutex = Mutex()

    var lastConnectedDevice: BluetoothDevice? = null
    private var autoConnectDone = false

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
            // If already connected to same device, reset first — external disconnect may have killed socket
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

            // Collect events in a separate coroutine so it doesn't block c.connect() below
            eventJob = scope.launch {
                try {
                    c.events.collect { event ->
                        when (event) {
                            is ConnectionEvent.Connected -> {
                                _connectionState.value = ConnectionState.CONNECTED
                                _state.value = _state.value.copy(
                                    connected = true,
                                    lastDeviceName = device.name ?: "Unknown",
                                    lastDeviceAddress = device.address
                                )
                                DebugLogger.i(TAG, "✅ Connected! Initializing ${handlers.size} handlers...")
                                for (handler in handlers) {
                                    try {
                                        handler.init(c)
                                    } catch (e: Exception) {
                                        DebugLogger.e(TAG, "Handler ${handler::class.simpleName}.init() failed", e)
                                    }
                                }
                                refreshState()
                                // Auto-enable low latency if preference is on
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
                            }
                            is ConnectionEvent.PackageReceived -> {
                                refreshState()
                            }
                            is ConnectionEvent.Error -> {
                                _connectionState.value = ConnectionState.DISCONNECTED
                                _state.value = _state.value.copy(connected = false)
                                DebugLogger.e(TAG, "Connection error: ${event.error.message}", event.error)
                                // Do NOT throw — let the eventJob catch block handle cleanup
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

    private fun refreshState() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val c = client ?: return@launch
            var s = _state.value
            for (handler in handlers) {
                s = handler.applyToState(c, s)
            }
            _state.value = s.copy(connected = true)
        }
    }

    suspend fun setProperty(prop: String, value: String) {
        val c = client ?: return
        for (handler in handlers) {
            handler.setProperty(c, prop, value)
        }
        delay(300)
        refreshState()
    }

    fun disconnect() {
        client?.destroy()
        client = null
        refreshJob?.cancel()
        eventJob?.cancel()
        eventJob = null
        _connectionState.value = ConnectionState.DISCONNECTED
        // Keep lastDevice info, only clear live state
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

    /**
     * Called once on app start from MainActivity — safe to call before user interaction.
     */
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
