package com.freebuds.controller.device

data class DeviceState(
    val connected: Boolean = false,
    val deviceName: String = "",
    val deviceAddress: String = "",
    // Persist last connected device info across disconnect
    val lastDeviceName: String? = null,
    val lastDeviceAddress: String? = null,
    val batteryLeft: Int? = null,
    val batteryRight: Int? = null,
    val batteryCase: Int? = null,
    val ancMode: AncMode = AncMode.OFF,
    val lowLatency: Boolean = false,
    val soundQuality: String = "",
    val eqPreset: String = "",
    val eqPresetOptions: List<String> = emptyList(),
    val eqRows: List<Int> = emptyList(),
    val autoPause: Boolean = false,
    val doubleTapLeft: String = "",
    val doubleTapRight: String = "",
    val longTapAction: String = "",
    val swipeGesture: String = "",
    val dualConnectEnabled: Boolean = false,
    val dualConnectPreferred: String = "",
    val firmwareVersion: String = "",
    val serialNumber: String = "",
    val hardwareVersion: String = ""
)

enum class AncMode(val displayName: String) {
    OFF("Off"),
    CANCELLATION("Noise Cancellation"),
    AWARENESS("Awareness"),
    UNKNOWN("Unknown")
}
