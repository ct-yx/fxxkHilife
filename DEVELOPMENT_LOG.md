# Development History

---

## 2026-06-26

**项目重构开始 — 之前 v1.7.3 及更早的开发历史已归档**

### 重构目标
- 全新架构设计
- 协议层加固
- 代码质量提升

### 状态变更
- 旧分支重命名为 `main-archived` / `feat/rust-native-daemon-proto-archived`
- 所有开发文件已清理，仅保留描述文件 + 图标，本地编译环境全部清除
- 上游 OpenFreebuds 仓库重新克隆到 `OpenFreebuds/`（git-ignored）
- CI 报错通过 `gh` CLI 获取日志

## v2.0.0-stable.1 (2026-06-27)

### 关键变更：回归 RFCOMM SPP

**背景**：v2.0.0-beta.1 尝试将底层通信从 RFCOMM SPP 替换为 BLE GATT，但该方案基于推测（假设华为耳机使用 BLE GATT 控制通道），未在 OpenFreebuds 上游代码中找到证据支持。

**实际发现**：OpenFreebuds `driver/generic/spp.py` 第 29-33 行使用 Linux 原生 **RFCOMM Socket**（`AF_BLUETOOTH` / `SOCK_STREAM` / `BTPROTO_RFCOMM`），指定 SPP 端口号连接。
- 每个耳机型号在配置中定义 `_spp_service_port`（FreeBuds 6i = 1）
- Android 上对应 `BluetoothDevice.createRfcommSocket()` + `connect()` 反射调用

### 修复内容

**包解析格式修正**（`HuaweiSppPackage.fromBytes`）：
- 魔数必须为 `b"Z\x00"`（`0x5A 0x00`），之前只检查了首字节
- 包体长度改为**单字节**（`data[2]`），之前误用双字节 `data[1:3]`
- 逐行对照上游 `__recv_pacakge` + `from_bytes` 重写

**接收循环修正**（`SppDriver.recvLoop`）：
- 严格按照上游 4 字节包头 + 单字节 body length 协议读取
- 短包（`length < 4`）正确处理丢弃
- 每包完整日志输出（方便调试）

**BatteryHandler 重试机制**：
- `onInit` 无响应时抛出异常，匹配上游 `init()` 的 5 次重试逻辑
- 超时后自动重试 5 次（每次 5s），而非放弃

### 架构变更
| 文件 | 操作 |
|------|------|
| `SppDriver.kt` | 重写 (严格对照 `OfbDriverSppGeneric` + `OfbDriverHuaweiGeneric`) |
| `GattDriver.kt` | 删除 (推测方案，无上游证据) |
| `HuaweiSppPackage.kt` | 修复包解析格式 |
| `BatteryHandler.kt` | 修复重试逻辑 |
| `HuaweiDeviceHandler.kt` | `onInit(driver: GattDriver)` → `SppDriver` |

### 已知问题
- FreeBuds 6i 已确认 SPP 连接成功，电池读取正验证中
- 更多 Handler（ANC、手势等）尚未实现
- SPP 端口号目前硬编码为 1，未来需根据型号动态配置

## v2.1.0 (2026-06-28)

### 协议层精准修复

**收包长度修正**（`SppDriver.recvLoop`）：
- `head[1:3]` 双字节 → `heading[2]` 单字节，匹配上游 `__recv_pacakge`
- 读取 `heading` 后只读 body `length` 字节，去掉多余的 +2（匹配 `await reader.read(length)`）
- 新增 `readFully()` 同步读取辅助，处理 TCP 粘包

**参数解析边界修正**（`HuaweiSppPackage.fromBytes`）：
- 循环终止条件 `pos < len + 4` → `pos < len + 3`，匹配上游 `while position < length + 3`
- 新增参数读写 `pos + 1 >= data.size` 和 `pos + 2 + l > data.size` 边界保护

**Handler 初始化重试**：
- 从单次 5s 超时 → 5 次重试 × 3s 超时（匹配上游 `OfbDriverHandlerHuawei.init`）

### 属性存储系统
- `putProperty`/`getProperty`/`setProperty`——完整的属性存储接口，与上游 `put_property`/`get_property` 对标
- 支持 `(group, prop)` 键值对以及 `extendGroup` 批量写入
- `setProperty` 自动路由到对应 Handler 的 `setProperty()` 方法
- `registerHandler` 注册 `commandIds`、`ignoreCommandIds`、`properties` 全路由
- `packageHandlers` 分发区分 `containsKey`（ 表示精确忽略，不存在表示未注册）

### 15 个上游 Handler 移植

| Handler | 功能 | 上游对照文件 |
|---------|------|-------------|
| `InfoHandler` | 设备信息（型号、固件、序列号） | `handlers/device_info.py` |
| `LogsHandler` | 忽略硬件日志包 `0a 0d` | `handlers/logs.py` |
| `InEarHandler` | 佩戴检测 `2b 03` | `handlers/state_in_ear.py` |
| `AutoPauseHandler` | 自动暂停开关 `2b 11` | `handlers/config_auto_pause.py` |
| `LowLatencyHandler` | 游戏/低延迟模式 `2b 6c` | `handlers/low_latency.py` |
| `SoundQualityHandler` | 连接质量 vs 音质 | `handlers/sound_quality_preference.py` |
| `VoiceLanguageHandler` | 语音语言读写 | `handlers/service_language.py` |
| `AncLegacyChangeHandler` | ANC 按钮变更检测 | `handlers/anc_change.py` |
| `AncHandler` | ANC 模式/级别读写 | `handlers/anc.py` |
| `DoubleTapHandler` | 双击手势 | `handlers/action_dual_tap.py` |
| `TripleTapHandler` | 三击手势 | `handlers/action_triple_tap.py` |
| `SwipeGestureHandler` | 滑动手势（音量控制） | `handlers/action_swipe_gesture.py` |
| `LongTapHandler` | 长按配置 | `handlers/action_long_tap.py` |
| `PowerButtonHandler` | 电源键双击（FreeBuds Studio） | `handlers/action_power_button.py` |

### 终端扩展
- `props`：打印当前属性存储内容
- `set group.prop value`：写入属性，路由到 `Handler.setProperty()`
- `connectToDevice` 重构为 `registerOpenFreebudsHandlers(driver)` 单次注册全部 Handler

### 未移植
- `OfbHuaweiEqualizerPresetHandler`（均衡器预设）
- `OfbHuaweiDualConnectHandler`（双连模式）
- 需要后续迭代补充

## v2.1.3 (2026-06-28)

### 性能优化（三轮迭代）

**第一轮：完全并行** — `initHandlers()` 改为 `coroutineScope` + `launch` 并行发起 13 个 Handler，12s 全局超时。FreeBuds 6i 蓝牙缓冲区有限，13 并发请求导致后半 Handler 丢包超时。

**第二轮：批次并行** — 每批 4 个 Handler、批次间 200ms 间隔。但 `coroutineScope` + `joinAll()` 导致批内等待最慢 Handler，实际总耗时未缩短。

**第三轮：交错并行 + 快速失败（当前）** — `mapIndexed` + `delay` × 80ms 交错发射，单 Handler 1.5s 超时 × 3 次重试，10s 全局超时。6i 实测 5.5s 完成 9/13，成功率达 70%。

### 新增功能
- **ANC 被动通知 (`2b2c`)**：AncHandler 新增 `2b2c` 命令支持，处理 6i 主动推送的 ANC 模式变更通知（单字节 param 格式）。
- **电池完整解析**：放宽 param 大小判断（`isNotEmpty()` / `size >= 3`），6i 多字节格式正常解析 global/L/R/case/is_charging。

### 缺陷修复
- **并行超时断连修复**：`withTimeout` 到期异常改为 `try-catch` 消化，只打 warning 不断连。
- **电池 param 大小兼容**：`size==1` → `isNotEmpty()`，`size==3` → `size>=3`，兼容 6i 多字节 param。
- **三击 `0126` 正常**：6i 官方支持三击，交错并行后成功获取。
- **AncHandler `onPackage` 双格式兼容**：`2b2a` 响应（2字节）和 `2b2c` 推送（1字节）统一处理。
- **6i 能力表保留 TRIPLE_TAP**：官方截图证实支持，回退误删。

### 设备能力表集成
- `registerOpenFreebudsHandlers` 由设备名匹配 `HuaweiModel`，查 `modelCapabilities` 只注册支持的 Handler
- 6i 过滤掉 `ACTION_POWER_BUTTON`、`ANC_LEGACY` 等不支持项，仅注册 13 个（含 LogsHandler）

### 已知问题
- **6i 蓝牙通道拥塞**：13 个交错请求中后部 4 个（device_info、gesture_double、gesture_swipe、voice_language）偶发超时，待 UI 开发时按需绑定
- **均衡器预设（EQ Preset/Custom）和双设备连接（Dual Connect）**：未实现

### 编译修复
- TerminalActivity 类型推断歧义修复

## v2.4.0 (开发中)

### 阶段一：底层修复 (2026-06-28)

**前后台感知 + 自适应轮询**：
- 新增 `DeviceRepository.setAppInForeground()`，由 `MainActivity` 生命周期驱动
- 前台 800ms 高频轮询（`fastPollJob`），后台 5s 低频轮询（`pollJob`）
- 切换回前台时自动恢复高频

**设备信息单次获取**：
- `deviceInfoFetched` 标记防止重复请求 `device_info`
- 进程冷启动（`init()`）+ 每次新连接时重置标记
- 仅在首次 `fastPoll` 循环中尝试获取一次

**被动通知全量覆盖**：
- 确认所有 Handler (`AncHandler`, `SoundQualityHandler`, `InEarHandler`, `BatteryHandler`) 的 `onDriverPackage` 已正确 `putProperty`
- 前台 800ms 刷新延迟最多 800ms 可见被动变更

**充电盒电量修复**：
- `syncProps()` 中 `case=100`（0x64）转为 0，表示无盒连接
- 正常范围值保持不变

**低延迟自动重开 + 后台服务**：
- `BluetoothService` 重写：注册 `ACTION_ACL_CONNECTED` / `ACTION_ACL_DISCONNECTED` 广播
- 检测到已保存设备蓝牙连接后自动发起 SPP 连接 + 3s 后自动开低延迟
- 通知栏增加 PendingIntent 跳转 MainActivity
### 阶段二：UI 重构 (2026-06-28)

**UI 翻译修正**：
- ANC 模式中文名修正："降噪" → "ANC模式"，透传子选项 "标准" → "透传模式"
- 取消按钮从对话框顶部移到内容区底部（Spacer + HorizontalDivider + TextButton）
- GestureScreen 的 OptionsDialog2 同样处理

**页面调度重构**：
- DeviceScreen 顶部栏移除断开按钮（`IconButton.Close`）
- HomeScreen 已保存设备列表每个条目尾部加红色删除图标（`Icons.Default.Delete`）
- `AppNavHost` 中 `onRemoveDevice` 回调执行 `removeSavedDevice` + 如果已连接则 `disconnect`

**手势选项下移到 DeviceScreen 底部**：
- 手势区块（`hasGesture` 判断 + 分组标题 + 可点击 ListItem）从 ANC 和音频之间移到音频之后、关于之前

**ANC 模式 haze 模糊滑块**：
- 新建 `AncModeSlider` 组件，`HazeState` + `haze`/`hazeChild` 实现带模糊和折射效果的分段选择器
- 三个选项（关闭/降噪/透传）等宽分布，选中项突出显示

**设置页改造**：
- `Theme.kt` 完全重写：新增 `LightColors` 浅色方案、`ThemeMode` 枚举（SYSTEM/DARK/LIGHT）、持久化
- `FxxkHilifeTheme` 接受 `mode` 参数，通过 `AppCompatDelegate.setDefaultNightMode` 同步系统主题
- `SettingsScreen.kt` 完全重写：主题分段选择器 + 壁纸导入（coil AsyncImage + `GetContent` + `takePersistableUriPermission`）+ 壁纸作用域选择（`WallpaperScope` 枚举，全部/仅主页/仅设置 FilterChip）+ 应用详情（项目理念/GitHub/更新地址）+ 圆形按钮 haze 模糊预览
- `AppNavHost` 适配主题/壁纸/作用域状态管理

### 阶段三：新功能 (2026-06-28)

**通知栏 ANC 三模式快切**：
- `BluetoothService` 新增 `ACTION_ANC_NORMAL`/`ACTION_ANC_CANCEL`/`ACTION_ANC_AWARE` 三个 Action
- `onStartCommand` 路由到 `setAncMode(mode)` 方法
- `createNotification` 通过 `PendingIntent.getService` 创建三个通知栏按钮（关闭/降噪/透传）

**日志控制**：
- `LogBuffer` 新增 `setMaxLines(max: Int)` 方法（范围 100-10000）、`getMaxLines()` 方法
- `MAX_LINES` 常量子段 → 动态 `_maxLines` 字段（默认 2000）
- `SettingsScreen` 调试区域新增 `LogRetentionSelector` 组件（500/1000/2000/5000/10000 行 FilterChip 选择）
- 日志保留行数持久化到 `log_max_lines`

**通知栏实时状态**：
- `DeviceProps` 新增 `connectedSince` 字段，`DeviceRepository` 追踪连接时刻
- `BluetoothService` 启动时通过 `props.collect` 监听属性变化，实时更新通知内容
- 通知显示 ANC 模式、音质模式、低延迟状态、佩戴时长（自动计时，h:m 格式）
- `scope = MainScope()` 统一管理协程，`onDestroy` 时取消 propsJob

### 文件变更
| 文件 | 变更 |
|------|------|
| `SettingsScreen.kt` | +250/-120 — 完全重写：主题/壁纸/作用域/应用详情/日志保留 |
| `Theme.kt` | +120/-30 — 三态主题 + LightColors + 持久化 |
| `AppNavHost.kt` | +45/-8 — 主题/壁纸/作用域状态管理 + onRemoveDevice |
| `DeviceScreen.kt` | +80/-30 — ANC 滑块 + 手势下移 + 断连按钮移除 |
| `HomeScreen.kt` | +25/-3 — 删除设备图标 + onRemoveDevice |
| `GestureScreen.kt` | +2/-2 — 取消按钮下移 |
| `BluetoothService.kt` | +135/-3 — ANC Action + setAncMode + props 监听 + 通知实时更新 |
| `DeviceRepository.kt` | +72/-8 — 前后台轮询 + 设备信息单次 + case 修复 + connectedAt + connectedSince |
| `LogBuffer.kt` | +12/-2 — 动态 _maxLines + setMaxLines/getMaxLines |
| `build.gradle.kts` | +1 — coil-compose 2.7.0 依赖 |
| `MainActivity.kt` | +15 — themeMode 状态 + 持久化加载 |
| **合计** | **~690/~200 行** |

### UI/UX 全面修复

**用户驱动改进**：基于 FreeBuds 6i SPP 连接日志和实际使用反馈，修复 8 项用户体验问题。

**底层修复**：
- **轮询提速**：属性轮询 10s → 3s（`DeviceRepository.startPolling` delay 3000ms）
- **ANC / 低延迟即时刷新**：`AncHandler.setProperty` 和 `LowLatencyHandler.setProperty` 先 `putProperty` 写入预期值再 `sendPackage`，UI 无需等待轮询即可看到新值
- **断连保护**：`SppDriver.recvLoop` 退出时设 `isConnected = false`，防止退出的 recvLoop 后 `pollJob` / `retryJob` 继续 TX 导致 Broken pipe
- **setProperty 响应优化**：增加 `delay(100)` 等硬件响应后 `syncProps()`

**UI 重构**：
- **五屏导航**：`AppNavHost` 重写，支持 Home / Device / Gesture / QrCode / Settings 五屏路由
- **主页改为已保存设备列表**：新建 `HomeScreen.kt`，显示所有已保存设备（`StringSet` 持久化），点击直接连接，扫描折叠在下
- **手势子页面**：新建 `GestureScreen.kt`，双击/三击/滑动手势/长按独立页面，全部中文选项（如 tap_action_pause → "播放/暂停"）
- **二维码页面**：新建 `QrCodeScreen.kt`，扫码连接占位（CameraX 待集成）
- **全属性中文映射**：`DeviceScreen.kt` 重写，ANC 模式（降噪/透传/关闭）、音质（声音优先/连接优先）、手势入口等全部显示中文
- 连接断开自动退回 Home 页

**数据层改进**：
- 持久化从单地址 `putString` → 多设备 `StringSet`（`saved_devices`），支持 `getSavedAddresses()` 和 `removeSavedDevice()`
- `DeviceViewModel` 暴露 `autoConnectSaved(address)` 按地址连接
- `ScanScreen.kt` 改为子面板，由 `HomeScreen` 折叠区调用

### 文件变更统计
| 文件 | 变更 |
|------|------|
| `SppDriver.kt` | +2 — recvLoop 退出设 isConnected=false |
| `DeviceRepository.kt` | +18/-3 — 轮询 3s + setProperty delay + StringSet 持久化 |
| `DeviceViewModel.kt` | +9/-3 — autoConnectSaved + getSavedAddresses + removeSavedDevice |
| `OpenFreebudsHandlers.kt` | +3 — AncHandler/LowLatencyHandler 先写后发 |
| `AppNavHost.kt` | +35/-14 — 五屏路由 + 断连退回 Home |
| `DeviceScreen.kt` | +159/-108 — 手势入口 + 全中文映射 |
| `GestureScreen.kt` | 新建 — 手势子页面 + 中文选项 |
| `HomeScreen.kt` | 新建 — 已保存设备主页 + 扫描折叠区 |
| `QrCodeScreen.kt` | 新建 — 二维码扫码占位 |
| **合计** | **+744/-108 行** |

## v2.4.1 (2026-06-28)

### Bug 修复与增强

**通知栏改善**：
- NotificationChannel 重要性 `IMPORTANCE_LOW` → `IMPORTANCE_DEFAULT`，确保通知栏图标可见
- 通知栏 ANC 按钮标签不变，音质映射修正已在 v2.4.0 完成

**Quick Settings Tile（快捷开关）**：
- 新增 `QuickSettingsTileService`：TileService 实现，点击打开应用，连接状态自动显示激活/未连接
- AndroidManifest 注册 `BIND_QUICK_SETTINGS_TILE` 权限
- 新增 `ic_tile.xml` 矢量图标（耳机/锁形图标）
- 用户需手动从快捷设置编辑页面添加 Tile 到控制面板

**已保存设备列表响应式修复**：
- `HomeScreen.savedAddresses` 从 `remember { ... }` 静态缓存改为 `LaunchedEffect(connState)` 订阅连接状态变化时刷新
- 删除设备后列表立即更新，无需退出重进

**日志模块改进**：
- `HilifeApplication.onCreate` 新增从 `SharedPreferences` 加载 `log_max_lines` 持久化值
- 应用冷启动后日志保留行数正确恢复

**6i ANC Level 确认**：
- 上游 `buds_6i.py` 第 15 行证实 `OfbHuaweiAncHandler(w_cancel_lvl=True, w_cancel_dynamic=True)`
- 当前 `DeviceCapability.kt` 中 `BUDS_6I` 已包含 `ANC_LEVEL` + `ANC_DYNAMIC`，UI 应正确展示降噪强度选择
- `2b04` 写入已在 `AncHandler.ignoreCommandIds` 中忽略，不会等待超时

**佩戴检测说明**：
- `InEarHandler.onInit` 初始设为 `false`，等待耳机 `2b03` 被动通知触发更新
- 当前 6i 日志未观察到 `2b03` 推送，需确认耳机固件是否支持佩戴检测推送

**设置页清理**：
- 移除"奇怪图标"：圆形按钮 haze 模糊预览组件已删除

### 文件变更
| 文件 | 变更 |
|------|------|
| `AndroidManifest.xml` | +11 — QuickSettingsTileService 注册 |
| `QuickSettingsTileService.kt` | 新建 — TileService 实现 (+64) |
| `ic_tile.xml` | 新建 — Tile 矢量图标 |
| `HilifeApplication.kt` | +5/-1 — log_max_lines 加载 + IMPORTANCE_DEFAULT |
| `HomeScreen.kt` | +6/-1 — savedAddresses 响应式刷新 |


## v2.6.0 (2026-06-28)

### Navigation Refactor
- Added `Scan` screen as independent destination in `AppNavHost`
- Saved device click now navigates directly to `DeviceScreen` instead of triggering inline scan
- Disconnect protection excludes `Scan` and `Gesture` screens from auto-redirect

### ANC Optimistic Update
- Introduced `optimisticAncMode` local state in `DeviceScreen` for instant UI feedback
- Replaced legacy buttons with Material3 `SegmentedButton`; removed Haze dependency

### Configurable Low-Latency
- `BluetoothService.onDeviceConnected()` now reads `PREF_AUTO_LOW_LATENCY` preference (default: true)
- Added "Connection Preferences" section in `SettingsScreen` with auto low-latency toggle

### Saved Devices Display Fix
- `getSavedAddresses()` now shows all saved device addresses with total count
- Removed `firstOrNull{isHuaweiOrHonor}` auto-connect logic from `DeviceViewModel.startScan()`



## v2.7.2 (2026-06-29)

### Interaction and Wallpaper Fixes
- Reworked `AncModeSlider` into a long pill-shaped slider with circular icon handles for `关闭` / `降噪` / `透传`.
- Reworked `ThemeSelector` into the same pill-shaped slider style for Follow / Dark / Light.
- `AppNavHost` now renders the selected wallpaper as a real global background according to `WallpaperScope`.
- Main page scaffolds use transparent containers so the global wallpaper is not hidden by default page backgrounds.

### Version Bump
- `versionCode` 20 → 21, `versionName` "2.7.1" → "2.7.2"

## v2.7.1 (2026-06-29)

### Persistent Notification Keep-alive
- `MainActivity` now starts `BluetoothService` as a foreground service on app launch, so the ongoing notification is created immediately.
- App launch and boot/package-replaced auto-start automatically reconnect the last saved earbuds when not already connected/connecting.
- `BluetoothService` keeps notification ANC actions (`关闭` / `降噪` / `透传`) available while the service is alive, even when the app UI is not open.
- Notification content updates immediately from `DeviceRepository.props.collect` for ANC mode, low-latency mode, and sound-quality mode.
- Listening duration is refreshed by a lightweight 60s ticker without polling the device again; battery display reuses existing repository data and does not increase query frequency.
- Removed stale ACL broadcast auto-connect / auto-low-latency code to avoid conflicts with the new app-launch reconnect flow.
- NotificationChannel is unified to `IMPORTANCE_LOW` with no badge for a stable persistent status notification.

### Version Bump
- `versionCode` 19 → 20, `versionName` "2.7.0" → "2.7.1"

## v2.7.0 (2026-06-28)

### Source-Level Optimistic Update
- `DeviceRepository.setProperty()` now updates `_props` StateFlow **immediately** before sending the command for `anc.mode`, `config.low_latency`, and `sound.quality_preference`, eliminating UI flicker entirely
- `delay(100)` → `150` for better hardware response tolerance
- `ensureDefaultAncOptions()` fills fallback `("normal", "cancellation", "awareness")` when `ancModeOptions` is empty
- Called at end of every `syncProps()` to guarantee defaults

### Tile Reactive Refresh
- `QuickSettingsTileService` now subscribes to `props.collect{}` during `onStartListening`, auto-updating Tile subtitle on any property change
- `propsJob` lifecycle managed via `onStopListening` → `cancel()` + null
- Click delay `800ms` → `300ms` (now inside coroutine with `delay(300)` + `updateTileState()`)

### NotificationChannel Adaptation
- `BluetoothService.onCreate()` now calls `createNotificationChannelIfNeeded()` before `startForeground()`, complying with Android 8+ requirements
- Channel: `CHANNEL_ID` ("bluetooth_service"), low importance, no badge, description on ANC/sound/low-latency status

### CI Auto-Release Enhancement
- Build triggers: added `main` branch + `v*` tag push
- `fetch-depth: 0` for accurate git log
- Release step: creates tag if missing, **updates assets on existing tag** via `gh release upload --clobber`
- Separated "Get Version" step for clean `VERSION_NAME` env

### DeviceScreen LaunchedEffect Protection
- 3000ms timeout guard added: if ANC mode doesn't match expected value within 3s, optimistic state auto-clears to prevent stuck UI

### Version Bump
- `versionCode` 18 → 19, `versionName` "2.6.0" → "2.7.0"

## v2.5.0 (2026-06-28)

### 阶段一：ANC UI 状态同步 + 移除 Haze

**Haze 模糊效果完全移除**：
- `DeviceScreen.kt`：`AncModeSlider` 从 Haze 双层 Box（hazeState + haze/hazeChild）替换为简单 `Row + Surface` 分段控件
- `SettingsScreen.kt`：`ThemeSelector` 同样移除 Haze，改用 `Row + Surface` 原生 Material3 样式
- 同时清除了两个文件中所有 Haze 相关 import（`HazeState`/`HazeStyle`/`HazeTint`/`haze`/`hazeChild`）
- 选中项通过 `tonalElevation = 2.dp` 提供轻微浮起效果替代模糊

**ANC 被动渲染确认**：
- `AncHandler.setProperty` 在第 253 行已先 `putProperty(group, prop, value)` 写入预期值再 `sendPackage`（2b04 在 ignoreCommandIds 中不等待响应）
- UI 通过 `props.collectAsState()` 直接读取 `props.ancMode`，完全被动渲染，切换无延迟

### 阶段二：导航逻辑修复

**确认无问题**：
- HomeScreen 的 `SavedDeviceItem` 已在 v2.4.1 修复 `clickable` 事件冲突（clickable 下放到子组件）
- AppNavHost 中 `LaunchedEffect(connState)` 检测 `Connected` 后自动切到 `Device` 页

### 阶段三：通知系统增强

**已确认实现完整**：
- BluetoothService 前景通知（startForeground）+ props.collect 实时更新 + 3 个 ANC Action 按钮
- NotificationChannel 重要性 IMPORTANCE_DEFAULT，发送 POST_NOTIFICATIONS 权限请求
- 通知内容含 ANC 模式、音质模式、低延迟状态、佩戴时长

### 阶段四：Quick Settings Tile 完善

**功能增强**：
- Tile `label` 从 "fxxkHilife" 改为 "ANC"
- 已连接时 `onClick` 直接切换 ANC 模式（关闭→降噪→透传→关闭）
- 未连接时打开 MainActivity
- Tile 副标题实时显示当前 ANC 模式中文标签（降噪/透传/关闭）
- 切换后 800ms 延迟更新 Tile 状态

### 阶段五：Init 超时优化

**重试策略改进**：
- `DeviceRepository.retryFailedHandlers` 重试间隔从固定 30s → 阶梯式（前 3 次 5s，之后 30s）
- 新增 `attempt` 计数器，日志输出当前 attempt 编号
- 关键 Handler（如 anc_global）能在连接后快速重试成功

### 文件变更
| 文件 | 变更 |
|------|------|
| `DeviceScreen.kt` | +27/-61 — Haze 移除，AncModeSlider 重写为 Row+Surface |
| `SettingsScreen.kt` | +25/-50 — Haze 移除，ThemeSelector 重写为 Row+Surface |
| `QuickSettingsTileService.kt` | +45/-15 — 一键 ANC 切换 + 实时状态副标题 |
| `DeviceRepository.kt` | +7/-4 — 阶梯式重试间隔 |
| `app/build.gradle.kts` | +2/-2 — 版本号 16→17, 2.4.1→2.5.0 |
| `README.md` | +4/-4 — 版本号 + 功能描述更新 |
| `README_EN.md` | +4/-4 — 版本号 + 功能描述更新 |
| `VERSION_MANAGEMENT.md` | +7/-7 — 版本号 + 历史表格更新 |
| `DEVELOPMENT_LOG.md` | +55 — v2.5.0 记录 |
| **合计** | **~176/~147 行** |

## v2.7.3 (2026-06-29)

### 自动连接与低延迟修复
- 所有自动连接入口统一走 `DeviceRepository.autoConnectSaved()` / `autoConnectLastSaved()`。
- 自动连接前先确认耳机已与手机系统蓝牙连接：优先反射 `BluetoothDevice.isConnected()`，兜底 `BluetoothManager.getConnectedDevices(HEADSET/A2DP)`。
- 自动低延迟/游戏模式在 SPP 连接和初始化流程完成后立即开始尝试开启，不再固定等待 1.5s。
- 若首次写入失败，每 500ms 连续重试，最多 30s；确认 `config.low_latency=true` 后自动停止。
- 断开连接时取消自动低延迟重试任务，避免残留协程继续写入。

### 日志与版本定位
- 应用启动时日志输出 `fxxkHilife <versionName> (<versionCode>) started`。
- 便于从用户导出的 SPP 日志中直接判断版本来源。

### 发布
- versionCode: 22
- versionName: 2.7.3
- tag: v2.7.3

