<p align="center">
  <img src="./docs/icon.png" alt="fxxkHilife Icon" width="128" height="128">
</p>

<h1 align="center">fxxkHilife</h1>

<p align="center">
  <b>A lightweight offline controller for HUAWEI FreeBuds / HONOR Earbuds</b>
</p>

> **v2.1.2** — Parallel Handler init (12s global timeout), ANC passive notification (`2b2c`) support, capability table fix (remove TripleTap for 6i), battery notify integration.
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

Current version: **v2.1.2**

- Previous v1.7.3 release archived (branch `main-archived`)
- Completed: protocol layer packet recv/parse precisely aligned with OpenFreebuds, property storage system, `props/set` terminal commands, 15 upstream Handlers (InfoHandler, BatteryHandler, InEarHandler, LogsHandler, AutoPauseHandler, LowLatencyHandler, SoundQualityHandler, VoiceLanguageHandler, AncLegacyChangeHandler, AncHandler, DoubleTapHandler, TripleTapHandler, SwipeGestureHandler, LongTapHandler, PowerButtonHandler)
- Handler architecture fully matches upstream `OfbDriverHandlerHuawei`'s `handler_id`/`commands`/`ignore_commands`/`properties` routing system
- Handler init parallelized (`coroutineScope` + `launch`) with 12s global timeout
- Device capability table for model-based Handler filtering
- ANC Handler supports both active request (`2b2a`) and passive notification (`2b2c`)
- CI compiles via GitHub Actions and publishes Release automatically
- Development log: [DEVELOPMENT_LOG.md](./DEVELOPMENT_LOG.md)

---

## License

For educational and personal research purposes only. Commercial use prohibited.
