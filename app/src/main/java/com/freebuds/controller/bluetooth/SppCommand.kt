package com.freebuds.controller.bluetooth

/**
 * All known Huawei SPP command IDs, ported from OpenFreebuds (spp_commands.py).
 *
 * Commands are 2-byte hex values. Read commands query device state,
 * write commands modify it (different trailing byte).
 */
object SppCommand {
    // Battery
    val BATTERY_READ = byteArrayOf(0x01, 0x08)
    val BATTERY_NOTIFY = byteArrayOf(0x01, 0x27)

    // ANC / Noise control (aligned with OpenFreebuds anc.py)
    val ANC_MODE_READ = byteArrayOf(0x2B, 0x2A)
    val ANC_MODE_WRITE = byteArrayOf(0x2B, 0x04)

    // Auto pause
    val AUTO_PAUSE_READ = byteArrayOf(0x2B, 0x11)
    val AUTO_PAUSE_WRITE = byteArrayOf(0x2B, 0x10)

    // Gestures
    val DUAL_TAP_READ = byteArrayOf(0x01, 0x20)
    val DUAL_TAP_WRITE = byteArrayOf(0x01, 0x1F)
    val TRIPLE_TAP_READ = byteArrayOf(0x01, 0x26)
    val TRIPLE_TAP_WRITE = byteArrayOf(0x01, 0x25)
    val SWIPE_READ = byteArrayOf(0x2B, 0x1F)
    val SWIPE_WRITE = byteArrayOf(0x2B, 0x1E)

    // Long tap (split: base action + ANC cycle)
    val LONG_TAP_BASE_READ = byteArrayOf(0x2B, 0x17)
    val LONG_TAP_BASE_WRITE = byteArrayOf(0x2B, 0x16)
    val LONG_TAP_ANC_READ = byteArrayOf(0x2B, 0x19)
    val LONG_TAP_ANC_WRITE = byteArrayOf(0x2B, 0x18)

    // Low latency mode
    val LOW_LATENCY = byteArrayOf(0x2B, 0x6C)

    // Sound quality preference
    val SOUND_QUALITY_READ = byteArrayOf(0x2B, 0xA3.toByte())
    val SOUND_QUALITY_WRITE = byteArrayOf(0x2B, 0xA2.toByte())

    // Dual connect
    val DUAL_CONNECT_ENABLED_READ = byteArrayOf(0x2B, 0x2F)
    val DUAL_CONNECT_ENABLED_WRITE = byteArrayOf(0x2B, 0x2E)
    val DUAL_CONNECT_ENUMERATE = byteArrayOf(0x2B, 0x31)
    val DUAL_CONNECT_PREFERRED_WRITE = byteArrayOf(0x2B, 0x32)
    val DUAL_CONNECT_EXECUTE = byteArrayOf(0x2B, 0x33)
    val DUAL_CONNECT_CHANGE_EVENT = byteArrayOf(0x2B, 0x36)
}
