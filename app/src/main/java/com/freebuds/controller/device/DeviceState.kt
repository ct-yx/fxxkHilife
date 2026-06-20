package com.freebuds.controller.device

data class DeviceState(
    val connected: Boolean = false,
    val deviceName: String = "",
    val deviceAddress: String = "",
    // Persist last connected device info across disconnect
    val lastDeviceName: String? = null,
    val lastDeviceAddress: String? = null,

    // === Battery ===
    val batteryLeft: Int? = null,
    val batteryRight: Int? = null,
    val batteryCase: Int? = null,
    val batteryChargingLeft: Boolean = false,
    val batteryChargingRight: Boolean = false,
    val batteryChargingCase: Boolean = false,

    // === ANC ===
    val ancMode: AncMode = AncMode.OFF,
    val ancLevel: String = "",
    val ancLevelOptions: List<String> = emptyList(),

    // === Sound ===
    val lowLatency: Boolean = false,
    val soundQuality: String = "",

    // === EQ ===
    val eqPreset: String = "",
    val eqPresetOptions: List<String> = emptyList(),
    val eqRows: List<Int> = emptyList(),

    // === Auto-pause ===
    val autoPause: Boolean = false,

    // === Gestures ===
    val doubleTapLeft: String = "",
    val doubleTapRight: String = "",
    val tripleTapLeft: String = "",
    val tripleTapRight: String = "",
    val longTapAction: String = "",
    val swipeGesture: String = "",

    // === Dual Connect ===
    val dualConnectEnabled: Boolean = false,
    val dualConnectPreferred: String = "",

    // === Device Info ===
    val firmwareVersion: String = "",
    val serialNumber: String = "",
    val hardwareVersion: String = "",

    // === In-ear state ===
    val earWorn: Boolean = false,

    // === Power button ===
    val powerButtonAction: String = "",

    // === Voice language ===
    val voiceLanguage: String = "",
    val voiceLanguageOptions: List<String> = emptyList(),

    // === Connection info ===
    val connectionInfo: String = ""
)

enum class AncMode(val displayName: String) {
    OFF("Off"),
    CANCELLATION("Noise Cancellation"),
    AWARENESS("Awareness"),
    UNKNOWN("Unknown")
}
