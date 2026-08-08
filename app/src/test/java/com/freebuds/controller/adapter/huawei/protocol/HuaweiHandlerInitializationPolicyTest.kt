package com.freebuds.controller.adapter.huawei.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.freebuds.controller.core.transport.EndpointSource

class HuaweiHandlerInitializationPolicyTest {
    @Test
    fun coreStateReadsHaveAShortBoundedTimeout() {
        assertEquals(1_000L, HuaweiHandlerInitializationPolicy.FAST_CORE_HANDLER_TIMEOUT_MS)
        assertEquals(3_000L, HuaweiHandlerInitializationPolicy.CORE_HANDLER_TIMEOUT_MS)
        assertEquals(80L, HuaweiHandlerInitializationPolicy.CORE_INTER_COMMAND_DELAY_MS)
        assertEquals(3_000L, HuaweiHandlerInitializationPolicy.CORE_RECOVERY_TIMEOUT_MS)
        assertEquals(3, HuaweiHandlerInitializationPolicy.CORE_RECOVERY_ROUNDS)
        assertEquals(250L, HuaweiHandlerInitializationPolicy.BACKGROUND_HANDOFF_DELAY_MS)
        assertTrue(HuaweiHandlerInitializationPolicy.worstCaseCoreReadyMs() <= 13_000L)
        assertTrue(HuaweiHandlerInitializationPolicy.worstCaseFastCoreReadyMs() <= 5_000L)
    }

    @Test
    fun verifiedEndpointsUseFastFirstPassAndUnknownEndpointsKeepRecoveryBudget() {
        assertEquals(
            1_000L,
            HuaweiHandlerInitializationPolicy.initialCoreTimeoutMs(EndpointSource.VerifiedModelConfig),
        )
        assertEquals(
            3_000L,
            HuaweiHandlerInitializationPolicy.initialCoreTimeoutMs(EndpointSource.CompatibilityFallback),
        )
    }

    @Test
    fun ancAndLowLatencyArePrioritizedAheadOfBatteryTelemetry() {
        val order = HuaweiHandlerInitializationPolicy.CORE_HANDLER_IDS_IN_ORDER
        assertTrue(order.indexOf("anc_global") < order.indexOf("battery"))
        assertTrue(order.indexOf("low_latency") < order.indexOf("battery"))
    }

    @Test
    fun optionalHandlersCannotConsumeTheCoreTimeoutBudget() {
        assertEquals(800L, HuaweiHandlerInitializationPolicy.deferredTimeoutMs("gesture_double", 3_000L))
        assertEquals(1_200L, HuaweiHandlerInitializationPolicy.deferredTimeoutMs("dual_connect", 3_000L))
        assertTrue(
            HuaweiHandlerInitializationPolicy.deferredTimeoutMs("voice_language", 500L) <= 500L
        )
    }
}
