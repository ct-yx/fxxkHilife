package com.freebuds.controller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlChannelStateTest {
    @Test
    fun defaultStateDoesNotPretendTheControlChannelIsReady() {
        val state = ControlChannelState()

        assertEquals(SystemLinkState.Disconnected, state.systemLink)
        assertEquals(ControlChannelStage.Idle, state.stage)
        assertTrue(state.pendingHandlers.isEmpty())
        assertTrue(state.failedHandlers.isEmpty())
    }

    @Test
    fun coreAndDeferredStagesRemainDistinct() {
        val core = ControlChannelState(stage = ControlChannelStage.InitializingCore)
        val deferred = core.copy(stage = ControlChannelStage.InitializingDeferred)

        assertEquals(ControlChannelStage.InitializingCore, core.stage)
        assertEquals(ControlChannelStage.InitializingDeferred, deferred.stage)
        assertEquals(core.attemptId, deferred.attemptId)
    }
}
