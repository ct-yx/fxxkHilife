package com.freebuds.controller.adapter.huawei.protocol

import com.freebuds.controller.core.transport.EndpointSource

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
    // Known model endpoints use the fast first pass. Failed handlers get the longer recovery
    // budget below, so slow/unknown devices retain a reliable fallback without delaying the
    // normal verified-model connection path.
    const val FAST_CORE_HANDLER_TIMEOUT_MS = 1_000L
    const val CORE_HANDLER_TIMEOUT_MS = 3_000L
    const val CORE_INTER_COMMAND_DELAY_MS = 80L
    const val CORE_RECOVERY_TIMEOUT_MS = 3_000L
    const val CORE_RECOVERY_ROUNDS = 3
    const val CORE_RECOVERY_ROUND_DELAY_MS = 1_000L
    const val BACKGROUND_HANDOFF_DELAY_MS = 250L

    /** The control actions users notice first must not wait behind battery telemetry. */
    val CORE_HANDLER_IDS_IN_ORDER = listOf(
        "drop_logs",
        "anc_global",
        "low_latency",
        "config_sound_quality",
        "battery",
        "tws_in_ear",
    )

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

    fun initialCoreTimeoutMs(source: EndpointSource): Long = when (source) {
        EndpointSource.VerifiedModelConfig -> FAST_CORE_HANDLER_TIMEOUT_MS
        EndpointSource.ServiceRecord,
        EndpointSource.RuntimeDiscovery,
        EndpointSource.CompatibilityFallback -> CORE_HANDLER_TIMEOUT_MS
    }

    /** One serialized initial wave covers the four request-based core handlers. */
    fun worstCaseCoreReadyMs(): Long {
        val commandGaps = (CORE_HANDLER_COUNT - 1) * CORE_INTER_COMMAND_DELAY_MS
        val oneWave = CORE_REQUEST_HANDLER_COUNT * CORE_HANDLER_TIMEOUT_MS + commandGaps
        return INITIAL_SETTLE_DELAY_MS + oneWave
    }

    fun worstCaseFastCoreReadyMs(): Long {
        val commandGaps = (CORE_HANDLER_COUNT - 1) * CORE_INTER_COMMAND_DELAY_MS
        val oneWave = CORE_REQUEST_HANDLER_COUNT * FAST_CORE_HANDLER_TIMEOUT_MS + commandGaps
        return INITIAL_SETTLE_DELAY_MS + oneWave
    }
}
