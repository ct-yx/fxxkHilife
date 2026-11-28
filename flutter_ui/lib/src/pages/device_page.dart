import 'package:flutter/widgets.dart';
import 'package:prism_ui/prism_ui.dart';

import '../app_state.dart';
import '../widgets/ui.dart';
import 'gesture_page.dart';

class DevicePage extends StatelessWidget {
  const DevicePage({super.key, required this.appState});

  final AppState appState;

  void _run(Future<void> action) => appState.run(action);

  @override
  Widget build(BuildContext context) {
    final snapshot = appState.snapshot;
    final connection = snapshot.connection;
    final device = snapshot.device;
    final available = connection.isReady || connection.deviceName != null;
    if (!available) {
      return PageScroll(
        children: [
          const SizedBox(height: 36),
          Panel(
            child: Column(
              children: [
                Text('连接后管理耳机', style: AppTextStyle.title2.of(context, emphasized: true)),
                const SizedBox(height: 8),
                Text(
                  '这里会显示电量、降噪、音频、双设备和手势控制。',
                  textAlign: TextAlign.center,
                  style: AppTextStyle.subheadline.of(context).copyWith(
                        color: context.colorScheme.onSurfaceVariant,
                      ),
                ),
              ],
            ),
          ),
        ],
      );
    }

    final ancModes = device.ancModeOptions.isEmpty
        ? <String>['normal', 'cancellation', 'awareness']
        : device.ancModeOptions;
    final ancMode = ancModes.contains(device.ancMode) ? device.ancMode! : ancModes.first;
    final soundOptions = device.soundQualityOptions;

    return PageScroll(
      children: [
        Panel(
          child: Row(
            children: [
              EarbudIcon(size: 30, color: context.colorScheme.primary),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      connection.deviceName ?? device.model ?? '已连接耳机',
                      style: AppTextStyle.title2.of(context, emphasized: true),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      connection.isReady ? '控制通道 ${connection.stage}' : connection.label,
                      style: AppTextStyle.subheadline.of(context).copyWith(
                            color: context.colorScheme.onSurfaceVariant,
                          ),
                    ),
                  ],
                ),
              ),
              StatusBadge(connection.label, positive: connection.isReady),
            ],
          ),
        ),
        SectionHeader(
          '电量',
          leading: EarbudIcon(size: 26, color: context.colorScheme.primary),
        ),
        Panel(
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              _BatteryValue(label: '左', value: device.batteryLeft),
              _BatteryValue(label: '右', value: device.batteryRight),
              _BatteryValue(label: '盒', value: device.batteryCase),
              _BatteryValue(label: '总览', value: device.batteryGlobal),
            ],
          ),
        ),
        SectionHeader('降噪与通透'),
        Panel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SegmentedControl<String>(
                segments: ancModes.map((value) => (value, readableMode(value))).toList(),
                value: ancMode,
                onChanged: (value) => _run(appState.bridge.setAncMode(value)),
              ),
              if (device.ancLevelOptions.isNotEmpty) ...[
                const SizedBox(height: 16),
                Text('强度', style: AppTextStyle.subheadline.of(context, emphasized: true)),
                const SizedBox(height: 8),
                SegmentedControl<String>(
                  segments: device.ancLevelOptions
                      .map((value) => (value, value))
                      .toList(),
                  value: device.ancLevelOptions.contains(device.ancLevel)
                      ? device.ancLevel!
                      : device.ancLevelOptions.first,
                  onChanged: (value) => _run(appState.bridge.setAncLevel(value)),
                ),
              ],
            ],
          ),
        ),
        SectionHeader('音频'),
        if (soundOptions.isNotEmpty)
          Panel(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('音质偏好', style: AppTextStyle.subheadline.of(context, emphasized: true)),
                const SizedBox(height: 8),
                SegmentedControl<String>(
                  segments: soundOptions.map((value) => (value, value)).toList(),
                  value: soundOptions.contains(device.soundQuality)
                      ? device.soundQuality!
                      : soundOptions.first,
                  onChanged: (value) => _run(appState.bridge.setSoundQuality(value)),
                ),
              ],
            ),
          ),
        const SizedBox(height: 8),
        ActionRow(
          title: '摘下自动暂停',
          subtitle: '检测到离耳后暂停播放',
          trailing: Switch(
            value: device.autoPause ?? false,
            onChanged: device.autoPause == null
                ? null
                : (value) => _run(appState.bridge.setAutoPause(value)),
            semanticLabel: '摘下自动暂停',
          ),
        ),
        const SizedBox(height: 8),
        ActionRow(
          title: '低延迟模式',
          subtitle: '减少游戏和视频中的音画延迟',
          trailing: Switch(
            value: device.lowLatency ?? false,
            onChanged: device.lowLatency == null
                ? null
                : (value) => _run(appState.bridge.setLowLatency(value)),
            semanticLabel: '低延迟模式',
          ),
        ),
        SectionHeader('双设备连接'),
        ActionRow(
          title: '启用双设备连接',
          subtitle: '在两个已配对设备之间保持连接',
          trailing: Switch(
            value: device.dualConnectEnabled ?? false,
            onChanged: device.dualConnectEnabled == null
                ? null
                : (value) => _run(appState.bridge.setDualConnect(value)),
            semanticLabel: '启用双设备连接',
          ),
        ),
        ...device.dualConnectDevices.map(
          (dual) => Padding(
            padding: const EdgeInsets.only(top: 8),
            child: ActionRow(
              title: dual.name,
              subtitle: dual.playing ? '播放中' : dual.address,
              leading: Text(dual.preferred ? '★' : '☆'),
              trailing: Switch(
                value: dual.autoConnect ?? false,
                onChanged: (value) => _run(
                  appState.bridge.setDualDeviceOption(dual.address, 'auto_connect', value),
                ),
                semanticLabel: '自动连接 ${dual.name}',
              ),
            ),
          ),
        ),
        SectionHeader('手势与设备信息'),
        Panel(
          onPressed: () => Navigator.of(context).push(
            CardPageRoute<void>(builder: (_) => GesturePage(appState: appState)),
          ),
          child: const ListRow(
            leading: Text('✦'),
            title: Text('手势设置'),
            subtitle: Text('双击、三击、滑动和长按'),
            trailing: GlyphIcon(Glyphs.chevronForward, size: 18),
          ),
        ),
        const SizedBox(height: 8),
        Panel(
          child: Column(
            children: [
              InfoRow(label: '型号', value: device.model),
              InfoRow(label: '固件版本', value: device.firmwareVersion),
              InfoRow(label: '佩戴状态', value: device.inEar == null ? null : (device.inEar! ? '已佩戴' : '未佩戴')),
            ],
          ),
        ),
      ],
    );
  }
}

class _BatteryValue extends StatelessWidget {
  const _BatteryValue({required this.label, required this.value});

  final String label;
  final int? value;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(percent(value), style: AppTextStyle.title3.of(context, emphasized: true)),
        const SizedBox(height: 4),
        Text(
          label,
          style: AppTextStyle.caption1.of(context).copyWith(
                color: context.colorScheme.onSurfaceVariant,
              ),
        ),
      ],
    );
  }
}

class InfoRow extends StatelessWidget {
  const InfoRow({super.key, required this.label, required this.value});

  final String label;
  final String? value;

  @override
  Widget build(BuildContext context) {
    return ListRow(title: Text(label), trailing: valueText(context, value));
  }
}
