<p align="center">
  <img src="./docs/icon.png" alt="fxxkHilife Icon" width="128" height="128">
</p>

<h1 align="center">fxxkHilife</h1>

<p align="center">
  <b>HUAWEI FreeBuds / HONOR Earbuds 的轻量离线控制 App</b>
</p>

> **v2.2.0** — Material 3 Compose UI 完整重构：权限引导、自动连接、持久化、后台重试、定时轮询、全局设置页、分享日志。
>
> 通过蓝牙 SPP 直接控制耳机，无需登录、无广告、完全离线。

**[English](./README_EN.md) | 中文**

本项目参考 [OpenFreebuds](https://github.com/melianmiko/OpenFreebuds) 的协议逆向工作。

---

## 构建

```bash
git clone https://github.com/ct-yx/fxxkHilife.git
cd fxxkHilife
./gradlew :app:assembleDebug
```

---

## 项目状态

当前版本：**v2.2.0**

### 已完成
- **Compose UI 完整重构**：四屏导航（权限引导 → 扫描 → 设备详情 ↔ 全局设置）
- **连接持久化**：连接后自动保存设备地址（SharedPreferences），返回扫描页不断连
- **自动连接**：扫描到华为/荣耀设备自动连接
- **后台重试**：初始化失败的 Handler 每 30 秒后台重试直至成功
- **定时轮询**：属性每 10 秒同步，电池被动推送 + 45 秒兜底
- **设置界面**：全局右上角入口，含版本信息、已保存设备、调试终端入口、分享日志
- **分享日志**：一键导出当前 SPP 日志为文本文件
- 13 个功能 Handler（info/battery/anc/double_tap/triple_tap/swipe/long_tap/auto_pause/low_latency/sound_quality/voice_language/in_ear/logs）
- 交错并行初始化（80ms 间隔、1.5s 快失、3 次重试、10s 全局超时）
- ANC 双重通知（主动请求 2b2a + 被动推送 2b2c）
- 电池完整解析（L/R/Case/充电状态）
- 设备能力表按型号过滤 Handler
- FreeBuds 6i 实测 9/13 Handler 5.5s 内初始化成功
- CI 自动编译发布 Release

### 已知问题
- 6i 蓝牙通道拥塞：device_info/gesture_double/gesture_swipe/voice_language 偶发初始化失败（后台 30s 自动重试）
- 均衡器预设（EQ Preset/Custom）和双设备连接（Dual Connect）：未实现
- FileProvider 需在 AndroidManifest.xml 注册（分享日志功能依赖）

---

## License

仅供学习与个人研究使用，禁止商业用途。
