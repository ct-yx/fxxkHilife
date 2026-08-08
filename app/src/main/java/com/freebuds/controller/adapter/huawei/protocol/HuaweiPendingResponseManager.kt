package com.freebuds.controller.adapter.huawei.protocol

import com.freebuds.controller.protocol.HuaweiSppPackage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

/**
 * Tracks in-flight Huawei SPP request/response waiters.
 *
 * Keeps the mutable pending-response map out of SppDriver so the driver only
 * coordinates transport, parsing and dispatching. Keys are lower-case hex
 * command/response ids, matching HuaweiSppPackage.toHex() call sites.
 */
class HuaweiPendingResponseManager {
    private data class PendingResponse(
        val deferred: CompletableDeferred<HuaweiSppPackage>,
        val accepts: (HuaweiSppPackage) -> Boolean,
    )

    private val lock = Any()
    private val pendingResponses = mutableMapOf<String, PendingResponse>()

    suspend fun register(
        responseId: String,
        timeoutMs: Long,
        accepts: (HuaweiSppPackage) -> Boolean = { true },
    ): CompletableDeferred<HuaweiSppPackage>? = withTimeoutOrNull(timeoutMs) {
        acquire(responseId, accepts)
    }

    private suspend fun acquire(
        responseId: String,
        accepts: (HuaweiSppPackage) -> Boolean,
    ): CompletableDeferred<HuaweiSppPackage> {
        while (true) {
            var acquired: CompletableDeferred<HuaweiSppPackage>? = null
            val current = synchronized(lock) {
                pendingResponses[responseId]?.deferred ?: CompletableDeferred<HuaweiSppPackage>().also {
                    pendingResponses[responseId] = PendingResponse(it, accepts)
                    acquired = it
                }
            }
            if (acquired != null) return acquired

            // Requests with the same response id must not replace each other. Wait only within
            // the caller's total deadline, then let the caller fail instead of hanging forever.
            current.join()
            yield()
        }
    }

    fun complete(commandId: String, pkg: HuaweiSppPackage): Boolean = synchronized(lock) {
        val pending = pendingResponses[commandId]
        if (pending != null && !pending.deferred.isCompleted && pending.accepts(pkg)) {
            pending.deferred.complete(pkg)
            true
        } else {
            false
        }
    }

    fun remove(responseId: String, deferred: CompletableDeferred<HuaweiSppPackage>) {
        synchronized(lock) {
            if (pendingResponses[responseId]?.deferred === deferred) {
                pendingResponses.remove(responseId)
            }
        }
        deferred.cancel()
    }

    suspend fun keys(): List<String> = synchronized(lock) {
        pendingResponses.keys.toList()
    }

    suspend fun clear() {
        synchronized(lock) {
            clearLocked()
        }
    }

    fun cancelAll() {
        synchronized(lock) {
            clearLocked()
        }
    }

    private fun clearLocked() {
        pendingResponses.values.forEach { it.deferred.cancel() }
        pendingResponses.clear()
    }
}
