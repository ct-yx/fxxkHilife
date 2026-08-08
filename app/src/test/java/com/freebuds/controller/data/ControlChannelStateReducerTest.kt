package com.freebuds.controller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ControlChannelStateReducerTest {
    private val reducer = ControlChannelStateReducer()

    @Test
    fun staleAttemptStageCannotOverwriteTheReplacementAttempt() {
        val first = reducer.beginAttempt(
            state = ControlChannelState(),
            deviceName = "Buds",
            address = "AA",
            attemptId = "attempt-1",
            trigger = ConnectionTrigger.UserAction,
            systemLink = SystemLinkState.Connected,
        )
        val second = reducer.beginAttempt(
            state = first,
            deviceName = "Buds",
            address = "AA",
            attemptId = "attempt-2",
            trigger = ConnectionTrigger.AclConnected,
            systemLink = SystemLinkState.Connected,
        )

        val unchanged = reducer.stage(
            state = second,
            attemptId = "attempt-1",
            stage = ControlChannelStage.Ready,
        )

        assertSame(second, unchanged)
        assertEquals("attempt-2", unchanged.attemptId)
        assertEquals(ControlChannelStage.ConnectingTransport, unchanged.stage)
    }

    @Test
    fun resetClearsAttemptAndHandlerDetailsButKeepsSystemLink() {
        val connected = reducer.beginAttempt(
            state = ControlChannelState(),
            deviceName = "Buds",
            address = "AA",
            attemptId = "attempt-1",
            trigger = ConnectionTrigger.UserAction,
            systemLink = SystemLinkState.Connected,
        ).copy(
            pendingHandlers = setOf("battery"),
            failedHandlers = setOf("anc_global"),
        )

        val reset = reducer.reset(SystemLinkState.Connected)

        assertEquals(SystemLinkState.Connected, reset.systemLink)
        assertEquals(ControlChannelStage.Idle, reset.stage)
        assertEquals(null, reset.attemptId)
        assertEquals(emptySet<String>(), reset.pendingHandlers)
        assertEquals(emptySet<String>(), reset.failedHandlers)
        assertEquals("attempt-1", connected.attemptId)
    }

    @Test
    fun linkUpdateForAnotherAddressIsIgnored() {
        val state = reducer.beginAttempt(
            state = ControlChannelState(),
            deviceName = "Buds",
            address = "AA",
            attemptId = "attempt-1",
            trigger = ConnectionTrigger.UserAction,
            systemLink = SystemLinkState.Connected,
        )

        val unchanged = reducer.updateSystemLink(state, "BB", SystemLinkState.Disconnected)

        assertSame(state, unchanged)
    }
}
