# Development History

## 2026-06-19 01:40
- Step: Initialize project structure — rename namespace, update build configs, create core package skeleton
- Files changed: settings.gradle.kts, app/build.gradle.kts, gradle/libs.versions.toml, AndroidManifest.xml, PROJECT_CORE.md, DEVELOPMENT_LOG.md
- Status: completed

## 2026-06-19 02:05
- Step: Bluetooth protocol layer + device management + basic UI component
- Files changed: SppCommand.kt, SppPackage.kt, Crc16.kt, SppClient.kt, Handler.kt, BatteryHandler.kt, AncModeHandler.kt, LowLatencyHandler.kt, SoundQualityHandler.kt, DeviceState.kt, DeviceProfile.kt, DeviceManager.kt, FreeBudsApp.kt, Theme.kt, BatteryCard.kt
- Status: completed

## 2026-06-19 02:15
- Step: Data persistence + navigation + full UI screens (MainScreen, SettingsScreen) + MainActivity entry
- Create PreferencesRepository (DataStore: blur_style, dark_mode, last_device_address, auto_connect)
- Create Navigation (NavHost with Main + Settings routes)
- Create MainScreen: ConnectionCard (Bluetooth status, connect/disconnect, device picker dialog), BatteryCard integration, QuickControlsCard (ANC segmented buttons, Low Latency switch, Sound Quality segmented buttons)
- Create SettingsScreen: Appearance (Visual Effect frosted/liquid/none radio buttons, Theme system/dark/light radio buttons), Connection (Auto Connect switch), About (version, device profile)
- Create BlurToggleCard: Haze effect wrapper with TODO integration points (frosted glass → HazeBlurDefaults.mgLarge(), liquid glass → LiquidGlassVisualEffect(), none → pass-through)
- Create MainActivity: edge-to-edge, auto-connect on launch via lifecycleScope, clean up DeviceManager on destroy
- Files changed: PreferencesRepository.kt, Navigation.kt, MainScreen.kt, SettingsScreen.kt, BlurToggleCard.kt, MainActivity.kt
- Status: completed

## 2026-06-19 02:30
- Step: Game mode Fixed On + Quick Settings Tile for ANC + Status Notification with listening time
- Low Latency changed from Switch to tri-state SegmentedButton: Off / On / Fixed On
  - Fixed On persists in DataStore (lowLatencyAutoOn)
  - DeviceManager auto-sends low_latency=true on every connection when Fixed On
  - "Fixed On" button shows selected state via isLowLatencyFixed
- Created AncQuickTileService: Quick Settings Tile, cycles ANC mode Off→Cancellation→Awareness on tap
  - Tile label shows current ANC mode, unavailable when disconnected
  - Registers in Manifest with BIND_QUICK_SETTINGS_TILE permission
- Created StatusNotificationService: Foreground service with persistent notification
  - Shows device name, ANC mode, battery (L/R/Case), listening time (hours/minutes/seconds)
  - Listening time accumulates across connect/disconnect cycles
  - Tap notification opens MainActivity
- Created notification channel CHANNEL_STATUS (freebuds_status)
- Added SettingsScreen toggles for "Low Latency Fixed On" and "Status Notification" (start/stop foreground service)
- Added permissions: FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE, POST_NOTIFICATIONS
- Added service declarations in AndroidManifest: AncQuickTileService + StatusNotificationService
- Files changed: FreeBudsApp.kt, DeviceManager.kt, PreferencesRepository.kt, MainScreen.kt, SettingsScreen.kt, AncQuickTileService.kt (new), StatusNotificationService.kt (new), AndroidManifest.xml
- Status: completed

## 2026-06-19 03:00 — Stage 1-4 Execution Complete
- Step: P0 Bug fix (duplicate DeviceManager, events.collect blocking) + Stage 2 (BlurToggleCard with Compose built-in blur/gradient) + Stage 3 (4 new Handlers: EqPreset, Gesture, DeviceInfo, DualConnect) + Stage 4 (DeviceState 24 fields, MainScreen UI: EQ card, Gesture card, DualConnect card, DeviceInfo card)
- Files changed: MainActivity.kt, DeviceManager.kt, DeviceState.kt, FreeBudsApp.kt, EqPresetHandler.kt (new), GestureHandler.kt (new), DeviceInfoHandler.kt (new), DualConnectHandler.kt (new), BlurToggleCard.kt, MainScreen.kt, DEVELOPMENT_LOG.md
- Status: completed — ready for compile verification

## 2026-06-19 04:00 — Bug Fix: Connection No Feedback + Debug Log Toggle
- Step: Fix `DeviceManager.connect()` never sets CONNECTING state; fail paths don't propagate to UI
- Root cause: `_connectionState` jumped from DISCONNECTED directly to CONNECTED; UI `isConnecting` was always false
- Fix: `connect()` sets `_connectionState = CONNECTING` at entry; `ConnectionEvent.Error` no longer `throw` in eventJob (catastrophic uncaught crash); fail paths throw `RuntimeException` caught by `MainScreen.try-catch` → Snackbar
- Added full debug logging system: `PreferencesRepository.DEBUG_LOG` + `debugLog` Flow + `setDebugLog()`; `FreeBudsApp.onCreate` syncs to `SppClient.logEnabled`; Logcat output for SPP connect steps
- Files changed: DeviceManager.kt, SppClient.kt, FreeBudsApp.kt, PreferencesRepository.kt, MainScreen.kt, SettingsScreen.kt, strings.xml (en+zh)
- Status: completed — build successful

## 2026-06-19 04:15 — Bug Fix: SecurityException on DevicePicker + App Rename
- Step: Fix crash when opening DevicePickerDialog after previous fixes
- Root cause: `DeviceManager.findPairedDevices()` calls `BluetoothAdapter.getBondedDevices()` which requires `BLUETOOTH_CONNECT` permission at runtime on Android 12+; Compose recomposition calls it even when permission check fails
- Fix: Wrap `findPairedDevices()` in `try-catch (SecurityException)` → returns empty list
- App renamed to "fxxkHilife" (both en/zh strings); version changed to "1.0-2-1"
- Files changed: DeviceManager.kt, app/build.gradle.kts, strings.xml (en+zh)
- Status: completed — build successful

## 2026-06-19 04:30 — Bug Fix: Repeated Clicks Crash (Concurrent Connect + Uncaught Coroutine Exception) + DebugLogger Dual-Path + Share Logs
- Step: Fix app crash/freeze after repeated button clicks; add file-based logging + share functionality
- Root cause 1: Multiple rapid `connect()` calls race; old connection cleanup races with new connection
- Fix 1: `DeviceManager` now uses `Mutex.withLock` to serialize connects; early-return if already CONNECTING; `eventJob` caught with `try-catch` (no more `throw` inside collect — was causing uncaught coroutine crash that `SupervisorJob` doesn't suppress)
- Root cause 2: Logcat-only logs lost on crash; no way to export logs
- Fix 2: Created `DebugLogger` singleton — dual-path logger (Logcat + rotating file in `cache/logs/`, max 1MB per file, keeps last 3); controlled by same `debugLog` preference; `FreeBugsApp.onCreate` calls `DebugLogger.init()`
- Added `FileProvider` for secure log file sharing; `file_paths.xml` points to `cache/logs/`; `SettingsScreen.Debug` card now has "Share Logs" icon button
- All `DeviceManager` and `SppClient` `Log.*(TAG` calls replaced with `DebugLogger.*(TAG` (both Logcat + file)
- Files changed: DeviceManager.kt (Mutex + eventJob fix + DebugLogger), SppClient.kt (remove android.util.Log, use DebugLogger), DebugLogger.kt (new), FreeBudsApp.kt (init DebugLogger, sync to DebugLogger), AndroidManifest.xml (FileProvider), res/xml/file_paths.xml (new), SettingsScreen.kt (Share button, context, import)
- Status: completed — ready for compile verification

## 2026-06-19 05:30 — Connection State Persistence + UI Polish
- Step: Fix disconnect UI — device name now gray-scale kept instead of disappearing; add reconnect button when last device known
- Root cause: `DeviceManager.disconnect()` reset `_state.value = DeviceState()`, wiping all device info; `ConnectionCard` had no `lastDeviceName` fallback
- Fix:
  - `DeviceState` added `lastDeviceName: String?` and `lastDeviceAddress: String?` (persisted across disconnect)
  - `DeviceManager.connect()` saves `lastDeviceName` on `ConnectionEvent.Connected`
  - `DeviceManager.disconnect()` no longer resets to `DeviceState()` — clears only live fields (battery, ANC, EQ, etc.), preserves `lastDevice` info
  - Added `DeviceManager.resetState()` for full cleanup
  - `ConnectionCard` now shows "Disconnected: [device name]" in gray (0.6f alpha) when `lastDeviceName` is set
  - Shows last device address in gray subtitle; button changes to "Reconnect" icon when last device known
  - Added `Color` import for `Color.UNSPECIFIED` usage
- Files changed: DeviceState.kt (lastDeviceName/Address fields), DeviceManager.kt (connect save + disconnect preserve + resetState), MainScreen.kt (ConnectionCard gray fallback + reconnect button)
- Status: completed — compiled successfully

## 2026-06-19 12:10 — Bug Fix: Connect Drops Immediately + Reconnect No-Op + Button Bounce + Desktop Label + Docs
- Step: Fix multiple P0 crashes and UX issues after compiled APK testing

**Root causes:**
1. `connect()` unconditionally called `disconnect()` at entry → killed the freshly established SPP socket → `eventJob cancelled (normal)` → app appeared connected then immediately dropped
2. `Disconnected`/`Error` events only set `_connectionState` but not `_state.connected=false` → UI kept showing connected cards
3. `Reconnect` button just opened device picker instead of directly reconnecting last device
4. All buttons (settings, connect, disconnect, device picker) had no debounce → rapid clicks caused concurrent connects → `Mutex` race + crash
5. Launcher icon showed internal name instead of "fxxkHilife" — missing `android:label` on `<application>`

**Fixes:**
- `DeviceManager.connect()` — added early return if already CONNECTED to same device (by `lastConnectedDevice.address`); saves `lastConnectedDevice` on entry; skips `disconnect()` when already connected
- `DeviceManager` eventJob — `Disconnected`/`Error` handlers now also set `_state.value = _state.value.copy(connected = false)` so UI cards hide immediately
- `MainScreen.ConnectionCard.onConnectClick` — if `deviceManager.lastConnectedDevice != null` → directly calls `deviceManager.connect(last)`; otherwise opens device picker
- `MainScreen` — added `btnLock` state variable; settings icon, connect button, disconnect button, device picker selection all guarded by `if (!btnLock) { btnLock = true; ... delay(500); btnLock = false }` → no more rapid-click crashes
- `AndroidManifest.xml` — added `android:label="@string/app_name"` to `<application>` tag → launcher icon now shows "fxxkHilife"
- `PROJECT_CORE.md` — added Software Identity section (app name `fxxkHilife`, package name) and Version Numbering Rule (`MAJOR.MINOR-fixN` with table and examples)
- Files changed: DeviceManager.kt (connect early-return + lastConnectedDevice + Disconnected/Error state reset), MainScreen.kt (reconnect direct + btnLock debounce), AndroidManifest.xml (application label), PROJECT_CORE.md (identity + versioning rules)
- Status: completed
