import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:prism_ui/prism_ui.dart';

import 'app_state.dart';
import 'app_shell.dart';
import 'native_bridge.dart';

void runFxxkHilife() {
  runApp(const FxxkHilifeApp());
}

class FxxkHilifeApp extends StatefulWidget {
  const FxxkHilifeApp({super.key});

  @override
  State<FxxkHilifeApp> createState() => _FxxkHilifeAppState();
}

class _FxxkHilifeAppState extends State<FxxkHilifeApp> {
  late final AppState appState;

  @override
  void initState() {
    super.initState();
    appState = AppState(NativeBridge());
    appState.addListener(_refreshTheme);
    unawaited(appState.initialize());
  }

  void _refreshTheme() => setState(() {});

  @override
  void dispose() {
    appState.removeListener(_refreshTheme);
    appState.dispose();
    super.dispose();
  }

  static final _lightTheme = ThemeData(
    colorScheme: ColorScheme.fromSeed(
      seedColor: const Color(0xFF0A84FF),
      brightness: Brightness.light,
      style: ColorStyle.vivid,
    ),
  );

  static final _darkTheme = ThemeData(
    colorScheme: ColorScheme.fromSeed(
      seedColor: const Color(0xFF64B5FF),
      brightness: Brightness.dark,
      style: ColorStyle.tinted,
    ),
  );

  ThemeMode get _themeMode => switch (appState.snapshot.settings.themeMode) {
        'dark' => ThemeMode.dark,
        'light' => ThemeMode.light,
        _ => ThemeMode.system,
      };

  @override
  Widget build(BuildContext context) {
    return PrismApp(
      title: 'fxxkHilife',
      theme: _lightTheme,
      darkTheme: _darkTheme,
      themeMode: _themeMode,
      liquidGlass: LiquidGlassQuality.standard,
      debugShowCheckedModeBanner: false,
      builder: (context, child) {
        final size = MediaQuery.sizeOf(context);
        return ViewScope(
          size: size,
          isMobile: size.width < 600,
          child: child ?? const SizedBox.shrink(),
        );
      },
      home: AppShell(appState: appState),
    );
  }
}
