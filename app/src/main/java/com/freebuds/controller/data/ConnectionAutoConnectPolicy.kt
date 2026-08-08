package com.freebuds.controller.data

/**
 * Pure backoff policy for system-triggered control-channel connects.
 *
 * Forced events bypass the current window but still update the next normal attempt deadline. A
 * rejected attempt doubles the delay up to the cap; an accepted attempt returns to the initial
 * delay. Keeping this state out of the Android Service makes the trigger policy deterministic and
 * unit-testable.
 */
class ConnectionAutoConnectPolicy(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val initialBackoffMs: Long = 5_000L,
    private val maxBackoffMs: Long = 60_000L,
) {
    private var nextAttemptAtMs: Long = 0L
    private var backoffMs: Long = initialBackoffMs

    fun canAttempt(force: Boolean = false): Boolean = force || nowMs() >= nextAttemptAtMs

    fun recordAccepted() {
        backoffMs = initialBackoffMs
        nextAttemptAtMs = nowMs() + backoffMs
    }

    fun recordRejected() {
        nextAttemptAtMs = nowMs() + backoffMs
        backoffMs = (backoffMs * 2L).coerceAtMost(maxBackoffMs)
    }

    fun nextAttemptAt(): Long = nextAttemptAtMs
    fun currentBackoffMs(): Long = backoffMs
}

/**
 * Background profile/periodic probes must not steal the connection slot from the in-app
 * hardware regression matrix. Explicit user, tile, ACL and regression commands remain allowed.
 */
fun isBackgroundAutoConnectSuppressed(
    trigger: ConnectionTrigger,
    hardwareRegressionActive: Boolean,
): Boolean = hardwareRegressionActive && trigger in setOf(
    ConnectionTrigger.PeriodicCheck,
    ConnectionTrigger.AudioProfileConnected,
)
