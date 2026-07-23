package com.freebuds.controller.adapter.huawei.protocol

/**
 * Timing policy for the Huawei SPP initialization phases.
 *
 * The device uses a single RFCOMM command channel.  A number of models, including the
 * FreeBuds 6i, only answer the most recent request when several state reads are outstanding.
 * Core reads are therefore intentionally serialized; optional capability probing remains
 * best-effort and is bounded so it never holds the UI in a "syncing" state indefinitely.
 */
internal object HuaweiHandlerInitializationPolicy {
    const val INITIAL_SETTLE_DELAY_MS = 250L
    const val CORE_HANDLER_TIMEOUT_MS = 1_000L
    const val CORE_INTER_COMMAND_DELAY_MS = 80L
    const val CORE_RETRY_DELAY_MS = 700L
    private const val CORE_HANDLER_COUNT = 6
    private const val CORE_REQUEST_HANDLER_COUNT = 4

    private const val DEFERRED_HANDLER_TIMEOUT_MS = 800L
    private const val DUAL_CONNECT_TIMEOUT_MS = 1_200L
    const val DEFERRED_INTER_COMMAND_DELAY_MS = 80L

    fun deferredTimeoutMs(handlerId: String, declaredTimeoutMs: Long): Long = when (handlerId) {
        // Enumeration intentionally accepts partial results after roughly one second.
        "dual_connect" -> declaredTimeoutMs.coerceAtMost(DUAL_CONNECT_TIMEOUT_MS)
        else -> declaredTimeoutMs.coerceAtMost(DEFERRED_HANDLER_TIMEOUT_MS)
    }

    /**
     * Two serialized core waves cover the four request-based core handlers.  `drop_logs` and
     * `tws_in_ear` complete locally, but both retain their inter-command position.
     */
    fun worstCaseCoreReadyMs(): Long {
        val commandGaps = (CORE_HANDLER_COUNT - 1) * CORE_INTER_COMMAND_DELAY_MS
        val oneWave = CORE_REQUEST_HANDLER_COUNT * CORE_HANDLER_TIMEOUT_MS + commandGaps
        return INITIAL_SETTLE_DELAY_MS + oneWave + CORE_RETRY_DELAY_MS + oneWave
    }
}
