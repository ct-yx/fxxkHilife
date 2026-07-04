package com.freebuds.controller.adapter.huawei.protocol

import com.freebuds.controller.protocol.HuaweiSppPackage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

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

    suspend fun register(responseId: String): CompletableDeferred<HuaweiSppPackage> {
        while (true) {
            val existing = synchronized(lock) { pendingResponses[responseId] }
            if (existing == null) break
            runCatching { existing.await() }
            delay(500)
        }

        val deferred = CompletableDeferred<HuaweiSppPackage>()
        synchronized(lock) {
            pendingResponses[responseId] = deferred
        }
        return deferred
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

    suspend fun remove(responseId: String) {
        synchronized(lock) {
            pendingResponses.remove(responseId)?.cancel()
        }
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
