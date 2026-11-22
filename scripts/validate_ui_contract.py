#!/usr/bin/env python3
"""Validate the Flutter presentation boundary and native semantic API."""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FLUTTER_ROOT = ROOT / "flutter_ui"
NATIVE_ROOT = ROOT / "app/src/main/java/com/freebuds/controller"
UI_ROOT = NATIVE_ROOT / "ui"


def fail(message: str) -> None:
    raise SystemExit(f"ui contract invalid: {message}")


def require_file(path: Path) -> str:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def main() -> int:
    pubspec = require_file(FLUTTER_ROOT / "pubspec.yaml")
    if "prism_ui: 0.1.3" not in pubspec:
        fail("Flutter module must pin prism_ui to 0.1.3")
    if "project_type: module" not in require_file(FLUTTER_ROOT / ".metadata"):
        fail("Flutter module metadata is missing")

    settings = require_file(ROOT / "settings.gradle.kts")
    if "flutter_ui/.android/include_flutter.groovy" not in settings:
        fail("Android host does not include the generated Flutter module")

    build = require_file(ROOT / "app/build.gradle.kts")
    if 'implementation(project(":flutter"))' not in build:
        fail("Android host does not depend on the Flutter module")
    for forbidden in ("androidx.compose", "org.jetbrains.kotlin.plugin.compose", "coil-compose"):
        if forbidden in build:
            fail(f"old Compose UI dependency remains: {forbidden}")

    main_activity = require_file(UI_ROOT / "MainActivity.kt")
    if "FlutterActivity" not in main_activity or "NativeUiBridge" not in main_activity:
        fail("MainActivity is not a Flutter host with the native bridge")

    bridge = require_file(NATIVE_ROOT / "nativeui/NativeUiBridge.kt")
    controller = require_file(NATIVE_ROOT / "nativeui/NativeUiController.kt")
    api = require_file(NATIVE_ROOT / "data/DeviceControlApi.kt")
    if 'fxxkhilife/native_ui' not in bridge or 'fxxkhilife/native_ui_state' not in bridge:
        fail("native channel names are missing")
    for semantic_command in (
        "setAncMode",
        "setLowLatency",
        "setDualConnect",
        "setGesture",
        "setUpdatePreferences",
    ):
        if semantic_command not in controller:
            fail(f"native controller command is missing: {semantic_command}")
    if "repository.setProperty(" in controller or "setProperty(" in bridge:
        fail("raw property writes crossed the UI bridge")
    for semantic_command in ("setAncMode", "setLowLatency", "setGesture"):
        if semantic_command not in api:
            fail(f"semantic device API is missing: {semantic_command}")

    flutter_sources = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (FLUTTER_ROOT / "lib").rglob("*.dart")
    )
    for required in (
        "PrismApp(",
        "PrismScaffold(",
        "NavigationBar(",
        "SegmentedControl<",
        "EventChannel('fxxkhilife/native_ui_state')",
    ):
        if required not in flutter_sources:
            fail(f"new Flutter UI boundary is missing {required}")

    old_ui_files = {
        "AppNavHost.kt",
        "HomeScreen.kt",
        "ScanScreen.kt",
        "DeviceScreen.kt",
        "GestureScreen.kt",
        "ListeningStatsScreen.kt",
        "SettingsScreen.kt",
        "PermissionGuideScreen.kt",
        "DeviceViewModel.kt",
    }
    for path in UI_ROOT.rglob("*.kt"):
        if path.name in old_ui_files:
            fail(f"old Compose UI file remains: {path.relative_to(ROOT)}")
        if "androidx.compose" in path.read_text(encoding="utf-8"):
            fail(f"Compose source remains in the native UI package: {path.relative_to(ROOT)}")

    print("ui contract OK: Flutter prism_ui presentation, semantic native API, and old Compose UI removed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
