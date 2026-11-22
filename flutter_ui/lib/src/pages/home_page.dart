import 'package:flutter/widgets.dart';
import 'package:prism_ui/prism_ui.dart';

import '../app_state.dart';
import '../widgets/ui.dart';

class HomePage extends StatelessWidget {
  const HomePage({
    super.key,
    required this.appState,
    required this.onOpenScan,
    required this.onOpenDevice,
  });

  final AppState appState;
  final VoidCallback onOpenScan;
  final VoidCallback onOpenDevice;

  @override
  Widget build(BuildContext context) {
    final snapshot = appState.snapshot;
    final connection = snapshot.connection;
    final device = snapshot.device;
    final hasDevice = connection.deviceName != null || device.model != null;

    return PageScroll(
      children: [
        Panel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          connection.deviceName ?? '还没有连接耳机',
                          style: AppTextStyle.title2.of(context, emphasized: true),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          hasDevice
                              ? '${connection.label} · ${connection.systemLink == 'connected' ? '系统蓝牙已连接' : '等待系统蓝牙'}'
                              : '连接华为或荣耀耳机后管理 ANC、音频和手势',
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
              if (connection.isFailed && connection.reason != null) ...[
                const SizedBox(height: 12),
                Text(
                  connection.reason!,
                  style: AppTextStyle.footnote.of(context).copyWith(
                        color: context.colorScheme.error,
                      ),
                ),
              ],
              const SizedBox(height: 18),
              Row(
                children: [
                  Expanded(
                    child: FilledButton(
                      onPressed: connection.isBusy
                          ? null
                          : connection.isReady
                              ? onOpenDevice
                              : onOpenScan,
                      child: Text(connection.isReady ? '打开设备' : '扫描并连接'),
                    ),
                  ),
                  if (connection.isReady) ...[
                    const SizedBox(width: 10),
                    IconButton.filled(
                      tooltip: '断开连接',
                      onPressed: () => appState.run(appState.bridge.invoke('disconnect')),
                      icon: const Text('×'),
                    ),
                  ],
                ],
              ),
            ],
          ),
        ),
        SectionHeader('已保存设备', caption: '连接成功后会自动记录设备地址'),
        if (snapshot.savedDevices.isEmpty)
          Panel(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('还没有保存的设备', style: AppTextStyle.body.of(context)),
                const SizedBox(height: 6),
                Text(
                  '扫描附近设备并完成一次连接，之后可以从这里快速重连。',
                  style: AppTextStyle.subheadline.of(context).copyWith(
                        color: context.colorScheme.onSurfaceVariant,
                      ),
                ),
              ],
            ),
          )
        else
          ...snapshot.savedDevices.map(
            (saved) => Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: ActionRow(
                title: saved.name,
                subtitle: saved.controlConnected
                    ? '控制通道已连接'
                    : saved.systemConnected
                        ? '系统蓝牙已连接'
                        : saved.address,
                leading: Text(saved.controlConnected ? '●' : '○'),
                trailing: StatusBadge(
                  saved.controlConnected
                      ? '已连接'
                      : saved.systemConnected
                          ? '可连接'
                          : '已保存',
                  positive: saved.controlConnected,
                ),
                onPressed: () => appState.run(appState.bridge.connect(saved.address)),
              ),
            ),
          ),
        SectionHeader('快速入口'),
        Panel(
          onPressed: onOpenScan,
          child: ListRow(
            leading: const Text('⌁'),
            title: const Text('扫描附近设备'),
            subtitle: const Text('读取系统已配对设备并发现新耳机'),
            trailing: const GlyphIcon(Glyphs.chevronForward, size: 18),
          ),
        ),
      ],
    );
  }
}
