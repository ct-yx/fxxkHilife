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

### 2026-06-26 (续)
- 创建重构分支 `refactor/v2-alpha`，设为 GitHub 默认主分支 ✅
- 重写所有描述文件（READMES、VERSION_MANAGEMENT、DEVELOPMENT_LOG）✅
- 配置 GitHub Actions CI（`build.yml`）：JDK 17 + Android SDK + Debug/Release 编译 + artifact 上传 ✅
- 搭建 Android 项目骨架：AGP 8.7.3 / Kotlin 1.9.24 / SDK 35，含 Application、BluetoothService、MainActivity、5密度启动图标 ✅
- 清理冗余根目录图标文件 ✅
- 实现终端风格日志界面：LogBuffer 数据层 + TerminalActivity UI，支持 clear/filter/share/check/download 命令 ✅
- 实现横竖屏自适应布局：竖屏 85%/15% 分屏、横屏 60/40 左右分屏 + 快捷按钮列 ✅
- 统一签名：Release 也走 debug 签名，保证覆盖安装 ✅
- 实现更新检查：UpdateChecker 通过 GitHub Releases API 获取最新版本 ✅
- 屏幕适配：深色不透明状态栏 + fitsSystemWindows，内容从安全区开始 ✅
- 实现快捷按钮栏：6颗命令按钮，竖屏横排、横屏竖列 ✅
- 实现协议命令词典：HuaweiSppCommand（21条命令 ID）+ HuaweiSppPackage（封解包 + CRC16 校验）✅
- 修复 CI 编译错误若干：settings.gradle.kts 语法、BuildConfig 未开启、companion object 合并、signingConfig 路径问题 ✅
- 更新版本至 v2.0.0-alpha.2
- 实现设备能力表 DeviceCapability.kt：12款型号 x 33项能力，对照 OpenFreebuds Python 驱动类逐型号翻译 ✅
- 实现蓝牙驱动层：SppDriver（蓝牙RFCOMM连接、包收发、响应等待、包分发、Handler注册）✅
- 实现蓝牙设备扫描：BluetoothScanner（BroadcastReceiver 方式）✅
- 定义 Handler 接口：HuaweiDeviceHandler（onInit + onPackage + commandIds）✅
- 终端命令扩展：scan / list / connect <n> / disconnect 四条蓝牙操作命令 ✅
- 运行时权限增强：按钮栏新增 scan 按钮，所有蓝牙操作日志走终端显示 ✅
- 更新版本至 v2.0.0-alpha.3（code=3）
- CI 改造：编译通过后自动创建 GitHub Release 并上传 APK

- 增强蓝牙扫描器：ScannedDevice 数据类（RSSI/品牌识别/配对状态/连接状态），BluetoothScanner 重构为 Boolean 回调 ✅
- 终端 list 命令增强：显示 🔹 华为/荣耀标识、RSSI 信号强度、[paired] 配对状态 ✅
- 扫描前自动列出已配对设备 ✅
- 版本更新至 v2.0.0-alpha.4（code=4）
- CI 自动 Release: 编译通过后 gh release create 上传 Debug+Release APK ✅

- 对照 OpenFreebuds 实现电池 Handler：BatteryHandler.kt（onInit 发送读请求、onPackage 解析电量、setOnBatteryUpdate 回调接口）✅
- 扫描后自动连接：扫到华为/荣耀设备立即自动连接，手动 connect 保留 ✅
- 所有 Handler 预留注册接口：connectToDevice() 集中注册，各 Handler 通过 registerHandler 注入 ✅
- 更新版本至 v2.0.0-alpha.5（code=5）


## v2.0.0-beta.1 (2026-06-27)

### 重大重构
- **底层蓝牙驱动全面替换**: RFCOMM SPP → BLE GATT
- 新建 : 基于 Android BluetoothGatt 的 BLE 数据通道驱动
- 自动发现华为 BLE Service (febf/fee0/fd00) 及 WRITE/NOTIFY 特征值
- 删除 : 因 Android 不支持已连接设备的二次 RFCOMM 通道

### 新增
- BLE 权限声明 ()
-  添加 FEATURE_BLUETOOTH_LE

### 修复
- 已配对耳机扫描后自动连接 BLE 通道
- 底层通信不再依赖 SPP RFCOMM

### 技术变更
| 文件 | 操作 |
|------|------|
|  | 新建 (382行) |
|  | 删除 |
|  | 引用替换: SppDriver→GattDriver |
|  | +FEATURE_BLUETOOTH_LE |

### 备注
GattDriver 支持华为/荣耀耳机 BLE 控制通道的自动发现，未来 Handler 扩展无需改动底层。
