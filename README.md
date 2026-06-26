<p align="center">
  <img src="./docs/icon.png" alt="fxxkHilife Icon" width="128" height="128">
</p>

<h1 align="center">fxxkHilife</h1>

<p align="center">
  <b>HUAWEI FreeBuds / HONOR Earbuds 的轻量离线控制 App</b>
</p>

> **v2.0.0-alpha.4** — 增强蓝牙扫描器（品牌识别+RSSI+配对状态），Handler 接口就绪。
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

当前版本：**v2.0.0-alpha.4**

- 旧 v1.7.3 版本已归档（分支 `main-archived`）
- 已完成：项目骨架、终端界面、签名统一、更新检查、屏幕适配、快捷按钮栏、协议命令词典、设备能力表（12型号）、蓝牙扫描、SPP驱动、Handler接口
- CI 通过 GitHub Actions 自动编译并发布 Release
- 开发记录见 [DEVELOPMENT_LOG.md](./DEVELOPMENT_LOG.md)

---

## License

仅供学习与个人研究使用，禁止商业用途。
