package com.freebuds.controller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionAttemptCoordinatorTest {
    @Test
    fun busyEntryPointForSameAddressReusesTheOriginalAttempt() {
        val coordinator = coordinator()
        val owner = coordinator.reserve(
            address = "AA:AA:AA:AA:AA:AA",
            trigger = ConnectionTrigger.UserAction,
            busy = false,
        )
        val duplicate = coordinator.reserve(
            address = "aa:aa:aa:aa:aa:aa",
            trigger = ConnectionTrigger.TileAction,
            busy = true,
        )

        assertNotNull(owner)
        assertNotNull(duplicate)
        assertTrue(owner!!.ownsAttempt)
        assertFalse(duplicate!!.ownsAttempt)
        assertSame(owner.attempt, duplicate.attempt)
        assertEquals("AA:AA:AA:AA:AA:AA", duplicate.attempt.address)
        assertEquals(ConnectionTrigger.UserAction, duplicate.attempt.trigger)
    }

    @Test
    fun busyEntryPointForDifferentAddressIsRejected() {
        val coordinator = coordinator()
        coordinator.reserve(
            address = "AA:AA:AA:AA:AA:AA",
            trigger = ConnectionTrigger.UserAction,
            busy = false,
        )

        assertNull(
            coordinator.reserve(
                address = "BB:BB:BB:BB:BB:BB",
                trigger = ConnectionTrigger.TileAction,
                busy = true,
            )
        )
    }

    @Test
    fun clearingAnOldAttemptCannotClearItsReplacement() {
        val coordinator = coordinator()
        val first = coordinator.reserve("AA", ConnectionTrigger.UserAction, busy = false)!!.attempt
        val replacement = coordinator.reserve("BB", ConnectionTrigger.PeriodicCheck, busy = false)!!.attempt

        assertFalse(coordinator.clearIfCurrent(first))
        assertSame(replacement, coordinator.current())
        assertTrue(coordinator.clearIfCurrent(replacement))
        assertNull(coordinator.current())
    }

    @Test
    fun idleReservationAfterFailureStartsANewAttempt() {
        val coordinator = coordinator()
        val first = coordinator.reserve("AA", ConnectionTrigger.HardwareRegression, busy = false)!!.attempt

        val next = coordinator.reserve("AA", ConnectionTrigger.HardwareRegression, busy = false)!!.attempt

        assertFalse(first === next)
        assertTrue(next.attemptId != first.attemptId)
        assertSame(next, coordinator.current())
    }

    @Test
    fun busyReservationWithoutAnActiveAttemptIsRejected() {
        val coordinator = coordinator()

        assertNull(coordinator.reserve("AA", ConnectionTrigger.ServiceCommand, busy = true))
    }

    private fun coordinator(): ConnectionAttemptCoordinator {
        var wall = 1_000L
        var monotonic = 5_000L
        return ConnectionAttemptCoordinator(
            wallClockMs = { wall },
            monotonicClockMs = { monotonic++ },
        )
    }
}
