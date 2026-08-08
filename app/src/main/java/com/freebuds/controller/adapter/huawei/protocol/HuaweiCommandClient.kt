package com.freebuds.controller.adapter.huawei.protocol

import com.freebuds.controller.core.protocol.ProtocolSession
import com.freebuds.controller.protocol.HuaweiSppPackage
import com.freebuds.controller.util.LogBuffer
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

enum class HuaweiCommandPhase {
    SENT,
    ACKED,
    READ_BACK_CONFIRMED,
    READ_BACK_MISMATCH,
    TIMEOUT,
    FAILED,
}

/** Explicit result for a write/ACK/read-back transaction. */
data class HuaweiCommandExchange(
    val operation: String,
    val phase: HuaweiCommandPhase,
    val request: HuaweiSppPackage,
    val ack: HuaweiSppPackage? = null,
    val readBack: HuaweiSppPackage? = null,
    val error: String? = null,
)

/**
 * Huawei command client above [ProtocolSession].
 *
 * SppDriver remains a compatibility facade for existing handlers, but response waiters, the
 * single command lane and write/read-back result semantics now live here. This prevents a feature
 * handler from accidentally bypassing the same serialization used by initialization.
 */
class HuaweiCommandClient(
    private val protocolSession: ProtocolSession<HuaweiSppPackage>,
    private val pendingResponses: HuaweiPendingResponseManager = HuaweiPendingResponseManager(),
    private val scheduler: HuaweiCommandScheduler = HuaweiCommandScheduler(),
) {
    suspend fun sendPackage(
        pkg: HuaweiSppPackage,
        timeout: Long = DEFAULT_TIMEOUT_MS,
        responsePredicate: (HuaweiSppPackage) -> Boolean = { true },
        priority: HuaweiCommandPriority = HuaweiCommandPriority.BACKGROUND,
        operation: String = "request-${pkg.commandKey}",
    ): HuaweiSppPackage? = scheduler.execute(priority, operation) {
        executeLocked(pkg, timeout, responsePredicate, operation).ack
    }

    suspend fun sendNowait(
        pkg: HuaweiSppPackage,
        priority: HuaweiCommandPriority = HuaweiCommandPriority.BACKGROUND,
        operation: String = "send-${pkg.commandKey}",
    ) {
        scheduler.execute(priority, operation) {
            sendNowaitLocked(pkg)
        }
    }

    suspend fun writeAndReadBack(
        operation: String,
        write: HuaweiSppPackage,
        read: HuaweiSppPackage,
        writeTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
        readTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
        settleDelayMs: Long = 0L,
        writeResponsePredicate: (HuaweiSppPackage) -> Boolean = { true },
        readResponsePredicate: (HuaweiSppPackage) -> Boolean = { true },
        readBackPredicate: (HuaweiSppPackage) -> Boolean,
        priority: HuaweiCommandPriority = HuaweiCommandPriority.USER_ACTION,
    ): HuaweiCommandExchange = scheduler.execute(priority, operation) {
        val writeResult = executeLocked(
            pkg = write,
            timeout = writeTimeoutMs,
            responsePredicate = writeResponsePredicate,
            operation = "$operation.write",
        )
        if (writeResult.phase != HuaweiCommandPhase.ACKED) return@execute writeResult.copy(operation = operation)

        if (settleDelayMs > 0L) delay(settleDelayMs)
        val readResult = executeLocked(
            pkg = read,
            timeout = readTimeoutMs,
            responsePredicate = readResponsePredicate,
            operation = "$operation.read",
        )
        if (readResult.phase != HuaweiCommandPhase.ACKED) {
            return@execute HuaweiCommandExchange(
                operation = operation,
                phase = readResult.phase,
                request = write,
                ack = writeResult.ack,
                readBack = readResult.ack,
                error = readResult.error,
            )
        }

        return@execute HuaweiCommandExchange(
            operation = operation,
            phase = if (readBackPredicate(readResult.ack!!)) {
                HuaweiCommandPhase.READ_BACK_CONFIRMED
            } else {
                HuaweiCommandPhase.READ_BACK_MISMATCH
            },
            request = write,
            ack = writeResult.ack,
            readBack = readResult.ack,
        )
    }

    /** Completes a waiter before the packet is offered to feature notification handlers. */
    fun complete(pkg: HuaweiSppPackage): Boolean = pendingResponses.complete(pkg.commandKey, pkg)

    /** Defers the next command until the caller's quiet window has elapsed. */
    fun setQuietUntil(untilMs: Long) = scheduler.setQuietUntil(untilMs)

    fun cancelAll() {
        pendingResponses.cancelAll()
        scheduler.cancelAll()
    }

    private suspend fun executeLocked(
        pkg: HuaweiSppPackage,
        timeout: Long,
        responsePredicate: (HuaweiSppPackage) -> Boolean,
        operation: String,
    ): HuaweiCommandExchange {
        val responseId = pkg.responseKey
        if (responseId.isEmpty()) {
            return try {
                sendNowaitLocked(pkg)
                HuaweiCommandExchange(operation, HuaweiCommandPhase.SENT, pkg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HuaweiCommandExchange(operation, HuaweiCommandPhase.FAILED, pkg, error = errorText(e))
            }
        }

        // Waiting for an earlier request with the same response id is part of this exchange's
        // timeout budget.  Starting a fresh timeout after registration could otherwise turn a
        // 2-second low-latency write into nearly 4 seconds when a stale request occupied the
        // response slot, making the initialization deadline and the diagnostic timing disagree.
        val startedAt = SystemClock.elapsedRealtime()
        val deferred = pendingResponses.register(responseId, timeout, responsePredicate)
        if (deferred == null) {
            LogBuffer.w("SPP", "REQ slot timeout cmd=${pkg.commandKey} resp=$responseId timeout=${timeout}ms")
            return HuaweiCommandExchange(operation, HuaweiCommandPhase.TIMEOUT, pkg)
        }
        LogBuffer.d("SPP", "REQ start cmd=${pkg.commandKey} resp=$responseId timeout=${timeout}ms operation=$operation")

        return try {
            sendNowaitLocked(pkg)
            val remaining = (timeout - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(1L)
            val response = withTimeoutOrNull(remaining) { deferred.await() }
            if (response == null) {
                val pendingKeys = pendingResponses.keys().joinToString(",")
                LogBuffer.w(
                    "SPP",
                    "REQ response timeout cmd=${pkg.commandKey} resp=$responseId " +
                        "elapsed=${SystemClock.elapsedRealtime() - startedAt}ms pending=[$pendingKeys]",
                )
                HuaweiCommandExchange(operation, HuaweiCommandPhase.TIMEOUT, pkg)
            } else {
                LogBuffer.d("SPP", "REQ ACK cmd=${pkg.commandKey} resp=$responseId operation=$operation")
                HuaweiCommandExchange(operation, HuaweiCommandPhase.ACKED, pkg, ack = response)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogBuffer.w("SPP", "REQ failed cmd=${pkg.commandKey} resp=$responseId reason=${errorText(e)}")
            HuaweiCommandExchange(operation, HuaweiCommandPhase.FAILED, pkg, error = errorText(e))
        } finally {
            pendingResponses.remove(responseId, deferred)
        }
    }

    private suspend fun sendNowaitLocked(pkg: HuaweiSppPackage) {
        LogBuffer.frame("TX", pkg.toBytes())
        protocolSession.send(pkg)
    }

    private fun errorText(error: Exception): String =
        "${error.javaClass.simpleName}:${error.message ?: "unknown"}"

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000L
    }
}
