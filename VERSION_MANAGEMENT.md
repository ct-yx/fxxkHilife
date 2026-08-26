# 版本管理

> 版本号统一修改入口，按清单逐一更新。

## 当前版本
- **当前正式发布：v4.6.0** (versionCode=103, 2026-08-18；标准 Material 3 UI、普通壁纸背景和深色模式亮度处理已发布)
- **当前开发测试：v4.7.1** (versionCode=105, 2026-08-26；Light Field、自适应透明度、无边框柔光材质与悬浮顶部栏，默认标签 `UI_LIGHT_FIELD_GLASS_V2`，尚待定向实机验收)
- **上一公开版本：v4.3.10** (versionCode=98, 2026-08-14；BT 重构后的首个大版本，BT-0 至 BT-4 已完成，BT-4 定向实机门已通过)

## v4.3.10 发布基线

- 发布定位：BT 重构后的首个公开大版本；UI 全量重构不包含在本版本内。
- 发布触发：推送与应用版本一致的 `v4.3.10` 标签，由 CI 负责重新执行自动化检查、构建 APK 和更新 Release 资产。
- 实机门：`BT4_STATE_CONTRACT_5`，5/5 PASS；只覆盖 HUAWEI FreeBuds 6i / Android API 36 / 固件 `HarmonyOS 6.0.0.292(F001H003C00)` 及该 profile。
- 当前开发方向：v4.7.1 进行 API 33+ Light Field AGSL 玻璃、API 32 MD3 fallback、截图、触摸受光、滚动、无障碍和展开交互定向验收；不重复 BT 全量矩阵。

## 文档同步记录

应用版本不因文档合并、历史归档或格式修复而增加：

- 2026-08-10：`ARCHITECTURE_TODO.md` 的原始架构正文归档至 `docs/REFACTOR_PLAN_UI_AND_BLUETOOTH.md` 附录 A；当前版本仍为 v4.3.6（versionCode=94）。
- 当前阶段状态和实机测试门仍以 `docs/REFACTOR_PLAN_UI_AND_BLUETOOTH.md` 为准，详细变更证据记录在 `DEVELOPMENT_LOG.md`。
- 2026-08-14：BT-3 实机报告已回灌并收口；BT-4 通用状态/能力契约完成代码收紧和 JVM 验证，等待 `BT4_STATE_CONTRACT_5` 实机门，应用版本仍为 v4.3.6（versionCode=94）。
- 2026-08-14：发布 v4.3.7（versionCode=95）BT-4 定向测试包；默认 Debug profile 为 `BT4_STATE_CONTRACT_5`，实机报告返回前不关闭 BT-4 阶段。
- 2026-08-14：发布 v4.3.8（versionCode=96）BT-4 状态契约 follow-up 包；收紧并发投影、能力域和报告 PASS 校验，实机报告返回前不关闭 BT-4 阶段。
- 2026-08-14：发布 v4.3.9（versionCode=97）BT-0 协议固定样本 follow-up 包；修正 5A 双字节长度、完整帧边界、参数边界和可选 CRC 校验，BT-4 仍等待同一 Debug 包的 5 轮实机报告。
- 2026-08-14：发布 v4.3.10（versionCode=98）BT 连接收尾与状态契约竞态 follow-up 包；修复状态映射串行化、断开前统计 flush、失败 session 清理、Manager command 串行边界、Handler 去重和共享 Adapter Registry，BT-4 重新等待该 Debug 包的 5 轮实机报告。
- 2026-08-14：v4.3.10 / 98 最终本地 CI 通过；Debug/Release 各 94 个 JVM 单元测试、`diff-check`、Debug/Release 构建均通过。Debug APK SHA-256=`d36085bc37a271d99aa5d0575d9af36be63e93b8eec69af134d562740c72bfec`，Release APK SHA-256=`501df2aa286c19141407c83e926ade357f1b1f55ff5706b6851b19d54de78e54`；随后由 `BT4_STATE_CONTRACT_5` 5 轮实机报告完成回灌。
- 2026-08-14：`/Users/chenhong/Downloads/fxxkHilife_hardware_regression_1786696542049.txt` 回灌并通过 `BT4_STATE_CONTRACT_5`：5/5、`expectedOperations=5`、`completedOperations=5`、`reportValid=true`、`overall=PASS`；BT-0 至 BT-4 蓝牙重构收口，应用版本仍为 v4.3.10 / versionCode 98，UI-0 暂不启动。
- 2026-08-14：将 `v4.3.10 / 98` 标记为 BT 重构首个公开 Release 基线；后续 UI 文档与页面改动不回写为 BT 功能完成，也不复用 BT-4 实机报告作为 UI 验收证据。

## 版本号位置

| 位置 | 当前值 |
|------|--------|
| `app/build.gradle.kts` | versionCode=105, versionName="4.7.1" |
| `app/src/main/res/values/strings.xml` | version_name=4.7.1 |
| `README.md` | v4.7.1 |
| `README_EN.md` | v4.7.1 |
| `DEVELOPMENT_LOG.md` | v4.7.1 (末尾) |

## 一键版本更新脚本

```bash
python3 scripts/bump_version.py <versionName> <versionCode> "修复说明"
# 例如：python3 scripts/bump_version.py 2.7.8 27 "修复说明"
```

脚本会同步更新：
- `app/build.gradle.kts`
- `app/src/main/res/values/strings.xml`
- `README.md`
- `README_EN.md`
- `docs/index.html`
- `VERSION_MANAGEMENT.md`
- `DEVELOPMENT_LOG.md`

> 脚本只负责统一替换版本号和插入基础发布记录；具体变更说明仍建议人工补充到 `DEVELOPMENT_LOG.md`。

## 历史版本

| 版本 | Code | 日期 | 主要变更 |
|------|------|------|---------|
| v4.7.1 | 105 | 2026-08-26 | UI Light Field V2 glass material, adaptive transparency and floating top bar |
| v4.7.0 | 104 | 2026-08-22 | 自研 AGSL 壁纸柔光玻璃与卡片视觉重构 |
| v4.6.0 | 103 | 2026-08-18 | 发布标准 Material 3 UI 与深色模式壁纸亮度优化 |
| v4.5.0 | 102 | 2026-08-16 | 移除玻璃渲染依赖，统一为标准 Material 3 UI 基线 |
| v4.4.2 | 101 | 2026-08-16 | 历史玻璃渲染尝试：无输入可见性与列表拖动 follow-up；已由 v4.5.0 移除 |
| v4.4.1 | 100 | 2026-08-16 | 历史玻璃渲染可见性与详情页滚动性能测试包 |
| v4.4.0 | 99 | 2026-08-15 | 历史 UI 错误报告修复包：typed glass/blur、Material 3 fallback、列表性能和连接会话导航 |
| v4.3.10 | 98 | 2026-08-14 | BT 重构首个公开大版本：连接生命周期、指令调度、能力映射与状态契约收口 |
| v4.3.9 | 97 | 2026-08-14 | 修正 5A 协议帧长度、CRC 与固定样本边界 |
| v4.3.8 | 96 | 2026-08-14 | BT-4 状态契约并发、能力语义与定向测试校验收紧 |
| v4.3.7 | 95 | 2026-08-14 | BT-4 通用状态、能力契约与定向实机测试包 |
| v4.3.6 | 94 | 2026-08-09 | BT-3 连接运行时状态与生命周期 Job 收敛到 EarbudConnectionManager；2026-08-14 实机门回灌并收口 |
| v4.3.5 | 93 | 2026-08-09 | 修复蓝牙状态并发回退与 RFCOMM 立即拒绝重试，进入 BT_STATE_RETRY_20 定向实机验证 |
| v4.3.4 | 92 | 2026-08-09 | 切换 36 项定向蓝牙回归测试并记录 ANC 修复验证 |
| v4.3.3 | 91 | 2026-08-09 | 修复 ANC 摘戴期间瞬时状态覆盖并加入定向实机测试 |
| v4.3.2 | 90 | 2026-08-08 | BT-1/BT-2 热重连恢复与初始化回归门槛 |
| v4.3.1 | 89 | 2026-08-08 | 修复 RFCOMM 立即失败重试、回归等待归属和初始化阶段终态 |
| v4.3.0 | 88 | 2026-08-08 | 蓝牙传输与连接重构测试版，供 BT-1 实机回归 |
| v4.2.6 | 87 | 2026-07-24 | 恢复控制通道建立后的自动低延迟优先写入，提升 ANC/低延迟在电池同步前的响应优先级 |
| v4.2.5 | 86 | 2026-07-24 | 串行化 FreeBuds 6i 核心 SPP 请求，限制初始化和自动低延迟等待，并允许控制通道连接后立即进入详情页 |
| v4.2.4 | 85 | 2026-07-14 | 以简体中文为默认语言，新增 English / 繁體中文，并覆盖设置、通知、快捷开关、统计页和终端文案 |
| v4.2.3 | 84 | 2026-07-14 | 修复离线日志分享、快捷开关未知状态，并恢复受控并发核心初始化和可靠后台连接发现 |
| v4.2.2 | 83 | 2026-07-07 | 保留后台自动控制连接，并恢复核心优先初始化，避免 EQ/双设备等慢项阻塞 FreeBuds 6i 连接 |
| v4.2.1 | 82 | 2026-07-04 | 对照 OpenFreebuds 修正初始化/dual-connect 判定，并新增系统蓝牙已连接时后台自动建立 App 控制连接 |
| v4.2.0 | 81 | 2026-07-03 | 液态玻璃 UI 重构、EQ 调音模式与双设备连接 MVP |
| v4.1.2 | 80 | 2026-07-03 | 全面代码审计修复：加固 RFCOMM discovery 权限处理与半开 socket 清理，修正扫描页已配对设备连接状态误标，移除设备信息 UI 强制解包 |
| v4.1.1 | 79 | 2026-07-03 | 优化底层 RFCOMM 连接效率：新增 RfcommSocketBridge 缓存反射方法，连接前取消蓝牙 discovery，并统一 SPP/Transport 建连路径与耗时日志 |
| v4.1.0 | 78 | 2026-07-01 | 新增通用 EarbudSession 与 LegacySppEarbudSession 桥接层，DeviceRepository 通过 session 管理连接、属性写入与状态映射，为第三方耳机接入铺路 |
| v3.8.7 | 74 | 2026-07-01 | 拆出 HuaweiHandlerInitializer，迁移 handler 初始化调度和 FreeBuds 6i/7i 核心状态 fast path |
| v3.8.8 | 75 | 2026-07-01 | 新增 EarbudStateMapper，将 Huawei 属性字符串映射为统一 DeviceProps |
| v3.9.0 | 76 | 2026-07-01 | EarbudAdapter 暴露 mapState 边界，DeviceRepository 通过 active adapter 同步 UI 状态 |
| v4.0.0 | 77 | 2026-07-01 | 稳定协议/厂商插件边界：SppDriver 继续拆分 pending response 与 handler 初始化，Repository 通过 adapter 状态映射消费统一 DeviceProps |
| v3.8.6 | 73 | 2026-07-01 | 继续削薄 SppDriver：新增 HuaweiPendingResponseManager，将 pending response 等待/消费/清理逻辑从驱动中抽出 |
| v3.8.5 | 72 | 2026-07-01 | 继续削薄 SppDriver：新增 HuaweiPropertyStore，将 Huawei Handler 属性仓库从驱动中抽出，为后续通用 EarbudState 映射做准备 |
| v3.8.4 | 71 | 2026-07-01 | 继续削薄 SppDriver：新增 HuaweiHandlerRegistry，将 Handler 列表、命令路由、忽略命令、属性路由和失败 Handler 集合从驱动中抽出 |
| v3.8.3 | 70 | 2026-07-01 | 继续 4.0 架构前置重构：新增通用 Protocol 接口与 HuaweiSppProtocol / HuaweiSppFramer，将 5A 包编码和流式解帧从蓝牙连接管理中抽象出来 |
| v3.8.2 | 69 | 2026-07-01 | 继续可插拔架构重构：新增 RfcommSppTransport，将 Android RFCOMM/SPP 原始连接与收发抽象为 Transport 层，为后续协议层迁移做准备 |
| v3.8.1 | 68 | 2026-07-01 | 启动可插拔耳机架构重构：新增 Transport / Adapter 接口骨架，抽出 HuaweiOpenFreebudsAdapter 并将 Handler 注册从 DeviceRepository 委托到厂商 Adapter |
| v3.8.0 | 67 | 2026-07-01 | 新增听音统计页面：按耳机连接时长累计总时长、今日时长、已听天数、连续天数，并提供近 16 周活动热力图入口 |
| v3.7.3 | 66 | 2026-06-30 | 补充 GitHub Pages SEO 元信息、robots.txt 与 sitemap.xml |
| v3.7.2 | 65 | 2026-06-30 | 修复设备页电池卡中单只耳机未连接时缺少斜杠标识的问题 |
| v3.7.1 | 64 | 2026-06-30 | 修复核心状态同步横幅文案，并解析粘包内嵌 SPP 回包以加快进入详情页 |
| v3.7.0 | 63 | 2026-06-30 | 优化电池水位视觉、未连接设备斜杠、详情页返回行为和后台补齐节奏 |
| v3.6.1 | 62 | 2026-06-30 | 修复 CI Gradle wrapper 校验失败，恢复手写 Gradle cache 以保证发布流程 |
| v3.6.0 | 61 | 2026-06-30 | 优化核心状态初始化与详情页进入门槛，重做双耳机 + 电池盒电量展示 |
| v3.5.1 | 60 | 2026-06-30 | 优化 GitHub Actions 构建与发布流程，tag 校验版本后发布并提取 DEVELOPMENT_LOG 作为说明 |
| v3.5.0 | 59 | 2026-06-30 | 新增耳机 + 耳机盒单色矢量图标，并应用到主页、扫描、设备电量和应用详情 |
| v3.4.0 | 58 | 2026-06-30 | 优化手势/设备选项菜单展开动画，重做应用详情卡，并修正 ANC 三模式图标居中与尺寸 |
| v3.3.0 | 57 | 2026-06-30 | 扩展主要用户界面 i18n 覆盖，日志/调试终端保留原始排障文本，并准备 GitHub CI 自动发布 |
| v3.0.6 | 53 | 2026-06-30 | 通用修复 ANC 子选项数字显示，并为其它型号启用温和核心状态优先初始化 |
| v3.0.5 | 52 | 2026-06-30 | 修复 FreeBuds 7i ANC 初始化慢、透传状态数字显示与状态回跳 |
| v3.0.4 | 51 | 2026-06-30 | 显示后台同步提示并暴露待补齐初始化项 |
| v3.0.3 | 50 | 2026-06-30 | 调整 FreeBuds 6i 初始化优先级，优先读取 ANC、低延迟和音质状态 |
| v3.0.2 | 49 | 2026-06-30 | 优化 FreeBuds 6i 连接初始化速度 |
| v3.0.1 | 48 | 2026-06-30 | 修复设置/手势页顶栏透明、初始化状态显示，并移除传统渲染用户选项 |
| v3.0.0 | 47 | 2026-06-29 | 发布 Haze 2.0 液态玻璃 UI 正式主线 |
| v2.13.1 | 46 | 2026-06-29 | 移除最新模式主体白色 tint |
| v2.13.0 | 45 | 2026-06-29 | 增强最新液态玻璃 Flowmix 风格边缘光学 |
| v2.12.3 | 44 | 2026-06-29 | 迁移 Kotlin compilerOptions DSL |
| v2.12.2 | 43 | 2026-06-29 | 修复 Haze 2.0 Kotlin 版本兼容性 |
| v2.12.1 | 42 | 2026-06-29 | 修复 Haze 2.0 构建链兼容性 |
| v2.12.0 | 41 | 2026-06-29 | 迁移 Haze 2.0 架构并新增玻璃渲染方案切换 |
| v2.11.0 | 40 | 2026-06-29 | 重构液态玻璃为背景驱动折射视觉 |
| v2.10.1 | 39 | 2026-06-29 | 新增液态玻璃个性化折叠设置与高级模式 |
| v2.10.0 | 38 | 2026-06-29 | 增强液态玻璃材质系统与可参数化玻璃卡片 |
| v2.9.6 | 37 | 2026-06-29 | 将耳机详情选项从弹窗改为卡片内展开选择 |
| v2.9.5 | 36 | 2026-06-29 | 修复手势页液态玻璃适配缺失 Alignment import 导致的 CI 编译错误 |
| v2.9.4 | 35 | 2026-06-29 | 补全手势与设置页面液态玻璃卡片适配 |
| v2.9.3 | 34 | 2026-06-29 | 修正传统/液态玻璃模式卡片边界与玻璃双层结构 |
| v2.9.2 | 33 | 2026-06-29 | 修复液态玻璃卡片双层色块并扩展卡片适配 |
| v2.9.1 | 32 | 2026-06-29 | 修复 v2.9.0 Haze 组件 CI 编译错误 |
| v2.9.0 | 31 | 2026-06-29 | Glass/Haze 基础组件与 Home/Device 双模式改造 |
| v2.8.3 | 30 | 2026-06-29 | 修复自动暂停写入确认 / 暂停 FreeBuds 7i 自动暂停选项 |
| v2.8.2 | 29 | 2026-06-29 | 修复耳机蓝牙断开后应用连接状态未更新 |
| v2.8.1 | 28 | 2026-06-29 | 修复初始化 ANC 状态未实时同步到 UI / 通知 / 快捷开关 |
| v2.8.0 | 27 | 2026-06-29 | UI 导航系统重构 / 展示模式基础 / 传统与液态玻璃模式切换入口 |
| v2.7.7 | 26 | 2026-06-29 | FreeBuds 7i 临时保守能力表 / 标记后续大版本多型号适配计划 |
| v2.7.6 | 25 | 2026-06-29 | 修复首次切到降噪旧状态包乱跳 / 恢复充电盒 100% 电量显示 / 增加一键版本更新脚本 |
| v2.7.2 | 21 | 2026-06-29 | ANC/主题长条胶囊滑块 / ANC 圆形图标滑块 / 全局壁纸背景修复 |
| v2.7.1 | 20 | 2026-06-29 | 常驻通知保活 / 电池与实时状态显示 / 打开应用/系统自启动自动连接已保存耳机 / 清理旧 ACL 自动连接残留 |
| v2.7.0 | 19 | 2026-06-28 | 源头乐观更新 / Tile 响应式刷新 / NotificationChannel 适配 / CI 自动发版 |
| v2.6.0 | 18 | 2026-06-28 | 导航重构 / ANC 乐观更新 / 低延迟可配置 / 已保存设备修复 |
| v2.5.0 | 17 | 2026-06-28 | Haze 移除 / Material3 分段控件 / QuickSettings Tile ANC 切换 / 重试策略优化 |
| v2.4.0 | 15 | 2026-06-28 | 三阶段大更新
| v2.4.0 | 15 | 2026-06-28 | 三阶段大更新：自适应轮询 / 三态主题+壁纸 / 通知栏ANC快切 / 日志控制 / ANC模糊滑块 |
| v2.3.0 | 14 | 2026-06-28 | UI/UX 全面修复：3s 轮询 / ANC 即时刷新 / 手势子页 / 已保存主页 / 全中文 / 五屏导航 |
| v2.2.0 | 13 | 2026-06-28 | Compose UI 完整重构 |
| v2.1.3 | 12 | 2026-06-28 | 交错并行初始化 + 自动打印属性 |
| v2.1.2 | 11 | 2026-06-28 | 三轮并行迭代 + ANC 被动通知 + 电池修复 |
| v2.1.0 | 10 | 2026-06-28 | 15 Handler 移植 + 属性存储系统 |
| v2.0.0-stable.1 | 9 | 2026-06-27 | 回归 RFCOMM SPP |
| v1.7.3 | 8 | (archived) | 旧版 |

## 规格

- versionCode：整数，每次发布 +1
- versionName：语义化版本号
