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

## prism_ui

The Flutter presentation module depends on [prism_ui](https://github.com/chen08209/prism_ui) version `0.1.3`:

```text
flutter_ui/pubspec.yaml
```

`prism_ui` is licensed under the GNU General Public License, version 3 (GPL-3.0). Its license and source notices are available in the package repository at [LICENSE](https://github.com/chen08209/prism_ui/blob/0.1.3/LICENSE) and [NOTICE](https://github.com/chen08209/prism_ui/blob/0.1.3/NOTICE). The package notice also identifies adapted code under Apache-2.0, MIT, and BSD-3-Clause licenses; those notices remain part of the dependency and must accompany redistributed builds.

## Other assets

The Android app also includes third-party ANC icons. Their attribution is shown in the in-app Settings > Other credits section and in the localized I18n entries. These assets are separate from the OpenFreebuds GPL-3.0 snapshot.
