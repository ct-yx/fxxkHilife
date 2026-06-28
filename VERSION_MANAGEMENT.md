# 版本管理

> 版本号统一修改入口，按清单逐一更新。

## 当前版本

- **v2.3.0** (versionCode=14, 2026-06-28)

## 版本号位置

| 位置 | 当前值 |
|------|--------|
| `app/build.gradle.kts` | versionCode=14, versionName="2.3.0" |
| `app/src/main/res/values/strings.xml` | version_name=2.3.0 |
| `README.md` | v2.3.0 |
| `README_EN.md` | v2.3.0 |
| `DEVELOPMENT_LOG.md` | v2.3.0 (末尾) |

## 历史版本

| 版本 | Code | 日期 | 主要变更 |
|------|------|------|---------|
| v2.3.0 | 14 | 2026-06-28 | UI/UX 全面修复：3s 轮询 / ANC 即时刷新 / 手势子页 / 已保存主页 / 全中文 / 五屏导航 |
| v2.2.0 | 13 | 2026-06-28 | Compose UI 完整重构 |
| v2.1.3 | 12 | 2026-06-28 | 交错并行初始化 + 自动打印属性 |
| v2.1.2 | 11 | 2026-06-28 | 三轮并行迭代 + ANC 被动通知 + 电池修复 |
| v2.1.0 | 10 | 2026-06-28 | 15 Handler 移植 + 属性存储系统 |
| v2.0.0-stable.1 | 9 | 2026-06-27 | 回归 RFCOMM SPP |
| v1.7.3 | 8 | (archived) | 旧版 |

## 规格

- versionCode：整数，每次发布 +1
- versionName：语义化版本号
