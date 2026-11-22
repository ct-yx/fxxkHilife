import 'dart:async';

import 'native_bridge.dart';

Map<String, dynamic> asMap(Object? value) {
  if (value is! Map) return <String, dynamic>{};
  return value.map((key, value) => MapEntry(key.toString(), value));
}

List<String> stringList(Object? value) => value is List
    ? value.whereType<Object>().map((item) => item.toString()).toList()
    : <String>[];

int? nullableInt(Object? value) => value is num ? value.toInt() : null;

bool? nullableBool(Object? value) => value is bool ? value : null;

class ConnectionSnapshot {
  const ConnectionSnapshot({
    required this.systemLink,
    required this.stage,
    this.deviceName,
    this.address,
    this.attemptId,
    this.endpoint,
    this.pendingHandlers = const <String>[],
    this.failedHandlers = const <String>[],
    this.reason,
  });

  final String systemLink;
  final String stage;
  final String? deviceName;
  final String? address;
  final String? attemptId;
  final String? endpoint;
  final List<String> pendingHandlers;
  final List<String> failedHandlers;
  final String? reason;

  bool get isReady => stage == 'ready' || stage == 'degraded';
  bool get isBusy => stage.contains('connecting') ||
      stage == 'settling' ||
      stage.contains('initializing') ||
      stage == 'disconnecting';
  bool get isFailed => stage == 'failed';

  String get label {
    switch (stage) {
      case 'ready':
        return '已连接';
      case 'degraded':
        return '部分功能未同步';
      case 'failed':
        return '连接失败';
      case 'idle':
        return '未连接';
      default:
        return '连接处理中';
    }
  }

  factory ConnectionSnapshot.fromMap(Object? value) {
    final map = asMap(value);
    return ConnectionSnapshot(
      systemLink: map['system_link'] as String? ?? 'disconnected',
      stage: map['stage'] as String? ?? 'idle',
      deviceName: map['device_name'] as String?,
      address: map['address'] as String?,
      attemptId: map['attempt_id'] as String?,
      endpoint: map['endpoint'] as String?,
      pendingHandlers: stringList(map['pending_handlers']),
      failedHandlers: stringList(map['failed_handlers']),
      reason: map['reason'] as String?,
    );
  }
}

class DualDeviceSnapshot {
  const DualDeviceSnapshot({
    required this.address,
    required this.name,
    this.autoConnect,
    required this.preferred,
    required this.connected,
    required this.playing,
  });

  final String address;
  final String name;
  final bool? autoConnect;
  final bool preferred;
  final bool connected;
  final bool playing;

  factory DualDeviceSnapshot.fromMap(Object? value) {
    final map = asMap(value);
    return DualDeviceSnapshot(
      address: map['address'] as String? ?? '',
      name: map['name'] as String? ?? '未知设备',
      autoConnect: nullableBool(map['auto_connect']),
      preferred: map['preferred'] as bool? ?? false,
      connected: map['connected'] as bool? ?? false,
      playing: map['playing'] as bool? ?? false,
    );
  }
}

class DeviceSnapshot {
  const DeviceSnapshot({
    this.batteryGlobal,
    this.batteryLeft,
    this.batteryRight,
    this.batteryCase,
    this.charging,
    this.ancMode,
    this.ancModeOptions = const <String>[],
    this.ancLevel,
    this.ancLevelOptions = const <String>[],
    this.autoPause,
    this.lowLatency,
    this.soundQuality,
    this.soundQualityOptions = const <String>[],
    this.equalizerPreset,
    this.equalizerPresetOptions = const <String>[],
    this.dualConnectEnabled,
    this.dualConnectDevices = const <DualDeviceSnapshot>[],
    this.preferredDevice,
    this.doubleTapLeft,
    this.doubleTapRight,
    this.tripleTapLeft,
    this.tripleTapRight,
    this.swipeGesture,
    this.longTap,
    this.gestureOptions = const <String>[],
    this.inEar,
    this.model,
    this.firmwareVersion,
    this.voiceLanguage,
    this.voiceLanguageOptions = const <String>[],
    this.pendingInitHandlers = const <String>[],
    this.capabilities = const <String>[],
  });

  final int? batteryGlobal;
  final int? batteryLeft;
  final int? batteryRight;
  final int? batteryCase;
  final bool? charging;
  final String? ancMode;
  final List<String> ancModeOptions;
  final String? ancLevel;
  final List<String> ancLevelOptions;
  final bool? autoPause;
  final bool? lowLatency;
  final String? soundQuality;
  final List<String> soundQualityOptions;
  final String? equalizerPreset;
  final List<String> equalizerPresetOptions;
  final bool? dualConnectEnabled;
  final List<DualDeviceSnapshot> dualConnectDevices;
  final String? preferredDevice;
  final String? doubleTapLeft;
  final String? doubleTapRight;
  final String? tripleTapLeft;
  final String? tripleTapRight;
  final String? swipeGesture;
  final String? longTap;
  final List<String> gestureOptions;
  final bool? inEar;
  final String? model;
  final String? firmwareVersion;
  final String? voiceLanguage;
  final List<String> voiceLanguageOptions;
  final List<String> pendingInitHandlers;
  final List<String> capabilities;

  bool supports(String capability) => capabilities.contains(capability);

  factory DeviceSnapshot.fromMap(Object? value) {
    final map = asMap(value);
    final dualDevices = map['dual_connect_devices'] is List
        ? (map['dual_connect_devices'] as List)
            .map(DualDeviceSnapshot.fromMap)
            .toList()
        : <DualDeviceSnapshot>[];
    return DeviceSnapshot(
      batteryGlobal: nullableInt(map['battery_global']),
      batteryLeft: nullableInt(map['battery_left']),
      batteryRight: nullableInt(map['battery_right']),
      batteryCase: nullableInt(map['battery_case']),
      charging: nullableBool(map['charging']),
      ancMode: map['anc_mode'] as String?,
      ancModeOptions: stringList(map['anc_mode_options']),
      ancLevel: map['anc_level'] as String?,
      ancLevelOptions: stringList(map['anc_level_options']),
      autoPause: nullableBool(map['auto_pause']),
      lowLatency: nullableBool(map['low_latency']),
      soundQuality: map['sound_quality'] as String?,
      soundQualityOptions: stringList(map['sound_quality_options']),
      equalizerPreset: map['equalizer_preset'] as String?,
      equalizerPresetOptions: stringList(map['equalizer_preset_options']),
      dualConnectEnabled: nullableBool(map['dual_connect_enabled']),
      dualConnectDevices: dualDevices,
      preferredDevice: map['preferred_device'] as String?,
      doubleTapLeft: map['double_tap_left'] as String?,
      doubleTapRight: map['double_tap_right'] as String?,
      tripleTapLeft: map['triple_tap_left'] as String?,
      tripleTapRight: map['triple_tap_right'] as String?,
      swipeGesture: map['swipe_gesture'] as String?,
      longTap: map['long_tap'] as String?,
      gestureOptions: stringList(map['gesture_options']),
      inEar: nullableBool(map['in_ear']),
      model: map['model'] as String?,
      firmwareVersion: map['firmware_version'] as String?,
      voiceLanguage: map['voice_language'] as String?,
      voiceLanguageOptions: stringList(map['voice_language_options']),
      pendingInitHandlers: stringList(map['pending_init_handlers']),
      capabilities: stringList(map['capabilities']),
    );
  }
}

class ScanDeviceSnapshot {
  const ScanDeviceSnapshot({
    required this.name,
    required this.address,
    required this.rssi,
    required this.bonded,
    required this.huaweiOrHonor,
    required this.connected,
  });

  final String name;
  final String address;
  final int rssi;
  final bool bonded;
  final bool huaweiOrHonor;
  final bool connected;

  factory ScanDeviceSnapshot.fromMap(Object? value) {
    final map = asMap(value);
    return ScanDeviceSnapshot(
      name: map['name'] as String? ?? '未知设备',
      address: map['address'] as String? ?? '',
      rssi: nullableInt(map['rssi']) ?? 0,
      bonded: map['bonded'] as bool? ?? false,
      huaweiOrHonor: map['huawei_or_honor'] as bool? ?? false,
      connected: map['connected'] as bool? ?? false,
    );
  }
}

class SavedDeviceSnapshot {
  const SavedDeviceSnapshot({
    required this.address,
    required this.name,
    required this.bonded,
    required this.systemConnected,
    required this.controlConnected,
  });

  final String address;
  final String name;
  final bool bonded;
  final bool systemConnected;
  final bool controlConnected;

  factory SavedDeviceSnapshot.fromMap(Object? value) {
    final map = asMap(value);
    return SavedDeviceSnapshot(
      address: map['address'] as String? ?? '',
      name: map['name'] as String? ?? '未知设备',
      bonded: map['bonded'] as bool? ?? false,
      systemConnected: map['system_connected'] as bool? ?? false,
      controlConnected: map['control_connected'] as bool? ?? false,
    );
  }
}

class ListeningSnapshot {
  const ListeningSnapshot({
    this.totalMs = 0,
    this.todayMs = 0,
    this.activeDays = 0,
    this.streakDays = 0,
  });

  final int totalMs;
  final int todayMs;
  final int activeDays;
  final int streakDays;

  factory ListeningSnapshot.fromMap(Object? value) {
    final map = asMap(value);
    return ListeningSnapshot(
      totalMs: nullableInt(map['total_ms']) ?? 0,
      todayMs: nullableInt(map['today_ms']) ?? 0,
      activeDays: nullableInt(map['active_days']) ?? 0,
      streakDays: nullableInt(map['streak_days']) ?? 0,
    );
  }
}

class SettingsSnapshot {
  const SettingsSnapshot({
    this.themeMode = 'system',
    this.locale = 'zh-CN',
    this.wallpaperUri,
    this.wallpaperScope = 'all',
    this.autoLowLatency = true,
    this.logMaxLines = 2000,
    this.protocolFrameLogging = false,
    this.autoCheckUpdates = true,
    this.autoDownloadUpdates = false,
  });

  final String themeMode;
  final String locale;
  final String? wallpaperUri;
  final String wallpaperScope;
  final bool autoLowLatency;
  final int logMaxLines;
  final bool protocolFrameLogging;
  final bool autoCheckUpdates;
  final bool autoDownloadUpdates;

  factory SettingsSnapshot.fromMap(Object? value) {
    final map = asMap(value);
    return SettingsSnapshot(
      themeMode: map['theme_mode'] as String? ?? 'system',
      locale: map['locale'] as String? ?? 'zh-CN',
      wallpaperUri: map['wallpaper_uri'] as String?,
      wallpaperScope: map['wallpaper_scope'] as String? ?? 'all',
      autoLowLatency: map['auto_low_latency'] as bool? ?? true,
      logMaxLines: nullableInt(map['log_max_lines']) ?? 2000,
      protocolFrameLogging: map['protocol_frame_logging'] as bool? ?? false,
      autoCheckUpdates: map['auto_check_updates'] as bool? ?? true,
      autoDownloadUpdates: map['auto_download_updates'] as bool? ?? false,
    );
  }
}

class UpdateSnapshot {
  const UpdateSnapshot({
    this.kind = 'idle',
    this.versionName,
    this.versionCode,
    this.checkedAtMs,
    this.downloadedBytes,
    this.totalBytes,
    this.reason,
    this.message,
    this.retryable,
  });

  final String kind;
  final String? versionName;
  final int? versionCode;
  final int? checkedAtMs;
  final int? downloadedBytes;
  final int? totalBytes;
  final String? reason;
  final String? message;
  final bool? retryable;

  double? get progress => totalBytes == null || totalBytes == 0
      ? null
      : (downloadedBytes ?? 0) / totalBytes!;

  factory UpdateSnapshot.fromMap(Object? value) {
    final map = asMap(value);
    return UpdateSnapshot(
      kind: map['kind'] as String? ?? 'idle',
      versionName: map['version_name'] as String?,
      versionCode: nullableInt(map['version_code']),
      checkedAtMs: nullableInt(map['checked_at_ms']),
      downloadedBytes: nullableInt(map['downloaded_bytes']),
      totalBytes: nullableInt(map['total_bytes']),
      reason: map['reason'] as String?,
      message: map['message'] as String?,
      retryable: nullableBool(map['retryable']),
    );
  }
}

class NativeSnapshot {
  const NativeSnapshot({
    required this.connection,
    required this.device,
    required this.scanning,
    required this.scanDevices,
    required this.savedDevices,
    required this.listening,
    required this.settings,
    required this.update,
  });

  final ConnectionSnapshot connection;
  final DeviceSnapshot device;
  final bool scanning;
  final List<ScanDeviceSnapshot> scanDevices;
  final List<SavedDeviceSnapshot> savedDevices;
  final ListeningSnapshot listening;
  final SettingsSnapshot settings;
  final UpdateSnapshot update;

  factory NativeSnapshot.fromMap(Object? value) {
    final map = asMap(value);
    final scan = asMap(map['scan']);
    final scanDevices = scan['devices'] is List
        ? (scan['devices'] as List).map(ScanDeviceSnapshot.fromMap).toList()
        : <ScanDeviceSnapshot>[];
    final saved = map['saved_devices'] is List
        ? (map['saved_devices'] as List)
            .map(SavedDeviceSnapshot.fromMap)
            .toList()
        : <SavedDeviceSnapshot>[];
    return NativeSnapshot(
      connection: ConnectionSnapshot.fromMap(map['connection']),
      device: DeviceSnapshot.fromMap(map['device']),
      scanning: scan['is_scanning'] as bool? ?? false,
      scanDevices: scanDevices,
      savedDevices: saved,
      listening: ListeningSnapshot.fromMap(map['listening']),
      settings: SettingsSnapshot.fromMap(map['settings']),
      update: UpdateSnapshot.fromMap(map['update']),
    );
  }

  factory NativeSnapshot.empty() => NativeSnapshot.fromMap(const <String, dynamic>{});
}

class AppState extends ChangeNotifier {
  AppState(this.bridge);

  final NativeBridge bridge;
  NativeSnapshot snapshot = NativeSnapshot.empty();
  Object? lastError;
  StreamSubscription<NativeSnapshot>? _subscription;

  Future<void> initialize() async {
    _subscription = bridge.snapshots.listen(
      (next) {
        snapshot = next;
        lastError = null;
        notifyListeners();
      },
      onError: (Object error) {
        lastError = error;
        notifyListeners();
      },
    );
    try {
      snapshot = await bridge.getState();
      notifyListeners();
    } catch (error) {
      lastError = error;
      notifyListeners();
    }
  }

  Future<void> run(Future<void> action) async {
    try {
      await action;
      lastError = null;
    } catch (error) {
      lastError = error;
    }
    notifyListeners();
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }
}
