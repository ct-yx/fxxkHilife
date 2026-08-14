package com.freebuds.controller.core.state

data class EarbudBatteryState(
    val global: Int? = null,
    val left: Int? = null,
    val right: Int? = null,
    val case: Int? = null,
    val isCharging: Boolean? = null,
)

data class EarbudAncState(
    val mode: String? = null,
    val modeOptions: List<String> = emptyList(),
    val level: String? = null,
    val levelOptions: List<String> = emptyList(),
)

data class EarbudAudioState(
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
    val voiceLanguage: String? = null,
    val voiceLanguageOptions: List<String> = emptyList(),
)

data class EarbudDualConnectState(
    val enabled: Boolean? = null,
    val devices: List<EarbudDualConnectDevice> = emptyList(),
    val preferredDevice: String? = null,
)

data class EarbudDualConnectDevice(
    val address: String,
    val name: String,
    val autoConnect: Boolean?,
    val preferred: Boolean,
    val connected: Boolean,
    val playing: Boolean,
)

data class EarbudGestureState(
    val doubleTapLeft: String? = null,
    val doubleTapRight: String? = null,
    val doubleTapOptions: List<String> = emptyList(),
    val tripleTapLeft: String? = null,
    val tripleTapRight: String? = null,
    val tripleTapOptions: List<String> = emptyList(),
    val doubleTapInCall: String? = null,
    val doubleTapInCallOptions: List<String> = emptyList(),
    val tripleTapInCall: String? = null,
    val tripleTapInCallOptions: List<String> = emptyList(),
    val longTap: String? = null,
    val longTapOptions: List<String> = emptyList(),
    val powerButton: String? = null,
    val powerButtonOptions: List<String> = emptyList(),
    val swipeGesture: String? = null,
    val swipeGestureOptions: List<String> = emptyList(),
)

data class EarbudWearingState(
    val inEar: Boolean? = null,
)

data class EarbudDeviceInfo(
    val model: String? = null,
    val firmwareVersion: String? = null,
)

/** Vendor-neutral snapshot; raw Huawei property names do not cross this boundary. */
data class EarbudState(
    val battery: EarbudBatteryState = EarbudBatteryState(),
    val anc: EarbudAncState = EarbudAncState(),
    val audio: EarbudAudioState = EarbudAudioState(),
    val dualConnect: EarbudDualConnectState = EarbudDualConnectState(),
    val gestures: EarbudGestureState = EarbudGestureState(),
    val wearing: EarbudWearingState = EarbudWearingState(),
    val deviceInfo: EarbudDeviceInfo = EarbudDeviceInfo(),
    val pendingInitHandlers: Set<String> = emptySet(),
    val connectedSince: Long? = null,
)
