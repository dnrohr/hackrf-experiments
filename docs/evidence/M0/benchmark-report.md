# M0 benchmark report

- Evidence status: **Hardware measurements pending**
- Recorded: 2026-09-13
- Repository host: Windows 11, PowerShell, JDK 17.0.19
- Android build stack: SDK 36, Build Tools 36.0.0, NDK 28.2.13676358,
  CMake 3.22.1, Gradle 9.6.0, AGP 9.4.0

This report intentionally distinguishes build evidence gathered on the
repository host from physical-device evidence that has not been gathered.

## Target inventory

| Item | Measured value |
| --- | --- |
| Phone manufacturer/model | Not available to this run |
| Android build / ABI | Not available; no `adb` device attached |
| Phone memory/free storage/battery | Not measured |
| USB-C hub, cable, and charger | Not provided |
| Android USB-host recognition/permission | Not measured |
| HackRF model/firmware/API/serial suffix | Not measured; Windows reports no attached HackRF |

The required physical topology diagram will be added here when named hardware
is available:

```text
charger -> powered USB-C OTG hub -> target Android phone
                              +--> data cable -> HackRF One -> suitable RX antenna
```

## Build and deterministic measurements

| Question | Method | Result |
| --- | --- | --- |
| Android/Compose project compiles | `gradlew.bat test assembleDebug` | Pass |
| Native boundary compiles | CMake/NDK for arm64-v8a and x86_64 during assemble | Pass |
| Pinned native provenance | libusb v1.0.29 and libhackrf v2026.01.3 archives verified by SHA-256 during CMake configuration | Pass |
| Receive-only source surface | `scripts/Test-ReceiveOnly.ps1` | Pass |
| Packaged native surface | `scripts/Test-NativeExports.ps1` checks both stripped ABI libraries | Pass: only nine project-owned RX JNI symbols exported |
| Raw sweep processing | synthetic HackRF device block with `0x7f7f`/64-bit frequency header plus malformed-block unit tests | Pass: frequency ranges and DFT power bins produced |
| Synthetic observation persistence | numeric representation round-trip and malformed-record unit tests | Pass |
| Foreground evidence controls | debug UI and merged manifest inspection | RX/sweep rate selection, identity, duration, expected/actual bytes, processing drops, native errors, location accuracy/age, and notification Stop are wired |
| Map/offline evidence controls | MapLibre compilation and source inspection | Persisted observations drive route, accuracy-size, and strength-color/size layers; action downloads or reopens a saved offline region |
| Connected test discovery | `gradlew.bat :app-ui:connectedDebugAndroidTest` | Blocked: `No connected devices!` |

These results do not substitute for opening or streaming from a HackRF.

## Required RX measurements

Use fixed gain with RF amplifier and antenna power off. For each run record
wall/monotonic start and end, delivered bytes, expected bytes, native/USB
errors, overruns, dropped units, process crashes, CPU load, battery trend, and
thermal status.

| Rate | Required duration | Screen | Topology | Result |
| --- | ---: | --- | --- | --- |
| 2 MS/s | 5 minutes | on | powered | Pending |
| 4 MS/s | 5 minutes | on | powered | Pending |
| 8 MS/s | 5 minutes | on | powered | Pending |
| highest passing | 15 minutes | off | powered | Pending |
| highest passing | optional | on | unpowered, only if safe | Pending |

For signed 8-bit interleaved IQ, expected byte count is
`sampleRateHz * 2 * elapsedSeconds`. Any difference must be reconciled with
explicit gap/error counters; zero unexplained loss is the gate.

## Physical validation sequence

1. Record the target inventory and topology above; never connect a transmitter
   directly to the HackRF input.
2. Run `scripts/Test-HackRF.ps1` on Windows and redact the serial to a suffix.
3. Build/install the debug APK, select 2, 4, or 8 MS/s, and start the test only
   from its visible screen. Record the native identity, byte rate, byte count,
   and callback-error count shown by the app.
4. Deny then grant USB/location/notification permissions and record behavior.
5. Execute the rate table, then the screen-off run with the notification Stop
   action available throughout.
6. During an active run, detach USB; verify a timestamped error, clean close,
   and a living process. Repeat with cancellation and reattachment.
7. Start the app's sweep test, confirm power-frame and malformed-block counters,
   capture a short sanitized raw block fixture, and cross-check its requested
   frequency range and power shape against Windows `hackrf_sweep` output.
8. Download the displayed map region, disable network access, relaunch, and
   record route/accuracy/strength overlays plus visible attribution.

## Current blocker

No target Android phone, powered USB topology, or attached HackRF was available
on 2026-09-13. `adb devices -l` returned an empty device list and the connected
test failed with `No connected devices!`. Therefore no throughput, identity,
detach, screen-off, location, screenshot/video, or offline-map behavior is
claimed.
