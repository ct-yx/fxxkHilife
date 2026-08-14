# Third-party notices

## OpenFreebuds

This repository contains a versioned reference snapshot of the upstream [OpenFreebuds](https://github.com/melianmiko/OpenFreebuds) project at commit `035bf2a87cdda9e9c8e8e90c662b7fa61270c6ee`:

```text
reference/openfreebuds/
```

The snapshot is licensed under the GNU General Public License, version 3 (GPL-3.0). The complete upstream license text is retained at:

```text
reference/openfreebuds/LICENSE
```

The snapshot is included for protocol, device-model, and handler research. It is not part of the Android Gradle source sets, is not compiled into the APK, and is not loaded by the application at runtime. The Android implementation is maintained as a separate Kotlin implementation; see [docs/UPSTREAM_OPENFREEBUDS.md](docs/UPSTREAM_OPENFREEBUDS.md) for the source mapping and update procedure.

If this repository later copies, compiles, links, or distributes identifiable OpenFreebuds code beyond the reference snapshot, the licensing, modification-marking, corresponding-source, and other GPL-3.0 obligations must be reviewed again before distribution.

## Other assets

The Android app also includes third-party ANC icons. Their attribution is shown in the in-app Settings > Other credits section and in the localized I18n entries. These assets are separate from the OpenFreebuds GPL-3.0 snapshot.
