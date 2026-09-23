# M5 dependency, vulnerability, and license review

Reviewed 2026-09-23 against the resolved `releaseRuntimeClasspath`, native
source pins, packaged notices, and public advisory sources.

## Resolved release inventory

| Component | Resolved version | Use | License/disposition |
| --- | --- | --- | --- |
| Kotlin | 2.3.21 | language/runtime | Apache-2.0; accepted |
| kotlinx-coroutines-android | 1.10.2 | concurrency | Apache-2.0; accepted |
| Android Gradle Plugin | 9.4.0 | build only | Apache-2.0; accepted |
| Kotlin Symbol Processing | 2.3.10 | build only | Apache-2.0; accepted |
| AndroidX Activity Compose | 1.12.4 | activity/UI | Apache-2.0; accepted |
| AndroidX Lifecycle | 2.9.4 | lifecycle | Apache-2.0; accepted |
| AndroidX Room | 2.8.4 | local database | Apache-2.0; accepted |
| Compose BOM | 2026.06.01 | version alignment | Apache-2.0 components; accepted |
| Compose UI / Material 3 | 1.11.4 / 1.4.0 | UI | Apache-2.0; accepted |
| MapLibre Native OpenGL | 13.6.1 | maps/offline regions | BSD-2-Clause and bundled notices; accepted |
| MapLibre Turf/GeoJSON | 6.0.1 | map geometry | bundled MapLibre notices; accepted |
| MapLibre Android Gestures | 0.0.4 | transitive map gestures | BSD-family terms in MapLibre Android compendium; accepted |
| Gson | 2.10.1 | transitive JSON | Apache-2.0; accepted |
| OkHttp / Okio | 4.12.0 / 3.6.0 | map networking | Apache-2.0; accepted |
| Timber | 5.0.1 | transitive logging | Apache-2.0; accepted; protected-field review below |
| libusb | 1.0.29 / pinned commit | USB adapter | LGPL-2.1-or-later; source/relink material required |
| libhackrf | 2026.01.3 / pinned commit | HackRF receive host | BSD-3-Clause; accepted |

The resolved `releaseRuntimeClasspath` contains 131 external coordinates when
multiplatform metadata modules, BOMs, and Android variants are counted
separately. The complete version families are: AndroidX Activity 1.12.4,
Annotation 1.9.1/Experimental 1.5.0, Arch 2.2.0, Autofill 1.0.0, Collection
1.5.0, Compose 1.11.4 with Material3 1.4.0, Concurrent 1.1.0, Core 1.17.0,
CustomView 1.0.0, DocumentFile 1.0.0, DynamicAnimation 1.0.0, Emoji2 1.4.0,
Fragment 1.8.9, Graphics Path 1.0.1, Lifecycle 2.9.4, NavigationEvent 1.0.2,
ProfileInstaller 1.4.0, Room 2.8.4, SavedState 1.3.2, SQLite 2.6.2, Startup
1.1.1, Tracing 1.2.0, Transition 1.6.0, Window 1.5.0, Kotlin stdlib 2.3.21
(legacy JDK shims 1.9.10), coroutines 1.10.2, serialization core 1.7.3,
JetBrains annotations 23.0.0, JSpecify 1.0.0, Gson 2.10.1, Guava
ListenableFuture 1.0, OkHttp 4.12.0, Okio 3.6.0, Timber 5.0.1, MapLibre
OpenGL 13.6.1, GeoJSON/Turf 6.0.1, and MapLibre gestures 0.0.4. Gradle's
dependency report is authoritative for the individual variant coordinates;
the reproduction command below regenerates it without a curated filter.

Test-only JUnit and AndroidX test artifacts are not shipped. Checksums for the
native source archives are recorded in
`android/radio-hackrf-native/THIRD_PARTY_NOTICES.md`. A consolidated notice is
packaged at `assets/THIRD_PARTY_NOTICES.md`. The release set also contains the
notice files, the exact application source revision, and checksum-pinned full
source archives for libusb and HackRF, plus MapLibre's exact Android license
compendium from the `android-v13.6.1` tag. The libusb archive contains the complete
LGPL-2.1-or-later text and preferred source; the project source archive contains
the Android build, local adapter patch, and relinking instructions. Together
these are the corresponding source/relinking materials for the private binary
distribution. The HackRF source archive contains its applicable source-file
copyright/license notices.

## Vulnerability disposition

An OSV API query on 2026-09-22 returned **zero known advisories** for the exact
Maven versions of Kotlin stdlib, coroutines, Activity, Lifecycle, Room,
MapLibre OpenGL/Turf, OkHttp, Okio, Gson, and Timber, and zero for the exact
GitHub release purls for libusb 1.0.29 and HackRF 2026.01.3. This is a point-in-
time advisory check, not a proof of absence; rerun it for every release.

MapLibre 13.6.1 is the current stable Android API used by the project and its
[security policy](https://github.com/maplibre/maplibre-native/blob/main/SECURITY.md)
defines private reporting. HackRF `2026.01.3` is the pinned upstream
[release](https://github.com/greatscottgadgets/hackrf/releases). No dependency
was upgraded merely to silence a scan, and no reported issue was waived.

## Service and privacy review

OpenFreeMap's public instance permits application integration and requires the
project's visible map-data attribution, but is provided as-is without an SLA and
may change or end. Its terms (last updated 2026-09-09) and current
[terms](https://openfreemap.org/tos/) and
[privacy policy](https://openfreemap.org/privacy/) were reviewed on
2026-09-22. The app displays `OpenFreeMap © OpenMapTiles Data from
OpenStreetMap`, confines basemap access to the maps module, and never embeds
private RF observations in tile requests. Cached maps and the equivalent list
are the availability fallback.

## Reproduction

```powershell
Push-Location android
.\gradlew.bat :app-ui:dependencies --configuration releaseRuntimeClasspath
.\gradlew.bat assembleRelease
Pop-Location
.\scripts\Test-M5Dependencies.ps1
.\scripts\Test-M5Release.ps1
```

The release review found no critical/high dependency issue and no license that
conflicts with private sideload distribution under ADR 009.
