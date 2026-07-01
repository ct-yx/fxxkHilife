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
 * Existing SppDriver reads exact frames from InputStream. This framer is for the new Transport
 * path where reads may return arbitrary chunks, so it maintains a small byte buffer and emits all
 * complete 5A frames, including embedded frames observed in some logs.
 */
class HuaweiSppFramer : ProtocolFramer<HuaweiSppPackage> {
    private val buffer = ArrayList<Byte>(4096)

    override fun accept(raw: ByteArray): List<HuaweiSppPackage> {
        raw.forEach { buffer.add(it) }
        val out = mutableListOf<HuaweiSppPackage>()

        while (true) {
            val start = findMagic()
            if (start < 0) {
                buffer.clear()
                return out
            }
            if (start > 0) repeat(start) { buffer.removeAt(0) }
            if (buffer.size < 4) return out

            val length = buffer[2].toInt() and 0xFF
            if (length < 4) {
                val drop = minOf(4 + length, buffer.size)
                repeat(drop) { buffer.removeAt(0) }
                continue
            }

            val frameSize = 4 + length
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
        for (i in 0 until buffer.size - 1) {
            if (buffer[i] == 0x5A.toByte() && buffer[i + 1] == 0x00.toByte()) return i
        }
        return -1
    }

    private fun extractEmbedded(frame: ByteArray): List<HuaweiSppPackage> {
        val out = mutableListOf<HuaweiSppPackage>()
        var pos = 4
        while (pos + 4 <= frame.size) {
            if (frame[pos] == 0x5A.toByte() && frame[pos + 1] == 0x00.toByte()) {
                val len = frame[pos + 2].toInt() and 0xFF
                val end = pos + 4 + len
                if (len >= 4 && end <= frame.size) {
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