package com.freebuds.controller.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EarbudStateStoreTest {
    @Test
    fun concurrentHandlerSnapshotCannotRestoreAnOlderInitializationStage() {
        val store = EarbudStateStore()
        val attemptId = "attempt-1"
        store.beginAttempt(
            deviceName = "Buds",
            address = "AA",
            attemptId = attemptId,
            trigger = ConnectionTrigger.AclConnected,
            systemLink = SystemLinkState.Connected,
        )
        val attempt = ConnectionAttemptTimeline(
            attemptId = attemptId,
            address = "AA",
            trigger = ConnectionTrigger.AclConnected,
            nowMs = { 0L },
        )
        store.updateStage(
            stage = ControlChannelStage.InitializingDeferred,
            attempt = attempt,
            systemLink = SystemLinkState.Connected,
            pendingHandlers = setOf("gesture_double"),
            failedHandlers = emptySet(),
        )

        val transformEntered = CountDownLatch(1)
        val releaseTransform = CountDownLatch(1)
        val invocations = AtomicInteger(0)
        val worker = thread(start = true) {
            store.updateControlChannel { state ->
                if (invocations.incrementAndGet() == 1) {
                    transformEntered.countDown()
                    assertTrue(releaseTransform.await(2, TimeUnit.SECONDS))
                }
                state.copy(pendingHandlers = emptySet())
            }
        }

        assertTrue(transformEntered.await(2, TimeUnit.SECONDS))
        store.updateStage(
            stage = ControlChannelStage.Ready,
            attempt = attempt,
            systemLink = SystemLinkState.Connected,
            pendingHandlers = emptySet(),
            failedHandlers = emptySet(),
        )
        releaseTransform.countDown()
        worker.join(2_000)

        assertTrue(!worker.isAlive)
        assertEquals(ControlChannelStage.Ready, store.controlChannelState.value.stage)
        assertEquals(emptySet<String>(), store.controlChannelState.value.pendingHandlers)
    }

    @Test
    fun stageAndHandlerUpdatesRemainBoundToTheActiveAttempt() {
        val store = EarbudStateStore()
        store.beginAttempt(
            deviceName = "FreeBuds",
            address = "AA",
            attemptId = "attempt-1",
            trigger = ConnectionTrigger.UserAction,
            systemLink = SystemLinkState.Connected,
        )
        store.updateStage(
            stage = ControlChannelStage.CoreReady,
            attempt = ConnectionAttemptTimeline(
                attemptId = "attempt-1",
                address = "AA",
                trigger = ConnectionTrigger.UserAction,
                nowMs = { 1L },
            ),
            systemLink = SystemLinkState.Connected,
            pendingHandlers = setOf("device_info"),
            failedHandlers = emptySet(),
        )
        store.updateHandlers(
            activeAttemptId = "attempt-2",
            pendingHandlers = setOf("stale"),
            failedHandlers = setOf("stale"),
        )

        val state = store.controlChannelState.value
        assertEquals(ControlChannelStage.CoreReady, state.stage)
        assertEquals(setOf("device_info"), state.pendingHandlers)
        assertEquals(emptySet<String>(), state.failedHandlers)
    }

    @Test
    fun propertyProjectionIsUpdatedWithoutExposingMutableFlow() {
        val store = EarbudStateStore()
        val initial = store.props
        store.updateProps { it.copy(lowLatency = true) }

        assertSame(initial, store.props)
        assertEquals(true, store.props.value.lowLatency)
    }

    @Test
    fun compatibilityFlowsConvergeToTheLatestCanonicalSnapshotAfterConcurrentUpdates() {
        val store = EarbudStateStore()
        val workers = List(4) { index ->
            thread(start = true) {
                repeat(50) { round ->
                    store.updateProps { it.copy(lowLatency = (index + round) % 2 == 0) }
                    store.updateControlChannel { it.copy(reason = "$index-$round") }
                }
            }
        }
        workers.forEach { it.join(2_000) }

        val snapshot = store.snapshot.value
        assertEquals(snapshot.state, store.earbudState.value)
        assertEquals(snapshot.props, store.props.value)
        assertEquals(snapshot.controlChannel, store.controlChannelState.value)
        assertEquals(snapshot.capabilities, store.capabilities.value)
    }

    @Test
    fun compatibilityPropertyWritesKeepPendingHandlersAlignedWithChannel() {
        val store = EarbudStateStore()

        store.updateProps { it.copy(pendingInitHandlers = listOf("optional_probe")) }

        assertEquals(
            setOf("optional_probe"),
            store.snapshot.value.controlChannel.pendingHandlers,
        )
        assertEquals(store.snapshot.value.controlChannel, store.controlChannelState.value)
    }
}
