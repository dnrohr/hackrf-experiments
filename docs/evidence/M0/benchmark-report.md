# M0 benchmark report

- Evidence status: **Complete**
- Recorded: 2026-09-13
- Repository host: Windows 11, PowerShell, JDK 17.0.19
- Android build stack: SDK 36, Build Tools 36.0.0, NDK 28.2.13676358,
  CMake 3.22.1, Gradle 9.6.0, AGP 9.4.0

This report separates measured physical-device results from the final inventory
gate.
Full Android and HackRF serials and precise live coordinates are intentionally
excluded.

## Target inventory

| Item | Measured value |
| --- | --- |
| Phone | Google Pixel 8a (`akita`) |
| Android build / ABI | Android 17, API 37; build `CP2A.260805.005`; arm64-v8a |
| Phone memory | Approximately 7.75 GB total |
| Phone storage | `/data` 110 GB total, approximately 25 GB free at inventory |
| Battery | 78% at inventory; healthy enough for powered bench runs |
| USB topology | Pixel USB-C port -> unmarked USB-C-male/USB-A-female adapter -> unmarked USB-A-male/Micro-USB-B-male data cable -> HackRF One Micro-USB-B port; no hub or charger |
| Android USB host/permission | USB host feature present; system permission granted to the app |
| HackRF | HackRF One older than r6; firmware 2026.01.3; USB API 1.10; serial suffix `84224b` |

Tested topology:

```text
Pixel 8a USB-C port
  -> unmarked USB-C male / USB-A female adapter
  -> unmarked USB-A male / Micro-USB-B male data cable
  -> Great Scott Gadgets HackRF One -> RX antenna

Windows host <- Wi-Fi ADB -> Pixel 8a (during RF measurements)
```

The phone directly hosted and bus-powered the HackRF; no hub, external charger,
or other USB power source was in the tested RF path. The cable and adapter have
no model markings available, so the connector sequence above is their exact
recorded identification. Windows `scripts/Test-HackRF.ps1` passed before
Android testing. Four other devices shared the reported Windows USB bus during
that independent host check; they were not part of the Android RF topology.

## Build and deterministic measurements

| Question | Method | Result |
| --- | --- | --- |
| Android/Compose project compiles | `gradlew.bat test assembleDebug` | Pass |
| Native boundary compiles | CMake/NDK for arm64-v8a and x86_64 during assemble | Pass |
| Pinned native provenance | libusb v1.0.29 and libhackrf v2026.01.3 archives verified by SHA-256 during CMake configuration | Pass |
| Receive-only source surface | `scripts/Test-ReceiveOnly.ps1` | Pass |
| Packaged native surface | `scripts/Test-NativeExports.ps1` checks both stripped ABI libraries | Pass: only project-owned RX JNI symbols exported |
| Android connected tests | Pixel 8a, Android 17 | Pass: USB-host, location-denial foreground-service, and activity-task-removal gates report no failures. A direct instrumentation run reports `OK (3 tests)`; the required Gradle connected task completed successfully over direct USB ADB on 2026-09-14. |
| Android libusb initialization | Physical open after disabling libusb device discovery and wrapping Android's permitted descriptor | Pass; avoids SELinux-denied `/dev/usb` and sysfs enumeration |
| Sweep processing | Physical sweep plus deterministic malformed-block tests | Pass: 1,504 power frames in 8 seconds, zero malformed blocks and callback errors |
| Sanitized fixture | `m0-hackrf-sweep-frame.bin`, encoded from the first processed physical frame | Pass: 36 bytes, 88.0–88.5 MHz, five rounded finite power bins, no timestamp, location, or identity |
| Synthetic observation persistence | Numeric representation round-trip and malformed-record unit tests | Pass |
| Map/offline behavior | Pixel 8a physical run with MapLibre OpenGL | Pass: overlays and attribution visible; 309 resources / 8,637,828 bytes downloaded and reopened |

## USB RX measurements

All runs used fixed zero LNA/VGA gain, RF amplifier off, and antenna-port power
off. `bytes` is cumulative USB-delivered data; the M0 spike retains only one
262,144-byte latest buffer and does not write IQ to disk. A counted overwrite
therefore means downstream spike processing did not consume a block, not that
the reported byte count consumed equivalent storage.

| Rate | Duration / screen | Final delivered bytes | Counted latest-buffer overwrites | Native callback errors | Battery / thermal | Result |
| --- | --- | ---: | ---: | ---: | --- | --- |
| 2 MS/s | 344 s, on | 1,377,304,576 | 2 | 0 | Not captured | Pass; final expected-byte delta was -151,424 bytes |
| 4 MS/s | at least 300 s, on | 2,594,177,024 | 3 | 0 | 74→72%; 30.7→31.3 °C | Pass |
| 8 MS/s | at least 300 s, on | 5,122,293,760 | 11 | 0 | 71→69%; 31.6→32.3 °C | Pass with production-queue constraint |
| 8 MS/s | 900 s continuously non-interactive (`Dozing`) | 20,086,259,712 cumulative at end | 1,234 cumulative | 0 observed | 62% at end; 31.1 °C | Pass with production-queue constraint |

The screen-off monitor sampled Android wakefulness every ten seconds and reset
its evidence clock if the device returned to `Awake`. The service process,
foreground notification, USB stream, and high-accuracy location registration
remained active for the full accepted 900-second window. CPU load was not
captured and is a limitation of this M0 evidence.

There was no unexplained loss or native crash in these runs: the native byte
counter advanced continuously, callback errors remained zero, and every
one-slot handoff overwrite was counted and shown. The growing screen-off
overwrite count is not acceptable as the production design; it is direct
evidence for M1's required bounded multistage acquisition/processing queues.

## Lifecycle evidence

- The foreground service was started from the visible activity and retained a
  persistent RX-only notification with a working Stop action.
- Notification Stop cancelled RX, closed the native session, removed the
  notification, and left the app process alive.
- Physical detach during active 2 MS/s RX stopped the service, removed the
  notification, left the process alive, and displayed `USB detached; native
  session closed` with no stale device identity.
- Several stop/start cycles completed without reconnecting USB.
- A reattach without process restart originally exposed a stale UI-enumeration
  bug. On the patched build, physical detach changed the live activity to
  `HackRF not attached`; physical reattach changed it to `required`, and a new
  USB grant refreshed the redacted identity to suffix `84224b` without an app
  process restart.
- Explicit USB denial left the device named but showed `USB permission: denied`
  and withheld its serial. After physical reattach, Android presented a fresh
  permission dialog and the app reopened the device normally.
- With both location permissions revoked, an app-owned foreground-service start
  reports an error and stops without crashing after Android's service-start
  deadline. The service uses a permission-neutral `shortService` bootstrap on
  Android 14+ before promoting to its acquisition types.
- The phone's custom launcher did not expose Overview to ADB key/gesture input,
  so an instrumented physical-device gate invoked the activity's Android
  `finishAndRemoveTask()` path directly. After task removal, the independently
  owned foreground service remained in `receiving` and its USB byte count
  continued advancing. Test cleanup then stopped the service and removed the
  notification.

## Connected-test runner evidence

On Wi-Fi ADB, `:app-ui:connectedDebugAndroidTest` executes the Pixel suite and
produces XML with three tests, zero failures, zero errors, and zero skipped
tests. Its merged `test-result.pb` likewise records the suite and every test as
`PASSED`. Running the same `am instrument` command directly exits successfully
with `OK (3 tests)` and `INSTRUMENTATION_CODE: -1`.

AGP 9.4.0 nevertheless writes `test-result-exit-code.txt` as `1` for that
Wi-Fi run. Inspection of the locally resolved UTP 32.4.0 implementation
establishes the cause: the Android test engine places the URL-escaped Wi-Fi
serial `<device-ip>%3A5555` in the JUnit device segment, while the UTP work
action looks up the result with the raw ADB serial `<device-ip>:5555`. The
missing map entry is treated as failure even though the suite protobuf is
`PASSED`. A
single-test run of the trivial USB-host assertion reproduces the same mismatch,
separating it from application behavior. AGP 9.4.0 reports UTP as its only
runner, so the deprecated disable flag cannot avoid the defect. With the Pixel
connected directly to the Windows host, its colon-free USB ADB serial allowed
the unmodified `:app-ui:connectedDebugAndroidTest` task to complete with
`BUILD SUCCESSFUL` on 2026-09-14. `ANDROID_SERIAL` selected only that transport
while Wi-Fi ADB was also visible. Failure ignoring remains disabled, and the
full phone serial is intentionally omitted from committed evidence.

## Sweep evidence

The Android sweep requested 88–108 MHz at 2 MS/s with 100 kHz processing bins.
In an eight-second physical run it produced 1,504 processed power frames, zero
malformed device blocks, zero native callback errors, and a stable native
identity. The first processed frame was encoded locally after rounding powers
to whole dB. The committed 36-byte fixture contains only an 88.0–88.5 MHz
header and five power values (`-87, -69, -64, -63, -69` dBFS).

An independent Windows `hackrf_sweep` run completed with HackRF Tools
2026.01.3 using `-a 0 -p 0 -f 88:108 -l 0 -g 0 -w 100000 -N 10`. It produced
40 CSV rows spanning exactly 88–108 MHz with 98,039.22 Hz reported bins and 204
samples per row. This confirms range coverage and plausible finite power data;
bit-for-bit equality is neither expected nor claimed because desktop
`hackrf_sweep` uses its own 20 MS/s sweep implementation and the antenna/host
topology changed. The raw CSV remains in ignored `.state/m0/` because it
contains timestamped local RF observations.

## Map evidence

The original demo style caused a reproducible native SIGABRT on the Pixel's
`DatabaseFileSource` thread while starting an offline region. The abort was an
uncaught `std::regex_error` caused by unresolved glyph-template braces, matching
MapLibre Native issue 4403. The prototype now uses the MapLibre-documented
OpenFreeMap Liberty style URL and a versioned region identifier.

On the fixed build, the Pixel downloaded 309 resources totaling 8,637,828
bytes, reopened the saved region, rendered the synthetic route, accuracy halos,
and relative-strength markers, and visibly retained MapLibre attribution. The
local screenshots are stored under ignored `.state/m0/`; they contain only the
repository's synthetic observations.

## Known limitations

- The cable and adapter are unmarked, so connector type and position are the
  most specific reproducible inventory available.
- If practical in a later field-hardening pass, reopen the completed region
  with network disabled; the saved resource count and normal reopen path are
  already verified.
