# M0 — Technical spikes

- Status: Ready
- Depends on: None
- Produces: Feasibility evidence, Android skeleton, foundational ADRs
- Next milestone: M1 — Radio and survey foundation

<!-- REQUIREMENTS: -->

## Objective

Retire the major feasibility risks before full application construction. Build
the smallest end-to-end prototypes needed to prove Android can open this HackRF,
sustain useful receive rates, run a user-visible location/USB foreground service,
persist representative observations, and render them on an offline-capable map.

This is an evidence milestone. Prototype code may become production code only
when its ownership, error handling, tests, and safety boundaries meet the
repository standards.

## Inputs

- `AGENTS.md` and `ROADMAP.md`.
- Application specification, especially sections 12, 20, 21, 23, and 24.
- HackRF One with firmware 2026.01.3.
- Android phone model, Android version, CPU ABI, free storage, and USB-C support.
- Powered USB-C OTG hub or equivalent stable topology.
- Windows tools already installed by this repository for independent radio and
  capture verification.

If the target phone or powered USB topology is unavailable, software scaffolding
may proceed, but M0 cannot be marked complete.

## Decisions this milestone must make

Create accepted ADRs for:

1. Android project structure, Gradle/toolchain versions, minimum SDK, and ABIs.
2. Native libhackrf/libusb build, packaging, and redistribution strategy.
3. Android USB permission and file-descriptor integration approach.
4. Foreground service ownership and process-lifecycle model.
5. MapLibre API, tile source, attribution, and offline-region approach.

Every decision must cite measured results or authoritative platform constraints.

## Deliverables

- `android/` Gradle project with Kotlin, Compose, NDK support, CI-suitable unit
  tests, and the module boundaries required by `AGENTS.md` or an ADR explaining
  a narrower spike layout and migration plan.
- Receive-only Kotlin/JNI prototype that can enumerate, open, read information,
  stream RX, stop, and close one HackRF.
- A debug-only hardware screen showing device identity, USB permission state,
  stream rate, bytes received, overruns, and errors.
- Foreground-service prototype combining an active USB session with foreground
  precise location and a persistent notification.
- Map prototype that loads stored synthetic observations and supports an offline
  region.
- `docs/evidence/M0/benchmark-report.md` with reproducible measurements.
- Accepted foundational ADRs under `docs/adr/`.
- `docs/evidence/M0/go-no-go.md` with explicit outcomes for every spike question.

## Tasks

### 1. Inventory the target

- Record phone manufacturer/model, Android build, ABI, memory, storage, and
  battery condition.
- Diagram the USB cable, hub, charger, and HackRF connection.
- Confirm Android recognizes USB host mode and grants device permission.
- Record HackRF model, firmware, API, and serial suffix locally; redact the full
  serial from committed evidence.

### 2. Scaffold the Android project

- Use Kotlin and Jetpack Compose.
- Set minimum API 29 and target the latest stable installed SDK unless the ADR
  records a justified change.
- Establish reproducible Gradle wrapper, dependency versions, lint, unit-test,
  and debug-build commands.
- Add CI that does not require physical hardware.
- Keep hardware tests in a separately invocable source set or task.

### 3. Prove the native receive-only boundary

- Build libhackrf and required USB components for selected ABIs.
- Expose only enumerate, open, device information, start sweep/RX, stop, and
  close operations through JNI.
- Add a static check that application-facing symbols and Kotlin interfaces do
  not expose transmit operations.
- Validate native lengths, rates, frequency ranges, and buffer ownership.
- Prove cancellation, close, and USB detach release native resources.

### 4. Benchmark USB receive throughput

- Measure sustained 2, 4, and 8 MS/s RX for at least five minutes each.
- Count delivered bytes, transfer errors, overruns, and process/native crashes.
- Repeat the highest successful rate with the screen off for 15 minutes.
- Compare powered and unpowered topology only when safe for the phone and radio.
- Record CPU load, battery trend, and thermal state when available.

### 5. Prove sweep parsing

- Receive sweep frames from the device.
- Parse frequency headers and power bins into a platform-independent structure.
- Store a short sanitized fixture suitable for later automated tests.
- Cross-check at least one sweep range with the Windows `hackrf_sweep` tool.

### 6. Prove foreground service behavior

- Start the service only from a visible activity.
- Declare connected-device and location service behavior required by the target
  Android SDK.
- Show a persistent notification with Stop.
- Continue USB RX plus location with the screen off.
- Verify denial, detach, app swipe-away, and service-stop behavior.
- Avoid background-location permission unless measured behavior proves it is
  necessary and an ADR accepts the privacy consequence.

### 7. Prove mapping

- Persist synthetic observations with accuracy values.
- Render route, accuracy, and relative-strength cells in MapLibre.
- Download and reopen one small offline region.
- Verify map attribution remains visible.
- Record tile-source licensing and network/privacy behavior.

### 8. Decide go/no-go

For each technical risk, record `go`, `go with constraint`, or `no-go` plus its
evidence. If direct Android USB is a no-go, do not silently pivot; write an ADR
that evaluates a Raspberry Pi RF-head architecture and update the specification
and roadmap only after the decision is accepted.

## Acceptance criteria

- [ ] The Android project builds from a clean checkout using documented commands.
- [ ] Unit tests and lint run without physical hardware.
- [ ] The target phone grants USB permission and reads correct HackRF identity.
- [ ] No application-facing native or Kotlin API exposes transmit behavior.
- [ ] At least 2 and 4 MS/s sustain for five minutes with zero unexplained data
  loss; 8 MS/s has measured pass/fail evidence.
- [ ] A 15-minute screen-off run preserves USB RX, location, and notification
  controls or documents a platform constraint with an accepted response.
- [ ] USB detach and cancellation close the device without a process restart.
- [ ] Sweep frames produce verified frequency/power observations.
- [ ] Stored observations render on a map with accuracy and an offline region.
- [ ] All five foundational decisions have accepted ADRs.
- [ ] Benchmark and go/no-go reports identify the exact tested hardware and
  software configuration.
- [ ] `scripts/Test-Planning.ps1` passes after roadmap and handoff updates.

## Validation

Run from `android/` unless the project ADR specifies otherwise:

```powershell
.\gradlew.bat lint test assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

Run from the repository root:

```powershell
.\scripts\Test-Planning.ps1
```

Document any hardware command or manual sequence in the benchmark report. A
physical-device criterion cannot be satisfied by an emulator.

## Out of scope

- Production survey persistence and UI.
- Detection or fingerprint clustering.
- Geographic aggregation beyond the map spike.
- User-facing IQ capture or export.
- App-store release work.

## Handoff

Complete this section before marking M0 complete:

- Commit and branch:
- Delivered behavior:
- Validation summary:
- Hardware evidence:
- ADRs:
- Known limitations:
- M1 starting point and cautions:
