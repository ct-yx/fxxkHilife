# 版本管理说明

> **本文件是项目的版本修改统一入口**
> 修改版本号时，务必按照以下清单逐项核对并更新。
> 如果以后新增了包含版本号的文件，请同步更新本清单。

---

## 版本号定义位置

| # | 文件 | 字段 | 当前值 | 更新方式 |
|---|------|------|--------|----------|
| 1 | `app/build.gradle.kts` | `versionCode` | `5` | 直接修改数字 |
| 2 | `app/build.gradle.kts` | `versionName` | `"v1.5.2"` | 直接修改字符串 |
| 3 | `app/src/main/res/values/strings.xml` | `<string name="version_name">` | `"v1.3.0-beta"` | 直接修改字符串 |
| 4 | `app/src/main/res/values-zh-rCN/strings.xml` | `<string name="version_name">` | `"v1.3.0-beta"` | 直接修改字符串 |
| 5 | `README.md` | "当前版本：\*\*v1.5.2\*\*" | `v1.5.2` | 直接修改文本 |
| 6 | `README_EN.md` | "Current version: \*\*v1.5.2\*\*" | `v1.5.2` | 直接修改文本 |
| 7 | `DEVELOPMENT_LOG.md` | 末尾新增变更记录 | — | 按模板追加 |
| — | `SettingsScreen.kt` | `BuildConfig.VERSION_NAME` | 自动读取 | **无需手动修改** |

---

## 修改步骤

### 1. 修改 `app/build.gradle.kts`

```gradle
defaultConfig {
    versionCode = <递增数字>
    versionName = "<新版本号>"
}
```

### 2. 修改 `strings.xml`（中文+英文）

编辑两个文件中的 `<string name="version_name">`：
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh-rCN/strings.xml`

### 3. 修改 `README.md` 和 `README_EN.md`

分别更新中英文 README 中的版本标记。

### 4. 更新 `DEVELOPMENT_LOG.md`

在文件末尾按以下格式追加记录：

```markdown
## YYYY-MM-DD

**vX.Y.Z Release — <变更简述>**

- 版本变更说明
- Version bump: versionCode **N→M**, versionName **"旧"→"新"**
✅ completed
```

### 5. 验证

修改完成后，执行以下验证：
- 确认 `BuildConfig.VERSION_NAME` 已自动包含新版本号
- SettingsScreen 的"About"区域会显示更新后的版本（无需手动修改）
- 确认 `Readme.md` / `README_EN.md` 最新版本号一致
- 确认 `DEVELOPMENT_LOG.md` 末尾记录准确

---

## 自动化升级脚本

```bash
#!/bin/bash
# 用法: ./scripts/update_version.sh <versionCode> <versionName>
# 例如: ./scripts/update_version.sh 6 "v1.6.0"

set -e

VERSION_CODE=$1
VERSION_NAME=$2

if [ -z "$VERSION_CODE" ] || [ -z "$VERSION_NAME" ]; then
    echo "Usage: $0 <versionCode> <versionName>"
    echo "Example: $0 6 \"v1.6.0\""
    exit 1
fi

# 1. build.gradle.kts
sed -i "s/versionCode = [0-9]*/versionCode = $VERSION_CODE/" app/build.gradle.kts
sed -i "s/versionName = \".*\"/versionName = \"$VERSION_NAME\"/" app/build.gradle.kts
echo "✅ app/build.gradle.kts updated"

# 2. strings.xml (EN)
sed -i "s|<string name=\"version_name\">.*</string>|<string name=\"version_name\">$VERSION_NAME</string>|" \
    app/src/main/res/values/strings.xml
echo "✅ values/strings.xml updated"

# 3. strings.xml (ZH)
sed -i "s|<string name=\"version_name\">.*</string>|<string name=\"version_name\">$VERSION_NAME</string>|" \
    app/src/main/res/values-zh-rCN/strings.xml
echo "✅ values-zh-rCN/strings.xml updated"

# 4. README.md
sed -i "s/当前版本：\*\*v[0-9.]*\*\*/当前版本：\*\*$VERSION_NAME\*\*/" README.md
echo "✅ README.md updated"

# 5. README_EN.md
sed -i "s/Current version: \*\*v[0-9.]*\*\*/Current version: \*\*$VERSION_NAME\*\*/" README_EN.md
echo "✅ README_EN.md updated"

echo ""
echo "🎉 所有版本号已更新至 $VERSION_NAME (versionCode=$VERSION_CODE)"
echo "⚠️  别忘了手动追加 DEVELOPMENT_LOG.md 记录！"
```

---

## 注意事项

- **`SettingsScreen.kt`** 中版本号通过 `BuildConfig.VERSION_NAME` 自动编译生成，**不需要**手动修改
- **`strings.xml` 中的 `version_name`** 是硬编码备用值（UI 显示用），需要同步更新
- **`DEVELOPMENT_LOG.md`** 使用 `sed` 替换容易错乱，建议手动追加
- 如果想用自动化脚本，请先 `chmod +x scripts/update_version.sh`
- 如果以后添加了新文件（如 `CHANGELOG.md`、`about_screen.xml` 等）包含版本号，请务必追加到上述清单中

---

## Rust 原生守护进程分支 (`feat/rust-native-daemon-proto`)

> `feat/rust-native-daemon-proto` 分支独立维护 Rust 守护进程原型，与 `main` 分支保持 CI 配置同步但拥有独立的版本演进。

### 分支定位
- **目标**: 在 Kotlin App 之外部署一个 Rust 本地守护进程，提供激进的保活、蓝牙健康监控、通知转发和进程 watchdog
- **基础**: 基于 `main` 分支的 `v1.5.2` 代码，新增 `native-daemon/` 目录
- **CI**: 复用 `main` 的 `android-build.yml`（已合并 CI 修复），构建 APK（不含 Rust binary，后续集成）

### 文件结构

| 路径 | 说明 |
|------|------|
| `native-daemon/Cargo.toml` | Rust 项目配置（tokio full, serde, libc, ctrlc, futures） |
| `native-daemon/src/main.rs` | 入口：DaemonConfig（环境变量）、DaemonState、tokio 主循环 |
| `native-daemon/src/ipc.rs` | Unix Domain Socket JSON-RPC 服务器（10 种 IPC 方法） |
| `native-daemon/src/bluetooth.rs` | HCI sysfs 蓝牙健康监控（非 root，/sys/class/bluetooth） |
| `native-daemon/src/keepalive.rs` | 进程 watchdog（pidof + /proc 回退 + am start 重启） |
| `native-daemon/src/notify.rs` | cmd notification post 通知派发（需 root/shell） |
| `native-daemon/src/daemon.rs` | PID 文件管理 + signal 0 进程存活检查 |
| `.gitignore` | 忽略 `native-daemon/target/` |

### 版本策略

Rust daemon 版本独立于 App 版本，仅在 `native-daemon/Cargo.toml` 中维护：

```toml
[package]
name = "fxxkhilife_daemon"
version = "0.1.0"          # ← 在此修改 Rust daemon 版本
```

| 版本 | 说明 |
|------|------|
| `0.1.0` | 原型阶段：5 模块 + IPC 通信验证通过 |
| `0.2.0`（计划） | Android 端集成：NativeDaemonClient + Launcher |
| `0.3.0`（计划） | cargo ndk 交叉编译 + APK 嵌入 |

### 与 main 分支的差异

| 维度 | main | feat/rust-native-daemon-proto |
|------|------|-------------------------------|
| App 代码 | 完整 v1.5.2 | 同 main（通过 merge 同步） |
| CI 配置 | actions @v5，完整修复 | 同 main ✅ |
| Rust 项目 | ❌ 无 | ✅ native-daemon/ |
| CI 触发 | push/PR → 构建 APK | push/PR → 构建 APK（同上） |

### 同步策略
- **CI 配置**：每次 main 修改 `.github/workflows/` 后，按需 cherry-pick 或 merge 到 rust 分支
- **App 代码**：rust 分支的 App 代码与 main 同步，通过定期 merge main 保持
- **Rust 代码**：rust 分支独有，不反向合并到 main

### 注意事项
- `native-daemon/target/` 已被 `.gitignore` 排除，Rust 编译产物不入库
- Rust daemon 需要 root/shell 权限运行，依赖 `pidof`、`am`、`cmd notification` 等 Android 系统命令
- 跨编译需使用 `cargo ndk` 或 Android NDK 工具链
