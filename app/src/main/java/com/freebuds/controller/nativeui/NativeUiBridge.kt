package com.freebuds.controller.nativeui

import android.app.Activity
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.embedding.engine.FlutterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Flutter channel adapter. Business state and command mapping live in [NativeUiController]. */
class NativeUiBridge(
    private val activity: Activity,
    engine: FlutterEngine,
) : MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
    private val controller = NativeUiController(activity)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val methodChannel = MethodChannel(engine.dartExecutor.binaryMessenger, METHOD_CHANNEL)
    private val eventChannel = EventChannel(engine.dartExecutor.binaryMessenger, EVENT_CHANNEL)
    private var eventJob: Job? = null
    private var eventSink: EventChannel.EventSink? = null

    init {
        methodChannel.setMethodCallHandler(this)
        eventChannel.setStreamHandler(this)
    }

    val nativeController: NativeUiController get() = controller

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "get_state" -> result.success(controller.state.value.toChannelMap())
                "connect" -> result.success(
                    mapOf("attempt_id" to controller.connect(call.requiredString("address")))
                )
                "disconnect" -> {
                    controller.disconnect()
                    result.success(null)
                }
                "refresh_saved_devices" -> {
                    controller.refreshSavedDevices()
                    result.success(null)
                }
                "start_scan" -> {
                    controller.startScan()
                    result.success(null)
                }
                "stop_scan" -> {
                    controller.stopScan()
                    result.success(null)
                }
                "set_anc_mode" -> {
                    controller.setAncMode(call.requiredString("value"))
                    result.success(null)
                }
                "set_anc_level" -> {
                    controller.setAncLevel(call.requiredString("value"))
                    result.success(null)
                }
                "set_sound_quality" -> {
                    controller.setSoundQuality(call.requiredString("value"))
                    result.success(null)
                }
                "set_equalizer_preset" -> {
                    controller.setEqualizerPreset(call.requiredString("value"))
                    result.success(null)
                }
                "set_auto_pause" -> {
                    controller.setAutoPause(call.requiredBoolean("enabled"))
                    result.success(null)
                }
                "set_low_latency" -> {
                    controller.setLowLatency(call.requiredBoolean("enabled"))
                    result.success(null)
                }
                "set_dual_connect" -> {
                    controller.setDualConnect(call.requiredBoolean("enabled"))
                    result.success(null)
                }
                "set_preferred_device" -> {
                    controller.setPreferredDevice(call.requiredString("address"))
                    result.success(null)
                }
                "set_dual_device_option" -> {
                    controller.setDualDeviceOption(
                        address = call.requiredString("address"),
                        option = call.requiredString("option"),
                        enabled = call.requiredBoolean("enabled"),
                    )
                    result.success(null)
                }
                "set_gesture" -> {
                    controller.setGesture(
                        target = call.requiredString("target"),
                        value = call.requiredString("value"),
                    )
                    result.success(null)
                }
                "set_theme_mode" -> {
                    controller.setThemeMode(call.requiredString("value"))
                    result.success(null)
                }
                "set_locale" -> {
                    controller.setLocale(call.requiredString("value"))
                    result.success(null)
                }
                "set_wallpaper_uri" -> {
                    controller.setWallpaperUri(call.argument<String>("value"))
                    result.success(null)
                }
                "set_wallpaper_scope" -> {
                    controller.setWallpaperScope(call.requiredString("value"))
                    result.success(null)
                }
                "set_auto_low_latency" -> {
                    controller.setAutoLowLatency(call.requiredBoolean("enabled"))
                    result.success(null)
                }
                "set_log_max_lines" -> {
                    controller.setLogMaxLines(call.requiredInt("value"))
                    result.success(null)
                }
                "set_protocol_frame_logging" -> {
                    controller.setProtocolFrameLogging(call.requiredBoolean("enabled"))
                    result.success(null)
                }
                "set_update_preferences" -> {
                    controller.setUpdatePreferences(
                        autoCheck = call.requiredBoolean("auto_check"),
                        autoDownload = call.requiredBoolean("auto_download"),
                    )
                    result.success(null)
                }
                "check_for_update" -> {
                    controller.checkForUpdate(call.argument<Boolean>("force") ?: false)
                    result.success(null)
                }
                "download_update" -> {
                    controller.downloadUpdate()
                    result.success(null)
                }
                "cancel_update_download" -> {
                    controller.cancelUpdateDownload()
                    result.success(null)
                }
                "install_update" -> {
                    controller.installUpdate()?.let(activity::startActivity)
                    result.success(null)
                }
                "get_logs" -> result.success(controller.logs())
                "clear_logs" -> {
                    controller.clearLogs()
                    result.success(null)
                }
                "share_log" -> {
                    controller.shareLog()
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        } catch (error: IllegalArgumentException) {
            result.error("INVALID_ARGUMENT", error.message, null)
        } catch (error: Exception) {
            result.error("NATIVE_ERROR", error.message, null)
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        eventJob?.cancel()
        eventJob = scope.launch {
            controller.state.collect { snapshot ->
                eventSink?.success(snapshot.toChannelMap())
            }
        }
    }

    override fun onCancel(arguments: Any?) {
        eventJob?.cancel()
        eventJob = null
        eventSink = null
    }

    fun close() {
        eventJob?.cancel()
        eventSink = null
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        controller.close()
        scope.cancel()
    }

    private fun MethodCall.requiredString(name: String): String =
        argument<String>(name)?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Missing argument: $name")

    private fun MethodCall.requiredBoolean(name: String): Boolean =
        argument<Boolean>(name) ?: throw IllegalArgumentException("Missing argument: $name")

    private fun MethodCall.requiredInt(name: String): Int =
        (argument<Number>(name) ?: throw IllegalArgumentException("Missing argument: $name"))
            .toInt()

    companion object {
        const val METHOD_CHANNEL = "fxxkhilife/native_ui"
        const val EVENT_CHANNEL = "fxxkhilife/native_ui_state"
    }
}

private fun NativeUiSnapshot.toChannelMap(): Map<String, Any?> = mapOf(
    "connection" to mapOf(
        "system_link" to connection.systemLink,
        "stage" to connection.stage,
        "device_name" to connection.deviceName,
        "address" to connection.address,
        "attempt_id" to connection.attemptId,
        "endpoint" to connection.endpoint,
        "pending_handlers" to connection.pendingHandlers,
        "failed_handlers" to connection.failedHandlers,
        "reason" to connection.reason,
    ),
    "device" to mapOf(
        "battery_global" to device.batteryGlobal,
        "battery_left" to device.batteryLeft,
        "battery_right" to device.batteryRight,
        "battery_case" to device.batteryCase,
        "charging" to device.charging,
        "anc_mode" to device.ancMode,
        "anc_mode_options" to device.ancModeOptions,
        "anc_level" to device.ancLevel,
        "anc_level_options" to device.ancLevelOptions,
        "auto_pause" to device.autoPause,
        "low_latency" to device.lowLatency,
        "sound_quality" to device.soundQuality,
        "sound_quality_options" to device.soundQualityOptions,
        "equalizer_preset" to device.equalizerPreset,
        "equalizer_preset_options" to device.equalizerPresetOptions,
        "dual_connect_enabled" to device.dualConnectEnabled,
        "dual_connect_devices" to device.dualConnectDevices.map { it.toMap() },
        "preferred_device" to device.preferredDevice,
        "double_tap_left" to device.doubleTapLeft,
        "double_tap_right" to device.doubleTapRight,
        "triple_tap_left" to device.tripleTapLeft,
        "triple_tap_right" to device.tripleTapRight,
        "swipe_gesture" to device.swipeGesture,
        "long_tap" to device.longTap,
        "gesture_options" to device.gestureOptions,
        "in_ear" to device.inEar,
        "model" to device.model,
        "firmware_version" to device.firmwareVersion,
        "voice_language" to device.voiceLanguage,
        "voice_language_options" to device.voiceLanguageOptions,
        "pending_init_handlers" to device.pendingInitHandlers,
        "capabilities" to device.capabilities,
    ),
    "scan" to mapOf(
        "is_scanning" to scan.isScanning,
        "devices" to scan.devices.map { it.toMap() },
    ),
    "saved_devices" to savedDevices.map { it.toMap() },
    "listening" to mapOf(
        "total_ms" to listening.totalMs,
        "today_ms" to listening.todayMs,
        "active_days" to listening.activeDays,
        "streak_days" to listening.streakDays,
    ),
    "settings" to mapOf(
        "theme_mode" to settings.themeMode,
        "locale" to settings.locale,
        "wallpaper_uri" to settings.wallpaperUri,
        "wallpaper_scope" to settings.wallpaperScope,
        "auto_low_latency" to settings.autoLowLatency,
        "log_max_lines" to settings.logMaxLines,
        "protocol_frame_logging" to settings.protocolFrameLogging,
        "auto_check_updates" to settings.autoCheckUpdates,
        "auto_download_updates" to settings.autoDownloadUpdates,
    ),
    "update" to mapOf(
        "kind" to update.kind,
        "version_name" to update.versionName,
        "version_code" to update.versionCode,
        "checked_at_ms" to update.checkedAtMs,
        "downloaded_bytes" to update.downloadedBytes,
        "total_bytes" to update.totalBytes,
        "reason" to update.reason,
        "message" to update.message,
        "retryable" to update.retryable,
    ),
)

private fun NativeDualDeviceSnapshot.toMap(): Map<String, Any?> = mapOf(
    "address" to address,
    "name" to name,
    "auto_connect" to autoConnect,
    "preferred" to preferred,
    "connected" to connected,
    "playing" to playing,
)

private fun NativeScanDeviceSnapshot.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "address" to address,
    "rssi" to rssi,
    "bonded" to bonded,
    "huawei_or_honor" to huaweiOrHonor,
    "connected" to connected,
)

private fun NativeSavedDeviceSnapshot.toMap(): Map<String, Any?> = mapOf(
    "address" to address,
    "name" to name,
    "bonded" to bonded,
    "system_connected" to systemConnected,
    "control_connected" to controlConnected,
)
