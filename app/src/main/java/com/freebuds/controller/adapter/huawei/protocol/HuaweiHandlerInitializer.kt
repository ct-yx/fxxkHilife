package com.freebuds.controller.adapter.huawei.protocol

import com.freebuds.controller.bluetooth.HuaweiDeviceHandler
import com.freebuds.controller.bluetooth.SppDriver
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Coordinates Huawei/OpenFreebuds handler initialization order and retry policy.
 *
 * SppDriver should not need to know which handlers are core, which models need a
 * fast path, or how init failures are tracked. It only provides transport APIs
 * and invokes this initializer after the receive loop has started.
 */
class HuaweiHandlerInitializer(private val registry: HuaweiHandlerRegistry) {
    suspend fun initialize(driver: SppDriver, deviceLabel: String?) {
        val label = deviceLabel ?: "FreeBuds"
        if (label.contains("FreeBuds 6i", ignoreCase = true) ||
            label.contains("FreeBuds 7i", ignoreCase = true)
        ) {
            initializeCoreStateFastPath(driver, label)
            return
        }

        initializeCoreFirstStaggered(driver)
    }

    private suspend fun initializeCoreFirstStaggered(driver: SppDriver) {
        try {
            val orderedHandlers = coreFirstHandlers()
            val gapMs = 140L
            withTimeout(12000) {
                LogBuffer.i(
                    "SPP",
                    "Starting core-first staggered init for ${registry.allHandlers().size} handlers (gap=${gapMs}ms, perHandler=1.5s×3, timeout=12s)"
                )
                coroutineScope {
                    orderedHandlers.mapIndexed { index, handler ->
                        launch {
                            if (index > 0) delay(index * gapMs)
                            var success = false
                            for (attempt in 0 until 3) {
                                try {
                                    withTimeout(1500) {
                                        handler.onInit(driver)
                                    }
                                    success = true
                                    LogBuffer.i("SPP", "Init ${handler.id} success (attempt=${attempt + 1})")
                                    break
                                } catch (e: TimeoutCancellationException) {
                                    LogBuffer.w("SPP", "Init ${handler.id} timeout (attempt=${attempt + 1})")
                                } catch (e: Exception) {
                                    LogBuffer.w("SPP", "Init ${handler.id} failed (attempt=${attempt + 1}): ${e.message}")
                                }
                            }
                            if (!success) {
                                LogBuffer.w("SPP", "Can't initialize ${handler.id}. Skipping.")
                                registry.failedHandlerIds.add(handler.id)
                            }
                        }
                    }.joinAll()
                }
                LogBuffer.i(
                    "SPP",
                    if (registry.failedHandlerIds.isEmpty()) "All handlers initialized"
                    else "Staggered init completed, ${registry.failedHandlerIds.size} failed: ${registry.failedHandlerIds}"
                )
            }
        } catch (e: TimeoutCancellationException) {
            LogBuffer.w("SPP", "Staggered init global timeout reached, proceeding with partial results")
        }
    }

    private fun coreFirstHandlers(): List<HuaweiDeviceHandler> {
        val coreIdsInOrder = listOf(
            "drop_logs",
            "battery",
            "anc_global",
            "low_latency",
            "config_sound_quality",
            "tws_in_ear",
        )
        val all = registry.allHandlers()
        val core = coreIdsInOrder.mapNotNull { id -> all.find { it.id == id } }
        return core + all.filter { handler -> handler.id !in coreIdsInOrder }
    }

    /**
     * FreeBuds 6i / 7i are sensitive to concurrent SPP initialization. The fast
     * path reads the core state first and leaves non-critical handlers for retry.
     */
    private suspend fun initializeCoreStateFastPath(driver: SppDriver, deviceLabel: String) {
        val fastIdsInOrder = listOf(
            "drop_logs",
            "battery",
            "anc_global",
            "low_latency",
            "config_sound_quality",
            "tws_in_ear",
        )
        val all = registry.allHandlers()
        val fastHandlers = fastIdsInOrder.mapNotNull { id -> all.find { it.id == id } }
        val deferredHandlers = all.filter { it.id !in fastIdsInOrder }

        LogBuffer.i(
            "SPP",
            "Starting $deviceLabel core-state fast init for ${fastHandlers.size}/${all.size} handlers; deferred=${deferredHandlers.map { it.id }}"
        )

        coroutineScope {
            fastHandlers.mapIndexed { index, handler ->
                async {
                    if (index > 0) delay(index * 90L)
                    try {
                        withTimeout(1500) {
                            handler.onInit(driver)
                        }
                        LogBuffer.i("SPP", "Core-state fast init ${handler.id} success")
                        null
                    } catch (e: TimeoutCancellationException) {
                        LogBuffer.w("SPP", "Core-state fast init ${handler.id} timeout")
                        handler.id
                    } catch (e: Exception) {
                        LogBuffer.w("SPP", "Core-state fast init ${handler.id} failed: ${e.message}")
                        handler.id
                    }
                }
            }.awaitAll().filterNotNull().forEach { registry.failedHandlerIds.add(it) }
        }

        registry.failedHandlerIds.addAll(deferredHandlers.map { it.id })
                LogBuffer.i("SPP", "$deviceLabel core-state fast init completed; deferred retry=${registry.failedHandlerIds}")
    }
}
