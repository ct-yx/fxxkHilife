package com.freebuds.controller.adapter.huawei.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiHandlerInitializationPolicyTest {
    @Test
    fun coreStateReadsHaveAShortBoundedTimeout() {
        assertEquals(1_000L, HuaweiHandlerInitializationPolicy.CORE_HANDLER_TIMEOUT_MS)
        assertEquals(80L, HuaweiHandlerInitializationPolicy.CORE_INTER_COMMAND_DELAY_MS)
        assertTrue(HuaweiHandlerInitializationPolicy.worstCaseCoreReadyMs() <= 10_000L)
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
