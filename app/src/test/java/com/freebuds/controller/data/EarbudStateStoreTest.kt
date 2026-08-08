package com.freebuds.controller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EarbudStateStoreTest {
    @Test
    fun stageAndHandlerUpdatesRemainBoundToTheActiveAttempt() {
        val store = EarbudStateStore()
        store.beginAttempt(
            deviceName = "FreeBuds",
            address = "AA",
            attemptId = "attempt-1",
            trigger = ConnectionTrigger.UserAction,
            systemLink = SystemLinkState.Connected,
        )
        store.updateStage(
            stage = ControlChannelStage.CoreReady,
            attempt = ConnectionAttemptTimeline(
                attemptId = "attempt-1",
                address = "AA",
                trigger = ConnectionTrigger.UserAction,
                nowMs = { 1L },
            ),
            systemLink = SystemLinkState.Connected,
            pendingHandlers = setOf("device_info"),
            failedHandlers = emptySet(),
        )
        store.updateHandlers(
            activeAttemptId = "attempt-2",
            pendingHandlers = setOf("stale"),
            failedHandlers = setOf("stale"),
        )

        val state = store.controlChannelState.value
        assertEquals(ControlChannelStage.CoreReady, state.stage)
        assertEquals(setOf("device_info"), state.pendingHandlers)
        assertEquals(emptySet<String>(), state.failedHandlers)
    }

    @Test
    fun propertyProjectionIsUpdatedWithoutExposingMutableFlow() {
        val store = EarbudStateStore()
        val initial = store.props
        store.updateProps { it.copy(lowLatency = true) }

        assertSame(initial, store.props)
        assertEquals(true, store.props.value.lowLatency)
    }
}
