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
}
