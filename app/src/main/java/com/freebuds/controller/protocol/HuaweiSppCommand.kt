package com.freebuds.controller.protocol

/**
 * 华为/荣耀 FreeBuds SPP 命令 ID 词典
 * 对照 OpenFreebuds 的 spp_commands.py
 */
object HuaweiSppCommand {
    // 电池
    val BATTERY_READ = byteArrayOf(0x01, 0x08)
    val BATTERY_NOTIFY = byteArrayOf(0x01, 0x27)

    // 自动暂停
    val AUTO_PAUSE_READ = byteArrayOf(0x2b, 0x11)
    val AUTO_PAUSE_WRITE = byteArrayOf(0x2b, 0x10)

    // 双击
    val DUAL_TAP_READ = byteArrayOf(0x01, 0x20)
    val DUAL_TAP_WRITE = byteArrayOf(0x01, 0x1f)

    // 三击
    val TRIPLE_TAP_READ = byteArrayOf(0x01, 0x26)
    val TRIPLE_TAP_WRITE = byteArrayOf(0x01, 0x25)

    // 长按（左右分离）
    val LONG_TAP_SPLIT_READ_BASE = byteArrayOf(0x2b, 0x17)
    val LONG_TAP_SPLIT_READ_ANC = byteArrayOf(0x2b, 0x19)
    val LONG_TAP_SPLIT_WRITE_BASE = byteArrayOf(0x2b, 0x16)
    val LONG_TAP_SPLIT_WRITE_ANC = byteArrayOf(0x2b, 0x18)

    // 滑动
    val SWIPE_READ = byteArrayOf(0x2b, 0x1f)
    val SWIPE_WRITE = byteArrayOf(0x2b, 0x1e)

    // 低延迟模式
    val LOW_LATENCY = byteArrayOf(0x2b, 0x6c)

    // 双设备连接
    val DUAL_CONNECT_ENABLED_READ = byteArrayOf(0x2b, 0x2f)
    val DUAL_CONNECT_ENABLED_WRITE = byteArrayOf(0x2b, 0x2e)
    val DUAL_CONNECT_ENUMERATE = byteArrayOf(0x2b, 0x31)
    val DUAL_CONNECT_PREFERRED_WRITE = byteArrayOf(0x2b, 0x32)
    val DUAL_CONNECT_EXECUTE = byteArrayOf(0x2b, 0x33)
    val DUAL_CONNECT_CHANGE_EVENT = byteArrayOf(0x2b, 0x36)

    // 写入动作映射（spp_commands.py 未直接列出，handle_action 系列用到）
    val ACTION_DUAL_TAP_WRITE = DUAL_TAP_WRITE
    val ACTION_TRIPLE_TAP_WRITE = TRIPLE_TAP_WRITE
    val ACTION_SWIPE_WRITE = SWIPE_WRITE
    val ACTION_LONG_TAP_SPLIT_WRITE = LONG_TAP_SPLIT_WRITE_BASE
    val ACTION_LONG_TAP_SPLIT_ANC_WRITE = LONG_TAP_SPLIT_WRITE_ANC
}
