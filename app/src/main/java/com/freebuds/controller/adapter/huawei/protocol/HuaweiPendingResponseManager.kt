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
    private val lock = Any()
    private val pendingResponses = mutableMapOf<String, CompletableDeferred<HuaweiSppPackage>>()

    suspend fun register(
        responseId: String,
        timeoutMs: Long,
    ): CompletableDeferred<HuaweiSppPackage>? = withTimeoutOrNull(timeoutMs) {
        while (true) {
            var acquired: CompletableDeferred<HuaweiSppPackage>? = null
            val current = synchronized(lock) {
                pendingResponses[responseId] ?: CompletableDeferred<HuaweiSppPackage>().also {
                    pendingResponses[responseId] = it
                    acquired = it
                }
            }
            if (acquired != null) return@withTimeoutOrNull acquired

            // Requests with the same response id must not replace each other. Wait only within
            // the caller's total deadline, then let the caller fail instead of hanging forever.
            current.join()
            yield()
        }
    }

    suspend fun complete(commandId: String, pkg: HuaweiSppPackage): Boolean = synchronized(lock) {
        val deferred = pendingResponses[commandId]
        if (deferred != null && !deferred.isCompleted) {
            deferred.complete(pkg)
            true
        } else {
            false
        }
    }

    fun remove(responseId: String, deferred: CompletableDeferred<HuaweiSppPackage>) {
        synchronized(lock) {
            if (pendingResponses[responseId] === deferred) {
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
        pendingResponses.values.forEach { it.cancel() }
        pendingResponses.clear()
    }
}
