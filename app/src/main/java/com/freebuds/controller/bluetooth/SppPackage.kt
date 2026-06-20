package com.freebuds.controller.bluetooth

import com.freebuds.controller.util.Crc16

class SppPackage(
    val commandId: ByteArray,
    val responseId: ByteArray = commandId,
    params: List<Pair<Int, ByteArray>> = emptyList()
) {
    val parameters: MutableMap<Int, ByteArray> = params.toMap().toMutableMap()

    init {
        require(commandId.size == 2) { "commandId must be 2 bytes" }
    }

    companion object {
        const val MAGIC: Byte = 0x5A
        const val SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb"

        fun readRequest(cmd: ByteArray, paramTypes: List<Int>): SppPackage =
            SppPackage(cmd, cmd, paramTypes.map { it to ByteArray(0) })

        fun writeRequest(cmd: ByteArray, params: List<Pair<Int, ByteArray>>, expectResponse: Boolean = true): SppPackage =
            SppPackage(cmd, if (expectResponse) cmd else ByteArray(0), params)
    }

    fun toBytes(): ByteArray {
        val body = mutableListOf<Byte>()
        body.addAll(commandId.toList())
        for ((type, value) in parameters) {
            body.add(type.toByte())
            body.add(value.size.toByte())
            body.addAll(value.toList())
        }
        val raw = mutableListOf<Byte>()
        raw.add(MAGIC)
        val len = body.size + 1
        raw.add(((len shr 8) and 0xFF).toByte())
        raw.add((len and 0xFF).toByte())
        raw.add(0x00)
        raw.addAll(body)
        raw.addAll(Crc16.compute(raw.toByteArray()).toList())
        return raw.toByteArray()
    }

    fun findParam(vararg types: Int): ByteArray {
        for (t in types) {
            parameters[t]?.let { return it }
        }
        return ByteArray(0)
    }

    fun matchesCommand(cmd: ByteArray) = commandId.contentEquals(cmd)

    override fun toString(): String {
        val params = parameters.entries.joinToString(", ") { (k, v) ->
            "p$k=${v.joinToString("") { "%02x".format(it) }}"
        }
        return "SPP[${commandId.joinToString("") { "%02x".format(it) }}, $params]"
    }
}
