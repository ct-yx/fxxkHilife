package com.freebuds.controller.adapter.huawei.protocol

import com.freebuds.controller.protocol.HuaweiSppPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiSppFramerTest {
    @Test
    fun acceptsFrameSplitAtEveryByteBoundary() {
        val packet = packet(command = byteArrayOf(0x2b, 0x2a), parameter = 1 to byteArrayOf(2, 1))
        val bytes = packet.toBytes()
        val frameSize = 4 + (bytes[2].toInt() and 0xff)

        for (split in 1 until bytes.size) {
            val framer = HuaweiSppFramer()
            val first = framer.accept(bytes.copyOfRange(0, split))
            if (split < frameSize) {
                assertTrue("split=$split", first.isEmpty())
            } else {
                assertEquals("split=$split", listOf("2b2a"), first.map { it.commandKey })
            }
            val decoded = framer.accept(bytes.copyOfRange(split, bytes.size))
            val allDecoded = first + decoded
            assertEquals("split=$split", listOf("2b2a"), allDecoded.map { it.commandKey })
            assertEquals(byteArrayOf(2, 1).toList(), allDecoded.single().findParam(1).toList())
        }
    }

    @Test
    fun retainsMagicWhenMarkerIsSplitAcrossReads() {
        val packet = packet(command = byteArrayOf(0x01, 0x07), parameter = 3 to byteArrayOf(0x41))
        val bytes = packet.toBytes()
        val framer = HuaweiSppFramer()

        assertTrue(framer.accept(byteArrayOf(0x13, 0x5a)).isEmpty())
        val decoded = framer.accept(bytes.copyOfRange(1, bytes.size))

        assertEquals(1, decoded.size)
        assertEquals("0107", decoded.single().commandKey)
        assertEquals(listOf(0x41.toByte()), decoded.single().findParam(3).toList())
    }

    @Test
    fun emitsMultipleFramesAndResynchronizesAfterNoise() {
        val first = packet(command = byteArrayOf(0x2b, 0x2a), parameter = 1 to byteArrayOf(1))
        val second = packet(command = byteArrayOf(0x2b, 0x6c), parameter = 2 to byteArrayOf(0))
        val raw = byteArrayOf(0x01, 0x7f) + first.toBytes() + second.toBytes()

        val decoded = HuaweiSppFramer().accept(raw)

        assertEquals(listOf("2b2a", "2b6c"), decoded.map { it.commandKey })
        assertNotNull(decoded[0].findParam(1))
        assertEquals(listOf(0.toByte()), decoded[1].findParam(2).toList())
    }

    @Test
    fun resetDiscardsPartialFrameFromPreviousConnection() {
        val packet = packet(command = byteArrayOf(0x2b, 0x03), parameter = 8 to byteArrayOf(1))
        val bytes = packet.toBytes()
        val framer = HuaweiSppFramer()

        framer.accept(bytes.copyOfRange(0, 3))
        framer.reset()

        assertTrue(framer.accept(bytes).isNotEmpty())
    }

    private fun packet(command: ByteArray, parameter: Pair<Int, ByteArray>): HuaweiSppPackage =
        HuaweiSppPackage(
            commandId = command,
            parameters = mutableMapOf(parameter.first to parameter.second),
        )
}
