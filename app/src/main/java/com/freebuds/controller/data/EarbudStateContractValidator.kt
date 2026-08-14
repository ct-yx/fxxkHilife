package com.freebuds.controller.data

import com.freebuds.controller.core.capability.EarbudCapability

/**
 * Pure BT-4 contract check shared by the debug runner and JVM tests.
 *
 * A connection can be transport-ready while its typed state is still incomplete.  The validator
 * therefore checks the whole published contract instead of treating a non-empty capability set
 * or a Degraded stage as success.
 */
data class EarbudStateContractVerdict(
    val passed: Boolean,
    val sameAttempt: Boolean,
    val terminalStage: Boolean,
    val readyStage: Boolean,
    val coreReady: Boolean,
    val exactCapabilities: Boolean,
    val projectionMatches: Boolean,
    val pendingConsistent: Boolean,
    val pendingEmpty: Boolean,
    val failedHandlersEmpty: Boolean,
    val stateDomains: Map<String, Boolean>,
) {
    val allStateDomainsConsistent: Boolean
        get() = stateDomains.values.all { it }
}

object EarbudStateContractValidator {
    fun validate(
        snapshot: EarbudSnapshot,
        expectedAttemptId: String?,
        expectedCapabilities: Set<EarbudCapability>,
    ): EarbudStateContractVerdict {
        val state = snapshot.state
        val props = snapshot.props
        val channel = snapshot.controlChannel
        val actualCapabilities = snapshot.capabilities
        val sameAttempt = expectedAttemptId != null && channel.attemptId == expectedAttemptId
        val terminalStage = channel.stage == ControlChannelStage.Ready
        val readyStage = channel.stage == ControlChannelStage.Ready
        val coreReady = EarbudStateMapper.isCoreStateReady(snapshot)
        val exactCapabilities = expectedCapabilities.isNotEmpty() &&
            actualCapabilities == expectedCapabilities
        val projectionMatches = EarbudStateMapper.toDeviceProps(state) == props
        val pendingConsistent = channel.pendingHandlers == state.pendingInitHandlers &&
            channel.pendingHandlers == props.pendingInitHandlers.toSet()
        val pendingEmpty = channel.pendingHandlers.isEmpty() &&
            state.pendingInitHandlers.isEmpty() &&
            props.pendingInitHandlers.isEmpty()
        val failedHandlersEmpty = channel.failedHandlers.isEmpty()
        val stateDomains = stateDomains(state, actualCapabilities)

        return EarbudStateContractVerdict(
            passed = sameAttempt && terminalStage && readyStage && coreReady &&
                exactCapabilities && projectionMatches && pendingConsistent &&
                pendingEmpty && failedHandlersEmpty && stateDomains.values.all { it },
            sameAttempt = sameAttempt,
            terminalStage = terminalStage,
            readyStage = readyStage,
            coreReady = coreReady,
            exactCapabilities = exactCapabilities,
            projectionMatches = projectionMatches,
            pendingConsistent = pendingConsistent,
            pendingEmpty = pendingEmpty,
            failedHandlersEmpty = failedHandlersEmpty,
            stateDomains = stateDomains,
        )
    }

    private fun stateDomains(
        state: com.freebuds.controller.core.state.EarbudState,
        capabilities: Set<EarbudCapability>,
    ): Map<String, Boolean> {
        val batteryRead = state.battery.global != null || state.battery.left != null ||
            state.battery.right != null || state.battery.case != null
        val gestureRead = listOf(
            state.gestures.doubleTapLeft,
            state.gestures.doubleTapRight,
            state.gestures.doubleTapInCall,
            state.gestures.tripleTapLeft,
            state.gestures.tripleTapRight,
            state.gestures.tripleTapInCall,
            state.gestures.longTap,
            state.gestures.powerButton,
            state.gestures.swipeGesture,
        ).any { it != null }
        val equalizerPresetRead = state.audio.equalizerPreset != null ||
            state.audio.equalizerPresetOptions.isNotEmpty()
        val customEqualizerRead = state.audio.equalizerRows.isNotEmpty() ||
            state.audio.equalizerPresetCreateOptions.isNotEmpty() ||
            state.audio.equalizerMaxCustomModes > 0
        val dualConnectRead = state.dualConnect.enabled != null ||
            state.dualConnect.devices.isNotEmpty()
        val voiceLanguageRead = state.audio.voiceLanguage != null ||
            state.audio.voiceLanguageOptions.isNotEmpty()
        // Normal mode intentionally has no level selector. The mode itself is the readable ANC
        // state; a level is required only when the current mode exposes a level domain.
        val ancModeRead = state.anc.mode in setOf("normal", "cancellation", "awareness")
        val ancLevelRead = ancModeRead && (state.anc.mode == "normal" ||
            state.anc.level != null || state.anc.levelOptions.isNotEmpty())
        // These are mode-specific domains. An inactive domain is not missing merely because the
        // current snapshot is in another mode.
        val ancDynamicRead = state.anc.mode != "cancellation" ||
            state.anc.levelOptions.any { it == "dynamic" } || state.anc.level == "dynamic"
        val voiceBoostRead = state.anc.mode != "awareness" ||
            state.anc.levelOptions.any { it == "voice_boost" } ||
            state.anc.level == "voice_boost"

        val checks = linkedMapOf<String, Boolean>()
        fun require(name: String, capability: EarbudCapability, value: Boolean) {
            if (capability in capabilities) checks[name] = value
        }

        require("battery", EarbudCapability.BATTERY, batteryRead)
        require("anc", EarbudCapability.ANC, ancModeRead)
        require("anc_level", EarbudCapability.ANC_LEVEL, ancLevelRead)
        require("anc_dynamic", EarbudCapability.ANC_DYNAMIC, ancDynamicRead)
        require("voice_boost", EarbudCapability.VOICE_BOOST, voiceBoostRead)
        require("auto_pause", EarbudCapability.AUTO_PAUSE, state.audio.autoPause != null)
        require("low_latency", EarbudCapability.LOW_LATENCY, state.audio.lowLatency != null)
        require("sound_quality", EarbudCapability.SOUND_QUALITY, state.audio.soundQuality != null)
        require("equalizer", EarbudCapability.EQUALIZER, equalizerPresetRead)
        require("custom_equalizer", EarbudCapability.CUSTOM_EQUALIZER, customEqualizerRead)
        require("dual_connect", EarbudCapability.DUAL_CONNECT, dualConnectRead)
        require("wear_detection", EarbudCapability.WEAR_DETECTION, state.wearing.inEar != null)
        require("gestures", EarbudCapability.GESTURES, gestureRead)
        require("voice_language", EarbudCapability.VOICE_LANGUAGE, voiceLanguageRead)
        require(
            "device_info",
            EarbudCapability.DEVICE_INFO,
            state.deviceInfo.model != null || state.deviceInfo.firmwareVersion != null,
        )
        // LOGS is a diagnostic command capability and intentionally has no user-state field.
        return checks
    }
}
