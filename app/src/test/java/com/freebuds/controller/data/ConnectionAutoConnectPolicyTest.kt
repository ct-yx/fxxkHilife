package com.freebuds.controller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionAutoConnectPolicyTest {
    @Test
    fun hardwareRegressionOnlyPausesPeriodicAndProfileProbes() {
        assertTrue(isBackgroundAutoConnectSuppressed(ConnectionTrigger.PeriodicCheck, true))
        assertTrue(isBackgroundAutoConnectSuppressed(ConnectionTrigger.AudioProfileConnected, true))
        assertFalse(isBackgroundAutoConnectSuppressed(ConnectionTrigger.AclConnected, true))
        assertFalse(isBackgroundAutoConnectSuppressed(ConnectionTrigger.HardwareRegression, true))
        assertFalse(isBackgroundAutoConnectSuppressed(ConnectionTrigger.PeriodicCheck, false))
    }

    @Test
    fun rejectedAttemptsBackOffAndAcceptedAttemptResetsTheDelay() {
        var now = 1_000L
        val policy = ConnectionAutoConnectPolicy(nowMs = { now })

        assertTrue(policy.canAttempt())
        policy.recordRejected()
        assertFalse(policy.canAttempt())
        assertEquals(6_000L, policy.nextAttemptAt())
        assertEquals(10_000L, policy.currentBackoffMs())

        now = 6_000L
        assertTrue(policy.canAttempt())
        policy.recordAccepted()
        assertEquals(11_000L, policy.nextAttemptAt())
        assertEquals(5_000L, policy.currentBackoffMs())
    }

    @Test
    fun forcedAttemptIgnoresTheWindowButDoesNotEraseBackoffState() {
        var now = 2_000L
        val policy = ConnectionAutoConnectPolicy(nowMs = { now })
        policy.recordRejected()

        assertTrue(policy.canAttempt(force = true))
        assertEquals(10_000L, policy.currentBackoffMs())
        now = 3_000L
        policy.recordRejected()
        assertEquals(13_000L, policy.nextAttemptAt())
        assertEquals(20_000L, policy.currentBackoffMs())
    }
}
