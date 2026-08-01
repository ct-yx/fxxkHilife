package com.freebuds.controller.core.protocol

import com.freebuds.controller.adapter.huawei.protocol.HuaweiSppProtocol
import com.freebuds.controller.core.transport.EarbudTransport
import com.freebuds.controller.protocol.HuaweiSppPackage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolSessionTest {
    @Test
    fun routesSplitTransportChunksThroughTheProtocolFramer() = runBlocking {
        val transport = FakeTransport()
        val session = HuaweiSppProtocol.createSession(transport)
        val received = mutableListOf<HuaweiSppPackage>()
        session.setPacketListener { received += it }

        assertTrue(session.connect())
        val bytes = packet().toBytes()
        transport.emit(bytes.copyOfRange(0, 3))
        transport.emit(bytes.copyOfRange(3, bytes.size))

        assertEquals(listOf("2b6c"), received.map { it.commandKey })
    }

    @Test
    fun reconnectResetsPartialFrameState() = runBlocking {
        val transport = FakeTransport()
        val session = HuaweiSppProtocol.createSession(transport)
        val received = mutableListOf<HuaweiSppPackage>()
        session.setPacketListener { received += it }

        session.connect()
        val bytes = packet().toBytes()
        transport.emit(bytes.copyOfRange(0, 3))
        session.disconnect()
        session.connect()
        transport.emit(bytes)

        assertEquals(1, received.size)
        assertEquals("2b6c", received.single().commandKey)
    }

    private fun packet() = HuaweiSppPackage(
        commandId = byteArrayOf(0x2b, 0x6c),
        parameters = mutableMapOf(2 to byteArrayOf(1)),
    )

    private class FakeTransport : EarbudTransport {
        override val id: String = "fake"
        override var isConnected: Boolean = false
        private var packetListener: (suspend (ByteArray) -> Unit)? = null
        private var disconnectListener: (() -> Unit)? = null

        override suspend fun connect(): Boolean {
            isConnected = true
            return true
        }

        override fun disconnect() {
            isConnected = false
            disconnectListener?.invoke()
        }

        override suspend fun send(raw: ByteArray) = Unit

        override fun setPacketListener(listener: (suspend (ByteArray) -> Unit)?) {
            packetListener = listener
        }

        override fun setDisconnectListener(listener: (() -> Unit)?) {
            disconnectListener = listener
        }

        suspend fun emit(raw: ByteArray) {
            packetListener?.invoke(raw)
        }
    }
}
