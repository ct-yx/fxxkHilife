<p align="center">
  <img src="./docs/icon.png" alt="fxxkHilife Icon" width="128" height="128">
</p>

<h1 align="center">fxxkHilife</h1>

<p align="center">
  <b>HUAWEI FreeBuds / HONOR Earbuds 的轻量离线控制 App</b>
</p>

> 通过经典蓝牙 SPP 直接控制耳机，无需登录华为账号、无广告、完全离线。

> **[English](./README_EN.md)** | 中文

本项目是 [OpenFreebuds](https://github.com/melianmiko/OpenFreebuds)（MelianMiko）的 Android 实现，感谢原作者出色的协议逆向工作。

---

## 为什么要做这个项目？

华为官方的「智慧生活 / HiLife」App 越来越让人难以忍受：
- 强制登录
- 各种广告、推荐和 telemetry
- 功能臃肿，启动慢

而很多用户其实只需要**电量查看、ANC 切换、EQ 调节、手势设置**这些基础功能。

fxxkHilife 就是为了解决这个问题而写的 —— 一个专注、干净、尊重用户的替代品。

---

## 支持的设备

目前支持以下型号（不同型号支持的功能有差异）：

| 型号                  | 主要支持功能 |
|-----------------------|--------------|
| FreeBuds 4i           | 电池、ANC、手势、自动暂停、EQ |
| FreeBuds 5i / 6i      | 电池、ANC、手势、自动暂停、EQ、低延迟、音质偏好 |
| FreeBuds Pro          | 电池、ANC、手势、EQ、双设备连接、语音语言 |
| FreeBuds Pro 2        | 电池、ANC、手势、EQ、音质偏好、双设备连接、语音语言 |
| FreeBuds Pro 3        | 电池、ANC、手势、EQ、低延迟、音质偏好、双设备连接、语音语言 |
| FreeLace Pro 2        | 电池、ANC、EQ、低延迟、音质偏好、双设备连接、语音语言 |

具体功能支持以 `DeviceProfile.kt` 为准。

---

## 主要功能

- 实时电池显示（左耳 / 右耳 / 充电盒 + 充电状态）
- ANC 模式切换 + 等级调节（含 Dynamic）
- 游戏低延迟模式（支持 Fixed On 自动保持）
- 音质偏好切换
- 均衡器预设
- 手势自定义（双击、三击、长按、滑动）
- 双设备连接管理
- 自动暂停（佩戴检测联动）
- 语音播报语言切换
- 设备固件 / 序列号等信息查看
- **快速设置磁贴**（ANC 一键切换）
- **前台常驻通知**（显示状态 + 累计佩戴时长）
- WorkManager 后台保活 + 断线自动重连
- 完善的双路调试日志（支持一键导出）

---

## 技术亮点

- **协议层**：纯 Kotlin 实现的 SPP (RFCOMM) 客户端，带 CRC16 校验、重试机制和超时处理
- **架构设计**：`SppClient` → `DeviceManager` → **可插拔 Handler** 体系
  - 每个功能独立一个 Handler（如 `AncModeHandler`、`GestureHandler`、`DualConnectHandler` 等）
  - 根据 `DeviceProfile` 动态加载对应功能
  - 新增功能或支持新机型非常方便
- UI：Jetpack Compose + Material You
- 状态管理：StateFlow + SharedFlow
- 持久化：DataStore
- 后台保活：WorkManager + Foreground Service

整个项目代码量不算大，但结构清晰，适合学习 Android 蓝牙 SPP 开发和模块化架构。

---

## 构建与使用

```bash
git clone https://github.com/ct-yx/fxxkHilife.git
cd fxxkHilife
./gradlew :app:assembleDebug
```

安装后授予蓝牙权限（Android 12+ 需要 `BLUETOOTH_CONNECT`），选择已配对的 FreeBuds 设备即可使用。

**注意**：
- 首次连接有时需要多尝试几次
- 部分功能（如双设备连接）需要耳机本身支持

---

## 项目状态

当前版本：**v1.6.6**（测试版本）

核心功能已基本完成，重点做了以下改进：
- 连接健壮性（Mutex + 重试 + 状态修正）
- 日志系统（Logcat + 文件轮转 + 一键分享）
- 代码质量（消除大量 `!!` 和静默异常捕获）
- 后台保活与通知体验
- **中英双语全覆盖**：MainScreen、SettingsScreen、BatteryCard 等所有 UI 界面已全部改用 stringResource，无硬编码英文残留
- **权限校验重写**：运行时动态检查 BLUETOOTH_CONNECT + BLUETOOTH_SCAN + ACCESS_FINE_LOCATION + POST_NOTIFICATIONS（Android 13+），权限提示界面完整展示所有权限说明
- **协议层对齐上游 OpenFreebuds**：全量 6 区代码审查完成，修复 2 项 P0 协议 Bug（ANC 命令 ID、充电状态缺失）、修正 SoundQuality param 位置、消除 9 项代码问题
- **手势全面完成**：四种手势（双击、三击、长按、滑动）全部完善，协议参数与上游完全对齐
- **ANC 三模式修复**：降噪/关闭/通透模式命令 payload 构造与上游对齐，Awareness 等级映射修正
- **后台保活增强**：WorkManager 定时保活 + CompanionDeviceManager 辅助自动重连 + 电量优化引导
- **权限错误提示优化**：权限拒绝时弹出中文/英文 Toast，连续拒绝后引导跳转系统设置
- **应用图标**：去除黑底，生成完整 mipmap 密度适配

### ⚠️ 已知问题（v1.6.6）

当前版本处于**功能验证阶段**，以下功能已实现代码但**在部分设备上存在异常**，欢迎测试反馈：

| 功能 | 状态 | 说明 |
|------|------|------|
| ANC 模式切换（应用内） | ✅ **正常** | 降噪 / 关闭 / 通透可正常切换 |
| ANC 快捷开关（Tile） | ✅ **已修复 v1.6.6** | 降噪/关闭/通透三模式循环切换 |
| **低延迟模式** | ❌ **异常** | 应用内切换可能不生效 |
| **EQ 预设** | ❌ **异常** | 切换预设后耳机无反应 |
| **手势设置** | ❌ **异常** | 双击/三击/长按/滑动设置后不生效 |
| **音质偏好** | ❌ **异常** | 稳定/质量模式切换无效果 |
| **双设备连接** | ❌ **异常** | 开关功能可能未正确同步到耳机 |
| 电池显示 | ✅ **正常** | 左右耳 + 充电盒电量 |
| 设备信息 | ✅ **正常** | 固件版本、序列号等 |
| 日志导出 | ✅ **正常** | 一键分享调试日志 |
| 主题切换 | ✅ **正常** | 浅色/深色/跟随系统 |

> 以上异常极大概率是**协议命令参数或时序问题**，欢迎提交 Issue 或 PR 协助修复。

详细开发记录见 [DEVELOPMENT_LOG.md](./DEVELOPMENT_LOG.md)

---

## License

仅供**学习与个人研究使用**，禁止商业用途。

协议实现参考自 [melianmiko/OpenFreebuds](https://github.com/melianmiko/OpenFreebuds) 项目。

---

## 致谢

- [MelianMiko](https://github.com/melianmiko) 的 OpenFreebuds 项目，提供了完整的协议参考
- 所有测试反馈和 issue 的用户

---

如果有功能建议、bug 反馈或者想支持新机型，欢迎提 Issue 或 PR。
