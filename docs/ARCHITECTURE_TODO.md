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
| UI 通用化 | 主计划 §3、§5 | `Route → ViewModel → typed state/event`，统一 `AppScaffold`，只保留标准 Material 3 surface |
| 新 Adapter 接入准备 | 主计划 §2.2、§7 | 延后到 BT-1/2/3 收口和 UI 状态契约冻结之后，按真实资料逐个接入 |

## 当前执行点

当前最近完成的 BT 门是公开发布基线 `4.3.10 / 98` 的 `BT4_STATE_CONTRACT_5`：

- 通用状态/能力契约 5 轮：连接到 `Ready` 后检查同一 attempt、精确能力集合、已声明状态域读回、`DeviceProps` 兼容投影，以及 pending/failed 终态。
- 不重复 BT-3 已通过的 F10 + 初始化10，不恢复历史 36/100 项矩阵。

实机报告 `/Users/chenhong/Downloads/fxxkHilife_hardware_regression_1786696542049.txt` 已返回并满足 5/5、`reportValid=true`、`overall=PASS`。BT-0 至 BT-4 已收口；UI-0 仍未开始，下一阶段按主计划进入 UI 基线与资源清点。

本文件仍只负责旧路径跳转和方案映射；发布说明、版本号、测试门与 UI 后续计划统一维护在主计划、`VERSION_MANAGEMENT.md` 和 `DEVELOPMENT_LOG.md` 中。
