package com.freebuds.controller.ui

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.freebuds.controller.HilifeApplication
import com.freebuds.controller.R
import com.freebuds.controller.util.LogBuffer
import com.freebuds.controller.util.LogBuffer.OnLogUpdateListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * 调试终端 —— 只负责日志展示和 props/set 命令。
 * 连接/断开由 DeviceRepository 统一管理，Terminal 不持有 SppDriver。
 */
class TerminalActivity : AppCompatActivity(), OnLogUpdateListener {

    private lateinit var outputView: TextView
    private lateinit var inputView: TextView
    private lateinit var scrollView: ScrollView
    private val scope = CoroutineScope(Dispatchers.Main)
    private val repo get() = HilifeApplication.instance.deviceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)
        title = "调试终端"

        outputView = findViewById(R.id.terminal_output)
        inputView  = findViewById(R.id.terminal_input)
        scrollView = findViewById(R.id.terminal_scroll)

        inputView.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) {
                handleCommand(inputView.text.toString())
                inputView.text = null
                true
            } else false
        }

        findViewById<Button>(R.id.btn_clear).setOnClickListener { handleCommand("clear") }
        // scan/list/disconnect 按钮不再有意义，复用为 props/set/share/help
        findViewById<Button>(R.id.btn_scan).setOnClickListener { handleCommand("props") }
        findViewById<Button>(R.id.btn_list).setOnClickListener { handleCommand("help") }
        findViewById<Button>(R.id.btn_disconnect).setOnClickListener { handleCommand("disconnect") }
        findViewById<Button>(R.id.btn_share).setOnClickListener { handleCommand("share") }
        findViewById<Button>(R.id.btn_perm).setOnClickListener { handleCommand("props") }
        findViewById<Button>(R.id.btn_help).setOnClickListener { handleCommand("help") }

        LogBuffer.registerListener(this)
        LogBuffer.i("Terminal", "调试终端 — 连接管理请返回主界面")
        LogBuffer.i("Terminal", "可用命令：clear | props | set <group.prop> <value> | share | disconnect | help")
        renderAll()
    }

    private fun handleCommand(cmd: String) {
        val trimmed = cmd.trim()
        LogBuffer.i(">", trimmed)
        when {
            trimmed.equals("clear", true)      -> LogBuffer.clear()
            trimmed.equals("share", true)      -> shareLog()
            trimmed.equals("props", true)      -> printProps()
            trimmed.startsWith("set ", true)   -> setProp(trimmed.removePrefix("set").trim())
            trimmed.equals("disconnect", true) -> { repo.disconnect(); finish() }
            trimmed.equals("help", true)       -> {
                LogBuffer.i("Terminal", "clear        — 清屏")
                LogBuffer.i("Terminal", "props        — 查看所有属性")
                LogBuffer.i("Terminal", "set g.p v    — 写入属性")
                LogBuffer.i("Terminal", "share        — 导出日志")
                LogBuffer.i("Terminal", "disconnect   — 断开连接并返回")
            }
            else -> LogBuffer.w("Terminal", "未知命令：$trimmed")
        }
    }

    private fun printProps() {
        scope.launch {
            val driver = repo.getDriver()
            if (driver == null) { LogBuffer.w("Prop", "未连接"); return@launch }
            val text = driver.getProperty() ?: ""
            if (text.isBlank()) LogBuffer.i("Prop", "暂无属性")
            else text.lines().forEach { LogBuffer.i("Prop", it) }
        }
    }

    private fun setProp(payload: String) {
        val firstSpace = payload.indexOf(' ')
        val key   = if (firstSpace > 0) payload.substring(0, firstSpace) else payload
        val value = if (firstSpace > 0) payload.substring(firstSpace + 1) else ""
        val dot   = key.indexOf('.')
        if (dot <= 0 || dot == key.lastIndex) {
            LogBuffer.w("Prop", "用法：set <group.prop> <value>"); return
        }
        scope.launch { repo.setProperty(key.substring(0, dot), key.substring(dot + 1), value) }
    }

    private fun shareLog() {
        val file = File(cacheDir, "fxxkHilife_log_${System.currentTimeMillis()}.txt")
        file.writeText(LogBuffer.getSnapshotText())
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "导出日志"
        ))
    }

    override fun onLogUpdate() = runOnUiThread { renderAll() }

    private fun renderAll() {
        outputView.text = colorize(LogBuffer.getSnapshotText())
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun colorize(text: String): SpannableString {
        val ss = SpannableString(text)
        var offset = 0
        for (line in text.lines()) {
            val bracket = line.indexOf('[')
            if (bracket >= 0 && bracket + 2 < line.length) {
                val color = when (line[bracket + 1]) {
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

    override fun onDestroy() {
        LogBuffer.unregisterListener(this)
        super.onDestroy()
    }
}
