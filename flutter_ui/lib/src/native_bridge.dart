import 'dart:async';

import 'package:flutter/services.dart';

import 'app_state.dart';

/// Stable channel boundary for the native Bluetooth runtime.
class NativeBridge {
  NativeBridge()
      : _methods = const MethodChannel('fxxkhilife/native_ui'),
        _events = const EventChannel('fxxkhilife/native_ui_state');

  final MethodChannel _methods;
  final EventChannel _events;

  Stream<NativeSnapshot> get snapshots => _events
      .receiveBroadcastStream()
      .map((value) => NativeSnapshot.fromMap(value));

  Future<NativeSnapshot> getState() async {
    final value = await _methods.invokeMethod<dynamic>('get_state');
    return NativeSnapshot.fromMap(value);
  }

  Future<void> _invoke(String method, [Map<String, dynamic>? arguments]) async {
    await _methods.invokeMethod<void>(method, arguments);
  }

  Future<void> disconnect() => _invoke('disconnect');

  Future<void> refreshSavedDevices() => _invoke('refresh_saved_devices');

  Future<void> startScan() => _invoke('start_scan');

  Future<void> stopScan() => _invoke('stop_scan');

  Future<String?> connect(String address) async {
    final value = await _methods.invokeMethod<dynamic>(
      'connect',
      <String, dynamic>{'address': address},
    );
    final map = _asMap(value);
    return map['attempt_id'] as String?;
  }

  Future<void> setAncMode(String value) => _invoke('set_anc_mode', {'value': value});

  Future<void> setAncLevel(String value) => _invoke('set_anc_level', {'value': value});

  Future<void> setSoundQuality(String value) =>
      _invoke('set_sound_quality', {'value': value});

  Future<void> setEqualizerPreset(String value) =>
      _invoke('set_equalizer_preset', {'value': value});

  Future<void> setAutoPause(bool enabled) =>
      _invoke('set_auto_pause', {'enabled': enabled});

  Future<void> setLowLatency(bool enabled) =>
      _invoke('set_low_latency', {'enabled': enabled});

  Future<void> setDualConnect(bool enabled) =>
      _invoke('set_dual_connect', {'enabled': enabled});

  Future<void> setPreferredDevice(String address) =>
      _invoke('set_preferred_device', {'address': address});

  Future<void> setDualDeviceOption(
    String address,
    String option,
    bool enabled,
  ) => _invoke('set_dual_device_option', <String, dynamic>{
        'address': address,
        'option': option,
        'enabled': enabled,
      });

  Future<void> setGesture(String target, String value) =>
      _invoke('set_gesture', {'target': target, 'value': value});

  Future<void> setThemeMode(String value) =>
      _invoke('set_theme_mode', {'value': value});

  Future<void> setLocale(String value) => _invoke('set_locale', {'value': value});

  Future<void> setWallpaperUri(String? value) =>
      _invoke('set_wallpaper_uri', {'value': value});

  Future<void> setWallpaperScope(String value) =>
      _invoke('set_wallpaper_scope', {'value': value});

  Future<void> setAutoLowLatency(bool enabled) =>
      _invoke('set_auto_low_latency', {'enabled': enabled});

  Future<void> setLogMaxLines(int value) =>
      _invoke('set_log_max_lines', {'value': value});

  Future<void> setProtocolFrameLogging(bool enabled) =>
      _invoke('set_protocol_frame_logging', {'enabled': enabled});

  Future<void> setUpdatePreferences(bool autoCheck, bool autoDownload) =>
      _invoke('set_update_preferences', <String, dynamic>{
        'auto_check': autoCheck,
        'auto_download': autoDownload,
      });

  Future<void> checkForUpdate({bool force = false}) =>
      _invoke('check_for_update', {'force': force});

  Future<void> downloadUpdate() => _invoke('download_update');

  Future<void> cancelUpdateDownload() => _invoke('cancel_update_download');

  Future<void> installUpdate() => _invoke('install_update');

  Future<String> getLogs() async =>
      await _methods.invokeMethod<String>('get_logs') ?? '';

  Future<void> clearLogs() => _invoke('clear_logs');

  Future<void> shareLog() => _invoke('share_log');

  static Map<String, dynamic> _asMap(Object? value) {
    if (value is! Map) return <String, dynamic>{};
    return value.map((key, value) => MapEntry(key.toString(), value));
  }
}
