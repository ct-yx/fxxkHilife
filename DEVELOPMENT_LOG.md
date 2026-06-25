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
