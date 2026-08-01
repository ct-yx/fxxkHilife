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
