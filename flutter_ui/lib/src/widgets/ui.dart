import 'dart:ui' as ui;

import 'package:flutter/widgets.dart';
import 'package:prism_ui/prism_ui.dart';
export 'package:prism_ui/icons/icons.dart';

class PrismAppBar extends StatelessWidget implements PreferredSizeWidget {
  const PrismAppBar({
    super.key,
    this.leading,
    this.title,
    this.actions = const <Widget>[],
  });

  final Widget? leading;
  final Widget? title;
  final List<Widget> actions;

  @override
  Size get preferredSize => const Size.fromHeight(72);

  @override
  Widget build(BuildContext context) => AppBar(
        leading: leading,
        title: title,
        actions: actions,
      );
}

/// A small, platform-neutral earbud and charging case mark.
///
/// The old Android vector has too many details for a 24–32 dp list leading
/// icon. This outline keeps the case and both earbuds legible at small sizes
/// and lets the surrounding screen choose the semantic tint.
class EarbudIcon extends StatelessWidget {
  const EarbudIcon({super.key, this.size = 28, this.color});

  final double size;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final tint =
        color ?? IconTheme.of(context).color ?? context.colorScheme.primary;
    return ExcludeSemantics(
      child: SizedBox.square(
        dimension: size,
        child: CustomPaint(painter: _EarbudIconPainter(tint)),
      ),
    );
  }
}

class _EarbudIconPainter extends CustomPainter {
  const _EarbudIconPainter(this.color);

  final Color color;

  @override
  void paint(ui.Canvas canvas, Size size) {
    final scale = size.shortestSide / 24;
    canvas
      ..save()
      ..scale(scale, scale);

    final stroke = ui.Paint()
      ..color = color
      ..style = ui.PaintingStyle.stroke
      ..strokeWidth = 1.7
      ..strokeCap = ui.StrokeCap.round
      ..strokeJoin = ui.StrokeJoin.round;

    // Two simple buds above the case.
    canvas
      ..drawCircle(const ui.Offset(8, 5.5), 2.15, stroke)
      ..drawCircle(const ui.Offset(16, 5.5), 2.15, stroke)
      ..drawLine(const ui.Offset(8, 7.7), const ui.Offset(8, 10.8), stroke)
      ..drawLine(const ui.Offset(16, 7.7), const ui.Offset(16, 10.8), stroke);

    final caseRect = ui.RRect.fromRectAndRadius(
      const ui.Rect.fromLTRB(3, 10, 21, 20.5),
      const ui.Radius.circular(4),
    );
    canvas
      ..drawRRect(caseRect, stroke)
      ..drawLine(const ui.Offset(5.2, 10), const ui.Offset(18.8, 10), stroke)
      ..restore();
  }

  @override
  bool shouldRepaint(_EarbudIconPainter oldDelegate) =>
      oldDelegate.color != color;
}

class PageScroll extends StatelessWidget {
  const PageScroll({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: EdgeInsets.fromLTRB(
        16,
        context.contentTopPadding,
        16,
        context.bottomInset + 28,
      ),
      children: children,
    );
  }
}

class SectionHeader extends StatelessWidget {
  const SectionHeader(this.title, {super.key, this.caption, this.leading});

  final String title;
  final String? caption;
  final Widget? leading;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 20, 4, 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (leading != null) ...[
            Padding(
              padding: const EdgeInsets.only(right: 8, top: 1),
              child: leading!,
            ),
          ],
          Expanded(
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
          ),
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
