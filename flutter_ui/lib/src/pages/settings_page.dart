import 'package:flutter/widgets.dart';
import 'package:prism_ui/prism_ui.dart';

import '../app_state.dart';
import '../widgets/ui.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key, required this.appState});

  final AppState appState;

  @override
  Widget build(BuildContext context) {
    final settings = appState.snapshot.settings;
    final update = appState.snapshot.update;
    return PageScroll(
      children: [
        SectionHeader('外观', caption: '主题和语言会保存到原生设置仓库'),
        Panel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('主题', style: AppTextStyle.subheadline.of(context, emphasized: true)),
              const SizedBox(height: 8),
              SegmentedControl<String>(
                segments: const [
                  ('system', '跟随系统'),
                  ('light', '浅色'),
                  ('dark', '深色'),
                ],
                value: settings.themeMode,
                onChanged: (value) => appState.run(
                  appState.bridge.setThemeMode(value),
                ),
              ),
              const SizedBox(height: 16),
              Text('语言', style: AppTextStyle.subheadline.of(context, emphasized: true)),
              const SizedBox(height: 8),
              SegmentedControl<String>(
                segments: const [
                  ('zh-CN', '简体中文'),
                  ('en', 'English'),
                  ('zh-TW', '繁體中文'),
                ],
                value: settings.locale,
                onChanged: (value) => appState.run(
                  appState.bridge.setLocale(value),
                ),
              ),
            ],
          ),
        ),
        SectionHeader('连接偏好'),
        ActionRow(
          title: '自动低延迟',
          subtitle: '连接已保存设备后自动开启低延迟',
          trailing: Switch(
            value: settings.autoLowLatency,
            onChanged: (value) => appState.run(
              appState.bridge.setAutoLowLatency(value),
            ),
            semanticLabel: '自动低延迟',
          ),
        ),
        SectionHeader('应用更新'),
        Panel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text('稳定版本', style: AppTextStyle.title3.of(context, emphasized: true)),
                  ),
                  StatusBadge(_updateLabel(update.kind), positive: update.kind == 'up_to_date'),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                _updateDescription(update),
                style: AppTextStyle.subheadline.of(context).copyWith(
                      color: update.kind == 'error'
                          ? context.colorScheme.error
                          : context.colorScheme.onSurfaceVariant,
                    ),
              ),
              if (update.kind == 'downloading') ...[
                const SizedBox(height: 12),
                LinearProgressIndicator(value: update.progress),
              ],
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: switch (update.kind) {
                      'available' => FilledButton(
                          onPressed: () => appState.run(appState.bridge.downloadUpdate()),
                          child: const Text('下载更新'),
                        ),
                      'downloading' => OutlinedButton(
                          onPressed: () => appState.run(
                            appState.bridge.cancelUpdateDownload(),
                          ),
                          child: const Text('取消下载'),
                        ),
                      'ready_to_install' => FilledButton(
                          onPressed: () => appState.run(appState.bridge.installUpdate()),
                          child: const Text('交给系统安装'),
                        ),
                      _ => OutlinedButton(
                          onPressed: () => appState.run(
                            appState.bridge.checkForUpdate(force: true),
                          ),
                          child: const Text('立即检查'),
                        ),
                    },
                  ),
                ],
              ),
            ],
          ),
        ),
        ActionRow(
          title: '自动检查更新',
          subtitle: '按更新间隔检查稳定版本',
          trailing: Switch(
            value: settings.autoCheckUpdates,
            onChanged: (value) => appState.run(
              appState.bridge.setUpdatePreferences(value, settings.autoDownloadUpdates),
            ),
            semanticLabel: '自动检查更新',
          ),
        ),
        const SizedBox(height: 8),
        ActionRow(
          title: '自动下载更新',
          subtitle: '发现新版本后自动下载 APK',
          trailing: Switch(
            value: settings.autoDownloadUpdates,
            onChanged: (value) => appState.run(
              appState.bridge.setUpdatePreferences(settings.autoCheckUpdates, value),
            ),
            semanticLabel: '自动下载更新',
          ),
        ),
        SectionHeader('诊断'),
        ActionRow(
          title: '查看运行日志',
          subtitle: '读取本地连接与协议诊断信息',
          leading: const Text('≡'),
          trailing: const GlyphIcon(Glyphs.chevronForward, size: 18),
          onPressed: () => Navigator.of(context).push(
            CardPageRoute<void>(
              builder: (_) => DiagnosticsPage(appState: appState),
            ),
          ),
        ),
        const SizedBox(height: 8),
        Panel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('fxxkHilife', style: AppTextStyle.title3.of(context, emphasized: true)),
              const SizedBox(height: 4),
              Text(
                '原生连接服务 + Flutter prism_ui 新界面',
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
}

String _updateLabel(String kind) {
  switch (kind) {
    case 'checking':
      return '检查中';
    case 'up_to_date':
      return '已是最新';
    case 'available':
      return '有新版本';
    case 'downloading':
      return '下载中';
    case 'ready_to_install':
      return '可安装';
    case 'error':
      return '失败';
    default:
      return '未检查';
  }
}

String _updateDescription(UpdateSnapshot update) {
  switch (update.kind) {
    case 'up_to_date':
      return '当前已是最新版本 ${update.versionName ?? ''}';
    case 'available':
      return '发现版本 ${update.versionName ?? '新版本'}';
    case 'downloading':
      return '正在下载 ${update.versionName ?? '更新'}';
    case 'ready_to_install':
      return '校验完成，可以交给 Android 系统确认安装';
    case 'error':
      return update.message ?? '更新检查失败';
    default:
      return '尚未检查更新';
  }
}

class DiagnosticsPage extends StatefulWidget {
  const DiagnosticsPage({super.key, required this.appState});

  final AppState appState;

  @override
  State<DiagnosticsPage> createState() => _DiagnosticsPageState();
}

class _DiagnosticsPageState extends State<DiagnosticsPage> {
  late Future<String> logs;

  @override
  void initState() {
    super.initState();
    logs = widget.appState.bridge.getLogs();
  }

  void reload() {
    setState(() => logs = widget.appState.bridge.getLogs());
  }

  @override
  Widget build(BuildContext context) {
    return PrismScaffold(
      bar: AppBar(
        leading: IconButton(
          tooltip: '返回',
          onPressed: () => Navigator.of(context).pop(),
          icon: const GlyphIcon(Glyphs.arrowBack),
        ),
        title: const Text('运行日志'),
        actions: [
          IconButton(
            tooltip: '刷新',
            onPressed: reload,
            icon: const Text('↻'),
          ),
        ],
      ),
      body: PageScroll(
        children: [
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () async {
                    await widget.appState.bridge.shareLog();
                  },
                  child: const Text('分享日志'),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: OutlinedButton(
                  onPressed: () async {
                    await widget.appState.bridge.clearLogs();
                    reload();
                  },
                  child: const Text('清空'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Panel(
            child: FutureBuilder<String>(
              future: logs,
              builder: (context, snapshot) {
                if (snapshot.connectionState != ConnectionState.done) {
                  return const LinearProgressIndicator();
                }
                return SelectableText(
                  snapshot.data?.isNotEmpty == true ? snapshot.data! : '暂无日志',
                  style: AppTextStyle.caption2.of(context).copyWith(
                        fontFamily: 'monospace',
                        color: context.colorScheme.onSurface,
                      ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
