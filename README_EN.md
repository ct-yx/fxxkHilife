<p align="center">
  <img src="./docs/icon.png" alt="fxxkHilife Icon" width="128" height="128">
</p>

<h1 align="center">fxxkHilife</h1>

<p align="center">
  <b>A lightweight offline controller for HUAWEI FreeBuds / HONOR Earbuds</b>
</p>

> **v2.1.3** — Staggered parallel init (80ms gap, 1.5s fast-fail), ANC passive notification, full battery parsing, 6i capability verified.
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

Current version: **v2.1.3**

- Previous v1.7.3 release archived (branch `main-archived`)
- Completed: protocol layer packet recv/parse precisely aligned with OpenFreebuds, property storage system, `props/set` terminal commands, 13 active Handlers (6i capability-filtered: InfoHandler, BatteryHandler, InEarHandler, LogsHandler, AutoPauseHandler, LowLatencyHandler, SoundQualityHandler, VoiceLanguageHandler, AncHandler, DoubleTapHandler, TripleTapHandler, SwipeGestureHandler, LongTapHandler)
- Handler architecture fully matches upstream `OfbDriverHandlerHuawei`'s `handler_id`/`commands`/`ignore_commands`/`properties` routing system
- Handler init: staggered parallel (`mapIndexed` + `delay` × 80ms), 1.5s fast-fail per handler × 3 retries, 10s global timeout
- Device capability table (`modelCapabilities`) for model-based Handler filtering
- ANC Handler supports both active request (`2b2a`) and passive notification (`2b2c`)
- Full battery parsing (global/L/R/case/charging status)
- FreeBuds 6i verified: 9/13 handlers init in ~5.5s, remaining 4 to be bound on-demand in future UI
- CI compiles via GitHub Actions and publishes Release automatically
- Development log: [DEVELOPMENT_LOG.md](./DEVELOPMENT_LOG.md)

---

## License

For educational and personal research purposes only. Commercial use prohibited.
