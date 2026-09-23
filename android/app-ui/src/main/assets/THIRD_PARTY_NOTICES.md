# RF Field Notebook third-party notices

The source revision and release companion material identify exact versions and
rebuild instructions. Copyright notices and license texts supplied inside AARs
remain part of those distributions.

The private release set accompanies this notice with MapLibre Native Android's
full license compendium from tag `android-v13.6.1` and checksum-pinned complete
source archives for libusb 1.0.29 and HackRF 2026.01.3. Those archives contain
the applicable LGPL and BSD terms; the application source archive contains the
Android adapter changes and relinking build.

| Component | Version | License |
| --- | --- | --- |
| Kotlin / Kotlin coroutines | 2.3.21 / 1.10.2 | Apache-2.0 |
| Android Gradle Plugin | 9.4.0 (build-time) | Apache-2.0 |
| AndroidX Activity, Lifecycle, Room, Test, Compose | pinned by build files; Compose BOM 2026.06.01 | Apache-2.0 |
| MapLibre Native Android OpenGL | 13.6.1 | BSD-2-Clause; bundled notices apply |
| Gson | 2.10.1 | Apache-2.0 |
| OkHttp / Okio | 4.12.0 / 3.6.0 (transitive) | Apache-2.0 |
| Timber | 5.0.1 (transitive) | Apache-2.0 |
| libusb | 1.0.29, commit `15a7ebb4d426c5ce196684347d2b7cafad862626` | LGPL-2.1-or-later |
| libhackrf host library | 2026.01.3, commit `01f7c8b3509e308c5e17b77a8ed2dbb594a98860` | BSD-3-Clause |

libusb and libhackrf are checksum-pinned and linked privately into the Android
receive-only adapter. Corresponding source, local modifications, build scripts,
and relinking instructions are available in the exact RF Field Notebook source
revision distributed with this APK. See
`android/radio-hackrf-native/THIRD_PARTY_NOTICES.md` and its CMake build.

Basemap attribution shown in the map is:
`OpenFreeMap © OpenMapTiles Data from OpenStreetMap`.
