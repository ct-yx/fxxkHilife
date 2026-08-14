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
        val frameSize = 5 + ((bytes[1].toInt() and 0xff) shl 8) + (bytes[2].toInt() and 0xff)
        assertEquals(bytes.size, frameSize)

        for (split in 1 until bytes.size) {
            val framer = HuaweiSppFramer()
            val first = framer.accept(bytes.copyOfRange(0, split))
            assertTrue("split=$split", first.isEmpty())
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
    fun emitsAnEmbeddedFrameUsingTheCompleteNestedFrameLength() {
        val nested = packet(command = byteArrayOf(0x2b, 0x6c), parameter = 2 to byteArrayOf(1))
        val outer = HuaweiSppPackage(
            commandId = byteArrayOf(0x01, 0x07),
            parameters = mutableMapOf(0x7f to nested.toBytes()),
        )

        val decoded = HuaweiSppFramer().accept(outer.toBytes())

        assertEquals(listOf("0107", "2b6c"), decoded.map { it.commandKey })
    }

    @Test
    fun skipsInvalidShortHeaderBeforeAValidFrame() {
        val valid = packet(command = byteArrayOf(0x2b, 0x03), parameter = 8 to byteArrayOf(1)).toBytes()
        val invalidHeader = byteArrayOf(0x5a, 0x00, 0x02, 0x00, 0x01, 0x02)

        val decoded = HuaweiSppFramer().accept(invalidHeader + valid)

        assertEquals(listOf("2b03"), decoded.map { it.commandKey })
    }

    @Test
    fun acceptsFramesWhoseLengthUsesTheHighByteAndKeepsBothChecksumBytes() {
        val firstValue = ByteArray(200) { (it and 0xff).toByte() }
        val secondValue = ByteArray(200) { ((it + 1) and 0xff).toByte() }
        val packet = HuaweiSppPackage(
            commandId = byteArrayOf(0x2b, 0x4a),
            parameters = mutableMapOf(8 to firstValue, 9 to secondValue),
        )
        val bytes = packet.toBytes()

        assertTrue((bytes[1].toInt() and 0xff) > 0)
        val decoded = HuaweiSppFramer().accept(bytes).single()
        assertEquals(packet.commandKey, decoded.commandKey)
        assertEquals(firstValue.toList(), decoded.findParam(8).toList())
        assertEquals(secondValue.toList(), decoded.findParam(9).toList())
        assertTrue(HuaweiSppPackage.fromBytes(bytes, validateChecksum = true) != null)
    }

    @Test
    fun rejectsTruncatedChecksumAndParameterThatCrossesPayloadBoundary() {
        val bytes = packet(command = byteArrayOf(0x01, 0x08), parameter = 1 to byteArrayOf(0x40)).toBytes()

        assertTrue(HuaweiSppPackage.fromBytes(bytes.copyOf(bytes.size - 1)) == null)

        val malformed = bytes.copyOf().also { it[7] = 0x7f }
        assertTrue(HuaweiSppPackage.fromBytes(malformed) == null)
    }

    @Test
    fun rejectsBadChecksumOnlyWhenChecksumValidationIsRequested() {
        val bytes = packet(command = byteArrayOf(0x2b, 0x2a), parameter = 1 to byteArrayOf(1)).toBytes()
        val corrupted = bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 0x01).toByte() }

        assertTrue(HuaweiSppPackage.fromBytes(corrupted) != null)
        assertTrue(HuaweiSppPackage.fromBytes(corrupted, validateChecksum = true) == null)
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
