package com.freebuds.controller.core.transport

import java.util.UUID

/**
 * Connection endpoint and timing selected for one RFCOMM session.
 *
 * The current Android bridge opens the channel-based RFCOMM API. [serviceUuid] is retained in the
 * config so a future service-record path can use the same endpoint contract without putting UUID
 * selection back into SppDriver.
 */
data class RfcommTransportConfig(
    val channel: Int = DEFAULT_CHANNEL,
    val serviceUuid: UUID? = null,
    val source: EndpointSource = EndpointSource.CompatibilityFallback,
    val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    /** Retry only an immediate RFCOMM rejection; a real connect timeout remains terminal. */
    val immediateRetryCount: Int = DEFAULT_IMMEDIATE_RETRY_COUNT,
    val immediateRetryDelayMs: Long = DEFAULT_IMMEDIATE_RETRY_DELAY_MS,
) {
    init {
        require(channel in MIN_CHANNEL..MAX_CHANNEL) { "RFCOMM channel must be $MIN_CHANNEL..$MAX_CHANNEL" }
        require(connectTimeoutMs > 0) { "connectTimeoutMs must be positive" }
        require(immediateRetryCount >= 0) { "immediateRetryCount must not be negative" }
        require(immediateRetryDelayMs >= 0) { "immediateRetryDelayMs must not be negative" }
    }

    fun endpointDescription(): String = buildString {
        append("rfcomm-channel=")
        append(channel)
        append(" source=")
        append(source.name)
        serviceUuid?.let {
            append(" uuid=")
            append(it)
        }
    }

    companion object {
        const val DEFAULT_CHANNEL = 1
        const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000L
        const val DEFAULT_IMMEDIATE_RETRY_COUNT = 1
        const val DEFAULT_IMMEDIATE_RETRY_DELAY_MS = 250L
        private const val MIN_CHANNEL = 1
        private const val MAX_CHANNEL = 30

        fun compatibilityFallback(): RfcommTransportConfig = RfcommTransportConfig()
    }
}

enum class EndpointSource {
    VerifiedModelConfig,
    ServiceRecord,
    RuntimeDiscovery,
    CompatibilityFallback,
}

/** Optional adapter capability for protocols that use RFCOMM. */
interface RfcommTransportConfigProvider {
    fun rfcommTransportConfig(deviceName: String?): RfcommTransportConfig
}
