<p align="center">
  <img src="./docs/icon.png" alt="fxxkHilife Icon" width="128" height="128">
</p>

<h1 align="center">fxxkHilife</h1>

<p align="center">
  <b>A lightweight offline controller for HUAWEI FreeBuds / HONOR Earbuds</b>
</p>
> **v2.7.1** — Persistent notification keep-alive fix: app launch / system auto-start starts the foreground service and reconnects the saved earbuds; notification shows listening duration / battery / ANC / low-latency / sound-quality state, with stale ACL auto-connect code removed.

>
> Controls your earbuds directly via classic Bluetooth SPP — no login, no ads, fully offline.

**[Project Home](https://ct-yx.github.io/fxxkHilife/) · [中文](./README.md) | English**

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

Current version: **v2.7.1**

### Completed
- **Adaptive polling**: 800ms foreground high-frequency, 5s background low-frequency, driven by Activity lifecycle
- **Tri-state theme**: Follow system / Dark / Light with Material3 native segmented selector
- **Wallpaper system**: Import custom image wallpaper (coil AsyncImage), scope selector (All / Home only / Settings only)
- **Notification ANC quick-switch**: Toggle ANC mode directly from notification bar (Off / ANC / Awareness), three Action buttons
- **Notification live status**: Persistent notification shows ANC mode, sound quality, low‑latency, and listening duration (instant property refresh plus 60s duration refresh; battery uses existing low-frequency repository data)
- **Log control**: Configurable log retention lines (500/1000/2000/5000/10000)
- **Navigation refactor**: Disconnect button moved to saved-device list on Home, red delete icon
- **ANC segmented control**: Material3 Surface + Row native segment selector (Off / ANC / Awareness), reactive instant switching
- **Settings overhaul**: Theme switch, wallpaper import + scope, app details (project idea / GitHub / update link)
- **Five-screen navigation**: Permission Guide → Home (saved devices + collapsible scan) → Device → Gesture sub‑page → Settings
- **Saved‑device home**: auto‑persist connected device addresses (StringSet), tap to reconnect, scan collapsed below
- **Gesture sub‑page**: double‑tap / triple‑tap / swipe / long‑press in dedicated screen with full chinese labels
- **Instant ANC / Low‑Latency refresh**: setProperty writes expected value first then sends command, no polling wait
- **Faster polling**: property sync 10s → 3s, setProperty adds 100ms delay + syncProps
- **Disconnect guard**: SppDriver recvLoop sets isConnected=false on exit, prevents Broken pipe
- **Full chinese label mapping**: Noise cancel / Transparency / Off, Sound quality / Connection first, Play/Pause / Next track etc.
- **Connection persistence**: save device address via SharedPreferences, stay connected on back‑navigation
- **Auto‑connect**: app launch reconnects the last saved earbuds; scan page still supports manual device selection
- **Background retry**: failed init handlers retried every 30s until successful
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
