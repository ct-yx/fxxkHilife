package com.freebuds.controller.data

import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionLifecycleTest {
    @Test
    fun stateAddressAndConnectionTimeBelongToOneRuntimeSlot() {
        val lifecycle = ConnectionLifecycle()

        lifecycle.setConnectionState(ConnectionState.Connecting("Buds", "attempt-1"))
        lifecycle.setConnectedAddress("AA")
        lifecycle.setConnectedAt(123L)

        assertEquals(ConnectionState.Connecting("Buds", "attempt-1"), lifecycle.connectionState.value)
        assertEquals("AA", lifecycle.connectedAddress())
        assertEquals(123L, lifecycle.connectedAt())
        assertTrue(lifecycle.isBusy())
    }

    @Test
    fun cancellingConnectionWorkCancelsEveryConnectionOwnedJob() {
        val lifecycle = ConnectionLifecycle()
        val connection = SupervisorJob()
        val initialization = SupervisorJob()
        val backgroundInitialization = SupervisorJob()
        val polling = SupervisorJob()
        val fastPolling = SupervisorJob()

        lifecycle.installConnectionJob(connection)
        lifecycle.installInitializationJob(initialization)
        lifecycle.installBackgroundInitializationJob(backgroundInitialization)
        lifecycle.installPollingJob(polling)
        lifecycle.installFastPollingJob(fastPolling)

        lifecycle.cancelConnectionWork()

        assertTrue(connection.isCancelled)
        assertTrue(initialization.isCancelled)
        assertTrue(backgroundInitialization.isCancelled)
        assertTrue(polling.isCancelled)
        assertTrue(fastPolling.isCancelled)
        assertFalse(lifecycle.isInitializationActive())
    }

    @Test
    fun manualSuppressionAndReconnectGuardAreTimeBound() {
        val lifecycle = ConnectionLifecycle()

        lifecycle.suppressManualDisconnect(1_500L)
        lifecycle.armReconnectGuard(2_500L)

        assertEquals(500L, lifecycle.manualDisconnectSuppressionRemaining(1_000L))
        assertEquals(1_500L, lifecycle.reconnectDelayMs(1_000L))

        lifecycle.clearManualDisconnectSuppression()
        assertEquals(0L, lifecycle.manualDisconnectSuppressionRemaining(1_000L))
    }

    @Test
    fun systemLinkBookkeepingIsIndependentFromControlState() {
        val lifecycle = ConnectionLifecycle()

        lifecycle.markSystemConnected("AA")
        assertTrue(lifecycle.isSystemConnected("AA"))

        lifecycle.markSystemDisconnected("AA")
        assertFalse(lifecycle.isSystemConnected("AA"))
    }
}
