# RF Field Notebook Android spike

This is the Milestone 0 Android feasibility project. It deliberately exposes a
receive-only radio boundary; hardware-dependent behavior remains a debug spike
until the evidence in `../docs/evidence/M0/` is complete.

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
```

The CI-safe build and export audit require no radio. The connected test is evidence
only when run on the named target phone with the physical HackRF topology.
