package com.freebuds.controller.protocol

import com.freebuds.controller.core.protocol.ProtocolPacket

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

class HuaweiSppPackage(
    val commandId: ByteArray,
    val responseId: ByteArray = commandId,
    val parameters: MutableMap<Int, ByteArray> = mutableMapOf()
) : ProtocolPacket {
    override val commandKey: String get() = commandId.toHexString()
    override val responseKey: String get() = responseId.toHexString()
    companion object {
        fun readRequest(cmd: ByteArray, vararg types: Int): HuaweiSppPackage {
            val m = mutableMapOf<Int, ByteArray>()
            for (t in types) m[t] = byteArrayOf()
            return HuaweiSppPackage(cmd, cmd, m)
        }

        fun changeRequest(cmd: ByteArray, vararg pairs: Pair<Int, ByteArray>): HuaweiSppPackage {
            val m = mutableMapOf<Int, ByteArray>()
            for ((k, v) in pairs) m[k] = v
            return HuaweiSppPackage(cmd, cmd, m)
        }

        fun changeRequestNoWait(cmd: ByteArray, vararg pairs: Pair<Int, ByteArray>): HuaweiSppPackage {
            val m = mutableMapOf<Int, ByteArray>()
            for ((k, v) in pairs) m[k] = v
            return HuaweiSppPackage(cmd, responseId = byteArrayOf(), parameters = m)
        }

        /**
         * Parses one complete 5A frame.
         *
         * The upstream length field is the payload length plus one.  The two checksum bytes are
         * therefore located at `length + 3`, and the complete frame occupies `length + 5` bytes.
         * Keeping that arithmetic here prevents the stream framer and the packet parser from
         * silently disagreeing about the final checksum byte.
         */
        fun fromBytes(data: ByteArray, validateChecksum: Boolean = false): HuaweiSppPackage? {
            if (data.size < MIN_FRAME_SIZE) return null
            if (data[0] != 0x5A.toByte()) return null
            if (data[3] != 0x00.toByte()) return null
            val len = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
            if (len < MIN_LENGTH) return null
            val payloadEnd = len + PAYLOAD_END_OFFSET
            val frameSize = payloadEnd + CHECKSUM_SIZE
            if (data.size < frameSize) return null
            if (validateChecksum) {
                val expected = crc16(data.copyOfRange(0, payloadEnd))
                val actual = data.copyOfRange(payloadEnd, frameSize)
                if (!expected.contentEquals(actual)) return null
            }
            val cmd = data.copyOfRange(4, 6)
            val p = HuaweiSppPackage(cmd)
            var pos = 6
            while (pos < payloadEnd) {
                if (pos + PARAMETER_HEADER_SIZE > payloadEnd) return null
                val t = data[pos].toInt() and 0xFF
                val l = data[pos + 1].toInt() and 0xFF
                if (pos + PARAMETER_HEADER_SIZE + l > payloadEnd) return null
                val v = if (l > 0) data.copyOfRange(pos + 2, pos + 2 + l) else byteArrayOf()
                p.parameters[t] = v
                pos += PARAMETER_HEADER_SIZE + l
            }
            return p
        }

        fun crc16(data: ByteArray): ByteArray {
            var c = 0
            for (b in data) {
                c = c xor ((b.toInt() and 0xFF) shl 8)
                for (i in 0 until 8) {
                    c = if ((c and 0x8000) != 0) ((c shl 1) xor 0x1021) else (c shl 1)
                    c = c and 0xFFFF
                }
            }
            return byteArrayOf(((c shr 8) and 0xFF).toByte(), (c and 0xFF).toByte())
        }

        private const val MIN_LENGTH = 3 // two command bytes plus the length-field offset
        private const val PAYLOAD_END_OFFSET = 3
        private const val CHECKSUM_SIZE = 2
        private const val MIN_FRAME_SIZE = MIN_LENGTH + PAYLOAD_END_OFFSET + CHECKSUM_SIZE
        private const val PARAMETER_HEADER_SIZE = 2
    }

    fun findParam(vararg types: Int): ByteArray {
        for (t in types) {
            val v = parameters[t]
            if (v != null) return v
        }
        return byteArrayOf()
    }

    fun toBytes(): ByteArray {
        val b = mutableListOf<Byte>()
        b.addAll(commandId.toList())
        for ((t, v) in parameters) {
            b.add(t.toByte())
            b.add(v.size.toByte())
            b.addAll(v.toList())
        }
        val p = mutableListOf<Byte>()
        p.add(0x5A.toByte())
        val l = b.size + 1
        p.add(((l shr 8) and 0xFF).toByte())
        p.add((l and 0xFF).toByte())
        p.add(0x00.toByte())
        p.addAll(b)
        val crc = crc16(p.toByteArray())
        p.add(crc[0])
        p.add(crc[1])
        return p.toByteArray()
    }

    override fun toString(): String {
        val hex = commandId.joinToString("") { "%02x".format(it) }
        val ps = parameters.map { (k, v) ->
            "p$k=" + v.joinToString("") { "%02x".format(it) }
        }
        return "c=$hex " + ps.joinToString(" ")
    }
}
