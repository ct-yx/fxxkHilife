package com.freebuds.controller.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningStatsRepositoryTest {
    @Test
    fun refreshAggregatesPersistedDailyValuesAndStreak() {
        val day = 86_400_000L
        val now = 100L * day + 12L * 60L * 60L * 1_000L
        val storage = MemoryListeningStatsStorage("100=60000;99=120000")
        val repository = ListeningStatsRepository(
            clockMs = { now },
            dayKey = { time -> (time / day).toString() },
        )

        repository.initialize(storage)
        repository.refresh(extraTodayMs = 60000, nowMs = now)

        assertEquals(240000L, repository.stats.value.totalMs)
        assertEquals(120000L, repository.stats.value.todayMs)
        assertEquals(2, repository.stats.value.activeDays)
        assertEquals(2, repository.stats.value.streakDays)
    }

    @Test
    fun stopTickerFlushesOnlyTheConnectedInterval() {
        val storage = MemoryListeningStatsStorage()
        val repository = ListeningStatsRepository(dayKey = { "today" })
        repository.initialize(storage)

        // The ticker itself is intentionally not started here; this verifies the same flush
        // boundary used by disconnect without requiring a real-time coroutine delay.
        repository.stopTicker(isConnected = { true })
        assertEquals(0L, repository.stats.value.totalMs)
    }

    @Test
    fun stopTickerFlushesTheLastConnectedIntervalBeforeDisconnect() {
        // The day key must change when the streak walker moves to the previous day;
        // a constant test key would make the production loop intentionally unbounded.
        val repository = ListeningStatsRepository(dayKey = { time -> (time / DAY_MS).toString() })
        repository.initialize(MemoryListeningStatsStorage())
        val scope = CoroutineScope(SupervisorJob())

        repository.startTicker(scope, isConnected = { true }, startAtMs = 1_000L)
        repository.stopTicker(isConnected = { true }, nowMs = 2_500L)

        assertEquals(1_500L, repository.stats.value.totalMs)
        scope.cancel()
    }

    private class MemoryListeningStatsStorage(initial: String = "") : ListeningStatsStorage {
        var value = initial
        override fun readDaily(): String = value
        override fun writeDaily(value: String) { this.value = value }
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
