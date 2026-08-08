package com.freebuds.controller.adapter.huawei.protocol

import com.freebuds.controller.bluetooth.HuaweiDeviceHandler
import com.freebuds.controller.protocol.HuaweiCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiCommandCatalogTest {
    @Test
    fun coreFeaturesShareCanonicalReadWriteIds() {
        val anc = HuaweiCommandCatalog.anc
        assertEquals("anc", anc.key)
        assertTrue(anc.readCommand.contentEquals(byteArrayOf(0x2b, 0x2a)))
        assertTrue(anc.writeCommand!!.contentEquals(byteArrayOf(0x2b, 0x04)))
        assertEquals(listOf(1, 2), anc.readParameters.toList())
        assertTrue(anc.incomingCommandIds.any { it.contentEquals(byteArrayOf(0x2b, 0x2c)) })

        val lowLatency = HuaweiCommandCatalog.lowLatency
        assertTrue(lowLatency.readCommand.contentEquals(byteArrayOf(0x2b, 0x6c)))
        assertTrue(lowLatency.writeCommand!!.contentEquals(byteArrayOf(0x2b, 0x6c)))
        assertEquals(listOf(2), lowLatency.readParameters.toList())
    }

    @Test
    fun catalogBuildsReadAndWritePackagesWithoutInlineByteArrays() {
        val spec = HuaweiCommandCatalog.soundQuality
        val read = spec.readRequest()
        val write = spec.writeRequest(1 to byteArrayOf(1))

        assertNotNull(read)
        assertTrue(read.commandId.contentEquals(byteArrayOf(0x2b, 0xa3.toByte())))
        assertTrue(read.parameters.containsKey(1))
        assertTrue(write.commandId.contentEquals(byteArrayOf(0x2b, 0xa2.toByte())))
        assertEquals(1, write.findParam(1).first().toInt())
    }

    @Test
    fun registryKeepsMultipleObserversForSharedNotificationCommand() {
        val registry = HuaweiHandlerRegistry()
        val command = byteArrayOf(0x2b, 0x03)

        registry.register(testHandler("first", command))
        registry.register(testHandler("second", command))

        assertEquals(listOf("first", "second"), registry.handlersForCommand("2b03").map { it.id })
    }

    private fun testHandler(id: String, command: ByteArray): HuaweiDeviceHandler = object : HuaweiDeviceHandler {
        private val handlerId = id
        override val id: String get() = handlerId
        override val commandIds = listOf(command)
        override val capabilities = emptyList<HuaweiCapability>()
    }
}
