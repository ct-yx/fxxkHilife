package com.freebuds.controller.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Minimal application i18n facade.
 *
 * Only the built-in Chinese table is provided for now. Additional language packs can be
 * added later by implementing [I18nProvider] and installing it through [LocalI18n].
 */
object I18n {
    const val DEFAULT_LOCALE = "zh-CN"

    fun t(key: String, vararg args: Any?): String = DefaultI18nProvider.t(key, *args)
}

interface I18nProvider {
    val localeTag: String
    fun t(key: String, vararg args: Any?): String
}

@Immutable
private object DefaultI18nProvider : I18nProvider {
    override val localeTag: String = I18n.DEFAULT_LOCALE

    private val strings = mapOf(
        "app.name" to "fxxkHilife",
        "common.back" to "返回",
        "common.cancel" to "取消",
        "common.none" to "无",
        "common.enabled" to "开启",
        "common.disabled" to "关闭",
        "common.loading" to "加载中…",
        "common.retry" to "重试",

        "notification.channel.bluetooth_status" to "蓝牙连接与 ANC 状态",
        "notification.channel.bluetooth_status_desc" to "显示当前 ANC 模式、听音时长、低延迟/音质状态",
        "notification.service.title" to "fxxkHilife",
        "notification.service.text" to "耳机服务运行中",
        "notification.action.anc_cancellation" to "降噪",
        "notification.action.anc_awareness" to "透传",

        "tile.anc" to "ANC",
        "tile.switching" to "正在切换…",
        "tile.connecting" to "正在连接耳机…",
        "tile.connected.tap_to" to "点按切到%s",
        "tile.connection_failed_retry" to "连接失败，点按重试",
        "tile.tap_connect" to "点按连接耳机",
        "tile.add_device_first" to "先在应用内添加耳机",

        "settings.title" to "设置",
        "settings.about" to "关于",
        "settings.version" to "版本",
        "settings.saved_devices" to "已保存的设备（%s）",
        "settings.connection_preferences" to "连接偏好",
        "settings.auto_low_latency" to "自动低延迟模式",
        "settings.auto_low_latency_desc" to "连接已保存耳机后自动开启低延迟",
        "settings.wallpaper_guide.title" to "建议先设置壁纸",
        "settings.wallpaper_guide.text" to "液态玻璃需要背景色彩参与模糊与折射。你可以先选择一张壁纸，也可以继续直接开启。",
        "settings.wallpaper_guide.pick" to "选择壁纸",
        "settings.wallpaper_guide.continue" to "仍然开启",

        "device.group.anc" to "ANC模式",
        "device.group.audio" to "音频",
        "device.group.background_sync" to "后台同步中",
        "device.pending.device_info" to "设备信息",
        "device.pending.gesture_double" to "双击手势",
        "device.pending.gesture_triple" to "三击手势",
        "device.pending.gesture_long" to "长按手势",
        "device.pending.gesture_swipe" to "滑动手势",
        "device.pending.tws_auto_pause" to "自动暂停",
        "device.pending.config_sound_quality" to "音质偏好",
        "device.pending.voice_language" to "语音语言",
        "device.pending.tws_in_ear" to "佩戴状态",
        "device.pending.battery" to "电量",
        "device.pending.low_latency" to "低延迟",

        "gesture.title" to "手势设置",
        "scan.title" to "选择设备",
        "home.scan" to "扫描设备",
        "permission.title" to "权限引导",
    )

    override fun t(key: String, vararg args: Any?): String {
        val raw = strings[key] ?: key
        return if (args.isEmpty()) raw else runCatching { raw.format(*args) }.getOrDefault(raw)
    }
}

val LocalI18n = staticCompositionLocalOf<I18nProvider> { DefaultI18nProvider }

@Composable
fun i18n(key: String, vararg args: Any?): String = LocalI18n.current.t(key, *args)
