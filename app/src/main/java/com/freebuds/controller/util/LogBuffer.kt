package com.freebuds.controller.util

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogBuffer {

    private val log = mutableListOf<LogEntry>()
    private val listeners = mutableListOf<OnLogUpdateListener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: Level,
        val tag: String,
        val message: String
    ) {
        val formattedTime: String get() = dateFormat.format(Date(timestamp))

        enum class Level { I, W, E, D }

        val levelChar: Char get() = level.name[0]
    }

    fun i(tag: String, msg: String) = add(LogEntry.Level.I, tag, msg)
    fun w(tag: String, msg: String) = add(LogEntry.Level.W, tag, msg)
    fun e(tag: String, msg: String) = add(LogEntry.Level.E, tag, msg)
    fun d(tag: String, msg: String) = add(LogEntry.Level.D, tag, msg)

    private fun add(level: LogEntry.Level, tag: String, msg: String) {
        val entry = LogEntry(level = level, tag = tag, message = msg)
        synchronized(log) {
            log.add(entry)
            if (log.size > MAX_LINES) {
                log.removeAt(0)
            }
        }
        mainHandler.post { listeners.forEach { it.onLogUpdate() } }
    }

    fun getSnapshot(): List<LogEntry> = synchronized(log) { log.toList() }

    fun getSnapshotText(filter: String? = null): String {
        val entries = if (filter.isNullOrBlank()) getSnapshot()
        else getSnapshot().filter { it.levelChar.toString().equals(filter, ignoreCase = true) }
        return entries.joinToString("\n") { e ->
            "${e.formattedTime} [${e.levelChar}] [${e.tag}] ${e.message}"
        }
    }

    fun clear() {
        synchronized(log) { log.clear() }
        mainHandler.post { listeners.forEach { it.onLogUpdate() } }
    }

    fun registerListener(l: OnLogUpdateListener) = listeners.add(l)
    fun unregisterListener(l: OnLogUpdateListener) = listeners.remove(l)

    interface OnLogUpdateListener {
        fun onLogUpdate()
    }

    private const val MAX_LINES = 2000
}
