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

---

## 2026-06-20

**v1.2.4 Release — Full 6-zone code review against upstream + protocol alignment + gesture complete + keep-alive + permission UX**

### Step 1 — App icon
- Removed black background from user-provided image, generated full mipmap density set (mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi)
- Updated `AndroidManifest.xml` to use `@mipmap/ic_launcher`

### Step 2 — Full 6-zone code review against upstream OpenFreebuds
- **Zone 1 (bluetooth/)**: 3 fixes — SppCommand.kt ANC command ID P0 Bug (0x01,0x09/0x0A → 0x2B,0x2A/0x2B,0x04), BatteryHandler.kt charging state param, SoundQualityHandler.kt param position (1→2)
- **Zone 2 (device/)**: 12 files checked, 0 changes needed ✓
- **Zone 3 (ui/)**: 3 fixes — BatteryCard.kt charging state display, MainScreen.kt pass charging params, SettingsScreen.kt hardcoded version → BuildConfig.VERSION_NAME
- **Zone 4 (service/)**: 2 files checked, 0 changes needed ✓
- **Zone 5 (data/)**: 1 file checked, 0 changes needed ✓
- **Zone 6 (util/ + root)**: 5 files checked, 0 changes needed ✓, CRC-16/XMODEM confirmed identical with upstream
- **Total**: 33 files reviewed, 9 code changes (2 P0 fixes, 1 P1 fix, 6 P2 improvements)

### Step 3 — Protocol alignment & ANC fix
- ANC level mapping corrected (awareness normal/voice_boost order, dynamic mode)
- All gesture protocol parameters fully matching upstream triple-tap (0x01,0x26), long-press (0x2B,0x17), swipe (0x2B,0x1F)
- `setProperty` protocol consistency verification added before/after write
- ANC 3-mode (noise canceling / off / awareness) payload construction aligned, Awareness level mapping fixed

### Step 4 — Background keep-alive enhancement
- Created `BackgroundKeepAliveWorker.kt` (WorkManager PeriodicWorkRequest, 15min interval, auto-reconnect on disconnect)
- Scheduled in `FreeBudsApp.onCreate()`
- Added CompanionDeviceManager assisted auto-reconnect (Android 8+)
- Added "Ignore battery optimization" guidance (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS intent)
- Added WorkManager dependency to build.gradle.kts and libs.versions.toml

### Step 5 — Permission UX improvement
- Added Chinese/English Toast for each permission denial (BLUETOOTH_CONNECT, BLUETOOTH_SCAN, ACCESS_FINE_LOCATION, POST_NOTIFICATIONS)
- AlertDialog → Settings.ACTION_APPLICATION_DETAILS_SETTINGS after 3+ denials or explicit "Deny"
- All prompt text added to strings.xml (zh/en)

### Step 6 — Gesture completion
- `GestureHandler.kt` rewritten: all 4 gesture types (double tap, triple tap, long press, swipe) fully implemented
- Independent change event registration per gesture type
- Cache + `applyToState` refresh
- `setProperty` write fully aligned with upstream protocol
- `DeviceState` expanded: tripleTapLeft/Right, swipeLeft/Right, longPressLeft/Right + all action options
- `GestureCard` UI extended with full 4-type layout in MainScreen

### Step 7 — Version bump & docs
- `build.gradle.kts`: versionCode **3→4**, versionName **"1.3.0-beta"→"v1.2.4"**
- `SettingsScreen.kt`: hardcoded "1.0.0" → BuildConfig.VERSION_NAME
- `README.md` / `README_EN.md` / `DEVELOPMENT_LOG.md` fully updated
- **Files changed**: ~18+ files
✅ completed

---

## 2026-06-20

**v1.5.2 Release — 版本统一合并: 整合 v1.2.4 (代码) + v1.3.0-beta (功能) 历史，统一升级为 v1.5.2**

### 版本混乱背景
- GitHub 上存在 v1.2.4 和 v1.3.0-beta 两个版本，但 git 历史为线性关系（无分叉）
- **v1.3.0-beta (348b73c)** 是 **v1.2.4 (1521469)** 的祖先提交
- v1.2.4 的代码实际更全（6 区上游对齐、手势完善、保活增强、权限 UX 优化）
- 版本号混乱源于维护过程中版本命名不一致

### 版本合并策略
- **保留 v1.2.4 代码**（功能最完整的版本）作为基础
- **统一版本号升级至 v1.5.2**（跳过冲突版本号，全新里程碑）
- 删除远程 Git tag: `v1.3.0-beta`，保留 `v1.2.2` / `v1.2.3` / `v1.2.4` 作为历史记录

### 更改内容
- `app/build.gradle.kts`: versionCode **4→5**, versionName **"v1.2.4"→"v1.5.2"**
- `README.md`: 当前版本 **v1.2.4** → **v1.5.2**（维护版本 → 稳定版本）
- `README_EN.md`: Current version **v1.2.4** → **v1.5.2**（maintenance → stable）
- `DEVELOPMENT_LOG.md`: 添加本条记录
- GitHub 远程同步：更新 tag 和默认分支
- **Files changed**: 3+ files
✅ completed

---

## 2026-06-20

**CI 修复: Gradle wrapper 指向本地路径，修复 GitHub Actions 构建失败**

### 根因
`gradle-wrapper.properties` 中 distributionUrl 指向本地 Termux 文件路径，CI runner 上无法访问

### 修复
distributionUrl 从 `file:///root/gradle/gradle-9.1.0-bin.zip` 改为官方 Gradle URL

### 关联改动
- 放弃本地编译，全面迁移至 GitHub Actions 云端编译
- 创建 `.github/workflows/android-build.yml` — push/PR 自动编译
- 创建 `.github/workflows/release.yml` — 打 tag 自动 Release
- **Files changed**: gradle-wrapper.properties
✅ completed
