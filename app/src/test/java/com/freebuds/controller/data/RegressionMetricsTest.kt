package com.freebuds.controller.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RegressionMetricsTest {
    @Test
    fun percentileUsesCeilingRankAndHandlesUnsortedSamples() {
        val samples = listOf(900L, 100L, 500L, 300L, 700L)

        assertEquals(500L, RegressionMetrics.percentile(samples, 0.50))
        assertEquals(900L, RegressionMetrics.percentile(samples, 0.95))
    }

    @Test
    fun emptySampleSetIsReportedAsZero() {
        assertEquals(0L, RegressionMetrics.percentile(emptyList(), 0.95))
    }
}
