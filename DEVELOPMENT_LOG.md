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
