package com.freebuds.controller.adapter.huawei.protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay

/**
 * Priority labels used by the single Huawei SPP command lane.
 *
 * A command can never overtake an exchange that is already on the wire, but a user action can
 * move ahead of queued background/deferred work.  This is deliberately a small scheduler rather
 * than a second transport queue: the caller coroutine keeps ownership of cancellation and the
 * scheduler only grants one lane permit at a time.
 */
enum class HuaweiCommandPriority {
    USER_ACTION,
    CORE_INITIALIZATION,
    DEFERRED_INITIALIZATION,
    BACKGROUND,
}

class HuaweiCommandScheduler(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private data class Ticket(
        val priority: HuaweiCommandPriority,
        val sequence: Long,
        val operation: String,
        val granted: CompletableDeferred<Unit> = CompletableDeferred(),
        val callerJob: Job?,
        var isGranted: Boolean = false,
    )

    private val lock = Any()
    private val queue = ArrayList<Ticket>()
    private var active: Ticket? = null
    private var nextSequence = 0L
    private var quietUntilMs = 0L

    /**
     * Run one operation on the lane.  Queue ordering is priority-first and FIFO within a
     * priority.  Cancellation while waiting removes the ticket; cancellation while executing
     * releases the lane in the same finally block.
     */
    suspend fun <T> execute(
        priority: HuaweiCommandPriority,
        operation: String,
        block: suspend () -> T,
    ): T {
        val callerJob = currentCoroutineContext()[Job]
        val ticket = synchronized(lock) {
            Ticket(
                priority = priority,
                sequence = nextSequence++,
                operation = operation,
                callerJob = callerJob,
            ).also {
                queue += it
                grantNextLocked()
            }
        }

        try {
            ticket.granted.await()
            val quietDelay = synchronized(lock) {
                (quietUntilMs - nowMs()).coerceAtLeast(0L)
            }
            if (quietDelay > 0L) sleep(quietDelay)
            return block()
        } finally {
            synchronized(lock) {
                if (ticket.isGranted) {
                    if (active === ticket) active = null
                    grantNextLocked()
                } else {
                    queue.remove(ticket)
                    ticket.granted.cancel()
                }
            }
        }
    }

    /** Prevents the next queued exchange from using the wire before this wall-clock deadline. */
    fun setQuietUntil(untilMs: Long) {
        synchronized(lock) {
            quietUntilMs = maxOf(quietUntilMs, untilMs)
        }
    }

    /**
     * Cancels queued work and the caller currently owning the lane.  The scheduler remains
     * reusable after a disconnect/reconnect; a later command can acquire a fresh permit.
     */
    fun cancelAll() {
        val activeJob = synchronized(lock) {
            queue.forEach { it.granted.cancel(CancellationException("command lane cancelled")) }
            queue.clear()
            active?.callerJob
        }
        activeJob?.cancel(CancellationException("command lane cancelled"))
    }

    private fun grantNextLocked() {
        if (active != null) return
        while (queue.isNotEmpty()) {
            val nextIndex = queue.indices.minWithOrNull(
                compareBy<Int>({ queue[it].priority.ordinal }, { queue[it].sequence })
            ) ?: return
            val next = queue.removeAt(nextIndex)
            if (next.callerJob?.isActive == false) {
                next.granted.cancel()
                continue
            }
            next.isGranted = true
            active = next
            next.granted.complete(Unit)
            return
        }
    }
}
