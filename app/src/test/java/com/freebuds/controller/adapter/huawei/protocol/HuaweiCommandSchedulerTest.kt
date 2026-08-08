package com.freebuds.controller.adapter.huawei.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HuaweiCommandSchedulerTest {
    @Test
    fun userActionMovesAheadOfQueuedDeferredWorkWithoutOvertakingActiveExchange() = runBlocking {
        val scheduler = HuaweiCommandScheduler()
        val order = mutableListOf<String>()
        val backgroundStarted = CompletableDeferred<Unit>()
        val releaseBackground = CompletableDeferred<Unit>()

        val background = async {
            scheduler.execute(HuaweiCommandPriority.BACKGROUND, "background") {
                order += "background-start"
                backgroundStarted.complete(Unit)
                releaseBackground.await()
                order += "background-end"
            }
        }
        backgroundStarted.await()

        val deferred = async {
            scheduler.execute(HuaweiCommandPriority.DEFERRED_INITIALIZATION, "deferred") {
                order += "deferred-start"
                order += "deferred-end"
            }
        }
        val user = async {
            scheduler.execute(HuaweiCommandPriority.USER_ACTION, "user") {
                order += "user-start"
                order += "user-end"
            }
        }
        // Let both requests enqueue while the background operation still owns the lane.
        delay(20)
        releaseBackground.complete(Unit)

        background.await()
        user.await()
        deferred.await()

        assertEquals(
            listOf(
                "background-start",
                "background-end",
                "user-start",
                "user-end",
                "deferred-start",
                "deferred-end",
            ),
            order,
        )
    }

    @Test
    fun quietWindowIsAppliedBeforeTheGrantedOperation() = runBlocking {
        var now = 1_000L
        val sleeps = mutableListOf<Long>()
        val scheduler = HuaweiCommandScheduler(
            nowMs = { now },
            sleep = { duration ->
                sleeps += duration
                now += duration
            },
        )

        scheduler.setQuietUntil(1_500L)
        scheduler.execute(HuaweiCommandPriority.CORE_INITIALIZATION, "core") { }

        assertEquals(listOf(500L), sleeps)
    }
}
