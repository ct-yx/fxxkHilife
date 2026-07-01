package com.freebuds.controller.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.FileProvider
import com.freebuds.controller.adapter.huawei.HuaweiOpenFreebudsAdapter
import com.freebuds.controller.bluetooth.SppDriver
import com.freebuds.controller.core.adapter.EarbudAdapter
import com.freebuds.controller.core.adapter.EarbudAdapterCallbacks
import com.freebuds.controller.i18n.I18n
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Calendar
import java.util.Locale

/** 连接状态 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Failed(val reason: String) : ConnectionState()
}

data class ListeningStats(
    val totalMs: Long = 0L,
    val todayMs: Long = 0L,
    val activeDays: Int = 0,
    val streakDays: Int = 0,
    val dailyMs: Map<String, Long> = emptyMap(),
)

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
    val pendingInitHandlers: List<String> = emptyList(),
    // 连接时刻 ( = 未连接)，用于计算佩戴时长
    val connectedSince: Long? = null,
)

/**
 * 连接与属性的单一数据源。
 * 由 HilifeApplication 持有单例，ViewModel 注入使用。
 */
class DeviceRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val adapters: List<EarbudAdapter> = listOf(HuaweiOpenFreebudsAdapter)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _props = MutableStateFlow(DeviceProps())
    val props: StateFlow<DeviceProps> = _props.asStateFlow()

    private val _listeningStats = MutableStateFlow(ListeningStats())
    val listeningStats: StateFlow<ListeningStats> = _listeningStats.asStateFlow()

    fun isCoreStateReady(): Boolean {
        val p = _props.value
        val hasBattery = p.batteryGlobal != null || p.batteryLeft != null || p.batteryRight != null || p.batteryCase != null
        return p.ancMode != null && p.lowLatency != null && hasBattery
    }

    private var driver: SppDriver? = null
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private val failedHandlers = mutableSetOf<String>()
    private var retryJob: Job? = null
    private var pollJob: Job? = null
    private var fastPollJob: Job? = null
    private var autoLowLatencyJob: Job? = null
    private var listeningStatsJob: Job? = null
    private var lastListeningTick: Long = 0L
    private var sppQuietUntil: Long = 0L
    private var foregroundMode: Boolean = false
    private var connectedAddress: String? = null
    private var aclReceiver: BroadcastReceiver? = null
    private var connectingJob: Job? = null

    // 设备信息是否已获取（本次进程生命周期内）
    private var deviceInfoFetched: Boolean = false

    // 连接建立时刻（>0 表示已连接），用于计算佩戴时长
    private var connectedAt: Long = 0

    // ── 前后台感知 ───────────────────────────────────────────────────────────
    fun setAppInForeground(foreground: Boolean) {
        foregroundMode = foreground
        if (driver?.isConnected == true) {
            if (foreground) {
                startFastPolling()
            } else {
                stopFastPolling()
            }
        }
    }

    // ── 初始化 SharedPreferences ─────────────────────────────────────────

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences("fxxk_device", Context.MODE_PRIVATE)
        refreshListeningStats()
        deviceInfoFetched = false // 每次进程启动重置
        registerAclDisconnectReceiver(context.applicationContext)
    }

    // ── 连接 ─────────────────────────────────────────────────────────────────

    fun connect(device: BluetoothDevice) {
        if (connectingJob?.isActive == true ||
            _connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected) return

        connectingJob = scope.launch {
            _connectionState.value = ConnectionState.Connecting(device.name ?: device.address)
            val adapter = adapters.firstOrNull { it.canHandle(device) } ?: HuaweiOpenFreebudsAdapter
            val d = SppDriver(device)
            d.onPropertyChanged = {
                scope.launch {
                    if (driver === d) syncProps()
                }
            }
            d.onDisconnected = {
                scope.launch { handleRemoteDisconnected(d, "SPP receive loop ended") }
            }
            registerHandlers(adapter, d, device.name ?: "")
            driver = d
            if (d.connect()) {
                _connectionState.value = ConnectionState.Connected(device.name ?: device.address)
                connectedAt = System.currentTimeMillis()
                connectedAddress = device.address
                saveDeviceAddress(device.address)
                syncProps()
                deviceInfoFetched = false // 新连接：允许重新获取设备信息
                failedHandlers.addAll(d.failedHandlerIds)
                syncProps()
                startPolling()
                startListeningStatsTicker()
                if (foregroundMode) startFastPolling()
                retryFailedHandlers()
                applyAutoLowLatencyIfEnabled()
            } else {
                driver = null
                connectedAddress = null
                _connectionState.value = ConnectionState.Failed(I18n.t("scan.connection_failed_short"))
            }
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        fastPollJob?.cancel()
        retryJob?.cancel()
        autoLowLatencyJob?.cancel()
        stopListeningStatsTicker()
        sppQuietUntil = 0L
        driver?.disconnect()
        driver = null
        connectedAddress = null
        deviceInfoFetched = false
        _connectionState.value = ConnectionState.Disconnected
        _props.value = DeviceProps()
        connectedAt = 0
    }

    private fun handleRemoteDisconnected(sourceDriver: SppDriver?, reason: String) {
        if (sourceDriver != null && driver !== sourceDriver) return
        if (_connectionState.value !is ConnectionState.Connected && driver == null) return
        LogBuffer.w("SPP", "Remote disconnected: $reason")
        pollJob?.cancel()
        fastPollJob?.cancel()
        retryJob?.cancel()
        autoLowLatencyJob?.cancel()
        stopListeningStatsTicker()
        sppQuietUntil = 0L
        driver = null
        connectedAddress = null
        deviceInfoFetched = false
        _connectionState.value = ConnectionState.Disconnected
        _props.value = DeviceProps()
        connectedAt = 0
    }

    private fun registerAclDisconnectReceiver(context: Context) {
        if (aclReceiver != null) return
        aclReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val address = device?.address ?: return
                if (address == connectedAddress) {
                    scope.launch { handleRemoteDisconnected(driver, "Bluetooth ACL disconnected") }
                }
            }
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(aclReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(aclReceiver, filter)
        }
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

    private fun getSystemConnectedDevice(address: String): BluetoothDevice? {
        val adapterDevice = runCatching {
            BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)
        }.getOrNull()

        val isConnectedByDevice = adapterDevice?.let { device ->
            runCatching {
                val method = device.javaClass.getMethod("isConnected")
                method.invoke(device) as? Boolean
            }.getOrNull()
        } == true
        if (isConnectedByDevice) return adapterDevice

        val context = appContext ?: return null
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return null
        val connectedDevices = buildList {
            addAll(runCatching { manager.getConnectedDevices(BluetoothProfile.HEADSET) }.getOrDefault(emptyList()))
            addAll(runCatching { manager.getConnectedDevices(BluetoothProfile.A2DP) }.getOrDefault(emptyList()))
        }
        return connectedDevices.firstOrNull { it.address == address }
    }

    fun autoConnectSaved(address: String): Boolean {
        if (_connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected) return true

        val device = getSystemConnectedDevice(address) ?: return false
        connect(device)
        return true
    }

    fun autoConnectLastSaved(): Boolean {
        val address = getSavedAddress() ?: return false
        return autoConnectSaved(address)
    }

    private fun applyAutoLowLatencyIfEnabled() {
        val enabled = appContext
            ?.getSharedPreferences("settings", Context.MODE_PRIVATE)
            ?.getBoolean("auto_low_latency", true) ?: true
        if (!enabled) return

        autoLowLatencyJob?.cancel()
        autoLowLatencyJob = scope.launch {
            val timeoutMs = 30_000L
            val startedAt = System.currentTimeMillis()
            var attempt = 0

            while (isActive && _connectionState.value is ConnectionState.Connected) {
                val currentLowLatency = _props.value.lowLatency
                if (currentLowLatency == true) {
                    LogBuffer.i("SPP", "Auto low latency confirmed")
                    return@launch
                }

                if (System.currentTimeMillis() - startedAt >= timeoutMs) break

                // 先让 low_latency handler 完成一次读取；未知状态下立刻写入会抢占核心初始化通道。
                if (currentLowLatency == null) {
                    delay(500)
                    continue
                }

                attempt++
                LogBuffer.i("SPP", "Auto low latency apply attempt $attempt")
                sppQuietUntil = System.currentTimeMillis() + 1_500L
                setProperty("config", "low_latency", "true")
                delay(900)
            }

            if (_connectionState.value is ConnectionState.Connected && _props.value.lowLatency != true) {
                LogBuffer.w("SPP", "Auto low latency was not confirmed within ${timeoutMs / 1000}s")
            }
        }
    }

    fun removeSavedDevice(address: String) {
        val p = prefs ?: return
        val set = p.getStringSet("saved_devices", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.remove(address)
        p.edit().putStringSet("saved_devices", set).apply()
    }

    // ── 后台重试失败 Handler（核心状态优先，避免非核心项抢占 ANC）───────────

    private val coreRetryOrder = listOf(
        "anc_global",
        "battery",
        "low_latency",
        "config_sound_quality",
        "tws_in_ear",
    )

    private fun orderedRetryHandlerIds(): List<String> {
        val corePending = coreRetryOrder.filter { it in failedHandlers }
        if (corePending.isNotEmpty()) return corePending
        return failedHandlers.sortedBy { id ->
            when (id) {
                "device_info" -> 10
                "gesture_double", "gesture_triple", "gesture_long", "gesture_swipe" -> 20
                "tws_auto_pause" -> 30
                "voice_language" -> 40
                else -> 50
            }
        }
    }

    private fun retryFailedHandlers() {
        retryJob?.cancel()
        if (failedHandlers.isEmpty()) return
        retryJob = scope.launch {
            var attempt = 0
            while (isActive && driver?.isConnected == true && failedHandlers.isNotEmpty()) {
                val hasCorePending = failedHandlers.any { it in coreRetryOrder }
                val delayMs = when {
                    hasCorePending && attempt < 5 -> 2_000L
                    hasCorePending -> 5_000L
                    attempt < 8 -> 8_000L
                    else -> 20_000L
                }
                delay(delayMs)
                val quietDelay = sppQuietUntil - System.currentTimeMillis()
                if (quietDelay > 0) delay(quietDelay)
                attempt++
                val d = driver ?: break
                val toRemove = mutableListOf<String>()
                val retryIds = orderedRetryHandlerIds().let { ids ->
                    if (ids.any { it in coreRetryOrder }) ids else ids.take(3)
                }
                for (handlerId in retryIds) {
                    val handler = d.getHandlerById(handlerId) ?: continue
                    try {
                        withTimeout(1500) {
                            handler.onInit(d)
                        }
                        LogBuffer.i("SPP", "Retry init $handlerId success (attempt=$attempt)")
                        toRemove.add(handlerId)
                    } catch (_: Exception) {
                        LogBuffer.w("SPP", "Retry init $handlerId still failed (attempt=$attempt), will retry in ${delayMs/1000}s")
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

    // ── 定时轮询（后台 5s 基础属性，前台 fastPoll 800ms）─────────────────

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && driver?.isConnected == true) {
                delay(5_000) // 后台每 5s 轮询一次
                if (!isActive || driver?.isConnected != true) break
                if (!foregroundMode) {
                    syncProps()
                }
            }
        }
    }

    private fun startFastPolling() {
        fastPollJob?.cancel()
        fastPollJob = scope.launch {
            var tick = 0
            while (isActive && driver?.isConnected == true && foregroundMode) {
                delay(800) // 前台 800ms 高频刷新
                if (!isActive || driver?.isConnected != true || !foregroundMode) break
                // 设备信息仅在首次连接时获取一次；核心状态/自动低延迟稳定前不抢 SPP 通道
                if (!deviceInfoFetched && System.currentTimeMillis() >= sppQuietUntil && failedHandlers.none { it in coreRetryOrder }) {
                    val d = driver ?: return@launch
                    val infoHandler = d.getHandlerById("device_info")
                    if (infoHandler != null) {
                        try {
                            withTimeout(1500) { infoHandler.onInit(d) }
                            deviceInfoFetched = true
                            failedHandlers.remove("device_info")
                        } catch (_: Exception) { }
                    } else {
                        deviceInfoFetched = true
                    }
                }
                syncProps()
                tick++
            }
        }
    }

    private fun stopFastPolling() {
        fastPollJob?.cancel()
        fastPollJob = null
    }

    // ── 属性写入（UI → 设备）──等待响应后重新同步 ─────────────────

    suspend fun setProperty(group: String, prop: String, value: String) {
        val d = driver ?: return

        // ========== v2.7.0 根因修复：源头乐观更新 ==========
        // 让 UI、Tile、Notification 全部瞬间看到预期值，彻底解决跳变问题
        when {
            group == "anc" && prop == "mode" -> {
                _props.value = _props.value.copy(ancMode = value)
            }
            group == "config" && prop == "low_latency" -> {
                _props.value = _props.value.copy(lowLatency = value.toBooleanStrictOrNull())
            }
            group == "sound" && prop == "quality_preference" -> {
                _props.value = _props.value.copy(soundQuality = value)
            }
        }

        d.setProperty(group, prop, value)
        delay(150)
        syncProps()
    }

    private fun ensureDefaultAncOptions() {
        if (_props.value.ancModeOptions.isEmpty()) {
            _props.value = _props.value.copy(
                ancModeOptions = listOf("normal", "cancellation", "awareness")
            )
        }
    }

    // ── 听音统计 ─────────────────────────────────────────────────────────

    private fun todayKey(time: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = time }
        return String.format(Locale.US, "%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    private fun readDailyListening(): MutableMap<String, Long> {
        val p = prefs ?: return mutableMapOf()
        val raw = p.getString("listening_daily_ms", "") ?: ""
        return raw.split(";")
            .mapNotNull { item ->
                val parts = item.split("=")
                if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0L) else null
            }
            .filter { it.first.isNotBlank() && it.second > 0L }
            .toMap()
            .toMutableMap()
    }

    private fun writeDailyListening(map: Map<String, Long>) {
        val p = prefs ?: return
        val compact = map.entries
            .sortedBy { it.key }
            .takeLast(180)
            .joinToString(";") { "${it.key}=${it.value}" }
        p.edit().putString("listening_daily_ms", compact).apply()
    }

    private fun refreshListeningStats(extraTodayMs: Long = 0L) {
        val daily = readDailyListening().toMutableMap()
        if (extraTodayMs > 0L) {
            val key = todayKey()
            daily[key] = (daily[key] ?: 0L) + extraTodayMs
            writeDailyListening(daily)
        }
        val today = todayKey()
        var streak = 0
        val cal = Calendar.getInstance()
        while (true) {
            val key = todayKey(cal.timeInMillis)
            if ((daily[key] ?: 0L) <= 0L) break
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        _listeningStats.value = ListeningStats(
            totalMs = daily.values.sum(),
            todayMs = daily[today] ?: 0L,
            activeDays = daily.count { it.value > 0L },
            streakDays = streak,
            dailyMs = daily.toMap(),
        )
    }

    private fun startListeningStatsTicker() {
        listeningStatsJob?.cancel()
        lastListeningTick = System.currentTimeMillis()
        listeningStatsJob = scope.launch {
            while (isActive) {
                delay(60_000)
                val now = System.currentTimeMillis()
                if (_connectionState.value is ConnectionState.Connected) {
                    val delta = (now - lastListeningTick).coerceIn(0L, 5 * 60_000L)
                    refreshListeningStats(delta)
                }
                lastListeningTick = now
            }
        }
    }

    private fun stopListeningStatsTicker() {
        val now = System.currentTimeMillis()
        val delta = if (lastListeningTick > 0L) (now - lastListeningTick).coerceIn(0L, 5 * 60_000L) else 0L
        if (delta > 0L && _connectionState.value is ConnectionState.Connected) {
            refreshListeningStats(delta)
        }
        listeningStatsJob?.cancel()
        listeningStatsJob = null
        lastListeningTick = 0L
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
            context.startActivity(Intent.createChooser(intent, I18n.t("settings.share_log_chooser")))
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

        val batteryCase = get("battery", "case")?.toIntOrNull()

        _props.value = DeviceProps(
            batteryGlobal    = get("battery", "global")?.toIntOrNull(),
            batteryLeft      = get("battery", "left")?.toIntOrNull(),
            batteryRight     = get("battery", "right")?.toIntOrNull(),
            batteryCase      = batteryCase,
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
            pendingInitHandlers = failedHandlers.toList(),
            connectedSince   = connectedAt.takeIf { it > 0 },
        )
        ensureDefaultAncOptions()
    }

    // ── 内部：注册 Handler ─────────────────────────────────────────────────────

    private fun registerHandlers(adapter: EarbudAdapter, d: SppDriver, name: String) {
        adapter.registerHandlers(
            driver = d,
            deviceName = name,
            callbacks = EarbudAdapterCallbacks(
                onStateChanged = { syncProps() }
            )
        )
    }

    fun getDriver(): SppDriver? = driver
}
