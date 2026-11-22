import 'package:flutter/widgets.dart';
import 'package:prism_ui/prism_ui.dart';

import 'app_state.dart';
import 'pages/device_page.dart';
import 'pages/home_page.dart';
import 'pages/listening_page.dart';
import 'pages/scan_page.dart';
import 'pages/settings_page.dart';
import 'widgets/ui.dart';

class AppShell extends StatefulWidget {
  const AppShell({super.key, required this.appState});

  final AppState appState;

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  var selectedIndex = 0;

  void openScan() {
    Navigator.of(context).push(
      CardPageRoute<void>(
        builder: (_) => ScanPage(appState: widget.appState),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.appState,
      builder: (context, _) {
        final pages = <Widget>[
          HomePage(
            appState: widget.appState,
            onOpenScan: openScan,
            onOpenDevice: () => setState(() => selectedIndex = 1),
          ),
          DevicePage(appState: widget.appState),
          ListeningPage(appState: widget.appState),
          SettingsPage(appState: widget.appState),
        ];
        final titles = <String>['设备', '控制', '统计', '设置'];
        return PrismScaffold(
          bar: PrismAppBar(title: Text(titles[selectedIndex])),
          body: IndexedStack(index: selectedIndex, children: pages),
          foot: NavigationBar(
            selectedIndex: selectedIndex,
            onDestinationSelected: (index) => setState(() => selectedIndex = index),
            destinations: const [
              NavigationDestination(icon: Text('⌂'), label: '设备'),
              NavigationDestination(icon: Text('◉'), label: '控制'),
              NavigationDestination(icon: Text('◒'), label: '统计'),
              NavigationDestination(icon: Text('⋯'), label: '设置'),
            ],
          ),
        );
      },
    );
  }
}
