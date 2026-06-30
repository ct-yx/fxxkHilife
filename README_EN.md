<p align="center">
  <img src="./docs/icon.png" alt="fxxkHilife Icon" width="128" height="128">
</p>

<h1 align="center">fxxkHilife</h1>

<p align="center">
  <b>An open-source offline controller for HUAWEI FreeBuds / HONOR Earbuds</b><br>
  <sub>No HUAWEI account · No ads · No cloud dependency · Direct Bluetooth SPP control</sub>
</p>

<p align="center">
  <a href="https://ct-yx.github.io/fxxkHilife/"><b>Project Home</b></a> ·
  <a href="./README.md">中文</a> ·
  <a href="https://github.com/ct-yx/fxxkHilife/releases/latest">Download latest</a> ·
  <a href="https://github.com/ct-yx/fxxkHilife/issues">Report / Join testing</a>
</p>

> **Current version: v3.7.2**
> Fixes the missing slash marker for individually disconnected earbuds in the device battery card.

---

## What is this?

fxxkHilife is a third-party, open-source, offline control panel for HUAWEI FreeBuds / HONOR Earbuds. It aims to restore everyday earbud controls without requiring account login, vendor ecosystem services, network access, ads, or data upload.

It communicates with earbuds directly through classic Bluetooth **SPP / RFCOMM**. The protocol work is based on [OpenFreebuds](https://github.com/melianmiko/OpenFreebuds), with an Android / Jetpack Compose interface built on top.

The project is still evolving quickly. Testers with more earbud models are very welcome to help verify protocol compatibility.

---

## Key features

- **Dual display modes**: the stable Material3 classic mode remains available; the Haze-based Liquid Glass mode is now enabled on Home / Device with glass cards, background blur, iridescent edges, and subtle highlights.

- **Connection and auto-connect**: scan HUAWEI / HONOR earbuds, save known devices, auto-connect on app launch / boot / foreground service; auto-connect is gated by system Bluetooth connection state before opening SPP.
- **ANC / Awareness / Off**: switch ANC modes from the in-app pill slider, Quick Settings Tile, or persistent notification actions.
- **Low-latency / game mode**: manual switch plus optional auto low-latency; after SPP initialization, the app starts immediately and retries every 500ms for up to 30s until confirmed.
- **Battery and wearing state**: left/right/case battery levels, charging state, wearing detection, auto-pause.
- **Gestures and audio preferences**: double tap, triple tap, long press, swipe gestures, sound quality vs connectivity preference, voice prompt language read/write.
- **Persistent notification and logs**: notification shows ANC, low-latency, sound quality, battery, and listening duration; debug terminal can inspect raw SPP logs, export logs, and write properties.
- **UI experience**: Material3 + Jetpack Compose, dark/light/system themes, wallpaper scope, unified i18n text entry points, Chinese option mapping, and multi-screen navigation.

---

## Demos

> Recorded on FreeBuds 6i. More previews are available on the [project home page](https://ct-yx.github.io/fxxkHilife/).

| Demo | File |
|------|------|
| First launch: permission guide, scan, and home flow | [`docs/demo_first_launch.mp4`](./docs/demo_first_launch.mp4) |
| Persistent notification status: ANC, low-latency, sound quality, battery, listening duration | [`docs/notification_status.jpg`](./docs/notification_status.jpg) |
| Auto low-latency / game mode after reconnecting to phone | [`docs/demo_auto_low_latency.mp4`](./docs/demo_auto_low_latency.mp4) |
| Switch ANC without opening the app | [`docs/demo_anc_without_app.mp4`](./docs/demo_anc_without_app.mp4) |

---

## Supported / planned-to-verify devices

> “Supported” means the app already has model detection and capability tables. Actual behavior may vary by firmware, region, and Android Bluetooth stack.
> **Main tested device: FreeBuds 6i. Other models need tester feedback.**

| Device | Status | Notes |
|--------|--------|-------|
| HUAWEI FreeBuds 6i | Tested | Main development device; ANC, gestures, battery, low-latency, and sound preference are being continuously tuned |
| HUAWEI FreeBuds 7i | Temporary conservative profile, full adaptation pending | v3.7.2 keeps a reduced capability table to lower initialization pressure and temporarily hides the unverified auto-pause option; full 7i support will continue with testers in the next major compatibility round for more models and vendors |
| HUAWEI FreeBuds 5i | Capability table ready, needs testing | ANC, ANC level, gestures, sound preference, low-latency |
| HUAWEI FreeBuds 4i / HONOR Earbuds 2 / 2 Lite / SE | Capability table ready, needs testing | Basic ANC, battery, wear detection, double/long tap, auto-pause |
| HUAWEI FreeBuds Pro | Capability table ready, needs testing | ANC, voice boost, swipe/long press, dual-connect capabilities may vary |
| HUAWEI FreeBuds Pro 2 | Capability table ready, needs testing | ANC, gestures, sound preference, EQ, dual-connect need verification |
| HUAWEI FreeBuds Pro 3 / Pro 4 / FreeClip | Capability table ready, needs testing | Newer devices with more capabilities; tester feedback is especially useful |
| HUAWEI FreeBuds SE / SE 2 / SE 4 | Capability table ready, needs testing | SE models differ significantly in available capabilities |
| HUAWEI FreeBuds Studio | Capability table ready, needs testing | Headphones; battery and wearing behavior differ from TWS earbuds |
| HUAWEI FreeLace Pro / Pro 2 | Capability table ready, needs testing | Neckband devices; some TWS features do not apply |
| Other HUAWEI / HONOR Earbuds | Can try generic connection | Unknown models use generic handlers; logs are welcome |

---

## Testers wanted

If you own any of the devices above, especially **FreeBuds 5i / Pro series / SE series / FreeClip / FreeLace**, testing feedback is very helpful.

Please include:

1. Earbud model and firmware version
2. Phone model, Android version / ROM
3. App version (v3.7.2+ logs include it automatically)
4. Which features work and which do not
5. Exported log from the in-app “Share log” action
6. For connection issues, whether Android system Bluetooth already shows the earbuds as connected

Feedback:

- GitHub Issues: <https://github.com/ct-yx/fxxkHilife/issues>
- Releases: <https://github.com/ct-yx/fxxkHilife/releases>

---

## Known limitations

- The control channel currently depends on classic Bluetooth SPP; some Android ROMs may restrict background Bluetooth behavior.
- Different firmware versions may respond differently to the same commands; untested models may have partial handler initialization failures.
- Battery, ANC, and gesture capabilities are filtered by model, but the capability table still needs calibration from real devices.
- EQ Preset / Custom EQ and Dual Connect are known protocol/capability targets, but UI and stable write flows are not complete yet.
- This is not an official app and does not guarantee feature parity with the official HiLife / AI Life app.

---

## Build

```bash
git clone https://github.com/ct-yx/fxxkHilife.git
cd fxxkHilife
./gradlew :app:assembleDebug
```

Release APKs are built and published automatically by GitHub CI.

---

## Disclaimer

For educational and personal research purposes only. Commercial use is prohibited.
Use at your own risk. This project is not affiliated with HUAWEI or HONOR.
