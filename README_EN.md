<p align="center">
  <img src="./docs/icon.png" alt="fxxkHilife Icon" width="128" height="128">
</p>

<h1 align="center">fxxkHilife</h1>

<p align="center">
  <b>A lightweight offline controller for HUAWEI FreeBuds / HONOR Earbuds</b>
</p>

> **v2.0.0-alpha.2** — protocol command dictionary complete, terminal UI ready.
>
> Controls your earbuds directly via classic Bluetooth SPP — no login, no ads, fully offline.

**[中文](./README.md) | English**

This project references protocol reverse engineering work from [OpenFreebuds](https://github.com/melianmiko/OpenFreebuds).

---

## Build

```bash
git clone https://github.com/ct-yx/fxxkHilife.git
cd fxxkHilife
./gradlew :app:assembleDebug
```

---

## Project Status

Current version: **v2.0.0-alpha.2**

- Previous v1.7.3 release archived (branch `main-archived`)
- Completed: project skeleton, terminal UI, unified signing, update checker, layout adaptation, shortcut buttons, protocol command dictionary (21 commands)
- CI compiled via GitHub Actions, logs retrieved through `gh` CLI
- Development log: [DEVELOPMENT_LOG.md](./DEVELOPMENT_LOG.md)

---

## License

For educational and personal research purposes only. Commercial use prohibited.
