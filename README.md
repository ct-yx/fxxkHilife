<p align="center">
  <img src="./docs/icon.png" alt="fxxkHilife Icon" width="128" height="128">
</p>

<h1 align="center">fxxkHilife</h1>

<p align="center">
  <b>HUAWEI FreeBuds / HONOR Earbuds 的轻量离线控制 App</b>
</p>
> **v2.7.0** — 源头乐观更新：DeviceRepository setProperty 同时更新 StateFlow，Tile 响应式刷新，NotificationChannel 适配，CI 自动发布 Release 带既有 Tag 覆盖。

>
> 通过蓝牙 SPP 直接控制耳机，无需登录、无广告、完全离线。

**[项目主页](https://ct-yx.github.io/fxxkHilife/) · [English](./README_EN.md) | 中文**

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

当前版本：**v2.6.0**

### 已完成
- **自适应轮询**：前台 800ms 高频轮询，后台 5s 低频，由 Activity 生命周期驱动
- **三态主题**：跟随系统 / 深色 / 浅色，Material3 原生分段选择器
- **壁纸系统**：支持导入图片壁纸（coil AsyncImage），可选择作用域（全部 / 仅主页 / 仅设置）
- **通知栏 ANC 快切**：通知栏直接切换 ANC 模式（关闭/降噪/透传），三个 Action 按钮
- **通知栏实时状态**：显示当前 ANC 模式、音质模式、低延迟状态、佩戴时长（自动计时）
- **日志控制**：可配置日志保留行数（500/1000/2000/5000/10000）
- **页面调度重构**：断连按钮移到主页已保存设备列表，红色删除图标
- **ANC 分段控件**：Material3 Surface + Row 原生分段选择器（关闭/降噪/透传），响应式状态即时切换
- **设置页改造**：主题切换、壁纸导入+作用域选择、应用详情（项目理念/GitHub/更新地址）
- **五屏导航**：权限引导 → 首页（已保存设备列表 + 扫描折叠区）→ 设备详情 → 手势子页面 → 全局设置
- **已保存设备主页**：自动持久化连接过的设备地址（StringSet），点击直接连接，扫描折叠在下
- **手势设置子页面**：双击 / 三击 / 滑动手势 / 长按独立页面，全部中文选项
- **ANC / 低延迟即时刷新**：setProperty 先写预期值再发命令，无需等待轮询
- **轮询提速**：属性轮询 10s → 3s，setProperty 后 100ms delay + syncProps
- **断连保护**：SppDriver recvLoop 退出时设 isConnected=false，防止 Broken pipe
- **全属性中文映射**：降噪模式、通透、关闭 / 声音优先、连接优先 / 播放暂停、下一首等
- **连接持久化**：连接后自动保存设备地址（SharedPreferences），返回首页不断连
- **自动连接**：扫描到华为/荣耀设备自动连接
- **后台重试**：初始化失败的 Handler 每 30 秒后台重试直至成功
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
