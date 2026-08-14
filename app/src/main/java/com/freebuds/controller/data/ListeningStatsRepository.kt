package com.freebuds.controller.data

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.Locale

data class ListeningStats(
    val totalMs: Long = 0L,
    val todayMs: Long = 0L,
    val activeDays: Int = 0,
    val streakDays: Int = 0,
    val dailyMs: Map<String, Long> = emptyMap(),
)

/** Persistence boundary for listening statistics, kept independent from Android preferences. */
interface ListeningStatsStorage {
    fun readDaily(): String
    fun writeDaily(value: String)
}

class SharedPreferencesListeningStatsStorage(
    private val preferences: SharedPreferences,
) : ListeningStatsStorage {
    override fun readDaily(): String =
        preferences.getString(KEY_DAILY_LISTENING_MS, "") ?: ""

    override fun writeDaily(value: String) {
        preferences.edit().putString(KEY_DAILY_LISTENING_MS, value).apply()
    }

    private companion object {
        const val KEY_DAILY_LISTENING_MS = "listening_daily_ms"
    }
}

/**
 * Owns listening-time persistence and its connection-scoped ticker.
 *
 * The Bluetooth repository only starts/stops this component when the control session changes;
 * it no longer owns date parsing, persistence, or ticker bookkeeping.
 */
class ListeningStatsRepository(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val dayKey: (Long) -> String = ::defaultListeningDayKey,
) {
    private val _stats = MutableStateFlow(ListeningStats())
    val stats: StateFlow<ListeningStats> = _stats.asStateFlow()

    private var storage: ListeningStatsStorage? = null
    private var tickerJob: Job? = null
    private var lastListeningTickMs: Long = 0L
    private val stateLock = Any()

    fun initialize(storage: ListeningStatsStorage) {
        synchronized(stateLock) {
            this.storage = storage
            refreshLocked()
        }
    }

    fun refresh(extraTodayMs: Long = 0L, nowMs: Long = clockMs()) {
        synchronized(stateLock) {
            refreshLocked(extraTodayMs, nowMs)
        }
    }

    private fun refreshLocked(extraTodayMs: Long = 0L, nowMs: Long = clockMs()) {
        val daily = readDaily().toMutableMap()
        if (extraTodayMs > 0L) {
            val today = dayKey(nowMs)
            daily[today] = (daily[today] ?: 0L) + extraTodayMs
            writeDaily(daily)
        }

        val today = dayKey(nowMs)
        var streak = 0
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMs }
        while (true) {
            val key = dayKey(calendar.timeInMillis)
            if ((daily[key] ?: 0L) <= 0L) break
            streak++
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        _stats.value = ListeningStats(
            totalMs = daily.values.sum(),
            todayMs = daily[today] ?: 0L,
            activeDays = daily.count { it.value > 0L },
            streakDays = streak,
            dailyMs = daily.toMap(),
        )
    }

    fun startTicker(
        scope: CoroutineScope,
        isConnected: () -> Boolean,
        startAtMs: Long = clockMs(),
    ) {
        synchronized(stateLock) {
            tickerJob?.cancel()
            lastListeningTickMs = startAtMs
            tickerJob = scope.launch {
                while (isActive) {
                    delay(TICK_INTERVAL_MS)
                    val now = clockMs()
                    synchronized(stateLock) {
                        if (isActive) {
                            val delta = (now - lastListeningTickMs)
                                .coerceIn(0L, MAX_TICK_DELTA_MS)
                            if (isConnected()) refreshLocked(delta, now)
                            lastListeningTickMs = now
                        }
                    }
                }
            }
        }
    }

    fun stopTicker(isConnected: () -> Boolean, nowMs: Long = clockMs()) {
        val now = nowMs
        val delta = synchronized(stateLock) {
            val pending = if (lastListeningTickMs > 0L) {
                (now - lastListeningTickMs).coerceIn(0L, MAX_TICK_DELTA_MS)
            } else {
                0L
            }
            tickerJob?.cancel()
            tickerJob = null
            lastListeningTickMs = 0L
            pending
        }
        if (delta > 0L && isConnected()) refresh(delta, now)
    }

    private fun readDaily(): Map<String, Long> {
        val raw = storage?.readDaily().orEmpty()
        return raw.split(';')
            .mapNotNull { item ->
                val separator = item.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = item.substring(0, separator)
                val value = item.substring(separator + 1).toLongOrNull() ?: return@mapNotNull null
                key.takeIf { it.isNotBlank() }?.let { it to value }
            }
            .filter { it.second > 0L }
            .toMap()
    }

    private fun writeDaily(daily: Map<String, Long>) {
        val compact = daily.entries
            .sortedBy { it.key }
            .takeLast(MAX_STORED_DAYS)
            .joinToString(";") { "${it.key}=${it.value}" }
        storage?.writeDaily(compact)
    }

    private companion object {
        const val TICK_INTERVAL_MS = 60_000L
        const val MAX_TICK_DELTA_MS = 5 * 60_000L
        const val MAX_STORED_DAYS = 180
    }
}

private fun defaultListeningDayKey(timeMs: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = timeMs }
    return String.format(
        Locale.US,
        "%04d-%02d-%02d",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
    )
}
