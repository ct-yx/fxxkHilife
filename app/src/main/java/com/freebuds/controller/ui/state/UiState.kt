package com.freebuds.controller.ui.state

import androidx.compose.runtime.Immutable
import com.freebuds.controller.bluetooth.ScannedDevice
import com.freebuds.controller.core.capability.EarbudCapability
import com.freebuds.controller.core.state.EarbudState
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.data.ControlChannelStage
import com.freebuds.controller.data.ControlChannelState
import com.freebuds.controller.data.ConnectionTrigger
import com.freebuds.controller.data.ListeningStats
import com.freebuds.controller.data.SavedDeviceConnection
import com.freebuds.controller.data.SystemLinkState
import com.freebuds.controller.data.SettingsSnapshot
import com.freebuds.controller.data.UpdateSettings
import com.freebuds.controller.data.UpdateUiState
import com.freebuds.controller.i18n.I18nLocale
import com.freebuds.controller.ui.theme.ThemeMode
import com.freebuds.controller.ui.WallpaperScope

@Immutable
data class ConnectionSummary(
    val systemConnected: Boolean = false,
    val stage: ControlChannelStage = ControlChannelStage.Idle,
    val deviceName: String? = null,
    val address: String? = null,
    val attemptId: String? = null,
    val trigger: ConnectionTrigger? = null,
    val pendingHandlers: Set<String> = emptySet(),
    val failedHandlers: Set<String> = emptySet(),
    val endpoint: String? = null,
    val reason: String? = null,
) {
    val isReady: Boolean get() = stage == ControlChannelStage.Ready || stage == ControlChannelStage.Degraded
    val stageLabel: String
        get() = when (stage) {
            ControlChannelStage.Idle -> "Idle"
            ControlChannelStage.ConnectingTransport -> "Connecting"
            ControlChannelStage.TransportReady -> "Transport ready"
            ControlChannelStage.Settling -> "Settling"
            ControlChannelStage.InitializingCore -> "Initializing"
            ControlChannelStage.CoreReady -> "Core ready"
            ControlChannelStage.InitializingDeferred -> "Syncing"
            ControlChannelStage.Ready -> "Ready"
            ControlChannelStage.Degraded -> "Degraded"
            ControlChannelStage.Disconnecting -> "Disconnecting"
            ControlChannelStage.Failed -> "Failed"
        }

    companion object {
        fun from(state: ControlChannelState): ConnectionSummary = ConnectionSummary(
            systemConnected = state.systemLink == SystemLinkState.Connected,
            stage = state.stage,
            deviceName = state.deviceName,
            address = state.address,
            attemptId = state.attemptId,
            trigger = state.trigger,
            pendingHandlers = state.pendingHandlers,
            failedHandlers = state.failedHandlers,
            endpoint = state.endpoint,
            reason = state.reason,
        )

        fun from(state: ConnectionState): ConnectionSummary = when (state) {
            ConnectionState.Disconnected -> ConnectionSummary()
            is ConnectionState.Connecting -> ConnectionSummary(
                stage = ControlChannelStage.ConnectingTransport,
                deviceName = state.deviceName,
                attemptId = state.attemptId,
            )
            is ConnectionState.Connected -> ConnectionSummary(
                systemConnected = true,
                stage = ControlChannelStage.Ready,
                deviceName = state.deviceName,
                attemptId = state.attemptId,
            )
            is ConnectionState.Failed -> ConnectionSummary(
                stage = ControlChannelStage.Failed,
                reason = state.reason,
                attemptId = state.attemptId,
            )
        }
    }
}

@Immutable
data class DeviceUiState(
    val connection: ConnectionSummary = ConnectionSummary(),
    val state: EarbudState = EarbudState(),
    val capabilities: Set<EarbudCapability> = emptySet(),
) {
    val hasDevice: Boolean get() = connection.deviceName != null || state.deviceInfo.model != null
}

@Immutable
data class AppUiState(
    val device: DeviceUiState = DeviceUiState(),
    val scan: ScanUiState = ScanUiState(),
    val savedDevices: List<SavedDeviceConnection> = emptyList(),
    val listeningStats: ListeningStats = ListeningStats(),
    val settings: SettingsUiState = SettingsUiState(),
    val update: UpdateUiState = UpdateUiState.Idle,
)

/** Settings are an application state boundary, not a collection of page-local callbacks. */
@Immutable
data class SettingsUiState(
    val snapshot: SettingsSnapshot = SettingsSnapshot(),
) {
    val themeMode: ThemeMode get() = snapshot.themeMode
    val locale: I18nLocale get() = snapshot.locale
    val wallpaperUri: String? get() = snapshot.wallpaperUri
    val wallpaperScope: WallpaperScope get() = snapshot.wallpaperScope
    val updateSettings: UpdateSettings get() = snapshot.updateSettings
}

@Immutable
data class ControlUiState(
    val connection: ConnectionSummary = ConnectionSummary(),
    val actionStates: Map<String, UiActionState> = emptyMap(),
)

enum class UiActionState { Idle, Disabled, Pending, Success, Failure }

@Immutable
data class ScanUiState(
    val isScanning: Boolean = false,
    val devices: List<ScannedDevice> = emptyList(),
)

sealed interface DeviceEvent {
    data class SetAncMode(val value: String) : DeviceEvent
    data class SetAncLevel(val value: String) : DeviceEvent
    data class SetSoundQuality(val value: String) : DeviceEvent
    data class SetEqualizerPreset(val value: String) : DeviceEvent
    data class SetAutoPause(val enabled: Boolean) : DeviceEvent
    data class SetLowLatency(val enabled: Boolean) : DeviceEvent
    data class SetDualConnect(val enabled: Boolean) : DeviceEvent
    data class SetPreferredDevice(val address: String) : DeviceEvent
    data class SetDualDeviceOption(val address: String, val property: DualDeviceProperty, val enabled: Boolean) : DeviceEvent
    data class SetGesture(val target: GestureTarget, val value: String) : DeviceEvent
    data object Refresh : DeviceEvent
}

sealed interface SettingsEvent {
    data class SetThemeMode(val value: ThemeMode) : SettingsEvent
    data class SetLocale(val value: I18nLocale) : SettingsEvent
    data class SetWallpaperUri(val value: String?) : SettingsEvent
    data class SetWallpaperScope(val value: WallpaperScope) : SettingsEvent
    data class SetAutoLowLatency(val value: Boolean) : SettingsEvent
    data class SetUpdateSettings(val value: UpdateSettings) : SettingsEvent
    data class SetLogMaxLines(val value: Int) : SettingsEvent
    data class SetProtocolFrameLogging(val value: Boolean) : SettingsEvent
    data class CheckForUpdate(val force: Boolean = false) : SettingsEvent
    data object DownloadUpdate : SettingsEvent
    data object CancelUpdateDownload : SettingsEvent
    data object InstallUpdate : SettingsEvent
    data class OpenRelease(val url: String?) : SettingsEvent
}

enum class DualDeviceProperty { Connected, AutoConnect }
enum class GestureTarget { DoubleTapLeft, DoubleTapRight, TripleTapLeft, TripleTapRight, Swipe, LongTap }

sealed interface NavigationEvent {
    data object OpenDevice : NavigationEvent
    data object OpenSettings : NavigationEvent
    data object OpenScan : NavigationEvent
    data object BackToHome : NavigationEvent
}

fun ConnectionSummary.isStableReady(): Boolean =
    stage == ControlChannelStage.Ready || stage == ControlChannelStage.Degraded
