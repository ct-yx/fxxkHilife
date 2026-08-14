# UI-0 基线与资源清点

> 建立日期：2026-08-14
>
> 本文件是迁移前基线，不代表新 UI 已通过截图或真机渲染验收。UI-0 的代码迁移允许复用这里的字段和资源结论，但不得把旧页面截图当作新页面通过证据。

## 1. 静态规模与路由

当前 `app/src/main/java/com/freebuds/controller/ui` 有 14 个 Kotlin 文件、约 183 KB、4325 行。生产路由由 `AppNavHost` 维护：

| Route | 当前页面 | 入口/退出 | 迁移结论 |
|---|---|---|---|
| `permission_guide` | `PermissionGuideScreen` | 首次权限不足；授权后进入 Home | 保留行为，迁移到统一外壳 |
| `home` | `HomeScreen` | 启动页、返回页、Service/Tile 间接入口 | 统一设备摘要和连接事件 |
| `scan` | `ScanScreen` | Home 扫描卡片 | 扫描完成只产生一个连接命令和一个导航事件 |
| `device` | `DeviceScreen` | 稳定连接状态自动进入 | 拆成电量、ANC、音频、双设备、设备信息组件 |
| `gesture` | `GestureScreen` | Device 手势入口 | 使用 typed event 和能力集合 |
| `listening_stats` | `ListeningStatsScreen` | Device 统计入口 | 仅消费 `ListeningStats` |
| `settings` | `SettingsScreen` | Home/Device 设置入口 | 迁移设置仓库、更新状态和统一控件 |
| `terminal` | `TerminalActivity` | Debug 设置/Device 入口 | Debug 能力保留；后续迁移到统一终端 route |

### 连接导航状态

页面当前同时观察兼容 `ConnectionState` 和 BT-4 的 `ControlChannelState`。迁移目标是只用以下稳定摘要：

```text
SystemLink: Disconnected / Connected
ControlChannel: Idle / ConnectingTransport / TransportReady / InitializingCore
Core: CoreReady / Ready / Degraded / Failed
Identity: deviceName / address / attemptId / trigger
Work: pendingHandlers / failedHandlers / reason
```

旧的“调用连接后立刻读取 `connectionState` 再决定是否跳转”属于已确认的竞态点；新导航必须由状态流和一次性 `NavigationEvent` 驱动。

## 2. 状态、字段和动作清单

| 领域 | 当前兼容投影 | 目标 typed state | 主要页面 | 目标事件 |
|---|---|---|---|---|
| 电量 | `DeviceProps.battery*` | `EarbudState.battery` | Home/Device | `RefreshDevice`, `RetryAction` |
| ANC | `ancMode*`, `ancLevel*` | `EarbudState.anc` | Device/通知/Tile | `SetAncMode`, `SetAncLevel` |
| 音频 | `autoPause`, `lowLatency`, `soundQuality*`, EQ | `EarbudState.audio` | Device | `SetAutoPause`, `SetLowLatency`, `SetSoundQuality`, `SetEqualizerPreset` |
| 双设备 | `dualConnect*` | `EarbudState.dualConnect` | Device | `SetDualConnect`, `SetPreferredDevice`, `SetDualDeviceOption` |
| 手势 | `*Tap*`, `longTap`, `swipeGesture` | `EarbudState.gestures` | Gesture | `SetGesture` |
| 佩戴 | `inEar` | `EarbudState.wearing` | Device | 只读 |
| 设备信息 | `deviceModel`, `firmwareVersion` | `EarbudState.deviceInfo` | Device | 只读 |
| 能力 | `Set<EarbudCapability>` | 同名能力集合 | 所有设备页 | 未声明能力不显示控制区 |
| 统计 | `ListeningStats` | `ListeningStats` | Stats | 只读 |

UI 页面不得再直接组合厂商 `group/prop`。现有 raw 写入位置已经登记在迁移代码的 `DeviceEvent` 映射中，兼容 raw 写入只允许留在 ViewModel/Repository 边界。

## 3. 设置 key 基线

| 旧存储 | key | 迁移目标 |
|---|---|---|
| `fxxk_theme` | `theme_mode` | `SettingsRepository.themeMode` |
| `fxxk_theme` | `wallpaper_uri` | `SettingsRepository.wallpaperUri` |
| `fxxk_theme` | `wallpaper_scope` | `SettingsRepository.wallpaperScope` |
| `fxxk_theme` | `log_max_lines` | `SettingsRepository.logMaxLines` |
| `fxxk_theme` | `log_protocol_frames` | `SettingsRepository.protocolFrameLogging` |
| `fxxk_ui` | `display_mode` | `SettingsRepository.displayMode` |
| `fxxk_ui_glass` | `tint_alpha`, `readability_strength`, `refraction_strength`, `depth`, `corner_radius_dp`, `surface_profile` | `LiquidGlassConfig` 兼容模型，最终由 UI surface profile 消费 |
| `fxxk_i18n` | `locale` | `SettingsRepository.locale` |
| `settings` | `auto_low_latency` | `SettingsRepository.autoLowLatency` |
| 新增 `fxxk_settings` | `update_channel`, `auto_check_enabled`, `auto_download_enabled`, `check_interval`, `last_check_at`, `last_notified_version_code`, `metered_network_allowed` | 更新流程专用，不进入蓝牙状态 |

迁移规则：首次读取新仓库时从旧 key 复制一次；写入只写新仓库；旧 key 在 UI-5 验收完成前保留读取兼容。

## 4. 美术与兼容资源

### 保留的美术资源

- `res/drawable/ic_anc_awareness.xml`
- `res/drawable/ic_anc_cancellation.xml`
- `res/drawable/ic_anc_normal.xml`
- `res/drawable/ic_earbuds_case.xml`
- `res/drawable/ic_tile.xml`
- `res/mipmap-*/ic_launcher.png`

### 迁移期间保留的非美术资源

- `res/values/strings.xml`
- `res/values-en/strings.xml`
- `res/values-zh-rTW/strings.xml`
- `res/values/colors.xml`
- `res/values/styles.xml`
- `res/values/themes.xml`
- `res/layout/activity_terminal.xml`
- `res/layout-land/activity_terminal.xml`
- `res/xml/file_paths.xml`

新增 `UiAssetCatalog` 只提供语义映射；页面不再根据页面状态散落 `R.drawable` 判断。

## 5. 更新能力基线

旧 `UpdateChecker` 草稿已删除；当前由 `UpdateRepository` 读取 Pages manifest，失败时回退到 Release manifest，按 `versionCode` 比较并维护检查/下载/校验/安装状态。Settings 通过 `SettingsEvent` 发送检查、下载、取消和安装动作，安装 Intent 由 ViewModel/更新仓库生成，路由负责启动系统安装器；Settings 仍保留 Release 页面入口作为人工兜底。

UI-5 的实现约束：

```text
update.json -> versionCode 比较 -> HTTPS 临时文件
-> SHA-256 / packageName / versionCode / 签名校验
-> ReadyToInstall -> Android Package Installer 用户确认
```

自动检查和下载不依赖蓝牙 Service、ACL、扫描或连接 attempt；自动安装不绕过系统确认。

## 6. 玻璃渲染基线

迁移前静态调用：

- `AppNavHost` 创建一个共享 `HazeState` 并挂载一个 `hazeSource`。
- `AdaptiveGlass.kt` 的卡片、面板和横幅使用旧嵌套 effect DSL。
- Home、Scan、Device、Gesture、ListeningStats、Settings 透传 `UiDisplayMode` 和 `HazeState?`。
- 当前默认 `UiDisplayMode` 是 `CLASSIC`，因此默认路径不会挂载模糊 effect；这解释了“依赖存在但看不到效果”的一部分现象。

UI-1 迁移后只允许 `ui/foundation/surface` 直接导入渲染库：

```text
GlassHost -> BackgroundLayer -> shared source
          -> SurfaceRenderer
             HazeGlass -> HazeBlur -> Tint -> Opaque
```

需要运行证据的项目：`renderer`、`sourceAttached`、`effectSurfaceCount`、`fallbackReason`、classic/glass 截图和代表性设备性能。

## 7. UI-0 验收状态

已完成：

- 路由、入口和重复连接风险清单。
- `DeviceProps` 到 `EarbudState`/`EarbudCapability` 的迁移表。
- 设置 key 和更新旧草稿清单。
- 资源保留与兼容边界。
- 迁移前 Haze 调用和默认经典路径的静态基线。

待实测/截图：

- 各 route 的 classic/glass、深浅色和状态截图。
- 真实输入下的 Glass/Blur/Tint/Opaque renderer 诊断。
- 大字体、TalkBack、横竖屏和进程重建的 UI 证据。

因此 UI-0 记录为 `[~] 进行中：等待 UI 基线截图与运行诊断`，不把文档完成误记为 UI-0 完成。

## 8. 当前静态门

`scripts/validate_ui_contract.py` 已纳入 `scripts/run_ci_checks.sh`，在 Android 构建前检查：

- 只有 `ui/foundation/surface/SurfaceRenderer.kt` 可以直接调用渲染库。
- 页面通过 `SettingsEvent`/typed state 交互，不直接持有设置存储或原始属性写入。
- `AppNavHost` 使用统一 `AppScaffold`，旧更新草稿已移除。

该静态门不替代 CI 构建、截图、无障碍或设备运行证据。
