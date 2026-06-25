package com.freebuds.controller.protocol

class HuaweiSppPackage(
    val commandId: ByteArray,
    val responseId: ByteArray = commandId.copyOf(),
    val parameters: MutableMap<Int, ByteArray> = mutableMapOf()
) {
    companion object {
        fun readRequest(cmd: ByteArray, vararg paramTypes: Int): HuaweiSppPackage {
            val params = mutableMapOf<Int, ByteArray>()
            for (t in paramTypes) params[t] = byteArrayOf()
            return HuaweiSppPackage(cmd, cmd.copyOf(), params)
        }

        fun changeRequest(
            cmd: ByteArray,
            vararg pairs: Pair<Int, ByteArray>
        ): HuaweiSppPackage {
            val params = mutableMapOf<Int, ByteArray>()
            for ((k, v) in pairs) params[k] = v
            return HuaweiSppPackage(cmd, cmd.copyOf(), params)
        }

        fun changeRequestNoWait(
            cmd: ByteArray,
            vararg pairs: Pair<Int, ByteArray>
        ): HuaweiSppPackage {
            val params = mutableMapOf<Int, ByteArray>()
            for ((k, v) in pairs) params[k] = v
            return HuaweiSppPackage(cmd, parameters = params)
        }

        fun fromBytes(data: ByteArray, validateCrc: Boolean = false): HuaweiSppPackage? {
            if (data.size < 7) return null
            if (data[0] != 0x5A.toByte()) return null
            if (data[3] != 0x00.toByte()) return null

            if (validateCrc) {
                val crcData = data.copyOfRange(0, data.size - 2)
                val crcValue = data.copyOfRange(data.size - 2, data.size)
                val calc = crc16Xmodem(crcData)
                if (!crcValue.contentEquals(calc)) {
                    return null
                }
            }

            val length = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
            val cmdId = data.copyOfRange(4, 6)

            val pkg = HuaweiSppPackage(cmdId)
            var pos = 6
            while (pos < length + 3) {
                val pType = data[pos].toInt() and 0xFF
                val pLen = data[pos + 1].toInt() and 0xFF
                val pValue = if (pLen > 0) data.copyOfRange(pos + 2, pos + 2 + pLen) else byteArrayOf()
                pkg.parameters[pType] = pValue
                pos += pLen + 2
            }
            return pkg
        }

        fun crc16Xmodem(data: ByteArray): ByteArray {
            var crc = 0
            for (b in data) {
                crc = crc xor ((b.toInt() and 0xFF) shl 8)
                for (i in 0 until 8) {
                    if ((crc and 0x8000) != 0) {
                        crc = (crc shl 1) xor 0x1021
                    } else {
                        crc = crc shl 1
                    }
                    crc = crc and 0xFFFF
                }
            }
            crc = crc and 0xFFFF
            return byteArrayOf((crc shr 8).toByte(), crc.toByte())
        }
    }

    fun findParam(vararg types: Int): ByteArray {
        for (t in types) {
            parameters[t]?.let { return it }
        }
        return byteArrayOf()
    }

    fun toBytes(): ByteArray {
        val body = mutableListOf<Byte>()
        body.addAll(commandId.toList())
        for ((pType, pValue) in parameters) {
            body.add(pType.toByte())
            body.add(pValue.size.toByte())
            body.addAll(pValue.toList())
        }

        val pkt = mutableListOf<Byte>()
        pkt.add(0x5A.toByte())
        val len = body.size + 1
        pkt.add(((len shr 8) and 0xFF).toByte())
        pkt.add((len and 0xFF).toByte())
        pkt.add(0x00.toByte())
        pkt.addAll(body)

        val crc = crc16Xmodem(pkt.toByteArray())
        pkt.add(crc[0])
        pkt.add(crc[1])

        return pkt.toByteArray()
    }

    override fun toString(): String {
        val hex = commandId.joinToString("") { "%02x".format(it) }
        val params = parameters.map { (k, v) ->
            "param_$k=${v.joinToString("") { "%02x".format(it) }}"
        }
        return "cmd=$hex ${params.joinToString(", ")}"
    }
}
