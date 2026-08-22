package com.freebuds.controller.util

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.max

/**
 * 进程内诊断日志。
 *
 * 日志写入来自 RFCOMM I/O 线程，因此这里避免了 ArrayList 头部删除和每条日志一次 UI
 * 刷新的做法。导出的报告包含会话元信息与摘要，便于直接定位连接/初始化问题。
 */
object LogBuffer {

    class BoundedCaptureToken internal constructor(
        internal val previousMaxLines: Int,
        internal val previousMaxBytes: Long?,
    )

    enum class Level { I, W, E, D }

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val monotonicMs: Long = SystemClock.elapsedRealtime(),
        val level: Level,
        val tag: String,
        val message: String,
    ) {
        val formattedTime: String get() = LogBuffer.timestampFormatter.format(Instant.ofEpochMilli(timestamp))
        val levelChar: Char get() = level.name[0]
    }

    private const val DEFAULT_MAX_LINES = 2_000
    private const val MIN_LINES = 100
    // A normal session stays small. The debug-only hardware regression runner switches to a byte
    // budget instead of a line limit, so long A-F runs retain all lines without producing a file
    // that is too large to share or process.
    private const val MAX_LINES = 50_000

    private val logLock = Any()
    private val listenerLock = Any()
    private val log = ArrayDeque<LogEntry>(DEFAULT_MAX_LINES)
    private val listeners = mutableSetOf<OnLogUpdateListener>()
    private val metadata = linkedMapOf<String, String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timestampFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        .withZone(ZoneId.systemDefault())

    @Volatile private var maxLines = DEFAULT_MAX_LINES
    @Volatile private var maxBytes: Long? = null
    private var storedBytes = 0L
    @Volatile private var protocolFrameLoggingEnabled = false
    private var notificationQueued = false
    @Volatile private var sessionStartedAt = System.currentTimeMillis()

    fun i(tag: String, msg: String) = add(Level.I, tag, msg)
    fun w(tag: String, msg: String) = add(Level.W, tag, msg)
    fun e(tag: String, msg: String) = add(Level.E, tag, msg)
    fun d(tag: String, msg: String) = add(Level.D, tag, msg)

    /** Raw SPP frames are valuable when diagnosing a device, but are intentionally opt-in. */
    fun frame(direction: String, bytes: ByteArray) {
        if (!protocolFrameLoggingEnabled) return
        d("Frame", "$direction bytes=${bytes.size} hex=${bytes.toHex()}")
    }

    fun setProtocolFrameLogging(enabled: Boolean) {
        if (protocolFrameLoggingEnabled == enabled) return
        protocolFrameLoggingEnabled = enabled
        i("Log", "Raw SPP frame capture ${if (enabled) "enabled" else "disabled"}")
    }

    fun isProtocolFrameLoggingEnabled(): Boolean = protocolFrameLoggingEnabled

    fun startSession(values: Map<String, String>) {
        sessionStartedAt = System.currentTimeMillis()
        synchronized(logLock) {
            metadata.clear()
            metadata.putAll(values.filterValues { it.isNotBlank() })
        }
        i("Session", "Started; ${values.entries.joinToString(" ") { "${it.key}=${it.value}" }}")
    }

    fun putMetadata(key: String, value: String?) {
        if (value.isNullOrBlank()) return
        synchronized(logLock) { metadata[key] = value }
    }

    /**
     * Enter a capture mode with no line-count limit and a hard byte budget.
     * The token restores the normal logging policy after the report is built.
     */
    fun beginBoundedCapture(maxBytes: Long): BoundedCaptureToken {
        require(maxBytes > 0) { "capture byte budget must be positive" }
        synchronized(logLock) {
            val token = BoundedCaptureToken(
                previousMaxLines = this.maxLines,
                previousMaxBytes = this.maxBytes,
            )
            this.maxLines = Int.MAX_VALUE
            this.maxBytes = maxBytes
            trimToLimits()
            return token
        }
    }

    fun endBoundedCapture(token: BoundedCaptureToken) {
        synchronized(logLock) {
            maxLines = token.previousMaxLines
            maxBytes = token.previousMaxBytes
            trimToLimits()
        }
    }

    fun isBoundedCaptureActive(): Boolean = maxBytes != null

    fun getStoredBytes(): Long = synchronized(logLock) { storedBytes }

    private fun add(level: Level, tag: String, msg: String) {
        val entry = LogEntry(level = level, tag = tag, message = msg)
        val entryBytes = entry.estimatedBytes()
        synchronized(logLock) {
            // A single pathological message must not bypass the active byte budget. Normal
            // protocol/frame entries are far below this size; dropping only this oversized entry
            // preserves the hard upper bound for the test capture.
            if (maxBytes?.let { entryBytes > it } == true) return
            while (log.isNotEmpty() && (log.size >= maxLines || exceedsByteBudget(entryBytes))) {
                removeFirstEntry()
            }
            log.addLast(entry)
            storedBytes += entryBytes
        }
        notifyListeners()
    }

    fun getSnapshot(): List<LogEntry> = synchronized(logLock) { log.toList() }

    fun getSnapshotText(filter: String? = null, maxEntries: Int? = null): String {
        val entries = filteredEntries(filter)
        val visible = maxEntries?.let { entries.takeLast(it.coerceAtLeast(1)) } ?: entries
        return buildString {
            if (visible.size < entries.size) {
                append("… ${entries.size - visible.size} earlier entries hidden in terminal …\n")
            }
            appendEntries(visible)
        }
    }

    fun getSummaryText(): String {
        return summaryText(getSnapshot())
    }

    private fun summaryText(entries: List<LogEntry>): String {
        val counts = Level.entries.associateWith { level -> entries.count { it.level == level } }
        val first = entries.firstOrNull()?.formattedTime ?: "-"
        val last = entries.lastOrNull()?.formattedTime ?: "-"
        return "entries=${entries.size}/${getMaxLines()} I=${counts[Level.I]} W=${counts[Level.W]} " +
            "E=${counts[Level.E]} D=${counts[Level.D]} range=$first..$last rawFrames=$protocolFrameLoggingEnabled"
    }

    /** Full, self-describing diagnostic bundle used by both share entry points. */
    fun getDiagnosticReport(): String {
        val entries = getSnapshot()
        val metadataSnapshot = synchronized(logLock) { metadata.toMap() }
        return buildString {
            appendLine("fxxkHilife diagnostic report")
            appendLine("format=2")
            appendLine("exportedAt=${timestampFormatter.format(Instant.now())}")
            appendLine("sessionStartedAt=${timestampFormatter.format(Instant.ofEpochMilli(sessionStartedAt))}")
            appendLine("rawSppFrames=$protocolFrameLoggingEnabled")
            appendLine(summaryText(entries))
            metadataSnapshot.forEach { (key, value) -> appendLine("meta.$key=$value") }
            appendLine("--- logs ---")
            appendEntries(entries)
            CrashReporter.latestReport()?.let { crash ->
                appendLine()
                appendLine("--- last uncaught exception ---")
                append(crash.trimEnd())
            }
        }
    }

    fun clear() {
        synchronized(logLock) {
            log.clear()
            storedBytes = 0L
        }
        notifyListeners()
    }

    fun setMaxLines(max: Int) {
        synchronized(logLock) {
            maxLines = max.coerceIn(MIN_LINES, MAX_LINES)
            trimToLimits()
        }
        notifyListeners()
    }

    fun getMaxLines(): Int = maxLines

    fun registerListener(listener: OnLogUpdateListener) {
        synchronized(listenerLock) { listeners.add(listener) }
    }

    fun unregisterListener(listener: OnLogUpdateListener) {
        synchronized(listenerLock) { listeners.remove(listener) }
    }

    private fun filteredEntries(filter: String?): List<LogEntry> {
        val entries = getSnapshot()
        val normalized = filter?.trim()?.uppercase(Locale.ROOT)
        return when (normalized) {
            null, "", "ALL" -> entries
            else -> entries.filter { it.level.name == normalized }
        }
    }

    private fun StringBuilder.appendEntries(entries: List<LogEntry>) {
        entries.forEachIndexed { index, entry ->
            append(entry.formattedTime)
            append(" +")
            append(entry.monotonicMs)
            append("ms [")
            append(entry.levelChar)
            append("] [")
            append(entry.tag)
            append("] ")
            append(entry.message)
            if (index != entries.lastIndex) append('\n')
        }
    }

    private fun exceedsByteBudget(nextEntryBytes: Long): Boolean =
        maxBytes?.let { storedBytes + nextEntryBytes > it } == true

    private fun trimToLimits() {
        while (log.isNotEmpty() && (log.size > maxLines || exceedsByteBudget(0L))) {
            removeFirstEntry()
        }
    }

    private fun removeFirstEntry() {
        val removed = log.removeFirst()
        storedBytes = max(0L, storedBytes - removed.estimatedBytes())
    }

    private fun LogEntry.estimatedBytes(): Long = buildString {
        append(formattedTime)
        append(" +")
        append(monotonicMs)
        append("ms [")
        append(levelChar)
        append("] [")
        append(tag)
        append("] ")
        append(message)
        append('\n')
    }.toByteArray(Charsets.UTF_8).size.toLong()

    /** Coalesce bursts so a terminal never renders once for every packet event. */
    private fun notifyListeners() {
        synchronized(listenerLock) {
            if (notificationQueued) return
            notificationQueued = true
        }
        mainHandler.postDelayed({
            val snapshot = synchronized(listenerLock) {
                notificationQueued = false
                listeners.toList()
            }
            snapshot.forEach { it.onLogUpdate() }
        }, 120L)
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) append("%02x".format(Locale.US, byte.toInt() and 0xFF))
    }

    interface OnLogUpdateListener {
        fun onLogUpdate()
    }
}
