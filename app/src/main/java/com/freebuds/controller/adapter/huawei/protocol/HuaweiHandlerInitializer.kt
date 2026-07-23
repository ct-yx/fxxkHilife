package com.freebuds.controller.adapter.huawei.protocol

import android.os.SystemClock
import com.freebuds.controller.bluetooth.HuaweiDeviceHandler
import com.freebuds.controller.bluetooth.SppDriver
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
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
        initializeOpenFreebudsStyle(driver, label)
    }

    suspend fun initializeCore(driver: SppDriver, deviceLabel: String?) {
        val label = deviceLabel ?: "FreeBuds"
        val handlers = coreHandlers()
        val startedAt = System.currentTimeMillis()
        LogBuffer.i(
            "SPP",
            "CORE init start device=$label handlers=${handlers.map { it.id }} mode=serial " +
                "timeout=${HuaweiHandlerInitializationPolicy.CORE_HANDLER_TIMEOUT_MS}ms " +
                "gap=${HuaweiHandlerInitializationPolicy.CORE_INTER_COMMAND_DELAY_MS}ms"
        )

        handlers.forEachIndexed { index, handler ->
            val success = initializeHandlerIfNeeded(
                driver = driver,
                handler = handler,
                maxAttempts = 1,
                timeoutMs = handler.initTimeoutMs.coerceAtMost(
                    HuaweiHandlerInitializationPolicy.CORE_HANDLER_TIMEOUT_MS
                ),
            )
            recordResult(handler, success, maxAttempts = 1)
            if (index != handlers.lastIndex) {
                delay(HuaweiHandlerInitializationPolicy.CORE_INTER_COMMAND_DELAY_MS)
            }
        }
        LogBuffer.i(
            "SPP",
            "CORE init finish elapsed=${System.currentTimeMillis() - startedAt}ms failed=${registry.failedHandlerIds.filter { id -> handlers.any { it.id == id } }}"
        )
    }

    suspend fun initializeDeferred(driver: SppDriver, deviceLabel: String?) {
        val label = deviceLabel ?: "FreeBuds"
        val handlers = deferredHandlers()
        if (handlers.isEmpty()) return
        val startedAt = System.currentTimeMillis()
        LogBuffer.i(
            "SPP",
            "DEFERRED init start device=$label handlers=${handlers.map { it.id }} mode=best-effort"
        )
        handlers.forEachIndexed { index, handler ->
            initializeAndRecord(
                driver = driver,
                handler = handler,
                maxAttempts = 1,
                timeoutMs = HuaweiHandlerInitializationPolicy.deferredTimeoutMs(
                    handler.id,
                    handler.initTimeoutMs,
                ),
            )
            if (index != handlers.lastIndex) {
                delay(HuaweiHandlerInitializationPolicy.DEFERRED_INTER_COMMAND_DELAY_MS)
            }
        }
        LogBuffer.i(
            "SPP",
            if (registry.failedHandlerIds.isEmpty()) {
                "DEFERRED init finish elapsed=${System.currentTimeMillis() - startedAt}ms allHandlersReady=true"
            } else {
                "DEFERRED init finish elapsed=${System.currentTimeMillis() - startedAt}ms failed=${registry.failedHandlerIds}"
            }
        )
    }

    private suspend fun initializeOpenFreebudsStyle(driver: SppDriver, deviceLabel: String) {
        val orderedHandlers = coreFirstHandlers()
        LogBuffer.i(
            "SPP",
            "OpenFreebuds-style init for $deviceLabel: handlers=${orderedHandlers.map { it.id }}, per-handler timeouts/attempts follow OpenFreebuds defaults unless overridden"
        )

        for (handler in orderedHandlers) {
            initializeAndRecord(driver, handler, maxAttempts = handler.initAttemptMax, timeoutMs = handler.initTimeoutMs)
            delay(80)
        }

        LogBuffer.i(
            "SPP",
            if (registry.failedHandlerIds.isEmpty()) "OpenFreebuds-style init completed: all handlers ready"
            else "OpenFreebuds-style init completed with degraded handlers=${registry.failedHandlerIds}"
        )
    }

    private val coreIdsInOrder = listOf(
            "drop_logs",
            "battery",
            "anc_global",
            "low_latency",
            "config_sound_quality",
            "tws_in_ear",
    )

    private fun coreHandlers(): List<HuaweiDeviceHandler> {
        val all = registry.allHandlers()
        return coreIdsInOrder.mapNotNull { id -> all.find { it.id == id } }
    }

    private fun deferredHandlers(): List<HuaweiDeviceHandler> {
        return registry.allHandlers().filter { handler -> handler.id !in coreIdsInOrder }
    }

    private fun coreFirstHandlers(): List<HuaweiDeviceHandler> {
        return coreHandlers() + deferredHandlers()
    }

    private suspend fun initializeAndRecord(
        driver: SppDriver,
        handler: HuaweiDeviceHandler,
        maxAttempts: Int,
        timeoutMs: Long,
    ) {
        val success = initializeHandlerIfNeeded(driver, handler, maxAttempts, timeoutMs)
        recordResult(handler, success, maxAttempts)
    }

    private suspend fun initializeHandlerIfNeeded(
        driver: SppDriver,
        handler: HuaweiDeviceHandler,
        maxAttempts: Int,
        timeoutMs: Long,
    ): Boolean {
        if (hasExpectedInitState(driver, handler.id)) return true
        return initializeHandler(driver, handler, maxAttempts, timeoutMs)
    }

    private fun recordResult(handler: HuaweiDeviceHandler, success: Boolean, maxAttempts: Int) {
        if (success) {
            registry.failedHandlerIds.remove(handler.id)
        } else {
            LogBuffer.w("SPP", "Can't initialize ${handler.id}. Skipping after $maxAttempts attempts.")
            registry.failedHandlerIds.add(handler.id)
        }
    }

    private suspend fun initializeHandler(
        driver: SppDriver,
        handler: HuaweiDeviceHandler,
        maxAttempts: Int,
        timeoutMs: Long,
    ): Boolean {
        for (attempt in 0 until maxAttempts) {
            val attemptStartedAt = SystemClock.elapsedRealtime()
            try {
                LogBuffer.d("SPP", "INIT start handler=${handler.id} attempt=${attempt + 1}/$maxAttempts timeout=${timeoutMs}ms")
                withTimeout(timeoutMs) {
                    handler.onInit(driver)
                }
                if (hasExpectedInitState(driver, handler.id)) {
                    LogBuffer.i("SPP", "INIT success handler=${handler.id} attempt=${attempt + 1}/$maxAttempts elapsed=${SystemClock.elapsedRealtime() - attemptStartedAt}ms")
                    return true
                }
                LogBuffer.w("SPP", "INIT missing-state handler=${handler.id} attempt=${attempt + 1}/$maxAttempts elapsed=${SystemClock.elapsedRealtime() - attemptStartedAt}ms")
            } catch (e: TimeoutCancellationException) {
                if (hasExpectedInitState(driver, handler.id)) {
                    LogBuffer.i("SPP", "INIT late-success handler=${handler.id} attempt=${attempt + 1}/$maxAttempts elapsed=${SystemClock.elapsedRealtime() - attemptStartedAt}ms")
                    return true
                }
                LogBuffer.w("SPP", "INIT timeout handler=${handler.id} attempt=${attempt + 1}/$maxAttempts elapsed=${SystemClock.elapsedRealtime() - attemptStartedAt}ms limit=${timeoutMs}ms")
            } catch (e: Exception) {
                if (hasExpectedInitState(driver, handler.id)) {
                    LogBuffer.i("SPP", "INIT late-success handler=${handler.id} attempt=${attempt + 1}/$maxAttempts elapsed=${SystemClock.elapsedRealtime() - attemptStartedAt}ms reason=${e.javaClass.simpleName}")
                    return true
                }
                LogBuffer.w("SPP", "INIT failed handler=${handler.id} attempt=${attempt + 1}/$maxAttempts elapsed=${SystemClock.elapsedRealtime() - attemptStartedAt}ms reason=${e.javaClass.simpleName}:${e.message}")
            }
            delay(120)
        }
        return false
    }

    private suspend fun hasExpectedInitState(driver: SppDriver, handlerId: String): Boolean {
        if (handlerId == "dual_connect") {
            return !driver.getProperty("dual_connect", "devices").isNullOrBlank()
        }
        val checks = expectedInitProperties[handlerId] ?: return true
        return checks.any { (group, prop) -> driver.getProperty(group, prop) != null }
    }

    private val expectedInitProperties = mapOf(
        "battery" to listOf("battery" to "global", "battery" to "left", "battery" to "right", "battery" to "case"),
        "anc_global" to listOf("anc" to "mode"),
        "low_latency" to listOf("config" to "low_latency"),
        "config_sound_quality" to listOf("sound" to "quality_preference"),
        "tws_in_ear" to listOf("state" to "in_ear"),
        "device_info" to listOf("info" to null),
        "gesture_double" to listOf("action" to "double_tap_left", "action" to "double_tap_right"),
        "gesture_triple" to listOf("action" to "triple_tap_left", "action" to "triple_tap_right"),
        "gesture_long" to listOf("action" to "long_tap"),
        "gesture_swipe" to listOf("action" to "swipe_gesture_options", "action" to "swipe_gesture"),
        "tws_auto_pause" to listOf("config" to "auto_pause"),
        "config_eq" to listOf("sound" to "equalizer_preset_options", "sound" to "equalizer_preset"),
        "voice_language" to listOf("service" to "language_options", "service" to "language"),
        "dual_connect" to listOf("dual_connect" to "devices"),
    )
}
