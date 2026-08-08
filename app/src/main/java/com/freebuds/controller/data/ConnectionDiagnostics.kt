package com.freebuds.controller.data

/**
 * The source that caused one control-channel connection attempt.
 *
 * Keeping this separate from [ConnectionState] is intentional: a connection can be requested
 * by several Android entry points while the user-facing state remains the same.
 */
enum class ConnectionTrigger {
    UserAction,
    ServiceCommand,
    AclConnected,
    AudioProfileConnected,
    PeriodicCheck,
    ScanCompleted,
    TileAction,
    /** A real-device regression run started from the debug terminal. */
    HardwareRegression,
}

/**
 * Timing boundaries used by the BT-0.2 baseline.
 *
 * These phases are diagnostic only for now. They must not be used as a replacement for the
 * existing connection state until the real-device baseline has been collected.
 */
enum class ConnectionPhase {
    Requested,
    SystemLinkObserved,
    DiscoveryStopped,
    TransportConnecting,
    TransportReady,
    Settling,
    CoreInitializing,
    CoreReady,
    DeferredInitializing,
    Ready,
    Degraded,
    Failed,
    Disconnecting,
    Disconnected,
}

/**
 * Terminal observation used by the hardware runner while waiting for one specific attempt.
 * A failed attempt is terminal too; waiting only for Connected turns an immediate RFCOMM
 * rejection into the full runner timeout and hides the real failure timing.
 */
internal enum class ConnectionAttemptWaitResult {
    Pending,
    Connected,
    Failed,
}

internal fun connectionAttemptWaitResult(
    state: ConnectionState,
    expectedAttemptId: String,
): ConnectionAttemptWaitResult = when (state) {
    is ConnectionState.Connected ->
        if (state.attemptId == expectedAttemptId) {
            ConnectionAttemptWaitResult.Connected
        } else {
            ConnectionAttemptWaitResult.Pending
        }

    is ConnectionState.Failed ->
        if (state.attemptId == expectedAttemptId) {
            ConnectionAttemptWaitResult.Failed
        } else {
            ConnectionAttemptWaitResult.Pending
        }

    else -> ConnectionAttemptWaitResult.Pending
}

data class ConnectionPhaseMark(
    val phase: ConnectionPhase,
    val elapsedSinceStartMs: Long,
    val firstMark: Boolean,
)

/**
 * Small monotonic timeline that is easy to test without an Android Bluetooth device.
 *
 * The production caller supplies a monotonic clock. A phase is recorded only once so a repeated
 * callback cannot silently replace the original boundary and corrupt P50/P95 measurements.
 */
class ConnectionAttemptTimeline(
    val attemptId: String,
    val address: String,
    val trigger: ConnectionTrigger,
    private val nowMs: () -> Long,
) {
    private val startedAtMs = nowMs()
    private val phaseTimes = linkedMapOf<ConnectionPhase, Long>()
    private val phaseLock = Any()

    fun mark(phase: ConnectionPhase): ConnectionPhaseMark {
        val now = nowMs()
        return synchronized(phaseLock) {
            val existing = phaseTimes[phase]
            if (existing != null) {
                return@synchronized ConnectionPhaseMark(
                    phase = phase,
                    elapsedSinceStartMs = existing - startedAtMs,
                    firstMark = false,
                )
            }
            phaseTimes[phase] = now
            ConnectionPhaseMark(
                phase = phase,
                elapsedSinceStartMs = now - startedAtMs,
                firstMark = true,
            )
        }
    }

    fun elapsed(from: ConnectionPhase, to: ConnectionPhase): Long? {
        return synchronized(phaseLock) {
            val start = phaseTimes[from]
            val end = phaseTimes[to]
            if (start == null || end == null) null else (end - start).coerceAtLeast(0L)
        }
    }

    fun elapsedSinceStart(): Long = (nowMs() - startedAtMs).coerceAtLeast(0L)

    fun hasMarked(phase: ConnectionPhase): Boolean = synchronized(phaseLock) {
        phase in phaseTimes
    }

    fun phaseTimes(): Map<ConnectionPhase, Long> = synchronized(phaseLock) {
        phaseTimes.toMap()
    }
}
