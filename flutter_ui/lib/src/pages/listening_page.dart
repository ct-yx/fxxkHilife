import 'package:flutter/widgets.dart';
import 'package:prism_ui/prism_ui.dart';

import '../app_state.dart';
import '../widgets/ui.dart';

class ListeningPage extends StatelessWidget {
  const ListeningPage({super.key, required this.appState});

  final AppState appState;

  @override
  Widget build(BuildContext context) {
    final stats = appState.snapshot.listening;
    return PageScroll(
      children: [
        SectionHeader('听音统计', caption: '数据保存在本机，由连接服务持续记录'),
        Panel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('今日听音', style: AppTextStyle.subheadline.of(context)),
              const SizedBox(height: 4),
              Text(
                formatMinutes(stats.todayMs),
                style: AppTextStyle.display.of(context, emphasized: true),
              ),
              const SizedBox(height: 4),
              Text(
                '累计 ${formatMinutes(stats.totalMs)}',
                style: AppTextStyle.subheadline.of(context).copyWith(
                      color: context.colorScheme.onSurfaceVariant,
                    ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              child: Panel(
                child: _StatValue(label: '活跃天数', value: '${stats.activeDays} 天'),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Panel(
                child: _StatValue(label: '连续天数', value: '${stats.streakDays} 天'),
              ),
            ),
          ],
        ),
        SectionHeader('统计说明'),
        Panel(
          child: Text(
            '只有控制通道建立后才会记录听音时长。断开连接时，服务会先保存本次会话，再更新统计数据。',
            style: AppTextStyle.body.of(context).copyWith(
                  color: context.colorScheme.onSurfaceVariant,
                ),
          ),
        ),
      ],
    );
  }
}

class _StatValue extends StatelessWidget {
  const _StatValue({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: AppTextStyle.footnote.of(context)),
        const SizedBox(height: 6),
        Text(value, style: AppTextStyle.title2.of(context, emphasized: true)),
      ],
    );
  }
}
