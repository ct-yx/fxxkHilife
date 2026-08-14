package com.freebuds.controller.data

import com.freebuds.controller.core.capability.EarbudCapability
import com.freebuds.controller.core.state.EarbudState
import com.freebuds.controller.core.state.EarbudAncState
import com.freebuds.controller.core.state.EarbudAudioState
import com.freebuds.controller.core.state.EarbudBatteryState
import com.freebuds.controller.core.state.EarbudDeviceInfo
import com.freebuds.controller.core.state.EarbudDualConnectDevice
import com.freebuds.controller.core.state.EarbudDualConnectState
import com.freebuds.controller.core.state.EarbudGestureState
import com.freebuds.controller.core.state.EarbudWearingState
import com.freebuds.controller.adapter.huawei.HuaweiOpenFreebudsAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarbudStateMapperTest {
    @Test
    fun genericStateRoundTripsThroughCompatibilityProjection() {
        val props = DeviceProps(
            batteryLeft = 80,
            ancMode = "cancellation",
            lowLatency = true,
            inEar = true,
            deviceModel = "FreeBuds 6i",
            pendingInitHandlers = listOf("gesture_double"),
            connectedSince = 123L,
        )

        val state = EarbudStateMapper.fromDeviceProps(props)
        val projected = EarbudStateMapper.toDeviceProps(state)

        assertEquals(props, projected)
        assertEquals(80, state.battery.left)
        assertEquals("cancellation", state.anc.mode)
        assertTrue(state.audio.lowLatency == true)
        assertEquals(setOf("gesture_double"), state.pendingInitHandlers)
    }

    @Test
    fun blankAncValuesAreNormalizedToUnknown() {
        val state = EarbudStateMapper.fromDeviceProps(
            DeviceProps(ancMode = "", ancLevel = "   "),
        )

        assertNull(state.anc.mode)
        assertNull(state.anc.level)
        assertNull(EarbudStateMapper.toDeviceProps(state).ancMode)
        assertNull(EarbudStateMapper.toDeviceProps(state).ancLevel)
    }

    @Test
    fun unknownCapabilitySetIsEmptyRatherThanAllCapabilities() {
        assertEquals(emptySet<EarbudCapability>(), HuaweiOpenFreebudsAdapter.capabilities("HUAWEI Unknown Buds"))
        assertEquals(null, HuaweiOpenFreebudsAdapter.detectModel("Generic Earbuds"))
        assertEquals(
            com.freebuds.controller.protocol.HuaweiModel.BUDS_4I,
            HuaweiOpenFreebudsAdapter.detectModel("HONOR Earbuds 2 SE"),
        )
    }

    @Test
    fun knownFreeBuds6iCapabilitySetMatchesTheRegisteredGenericContract() {
        val capabilities = HuaweiOpenFreebudsAdapter.capabilities("HUAWEI FreeBuds 6i")

        assertTrue(EarbudCapability.DEVICE_INFO in capabilities)
        assertTrue(EarbudCapability.BATTERY in capabilities)
        assertTrue(EarbudCapability.ANC in capabilities)
        assertTrue(EarbudCapability.LOW_LATENCY in capabilities)
        assertTrue(EarbudCapability.WEAR_DETECTION in capabilities)
        assertTrue(EarbudCapability.GESTURES in capabilities)
    }

    @Test
    fun coreReadinessDoesNotRequireUnsupportedOptionalFields() {
        val state = EarbudState(
            battery = com.freebuds.controller.core.state.EarbudBatteryState(global = 80),
            anc = com.freebuds.controller.core.state.EarbudAncState(),
            audio = com.freebuds.controller.core.state.EarbudAudioState(),
        )

        assertTrue(
            EarbudStateMapper.isCoreStateReady(
                state,
                setOf(EarbudCapability.BATTERY),
            )
        )
    }

    @Test
    fun unknownCapabilityProfileIsNotReportedAsCoreReady() {
        val state = EarbudState(
            battery = com.freebuds.controller.core.state.EarbudBatteryState(global = 80),
            anc = com.freebuds.controller.core.state.EarbudAncState(mode = "cancellation"),
            audio = com.freebuds.controller.core.state.EarbudAudioState(lowLatency = true),
        )

        assertTrue(!EarbudStateMapper.isCoreStateReady(state, emptySet()))
    }

    @Test
    fun metadataOnlyCapabilityProfileIsNotReportedAsCoreReady() {
        val state = EarbudState(deviceInfo = EarbudDeviceInfo(model = "FreeBuds 6i"))

        assertTrue(
            !EarbudStateMapper.isCoreStateReady(
                state,
                setOf(EarbudCapability.DEVICE_INFO, EarbudCapability.LOGS),
            )
        )
    }

    @Test
    fun fullFreeBuds6iContractPassesOnlyWhenEveryAdvertisedDomainIsReadable() {
        val capabilities = HuaweiOpenFreebudsAdapter.capabilities("HUAWEI FreeBuds 6i")
        val state = EarbudState(
            battery = EarbudBatteryState(global = 80, left = 79, right = 81, case = 60),
            anc = EarbudAncState(
                mode = "cancellation",
                modeOptions = listOf("normal", "cancellation", "awareness"),
                level = "comfort",
                levelOptions = listOf("comfort", "normal", "ultra", "dynamic"),
            ),
            audio = EarbudAudioState(
                autoPause = true,
                lowLatency = true,
                soundQuality = "sqp_quality",
                equalizerPreset = "equalizer_preset_default",
                equalizerPresetOptions = listOf("equalizer_preset_default"),
                equalizerRows = List(10) { 0 },
                equalizerMaxCustomModes = 3,
                voiceLanguageOptions = listOf("zh-CN", "en-GB"),
            ),
            dualConnect = EarbudDualConnectState(
                enabled = true,
                devices = listOf(EarbudDualConnectDevice("a", "phone", null, true, true, false)),
            ),
            gestures = EarbudGestureState(doubleTapLeft = "tap_action_pause"),
            wearing = EarbudWearingState(inEar = false),
            deviceInfo = EarbudDeviceInfo(model = "FreeBuds 6i"),
        )
        val props = EarbudStateMapper.toDeviceProps(state)
        val channel = ControlChannelState(
            stage = ControlChannelStage.Ready,
            attemptId = "attempt-1",
            pendingHandlers = emptySet(),
            failedHandlers = emptySet(),
        )
        val verdict = EarbudStateContractValidator.validate(
            snapshot = EarbudSnapshot(state, props, capabilities, channel),
            expectedAttemptId = "attempt-1",
            expectedCapabilities = capabilities,
        )

        assertTrue(verdict.passed)
        assertTrue(verdict.exactCapabilities)
        assertTrue(verdict.allStateDomainsConsistent)
        assertTrue(verdict.pendingEmpty)
    }

    @Test
    fun fullFreeBuds6iContractRejectsMissingOptionalAdvertisedDomain() {
        val capabilities = HuaweiOpenFreebudsAdapter.capabilities("HUAWEI FreeBuds 6i")
        val state = EarbudState(
            battery = EarbudBatteryState(global = 80),
            anc = EarbudAncState(
                mode = "cancellation",
                level = "comfort",
                levelOptions = listOf("comfort", "normal", "ultra", "dynamic"),
            ),
            audio = EarbudAudioState(
                lowLatency = true,
            ),
            wearing = EarbudWearingState(inEar = false),
            deviceInfo = EarbudDeviceInfo(model = "FreeBuds 6i"),
        )
        val verdict = EarbudStateContractValidator.validate(
            snapshot = EarbudSnapshot(
                state,
                EarbudStateMapper.toDeviceProps(state),
                capabilities,
                ControlChannelState(stage = ControlChannelStage.Ready, attemptId = "attempt-1"),
            ),
            expectedAttemptId = "attempt-1",
            expectedCapabilities = capabilities,
        )

        assertTrue(!verdict.passed)
        assertEquals(false, verdict.stateDomains["equalizer"])
        assertEquals(false, verdict.stateDomains["sound_quality"])
    }

    @Test
    fun degradedStageOrFailedHandlersDoesNotPassContract() {
        val capabilities = setOf(EarbudCapability.BATTERY)
        val state = EarbudState(battery = EarbudBatteryState(global = 80))
        val verdict = EarbudStateContractValidator.validate(
            snapshot = EarbudSnapshot(
                state,
                EarbudStateMapper.toDeviceProps(state),
                capabilities,
                ControlChannelState(
                    stage = ControlChannelStage.Degraded,
                    attemptId = "attempt-1",
                    failedHandlers = setOf("battery"),
                ),
            ),
            expectedAttemptId = "attempt-1",
            expectedCapabilities = capabilities,
        )

        assertTrue(!verdict.passed)
        assertTrue(!verdict.readyStage)
        assertTrue(!verdict.failedHandlersEmpty)
    }

    @Test
    fun readyContractWithPendingInitializationDoesNotPass() {
        val capabilities = setOf(EarbudCapability.BATTERY)
        val state = EarbudState(
            battery = EarbudBatteryState(global = 80),
            pendingInitHandlers = setOf("optional_probe"),
        )
        val props = EarbudStateMapper.toDeviceProps(state)
        val verdict = EarbudStateContractValidator.validate(
            snapshot = EarbudSnapshot(
                state,
                props,
                capabilities,
                ControlChannelState(
                    stage = ControlChannelStage.Ready,
                    attemptId = "attempt-1",
                    pendingHandlers = setOf("optional_probe"),
                ),
            ),
            expectedAttemptId = "attempt-1",
            expectedCapabilities = capabilities,
        )

        assertTrue(!verdict.passed)
        assertTrue(verdict.pendingConsistent)
        assertTrue(!verdict.pendingEmpty)
    }

    @Test
    fun presetAndCustomEqualizerDomainsAreValidatedIndependently() {
        val capabilities = setOf(EarbudCapability.EQUALIZER, EarbudCapability.CUSTOM_EQUALIZER)
        val state = EarbudState(
            audio = EarbudAudioState(
                equalizerPreset = "equalizer_preset_default",
                equalizerPresetOptions = listOf("equalizer_preset_default"),
            ),
        )
        val verdict = EarbudStateContractValidator.validate(
            snapshot = EarbudSnapshot(
                state,
                EarbudStateMapper.toDeviceProps(state),
                capabilities,
                ControlChannelState(stage = ControlChannelStage.Ready, attemptId = "attempt-1"),
            ),
            expectedAttemptId = "attempt-1",
            expectedCapabilities = capabilities,
        )

        assertTrue(verdict.stateDomains["equalizer"] == true)
        assertEquals(false, verdict.stateDomains["custom_equalizer"])
        assertTrue(!verdict.passed)
    }

    @Test
    fun dynamicAndVoiceBoostDomainsRequireTheirActiveAncOptions() {
        val dynamicCapabilities = setOf(
            EarbudCapability.ANC,
            EarbudCapability.ANC_LEVEL,
            EarbudCapability.ANC_DYNAMIC,
        )
        val cancellationState = EarbudState(
            anc = EarbudAncState(
                mode = "cancellation",
                level = "comfort",
                levelOptions = listOf("comfort", "normal", "ultra"),
            ),
        )
        val dynamicVerdict = EarbudStateContractValidator.validate(
            snapshot = EarbudSnapshot(
                cancellationState,
                EarbudStateMapper.toDeviceProps(cancellationState),
                dynamicCapabilities,
                ControlChannelState(stage = ControlChannelStage.Ready, attemptId = "attempt-1"),
            ),
            expectedAttemptId = "attempt-1",
            expectedCapabilities = dynamicCapabilities,
        )

        assertEquals(false, dynamicVerdict.stateDomains["anc_dynamic"])

        val voiceBoostCapabilities = setOf(EarbudCapability.ANC, EarbudCapability.VOICE_BOOST)
        val awarenessState = EarbudState(
            anc = EarbudAncState(
                mode = "awareness",
                level = "normal",
                levelOptions = listOf("normal"),
            ),
        )
        val voiceBoostVerdict = EarbudStateContractValidator.validate(
            snapshot = EarbudSnapshot(
                awarenessState,
                EarbudStateMapper.toDeviceProps(awarenessState),
                voiceBoostCapabilities,
                ControlChannelState(stage = ControlChannelStage.Ready, attemptId = "attempt-1"),
            ),
            expectedAttemptId = "attempt-1",
            expectedCapabilities = voiceBoostCapabilities,
        )

        assertEquals(false, voiceBoostVerdict.stateDomains["voice_boost"])
    }
}
