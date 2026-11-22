package com.freebuds.controller.data

/**
 * Semantic device command API shared by Flutter, the foreground service and system tile.
 *
 * Protocol group/property names stay inside this adapter. Callers describe intent such as
 * [setAncMode] or [setLowLatency] and do not depend on the vendor property namespace.
 */
class DeviceControlApi(private val repository: DeviceRepository) {
    suspend fun setAncMode(value: String) = repository.setProperty("anc", "mode", value)

    suspend fun setAncLevel(value: String) = repository.setProperty("anc", "level", value)

    suspend fun setSoundQuality(value: String) =
        repository.setProperty("sound", "quality_preference", value)

    suspend fun setEqualizerPreset(value: String) =
        repository.setProperty("sound", "equalizer_preset", value)

    suspend fun setAutoPause(enabled: Boolean) =
        repository.setProperty("config", "auto_pause", enabled.toString())

    suspend fun setLowLatency(enabled: Boolean) =
        repository.setProperty("config", "low_latency", enabled.toString())

    suspend fun setDualConnect(enabled: Boolean) =
        repository.setProperty("dual_connect", "enabled", enabled.toString())

    suspend fun setPreferredDevice(address: String) =
        repository.setProperty("dual_connect", "preferred_device", address)

    suspend fun setDualDeviceOption(address: String, option: DualDeviceOption, enabled: Boolean) {
        repository.setProperty("dual_connect", "$address:${option.key}", enabled.toString())
    }

    suspend fun setGesture(target: GestureTarget, value: String) =
        repository.setProperty("action", target.key, value)

    enum class DualDeviceOption(val key: String) {
        CONNECTED("connected"),
        AUTO_CONNECT("auto_connect"),
        ;

        companion object {
            fun fromApi(value: String): DualDeviceOption = when (value) {
                "connected" -> CONNECTED
                "auto_connect" -> AUTO_CONNECT
                else -> throw IllegalArgumentException("Unsupported dual-device option: $value")
            }
        }
    }

    enum class GestureTarget(val key: String) {
        DOUBLE_TAP_LEFT("double_tap_left"),
        DOUBLE_TAP_RIGHT("double_tap_right"),
        TRIPLE_TAP_LEFT("triple_tap_left"),
        TRIPLE_TAP_RIGHT("triple_tap_right"),
        SWIPE("swipe_gesture"),
        LONG_TAP("long_tap"),
        ;

        companion object {
            fun fromApi(value: String): GestureTarget = entries.firstOrNull { it.key == value }
                ?: throw IllegalArgumentException("Unsupported gesture target: $value")
        }
    }
}
