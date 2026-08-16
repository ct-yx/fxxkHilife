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
    for required in (
        "hazeSource",
        "hazeGlass",
        "hazeBlur",
        "GlassStyle",
        "GlassOptics",
        "backgroundAvailable",
        "sourceAttached",
        "SurfaceRenderMode",
        "hardwareAccelerated",
    ):
        if required not in surface_text:
            fail(f"surface adapter does not contain {required}")

    build_text = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    for required in (
        'dev.chrisbanes.haze:haze:2.0.0-alpha05',
        'dev.chrisbanes.haze:haze-blur:2.0.0-alpha05',
        'dev.chrisbanes.haze:haze-glass:2.0.0-alpha05',
    ):
        if required not in build_text:
            fail(f"Haze alpha05 dependency is missing: {required}")
    if "2.0.0-alpha03" in build_text:
        fail("old Haze alpha03 dependency remains in the production build")
    if "dev.chrisbanes.haze:haze-blur-materials" in build_text:
        fail("haze-blur-materials has no production call site")
    if 'androidx.lifecycle:lifecycle-runtime-compose:2.11.0' not in build_text:
        fail("lifecycle-aware Compose collection dependency is missing")

    if "compileSdk = 37" not in build_text:
        fail("alpha05 requires compileSdk 37")
    if "hazeGlass" not in surface_text or "hazeBlur" not in surface_text:
        fail("glass and blur must remain separate renderer call sites")
    if "hazeEffect" in surface_text or "blurEffect" in surface_text:
        fail("legacy alpha03 effect path remains in the production surface adapter")
    if "source_unavailable" not in surface_text or "return SurfaceRenderMode.Material3" not in surface_text:
        fail("missing visible Material 3 fallback for an unavailable background source")

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
        if "collectAsState()" in text:
            fail(f"route uses lifecycle-unaware Flow collection: {path.relative_to(ROOT)}")

    if not (UI_ROOT / "state/UiState.kt").is_file():
        fail("typed UI state file is missing")
    if "sealed interface SettingsEvent" not in (UI_ROOT / "state/UiState.kt").read_text(encoding="utf-8"):
        fail("SettingsEvent boundary is missing")
    if "AppScaffold(" not in (UI_ROOT / "AppNavHost.kt").read_text(encoding="utf-8"):
        fail("AppNavHost does not use AppScaffold")
    nav_text = (UI_ROOT / "AppNavHost.kt").read_text(encoding="utf-8")
    if "DeviceNavigationState" not in nav_text or "navController.navigate(Route.Device)" not in nav_text:
        fail("device navigation intent/session boundary is missing")
    if "viewModel.connect(device)" not in nav_text:
        fail("scanned-device navigation does not start an explicit user connection")

    home_text = (UI_ROOT / "HomeScreen.kt").read_text(encoding="utf-8")
    if "LaunchedEffect(connection.address)" not in home_text:
        fail("saved-device refresh is not keyed by the target address")
    if "LaunchedEffect(connection.systemConnected" in home_text:
        fail("saved-device refresh is still keyed by every system connection phase")

    stable_key_requirements = {
        "HomeScreen.kt": "key = { it.address }",
        "ScanScreen.kt": "key = { it.device.address }",
        # DeviceScreen keeps the dual-connect cards inside an isolated section so the parent
        # LazyColumn does not rebuild on every props update; the section still keys each card by
        # address through Compose's key() primitive.
        "DeviceScreen.kt": ("key = { it.address }", "key(device.address)"),
    }
    for filename, required in stable_key_requirements.items():
        text = (UI_ROOT / filename).read_text(encoding="utf-8")
        alternatives = (required,) if isinstance(required, str) else required
        if not any(candidate in text for candidate in alternatives):
            fail(f"stable device key is missing: {filename}")

    if any("animateContentSize" in path.read_text(encoding="utf-8") for path in UI_ROOT.rglob("*.kt")):
        fail("expandable UI still combines animateContentSize with visibility animation")
    if (ROOT / "app/src/main/java/com/freebuds/controller/data/UpdateChecker.kt").exists():
        fail("obsolete UpdateChecker.kt is still present")

    print("ui contract OK: typed state/events, single surface adapter, and route boundaries")
    return 0


if __name__ == "__main__":
    sys.exit(main())
