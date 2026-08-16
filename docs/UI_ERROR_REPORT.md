# UI 重构错误报告

- 报告日期：2026-08-16
- 影响范围：液态玻璃渲染、列表滚动性能、设备详情页导航
- 原始状态：报告创建时已确认根因，尚未修复（历史快照）
- 当前状态：v4.4.3 / 102 已针对“效果几乎没有、滑动仍卡顿”改为共享 viewport glass 和稳定 source 策略；等待 GitHub Actions、截图/运行诊断和定向性能实测
- 证据边界：本报告基于当前源码审查；未将构建通过、静态检查或蓝牙连接状态当作 UI 运行成功证据

## 修复回灌（2026-08-15，v4.4.0 / 99）

本轮不把静态代码改动直接视为问题关闭，先记录实现和下一道验证门：

- **UI-ERR-001**：`SurfaceRenderer` 已接入 alpha05 typed `hazeGlass`/`hazeBlur`、`GlassStyle`、`GlassOptics.Fixed` 和 `HazeBlurStyle`；`AppScaffold` 只有在壁纸成功加载后绑定 source。无背景、加载中或加载失败时，实际 renderer 回到 Material 3，并记录 requested/actual renderer、source、effect 数量和 fallback reason。
- **UI-ERR-002**：保存设备、扫描设备和双连接列表加入地址稳定 key；长列表默认 Tint/Material 3；展开选项移除与 `AnimatedVisibility` 重复的 `animateContentSize`。
- **UI-ERR-002/003 收口**：页面状态改用生命周期感知的 `collectAsStateWithLifecycle`；Home 只在控制目标地址变化时刷新保存设备连接，避免每个 `systemConnected` 阶段重复刷新；扫描设备点击使用明确的 `BluetoothDevice` 用户连接入口，保存设备点击继续使用已保存地址入口。
- **UI-ERR-003**：详情页自动导航绑定 `address + attemptId`，同一会话只消费一次；用户返回后不再自动重开，Home/Scan 点击设备直接产生详情导航意图。
- **CI 配置跟进**：GitHub Actions 首次进入 AGP 9.1 后发现模块级 `org.jetbrains.kotlin.android` 插件已被内置 Kotlin 接管；已移除模块/根项目的多余声明，保留 Compose Compiler 插件，后续 CI 已完成源码和 alpha05 typed API 编译验证。
- **CI 验证完成**：GitHub Actions `31833142274` 通过 `diff-check`、UI contract、102 个 JVM 测试和 Debug/Release 构建；Debug SHA-256 为 `0aaa7353a39412cdc15ae80af3fcfcbcfa7340fdebb465e338ba44c9f764991f`，Release SHA-256 为 `853ffb596090289b89456d991c06769b59c94385e9d1e1603c4dc2a3d818e9a8`。

**未关闭条件**：GitHub Actions 构建通过；`UI_GLASS_RENDERING_TARGETED`、`UI_GLASS_PERFORMANCE`、`UI_NAVIGATION_SESSION` 和无背景 Material 3 fallback 的截图/运行诊断通过。未完成这些条件前，不把本报告标记为完全解决。

## Follow-up 3：v4.4.3 / 102 对“效果几乎没有、滑动仍卡顿”的共享视口修复

### 根因复核

- `StandardCard`、`CompactRow` 和大多数 `AdaptiveCard` 默认解析为 `TintOnly`；上一版又把 LazyColumn 内的 Feature/Hero 一并降级，结果列表只剩装饰皮肤，没有稳定的真实 `hazeGlass` surface，静止画面自然几乎看不到背景采样和光学层。
- 上一包在滚动开始时切换 effect 节点并摘挂全窗口 `hazeSource`；当前工作树即使改成静态行皮肤，列表行仍读取 `host.isScrolling`，触摸边界仍会让可见 item 批量失效。

### 本轮修改

- `GlassScrollableContent` 现在在 LazyColumn 后台创建一个稳定的真实 typed `hazeGlass` viewport；列表行从首次组合开始固定使用 draw-only 的渐变、内高光和边缘皮肤，不逐行创建 captured-content effect。
- Home、Device、Settings、Scan、Gesture、ListeningStats 的列表不在手势开始时创建/替换 Haze 节点；列表行也不再读取 `host.isScrolling`，触摸边界不会触发整批可见 item 的 renderer 重算。
- `GlassHost` 的静态背景 source 在滚动期间保持挂载，viewport effect 使用单一输入；`surfaceRole`、`sourceAttached`、`effectSurfaceCount` 和 `scrollable_surface_budget` 诊断语义继续保留。
- `LiquidGlassBackdrop` 增加背景色场对比度，使静态皮肤和真实 glass 都有可观察的输入层次。
- 增加 `GlassScrollPolicyTest`，锁定“列表行 effect 永不挂载、共享 viewport 只在 Liquid/source/硬件/API 33+ 就绪时启用、非 effect renderer 不改变”的回归规则。

### 本轮定向验证门

使用 CI 产物标签 `UI_GLASS_SHARED_VIEWPORT`，只验证本轮改动：

1. Liquid 模式无壁纸：Home、Device、Settings 各截静止图；确认静态彩色背景存在，API 33+ 的共享 viewport 实际记录 `HazeGlass`，普通行记录 `TintOnly`。
2. Liquid 模式有非纯色壁纸：重复上述截图，确认壁纸颜色能透过共享 viewport 和列表皮肤产生可见层次；不能只看依赖或 import。
3. Home、Device、Settings 各连续滑动 3 轮：记录 P95 帧时间、jank、内存、`effectSurfaceCount` 和 `sourceAttached`；滚动过程中 effect/source 不摘挂，effect 数量不随列表项组合上升。
4. Classic 模式回归一次：确认布局、文本、交互和现有美术资源不变。

本轮不复跑蓝牙 A-F、36/100 项矩阵，也不关闭 UI-1/UI-4；完成条件仍是 CI + 真实截图/运行诊断 + 定向性能报告。

## 历史记录：共享视口前的实现阶段（2026-08-16）

本次只处理 UI-ERR-001/002，不重新打开已经收口的蓝牙任务，也不复跑蓝牙 A-F/36/100 项矩阵。

### 已修改

- `AdaptiveCard` 增加显式 `SurfaceRole`；Home 扫描卡、连接横幅和 Device 电量卡提升为少量 `FeatureCard`，保存设备/扫描设备行、设置行和普通选项仍为 `TintOnly`，避免用“所有卡片都加玻璃”换取可见性。
- `SurfaceRenderer` 只为真实 effect surface 创建 `HazeInput.Sources`；typed glass 使用 `SurfaceProfile`、更明确的边缘折射高度/位移、受控色散和 `expandLayerBounds=true`。无有效壁纸仍按既定硬门回退 Material 3，不用默认渐变或透明层伪造输入。
- `DeviceScreen` 拆成独立的电量、后台同步、ANC、音频、双连、手势和关于区域；每个区域只收集对应的 `DeviceProps` 投影并 `distinctUntilChanged`，双连卡片保留地址 key。

### 本轮只需验证

1. `UI_GLASS_RENDERING_TARGETED`：有有效非纯色壁纸时，Home 的连接/扫描重点卡和 Device 的电量/ANC surface 记录实际 renderer、source 和 effect 数量；无壁纸仍记录 Material 3 fallback。
2. `UI_GLASS_PERFORMANCE`：只滚动 Home 保存设备列表和 Device 详情列表，分别记录 3 轮 P95 帧时间、jank、内存和 effect 数量；连接状态/电量变化期间确认未变化区域不出现整列跳动。

本轮不要求重新验证导航会话、设置更新流程、无障碍全矩阵或蓝牙实机矩阵。

## Follow-up 2：v4.4.2 / 101 对“效果几乎没有、滑动仍卡顿”的修复

### 代码调整

- `AppScaffold` 在 Liquid 模式下始终绘制静态 `LiquidGlassBackdrop`，用户壁纸改为可选增强层；因此没有壁纸、壁纸加载失败或 URI 无效时仍有真实 Haze source 输入，不再把正常 Liquid 使用场景硬退回 Material 3。
- `SurfaceRenderer` 提升 Liquid 模式的正文透明度和 Tint-only 行的边框/填充可见性；普通行仍不创建 Haze effect，避免用效果数量换视觉。
- `GlassScrollPerformance` 绑定 Home、Device、Settings、Scan、Gesture 和 ListeningStats 的 `LazyListState`。拖动期间 effect surface 临时使用低成本 Tint-only 皮肤，且窗口 source 停止采样；静止后恢复 `hazeGlass`/`hazeBlur`。
- Haze effect 恢复 `Adaptive` performance mode、`expandLayerBounds=false`；上一版为增强效果临时改成 `Default/true`，这会扩大滚动中的 GPU layer，是卡顿风险来源之一。
- Settings 允许直接开启 Liquid 模式，不再把壁纸选择当作前置条件。

### 当前验证门

仅对 `UI_GLASS_SCROLL_FIX` 做定向验证：

1. 无壁纸打开 Liquid：Home 和 Device 各截静止画面，诊断应为 `sourceAttached=1`；API 33+ 的关键 Feature surface 应为 `HazeGlass`，普通行应为 `TintOnly`。
2. 有非纯色壁纸打开 Liquid：对照静止截图，确认边缘折射/色散和背景层次明显增加。
3. Home、Device 各连续滑动 3 轮：记录滚动 P95 帧时间、jank、内存和 effect 数量；拖动期间 effect 数量应降为 0 或仅保留不可见区域，停止后恢复。
4. Classic 模式做一次回归：确认 Material 3 外观和交互不变。

GitHub Actions `31931436152` 已通过 diff-check、UI contract、Gradle 自动测试和 Debug/Release 构建；Debug SHA-256=`1ab62a8c823120783f459d2916dd654201df1d35ab484160b6bea0dbc7fd6731`，Release SHA-256=`0dc74de065a9380cb874b449c835879c1fea33fded65fd7719b4e44fac146dda`。UI-1/UI-4 仍为 `[~] 目标受阻`，等待以上定向报告；不重复蓝牙矩阵。

## UI-ERR-001：液态玻璃实际仍为毛玻璃（原始问题）

> 本节的“当前实现”描述是报告创建时的修复前代码快照，用于保留根因证据；修复后的实现与待关闭条件见“修复回灌”和“当前结论”。

### 严重程度

高。当前实现与计划中的 Haze 2.0 液态玻璃目标不一致，`HazeGlass` 名称会误导后续验证结果。

### 用户表现

界面看起来比旧版本更统一，但背景主要表现为模糊、着色和噪声，没有观察到真实的玻璃光学折射、边缘响应或受控 optics 效果。

### 代码证据

文件：`app/src/main/java/com/freebuds/controller/ui/foundation/surface/SurfaceRenderer.kt`

当前 `SurfaceRenderMode.HazeGlass` 和 `SurfaceRenderMode.HazeBlur` 共用同一套旧实现：

```kotlin
Modifier.hazeEffect(state = host.hazeState) {
    blurEffect {
        blurRadius = configuredSpec.blurRadius
        noiseFactor = ...
        colorEffects = listOf(HazeColorEffect.tint(...))
    }
}
```

具体位置：`SurfaceRenderer.kt:150-163`。

因此：

- `HazeGlass` 没有调用 `Modifier.hazeGlass`。
- 没有使用 `GlassStyle`。
- 没有使用 `GlassOptics.Adaptive` 或 `GlassOptics.Fixed`。
- `depth` 和 `refractionStrength` 没有驱动真实折射，只参与参数缩放、颜色和边框表现。
- `noiseFactor` 和 tint 只能增强毛玻璃材质感，不能构成液态玻璃。
- `AdaptiveGlass.kt` 只是 facade，无法改变底层仍使用 `hazeEffect`/`blurEffect` 的事实。

依赖边界：

- `app/build.gradle.kts` 仍使用 Haze `2.0.0-alpha03`。
- 当前没有 `dev.chrisbanes.haze:haze-glass:2.0.0-alpha05`。

### 根因

渲染模式枚举已提前加入 `HazeGlass`，但生产 adapter 尚未迁移到 Haze 2.0 alpha05 的 typed glass API，导致“目标架构名称”和“实际渲染实现”不一致。

### 修复要求

1. 将 Haze 相关依赖集中升级到 `2.0.0-alpha05`。
2. 增加并验证 `haze-glass:2.0.0-alpha05`。
3. 在 foundation surface adapter 中使用真正的 `Modifier.hazeGlass`、`GlassStyle` 和受控 `GlassOptics`。
4. 将 `hazeBlur` 明确限定为低特效路径和 glass fallback，不再将 blur 实现标记为 glass。
5. 保留 `hazeGlass → hazeBlur → tint → opaque` 回退链，但每一层必须对应真实不同的 renderer。
6. 对 Hero、连接横幅和少量 FeatureCard 验证真实 captured content、边缘光学响应和背景变化；长列表不得逐项创建 glass effect。
7. 将自定义 `liquidGlassOptics` 的品牌装饰与 Haze 官方 glass renderer 分离，不能用自定义绘制假装官方玻璃效果已完成。

### 验收条件

- Debug 诊断能区分实际 `HAZE_GLASS` 与 `HAZE_BLUR`，不能只根据请求模式记录 renderer。
- 截图和运行诊断同时证明 `HAZE_GLASS` 使用了真实 glass modifier 和有效输入源。
- 在 `TINT_ONLY`、`OPAQUE` 下仍保持相同布局、内容、触控和无障碍语义。
- 在滚动列表中只使用 tint/opaque 或少量 blur，不出现每行独立 glass effect。
- 未经截图、运行诊断和交互验证，不得将 UI-1 或液态玻璃迁移标记为完成。

## UI-ERR-002：液态玻璃列表滚动卡顿

### 严重程度

高。影响 Home、Device、Settings 等主要页面的基本滚动交互。

### 用户表现

在液态玻璃模式下滑动列表时明显掉帧、拖动不跟手；经典模式或减少卡片数量时可能减轻。

### 代码证据

文件：`app/src/main/java/com/freebuds/controller/ui/foundation/surface/SurfaceRenderer.kt`

`SurfaceRenderer.Card` 会对每个 effect surface 挂载背景采样和 blur：`SurfaceRenderer.kt:150-163`。

文件：`app/src/main/java/com/freebuds/controller/ui/HomeScreen.kt`

保存设备列表使用多个卡片，但未设置稳定 key：`HomeScreen.kt:106-113`。

文件：`app/src/main/java/com/freebuds/controller/ui/ScanScreen.kt`

扫描设备列表同样未设置稳定 key：`ScanScreen.kt:118-123`。

文件：`app/src/main/java/com/freebuds/controller/ui/DeviceScreen.kt`

设备功能页包含大量条件 item 和卡片，双连接设备列表也未建立稳定 key，约位于 `DeviceScreen.kt:149-285`。

文件：`app/src/main/java/com/freebuds/controller/ui/HomeScreen.kt`

以下 effect 会在连接阶段变化时重复刷新保存设备列表：

```kotlin
LaunchedEffect(deviceUiState.connection.systemConnected, deviceUiState.connection.stage) {
    viewModel.refreshSavedDeviceConnections()
}
```

具体位置：`HomeScreen.kt:45-47`。

### 根因

- 滚动视口内多个卡片同时进行 Haze 背景采样和 blur。
- `HazeGlass` 当前实际也是 blur，因此无法获得 glass 预期效果，却承担了 blur 的 GPU 成本。
- 列表没有稳定 key，设备状态变化或排序变化时会增加 item 复用和重组成本。
- 连接阶段每次变化都刷新保存设备列表，额外触发列表更新和卡片重组。
- 页面顶层收集较大的设备状态，协议字段变化可能让整个列表重新组合。

Canvas 短划线不是当前最主要的卡顿来源。

### 修复要求

1. Hero 或连接横幅保留少量 Haze glass；StandardCard、CompactRow 和长列表默认使用 tint/opaque，必要时才使用低强度 blur。
2. 为保存设备、扫描设备和双连接设备添加稳定 key，优先使用设备地址。
3. 只在保存设备集合或目标设备地址真正变化时刷新保存设备状态，避免对每个连接阶段重复提交刷新命令。
4. 将连接横幅、设备卡片和列表区域拆成更窄的状态订阅；使用生命周期感知的 StateFlow 收集方式。
5. 对页面内重复的选项映射和派生计算使用 keyed `remember`，避免每次协议状态更新都重新计算。
6. 展开区域只保留一种尺寸/可见性动画，避免同时对同一内容使用 `animateContentSize` 和 `AnimatedVisibility`。

### 验收条件

- `UI_GLASS_PERFORMANCE` 记录经典、tint、blur、glass、opaque 五种模式的首屏、滚动 P95 帧时间、jank、内存和 effect 数量。
- Home、Device、Settings 的长列表滚动不能为每个列表行创建独立 glass effect。
- 设备状态更新时，未变化的列表 item 不应整体重组。
- 稳定 key 覆盖保存设备、扫描设备和双连接设备。
- 真实设备或代表性 Android 12+ 环境完成滚动交互验证后，才能关闭该问题。

## UI-ERR-003：自动进入详情后返回，主界面设备卡片无法再次进入

### 严重程度

高。阻断设备详情页的基本导航流程。

### 用户表现

系统识别到耳机连接后自动进入详情页。用户退出详情页回到 Home 后，再点击同一耳机卡片，有时无法进入详情页；部分情况下详情页也表现为无法退出或返回后再次被自动打开。

### 代码证据

文件：`app/src/main/java/com/freebuds/controller/ui/AppNavHost.kt`

详情页返回时设置：

```kotlin
userLeftDevice = true
navController.popBackStack(Route.Home, inclusive = false)
```

具体位置：`AppNavHost.kt:137-140`。

自动导航条件为：

```kotlin
if (deviceUiState.connection.isReady && currentRoute == Route.Home && !userLeftDevice) {
    navController.navigate(Route.Device) { launchSingleTop = true }
}
```

具体位置：`AppNavHost.kt:71-74`。

但 Home 卡片点击只发送连接请求：

```kotlin
onDeviceClick = { address -> viewModel.autoConnectSaved(address) }
```

具体位置：`AppNavHost.kt:105-110`。

该回调没有：

- 重置 `userLeftDevice`。
- 显式导航到 `Route.Device`。
- 区分“用户主动打开详情”和“系统自动打开详情”。

### 根因

`userLeftDevice` 被用于同时控制用户返回和自动导航，但没有与具体连接会话绑定。用户返回后它保持 `true`，而卡片点击只重复发送已经连接设备的连接命令，不会产生新的连接状态变化，因此自动导航条件不会再次满足。

自动导航又依赖整个 `deviceUiState.connection` 和当前 route 的变化，导致返回、重连、连接阶段变化时出现竞争或重复跳转。

### 修复要求

1. Home 设备卡片点击必须显式导航到 `Route.Device`，不能依赖连接命令触发导航。
2. 用户主动点击卡片时清除当前会话的自动跳转抑制状态。
3. 自动跳转只响应一次明确的系统连接事件，并与设备地址/连接 attempt 绑定。
4. 用户返回详情后，在同一连接会话内禁止自动重新打开详情。
5. 新连接会话开始时重新计算自动跳转资格。
6. 所有详情页返回入口统一使用同一个导航操作，避免部分入口只 `popBackStack()`、部分入口按 Route 回退造成栈行为不一致。
7. `launchSingleTop` 只能防止重复 destination，不能代替导航意图和会话状态管理。

### 验收条件

至少完成以下流程各 5 轮：

1. 系统已连接 → 自动进入详情 → 返回 Home → 点击设备卡片 → 成功进入详情。
2. 系统已连接 → 自动进入详情 → 返回 Home → 不点击卡片 → 不自动再次进入详情。
3. Home 点击未连接的保存设备 → 发起连接 → 按连接阶段正常进入详情或保持 Home，不重复 push。
4. 详情页进入 Gesture、ListeningStats、Settings 后逐级返回，最终只回到预期页面。
5. 连接失败、断开、重连和替换设备时，自动跳转抑制状态不会泄漏到下一台设备。
6. 系统连接广播、Service 自动连接、扫描选择和 Home 手动点击不会产生重复详情页实例。

## 建议修复顺序

1. 先修复导航意图和连接会话绑定，避免用户无法操作设备详情。
2. 再将列表中的大量 effect 降级为 tint/opaque，并增加稳定 key，验证滚动基线。
3. 最后升级 Haze 到 alpha05，接入真正的 `hazeGlass`/`GlassStyle`/`GlassOptics`，只在少量 surface 上启用真实液态玻璃。
4. 每一步分别运行构建、JVM/静态检查、Compose 语义测试、截图矩阵和真实滚动/导航交互验证。

## 当前结论

`v4.4.3 / 102` 在上一轮基础上改为共享 viewport：Liquid 始终有静态彩色 source，每个 LazyColumn 只保留一个稳定的真实 typed glass viewport，普通行使用可见但低成本的 Tint-only 玻璃皮肤；拖动期间不再切换 source/effect，列表行不再观察滚动状态。Device 页面按区域订阅精简状态，上一轮的稳定 key、生命周期感知收集、Home 地址刷新和 `address + attemptId` 导航边界继续保留。

问题仍保持“目标受阻”，因为代码修复和 CI 通过不能替代真实输入下的截图/运行诊断、滚动性能和导航交互证据。报告返回前，不将 UI-ERR-001/002/003 标记为完全关闭。
