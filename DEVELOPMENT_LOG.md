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

## v2.7.4 (2026-06-29)

### Bug 修复
- 修复音质偏好首次切换后检测不及时的问题：`2ba2` 写入后耳机可能直接推送 `2ba3` 状态包，`SoundQualityHandler` 现在会处理异步 `2ba3` 并乐观更新，再短间隔读取确认。
- 修复音质偏好多次切换才正确的问题：写入不再阻塞等待不稳定的 `2ba2` ACK，避免 UI/通知状态滞后。
- 修复 ANC 子模式显示混乱：降噪模式下 `normal` 显示为“均衡”，通透模式下 `normal` 显示为“普通透传”。
- 修复 ANC 切到通透后下方标题仍显示“降噪强度”的问题：根据当前 ANC 模式动态显示“降噪强度”或“通透模式”。
- 修复 ANC 主模式切换时旧 `level_options` 残留导致选项混用的问题。

### 权限引导
- 权限申请界面加入 Android 13+ 通知权限申请。
- 权限申请界面加入后台保活 / 电池优化白名单入口。
- 权限申请界面加入部分 ROM 自启动管理入口，并提供应用详情页兜底。

### 发布
- versionCode: 23
- versionName: 2.7.4
- tag: v2.7.4

## v2.7.5 (2026-06-29)

### Bug 修复
- 修复 ANC 主模式切换后的状态乱跳：耳机在写入 `2b04` 后可能继续推送旧的 `2b2a/2b2c` 状态包，旧包会覆盖刚刚乐观更新的 UI。
- `AncHandler` 现在会在主模式写入后进入短暂 pending 窗口，只接受目标模式状态包；窗口内不匹配的旧状态包会被忽略。
- 保留 v2.7.4 的音质偏好异步 `2ba3` 同步修复、ANC 子模式标题/选项修复和权限引导增强。

### 发布
- versionCode: 24
- versionName: 2.7.5
- tag: v2.7.5

## v2.7.6 (2026-06-29)

### Bug 修复
- 修复从任意 ANC 模式第一次切换到“降噪”时按钮乱跳的问题。
- 根因：耳机在 `2b04` 写入后可能先回目标 `2b2a`，随后继续推送上一模式的旧 `2b2c`，v2.7.5 在收到首个目标包后过早解除 pending，导致旧包仍可覆盖 UI。
- 修复：ANC 主模式写入后保留完整 4s pending 窗口；窗口内只接受目标模式状态包，不匹配的旧 `2b2a/2b2c` 全部忽略。
- 修复充电盒 100% 电量无法正常显示的问题：移除 `case == 100` 强制当作无效值显示 0 的旧逻辑，真实显示设备返回的 100%。

### 工程改进
- 新增 `scripts/bump_version.py`，用于一键同步 versionCode/versionName、README、README_EN、docs/index.html、VERSION_MANAGEMENT 和 DEVELOPMENT_LOG。
- `VERSION_MANAGEMENT.md` 增加脚本用法说明，方便后续发版。

### 发布
- versionCode: 25
- versionName: 2.7.6
- tag: v2.7.6

## v2.7.7 (2026-06-29)

### FreeBuds 7i 临时适配
- 新增 `HuaweiModel.BUDS_7I`，匹配 `FreeBuds 7i`，SPP 端口为 1。
- 为 FreeBuds 7i 增加临时保守能力表，避免未知型号走通用全量 Handler 导致 15 个 Handler 同时初始化、pending 堆积和大量 timeout。
- 7i 临时能力表保留核心功能：INFO / WEAR_DETECT / BATTERY / ANC / TRIPLE_TAP / SOUND_QUALITY / LOW_LATENCY / VOICE_LANGUAGE。v2.8.3 起暂时移除 7i 上未确认可用的 AUTO_PAUSE。
- 暂时不启用长按、滑动、Power Button、EQ、双设备等更容易拖慢初始化或仍需验证的能力。
- 说明：这不是 FreeBuds 7i 完整体适配，只是为了降低初始化压力、改善连接后等待时间的临时修复。

### 后续计划
- FreeBuds 7i 将在下一轮大版本中继续与测试者协作完整适配。
- 下一轮大版本会同步推进更多不同型号、更多不同厂商耳机的兼容性适配。

### 发布
- versionCode: 26
- versionName: 2.7.7
- tag: v2.7.7

## v2.8.0 (2026-06-29)

### UI 导航系统重构
- `AppNavHost` 从手动 `currentScreen` 状态切换迁移为 Navigation Compose `NavHost` / `rememberNavController`。
- 修复从设备详情返回主页后，点击已保存耳机只触发连接但无法进入详情页的问题。
- 修复设置页返回依赖连接状态导致来源混乱的问题，改为标准返回栈 `popBackStack()`。
- 手势页、扫描页、设置页统一使用导航返回栈，系统边缘返回可回到上一级页面，不再直接退到桌面。

### 展示模式基础
- 新增 `UiDisplayMode`：`CLASSIC` 传统展示、`LIQUID_GLASS` 液态玻璃。
- 新增 `loadUiDisplayMode()` / `saveUiDisplayMode()`，通过 `fxxk_ui.display_mode` 持久化展示模式。
- 设置页新增“展示模式”切换入口，为后续液态玻璃 UI 重构提供开关。
- 当前默认仍为传统展示模式，液态玻璃模式先作为后续页面改造的入口，不强制替换现有 UI。

### 依赖
- 新增 `androidx.navigation:navigation-compose:2.8.5`。
- Haze 依赖已存在，后续液态玻璃组件将基于 `dev.chrisbanes.haze` 与本地 `third_party/haze` 参考实现继续推进。

### 发布
- versionCode: 27
- versionName: 2.8.0
- tag: v2.8.0

## v2.8.1 (2026-06-29)

### ANC 状态同步修复
- 修复耳机初始化阶段已经收到 ANC 状态包，但应用 UI 仍显示默认“关闭”的问题。
- 根因：`AncHandler` 已经通过 `SppDriver.putProperty("anc", ...)` 写入属性仓库，但 `DeviceRepository.props` 只在连接完成、轮询或手动写入后同步，初始化期间的被动属性更新没有立即推送到 StateFlow。
- `SppDriver` 新增 `onPropertyChanged` 回调，任意 Handler 更新属性仓库后通知 Repository 立即 `syncProps()`。
- 统一修复所有 ANC 入口：详情页 ANC 按钮、通知栏 ANC 按钮、Quick Settings Tile 快捷开关都读取同一个实时 `DeviceRepository.props`。

### 发布
- versionCode: 28
- versionName: 2.8.1
- tag: v2.8.1

## v2.8.2 (2026-06-29)

### 连接状态同步修复
- 修复耳机与手机蓝牙断开后，应用仍停留在 `Connected` 状态的问题。
- `SppDriver` 新增 `onDisconnected` 回调：RFCOMM 接收循环结束、流关闭或异常退出后通知 `DeviceRepository`。
- `DeviceRepository` 新增远端断开处理：取消轮询/重试/自动低延迟任务，清空 driver、props、connectedSince，并将连接状态切回 `Disconnected`。
- 新增系统 `BluetoothDevice.ACTION_ACL_DISCONNECTED` 动态广播监听作为兜底，手机蓝牙层断开目标耳机时立即刷新应用状态。
- 修复后 UI、通知栏、Quick Settings Tile 都会跟随仓库连接状态同步更新。

### 发布
- versionCode: 29
- versionName: 2.8.2
- tag: v2.8.2

## v2.8.3 (2026-06-29)

### 自动暂停处理
- 对照上游 OpenFreebuds，当前 `AutoPauseHandler` 使用的协议命令与上游一致：读取 `2b11`、写入 `2b10`、确认参数 `127`。
- 测试日志显示 FreeBuds 7i 可以读取自动暂停状态，但写入 `2b10` 后经常超时或仅返回异常/泛化 ACK，实际功能未生效。
- `AutoPauseHandler` 改为写入后再次读取 `2b11` 做确认，只有读回状态与目标一致时才更新 `config.auto_pause`，避免 UI 误报写入成功。
- 从 FreeBuds 7i 临时保守能力表中暂时移除 `AUTO_PAUSE`，避免在 7i 上展示当前未确认可用的“摘下自动暂停”选项。
- 该能力后续留到 4.x 多型号/多厂商适配阶段与测试者继续验证。

### 发布
- versionCode: 30
- versionName: 2.8.3
- tag: v2.8.3

## v2.9.0 (2026-06-29)

### Glass / Haze 基础组件
- 新增 `ui/glass/AdaptiveGlass.kt`，提供 `AdaptiveCard`、`LiquidGlassCard`、`LiquidGlassPanel`。
- 液态玻璃模式基于 Haze `1.6.7` 的 `hazeSource` / `hazeEffect` / `rememberHazeState` / `HazeTint` 实现。
- 玻璃组件加入背景模糊、半透明 tint、细噪声、虹彩边缘和高光描边；传统模式仍回落到 Material3 Card。

### Home / Device 双模式改造
- `AppNavHost` 创建全局 `hazeState`，根背景作为 Haze source，传递到 Home / Device。
- Home 页面已保存设备卡片、扫描入口卡片接入 `AdaptiveCard`。
- Device 页面电池卡片、ANC 分段控制区域接入液态玻璃容器。
- 传统展示模式保持原有 Material3 体验；液态玻璃模式在同一页面结构上启用玻璃视觉。

### 壁纸引导
- 设置页切换到“液态玻璃”且尚未设置壁纸时，会弹出非强制引导。
- 用户可选择壁纸后自动开启液态玻璃，也可以选择“仍然开启”直接启用。

### 发布
- versionCode: 31
- versionName: 2.9.0
- tag: v2.9.0

## v2.9.1 (2026-06-29)

### CI 编译修复
- 修复 v2.9.0 `AdaptiveGlass.kt` 中 Haze effect lambda 内直接访问 `MaterialTheme.colorScheme` 导致的 Compose 编译错误。
- 报错：`@Composable invocations can only happen from the context of a @Composable function`。
- 处理：在 Composable 上下文提前计算 `primaryTint`，再传入 `hazeEffect` 的非 Composable 配置 lambda。
- 删除本地临时 Haze 源码副本后，将 `.gitignore` 中 `third_party/haze/` 调整为 `third_party/`，防止后续参考仓库误提交。

### 发布
- versionCode: 32
- versionName: 2.9.1
- tag: v2.9.1

## v2.9.2 (2026-06-29)

### 液态玻璃视觉修复
- 修复液态玻璃卡片在浅色/传统视觉下出现“深色外圈 + 浅色内芯”的双层色块问题。
- `LiquidGlassCard` 移除内部二次渐变背景，改为单层统一玻璃材质，降低外圈/内芯割裂感。
- `AdaptiveCard` 传统模式统一加入内容 padding，避免同一组件在传统/玻璃模式下版式不一致。

### 卡片适配扩展
- Device 页面进一步适配：音频选项、开关项、手势入口、设备信息、调试终端都改为 `AdaptiveCard`。
- Scan 页面设备列表项接入 `AdaptiveCard`，耳机扫描列表在液态玻璃模式下与主页保存设备卡片风格一致。
- Scan 页面顶部栏在液态玻璃模式下透明化。

### 发布
- versionCode: 33
- versionName: 2.9.2
- tag: v2.9.2

## v2.9.3 (2026-06-29)

### 展示模式边界修复
- 明确传统模式与液态玻璃模式的组件边界：传统模式保持单层 Material3 Card，不继承玻璃容器结构。
- 恢复液态玻璃卡片的“外层玻璃折射 + 内层柔和内容层”双层结构，但只在 `LIQUID_GLASS` 模式启用。
- `LiquidGlassPanel` 在传统模式下不再额外绘制 surfaceVariant 背景，只直接渲染原始内容，避免传统模式出现类似玻璃卡片的双层视觉。
- 调整玻璃 tint / inner layer alpha，让双层玻璃有层次但不再变成突兀的深色外圈。

### 发布
- versionCode: 34
- versionName: 2.9.3
- tag: v2.9.3

## v2.9.4 (2026-06-29)

### 液态玻璃适配补全
- `GestureScreen` 接入 `displayMode` / `hazeState`，双击、三击、滑动、长按等手势二级设置项改为 `AdaptiveCard`。
- `SettingsScreen` 接入 `hazeState`，主题、展示模式、壁纸、壁纸范围、关于、连接偏好、调试、应用详情、其他贡献等设置区改为液态玻璃卡片。
- 新增设置页通用 `SettingsCard`，传统模式继续保持单层 Material3 卡片，液态玻璃模式切换为玻璃卡片。
- 日志保留、分享日志、调试终端等二级设置入口也统一纳入玻璃卡片体系。

### 发布
- versionCode: 35
- versionName: 2.9.4
- tag: v2.9.4

## v2.9.5 (2026-06-29)

### 发布
- 修复手势页液态玻璃适配缺失 Alignment import 导致的 CI 编译错误
- versionCode: 36
- versionName: 2.9.5
- tag: v2.9.5

## v2.9.6 (2026-06-29)

### 耳机详情选项交互优化
- 将耳机详情页的降噪强度、通透模式、音质偏好等 `DeviceOptionItem` 从小弹窗选择改为卡片内展开选择。
- 展开后直接在当前玻璃卡片内展示 `FilterChip` 选项，选中后自动收起，避免液态玻璃界面里突然出现突兀 Dialog。
- 当前选项使用选中态和 Check 图标标记，保留传统/液态玻璃双模式一致交互。
- 移除详情页已不再使用的 `OptionsDialog`。

### 发布
- versionCode: 37
- versionName: 2.9.6
- tag: v2.9.6

## v2.10.0 (2026-06-29)

### 液态玻璃材质增强
- 重构 `AdaptiveGlass.kt`，将 `LiquidGlassCard` 升级为可参数化的增强版玻璃组件。
- 新增 `tint`、`refractionStrength`、`depth`、`shape`、`cornerRadius`、`surfaceProfile` 等参数，用于控制染色、折射感、厚度、形状与表面轮廓。
- 新增 `GlassSurfaceProfile`：`Rounded` / `Squircle` / `Circle`，用于不同卡片轮廓下的边缘高光与厚度表现。
- 在 Haze 1.6.7 可用 API 上实现兼容方案：继续使用 `hazeSource` + `hazeEffect`，不直接调用当前依赖中未确认存在的 `liquidGlassEffect`。
- 增强玻璃视觉：更低实体背景、更强背景模糊、半透明 tint、左上镜面高光、Fresnel 暗边、彩色细边框和内部折射光带。
- `LiquidGlassPanel` 同步使用新的光学边缘处理，让 ANC 滑块等面板与卡片材质保持一致。
- 添加 `LiquidGlassCardPreview`，展示父级 `rememberHazeState()`、背景 `.hazeSource(hazeState)` 和多个液态玻璃卡片叠加效果。
- 在组件注释中补充使用示例、Android 12+ 效果最佳、避免大量重叠 `hazeEffect`、低端机降低折射/深度/卡片数量等性能说明。

### 发布
- versionCode: 38
- versionName: 2.10.0
- tag: v2.10.0

## v2.10.1 (2026-06-29)

### 液态玻璃个性化设置
- 新增 `LiquidGlassConfig` 与 `LocalLiquidGlassConfig`，支持全局读取并持久化液态玻璃参数。
- 设置页新增“个性化”分组，默认只显示折叠入口，避免设置页过长。
- 展开后提供类似 Flowmix 的分段预设：玻璃模糊强度、边缘折射、深度效果、可读性增强。
- “高级模式”作为二级折叠区域，提供 Tint、Refraction、Depth、Radius 滑杆和表面轮廓选择。
- `AppNavHost` 负责加载/保存配置，并通过 CompositionLocal 让所有 `LiquidGlassCard` / `LiquidGlassPanel` 实时使用最新参数。
- `LiquidGlassCard` 的参数改为可选值：未显式指定时自动读取用户个性化配置；Preview/特殊卡片仍可单独覆盖。

### 发布
- versionCode: 39
- versionName: 2.10.1
- tag: v2.10.1

