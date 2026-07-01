package com.freebuds.controller.core.protocol

import com.freebuds.controller.core.transport.EarbudTransport

/** Vendor-neutral protocol boundary above a raw transport. */
interface EarbudProtocol<P : ProtocolPacket> {
    val id: String

    fun encode(packet: P): ByteArray
    fun tryDecode(frame: ByteArray): P?
    fun createFramer(): ProtocolFramer<P>
    fun createSession(transport: EarbudTransport): ProtocolSession<P> = ProtocolSession(this, transport)
}

interface ProtocolPacket {
    val commandKey: String
    val responseKey: String
}

/**
 * Incremental framer: accepts arbitrary raw bytes from a transport and emits complete protocol
 * frames. This isolates stream reassembly from Bluetooth connection management.
 */
interface ProtocolFramer<P : ProtocolPacket> {
    fun accept(raw: ByteArray): List<P>
    fun reset()
}

class ProtocolSession<P : ProtocolPacket>(
    private val protocol: EarbudProtocol<P>,
    private val transport: EarbudTransport,
) {
    private val framer = protocol.createFramer()
    private var packetListener: (suspend (P) -> Unit)? = null

    fun setPacketListener(listener: (suspend (P) -> Unit)?) {
        packetListener = listener
        transport.setPacketListener { raw ->
            val packets = framer.accept(raw)
            for (packet in packets) packetListener?.invoke(packet)
        }
    }

    suspend fun connect(): Boolean = transport.connect()
    fun disconnect() = transport.disconnect()
    suspend fun send(packet: P) = transport.send(protocol.encode(packet))
    fun setDisconnectListener(listener: (() -> Unit)?) = transport.setDisconnectListener(listener)
}