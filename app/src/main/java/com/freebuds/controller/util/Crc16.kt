package com.freebuds.controller.util

object Crc16 {
    private val table = IntArray(256).apply {
        for (i in 0..255) {
            var crc = 0; var c = i shl 8
            for (j in 0..7) {
                crc = crc shl 1; c = c shl 1
                if ((c xor crc) and 0x10000 != 0) crc = crc xor 0x1021
            }
            set(i, crc)
        }
    }

    fun compute(data: ByteArray): ByteArray {
        var crc = 0
        for (b in data) {
            crc = (crc shl 8) xor table[((crc shr 8) xor (b.toInt() and 0xFF)) and 0xFF]
        }
        return byteArrayOf(((crc shr 8) and 0xFF).toByte(), (crc and 0xFF).toByte())
    }
}
