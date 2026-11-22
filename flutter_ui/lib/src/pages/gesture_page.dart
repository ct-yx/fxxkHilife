import 'package:flutter/widgets.dart';
import 'package:prism_ui/prism_ui.dart';

import '../app_state.dart';
import '../widgets/ui.dart';

class GesturePage extends StatelessWidget {
  const GesturePage({super.key, required this.appState});

  final AppState appState;

  @override
  Widget build(BuildContext context) {
    final device = appState.snapshot.device;
    final options = device.gestureOptions.isEmpty
        ? const <String>['tap_action_pause', 'tap_action_next', 'tap_action_prev', 'tap_action_off']
        : device.gestureOptions;

    return AnimatedBuilder(
      animation: appState,
      builder: (context, _) {
        final current = appState.snapshot.device;
        return PrismScaffold(
          bar: PrismAppBar(
            leading: IconButton(
              tooltip: '返回',
              onPressed: () => Navigator.of(context).pop(),
              icon: const GlyphIcon(Glyphs.arrowBack),
            ),
            title: const Text('手势设置'),
          ),
          body: PageScroll(
            children: [
              SectionHeader('触控动作', caption: '点按一行可以循环选择可用动作'),
              _GestureRow(
                title: '左耳双击',
                target: 'double_tap_left',
                value: current.doubleTapLeft,
                options: options,
                appState: appState,
              ),
              const SizedBox(height: 8),
              _GestureRow(
                title: '右耳双击',
                target: 'double_tap_right',
                value: current.doubleTapRight,
                options: options,
                appState: appState,
              ),
              const SizedBox(height: 8),
              _GestureRow(
                title: '左耳三击',
                target: 'triple_tap_left',
                value: current.tripleTapLeft,
                options: options,
                appState: appState,
              ),
              const SizedBox(height: 8),
              _GestureRow(
                title: '右耳三击',
                target: 'triple_tap_right',
                value: current.tripleTapRight,
                options: options,
                appState: appState,
              ),
              const SizedBox(height: 8),
              _GestureRow(
                title: '滑动',
                target: 'swipe_gesture',
                value: current.swipeGesture,
                options: options,
                appState: appState,
              ),
              const SizedBox(height: 8),
              _GestureRow(
                title: '长按',
                target: 'long_tap',
                value: current.longTap,
                options: options,
                appState: appState,
              ),
            ],
          ),
        );
      },
    );
  }
}

class _GestureRow extends StatelessWidget {
  const _GestureRow({
    required this.title,
    required this.target,
    required this.value,
    required this.options,
    required this.appState,
  });

  final String title;
  final String target;
  final String? value;
  final List<String> options;
  final AppState appState;

  @override
  Widget build(BuildContext context) {
    final currentIndex = value == null ? -1 : options.indexOf(value!);
    final next = options[(currentIndex + 1) % options.length];
    return ActionRow(
      title: title,
      subtitle: gestureLabel(value),
      leading: const Text('✦'),
      trailing: const GlyphIcon(Glyphs.chevronForward, size: 18),
      onPressed: () => appState.run(appState.bridge.setGesture(target, next)),
    );
  }
}

String gestureLabel(String? raw) {
  switch (raw) {
    case 'tap_action_pause':
      return '播放 / 暂停';
    case 'tap_action_next':
      return '下一曲';
    case 'tap_action_prev':
      return '上一曲';
    case 'tap_action_assistant':
      return '语音助手';
    case 'tap_action_change_volume':
      return '调节音量';
    case 'tap_action_off':
      return '关闭';
    default:
      return raw ?? '未读取';
  }
}
