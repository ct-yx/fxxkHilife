import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:prism_ui/prism_ui.dart';

import '../app_state.dart';
import '../widgets/ui.dart';

class ScanPage extends StatefulWidget {
  const ScanPage({super.key, required this.appState});

  final AppState appState;

  @override
  State<ScanPage> createState() => _ScanPageState();
}

class _ScanPageState extends State<ScanPage> {
  @override
  void initState() {
    super.initState();
    unawaited(widget.appState.run(widget.appState.bridge.invoke('start_scan')));
  }

  @override
  void dispose() {
    unawaited(widget.appState.run(widget.appState.bridge.invoke('stop_scan')));
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.appState,
      builder: (context, _) {
        final snapshot = widget.appState.snapshot;
        final devices = [...snapshot.scanDevices]
          ..sort((a, b) {
            if (a.huaweiOrHonor != b.huaweiOrHonor) {
              return a.huaweiOrHonor ? -1 : 1;
            }
            return b.rssi.compareTo(a.rssi);
          });
        return PrismScaffold(
          bar: AppBar(
            leading: IconButton(
              tooltip: '返回',
              onPressed: () => Navigator.of(context).pop(),
              icon: const GlyphIcon(Glyphs.arrowBack),
            ),
            title: const Text('选择设备'),
            actions: [
              IconButton(
                tooltip: snapshot.scanning ? '停止扫描' : '重新扫描',
                onPressed: snapshot.scanning
                    ? () => widget.appState.run(
                          widget.appState.bridge.invoke('stop_scan'),
                        )
                    : () => widget.appState.run(
                          widget.appState.bridge.invoke('start_scan'),
                        ),
                icon: Text(snapshot.scanning ? '×' : '↻'),
              ),
            ],
          ),
          body: PageScroll(
            children: [
              if (snapshot.connection.isReady &&
                  snapshot.connection.deviceName != null)
                Panel(
                  child: Row(
                    children: [
                      const Text('●'),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          '当前连接：${snapshot.connection.deviceName}',
                          style: AppTextStyle.body.of(context),
                        ),
                      ),
                      StatusBadge('控制通道已连接', positive: true),
                    ],
                  ),
                ),
              SectionHeader(
                '附近设备',
                caption: snapshot.scanning
                    ? '正在读取蓝牙发现结果…'
                    : '点按设备后由原生连接管理器接管连接流程',
              ),
              if (snapshot.scanning)
                Panel(
                  child: Row(
                    children: [
                      SizedBox(
                        width: 140,
                        child: LinearProgressIndicator(),
                      ),
                      const SizedBox(width: 12),
                      const Text('扫描中'),
                    ],
                  ),
                ),
              if (!snapshot.scanning && devices.isEmpty)
                Panel(
                  onPressed: () => widget.appState.run(
                    widget.appState.bridge.invoke('start_scan'),
                  ),
                  child: const ListRow(
                    leading: Text('⌁'),
                    title: Text('没有发现设备'),
                    subtitle: Text('重新扫描或先在系统蓝牙设置中完成配对'),
                    trailing: GlyphIcon(Glyphs.chevronForward, size: 18),
                  ),
                ),
              ...devices.map(
                (device) => Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: ActionRow(
                    title: device.name,
                    subtitle: '${device.address} · RSSI ${device.rssi}',
                    leading: Text(device.huaweiOrHonor ? '◆' : '◇'),
                    trailing: StatusBadge(
                      device.connected
                          ? '系统已连接'
                          : device.bonded
                              ? '已配对'
                              : '附近',
                      positive: device.connected,
                    ),
                    onPressed: () async {
                      await widget.appState.run(
                        widget.appState.bridge.connect(device.address),
                      );
                      if (context.mounted) Navigator.of(context).pop();
                    },
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
