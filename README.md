# fxxkHilife — HUAWEI FreeBuds Android Companion

> 轻量、离线、免登录 — 替代华为智慧生活的 FreeBuds 蓝牙 SPP 控制应用

**fxxkHilife** 是一个基于 **Kotlin + Jetpack Compose + Material You** 的 Android 原生应用，通过蓝牙 SPP 协议（RFCOMM）直接控制 HUAWEI FreeBuds / HONOR Earbuds 系列耳机。无需登录华为账号、无广告、完全离线运行。

协议层核心逻辑移植自 [OpenFreebuds](https://github.com/ct-yx/OpenFreebuds)（MelianMiko 的反向工程桌面项目）。

---

## 📋 目录

- [核心功能](#-核心功能)
- [技术栈](#-技术栈)
- [项目结构](#-项目结构)
- [版本号规则](#-版本号规则)
- [开发进度](#-开发进度)
- [快速开始](#-快速开始)
- [设计理念](#-设计理念)
- [相关资源](#-相关资源)

---

## 🎯 核心功能

### ✅ 已实现
| 功能 | 状态 | 说明 |
|------|------|------|
| 蓝牙 SPP 连接 | ✅ | RFCOMM socket 连接 FreeBuds 系列耳机 |
| 设备搜索/选择 | ✅ | 扫描已配对设备、连接/断开切换、自动重连 |
| 连接状态持久化 | ✅ | 断开后保留设备名灰色显示 + Reconnect 按钮 |
| 电池显示 | ✅ | 左耳/右耳/充电盒电量（需 Handler 实现协议） |
| ANC 模式切换 | ✅ | Off / Noise Cancel / Awareness 三档 |
| 游戏低延迟模式 | ✅ | Off / On / Fixed On（启动自动开启） |
| 音质偏好 | ✅ | Stable（稳定优先）/ Quality（音质优先） |
| 均衡器预设 | ✅ | 多预设选择（需 Handler 实现协议） |
| 手势配置 | ✅ | 双击左/右耳动作选择（需 Handler 实现协议） |
| 双设备连接 | ✅ | Dual Connect 开关（需 Handler 实现协议） |
| 设备信息 | ✅ | 固件版本、序列号、硬件版本（需 Handler 实现协议） |
| 调试日志 | ✅ | Logcat + 文件双路日志，支持分享 |
| ANC 快速设置磁贴 | ✅ | 下拉快速切换 ANC 模式 |
| 状态通知 | ✅ | 前台服务显示连接状态、电量、聆听时长 |
| 按钮防抖 | ✅ | 防止多次点击导致闪退/并发连接 |

### 🔄 待完善
| 功能 | 说明 |
|------|------|
| Handler 协议实现 | 8 个 Handler 框架已搭好，需补充华为 SPP 写命令的协议字节 |
| 开机自启 | 目前仅基于 DataStore 的 Auto Connect 配置 |
| 通知持久化 | StatusNotificationService 需优化监听时间计算 |
| 主题自定义 | 支持 Frosted / Liquid Glass 毛玻璃效果（Haze 库已集成但不稳定已移除） |

---

## 🛠 技术栈

| 层 | 选型 |
|---|------|
| 语言 | Kotlin 100% |
| UI 框架 | Jetpack Compose + Material 3 (Material You) |
| 主题 | Dynamic Color (Monet)，支持 Android 12+ 壁纸取色 |
| 蓝牙 | Android BluetoothSocket / SPP RFCOMM (UUID `00001101-0000-1000-8000-00805f9b34fb`) |
| 并发 | Kotlin Coroutines + Flow + Mutex |
| 架构 | Repository 模式：`SppClient` → `DeviceManager` → `UI StateFlow` |
| DI | 手动 DI（保持简单，无 Hilt/Koin） |
| 持久化 | DataStore Preferences |
| 最低 SDK | 26 (Android 8.0) |
| 目标 SDK | 35 |

---

## 📁 项目结构

```
fxxkHilife/
├── app/
│   └── src/main/java/com/freebuds/controller/
│       ├── MainActivity.kt              # 入口 Activity + 权限处理 + 自动连接
│       ├── FreeBudsApp.kt               # Application 类，初始化 DI + 日志
│       ├── bluetooth/
│       │   ├── SppClient.kt             # RFCOMM socket 连接/读写/事件
│       │   ├── SppPackage.kt            # 华为 SPP 二进制协议解析
│       │   ├── SppCommand.kt            # 所有已知命令 ID
│       │   └── ConnectionEvent.kt       # 连接事件（Connected/Disconnected/Error/PackageReceived）
│       ├── device/
│       │   ├── DeviceManager.kt         # 核心：设备发现 → 连接 → handler 管理 → 状态分发
│       │   ├── DeviceProfile.kt         # 设备型号特征配置（支持哪些 Handler）
│       │   ├── DeviceState.kt           # 所有实时状态的数据类
│       │   └── handler/
│       │       ├── Handler.kt           # 接口：init / onPackage / applyToState / setProperty
│       │       ├── BatteryHandler.kt
│       │       ├── AncModeHandler.kt
│       │       ├── LowLatencyHandler.kt
│       │       ├── SoundQualityHandler.kt
│       │       ├── EqPresetHandler.kt
│       │       ├── GestureHandler.kt
│       │       ├── DeviceInfoHandler.kt
│       │       └── DualConnectHandler.kt
│       ├── ui/
│       │   ├── theme/Theme.kt / Color.kt / Type.kt
│       │   ├── screen/
│       │   │   ├── MainScreen.kt        # 主页：连接卡片 + 控制面板 + 设备选择
│       │   │   ├── SettingsScreen.kt    # 设置页：主题/连接/调试/关于
│       │   │   └── PermissionPromptScreen.kt
│       │   ├── component/
│       │   │   ├── BatteryCard.kt
│       │   │   ├── EqualizerPanel.kt
│       │   │   └── GestureEditor.kt
│       │   └── navigation/Navigation.kt
│       ├── data/
│       │   └── PreferencesRepository.kt  # DataStore 持久化
│       ├── util/
│       │   ├── Crc16.kt                  # CRC16-XMODEM 校验
│       │   └── DebugLogger.kt            # Logcat + 文件双路日志
│       └── service/
│           ├── AncQuickTileService.kt     # 快速设置磁贴
│           └── StatusNotificationService.kt  # 前台服务通知
├── OpenFreebuds/                         # Python 桌面端协议参考（原始反向工程）
├── gradle/
│   ├── libs.versions.toml                # 依赖版本管理
│   └── wrapper/
├── PROJECT_CORE.md                       # 核心架构文档
├── DEVELOPMENT_LOG.md                    # 开发日志
├── README.init.md                        # 原始模板 README（备份）
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🔖 版本号规则

格式：`MAJOR.MINOR-fixN`

| 部分 | 含义 | 何时递增 |
|------|------|----------|
| MAJOR | 重大功能更新/重构 | +1 当新增重大能力（如新设备家族支持、UI 重构） |
| MINOR | 小功能增删改 | +1 当添加/改进小功能（如新控件、新设置项） |
| fixN | 第 N 次修复 | fix1 / fix2 / fix3 … 每完成一轮 Bug 修复 |

**当前版本：** `1.0.0-fix3`

---

## 📈 开发进度

### 🏗 Stage 1 — 基础蓝牙连接 ✅
- [x] SPP 客户端（socket 创建、连接、读写）
- [x] 华为 SPP 二进制协议解析
- [x] 设备配对扫描 + 选择
- [x] 连接/断开状态管理

### 🧩 Stage 2 — Handler 框架 + 基础 UI ✅
- [x] Handler 接口设计（设备无关的可插拔协议单元）
- [x] 8 个 Handler 框架（Battery / ANC / LowLatency / SoundQuality / EQ / Gesture / DeviceInfo / DualConnect）
- [x] ConnectionCard（连接状态显示 + Reconnect）
- [x] QuickControlsCard（ANC / 低延迟 / 音质）
- [x] EqualizerPanel / GestureSettingsCard

### 🎨 Stage 3 — 调试 & 体验优化 ✅
- [x] DebugLogger 双路日志（Logcat + 文件）
- [x] 日志分享（FileProvider）
- [x] 按钮防抖（btnLock）
- [x] 桌面图标显示应用名
- [x] 版本号规则文档化
- [x] 自动连接不冲突 UI

### 📲 Stage 4 — 系统集成（基本完成）
- [x] ANC 快速设置磁贴
- [x] 前台服务 + 状态通知
- [x] 权限管理

### 🔧 Stage 5 — 协议补完（待完成）
- [ ] `BatteryHandler` — 补全电池订阅 + 读取写命令
- [ ] `AncModeHandler` — 补全模式切换写命令
- [ ] `LowLatencyHandler` — 补全低延迟开关写命令
- [ ] `SoundQualityHandler` — 补全音质偏好写命令
- [ ] `EqPresetHandler` — 补全 EQ 预设读写命令
- [ ] `GestureHandler` — 补全手势配置读写命令
- [ ] `DeviceInfoHandler` — 补全设备信息读命令
- [ ] `DualConnectHandler` — 补全双设备连接写命令

---

## 🚀 快速开始

### 环境要求
- JDK 17+
- Android SDK 35（已配置）
- Gradle Wrapper（已包含）

### 构建并安装

```bash
# Debug APK
./gradlew :app:assembleDebug

# APK 路径
app/build/outputs/apk/debug/app-debug.apk

# 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 首次使用
1. 安装后打开 → 授权 `BLUETOOTH_CONNECT` + `BLUETOOTH_SCAN` 权限
2. 点击 "Connect" → 选择已配对的 FreeBuds 设备
3. 连接成功后自动显示控制面板

---

## 💡 设计理念

- **无登录、无广告** — 完全离线运行，不收集任何数据
- **轻量** — APK < 2MB，无第三方 SDK/分析库
- **SPP 优先** — 使用经典蓝牙 RFCOMM 而非 BLE，兼容性更好
- **可插拔 Handler** — 每种设备功能（电池、ANC、EQ 等）由一个独立的 Handler 实现，便于扩展新设备型号
- **Material You 第一** — 动态取色、圆角、沉浸式

---

## 📚 相关资源

- [OpenFreebuds](https://github.com/ct-yx/OpenFreebuds) — 反向工程的华为 FreeBuds 协议桌面端实现（Python）
- [Android BluetoothSocket 文档](https://developer.android.com/reference/android/bluetooth/BluetoothSocket)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material 3](https://m3.material.io/)

---

## 📄 许可证

本项目基于 OpenFreebuds 的协议反向工程成果开发，仅供学习研究使用。请遵守当地法律法规，勿用于商业用途。

---

**Made with ❤️ by someone who just wants to control earbuds without logging in.**
