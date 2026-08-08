package com.freebuds.controller.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionAttemptWaitPolicyTest {
    @Test
    fun matchingFailureIsTerminalInsteadOfWaitingForTheFullTimeout() {
        assertEquals(
            ConnectionAttemptWaitResult.Failed,
            connectionAttemptWaitResult(
                ConnectionState.Failed("transport connect returned false", "attempt-1"),
                expectedAttemptId = "attempt-1",
            ),
        )
    }

    @Test
    fun anOlderAttemptFailureDoesNotTerminateTheCurrentWait() {
        assertEquals(
            ConnectionAttemptWaitResult.Pending,
            connectionAttemptWaitResult(
                ConnectionState.Failed("old attempt", "attempt-old"),
                expectedAttemptId = "attempt-current",
            ),
        )
    }

    @Test
    fun matchingConnectedAttemptCompletesTheWait() {
        assertEquals(
            ConnectionAttemptWaitResult.Connected,
            connectionAttemptWaitResult(
                ConnectionState.Connected("FreeBuds", "attempt-1"),
                expectedAttemptId = "attempt-1",
            ),
        )
    }
}
