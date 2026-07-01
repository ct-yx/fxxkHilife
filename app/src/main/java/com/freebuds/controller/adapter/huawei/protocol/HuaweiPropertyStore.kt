package com.freebuds.controller.adapter.huawei.protocol

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Huawei/OpenFreebuds property store.
 *
 * Handlers expose device state through (group, prop) string properties. Keeping the mutable store
 * outside SppDriver makes the driver less responsible for vendor state representation and prepares
 * the later move toward a generic EarbudState mapper.
 */
class HuaweiPropertyStore {
    private val store = mutableMapOf<String, MutableMap<String, String>>()
    private val mutex = Mutex()

    suspend fun put(group: String, prop: String?, value: String?, extendGroup: Boolean = false) {
        mutex.withLock {
            if (prop == null) {
                val incoming = parseObject(value)
                store[group] = if (extendGroup) {
                    (store[group] ?: mutableMapOf()).apply { putAll(incoming) }
                } else {
                    incoming.toMutableMap()
                }
            } else {
                val target = store.getOrPut(group) { mutableMapOf() }
                if (value == null) target.remove(prop) else target[prop] = value
            }
        }
    }

    suspend fun get(group: String? = null, prop: String? = null, fallback: String? = null): String? {
        return mutex.withLock {
            when {
                group == null -> store.entries.joinToString("\n") { (g, props) ->
                    props.entries.joinToString("\n") { (p, v) -> "$g.$p=$v" }
                }
                prop == null -> store[group]?.entries?.joinToString("\n") { (p, v) -> "$group.$p=$v" } ?: fallback
                else -> store[group]?.get(prop) ?: fallback
            }
        }
    }

    suspend fun snapshot(): Map<String, Map<String, String>> {
        return mutex.withLock { store.mapValues { it.value.toMap() }.toMap() }
    }

    suspend fun clear() {
        mutex.withLock { store.clear() }
    }

    private fun parseObject(value: String?): Map<String, String> {
        if (value.isNullOrBlank()) return emptyMap()
        return value.lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
            }
            .toMap()
    }
}