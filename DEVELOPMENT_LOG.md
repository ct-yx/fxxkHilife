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
