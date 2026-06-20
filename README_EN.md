<p align="center">
  <img src="./docs/icon.png" alt="fxxkHilife Icon" width="128" height="128">
</p>

<h1 align="center">fxxkHilife</h1>

<p align="center">
  <b>A lightweight offline controller for HUAWEI FreeBuds / HONOR Earbuds</b>
</p>

> Controls your earbuds directly via classic Bluetooth SPP — no login, no ads, fully offline.

This project is an Android implementation of [OpenFreebuds](https://github.com/melianmiko/OpenFreebuds) by MelianMiko. Huge thanks to the original author for the excellent protocol reverse engineering work.

---

## Why this project?

Huawei's official "Smart Life / HiLife" app has become increasingly frustrating:

- Mandatory account login
- Ads, recommendations, and telemetry
- Bloated and slow to launch

Most users only need the basics: **battery check, ANC toggle, EQ, and gesture settings**.

fxxkHilife was built as a focused, clean, privacy-respecting alternative.

---

## Supported Devices

The following models are currently supported (feature availability varies by model):

| Model               | Key Features |
|---------------------|--------------|
| FreeBuds 4i         | Battery, ANC, Gestures, Auto Pause, EQ |
| FreeBuds 5i / 6i    | Battery, ANC, Gestures, Auto Pause, EQ, Low Latency, Sound Quality |
| FreeBuds Pro        | Battery, ANC, Gestures, EQ, Dual Connect, Voice Language |
| FreeBuds Pro 2      | Battery, ANC, Gestures, EQ, Sound Quality, Dual Connect, Voice Language |
| FreeBuds Pro 3      | Battery, ANC, Gestures, EQ, Low Latency, Sound Quality, Dual Connect, Voice Language |
| FreeLace Pro 2      | Battery, ANC, EQ, Low Latency, Sound Quality, Dual Connect, Voice Language |

For the exact feature set per model, refer to `DeviceProfile.kt`.

---

## Features

- Real-time battery display (left / right / case + charging status)
- ANC mode switching + level adjustment (including Dynamic)
- Gaming low-latency mode (optional Fixed On auto-keep)
- Sound quality preference switching
- Equalizer presets
- Gesture customization (double tap, triple tap, long press, swipe)
- Dual device connection management
- Auto pause (wear detection linked)
- Voice prompt language switching
- Device firmware & serial number info
- **Quick Settings tile** (one-tap ANC toggle)
- **Persistent foreground notification** (status + cumulative wear time)
- WorkManager background keep-alive + auto-reconnect
- Comprehensive dual-channel debug logs (one-tap export)

---

## Technical Highlights

- **Protocol layer**: Pure Kotlin SPP (RFCOMM) client with CRC16 checksum, retry mechanism, and timeout handling
- **Architecture**: `SppClient` → `DeviceManager` → **Pluggable Handler** system
  - Each feature has its own Handler (e.g. `AncModeHandler`, `GestureHandler`, `DualConnectHandler`)
  - Dynamically loaded based on `DeviceProfile`
  - Easy to add new features or support new models
- **UI**: Jetpack Compose + Material You
- **State management**: StateFlow + SharedFlow
- **Persistence**: DataStore
- **Background keep-alive**: WorkManager + Foreground Service

The codebase is moderate in size but cleanly structured — great for learning Android Bluetooth SPP development and modular architecture.

---

## Build & Usage

```bash
git clone https://github.com/ct-yx/fxxkHilife.git
cd fxxkHilife
./gradlew :app:assembleDebug
```

After installation, grant Bluetooth permissions (Android 12+ requires `BLUETOOTH_CONNECT`), then select a paired FreeBuds device and you're good to go.

**Notes**:
- First connection may require a couple of attempts
- Some features (e.g. Dual Connect) require hardware support from the earbuds

---

## Project Status

Current version: **v1.7.0** (testing release)

Core functionality is largely complete. Key improvements include:

- Connection robustness (Mutex + retry + state correction)
- Logging system (Logcat + file rotation + one-tap sharing)
- Code quality (eliminated most `!!` and silent exception catches)
- Background keep-alive & notification UX
- **Full bilingual UI (Chinese & English)**: All screens (MainScreen, SettingsScreen, BatteryCard etc.) use `stringResource` — zero hardcoded English strings
- **Rewritten permission handling**: Runtime checks for `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `ACCESS_FINE_LOCATION`, and `POST_NOTIFICATIONS` (Android 13+), with a dedicated permission info screen
- **Protocol layer aligned with upstream OpenFreebuds**: Full 6-zone code review complete, 2 P0 protocol bugs fixed (ANC command IDs, missing charging state), SoundQuality param position corrected, 9 code issues resolved
- **Gestures fully completed**: All 4 gesture types (double tap, triple tap, long press, swipe) finalized, protocol parameters fully matching upstream
- **ANC 3-mode fix**: Noise canceling / off / awareness mode payload construction aligned with upstream, Awareness level mapping corrected
- **Background keep-alive enhancement**: WorkManager periodic keep-alive + CompanionDeviceManager auto-reconnect + battery optimization guidance
- **Permission error UX improved**: Chinese/English Toast on permission denial, AlertDialog redirecting to system settings after repeated denials
- **App icon**: Black background removed, full mipmap density set generated

### ⚠️ Known Issues (v1.7.0)

This release is in **feature validation stage**. The following features have code implementation but **may not work correctly on all devices**. Testing feedback is welcome:

| Feature | Status | Notes |
|---------|--------|-------|
| ANC mode switching (in-app) | ✅ **Working** | Off / Noise Cancel / Awareness |
| Noise Cancel Tile | ✅ **Rewritten v1.6.8** | Split into 2 independent Tiles: Noise Cancel + Awareness, each is two-state (on/off) |
| **Low Latency mode** | ❌ **Broken** | In-app toggle may not take effect |
| **EQ Presets** | ❌ **Broken** | No response after switching presets |
| **Gesture settings** | ❌ **Broken** | Double/triple/long press/swipe not working |
| **Sound Quality preference** | ❌ **Broken** | Stable/Quality toggle has no effect |
| **Dual Device Connect** | ❌ **Broken** | Toggle may not sync to earbuds |
| Battery display | ✅ **Working** | Left / Right / Case |
| Device info | ✅ **Working** | Firmware, serial number etc. |
| Log export | ✅ **Working** | One-tap share debug logs |
| Theme switching | ✅ **Working** | Light / Dark / System |

> These issues are most likely **protocol command parameter or timing problems**. Issues and PRs are welcome.

Detailed development log: [DEVELOPMENT_LOG.md](./DEVELOPMENT_LOG.md)

---

## License

For **educational and personal research purposes only**. Commercial use is prohibited.

Protocol implementation references [melianmiko/OpenFreebuds](https://github.com/melianmiko/OpenFreebuds).

---

## Acknowledgements

- [MelianMiko](https://github.com/melianmiko) for the OpenFreebuds project and comprehensive protocol reference
- All users who contributed feedback and issues

---

For feature requests, bug reports, or new model support, feel free to open an Issue or PR.
