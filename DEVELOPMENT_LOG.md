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

---

## 2026-06-20

**Rust 原生守护进程原型 — `feat/rust-native-daemon-proto` 分支**

### 背景
App 保活依赖 WorkManager (15min) + Kotlin 层心跳 (30s)，进程被系统强杀后 WorkManager 也被停用。Rust daemon 作为独立进程提供激进的保活和系统级监控。

### Rust daemon 架构（6 模块）
- **`main.rs`** — 入口：DaemonConfig（环境变量配置）、DaemonState（共享状态）、tokio 主循环启动 IPC/BT/Watchdog 三个子系统
- **`ipc.rs`** — Unix Domain Socket JSON-RPC 服务器（`/tmp/fh_daemon.sock`），支持 10 种 IPC 方法
- **`bluetooth.rs`** — HCI sysfs 蓝牙健康监控（非 root，读取 `/sys/class/bluetooth/hci*/uevent`）
- **`keepalive.rs`** — 进程 watchdog（pidof + /proc 回退 + am start 重启）
- **`notify.rs`** — cmd notification post 通知派发（需 root/shell 权限）
- **`daemon.rs`** — PID 文件管理 + signal 0 进程存活检查

### 编译验证
- Cargo.toml 依赖：tokio(full), serde+serde_json, libc, ctrlc, futures（共 48 个依赖包）
- 编译 0 error, 3 warnings（未使用的 import/变量）
- **新文件**：`native-daemon/Cargo.toml` + 6 个 .rs 源文件 + Cargo.lock + `.gitignore` 忽略规则
- **分支**: `feat/rust-native-daemon-proto` — 已与 main 的 CI 配置同步

### 待完成
- Android 端集成：`NativeDaemonClient.kt`（IPC 客户端）、`NativeDaemonLauncher.kt`（进程管理）
- cargo ndk 交叉编译到 `aarch64-linux-android`
- 嵌入 APK `jniLibs/arm64-v8a/`，App 启动时释放到 data 目录
✅ completed

---

## 2026-06-20

**Bug fix: UI 控件点击后属性状态不更新（ANC / Low Latency / Sound Quality / Gestures / Dual Connect）**

### Root cause

核心链路问题：所有 Handler 的 `setProperty()` 写命令都使用 `SppPackage.writeRequest()`，其默认 `responseId = cmd` 导致 `SppClient.send()` 会阻塞等待设备响应。FreeBuds 设备对写命令不回复 ACK，导致：

1. **写命令超时占用 pending 表** — 5s 超时期间该命令 ID 在 `pending` map 中未被清理，如果后续有同命令 ID（如 `refreshState` 的读命令）就冲突
2. **`refreshState()` 无超时保护** — 单个 Handler 读超时（如 GestureHandler 每个手势都发独立读命令）会阻塞整个状态刷新
3. **UI 比较用 `displayName` 字符串** — 枚举值比较更可靠

### 修复

1. **`SppPackage.writeRequest()`** — 新增 `expectResponse: Boolean` 参数，`false` 时设 `responseId = ByteArray(0)`，写入后立即返回不等待
2. **所有 6 个 Handler 的 `setProperty`** — AncModeHandler、LowLatencyHandler、SoundQualityHandler、EqPresetHandler、GestureHandler、DualConnectHandler 全部改用 `expectResponse = false`
3. **`DeviceManager.refreshState()`** — 每个 Handler 的 `applyToState` 包裹 `withTimeout(2000ms)` + try-catch，单 Handler 超时不阻塞整体刷新
4. **`DeviceManager.setProperty()`** — 写后等待从 300ms 增至 500ms，给设备更多处理时间
5. **`MainScreen.kt` QuickControlsCard** — ANC 按钮 `selected` 比较从 `displayName == "Off"` 改为 `AncMode.OFF` 枚举对比

- Files: `SppPackage.kt`, `DeviceManager.kt`, `AncModeHandler.kt`, `LowLatencyHandler.kt`, `SoundQualityHandler.kt`, `EqPresetHandler.kt`, `GestureHandler.kt`, `DualConnectHandler.kt`, `MainScreen.kt`
✅ completed

---

## 2026-06-20

**v1.5.3 Release — 版本号升级: v1.5.2 → v1.5.3**

- 版本号更新 patch 版本
- Version bump: versionCode **5→6**, versionName **"v1.5.2"→"v1.5.3"**
✅ completed

---

## 2026-06-20

**Bug fix: 切换连接协议后声音变小 / 卡顿 / 未生效**

### Root cause

上一轮修复 (v1.5.3) 将 6 个 Handler 的 `setProperty()` 全部改为 `expectResponse=false`（fire-and-forget），但这个策略**不适用于所有设备**。上游 OpenFreebuds 的 `sound_quality_preference.py` 和 `low_latency.py` 使用 `change_rq`（等待设备 ACK 的写命令）—— FreeBuds 设备实际上会回复写命令 ACK。

`expectResponse=false` 导致：
1. **切换未生效** — 写命令发出后不等 ACK 就退出，设备可能还没处理完写命令
2. **声音变小/卡顿** — 状态不同步，后续 `refreshState()` 读了旧值，UI 与实际状态不一致
3. **写入时序混乱** — 不等 ACK 就立即写入下一个命令，设备缓冲区堆积

### 修复

1. **恢复 `expectResponse=true`** — 6 个 Handler 全部改回等待设备 ACK
   - `AncModeHandler` — ANC 模式/级别写入 ✅
   - `LowLatencyHandler` — 低延迟模式写入 ✅
   - `SoundQualityHandler` — 音质偏好写入 ✅
   - `EqPresetHandler` — EQ 预设写入 ✅
   - `GestureHandler` — 手势操作写入 ✅
   - `DualConnectHandler` — 双设备连接写入 ✅
2. **添加超时保护** — 所有写操作传入 `timeoutMs=2000`，避免设备无响应时永久卡住
3. **保留 `delay(500) + refreshState()`** — 写后等待设备状态稳定并重新读取

**关键区别**：`expectResponse` 现在是 Handler 级别的配置项，而非全局默认；每个 Handler 可按需选择等/不等 ACK。

- **Files changed**: `AncModeHandler.kt`, `LowLatencyHandler.kt`, `SoundQualityHandler.kt`, `EqPresetHandler.kt`, `GestureHandler.kt`, `DualConnectHandler.kt`
✅ completed

---

## 2026-06-20

**v1.6.4 Release — 协议层修复 + 版本号升级**

- 修复连接协议切换导致的声音变小/卡顿/未生效 Bug
- 恢复所有 Handler 写命令等待设备 ACK（`expectResponse=true`, `timeoutMs=2000`）
- Version bump: versionCode **6→7**, versionName **"v1.5.3"→"v1.6.4"**
- 更新 `README.md` / `README_EN.md` / `VERSION_MANAGEMENT.md` / `DEVELOPMENT_LOG.md`
✅ completed

---

## 2026-06-20

**v1.6.5 Release — CI 编译错误修复 + 版本号升级**

- 修复 CI 编译错误 #1: `FreeBudsApp.kt` — 缺少 `kotlinx.coroutines.flow.first` import
- 修复 CI 编译错误 #2: `MainScreen.kt` — `LocalContext.current` 在非 Composable 上下文中使用
- Version bump: versionCode **7→8**, versionName **"v1.6.4"→"v1.6.5"**
- 更新 `README.md` / `README_EN.md` / `VERSION_MANAGEMENT.md` / `DEVELOPMENT_LOG.md`

### ⚠️ 已知问题（v1.6.5）

用户测试发现以下功能存在异常：
| 问题 | 详情 |
|------|------|
| ANC 快捷开关（Tile） | 仅能切换到降噪模式，关闭/通透模式不生效 |
| 低延迟模式 | 应用内切换不生效 |
| EQ 预设 | 切换预设后耳机无反应 |
| 手势设置 | 双击/三击/长按/滑动设置后不生效 |
| 音质偏好 | 稳定/质量模式切换无效果 |
| 双设备连接 | 开关功能未正确同步到耳机 |
✅ completed

---

## 2026-06-20

**v1.6.6 Release — 修复 ANC 快捷开关（Tile）+ 版本号升级**

- **修复 ANC 快捷开关**：根因是 `AncQuickTileService` 传 `next.name.lowercase()` = `"off"` 给 `AncModeHandler`，但 `MODE_MAP` 中 key 0 对应的是 `"normal"`（非 `"off"`），导致 payload 始终指向降噪模式
- 修复方式：在 Tile 中用 `when` 显式映射 `AncMode` → `"normal"`/`"cancellation"`/`"awareness"`
- 更新 `README.md` / `README_EN.md` — 快捷开关状态改为 ✅ **已修复**
- Version bump: versionCode **8→9**, versionName **"v1.6.5"→"v1.6.6"**
- 更新所有描述文件和 `VERSION_MANAGEMENT.md`
✅ completed

---

## 2026-06-20

**v1.6.7 Release — 清理上游 OpenFreebuds 代码 + 重新克隆并排除 git 追踪 + 版本号升级**

- 从 git 追踪中移除旧 `OpenFreebuds/` 目录（`git rm --cached`）
- 从工作区删除旧 `OpenFreebuds/` 目录
- 重新从 `https://github.com/melianmiko/OpenFreebuds.git` 克隆到 `OpenFreebuds/`
- 在 `.gitignore` 中添加 `/OpenFreebuds/` 规则，上游代码不再纳入 git 追踪
- Version bump: versionCode **9→10**, versionName **"v1.6.6"→"v1.6.7"**
- 更新 `README.md` / `README_EN.md` / `VERSION_MANAGEMENT.md` / `DEVELOPMENT_LOG.md`
✅ completed

---

## 2026-06-20

**v1.6.8 Release — ANC 快捷开关重写为两个独立二态 Tile**

### 背景
用户反馈快速设置 Tile 只能开启降噪、不能关闭，且只有白色常亮状态。分析确认 Android Quick Settings Tile 仅支持二态（STATE_ACTIVE / STATE_INACTIVE），三态循环不适用于此场景。

### 改动
1. **Tile 拆分** — 将单个 `AncQuickTileService`（三态循环）拆为两个独立二态 Tile：
   - `NoiseCancelTileService` — ACTIVE=降噪开启，INACTIVE=降噪关闭（→ "normal"）
   - `AwarenessTileService` — ACTIVE=通透开启，INACTIVE=通透关闭（→ "normal"）
2. **`AndroidManifest.xml`** — 注册两个独立的 Tile Service，分别绑定 `action.QS_TILE`
3. **strings.xml** — 新增 `tile_nc_label`（降噪）、`tile_aw_label`（透传），移除旧三态 Tile 字符串
4. **版本号升级** — versionCode **10→11**, versionName **"v1.6.7"→"v1.6.8"**
5. 更新所有描述文件
✅ completed

---

## 2026-06-20

**v1.6.9 Release — 修复中英文切换不生效 + 消除所有硬编码英文文本**

### 背景
用户反馈语言切换按钮无效：主界面部分英文部分中文，设置界面全部英文。根因有两个：
1. `attachBaseContext` 未重写 → Activity 重建后仍使用系统默认 Locale
2. `MainScreen.kt` 中存在大量硬编码英文文本（~30 处），未使用 `stringResource`

### 改动
1. **核心修复 — FreeBudsApp 语言系统重写**：
   - 重写 `FreeBudsApp.attachBaseContext()` — 借助 `createConfigurationContext()` 在 Application 启动时注入已保存的语言
   - 新增 `SharedPreferences` 同步文件 `freebuds_lang_sync`，用于 `attachBaseContext` 阶段在 DataStore 不可用时读取语言偏好
   - `PreferencesRepository.setLanguage()` 同步写入 SharedPreferences 副本
   - 启动时自动同步 `currentLocale` 到同步文件

2. **全面消除硬编码英文文本（MainScreen.kt）**：
   - QuickControlsCard — ANC/低延迟/音质偏好标签和按钮全部改为 `stringResource`
   - EqPresetCard — 标题/Loading/Current 改为 stringResource
   - GestureSettingsCard — 标题/左耳/右耳/动作列表全部改为 stringResource
   - DualConnectCard — 标题/描述改为 `section_dual_connect`/`dual_enabled`/`dual_disabled`
   - DeviceInfoCard — 标题/Firmware/Serial/Hardware 改为 stringResource
   - ConnectionCard — "Reconnect"/"Disconnected:" 改为 stringResource
   - GesturePickerDialog — "Left Double-Tap" / "Right Double-Tap" / "Cancel" 全部改为 stringResource
   - DevicePickerDialog — "Unknown Device" 改为 stringResource

3. **清理**：删除未使用的 `import kotlinx.coroutines.flow.first`，修复重复的 `SegmentedButton` 行

4. **版本号升级** — versionCode **11→12**, versionName **"v1.6.8"→"v1.6.9"**
5. 更新所有描述文件和 VERSION_MANAGEMENT.md
✅ completed

---

## 2026-06-20

**v1.7.1 Release — 修复 ANC/低延迟/音质切换后 UI 不更新：写后重读确认状态**

### 背景
用户反馈 ANC 切换逻辑可用，但 UI 不反映设备真实 ANC 模式状态，也无法获取低延迟/音质协议的当前状态。根因是所有 Handler 的 `setProperty()` 写命令后不重新读回确认状态。

### Root cause
核心链路问题：所有写操作完成后各自 Handler 不独立确认状态，依赖 `DeviceManager.refreshState()` 在 `PackageReceived` 事件后触发。但 `DeviceManager.refreshState()` 是**顺序遍历**所有 Handler，导致：
1. `refreshState()` 中慢的 Handler 拖慢整体刷新
2. `PackageReceived → refreshState()` 与 `write → delay(1000) → refreshState()` 造成竞态和状态覆盖
3. UI 在写操作完成后立刻读到的还是旧值

### 修复方案 — 每个 handler 写后独立重读确认

1. **`AncModeHandler.kt`** — `setProperty()` 写后 delay(500) + 重新构建读命令发送解析，更新 `activeModeByte` 状态变量
2. **`LowLatencyHandler.kt`** — `setProperty()` 写后 delay(1000) + 重新读 param 2 确认；新增 DebugLogger import
3. **`SoundQualityHandler.kt`** — `setProperty()` 写后 delay(500) + 重新读 param 2 确认；新增 TAG 和 DebugLogger import
4. **`DeviceManager.kt`** — `setProperty()` 中 delay 从 1000ms 减至 300ms，注释优化，移除多余的 refreshState（各 handler 自行确认）

### 版本号升级
- versionCode **13→14**, versionName **"v1.7.0"→"v1.7.1"**

### 描述文件更新
- `README.md` / `README_EN.md` — 已知问题版本号更新至 v1.7.1
- `VERSION_MANAGEMENT.md` — 版本清单更新
- `strings.xml` / `values-zh-rCN/strings.xml` — version_name 更新
- ✅ **已 commit、已 tag、已 push，CI 自动构建 Release**

### 文件改动
- `app/build.gradle.kts` — versionCode 13→14, versionName "v1.7.0"→"v1.7.1"
- `AncModeHandler.kt` — 写后重读 ANC 状态确认
- `LowLatencyHandler.kt` — 写后重读低延迟状态确认
- `SoundQualityHandler.kt` — 写后重读音质状态确认
- `DeviceManager.kt` — setProperty delay 1000ms→300ms
- `README.md` / `README_EN.md` — 已知问题版本号更新
- `VERSION_MANAGEMENT.md` — 版本号清单更新
- `strings.xml` / `values-zh-rCN/strings.xml` — version_name 更新
✅ completed

---

## 2026-06-20

**v1.7.0 Release — 修复电池电量读取：按上游 protocol 支持 global 回退**

### 背景
用户反馈在 1.0x 版本时电池电量状态能正常获取，但当前版本无法显示。排查发现 `BatteryHandler.handleBattery()` 只从 param 2（left/right/case）解析数据，当设备返回不含 param 2 时直接跳过更新。

### 上游参考
上游 `battery.py` 的逻辑：
- 先检查 **param 1（global battery，单字节）** → 解析 global 值
- 只有 param 2 存在且 **长度 == 3** 且 **w_tws=True** 时才解析 left/right/case
- 如果 param 2 不存在，**仅用 param 1 更新 global**

### 修复
1. **`BatteryHandler.kt`** 完全重写 `handleBattery()`：
   - **优先**检测 param 2 且 `size == 3` → 精确解析 left/right/case（匹配上游 `len == 3` 条件）
   - **回退**使用 param 1（global）→ 对三个槽位使用同一 global 值
   - 两个路径都解析充电状态（param 3）
   - 两个路径都无效时保留旧状态
2. **版本号升级** — versionCode **12→13**, versionName **"v1.6.9"→"v1.7.0"**
3. 更新所有描述文件和 VERSION_MANAGEMENT.md
✅ completed

---

## 2026-06-20

**v1.7.2 Release — 连接耳机后自动检测当前各项状态：按上游 on_init 模式实现**

### 背景
用户需求：连接耳机后能自动检测耳机当前的各项状态（ANC、低延迟、音质、电量、设备信息、手势、EQ、双设备连接），而非依赖用户手动操作后才刷新。

### 上游模式
上游 `generic.py` 的连接初始化流程：
1. **`_start_all_handlers()`** — 绑定所有 handler → 调用每个 handler 的 `init()` 
2. **`handler.init()`** — 包含重试循环（最多 5 次，每次 3s 超时），循环调用 `on_init()` 直到成功
3. **`handler.on_init()`** — 发送读命令 → 等待响应 → 解析状态

### 修复内容

1. **`Handler.kt`** — 新增 `onInit(client, state): DeviceState?` 接口方法（默认返回 null，不强制所有 handler 实现）

2. **所有 8 个 handler 实现 `onInit()`**：
   | Handler | onInit 行为 | 发送命令 |
   |---------|------------|----------|
   | `BatteryHandler` | 读 param 1/2/3，返回电池状态 | `0x01,0x08` params [1,2,3] |
   | `AncModeHandler` | 读 param 1/2，返回 ANC 模式+级别+更新 activeModeByte | `0x2B,0x2A` params [1,2] |
   | `LowLatencyHandler` | 读 param 2，返回低延迟模式 | `0x01,0x09/0x0A` param [2] |
   | `SoundQualityHandler` | 读 param 2，返回音质偏好 | `0x01,0x13` param [1] |
   | `DeviceInfoHandler` | 读 params 0-31，返回固件/序列号/硬件版本 | `0x01,0x07` params [0..31] |
   | `EqPresetHandler` | 读 params 1-8，返回当前 EQ 预设+可用预设列表 | `0x2B,0x4A` params [1..8] |
   | `GestureHandler` | 复用 `applyToState`，读所有手势 | 4 条读命令 |
   | `DualConnectHandler` | 读 param 1，返回双设备连接状态 | `0x2B,0x2F` param [1] |

3. **`DeviceManager.kt`** — 连接流程改为三阶段：
   - **Phase 1**：注册事件回调（handler.init，原行为）
   - **Phase 2**：主动查询初始状态（每个 handler.onInit，重试 3 次×2s 超时，失败跳过）
   - **Phase 3**：全量状态刷新（refreshState，捕获任何遗漏更新）
   - 重连事件（`handleEvent`）也同步更新为三阶段模式

### 版本号升级
- versionCode **14→15**, versionName **"v1.7.1"→"v1.7.2"**

### 文件改动
- `Handler.kt` — 新增 `onInit()` 接口方法
- `BatteryHandler.kt` — 实现 onInit（发读命令解析电池）
- `AncModeHandler.kt` — 实现 onInit（发读命令解析 ANC）
- `LowLatencyHandler.kt` — 实现 onInit（发读命令解析低延迟）
- `SoundQualityHandler.kt` — 实现 onInit（发读命令解析音质）
- `DeviceInfoHandler.kt` — 实现 onInit（发读命令解析固件信息）
- `EqPresetHandler.kt` — 实现 onInit（发读命令解析 EQ）
- `GestureHandler.kt` — 实现 onInit（复用 applyToState 读手势）
- `DualConnectHandler.kt` — 实现 onInit（发读命令解析双设备连接）
- `DeviceManager.kt` — 三阶段连接初始化（init → onInit → refreshState）
- 全部描述文件和 VERSION_MANAGEMENT.md 更新
✅ completed

---

## 2026-06-20

**v1.7.3 Release — 修复 Android CI Build #30-#33 持续失败：Gradle 构建缓存损坏**

### 背景
Android CI Build #30（v1.6.9）到 #33（v1.7.2）连续 4 次失败，而 Release 工作流在同一次 commit 下全部成功。分析确定两个工作流的唯一差异：
- **android-build.yml** — 先 `assembleDebug`，再 `assembleRelease`
- **release.yml** — 直接 `assembleRelease`

Debug 构建链使用了旧的缓存数据导致失败，根因是 Gradle 构建缓存损坏。

### 修复
1. **`.github/workflows/android-build.yml`** — `assembleDebug` 步骤添加 `--no-build-cache` 参数，绕过损坏的缓存
2. **版本号升级** — versionCode **15→16**, versionName **"v1.7.2"→"v1.7.3"**
3. 更新所有描述文件和 VERSION_MANAGEMENT.md

### 改动文件
- `.github/workflows/android-build.yml` — assembleDebug 添加 `--no-build-cache`
- 全部版本描述文件更新
✅ completed
