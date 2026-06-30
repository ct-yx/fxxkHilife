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
        "common.unknown" to "未知",
        "common.default" to "默认",
        "common.enhanced" to "增强",

        "anc.mode.off" to "关闭",
        "anc.mode.cancellation" to "降噪",
        "anc.mode.awareness" to "透传",
        "anc.level.comfort" to "舒适",
        "anc.level.normal" to "均衡",
        "anc.level.ultra" to "深度",
        "anc.level.dynamic" to "动态",
        "anc.awareness.normal" to "普通透传",
        "anc.awareness.voice_boost" to "人声增强",
        "anc.level_title.cancellation" to "降噪强度",
        "anc.level_title.awareness" to "通透模式",
        "anc.level_title.default" to "ANC 子模式",

        "sound.quality.connectivity" to "连接优先",
        "sound.quality.quality" to "音质优先",
        "sound.quality.sound_first" to "声音优先",

        "notification.channel.bluetooth_status" to "蓝牙连接与 ANC 状态",
        "notification.channel.bluetooth_status_desc" to "显示当前 ANC 模式、听音时长、低延迟/音质状态",
        "notification.service.title" to "fxxkHilife",
        "notification.service.text" to "耳机服务运行中",
        "notification.action.anc_cancellation" to "降噪",
        "notification.action.anc_awareness" to "透传",
        "notification.battery" to "电量：%s",
        "notification.battery_left" to "左%s%%",
        "notification.battery_right" to "右%s%%",
        "notification.battery_case" to "盒%s%%",
        "notification.sound_quality" to "音质：%s",
        "notification.low_latency" to "低延迟：%s",
        "notification.wearing" to "佩戴：%s",

        "tile.anc" to "ANC",
        "tile.switching" to "正在切换…",
        "tile.connecting" to "正在连接耳机…",
        "tile.connected.tap_to" to "点按切到%s",
        "tile.connection_failed_retry" to "连接失败，点按重试",
        "tile.tap_connect" to "点按连接耳机",
        "tile.add_device_first" to "先在应用内添加耳机",
        "tile.switching_to_mode" to "正在切换到%s模式",
        "tile.connecting_saved_device" to "正在连接已和手机蓝牙连接的耳机",
        "tile.current_tap_to_mode" to "当前%s模式，点按切到%s模式",
        "tile.earbuds_connecting" to "耳机连接中",
        "tile.connection_failed_retry_desc" to "连接失败，点按重新连接耳机",

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

        "common.settings" to "设置",
        "common.delete" to "删除",
        "common.clear" to "清除",
        "common.continue" to "继续",
        "common.scanning" to "扫描中",
        "common.charging" to "充电中",

        "home.empty_saved_devices" to "尚未保存任何设备",
        "home.scan_new_device" to "扫描新设备",
        "home.start_scan" to "开始扫描",
        "home.saved_devices" to "已保存的设备",
        "home.saved_after_connect" to "扫描并连接后会自动保存",
        "home.scan_nearby_huawei" to "发现附近的华为/荣耀耳机",
        "home.tap_start_scan" to "点击\"开始扫描\"发现附近设备",
        "home.bonded" to "已配对",

        "scan.title" to "扫描设备",
        "scan.rescan" to "重新扫描",
        "scan.empty" to "未发现设备",
        "scan.nearby_devices" to "附近设备",
        "scan.connected_to" to "已连接 %s",
        "scan.connecting_to" to "正在连接 %s…",
        "scan.connection_failed" to "连接失败：%s",
        "scan.connection_failed_short" to "连接失败",
        "scan.huawei_honor_devices" to "华为 / 荣耀设备",
        "scan.other_devices" to "其他设备",

        "permission.keep_alive" to "后台保活",
        "permission.auto_start_settings" to "自启动设置",
        "permission.subtitle" to "华为 / 荣耀耳机控制",
        "permission.description" to "需要蓝牙权限连接耳机；建议开启通知与后台/自启动权限，以保证常驻通知和开机自动连接正常工作。",
        "permission.bluetooth_connect" to "蓝牙扫描与连接",
        "permission.notification" to "通知权限",
        "permission.keep_alive_whitelist" to "后台保活 / 电池优化白名单",
        "permission.manual_allow_hint" to "需在系统设置中手动允许",
        "permission.auto_start" to "自启动 / 开机后自动连接",
        "permission.rom_manual_hint" to "部分国产 ROM 需手动开启",
        "permission.grant_bluetooth" to "授予蓝牙权限",
        "permission.grant_bluetooth_notification" to "授予蓝牙与通知权限",
        "permission.privacy_local" to "我们不会上传任何数据，所有操作均在本地完成。",
        "permission.required" to "必需",
        "permission.recommended" to "建议开启",
        "permission.granted" to "已授权",
        "permission.not_granted" to "未授权",
        "permission.suggested" to "建议",

        "device.group.gestures" to "手势",
        "device.group.about" to "关于",
        "device.gesture_settings" to "手势设置",
        "device.gesture_settings_desc" to "双击 / 三击 / 滑动 / 长按",
        "device.debug_terminal" to "调试终端",
        "device.debug_terminal_desc" to "查看 SPP 原始日志 / 发送命令",
        "device.battery" to "电量",
        "device.model" to "型号",
        "device.firmware" to "固件版本",
        "device.battery.left" to "左",
        "device.battery.right" to "右",
        "device.battery.case" to "盒",
        "device.battery.earbuds" to "耳机",
        "device.pending.auto_pause" to "摘下暂停",
        "device.pending.more_suffix" to " 等 %s 项",
        "device.pending.detail" to "正在补齐：%s%s。通常 15–45 秒内陆续完成，慢项会保持后台重试。",
        "device.option.sound_quality" to "音质偏好",
        "device.option.auto_pause" to "摘下自动暂停",
        "device.option.low_latency" to "低延迟模式",

        "settings.theme" to "主题",
        "settings.personalization" to "个性化",
        "settings.wallpaper" to "壁纸",
        "settings.debug" to "调试",
        "settings.debug_terminal" to "调试终端",
        "settings.debug_terminal_desc" to "查看 SPP 原始日志 / 发送命令",
        "settings.share_log" to "分享日志",
        "settings.share_log_chooser" to "分享日志",
        "settings.share_log_desc" to "导出当前日志为文本文件",
        "settings.debug_requires_connected" to "调试功能需连接耳机后使用",
        "settings.app_details" to "应用详情",
        "settings.project_philosophy" to "项目理念",
        "settings.project_philosophy_desc" to "为华为 FreeBuds 系列耳机提供第三方开源控制面板，还原官方 App 的完整功能，同时保持轻量与高效。",
        "settings.update_url" to "更新地址",
        "settings.other_credits" to "其他贡献",
        "settings.third_party_icons" to "第三方图标",
        "settings.third_party_icons_desc" to "点击展开图标来源与授权信息",
        "settings.liquid_glass_personalization" to "液态玻璃个性化",
        "settings.liquid_glass_personalization_desc" to "调节模糊、边缘折射、深度与可读性",
        "settings.glass_blur_desc" to "调整玻璃效果的透明与朦胧程度",
        "settings.glass_lens_desc" to "一种 Lens 效果，增强边缘厚度与折射感",
        "settings.glass_depth_desc" to "增强玻璃厚度、暗边与层次",
        "settings.glass_readability_desc" to "复杂/浅色壁纸下保护文字，不改变玻璃主体透明度",
        "settings.advanced_mode" to "高级模式",
        "settings.edge_highlight_desc" to "影响玻璃边缘高光形态",
        "settings.wallpaper_preview" to "壁纸预览",
        "settings.display_scope" to "展示范围：",
        "settings.glass_blur" to "玻璃模糊强度",
        "settings.glass_refraction" to "液态玻璃边缘折射",
        "settings.glass_depth" to "液态玻璃深度效果",
        "settings.glass_readability" to "液态玻璃可读性增强",
        "settings.surface_profile" to "表面轮廓",
        "settings.wallpaper_import" to "导入壁纸",
        "settings.wallpaper_change" to "更换壁纸",
        "settings.scope.all" to "全部界面",
        "settings.scope.home" to "仅主页",
        "settings.scope.settings" to "仅设置",
        "settings.theme.system" to "跟随",
        "settings.theme.dark" to "深色",
        "settings.theme.light" to "浅色",
        "settings.glass.transparent" to "通透",
        "settings.glass.low" to "较低",
        "settings.surface.rounded" to "圆角",
        "settings.surface.squircle" to "柔方",
        "settings.surface.circle" to "圆形",
        "settings.display_mode" to "展示模式",
        "ui.display.classic" to "传统展示",
        "ui.display.classic_desc" to "稳定、清晰、性能开销低",
        "ui.display.liquid_glass" to "液态玻璃",
        "ui.display.liquid_glass_desc" to "壁纸、毛玻璃、虹彩边缘与漂浮质感",
        "settings.log_retention" to "日志保留：",
        "settings.log_lines" to "%s 行",
        "settings.glass.misty" to "朦胧",
        "settings.glass.high" to "较高",

        "gesture.double_left" to "双击 · 左",
        "gesture.double_right" to "双击 · 右",
        "gesture.triple_left" to "三击 · 左",
        "gesture.triple_right" to "三击 · 右",
        "gesture.swipe" to "滑动手势",
        "gesture.long_tap" to "长按",
        "gesture.action.pause" to "播放/暂停",
        "gesture.action.next" to "下一首",
        "gesture.action.prev" to "上一首",
        "gesture.action.assistant" to "语音助手",
        "gesture.action.answer" to "接听/挂断",
        "gesture.action.volume" to "音量调节",
        "gesture.noise.disabled" to "关闭降噪切换",
        "gesture.noise.off_on" to "降噪切换",
        "gesture.noise.off_on_aw" to "降噪/透传切换",
        "gesture.noise.on_aw" to "透传切换",
        "gesture.noise.off_an" to "仅降噪",

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
