# 架构重构计划（已合并）

本文件的独立 TODO 已合并到唯一主计划：

- [`REFACTOR_PLAN_UI_AND_BLUETOOTH.md`](./REFACTOR_PLAN_UI_AND_BLUETOOTH.md)

从 2026-08-10 起，不再在本文件维护第二份阶段清单、版本号或测试门。本文件保留为旧路径跳转说明，完整历史正文已归档到主计划的“附录 A：原架构 TODO（历史设计记录）”，避免历史链接失效。

## 合并后的子计划映射

| 原架构 TODO | 主计划位置 | 当前选择 |
|---|---|---|
| Transport | 主计划 §2.1、§4.3.3 | `EarbudTransport` 只作为 seam；生产唯一使用 `RfcommSppTransport`，暂不实现 BLE/Native |
| Protocol | 主计划 §2.1、§4.3、§4.4 | `ProtocolSession + HuaweiSppFramer + CommandCatalog/Client/Scheduler`，先做单协议深模块 |
| Capability / State | 主计划 §2.1、§3.3、§4.7 BT-4 | `EarbudCapability + EarbudState + ControlChannelState`，`DeviceProps` 保留兼容映射 |
| Adapter | 主计划 §2.1、§4.5 | 静态 `EarbudAdapterRegistry`，先维护 `HuaweiOpenFreebudsAdapter`；第二个真实 Adapter 出现后再扩展优先级匹配 |
| Repository 瘦身 | 主计划 §2.1、§4.7 BT-3 | `EarbudConnectionManager` 统一连接编排；`EarbudStateStore` 管状态；BT-3 实机门关闭后再抽 `ListeningStatsRepository` |
| UI 通用化 | 主计划 §3、§5 | `Route → ViewModel → typed state/event`，统一 `AppScaffold`，继续保留 Haze 2.0 |
| 新 Adapter 接入准备 | 主计划 §2.2、§7 | 延后到 BT-1/2/3 收口和 UI 状态契约冻结之后，按真实资料逐个接入 |

## 当前执行点

当前唯一待测门是 `4.3.6 / 94` 的 `BT_MANAGER_RUNTIME_20`：

- F 场景 10 轮：手动断开抑制、RFCOMM 重连、ACL 清理/重连、Ready 终态、attempt 归属。
- 应用入口初始化推进 10 轮：观察 10 秒后的 stage、pendingHandlers、failedHandlers 和最终终态。

收到实机报告后，继续按主计划顺序推进；测试失败只追加对应场景，不恢复完整 36/100 项矩阵。版本号保持 `4.3.6 / 94`，计划合并本身不提升版本。
