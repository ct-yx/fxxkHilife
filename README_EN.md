<p align="center">
  <img src="./docs/icon.png" alt="fxxkHilife Icon" width="128" height="128">
</p>

<h1 align="center">fxxkHilife</h1>

<p align="center">
  <b>A lightweight offline controller for HUAWEI FreeBuds / HONOR Earbuds</b>
</p>

> **v2.2.0** — Full Material 3 Compose UI rewrite: permission guide, auto‑connect, device persistence, background retry, timed polling, settings screen, log sharing.
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

Current version: **v2.2.0**

### Completed
- **Compose UI rewrite**: four‑screen navigation (Permission Guide → Scan → Device ↔ Settings)
- **Connection persistence**: save device address via SharedPreferences, stay connected on back‑navigation
- **Auto‑connect**: automatically connect to discovered Huawei/Honor devices
- **Background retry**: failed init handlers retried every 30s until successful
- **Timed polling**: properties synced every 10s, battery on passive push + 45s fallback
- **Settings screen**: global top‑right entry with version, saved device, debug terminal, log sharing
- **Log sharing**: one‑tap export current SPP logs as a text file
- 13 functional Handlers (info/battery/anc/double_tap/triple_tap/swipe/long_tap/auto_pause/low_latency/sound_quality/voice_language/in_ear/logs)
- Staggered parallel init (80ms gap, 1.5s fast‑fail, 3 retries, 10s global timeout)
- ANC dual notification (active 2b2a + passive push 2b2c)
- Full battery parsing (L/R/Case/charging status)
- Capability‑table per‑model Handler filtering
- FreeBuds 6i verified: 9/13 handlers init in ~5.5s
- CI auto‑builds and publishes Release

### Known Issues
- 6i Bluetooth channel congestion: device_info/gesture_double/gesture_swipe/voice_language may fail init (auto‑retried every 30s)
- EQ Preset/Custom and Dual Connect: not yet implemented
- FileProvider registration needed in AndroidManifest.xml for log sharing

---

## License

For educational and personal research purposes only. Commercial use prohibited.
