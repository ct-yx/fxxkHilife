package com.freebuds.controller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionAttemptTimelineTest {
    @Test
    fun phaseDurationsUseTheFirstMonotonicMark() {
        var now = 1_000L
        val timeline = ConnectionAttemptTimeline(
            attemptId = "attempt-1",
            address = "AA:BB:CC:DD:EE:FF",
            trigger = ConnectionTrigger.UserAction,
            nowMs = { now },
        )

        assertEquals(0L, timeline.mark(ConnectionPhase.Requested).elapsedSinceStartMs)
        now += 40L
        assertEquals(40L, timeline.mark(ConnectionPhase.TransportConnecting).elapsedSinceStartMs)
        now += 120L
        assertEquals(160L, timeline.mark(ConnectionPhase.TransportReady).elapsedSinceStartMs)
        now += 250L
        assertEquals(410L, timeline.mark(ConnectionPhase.CoreReady).elapsedSinceStartMs)
        assertEquals(250L, timeline.elapsed(ConnectionPhase.TransportReady, ConnectionPhase.CoreReady) ?: -1L)

        val duplicate = timeline.mark(ConnectionPhase.TransportReady)
        assertFalse(duplicate.firstMark)
        assertEquals(160L, duplicate.elapsedSinceStartMs)
        assertEquals(410L, timeline.elapsedSinceStart())
    }

    @Test
    fun missingPhasesDoNotInventDurations() {
        val timeline = ConnectionAttemptTimeline(
            attemptId = "attempt-2",
            address = "AA:BB:CC:DD:EE:FF",
            trigger = ConnectionTrigger.PeriodicCheck,
            nowMs = { 500L },
        )

        assertTrue(timeline.elapsed(ConnectionPhase.TransportReady, ConnectionPhase.Ready) == null)
        assertFalse(timeline.hasMarked(ConnectionPhase.Ready))
    }
}
