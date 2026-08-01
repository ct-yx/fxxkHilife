package com.freebuds.controller.adapter.huawei.protocol

import com.freebuds.controller.protocol.HuaweiSppPackage
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HuaweiPendingResponseManagerTest {
    @Test
    fun distinctResponseIdsCanBeAcquiredConcurrently() = runBlocking {
        val manager = HuaweiPendingResponseManager()
        val first = manager.register("0108", 100)
        val second = manager.register("2b2a", 100)

        assertNotNull(first)
        assertNotNull(second)
        manager.remove("0108", first!!)
        manager.remove("2b2a", second!!)
    }

    @Test
    fun sameResponseIdWaitsForItsOwnPreviousSlot() = runBlocking {
        val manager = HuaweiPendingResponseManager()
        val first = manager.register("2b2a", 100)!!
        val second = async { manager.register("2b2a", 500) }

        delay(50)
        assertFalse(second.isCompleted)
        manager.remove("2b2a", first)

        val acquired = withTimeout(300) { second.await() }
        assertNotNull(acquired)
        manager.remove("2b2a", acquired!!)
    }

    @Test
    fun sameResponseIdHasABoundedWait() = runBlocking {
        val manager = HuaweiPendingResponseManager()
        val first = manager.register("2b2a", 100)!!

        assertNull(manager.register("2b2a", 80))
        manager.remove("2b2a", first)
    }

    @Test
    fun responsePredicateLeavesAnAckForTheCorrectReadback() = runBlocking {
        val manager = HuaweiPendingResponseManager()
        val pending = manager.register("2b6c", 500) { it.findParam(2).isNotEmpty() }!!
        val ack = HuaweiSppPackage(
            commandId = byteArrayOf(0x2b, 0x6c),
            parameters = mutableMapOf(127 to byteArrayOf(1)),
        )
        val readback = HuaweiSppPackage(
            commandId = byteArrayOf(0x2b, 0x6c),
            parameters = mutableMapOf(2 to byteArrayOf(1)),
        )

        assertFalse(manager.complete("2b6c", ack))
        assertTrue(manager.complete("2b6c", readback))
        assertSame(readback, pending.await())
        manager.remove("2b6c", pending)
    }
}
