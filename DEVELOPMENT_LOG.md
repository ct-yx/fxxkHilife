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
- 所有开发文件已清理，仅保留描述文件 + 图标
- 上游 OpenFreebuds 仓库已重新克隆到 `OpenFreebuds/`（git-ignored）
- 本地编译环境（Gradle/SDK/Rust 工具链）已全部清理，编译全权由 GitHub Actions CI 负责
- CI 报错通过 `gh` CLI 获取日志
