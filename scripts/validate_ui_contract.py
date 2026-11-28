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
    if "path: vendor/prism_ui" not in pubspec:
        fail("Flutter module must use the vendored prism_ui compatibility source")
    prism_source = require_file(FLUTTER_ROOT / "vendor/prism_ui/lib/prism_list.dart")
    route_source = require_file(FLUTTER_ROOT / "vendor/prism_ui/lib/route_motion_hold.dart")
    require_file(FLUTTER_ROOT / "vendor/prism_ui/UPSTREAM.md")
    if "package:flutter/rendering.dart" not in prism_source or "package:flutter/rendering.dart" not in route_source:
        fail("vendored prism_ui must import ScrollCacheExtent from flutter/rendering.dart")
    if "project_type: module" not in require_file(FLUTTER_ROOT / ".metadata"):
        fail("Flutter module metadata is missing")

    settings = require_file(ROOT / "settings.gradle.kts")
    if "flutter_ui/.android/include_flutter.groovy" not in settings:
        fail("Android host does not include the generated Flutter module")

    build = require_file(ROOT / "app/build.gradle.kts")
    if 'implementation(project(":flutter"))' not in build:
        fail("Android host does not depend on the Flutter module")
    if 'id("org.jetbrains.kotlin.android")' not in build:
        fail("Android host must apply the Kotlin Android plugin in the app module")
    for forbidden in ("androidx.compose", "org.jetbrains.kotlin.plugin.compose", "coil-compose"):
        if forbidden in build:
            fail(f"old Compose UI dependency remains: {forbidden}")

    if 'id("com.android.application") version "9.1.0" apply false' not in settings:
        fail("host AGP must stay on the Flutter 3.47 template version 9.1.0")
    if 'id("org.jetbrains.kotlin.android") version "2.4.0" apply false' not in settings:
        fail("host Kotlin Gradle Plugin must stay on the Flutter 3.47 template version 2.4.0")
    wrapper = require_file(ROOT / "gradle/wrapper/gradle-wrapper.properties")
    if "gradle-9.3.1-all.zip" not in wrapper:
        fail("Gradle wrapper must stay on the Flutter 3.47 template version 9.3.1")
    properties = require_file(ROOT / "gradle.properties")
    for flag in ("android.newDsl=false", "android.builtInKotlin=false"):
        if flag not in properties:
            fail(f"Flutter Android template flag is missing: {flag}")
    root_build = require_file(ROOT / "build.gradle.kts")
    for repository in ("google()", "mavenCentral()"):
        if repository not in root_build:
            fail(f"root Gradle project must expose {repository} to native dependencies")

    main_activity = require_file(UI_ROOT / "MainActivity.kt")
    if "FlutterActivity" not in main_activity or "NativeUiBridge" not in main_activity:
        fail("MainActivity is not a Flutter host with the native bridge")

    bridge = require_file(NATIVE_ROOT / "nativeui/NativeUiBridge.kt")
    controller = require_file(NATIVE_ROOT / "nativeui/NativeUiController.kt")
    api = require_file(NATIVE_ROOT / "data/DeviceControlApi.kt")
    runtime = require_file(NATIVE_ROOT / "runtime/EarbudRuntime.kt")
    runtime_adapter = require_file(NATIVE_ROOT / "runtime/RepositoryEarbudRuntime.kt")
    dart_bridge = require_file(FLUTTER_ROOT / "lib/src/native_bridge.dart")
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
    for typed_method in ("disconnect", "startScan", "stopScan"):
        if f"Future<void> {typed_method}" not in dart_bridge:
            fail(f"typed Flutter native method is missing: {typed_method}")
    for semantic_command in ("setAncMode", "setLowLatency", "setGesture"):
        if semantic_command not in api:
            fail(f"semantic device API is missing: {semantic_command}")
    for required in ("sealed interface RuntimeCommand", "interface EarbudRuntime", "RuntimeSnapshot"):
        if required not in runtime:
            fail(f"runtime command seam is missing {required}")
    if "DeviceControlApi(repository)" in controller:
        fail("native UI controller must dispatch through EarbudRuntime")
    if "RuntimeCommand.SetAncMode" not in runtime_adapter:
        fail("runtime adapter does not expose semantic device commands")

    flutter_sources = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (FLUTTER_ROOT / "lib").rglob("*.dart")
    )
    if ".bridge.invoke(" in flutter_sources:
        fail("Flutter pages must use typed native methods instead of raw channel names")
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
    stale_test_root = ROOT / "app/src/test/java/com/freebuds/controller/ui"
    if stale_test_root.exists():
        fail("old Compose UI tests remain under app/src/test/java/com/freebuds/controller/ui")
    for path in UI_ROOT.rglob("*.kt"):
        if path.name in old_ui_files:
            fail(f"old Compose UI file remains: {path.relative_to(ROOT)}")
        if "androidx.compose" in path.read_text(encoding="utf-8"):
            fail(f"Compose source remains in the native UI package: {path.relative_to(ROOT)}")

    print("ui contract OK: Flutter prism_ui presentation, semantic native API, and old Compose UI removed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
