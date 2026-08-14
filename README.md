<p align="center">
  <img src="./docs/icon.png" alt="fxxkHilife Icon" width="128" height="128">
</p>

<h1 align="center">fxxkHilife</h1>

<p align="center">
  <b>面向 HUAWEI FreeBuds / HONOR Earbuds 的开源离线控制 App</b><br>
  <sub>无需华为账号 · 无广告 · 不依赖云服务 · 通过 Bluetooth SPP 直接控制耳机</sub>
</p>

<p align="center">
  <a href="https://ct-yx.github.io/fxxkHilife/"><b>项目主页</b></a> ·
  <a href="./README_EN.md">English</a> ·
  <a href="https://github.com/ct-yx/fxxkHilife/releases/latest">下载最新版</a> ·
  <a href="https://github.com/ct-yx/fxxkHilife/issues">反馈问题 / 参与测试</a>
</p>

> **当前开发包：v4.4.0（versionCode 99）**
> **当前公开发布：v4.3.10（versionCode 98）**；BT 重构后的首个大版本已收口，v4.4.0 是 UI 错误报告修复测试包，等待 GitHub Actions 与定向实测。

---

## 项目主页

完整介绍、下载入口、演示素材和开发日志见 GitHub Pages：

<https://ct-yx.github.io/fxxkHilife/>

---

## v4.4.0：UI 错误报告修复测试包

本包不修改 BT-0 至 BT-4。它针对 `UI_ERROR_FIX_ALPHA05` 构建标签修复三类已确认问题：

- 真实 typed `hazeGlass`/`hazeBlur`、`GlassStyle`/`GlassOptics`；壁纸未加载、无效或不存在时强制使用 Material 3，不用透明空 surface 冒充玻璃。
- 保存设备、扫描设备和双连接设备使用地址稳定 key；长列表不逐行创建 effect，展开内容只保留一种尺寸动画。
- 详情页自动进入绑定设备地址与连接 `attemptId`；同一会话返回后不自动重开，Home/Scan 点击设备都显式进入详情页。
- UI Flow 使用生命周期感知收集；Home 刷新只跟随控制目标地址，扫描结果走明确的用户连接入口。

当前只完成源码与静态契约收口，尚未把 GitHub Actions、截图/运行诊断和定向交互报告当作 UI 完成证据。测试只覆盖玻璃渲染、无背景 fallback、列表滚动和连接会话导航，不重复 BT A-F/36/100 项矩阵。

---

## v4.3.10：BT 重构首个大版本

这次发布先收口蓝牙，不把尚未开始的 UI-0/UI-1 混入版本承诺。BT-0 至 BT-4 已完成，应用继续使用经典蓝牙 SPP / RFCOMM，页面只消费稳定的状态与能力边界。

### 本版本完成

- 统一 `RfcommSppTransport`、`ProtocolSession`、`CommandClient/Scheduler` 与 `EarbudConnectionManager` 的生产路径，避免多个入口重复建连。
- 按同一 `attempt` 管理连接、初始化、轮询和状态投影；失败连接主动清理 session，手动断开、ACL 断开会先取消工作并 flush 听音统计。
- 固定 endpoint/channel/source 的来源记录，收紧型号能力表和 Handler 注册；未知型号不再强行注册全部 Handler。
- ANC 以权威读回为状态来源，低延迟使用写入、ACK/读回和最终状态同步；通用 `EarbudSnapshot` 同时提供能力、控制通道、pending/failed 和兼容 `DeviceProps` 投影。
- 修复 Huawei 5A 双字节长度、完整帧边界、payload 参数边界和可选 CRC 校验，避免大帧或粘包被错误截断。

### 验证证据

- Debug/Release 各 94 个 JVM 测试通过，均为 `0 failures / 0 errors / 0 skipped`；Debug/Release 构建和 `diff-check` 通过。
- `BT4_STATE_CONTRACT_5` 在 HUAWEI FreeBuds 6i、Android API 36、固件 `HarmonyOS 6.0.0.292(F001H003C00)` 上 5/5 通过：`reportValid=true`、`overall=PASS`，endpoint=`rfcomm-channel=1`，source=`VerifiedModelConfig`，契约检查 P50/P95=`2549/3573ms`。
- 上述实机证据只覆盖该设备、系统、固件和测试 profile；其他型号仍需各自的定向日志，不能把 5 轮状态契约结果扩展为所有指令已验证。

### 下一阶段

UI-0 将先建立页面、状态、交互、设置 key 和美术资源基线，再按 UI-1 至 UI-5 重构。现有美术资源保留；Haze 2.0 只作为待验证的 surface 渲染层，下一阶段会以实际渲染效果、降级路径和截图验收为准，不把“声明依赖”当作 UI 完成证据。

---

## 这是什么？

fxxkHilife 是一个第三方、开源、离线的耳机控制面板，目标是让 HUAWEI FreeBuds / HONOR Earbuds 用户在不登录账号、不接入厂商生态、不上传数据的情况下，依然能控制耳机的常用功能。

它直接使用经典蓝牙 **SPP / RFCOMM** 与耳机通信，协议实现参考了 [OpenFreebuds](https://github.com/melianmiko/OpenFreebuds) 的逆向工作，并在 Android / Jetpack Compose 上实现了完整的移动端界面。

这个项目仍在快速迭代中。现在我正在招募更多型号的测试者，帮助确认不同耳机型号上的协议兼容性。

---

## 主要能力

- **UI 重构测试线**：v4.4.0 开始按 UI-ERR-001/002/003 和 `UI_ERROR_FIX_ALPHA05` 继续收口；现有美术资源保留，最终状态以截图、运行诊断、无障碍和交互报告为准。

- **设备连接与自动连接**：扫描 HUAWEI / HONOR 耳机，自动保存设备；常驻服务检测到已保存耳机与手机系统蓝牙连接后，会按退避策略自动建立 App SPP 控制连接。
- **ANC / 透传 / 关闭**：应用内长条胶囊滑块、Quick Settings Tile、常驻通知三按钮均可切换 ANC 模式；在第三方 Android 手机上也可以不打开应用，通过系统快捷开关直接循环切换 ANC。
- **低延迟 / 游戏模式**：支持手动开关；也可在设置中启用自动低延迟，耳机控制通道建立后即优先写入，随后回读确认；失败仅在 10 秒窗口内有限重试，适合游戏/视频场景。
- **电量与佩戴状态**：左右耳 / 充电盒电量、充电状态、佩戴检测、摘下自动暂停。
- **手势与音频偏好**：双击、三击、长按、滑动手势配置；音质优先 / 连接优先；EQ 调音预设读取、展示、写入和回读同步；语音提示语言读取与写入能力。
- **双设备连接 MVP**：读取耳机侧双连开关与已配对设备列表，展示首选、播放中、连接和自动连接状态；支持切换双连开关、首选设备、连接/断开与自动连接标记。Android 侧仍保持单 SPP 控制通道，并在 UI 中清晰显示系统蓝牙连接与控制通道连接状态。
- **常驻通知与日志**：通知显示 ANC、低延迟、音质、电量、听音时长；调试终端可查看 SPP 原始日志、导出日志、写入属性。
- **界面体验**：Material3 + Jetpack Compose，深色/浅色/跟随系统，全局壁纸与作用域控制，统一 i18n 文案入口与中文选项映射。

---

## 功能展示

> 以下素材为 FreeBuds 6i 实机录制，更多展示见 [项目主页](https://ct-yx.github.io/fxxkHilife/)。

| 展示 | 文件 |
|------|------|
| 初次进入应用：权限引导、扫描与主界面 | [`docs/demo_first_launch.mp4`](./docs/demo_first_launch.mp4) |
| 常驻通知信息显示：ANC、低延迟、音质、电量、听音时长 | [`docs/notification_status.jpg`](./docs/notification_status.jpg) |
| 重新连接手机后自动开启低延迟 / 游戏模式 | [`docs/demo_auto_low_latency.mp4`](./docs/demo_auto_low_latency.mp4) |
| 不打开应用切换 ANC 模式 | [`docs/demo_anc_without_app.mp4`](./docs/demo_anc_without_app.mp4) |

---

## 已支持 / 计划验证的设备

> “支持”表示项目中已有型号识别与能力表，实际功能会因耳机固件、地区版本、系统蓝牙栈而有差异。
> **目前实测重点：FreeBuds 6i。其他型号非常需要测试者反馈。**

| 设备 | 当前状态 | 说明 |
|------|----------|------|
| HUAWEI FreeBuds 6i | 已实测 | 主要开发与验证设备，ANC/手势/电量/低延迟/音质偏好等功能持续调优中 |
| HUAWEI FreeBuds 7i | 临时保守适配，待完整适配 | 保留保守能力表以降低初始化压力，并隐藏未确认可用的自动暂停选项；完整适配需要更多实机日志 |
| HUAWEI FreeBuds 5i | 已适配能力表，待更多实测 | 支持 ANC、ANC 强度、手势、音质偏好、低延迟等能力 |
| HUAWEI FreeBuds 4i / HONOR Earbuds 2 / 2 Lite / SE | 已适配能力表，待更多实测 | 偏基础能力：ANC、电量、佩戴检测、双击/长按、自动暂停等 |
| HUAWEI FreeBuds Pro | 已适配能力表，待更多实测 | ANC、语音增强、滑动/长按、双设备等能力存在型号差异 |
| HUAWEI FreeBuds Pro 2 | 已适配能力表，待更多实测 | ANC、手势、音质偏好、EQ、双设备等能力待验证 |
| HUAWEI FreeBuds Pro 3 / Pro 4 / FreeClip | 已适配能力表，待更多实测 | 新型号能力较多，尤其需要测试反馈 |
| HUAWEI FreeBuds SE / SE 2 / SE 4 | 已适配能力表，待更多实测 | 不同 SE 型号能力差异较大 |
| HUAWEI FreeBuds Studio | 已适配能力表，待更多实测 | 头戴式设备，电量与佩戴能力和 TWS 不同 |
| HUAWEI FreeLace Pro / Pro 2 | 已适配能力表，待更多实测 | 颈挂式设备，部分 TWS 能力不适用 |
| 其他 HUAWEI / HONOR Earbuds | 可尝试扫描 | 未知型号不会强行注册全部 Handler；欢迎提交型号识别、能力和连接日志 |

---

## 正在招募测试者

如果你有上表中的任意型号，尤其是 **FreeBuds 5i / Pro 系列 / SE 系列 / FreeClip / FreeLace**，欢迎参与测试。

反馈时请尽量提供：

1. 耳机型号与固件版本
2. 手机型号、Android 版本 / ROM
3. App 版本号（v4.2.0 起日志会自动显示）
4. 哪些功能正常，哪些功能异常
5. 从应用内“分享日志”导出的日志文件
6. 如果是连接问题，请说明系统蓝牙里是否已经显示耳机已连接

反馈入口：

- GitHub Issues：<https://github.com/ct-yx/fxxkHilife/issues>
- Release 页面：<https://github.com/ct-yx/fxxkHilife/releases>

---

## 已知限制

- 目前底层控制依赖经典蓝牙 SPP；部分手机 ROM 可能限制后台蓝牙行为。
- 不同耳机固件对同一命令的响应可能不同；未实测型号会按保守能力表处理，部分功能需要单独验证。
- 电池、ANC、手势等能力会按型号过滤，但能力表仍需要更多真实设备校准。
- EQ Preset 已支持状态读取、选项展示、内置/已验证兼容预设写入和回读同步；Custom EQ 目前只展示耳机回报的自定义频段和保存状态，不写入未知自定义 payload。
- 双设备连接为 MVP：耳机侧双连状态可读写，但 Android 控制通道仍以单 active SPP session 为稳定路径；如手机蓝牙栈不允许同时稳定维护多个 SPP，应用会降级为系统连接状态展示 + 当前设备控制。
- 本项目不是官方 App，不保证覆盖官方 App 的所有功能。

---

## 构建

```bash
git clone https://github.com/ct-yx/fxxkHilife.git
cd fxxkHilife
./gradlew :app:assembleDebug
```

Release APK 由 GitHub CI 自动构建发布。

---

## 免责声明

本项目仅供学习与个人研究使用，禁止商业用途。
使用本项目产生的连接异常、设置异常或设备兼容性问题请自行承担风险。项目与 HUAWEI / HONOR 官方无关。
