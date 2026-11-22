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

  Future<void> invoke(String method, [Map<String, dynamic>? arguments]) async {
    await _methods.invokeMethod<void>(method, arguments);
  }

  Future<String?> connect(String address) async {
    final value = await _methods.invokeMethod<dynamic>(
      'connect',
      <String, dynamic>{'address': address},
    );
    final map = _asMap(value);
    return map['attempt_id'] as String?;
  }

  Future<void> setAncMode(String value) => invoke('set_anc_mode', {'value': value});

  Future<void> setAncLevel(String value) => invoke('set_anc_level', {'value': value});

  Future<void> setSoundQuality(String value) =>
      invoke('set_sound_quality', {'value': value});

  Future<void> setEqualizerPreset(String value) =>
      invoke('set_equalizer_preset', {'value': value});

  Future<void> setAutoPause(bool enabled) =>
      invoke('set_auto_pause', {'enabled': enabled});

  Future<void> setLowLatency(bool enabled) =>
      invoke('set_low_latency', {'enabled': enabled});

  Future<void> setDualConnect(bool enabled) =>
      invoke('set_dual_connect', {'enabled': enabled});

  Future<void> setPreferredDevice(String address) =>
      invoke('set_preferred_device', {'address': address});

  Future<void> setDualDeviceOption(
    String address,
    String option,
    bool enabled,
  ) => invoke('set_dual_device_option', <String, dynamic>{
        'address': address,
        'option': option,
        'enabled': enabled,
      });

  Future<void> setGesture(String target, String value) =>
      invoke('set_gesture', {'target': target, 'value': value});

  Future<void> setThemeMode(String value) =>
      invoke('set_theme_mode', {'value': value});

  Future<void> setLocale(String value) => invoke('set_locale', {'value': value});

  Future<void> setWallpaperUri(String? value) =>
      invoke('set_wallpaper_uri', {'value': value});

  Future<void> setWallpaperScope(String value) =>
      invoke('set_wallpaper_scope', {'value': value});

  Future<void> setAutoLowLatency(bool enabled) =>
      invoke('set_auto_low_latency', {'enabled': enabled});

  Future<void> setLogMaxLines(int value) =>
      invoke('set_log_max_lines', {'value': value});

  Future<void> setProtocolFrameLogging(bool enabled) =>
      invoke('set_protocol_frame_logging', {'enabled': enabled});

  Future<void> setUpdatePreferences(bool autoCheck, bool autoDownload) =>
      invoke('set_update_preferences', <String, dynamic>{
        'auto_check': autoCheck,
        'auto_download': autoDownload,
      });

  Future<void> checkForUpdate({bool force = false}) =>
      invoke('check_for_update', {'force': force});

  Future<void> downloadUpdate() => invoke('download_update');

  Future<void> cancelUpdateDownload() => invoke('cancel_update_download');

  Future<void> installUpdate() => invoke('install_update');

  Future<String> getLogs() async =>
      await _methods.invokeMethod<String>('get_logs') ?? '';

  Future<void> clearLogs() => invoke('clear_logs');

  Future<void> shareLog() => invoke('share_log');

  static Map<String, dynamic> _asMap(Object? value) {
    if (value is! Map) return <String, dynamic>{};
    return value.map((key, value) => MapEntry(key.toString(), value));
  }
}
