import 'package:flutter/widgets.dart';
import 'package:prism_ui/prism_ui.dart';

class PageScroll extends StatelessWidget {
  const PageScroll({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
      children: children,
    );
  }
}

class SectionHeader extends StatelessWidget {
  const SectionHeader(this.title, {super.key, this.caption});

  final String title;
  final String? caption;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 20, 4, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: AppTextStyle.headline.of(context).copyWith(
                  color: context.colorScheme.onSurface,
                ),
          ),
          if (caption != null) ...[
            const SizedBox(height: 3),
            Text(
              caption!,
              style: AppTextStyle.footnote.of(context).copyWith(
                    color: context.colorScheme.onSurfaceVariant,
                  ),
            ),
          ],
        ],
      ),
    );
  }
}

class Panel extends StatelessWidget {
  const Panel({super.key, required this.child, this.onPressed});

  final Widget child;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return Card(
      inert: onPressed == null,
      onPressed: onPressed,
      padding: const EdgeInsets.all(16),
      child: child,
    );
  }
}

class StatusBadge extends StatelessWidget {
  const StatusBadge(this.label, {super.key, this.positive = false});

  final String label;
  final bool positive;

  @override
  Widget build(BuildContext context) {
    final scheme = context.colorScheme;
    return DecoratedBox(
      decoration: ShapeDecoration(
        color: positive ? scheme.primaryContainer : scheme.surfaceContainerHighest,
        shape: AppShape.full,
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        child: Text(
          label,
          style: AppTextStyle.caption1.of(context, emphasized: true).copyWith(
                color: positive ? scheme.onPrimaryContainer : scheme.onSurfaceVariant,
              ),
        ),
      ),
    );
  }
}

class ActionRow extends StatelessWidget {
  const ActionRow({
    super.key,
    required this.title,
    this.subtitle,
    this.leading,
    this.trailing,
    this.onPressed,
  });

  final String title;
  final String? subtitle;
  final Widget? leading;
  final Widget? trailing;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return Card(
      inert: onPressed == null,
      onPressed: onPressed,
      padding: EdgeInsets.zero,
      child: ListRow(
        leading: leading,
        title: Text(title),
        subtitle: subtitle == null ? null : Text(subtitle!),
        trailing: trailing,
      ),
    );
  }
}

Widget valueText(BuildContext context, String? value) => Text(
      value ?? '—',
      style: AppTextStyle.subheadline.of(context).copyWith(
            color: context.colorScheme.onSurfaceVariant,
          ),
    );

String percent(int? value) => value == null ? '—' : '$value%';

String formatMinutes(int milliseconds) {
  final minutes = (milliseconds / 60000).round();
  if (minutes < 60) return '$minutes 分钟';
  final hours = minutes ~/ 60;
  final rest = minutes % 60;
  return rest == 0 ? '$hours 小时' : '$hours 小时 $rest 分钟';
}

String readableMode(String? raw) {
  switch (raw) {
    case 'cancellation':
      return '降噪';
    case 'awareness':
      return '透传';
    case 'normal':
      return '关闭';
    case 'dynamic':
      return '动态';
    default:
      return raw ?? '未知';
  }
}
