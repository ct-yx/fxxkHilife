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

## v2.1.2 (2026-06-28)

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
