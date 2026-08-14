package com.freebuds.controller.data

import com.freebuds.controller.bluetooth.SppDriver
import com.freebuds.controller.core.state.EarbudAncState
import com.freebuds.controller.core.state.EarbudAudioState
import com.freebuds.controller.core.state.EarbudBatteryState
import com.freebuds.controller.core.state.EarbudDeviceInfo
import com.freebuds.controller.core.state.EarbudDualConnectDevice
import com.freebuds.controller.core.state.EarbudDualConnectState
import com.freebuds.controller.core.state.EarbudGestureState
import com.freebuds.controller.core.state.EarbudState
import com.freebuds.controller.core.state.EarbudWearingState
import com.freebuds.controller.core.capability.EarbudCapability

/**
 * Maps vendor/protocol property strings into the UI-facing DeviceProps model.
 *
 * The Huawei SPP stack still stores raw OpenFreebuds-style group/prop strings,
 * but repository/UI code should consume this typed snapshot instead of knowing
 * every protocol key.
 */
object EarbudStateMapper {
    /**
     * Core readiness is capability-aware.  Models without ANC or low-latency support must not
     * wait forever for fields that their adapter deliberately never registers.  An empty set is
     * the conservative unknown-model profile, so it is not considered ready.
     */
    fun isCoreStateReady(
        state: EarbudState,
        capabilities: Set<EarbudCapability>,
    ): Boolean = isCoreStateReady(EarbudSnapshot(state, toDeviceProps(state), capabilities))

    fun isCoreStateReady(snapshot: EarbudSnapshot): Boolean {
        val state = snapshot.state
        val capabilities = snapshot.capabilities
        // A model that only exposes metadata/diagnostics has no readable core control state yet.
        // Do not let DEVICE_INFO or LOGS alone make the connection look initialized.
        if (capabilities.intersect(CORE_CAPABILITIES).isEmpty()) return false

        val hasBattery = state.battery.global != null || state.battery.left != null ||
            state.battery.right != null || state.battery.case != null
        val batteryReady = EarbudCapability.BATTERY !in capabilities || hasBattery
        val ancReady = EarbudCapability.ANC !in capabilities || state.anc.mode != null
        val lowLatencyReady =
            EarbudCapability.LOW_LATENCY !in capabilities || state.audio.lowLatency != null
        return batteryReady && ancReady && lowLatencyReady
    }

    private val CORE_CAPABILITIES = setOf(
        EarbudCapability.BATTERY,
        EarbudCapability.ANC,
        EarbudCapability.LOW_LATENCY,
    )

    suspend fun fromHuaweiDriver(
        driver: SppDriver,
        pendingHandlers: Collection<String>,
        connectedSince: Long?,
    ): DeviceProps {
        suspend fun getText(group: String, prop: String): String? =
            driver.getProperty(group, prop)?.takeUnless { it.isBlank() }
        suspend fun opts(group: String, prop: String) =
            getText(group, prop)?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

        return DeviceProps(
            batteryGlobal = getText("battery", "global")?.toIntOrNull(),
            batteryLeft = getText("battery", "left")?.toIntOrNull(),
            batteryRight = getText("battery", "right")?.toIntOrNull(),
            batteryCase = getText("battery", "case")?.toIntOrNull(),
            isCharging = getText("battery", "is_charging")?.toBooleanStrictOrNull(),
            ancMode = getText("anc", "mode"),
            ancModeOptions = opts("anc", "mode_options"),
            ancLevel = getText("anc", "level"),
            ancLevelOptions = opts("anc", "level_options"),
            autoPause = getText("config", "auto_pause")?.toBooleanStrictOrNull(),
            lowLatency = getText("config", "low_latency")?.toBooleanStrictOrNull(),
            soundQuality = getText("sound", "quality_preference"),
            soundQualityOptions = opts("sound", "quality_preference_options"),
            equalizerPreset = getText("sound", "equalizer_preset"),
            equalizerPresetOptions = opts("sound", "equalizer_preset_options"),
            equalizerPresetCreateOptions = opts("sound", "equalizer_preset_create_options"),
            equalizerRows = opts("sound", "equalizer_rows").mapNotNull { it.toIntOrNull() },
            equalizerSaved = getText("sound", "equalizer_saved")?.toBooleanStrictOrNull(),
            equalizerMaxCustomModes = getText("sound", "equalizer_max_custom_modes")?.toIntOrNull() ?: 0,
            dualConnectEnabled = getText("dual_connect", "enabled")?.toBooleanStrictOrNull(),
            dualConnectDevices = parseDualConnectDevices(getText("dual_connect", "devices")),
            dualConnectPreferredDevice = getText("dual_connect", "preferred_device"),
            doubleTapLeft = getText("action", "double_tap_left"),
            doubleTapRight = getText("action", "double_tap_right"),
            doubleTapOptions = opts("action", "double_tap_options"),
            doubleTapInCall = getText("action", "double_tap_in_call"),
            doubleTapInCallOptions = opts("action", "double_tap_in_call_options"),
            tripleTapLeft = getText("action", "triple_tap_left"),
            tripleTapRight = getText("action", "triple_tap_right"),
            tripleTapOptions = opts("action", "triple_tap_options"),
            tripleTapInCall = getText("action", "triple_tap_in_call"),
            tripleTapInCallOptions = opts("action", "triple_tap_in_call_options"),
            longTap = getText("action", "long_tap"),
            longTapOptions = opts("action", "long_tap_options"),
            powerButton = getText("action", "power_button"),
            powerButtonOptions = opts("action", "power_button_options"),
            swipeGesture = getText("action", "swipe_gesture"),
            swipeGestureOptions = opts("action", "swipe_gesture_options"),
            inEar = getText("state", "in_ear")?.toBooleanStrictOrNull(),
            deviceModel = getText("info", "device_model"),
            firmwareVersion = getText("info", "software_ver"),
            voiceLanguage = getText("service", "language"),
            voiceLanguageOptions = opts("service", "language_options"),
            pendingInitHandlers = pendingHandlers.toList().sorted(),
            connectedSince = connectedSince,
        )
    }

    fun fromDeviceProps(props: DeviceProps, connectedSince: Long? = props.connectedSince): EarbudState =
        EarbudState(
            battery = EarbudBatteryState(
                global = props.batteryGlobal,
                left = props.batteryLeft,
                right = props.batteryRight,
                case = props.batteryCase,
                isCharging = props.isCharging,
            ),
            anc = EarbudAncState(
                mode = props.ancMode.nonBlankOrNull(),
                modeOptions = props.ancModeOptions,
                level = props.ancLevel.nonBlankOrNull(),
                levelOptions = props.ancLevelOptions,
            ),
            dualConnect = EarbudDualConnectState(
                enabled = props.dualConnectEnabled,
                devices = props.dualConnectDevices.map {
                    EarbudDualConnectDevice(
                        address = it.address,
                        name = it.name,
                        autoConnect = it.autoConnect,
                        preferred = it.preferred,
                        connected = it.connected,
                        playing = it.playing,
                    )
                },
                preferredDevice = props.dualConnectPreferredDevice,
            ),
            gestures = EarbudGestureState(
                doubleTapLeft = props.doubleTapLeft,
                doubleTapRight = props.doubleTapRight,
                doubleTapOptions = props.doubleTapOptions,
                doubleTapInCall = props.doubleTapInCall,
                doubleTapInCallOptions = props.doubleTapInCallOptions,
                tripleTapLeft = props.tripleTapLeft,
                tripleTapRight = props.tripleTapRight,
                tripleTapOptions = props.tripleTapOptions,
                tripleTapInCall = props.tripleTapInCall,
                tripleTapInCallOptions = props.tripleTapInCallOptions,
                longTap = props.longTap,
                longTapOptions = props.longTapOptions,
                powerButton = props.powerButton,
                powerButtonOptions = props.powerButtonOptions,
                swipeGesture = props.swipeGesture,
                swipeGestureOptions = props.swipeGestureOptions,
            ),
            wearing = EarbudWearingState(props.inEar),
            deviceInfo = EarbudDeviceInfo(props.deviceModel, props.firmwareVersion),
            audio = EarbudAudioState(
                autoPause = props.autoPause,
                lowLatency = props.lowLatency,
                soundQuality = props.soundQuality,
                soundQualityOptions = props.soundQualityOptions,
                equalizerPreset = props.equalizerPreset,
                equalizerPresetOptions = props.equalizerPresetOptions,
                equalizerPresetCreateOptions = props.equalizerPresetCreateOptions,
                equalizerRows = props.equalizerRows,
                equalizerSaved = props.equalizerSaved,
                equalizerMaxCustomModes = props.equalizerMaxCustomModes,
                voiceLanguage = props.voiceLanguage,
                voiceLanguageOptions = props.voiceLanguageOptions,
            ),
            pendingInitHandlers = props.pendingInitHandlers.toSet(),
            connectedSince = connectedSince,
        )

    /** Projects the generic contract back to the legacy UI snapshot without raw protocol keys. */
    fun toDeviceProps(state: EarbudState): DeviceProps = DeviceProps(
        batteryGlobal = state.battery.global,
        batteryLeft = state.battery.left,
        batteryRight = state.battery.right,
        batteryCase = state.battery.case,
        isCharging = state.battery.isCharging,
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
        equalizerPresetCreateOptions = state.audio.equalizerPresetCreateOptions,
        equalizerRows = state.audio.equalizerRows,
        equalizerSaved = state.audio.equalizerSaved,
        equalizerMaxCustomModes = state.audio.equalizerMaxCustomModes,
        dualConnectEnabled = state.dualConnect.enabled,
        dualConnectDevices = state.dualConnect.devices.map {
            DualConnectDevice(
                address = it.address,
                name = it.name,
                autoConnect = it.autoConnect,
                preferred = it.preferred,
                connected = it.connected,
                playing = it.playing,
            )
        },
        dualConnectPreferredDevice = state.dualConnect.preferredDevice,
        doubleTapLeft = state.gestures.doubleTapLeft,
        doubleTapRight = state.gestures.doubleTapRight,
        doubleTapOptions = state.gestures.doubleTapOptions,
        doubleTapInCall = state.gestures.doubleTapInCall,
        doubleTapInCallOptions = state.gestures.doubleTapInCallOptions,
        tripleTapLeft = state.gestures.tripleTapLeft,
        tripleTapRight = state.gestures.tripleTapRight,
        tripleTapOptions = state.gestures.tripleTapOptions,
        tripleTapInCall = state.gestures.tripleTapInCall,
        tripleTapInCallOptions = state.gestures.tripleTapInCallOptions,
        longTap = state.gestures.longTap,
        longTapOptions = state.gestures.longTapOptions,
        powerButton = state.gestures.powerButton,
        powerButtonOptions = state.gestures.powerButtonOptions,
        swipeGesture = state.gestures.swipeGesture,
        swipeGestureOptions = state.gestures.swipeGestureOptions,
        inEar = state.wearing.inEar,
        deviceModel = state.deviceInfo.model,
        firmwareVersion = state.deviceInfo.firmwareVersion,
        voiceLanguage = state.audio.voiceLanguage,
        voiceLanguageOptions = state.audio.voiceLanguageOptions,
        pendingInitHandlers = state.pendingInitHandlers.toList().sorted(),
        connectedSince = state.connectedSince,
    )

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

    private fun String?.nonBlankOrNull(): String? = takeUnless { it.isNullOrBlank() }
}
