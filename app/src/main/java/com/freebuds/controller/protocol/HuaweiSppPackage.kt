package com.freebuds.controller.protocol

/**
 * 华为 SPP 协议包
 * 对照 OpenFreebuds package.py
 *
 * 协议格式：
 *   `[0x5A]` `[length:2]` `[0x00]` `[command_id:2]` `[param...]` `[crc16:2]`
 *   每个参数: [type:1] [len:1] [value:N]
 */
class HuaweiSppPackage(
    val commandId: ByteArray,
    val responseId: ByteArray = commandId,
    val parameters: MutableMap<Int, ByteArray> = mutableMapOf()
) {
    companion object {
        /** 构造读取请求：parameters 传参 type 列表 */
        fun readRequest(cmd: ByteArray, vararg paramTypes: Int): HuaweiSppPackage {
            val params = mutableMapOf<Int, ByteArray>()
            for (t in paramTypes) params[t] = byteArrayOf()
            return HuaweiSppPackage(cmd, cmd, params)
        }

        /** 构造变更请求（等待应答） */
        fun changeRequest(
            cmd: ByteArray,
            vararg pairs: Pair<Int, ByteArray>
        ): HuaweiSppPackage {
            val params = mutableMapOf<Int, ByteArray>()
            for ((k, v) in pairs) params[k] = v
            return HuaweiSppPackage(cmd, cmd, params)
        }

        /** 构造变更请求（不等待应答） */
        fun changeRequestNoWait(
            cmd: ByteArray,
            vararg pairs: Pair<Int, ByteArray>
        ): HuaweiSppPackage {
            val params = mutableMapOf<Int, ByteArray>()
            for ((k, v) in pairs) params[k] = v
            return HuaweiSppPackage(cmd, parameters = params)
        }

        /** 从字节流解析包 */
        fun fromBytes(data: ByteArray, validateCrc: Boolean = false): HuaweiSppPackage? {
            if (data.size < 7) return null
            if (data[0] != 0x5A.toByte()) return null
            if (data[3] != 0x00.toByte()) return null

            if (validateCrc) {
                val crcData = data.copyOfRange(0, data.size - 2)
                val crcValue = data.copyOfRange(data.size - 2, data.size)
                val calc = crc16Xmodem(crcData)
                if (!crcValue.contentEquals(calc)) {
                    return null // CRC 校验失败
                }
            }

            val length = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
            val commandId = data.copyOfRange(4, 6)

            val pkg = HuaweiSppPackage(commandId)
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

        /** CRC-16/XMODEM 实现 */
        fun crc16Xmodem(data: ByteArray): ByteArray {
            var crc = 0
            for (b in data) {
                crc = crc xor (b.toInt() and 0xFF shl 8)
                for (i in 0 until 8) {
                    if (crc and 0x8000 != 0) {
                        crc = crc shl 1 xor 0x1021
                    } else {
                        crc = crc shl 1
                    }
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

    /** 序列化为字节流 */
    fun toBytes(): ByteArray {
        // 1. 构建 body
        val body = mutableListOf<Byte>()
        body.addAll(commandId.toList())
        for ((pType, pValue) in parameters) {
            body.add(pType.toByte())
            body.add(pValue.size.toByte())
            body.addAll(pValue.toList())
        }

        // 2. 构建完整包
        val pkt = mutableListOf<Byte>()
        pkt.add(0x5A.toByte())
        val len = (body.size + 1) and 0xFFFF
        pkt.add((len shr 8).toByte())
        pkt.add(len.toByte())
        pkt.add(0x00.toByte())
        pkt.addAll(body)

        // 3. CRC16
        val pktBytes = pkt.toByteArray()
        val crc = crc16Xmodem(pktBytes)
        pkt.addAll(crc.toList())

        return pkt.toByteArray()
    }

    override fun toString(): String {
        val params = parameters.map { (k, v) -> "param_$k=${v.toHex()}" }
        return "cmd=${commandId.toHex()} ${params.joinToString(", ")}"
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
