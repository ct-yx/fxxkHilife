# Development History

---

## 2026-06-19 01:40

**Initialize project structure**

- Rename namespace, update build configs, create core package skeleton
- Files: `settings.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `AndroidManifest.xml`, `PROJECT_CORE.md`, `DEVELOPMENT_LOG.md`
✅ completed

---

## 2026-06-19 02:05

**Bluetooth protocol layer + device management + basic UI components**

- `SppCommand.kt`, `SppPackage.kt`, `Crc16.kt`, `SppClient.kt`
- `Handler.kt`, `BatteryHandler.kt`, `AncModeHandler.kt`, `LowLatencyHandler.kt`, `SoundQualityHandler.kt`
- `DeviceState.kt`, `DeviceProfile.kt`, `DeviceManager.kt`
- `FreeBudsApp.kt`, `Theme.kt`, `BatteryCard.kt`
✅ completed

---

## 2026-06-19 02:15

**Data persistence + navigation + full UI screens + MainActivity entry**

- Create `PreferencesRepository` (blur_style, dark_mode, last_device_address, auto_connect)
- Create `Navigation` (NavHost with Main + Settings routes)
- Create `MainScreen` — ConnectionCard, BatteryCard, QuickControlsCard (ANC, Low Latency, Sound Quality)
- Create `SettingsScreen` — Appearance (Visual Effect, Theme), Connection (Auto Connect), About (version, device profile)
- Create `BlurToggleCard` — Haze wrapper with TODO integration points
- Create `MainActivity` — edge-to-edge, auto-connect on launch, cleanup on destroy
- Files: `PreferencesRepository.kt`, `Navigation.kt`, `MainScreen.kt`, `SettingsScreen.kt`, `BlurToggleCard.kt`, `MainActivity.kt`
✅ completed

---

## 2026-06-19 02:30

**Game mode Fixed On + Quick Settings Tile + Status Notification**

- Low Latency changed to tri-state SegmentedButton: Off / On / Fixed On (persisted in DataStore)
- Created `AncQuickTileService` — Quick Settings Tile, cycles ANC mode on tap
- Created `StatusNotificationService` — foreground service showing device name, ANC mode, battery, cumulative listening time
- Created notification channel `CHANNEL_STATUS`
- Added SettingsScreen toggles for "Low Latency Fixed On" and "Status Notification"
- Added permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS`
- Files: `AncQuickTileService.kt` (new), `StatusNotificationService.kt` (new), `AndroidManifest.xml`, `FreeBudsApp.kt`, `DeviceManager.kt`, `PreferencesRepository.kt`, `MainScreen.kt`, `SettingsScreen.kt`
✅ completed

---

## 2026-06-19 03:00

**Stage 1–4 execution complete — P0 bug fix + UI Handlers**

- Fix duplicate DeviceManager & events.collect blocking
- Stage 2: BlurToggleCard with Compose built-in blur/gradient
- Stage 3: 4 new Handlers — `EqPresetHandler`, `GestureHandler`, `DeviceInfoHandler`, `DualConnectHandler`
- Stage 4: DeviceState expanded (24 fields), MainScreen UI cards (EQ, Gesture, DualConnect, DeviceInfo)
✅ completed

---

## 2026-06-19 04:00

**Bug fix: connection no feedback + debug log toggle**

- Root cause: `_connectionState` jumped directly from DISCONNECTED to CONNECTED; UI `isConnecting` was always false
- Fix: `connect()` sets CONNECTING state at entry; fail paths use try-catch → Snackbar
- Added debug logging system — `PreferencesRepository.DEBUG_LOG` + file/Logcat dual output
- Files: `DeviceManager.kt`, `SppClient.kt`, `FreeBudsApp.kt`, `PreferencesRepository.kt`, `MainScreen.kt`, `SettingsScreen.kt`
✅ completed

---

## 2026-06-19 04:15

**Bug fix: SecurityException on DevicePicker + app rename**

- Root cause: `getBondedDevices()` requires `BLUETOOTH_CONNECT` at runtime on Android 12+
- Fix: wrap `findPairedDevices()` in try-catch → returns empty list on SecurityException
- App renamed to "fxxkHilife" (both languages); version → `1.0-2-1`
- Files: `DeviceManager.kt`, `app/build.gradle.kts`, `strings.xml`
✅ completed

---

## 2026-06-19 04:30

**Bug fix: repeated clicks crash + DebugLogger dual-path + share logs**

- Root cause 1: rapid `connect()` calls race; old connection cleanup races with new connection
- Fix 1: `Mutex.withLock` serializes connects; early-return if already CONNECTING; eventJob try-catch
- Root cause 2: Logcat-only logs lost on crash
- Fix 2: `DebugLogger` singleton — Logcat + rotating file (`cache/logs/`, 1MB max, keeps last 3)
- Added `FileProvider` for secure log sharing; SettingsScreen "Share Logs" button
- Files: `DeviceManager.kt`, `SppClient.kt`, `DebugLogger.kt` (new), `FreeBudsApp.kt`, `AndroidManifest.xml`, `file_paths.xml` (new), `SettingsScreen.kt`
✅ completed

---

## 2026-06-19 05:30

**Connection state persistence + UI polish**

- Root cause: `disconnect()` reset `_state` to `DeviceState()`, wiping all device info
- Fix: `DeviceState` adds `lastDeviceName` / `lastDeviceAddress` (persisted); disconnect preserves these; `ConnectionCard` shows "Disconnected: [name]" in gray
- Added `DeviceManager.resetState()` for full cleanup
- Files: `DeviceState.kt`, `DeviceManager.kt`, `MainScreen.kt`
✅ completed

---

## 2026-06-19 12:10

**Bug fix: connect drops immediately + reconnect no-op + button bounce + desktop label + docs**

**Root causes:**
1. `connect()` unconditionally called `disconnect()` at entry → killed fresh SPP socket
2. Disconnected/Error events only set `_connectionState` but not `_state.connected = false`
3. Reconnect button opened device picker instead of reconnecting last device
4. No button debounce → rapid clicks caused concurrent connects
5. Launcher icon showed internal name (missing `android:label`)

**Fixes:**
- `connect()` — early return if already CONNECTED to same device; skip `disconnect()` when connected
- EventJob — Disconnected/Error now set `_state.connected = false`
- Reconnect — directly calls `connect(last)` if `lastConnectedDevice != null`
- Added `btnLock` debounce (500ms) on all buttons
- `AndroidManifest.xml` — added `android:label` on `<application>`
- Files: `DeviceManager.kt`, `MainScreen.kt`, `AndroidManifest.xml`, `PROJECT_CORE.md`
✅ completed

---

## 2026-06-20

**v1.2.2 Release — 4 remaining issues fixed**

- Fix unregistered `AutoPauseHandler` / `VoiceLanguageHandler` in DeviceManager
- Add missing `AutoPauseCard` / `VoiceLanguageCard` in MainScreen
- Add `voiceLanguage` / `voiceLanguageOptions` fields to DeviceState
- Version bump: `versionCode 1→2`, `versionName "1.0-2-1"→"1.0-2-2"`
- `README.md` fully updated
✅ completed

---

## 2026-06-20

**v1.3.0 — Major refactor: upstream alignment + bilingual + permissions + UI + keep-alive**

### Upstream comparison
- Compared all 18 handlers from melianmiko/OpenFreebuds main branch
- Key gaps identified:
  1. `AncModeHandler` incomplete (upstream 108 lines, current only mode parsing)
  2. `GestureHandler` missing 3 of 4 gesture types
  3. Missing handlers: `state_in_ear.py`, `action_power_button.py`, `logs.py`
  4. Missing DeviceProfile features: `VOICE_LANGUAGE`, `ANC_LEVEL`, `ANC_DYNAMIC`, `IN_EAR_DETECTION`

### Step 1 — Handler completion
- `GestureHandler` expanded to full 4-type gesture support (double/triple/long/swipe)
- `AncModeHandler` rewritten — added cancel_level / awareness_level / dynamic support
- `StateInEarHandler` — new (aligns upstream state_in_ear.py)
- `PowerButtonHandler` — new (aligns upstream action_power_button.py)
- `DualConnectHandler` upgraded from read-only to full enum + 7 operations
- DeviceState expanded: tripleTap, longTap split, swipeGesture, earWorn, powerButton

### Step 2 — Full bilingual UI
- `strings.xml` (en/zh) fully completed — all UI strings, gestures, ANC levels, permissions
- All UI components use `stringResource`, zero hardcoded English

### Step 3 — Permission system rewrite
- Fully check all runtime permissions in MainActivity
- Added `POST_NOTIFICATIONS` (Android 13+), `ACCESS_FINE_LOCATION` (scan) checks
- User-friendly guidance text and settings redirect

### Step 4 — UI adjustments
- Connected area shows signal strength / protocol version / latency
- Settings bottom: contributor info, version, disclaimer
- App icon added (user-provided image, adaptive)
- All hardcoded strings replaced with `stringResource`

### Step 5 — Logging enhancements
- Added `[ERROR] [WARN] [INFO] [DEBUG]` tag prefixes
- Expanded log scope: Bluetooth events, handler state, write command confirmations
- Daily log file rotation

### Step 6 — Background keep-alive
- Connection health heartbeat (every 30s ping)
- Exponential backoff retry (1s-2s-4s-8s-15s max)
- CompanionDeviceManager for auto-reconnect (Android 8+)
- Battery optimization ignore guide

### Step 7 — Clear data + version bump
- Add "Clear Data" button (wipes DataStore + logs, then restart)
- Version `1.0-2-2` → `1.3.0` (major MINOR bump)
- Version unified across `build.gradle.kts`, `SettingsScreen`, `README.md`

### Step 8 — Docs + Release
- `README.md` updated
- `DEVELOPMENT_LOG.md` updated
- Compilation: zero errors, zero warnings
- Created v1.3.0 Pre-release, uploaded APK
- Files changed: ~30+ files
✅ completed

---

## 2026-06-20

**v1.3.0-beta release: README description update + beta version marker**

- Updated README.md version from v1.2.2 to v1.3.0-beta
- Confirmed `build.gradle.kts` versionName = "1.3.0-beta"
- Compiled release APK (beta)
- Created GitHub Release v1.3.0-beta (marked as beta)
✅ completed
