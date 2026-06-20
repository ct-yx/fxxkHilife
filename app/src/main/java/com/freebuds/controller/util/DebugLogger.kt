package com.freebuds.controller.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {

    private var logDir: File? = null
    private var logFile: File? = null
    private var debugEnabled = false   // controls d() level only

    private const val MAX_LOG_SIZE = 1024 * 1024 // 1MB before rotation
    private const val TAG = "DebugLogger"

    fun init(context: Context) {
        logDir = File(context.cacheDir, "logs")
        logDir?.mkdirs()
        rotateLog()
        Log.i(TAG, "DebugLogger initialized: ${logDir?.absolutePath}")
    }

    /**
     * Toggle verbose debug logging.
     * When disabled, only info/warn/error are output (always recorded).
     * When enabled, debug-level details are also emitted.
     */
    fun setEnabled(on: Boolean) {
        debugEnabled = on
        if (on && logFile == null) rotateLog()
        Log.i(TAG, "Debug logging ${if (on) "enabled" else "disabled"}")
    }

    fun isEnabled(): Boolean = debugEnabled

    /**
     * Debug: verbose details — only when debug logging is enabled.
     * Use for fine-grained SPP traffic, handler step traces, etc.
     */
    fun d(tag: String, msg: String) {
        if (!debugEnabled) return
        Log.d(tag, msg)
        writeToFile("D/$tag: $msg")
    }

    /**
     * Info: notable lifecycle / state changes — always recorded.
     * Use for connect/disconnect, property changes, mode switches.
     */
    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        writeToFile("I/$tag: $msg")
    }

    /**
     * Warn: recoverable issues — always recorded.
     * Use for timeouts, retries, unexpected but non-fatal conditions.
     */
    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        writeToFile("W/$tag: $msg")
    }

    /**
     * Error: failures — always recorded, including full stack trace.
     * Use for exceptions, protocol errors, connection drops.
     */
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) {
            Log.e(tag, msg, tr)
            writeToFile("E/$tag: $msg\n${Log.getStackTraceString(tr)}")
        } else {
            Log.e(tag, msg)
            writeToFile("E/$tag: $msg")
        }
    }

    @Synchronized
    private fun writeToFile(line: String) {
        val file = logFile ?: return
        try {
            if (file.length() > MAX_LOG_SIZE) rotateLog()
            val ts = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            file.appendText("$ts $line\n")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write log file", e)
        }
    }

    private fun rotateLog() {
        val dir = logDir ?: return
        // Delete old logs beyond newest 3
        val files = dir.listFiles { f -> f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        for (old in files.drop(3)) old.delete()

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        logFile = File(dir, "fxxk_$ts.log")
        try {
            logFile?.createNewFile()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create log file", e)
        }
    }

    fun getLogFiles(): List<File> {
        return logDir?.listFiles { f -> f.name.endsWith(".log") }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun getLatestLogFile(): File? = getLogFiles().firstOrNull()

    fun getLogContent(): String {
        val file = getLatestLogFile() ?: return "No logs yet"
        return try {
            file.readText()
        } catch (e: Exception) {
            "Failed to read log: ${e.message}"
        }
    }

    /** Create a share Intent with the latest log file attached */
    fun createShareLogIntent(context: Context): Intent? {
        val file = getLatestLogFile() ?: return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "fxxkHilife Debug Log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
