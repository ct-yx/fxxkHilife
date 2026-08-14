#!/usr/bin/env python3
"""Validate the UI migration boundaries without requiring an Android device."""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
UI_ROOT = ROOT / "app/src/main/java/com/freebuds/controller/ui"
SURFACE_ADAPTER = UI_ROOT / "foundation/surface/SurfaceRenderer.kt"
ROUTE_FILES = {
    UI_ROOT / "AppNavHost.kt",
    UI_ROOT / "HomeScreen.kt",
    UI_ROOT / "ScanScreen.kt",
    UI_ROOT / "DeviceScreen.kt",
    UI_ROOT / "GestureScreen.kt",
    UI_ROOT / "ListeningStatsScreen.kt",
    UI_ROOT / "SettingsScreen.kt",
}


def fail(message: str) -> None:
    raise SystemExit(f"ui contract invalid: {message}")


def main() -> int:
    if not SURFACE_ADAPTER.is_file():
        fail("SurfaceRenderer.kt is missing")
    surface_text = SURFACE_ADAPTER.read_text(encoding="utf-8")
    for required in ("hazeSource", "hazeEffect", "blurEffect", "SurfaceRenderMode"):
        if required not in surface_text:
            fail(f"surface adapter does not contain {required}")

    build_text = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    for required in (
        'dev.chrisbanes.haze:haze:2.0.0-alpha03',
        'dev.chrisbanes.haze:haze-blur:2.0.0-alpha03',
    ):
        if required not in build_text:
            fail(f"Haze compatibility dependency is missing: {required}")
    if "dev.chrisbanes.haze:haze-glass" in build_text:
        fail("haze-glass is not part of the current compileSdk 36 baseline")
    if "dev.chrisbanes.haze:haze-blur-materials" in build_text:
        fail("haze-blur-materials has no production call site")

    for path in UI_ROOT.rglob("*.kt"):
        if path == SURFACE_ADAPTER:
            continue
        text = path.read_text(encoding="utf-8")
        if "dev.chrisbanes.haze" in text:
            fail(f"direct rendering-library import outside adapter: {path.relative_to(ROOT)}")

    for path in ROUTE_FILES:
        if not path.is_file():
            fail(f"route file is missing: {path.relative_to(ROOT)}")
        text = path.read_text(encoding="utf-8")
        if "getSharedPreferences(" in text:
            fail(f"route owns persistence directly: {path.relative_to(ROOT)}")
        if path.name != "TerminalActivity.kt" and "setProperty(" in text:
            fail(f"route owns raw property writes: {path.relative_to(ROOT)}")

    if not (UI_ROOT / "state/UiState.kt").is_file():
        fail("typed UI state file is missing")
    if "sealed interface SettingsEvent" not in (UI_ROOT / "state/UiState.kt").read_text(encoding="utf-8"):
        fail("SettingsEvent boundary is missing")
    if "AppScaffold(" not in (UI_ROOT / "AppNavHost.kt").read_text(encoding="utf-8"):
        fail("AppNavHost does not use AppScaffold")
    if (ROOT / "app/src/main/java/com/freebuds/controller/data/UpdateChecker.kt").exists():
        fail("obsolete UpdateChecker.kt is still present")

    print("ui contract OK: typed state/events, single surface adapter, and route boundaries")
    return 0


if __name__ == "__main__":
    sys.exit(main())
