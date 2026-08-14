package com.freebuds.controller.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiSppPackageFixtureTest {
    @Test
    fun upstreamBatteryFixturesDecodeWithChecksumAndRoundTrip() {
        val request = readFixture("get_battery_request.hex")
        val response = readFixture("battery_response_base.hex")
        val legacy = readFixture("battery_response_legacy.hex")

        val requestPackage = HuaweiSppPackage.fromBytes(request, validateChecksum = true)
        val responsePackage = HuaweiSppPackage.fromBytes(response, validateChecksum = true)
        val legacyPackage = HuaweiSppPackage.fromBytes(legacy, validateChecksum = true)

        assertNotNull(requestPackage)
        assertNotNull(responsePackage)
        assertNotNull(legacyPackage)
        assertEquals("0108", requestPackage!!.commandKey)
        assertEquals(listOf(1, 2, 3), requestPackage.parameters.keys.toList())
        assertEquals(listOf(0x40.toByte()), responsePackage!!.findParam(1).toList())
        assertEquals(listOf(0x10, 0x20, 0x30).map { it.toByte() }, responsePackage.findParam(2).toList())
        assertEquals(listOf(0x40.toByte()), legacyPackage!!.findParam(1).toList())
        assertTrue(requestPackage.toBytes().contentEquals(request))
        assertTrue(responsePackage.toBytes().contentEquals(response))
        assertTrue(legacyPackage.toBytes().contentEquals(legacy))
    }

    @Test
    fun upstreamAutoPauseFixturesPreserveReadWriteCommandShapes() {
        val readRequest = HuaweiSppPackage.fromBytes(
            readFixture("get_auto_pause_request.hex"),
            validateChecksum = true,
        )
        val readResponse = HuaweiSppPackage.fromBytes(
            readFixture("auto_pause_response.hex"),
            validateChecksum = true,
        )
        val writeRequest = HuaweiSppPackage.fromBytes(
            readFixture("set_auto_pause_on_request.hex"),
            validateChecksum = true,
        )
        val writeResponse = HuaweiSppPackage.fromBytes(
            readFixture("set_auto_pause_on_response.hex"),
            validateChecksum = true,
        )

        assertEquals("2b11", readRequest?.commandKey)
        assertEquals("2b11", readResponse?.commandKey)
        assertEquals("2b10", writeRequest?.commandKey)
        assertEquals("2b10", writeResponse?.commandKey)
        assertEquals(listOf(0.toByte()), readResponse?.findParam(1)?.toList())
        assertEquals(listOf(1.toByte()), writeRequest?.findParam(1)?.toList())
        assertTrue(writeResponse?.findParam(0x7f)?.isNotEmpty() == true)
    }

    private fun readFixture(name: String): ByteArray {
        val stream = checkNotNull(javaClass.getResourceAsStream("/fixtures/huawei_spp/$name"))
        val hex = stream.bufferedReader().use { it.readText() }.filterNot(Char::isWhitespace)
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
