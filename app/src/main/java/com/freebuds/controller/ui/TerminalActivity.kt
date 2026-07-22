package com.freebuds.controller.ui

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.freebuds.controller.HilifeApplication
import com.freebuds.controller.R
import com.freebuds.controller.i18n.I18n
import com.freebuds.controller.util.LogBuffer
import com.freebuds.controller.util.LogBuffer.OnLogUpdateListener
import kotlinx.coroutines.launch

/**
 * 调试终端 —— 只负责日志展示和 props/set 命令。
 * 连接/断开由 DeviceRepository 统一管理，Terminal 不持有 SppDriver。
 */
class TerminalActivity : AppCompatActivity(), OnLogUpdateListener {

    private lateinit var outputView: TextView
    private lateinit var inputView: TextView
    private lateinit var scrollView: ScrollView
    private var levelFilter: String? = null
    private val repo get() = HilifeApplication.instance.deviceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)
        title = I18n.t("terminal.title")

        outputView = findViewById(R.id.terminal_output)
        inputView  = findViewById(R.id.terminal_input)
        scrollView = findViewById(R.id.terminal_scroll)
        findViewById<Button>(R.id.btn_clear).text = I18n.t("terminal.clear")
        findViewById<Button>(R.id.btn_scan).text = I18n.t("terminal.props")
        findViewById<Button>(R.id.btn_list).text = I18n.t("terminal.help")
        findViewById<Button>(R.id.btn_disconnect).text = I18n.t("terminal.disconnect")
        findViewById<Button>(R.id.btn_share).text = I18n.t("terminal.share")
        findViewById<Button>(R.id.btn_perm).text = I18n.t("terminal.permissions")
        findViewById<Button>(R.id.btn_help).text = I18n.t("terminal.help")
        inputView.hint = I18n.t("terminal.input_hint")

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
        LogBuffer.i("Terminal", I18n.t("terminal.started"))
        LogBuffer.i("Terminal", I18n.t("terminal.commands"))
        renderAll()
    }

    private fun handleCommand(cmd: String) {
        val trimmed = cmd.trim()
        LogBuffer.i(">", trimmed)
        when {
            trimmed.equals("clear", true)      -> LogBuffer.clear()
            trimmed.equals("share", true)      -> shareLog()
            trimmed.equals("summary", true)    -> LogBuffer.i("Summary", LogBuffer.getSummaryText())
            trimmed.startsWith("filter ", true) -> setFilter(trimmed.substringAfter(' '))
            trimmed.equals("props", true)      -> printProps()
            trimmed.startsWith("set ", true)   -> setProp(trimmed.removePrefix("set").trim())
            trimmed.equals("disconnect", true) -> { repo.disconnect(); finish() }
            trimmed.equals("help", true)       -> {
                LogBuffer.i("Terminal", I18n.t("terminal.help.clear"))
                LogBuffer.i("Terminal", I18n.t("terminal.help.props"))
                LogBuffer.i("Terminal", I18n.t("terminal.help.set"))
                LogBuffer.i("Terminal", I18n.t("terminal.help.share"))
                LogBuffer.i("Terminal", I18n.t("terminal.help.summary"))
                LogBuffer.i("Terminal", I18n.t("terminal.help.filter"))
                LogBuffer.i("Terminal", I18n.t("terminal.help.disconnect"))
            }
            else -> LogBuffer.w("Terminal", I18n.t("terminal.unknown_command", trimmed))
        }
    }

    private fun printProps() {
        lifecycleScope.launch {
            val driver = repo.getDriver()
            if (driver == null) { LogBuffer.w("Prop", I18n.t("terminal.not_connected")); return@launch }
            val text = driver.getProperty() ?: ""
            if (text.isBlank()) LogBuffer.i("Prop", I18n.t("terminal.no_properties"))
            else text.lines().forEach { LogBuffer.i("Prop", it) }
        }
    }

    private fun setProp(payload: String) {
        val firstSpace = payload.indexOf(' ')
        val key   = if (firstSpace > 0) payload.substring(0, firstSpace) else payload
        val value = if (firstSpace > 0) payload.substring(firstSpace + 1) else ""
        val dot   = key.indexOf('.')
        if (dot <= 0 || dot == key.lastIndex) {
            LogBuffer.w("Prop", I18n.t("terminal.usage_set")); return
        }
        lifecycleScope.launch { repo.setProperty(key.substring(0, dot), key.substring(dot + 1), value) }
    }

    private fun shareLog() {
        repo.shareLog(this, I18n.t("terminal.export_log"))
    }

    private fun setFilter(value: String) {
        levelFilter = value.trim().uppercase().takeIf { it in setOf("I", "W", "E", "D") }
        LogBuffer.i("Terminal", I18n.t("terminal.filter_active", levelFilter ?: "ALL"))
        renderAll()
    }

    override fun onLogUpdate() = renderAll()

    private fun renderAll() {
        outputView.text = colorize(LogBuffer.getSnapshotText(levelFilter, maxEntries = 1_200))
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
