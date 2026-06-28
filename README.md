<p align="center">
  <img src="./docs/icon.png" alt="fxxkHilife Icon" width="128" height="128">
</p>

<h1 align="center">fxxkHilife</h1>

<p align="center">
  <b>HUAWEI FreeBuds / HONOR Earbuds 的轻量离线控制 App</b>
</p>

> **v2.1.2** — 并行 Handler 初始化（12s 全局超时），ANC 被动通知 (`2b2c`) 支持，能力表修正（6i 去除 TripleTap），电池被动通知对接。
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

当前版本：**v2.1.2**

- 旧 v1.7.3 版本已归档（分支 `main-archived`）
- 已完成：协议层收包/解析精准对齐 OpenFreebuds，属性存储系统，`props/set` 终端命令，15 个上游 Handler（InfoHandler、BatteryHandler、InEarHandler、LogsHandler、AutoPauseHandler、LowLatencyHandler、SoundQualityHandler、VoiceLanguageHandler、AncLegacyChangeHandler、AncHandler、DoubleTapHandler、TripleTapHandler、SwipeGestureHandler、LongTapHandler、PowerButtonHandler）
- 协议架构全面匹配上游 `OfbDriverHandlerHuawei` 的 `handler_id`/`commands`/`ignore_commands`/`properties` 路由体系
- Handler 初始化改为并行（`coroutineScope` + `launch`），12s 全局超时，不再串行阻塞
- 设备能力表建模过滤，按型号只注册支持的 Handler
- ANC Handler 同时支持主动请求 (`2b2a`) 和被动通知 (`2b2c`)
- CI 通过 GitHub Actions 自动编译并发布 Release
- 开发记录见 [DEVELOPMENT_LOG.md](./DEVELOPMENT_LOG.md)

---

## License

仅供学习与个人研究使用，禁止商业用途。
