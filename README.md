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

> **当前版本：v2.12.2**
> 合并完成 Glass / Haze 基础组件与 Home / Device 双模式改造；传统展示保持可用，液态玻璃模式会在无壁纸时引导用户先选择壁纸（非强制）。

---

## 这是什么？

fxxkHilife 是一个第三方、开源、离线的耳机控制面板，目标是让 HUAWEI FreeBuds / HONOR Earbuds 用户在不登录账号、不接入厂商生态、不上传数据的情况下，依然能控制耳机的常用功能。

它直接使用经典蓝牙 **SPP / RFCOMM** 与耳机通信，协议实现参考了 [OpenFreebuds](https://github.com/melianmiko/OpenFreebuds) 的逆向工作，并在 Android / Jetpack Compose 上实现了完整的移动端界面。

这个项目仍在快速迭代中。现在我正在招募更多型号的测试者，帮助确认不同耳机型号上的协议兼容性。

---

## 主要能力

- **双展示模式**：传统 Material3 展示保持稳定可用；液态玻璃模式基于 Haze，已在 Home / Device 页面启用玻璃卡片、背景模糊、虹彩边缘与高光质感。

- **设备连接与自动连接**：扫描 HUAWEI / HONOR 耳机，自动保存设备，打开应用/系统自启动/常驻服务场景下自动连接；自动连接前会确认耳机已与手机系统蓝牙连接。
- **ANC / 透传 / 关闭**：应用内长条胶囊滑块、Quick Settings Tile、常驻通知三按钮均可切换 ANC 模式。
- **低延迟 / 游戏模式**：支持手动开关；也可在设置中启用自动低延迟，连接初始化完成后立即尝试开启，失败时每 500ms 重试，最多 30s。
- **电量与佩戴状态**：左右耳 / 充电盒电量、充电状态、佩戴检测、摘下自动暂停。
- **手势与音频偏好**：双击、三击、长按、滑动手势配置；音质优先 / 连接优先；语音提示语言读取与写入能力。
- **常驻通知与日志**：通知显示 ANC、低延迟、音质、电量、听音时长；调试终端可查看 SPP 原始日志、导出日志、写入属性。
- **界面体验**：Material3 + Jetpack Compose，深色/浅色/跟随系统，全局壁纸与作用域控制，中文选项映射。

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
| HUAWEI FreeBuds 7i | 临时保守适配，待完整适配 | v2.12.2 起保留保守能力表以降低初始化压力，并暂时隐藏未确认可用的自动暂停选项；完整适配会在下一轮大版本“更多型号/更多厂商耳机适配”中与测试者继续推进 |
| HUAWEI FreeBuds 5i | 已适配能力表，待更多实测 | 支持 ANC、ANC 强度、手势、音质偏好、低延迟等能力 |
| HUAWEI FreeBuds 4i / HONOR Earbuds 2 / 2 Lite / SE | 已适配能力表，待更多实测 | 偏基础能力：ANC、电量、佩戴检测、双击/长按、自动暂停等 |
| HUAWEI FreeBuds Pro | 已适配能力表，待更多实测 | ANC、语音增强、滑动/长按、双设备等能力存在型号差异 |
| HUAWEI FreeBuds Pro 2 | 已适配能力表，待更多实测 | ANC、手势、音质偏好、EQ、双设备等能力待验证 |
| HUAWEI FreeBuds Pro 3 / Pro 4 / FreeClip | 已适配能力表，待更多实测 | 新型号能力较多，尤其需要测试反馈 |
| HUAWEI FreeBuds SE / SE 2 / SE 4 | 已适配能力表，待更多实测 | 不同 SE 型号能力差异较大 |
| HUAWEI FreeBuds Studio | 已适配能力表，待更多实测 | 头戴式设备，电量与佩戴能力和 TWS 不同 |
| HUAWEI FreeLace Pro / Pro 2 | 已适配能力表，待更多实测 | 颈挂式设备，部分 TWS 能力不适用 |
| 其他 HUAWEI / HONOR Earbuds | 可尝试扫描连接 | 未命中型号时会注册通用 Handler，欢迎提交日志 |

---

## 正在招募测试者

如果你有上表中的任意型号，尤其是 **FreeBuds 5i / Pro 系列 / SE 系列 / FreeClip / FreeLace**，欢迎参与测试。

反馈时请尽量提供：

1. 耳机型号与固件版本
2. 手机型号、Android 版本 / ROM
3. App 版本号（v2.12.2 起日志会自动显示）
4. 哪些功能正常，哪些功能异常
5. 从应用内“分享日志”导出的日志文件
6. 如果是连接问题，请说明系统蓝牙里是否已经显示耳机已连接

反馈入口：

- GitHub Issues：<https://github.com/ct-yx/fxxkHilife/issues>
- Release 页面：<https://github.com/ct-yx/fxxkHilife/releases>

---

## 已知限制

- 目前底层控制依赖经典蓝牙 SPP；部分手机 ROM 可能限制后台蓝牙行为。
- 不同耳机固件对同一命令的响应可能不同，未实测型号可能出现部分 Handler 初始化失败。
- 电池、ANC、手势等能力会按型号过滤，但能力表仍需要更多真实设备校准。
- EQ Preset / Custom EQ、双设备连接等能力已有协议入口或能力表记录，但尚未完整实现 UI 与稳定写入流程。
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
