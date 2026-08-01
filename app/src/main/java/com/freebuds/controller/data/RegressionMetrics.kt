package com.freebuds.controller.data

/** Pure statistics used by the on-device regression report and unit-tested off-device. */
internal object RegressionMetrics {
    fun percentile(values: List<Long>, percentile: Double): Long {
        if (values.isEmpty()) return 0L
        require(percentile in 0.0..1.0) { "percentile must be between 0 and 1" }
        val sorted = values.sorted()
        val index = kotlin.math.ceil((sorted.size - 1) * percentile).toInt()
        return sorted[index.coerceIn(0, sorted.lastIndex)]
    }
}
