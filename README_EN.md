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

> **Current development branch: `codex/prism-ui-rewrite`**
> The old Compose pages were removed from the production graph. The new presentation layer is rebuilt with Flutter Add-to-App and `prism_ui`.
> RFCOMM/SPP, model adapters, the foreground connection service and system tile remain native and are exposed through typed snapshots and semantic commands.

---

## Project home

Full introduction, download links, demos, and development logs are available on GitHub Pages:

<https://ct-yx.github.io/fxxkHilife/>

---

## Current development stage

- The Flutter pages, native UI interface and unified `EarbudRuntime` command seam are in place.
- GitHub Actions passes `flutter pub get`, `flutter analyze` and the UI contract.
- The toolchain is now aligned with Flutter 3.47: AGP 8.11.1, Gradle 8.14 and Kotlin 2.2.20; a new Actions run must verify both APK variants.
- Startup, scan, connection, read-back and background reconnect still need verification with the new APK.
- After cloud and device acceptance, `codex/prism-ui-rewrite` will become the GitHub default branch; old branches remain available.

---

## What is this?

fxxkHilife is a third-party, open-source, offline control panel for HUAWEI FreeBuds / HONOR Earbuds. It aims to restore everyday earbud controls without requiring account login, vendor ecosystem services, network access, ads, or data upload.

It communicates with earbuds directly through classic Bluetooth **SPP / RFCOMM**. The protocol work is based on [OpenFreebuds](https://github.com/melianmiko/OpenFreebuds). The presentation layer is rebuilt independently in Flutter `prism_ui`; native code owns connection, device state and system services.

The project is still evolving quickly. Testers with more earbud models are very welcome to help verify protocol compatibility.

---

## Key features

- **UI refactor**: a new Flutter `prism_ui` presentation layer separates device status, controls, gestures, listening statistics, settings and diagnostics from the native connection runtime.

- **Connection and auto-connect**: scan HUAWEI / HONOR earbuds and save known devices; the foreground service detects saved earbuds already connected by Android Bluetooth and opens the app SPP control channel with backoff.
- **ANC / Awareness / Off**: switch ANC modes from the in-app pill slider, Quick Settings Tile, or persistent notification actions; on third-party Android phones, you can cycle ANC directly from the system quick settings panel without opening the app.
- **Low-latency / game mode**: manual switch plus optional auto low-latency; once the SPP control channel is connected, the app gives its write priority, verifies with a read-back, and retries only within a 10-second bounded window.
- **Battery and wearing state**: left/right/case battery levels, charging state, wearing detection, auto-pause.
- **Gestures and audio preferences**: double tap, triple tap, long press, swipe gestures, sound quality vs connectivity preference, EQ preset read/display/write/read-back sync, and voice prompt language read/write.
- **Dual Connect MVP**: reads the earbud-side dual-connect toggle and paired-device list, displays preferred/playing/connected/auto-connect states, and supports toggling the dual-connect switch, preferred device, connect/disconnect, and auto-connect flags. Android-side control remains a single active SPP session, with UI showing both system Bluetooth state and control-channel state.
- **Persistent notification and logs**: notification shows ANC, low-latency, sound quality, battery, and listening duration; debug terminal can inspect raw SPP logs, export logs, and write properties.
- **UI experience**: Flutter `prism_ui` owns theme and navigation while the native layer exposes typed snapshots and semantic commands.

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
| HUAWEI FreeBuds 7i | Temporary conservative profile, full adaptation pending | A reduced capability table lowers initialization pressure and hides the unverified auto-pause option; full adaptation needs more hardware logs |
| HUAWEI FreeBuds 5i | Capability table ready, needs testing | ANC, ANC level, gestures, sound preference, low-latency |
| HUAWEI FreeBuds 4i / HONOR Earbuds 2 / 2 Lite / SE | Capability table ready, needs testing | Basic ANC, battery, wear detection, double/long tap, auto-pause |
| HUAWEI FreeBuds Pro | Capability table ready, needs testing | ANC, voice boost, swipe/long press, dual-connect capabilities may vary |
| HUAWEI FreeBuds Pro 2 | Capability table ready, needs testing | ANC, gestures, sound preference, EQ, dual-connect need verification |
| HUAWEI FreeBuds Pro 3 / Pro 4 / FreeClip | Capability table ready, needs testing | Newer devices with more capabilities; tester feedback is especially useful |
| HUAWEI FreeBuds SE / SE 2 / SE 4 | Capability table ready, needs testing | SE models differ significantly in available capabilities |
| HUAWEI FreeBuds Studio | Capability table ready, needs testing | Headphones; battery and wearing behavior differ from TWS earbuds |
| HUAWEI FreeLace Pro / Pro 2 | Capability table ready, needs testing | Neckband devices; some TWS features do not apply |
| Other HUAWEI / HONOR Earbuds | Can try scanning | Unknown models are not forced through every Handler; model, capability, and connection logs are welcome |

---

## Testers wanted

If you own any of the devices above, especially **FreeBuds 5i / Pro series / SE series / FreeClip / FreeLace**, testing feedback is very helpful.

Please include:

1. Earbud model and firmware version
2. Phone model, Android version / ROM
3. App version (v4.2.0+ logs include it automatically)
4. Which features work and which do not
5. Exported log from the in-app “Share log” action
6. For connection issues, whether Android system Bluetooth already shows the earbuds as connected

Feedback:

- GitHub Issues: <https://github.com/ct-yx/fxxkHilife/issues>
- Releases: <https://github.com/ct-yx/fxxkHilife/releases>

---

## Known limitations

- The control channel currently depends on classic Bluetooth SPP; some Android ROMs may restrict background Bluetooth behavior.
- Different firmware versions may respond differently to the same commands; untested models use conservative capabilities and need targeted verification for additional features.
- Battery, ANC, and gesture capabilities are filtered by model, but the capability table still needs calibration from real devices.
- EQ Preset now supports state reads, option display, built-in and verified compatibility preset writes, and read-back sync. Custom EQ currently only displays the custom rows and saved state reported by the earbuds; unknown custom payload writes are intentionally not sent.
- Dual Connect is an MVP: earbud-side dual-connect state is readable/writable, but Android control remains on one stable active SPP session. If a phone Bluetooth stack cannot maintain multiple SPP links reliably, the app degrades to system connection display plus current-device control.
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
