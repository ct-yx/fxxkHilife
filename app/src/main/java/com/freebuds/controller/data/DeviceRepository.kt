package com.freebuds.controller.data

import android.bluetooth.BluetoothDevice
import com.freebuds.controller.bluetooth.*
import com.freebuds.controller.protocol.HuaweiCapability
import com.freebuds.controller.protocol.HuaweiModel
import com.freebuds.controller.protocol.modelCapabilities
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 连接状态 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Failed(val reason: String) : ConnectionState()
}

/** 设备属性快照（UI 消费） */
data class DeviceProps(
    val batteryGlobal: Int? = null,
    val batteryLeft: Int? = null,
    val batteryRight: Int? = null,
    val batteryCase: Int? = null,
    val isCharging: Boolean? = null,
    val ancMode: String? = null,
    val ancModeOptions: List<String> = emptyList(),
    val ancLevel: String? = null,
    val ancLevelOptions: List<String> = emptyList(),
    val autoPause: Boolean? = null,
    val lowLatency: Boolean? = null,
    val soundQuality: String? = null,
    val soundQualityOptions: List<String> = emptyList(),
    val doubleTapLeft: String? = null,
    val doubleTapRight: String? = null,
    val tripleTapLeft: String? = null,
    val tripleTapRight: String? = null,
    val longTap: String? = null,
    val longTapOptions: List<String> = emptyList(),
    val swipeGesture: String? = null,
    val swipeGestureOptions: List<String> = emptyList(),
    val inEar: Boolean? = null,
    val deviceModel: String? = null,
    val firmwareVersion: String? = null,
)

/**
 * 连接与属性的单一数据源。
 * 由 HilifeApplication 持有单例，ViewModel 注入使用。
 */
class DeviceRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _props = MutableStateFlow(DeviceProps())
    val props: StateFlow<DeviceProps> = _props.asStateFlow()

    private var driver: SppDriver? = null

    // ── 连接 ─────────────────────────────────────────────────────────────────

    fun connect(device: BluetoothDevice) {
        if (_connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected) return

        scope.launch {
            _connectionState.value = ConnectionState.Connecting(device.name ?: device.address)
            val d = SppDriver(device)
            registerHandlers(d, device.name ?: "")
            driver = d
            if (d.connect()) {
                _connectionState.value = ConnectionState.Connected(device.name ?: device.address)
                // 连接后开始同步属性
                syncProps()
            } else {
                driver = null
                _connectionState.value = ConnectionState.Failed("连接失败")
            }
        }
    }

    fun disconnect() {
        driver?.disconnect()
        driver = null
        _connectionState.value = ConnectionState.Disconnected
        _props.value = DeviceProps()
    }

    // ── 属性写入（UI → 设备） ─────────────────────────────────────────────────

    suspend fun setProperty(group: String, prop: String, value: String) {
        driver?.setProperty(group, prop, value)
        syncProps()
    }

    // ── 内部：从 driver 属性存储同步到 StateFlow ──────────────────────────────

    private suspend fun syncProps() {
        val d = driver ?: return
        suspend fun get(group: String, prop: String) = d.getProperty(group, prop)
        suspend fun opts(group: String, prop: String) =
            get(group, prop)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

        _props.value = DeviceProps(
            batteryGlobal    = get("battery", "global")?.toIntOrNull(),
            batteryLeft      = get("battery", "left")?.toIntOrNull(),
            batteryRight     = get("battery", "right")?.toIntOrNull(),
            batteryCase      = get("battery", "case")?.toIntOrNull(),
            isCharging       = get("battery", "is_charging")?.toBooleanStrictOrNull(),
            ancMode          = get("anc", "mode"),
            ancModeOptions   = opts("anc", "mode_options"),
            ancLevel         = get("anc", "level"),
            ancLevelOptions  = opts("anc", "level_options"),
            autoPause        = get("config", "auto_pause")?.toBooleanStrictOrNull(),
            lowLatency       = get("config", "low_latency")?.toBooleanStrictOrNull(),
            soundQuality     = get("sound", "quality_preference"),
            soundQualityOptions = opts("sound", "quality_preference_options"),
            doubleTapLeft    = get("action", "double_tap_left"),
            doubleTapRight   = get("action", "double_tap_right"),
            tripleTapLeft    = get("action", "triple_tap_left"),
            tripleTapRight   = get("action", "triple_tap_right"),
            longTap          = get("action", "long_tap"),
            longTapOptions   = opts("action", "long_tap_options"),
            swipeGesture     = get("action", "swipe_gesture"),
            swipeGestureOptions = opts("action", "swipe_gesture_options"),
            inEar            = get("state", "in_ear")?.toBooleanStrictOrNull(),
            deviceModel      = get("info", "device_model"),
            firmwareVersion  = get("info", "software_ver"),
        )
    }

    // ── 内部：注册 Handler（从 TerminalActivity 迁移过来） ─────────────────────

    private fun registerHandlers(d: SppDriver, name: String) {
        val model = detectModel(name)
        val caps = modelCapabilities[model]?.toSet() ?: emptySet()
        fun has(c: HuaweiCapability) = caps.isEmpty() || c in caps

        if (true)              d.registerHandler(LogsHandler())
        if (has(HuaweiCapability.INFO))               d.registerHandler(InfoHandler())
        if (has(HuaweiCapability.WEAR_DETECT))        d.registerHandler(InEarHandler())
        if (has(HuaweiCapability.BATTERY)) {
            val bh = BatteryHandler()
            bh.setOnBatteryUpdate { scope.launch { syncProps() } }
            d.registerHandler(bh)
        }
        if (has(HuaweiCapability.ANC_LEGACY))         d.registerHandler(AncLegacyChangeHandler())
        if (has(HuaweiCapability.ANC))                d.registerHandler(AncHandler())
        if (has(HuaweiCapability.ACTION_DOUBLE_TAP))  d.registerHandler(DoubleTapHandler())
        if (has(HuaweiCapability.ACTION_TRIPLE_TAP))  d.registerHandler(TripleTapHandler())
        if (has(HuaweiCapability.ACTION_LONG_TAP_SPLIT)) d.registerHandler(LongTapHandler())
        if (has(HuaweiCapability.ACTION_SWIPE))       d.registerHandler(SwipeGestureHandler())
        if (has(HuaweiCapability.ACTION_POWER_BUTTON)) d.registerHandler(PowerButtonHandler())
        if (has(HuaweiCapability.AUTO_PAUSE))         d.registerHandler(AutoPauseHandler())
        if (has(HuaweiCapability.LOW_LATENCY))        d.registerHandler(LowLatencyHandler())
        if (has(HuaweiCapability.SOUND_QUALITY))      d.registerHandler(SoundQualityHandler())
        if (has(HuaweiCapability.VOICE_LANGUAGE))     d.registerHandler(VoiceLanguageHandler())
    }

    private fun detectModel(name: String): HuaweiModel? = when {
        name.contains("FreeBuds 6i", true)    -> HuaweiModel.BUDS_6I
        name.contains("FreeBuds Pro 4", true) ||
        name.contains("FreeBuds Pro 3", true) ||
        name.contains("FreeClip", true)        -> HuaweiModel.BUDS_PRO_3
        name.contains("FreeBuds Pro 2", true)  -> HuaweiModel.BUDS_PRO_2
        name.contains("FreeBuds Pro", true)    -> HuaweiModel.BUDS_PRO
        name.contains("FreeBuds Studio", true) -> HuaweiModel.STUDIO
        name.contains("FreeBuds SE 4", true)   -> HuaweiModel.BUDS_SE_4
        name.contains("FreeBuds SE 2", true)   -> HuaweiModel.BUDS_SE_2
        name.contains("FreeBuds SE", true)     -> HuaweiModel.BUDS_SE
        name.contains("FreeBuds 5i", true)     -> HuaweiModel.BUDS_5I
        name.contains("FreeBuds 4i", true)     -> HuaweiModel.BUDS_4I
        name.contains("FreeLace Pro 2", true)  -> HuaweiModel.LACE_PRO_2
        name.contains("FreeLace Pro", true)    -> HuaweiModel.LACE_PRO
        name.contains("Earbuds", true)         -> HuaweiModel.BUDS_4I
        else -> null
    }

    fun getDriver(): SppDriver? = driver
}
