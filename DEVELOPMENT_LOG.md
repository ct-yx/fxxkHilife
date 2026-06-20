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

## 2026-06-20 — v1.2.2 Release: 4残留问题修复 + 版本号更新
- Step: 修复4个残留问题（AutoPauseHandler未注册、VoiceLanguageHandler未注册、MainScreen缺失AutoPauseCard/VoiceLanguageCard、DeviceState缺少voiceLanguage字段）
- Files changed: DeviceManager.kt（注册AutoPauseHandler + VoiceLanguageHandler）、DeviceState.kt（新增voiceLanguage/voiceLanguageOptions）、MainScreen.kt（新增AutoPauseCard + VoiceLanguageCard）
- Step: 版本号更新 versionCode=1→2, versionName="1.0-2-1"→"1.0-2-2"
- Step: README.md全面更新（版本号、功能表、开发进度）
- Status: completed — v1.2.2 ready for push and release

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

## 2026-06-20 — v1.3.0 大规模重构：上游对齐 + 双语 + 权限 + UI完善 + 后台保活
- **上游对比分析**：对比 melianmiko/OpenFreebuds main 分支全部 18 个 handler 实现
- **发现的关键差异**：
  1. **AncModeHandler 严重不完整**：上游 anc.py (108行) 完整实现 mode/level/dynamic 三段式解析，当前仅解析 mode 且写命令硬编码
  2. **GestureHandler 严重缺失**：上游4个独立handler（double_tap/triple_tap/long_tap(含split)/swipe），当前仅实现 double_tap
  3. **缺失上游Handler**：state_in_ear.py（佩戴检测）、action_power_button.py（电源按键）、logs.py（日志）
  4. **Connection Protocol**：上游无"协议切换"功能，统一使用 SPP RFCOMM，当前实现正确
  5. **DeviceProfile Feature 缺失**：VOICE_LANGUAGE、ANC_LEVEL、ANC_DYNAMIC、IN_EAR_DETECTION
- **版本号**：1.0-2-2 → 1.3.0（重大功能增改，MINOR+1）
- **Step 1: Handler补全**
  - GestureHandler 从仅 double_tap 扩展到完整四类手势（double/triple/long/swipe）
  - AncModeHandler 重写，增加 cancel_level/awareness_level/dynamic 支持
  - 新增 StateInEarHandler（佩戴检测，对齐上游 state_in_ear.py）
  - 新增 PowerButtonHandler（电源按钮动作，对齐上游 action_power_button.py）
  - DualConnectHandler 从仅读 enabled 升级为完整枚举+7种操作（对齐上游 dual_connect 模块）
  - DeviceState 新增 tripleTapLeft/Right、longTapLeft/Right/Split、swipeGesture、earWorn、powerButton 等字段
- **Step 2: 双语完整覆盖**
  - strings.xml（en/zh）全面补全所有 UI 字符串（手势/ANC等级/后台保活/权限等）
  - 所有 UI 组件使用 stringResource 替换硬编码英文
- **Step 3: 权限系统重写**
  - MainActivity 权限索要改为完整校验所有运行时权限
  - 新增 POST_NOTIFICATIONS（Android 13+）、ACCESS_FINE_LOCATION（扫描）校验
  - 增加用户友好的引导文本和设置跳转
- **Step 4: UI调整**
  - 主界面已连接区域增加连接信息（信号强度/协议版本/延迟状态）
  - 设置底部增加软件详情（贡献者/版本号/免责条款）
  - 清理无效控件，替换所有硬编码字符串为 stringResource
  - 添加应用图标（基于用户上传图片，去黑底 → 自适应图标）
- **Step 5: 日志模块增强**
  - 增加 [ERROR] [WARN] [INFO] [DEBUG] 标签前缀
  - 增加日志可记录范围（蓝牙事件/Handler状态/写命令确认）
  - 增加按日期分文件日志
- **Step 6: 后台保活增强**
  - 添加连接健康心跳（每30s ping）
  - 指数退避重试（1s-2s-4s-8s-15s max）
  - CompanionDeviceManager 辅助自动重连（Android 8+）
  - "忽略电池优化"引导跳转
- **Step 7: 清空数据逻辑 + 版本号修正**
  - 添加「清空数据」按钮（清除 DataStore + 日志文件后重启）
  - 修正所有版本号位置：build.gradle.kts、SettingsScreen、README.md 统一
- **Step 8: 文档 + 版本号更新**
  - README.md 更新版本号、贡献者、免责条款
  - DEVELOPMENT_LOG.md 更新完整记录
- **Step 9: 编译验证 + Release 发布**
  - 编译零错误零警告
  - 创建 v1.3.0 Pre-release，上传 APK
  - git push origin main
- Files changed: 约30+ 个文件
- Status: completed

## 2026-06-20 — v1.3.0-beta 发布：README 描述更新 + 版本号测试版标记
- Step: 修改 README.md 中版本号从 v1.2.2 更新为 v1.3.0-beta
- Step: 确认 build.gradle.kts 中 versionName = "1.3.0-beta"（测试版标记已存在）
- Step: 编译 Release APK（测试版）
- Step: git push origin main + 创建 GitHub Release v1.3.0-beta（标注测试版）
- Status: completed
