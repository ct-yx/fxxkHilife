# UI 与蓝牙指令/连接重构规划

> 状态：执行中；当前按“先蓝牙、后 UI”的顺序推进
>
> 基线：v4.2.6，2026-08-01
>
> 目标：把当前“能工作但边界偏大”的 UI、蓝牙连接和 SPP 指令实现整理成可持续迭代的结构。先稳定现有 HUAWEI / HONOR + RFCOMM SPP 路线，再为后续型号和协议扩展留出边界。

> 测试模式日志约定：Debug“自动实机测试”每次启动都会清空旧缓存，测试期间不设日志行数上限，改用约 160,000,000 字节的 UTF-8 缓存预算；导出的最终报告在写文件前再限制为 190,000,000 字节以内，确保小于 200 MB。正常日志策略在报告构建完成后恢复。

## 0. 执行状态、完成标记与版本基线

### 0.1 完成标记规则

每完成一个阶段，必须在本文件的阶段标题和下表同步更新状态：

```text
[ ] 未开始
[~] 进行中
[x] 已完成
```

阶段只有在代码/文档、自动化测试、实机回归和回退记录都齐备后，才能标记为 `[x] 已完成`。每次标记还要记录：

- 完成日期。
- 对应提交或发布版本。
- 自动化测试结果和实机型号/系统版本。
- 关键日志或耗时结论。
- 回退方式。

当前已完成的是 **BT-0.1 的本地协议/能力代码审计与资料存在性核对**；这不代表所有型号和所有指令都已经实机验证。连接速度实测资料已经开始回收，BT-0.2 保持进行中，尚未进入决策门。

### 0.2 阶段总表

| 阶段 | 状态 | 当前记录/完成条件 |
|---|---|---|
| BT-0.1 协议/能力本地审计 | [x] 已完成 | 2026-08-01；完成现有 Kotlin 协议、能力表、上游资料快照存在性核对；未提升未经实机验证的能力状态 |
| BT-0.2 连接速度实机基线 | [~] 进行中 | 已加入 attemptId/阶段计时/触发来源日志；已收到主设备两份初始化日志，仍需同一设备每个场景至少 10 次，产出 P50/P95、失败原因和阶段耗时 |
| BT-0.3 连接路线决策门 | [ ] 未开始 | 依据 BT-0.2 的数据决定先改端点、初始化调度或连接编排 |
| BT-1 唯一生产 Transport | [~] 进行中 | 已切换到唯一 `RfcommSppTransport` Socket 路径并通过本地自动化回归；仍待主设备实机回归后才能完成 |
| BT-2 CommandClient / Scheduler / Feature | [ ] 未开始 | 指令目录、请求队列和 Feature 边界稳定，核心命令实机回归通过 |
| BT-3 ConnectionManager 收敛入口 | [ ] 未开始 | ACL/A2DP/HEADSET/Service/Tile/周期检查不会并行建连 |
| BT-4 通用蓝牙状态输出 | [ ] 未开始 | 输出 SystemLink、Transport、Core、Deferred 分层状态，供 UI-1 消费 |
| UI-0 UI 行为基线 | [ ] 未开始 | 截图、交互路径、字段读写清单和测试基线齐备 |
| UI-1 UI State / Event | [ ] 未开始 | 页面通过 typed state/event 工作，保留兼容层 |
| UI-2 页面与公共组件拆分 | [ ] 未开始 | Device/Settings 和公共外壳拆分完成 |
| UI-3 全局状态与持久化 | [ ] 未开始 | `AppUiState`、`SettingsRepository`/DataStore 和导航事件收敛 |
| UI-4 移除兼容层 | [ ] 未开始 | 页面不再依赖 raw property，classic/glass 共用同一能力判断 |

### 0.3 应用版本号方案

当前应用版本冻结为 **versionName `4.2.6` / versionCode `87`**，作为本次重构的基线。BT-0 的审计、研究、日志和测试准备不单独发版，也不在本轮修改应用版本号。

| 里程碑 | 计划 versionName | 计划 versionCode | 说明 |
|---|---:|---:|---|
| BT-0 文档/诊断基线 | 4.2.6 | 87 | 保持当前基线，不发布 |
| BT-1 Transport 统一 | 4.3.0 | 88 | 第一版可回归的蓝牙传输重构 |
| BT-2 指令调度与 Feature | 4.3.1 | 89 | 命令目录、队列和能力拆分 |
| BT-3 连接管理收敛 | 4.3.2 | 90 | 单一连接编排入口 |
| BT-4 通用蓝牙状态输出 | 4.3.3 | 91 | 分层连接状态和通用状态输出 |
| UI-1 + UI-2 | 4.4.0 | 92 | UI State/Event 与页面组件拆分 |
| UI-3 全局状态与持久化 | 4.4.1 | 93 | 全局状态、设置仓库和导航收敛 |
| UI-4 移除兼容层 | 4.5.0 | 94 | 完成 UI 重构；若产生不兼容变更，再单独评估大版本 |

版本规则：

- 只有能独立编译、测试、回归和回退的代码里程碑才提升版本号；只改本文件或只做诊断不提升版本。
- `versionCode` 每次发布递增 1；表中的 code 以当前 87 为基准，若期间已有其他发布，执行时以实际最新值顺延。
- 统一使用 `python3 scripts/bump_version.py <versionName> <versionCode> "变更说明"` 更新应用版本、资源、README、`VERSION_MANAGEMENT.md` 和 `DEVELOPMENT_LOG.md`。
- 阶段完成时，同时更新本文件的 `[x]`、`VERSION_MANAGEMENT.md` 的历史记录和 `DEVELOPMENT_LOG.md`；版本号不因提前勾选计划项而变更。

本节对应文件：`app/build.gradle.kts`、`app/src/main/res/values/strings.xml`、`VERSION_MANAGEMENT.md`、`scripts/bump_version.py`。

## 1. 范围与原则

本次只规划两个大项：

1. **UI 规范与重构**：页面拆分、状态模型、导航、组件、主题/壁纸、文案和交互状态。
2. **蓝牙指令与连接重构**：系统蓝牙连接、RFCOMM/SPP 传输、5A 封包、请求响应、Handler、能力和状态映射。

两项不能一次性“大爆炸”重写，采用兼容层 + 小步迁移：

- 重构期间保持现有 UI 行为和设备控制能力。
- UI 不再知道厂商命令、`group/prop` 或原始参数。
- 蓝牙传输层不再知道 UI 状态；协议层不再负责页面导航和持久化。
- 未验证的设备能力继续降级隐藏，不因重构扩大误报范围。
- 每个阶段都能独立编译、测试和回退。

## 2. 当前结构基线

```mermaid
flowchart TD
    UI[Compose Screens\nHome / Scan / Device / Settings / Gesture]
    VM[DeviceViewModel\n薄透传层]
    Repo[DeviceRepository\n连接 + 轮询 + 属性 + 统计 + 自动连接]
    Session[LegacySppEarbudSession]
    Driver[SppDriver\nSocket + 收包 + 请求 + Handler + 属性]
    Handler[OpenFreebudsHandlers.kt\n多功能 Handler 集合]
    Protocol[HuaweiSppPackage / HuaweiSppProtocol / Framer]
    Transport[RfcommSppTransport / RfcommSocketBridge]
    Service[BluetoothService / QuickSettingsTile / ACL Receiver]

    UI --> VM --> Repo --> Session --> Driver
    Driver --> Handler
    Driver --> Protocol
    Driver --> Transport
    Service --> Repo
    Service --> Driver
```

现有骨架已经具备 `core/transport`、`core/protocol`、`core/session` 和 `core/adapter`。BT-1 迁移后，生产 Socket、输入流、输出流和关闭逻辑统一由 `RfcommSppTransport` 承载；`SppDriver` 只保留 Huawei 分帧会话、请求响应、Handler 和属性兼容层。后续不再增加第二套并行 Socket 实现。

## 3. 大项一：UI 规范与重构

### 3.1 现存问题

#### A. 页面文件承担过多职责

- `DeviceScreen.kt`、`SettingsScreen.kt` 体积过大，页面布局、原始属性读写、选项中文映射、持久化和交互状态混在一起。
- `AppNavHost.kt` 同时持有导航、主题、语言、壁纸、玻璃配置和连接态自动跳转。
- `DeviceViewModel` 基本是 Repository 的透传层，缺少面向页面的 UI 状态和用户事件。

#### B. UI 直接依赖底层属性协议

当前页面存在类似以下调用：

```kotlin
viewModel.setProperty("anc", "mode", value)
viewModel.setProperty("config", "low_latency", value)
viewModel.setProperty("dual_connect", "preferred_device", address)
```

这会导致：

- UI 必须知道厂商属性组、属性名和字符串值。
- 写入中的 loading、成功、失败、读回确认没有统一模型。
- `DeviceProps` 继续横向增加字段，功能越多页面越难维护。
- 选项显示值与原始值通过页面内 `chineseTap`、`chineseSwipe` 等函数映射，容易重复和错位。

#### C. 展示系统和配置状态分散

- `CLASSIC` / `LIQUID_GLASS`、`HazeState`、`LiquidGlassConfig` 参数在多个页面间层层传递。
- 主题、语言、壁纸和显示模式直接在 Compose 页面中读取/写入 `SharedPreferences`。
- 每个页面自行实现 `Scaffold`、`TopAppBar`、卡片间距和 section header，规范难以统一。

#### D. 导航与连接状态耦合

- 连接成功后由 `AppNavHost` 通过 `LaunchedEffect` 自动跳转详情页。
- Home、Scan、Device 都有相似的“连接后进入详情”判断。
- 用户返回、手动断开、自动连接和系统 ACL 断开之间缺少统一的导航事件语义。

#### E. 连接入口存在异步时序差异

- `AppNavHost` 在调用连接方法后立即读取 `connectionState`，但 Repository 的连接工作在协程中执行；调用返回时通常还处于 `Connecting`。
- Scan 完成回调会尝试自动连接，设备行点击又可能再次发送连接意图；两个入口需要统一去重。
- `MainActivity.onCreate` 和 `onResume` 都会启动 Service 自动连接命令，Service 目前依靠状态检查降低重复建连风险，但 UI 没有统一的 `attemptId` 或触发来源。
- 页面只显示 Connected/Connecting 等粗粒度状态，无法表达“控制通道已建立但核心能力仍初始化中”。

重构后，导航应监听 `DeviceUiState` 中的稳定状态和一次性 `NavigationEvent`，不再依赖点击回调返回瞬间的状态快照。

### 3.2 目标 UI 分层

```text
ui/
  app/
    AppState.kt                 # 全局主题、语言、导航、连接摘要
    AppEvent.kt                 # 全局用户事件
    AppNavHost.kt               # 只负责路由和事件分发
    AppScaffold.kt              # 统一页面外壳
  device/
    DeviceRoute.kt              # collect 状态、调用 ViewModel
    DeviceScreen.kt             # 纯渲染
    DeviceUiState.kt            # 页面状态
    DeviceEvent.kt              # 页面事件
    components/                 # 电池、ANC、音频、双连、手势卡片
  settings/
    SettingsRoute.kt
    SettingsScreen.kt
    SettingsUiState.kt
    SettingsEvent.kt
  components/
    AppTopBar.kt
    SettingsSection.kt
    SettingRow.kt
    AsyncActionIndicator.kt
    ConnectionBanner.kt
  theme/
    Theme.kt
    UiTokens.kt
    DisplayStyle.kt
```

推荐依赖方向：

```text
Screen/Route -> ViewModel -> UseCase/Repository -> EarbudSession
Screen/Route -> UiState / UiEvent
UI components -> semantic tokens only
```

### 3.3 状态模型目标

短期保留 `DeviceProps` 作为兼容快照，新增通用模型并逐页迁移：

```kotlin
data class EarbudState(
    val identity: DeviceIdentity = DeviceIdentity(),
    val connection: ControlConnectionState = ControlConnectionState.Disconnected,
    val battery: BatteryState? = null,
    val noiseControl: NoiseControlState? = null,
    val audio: AudioState? = null,
    val gestures: GestureState? = null,
    val wearing: WearingState? = null,
    val dualConnect: DualConnectState? = null,
    val diagnostics: DiagnosticsState = DiagnosticsState(),
)

data class DeviceUiState(
    val state: EarbudState = EarbudState(),
    val screenStatus: ScreenStatus = ScreenStatus.Loading,
    val pendingAction: DeviceAction? = null,
    val actionError: UiError? = null,
)

sealed interface ControlUiState {
    data object Disconnected : ControlUiState
    data class Connecting(val trigger: ConnectionTrigger) : ControlUiState
    data object TransportReady : ControlUiState
    data class Initializing(val completed: Int, val total: Int) : ControlUiState
    data object Ready : ControlUiState
    data class Degraded(val failedCapabilities: Set<EarbudCapability>) : ControlUiState
    data class Failed(val reason: UiError) : ControlUiState
}
```

约定：

- `null` 表示设备没有提供该能力或尚未得到结果；不要用 `0`、空字符串代替未知。
- `capabilities` 决定是否展示功能；页面只消费已经映射好的 `EarbudState`。
- `ScreenStatus` 至少区分 `Loading`、`Ready`、`Degraded`、`Disconnected`、`Error`。
- `ControlUiState` 还要区分 `Connecting`、`TransportReady`、`Initializing` 和 `Ready`，避免把 RFCOMM 成功误显示成全部功能已经可用。
- 写操作必须有 `Pending/Success/Failure` 状态，并支持设备读回确认。
- raw 值到展示文案的映射放到 `UiTextMapper` / `OptionPresenter`，不要散落在页面。
- 连接提示横幅只消费 `ControlUiState` 和结构化失败原因；页面不根据日志字符串猜测当前阶段。

### 3.4 UI 事件目标

把字符串写入改成有类型的事件：

```kotlin
sealed interface DeviceEvent {
    data class SetAncMode(val mode: AncMode) : DeviceEvent
    data class SetAncLevel(val level: AncLevel) : DeviceEvent
    data class SetLowLatency(val enabled: Boolean) : DeviceEvent
    data class SetGesture(val target: GestureTarget, val action: GestureAction) : DeviceEvent
    data object Refresh : DeviceEvent
    data object OpenTerminal : DeviceEvent
}
```

ViewModel 负责把事件交给用例；用例再调用协议无关的控制接口。页面中不出现 `setProperty(group, prop, value)`。

### 3.5 UI 规范

- 采用 Apple/HIG-inspired 的清晰层级 + Material 3 语义组件；颜色只使用语义色，不在页面硬编码近似色值。
- 统一 `AppScaffold`、顶部栏、section 标题、设置行、状态横幅、空状态和错误状态。
- 普通/玻璃展示模式共享内容组件，只替换 surface/render profile；避免每个页面各写一份布局。
- 交互目标至少 44dp；支持键盘焦点、TalkBack、较大文字、深色/浅色和 reduced motion。
- 统一 loading、disabled、pending、失败重试和成功反馈，不用瞬时乐观值掩盖失败。
- 设置持久化迁移到 `SettingsRepository`，优先使用 DataStore；Composable 只读写状态，不直接操作 `SharedPreferences`。
- 所有用户可见文字走 i18n key；协议 raw 值只能出现在调试终端和日志页面。

#### 3.5.1 Haze 2.0 保留方案

继续使用当前 Haze 2.0，不在本次 UI 重构中替换或升级玻璃渲染依赖：

- 当前锁定 `haze:2.0.0-alpha03`、`haze-blur:2.0.0-alpha03`、`haze-blur-materials:2.0.0-alpha03`。
- `LIQUID_GLASS` 模式继续使用 Haze 2.0；`CLASSIC` 模式使用 Material 3 surface/render profile。
- Haze 只负责模糊、玻璃材质和渲染层，不参与连接、设备能力、页面导航或持久化状态。
- `HazeState` 集中由 `AppScaffold` / `AdaptiveGlassSurface` 管理，页面和业务 ViewModel 不直接创建或修改它。
- 普通模式与玻璃模式共享同一内容组件、`UiState` 和能力隐藏规则，只替换 surface/render profile。
- Haze 依赖升级另开独立变更；UI-1 至 UI-4 期间不与状态、导航、组件拆分混合升级，避免难以定位构建和视觉回归。

相关实现文件：

- `app/src/main/java/com/freebuds/controller/ui/glass/AdaptiveGlass.kt`
- `app/src/main/java/com/freebuds/controller/ui/glass/LiquidGlassConfig.kt`
- `app/build.gradle.kts`

### 3.6 UI 迁移阶段

#### UI-0：建立基线 `[ ] 未开始`

- 记录 Home、Scan、Device、Gesture、Settings 的截图和主要交互路径。
- 列出所有 `DeviceProps` 字段、页面读取位置和写入入口。
- 为 `EarbudStateMapper`、导航和关键选项映射补充单元测试。

#### UI-1：先抽状态和事件 `[ ] 未开始`

- 新增 `DeviceUiState`、`DeviceEvent` 和 `DeviceViewModel` 的事件入口。
- 保留 `DeviceProps` 与旧 `setProperty` 作为兼容层。
- 先迁移 ANC、低延迟、音质偏好三个高频控制项，验证 pending/读回/失败状态。
- 增加 `ControlUiState` 和一次性 `NavigationEvent`；连接发起后由状态流驱动页面反馈，成功后由事件驱动导航。
- 统一设备行点击、扫描完成自动连接、Service 自动连接和 Tile 连接命令的触发来源与去重规则。

#### UI-2：拆页面与公共组件 `[ ] 未开始`

- 将 `DeviceScreen` 拆成 `BatteryCard`、`NoiseControlSection`、`AudioSection`、`DualConnectSection`、`GestureEntryCard` 等独立组件。
- 将 `SettingsScreen` 拆成主题、语言、壁纸、连接偏好、调试、关于等 section。
- 抽出公共 `AppScaffold`、`AppTopBar`、`SettingsSection` 和 `SettingRow`。

#### UI-3：收拢全局状态与持久化 `[ ] 未开始`

- `AppNavHost` 只保留 route 和导航事件，不再管理具体设置写入。
- 建立 `AppUiState`，统一主题、语言、壁纸、显示风格和连接摘要。
- 将各类 `SharedPreferences` key 集中管理，迁移到 `SettingsRepository` / DataStore。
- 连接摘要只展示当前设备的 SystemLink、ControlChannel 和 CoreReady 三个必要层级；详细阶段耗时放到诊断页。

#### UI-4：移除兼容依赖 `[ ] 未开始`

- 所有页面改为消费 `EarbudState` 和 typed event。
- 删除 UI 中的 raw group/prop、中文协议值映射和重复 option 组件。
- 对 classic/glass 两种展示模式做同一组 UI 测试，确认能力隐藏规则一致。

## 4. 大项二：蓝牙指令与连接重构

### 4.1 现存问题

#### A. `SppDriver` 的命令边界仍需继续收敛

BT-1 已移除 `SppDriver` 对 Socket、输入输出流和原始收包循环的直接持有；当前它仍负责：

- 发送串行化、请求响应等待、超时和 pending response。
- Handler 注册、命令分发、属性仓库和初始化入口。
- 断开回调、日志和旧 Handler 兼容桥接。

这些职责将在 BT-2 迁移到 `CommandClient`、`CommandScheduler` 和按能力划分的 Feature；BT-1 不提前扩大修改范围。

#### B. 重复 Transport 路径（BT-1 已处理）

当前只有 `RfcommSppTransport` 建立/关闭 RFCOMM Socket、读原始字节和写输出流；`SppDriver` 通过 `ProtocolSession` 使用它。该项仍保留在问题清单中，是为了记录迁移前的风险，验收证据见 BT-1 进度记录。

#### C. 指令定义和 Handler 组织分散

- `HuaweiSppCommand.kt` 只收录了部分常量，仍有大量 `b(0x.., 0x..)` 散落在 `OpenFreebudsHandlers.kt`。
- 读、写、异步通知、ACK、读回确认、是否等待响应等语义靠调用位置推断。
- 多个功能 Handler 集中在一个约 800 行文件中，能力声明、解析、写入和兼容逻辑不易定位。
- Handler 接口直接接收 `SppDriver`，业务层无法替换协议会话。

#### D. 连接编排和轮询分散

- `DeviceRepository` 同时管理连接、ACL 广播、自动连接、初始化、前后台轮询、低延迟重试、统计和属性映射。
- `BluetoothService`、Quick Settings Tile 和页面都能触发控制动作。
- 系统蓝牙连接与 App 的 SPP 控制通道已经在 UI 中分开显示，但底层还没有明确的状态机。
- 双连目前是耳机侧能力，Android 仍只维护一个 active SPP session；该限制需要变成明确契约，而不是隐藏在实现里。

#### E. “连接慢”缺少阶段定义

当前至少存在三段不同的时间，页面和日志还没有稳定地区分它们：

```text
系统蓝牙链路建立
  -> RFCOMM/SPP Socket 建立
  -> 协议初始化和核心状态可用
```

当前实现中，Socket 成功后就在 `DeviceRepository` 标记 `Connected`，随后才开始初始化。用户看到的“连接完成”与“电量/ANC/低延迟可用”因此可能不是同一个时刻。

已确认的初始化等待来源：

- 初始稳定等待：250ms。
- 自动低延迟优先写入：写入、ACK/响应和回读合计最多约 2 秒，另有 Repository 的 150ms 同步等待。
- 核心 Handler：单通道串行，每个请求最多 1 秒，命令之间间隔 80ms。
- 核心失败后再次执行一轮核心初始化，重试间隔 700ms。
- 核心阶段完成后再等待 1 秒，才进入延迟 Handler。
- 自动低延迟后续重试与延迟初始化分属不同 Job；两者最终仍会争用同一 `requestMutex`，可能拉长延迟初始化。

因此，重构前必须先把 `TransportReady`、`CoreReady`、`Ready` 和 `Degraded` 分开统计；先优化阶段边界，再调整具体延迟值。

### 4.2 指令基线清单

以下是按当前代码整理出的第一版指令目录。迁移时以它为单一清单，逐条补齐：方向、参数、响应、通知、超时、重试、能力和验证状态。

| 功能 | 读取/状态 | 写入/动作 | 备注 |
|---|---|---|---|
| 电量 | `01 08` | — | `01 27` 为通知 |
| ANC | `2b 2a` | `2b 04` | `2b 2c` 为状态通知；存在旧包覆盖新状态问题 |
| 低延迟 | `2b 6c` | `2b 6c` | 写入后读回确认 |
| 音质偏好 | `2b a3` | `2b a2` | 写入可能只收到异步 `2b a3` |
| 自动暂停 | `2b 11` | `2b 10` | ACK 后读回确认 |
| 双击 | `01 20` | `01 1f` | 左右参数 |
| 三击 | `01 26` | `01 25` | 左右参数 |
| 长按 | `2b 17` / `2b 19` | `2b 16` / `2b 18` | 基础/ANC 分路 |
| 滑动 | `2b 1f` | `2b 1e` | 参数编码需统一 |
| EQ | `2b 4a` | `2b 49` | Custom payload 仍按能力和验证情况限制 |
| 双连开关 | `2b 2f` | `2b 2e` | 耳机侧双连 |
| 双连设备 | `2b 31` / `2b 36` | `2b 32` / `2b 33` | 枚举和变化事件 |
| 语音语言 | `0c 02` | `0c 01` | 文本和语言参数 |
| 设备信息 | `01 07` | — | 固件、型号、序列等字段 |
| 佩戴检测 | `2b 03` | — | 主动推送 |

注：该表是代码基线，不代表所有型号都支持。每条指令都必须绑定型号能力和实机验证记录。

### 4.3 目标蓝牙分层

```text
BluetoothSystemLink
  └─ 只负责系统蓝牙状态、ACL/A2DP/HEADSET 观察和设备发现

EarbudTransport
  └─ 只负责 RFCOMM/BLE 等原始字节流的连接、收发和断开

ProtocolFramer / EarbudProtocol
  └─ 负责分帧、封包、解包、校验和协议包模型

CommandClient
  └─ 负责请求响应、串行队列、超时、重试、ACK、读回确认和取消

FeatureHandler
  └─ 按功能解析参数、生成命令、发布 typed state/event

EarbudSession
  └─ 组合 Transport + Protocol + CommandClient + FeatureHandlers

ConnectionManager / StateStore
  └─ 管理 session 生命周期和通用 EarbudState
```

#### 4.3.1 第三方连接逻辑对比结论

对 APP_A、APP_B、APP_C 的只读代码检查显示，它们虽然实现语言和平台不同，但连接边界基本一致：

| 方案 | 系统蓝牙前提 | RFCOMM 端点 | 连接编排 | 初始化关系 |
|---|---|---|---|---|
| APP_A | 先确认设备处于系统 connected | 标准 SPP UUID | 单一 `_connection`，失败最多 3 次，间隔约 50ms | Socket 成功后立即启动读流和协议层 |
| APP_B | Service 负责后台连接 | 支持固定 UUID / 动态 UUID | Android Service + 独立 Backend；读写和状态回调分开 | `connect()` 返回后再创建设备会话和状态初始化 |
| APP_C | 连接前显式取消 discovery | 型号声明的 Service UUID | 写队列 + 读线程；一个队列管理 Socket 生命周期 | Socket 完成后再排队执行初始化事务 |
| 当前项目 | ACL/A2DP/HEADSET/周期检查均可触发 | 隐藏 API + 固定 `port=1` | Service、Repository、页面和 Tile 多入口 | Repository 标记 Connected 后启动多阶段初始化 |

可吸收的共同原则：

1. **系统蓝牙连接和 App 控制通道是两个状态。** 系统 connected 只代表 RFCOMM 建连的前提，不代表协议已经 Ready。
2. **一个设备只有一个连接编排者。** UI、Tile 和广播只发送连接命令，不直接创建 Socket。
3. **端点选择必须进入型号配置。** 优先使用已验证的 Service UUID；只有有实机证据时才使用固定 channel/port，并记录 fallback 顺序。
4. **读流和写队列从协议 Handler 分离。** Socket 生命周期、EOF、写入失败和协议响应分别有自己的状态事件。
5. **初始化不能反向阻塞传输就绪。** Socket 可用后先发布 `TransportReady`，核心状态和可选能力在后续阶段完成。

这些项目没有证明“UUID 一定比固定端口快”。UUID 方案首先解决的是服务选择和型号兼容；速度是否改善，必须在同一设备、同一系统、同一连接前提下实测。

#### 4.3.2 连接状态与时间线

新增统一的连接事件模型，所有事件带同一个 `attemptId`：

```kotlin
enum class ConnectionPhase {
    Requested,
    SystemLinkObserved,
    DiscoveryStopped,
    TransportConnecting,
    TransportReady,
    Settling,
    CoreInitializing,
    CoreReady,
    DeferredInitializing,
    Ready,
    Degraded,
    Failed,
    Disconnecting,
    Disconnected,
}

data class ConnectionAttempt(
    val attemptId: String,
    val address: String,
    val trigger: ConnectionTrigger,
    val endpoint: RfcommEndpoint,
    val phase: ConnectionPhase,
    val retryIndex: Int,
    val startedAtElapsedMs: Long,
)
```

`ConnectionAttempt` 是诊断模型，不要求直接暴露给普通 UI。日志至少记录：

- 触发来源：手动选择、Service 命令、ACL、A2DP、HEADSET、周期检查。
- 系统链路状态、discovery 是否 active、选中的 UUID/port 和 endpoint 来源。
- `TransportConnecting -> TransportReady` 耗时。
- `TransportReady -> CoreReady` 耗时。
- `CoreReady -> Ready` 耗时。
- 每个请求的 command、response、排队等待、响应等待、重试次数和超时原因。
- 断开来源：ACL、EOF、写失败、初始化失败、手动断开或 Service 销毁。

用户可见状态只保留较少的语义：

```text
Connecting       = Requested / TransportConnecting / Settling
Connected        = TransportReady
Initializing     = CoreInitializing / DeferredInitializing
Ready            = Ready
Limited          = Degraded
Disconnected     = Failed / Disconnected
```

这样既能让页面尽快显示控制通道已建立，也能让电量、ANC 等功能明确显示“初始化中”，避免把协议初始化时间误判为 Socket 连接时间。

#### 4.3.3 RFCOMM 端点策略

端点由 `RfcommTransportConfig` 统一承载，并由型号适配器提供：

```kotlin
data class RfcommTransportConfig(
    val serviceUuid: UUID? = null,
    val channel: Int = 1,
    val source: EndpointSource,
    val connectTimeoutMs: Long = 10_000L,
)

enum class EndpointSource {
    VerifiedModelConfig,
    ServiceRecord,
    RuntimeDiscovery,
    CompatibilityFallback,
}
```

策略顺序：

1. 型号已有实机证据的 Service UUID。
2. 型号能力表明确记录且已验证的 channel/port。
3. Android Service Record 动态发现。
4. 兼容 fallback；每次使用都记录原因和结果。

当前实现状态与约束：

- `HuaweiModel.sppPort`、Service UUID 和最终 Transport 端点必须来自同一份配置，禁止表中有值但连接层继续硬编码。
- 已知型号的 channel 已通过 `RfcommTransportConfigProvider` 传入 Transport；未知名称使用 channel 1 的兼容 fallback，并在日志中记录 `source`。
- 当前 Android bridge 仍使用 channel API；`serviceUuid` 已进入配置契约并记录，Service Record/UUID 选择留待后续端点实机证据。
- 未验证端点不能静默覆盖已验证端点。
- UUID 与 channel 的对比测试必须固定设备、系统版本、蓝牙系统连接状态和 discovery 状态。
- `socket.connect()` 必须由 Transport 统一执行，并有取消和应用层超时；超时后关闭当前 Socket，再进入统一 backoff。

#### 4.3.4 初始化调度策略

将初始化拆成三个级别，避免所有能力都参与“连接完成”的判定：

| 级别 | 内容 | Ready 影响 | 失败处理 |
|---|---|---|---|
| Transport | Socket、输入输出流、接收循环 | 产生 `TransportReady` | 关闭 Socket，进入重试 |
| Core | 页面首屏需要的 ANC、电量、低延迟、音质等 | 决定 `Ready` 或 `Degraded` | 单命令超时，保留可用能力 |
| Deferred | 设备信息、手势、EQ、双连枚举、语言等 | 不阻塞控制通道 | 后台 best-effort，按能力降级 |

自动低延迟需要单独建模为 `ConnectionAction`，不要和核心读取共享一个“初始化完成”标志：

- 先记录写入开始、ACK、读回确认和最终结果。
- 基线测试时分别比较“自动低延迟关闭”和“自动低延迟开启”。
- 若目标是缩短首屏可用时间，将低延迟写入放到 `Ready` 之后，或让它使用独立的低优先级队列。
- 低延迟后续重试不能与 Deferred 初始化并行争用同一请求队列；应由一个 Scheduler 排队。
- `sppQuietUntil` 必须成为 Scheduler 的实际门控条件，不能只被 fast polling 检查。

当前策略的最慢核心路径可按以下方式估算：

```text
250ms settle
+ 4 个请求 × 1000ms timeout
+ 5 个 80ms inter-command gap
+ 700ms retry gap
+ 第二轮核心请求
≈ 9750ms（不含低延迟优先写入和后续 Deferred）
```

该数字是超时上界，不是实测耗时；它的用途是解释为什么“Socket 已连接但页面仍在加载”可能持续数秒。

#### 4.3.5 自动连接触发收敛

建立 `ConnectionTrigger` 和单一 `EarbudConnectionManager`：

```kotlin
enum class ConnectionTrigger {
    UserAction,
    ServiceCommand,
    AclConnected,
    AudioProfileConnected,
    PeriodicCheck,
}
```

Manager 负责：

- 合并同一地址短时间内的 ACL/A2DP/HEADSET 事件。
- 为同一地址创建唯一 `attemptId`，拒绝重复 active session。
- 统一处理手动断开抑制、取消、backoff、重试和失败原因。
- 只有系统蓝牙状态满足前提时才尝试打开控制通道。
- 连接失败后不立即被另一个广播入口重新拉起。

`MainActivity.onCreate`、`onResume`、Service profile callback、系统广播和周期检查都只调用 Manager；Repository 不再分别维护多组自动连接 Job。

### 4.4 指令模型目标

不要让业务 Handler 直接拼 `ByteArray`。先建立一个协议内的声明模型：

```kotlin
@JvmInline
value class CommandId(val hex: String)

data class CommandSpec(
    val id: CommandId,
    val responseId: CommandId? = id,
    val kind: CommandKind,
    val timeoutMs: Long,
    val retry: RetryPolicy = RetryPolicy.None,
    val confirmation: ConfirmationPolicy = ConfirmationPolicy.None,
)

enum class CommandKind { Read, Write, Notify, Ack, FireAndForget }

sealed interface ConfirmationPolicy {
    data object None : ConfirmationPolicy
    data class ReadBack(val command: CommandSpec) : ConfirmationPolicy
    data class AsyncState(val responseId: CommandId) : ConfirmationPolicy
}
```

实际实现可以比示例更简单，但必须明确这些语义：

- 命令 ID、响应 ID、通知 ID 不混用。
- 同一 RFCOMM 通道保持可控的请求顺序；不允许未知并发请求互相覆盖 response waiter。
- timeout、retry、quiet window 和初始化优先级由策略集中定义。
- `FireAndForget` 也必须经过发送队列，不能越过正在等待的请求。
- 写入结果分为“已发送”“收到 ACK”“读回确认”“超时/失败”，不能只返回 Boolean。
- 收到异步状态包时，按时间戳和 pending action 处理旧包覆盖问题。

### 4.5 Handler 拆分目标

将 `OpenFreebudsHandlers.kt` 拆成按能力组织的文件/包：

```text
adapter/huawei/features/
  battery/BatteryFeature.kt
  noise/AncFeature.kt
  audio/LowLatencyFeature.kt
  audio/SoundQualityFeature.kt
  audio/EqualizerFeature.kt
  gestures/TapFeature.kt
  gestures/SwipeFeature.kt
  gestures/LongPressFeature.kt
  connection/DualConnectFeature.kt
  device/InfoFeature.kt
  device/WearingFeature.kt
  device/VoiceLanguageFeature.kt
```

每个 Feature 只暴露：

```kotlin
interface EarbudFeature<S> {
    val id: String
    val capabilities: Set<EarbudCapability>
    suspend fun initialize(client: CommandClient): FeatureInitResult
    suspend fun onPacket(packet: ProtocolPacket): FeatureEvent?
    suspend fun execute(action: FeatureAction): CommandResult
}
```

旧 `HuaweiDeviceHandler` 可以先作为适配器保留；新 Feature 不应再直接依赖 `SppDriver`。

### 4.6 连接状态机目标

明确区分两种连接：

```text
SystemLinkState
  Disconnected -> Connected -> Disconnected

ControlChannelState
  Idle
    -> ConnectingTransport
    -> Settling
    -> InitializingCore
    -> Ready
    -> Degraded
    -> Disconnecting
    -> Failed
```

约定：

- 系统蓝牙已连接不等于 App 控制通道已连接。
- 手动断开只影响控制通道，并在策略窗口内抑制自动抢连。
- ACL 断开、Socket EOF、发送失败和初始化失败分别记录原因，再统一转成状态事件。
- 一个设备地址同一时间最多一个 active control session；多设备/双连扩展时再引入 `SessionRegistry`。
- Service、Tile 和 UI 只发送 `ConnectionCommand`，不直接创建 Socket 或调用 Handler。
- 所有连接重试使用统一 backoff 和取消规则，Repository 不再维护多套相互独立的 retry job。

### 4.7 蓝牙迁移阶段

#### BT-0：协议与连接基线 `[~] 进行中`

- 保存当前实机日志样本：连接、初始化、ANC 切换、低延迟切换、断开、重连、双连枚举。
- 为 `HuaweiSppPackage` / `HuaweiSppFramer` 添加分片、粘包、嵌套包、非法 magic、非法长度测试。
- 明确 5A 帧长度、CRC、参数边界的唯一解释，删除注释与实现不一致的地方。
- 先不改变现有命令行为。

##### BT-0.1：当前本地协议/能力基线审计 `[x] 已完成`

> 完成记录：2026-08-01；已完成本地 Kotlin 协议/能力实现、型号表和上游逆向资料快照存在性核对。实机证据表仍按型号和能力逐项补齐，未验证项继续保持 `PARTIAL`/`UNKNOWN`。

本地检查结果：从上游逆向资料整理出的**派生代码仍然存在**，但原始 `OpenFreebuds/` 快照目录当前不在工作区。因此后续重构不能只依赖现有 Kotlin 文件推断来源，应该重新固定上游提交或保存一份可追溯的协议资料快照。

| 审计对象 | 当前状态 | 结论 |
|---|---|---|
| SPP 5A 封包、解析、分帧 | `HuaweiSppPackage`、`HuaweiSppProtocol`、`HuaweiSppFramer` 仍在 | 主协议实现仍保留，需补齐固定测试样本 |
| 主要控制指令 | 常量和 Handler 仍在使用 | 主验证设备的核心指令链路延续有效，全部型号尚未完成复核 |
| 型号能力表 | `HuaweiModel` + `modelCapabilities` 仍在 | 属于兼容性基线，不是全型号最终真值 |
| 原始逆向资料 | `OpenFreebuds/` 当前缺失且被 gitignore | 需要固定来源版本或保存引用快照 |

已确认的结构性问题：

- `HuaweiModel.sppPort` 没有传入连接层，`SppDriver` 仍硬编码使用端口 `1`；表中的端口差异当前不会生效。
- 未识别型号会得到空能力集合，而当前 `has()` 将空集合解释为“全部能力”，与未知能力默认隐藏的原则相反。
- `HuaweiCapability` 中部分长按扩展能力只有枚举/表项，没有独立 Handler 或完整 UI 链路，需逐项标记为 `implemented`、`parsed-only` 或 `unverified`。
- 多个型号共用一个能力档案，部分型号在代码中明确属于临时保守适配，不能直接视作实机验证结论。
- 指令常量不完整，仍有裸 `byteArrayOf(...)` 分散在 Handler 中；迁移时需要建立唯一指令目录。
- 当前测试重点集中在初始化策略、pending response 和 i18n，协议分片、粘包、CRC、命令读回和型号能力表尚需补充测试。

BT-0 必须产出一份能力证据表：

| 型号 | SPP 端口 | 能力 | 读取命令 | 写入命令 | 通知命令 | 证据 | 状态 |
|---|---:|---|---|---|---|---|---|
| MODEL | PORT | CAPABILITY | COMMAND | COMMAND | COMMAND | LOG / SOURCE / DEVICE | VERIFIED / PARTIAL / UNKNOWN |

在证据表完成前，新增型号能力默认使用保守策略；已有表项若没有实机日志或可追溯来源，标记为 `PARTIAL`，不直接提升为已验证能力。

##### BT-0.2：连接速度专项基线 `[~] 进行中`

> 当前记录：2026-08-01；已加入可测试的 `ConnectionAttemptTimeline`、attemptId、触发来源和连接阶段日志，并在 Debug 调试终端加入“自动实机测试”按钮。按钮会自动执行 A-F 各 10 轮、ANC/低延迟读回、Service/Tile 去重和断开/重连检查，完成后自动分享包含 P50/P95 和完整诊断日志的报告。尚未取得实机数据，因此不能标记为已完成。

已收到主验证设备的 `a.txt`、`b.txt` 两份诊断报告，作为问题定位证据，不作为阶段完成证据：

| 报告 | Transport | Transport→CoreReady | 结果 | 关键现象 |
|---|---:|---:|---|---|
| a | 770ms | 7535ms | Degraded | 首轮 `anc_global`/`low_latency` 超时；后续 `sound`、`battery` 出现成功，说明初始化受时序影响 |
| b | 1009ms | 11823ms | Degraded | 自动低延迟重试与 Deferred 初始化并行，出现 `queueWait=749ms/800ms`，`2b6c` ACK 被当作读回结果的风险 |

本轮修复方向：核心读取超时从 1s 调整为 3s；失败核心能力增加串行恢复轮次；自动低延迟改为同一初始化队列内串行执行，并要求 `2b6c` 读回响应包含参数 `2`；自动连接入口先检查 active `connectingJob`，减少重复触发。修复后的 CI 包仍需实机复测 ANC、低延迟和初始化持续推进。

该小阶段只增加观测和测试，不调整生产时序。每个测试场景至少执行 10 次，记录成功次数、P50、P95、最大值和失败原因：

| 场景 | 系统蓝牙状态 | discovery | 自动低延迟 | 目的 |
|---|---|---|---|---|
| A | 已 connected | 否 | 关 | 得到纯 Transport + 最小初始化基线 |
| B | 已 connected | 否 | 开 | 确认低延迟写入/回读的额外成本 |
| C | 刚收到 ACL connected | 否 | 关 | 观察系统链路到 RFCOMM 的稳定窗口 |
| D | 扫描结束后立即连接 | 是/刚结束 | 关 | 验证 discovery 取消和 Socket 建连耗时 |
| E | Socket 首次失败后重试 | 否 | 关 | 验证关闭、backoff 和重复连接抑制 |
| F | 手动断开后系统仍 connected | 否 | 任意 | 验证手动抑制不会被广播抢回 |

每次测试必须导出：

```text
device model / firmware / Android version / app version
trigger / attemptId / endpoint
systemConnectedAt
transportConnectingAt / transportReadyAt
coreInitStartAt / coreReadyAt
readyAt / disconnectedAt
```

当前版本的诊断报告会额外写入 `[ConnPhase]` 日志，包含 `attemptId`、触发来源和阶段相对耗时。可用以下脚本对导出的报告汇总 P50/P95：

```bash
python3 scripts/analyze_connection_timing.py /path/to/fxxkHilife_diagnostic.txt
```

脚本只使用每个阶段第一次出现的时间点；缺少阶段时显示 `-`，不把不完整连接伪装成成功样本。应用内可通过调试终端执行 `share` 导出报告；BT-0.2 基线测试期间建议关闭原始 SPP 十六进制抓包，除非该场景需要分析协议内容。

判读规则：

- `TransportReady - TransportConnecting` 高：优先检查系统蓝牙时序、discovery、endpoint 和 Socket 超时。
- `CoreReady - TransportReady` 高：优先检查初始化队列、请求 timeout、重试和低延迟动作。
- `Ready - CoreReady` 高：优先检查 Deferred Scheduler、自动低延迟后续重试和页面状态映射。
- 同一 attempt 出现多个 `TransportConnecting`：先修连接编排重复触发，再看底层速度。

##### BT-0.3：专项基线后的决策门 `[ ] 未开始`

完成 BT-0.2 后再选择实现路径：

1. Transport 阶段占主要耗时：先迁移 UUID/端点策略、连接超时和统一重试。
2. Core 阶段占主要耗时：先拆分最小 Core 初始化和 Deferred 初始化，暂缓调整协议命令。
3. 多入口重复触发明显：先完成 `EarbudConnectionManager`，再继续 Transport 迁移。
4. 只有少数型号失败：保持通用连接路径，新增型号级 endpoint/settle/retry 配置，不在全局增加固定等待。

#### BT-1：唯一生产传输路径 `[~] 进行中`

> 进度记录：2026-08-01；代码已将生产 RFCOMM Socket、输入输出流、读循环和关闭逻辑收敛到 `RfcommSppTransport`，`SppDriver` 通过 `ProtocolSession` 使用 `HuaweiSppFramer`。本地和 GitHub Actions 自动流程均已通过：Debug/Release 各 23 个单元测试、0 failure、Debug/Release 构建成功；验证运行是 [run 30693967119](https://github.com/ct-yx/fxxkHilife/actions/runs/30693967119)，生成 `automated-test-report-v4.2.6-87`、`fxxkHilife-debug-v4.2.6-87` 与 `fxxkHilife-v4.2.6-87`。由于当前没有新的主设备实机回归日志，暂不标记为 `[x]`，版本仍冻结为 `4.2.6 (87)`。

已完成：

- `[x]` `RfcommSppTransport` 成为唯一生产 Socket 实现。
- `[x]` 将 `SppDriver` 的接收循环、Socket 关闭和原始发送路径迁移到 Transport + ProtocolSession。
- `[x]` 分帧层覆盖分片、粘包、噪声重同步、跨读取 magic 和重连清理；`ProtocolSession` 覆盖分片转发和重连重置。
- `[x]` 删除上层重复 TX 锁，保留请求队列串行化；fire-and-forget 仍不能越过正在等待的响应。

仍待完成：

- `[x]` 将 UUID、channel、连接超时、取消和重试收敛为 `RfcommTransportConfig`，移除生产路径中的固定 port 常量；已知型号使用型号端点，未知型号保留 channel 1 compatibility fallback，并将 endpoint/source 写入连接元数据和日志。
- `[ ]` 在主验证设备上回归 ANC、自动低延迟、初始化持续推进、断开/重连，并记录回退结果。
- `[ ]` 实机回归通过后再提升到 `4.3.0 (88)` 并标记 `[x]`。

#### 实机回归待测队列（BT-0.2 / BT-1 合并收集）

以下项目先累计，等 BT-1 代码和 CI 稳定后一次性请求目标受阻测试；在此之前不反复打断用户：

1. **连接基线**：场景 A-F 各执行至少 10 次，记录同一设备、系统版本、固件、触发来源、discovery、attemptId，以及 SystemLink、Transport、Core、Ready 各阶段耗时，计算 P50/P95。
2. **端点选择**：已知型号连接一次，确认日志中的 `rfcomm-channel`、`source` 与型号配置一致；未知名称再确认兼容 fallback 仍可记录。
3. **首轮初始化**：连续进入应用/连接，确认 ANC、电量、音质、低延迟的初始化结果不再随机缺失；特别观察 10 秒后仍有失败能力时是否继续推进恢复。
4. **ANC 控制**：连接后读取 ANC 模式/级别，切换一次模式并确认 UI 状态读回；记录失败 Handler 和对应命令响应。
5. **自动低延迟**：分别关闭/开启自动低延迟连接，确认写入、ACK、延迟回读和最终 `config.low_latency` 状态；不能只依据 ACK 判断成功。
6. **断开与重连**：手动断开、系统 ACL 断开、重新连接各执行一次，确认没有旧会话继续轮询、发送或触发自动低延迟。
7. **重复入口**：从应用进入、Service/Tile、ACL 自动连接和扫描结束触发连接各覆盖一次，确认同一 attempt 不产生并行 Socket。

每项测试由 Debug 按钮自动记录；激活测试模式后不再使用行数上限，而是使用约 160,000,000 字节的 UTF-8 缓存预算，避免 A-F 各 10 轮被 50,000 行截断。报告写入分享文件前限制为 190,000,000 字节以内，最终文件保持小于 200 MB；测试结束、取消、无设备和异常路径都会恢复正常日志策略。目标受阻时一次性分享测试报告，再统一判断 BT-0.2、BT-0.3 和 BT-1 的完成标记，不拆成多轮零散测试。注意：ACL 广播本身由系统触发，按钮同时执行同一清理路径的标记性回归；报告会区分 live broadcast 与 synthetic cleanup。

#### BT-2：CommandClient 与 Feature 拆分 `[ ] 未开始`

- 建立命令目录和 `CommandSpec`，消除 Handler 中散落的裸命令字节。
- 拆出 Battery、ANC、LowLatency、SoundQuality、Gesture、EQ、DualConnect 等 Feature。
- 把初始化顺序、核心/延迟能力、超时和重试从 Handler 本体移到策略配置。
- 增加单一 `CommandScheduler`，统一普通请求、写入确认、读回确认、异步通知和 Deferred 初始化的优先级。
- 将“写入已发送”“收到 ACK”“读回确认”“异步状态到达”建模为不同结果，避免以一个 Boolean 代表完整动作。
- 保留旧 Handler 适配器，逐个 Feature 替换，不一次改完。

#### BT-3：连接编排拆分 `[ ] 未开始`

- `SystemBluetoothMonitor`：系统蓝牙连接观察。
- `EarbudConnectionManager`：连接、断开、自动连接、backoff、手动抑制。
- `EarbudSessionRegistry`：按设备地址维护唯一 session。
- `EarbudStateStore`：状态流、失败能力、pending action 和连接原因。
- `ListeningStatsRepository`：从连接编排中移出统计逻辑。
- 统一 ACL、A2DP、HEADSET、Service 命令和周期检查的触发去重，所有触发都携带 `ConnectionTrigger`。
- 连接状态按 SystemLink、Transport、Core、Deferred 四层发布，禁止用单一 `Connected` 覆盖全部阶段。
- `BluetoothService` / Tile 改为消费 manager 的命令和状态。

#### BT-4：通用蓝牙状态输出（UI 接入前置） `[ ] 未开始`

- `DeviceProps` 只作为兼容映射，不再继续扩字段。
- Huawei Adapter 输出通用 `EarbudState` 和能力集合。
- 先稳定通用 `EarbudState`、能力集合和分层连接状态的输出契约，不在 BT-4 直接改页面。
- UI-1 再通过 typed state/action 消费该契约；UI-4 完成后删除 raw property 依赖。

## 5. 两项工作的依赖与执行顺序

执行顺序固定为**先完成蓝牙，再开始 UI**，不交叉推进会改变连接时序的 UI 代码：

1. **BT-0.1 `[x]`**：完成协议/能力本地审计，冻结 `v4.2.6 / versionCode 87` 基线。
2. **BT-0.2 `[~]`**：在主验证设备上完成连接速度实机基线，先区分 Transport、Core、Deferred 三段耗时；代码观测已就绪，等待实机数据。
3. **BT-0.3 `[ ]`**：依据数据决定端点、初始化调度或连接入口的优先改造方向。
4. **BT-1 `[~]`**：让 `RfcommSppTransport` 成为唯一生产 Socket 路径，发布 `TransportReady`；当前等待实机回归。
5. **BT-2 `[ ]`**：拆出 `CommandClient`、`CommandScheduler` 和按能力划分的 Feature，稳定指令与状态接口。
6. **BT-3 `[ ]`**：由 `EarbudConnectionManager` 收敛 ACL/A2DP/HEADSET/Service/Tile/周期检查等连接入口。
7. **BT-4 `[ ]`**：输出通用 `EarbudState` 和 SystemLink/Transport/Core/Deferred 分层状态，冻结 UI 消费契约。
8. **UI-0 `[ ]`**：蓝牙链路稳定后，再记录页面截图、交互和字段读写基线。
9. **UI-1 `[ ]`**：建立 `DeviceUiState`、typed event 和导航事件，先保留旧属性兼容层。
10. **UI-2 `[ ]`**：拆分页面和公共组件，继续保持 Haze 2.0 与 Material 3 双展示模式。
11. **UI-3 `[ ]`**：收拢 `AppUiState`、设置持久化和导航状态。
12. **UI-4 `[ ]`**：移除 raw property/兼容层，完成 classic/glass 共用状态和能力判断。

每个阶段都遵循：**改一层、加测试、跑实机、记录日志、更新本文件状态和版本记录、再迁移下一层**。未满足验收条件时保持 `[~]` 或 `[ ]`，不提前标记完成。

## 6. 验收标准

### UI

- 页面文件只负责渲染和事件分发，不直接读取/写入 `SharedPreferences`。
- 页面中不存在厂商 raw `group/prop` 和协议命令字节。
- ANC、低延迟、音质偏好、手势至少具备 pending、成功读回、失败重试/提示状态。
- 连接页面可以分别呈现系统蓝牙已连接、控制通道已建立、核心能力初始化中、Ready 和 Degraded。
- classic 与 glass 模式使用同一状态和能力判断，不能出现一套显示、另一套隐藏规则。
- Home、Scan、Device、Settings、Gesture 的返回、断开、自动连接路径有导航测试。
- 设备行点击、扫描完成回调、Service 自动连接和 Tile 命令不会重复创建导航事件或连接尝试。
- 连接成功后的详情页跳转由状态流/一次性导航事件驱动，不依赖连接方法返回瞬间的状态快照。
- light/dark、中文/English/繁體、无能力、初始化降级和断开状态均可渲染。

### 蓝牙指令/连接

- Transport、Framer、CommandClient、Feature、ConnectionManager 可以单独测试。
- 日志可以分别给出 `SystemLink -> TransportReady`、`TransportReady -> CoreReady` 和 `CoreReady -> Ready` 的 P50/P95；连接优化不能只看一个总耗时。
- 同一设备的连续连接尝试拥有唯一 `attemptId`，ACL/A2DP/HEADSET/周期事件不会产生并行 Socket。
- endpoint 选择来自型号配置或明确的 fallback，并记录 UUID/channel、来源和最终结果。
- `socket.connect()` 有取消、超时、关闭和统一 backoff；失败尝试不会遗留半开 Socket。
- 自动低延迟的写入、ACK、读回、后续重试不会阻塞或插队破坏 Core/Deferred Scheduler。
- 分片、粘包、嵌套包、非法长度和断开 EOF 有固定测试样本。
- 同一 response key 不发生 waiter 覆盖；发送队列不会被 fire-and-forget 插队破坏。
- 写入动作能区分发送成功、ACK、读回确认、异步通知和超时。
- 手动断开、ACL 断开、Socket 断开、初始化降级和自动重连都能得到明确状态与日志原因。
- FreeBuds 6i 作为主验证设备，至少回归电量、ANC、低延迟、音质、断连重连；其他型号按能力表逐项记录。
- 原有命令和 UI 行为没有因架构迁移产生未记录的变化。

## 7. 暂不处理的内容

- 暂不新增新的厂商协议或 BLE 控制通道。
- 暂不实现 Android 侧多条并发 SPP 控制 session。
- 暂不开放未经实机验证的 Custom EQ payload。
- 暂不在重构期更换产品视觉方向；只统一已有 classic/glass 两种展示的工程边界。
- 暂不把所有历史日志和终端调试文本做用户化改写，原始协议日志继续保留可检索性。

## 8. 一键自动测试与每次提交的最小检查

以后默认使用同一入口，不再要求手动拼接 Gradle 命令：

```bash
./scripts/run_ci_checks.sh ci-output
```

该入口会自动执行 `git diff --check`、`clean test assembleDebug assembleRelease`，并在 `ci-output/` 保留命令日志、JUnit XML、HTML 报告、版本/提交信息、APK 数量和 SHA-256 摘要；即使构建失败也会先收集报告再返回失败码。

GitHub 上点击 **Actions → Build & Release → Run workflow → Run workflow** 即可执行同一流程。运行结束后：

- 下载 `automated-test-report-*`：查看 `summary.md`、`gradle.log` 和测试报告。
- 下载 `fxxkHilife-*`：取得 Release APK；只有自动化检查成功时才上传 APK。
- 下载 `fxxkHilife-debug-*`：取得开发阶段实机回归 APK；其中包含“自动实机测试”按钮。该按钮由 `BuildConfig.DEBUG` 控制，Release APK 不显示此按钮。
- 自动化失败时先下载报告定位问题，不把失败构建当作可测试包。

蓝牙相关提交还应附：

- 影响的命令/状态清单。
- 至少一份分片或响应超时测试结果。
- 实机型号、系统版本、App 版本和关键日志摘要。
- 若改变连接或初始化时序，说明回退方式。
