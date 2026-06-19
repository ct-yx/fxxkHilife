# FreeBudsController — Android Companion for HUAWEI FreeBuds

## Core Purpose

Replace the bloated official HUAWEI Smart Life app with a lightweight, offline-first, login-free Android app that gives full control over HUAWEI FreeBuds / HONOR Earbuds via Bluetooth SPP.

The OpenFreebuds desktop project (by MelianMiko) proved the reverse-engineered Huawei BLE/SPP protocol works. This Android port ports that protocol to Kotlin, adds a clean Material You UI, and integrates the Haze library for real-time blur effects (frosted glass / liquid glass).

### Software Identity

- **App name (Desktop label):** `fxxkHilife` — defined in `strings.xml` as `app_name`, applied via `android:label="@string/app_name"` on `<application>` in `AndroidManifest.xml`. This is what users see under the launcher icon.
- **Package name:** `com.freebuds.controller` (Gradle module name = `fxxkHilife` in `settings.gradle.kts`)

### Version Numbering Rule

Format: `MAJOR.MINOR-fixN`

| Part | Meaning | When to bump |
|------|---------|--------------|
| MAJOR | Major feature update / breaking changes | +1 when adding a big new capability (e.g. new device profile family, UI redesign) |
| MINOR | Minor feature addition or modification | +1 when adding/improving a small feature (e.g. new quick control, new settings toggle) |
| fixN  | Bug fix count | fix1, fix2, fix3 … each time a round of bug fixes is completed |

Examples:  
- `1.0-fix1` — first release, first bug fix  
- `1.1-fix2` — one minor feature added, second round of fixes  
- `2.0-fix3` — major rewrite, third round of fixes

The current version is tracked in `PROJECT_CORE.md` and displayed in Settings > About.

## Technical Stack

| Layer | Choice |
|---|---|
| Language | Kotlin 100% |
| UI Toolkit | Jetpack Compose + Material You (Material 3) |
| Theme | Dynamic Color (Monet), supports Android 12+ wallpaper theming |
| Blur Engine | Haze library (by Chris Banes) — provides both frosted glass (haze-blur) and liquid glass (haze-liquidglass) visual effects |
| Bluetooth | Android BluetoothSocket via SPP RFCOMM (UUID 00001101-0000-1000-8000-00805f9b34fb) |
| Concurrency | Kotlin Coroutines + Flow |
| Architecture | Repository pattern: `BluetoothRepository` → `DeviceManager` → `UI State` |
| DI | Manual DI (keep it simple, no Hilt/Koin) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |

## Architecture & Extensibility

### Package Layout
```
com.freebuds.controller/
├── app/
│   ├── MainActivity.kt
│   └── FreeBudsApp.kt          // Application class, DI container, DebugLogger init
├── bluetooth/
│   ├── SppClient.kt            // Raw RFCOMM socket read/write
│   ├── SppPackage.kt           // Huawei SPP binary protocol parser
│   ├── SppCommand.kt           // All known command IDs (ported from spp_commands.py)
│   └── BluetoothRepository.kt  // Connection lifecycle, reconnection
├── device/
│   ├── DeviceManager.kt        // Discovers device → Mutex-guarded connect → loads profile → initializes handlers
│   ├── DeviceProfile.kt        // Sealed class per device model (which features it supports)
│   ├── DeviceState.kt          // Data class holding all current properties
│   └── handler/
│       ├── Handler.kt          // Interface: init(), onPackage(), getProperties(), setProperty()
│       ├── BatteryHandler.kt
│       ├── AncModeHandler.kt   // Noise cancellation toggle
│       ├── EqPresetHandler.kt  // Equalizer presets
│       ├── GestureHandler.kt   // Tap/long-press gesture config
│       ├── LowLatencyHandler.kt
│       ├── SoundQualityPreferenceHandler.kt
│       ├── DualConnectHandler.kt
│       └── DeviceInfoHandler.kt
├── ui/
│   ├── theme/
│   │   ├── Theme.kt            // Material You + Haze integration
│   │   ├── Color.kt
│   │   └── Type.kt
│   ├── screen/
│   │   ├── MainScreen.kt       // Dashboard: ConnectionCard (gray disconnected name + reconnect), battery + quick controls + device picker
│   │   ├── SettingsScreen.kt   // App settings: appearance, connection, debug log toggle + share logs button
│   │   ├── PermissionPromptScreen.kt // Initial permission grant screen
│   │   └── (PreferencesScreen — merged into SettingsScreen)
│   ├── component/
│   │   ├── BatteryCard.kt
│   │   ├── BlurToggleCard.kt   // Toggle frosted/liquid glass per card
│   │   ├── EqualizerPanel.kt
│   │   └── GestureEditor.kt
│   └── navigation/
│       └── Navigation.kt
├── data/
│   ├── PreferencesRepository.kt // DataStore prefs (blur style, theme, last device, debug_log)
│   └── DeviceDatabase.kt       // Room? Optional, store paired device info
├── util/
│   ├── Crc16.kt               // CRC16-XMODEM checksum (from openfreebuds utils)
│   └── DebugLogger.kt         // Dual-path logger: Logcat + rotating file (cache/logs/), share via FileProvider
└── service/
    ├── AncQuickTileService.kt  // Quick Settings Tile for ANC cycling
    └── StatusNotificationService.kt  // Foreground service with listening time, battery, ANC
```

### Extensibility Rules
1. **Adding a new device model** → Create a new `DeviceProfile` sealed subclass + enable existing handlers.
2. **Adding a new Bluetooth command** → Add constant in `SppCommand.kt`, create handler in `device/handler/`.
3. **Handlers are independent** — each handler owns its own `sendPackage`/`receive` matching by command ID.
4. **UI is driven by `DeviceState`** — `DeviceManager` exposes `StateFlow<DeviceState>`, Compose observes it.

## Bluetooth Protocol Implementation (ported from OpenFreebuds)

### SPP Binary Format
```
Byte 0:  'Z' (0x5A) — magic header
Byte 1-2: length (big-endian, body + 1)
Byte 3:   0x00 — reserved
Byte 4-5: command_id (2 bytes, e.g. 0x0108 for battery)
Body:     TLV sequence:
          [type:1byte][length:1byte][value:length bytes]
Tail:     CRC16-XMODEM (2 bytes) over bytes 0..len-2
```

### Key Commands (from OpenFreebuds)
| Hex | Description |
|---|---|
| `01 08` | Battery level read |
| `01 27` | Battery notification subscribe |
| `2b 6c` | Low latency mode toggle |
| `2b a3` | Sound quality preference read (sqp_connectivity / sqp_quality) |
| `01 20` | Double-tap gesture read |
| `2b 1f` | Swipe gesture read |
| `2b 17` | Long-tap base action read |
| `2b 19` | Long-tap ANC action read |
| `2b 2f` | Dual-connect enabled read |
| `2b 31` | Dual-connect device enumerate |

All write commands have their own write-opcode (same base, different trailing byte). See `SppCommand.kt`.

### Connection Flow
1. Discover paired HUAWEI/HONOR Bluetooth devices via `BluetoothAdapter`.
2. Connect to SPP service UUID `00001101-0000-1000-8000-00805f9b34fb`.
3. Send read commands for each handler (battery, ANC, EQ, gestures...).
4. Subscribe to notifications (battery level changes, ANC state changes).
5. UI reacts to `DeviceState` updates.

## Haze Library Integration

The Haze library sits on top of every "glass card" in the UI:

- **Frosted glass** (`HazeBlurStyle`): Standard blur behind content. Used for general cards.
- **Liquid glass** (`LiquidGlassVisualEffect`): Chromatic aberration + dynamic ripples. Used for the main connection card and battery display.
- **Toggle at runtime**: A `remember` switch that swaps the visual effect between the two. Implemented in `BlurToggleCard.kt`.

Usage:
```kotlin
Card(
    modifier = Modifier.hazeEffect(state, style = HazeBlurDefaults.mgLarge())
) { ... }
```

## UI/UX Guidelines

1. **Material You first** — dynamic color, rounded corners, minimal chrome.
2. **One-handed operation** — controls placed in bottom half of screen.
3. **No ads, no login** — the app works fully offline.
4. **Dark mode support** — follow system dark theme.
5. **Blur style preference** — user can choose frosted glass, liquid glass, or no blur in settings.
6. **Real-time feedback** — battery, ANC mode, and connection state update immediately via Bluetooth notification subscription.

## Development History Log

Every completed step MUST record a brief entry in `DEVELOPMENT_LOG.md` before moving to the next step. Format:
```
## YYYY-MM-DD HH:MM
- Step: <description>
- Files changed: <list>
- Status: <completed/partial/blocked>
```

## Development Rules (MANDATORY)

1. **Before any coding session** — read `DEVELOPMENT_LOG.md` and `PROJECT_CORE.md` first.
2. **Every feature branch** → documented in `DEVELOPMENT_LOG.md`.
3. **Keep code readable** — meaningful names, single responsibility, comments for non-obvious logic.
4. **Test Bluetooth without real earbuds** → implement a `DebugDeviceProfile` that returns mock data.
5. **Build first, polish later** — get Bluetooth working with basic controls, then add Haze effects and animations.
