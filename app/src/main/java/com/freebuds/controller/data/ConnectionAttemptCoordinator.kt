package com.freebuds.controller.data

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

data class ConnectionAttemptReservation(
    val attempt: ConnectionAttemptTimeline,
    val ownsAttempt: Boolean,
)

/**
 * Small, vendor-neutral reservation boundary for one device control-channel attempt.
 *
 * The repository still owns state flows and session side effects, while this class owns the
 * invariant that duplicate entry points reuse one active attempt. Keeping that invariant in a
 * pure object makes the Service/Tile/ACL deduplication rule testable before the full connection
 * manager is extracted.
 */
class ConnectionAttemptCoordinator(
    private val wallClockMs: () -> Long = { System.currentTimeMillis() },
    private val monotonicClockMs: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val lock = Any()
    private val sequence = AtomicLong(0L)
    private var active: ConnectionAttemptTimeline? = null

    fun reserve(
        address: String,
        trigger: ConnectionTrigger,
        busy: Boolean,
    ): ConnectionAttemptReservation? = synchronized(lock) {
        if (busy) {
            val current = active ?: return@synchronized null
            // A duplicate trigger for the same device may wait on the existing attempt. A
            // request for another address must not inherit that attempt id: callers would then
            // wait for the wrong device and the service could report a false deduplication hit.
            if (!current.address.equals(address, ignoreCase = true)) return@synchronized null
            return@synchronized ConnectionAttemptReservation(current, ownsAttempt = false)
        }

        ConnectionAttemptTimeline(
            attemptId = "attempt-${wallClockMs()}-${sequence.incrementAndGet()}",
            address = address,
            trigger = trigger,
            nowMs = monotonicClockMs,
        ).also { active = it }
            .let { ConnectionAttemptReservation(it, ownsAttempt = true) }
    }

    fun current(): ConnectionAttemptTimeline? = synchronized(lock) { active }

    fun isCurrent(attempt: ConnectionAttemptTimeline): Boolean = synchronized(lock) {
        active === attempt
    }

    fun clearIfCurrent(attempt: ConnectionAttemptTimeline): Boolean = synchronized(lock) {
        if (active !== attempt) return@synchronized false
        active = null
        true
    }

    fun clear(): ConnectionAttemptTimeline? = synchronized(lock) {
        val previous = active
        active = null
        previous
    }
}
