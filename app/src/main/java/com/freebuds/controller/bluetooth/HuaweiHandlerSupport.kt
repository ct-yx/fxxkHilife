package com.freebuds.controller.bluetooth

/** Shared protocol-value helpers for the split Huawei feature handlers. */
internal fun b(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()
internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
internal fun ByteArray.signedByte(): Int = first().toInt()
internal fun Boolean.asString(): String = if (this) "true" else "false"
internal fun options(values: Map<Int, String>): String = values.values.joinToString(",")
internal fun reverseOption(values: Map<Int, String>, value: String): Int =
    values.entries.firstOrNull { it.value == value }?.key ?: value.toInt()
