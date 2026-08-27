#!/usr/bin/env python3
"""Validate the Material 3 fallback and centralized wallpaper glass boundary."""

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
        "enum class SurfaceRenderMode {",
        "WallpaperGlass",
        "CardDefaults.cardColors",
        "MaterialTheme.colorScheme.surfaceContainerHigh",
        "SurfaceRole",
        "UiDisplayMode.MATERIAL3",
    ):
        if required not in surface_text:
            fail(f"Material 3 surface adapter does not contain {required}")

    build_text = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    if "dev.chrisbanes.haze" in build_text.lower():
        fail("the production build still declares the removed rendering library")
    if 'androidx.compose.material3:material3' not in build_text:
        fail("Material 3 dependency is missing")
    if 'androidx.lifecycle:lifecycle-runtime-compose:2.11.0' not in build_text:
        fail("lifecycle-aware Compose collection dependency is missing")

    production_text = "\n".join(
        path.read_text(encoding="utf-8")
        for path in UI_ROOT.rglob("*.kt")
    ).lower()
    for forbidden in (
        "dev.chrisbanes.haze",
        "hazeeffect",
        "hazeglass",
        "hazeblur",
        "hazestate",
        "liquidglass",
        "glasshost",
        "glassscrollperformance",
    ):
        if forbidden in production_text:
            fail(f"removed rendering path remains in production UI: {forbidden}")

    glass_text = (UI_ROOT / "foundation/surface/WallpaperGlass.kt").read_text(encoding="utf-8")
    shader_source = ROOT / "app/src/main/res/raw/wallpaper_glass.agsl"
    for required in (
        "RuntimeShader",
        "WallpaperGlassRenderer",
        "calculateWallpaperTextureSize",
        "rememberWallpaperTexture",
        "Build.VERSION_CODES.TIRAMISU",
        "LightFieldState",
        "lightPosition",
    ):
        if required not in glass_text:
            fail(f"centralized wallpaper glass boundary is missing {required}")
    if ".border(" in glass_text:
        fail("wallpaper glass still uses a default rectangular border")
    if not shader_source.is_file() or "uniform shader wallpaper" not in shader_source.read_text(encoding="utf-8"):
        fail("independent AGSL wallpaper shader resource is missing")
    shader_text = shader_source.read_text(encoding="utf-8")
    for required in ("lightPosition", "lightIntensity", "textLuma", "sampleLuma"):
        if required not in shader_text:
            fail(f"wallpaper glass shader is missing {required}")

    for path in ROUTE_FILES:
        if "RuntimeShader" in path.read_text(encoding="utf-8"):
            fail(f"route owns AGSL shader creation: {path.relative_to(ROOT)}")

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
    scaffold_text = (UI_ROOT / "foundation/components/AppScaffold.kt").read_text(encoding="utf-8")
    if "toBitmap(config = android.graphics.Bitmap.Config.ARGB_8888)" not in scaffold_text:
        fail("wallpaper source is not converted to a software-readable ARGB_8888 bitmap")
    if "isWallpaperGlassSafeMode" not in scaffold_text or "!glassSafeMode" not in scaffold_text:
        fail("wallpaper glass startup safe-mode gate is missing")
    crash_reporter = ROOT / "app/src/main/java/com/freebuds/controller/util/CrashReporter.kt"
    if not crash_reporter.is_file() or "wallpaper_glass_safe_mode" not in crash_reporter.read_text(encoding="utf-8"):
        fail("crash reporter does not persist the wallpaper glass safe-mode marker")
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

    print("ui contract OK: Material 3 fallback, centralized AGSL wallpaper glass, typed state/events, and route boundaries")
    return 0


if __name__ == "__main__":
    sys.exit(main())
