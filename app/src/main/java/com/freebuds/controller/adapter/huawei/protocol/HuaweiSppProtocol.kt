package com.freebuds.controller.adapter.huawei.protocol

import com.freebuds.controller.core.protocol.EarbudProtocol
import com.freebuds.controller.core.protocol.ProtocolFramer
import com.freebuds.controller.protocol.HuaweiSppPackage

/** Huawei/OpenFreebuds SPP 5A packet protocol. */
object HuaweiSppProtocol : EarbudProtocol<HuaweiSppPackage> {
    override val id: String = "huawei_spp_5a"

    override fun encode(packet: HuaweiSppPackage): ByteArray = packet.toBytes()

    override fun tryDecode(frame: ByteArray): HuaweiSppPackage? = HuaweiSppPackage.fromBytes(frame)

    override fun createFramer(): ProtocolFramer<HuaweiSppPackage> = HuaweiSppFramer()
}

/**
 * Incremental decoder for Huawei SPP frames.
 *
 * RFCOMM reads may return arbitrary chunks, so this framer maintains a small byte buffer and
 * emits all complete 5A frames, including embedded frames observed in some logs.
 */
class HuaweiSppFramer : ProtocolFramer<HuaweiSppPackage> {
    private val buffer = ArrayList<Byte>(4096)

    override fun accept(raw: ByteArray): List<HuaweiSppPackage> {
        raw.forEach { buffer.add(it) }
        val out = mutableListOf<HuaweiSppPackage>()

        while (true) {
            val start = findMagic()
            if (start < 0) {
                // Keep a trailing 0x5a: the following read may contain the second magic byte.
                // Clearing it loses a frame whenever the transport splits the two-byte magic
                // marker across reads.
                val trailing = buffer.lastOrNull()
                buffer.clear()
                if (trailing == 0x5A.toByte()) buffer.add(trailing)
                return out
            }
            if (start > 0) repeat(start) { buffer.removeAt(0) }
            if (buffer.size < 4) return out

            if (buffer[3] != 0x00.toByte()) {
                buffer.removeAt(0)
                continue
            }

            val length = ((buffer[1].toInt() and 0xFF) shl 8) or
                (buffer[2].toInt() and 0xFF)
            if (length < 3) {
                buffer.removeAt(0)
                continue
            }

            // The length field includes the upstream offset byte but not both checksum bytes.
            // A complete frame is therefore length + 5 bytes, not length + 4.
            val frameSize = length + 5
            if (buffer.size < frameSize) return out

            val frame = ByteArray(frameSize) { buffer[it] }
            repeat(frameSize) { buffer.removeAt(0) }
            HuaweiSppPackage.fromBytes(frame)?.let { out.add(it) }
            out.addAll(extractEmbedded(frame))
        }
    }

    override fun reset() {
        buffer.clear()
    }

    private fun findMagic(): Int {
        for (i in buffer.indices) {
            if (buffer[i] == 0x5A.toByte()) return i
        }
        return -1
    }

    private fun extractEmbedded(frame: ByteArray): List<HuaweiSppPackage> {
        val out = mutableListOf<HuaweiSppPackage>()
        var pos = 4
        while (pos + 4 <= frame.size) {
            if (frame[pos] == 0x5A.toByte() && frame[pos + 3] == 0x00.toByte()) {
                val len = ((frame[pos + 1].toInt() and 0xFF) shl 8) or
                    (frame[pos + 2].toInt() and 0xFF)
                val end = pos + len + 5
                if (len >= 3 && end <= frame.size) {
                    HuaweiSppPackage.fromBytes(frame.copyOfRange(pos, end))?.let { out.add(it) }
                    pos = end
                    continue
                }
            }
            pos++
        }
        return out
    }
}
