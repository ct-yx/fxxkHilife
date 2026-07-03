package com.freebuds.controller.data

import com.freebuds.controller.bluetooth.SppDriver

/**
 * Maps vendor/protocol property strings into the UI-facing DeviceProps model.
 *
 * The Huawei SPP stack still stores raw OpenFreebuds-style group/prop strings,
 * but repository/UI code should consume this typed snapshot instead of knowing
 * every protocol key.
 */
object EarbudStateMapper {
    suspend fun fromHuaweiDriver(
        driver: SppDriver,
        failedHandlers: Collection<String>,
        connectedSince: Long?,
    ): DeviceProps {
        suspend fun get(group: String, prop: String) = driver.getProperty(group, prop)
        suspend fun opts(group: String, prop: String) =
            get(group, prop)?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

        return DeviceProps(
            batteryGlobal = get("battery", "global")?.toIntOrNull(),
            batteryLeft = get("battery", "left")?.toIntOrNull(),
            batteryRight = get("battery", "right")?.toIntOrNull(),
            batteryCase = get("battery", "case")?.toIntOrNull(),
            isCharging = get("battery", "is_charging")?.toBooleanStrictOrNull(),
            ancMode = get("anc", "mode"),
            ancModeOptions = opts("anc", "mode_options"),
            ancLevel = get("anc", "level"),
            ancLevelOptions = opts("anc", "level_options"),
            autoPause = get("config", "auto_pause")?.toBooleanStrictOrNull(),
            lowLatency = get("config", "low_latency")?.toBooleanStrictOrNull(),
            soundQuality = get("sound", "quality_preference"),
            soundQualityOptions = opts("sound", "quality_preference_options"),
            equalizerPreset = get("sound", "equalizer_preset"),
            equalizerPresetOptions = opts("sound", "equalizer_preset_options"),
            equalizerPresetCreateOptions = opts("sound", "equalizer_preset_create_options"),
            equalizerRows = opts("sound", "equalizer_rows").mapNotNull { it.toIntOrNull() },
            equalizerSaved = get("sound", "equalizer_saved")?.toBooleanStrictOrNull(),
            equalizerMaxCustomModes = get("sound", "equalizer_max_custom_modes")?.toIntOrNull() ?: 0,
            dualConnectEnabled = get("dual_connect", "enabled")?.toBooleanStrictOrNull(),
            dualConnectDevices = parseDualConnectDevices(get("dual_connect", "devices")),
            dualConnectPreferredDevice = get("dual_connect", "preferred_device")?.takeIf { it.isNotBlank() },
            doubleTapLeft = get("action", "double_tap_left"),
            doubleTapRight = get("action", "double_tap_right"),
            doubleTapOptions = opts("action", "double_tap_options"),
            tripleTapLeft = get("action", "triple_tap_left"),
            tripleTapRight = get("action", "triple_tap_right"),
            tripleTapOptions = opts("action", "triple_tap_options"),
            longTap = get("action", "long_tap"),
            longTapOptions = opts("action", "long_tap_options"),
            swipeGesture = get("action", "swipe_gesture"),
            swipeGestureOptions = opts("action", "swipe_gesture_options"),
            inEar = get("state", "in_ear")?.toBooleanStrictOrNull(),
            deviceModel = get("info", "device_model"),
            firmwareVersion = get("info", "software_ver"),
            pendingInitHandlers = failedHandlers.toList(),
            connectedSince = connectedSince,
        )
    }

    private fun parseDualConnectDevices(raw: String?): List<DualConnectDevice> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("|").mapNotNull { row ->
            val parts = row.split(";")
            if (parts.size < 6) return@mapNotNull null
            DualConnectDevice(
                address = parts[0],
                name = parts[1].decodeListValue().ifBlank { parts[0] },
                autoConnect = parts[2].takeIf { it.isNotBlank() }?.toBooleanStrictOrNull(),
                preferred = parts[3].toBooleanStrictOrNull() ?: false,
                connected = parts[4].toBooleanStrictOrNull() ?: false,
                playing = parts[5].toBooleanStrictOrNull() ?: false,
            )
        }
    }

    private fun String.decodeListValue(): String =
        replace("%7C", "|").replace("%3B", ";").replace("%25", "%")
}
