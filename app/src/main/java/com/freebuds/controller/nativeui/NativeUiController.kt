package com.freebuds.controller.nativeui

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import com.freebuds.controller.HilifeApplication
import com.freebuds.controller.bluetooth.BluetoothScanner
import com.freebuds.controller.bluetooth.ScannedDevice
import com.freebuds.controller.core.capability.EarbudCapability
import com.freebuds.controller.core.state.EarbudState
import com.freebuds.controller.data.ConnectionCommand
import com.freebuds.controller.data.ConnectionCommandResult
import com.freebuds.controller.data.ControlChannelState
import com.freebuds.controller.data.DeviceControlApi
import com.freebuds.controller.data.DeviceRepository
import com.freebuds.controller.data.EarbudConnectionManager
import com.freebuds.controller.data.SettingsRepository
import com.freebuds.controller.data.SettingsSnapshot
import com.freebuds.controller.data.ThemeMode
import com.freebuds.controller.data.UpdateManifest
import com.freebuds.controller.data.UpdateRepository
import com.freebuds.controller.data.UpdateUiState
import com.freebuds.controller.data.WallpaperScope
import com.freebuds.controller.i18n.I18n
import com.freebuds.controller.i18n.I18nLocale
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Native application API consumed by the Flutter presentation layer.
 *
 * This class deliberately has no Android view or Flutter channel dependency. It is a
 * typed façade over the existing device and connection runtime, which keeps the protocol mapping
 * in one place while allowing the UI to be replaced independently.
 */
class NativeUiController(private val context: Context) {
    private val appContext = context.applicationContext
    private val repository: DeviceRepository = HilifeApplication.instance.deviceRepository
    private val connectionManager: EarbudConnectionManager = repository.connectionManager
    private val controls = DeviceControlApi(repository)
    private val settingsRepository = HilifeApplication.instance.settingsRepository
    private val updateRepository = UpdateRepository(appContext, settingsRepository)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _scan = MutableStateFlow(NativeScanSnapshot())
    private val _state = MutableStateFlow(buildSnapshot())
    val state: StateFlow<NativeUiSnapshot> = _state.asStateFlow()

    private var scanner: BluetoothScanner? = null
    private val observers = mutableListOf<Job>()

    init {
        observe(repository.controlChannelState)
        observe(repository.earbudState)
        observe(repository.capabilities)
        observe(repository.savedDeviceConnections)
        observe(repository.listeningStats)
        observe(settingsRepository.state)
        observe(updateRepository.state)
        observe(_scan)
    }

    private fun observe(flow: StateFlow<*>) {
        observers += scope.launch {
            flow.collect { publishSnapshot() }
        }
    }

    private fun publishSnapshot() {
        _state.value = buildSnapshot()
    }

    private fun buildSnapshot(): NativeUiSnapshot = NativeUiSnapshot(
        connection = NativeConnectionSnapshot.from(repository.controlChannelState.value),
        device = NativeDeviceSnapshot.from(
            state = repository.earbudState.value,
            capabilities = repository.capabilities.value,
        ),
        scan = _scan.value,
        savedDevices = repository.savedDeviceConnections.value.map(NativeSavedDeviceSnapshot::from),
        listening = NativeListeningSnapshot(
            totalMs = repository.listeningStats.value.totalMs,
            todayMs = repository.listeningStats.value.todayMs,
            activeDays = repository.listeningStats.value.activeDays,
            streakDays = repository.listeningStats.value.streakDays,
        ),
        settings = NativeSettingsSnapshot.from(settingsRepository.state.value),
        update = NativeUpdateSnapshot.from(updateRepository.state.value),
    )

    fun connect(address: String): String? {
        val device = runCatching { BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address) }
            .getOrNull()
            ?: return null
        return when (val result = connectionManager.submit(ConnectionCommand.Connect(device))) {
            is ConnectionCommandResult.Attempt -> result.attemptId
            else -> null
        }
    }

    fun disconnect() {
        connectionManager.submit(ConnectionCommand.Disconnect)
    }

    fun refreshSavedDevices() {
        connectionManager.submit(ConnectionCommand.RefreshSavedDeviceConnections)
    }

    fun startScan() {
        scanner?.stopScan()
        val nextScanner = BluetoothScanner(appContext)
        scanner = nextScanner
        _scan.value = NativeScanSnapshot(isScanning = true)
        nextScanner.startScan { success ->
            _scan.value = NativeScanSnapshot(
                isScanning = false,
                devices = if (success) nextScanner.found.toList().map(NativeScanDeviceSnapshot::from)
                else emptyList(),
            )
        }
        _scan.value = _scan.value.copy(
            devices = nextScanner.found.toList().map(NativeScanDeviceSnapshot::from),
        )
    }

    fun stopScan() {
        scanner?.stopScan()
        scanner = null
        _scan.value = _scan.value.copy(isScanning = false)
    }

    fun setAncMode(value: String) = scope.launch { controls.setAncMode(value) }

    fun setAncLevel(value: String) = scope.launch { controls.setAncLevel(value) }

    fun setSoundQuality(value: String) = scope.launch { controls.setSoundQuality(value) }

    fun setEqualizerPreset(value: String) = scope.launch { controls.setEqualizerPreset(value) }

    fun setAutoPause(enabled: Boolean) = scope.launch { controls.setAutoPause(enabled) }

    fun setLowLatency(enabled: Boolean) = scope.launch { controls.setLowLatency(enabled) }

    fun setDualConnect(enabled: Boolean) = scope.launch { controls.setDualConnect(enabled) }

    fun setPreferredDevice(address: String) = scope.launch { controls.setPreferredDevice(address) }

    fun setDualDeviceOption(address: String, option: String, enabled: Boolean) {
        val target = DeviceControlApi.DualDeviceOption.fromApi(option)
        scope.launch { controls.setDualDeviceOption(address, target, enabled) }
    }

    fun setGesture(target: String, value: String) {
        val gesture = DeviceControlApi.GestureTarget.fromApi(target)
        scope.launch { controls.setGesture(gesture, value) }
    }

    fun setThemeMode(value: String) = settingsRepository.setThemeMode(ThemeMode.fromApi(value))

    fun setLocale(tag: String) {
        val locale = I18nLocale.fromTag(tag)
        I18n.setLocale(locale)
        settingsRepository.setLocale(locale)
    }

    fun setWallpaperUri(uri: String?) = settingsRepository.setWallpaperUri(uri)

    fun setWallpaperScope(value: String) =
        settingsRepository.setWallpaperScope(WallpaperScope.fromApi(value))

    fun setAutoLowLatency(value: Boolean) = settingsRepository.setAutoLowLatency(value)

    fun setLogMaxLines(value: Int) = settingsRepository.setLogMaxLines(value.coerceIn(100, 20_000))

    fun setProtocolFrameLogging(value: Boolean) = settingsRepository.setProtocolFrameLogging(value)

    fun setUpdatePreferences(autoCheck: Boolean, autoDownload: Boolean) {
        val current = settingsRepository.updateSettings()
        settingsRepository.setUpdateSettings(
            current.copy(
                autoCheckEnabled = autoCheck,
                autoDownloadEnabled = autoDownload,
            ),
        )
    }

    fun checkForUpdate(force: Boolean) = updateRepository.check(force)

    fun downloadUpdate() = updateRepository.download()

    fun cancelUpdateDownload() = updateRepository.cancelDownload()

    fun installUpdate(): Intent? = updateRepository.install()

    fun shareLog() = repository.shareLog(appContext, I18n.t("settings.share_log_chooser"))

    fun logs(): String = LogBuffer.getSnapshotText(maxEntries = 1_200)

    fun clearLogs() = LogBuffer.clear()

    fun setAppInForeground(foreground: Boolean) {
        repository.setAppInForeground(foreground)
        if (foreground) updateRepository.check(force = false)
    }

    fun close() {
        stopScan()
        observers.forEach(Job::cancel)
        observers.clear()
        updateRepository.close()
        scope.cancel()
    }

}

data class NativeUiSnapshot(
    val connection: NativeConnectionSnapshot,
    val device: NativeDeviceSnapshot,
    val scan: NativeScanSnapshot,
    val savedDevices: List<NativeSavedDeviceSnapshot>,
    val listening: NativeListeningSnapshot,
    val settings: NativeSettingsSnapshot,
    val update: NativeUpdateSnapshot,
)

data class NativeConnectionSnapshot(
    val systemLink: String,
    val stage: String,
    val deviceName: String?,
    val address: String?,
    val attemptId: String?,
    val endpoint: String?,
    val pendingHandlers: List<String>,
    val failedHandlers: List<String>,
    val reason: String?,
) {
    companion object {
        fun from(value: ControlChannelState) = NativeConnectionSnapshot(
            systemLink = value.systemLink.name.lowercase(),
            stage = value.stage.name.lowercase(),
            deviceName = value.deviceName,
            address = value.address,
            attemptId = value.attemptId,
            endpoint = value.endpoint,
            pendingHandlers = value.pendingHandlers.sorted(),
            failedHandlers = value.failedHandlers.sorted(),
            reason = value.reason,
        )
    }
}

data class NativeDeviceSnapshot(
    val batteryGlobal: Int?,
    val batteryLeft: Int?,
    val batteryRight: Int?,
    val batteryCase: Int?,
    val charging: Boolean?,
    val ancMode: String?,
    val ancModeOptions: List<String>,
    val ancLevel: String?,
    val ancLevelOptions: List<String>,
    val autoPause: Boolean?,
    val lowLatency: Boolean?,
    val soundQuality: String?,
    val soundQualityOptions: List<String>,
    val equalizerPreset: String?,
    val equalizerPresetOptions: List<String>,
    val dualConnectEnabled: Boolean?,
    val dualConnectDevices: List<NativeDualDeviceSnapshot>,
    val preferredDevice: String?,
    val doubleTapLeft: String?,
    val doubleTapRight: String?,
    val tripleTapLeft: String?,
    val tripleTapRight: String?,
    val swipeGesture: String?,
    val longTap: String?,
    val gestureOptions: List<String>,
    val inEar: Boolean?,
    val model: String?,
    val firmwareVersion: String?,
    val voiceLanguage: String?,
    val voiceLanguageOptions: List<String>,
    val pendingInitHandlers: List<String>,
    val capabilities: List<String>,
) {
    companion object {
        fun from(state: EarbudState, capabilities: Set<EarbudCapability>) = NativeDeviceSnapshot(
            batteryGlobal = state.battery.global,
            batteryLeft = state.battery.left,
            batteryRight = state.battery.right,
            batteryCase = state.battery.case,
            charging = state.battery.isCharging,
            ancMode = state.anc.mode,
            ancModeOptions = state.anc.modeOptions,
            ancLevel = state.anc.level,
            ancLevelOptions = state.anc.levelOptions,
            autoPause = state.audio.autoPause,
            lowLatency = state.audio.lowLatency,
            soundQuality = state.audio.soundQuality,
            soundQualityOptions = state.audio.soundQualityOptions,
            equalizerPreset = state.audio.equalizerPreset,
            equalizerPresetOptions = state.audio.equalizerPresetOptions,
            dualConnectEnabled = state.dualConnect.enabled,
            dualConnectDevices = state.dualConnect.devices.map(NativeDualDeviceSnapshot::from),
            preferredDevice = state.dualConnect.preferredDevice,
            doubleTapLeft = state.gestures.doubleTapLeft,
            doubleTapRight = state.gestures.doubleTapRight,
            tripleTapLeft = state.gestures.tripleTapLeft,
            tripleTapRight = state.gestures.tripleTapRight,
            swipeGesture = state.gestures.swipeGesture,
            longTap = state.gestures.longTap,
            gestureOptions = state.gestures.doubleTapOptions,
            inEar = state.wearing.inEar,
            model = state.deviceInfo.model,
            firmwareVersion = state.deviceInfo.firmwareVersion,
            voiceLanguage = state.audio.voiceLanguage,
            voiceLanguageOptions = state.audio.voiceLanguageOptions,
            pendingInitHandlers = state.pendingInitHandlers.sorted(),
            capabilities = capabilities.map(EarbudCapability::name).sorted(),
        )
    }
}

data class NativeDualDeviceSnapshot(
    val address: String,
    val name: String,
    val autoConnect: Boolean?,
    val preferred: Boolean,
    val connected: Boolean,
    val playing: Boolean,
) {
    companion object {
        fun from(value: com.freebuds.controller.core.state.EarbudDualConnectDevice) =
            NativeDualDeviceSnapshot(
                address = value.address,
                name = value.name,
                autoConnect = value.autoConnect,
                preferred = value.preferred,
                connected = value.connected,
                playing = value.playing,
            )
    }
}

data class NativeScanSnapshot(
    val isScanning: Boolean = false,
    val devices: List<NativeScanDeviceSnapshot> = emptyList(),
)

data class NativeScanDeviceSnapshot(
    val name: String,
    val address: String,
    val rssi: Int,
    val bonded: Boolean,
    val huaweiOrHonor: Boolean,
    val connected: Boolean,
) {
    companion object {
        fun from(value: ScannedDevice) = NativeScanDeviceSnapshot(
            name = value.displayName,
            address = value.address,
            rssi = value.rssi,
            bonded = value.isBonded,
            huaweiOrHonor = value.isHuaweiOrHonor,
            connected = value.isConnected,
        )
    }
}

data class NativeSavedDeviceSnapshot(
    val address: String,
    val name: String,
    val bonded: Boolean,
    val systemConnected: Boolean,
    val controlConnected: Boolean,
) {
    companion object {
        fun from(value: com.freebuds.controller.data.SavedDeviceConnection) = NativeSavedDeviceSnapshot(
            address = value.address,
            name = value.name,
            bonded = value.isBonded,
            systemConnected = value.isSystemConnected,
            controlConnected = value.isControlConnected,
        )
    }
}

data class NativeListeningSnapshot(
    val totalMs: Long,
    val todayMs: Long,
    val activeDays: Int,
    val streakDays: Int,
)

data class NativeSettingsSnapshot(
    val themeMode: String,
    val locale: String,
    val wallpaperUri: String?,
    val wallpaperScope: String,
    val autoLowLatency: Boolean,
    val logMaxLines: Int,
    val protocolFrameLogging: Boolean,
    val autoCheckUpdates: Boolean,
    val autoDownloadUpdates: Boolean,
) {
    companion object {
        fun from(value: SettingsSnapshot) = NativeSettingsSnapshot(
            themeMode = value.themeMode.name.lowercase(),
            locale = value.locale.tag,
            wallpaperUri = value.wallpaperUri,
            wallpaperScope = value.wallpaperScope.name.lowercase(),
            autoLowLatency = value.autoLowLatency,
            logMaxLines = value.logMaxLines,
            protocolFrameLogging = value.protocolFrameLogging,
            autoCheckUpdates = value.updateSettings.autoCheckEnabled,
            autoDownloadUpdates = value.updateSettings.autoDownloadEnabled,
        )
    }
}

data class NativeUpdateSnapshot(
    val kind: String,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val checkedAtMs: Long? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val reason: String? = null,
    val message: String? = null,
    val retryable: Boolean? = null,
) {
    companion object {
        fun from(value: UpdateUiState): NativeUpdateSnapshot = when (value) {
            UpdateUiState.Idle -> NativeUpdateSnapshot("idle")
            is UpdateUiState.Checking -> NativeUpdateSnapshot("checking", checkedAtMs = value.startedAtMs)
            is UpdateUiState.UpToDate -> NativeUpdateSnapshot(
                "up_to_date",
                versionName = value.currentVersionName,
                checkedAtMs = value.checkedAtMs,
            )
            is UpdateUiState.Available -> value.manifest.snapshot("available", value.checkedAtMs)
            is UpdateUiState.Downloading -> value.manifest.snapshot(
                "downloading",
                downloadedBytes = value.downloadedBytes,
                totalBytes = value.totalBytes,
            )
            is UpdateUiState.ReadyToInstall -> value.manifest.snapshot("ready_to_install")
            is UpdateUiState.Installing -> value.manifest.snapshot("installing")
            is UpdateUiState.Error -> NativeUpdateSnapshot(
                kind = "error",
                reason = value.reason.name.lowercase(),
                message = value.message,
                retryable = value.retryable,
            )
        }

        private fun UpdateManifest.snapshot(
            kind: String,
            checkedAtMs: Long? = null,
            downloadedBytes: Long? = null,
            totalBytes: Long? = null,
        ) = NativeUpdateSnapshot(
            kind = kind,
            versionName = versionName,
            versionCode = versionCode,
            checkedAtMs = checkedAtMs,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
        )
    }
}
