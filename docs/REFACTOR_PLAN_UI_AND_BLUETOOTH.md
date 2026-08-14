# UI 与蓝牙指令/连接重构规划

> 状态：执行中；BT-0 至 BT-4 已收口，当前重心转为 UI 全量重构（计划已核对，按错误报告继续实现）
>
> 历史基线：v4.2.6 / versionCode 87，2026-08-01
>
> 当前发布基线：v4.3.10 / versionCode 98，2026-08-14（BT 重构后的首个公开大版本；BT-0 至 BT-4 蓝牙阶段已完成；BT-4 定向实机门已通过）
>
> 当前开发包：v4.4.0 / versionCode 99，2026-08-15（UI 错误报告修复包；等待 GitHub Actions、截图/运行诊断和定向交互实测，不代表已发布 Release）
>
> 目标：把当前“能工作但边界偏大”的 UI、蓝牙连接和 SPP 指令实现整理成可持续迭代的结构。蓝牙阶段先稳定现有 HUAWEI / HONOR + RFCOMM SPP 路线；当前转入 UI 基本全量重构，统一视觉、交互、状态和导航，同时保留已经验证可用的美术资源。

> 测试模式日志约定：Debug“自动实机测试”每次启动都会清空旧缓存，测试期间不设日志行数上限，改用约 160,000,000 字节的 UTF-8 缓存预算；导出的最终报告在写文件前再限制为 190,000,000 字节以内，确保小于 200 MB。正常日志策略在报告构建完成后恢复。

> **唯一计划入口**：原 `docs/ARCHITECTURE_TODO.md` 的通用 Transport / Protocol / Capability / Adapter TODO 已合并到本文件；旧路径只保留跳转说明，不再维护第二份清单。

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

当前 **BT-0 至 BT-4 已完成**：`v4.3.10 / 98` 是 BT 重构后的首个公开大版本，主验证设备的蓝牙状态契约门已通过，但结论仍限定在已有型号/固件/系统证据范围内，不外推为所有型号和所有指令均已实机验证。UI-0 至 UI-5 已有第一轮结构迁移，但该迁移未完成生产切换，且已由 `docs/UI_ERROR_REPORT.md` 确认存在液态玻璃伪实现、滚动性能和详情导航阻塞；它们不能标记为完成，也不能作为完全重构完成的证据。后续 UI 按 BG-0、UI-FOUNDATION、FROSTED-1、LIQUID-1、NAV-1、DEVICE-1、SETTINGS-1、LEGACY-CUTOVER 和 UI-RELEASE 重新收口。

### 0.1.1 定向实机测试规则

实机测试规模跟随本轮代码改动，不再默认启动完整 100 项矩阵：

| 改动类型 | 默认强度 | 适用场景 |
|---|---:|---|
| 日志字段、报告格式、状态判定 | 3 轮 | 核对字段存在、attempt 归属和终态输出 |
| 普通能力读写 | 5 轮 | ANC、低延迟、读回一致性等功能行为 |
| 重连、ACL、并发入口和连接耗时 | 每个相关场景 10 轮 | 需要观察竞态并计算 P50/P95 |
| 跨模块发布验收 | 100 项 | BT 阶段合并、Release 候选或多个模块同时改动 |

上一轮 `4.3.2 / 90` 的定向清单为 **36 项**；`4.3.3 / 91` 先针对 ANC 摘戴竞态进行修复和人工确认，`4.3.4 / 92` 已完成这组复测。报告曾显示 B、D、E 已通过，F 和应用入口初始化存在状态判定/热重连尾部问题；`4.3.5 / 93` 与 `4.3.6 / 94` 已针对这两个问题完成代码和实机复测，历史结果保留如下：

| 组别 | 轮次 | 覆盖内容 |
|---|---:|---|
| B | 10 | 系统已连接、discovery 关闭、自动低延迟开启 |
| F | 10 | 手动断开抑制、RFCOMM 重连和 ACL 清理/重连 |
| 初始化 | 10 | 应用入口，观察 10 秒后初始化是否继续推进 |
| D | 3 | discovery 完成入口、saved-fallback、endpoint/channel/source |
| E | 3 | 首次失败后的外层重试日志语义 |

合计 **36 项**。A/C、ANC 独立读写、自动低延迟读回和入口去重不在本轮重复执行；这些内容留给对应改动或完整 100 项发布验收。

`4.3.4 / 92` 报告 `/Users/chenhong/Downloads/fxxkHilife_hardware_regression_1786277130416.txt` 的结果为 **31/36**：B=10/10、F=7/10、初始化推进=8/10、D=3/3、E=3/3；endpoint 始终为 `rfcomm-channel=1`，source 始终为 `VerifiedModelConfig`。F1/F3/F9 的日志在同一 attempt 已出现 `CoreReady/Ready`，但结果快照仍出现 `InitializingDeferred` 或等待超时；初始化第 2、6 轮则在 RFCOMM 三次立即拒绝后失败。该报告因此同时暴露了状态流并发覆盖和第四次 Socket 创建不足两个独立问题。

`4.3.5 / 93` 的 `BT_STATE_RETRY_20` 与 `4.3.6 / 94` 的 Manager 运行时改动覆盖同一组 F/初始化风险；本轮使用更明确的 `BT_MANAGER_RUNTIME_20` 名称，避免只看版本号无法区分测试目的。该 profile 已完成，结果见下方回灌记录：

| 组别 | 轮次 | 覆盖内容 |
|---|---:|---|
| F | 10 | 手动断开抑制、RFCOMM/ACL 重连；验证 Ready 终态快照、attempt 归属和热重连排水 |
| 初始化 | 10 | 应用入口，观察 10 秒后初始化是否继续推进；验证同一 attempt、Ready/Degraded 和 pending 为空 |

合计 **20 项**。B、D、E 已在 `4.3.4 / 92` 通过，本轮不重复；若 F/初始化仍失败，只追加对应失败项，不恢复整套 36 项。

> **BT 阶段已收口并作为公开 Release 基线**：`v4.3.10 / 98` 的 `BT4_STATE_CONTRACT_5` 5 轮实机报告已通过。BT-4 只检查通用状态/能力契约，不重复 A-F 或 ANC/低延迟功能矩阵；UI-0 已进入基线和基础层实现，后续只安排 UI 定向验证。

### 0.2 本轮执行记录（先蓝牙，后实机测试）

本轮继续完成蓝牙组件重构、定向实机测试矩阵和本地验证；代码/CI 完成后，只安排与本轮改动对应的测试项。

- 当前发布基线为 `4.3.10 / 98`，对应 BT-4 通用状态/能力契约的 `BT4_STATE_CONTRACT_5` 定向门槛；`4.3.9 / 97` 为 BT-0 协议固定样本 follow-up 包，`4.3.8 / 96` 为上一版 BT-4 状态契约 follow-up 包，`4.3.7 / 95` 为首轮 BT-4 契约测试包，`4.3.6 / 94` 为已通过实机门的 BT-3 连接运行时包，`4.3.5 / 93` 为上一轮状态/重试修复包，`4.3.4 / 92` 为 36 项报告包，`4.3.3 / 91` 为 ANC 摘戴状态修复包，`4.3.2 / 90` 为上一轮热重连/初始化回归包，`4.3.1 / 89` 为更早的完整实机报告包，`4.3.0 / 88` 仅作为 BT-1 首轮实机报告包，`4.2.6 / 87` 仅保留为重构前基线。
- `4.3.10 / 98` 本轮补齐 BT 收尾：同一 attempt 内的状态映射串行化、兼容 pending 投影同步、失败连接资源断开、统计 ticker 断开前 flush、Manager command 边界串行化、Handler ID 去重和共享 Adapter Registry；代码验证后完成同一 `BT4_STATE_CONTRACT_5` 5 轮实机门。
- `/Users/chenhong/Downloads/fxxkHilife_hardware_regression_1786696542049.txt` 已回灌：FreeBuds 6i、Android API 36、固件 `HarmonyOS 6.0.0.292(F001H003C00)`，5/5 轮通过；`expectedOperations=5`、`completedOperations=5`、`reportValid=true`、`overall=PASS`。五轮均为同一轮内 `expectedAttempt=actualAttempt`、`stage=Ready`、`coreReady=true`、能力集合精确匹配、三方 pending 一致且为空、failed 为空、兼容投影一致；endpoint=`rfcomm-channel=1`、source=`VerifiedModelConfig`，契约检查 P50/P95=`2549/3573ms`，最大 `3573ms`。
- `4.3.10 / 98` 最终本地 CI 已通过：`diff-check`、Debug/Release 单元测试和 Debug/Release 构建均通过；Debug/Release 各 `94 tests`，均为 `0 failures / 0 errors / 0 skipped`。结果目录为 `ci-output-bt-connection-followup-4.3.10-98`；Debug APK SHA-256=`d36085bc37a271d99aa5d0575d9af36be63e93b8eec69af134d562740c72bfec`，Release APK SHA-256=`501df2aa286c19141407c83e926ade357f1b1f55ff5706b6851b19d54de78e54`。
- `4.3.8 / 96` 本地完整 CI 已通过：82 个 Debug 单元测试和 82 个 Release 单元测试均为 `0 failures / 0 errors / 0 skipped`；`diff-check`、Debug/Release 测试与构建均通过。Debug APK SHA-256=`c1441879f2c0a678f78d1dea33af5751e9ba6a610c6ae4fc90cde51857754927`，Release APK SHA-256=`0c3008501d51c0bbab1efe68a30284695051d7dad8c71dc870d7af2e3c2ad6d4`；结果目录为 `ci-output-bt4-state-contract-4.3.8-96`。
- 保留未跟踪的 `.DS_Store` 和 `对话历史记录.md`，不纳入代码提交。
- 本轮重点：在 BT-4 状态契约实机门之前，先收口 Huawei 5A 协议边界；`HuaweiSppPackage` 现在按双字节长度、完整 checksum 帧和 payload 参数边界解析，`HuaweiSppFramer` 不再截断高字节长度或末尾校验字节。连接状态、活动地址、系统链路集合、连接/初始化/轮询 Job 句柄仍由 `EarbudConnectionManager` 持有；不新增第二套 Transport/Session。
- 2026-08-09 ANC 诊断回灌：`/Users/chenhong/Downloads/fxxkHilife_diagnostic_1786212455286.txt` 的 `2b2c` 在摘戴期间以短 burst 连续出现，旧 Handler 直接把它当成模式快照写入属性；日志只记录参数键，尚未形成可验证的 mode 值证据。
- 2026-08-09 ANC 状态修复：`2b2a` 保持唯一权威状态来源；`2b2c` 只作为变化提示，不直接更新 ANC 属性，并在 250ms 安静窗口后通过同一 command lane 发起一次无等待 `2b2a` 读取；收到权威 `2b2a` 后取消未执行的刷新任务。
- 2026-08-09 ANC 定向测试报告回灌：`/Users/chenhong/Downloads/fxxkHilife_hardware_regression_1786263762016.txt` 为 `format=2`、`profile=ANC_WEAR_STATE`、`4.3.3 / 91`，设备为 HUAWEI FreeBuds 6i；10 轮均已执行，但验收结果为 `pass=0 fail=10`。每轮均为 `notifications2b2c=0`、`accepted2b2c=0`、`refreshes=0`、`authoritative2b2a=0`、`exercised=false`、`refreshed=false`；因此没有形成一次有效的摘下/放置/双耳重新佩戴事件证据，尚不足以证明修复通过，也不足以判断模式稳定性回归。连接端点仍为 `rfcomm-channel=1 source=VerifiedModelConfig`，报告未显示连接路线本身失败。
- 2026-08-09 自动 ANC profile 报告标记为无效测试记录：10 轮窗口均未捕获实际摘戴事件；随后以人工诊断报告确认修复效果，并恢复 36 项定向 profile。
- 2026-08-09 ANC 人工验证报告回灌：`/Users/chenhong/Downloads/fxxkHilife_diagnostic_1786264858472.txt` 记录到 `2b2c` 通知 36 次、安静窗口刷新 20 次、权威 `2b2a` 接受 45 次，未出现 `State accepted source=2b2c`；16:40:30–16:40:32 期间滞后的 `mode=0` 被 `pending=1` 守卫丢弃，最终仍回到 `2b2a mode=1`。这支持“设备发送摘戴瞬时通知、旧软件错误采纳”的根因判断，ANC 修复人工通过。
- 2026-08-09 `4.3.4 / 92` 的 36 项实机报告回灌：B=10/10、F=7/10、初始化推进=8/10、D=3/3、E=3/3，总计 31/36；endpoint=`rfcomm-channel=1`、source=`VerifiedModelConfig`。F1/F3/F9 的阶段日志已到 `Ready`，但 Runner 最终快照出现 `InitializingDeferred` 或等待超时；初始化第 2、6 轮出现三次 RFCOMM immediate rejection 后失败。13 个完整 attempt 的阶段统计为 Transport→CoreReady P50/P95=`860/888ms`，CoreReady→Ready P50/P95=`1350/1429ms`；该报告未发现 handler 初始化失败，说明本轮优先修状态归并和立即拒绝尾部。
- 2026-08-09 本轮最小修复：`EarbudStateStore` 的 ControlChannel/DeviceProps 读改写改为原子 `StateFlow.update`，避免并发 handler 快照把 `Ready` 覆盖回 `InitializingDeferred`；`RfcommTransportConfig` 的 immediate retry 从 2 次提升到 3 次，最多创建 4 个 Socket，间隔 `250/500/750ms`，正常首连路径不增加延迟；新增并发状态回归测试和 20 项计划测试。
- 2026-08-09 `4.3.5 / 93` Debug 自动测试按钮切换为 `BT_STATE_RETRY_20`，执行顺序为 F10、初始化10；`BT_TARGETED_36` 保留用于历史 36 项门槛复现，`FULL_MATRIX` 仍保留给跨模块/发布验收，Release 不暴露测试按钮。
- 2026-08-09 `4.3.5 / 93` 本地 CI：`./scripts/run_ci_checks.sh ci-output-bt-state-retry-20` 通过（diff-check、Debug/Release 单元测试、Debug/Release 构建）；Debug APK SHA-256=`bd28b23cf6916bc141d66eef0f8b7fadff730161da0c07a62c5bcb0fd54b9b8f`，Release APK SHA-256=`13e498c7e55041b8778569c532a2aeb02c3dab773afcd9171e8cc8a6e90b3b9e`。这只是本地/静态证据，仍需同一设备的 20 项实机验证。
- GitHub Actions [31313780654](https://github.com/ct-yx/fxxkHilife/actions/runs/31313780654) 已通过；产物为 `fxxkHilife-debug-v4.3.5-93`、`fxxkHilife-v4.3.5-93` 和 `automated-test-report-v4.3.5-93`。CI 通过只证明构建/自动化测试通过，不替代主验证设备的 20 项实机报告。
- 2026-08-09 `4.3.6 / 94` BT-3 运行时所有权收敛：新增 `ConnectionLifecycle`，`EarbudConnectionManager` 现在持有兼容 `ConnectionState`、活动地址/连接时间、手动断开抑制、RFCOMM 排水窗口、系统链路集合以及连接/初始化/轮询 Job 句柄；Repository 通过 Manager 接口完成安装、取消和状态发布，断开时统一取消连接工作后再摘除 session。
- 2026-08-09 `4.3.6 / 94` Debug 自动测试按钮改为 `BT_MANAGER_RUNTIME_20`；执行范围仍为 F10 + 应用入口初始化10，不重复 B/D/E，也不把未收到的 `4.3.5 / 93` 实机结果默认为通过。
- 2026-08-09 `4.3.6 / 94` 本地 CI：`./scripts/run_ci_checks.sh ci-output-bt-manager-runtime-4.3.6-94` 通过；Debug APK SHA-256=`84bcca035c04eb5d3380ed343a20f5637a380e7b22ce6c7249307e85a2194efd`，Release APK SHA-256=`43f9bced10a1e24ef2600f7f7134bf04d2bea86b60eb030771f540275e5d396d`。这是静态/本地证据，仍需主验证设备的 20 项报告。
- 2026-08-14 `4.3.6 / 94` 实机报告回灌：`/Users/chenhong/Downloads/fxxkHilife_hardware_regression_1786655973008.txt`，`profile=BT_MANAGER_RUNTIME_20`，HUAWEI FreeBuds 6i / Xiaomi 24069RA21C / Android API 36 / firmware=`unknown`，endpoint=`rfcomm-channel=1`，source=`VerifiedModelConfig`。F=10/10、应用入口初始化=10/10；F 总耗时 P50/P95=`7562/8984ms`，Transport Connect=`289/300ms`，Transport→CoreReady=`833/978ms`，CoreReady→Ready=`1363/1443ms`；20 项均为同一 attempt，最终 Ready，pending/failed 为空，并验证手动断开抑制、RFCOMM 重连、ACL 清理/重连。A-E 未执行是定向 profile 设计，不计为失败。

- 2026-08-14 BT-4 契约校验收紧：`EarbudSnapshot` 作为通用状态、兼容 `DeviceProps` 和能力集合的原子读取面；`EarbudStateContractValidator` 要求同一 attempt、`Ready`、无 failed handler、pending 三方一致、能力集合精确匹配，以及已声明状态域均有读回。`Degraded` 只作为运行状态，不再作为 BT-4 通过条件。
- 2026-08-14 BT-4 适配器边界收紧：删除宽泛 `Earbuds -> BUDS_4I` 匹配，仅保留上游明确列出的 Honor Earbuds 2 系列；未知型号拒绝创建连接 attempt，未知型号能力为空且不注册全部 Handler；Logs 按 `LOGS` 能力注册，LongTap 同时支持 basic/split 能力。断开和 ACL 清理统一使用 `resetContract()`。
- 2026-08-14 BT-4 定向测试调整：默认 Debug profile 为 `BT4_STATE_CONTRACT_5`，固定 5 轮；报告新增 `expectedOperations`/`completedOperations`、`reportValid`、`overall` 和 `reportIssue`，异常中断或样本名/轮次不完整时报告标为无效。BT-4 报告不再输出 A-F 的零样本统计，每轮只验证状态契约，普通连接速度与功能读写不重复测试。

本轮新增的代码边界：

- `ConnectionAttemptCoordinator` 已独立出来并加入 JVM 单元测试：重复入口复用原 attempt、旧 attempt 不能清理替换 attempt、失败后的空闲重试生成新 attempt。
- `DeviceRepository` 在安装 session、接受 TransportReady 和写入失败状态时再次在 `connectionLock` 内校验 attempt/session，避免快速断开/重连时旧 session 覆盖新 session。
- `HuaweiCommandClient` 将等待同一 response key 的排队时间计入请求总 timeout，避免 ACK/读回超时预算被隐式翻倍；`HuaweiCommandScheduler` 已按优先级排队，并将 `sppQuietUntil` 作为实际发送前门控。
- `EarbudConnectionManager` 已成为 UI、Service、Tile、Terminal 和回归 Runner 的 typed command 边界；Repository 暂作为兼容实现，连接状态、活动地址、系统链路集合和生命周期 Job 句柄已移入 Manager 的 `ConnectionLifecycle`。
- `ConnectionLifecycle` 已将连接/初始化/轮询 Job 的注册、取消与当前 session/attempt 校验放在同一个 Manager 生命周期锁边界内；断开路径先取消连接工作，再摘除 session，避免新 attempt 被旧 Job 迟到回调覆盖。
- `ConnectionAutoConnectPolicy` 已从 BluetoothService 抽出，统一管理 force、backoff、失败指数退避和成功后重置，并有 JVM 单元测试。
- `ControlChannelStateReducer` 已抽出 attempt-aware 的 begin/stage/handler/link/reset 归约逻辑；旧 attempt 的迟到阶段回调会被状态层丢弃，并有 JVM 单元测试。
- `EarbudSessionRegistry` 已接管单一活动 session 的 install/detach/identity 校验；旧 session 的迟到 EOF/断开回调不会摘除替换 session，并有 JVM 单元测试。
- `ConnectionAttemptCoordinator` 的去重现在按设备地址匹配（忽略地址大小写）；不同地址不会继承当前设备的 attemptId，自动连接入口也会明确记录“另一设备控制通道占用”。
- `BluetoothScanner` 的终态回调增加一次性闸门；绑定设备回退、正常 discovery 完成和 stopScan 竞态不会重复触发 ScanCompleted 建连。
- `SystemBluetoothMonitor` 已从 `BluetoothService` 抽出，统一持有 ACL/A2DP/HEADSET 广播、Profile Proxy 和周期检查；Service 只负责生命周期与通知。
- `EarbudStateStore` 已接管 `ControlChannelState` 与 `DeviceProps` 的可变 StateFlow；Repository 只通过 store 发布 attempt-aware 阶段、能力 pending/failed 和属性兼容快照。
- 本轮将版本从 `4.3.5 / 93` 提升到 `4.3.6 / 94`；Debug 自动测试按钮切换为 `BT_MANAGER_RUNTIME_20`，不改变 Release 行为。
- 自动测试按钮默认使用 `BT_MANAGER_RUNTIME_20` profile，执行 F10、初始化持续推进10轮，合计20项；`BT_STATE_RETRY_20` 保留用于上一轮报告语义复现，`BT_TARGETED_36` 保留用于历史 36 项门槛复现，`ANC_WEAR_STATE` 保留用于 ANC 专项复测，`FULL_MATRIX` 仍保留给 Release 候选或跨模块验收。
- 2026-08-02 连接速度优化：已知型号首轮 Core 初始化使用 1 秒快速超时；3 秒恢复、自动低延迟重试和 Deferred 探测移到同一 command lane 的后台初始化任务，不再阻塞首轮 CoreReady；未知/兼容端点继续使用 3 秒首轮预算。
- 2026-08-02 实机报告回灌：调整前报告 `/Users/chenhong/Downloads/fxxkHilife_hardware_regression_1785615540067.txt` 的 A/B/C/E 各 10 轮为 PASS，但约 30 秒耗时是后续 `PeriodicCheck` 连接被旧 Runner 误归属；D 为 0/10（`startDiscovery=false` 未走绑定设备回退），F 为 0/10（ACL 断开后旧 session/连接入口未收敛），功能检查因同一归属问题未建立有效控制通道。每次有效 attempt 的 endpoint 均为 `rfcomm-channel=1 source=VerifiedModelConfig`；诊断阶段样本的 Transport→CoreReady P50/P95 约 `829/888ms`，CoreReady→Ready P50/P95 约 `2087/2163ms`。这些结果作为重构前基线，不作为新代码的通过证据。
- 2026-08-02 报告回灌后的代码修复：自动测试的自动连接结果现在从 `ConnectionCommandResult.Accepted.attemptId` 直接取得，不再在请求后读取易被周期检查覆盖的当前 attempt；空 attempt 不再接受后续后台连接作为样本结果。D 走 `startDiscovery=false` 的绑定设备回退并用一次性终态回调防重复建连；F 的真实 ACL 断开现在由 `SystemBluetoothMonitor` 发出 `SystemAclDisconnected` typed command，统一执行 session/reader/init 清理，再保留 250ms RFCOMM 重连排水窗口。报告新增 `TransportConnect`、`Transport→CoreReady`、`CoreReady→Ready` 的 P50/P95 字段。
- 2026-08-02 连接生命周期所有权继续收敛：`EarbudConnectionManager` 接管 `EarbudSessionRegistry` 和 `ConnectionAttemptCoordinator`，Repository 只通过 Manager 的 identity-aware 接口安装、摘除、校验 session 和 attempt；迟到的属性、EOF 和初始化回调不再直接比较 Repository 内部生命周期对象。
- 2026-08-02 本轮本地验证：`./scripts/run_ci_checks.sh ci-output-lifecycle-owner-fixed` 通过（diff-check、Debug/Release 单元测试、Debug/Release 构建）；Debug APK 为 `/Users/chenhong/Documents/fxxkhilife/fxxkHilife/app/build/outputs/apk/debug/app-debug.apk`，SHA-256=`b08090e1d64ffc3836eb3b823a52b7c1f4a51023c43ed131dca0cf32e81123e0`；Release SHA-256=`6ec9827fdccae5d5d0a08c7048ea9e1878bdb2407711bbfbcbd5960bc9794e15`。该次构建使用当时的 `4.2.6 / 87` 基线，未以本地构建代替实机验收。
- 2026-08-08 实机报告回灌：`/Users/chenhong/Downloads/fxxkHilife_hardware_regression_1786170584449.txt` 顶层仍为 `format=1`，只有嵌入的诊断段为 `format=2`，且只包含 3 条功能检查，不符合当前工作树中 A-F 60 项 + 功能 40 项、顶层 `format=2` 的报告契约，因此不能作为当前新 APK 的验收证据。它仍暴露出可复现的旧 Runner 问题：A/B/C/E 各 10/10 PASS 但耗时约 30 秒，D 为 0/10（扫描完成未返回保存设备），F 为 0/10（ACL 重连未稳定，后续轮次出现初始连接失败），功能检查均因控制通道归属失败而失败；有效 attempt 仍为 `rfcomm-channel=1 source=VerifiedModelConfig`，诊断样本 Transport→CoreReady 为 `866ms`、CoreReady→Ready 为 `2164ms`。日志还显示 `PeriodicCheck` 在矩阵中抢占连接槽。
- 2026-08-08 报告回灌后的修复：回归 Runner 在 discovery 成功但没有 `ACTION_FOUND` 时使用保存设备作为明确的 `saved-fallback`，并保留扫描终态一次性闸门；测试期间只暂停 `PeriodicCheck`/`AudioProfileConnected` 后台探测，Service/Tile/ACL/HardwareRegression 等显式入口仍走生产 command path；F 场景先等待 CoreReady，再执行手动断开、RFCOMM 重连和 ACL 清理，`SystemAclDisconnected` 改为同步清理旧 session/reader/init，避免立即 ACL 重连复用旧 attempt；ANC、低延迟和入口去重检查也先等待 CoreReady。
- 2026-08-08 修复后本地验证：`./scripts/run_ci_checks.sh ci-output-acl-sync-regression-escalated` 通过（diff-check、Debug/Release 单元测试、Debug/Release 构建）；Debug APK SHA-256=`f24f2ebde90055ed662f073771463b4cac77a153a2d67b25825caf73888c4309`，Release APK SHA-256=`d34186d03a9ccb6cf1081f1abc1f10c45ee570e7ff254fa6e91d046b24ec101f`。该次构建使用当时的 `4.2.6 / 87` 基线；D/F 和 100 项新报告仍需同一设备重新运行后才能更新阶段完成标记。
- 2026-08-08 版本区分后的本地验证：`./scripts/run_ci_checks.sh ci-output-bt-4.3.0-88-final` 通过（diff-check、Debug/Release 单元测试、Debug/Release 构建）；当前测试包为 `4.3.0 / 88`，Debug APK SHA-256=`ba4c0cc9ea8cbd6716d1bde7821e9b6d2b3b6488062782c1f7218ed5ae5a8500`，Release APK SHA-256=`4c584ff640e701a7a6eab91cc2051bf15ab3c83a25e5dbf2836e8107d9c45906`。仍需同一设备重新运行 D/F 和 100 项实机矩阵后更新阶段完成标记。
- 2026-08-09 ANC 定向包本地验证：`./scripts/run_ci_checks.sh ci-output-bt-4.3.3-91` 通过（diff-check、Debug/Release 单元测试、Debug/Release 构建）；Debug APK SHA-256=`dc770772ffb36ac5dc2a4dbbc4143e863406d96dca9d5193345ba7e61f9dbcbb`，Release APK SHA-256=`5b083d9242a07908d306d3fda66ba3200f0c27dc503328d5cb66bc8ad8269bb`。默认自动测试 profile 已切换为 `ANC_WEAR_STATE`，每轮 10 秒摘戴观察；仍需主验证设备实机回归。

- 2026-08-08 实机报告 `/Users/chenhong/Downloads/fxxkHilife_hardware_regression_1786177384365.txt` 回灌：顶层 `format=2`，包含 A-F 60 轮和功能 40 轮；endpoint=`rfcomm-channel=1`，source=`VerifiedModelConfig`。A-F 为 `7/10、5/10、5/10、5/10、10/10、9/10`，初始化持续推进 `5/10`，ANC/自动低延迟/入口去重均 `10/10`。A-D 失败在约 `240ms` 进入 `transport_connect_returned_false`，旧 Runner 却等待约 30 秒；E 的首次失败后重试全部恢复，支持在统一 Transport 边界增加一次有界重试。成功样本 Transport→CoreReady 约 `0.8s`，CoreReady→Ready 约 `1.3s`；F 的 `coreToReady=0` 属于旧 Runner 把 CoreReady 当完整 Ready 的统计语义问题。
- 2026-08-08 报告后的代码修复已落地到 `4.3.1 / 89`：立即非超时 RFCOMM 拒绝默认重试 1 次、间隔 `250ms`，真实连接超时不重复延长；Transport 增加 generation 检查、旧 socket 清理和底层 cause 日志；Runner 按当前 `attemptId` 将 `Failed` 作为终态，控制通道阶段失败也立即结束等待，ANC/低延迟/入口去重从 `CoreReady` 开始，F 必须达到完整 `Ready/Degraded`。本轮仍需同一设备运行新 Debug 包后才能更新实机阶段完成标记。
- 2026-08-08 `4.3.1 / 89` 本地 CI：`./scripts/run_ci_checks.sh ci-output-bt-4.3.1-89` 通过（Debug/Release 各 51 个 JVM 单元测试、diff-check、Debug/Release 构建）；Debug APK SHA-256=`8e2510ac44316c1a34eb5b86dc604d00ca40b56794501a7b656770813ce3f8d1`，Release APK SHA-256=`f3ca20a13ea9463ebd6bd8c5eaa84c5814cd13ca3268cc123c587f19a7002059`。这只是静态/本地构建证据，尚未替代同一设备的实机验收。
- 2026-08-08 新实机报告回灌：`/Users/chenhong/Downloads/fxxkHilife_hardware_regression_1786192207596.txt` 为顶层 `format=2` 的完整 100 项报告，设备为 HUAWEI FreeBuds 6i，endpoint=`rfcomm-channel=1`、source=`VerifiedModelConfig`。A-F 为 `10/10、9/10、10/10、10/10、10/10、9/10`，初始化持续推进、ANC、自动低延迟、入口去重均为 `10/10`。唯一两次失败（B-5、F-1）都在 `495/500ms` 内发生两次 RFCOMM immediate rejection，未进入真实连接超时；说明上一版仅 1 次重试仍不足以覆盖热断开后的系统排水窗口。成功样本的 Transport→CoreReady P50/P95 约 `850/3840ms`（A 的单个异常样本拉高 P95），CoreReady→Ready 各场景约 `1.3/1.6s`；A-F 的约 `10.5s` 总耗时是 Runner 保留的 10 秒观察窗口，不是 Socket 到 Ready 的实际耗时，日志阶段显示常规 Ready 约 `2.4–3.3s`。
- 2026-08-08 报告后的本轮修复已落地到 `4.3.2 / 90`：立即拒绝重试从 1 次提升为 2 次（最多 3 个 Socket 创建），重试等待采用 `250ms/500ms` 线性排水间隔；真实连接超时仍立即终止，不重复延长 10 秒预算。初始化持续推进检查收紧为“同一 attempt + Core 状态有效 + 最终到达 `Ready/Degraded`”，不再把仅到 `CoreReady` 视为通过。下一步需要同一设备重新执行回归矩阵验证第三次 Socket 创建和严格初始化判定。
- 2026-08-08 子代理并行复核确认：4.3.1 报告为 `98/100`（A-F `58/60`、功能 `40/40`），B-5/F-1 是两次 immediate rejection 后仍失败；E 的 10/10 主要是 Transport 内部恢复，不能证明 Runner 外层“首轮失败后重试”。报告的约 `10.5s` 主要是固定观察窗口，不能作为连接耗时；诊断 `meta.*` 可能混入不同 attempt，阶段统计必须以逐条 `RESULT/ConnPhase` 为准。
- 2026-08-08 本轮代码补齐：`RfcommTransportConfig` 默认允许最多 3 个 Socket，按 `250ms/500ms` 线性排水；`SppDriver → RfcommSppTransport` 传递外层 `attemptId`，每次 immediate rejection、timeout 和最终失败都带 `attemptId` 与 `attempt=n/3`；初始化持续推进功能项现在必须满足同一 attempt、`Ready/Degraded` 终态、核心状态有效且 pending 为空；报告补充手机型号、Android API、固件和各阶段有效样本数/`coreToTerminal` P50/P95。
- 2026-08-08 本轮本地 CI：`./scripts/run_ci_checks.sh ci-output-bt-4.3.2-90` 通过（diff-check、Debug/Release 单元测试、Debug/Release 构建）；4.3.2/90 Debug APK SHA-256=`85665804ecd3efd29635c2931acb01d4363300f5e4bb6467709a377e27ef9e67`，Release APK SHA-256=`7db4c5ee28ebb0767d4d77dddeecf6f2d8cbf6df307522d1db444d7c99de41c9`。这仍是本地/静态证据，不替代同一设备的实机验证。
- 2026-08-09 新报告 `/Users/chenhong/Downloads/fxxkHilife_hardware_regression_1786277130416.txt` 已完成只读回灌和并行复核：`BT_TARGETED_36` 为 31/36；成功阶段的 Transport→CoreReady P50/P95=`860/888ms`、CoreReady→Ready P50/P95=`1350/1429ms`，失败集中于 F 的结果层终态快照和两轮传输三次立即拒绝。E=3/3 只能证明外层第一次请求最终成功，不能证明没有内部 Transport 重试。

### 0.3 阶段总表

| 阶段 | 状态 | 当前记录/完成条件 |
|---|---|---|
| BT-0.1 协议/能力本地审计 | [x] 已完成 | 2026-08-14；完成 Kotlin 协议、能力表、上游快照和固定十六进制样本核对；协议样本已由 `HuaweiSppPackageFixtureTest`/`HuaweiSppFramerTest` 覆盖，未提升未经实机验证的能力状态 |
| BT-0.2 连接速度实机基线 | [x] 已完成 | 2026-08-14；FreeBuds 6i 定向复测 F/初始化20项通过；Transport Connect P50/P95=`289/300ms`，Transport→CoreReady=`833/978ms`，CoreReady→Ready=`1363/1443ms`；范围仅限该设备、Android 36、固件 unknown 和 `BT_MANAGER_RUNTIME_20` |
| BT-0.3 连接路线决策门 | [x] 已完成 | 2026-08-01；endpoint/channel 已被实机确认，优先改连接编排、attempt 归属、session 生命周期和命令调度；当前测试包为 4.3.10 / 98 |
| BT-1 唯一生产 Transport | [x] 已完成 | 2026-08-14；唯一 `RfcommSppTransport` 路径及 endpoint/source 在同设备定向回归中稳定；保留跨型号/固件未覆盖边界 |
| BT-2 CommandClient / Scheduler / Feature | [x] 已完成 | 2026-08-14；命令目录、单一 command lane、ANC 权威读回和低延迟路径已有 JVM/人工/定向实机证据；未验证能力仍按型号表标记 |
| BT-3 ConnectionManager 收敛入口 | [x] 已完成 | 2026-08-14；`ConnectionLifecycle`、session/attempt/Job 所有权收敛，并由 `BT_MANAGER_RUNTIME_20` F10+初始化10 实机确认；`4.3.10 / 98` 补齐统计 flush、失败 session 断开、command 串行边界和同 attempt 状态映射收尾 |
| BT-4 通用蓝牙状态输出 | [x] 已完成 | `4.3.10 / 98` 完成原子快照投影、canonical channel、pending/failed 语义收敛、空值规范化、metadata-only core readiness 防误报、严格 PASS 报告校验和 5A 协议边界修复；94/94 Debug、94/94 Release 单元测试、`git diff --check`、Debug/Release 构建通过；同一主验证设备 `BT4_STATE_CONTRACT_5` 实机 5/5 通过。页面尚未迁移消费 |
| UI-0 UI 基线与资源清点 | [~] 目标受阻：等待实测报告 | 2026-08-15；静态路由/状态/设置 key/资源/渲染调用基线已写入 `docs/UI_BASELINE.md`，新增更新 manifest 契约；等待新 UI 截图和运行诊断门 |
| UI-1 设计令牌与渲染基础 | [~] 目标受阻：等待 GitHub CI 与定向实测 | 2026-08-15；`ref/sys/comp` 令牌、`AppScaffold`、公共 surface、alpha05 typed blur/glass adapter、无背景 Material 3 硬门和 `UiAssetCatalog` 已落地；等待 CI、截图/性能证据 |
| UI-2 typed State / Event / Navigation | [~] 目标受阻：等待实测报告 | 2026-08-15；`AppUiState`/`DeviceUiState`/`SettingsUiState`、typed `DeviceEvent`/`SettingsEvent` 已接入核心页面，保留兼容层；等待动作状态矩阵 |
| UI-3 全局外壳与主路由 | [~] 目标受阻：等待 GitHub CI 与定向实测 | 2026-08-15；背景/source 归属、稳定 key 和连接会话导航已收敛，仍需导航会话和外壳截图验证 |
| UI-4 设备功能页面 | [~] 目标受阻：等待实测报告 | 2026-08-15；Device/Gesture/ListeningStats 已使用统一 surface 和 typed event，电量/后台同步/EQ/双设备/选项开关/设备信息组件已拆至 `ui/DeviceFeatureComponents.kt`，能力矩阵验证待完成 |
| UI-5 设置、持久化与兼容层收口 | [~] 目标受阻：等待实测报告 | 2026-08-15；SettingsRepository、更新检查/下载/安装状态和 UpdateCard 已接入，仍需持久化、无障碍和更新流程验证 |

### 0.4 应用版本号方案

当前开发包为 **versionName `4.4.0` / versionCode `99`**；当前公开发布基线仍为 `4.3.10 / 98`。`4.3.9 / 97` 是 BT-0 协议固定样本 follow-up 包，`4.3.8 / 96` 是上一版 BT-4 状态契约 follow-up 包，`4.3.7 / 95` 是首轮 BT-4 契约测试包，`4.3.6 / 94` 是已通过实机门的 BT-3 连接运行时包，`4.3.5 / 93` 是状态/重试修复包，`4.3.4 / 92` 是 36 项定向回归包，`4.3.3 / 91` 是 ANC 摘戴状态修复包，`4.3.2 / 90` 是上一轮热重连/初始化回归包，`4.3.1 / 89` 是更早的完整实机报告包，`4.3.0 / 88` 是上一版 BT-1 实机报告包，`4.2.6 / 87` 仅作为本次重构的历史基线。BT-0 的审计、研究、日志和测试准备不单独发版。

| 里程碑 | 计划 versionName | 计划 versionCode | 说明 |
|---|---:|---:|---|
| BT-0 文档/诊断基线 | 4.2.6 | 87 | 保持当前基线，不发布 |
| BT-1 Transport 统一 | 4.3.0 | 88 | 第一版可回归的蓝牙传输重构 |
| BT-2 指令调度与 Feature | 4.3.1 | 89 | 命令目录、队列和能力拆分 |
| BT-1/BT-2 热重连回归包 | 4.3.2 | 90 | 第三次 Socket 创建、线性排水和严格初始化验收 |
| BT-2 ANC 摘戴状态稳定性包 | 4.3.3 | 91 | `2b2c` 只作变化提示，安静窗口后以 `2b2a` 作为权威状态 |
| BT-2 修复后 36 项定向回归包 | 4.3.4 | 92 | B/F/初始化各 10 轮，D/E 各 3 轮 |
| BT-2 状态/重试 follow-up 定向包 | 4.3.5 | 93 | `BT_STATE_RETRY_20`：F10、初始化10；状态并发与第四次 Socket 创建 |
| BT-3 连接管理收敛 | 4.3.6 | 94 | Manager 持有连接运行时状态、session 与生命周期 Job 的兼容收敛 |
| BT-4 通用蓝牙状态输出 | 4.3.7 | 95 | 分层连接状态和通用状态输出 |
| BT-4 状态契约 follow-up | 4.3.8 | 96 | 并发投影、能力域、空值语义和定向报告严格校验 |
| BT-0 协议样本 follow-up / BT-4 测试包 | 4.3.9 | 97 | 5A 双字节长度、完整帧/参数/CRC 边界与固定上游样本；BT-4 5 轮实机门后续在 4.3.10 收口 |
| BT 重构首个公开大版本 / BT-4 follow-up | 4.3.10 | 98 | 状态映射串行化、断开前统计 flush、失败 session 清理、command/handler 边界；BT4_STATE_CONTRACT_5 已 5/5 通过 |
| UI-0 基线与资源清点 | — | — | 只改描述文件、清单和诊断基线，不发包、不提升版本 |
| UI-1 / UI-ERR 修复测试包 | 4.4.0 | 99 | `UI_ERROR_FIX_ALPHA05`：真实 typed glass/blur、无背景 Material 3 fallback、列表稳定 key 和会话导航修复 |
| UI-2 typed State / Event / Navigation | 4.4.1 | 100 | `UI_STATE_EVENT`：页面状态、动作反馈、导航事件和兼容投影 |
| UI-3 全局外壳与主路由 | 4.4.2 | 101 | `UI_SHELL_ROUTES`：Home/Scan/Permission 以及连接入口收敛 |
| UI-4 设备功能页面 | 4.4.3 | 102 | `UI_DEVICE_FEATURES`：Device/Gesture/ListeningStats/Terminal 迁移 |
| UI-5 设置、持久化与兼容层收口 | 4.5.0 | 103 | `UI_COMPLETE`：Settings、无障碍、双模式验收并移除 UI raw property；若产生不兼容变更，再单独评估大版本 |

版本规则：

- 只有能独立编译、测试、回归和回退的代码里程碑才提升版本号；只改本文件或只做诊断不提升版本。
- `versionCode` 每次发布递增 1；BT-1 测试包使用 `88`，BT-1/BT-2 首轮修复包使用 `89`，热重连回归包使用 `90`，ANC 摘戴状态稳定性包使用 `91`，36 项定向回归包使用 `92`，状态/重试 follow-up 使用 `93`，BT-3 连接运行时收敛使用 `94`，BT-4 首轮契约包使用 `95`，状态契约 follow-up 使用 `96`，协议样本 follow-up 使用 `97`，BT-3 收尾/BT-4 follow-up 使用 `98`；UI 阶段除版本号外必须同时使用 `UI_FOUNDATION`、`UI_STATE_EVENT` 等明确构建标签，后续以 `98` 为当前基准顺延。
- 统一使用 `python3 scripts/bump_version.py <versionName> <versionCode> "变更说明"` 更新应用版本、资源、README、`VERSION_MANAGEMENT.md` 和 `DEVELOPMENT_LOG.md`。
- GitHub Actions 的手动构建必须填写或保留阶段标签；当前默认 `UI_ERROR_FIX_ALPHA05`，产物名、测试报告名和 metadata 都带该标签，避免只看版本号无法区分 UI 测试包。
- 阶段完成时，同时更新本文件的 `[x]`、`VERSION_MANAGEMENT.md` 的历史记录和 `DEVELOPMENT_LOG.md`；版本号不因提前勾选计划项而变更。

本节对应文件：`app/build.gradle.kts`、`app/src/main/res/values/strings.xml`、`VERSION_MANAGEMENT.md`、`scripts/bump_version.py`。

### 0.5 2026-08-14 UI-0 至 UI-5 计划核对结论

本轮开始前核对计划，执行顺序和技术边界没有需要推翻的地方，但原文有三处需要校正：

1. **阶段位置**：当前不是“只做 UI-0 文档规划”，而是 UI-0 基线、UI-1 基础层以及 UI-2 至 UI-5 的第一轮结构迁移已经并行落地；各阶段仍保持 `[~]`，因为没有 CI、截图、无障碍和真实运行证据，不能提前标记 `[x]`。
2. **验收粒度**：UI 不复用 BT 的 36/100 项矩阵。先验证公共外壳、玻璃渲染、状态事件、设置持久化和更新流程；设备功能只针对本轮迁移的组件做定向验证。
3. **当前实现缺口**：Device 的电量、后台同步、EQ 和双设备卡片需要从 route 文件拆出；更新卡片需要显示检查时间、渠道、发布日期和具体 Release 地址；玻璃配置必须真正进入 `SurfaceRenderer`，不能只通过 `CompositionLocal` 保存。
4. **本轮边界收口**：设置页已改为 `SettingsEvent` typed event，`AppUiState` 同时包含设备、扫描、设置和更新状态；安装 Intent 在 ViewModel/更新仓库生成，页面只发送事件，路由负责启动系统安装器。新增 `scripts/validate_ui_contract.py`，在 CI 的 Gradle 前检查单一渲染适配器、typed event 和页面持久化边界。版本号仍保持 `4.3.10 / 98`，等待 UI 定向 CI 与截图证据。

本轮实现顺序固定为：先修正导航/更新边界和静态错误，再拆 UI-4 设备组件，随后交给 GitHub Actions 构建；在构建包和截图返回前，UI-0 至 UI-5 都只记录为“实现中”，不提升版本号。

### 0.5.1 2026-08-15 UI-0 至 UI-5 静态收口记录（历史记录）

- `AppNavHost` 已固定为单一 `AppScaffold -> NavHost` 外壳；页面不再创建第二个背景 source。
- Settings 已统一为 `SettingsEvent`；`AppUiState` 现在同时投影设备、扫描、设置和更新状态，安装 Intent 由 ViewModel/更新仓库产生。
- Device 的通用选项、开关和设备信息卡已与电量、后台同步、EQ、双设备卡一起移入 `DeviceFeatureComponents.kt`。
- `scripts/validate_ui_contract.py` 已加入 CI 构建前置检查；手动构建默认使用 `UI_FOUNDATION_SMOKE` 标签，产物和报告不再只依赖版本号区分。
- `SettingsRepository` 的玻璃配置读取已补齐 typed profile/renderer 依赖；UI 路由已改用生命周期感知的 Flow 收集，Home 保存设备刷新只绑定目标地址；`git diff --check`、UI contract、更新 manifest、Android XML、Workflow YAML 和 Kotlin 分隔符静态检查通过；未执行本地 Gradle，仍待 GitHub Actions、截图、无障碍和实际运行证据。

### 0.5.2 2026-08-15 UI-0 至 UI-5 编译前收口（历史记录）

- 计划核对结论保持不变：UI-0 至 UI-5 的顺序、版本边界和“先代码、再 CI、再定向运行验收”规则可执行；本轮不改蓝牙组件和应用版本号。
- 第一轮结构迁移已覆盖统一 `AppScaffold`/surface adapter、typed UI state/event、Home/Scan/Permission 主路由、Device 功能组件、SettingsRepository 和更新状态/下载安装边界。
- 编译前静态门已全部通过：`git diff --check`、`scripts/validate_ui_contract.py`、`scripts/validate_update_manifest.py`、Android XML、Workflow YAML 和 Kotlin 分隔符扫描。
- UI-0 至 UI-5 继续标记为 `[~]`：当前结果只证明源码边界和静态契约，不能替代 GitHub Actions 编译、classic/glass 截图、无障碍和实际设备运行证据。
- 下一步固定为使用 `UI_ERROR_FIX_ALPHA05` 构建标签交给 GitHub Actions；包返回后只做 UI 定向验证，不重复 BT 36/100 项矩阵。

### 0.5.3 2026-08-15 Haze 依赖计划复核与修正（历史记录）

- 计划原先把官方最新的 Haze `2.0.0-alpha05`、typed API、`haze-glass` 和 UI 页面迁移绑定在同一个阶段，这会把 UI 重构和 Android 构建工具链升级混成一个不可定位的变更，计划有问题。
- GitHub Actions `31819769359` 已给出明确阻断证据：alpha05 的 `haze`、`haze-blur`、`haze-glass` 要求 compileSdk 37；解析出的 Lifecycle Compose 2.11.0 同时要求 compileSdk 37 和 AGP 9.1，而项目当前基线是 compileSdk 36 / AGP 8.9.1。失败发生在 `checkDebugAarMetadata`，不是页面逻辑或 Haze 是否被调用。
- 当时的 UI-0 至 UI-5 暂用 Haze 2.0 `2.0.0-alpha03` 兼容基线，作为后续修复前的历史方案；该方案没有 `haze-glass`，不能作为当前实现或 Liquid 证据。
- 当前开发包已由 0.5.6 独立迁移至 alpha05 typed API；`MATERIAL3`、`FROSTED`、`LIQUID` 三态解析和真实运行证据门仍然有效。

### 0.5.4 2026-08-15 CI 编译跟进（历史记录）

- `5c6e2d6` 对应的 GitHub Actions `31821253213` 已越过 AAR metadata 门，但暴露了第一轮结构迁移的 Compose import 缺口：`DeviceFeatureComponents` 缺少 `remember`/`mutableStateOf`/delegate import，`AppScaffold` 的返回图标引用方式不兼容；已在 `e073cab`、`df7bfd2` 修正。
- 随后的 CI 已进入 JVM 测试，98 个测试中只有 `UpdateManifestTest` 2 项失败，根因是本地单元测试使用 Android `org.json.JSONObject` stub，`getInt` 直接抛出 `Method ... not mocked`；这不是更新业务逻辑失败。已加入 test-only `org.json:json` 实现，生产 APK 不携带该依赖。
- 当时仍不标记 UI-0 至 UI-5 完成，也不提升版本号；本次 GitHub Actions 已通过，下一步进入 `UI_GLASS_RENDERING_TARGETED`、截图、无障碍和性能定向验收。

### 0.5.5 2026-08-15 UI_FOUNDATION_SMOKE 通过，进入定向实测门（历史记录）

- GitHub Actions [31822271929](https://github.com/ct-yx/fxxkHilife/actions/runs/31822271929) 已通过：`diff-check`、UI contract、Debug/Release 单元测试和 Debug/Release 构建均通过；Debug/Release 各 `98 tests`，`0 failures / 0 errors / 0 skipped`。
- Debug 包：`fxxkHilife-debug-UI_FOUNDATION_SMOKE-v4.3.10-98.apk`，SHA-256=`6a487c5d699f5fab65dcaa931a2aefd36ec8b8aac63917921b682b01d448169d`。
- Release 包：`fxxkHilife-UI_FOUNDATION_SMOKE-v4.3.10-98.apk`，SHA-256=`c4e6f68b5d2575610a93b4e150e6dddaec0f45ae722abc0df622c1a2d50d02b2`。本轮实测优先安装 Debug 包，Release 只做构建对照。
- 报告产物：`automated-test-report-UI_FOUNDATION_SMOKE-v4.3.10-98`。构建标签已用于区分本轮 UI 包，版本仍保持 `4.3.10 / 98`。
- UI-0 至 UI-5 已完成第一轮代码结构迁移，但因为尚无真实截图、渲染诊断、无障碍、性能和更新流程报告，阶段状态统一改为“目标受阻：等待实测报告”，不提前标记 `[x]`。

**本次只测 UI，不重复蓝牙 A-F/36/100 轮：**

1. `UI_GLASS_RENDERING_TARGETED`：Debug 包中分别在 `CLASSIC` 和 `LIQUID_GLASS` 下打开 Home、Device、Settings；各截一张非纯色背景截图，并记录 `renderer`、`sourceAttached`、`effectSurfaceCount`、`fallbackReason`。切换 route、旋转/重建后确认 source/effect 数量不累积、页面不出现透明空洞。
2. `UI_CONTROL_STATE_MATRIX`：针对 ANC、低延迟、音质各执行一次 Idle、Pending、ACK/读回、Failure/Retry；核对按钮文字、选中语义、不可用能力隐藏和返回后状态，不操作未改动的蓝牙全量矩阵。
3. `UI_SHELL_ROUTES`：Home → Scan → Permission → Device → Settings 各走一遍；从应用、Service/Tile 和扫描结束入口分别进入一次，确认只创建一个外壳、返回栈正确且没有重复建连入口。
4. `UI_SETTINGS_PERSISTENCE` + `UI_ACCESSIBILITY_MATRIX`：修改主题、显示模式、壁纸、语言/大字和 reduced motion，重启应用后各核对一次；检查 TalkBack、焦点顺序、44dp 触控目标和高对比度 opaque fallback。
5. `UI_UPDATE_CHECK_TARGETED`：执行同版本 manifest 的“已是最新”、无网络、manifest 读取失败各一次；只验证检查时间、渠道、版本比较和错误状态，不下载安装未知 APK。

实测报告返回前，UI-0 至 UI-5 保持“目标受阻”；报告只需包含上述对应标签、截图/诊断日志和失败步骤，不需要再次跑 BT 回归。
- 本次只修正依赖、适配器、静态契约和计划记录，版本仍为 `4.3.10 / 98`；UI-0 至 UI-5 继续保持 `[~]`，等待 GitHub Actions、截图、无障碍和实际运行证据。

### 0.5.6 2026-08-15 UI 错误报告修复包（v4.4.0 / 99）

- UI-ERR-001：依赖和 production adapter 已迁移到 alpha05 typed `hazeGlass`/`hazeBlur`、`GlassStyle`、`GlassOptics.Fixed`；`HazeGlass` 不再复用旧 `hazeEffect + blurEffect` 实现。
- UI-ERR-001：`AppScaffold` 只有在壁纸 URI 有效且图片加载成功后才绑定唯一 source；无壁纸、加载中或加载失败时，surface 解析为 Material 3，诊断同时记录 requested renderer、actual renderer、source 和 fallback reason。
- UI-ERR-002：保存设备、扫描设备和双连接设备使用地址稳定 key；长列表默认 Tint/Material 3，不逐行创建 glass/blur effect；展开内容只保留 `AnimatedVisibility` 尺寸动画；路由使用生命周期感知收集，Home 刷新只绑定目标地址。
- UI-ERR-003：设备详情自动进入绑定 `address + attemptId`，系统触发只消费一次；用户返回后同一会话不再自动重开，Home/Scan 点击设备都显式导航到详情页。
- 本轮版本为开发测试包 `4.4.0 / 99`，不修改 BT-0 至 BT-4 和既有蓝牙实机矩阵；GitHub Actions 构建标签改为 `UI_ERROR_FIX_ALPHA05`。
- 当前目标受阻点：等待 GitHub Actions 构建、`UI_GLASS_RENDERING_TARGETED`、`UI_GLASS_PERFORMANCE`、`UI_NAVIGATION_SESSION` 和无背景 fallback 截图/运行诊断；未执行本地 Gradle，不将静态门当作 UI 完成证据。

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
    UI[Compose Screens<br/>Home / Scan / Device / Settings / Gesture]
    VM[DeviceViewModel<br/>薄透传层]
    Repo[DeviceRepository<br/>连接 + 轮询 + 属性 + 统计 + 自动连接]
    Session[LegacySppEarbudSession]
    Driver[SppDriver<br/>Socket + 收包 + 请求 + Handler + 属性]
    Handler[Huawei capability handlers<br/>Battery / ANC / Audio / Gesture / EQ / DualConnect / Info]
    Protocol[HuaweiSppPackage / HuaweiSppProtocol / Framer]
    Transport[RfcommSppTransport / RfcommSocketBridge]
    Service[BluetoothService / QuickSettingsTile / SystemBluetoothMonitor]

    UI --> VM --> Repo --> Session --> Driver
    Driver --> Handler
    Driver --> Protocol
    Driver --> Transport
    Service --> Repo
    Service --> Driver
```

现有骨架已经具备 `core/transport`、`core/protocol`、`core/session` 和 `core/adapter`。BT-1 迁移后，生产 Socket、输入流、输出流和关闭逻辑统一由 `RfcommSppTransport` 承载；`SppDriver` 只保留 Huawei 分帧会话、请求响应、Handler 和属性兼容层。后续不再增加第二套并行 Socket 实现。

### 2.1 合并后的架构决策（唯一方案）

本节吸收原 `ARCHITECTURE_TODO.md` 的通用架构内容，并在每个子计划中明确当前采用的方案。选择标准是：先满足当前主验证设备的稳定性，再保留真实可用的扩展 seam；不为尚不存在的第二种 Transport、Protocol 或 Adapter 提前堆叠抽象。

| 子计划 | 候选方向 | 当前选择 | 选择理由与边界 | 验收证据 |
|---|---|---|---|---|
| Transport | RFCOMM、BLE GATT、Native bridge 并行抽象 | 保留小型 `EarbudTransport` seam；生产只使用 `RfcommSppTransport` | 当前设备只有 RFCOMM/SPP 实机证据；BLE/Native 先不实现，避免第二条 Socket/Reader 路径分叉 | `RfcommTransportConfigTest`、分帧/传输 JVM 测试、实机阶段耗时 |
| Protocol | 大一统厂商无关协议，或 Huawei 单协议深模块 | 采用 `ProtocolSession + HuaweiSppFramer + HuaweiCommandCatalog/Client/Scheduler` | 先把分帧、请求响应和 command lane 做深；厂商差异留在 Adapter，不让 UI 或 Transport 感知 | Framer/Session/Command 测试、ACK/读回结果、超时和重连报告 |
| Capability / State | 继续扩张 `DeviceProps`，或立即全量替换 | `EarbudCapability + EarbudState + ControlChannelState` 作为目标模型，`DeviceProps` 仅作兼容映射 | 页面只消费已映射且已确认的能力；迁移期间不破坏现有页面和通知 | StateStore/Mapper 测试、能力隐藏检查、逐页 UI 验证 |
| Adapter | 动态插件系统，或静态内置注册表 | 采用静态 `EarbudAdapterRegistry`，先维护 `HuaweiOpenFreebudsAdapter` | 当前只有一个真实 Adapter；静态注册更容易构建、回退和审计。出现第二个真实 Adapter 后再增加优先级匹配 | Adapter 匹配测试、独立日志标识、型号能力表 |
| Connection orchestration | Repository 继续全包，或 Manager/Repository 双重编排 | `EarbudConnectionManager` 作为唯一连接编排者；Repository 暂作兼容副作用门面 | Manager 持有 attempt/session/state/Job 生命周期，避免多个入口重复建连；下一阶段再收窄 host interface | `ConnectionLifecycleTest`、BT-3 `BT_MANAGER_RUNTIME_20` 实机报告 |
| Statistics / logging | 立即把所有统计和日志拆成多个 Repository，或继续堆进 Manager | 已抽出 `ListeningStatsRepository`；日志继续由 `LogBuffer + regression report` 负责 | 统计拆分不改变连接时序；断开前由 Repository flush 最后一段连接时长，暂不增加一层无行为收益的 `LogRepository` | 统计单元测试、报告格式检查、连接回归无行为变化 |
| UI state / rendering | 页面直接读 raw property，或先建立状态/事件再拆页面 | `Route -> ViewModel -> typed state/event -> Repository/UseCase`；统一 `AppScaffold`，保留 Haze 2.0 | 先完成 UI-0 基线，再按 UI-1 至 UI-5 分阶段替换；玻璃渲染只属于 UI surface，不参与连接和持久化 | UI-0 基线、UI-1/2 状态与事件测试、UI-5 classic/glass 双模式验证 |
| Hardware validation | 每轮都跑完整矩阵，或只测本轮影响面 | 按改动定向测试；当前仅 `BT4_STATE_CONTRACT_5` 5 轮 | 状态契约改动只验证同一 attempt、Ready、能力集合、读回投影和 pending/failed 终态；不重复无关场景 | Debug-only 自动测试报告；Release 不暴露测试按钮 |

### 2.2 合并后的执行顺序

```text
BT_MANAGER_RUNTIME_20 实机报告
  -> 收口 BT-1 / BT-2 / BT-3 的实机门
  -> BT-4：冻结通用连接状态、EarbudCapability、EarbudState 映射契约
  -> ListeningStatsRepository：从连接编排中移出统计
  -> UI-0：行为、状态、设置 key、美术资源和 DeviceProps 基线（已形成，等待截图/诊断）
  -> UI-1：设计令牌、主题、AppScaffold、surface adapter、UiAssetCatalog
  -> UI-2：AppUiState / DeviceUiState / typed event / NavigationEvent
  -> UI-3：全局外壳与 Home/Scan/Permission 主路由
  -> UI-4：Device/Gesture/ListeningStats/Terminal 功能页面
  -> UI-5：Settings / DataStore / 无障碍 / classic-glass / raw property 收口
  -> 第二个真实 Adapter 接入评估
```

每一步都按“代码 → JVM/静态测试 → GitHub Actions → 定向 UI 验证 → 文档回写 → 可回退提交”的顺序执行。当前处于 UI-0 基线和 UI-1 至 UI-5 第一轮结构迁移阶段；进入真实设备门后只追加本阶段对应的测试，不扩大为完整 BT 矩阵。

### 2.3 明确延后的方案

- `BleGattTransport`、`NativeBridgeTransport`：当前没有目标设备和协议证据，保留接口位置，不实现伪适配。
- 动态插件加载、多个 Adapter 试探匹配和多 SPP session：等第二个真实 Adapter 或多设备控制需求出现后再设计。
- 未验证的 Custom EQ payload：继续只读或按已验证能力展示。
- Haze 依赖冻结：当前 UI-1 锁定 Haze 2.0 alpha05 的 `hazeSource`/`hazeGlass`/`hazeBlur` typed 组合；compileSdk 37、AGP 9.1 和 Lifecycle Compose 2.11.0 已作为同一构建门升级，旧 alpha03 组合只保留为历史迁移记录。
- 全量 `LogRepository`：现有 `LogBuffer` 和报告导出已经满足诊断需求，避免先做无收益的转发层。

## 3. 大项一：UI 规范与重构

### 3.0 本轮 UI 重构范围、审计结论与资源保留边界

本轮采用“全量 UI 重构、分阶段替换”的方式，而不是继续在旧页面上叠加样式修补。重构对象包含页面布局、主题令牌、组件、状态展示、导航、设置持久化、文案和无障碍；蓝牙协议、连接时序和已经通过 BT-4 的状态契约作为稳定依赖，UI 只通过 typed state/event 消费它们。

#### 当前 UI 基线

当前 UI 目录共 14 个 Kotlin 文件、约 183 KB、4325 行；主要复杂度集中在以下文件：

| 文件 | 规模 | 当前职责 | 重构去向 |
|---|---:|---|---|
| `ui/DeviceScreen.kt` | 935 行 | 设备信息、ANC、音频、低延迟、双连、卡片布局和属性写入 | `device/route`、`device/state`、`device/components` |
| `ui/SettingsScreen.kt` | 1002 行 | 主题、语言、壁纸、玻璃参数、连接偏好、调试和关于 | `settings/route`、`settings/state`、`settings/components`、`SettingsRepository` |
| `ui/AppNavHost.kt` | 251 行 | 路由、自动跳转、主题/语言/壁纸、显示模式、Haze 状态 | `app/AppNavHost`、`AppUiState`、`AppScaffold` |
| `ui/HomeScreen.kt` | 382 行 | 首页设备列表、连接状态和自动跳转 | `home/route`、`home/components`、统一导航事件 |
| `ui/glass/AdaptiveGlass.kt` | 525 行 | 玻璃/经典 surface 的渲染分支和 Haze 调用 | `foundation/surface`；保留 Haze 适配器 |
| `ui/GestureScreen.kt`、`ScanScreen.kt`、`ListeningStatsScreen.kt` | 245/201/189 行 | 手势、扫描、统计页面各自维护布局和状态 | 各自 route + typed state + 公共组件 |
| `ui/TerminalActivity.kt`、`layout/*terminal*.xml` | 218 行及 XML | 终端调试入口和旧 View 布局 | 先保留调试能力，后迁移为统一终端 route |

已确认的重构触发点：页面直接使用 `DeviceProps` 和 `setProperty(group, prop, value)`；多个页面重复实现 `Scaffold`、TopBar、卡片和间距；`SharedPreferences` 在 Composable 内直接读写；旧 `CLASSIC`/`LIQUID_GLASS` 分支与 Haze 状态跨页面传递；连接成功后的跳转依赖异步调用返回瞬间的状态。上述问题全部纳入完全 UI 重构，旧页面和兼容 facade 不能继续作为 production fallback。

#### 应用更新能力纳入范围

检查更新和自动更新也纳入本次 UI 重构，但它属于“设置/关于 + 应用级状态”的独立能力，不进入 Bluetooth Manager、Service、Tile 或启动时的连接编排：

- 旧 `app/src/main/java/com/freebuds/controller/data/UpdateChecker.kt` 是未接入页面的最小 API 草稿，已在 UI-5 第一轮迁移中删除；当前由 `UpdateRepository` 负责 manifest、版本比较、缓存、校验、下载和安装状态。
- 当前 Settings 的“更新地址”按钮只打开 Release 页面；页面没有“检查中、已是最新、有新版本、下载中、待安装、失败/重试”等状态，也没有自动检查开关。
- `AndroidManifest.xml` 已加入更新网络、安装权限和受控 `FileProvider`；本轮仍不引入 WorkManager，自动检查只在应用前台生命周期合并触发，长期后台调度留给后续独立阶段。
- UI-0 盘点现有入口、版本字段、Release 资产、签名/安装约束和本地化 key；UI-2 冻结 typed state/event；UI-5 实现设置页、更新仓库、下载校验和系统安装交互。

更新功能的产品边界固定为：**自动检查可以后台执行，自动安装始终交给 Android 系统确认**。默认提供“手动检查”和“自动检查”两种开关；“自动下载并提醒安装”作为单独可选项，不在蓝牙服务启动、ACL 回调或每次页面重组时触发。

#### 美术资源保留清单

下列资源属于现有视觉资产，继续保留并在新 UI 中通过语义目录引用：

- `app/src/main/res/drawable/ic_anc_awareness.xml`
- `app/src/main/res/drawable/ic_anc_cancellation.xml`
- `app/src/main/res/drawable/ic_anc_normal.xml`
- `app/src/main/res/drawable/ic_earbuds_case.xml`
- `app/src/main/res/drawable/ic_tile.xml`
- `app/src/main/res/mipmap-hdpi/ic_launcher.png`
- `app/src/main/res/mipmap-mdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xhdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xxhdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`

以下资源也继续保留，但分别按“文案/主题/兼容布局/运行配置”迁移，不把它们误判为可删除的旧美术资源：

- 多语言：`res/values/strings.xml`、`res/values-en/strings.xml`、`res/values-zh-rTW/strings.xml`。
- 主题与颜色基线：`res/values/colors.xml`、`res/values/styles.xml`、`res/values/themes.xml`；新主题完成验收前保留，之后仅清理已经无引用的定义。
- 旧终端布局：`res/layout/activity_terminal.xml`、`res/layout-land/activity_terminal.xml`；终端迁移完成前保留。
- 运行配置：`res/xml/file_paths.xml`；仅随功能需要调整，保持文件分享契约稳定。

资源迁移规则：

1. UI-0 建立 `UiAssetCatalog` 的语义映射，例如 `AncMode.Awareness -> R.drawable.ic_anc_awareness`；页面只引用语义资源，不散落 `R.drawable` 判断。
2. 新页面替换旧页面前，旧资源保持可编译、可回退；验证通过后再清理无引用资源，并在提交中记录删除清单。
3. 图标、启动图和本地化 key 保持现有语义与兼容名称；新文案只增加统一的 i18n key，不在 Composable 中硬编码用户可见文本。
4. 美术资源保留与布局规则重写分开处理：保留图形资产，不延续旧页面的颜色、圆角、阴影和间距组合。

#### UI 重构完成形态

```text
AppUiState
  ├─ AppScaffold / NavigationEvent
  ├─ ThemeState / DisplayStyle / WallpaperState
  └─ ConnectionSummary

Route -> ViewModel -> UiState / UiEvent -> UseCase -> Repository / Manager
                         └─ UiAssetCatalog + semantic UiTokens

EarbudState + capabilities -> feature sections
Haze 2.0 / Material 3       -> surface adapter only
```

完成后，页面可以整套替换而不改 Bluetooth Manager；经典和玻璃模式使用相同的内容、能力判定和交互状态，只切换 surface/render profile。

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

- 旧 `CLASSIC` / `LIQUID_GLASS`、`HazeState`、`LiquidGlassConfig` 参数曾在多个页面间层层传递；完全重构将改为 `RequestedDisplayMode` + `BackgroundState` + `ResolvedSurfaceMode`，页面不再接收 Haze 状态或视觉常量。
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
  update/
    UpdateRoute.kt              # 检查、下载、安装状态的 UI 入口
    UpdateUiState.kt
    UpdateEvent.kt
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
- 更新操作必须区分 `Checking`、`UpToDate`、`Available`、`Downloading`、`ReadyToInstall`、`Installing`、`Deferred` 和 `Error`；下载失败、哈希不匹配、签名/包名不符和系统安装设置均显示结构化原因。
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

应用级更新事件单独放在 `AppEvent`/`UpdateEvent`，不混入 `DeviceEvent`：`CheckNow`、`EnableAutoCheck`、`EnableAutoDownload`、`Download`、`CancelDownload`、`Install`、`DismissVersion`、`OpenReleasePage` 和 `Retry`。ViewModel 只调用更新用例，不让 Composable 直接访问网络、文件或安装器。

ViewModel 负责把事件交给用例；用例再调用协议无关的控制接口。页面中不出现 `setProperty(group, prop, value)`。

### 3.5 UI 规范

- 采用 Apple/HIG-inspired 的清晰层级 + Material 3 语义组件；颜色只使用语义色，不在页面硬编码近似色值。
- 统一 `AppScaffold`、顶部栏、section 标题、设置行、状态横幅、空状态和错误状态。
- 普通/玻璃展示模式共享内容组件，只替换 surface/render profile；避免每个页面各写一份布局。
- 交互目标至少 44dp；支持键盘焦点、TalkBack、较大文字、深色/浅色和 reduced motion。
- 统一 loading、disabled、pending、失败重试和成功反馈，不用瞬时乐观值掩盖失败。
- 设置持久化迁移到 `SettingsRepository`，优先使用 DataStore；Composable 只读写状态，不直接操作 `SharedPreferences`。
- 所有用户可见文字走 i18n key；协议 raw 值只能出现在调试终端和日志页面。
- 更新状态、下载进度、待安装提示和失败原因使用同一套 `AsyncActionIndicator`/`ErrorState`；自动检查不弹出打断蓝牙控制的全屏页面。

#### 3.5.2 统一按钮、选项与异步动作控件

所有页面必须使用同一套 typed 控件契约；业务页面只声明语义、状态和事件，不自行组合 Material Button、Switch、Dropdown、Slider 或玻璃效果。控件的 surface 由 `SurfaceRenderer` 决定，业务控件不直接导入 Haze。

**按钮类型：**

| 类型 | 用途 | 默认 surface | 约束 |
|---|---|---|---|
| `PrimaryActionButton` | 页面主操作、保存、连接、确认 | Hero/重点区域可用低强度 Haze effect profile；普通场景使用 Material primary | 同一 surface 只允许一个主操作；Pending 时显示进度，不重复发送 |
| `SecondaryActionButton` | 次级操作、刷新、选择设备 | `hazeBlur` 或低强度 tint | 不与主操作竞争视觉层级 |
| `TertiaryActionButton` | 取消、稍后处理、打开详情 | transparent/tint | 保持 44dp 触控区域，不用高折射玻璃 |
| `DestructiveActionButton` | 断开、删除、清除 | opaque/tint error surface | 必须有确认或可撤销反馈，不使用透明红色叠加玻璃 |
| `IconActionButton` | 设置、返回、更多、终端入口 | 继承父级 surface；必要时 `hazeBlur` | 必须有 contentDescription、焦点语义和最小触控尺寸 |
| `ToggleButton` | 显示风格、模式、快速开关 | 高/低强度 Haze effect 或 tint 的 selected/unselected surface | selected 状态不能只依赖颜色，必须有语义和图标/文字差异 |

**按钮状态：**

```kotlin
sealed interface UiActionState {
    data object Idle : UiActionState
    data object Disabled : UiActionState
    data class Pending(val label: UiText? = null) : UiActionState
    data object Success : UiActionState
    data class Failure(val error: UiError, val canRetry: Boolean) : UiActionState
}
```

- `Pending` 禁止重复提交；按钮保留原布局尺寸，使用进度指示器和明确文案。
- `Success` 提供短暂但可读的状态反馈；不能只闪烁颜色或依赖玻璃高光。
- `Failure` 显示结构化原因，并由 `Retry` 事件重新执行同一 typed action。
- `Disabled` 与“能力不存在/当前状态不可用”分开表达；能力隐藏使用 section 不渲染，业务错误使用 `Failure`。
- reduced motion 下不使用弹跳、折射动画或连续 shimmer；状态变化保留文本/语义反馈。

**选项控件：**

| 控件 | 数据类型 | 适用场景 | 统一行为 |
|---|---|---|---|
| `BooleanOptionRow` | `Boolean` | 自动暂停、低延迟、自动检查 | Switch/checkbox 只发送 typed event，写入显示 Pending→Readback→Success |
| `SingleChoiceOption` | enum/value | ANC 模式、音质、主题、语言 | 单选列表/菜单统一使用 `UiOption<T>`，当前值未知时不默认选中 |
| `DependentChoiceOption` | parent + child | ANC 模式与级别、手势目标与动作 | 父选项变化后清理无效子选项，等待设备读回后再确认显示值 |
| `SegmentedOption` | sealed enum | CLASSIC/LIQUID_GLASS、显示 profile | 适合少量互斥值；selected 由语义状态表达，不只依赖颜色 |
| `SliderOption` | bounded numeric | 亮度、玻璃强度、可调参数 | 显示当前值和范围；拖动结束后提交，避免每个 frame 写设备或重建 Haze style |
| `ActionPicker` | typed action | 双击、三击、长按、滑动 | 选项由能力集合过滤；未验证 action 不伪造为可用 |

统一选项模型：

```kotlin
data class UiOption<T>(
    val value: T,
    val title: UiText,
    val description: UiText? = null,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val unavailableReason: UiText? = null,
)

data class OptionUiState<T>(
    val options: List<UiOption<T>> = emptyList(),
    val selectedValue: T? = null,
    val pendingValue: T? = null,
    val error: UiError? = null,
)
```

`UiOption` 的 `title`/`description` 由 `UiTextMapper`/`OptionPresenter` 生成；页面不得继续维护 `chineseAncMode`、`chineseSoundQuality`、`chineseTap` 等 raw-to-text 映射。`enabled=false`、`unavailableReason`、`selectedValue=null` 和 `pendingValue` 必须分别表达，不能用空字符串、默认值或 0 混淆。

**液态玻璃控件表现：**

1. 控件内容层与玻璃 surface 分离：`SurfaceRenderer` 负责高强度 Haze effect → 低强度 blur → tint → opaque 选择，按钮/选项只提供 `SurfaceRole`、`selected`、`UiActionState` 和内容。
2. 主按钮和选中的分段选项可以使用低强度 Haze effect；高强度 Hero 只作为父级 surface，按钮内部不再嵌套第二个独立 `HazeState`/source。
3. 次级按钮、设置行和大量选项默认使用 `hazeBlur` 或 tint；长列表不逐项创建 effect，优先由父级 Card 承载一个 Haze surface。
4. Pending、Failure、Disabled 使用稳定的 Material 语义色、图标和文本；不通过改变 blur、折射或透明度表达业务状态。
5. 高对比度、大字体、低性能设备、无壁纸或 source 不可用时，控件保留相同布局、触控区域、焦点顺序和语义，surface 依次降级到 `hazeBlur`、tint 或 opaque。
6. 所有按钮和选项最小触控目标为 44dp；Icon-only 控件必须提供 content description，选择器必须向 TalkBack 暴露当前值、可用选项和 Pending/Failure 状态。

**统一组件目录：**

```text
ui/foundation/components/
  PrimaryActionButton.kt
  SecondaryActionButton.kt
  TertiaryActionButton.kt
  DestructiveActionButton.kt
  IconActionButton.kt
  ToggleButton.kt
  BooleanOptionRow.kt
  SingleChoiceOption.kt
  DependentChoiceOption.kt
  SegmentedOption.kt
  SliderOption.kt
  ActionPicker.kt
  AsyncActionIndicator.kt
  OptionPresenter.kt
  UiTextMapper.kt
```

这些组件先在 UI-1 建立 visual/state contract，UI-2 接入 typed event 和 `OptionUiState`，UI-3/UI-4 替换 Home、Device、Gesture、Stats、Terminal 的局部控件，UI-5 替换 Settings 和更新流程中的所有按钮/选项。


UI 重构当前锁定 Haze 2.0 `2.0.0-alpha05` typed API 作为可编译实现基线。官方截至 2026-08-15 最新发布测试版为 `2.0.0-alpha05`，没有 2.0 beta/stable；alpha05 要求 compileSdk 37/AGP 9.1，因此工具链升级已作为同一可回退构建门合入 `v4.4.0 / 99`，不再把 alpha03 的旧实现混入当前 production path。

- UI-0 记录 alpha03 旧调用与 alpha05 typed 调用的迁移差异、实际 source/effect 数量、截图和 fallback；旧调用仅作为历史基线。
- UI-1 使用 alpha05 的 `HazeState`、`hazeSource`、`HazeInput.Sources`、`hazeGlass`、`hazeBlur`、`GlassStyle`、`GlassOptics.Fixed` 和 `HazeBlurStyle`，由 `SurfaceRenderer` 统一封装；页面不直接依赖 Haze API。
- `LIQUID_GLASS` 是当前兼容设置中的视觉 profile；在 alpha05 adapter 中由真实 `hazeGlass`、`GlassStyle` 和 `GlassOptics.Fixed` 实现。没有有效 source 时实际 renderer 必须是 Material 3，不能把 blur/tint 误报为 Liquid。`CLASSIC` 使用 Material 3 surface/render profile，两者共用内容树。
- Haze 只负责模糊、玻璃视觉 profile 和渲染层，不参与连接、设备能力、页面导航或持久化状态。
- `HazeState` 集中由 `GlassHost` / `AppScaffold` 管理，页面和业务 ViewModel 不直接创建或修改它。
- 普通模式、液态玻璃模式和可读性降级模式共享同一内容组件、`UiState` 和能力隐藏规则，只替换 surface/render profile。
- alpha05、官方 Glass surface 和构建工具链升级已合入当前开发包；当前回退按背景、平台和硬件能力切换到 Material 3、明确 blur、TintOnly 或 Opaque，不影响蓝牙和 typed UI 状态。GitHub Actions、运行诊断和截图仍是完成门。

官方核对入口：
- 版本：https://github.com/chrisbanes/haze/releases/tag/2.0.0-alpha05
- 迁移：https://chrisbanes.github.io/haze/latest/migrating-2.0/
- Blur：https://chrisbanes.github.io/haze/latest/blur/usage/
- Glass：https://chrisbanes.github.io/haze/latest/effects/glass/
- 性能：https://chrisbanes.github.io/haze/latest/performance/

相关实现文件：

- `app/src/main/java/com/freebuds/controller/ui/glass/AdaptiveGlass.kt`
- `app/src/main/java/com/freebuds/controller/ui/glass/LiquidGlassConfig.kt`
- `app/src/main/java/com/freebuds/controller/ui/foundation/surface/SurfaceRenderer.kt`
- `app/build.gradle.kts`

#### 3.5.1 完全 UI 重构与双玻璃模式总契约

本节覆盖并取代本章此前把 alpha03 `hazeEffect + blurEffect` 描述为 `HazeGlass` 的旧表述。当前第一轮 UI foundation 迁移不等于完全重构完成；`docs/UI_ERROR_REPORT.md` 中的 UI-ERR-001/002/003 仍是阻塞项。

**请求模式、背景资格和最终 renderer 必须分离：**

```text
RequestedDisplayMode: MATERIAL3 | FROSTED | LIQUID
BackgroundState: Available(sourceId, inputKind, isNonSolid) | Loading | Absent | Invalid(reason)
ResolvedSurfaceMode: MATERIAL3 | FROSTED | LIQUID
```

解析规则固定为：

```text
BackgroundState != Available(isNonSolid = true) -> MATERIAL3
RequestedDisplayMode == MATERIAL3 -> MATERIAL3
RequestedDisplayMode == FROSTED + valid background + blur capability -> FROSTED
RequestedDisplayMode == LIQUID + valid background + real haze-glass capability -> LIQUID
otherwise -> MATERIAL3
```

用户没有导入有效非纯色背景时，FROSTED 和 LIQUID 都必须关闭，只使用标准 Material 3；不得使用默认渐变、透明 surface、noise 或低 alpha tint 冒充玻璃输入。无背景硬断言为 `resolvedMode=MATERIAL3`、`sourceAttached=false`、`effectSurfaceCount=0`。Liquid 不得静默降级为 Frosted；真实 haze-glass 不可用时必须明确记录 `DisabledNoBackground`、`DisabledUnsupported` 或 `Failed` 并使用 Material 3。Frosted 的 blur 不可用时也回到 Material 3。

状态至少暴露 `requestedMode`、`backgroundState`、`resolvedMode`、`glassAvailability`、`sourceAttached`、`rendererKind`、`effectSurfaceCount` 和 `fallbackReason`。Material 3、Frosted、Liquid 共用同一内容树、状态树、布局、焦点顺序、触控目标和无障碍语义；业务状态不得由 blur、透明度、折射、噪声或高光表达。

**生产渲染边界：**

- `GlassHost` 是每个窗口唯一的背景宿主和 source 所有者；只有有效非纯色背景才创建 source。
- `MATERIAL3` 只使用 Material 3，不调用 Haze。
- `FROSTED` 只使用真实 blur modifier 和有效 source，代码、日志、诊断和测试不得称其为 HazeGlass 或 Liquid。
- `LIQUID` 必须使用真实 Haze `Modifier.hazeGlass`、`GlassStyle` 和受控 `GlassOptics.Adaptive`/`Fixed`；`hazeEffect + blurEffect`、noise、tint、边框或自定义绘制不能单独作为 Liquid 证据。
- Haze alpha05、`haze-glass`、compileSdk/AGP/Lifecycle 兼容性单独设构建门；依赖或工具链不满足时 Liquid 为 `BLOCKED/UNAVAILABLE`，不得以 alpha03 blur 通过。
- 每个窗口一个 source；Hero 和少量 Feature 才允许 effect，CompactRow/长列表不逐行创建 effect，单屏主要 effect 默认不超过 6 个。
- 页面禁止直接 import Haze、创建 `HazeState`/source/effect、读取 `SharedPreferences` 或调用 raw `setProperty(group, prop, value)`。

**背景生命周期和旧入口：**

- 壁纸 URI、加载状态、作用域、非纯色资格和权限由 `AppUiState`/`BackgroundRepository` 提供；页面不自行加载背景。
- 加载中、读取失败、纯色、尺寸无效或 source 未绑定都解析为 Material 3；route 切换、旋转和进程重建不能累积 source/effect。
- `LiquidGlassConfig` 仅作为持久化迁移模型，不能直接决定实际 renderer。
- 新 `AppScaffold -> BackgroundLayer -> SurfaceRenderer -> Route` 是唯一生产入口。旧 `AppNavHost`、旧 Screen、`AdaptiveGlass`/`LiquidGlassPanel` facade、旧页面 Haze 参数透传和旧 XML 不得注册到 production NavHost 或被 production composable 调用；暂留代码只能进入 legacy/test source set，并由 CI 阻断 production 引用。

**阻塞关系：** UI-ERR-001 阻塞 LIQUID-1；UI-ERR-002 阻塞 FROSTED-1、LIQUID-1 和 UI-RELEASE；UI-ERR-003 阻塞 NAV-1。CI、import 成功或第一轮结构迁移都不能标记完全重构或 Liquid 完成。



本轮已重新核对并锁定 Haze 2.0 `alpha05` typed API。依赖、compileSdk、AGP、Kotlin、Gradle 和 Compose 基线作为同一可回退提交升级；真正的 Liquid renderer 只允许在唯一 surface adapter 中调用，GitHub Actions 和实机截图/诊断通过前保持 UI 阶段受阻。

**依赖锁定：**

| 依赖 | 当前版本 | 规划处理 |
|---|---|---|
| `dev.chrisbanes.haze:haze` | `2.0.0-alpha05` | 当前 typed foundation 基线；仅由 foundation adapter 直接调用 |
| `dev.chrisbanes.haze:haze-blur` | `2.0.0-alpha05` | 真实 Frosted/fallback renderer；使用 `hazeBlur`/`HazeBlurStyle` |
| `dev.chrisbanes.haze:haze-blur-materials` | 已删除 | 当前没有 `HazeMaterials` 调用，不保留只声明未使用的依赖 |
| `dev.chrisbanes.haze:haze-glass` | `2.0.0-alpha05` | 真实 Liquid renderer；只由 `SurfaceRenderer.kt` 调用，运行证据不足时回到 Material 3 |

**当前调用基线：**

- `AppScaffold` 创建唯一 `GlassHost`，由根背景容器使用 `.hazeSource(sharedHazeState)`。
- `SurfaceRenderer.kt` 使用 `.hazeGlass(input, GlassStyle, GlassOptics.Fixed)` 和独立的 `.hazeBlur(input, HazeBlurStyle)`，两者均保留可见 Material 3 fallback。
- `AdaptiveGlass.kt` 只保留公共迁移入口，页面不再持有 `HazeState?`；Home、Scan、Device、Gesture、ListeningStats、Settings 只传 `UiDisplayMode` 和项目自己的 `SurfaceSpec`。
- 依赖存在和 import 不算运行证据；必须在 `LIQUID_GLASS` 下记录 requested/actual renderer、background/source、effect 数量和 fallback reason，确认用户确实看到真实 glass 或 Material 3 降级。

**目标依赖边界：**

```text
AppScaffold
  └─ GlassHost
      ├─ BackgroundLayer
      │   ├─ Wallpaper / Gradient / SolidColor
      │   └─ hazeSource(sharedHazeState)
      └─ NavHost + route content
          └─ SurfaceRenderer
              ├─ HazeGlassRenderer
              ├─ HazeBlurRenderer
              ├─ TintOnlyRenderer
              └─ OpaqueRenderer
```

1. `GlassHost` 是每个 Activity/window 的唯一 `HazeState` 所有者；页面、ViewModel、UseCase 和设备组件均不创建或修改 `HazeState`。
2. 只有 `ui/foundation/surface` 包直接导入 Haze API；业务页面只接收 `SurfaceRole`、`SurfaceTone`、`SurfaceSpec` 等项目类型。未来 alpha05 的 experimental API 也只能放在该 adapter，不能扩散到页面。
3. `AppScaffold` 负责背景图、渐变、系统栏、Haze source、统一 content padding 和 surface profile；页面只声明内容和语义层级。
4. `SurfaceRenderer` 是唯一渲染适配面：经典模式走 Material 3 surface；有效 source 且 API 能力满足时，Liquid profile 走真实 `hazeGlass`；blur 只作为明确的 Frosted/平台 fallback，source 不可用或初始化失败时直接走 Material 3。所有路径共享相同的内容树和状态树。
5. alpha05 的 `HazeInput.Sources` 通过共享 `HazeState`/`hazeSource` 连接；`GlassStyle`/`GlassOptics.Fixed` 与 `HazeBlurStyle` 分别配置两种 renderer。不在页面复制 source，也不把透明 surface 当作失败结果。
6. `HazeSourceRetention` 只决定 source 不可用时保留最近帧还是清空：默认是 `HazeSourceRetention.KeepLastFrame`，隐私场景使用 `ClearWhenUnavailable`。它不等于页面/后台生命周期控制；进入后台、页面离开或隐私场景时是否停止捕获，必须由 `GlassHost` 的 lifecycle policy 另行控制。诊断不得记录壁纸 URI、设备地址或协议 payload。
7. `GlassSurface` 等旧函数只作为迁移期间的页面适配入口，不新增第二套渲染实现；完成全量 UI 迁移后删除旧的 Haze 参数透传和重复 surface 实现。新页面只能通过 `SurfaceRenderer` 使用 Haze 2.0 适配器。

**渲染层级（液态玻璃的唯一推荐形态）：**

```text
Window / Activity
└─ GlassHost                         # 每个窗口唯一的 HazeState 所有者
   ├─ BackgroundLayer                # 壁纸 / 渐变 / 纯色，先绘制真实输入
   │  ├─ WallpaperLayer
   │  ├─ AmbientGradientLayer        # 非 Haze 的低成本氛围渐变
   │  └─ hazeSource(sharedHazeState)  # 只挂一次 source
   └─ AppScaffold
      ├─ SystemBars / AppBar          # 低强度 blur 或 tint
      └─ NavHost
         └─ RouteContent
            ├─ HeroSurface             # 少量高折射液态玻璃
            ├─ FeatureSurface[]        # 电量、ANC、音频等主要卡片
            ├─ StandardSurface[]      # 普通信息卡，限制数量
            ├─ CompactSurface[]       # 设置行/列表项，默认不独立采样
            └─ Dialog / Sheet          # 可读性优先，默认 opaque
               └─ ContentLayer         # 文本、图标、控件、语义和交互反馈
```

本项目当前使用 alpha05 typed API：`hazeBlur` 只代表 Frosted/平台 fallback，`hazeGlass` 才代表 Liquid。无有效壁纸 source、加载失败、API 不满足或运行初始化失败时，实际 renderer 必须记录为 Material 3，不得用 tint、noise、边框或旧 blur 假装 Liquid。

**卡片折射分级和性能预算：**

| SurfaceRole | 液态玻璃策略 | 推荐 Haze effect / Blur 强度 | 单屏预算 | 典型位置与说明 |
|---|---|---|---:|---|
| `AppBackground` | 只作真实输入，不挂 effect | 无 effect；壁纸/渐变由 source 提供 | 1 source | 全窗口背景；不把纯色背景误报为玻璃输入 |
| `AppBar` | 顶部栏默认 tint/Material 3，不逐层采样 | 无独立 effect | 0 | 顶部栏；不与 Hero 叠加独立高强度 effect |
| `Hero` | 完整视觉层次：背景采样、blur、tint、边缘边框和可读性遮罩 | 共享 Haze source；blur 22–30dp；`depth` 0.32–0.45 只作为项目 token；失败时回退 tint | 1 | 首页设备摘要、核心连接横幅；只允许一个主 Hero |
| `FeatureCard` | 标准液态玻璃视觉：保留背景层次和圆角边缘，减少动态 effect | 共享 Haze source；blur 14–18dp；`depth` 0.22–0.34 只作为项目 token；低特效时 tint | 2–3 | Device 的 ANC、电量、音频等主要卡片；同屏只选最重要的 2–3 张 |
| `StandardCard` | 长列表默认 tint/Material 3；只有显式的小范围 surface 才允许 `hazeBlur` | 默认无独立 effect；小范围 blur 10–14dp | 0（列表） | Stats、设备信息和次级状态；滚动列表中优先合并为 section surface |
| `CompactRow` | 不创建独立 Haze effect；使用透明/tint surface 继承父卡片 | 无独立 blur/glass；静态 alpha 和 divider | 0 | Settings 行、长列表项、开关和选择器；避免 N 个行组件各采样一次 |
| `Dialog/Sheet` | 不依赖背景采样和折射；保证文字和控件可读 | opaque 或高可读 tint；必要时单一低强度 blur | 1 | 确认、错误、权限和更新安装提示；不让动态壁纸影响关键操作 |

默认单个 route 同时最多 6 个主要 effect surface；长列表 CompactRow 永远不因数量增长而增加 effect。渲染选择不再使用 `hazeEffect(高强度) → hazeEffect(低强度) → tint → opaque` 作为所谓 Liquid 链路，而是严格按 `MATERIAL3`、真实 `FROSTED`、真实 `LIQUID` 三态解析；背景无效时直接 `MATERIAL3`。

**液态玻璃的真实输入和折射规则：**

1. 共享 `HazeState`/`hazeSource` 是 Hero、FeatureCard 和 StandardCard 的唯一背景输入；禁止把 effect surface 自身作为唯一 source，也禁止每个卡片创建 `rememberHazeState()`。
2. Hero 和高优先级 FeatureCard 可以使用更大的 blur、tint 和边框强度；`depth`、refraction 等只作为项目 token，不伪装成 alpha05 官方 optics 参数。进入低特效 profile 后，FeatureCard/StandardCard 改用低强度 blur 或 tint。
3. 边缘高光、品牌装饰和轻量 chromatic effect 只能作为内容上方的小面积 `drawWithCache` 层；不得用大面积白色填充或透明度伪造玻璃，也不得以装饰层代替 Haze 真实输入。
4. blur radius、tint 和 shape 使用稳定、可复用的配置；滚动时不动态修改 effect 参数。用户调节参数只在设置提交后重建对应 surface profile。
5. `HazeSourceRetention` 只决定 source 不可用时保留最近帧还是清空：默认是 `HazeSourceRetention.KeepLastFrame`，隐私场景使用 `ClearWhenUnavailable`。它不等于页面/后台生命周期控制；进入后台、页面离开或隐私场景时是否停止捕获，必须由 `GlassHost` 的 lifecycle policy 另行控制。
6. Haze 2.0 alpha05 的平台行为按目标设备实测决定；本项目在 API 26–30 默认使用 Material 3，API 31/32 才允许明确的 blur fallback，API 33+ 作为主要真实 glass 验证范围。这是项目策略，不是官方兼容承诺；fallback 必须保留同样的颜色语义、shape、内容和交互。

**运行时性能策略：**

- 当前 alpha05 使用 `HazePerformanceMode.Adaptive` 和 typed style API；性能策略仍由 effect 数量、blur radius、source 是否可用和 `SurfaceRenderMode` 共同控制。
- 性能优先级固定为：减少 effect 面积 → 减少同屏 effect 数量 → 减少动态输入/高级 optics → 降低 blur 质量；不要先用更高透明度掩盖卡顿或采样为空。
- LazyColumn/LazyRow 的 item 不逐个挂 Haze effect；使用父级 FeatureSurface 包住一组内容，或对不可见/低优先级 item 只绘制 tint。
- 连接状态、蓝牙轮询和玻璃渲染完全分离；连接状态变化只更新内容，不重建 `HazeState`、BackgroundLayer 或所有 surface style。
- Debug-only 诊断记录 `surfaceRole`、`renderer`、`effectSurfaceCount`、输入是否为空、`performanceMode`、首屏时间、滚动 P95 帧时间、jank、内存和 fallback reason；不记录壁纸 URI、设备地址或协议 payload。
- UI-1 的目标是无崩溃/ANR，Haze effect profile 相对 CLASSIC 的滚动 P95 帧时间回归不超过 20%；超过时按上述降级顺序收敛，不能以“效果更强”为理由保留超预算实现。


**参数与主题令牌：**

- `LiquidGlassConfig` 继续作为持久化兼容模型；`tintAlpha`、`readabilityStrength`、`refractionStrength`、`depth`、`cornerRadiusDp` 和 `surfaceProfile` 由 `GlassTokenResolver` 转成语义 `SurfaceSpec`。
- 页面不再传递单独的 `tint`、`depth`、`refractionStrength` 常量；页面只声明 `Hero`、`Card`、`AppBar` 等角色。
- 现有参数范围和默认值先保持，UI-0 记录现状；UI-1 将默认方案收敛为 `Subtle`、`Standard`、`Emphasis`、`OpaqueFallback` 四个 profile。
- Settings 的用户选项只改变显示偏好；渲染器根据高对比度、大字体、reduced motion、设备能力和当前窗口状态自动选择可读性更高的 profile。
- 玻璃边框、内侧 readability veil、颜色 tint 和阴影均使用 Material 语义色与 `comp.*` 令牌，页面不写近似色值。

**背景、壁纸和生命周期：**

- 壁纸 URI、作用域和透明度由 `AppUiState` 提供，`BackgroundLayer` 统一加载；Home、Settings 等页面不各自加载背景图。
- route 切换只改变 content，不重新创建 Haze source；Activity 重建时由 `GlassHost` 根据窗口生命周期重新建立 `HazeState` 和 source，并按 privacy policy 决定是否清空 retained frame。
- 壁纸加载失败、没有壁纸、权限变化和窗口尺寸变化都回到 gradient/solid 背景，玻璃内容继续可读。
- Haze 渲染状态与蓝牙连接状态分离；设备连接/断开不会触发玻璃参数重置，也不会让页面为连接状态重新创建渲染器。

**性能和降级策略：**

```kotlin
enum class SurfaceRenderMode {
    HAZE_GLASS,
    HAZE_BLUR,
    TINT_ONLY,
    OPAQUE,
}

data class GlassRuntimePolicy(
    val mode: SurfaceRenderMode,
    val blurEnabled: Boolean,
    val reason: String?,
)
```

- Android 26–30 默认走 `TintRenderer`/`OpaqueRenderer`；Android 31/32 只在实测可接受时启用有限 blur；Android 33+ 作为主要 Haze Glass 验证范围。这是本项目的降级策略，不是 Haze 官方完整兼容表；官方平台行为需以 `https://chrisbanes.github.io/haze/latest/blur/platforms/` 和目标设备实测为准。
- Haze 初始化或 `hazeEffect` 应用异常时，当前 surface 先回退到同一输入上的低强度 blur；Blur 也不可用、输入为空或性能策略明确关闭模糊时，再回退到可见 tint/opaque。页面状态和交互继续存在。
- 高对比度、较大文字、低性能设备和用户关闭玻璃时，降低 refraction/depth，必要时关闭 blur；可读性优先于视觉效果。
- 不在滚动回调中反复修改 blur 参数；背景、profile 和 shape 使用稳定对象，减少重组和 effect 重建。
- UI-1 记录同一设备下 `CLASSIC`、`TINT_ONLY` 和 Haze effect profile 的首屏时间、滚动帧时间、内存和崩溃/ANR；后续玻璃改动以该基线比较，目标为无崩溃/ANR，且 P95 帧时间相对经典模式回归控制在 20% 以内。
- 诊断日志仅记录 `renderer`、`surfaceRole`、`blurEnabled`、`fallbackReason` 和窗口 profile，不写入用户壁纸 URI 或设备协议数据。

**可读性和无障碍契约：**

- 文本、开关、图标和状态颜色在 `HAZE_GLASS`、`HAZE_BLUR`、`TINT_ONLY`、`OPAQUE` 四种模式使用同一语义色和内容描述。
- `readabilityStrength` 只增强内容底层遮罩，不改变业务状态，也不把低对比度文字涂成装饰色。
- TalkBack/键盘焦点顺序由内容树决定，玻璃 surface 不拦截焦点；装饰性壁纸和边缘光学绘制均标记为无障碍装饰。
- reduced motion 关闭形变、折射和边缘动画；状态变化仍提供文本/语义反馈。
- UI-5 的高对比度和大字体验收中，允许自动切到 `OPAQUE`，但功能入口、资源语义和布局顺序保持一致。

**Haze 迁移阶段与退出条件：**

| 阶段 | Haze 工作 | 直接验收 |
|---|---|---|
| UI-0 | 盘点 alpha03 旧调用、alpha05 typed API、HazeState、hazeSource、玻璃参数和截图 | 形成迁移清单、surface 数量、effect 数量和资源基线；确认旧调用已退出 production graph，alpha05 依赖与真实调用一致 |
| UI-1 | 建立共享 `GlassHost`、`BackgroundLayer`、`SurfaceRenderer` 和 typed glass/blur、Tint/Opaque renderer；完成 Hero/Feature/Standard/Compact/Dialog 的 effect 预算与 fallback | `hazeGlass`、`hazeBlur`、Tint/Opaque 最终降级均可运行；公共 preview 覆盖各 renderer，并记录 source/effect 数量和性能 |
| UI-2 | typed state/event 与渲染器解耦，Device 组件去除 Haze 参数 | feature 目录无 Haze import；动作状态与渲染模式独立，CompactRow 不增加 effect |
| UI-3 | `AppScaffold` 接管背景和共享 HazeState，迁移 Home/Scan/Permission | 每个入口只创建一个 host/source，路由切换无重复 source；单屏 effect 不超过预算 |
| UI-4 | 按 SurfaceRole 迁移 Device/Gesture/Stats/Terminal | Hero/Feature/Standard 的折射级别符合表格，玻璃卡数量、滚动性能、能力状态和美术资源映射满足基线 |
| UI-5 | Settings 迁移、fallback/无障碍收口、删除旧 Haze facade 和页面依赖 | 只剩 foundation 层 Haze 2.0 alpha05 import；可通过开关回退 classic/opaque；长列表、Dialog 和高对比度场景不依赖玻璃效果 |

Haze 相关变更的回退点固定为项目自己的 `SurfaceRenderMode.Material3`、`TintOnly` 或 `Opaque`，以及 `UiDisplayMode.CLASSIC`；不回退 `EarbudState`、连接管理或设备功能代码。

#### 3.5.1.2 官方仓库更新核对与本项目实际调用审计

核对日期：2026-08-15。官方来源：

- 仓库：[https://github.com/chrisbanes/haze](https://github.com/chrisbanes/haze)
- 最新预发布：[`2.0.0-alpha05`](https://github.com/chrisbanes/haze/releases/tag/2.0.0-alpha05)，发布于 2026-08-12。
- 上一预发布：[`2.0.0-alpha04`](https://github.com/chrisbanes/haze/releases/tag/2.0.0-alpha04)，发布于 2026-08-08。
- 迁移指南：[https://chrisbanes.github.io/haze/latest/migrating-2.0/](https://chrisbanes.github.io/haze/latest/migrating-2.0/)
- Blur 使用：[https://chrisbanes.github.io/haze/latest/blur/usage/](https://chrisbanes.github.io/haze/latest/blur/usage/)
- 平台行为：[https://chrisbanes.github.io/haze/latest/blur/platforms/](https://chrisbanes.github.io/haze/latest/blur/platforms/)
- Glass：[https://chrisbanes.github.io/haze/latest/effects/glass/](https://chrisbanes.github.io/haze/latest/effects/glass/)
- 性能指南：[https://chrisbanes.github.io/haze/latest/performance/](https://chrisbanes.github.io/haze/latest/performance/)
- 许可证：Apache-2.0。`main` 在 alpha05 之后仍有未发布提交，因此本项目锁定发布版本，不跟随 `main` 快照。

**官方更新对本项目的影响：**

1. 历史 CI `31819769359` 曾在 `checkDebugAarMetadata` 阶段证明 alpha05 不能直接放入 compileSdk 36/AGP 8.9.1 基线；该失败记录保留，之后通过同一可回退提交把工具链升级到 compileSdk 37/AGP 9.1。
2. alpha05 的 `haze-glass` 是独立 experimental artifact；`v4.4.0 / 99` 已声明并在唯一 `SurfaceRenderer` adapter 中实际调用。`AdaptiveGlass.kt` 的项目装饰参数不作为官方 Glass 证据，仍必须通过截图、运行诊断和性能验证。
3. alpha03 的 `hazeEffect`/`blurEffect` 只保留在历史说明和迁移记录中；当前 production graph 使用 alpha05 typed `HazeInput`、`HazeBlurStyle`、`HazePerformanceMode`、`hazeGlass` 和 `hazeBlur`，不能把旧调用当作当前实现。

**当前依赖和实际调用证据：**

| 项目 | 声明位置 | 直接源码调用 | 默认运行路径 | 结论 |
|---|---|---|---|---|
| `haze` | `app/build.gradle.kts` | `HazeState`、`rememberHazeState`、`hazeSource`、`HazeInput.Sources` | `AppScaffold` 的 `GlassHost` 创建唯一 source；effect 由 surface profile 决定 | 有实际调用 |
| `haze-blur` | `app/build.gradle.kts` | `HazeColorEffect`、`hazeBlur`、`HazeBlurStyle` | `SurfaceRenderer` 的明确 Frosted/平台 fallback 路径执行 | 有实际调用 |
| `haze-blur-materials` | 已删除 | 没有 `HazeMaterials` 或 `blur.materials` import | 没有直接调用 | 不保留只声明未使用依赖 |
| `haze-glass` | `app/build.gradle.kts` | `Modifier.hazeGlass`、`GlassStyle`、`GlassOptics.Fixed` | `SurfaceRenderer` 的少量 Hero/Feature Liquid 路径 | 已接入；仍需 GitHub CI、真实输入截图和运行诊断 |

历史代码核对结果：

- `v2.9.0` 已在 `AdaptiveGlass.kt` 使用 `HazeState`、`hazeSource`、`hazeEffect`；当时依赖为 Haze 1.6.7。
- `v2.12.0` 将依赖切换到 Haze 2.0 alpha03，并把 `HazeTint` 迁移为 `HazeColorEffect` + `blurEffect`；这不是只有 Gradle 声明，代码中已有实际调用。
- `v4.2.6` 仍保留上述 Haze 2.0 alpha03 调用链；本轮没有复制旧页面的参数透传，而是把 alpha05 typed 调用收拢到 `SurfaceRenderer`。
- “依赖写了但没有效果”有两个明确边界：默认 `CLASSIC` 本来就只走 Material 3；而 Liquid 在壁纸未成功加载时现在也会明确回到 Material 3，不再创建空 source/effect。只有有效壁纸 source、Liquid 请求和支持的 API 同时成立时，才挂载真实 `hazeGlass`。

**依赖处理决定：**

1. UI-0 记录 alpha05 typed API 的静态调用、运行诊断和视觉基线，并确认依赖和实际调用一致。
2. UI-1 先完成 alpha05 的共享 Haze adapter：`GlassHost` 提供唯一 source，`SurfaceRenderer` 负责 glass/blur/Material 3 选择；通过 CI、运行截图和性能矩阵后，再继续页面迁移。
3. `haze-blur-materials` 当前没有实际调用，已删除；只有出现明确的 `HazeMaterials.*` 使用场景时才另行评估依赖。
4. `haze-glass` 已进入当前 `v4.4.0 / 99` 开发包；`UI_ERROR_FIX_ALPHA05` 必须同时通过 compileSdk/AGP/Lifecycle、真实输入截图和运行诊断，不能用 import 或依赖存在替代效果证据。
5. 当前 UI-1 构建标签使用 `UI_ERROR_FIX_ALPHA05`；版本号不是唯一识别方式。

**实际调用证明规则：**

- `CLASSIC` 测试记录 `renderer=Material3`、`sourceAttached=0` 或 `effectSurfaces=0` 的预期结果。
- `LIQUID_GLASS` 测试必须先通过设置选择玻璃模式，再记录 requested renderer、actual `HazeGlass`、`sourceAttached=1`、实际 effect surface 数量和 fallback reason；API/输入不满足时只能记录 Material 3。低版本 blur 只记录 `HazeBlur`，不能把它当作 Liquid。只看 Gradle 依赖或 import 不算运行证据。
- Debug-only 的 `GlassRuntimeDiagnostics` 记录 requested/actual renderer、background/source、effect 数量、API、硬件加速和 fallback reason，不记录壁纸 URI、设备地址或协议 payload；Release 不暴露诊断入口。
- `UI_ERROR_FIX_ALPHA05` 验收必须包含 alpha05 `hazeGlass`/`GlassStyle` 的实际 Hero/Feature preset、真实输入截图、无背景 Material 3 fallback、列表 effect 数量和导航会话证据；不能用 import 或依赖存在替代运行证据。

#### 3.5.1.3 玻璃渲染正确性硬门槛

“调用成功”与“用户看到了正确效果”分开验收。后续重构必须满足以下约束：

1. **唯一宿主和真实输入**：`GlassHost` 是 Activity/window 内唯一的 `HazeState` 所有者；`BackgroundLayer` 先绘制壁纸、渐变或非纯色背景，再挂载唯一 source，`NavHost` 只作为其后的内容层。禁止让 effect surface 自己成为唯一输入，也禁止在每个页面重复创建 source。
2. **分层渲染和 effect 预算**：窗口只保留一个 source；Hero 最多 1 个、FeatureCard 最多 3 个、StandardCard 最多 2 个，CompactRow 不创建独立 effect；单屏主要 effect 默认不超过 6 个。超过预算时优先将 StandardCard/FeatureCard/AppBar 降为 tint 或低强度 blur。
3. **统一适配器**：业务页面不得直接 import 该库；`SurfaceRenderer` 统一负责共享 `HazeState`、typed `hazeGlass`/`hazeBlur`、tint、opaque fallback 和 Android 能力判断。alpha03 的嵌套 DSL 只作为历史迁移记录，不能回到 production adapter。
4. **装饰只作增强层**：Hero 和少量 FeatureCard 才允许轻量边缘高光、内侧遮罩和受控 chromatic 装饰；不得通过大面积白色填充或透明度伪造玻璃；StandardCard、CompactRow 和 Dialog 不依赖高级折射。
5. **不可见降级**：模糊不支持、硬件加速不可用、source 为空、输入是纯色或 effect 初始化失败时，必须切换到可见的 `TintRenderer` 或 `OpaqueRenderer`。不得使用“透明 surface + 空 tint”作为失败结果。
6. **可观测性**：Debug-only `GlassRuntimeDiagnostics` 记录 `mode`、`renderer`、`sourceAttached`、`sourceAreaCount`、`effectSurfaceCount`、`apiLevel`、`hardwareAccelerated`、`performanceMode`、首屏/滚动 P95 帧时间、jank、内存和 `fallbackReason`。只记录渲染元数据，不记录壁纸 URI、设备地址或协议 payload。
7. **视觉基线**：每个公共 surface 必须在非纯色渐变/壁纸输入下同时提供 `CLASSIC`、真实 blur/glass、fallback 三张对照预览；截图验收不能只证明组件存在或 import 成功。
8. **路由覆盖**：Home、Device、Settings 至少各验证一个 surface；切换 route、旋转/重建和切换 `CLASSIC`/`LIQUID_GLASS` 后，source 与 effect 数量不得累积，显示结果不得退化为空白或完全不透明错误色块。

本门槛的定向测试标签为 `UI_GLASS_RENDERING_TARGETED`，只覆盖本轮修改涉及的 renderer、surface 和 route，不复跑蓝牙 36 轮矩阵。未满足运行时和截图证据前，UI-1 保持 `[~] 进行中`。

### 3.6 UI 迁移阶段

所有 UI 阶段都按“实现一层 → JVM/静态检查 → GitHub Actions 构建 → 定向界面/实机验证 → 文档回写 → 可回退提交”执行。阶段标题只有在该阶段的代码、测试、证据和回退记录齐备后才改为 `[x] 已完成`。当前已形成 UI-0 基线和 UI-1 至 UI-5 的第一轮结构迁移，以下阶段保持 `[~]`，不把代码已落地误记为验收完成。

#### UI-0：行为、状态、资源基线 `[~] 进行中`

交付物：

- 建立 Home、Scan、Device、Gesture、ListeningStats、Settings、PermissionGuide、Terminal 的路由和截图基线。
- 现有 `docs/screenshot_home.jpg`、`docs/screenshot_device.jpg` 作为初始视觉参照；补齐其余 route、classic/glass、深浅色和状态截图，不把现有截图直接当作新 UI 验收通过。
- 增加 `UI_GLASS_RENDERING_TARGETED` 基线：非纯色输入下的真实效果、无效果能力下的可见 fallback、纯色/无壁纸输入下的明确 tint 或 opaque 结果；记录 source/effect 数量和 fallback reason。
- 记录断开、系统链路已连接、TransportReady、CoreInitializing、Ready、Degraded、Failed、无能力、动作 Pending/读回/失败等状态的显示矩阵。
- 列出全部 `DeviceProps` 字段、`EarbudState`/`EarbudCapability` 映射、页面读取位置、`setProperty` 入口、设置 key 和导航触发来源。
- 记录已删除的旧 `UpdateChecker` 草稿、Settings 的“更新地址”入口、`BuildConfig.VERSION_NAME/VERSION_CODE`、Release 资产和安装权限，并把有效能力迁移为 typed update state。
- 完成美术资源保留清单、`UiAssetCatalog` 映射表和旧 XML/主题资源的迁移边界。
- 为 `EarbudStateMapper`、能力展示、关键选项映射、更新 manifest 和导航去重补充基线测试；本阶段不提升应用版本。

退出条件：基线清单可逐项回溯，任何现有页面的功能和资源都有“保留、迁移或废弃”的明确结论。

#### UI-1：设计令牌、主题和渲染基础 `[~] 进行中`

交付物：

- 新增 `ui/foundation/tokens/UiTokens.kt`，按 `ref.*`、`sys.*`、`comp.*` 三层表达颜色、排版、间距、圆角、边框、层级和动效。
- 新增统一 `AppTheme`、`AppScaffold`、`AppTopBar`、`SectionHeader`、`SettingRow`、`PrimaryActionButton`、`SecondaryActionButton`、`TertiaryActionButton`、`DestructiveActionButton`、`IconActionButton`、`ToggleButton`、`BooleanOptionRow`、`SingleChoiceOption`、`DependentChoiceOption`、`SegmentedOption`、`SliderOption`、`ActionPicker`、`AsyncActionIndicator`、`ConnectionBanner`、`EmptyState`、`ErrorState`。
- 为按钮和选项建立统一 visual/state contract：`UiActionState`、`UiOption<T>`、`OptionUiState<T>`、`UiTextMapper`、`OptionPresenter`；所有控件支持 `Idle/Disabled/Pending/Success/Failure` 或选项 `selected/pending/unknown/unavailable` 语义。
- 将 CLASSIC/LIQUID_GLASS 收敛为同一内容树的 SurfaceProfile；控件 surface 按高强度 Haze effect → 低强度 blur → tint → opaque 选择，Haze 2.0 只位于 surface adapter，长列表行不逐项创建 effect。
- 统一 8dp 间距节奏、至少 44dp 触控目标、动态字体、深浅色、对比度、TalkBack、键盘焦点和 reduced motion 的工程入口。

- 建立 `UiAssetCatalog`，将 ANC 三种图标、耳机盒、Tile 图标映射为 `AncVisual`、`DeviceVisual` 等语义资源。

退出条件：公共组件、按钮、选项和 token 有预览/单元基线，alpha03 Haze effect surface 可运行；旧页面的生产入口可暂时保留，但不再复制旧的页面级 Haze 参数透传。alpha05 surface 属于独立升级门。
#### UI-2：typed State、Event 与 Navigation `[~] 进行中`

交付物：

- 建立 `AppUiState`、`DeviceUiState`、`SettingsUiState`、`ControlUiState`、`UiActionState` 和一次性 `NavigationEvent`。
- `DeviceViewModel` 由字符串透传层改为 typed event 入口；先迁移 ANC、低延迟、音质偏好三个高频动作。
- 页面消费 `EarbudState` 和能力集合；`null` 表示未知/未提供，能力集合决定 section 是否出现。
- 按钮、开关、选择器和滑块全部使用 UI-1 的统一控件契约；页面只发送 typed event，不直接调用 Material 控件状态或 Haze API。
- 每个写操作区分 `Pending → Ack/Readback → Success`、`Failure` 和重试；选项区分 `selectedValue`、`pendingValue`、未知和不可用原因；页面不从日志文本猜测状态。
- 连接发起、自动连接、设备点击、扫描完成、Service 和 Tile 统一使用 Manager command 与触发来源，导航只接收稳定状态和 `NavigationEvent`。

退出条件：迁移的三个高频动作具备 pending、ACK/读回、失败提示和重试测试；迁移页面中不再出现厂商 `group/prop` 或协议字节。

#### UI-3：全局外壳与主路由 `[~] 进行中`

交付物：

- `AppNavHost` 只维护 route、back stack 和事件分发；主题、语言、壁纸、玻璃配置和连接摘要统一由 `AppUiState` 提供。
- 迁移 Home、Scan、PermissionGuide 和首页设备列表，统一设备行、空状态、扫描中、扫描结束、连接中和失败状态。
- 使用 `ConnectionSummary` 分开呈现 SystemLink、ControlChannel、CoreReady/Degraded；详情跳转由状态流驱动。
- 统一返回、手动断开、自动重连、用户离开详情页和 ACL 断开的导航语义，保留 `attemptId`/触发来源用于诊断。

退出条件：Home/Scan/Permission 的所有入口只产生一个连接命令和一个导航事件；旧 `AppNavHost` 的设置读写和重复连接判断全部移出。

#### UI-4：设备功能页面 `[~] 进行中`

交付物：

- 将 `DeviceScreen` 拆为 `BatteryCard`、`NoiseControlSection`、`AudioSection`、`LowLatencyRow`、`DualConnectSection`、`GestureEntryCard`、`DeviceInfoSection` 等独立组件；当前已将 `BatteryCard`、后台同步、EQ 状态、双设备卡片、通用选项/开关/设备信息卡放入 `ui/DeviceFeatureComponents.kt`，route 只保留布局和事件编排。
- 迁移 Gesture、ListeningStats 和 Terminal；Terminal 先保持 Debug/诊断能力，旧 XML 仅作为迁移期间的布局输入，完成统一 route 后删除生产入口中的旧布局依赖。
- 按 `EarbudCapability` 控制 section 可见性；未知、未验证和失败能力只显示结构化降级状态，不展示伪造的默认值。
- 所有设备功能使用同一 `SettingRow`、`PrimaryActionButton`/`SecondaryActionButton`、`BooleanOptionRow`、`SingleChoiceOption`、`DependentChoiceOption`、`SegmentedOption`、`ActionPicker`、确认反馈、错误重试和连接横幅；保留现有 ANC 美术资源并统一尺寸/语义。
- Device、Gesture、ListeningStats、Terminal 的按钮/选项必须通过 `OptionPresenter` 生成文案和可用状态；不再在各页面维护 raw 值映射。

退出条件：Device/Gesture/ListeningStats/Terminal 在 classic 和 glass 下共用同一状态树；每个本轮改动的设备能力只做对应定向验证，不恢复无关的 36/100 项蓝牙矩阵。

#### UI-5：设置、持久化、无障碍与兼容层收口 `[~] 进行中`

交付物：

- 将 Settings 拆为主题、语言、壁纸、显示风格、连接偏好、调试、关于等 section；Composable 只发送事件，不直接读写 `SharedPreferences`。
- 集中迁移设置 key 到 `SettingsRepository`，按需要落到 DataStore；处理旧 key 读取、一次性迁移和进程重启后的状态恢复。
- 在“关于/应用详情”加入 `UpdateCard`：展示当前版本、最后检查时间、更新渠道、检查按钮、可用版本、发布日期、更新说明和下载/安装进度；保留 Release 页面入口作为人工兜底。
- 以 `UpdateRepository` 承担 `CheckForUpdateUseCase`/`DownloadUpdateUseCase` 的边界；页面只消费 `UpdateUiState`，更新请求不依赖蓝牙连接状态。后续若拆出独立 UseCase，只改变内部组织，不改变页面契约。
- 完成 light/dark、中文/English/繁體、横竖屏、较大文字、TalkBack/键盘焦点、对比度和 reduced motion 验收。
- 对 classic/glass 运行同一页面和状态矩阵；确认资源映射、能力隐藏、错误反馈和导航行为一致。
- `rg` 确认页面已无 raw `group/prop`、直接 `SharedPreferences` 和重复 option 映射后，才删除兼容 UI 代码；删除项单独记录，保证可回退。

退出条件：全部 UI route 通过统一外壳、状态、事件、持久化和资源目录；旧页面不再作为生产入口，alpha03 Haze 调用和兼容 facade 均已删除。

#### UI-5 附属能力：检查更新与自动更新设计 `[~] 结构已实现，等待验证`

**数据源与版本契约：**

- 首选读取 Pages 上的 `update.json`，避免每次启动直接依赖 Release API 的速率限制；GitHub Releases API 只作为临时 fallback 和“打开 Release 页面”的来源。
- `update.json` 固定包含 `schemaVersion`、`channel`、`versionName`、`versionCode`、`releaseUrl`、`apkUrl`、`sha256`、`publishedAt`、`minSdk` 和 `notesUrl`。CI 在 `v*` 标签发布时生成或校验它，`versionName`/`versionCode` 必须与 `app/build.gradle.kts` 一致。
- 稳定版默认只消费非 draft、非 prerelease 的 Release；Debug 不把开发测试包推入稳定更新渠道。下载地址只接受 HTTPS 和允许的仓库域名，禁止从 Release 正文或不明跳转地址猜测 APK。
- 版本比较以 `versionCode` 为主、`versionName` 为展示和回退；明确处理同版本、降级、预发布后缀、损坏字段和当前版本高于远端版本的情况。

**更新流程：**

```text
App start / Settings / 手动检查
        -> UpdateViewModel
        -> CheckForUpdateUseCase
        -> UpdateRepository (update.json -> Releases API fallback)
        -> UpdateUiState: UpToDate / Available / Error

Available + 用户允许自动下载
        -> DownloadUpdateUseCase
        -> HTTPS 临时文件 + 进度/取消/有限重试
        -> SHA-256 + packageName + versionCode + APK 签名校验
        -> ReadyToInstall
        -> Android Package Installer 的用户确认
```

**持久化与触发策略：**

- 设置 key 统一进入 `SettingsRepository`/DataStore：`update_channel`、`auto_check_enabled`、`auto_download_enabled`、`check_interval`、`last_check_at`、`last_notified_version_code`、`metered_network_allowed`。
- 默认开启低频自动检查，检查间隔至少 24 小时；手动检查绕过间隔但保留请求超时和退避。应用前台恢复、设置页进入和网络恢复只合并为一个检查任务。
- 自动下载默认关闭；开启后仅在新版本、网络条件和存储空间满足时下载，下载完成后显示明确的“准备安装”卡片。取消、稍后处理和忽略当前版本都可恢复。
- 更新任务与 Bluetooth Service、BootReceiver、ACL、扫描和连接 attempt 完全隔离；检查失败、限流、无网络或安装器被拒绝均不改变耳机状态。
- 安装前使用 `FileProvider`/受控缓存目录和 Android 系统 Package Installer；校验失败立即删除临时文件并保留可诊断错误，安装权限和用户确认由系统设置流程处理。

**UI 状态与退出条件：**

- Settings/About 具备当前版本、检查时间、渠道、可用版本、更新说明、下载百分比、暂停/取消、重试、稍后安装和打开 Release 页面等完整状态。
- 冷启动、前后台切换、进程重启、横竖屏、深浅色、三种语言、无网络、API 限流、manifest 损坏、下载中断、哈希错误、签名不符和系统安装确认均有可回溯状态。
- 自动下载不会阻塞首页、蓝牙连接或详情页；页面销毁后任务继续由 repository/worker 管理，回到页面时从持久化状态恢复。
- 只有远端 `versionCode` 更高、manifest 完整、APK 下载成功且哈希/包名/签名校验通过时，才把状态提升为 `ReadyToInstall`。

### 3.7 UI 阶段的定向测试与目标受阻点

UI 阶段不复用 BT 的 36/100 项完整矩阵，测试范围按实际改动选择：

| 测试标签 | 对应阶段 | 验证内容 | 默认强度 |
|---|---|---|---:|
| `UI_ERROR_FIX_ALPHA05` | UI-1/UI-3 | alpha05 typed glass/blur、无背景 Material 3 硬门、稳定 key、连接会话导航 | GitHub Actions 1 次；实机定向 1 轮 |
| `UI_GLASS_RENDERING_TARGETED` | UI-1/UI-5 | requested/actual renderer、`hazeGlass`、`hazeBlur`、Material 3 fallback、有/无壁纸、加载失败、深浅色和高对比度 | 每个关键组合 1 次 |
| `UI_CONTROL_STATE_MATRIX` | UI-1/UI-2/UI-4/UI-5 | 所有按钮类型、`Idle/Disabled/Pending/Success/Failure`、Boolean/SingleChoice/Dependent/Segmented/Slider/ActionPicker、未知/不可用/读回失败和四种 surface renderer | 每种关键组合 1 次 |
| `UI_GLASS_PERFORMANCE` | UI-1/UI-4 | Home/Device/Settings 首屏、保存/扫描/双连接列表滚动、切换 route 的帧时间、内存和 effect 数量 | 每个受影响页面 3 轮 |
| `UI_STATE_EVENT_TARGETED` | UI-2 | ANC/低延迟/音质的 typed event、Pending、ACK/读回、失败重试和能力隐藏 | 每个改动能力 5 轮 |
| `UI_NAVIGATION_SESSION` | UI-3 | 自动进入详情、返回后同会话不重开、Home/Scan 明确点击、attempt 替换和重复入口 | 每个会话场景 5 轮 |
| `UI_DEVICE_FEATURES` | UI-4 | 本轮迁移的设备组件、状态横幅、无能力/降级/断开展示 | 每个改动功能 5 轮 |
| `UI_SETTINGS_PERSISTENCE` | UI-5 | 设置迁移、进程重启、语言/主题/壁纸/显示模式和横竖屏恢复 | 每个设置路径 3 轮 |
| `UI_UPDATE_CHECK_TARGETED` | UI-0/UI-2/UI-5 | update manifest/API、versionCode 比较、稳定渠道、缓存、限流、无网络和失败状态 | 每个状态 1 次 |
| `UI_UPDATE_DOWNLOAD_VERIFY` | UI-5 | HTTPS 下载、进度/取消/恢复、SHA-256、packageName、versionCode、签名和临时文件清理 | 每个异常 1 次 |
| `UI_UPDATE_INSTALL_FLOW` | UI-5 | Package Installer、安装权限、用户确认、稍后安装、忽略版本和进程重启恢复 | Android API 26/35 各 1 次 |
| `UI_UPDATE_BACKGROUND` | UI-5 | 24 小时去重、前后台/网络恢复合并、计费网络策略，以及与 Bluetooth Service 的隔离 | 3 个生命周期场景 |
| `UI_ACCESSIBILITY_MATRIX` | UI-5 | TalkBack、键盘焦点、较大文字、reduced motion、对比度和 opaque fallback | 每个 route 1 次 |
| `UI_RELEASE_AUDIT` | UI-5 | 全 route、全状态、全部语言、无障碍和 classic/glass 的最终回归 | 1 个完整 UI 矩阵 |

验收证据分为三层：GitHub Actions 的 `diff-check`/JVM/构建报告、模拟状态/Compose 或截图基线、需要真实设备的定向报告。只有真实设备验证涉及蓝牙动作时，才收集 `attemptId`、endpoint、channel、source 和阶段耗时；UI 视觉和导航改动只保留对应截图/交互日志。

当某一阶段代码完成并进入真实设备或人工交互门时，将该阶段标题改为 `[~] 目标受阻：等待实测报告`，并在本文件记录测试标签、构建标签、设备/系统、步骤、期望结果和报告路径；报告返回后只修复该阶段对应问题，再把状态改为 `[x]` 或继续保持 `[~]`。当前 `v4.4.0 / 99` 已准备 UI 专用测试包，但尚无 GitHub CI 产物、截图/运行诊断证据，因此保持目标受阻。

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

已确认的初始化等待来源（优化前基线与当前策略）：

- 初始稳定等待保留 250ms。
- 已知型号的首轮 Core Handler 单通道串行，每个请求最多 1 秒，命令之间间隔 80ms；未知/兼容端点首轮使用 3 秒预算。
- 3 秒恢复超时、核心失败重试、自动低延迟确认和 Deferred 探测进入后台初始化任务，不阻塞首轮 `CoreReady`。
- 自动低延迟仍经过同一 `HuaweiCommandScheduler` command lane；恢复任务不会创建第二套 Socket、Reader 或 response lane。
- 低延迟写入后的后台交接等待从固定 1 秒缩短为 250ms；写入自身的 ACK/读回 settle 仍由命令 Handler 保留。

因此，重构前必须先把 `TransportReady`、`CoreReady`、`Ready` 和 `Degraded` 分开统计；先优化阶段边界，再调整具体延迟值。

### 4.2 指令基线清单

以下是按当前代码整理出的第一版指令目录。迁移时以它为单一清单，逐条补齐：方向、参数、响应、通知、超时、重试、能力和验证状态。

| 功能 | 读取/状态 | 写入/动作 | 备注 |
|---|---|---|---|
| 电量 | `01 08` | — | `01 27` 为通知 |
| ANC | `2b 2a` | `2b 04` | `2b 2c` 只作摘戴/模式变化提示；不直接写入 UI，burst 安静后刷新 `2b 2a` |
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
| Core fast pass | 页面首屏需要的 ANC、电量、低延迟、音质等首轮状态 | 产生 `CoreReady`；已知型号优先缩短首屏可用时间 | 首轮 1 秒；失败能力进入后台恢复 |
| Core recovery / Action | 失败 Core 重试、自动低延迟 ACK/读回确认 | 更新最终 `Ready` 或 `Degraded`，不回退 Transport | 3 秒恢复预算，单一 command lane |
| Deferred | 设备信息、手势、EQ、双连枚举、语言等 | 不阻塞控制通道 | 后台 best-effort，按能力降级 |

自动低延迟需要单独建模为 `ConnectionAction`，不要和核心读取共享一个“初始化完成”标志：

- 先记录写入开始、ACK、读回确认和最终结果。
- 基线测试时分别比较“自动低延迟关闭”和“自动低延迟开启”。
- 低延迟写入放在首轮 `CoreReady` 之后的后台 action 中；它仍使用同一 Scheduler，不创建独立请求队列。
- 低延迟后续重试不能与 Deferred 初始化并行争用同一请求队列；应由一个 Scheduler 排队。
- `sppQuietUntil` 必须成为 Scheduler 的实际门控条件，不能只被 fast polling 检查。

当前已知型号首轮快速路径可按以下方式估算：

```text
250ms settle
+ 4 个请求 × 1000ms timeout
+ 5 个 80ms inter-command gap
≈ 4650ms 首轮超时上界

失败恢复、自动低延迟和 Deferred：后台继续执行，不计入首轮 CoreReady
```

该数字是超时上界，不是实测耗时；目标是让“Socket 已连接”和“首轮核心状态可用”先恢复到上一版速度，同时保留失败恢复和单队列时序保护。

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

#### BT-0：协议与连接基线 `[x] 已完成`

- 保存当前实机日志样本：连接、初始化、ANC 切换、低延迟切换、断开、重连、双连枚举。
- `[x]` 为 `HuaweiSppPackage` / `HuaweiSppFramer` 添加分片、粘包、跨读取 magic、嵌套包和非法长度测试；固定样本位于 `app/src/test/resources/fixtures/huawei_spp/`。
- `[x]` 明确 5A 双字节长度、完整帧（`length + 5`）、CRC 和参数边界的唯一解释，删除帧长计算与实现不一致的问题。
- 先不改变现有命令行为。

##### BT-0.1：当前本地协议/能力基线审计 `[x] 已完成`

> 完成记录：2026-08-14；已完成本地 Kotlin 协议/能力实现、型号表、上游逆向资料和固定十六进制样本核对。2026-08-10 固定了 OpenFreebuds commit `035bf2a87cdda9e9c8e8e90c662b7fa61270c6ee` 的 292 文件参考快照；该快照只用于研究，不进入 Android 构建。协议样本已由 `HuaweiSppPackageFixtureTest`/`HuaweiSppFramerTest` 闭环；实机能力证据表、跨型号实机门仍按各自验收条件补齐，未验证项继续保持 `PARTIAL`/`UNKNOWN`。

本地检查结果：原始上游资料已保存为 `reference/openfreebuds/`，来源、许可证、路径映射和更新流程固定在 [`docs/UPSTREAM_OPENFREEBUDS.md`](./UPSTREAM_OPENFREEBUDS.md) 与 [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md)。当前 Kotlin 是独立的 Android 派生实现；后续结论必须同时标记“上游来源”“当前代码实现”和“本项目实机证据”，不能把其中任一项单独写成已验证行为。

| 审计对象 | 当前状态 | 结论 |
|---|---|---|
| SPP 5A 封包、解析、分帧 | `HuaweiSppPackage`、`HuaweiSppProtocol`、`HuaweiSppFramer` 仍在 | 双字节长度、`length + 5` 完整帧、参数边界、可选 CRC 校验和固定上游样本已由 `HuaweiSppPackageFixtureTest`/`HuaweiSppFramerTest` 覆盖 |
| 主要控制指令 | `HuaweiCommandCatalog`、Handler 和 Scheduler 仍在使用 | 主验证设备的核心指令链路已有代码和部分实机证据，全部型号尚未完成复核 |
| 型号能力表 | `HuaweiModel` + `modelCapabilities` 仍在 | 属于兼容性基线；必须按型号、能力、命令方向和证据来源维护，不是全型号最终真值 |
| 原始逆向资料 | 已固定 `reference/openfreebuds/` 快照 | 以固定 commit 和 `docs/UPSTREAM_OPENFREEBUDS.md` 为唯一研究来源入口，不把分支 HEAD 或未记录网页内容当作可复核基线 |

已确认的结构性问题：

- `HuaweiModel.sppPort` 没有传入连接层，`SppDriver` 仍硬编码使用端口 `1`；表中的端口差异当前不会生效。
- 未识别型号会得到空能力集合，而当前 `has()` 将空集合解释为“全部能力”，与未知能力默认隐藏的原则相反。
- `HuaweiCapability` 中部分长按扩展能力只有枚举/表项，没有独立 Handler 或完整 UI 链路，需逐项标记为 `implemented`、`parsed-only` 或 `unverified`。
- 多个型号共用一个能力档案，部分型号在代码中明确属于临时保守适配，不能直接视作实机验证结论。
- 指令常量不完整，仍有裸 `byteArrayOf(...)` 分散在 Handler 中；迁移时需要建立唯一指令目录。
- 当前测试重点集中在初始化策略、pending response 和 i18n；协议固定样本/分片/粘包/CRC 边界已补齐，命令读回和型号能力表仍需逐型号测试证据。

BT-0.1 的资料和证据边界固定如下：

1. `docs/UPSTREAM_OPENFREEBUDS.md` 记录上游 commit、许可证、路径映射和快照更新流程；它证明来源可追溯，不证明 Android 行为已经验证。
2. `[x]` 协议固定样本已进入 Android 测试可读取的版本化 fixture，并覆盖分片、粘包、跨读取 magic、非法长度、CRC/参数边界、嵌套包、EOF 和重连清理；测试通过后协议样本闭环。`EOF`/重连清理仍由 `ProtocolSessionTest` 覆盖。
3. 能力证据表的 `VERIFIED` 只允许引用同一型号/固件的实机报告或可复核的本地测试证据；只有上游源码或 Kotlin 表项时最多标记 `PARTIAL`，没有可追溯证据时标记 `UNKNOWN`。
4. `BT_MANAGER_RUNTIME_20`、BT-0.2 和其他硬件 profile 的报告单独作为实机门证据；CI、JVM 测试、静态审计和上游快照都不能替代同一设备的 F10 + 初始化10 报告。

BT-0 必须产出一份能力证据表：

| 型号 | SPP 端口 | 能力 | 读取命令 | 写入命令 | 通知命令 | 证据 | 状态 |
|---|---:|---|---|---|---|---|---|
| MODEL | PORT | CAPABILITY | COMMAND | COMMAND | COMMAND | LOG / SOURCE / DEVICE | VERIFIED / PARTIAL / UNKNOWN |

证据字段必须能回到具体的上游路径、代码符号、测试 fixture/测试名或实机报告路径，并注明型号、固件、App 版本和 profile。能力证据表和协议固定样本完成前，新增型号能力默认使用保守策略；已有表项若没有实机日志或可追溯来源，标记为 `PARTIAL`，不直接提升为已验证能力。

##### BT-0.2：连接速度专项基线 `[x] 已完成`

> 完成记录：2026-08-14；`4.3.6 / 94` 的 `BT_MANAGER_RUNTIME_20` 已完成 F10 + 应用入口初始化10，主设备定向实机门通过。跨型号/固件仍不在本阶段结论内。

已收到主验证设备的 `a.txt`、`b.txt` 两份诊断报告，作为问题定位证据，不作为阶段完成证据：

| 报告 | Transport | Transport→CoreReady | 结果 | 关键现象 |
|---|---:|---:|---|---|
| a | 770ms | 7535ms | Degraded | 首轮 `anc_global`/`low_latency` 超时；后续 `sound`、`battery` 出现成功，说明初始化受时序影响 |
| b | 1009ms | 11823ms | Degraded | 自动低延迟重试与 Deferred 初始化并行，出现 `queueWait=749ms/800ms`，`2b6c` ACK 被当作读回结果的风险 |

调整前实机报告摘要（文件：`/Users/chenhong/Downloads/fxxkHilife_hardware_regression_1785615540067.txt`；HUAWEI FreeBuds 6i，Android API 36，应用基线 `4.2.6 / 87`）：

| 场景 | 结果 | 报告 P50/P95 | 结论 |
|---|---:|---:|---|
| A | 10/10 PASS | 29976 / 30288ms | 报告等待窗口通过；阶段日志显示实际 Transport/Core/Ready 分段明显更短 |
| B | 10/10 PASS | 29998 / 30260ms | 自动低延迟开启路径通过，但需继续确认 ACK/读回顺序 |
| C | 10/10 PASS | 29987 / 30194ms | ACL 入口通过 |
| D | 0/10 PASS | 0 / 0ms | `startDiscovery()` 返回 false，已列出的绑定设备未进入 runner 回退路径 |
| E | 10/10 PASS | 29979 / 30245ms | 失败后重试最终通过，但旧 runner 需要绑定首个 attempt，避免统计到 PeriodicCheck |
| F | 0/10 PASS | 0 / 0ms | 第 1 轮走到手动重连和 ACL 断开，但 ACL 重连失败；后续轮次在初始控制通道阶段失败 |

同一报告的每次成功连接明细确认：endpoint 为 `rfcomm-channel=1`，source 为 `VerifiedModelConfig`；连接慢的主因暂时不是 channel。对报告中可配对到完整阶段的 40 个 `PeriodicCheck` attempt 解析得到 Transport→CoreReady 中位数约 `829ms`、P95 `888ms`，CoreReady→Ready 中位数约 `2087ms`、P95 `2163ms`。A/B/C/E 的约 30 秒主要是旧 runner 等待到了后续 `PeriodicCheck` attempt，不能直接当作单次控制通道耗时；新报告已改为按请求返回的 attemptId 统计，且额外输出阶段 P50/P95。

本轮已落地的修复方向：

- `HuaweiCommandCatalog` 集中读/写/通知命令，`HuaweiCommandClient` 提供 ACK、读回确认、超时/失败结果，所有发送经过 `HuaweiCommandScheduler` 单一 command lane。
- `HuaweiHandlerRegistry` 支持同一命令由多个功能观察者接收，避免 `2b03` 的佩戴检测与旧 ANC 变化处理互相覆盖。
- `DeviceRepository.connect()` 在启动协程前同步保留 attemptId；重复入口复用同一 attempt，回归 runner 只接受自己请求的 attempt。
- `RfcommSppTransport` 增加 generation 防护，旧 read loop 的迟到异常不会关闭新 Socket；ACL/测试断开显式关闭旧 session。
- `BluetoothScanner` 在 `startDiscovery=false` 且已列出绑定设备时使用绑定设备回退，并继续记录真实 discovery 失败原因。
- 回归报告在最终断开后仍能从型号适配器恢复 endpoint/channel/source，避免报告头部显示 `endpoint=unavailable`。
- 自动连接请求现在返回 `accepted + attemptId`，回归 runner 对空 id、超时和后续周期连接严格隔离；F 的 ACL 重连通过 `AutoConnectKnownSystemConnected` typed command 进入同一入口。
- 手动/ACL/EOF 清理后仅对热重连增加 `250ms` RFCOMM 排水窗口，冷连接不增加等待；测试报告的场景明细与统计同步记录三个阶段耗时。

上一轮 `4.3.2 / 90` 的实机验收重点是第三次 RFCOMM Socket 创建、B/F 热断开排水窗口、D 回退和初始化终态；`4.3.3 / 91` 已完成 ANC 摘戴状态来源修复和人工验证；`4.3.4 / 92` 的 36 项报告为 31/36，B/D/E 已通过；`4.3.5 / 93` 的 F10/初始化10作为历史 follow-up 保留；`4.3.6 / 94` 已完成 F10/初始化10，验证 Manager 接管状态与 Job 后的断开/初始化生命周期。

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

##### BT-0.3：专项基线后的决策门 `[x] 已完成`

根据 60 轮主报告选择实现路径：

1. **不先改端点**：HUAWEI FreeBuds 6i 的实机日志稳定显示 `rfcomm-channel=1 source=VerifiedModelConfig`，当前证据不足以更换 UUID/channel。
2. **先改连接编排**：A/B/C/E 的总耗时主要被错误归属到后续 `PeriodicCheck` attempt；必须先让测试和生产状态绑定同一 attempt。
3. **先修 session 生命周期**：F 只有 2/10，通过旧 RFCOMM reader/session 可能在断开后残留，先用 generation 和显式关闭阻断竞态。
4. **再收敛命令调度**：ANC/低延迟/Deferred 共享单一 command lane，读回、ACK 和通知不能相互误认。
5. **D 走扫描回退**：`startDiscovery=false` 且系统已返回绑定设备时，使用绑定设备结果继续进入扫描完成连接路径，并在日志中保留回退原因。

#### BT-1：唯一生产传输路径 `[x] 已完成`

> 完成记录：2026-08-14；代码已将生产 RFCOMM Socket、输入输出流、读循环和关闭逻辑收敛到 `RfcommSppTransport`，并由 `BT_MANAGER_RUNTIME_20` 在主验证设备确认 endpoint=`rfcomm-channel=1`、source=`VerifiedModelConfig` 和阶段耗时稳定。跨型号/固件仍需另行验证。

已完成：

- `[x]` `RfcommSppTransport` 成为唯一生产 Socket 实现。
- `[x]` 将 `SppDriver` 的接收循环、Socket 关闭和原始发送路径迁移到 Transport + ProtocolSession。
- `[x]` 分帧层覆盖分片、粘包、噪声重同步、跨读取 magic 和重连清理；`ProtocolSession` 覆盖分片转发和重连重置。
- `[x]` 删除上层重复 TX 锁，保留请求队列串行化；fire-and-forget 仍不能越过正在等待的响应。

仍待完成：

- `[x]` 将 UUID、channel、连接超时、取消和重试收敛为 `RfcommTransportConfig`，移除生产路径中的固定 port 常量；已知型号使用型号端点，未知型号保留 channel 1 compatibility fallback，并将 endpoint/source 写入连接元数据和日志。
- `[x]` 在主验证设备上完成 ANC 人工验证、自动低延迟既有验证、初始化持续推进、断开/重连，并记录回退结果；BT-4 新增状态/能力映射仍需单独回归。
- `[x]` 已发布 `4.3.2 (90)` 作为热重连恢复和严格初始化判定的历史回归测试包；最终连接阶段证据由 `4.3.6 (94)` 主设备报告收口。
- `[x]` `4.3.3 (91)` 的 ANC 摘戴状态修复已通过人工诊断验证；报告确认 `2b2c` 未被当作最终状态，滞后状态被守卫过滤。
- `[x]` 已发布 `4.3.4 (92)` 并取得同一设备 36 项定向报告：31/36；B=10/10、D=3/3、E=3/3，F=7/10、初始化=8/10。阶段未因报告层假失败而提前完成。
- `[x]` 已发布 `4.3.5 (93)` 作为状态/重试 follow-up 测试包；其针对的 F/初始化风险由 `4.3.6 (94)` 的 Manager 运行时报告最终覆盖。
- `[x]` 已发布 `4.3.6 (94)` 作为 BT-3 连接运行时收敛测试包；同一设备已完成 `BT_MANAGER_RUNTIME_20`：F10、初始化10。

#### 实机回归待测队列（按当前改动定向收集）

以下项目只覆盖当前版本实际改动；每完成一个子阶段就在本文件回写 `[x]`、版本、测试轮次和报告路径：

1. `[x]` **B 场景**：`4.3.4 / 92`，10 轮通过；系统已连接、discovery 关闭、自动低延迟开启。
2. `[x]` **F 场景**：`4.3.6 / 94`，10 轮；报告 `/Users/chenhong/Downloads/fxxkHilife_hardware_regression_1786655973008.txt`，手动断开抑制、RFCOMM 重连和 ACL 清理/重连均通过，最终 Ready；总耗时 P50/P95=`7562/8984ms`。
3. `[x]` **应用入口初始化推进**：`4.3.6 / 94`，10 轮；同一报告通过。10 秒观察后仍继续推进，20 项均为同一 attempt，最终 Ready，pending/failed 为空。
4. `[x]` **D 场景**：`4.3.4 / 92`，3 轮通过；discovery 完成、saved-fallback、endpoint/channel/source。
5. `[x]` **E 场景**：`4.3.4 / 92`，3 轮通过；首轮失败后的外层重试语义仍需区分 Transport 内部重试。

当前 `BT_MANAGER_RUNTIME_20` 实机门已完成。36 项历史门槛的 B/D/E 已完成，ANC 摘戴专项由 `ANC_WEAR_STATE` 保留并已通过人工诊断验证；后续 BT-4 只增加通用状态/能力映射相关项目，不恢复完整矩阵。测试报告沿用小于 200 MB 的字节预算；出现新问题时，只在本表追加对应场景或功能项，保持定向范围。

定向运行输出与全量运行使用同一报告格式：每项输出 PASS/FAIL、P50/P95、最大耗时和失败详情；B/F 额外输出 Socket 尝试、排水间隔和 attemptId，初始化项额外输出观察前后 stage、pendingHandlers、failedHandlers 和最终 core 状态。完整 100 项矩阵只在跨模块验收、Release 候选或多个 BT 阶段同时变更时启用。激活测试模式后仍使用约 160,000,000 字节的 UTF-8 缓存预算，报告写入分享文件前限制为 190,000,000 字节以内，最终文件保持小于 200 MB；测试结束、取消、无设备和异常路径都会恢复正常日志策略。ACL 广播本身由系统触发，按钮中的 ACL 断开使用同一清理路径的 synthetic regression hook；报告会区分真实入口和 synthetic cleanup。

#### BT-2：CommandClient 与 Feature 拆分 `[x] 已完成`

> 完成记录：2026-08-14；代码已新增 `HuaweiCommandCatalog`、`HuaweiCommandClient`、`HuaweiCommandScheduler`，并将核心 Handler 的读写迁移到目录。`2b2a` 权威 ANC 快照、同 lane 刷新、低延迟 ACK/读回和连接初始化路径已有 JVM、人工诊断与主设备定向回归证据；未覆盖型号/固件仍按 `PARTIAL`/`UNKNOWN` 管理。

- `[x]` 建立 Huawei 命令目录和 `HuaweiCommandSpec`，消除主要 Handler 中散落的命令字节。
- `[~]` Battery、ANC、LowLatency、SoundQuality、Gesture、EQ、DualConnect 已经通过兼容 Handler 使用目录；ANC 的 `2b2c`/`2b2a` 状态来源修复已落地，按能力拆文件的 Feature 迁移留在后续小阶段。
- 把初始化顺序、核心/延迟能力、超时和重试从 Handler 本体移到策略配置。
- `[x]` 增加单一 `HuaweiCommandScheduler`，普通请求、写入确认、读回确认和 fire-and-forget 均经过同一 command lane；USER_ACTION 会优先于已排队的 Deferred/Background 请求，且不会越过已在执行的交换。
- `[x]` 将 `sppQuietUntil` 接入 Scheduler 的实际发送前门控，并让 disconnect 取消活动/排队命令。
- `[x]` 将“已发送”“ACK”“读回确认”“读回不匹配”“超时/失败”建模为 `HuaweiCommandPhase`，并把同 response key 的排队时间计入单一 timeout 预算；优先级队列已按 USER_ACTION、CORE、DEFERRED、BACKGROUND 生效。
- 保留旧 Handler 适配器，逐个 Feature 替换，不一次改完。

#### BT-3：连接编排拆分 `[x] 已完成`

> 完成记录：2026-08-14；UI、Service、Tile、Terminal 和回归 Runner 已改为 typed `ConnectionCommand`，Manager 接管 attempt、session、ACL 清理、连接状态和生命周期 Job；`BT_MANAGER_RUNTIME_20` 在 HUAWEI FreeBuds 6i 主设备完成 F10 + 初始化10，20/20 通过。BT-3 的后续统计仓库和 host 依赖收窄属于已验证门关闭后的结构性收尾，不改变生产连接时序。

- `SystemBluetoothMonitor`：系统蓝牙连接观察。
- `EarbudConnectionManager`：连接、断开、自动连接、backoff、手动抑制。
- `EarbudConnectionManager` 内的 `EarbudSessionRegistry`：按设备地址维护唯一 session，Repository 只能通过 identity-aware 方法操作。
- `EarbudStateStore`：状态流、失败能力、pending action 和连接原因。
- `ListeningStatsRepository`：从连接编排中移出统计逻辑。
- 统一 ACL、A2DP、HEADSET、Service 命令和周期检查的触发去重，所有触发都携带 `ConnectionTrigger`。
- 连接状态按 SystemLink、Transport、Core、Deferred 四层发布，禁止用单一 `Connected` 覆盖全部阶段。
- `BluetoothService` / Tile / UI / Terminal 改为消费 manager 的命令和状态；本轮已把 Repository 内的兼容连接状态、活动地址、系统链路集合和连接相关 Job 句柄迁移到 Manager 的 `ConnectionLifecycle`。
- `[x]` 入口去重的地址边界、扫描终态回调闸门、`SystemBluetoothMonitor`、ACL typed cleanup、session registry、attempt coordinator 以及连接运行时 Job/state 所有权已补齐；本轮继续将 Manager 依赖收窄为 `ConnectionManagerHost`，并将听音统计抽为 `ListeningStatsRepository`，不改变连接路径。
- `[x]` `4.3.10 / 98` 收尾了同一 attempt 的 `syncProps()` 串行化、失败 Transport 的 session 断开、断开前听音统计 flush 和 Manager command 边界串行化；这些改动只收紧生命周期，不改变已验证的 RFCOMM endpoint/channel。

#### BT-4：通用蓝牙状态输出（UI 接入前置） `[x] 已完成`

> 完成记录：`4.3.10 / 98` 在 `4.3.9 / 97` 基础上补齐同一 attempt 状态映射串行化、原子 snapshot 的 canonical channel、兼容 pending 投影、metadata-only core readiness 防误报，并加入 BT 收尾回归测试；Debug/Release 各 94 个单元测试、`git diff --check` 和 Debug/Release 构建均已通过，`BT4_STATE_CONTRACT_5` 5 轮实机报告也已通过。下一阶段以该状态契约驱动 UI，页面迁移尚未开始。

- `[x]` `DeviceProps` 只作为兼容映射；可变状态流已移入 `EarbudStateStore`，不再让 Repository/UI 直接持有状态写入器。
- `[x]` Huawei Adapter 输出通用 `EarbudState` 和能力集合；未知型号采用保守空能力集合，不再把未知型号解释为“全部能力”。
- `[x]` 通用 `EarbudState`、能力集合和分层连接状态的输出契约已完成，页面迁移从 UI-2 开始。
- UI-2 至 UI-4 通过 typed state/action 逐页消费该契约；UI-5 完成后删除 raw property 依赖。

## 5. 两项工作的依赖与执行顺序

执行顺序固定为**先完成蓝牙，再开始 UI**；BT-0 至 BT-4 已收口。UI 第一轮结构迁移被错误报告阻塞，不作为完成阶段；完全重构按以下顺序重新推进：

1. **BT-0.1 至 BT-4 `[x]`**：保持既有蓝牙状态、能力、连接生命周期和实机证据边界，不因 UI 重构重跑或改写。
2. **UI-RESET `[~]`**：记录第一轮迁移未完成生产切换，登记 UI-ERR-001/002/003 阻塞关系，Liquid 固定为 `BLOCKED/UNAVAILABLE`。
3. **BG-0 `[ ]`**：建立 `BackgroundState`、`RequestedDisplayMode`、`ResolvedSurfaceMode` 和无背景 Material 3 硬门。
4. **UI-FOUNDATION `[ ]`**：建立统一 Material 3/Frosted/Liquid surface、按钮、选项、状态和无障碍契约；页面不直接导入 Haze。
5. **FROSTED-1 `[ ]`**：把 alpha03 blur 明确重命名为真实毛玻璃 renderer，完成有效背景、无背景和滚动性能验证；不得称为 Liquid。
6. **LIQUID-1 `[ ]`**：独立解决 Haze alpha05/`haze-glass`/compileSdk/AGP/Lifecycle 门，再接入真实 `hazeGlass`、`GlassStyle`、`GlassOptics`；缺少任一证据时保持 blocked。
7. **NAV-1 `[ ]`**：用新的状态驱动导航完全替换旧 AppNavHost，修复自动进入、返回、再次点击和连接会话去重。
8. **DEVICE-1 `[ ]`**：完全替换 Device/Gesture/ListeningStats/Terminal 生产 route，迁移统一控件和 typed state/event。
9. **SETTINGS-1 `[ ]`**：完全替换 Settings，接入背景导入、模式解析、持久化、更新和无障碍状态。
10. **LEGACY-CUTOVER `[ ]`**：旧 Screen、旧 NavHost、旧 facade、旧 XML 和页面 Haze 参数透传从 production graph 清零。
11. **UI-RELEASE `[ ]`**：完成 Material 3/Frosted/Liquid、背景资格、性能、导航、无障碍和旧入口清零的最终矩阵。

每个阶段都遵循：**改一层、加测试、跑 CI、跑截图/运行诊断、跑交互性能、记录日志、更新本文件状态和回退记录、再迁移下一层**。未满足阶段门时保持 `[~]` 或 `[ ]`，不提前标记完成。

## 6. 验收标准

### UI

- 请求模式、背景资格和实际 renderer 分离：Material 3、Frosted、Liquid 必须分别可观测，不能根据请求模式伪造实际效果。
- 没有用户导入有效非纯色背景、背景加载失败、纯色或 source 未绑定时，Frosted 和 Liquid 均强制解析为标准 Material 3；此时 `sourceAttached=false`、`effectSurfaceCount=0`，不能出现透明空洞。
- Frosted 必须是真实 blur；Liquid 必须是真实 `hazeGlass`/`GlassStyle`/`GlassOptics`；alpha03 `hazeEffect + blurEffect` 只能作为历史毛玻璃实现，不能作为 Liquid 证据。
- UI-ERR-001/002/003 在真实 Liquid、滚动性能和导航流程分别关闭前，UI 不能标记 `[x]` 或使用 `UI_COMPLETE`。
- 页面文件只负责渲染和事件分发，不直接读取/写入 `SharedPreferences`、Haze API、厂商 raw `group/prop` 或协议命令字节。
- production graph 只能存在新 AppScaffold、背景宿主、SurfaceRenderer、typed route 和统一控件；旧 AppNavHost、旧 Screen、旧 facade 和旧 XML 不能继续作为生产回退入口。
- 所有按钮、开关、选择器、滑块和手势选项都使用统一组件目录、`UiActionState`/`OptionUiState` 和 `OptionPresenter`；页面不得重复定义控件样式、Pending/Failure 逻辑或 raw option 文案映射。
- 按钮至少覆盖 `Primary`、`Secondary`、`Tertiary`、`Destructive`、`Icon`、`Toggle` 类型，并统一处理 `Idle`、`Disabled`、`Pending`、`Success`、`Failure`。
- 选项至少覆盖 Boolean、SingleChoice、DependentChoice、Segmented、Slider 和 ActionPicker；必须区分 selected、pending、unknown、unavailable 和读回失败。
- Home、Scan、Device、Settings、Gesture 的返回、断开、自动连接路径有导航测试；用户点击设备卡片必须产生明确 `OpenDevice(address)` 事件。
- 连接页面可以分别呈现系统蓝牙已连接、控制通道已建立、核心能力初始化中、Ready 和 Degraded。
- Material 3、Frosted、Liquid 共用相同布局、触控、焦点、状态和无障碍语义；高对比度、大字和 reduced motion 可强制 Material 3。
- Hero/少量 Feature 才允许 effect；长列表 CompactRow 不逐项创建 effect，保存设备/扫描设备/双连接设备使用稳定 key，未变化 item 不因设备状态更新整体重组。
- 连接成功后的详情页跳转由一次性导航事件和连接会话驱动，不依赖连接方法返回瞬间的快照；返回后同一会话不会重新自动打开。
- UI 测试报告使用 `UI_BACKGROUND_QUALIFICATION`、`UI_MATERIAL3_BASELINE`、`UI_FROSTED_RENDERER`、`UI_LIQUID_RENDERER`、`UI_MODE_PARITY`、`UI_NAVIGATION_SESSION`、`UI_GLASS_PERFORMANCE`、`UI_LEGACY_GRAPH_AUDIT`、`UI_ACCESSIBILITY_MATRIX` 和 `UI_RELEASE_AUDIT`，不以版本号单独作为阶段识别。

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
- BT-0.1 的协议固定样本已纳入版本控制并覆盖分片、粘包、跨读取 magic、非法长度、CRC/参数边界、EOF 和重连清理；测试名称或 fixture 路径可从报告回溯。
- 型号能力证据表逐项记录型号、固件、端口、读/写/通知命令、证据路径和 `VERIFIED`/`PARTIAL`/`UNKNOWN`；只有同型号可复核实机或自动化证据才能标记 `VERIFIED`。
- OpenFreebuds 研究结论可回溯到固定 commit、快照路径和许可证说明；上游资料、CI/JVM 测试和静态审计均不替代硬件实机门。
- `BT_MANAGER_RUNTIME_20` 已在同一主验证设备完成 `4.3.6 / 94` 的 F10 + 初始化10；后续 BT 改动仍须使用对应 profile 和新报告，不把旧报告扩展到新代码。
- `BT4_STATE_CONTRACT_5` 已在 `4.3.10 / 98` 主验证设备完成 5/5；后续 UI 只消费已冻结的状态契约，UI 改动不重复蓝牙矩阵，除非同时修改 BT 状态映射。

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

## 9. 历史文档维护规则

为避免计划、版本记录和开发日志互相漂移，后续按以下职责维护：

| 内容 | 唯一维护位置 | 维护时机 |
|---|---|---|
| 当前架构方案、阶段状态、目标受阻点、定向测试清单 | 本文件第 0–8 节 | 每完成一项代码/测试工作后同步 |
| 原始架构设计、已替代方案和旧阶段记录 | 本文件附录 A | 只追加历史说明，不改写原始语义 |
| 旧路径兼容说明 | `docs/ARCHITECTURE_TODO.md` | 仅在主计划入口变化时更新跳转 |
| 应用版本与 versionCode 历史 | `VERSION_MANAGEMENT.md` | 只有可独立构建、测试、回归和回退的应用里程碑才增加版本项 |
| 详细实现、日志和验证证据 | `DEVELOPMENT_LOG.md` | 每个代码里程碑或重要实测回灌后追加记录 |

维护约束：

1. 历史记录只追加，不删除已发布版本、测试结论或失败证据；新证据通过日期、版本、profile 和报告文件名关联旧记录。
2. 文档合并、格式修复和历史归档属于文档变更，不提升 `versionName`/`versionCode`，也不把实机门提前标记为通过。
3. 当前状态只看本文件前文；附录 A 中的 `[x]`/`[ ]` 和旧版本描述仅用于还原当时上下文。
4. 原始硬件报告、CI 输出和对话历史不复制进计划正文；正文只保留可检索的文件名、版本、profile、结论和证据路径。
5. 能力证据表、协议 fixture 和上游快照必须保持可回溯：每条结论至少关联代码符号/测试名、固定来源路径或实机报告路径；来源缺失时不得提升状态。
6. `BT_MANAGER_RUNTIME_20` 与 `BT4_STATE_CONTRACT_5` 的当前主验证门已经关闭；后续若修改 BT 状态/连接代码，重新建立对应的定向 profile 和报告，不把 UI 报告当成蓝牙证据。
7. 每次文档维护完成后执行 `git diff --check`，只提交明确的文档文件，不提交 `.DS_Store`、`ci-output-*` 或本地对话记录。

### 9.1 本次历史文档维护记录

- 2026-08-10：将合并前 `ARCHITECTURE_TODO.md` 的完整通用架构正文、接口示例、迁移步骤和 v4.1–v4.2.1 历史记录归档至本文件附录 A。
- 2026-08-10：`ARCHITECTURE_TODO.md` 改为旧路径跳转说明，停止维护第二份阶段清单、版本号和测试门。
- 2026-08-14：BT-0 至 BT-4 已收口，当前应用版本为 `4.3.10 / 98`；本次将下一阶段重心改为 UI 全量重构，新增 UI-0 至 UI-5 顺序、美术资源保留清单、构建标签和定向验收规则；版本号保持不变。
- 2026-08-15：根据 `docs/UI_ERROR_REPORT.md` 重置 UI 完全重构计划：第一轮 UI-0 至 UI-5 结构迁移不再视为完成；新增 BG-0、UI-FOUNDATION、FROSTED-1、LIQUID-1、NAV-1、DEVICE-1、SETTINGS-1、LEGACY-CUTOVER 和 UI-RELEASE 阶段。明确无有效导入背景时玻璃请求必须解析为标准 Material 3，且 source/effect 为 0；blur 不得称为 Liquid；旧页面/旧 facade 不得继续位于 production graph。
- 2026-08-15：`v4.4.0 / 99` 进入 UI 错误报告修复门：alpha05 typed glass/blur、无背景加载状态、列表稳定 key 和 address/attemptId 会话导航已落地；构建标签为 `UI_ERROR_FIX_ALPHA05`，等待 GitHub Actions、截图/运行诊断和定向交互报告。

---

## 附录 A：原架构 TODO（历史设计记录）

> 本附录完整保留合并前 `ARCHITECTURE_TODO.md` 中的通用架构方案、接口示例、迁移记录和历史版本记录。它用于保留设计上下文，不作为第二份执行清单；当前方案选择、完成状态、版本号和实机测试门均以本文第 0–8 节为准。附录中的 `[x]`/`[ ]` 是当时记录，不代表当前状态。

### 原始标题

> 架构重构 TODO：可插拔耳机协议与能力层

### 原始文件说明

专项重构规划见：[`REFACTOR_PLAN_UI_AND_BLUETOOTH.md`](./REFACTOR_PLAN_UI_AND_BLUETOOTH.md)。本文继续保留通用 Adapter / Protocol / Capability 架构 TODO；UI 与蓝牙指令/连接的阶段化执行、验收标准和迁移顺序以专项文档为准。

### 原始目标

> 目标：把当前偏 Huawei / OpenFreebuds 的实现，逐步整理成可接入更多逆向耳机项目的插件式架构。

### 背景

当前项目的协议、能力表、Handler 注册、UI 属性映射已经可以支撑 HUAWEI / HONOR Earbuds 的核心控制，但接口边界仍然偏“单厂商内聚”：

- `DeviceRepository` 同时承担连接编排、型号识别、Handler 注册、属性同步、统计状态等职责。
- `SppDriver` / `HuaweiDeviceHandler` / `OpenFreebudsHandlers` 与华为协议强绑定。
- UI 层直接消费 `DeviceProps`，其中字段也以当前华为能力为主。
- 后续如果接入其他逆向项目的耳机，容易把更多厂商分支继续塞进现有仓库和 Handler 注册逻辑。

### 分层目标

#### 1. Transport 层

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

#### 2. Protocol 层

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

#### 3. Capability 层

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

#### 4. Plugin / Adapter 层

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

#### 5. State 层

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

#### 6. Repository 层瘦身

当前 `DeviceRepository` 后续拆分方向：

- `EarbudConnectionManager`：扫描、连接、断开、自动连接。
- `EarbudAdapterRegistry`：管理可用插件与设备匹配。
- `EarbudStateStore`：通用状态仓库。
- `ListeningStatsRepository`：听音统计独立持久化。
- `LogRepository`：日志导出与调试。

### 迁移步骤

#### 阶段 1：不破坏现有功能的接口整理

- [x] 新建 `core/transport` 包，定义 `EarbudTransport`。
- [x] 将当前 SPP 连接能力包一层 `RfcommSppTransport`，先作为新架构 Transport 引入；生产路径暂不替换 `SppDriver`。
- [x] 新建 `core/adapter` 包，定义 `EarbudAdapter` / `EarbudController` 草案。
- [ ] 将 `HuaweiModel` / `HuaweiCapability` 移入 `protocol/huawei` 或 `adapter/huawei` 命名空间。
- [x] `DeviceRepository.registerHandlers()` 改为委托给 `HuaweiOpenFreebudsAdapter`。
- [x] 新建 `core/protocol` 包，定义 `EarbudProtocol` / `ProtocolFramer` / `ProtocolSession`。
- [x] 新增 `HuaweiSppProtocol` / `HuaweiSppFramer`，把 5A 包编码与流式解帧抽到协议层；生产路径暂不替换 `SppDriver`。
- [x] 新增 `HuaweiHandlerRegistry`，把 Handler 列表、命令路由、ignore command、属性路由和失败 Handler 集合从 `SppDriver` 中抽出。
- [x] 新增 `HuaweiPropertyStore`，把 Huawei Handler 属性仓库从 `SppDriver` 中抽出，为后续通用 `EarbudState` 映射做准备。

#### 阶段 2：能力与状态通用化

- [ ] 定义通用 `EarbudCapability`。
- [ ] 定义通用 `EarbudState`，先与 `DeviceProps` 并存。
- [ ] 给 Huawei Adapter 增加 `HuaweiStateMapper`，负责 Huawei 属性 → 通用状态。
- [ ] UI 层逐步从 `DeviceProps` 迁移到通用状态。
- [ ] 未迁移字段继续通过兼容层提供。

#### 阶段 3：统计与附加功能解耦

- [ ] 将听音统计从 `DeviceRepository` 独立为 `ListeningStatsRepository`。
- [ ] 支持统计来源策略：连接时长 / 佩戴时长 / 媒体播放状态。
- [ ] 为不同 Adapter 暴露是否支持稳定佩戴检测。

#### 阶段 4：新耳机项目接入准备

- [ ] 为 Adapter 增加独立测试入口和日志导出标识。
- [ ] 支持一个设备被多个 Adapter 试探匹配，但只激活优先级最高者。
- [ ] 新增“实验性适配”标记，UI 中提示风险。
- [ ] 允许插件声明自己的调试面板字段。

### 近期优先级

1. 先让 v3.8.0 听音统计通过 CI。
2. 下一版只做接口拆分，不新增大功能。
3. 优先把 Huawei/OpenFreebuds 现有能力包成第一个 Adapter。
4. 保持 UI 行为不变，避免在重构期引入用户可见退化。

### 设计原则

- 核心 UI 不直接依赖厂商协议字段。
- 新厂商接入不修改主 Repository 的大段逻辑。
- 未验证能力默认隐藏。
- 所有实验性能力必须能导出日志。
- 重构期间保持当前 Huawei / HONOR 功能稳定优先。

### v4.1.0 第三方接入边界
- 新增 `core/session/EarbudSession`，作为连接态耳机的通用运行时边界。
- 新增 `LegacySppEarbudSession`，把现有 Huawei `SppDriver` 包装成 session，避免 `DeviceRepository` 继续直接依赖具体驱动。
- 后续第三方耳机应优先实现自己的 `EarbudSession` / `EarbudAdapter.mapState()`，再按需接入 `EarbudTransport` 与 `EarbudProtocol`。


### v4.1.1 底层连接效率
- 新增 `core/transport/RfcommSocketBridge`，集中封装 Android 隐藏 RFCOMM socket API。
- 缓存 `createRfcommSocket`、`connect`、`close`、`getInputStream`、`getOutputStream` 反射 Method，减少重复连接开销。
- 建连前主动取消 Bluetooth discovery，避免扫描拖慢 RFCOMM 连接。
- `SppDriver` 与 `RfcommSppTransport` 共用同一建连路径，为后续迁移到通用 transport 降低差异。


### v4.1.2 稳定性审计
- `RfcommSocketBridge` 的 discovery 取消操作改为权限安全兜底，避免 Android 12+ 权限异常阻断建连。
- RFCOMM socket 在 `connect()` 失败时主动关闭，降低半开 socket 泄漏风险。
- `BluetoothScanner` 不再把所有已配对设备误标为已连接，改为读取系统 HEADSET/A2DP 连接状态与隐藏 `isConnected()` 结果。
- 扫描器对 `device.name/address/bondState` 增加安全读取，避免运行时蓝牙权限异常导致扫描页崩溃。
- `DeviceScreen` 设备型号/固件展示移除 `!!` 强制解包。


### v4.2.0 EQ 与双设备 MVP
- UI 层继续沿用 `DeviceProps`，新增 EQ preset、custom EQ 只读状态、dual-connect 设备列表和保存设备连接快照字段，后续迁移到通用 `EarbudState` 时应拆成 `AudioState` 与 `ConnectionState` 子结构。
- `EqualizerPresetHandler` 只写入 OpenFreebuds 已验证的 preset payload：内置 preset 使用 `2b49` 参数 1，已验证兼容预设使用 `mode_id/name/rows/action` 固定 payload。Custom EQ 行编辑需要用户自定义 `mode_id/name/rows/action` 组合 payload，目前只解析和展示耳机返回的 rows/saved/max-count，暂不开放写入。
- `DualConnectHandler` 管理耳机侧双连能力：读取开关、枚举设备、首选设备、连接/断开、自动连接和解绑命令。Android 本地仍只维护一个 active `EarbudSession`，避免在未知 ROM/蓝牙栈上强行创建两条并发 SPP 控制通道。
- 首页保存设备列表显示系统蓝牙连接与当前控制通道连接状态。若未来实现真正双 session，应先把 `DeviceRepository.session` 拆为按 address 管理的 session map，并明确通知/Tile/统计使用哪一个 active control target。
- Liquid Glass UI 增加 `AdaptiveGlassBanner`，Home / Scan / Device 的状态反馈走同一 Haze 2.0 卡片体系；经典模式仍使用低成本 Material3 Surface/Card。

### v4.2.1 OpenFreebuds 初始化对齐与后台控制连接
- Handler 初始化回到 OpenFreebuds 语义：注册后的 handler 顺序初始化，默认 3s × 5 次；`dual_connect` 使用独立短窗口和 6 次尝试；失败后作为能力降级展示，不再后台无限重试抢占 SPP。
- `HuaweiPendingResponseManager` 遇到相同 responseId 时等待前一个请求完成，不再取消旧 waiter；`sendPackage` 默认 timeout 恢复到秒级，只有已知异步/短窗口命令显式使用较短 timeout。
- `DualConnectHandler` 的成功条件以有效 `2b31` 设备列表写入为准，避免只收到开关状态或已写入列表后仍继续重试枚举。
- 后台保活服务监听系统 ACL/A2DP/HEADSET 连接事件，并周期检查已保存设备；系统蓝牙在线才尝试 App SPP 控制连接，失败按退避重试，手动断开后短期不自动抢回。
