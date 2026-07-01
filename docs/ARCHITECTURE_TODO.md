# 架构重构 TODO：可插拔耳机协议与能力层

> 目标：把当前偏 Huawei / OpenFreebuds 的实现，逐步整理成可接入更多逆向耳机项目的插件式架构。

## 背景

当前项目的协议、能力表、Handler 注册、UI 属性映射已经可以支撑 HUAWEI / HONOR Earbuds 的核心控制，但接口边界仍然偏“单厂商内聚”：

- `DeviceRepository` 同时承担连接编排、型号识别、Handler 注册、属性同步、统计状态等职责。
- `SppDriver` / `HuaweiDeviceHandler` / `OpenFreebudsHandlers` 与华为协议强绑定。
- UI 层直接消费 `DeviceProps`，其中字段也以当前华为能力为主。
- 后续如果接入其他逆向项目的耳机，容易把更多厂商分支继续塞进现有仓库和 Handler 注册逻辑。

## 分层目标

### 1. Transport 层

抽象耳机通信通道，不关心具体协议语义。

候选接口：

```kotlin
interface EarbudTransport {
    val isConnected: Boolean
    suspend fun connect(): Boolean
    fun disconnect()
    suspend fun send(bytes: ByteArray)
    fun setPacketListener(listener: suspend (ByteArray) -> Unit)
}
```

实现方向：

- `RfcommSppTransport`：当前 Huawei / OpenFreebuds SPP 路线。
- `BleGattTransport`：预留给 BLE GATT 控制通道。
- `NativeBridgeTransport`：预留给未来 native daemon / vendor SDK / root bridge。

### 2. Protocol 层

负责包格式、命令 ID、参数解析、请求响应匹配。

候选接口：

```kotlin
interface EarbudProtocol {
    val id: String
    fun decode(raw: ByteArray): ProtocolPacket?
    fun encode(packet: ProtocolPacket): ByteArray
    fun createSession(transport: EarbudTransport): ProtocolSession
}
```

当前可拆出：

- `HuaweiSppProtocol`
- `HuaweiSppPackage`
- pending response / ignore command 逻辑

后续可新增：

- `SamsungBudsProtocol`（如存在可用逆向资料）
- `XiaomiBudsProtocol`
- `GenericBleEarbudProtocol`

### 3. Capability 层

把功能能力从厂商 Handler 中抽象成稳定能力接口。

候选能力：

```kotlin
enum class EarbudCapability {
    Battery,
    AncMode,
    AncLevel,
    AwarenessMode,
    LowLatency,
    SoundQuality,
    WearingState,
    AutoPause,
    GestureDoubleTap,
    GestureTripleTap,
    GestureLongPress,
    GestureSwipe,
    VoiceLanguage,
    Equalizer,
    DualConnect,
}
```

目标：

- UI 只关心通用能力与通用属性。
- 厂商插件负责把自己的协议字段映射成通用属性。
- 未确认能力默认不展示，避免 UI 误报。

### 4. Plugin / Adapter 层

每个厂商或逆向项目作为一个插件式 Adapter。

候选接口：

```kotlin
interface EarbudAdapter {
    val id: String
    val displayName: String
    fun canHandle(device: DiscoveredEarbud): Boolean
    fun createController(device: DiscoveredEarbud): EarbudController
}
```

`EarbudController` 负责：

- 连接设备
- 暴露 `StateFlow<EarbudState>`
- 暴露 `capabilities`
- 执行 `setProperty()` / `invokeAction()`
- 导出调试日志

首个插件：

- `HuaweiOpenFreebudsAdapter`

后续插件：

- 按逆向资料成熟度逐个接入，不污染核心仓库。

### 5. State 层

将 `DeviceProps` 演进为更通用的 `EarbudState`。

短期做法：保留 `DeviceProps`，新增 Adapter 内部映射。

长期目标：

```kotlin
data class EarbudState(
    val deviceName: String?,
    val modelName: String?,
    val firmwareVersion: String?,
    val battery: BatteryState?,
    val anc: AncState?,
    val audio: AudioState?,
    val gestures: GestureState?,
    val wearing: WearingState?,
    val pendingTasks: List<PendingTask>,
)
```

### 6. Repository 层瘦身

当前 `DeviceRepository` 后续拆分方向：

- `EarbudConnectionManager`：扫描、连接、断开、自动连接。
- `EarbudAdapterRegistry`：管理可用插件与设备匹配。
- `EarbudStateStore`：通用状态仓库。
- `ListeningStatsRepository`：听音统计独立持久化。
- `LogRepository`：日志导出与调试。

## 迁移步骤

### 阶段 1：不破坏现有功能的接口整理

- [x] 新建 `core/transport` 包，定义 `EarbudTransport`。
- [x] 将当前 SPP 连接能力包一层 `RfcommSppTransport`，先作为新架构 Transport 引入；生产路径暂不替换 `SppDriver`。
- [x] 新建 `core/adapter` 包，定义 `EarbudAdapter` / `EarbudController` 草案。
- [ ] 将 `HuaweiModel` / `HuaweiCapability` 移入 `protocol/huawei` 或 `adapter/huawei` 命名空间。
- [x] `DeviceRepository.registerHandlers()` 改为委托给 `HuaweiOpenFreebudsAdapter`。

### 阶段 2：能力与状态通用化

- [ ] 定义通用 `EarbudCapability`。
- [ ] 定义通用 `EarbudState`，先与 `DeviceProps` 并存。
- [ ] 给 Huawei Adapter 增加 `HuaweiStateMapper`，负责 Huawei 属性 → 通用状态。
- [ ] UI 层逐步从 `DeviceProps` 迁移到通用状态。
- [ ] 未迁移字段继续通过兼容层提供。

### 阶段 3：统计与附加功能解耦

- [ ] 将听音统计从 `DeviceRepository` 独立为 `ListeningStatsRepository`。
- [ ] 支持统计来源策略：连接时长 / 佩戴时长 / 媒体播放状态。
- [ ] 为不同 Adapter 暴露是否支持稳定佩戴检测。

### 阶段 4：新耳机项目接入准备

- [ ] 为 Adapter 增加独立测试入口和日志导出标识。
- [ ] 支持一个设备被多个 Adapter 试探匹配，但只激活优先级最高者。
- [ ] 新增“实验性适配”标记，UI 中提示风险。
- [ ] 允许插件声明自己的调试面板字段。

## 近期优先级

1. 先让 v3.8.0 听音统计通过 CI。
2. 下一版只做接口拆分，不新增大功能。
3. 优先把 Huawei/OpenFreebuds 现有能力包成第一个 Adapter。
4. 保持 UI 行为不变，避免在重构期引入用户可见退化。

## 设计原则

- 核心 UI 不直接依赖厂商协议字段。
- 新厂商接入不修改主 Repository 的大段逻辑。
- 未验证能力默认隐藏。
- 所有实验性能力必须能导出日志。
- 重构期间保持当前 Huawei / HONOR 功能稳定优先。