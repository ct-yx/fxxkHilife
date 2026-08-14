package com.freebuds.controller.data

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freebuds.controller.HilifeApplication
import com.freebuds.controller.bluetooth.BluetoothScanner
import com.freebuds.controller.bluetooth.ScannedDevice
import com.freebuds.controller.core.capability.EarbudCapability
import com.freebuds.controller.core.state.EarbudState
import com.freebuds.controller.ui.state.AppUiState
import com.freebuds.controller.ui.state.DeviceEvent
import com.freebuds.controller.ui.state.DeviceUiState
import com.freebuds.controller.ui.state.DualDeviceProperty
import com.freebuds.controller.ui.state.GestureTarget
import com.freebuds.controller.ui.state.ScanUiState
import com.freebuds.controller.ui.state.SettingsEvent
import com.freebuds.controller.ui.state.SettingsUiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ScanState(
    val isScanning: Boolean = false,
    val devices: List<ScannedDevice> = emptyList(),
)

class DeviceViewModel : ViewModel() {

    private val repo = HilifeApplication.instance.deviceRepository
    private val connectionManager = repo.connectionManager
    /** One settings owner for Activity, routes and update tasks. */
    val settingsRepository = SettingsRepository(HilifeApplication.instance)
    private val updateRepository = UpdateRepository(HilifeApplication.instance, settingsRepository)

    // ── 透传 Repository 的 Flow ───────────────────────────────────────────────
    val connectionState: StateFlow<ConnectionState> = repo.connectionState
    val controlChannelState: StateFlow<ControlChannelState> = repo.controlChannelState
    val earbudState: StateFlow<EarbudState> = repo.earbudState
    val capabilities: StateFlow<Set<EarbudCapability>> = repo.capabilities
    val props: StateFlow<DeviceProps> = repo.props
    val listeningStats: StateFlow<ListeningStats> = repo.listeningStats
    val savedDeviceConnections: StateFlow<List<SavedDeviceConnection>> = repo.savedDeviceConnections
    val updateState: StateFlow<UpdateUiState> = updateRepository.state
    val settingsState: StateFlow<SettingsSnapshot> = settingsRepository.state

    fun isCoreStateReady(): Boolean = repo.isCoreStateReady()

    // ── 扫描状态（只在 ViewModel 层维护，与连接无关） ─────────────────────────
    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    /** Typed projection used by routes; legacy props remains available during migration. */
    val deviceUiState: StateFlow<DeviceUiState> = combine(
        controlChannelState,
        earbudState,
        capabilities,
    ) { channel, state, capabilitySet ->
        DeviceUiState(
            connection = com.freebuds.controller.ui.state.ConnectionSummary.from(channel),
            state = state,
            capabilities = capabilitySet,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DeviceUiState())

    private val baseAppUiState: Flow<AppUiState> = combine(
        deviceUiState,
        scanState,
        savedDeviceConnections,
        listeningStats,
    ) { device, scan, saved, stats ->
        AppUiState(
            device = device,
            scan = ScanUiState(scan.isScanning, scan.devices),
            savedDevices = saved,
            listeningStats = stats,
        )
    }

    val appUiState: StateFlow<AppUiState> = combine(
        baseAppUiState,
        settingsState,
        updateState,
    ) { base, settings, update ->
        base.copy(
            settings = SettingsUiState(settings),
            update = update,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    private var scanner: BluetoothScanner? = null

    // ── 连接操作 ──────────────────────────────────────────────────────────────
    fun connect(device: BluetoothDevice): String? =
        (connectionManager.submit(ConnectionCommand.Connect(device)) as ConnectionCommandResult.Attempt).attemptId

    fun disconnect() {
        connectionManager.submit(ConnectionCommand.Disconnect)
    }

    // ── 属性写入 ──────────────────────────────────────────────────────────────
    fun setProperty(group: String, prop: String, value: String) {
        viewModelScope.launch { repo.setProperty(group, prop, value) }
    }

    /** Typed UI command boundary. Raw protocol keys stay inside this compatibility mapping. */
    fun onEvent(event: DeviceEvent) {
        when (event) {
            is DeviceEvent.SetAncMode -> setProperty("anc", "mode", event.value)
            is DeviceEvent.SetAncLevel -> setProperty("anc", "level", event.value)
            is DeviceEvent.SetSoundQuality -> setProperty("sound", "quality_preference", event.value)
            is DeviceEvent.SetEqualizerPreset -> setProperty("sound", "equalizer_preset", event.value)
            is DeviceEvent.SetAutoPause -> setProperty("config", "auto_pause", event.enabled.toString())
            is DeviceEvent.SetLowLatency -> setProperty("config", "low_latency", event.enabled.toString())
            is DeviceEvent.SetDualConnect -> setProperty("dual_connect", "enabled", event.enabled.toString())
            is DeviceEvent.SetPreferredDevice -> setProperty("dual_connect", "preferred_device", event.address)
            is DeviceEvent.SetDualDeviceOption -> setProperty(
                "dual_connect",
                "${event.address}:${event.property.name.lowercase()}",
                event.enabled.toString(),
            )
            is DeviceEvent.SetGesture -> setProperty(
                "action",
                when (event.target) {
                    GestureTarget.DoubleTapLeft -> "double_tap_left"
                    GestureTarget.DoubleTapRight -> "double_tap_right"
                    GestureTarget.TripleTapLeft -> "triple_tap_left"
                    GestureTarget.TripleTapRight -> "triple_tap_right"
                    GestureTarget.Swipe -> "swipe_gesture"
                    GestureTarget.LongTap -> "long_tap"
                },
                event.value,
            )
            DeviceEvent.Refresh -> refreshSavedDeviceConnections()
        }
    }

    fun checkForUpdate(force: Boolean = false) = updateRepository.check(force)
    fun downloadUpdate() = updateRepository.download()
    fun cancelUpdateDownload() = updateRepository.cancelDownload()
    fun installUpdate(): android.content.Intent? = updateRepository.install()
    fun maybeCheckForUpdate() = updateRepository.check(force = false)

    /** Typed settings boundary used by the Settings route. */
    fun onSettingsEvent(event: SettingsEvent): android.content.Intent? {
        when (event) {
            is SettingsEvent.SetThemeMode -> settingsRepository.setThemeMode(event.value)
            is SettingsEvent.SetLocale -> settingsRepository.setLocale(event.value)
            is SettingsEvent.SetDisplayMode -> settingsRepository.setDisplayMode(event.value)
            is SettingsEvent.SetWallpaperUri -> settingsRepository.setWallpaperUri(event.value)
            is SettingsEvent.SetWallpaperScope -> settingsRepository.setWallpaperScope(event.value)
            is SettingsEvent.SetGlassConfig -> settingsRepository.setGlassConfig(event.value)
            is SettingsEvent.SetAutoLowLatency -> settingsRepository.setAutoLowLatency(event.value)
            is SettingsEvent.SetUpdateSettings -> settingsRepository.setUpdateSettings(event.value)
            is SettingsEvent.SetLogMaxLines -> settingsRepository.setLogMaxLines(event.value)
            is SettingsEvent.SetProtocolFrameLogging -> settingsRepository.setProtocolFrameLogging(event.value)
            is SettingsEvent.CheckForUpdate -> checkForUpdate(force = event.force)
            SettingsEvent.DownloadUpdate -> downloadUpdate()
            SettingsEvent.CancelUpdateDownload -> cancelUpdateDownload()
            SettingsEvent.InstallUpdate -> return installUpdate()
            is SettingsEvent.OpenRelease -> Unit
        }
        return null
    }

    // ── 分享日志 ──────────────────────────────────────────────────────────────
    fun shareLog(context: Context) = repo.shareLog(context)

    // ── 已保存设备地址 ────────────────────────────────────────────────────────
    fun getSavedAddresses(): List<String> = repo.getSavedAddresses()
    fun getSavedAddress(): String? = repo.getSavedAddress()
    fun removeSavedDevice(address: String) = repo.removeSavedDevice(address)
    fun refreshSavedDeviceConnections() {
        connectionManager.submit(ConnectionCommand.RefreshSavedDeviceConnections)
    }

    // ── 前后台感知 ────────────────────────────────────────────────────────────
    fun setAppInForeground(foreground: Boolean) = repo.setAppInForeground(foreground)

    // ── 自动连接已保存设备 ────────────────────────────────────────────────────
    fun autoConnectSaved(address: String): Boolean =
        (connectionManager.submit(ConnectionCommand.AutoConnectSaved(address)) as ConnectionCommandResult.Accepted).value

    fun autoConnectLast(context: Context): Boolean =
        (connectionManager.submit(ConnectionCommand.AutoConnectLastSaved()) as ConnectionCommandResult.Accepted).value

    // ── 扫描 ──────────────────────────────────────────────────────────────────
    fun startScan(context: Context) {
        scanner?.stopScan()
        _scanState.value = ScanState(isScanning = true)
        scanner = BluetoothScanner(context).also { s ->
            s.startScan { success ->
                _scanState.value = ScanState(
                    isScanning = false,
                    devices = if (success) s.found.toList() else emptyList()
                )
                // 扫描完成后，尝试自动连接华为设备
                val huawei = s.found.firstOrNull { it.isHuaweiOrHonor }
                if (huawei != null && repo.connectionState.value !is ConnectionState.Connected) {
                    connectionManager.submit(
                        ConnectionCommand.Connect(huawei.device, ConnectionTrigger.ScanCompleted)
                    )
                }
            }
            // 立即同步已配对设备
            _scanState.value = _scanState.value.copy(devices = s.found.toList())
        }
    }

    fun stopScan() {
        scanner?.stopScan()
        scanner = null
        _scanState.value = _scanState.value.copy(isScanning = false)
    }

    override fun onCleared() {
        stopScan()
        updateRepository.close()
        super.onCleared()
    }
}
