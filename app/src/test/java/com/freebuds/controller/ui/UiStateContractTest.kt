package com.freebuds.controller.ui

import com.freebuds.controller.data.ControlChannelStage
import com.freebuds.controller.data.ControlChannelState
import com.freebuds.controller.data.SystemLinkState
import com.freebuds.controller.ui.foundation.components.OptionPresenter
import com.freebuds.controller.ui.foundation.components.UiTextMapper
import com.freebuds.controller.ui.state.ConnectionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateContractTest {
    @Test
    fun connectionSummaryKeepsSystemAndControlStatesSeparate() {
        val summary = ConnectionSummary.from(
            ControlChannelState(
                systemLink = SystemLinkState.Connected,
                stage = ControlChannelStage.CoreReady,
                endpoint = "rfcomm-channel=1",
                attemptId = "attempt-1",
            )
        )

        assertTrue(summary.systemConnected)
        assertEquals(ControlChannelStage.CoreReady, summary.stage)
        assertEquals("rfcomm-channel=1", summary.endpoint)
        assertFalse(summary.isReady)
    }

    @Test
    fun optionPresenterMarksSelectedAndPendingValues() {
        val state = OptionPresenter.present(
            values = listOf("normal", "cancellation", "awareness"),
            selectedValue = "normal",
            pendingValue = "cancellation",
            mapper = UiTextMapper { it.uppercase() },
        )

        assertEquals("NORMAL", state.options.first { it.selected }.label)
        assertEquals("CANCELLATION", state.options.first { it.pending }.label)
        assertTrue(state.isPending)
        assertTrue(state.isAvailable)
    }
}
