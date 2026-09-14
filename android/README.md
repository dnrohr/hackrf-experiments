# RF Field Notebook Android application

This project contains the M1 receive-only survey foundation. The application
discovers a permitted HackRF, versions equipment and band profiles, runs a
foreground spectrum survey, associates observations with location fixes, and
stores one-second spectrum aggregates plus explicit health and gap records.

The `radio-api` surface deliberately exposes no transmit operation. `SweepConfig`
accepts one to ten non-overlapping ranges, fixed gains, a measured sample rate,
and its matching baseband filter. The native adapter applies those exact settings
and turns the RF amplifier and antenna-port power off on every open, failed start,
stop, and close path.

## Prerequisites

- JDK 17
- Android SDK 36 with Build Tools 36.0.0
- Android NDK 28.2.13676358 and CMake 3.22.1

From this directory, run:

```powershell
.\gradlew.bat lint test assembleDebug
.\gradlew.bat connectedDebugAndroidTest # physical target only
```

Then, from the repository root, audit the packaged ABI exports:

```powershell
.\scripts\Test-NativeExports.ps1
.\scripts\Test-ReceiveOnly.ps1
.\scripts\Test-Planning.ps1
```

The CI-safe build and static audits require no radio. A connected test is evidence
only when its report names the target phone and actual USB topology. M1 hardware
results and schema notes live under `../docs/evidence/M1/`.
