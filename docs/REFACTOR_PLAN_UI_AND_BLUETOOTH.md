# fxxkHilife 新 UI 与连接 API 重构计划

> 当前分支：`codex/prism-ui-rewrite`
> 目标分支：`codex/prism-ui-rewrite` 成功验收后设为仓库默认分支
> UI 框架：Flutter Add-to-App + 仓库内固定的 `prism_ui v0.1.3`
> 底层通信：保留现有 RFCOMM/SPP、型号适配器、协议和后台连接服务

## 1. 这次重构解决什么问题

旧规划把页面、主题、渲染效果、状态和连接代码放在同一条迁移链上，导致页面改动会牵动蓝牙实现。新规划只保留一条依赖方向：

```text
Flutter 页面
    ↓ 只消费快照、提交语义命令
NativeUiBridge（Flutter channel 适配器）
    ↓
NativeUiController（应用级 UI 接口）
    ↓
EarbudRuntime（统一命令与快照入口）
    ↓
DeviceControlApi / SettingsRepository / UpdateRepository
    ↓
EarbudConnectionManager / DeviceRepository
    ↓
RFCOMM/SPP 协议与型号 Adapter
```

规则：

1. 新 UI 不读取 `SharedPreferences`、蓝牙对象、协议 command id 或 raw property key。
2. 原生 channel 只承载序列化后的快照和语义命令。
3. 页面不能调用通用 `invoke("...")`；每个操作都在 Dart 和 Kotlin 两侧有明确名称。
4. 连接服务、Quick Settings Tile 和 Flutter 页面共用同一个连接管理器和设备控制入口。
5. 旧 Compose 页面、旧 ViewModel、旧 surface/主题实现不回流生产代码。

## 2. 当前完成状态

| 阶段 | 状态 | 证据 / 未完成项 |
| --- | --- | --- |
| P0 盘点与切分 | [x] | 已确认旧 UI 与连接底层耦合点，保留底层运行时 |
| P1 删除旧 UI 生产图 | [x] | Compose 页面、主题、基础组件、XML 页面和 AGSL 资源已删除；旧 UI 测试已删除 |
| P2 原生语义接口 | [x] | `EarbudRuntime`、`RuntimeCommand`、`NativeUiController`、设置/更新接口已建立；仍需云端编译和接口回归 |
| P3 Flutter prism_ui 页面 | [~] | 首页、扫描、设备控制、手势、听音统计、设置/日志已写入；仍需设备运行验收 |
| P4 云端构建 | [x] | Actions [46111128101](https://github.com/ct-yx/fxxkHilife/actions/runs/46111128101) 已通过 Flutter 分析、UI contract、Gradle 测试和 Debug/Release APK 构建；artifact SHA-256 已记录在 README |
| P5 默认分支切换 | [ ] | 等 P6 真机验收后，把 `codex/prism-ui-rewrite` 设为 GitHub 默认分支；当前默认分支仍是 `refactor/v2-alpha` |
| P6 真机验收 | [ ] | 本机未发现 Android 设备；启动、扫描、连接、断开、后台重连、能力写入和日志导出仍需用 Actions artifact 验证 |

## 3. 代码职责

### 3.1 Flutter 层：`flutter_ui/lib`

- `app_state.dart`：保存 `NativeSnapshot`，负责事件流订阅和页面刷新。
- `native_bridge.dart`：唯一 Dart channel 适配器；公开 `connect`、`startScan`、`setAncMode` 等类型化方法。
- `app.dart` / `app_shell.dart`：主题、导航和页面外壳。
- `pages/`：只表达页面布局、状态显示和用户意图。
- `widgets/ui.dart`：通用 Prism/Material 3 风格控件。

页面不允许出现：

- `MethodChannel` / `EventChannel`；
- `invoke("raw_method")`；
- `anc/mode`、`2b2a`、`rfcomm` 等底层字段；
- 原生设置存储或连接生命周期代码。

### 3.2 原生 UI 接口：`app/src/main/java/com/freebuds/controller/nativeui`

- `NativeUiController`：应用级深模块。把连接状态、设备状态、扫描结果、设置、更新和诊断日志组合成一个 `NativeUiSnapshot`。
- `NativeUiBridge`：只做 channel 方法分发、参数校验、快照序列化和事件推送，不写协议逻辑。
- `runtime/EarbudRuntime`：原生运行时唯一的设备命令和连接快照接缝；Flutter、前台服务和 Quick Settings Tile 共用它。
- 通道固定为：

```text
fxxkhilife/native_ui
fxxkhilife/native_ui_state
```

快照分为 `connection`、`device`、`scan`、`saved_devices`、`listening`、`settings`、`update` 七个区域。新增字段先更新 Kotlin 快照、channel map、Dart model 和静态 contract，再接入页面。

### 3.3 设备语义接口：`DeviceControlApi`

页面和 Bridge 只使用以下语义：

- ANC 模式与强度；
- 低延迟、自动暂停；
- 音质、EQ、双设备；
- 手势动作；
- 主题、语言、日志、更新和壁纸设置。

厂商 property group/key 只允许留在 `DeviceControlApi` 与 Repository 内部。新增能力必须先增加枚举/方法，再由页面调用。

`NativeUiController` 不直接创建 `DeviceControlApi`，而是提交 `RuntimeCommand`；这样 UI、服务和 Tile 对连接生命周期拥有同一条命令路径。

### 3.4 蓝牙底层

保留并继续规范化：

- `EarbudConnectionManager`：唯一连接生命周期、attempt、session、断开和重连入口；
- `DeviceRepository` / `EarbudStateStore`：状态单一来源；
- `EarbudTransport`、`HuaweiSppProtocol`、`HuaweiCommandCatalog`：传输、协议和命令实现；
- `HuaweiOpenFreebudsAdapter`：型号能力与 handler 注册。

这些模块不得依赖 Flutter、页面类或 channel 类型。

## 4. 执行顺序

### P4.1 修复云端工具链

1. 使用 Flutter 3.47.0 官方模板的 AGP `9.1.0`、Gradle `9.3.1` 和 Kotlin Gradle Plugin `2.4.0`。
2. 在 `gradle.properties` 保留模板要求的 `android.newDsl=false` 与 `android.builtInKotlin=false`，并在 `app` 模块显式应用 Kotlin Android 插件。
3. 保持 Flutter 3.47.0、Android API 37、JDK 17。
4. 根项目对 Flutter 注入的 `download.flutter.io` 之外，显式提供 Google 和 Maven Central 仓库，保证原生依赖可解析。
5. 固定上游 `prism_ui v0.1.3` 到 `flutter_ui/vendor/prism_ui`，只保留 Flutter 3.47 所需的显式导入兼容修复。
6. 重新运行 GitHub Actions，必须看到：
   - `flutter pub get` 通过；
   - `flutter analyze` 为 `No issues found`；
   - `ui-contract` 通过；
   - Debug/Release Gradle、测试和 APK 定位均通过。

### P4.2 收紧 UI 接口

1. 所有 Flutter 页面只调用 `NativeBridge` 的类型化方法。
2. 静态脚本阻止旧 UI 文件、Compose import、raw channel 调用和旧 UI 测试目录回流。
3. 任何新设备能力先进入 `RuntimeCommand` 和 `DeviceControlApi`，再进入页面、服务或 Tile。

### P4.3 真机验收

按用户路径执行一轮：

1. 首次启动和权限；
2. 扫描、选择已配对设备、连接到 `Ready/Degraded`；
3. ANC、低延迟、音质、手势写入后观察快照读回；
4. 断开、返回后台、系统蓝牙重连；
5. 设置持久化、更新检查、日志刷新/清空/分享；
6. 重复进入页面，确认没有旧 Compose 页面或旧 Activity。

真机结论必须写明设备、Android 版本、固件和具体路径；云端构建不能代替这一步。

### P5 默认分支切换

只有 P4 云端构建和 P6 真机验收都成功，且没有未提交代码时执行：

```bash
gh api --method PATCH repos/ct-yx/fxxkHilife \
  -f default_branch=codex/prism-ui-rewrite
```

旧的 `refactor/v2-alpha` 分支保留，不删除。

## 5. 验证命令

本地只做不依赖 Android/Flutter SDK 的检查：

```bash
git diff --check
python3 scripts/validate_ui_contract.py
```

云端由 `.github/workflows/build.yml` 执行：

```text
flutter pub get
flutter analyze
ui-contract
./gradlew --no-daemon clean test assembleDebug assembleRelease
```

当前证据边界：Actions 46111128101 已在提交 `0d73797` 上通过 Flutter 分析、UI contract、Gradle 测试并产出 Debug/Release APK；这证明云端构建链路和静态门禁通过，不证明真实 Android 设备上的 UI、蓝牙连接或后台重连已验收。本机当前没有可用 `adb` 设备。
