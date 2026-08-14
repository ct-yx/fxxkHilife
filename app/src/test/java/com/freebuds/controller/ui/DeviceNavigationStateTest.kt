package com.freebuds.controller.ui

import com.freebuds.controller.data.ConnectionTrigger
import com.freebuds.controller.ui.state.DeviceConnectionSession
import com.freebuds.controller.ui.state.DeviceNavigationState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceNavigationStateTest {
    private val session = DeviceConnectionSession(
        address = "AA:BB:CC:DD:EE:FF",
        attemptId = "attempt-1",
    )

    @Test
    fun automaticReadyEventOpensOnlyOnceForOneSession() {
        val state = DeviceNavigationState().observeSession(session)

        assertTrue(state.shouldAutoOpen(isReady = true, trigger = ConnectionTrigger.AclConnected))

        val opened = state.markAutoOpened()
        assertFalse(opened.shouldAutoOpen(isReady = true, trigger = ConnectionTrigger.AclConnected))
        assertFalse(opened.shouldAutoOpen(isReady = true, trigger = ConnectionTrigger.ServiceCommand))
    }

    @Test
    fun userBackSuppressesOnlyCurrentSessionAndManualOpenClearsSuppression() {
        val state = DeviceNavigationState()
            .observeSession(session)
            .markAutoOpened()
            .markUserLeft()

        assertFalse(state.shouldAutoOpen(isReady = true, trigger = ConnectionTrigger.AclConnected))
        assertFalse(state.markUserOpened().shouldAutoOpen(isReady = true, trigger = ConnectionTrigger.AclConnected))
    }

    @Test
    fun newAttemptResetsPreviousNavigationDecision() {
        val replacement = session.copy(attemptId = "attempt-2")
        val state = DeviceNavigationState()
            .observeSession(session)
            .markAutoOpened()
            .markUserLeft()
            .observeSession(replacement)

        assertTrue(state.shouldAutoOpen(isReady = true, trigger = ConnectionTrigger.ServiceCommand))
    }

    @Test
    fun userAndRegressionTriggersNeverCauseImplicitNavigation() {
        val state = DeviceNavigationState().observeSession(session)

        assertFalse(state.shouldAutoOpen(isReady = true, trigger = ConnectionTrigger.UserAction))
        assertFalse(state.shouldAutoOpen(isReady = true, trigger = ConnectionTrigger.TileAction))
        assertFalse(state.shouldAutoOpen(isReady = true, trigger = ConnectionTrigger.HardwareRegression))
    }
}
