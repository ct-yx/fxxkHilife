package com.freebuds.controller.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.inputmethod.EditorInfo
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.freebuds.controller.BuildConfig
import com.freebuds.controller.R
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

        LogBuffer.registerListener(this)
        printBanner()
        renderAll()
    }

    private fun printBanner() {
        LogBuffer.i("Terminal", "fxxkHilife v2 Terminal — ${BuildConfig.VERSION_NAME}")
        LogBuffer.i("Terminal", "Commands: clear | filter [I/W/E/D] | share | check | download | help")
        LogBuffer.i("Terminal", "---")
    }

    private fun handleCommand(cmd: String) {
        val trimmed = cmd.trim()
        LogBuffer.i(">", trimmed)

        when {
            trimmed.equals("clear", ignoreCase = true) -> LogBuffer.clear()
            trimmed.startsWith("filter", ignoreCase = true) -> {
                val arg = trimmed.removePrefix("filter").trim()
                if (arg.length == 1 && arg.first() in listOf('I', 'W', 'E', 'D')) {
                    currentFilter = arg.uppercase()
                    LogBuffer.i("Terminal", "Filter set to [$currentFilter]")
                } else {
                    currentFilter = null
                    LogBuffer.i("Terminal", "Filter cleared (show all)")
                }
                renderAll()
            }
            trimmed.equals("share", ignoreCase = true) -> shareLog()
            trimmed.equals("check", ignoreCase = true) -> checkUpdate()
            trimmed.equals("download", ignoreCase = true) -> downloadLatest()
            trimmed.equals("help", ignoreCase = true) -> {
                LogBuffer.i("Terminal", "Available commands:")
                LogBuffer.i("Terminal", "  clear        — clear screen")
                LogBuffer.i("Terminal", "  filter I     — show only info")
                LogBuffer.i("Terminal", "  filter W     — show only warnings")
                LogBuffer.i("Terminal", "  filter E     — show only errors")
                LogBuffer.i("Terminal", "  filter D     — show only debug")
                LogBuffer.i("Terminal", "  filter       — show all levels")
                LogBuffer.i("Terminal", "  share        — export log as text file")
                LogBuffer.i("Terminal", "  check        — check for updates")
                LogBuffer.i("Terminal", "  download     — download & open latest APK")
                LogBuffer.i("Terminal", "  help         — this message")
            }
            else -> LogBuffer.w("Terminal", "Unknown command: $trimmed — type help")
        }
    }

    private fun checkUpdate() {
        LogBuffer.i("Update", "Checking for updates...")
        scope.launch {
            val info = UpdateChecker.check()
            if (UpdateChecker.hasUpdate(info)) {
                LogBuffer.i("Update", "New version available: ${info!!.latestVersion}")
                LogBuffer.i("Update", info.releaseNotes)
                LogBuffer.i("Update", "Type 'download' to get it")
            } else if (info != null) {
                LogBuffer.i("Update", "Already up-to-date (${info.latestVersion})")
            } else {
                LogBuffer.w("Update", "Failed to check updates (no network?)")
            }
        }
    }

    private fun downloadLatest() {
        LogBuffer.i("Update", "Fetching latest release info...")
        scope.launch {
            val info = UpdateChecker.check()
            if (info == null || info.downloadUrl.isBlank()) {
                LogBuffer.w("Update", "No download URL available")
                return@launch
            }
            if (!UpdateChecker.hasUpdate(info)) {
                LogBuffer.i("Update", "Already up-to-date (${info.latestVersion})")
            }
            LogBuffer.i("Update", "Opening: ${info.downloadUrl}")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
            startActivity(intent)
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

    override fun onDestroy() {
        LogBuffer.unregisterListener(this)
        super.onDestroy()
    }
}
