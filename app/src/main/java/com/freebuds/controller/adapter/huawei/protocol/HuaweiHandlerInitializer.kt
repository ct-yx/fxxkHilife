package com.freebuds.controller.adapter.huawei.protocol

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

    private suspend fun initializeOpenFreebudsStyle(driver: SppDriver, deviceLabel: String) {
        val orderedHandlers = coreFirstHandlers()
        LogBuffer.i(
            "SPP",
            "OpenFreebuds-style init for $deviceLabel: handlers=${orderedHandlers.map { it.id }}, per-handler timeouts/attempts follow OpenFreebuds defaults unless overridden"
        )

        for (handler in orderedHandlers) {
            val success = initializeHandler(driver, handler)
            if (!success) {
                LogBuffer.w("SPP", "Can't initialize ${handler.id}. Skipping after ${handler.initAttemptMax} attempts.")
                registry.failedHandlerIds.add(handler.id)
            }
            delay(80)
        }

        LogBuffer.i(
            "SPP",
            if (registry.failedHandlerIds.isEmpty()) "OpenFreebuds-style init completed: all handlers ready"
            else "OpenFreebuds-style init completed with degraded handlers=${registry.failedHandlerIds}"
        )
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

    private suspend fun initializeHandler(driver: SppDriver, handler: HuaweiDeviceHandler): Boolean {
        for (attempt in 0 until handler.initAttemptMax) {
            try {
                LogBuffer.d("SPP", "Init ${handler.id}, attempt=$attempt")
                withTimeout(handler.initTimeoutMs) {
                    handler.onInit(driver)
                }
                if (hasExpectedInitState(driver, handler.id)) {
                    LogBuffer.i("SPP", "Init ${handler.id} success (attempt=${attempt + 1})")
                    return true
                }
                LogBuffer.w("SPP", "Init ${handler.id} returned without expected state (attempt=${attempt + 1})")
            } catch (e: TimeoutCancellationException) {
                if (hasExpectedInitState(driver, handler.id)) {
                    LogBuffer.i("SPP", "Init ${handler.id} accepted after timeout because expected state is present (attempt=${attempt + 1})")
                    return true
                }
                LogBuffer.w("SPP", "Init ${handler.id} timeout (attempt=${attempt + 1}, timeout=${handler.initTimeoutMs}ms)")
            } catch (e: Exception) {
                if (hasExpectedInitState(driver, handler.id)) {
                    LogBuffer.i("SPP", "Init ${handler.id} accepted after error because expected state is present (attempt=${attempt + 1})")
                    return true
                }
                LogBuffer.w("SPP", "Init ${handler.id} failed (attempt=${attempt + 1}): ${e.message}")
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
