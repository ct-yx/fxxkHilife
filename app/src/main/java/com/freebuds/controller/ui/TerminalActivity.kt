package com.freebuds.controller.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.freebuds.controller.BuildConfig
import com.freebuds.controller.R
import com.freebuds.controller.bluetooth.BluetoothScanner
import com.freebuds.controller.bluetooth.BatteryHandler
import com.freebuds.controller.bluetooth.ScannedDevice
import com.freebuds.controller.bluetooth.SppDriver
import com.freebuds.controller.data.UpdateChecker
import com.freebuds.controller.util.LogBuffer
import com.freebuds.controller.util.LogBuffer.OnLogUpdateListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class TerminalActivity : AppCompatActivity(), OnLogUpdateListener {

    private lateinit var outputView: TextView
    private lateinit var inputView: TextView
    private lateinit var scrollView: ScrollView
    private var currentFilter: String? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private var bluetoothScanner: BluetoothScanner? = null
    private var sppDriver: SppDriver? = null
    private var scannedDevices: List<ScannedDevice> = emptyList()

    // 收集所有需要运行时申请的权限
    private val requiredPermissions: List<String> by lazy {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
            list.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list
    }

    // 权限弹窗回调后自动重试的操作
    private var pendingAction: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.filter { it.value }.keys
        val denied = result.filter { !it.value }.keys
        if (granted.isNotEmpty()) {
            LogBuffer.i("Perm", "Granted: ${granted.joinToString(", ")}")
        }
        if (denied.isNotEmpty()) {
            LogBuffer.w("Perm", "Denied: ${denied.joinToString(", ")}")
            // 检查是否被永久拒绝（不再询问）
            val permanentlyDenied = denied.any {
                !shouldShowRequestPermissionRationale(it)
            }
            if (permanentlyDenied) {
                LogBuffer.w("Perm", "Permission permanently denied — please enable in Settings")
                pendingAction = null
                return@registerForActivityResult
            }
        }
        val allGranted = requiredPermissions.all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            pendingAction?.invoke()
            pendingAction = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)
        title = "fxxkHilife Terminal"

        outputView = findViewById(R.id.terminal_output)
        inputView = findViewById(R.id.terminal_input)
        scrollView = findViewById(R.id.terminal_scroll)

        inputView.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) {
                handleCommand(inputView.text.toString())
                inputView.text = null
                true
            } else false
        }

        findViewById<Button>(R.id.btn_clear).setOnClickListener { handleCommand("clear") }
        findViewById<Button>(R.id.btn_scan).setOnClickListener { handleCommand("scan") }
        findViewById<Button>(R.id.btn_share).setOnClickListener { handleCommand("share") }
        findViewById<Button>(R.id.btn_list).setOnClickListener { handleCommand("list") }
        findViewById<Button>(R.id.btn_disconnect).setOnClickListener { handleCommand("disconnect") }
        findViewById<Button>(R.id.btn_perm).setOnClickListener { handleCommand("perm") }
        findViewById<Button>(R.id.btn_help).setOnClickListener { handleCommand("help") }

        LogBuffer.registerListener(this)
        printBanner()
        checkPermissions()
        renderAll()
    }

    private fun printBanner() {
        LogBuffer.i("Terminal", "fxxkHilife v2 Terminal — ${BuildConfig.VERSION_NAME}")
        LogBuffer.i("Terminal", "Commands: clear | share | perm | scan | list | connect <n> | disconnect | props | set <group.prop> <value> | help")
        LogBuffer.i("Terminal", "---")
    }

    private fun checkPermissions() {
        val missing = requiredPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            LogBuffer.i("Perm", "All required permissions already granted")
            return
        }
        LogBuffer.i("Perm", "Requesting permissions: ${missing.joinToString(", ")}")
        permissionLauncher.launch(missing.toTypedArray())
    }

    private fun handleCommand(cmd: String) {
        val trimmed = cmd.trim()
        LogBuffer.i(">", trimmed)

        when {
            trimmed.equals("clear", ignoreCase = true) -> LogBuffer.clear()
            trimmed.equals("share", ignoreCase = true) -> shareLog()
            trimmed.equals("perm", ignoreCase = true) -> checkPermissions()
            trimmed.equals("scan", ignoreCase = true) -> scanDevices()
            trimmed.startsWith("connect", ignoreCase = true) -> connectDevice(trimmed.removePrefix("connect").trim())
            trimmed.equals("disconnect", ignoreCase = true) -> disconnectDevice()
            trimmed.equals("list", ignoreCase = true) -> listDevices()
            trimmed.equals("props", ignoreCase = true) -> printProps()
            trimmed.startsWith("set ", ignoreCase = true) -> setProp(trimmed.removePrefix("set").trim())
            trimmed.equals("help", ignoreCase = true) -> {
                LogBuffer.i("Terminal", "Available commands:")
                LogBuffer.i("Terminal", "  clear        — clear screen")
                LogBuffer.i("Terminal", "  share        — export log as text file")
                LogBuffer.i("Terminal", "  perm         — re-check permissions")
                LogBuffer.i("Terminal", "  scan         — scan BT devices")
                LogBuffer.i("Terminal", "  list         — list scanned devices")
                LogBuffer.i("Terminal", "  connect <n>  — connect to device #n")
                LogBuffer.i("Terminal", "  disconnect   — disconnect device")
                LogBuffer.i("Terminal", "  props        — print OpenFreebuds property store")
                LogBuffer.i("Terminal", "  set <group.prop> <value> — write OpenFreebuds property")
                LogBuffer.i("Terminal", "  help         — this message")
            }
            else -> LogBuffer.w("Terminal", "Unknown command: $trimmed — type help")
        }
    }


    override fun onLogUpdate() = runOnUiThread { renderAll() }

    private fun renderAll() {
        if (currentFilter != null) {
            outputView.text = colorize(LogBuffer.getSnapshotText(currentFilter))
        } else {
            outputView.text = colorize(LogBuffer.getSnapshotText())
        }
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun colorize(text: String): SpannableString {
        val ss = SpannableString(text)
        val lines = text.lines()
        var offset = 0
        for (line in lines) {
            val bracket = line.indexOf('[')
            if (bracket >= 0 && bracket + 2 < line.length) {
                val lvl = line[bracket + 1]
                val color = when (lvl) {
                    'E' -> 0xFFFF4444.toInt()
                    'W' -> 0xFFFFBB33.toInt()
                    'I' -> 0xFF99CC00.toInt()
                    'D' -> 0xFF33B5E5.toInt()
                    else -> 0xFF00FF00.toInt()
                }
                ss.setSpan(ForegroundColorSpan(color), offset + bracket + 1, offset + bracket + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            offset += line.length + 1
        }
        return ss
    }

    private fun shareLog() {
        val text = LogBuffer.getSnapshotText(currentFilter)
        val file = File(cacheDir, "fxxkHilife_log_${System.currentTimeMillis()}.txt")
        file.writeText(text)

        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Log"))
    }

    private fun scanDevices() {
        val missing = requiredPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            LogBuffer.w("Perm", "Missing permissions for BT scan: ${missing.joinToString(", ")}")
            pendingAction = { scanDevices() }
            LogBuffer.i("Perm", "Requesting permissions...")
            permissionLauncher.launch(missing.toTypedArray())
            return
        }
        bluetoothScanner = BluetoothScanner(this)
        bluetoothScanner?.startScan { success ->
            if (success) {
                scannedDevices = bluetoothScanner!!.found.toList()
                val hwCount = scannedDevices.count { it.isHuaweiOrHonor }
                LogBuffer.i("Scan", "Found ${scannedDevices.size} devices ($hwCount Huawei/Honor). Type 'list' to see them")
            } else {
                LogBuffer.w("Scan", "Scan failed or cancelled")
            }
        }
        // startScan 内部已经同步了已配对设备到 found，直接同步过来触发自动连接
        scannedDevices = bluetoothScanner!!.found.toList()
        val hwDevice = scannedDevices.firstOrNull { it.isHuaweiOrHonor }
        if (hwDevice != null) {
            LogBuffer.i("Scan", "Auto-connecting to ${hwDevice.displayName}...")
            scope.launch { connectToDevice(hwDevice) }
        }
    }

    private fun listDevices() {
        if (scannedDevices.isEmpty()) {
            LogBuffer.i("BT", "No devices scanned yet. Type 'scan' first")
            return
        }
        LogBuffer.i("BT", "--- Scanned devices ---")
        scannedDevices.forEachIndexed { i, d ->
            val tag = if (d.isHuaweiOrHonor) "🔹 " else "  "
            val rssiStr = if (d.rssi != 0) " RSSI:${d.rssi}" else ""
            val bondedStr = if (d.isBonded) " [paired]" else ""
            LogBuffer.i("BT", "  $tag[$i] ${d.displayName}  ${d.address}$rssiStr$bondedStr")
        }
        LogBuffer.i("BT", "Use 'connect <n>' to connect")
    }

    private fun printProps() {
        val driver = sppDriver
        if (driver == null) {
            LogBuffer.w("Prop", "Not connected")
            return
        }
        scope.launch {
            val text = driver.getProperty() ?: ""
            if (text.isBlank()) LogBuffer.i("Prop", "No properties yet")
            else text.lines().forEach { LogBuffer.i("Prop", it) }
        }
    }

    private fun setProp(payload: String) {
        val driver = sppDriver
        if (driver == null) {
            LogBuffer.w("Prop", "Not connected")
            return
        }
        val firstSpace = payload.indexOf(' ')
        val key = if (firstSpace > 0) payload.substring(0, firstSpace) else payload
        val value = if (firstSpace > 0) payload.substring(firstSpace + 1) else ""
        val dot = key.indexOf('.')
        if (dot <= 0 || dot == key.lastIndex) {
            LogBuffer.w("Prop", "Usage: set <group.prop> <value>")
            return
        }
        scope.launch {
            driver.setProperty(key.substring(0, dot), key.substring(dot + 1), value)
        }
    }

    private fun connectDevice(indexStr: String) {
        val missing = requiredPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            LogBuffer.w("Perm", "Missing permissions for BT connect: ${missing.joinToString(", ")}")
            pendingAction = { connectDevice(indexStr) }
            LogBuffer.i("Perm", "Requesting permissions...")
            permissionLauncher.launch(missing.toTypedArray())
            return
        }
        val index = indexStr.toIntOrNull()
        if (index == null || index !in scannedDevices.indices) {
            LogBuffer.w("BT", "Invalid index. Type 'list' to see devices")
            return
        }
        scope.launch { connectToDevice(scannedDevices[index]) }
    }

    private fun registerOpenFreebudsHandlers(driver: SppDriver) {
        val battery = BatteryHandler()
        battery.setOnBatteryUpdate { LogBuffer.i("Battery", "Update") }
        listOf(
            LogsHandler(),
            InfoHandler(),
            InEarHandler(),
            battery,
            AncLegacyChangeHandler(),
            AncHandler(),
            DoubleTapHandler(),
            TripleTapHandler(),
            LongTapHandler(),
            SwipeGestureHandler(),
            PowerButtonHandler(),
            AutoPauseHandler(),
            LowLatencyHandler(),
            SoundQualityHandler(),
            VoiceLanguageHandler(),
        ).forEach { driver.registerHandler(it) }
    }

    /** 连接设备并注册所有 Handler（可供 scan 自动连接调用） */
    private suspend fun connectToDevice(sd: ScannedDevice) {
        LogBuffer.i("BT", "Connecting to ${sd.displayName}...")
        val driver = SppDriver(sd.device)
        registerOpenFreebudsHandlers(driver)
        sppDriver = driver
        if (driver.connect()) {
            LogBuffer.i("BT", "Connected to ${sd.displayName}")
        } else {
            LogBuffer.e("BT", "Connection failed"); sppDriver = null
        }
    }

    private fun disconnectDevice() {
        sppDriver?.disconnect()
        sppDriver = null
        LogBuffer.i("BT", "Disconnected")
    }

    override fun onDestroy() {
        bluetoothScanner?.stopScan()
        sppDriver?.disconnect()
        LogBuffer.unregisterListener(this)
        super.onDestroy()
    }
}
