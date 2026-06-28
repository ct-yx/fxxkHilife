package com.freebuds.controller.data

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.FileProvider
import com.freebuds.controller.bluetooth.*
import com.freebuds.controller.protocol.HuaweiCapability
import com.freebuds.controller.protocol.HuaweiModel
import com.freebuds.controller.protocol.modelCapabilities
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

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
    val doubleTapOptions: List<String> = emptyList(),
    val tripleTapLeft: String? = null,
    val tripleTapRight: String? = null,
    val tripleTapOptions: List<String> = emptyList(),
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
    private var prefs: SharedPreferences? = null
    private val failedHandlers = mutableSetOf<String>()
    private var retryJob: Job? = null
    private var pollJob: Job? = null

    // ── 初始化 SharedPreferences ─────────────────────────────────────────

    fun init(context: Context) {
        prefs = context.getSharedPreferences("fxxk_device", Context.MODE_PRIVATE)
    }

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
                saveDeviceAddress(device.address)
                syncProps()
                failedHandlers.addAll(d.failedHandlerIds)
                startPolling()
                retryFailedHandlers()
            } else {
                driver = null
                _connectionState.value = ConnectionState.Failed("连接失败")
            }
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        retryJob?.cancel()
        driver?.disconnect()
        driver = null
        _connectionState.value = ConnectionState.Disconnected
        _props.value = DeviceProps()
    }

    // ── 持久化设备地址（集合）───────────────────────────────────────────

    private fun saveDeviceAddress(address: String) {
        val p = prefs ?: return
        val set = p.getStringSet("saved_devices", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(address)
        p.edit().putStringSet("saved_devices", set).apply()
    }

    fun getSavedAddresses(): List<String> {
        val p = prefs ?: return emptyList()
        return p.getStringSet("saved_devices", emptySet())?.toList() ?: emptyList()
    }

    fun getSavedAddress(): String? = getSavedAddresses().lastOrNull()

    fun removeSavedDevice(address: String) {
        val p = prefs ?: return
        val set = p.getStringSet("saved_devices", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.remove(address)
        p.edit().putStringSet("saved_devices", set).apply()
    }

    // ── 后台重试失败 Handler（30s 间隔）───────────────────────────────────

    private fun retryFailedHandlers() {
        retryJob?.cancel()
        if (failedHandlers.isEmpty()) return
        retryJob = scope.launch {
            while (isActive && driver?.isConnected == true && failedHandlers.isNotEmpty()) {
                delay(30_000) // 30 秒间隔
                val d = driver ?: break
                val toRemove = mutableListOf<String>()
                for (handlerId in failedHandlers) {
                    val handler = d.getHandlerById(handlerId) ?: continue
                    try {
                        withTimeout(1500) {
                            handler.onInit(d)
                        }
                        LogBuffer.i("SPP", "Retry init $handlerId success")
                        toRemove.add(handlerId)
                    } catch (_: Exception) {
                        LogBuffer.w("SPP", "Retry init $handlerId still failed, will retry in 30s")
                    }
                }
                failedHandlers.removeAll(toRemove)
                if (failedHandlers.isEmpty()) {
                    LogBuffer.i("SPP", "All failed handlers recovered")
                }
                syncProps()
            }
        }
    }

    // ── 定时轮询（电池 45s，基础 3s）────────────────────────────────────

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            var batteryTick = 0L
            while (isActive && driver?.isConnected == true) {
                delay(3_000) // 基础属性每 3s 轮询一次
                if (!isActive || driver?.isConnected != true) break
                syncProps()
                batteryTick++
                // 每 45 秒额外同步电池（被动推送为主）
                if (batteryTick >= 15) {
                    batteryTick = 0
                }
            }
        }
    }

    // ── 属性写入（UI → 设备）──等待响应后重新同步 ─────────────────

    suspend fun setProperty(group: String, prop: String, value: String) {
        val d = driver ?: return
        d.setProperty(group, prop, value)
        // 重新发起 onInit 获取真实值（Handler.setProperty 内部已发 init 请求）
        delay(100) // 给硬件响应时间
        syncProps()
    }

    // ── 分享日志 ─────────────────────────────────────────────────────────

    fun shareLog(context: Context) {
        try {
            val text = LogBuffer.getSnapshotText()
            val dir = File(context.cacheDir, "logs")
            dir.mkdirs()
            val file = File(dir, "fxxkHilife_log_${System.currentTimeMillis()}.txt")
            file.writeText(text)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享日志"))
        } catch (e: Exception) {
            LogBuffer.e("Share", "Failed to share log: ${e.message}")
        }
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
            doubleTapOptions = opts("action", "double_tap_options"),
            tripleTapLeft    = get("action", "triple_tap_left"),
            tripleTapRight   = get("action", "triple_tap_right"),
            tripleTapOptions = opts("action", "triple_tap_options"),
            longTap          = get("action", "long_tap"),
            longTapOptions   = opts("action", "long_tap_options"),
            swipeGesture     = get("action", "swipe_gesture"),
            swipeGestureOptions = opts("action", "swipe_gesture_options"),
            inEar            = get("state", "in_ear")?.toBooleanStrictOrNull(),
            deviceModel      = get("info", "device_model"),
            firmwareVersion  = get("info", "software_ver"),
        )
    }

    // ── 内部：注册 Handler ─────────────────────────────────────────────────────

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
