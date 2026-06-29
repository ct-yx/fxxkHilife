# 版本管理

> 版本号统一修改入口，按清单逐一更新。

## 当前版本
- **v2.8.3** (versionCode=30, 2026-06-29)

## 版本号位置

| 位置 | 当前值 |
|------|--------|
| `app/build.gradle.kts` | versionCode=30, versionName="2.8.3" |
| `app/src/main/res/values/strings.xml` | version_name=2.8.3 |
| `README.md` | v2.8.3 |
| `README_EN.md` | v2.8.3 |
| `DEVELOPMENT_LOG.md` | v2.8.3 (末尾) |

## 一键版本更新脚本

```bash
python3 scripts/bump_version.py <versionName> <versionCode> "修复说明"
# 例如：python3 scripts/bump_version.py 2.7.8 27 "修复说明"
```

脚本会同步更新：
- `app/build.gradle.kts`
- `app/src/main/res/values/strings.xml`
- `README.md`
- `README_EN.md`
- `docs/index.html`
- `VERSION_MANAGEMENT.md`
- `DEVELOPMENT_LOG.md`

> 脚本只负责统一替换版本号和插入基础发布记录；具体变更说明仍建议人工补充到 `DEVELOPMENT_LOG.md`。

## 历史版本

| 版本 | Code | 日期 | 主要变更 |
|------|------|------|---------|
| v2.8.3 | 30 | 2026-06-29 | 修复自动暂停写入确认 / 暂停 FreeBuds 7i 自动暂停选项 |
| v2.8.2 | 29 | 2026-06-29 | 修复耳机蓝牙断开后应用连接状态未更新 |
| v2.8.1 | 28 | 2026-06-29 | 修复初始化 ANC 状态未实时同步到 UI / 通知 / 快捷开关 |
| v2.8.0 | 27 | 2026-06-29 | UI 导航系统重构 / 展示模式基础 / 传统与液态玻璃模式切换入口 |
| v2.7.7 | 26 | 2026-06-29 | FreeBuds 7i 临时保守能力表 / 标记后续大版本多型号适配计划 |
| v2.7.6 | 25 | 2026-06-29 | 修复首次切到降噪旧状态包乱跳 / 恢复充电盒 100% 电量显示 / 增加一键版本更新脚本 |
| v2.7.2 | 21 | 2026-06-29 | ANC/主题长条胶囊滑块 / ANC 圆形图标滑块 / 全局壁纸背景修复 |
| v2.7.1 | 20 | 2026-06-29 | 常驻通知保活 / 电池与实时状态显示 / 打开应用/系统自启动自动连接已保存耳机 / 清理旧 ACL 自动连接残留 |
| v2.7.0 | 19 | 2026-06-28 | 源头乐观更新 / Tile 响应式刷新 / NotificationChannel 适配 / CI 自动发版 |
| v2.6.0 | 18 | 2026-06-28 | 导航重构 / ANC 乐观更新 / 低延迟可配置 / 已保存设备修复 |
| v2.5.0 | 17 | 2026-06-28 | Haze 移除 / Material3 分段控件 / QuickSettings Tile ANC 切换 / 重试策略优化 |
| v2.4.0 | 15 | 2026-06-28 | 三阶段大更新
| v2.4.0 | 15 | 2026-06-28 | 三阶段大更新：自适应轮询 / 三态主题+壁纸 / 通知栏ANC快切 / 日志控制 / ANC模糊滑块 |
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
