# RF Field Notebook Android application

This project contains the M4 receive-only field notebook. The application
discovers a permitted HackRF, versions equipment and band profiles, runs a
foreground spectrum survey, associates observations with location fixes, and
stores one-second spectrum aggregates plus explicit health and gap records.

## M4 focused IQ capture and bundles

From setup, grant HackRF USB permission and choose **Manual focused RX / IQ
capture**, or enter from a discovery detail. Start the focused preview first so
the app measures sustained USB delivery. Captures are manual, 0.25–30 seconds,
signed 8-bit interleaved I/Q (`.cs8`), and use a fixed 256 MiB storage reserve.
RF amplifier and antenna-port power remain off.

Completed captures live in app-private storage with JSON sidecars and PGM
previews. Review IQ, route, coordinate, note, and device-suffix choices before
opening Android's share sheet. Share archives in cache expire after 24 hours.
Use **Import validated survey bundle** for same-version bundles; unsafe or
unsupported archives are rejected before any imported directory is committed.
Desktop validation commands are in
`docs/evidence/M4/schema-and-interoperability.md`.

## M2 discovery interfaces

`signal-processing` owns `detector-v1` and `cluster-v1`. Its inputs are
one-second aggregate frames scoped to an exact equipment-profile version; its
outputs are immutable detections and deterministic fingerprint summaries.
Artifact flags preserve suspicious observations rather than deleting them.

`storage` schema version 3 adds derived detections, fingerprints, ranked hints,
correction provenance, and reprocessing jobs. `DiscoveryRepository` can
reprocess a completed M1 survey entirely offline. Derived rows may be replaced;
the source M1 aggregates remain unchanged. Split/merge corrections reassign
source detection IDs transactionally and retain an audit record.

The app's Discoveries and Signal detail pages expose empty, processing,
failure/partial, large-list, timeline, evidence, and reversible review states.
Map and focused-IQ extension points remain explicit and inactive until M3/M4.
Pixel 8a connected-test and screenshot evidence is recorded under
`../docs/evidence/M2/ui-review.md`.

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
