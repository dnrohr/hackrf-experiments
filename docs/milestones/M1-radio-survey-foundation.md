# M1 — Radio and survey foundation

- Status: Blocked by M0
- Depends on: M0 — Technical spikes
- Produces: Reliable receive-only survey platform and persisted observations
- Next milestone: M2 — Detection and fingerprinting

<!-- REQUIREMENTS: FR-USB-001, FR-USB-002, FR-USB-003, FR-USB-004, FR-USB-005, FR-USB-006, FR-USB-007, FR-EQP-001, FR-EQP-002, FR-EQP-003, FR-EQP-004, FR-BAND-001, FR-BAND-002, FR-BAND-003, FR-BAND-004, FR-BAND-005, FR-LOC-001, FR-LOC-002, FR-LOC-003, FR-LOC-004, FR-LOC-005, FR-ACQ-001, FR-ACQ-002, FR-ACQ-003, FR-ACQ-004, FR-ACQ-005, FR-ACQ-006, NFR-PERF-001, NFR-PERF-002, NFR-PERF-003, NFR-PERF-004, NFR-PERF-005, NFR-PERF-006 -->

## Objective

Turn the successful M0 prototypes into a production-quality foundation. A user
must be able to connect one HackRF, select immutable/versioned equipment and
band profiles, start a foreground survey, walk with the screen off, and finish
with trustworthy GPS-tagged spectrum aggregates plus a complete health record.

M1 stops at observation storage. It does not decide which observations are
interesting; that belongs to M2.

## Inputs

- Completed M0 brief and evidence.
- All accepted M0 ADRs.
- M0 Android project and receive-only native boundary.
- Application specification sections 7–9.5, 10, 12–15, and 20.
- Physical target phone, powered USB topology, and HackRF.
- Sanitized sweep fixture captured during M0.

## Primary requirement ownership

M1 owns all `FR-USB`, `FR-EQP`, `FR-BAND`, `FR-LOC`, `FR-ACQ`, and `NFR-PERF`
requirements listed in the metadata above. The agent must create a traceability
table in `docs/evidence/M1/requirements.md` linking every ID to implementation,
tests, and evidence.

## Deliverables

- Stable production implementations of `radio-api`, `radio-hackrf-native`,
  `acquisition-service`, `storage`, and relevant `app-ui`/`domain` flows.
- First-run and Equipment flows for USB permission, compatibility, radio test,
  equipment profiles, and band profiles.
- Persisted survey and connection state machines.
- Foreground acquisition service with screen-off operation and notification
  Pause/Resume/Stop controls.
- Location fixes, sweep observations, aggregates, gaps, and health counters in a
  migrated Room schema.
- Active-survey and completion-summary screens.
- Editable starter band profiles defined by the specification.
- `docs/evidence/M1/` containing requirement traceability, hardware run reports,
  schema notes, and failure-injection results.

## Tasks

### 1. Promote the radio boundary

- Replace spike-only shortcuts with explicit ownership and structured errors.
- Open by selected serial suffix and prevent two sessions for one device.
- Validate firmware/API compatibility before acquisition.
- Measure stream capacity and reject unsupported profile rates with a useful
  explanation.
- Handle permission denial, detach, stall, reset, cancellation, and close.
- Add static/build checks protecting the receive-only boundary.

### 2. Implement versioned equipment profiles

- Store radio, antenna, adapters, sample rate, filter, gains, amplifier, and
  antenna-power state.
- Default amplifier and antenna power off.
- Treat an edited profile as a new version once referenced by a survey.
- Provide a comparability function that returns both a result and reasons.
- Avoid claims that an antenna or power reading is calibrated.

### 3. Implement versioned band profiles

- Validate ranges, exclusions, resolution, revisit target, threshold, and
  minimum bandwidth.
- Reject overlapping or out-of-device ranges.
- Estimate cycle duration using measured M0 throughput.
- Create editable starter profiles without implying transmit authorization.
- Version profiles once used by a survey.

### 4. Implement the survey state machine

- Persist Draft, Validating, Active, Paused, Finalizing, Complete, and Failed.
- Make start and finalization idempotent.
- Record state changes with wall and monotonic times.
- Recover interrupted surveys after process death.
- Never leave the radio streaming outside Active state.

### 5. Implement foreground acquisition

- Start from a visible user action after permission/profile validation.
- Own radio and location lifecycles in the service.
- Use bounded native, processing, and persistence queues.
- Reduce UI work before dropping acquisition metadata.
- Count every malformed frame, overrun, dropped unit, stale fix, and service gap.
- Keep continuous wideband IQ disabled in survey mode.

### 6. Associate location and observations

- Store wall and monotonic timestamps.
- Attach the closest valid, fresh fix within a documented tolerance.
- Mark bounded short-gap interpolation explicitly.
- Preserve unlocated observations and explain them in survey summaries.
- Record GPS accuracy, provider, fix age, speed, and bearing when available.

### 7. Persist aggregates and health

- Store bounded time/frequency aggregates sufficient for M2 reprocessing.
- Keep raw sweep fixtures only when explicitly captured for diagnostics.
- Add migrations and migration tests from every committed schema version.
- Batch writes without risking a large loss window.
- Report storage estimate/reserve, USB rate, queue depth, battery, and thermal
  warnings without silently changing measurement parameters.

### 8. Build the user workflows

- First connection and receive test.
- Equipment and band profile create/edit/version views.
- Survey preflight with GPS and storage quality.
- Active survey with accessible status and notification actions.
- Pause/resume and orderly stop.
- Completion summary with explicit data-quality gaps.

## Acceptance criteria

- [ ] Every primary requirement ID has implementation, automated-test, and/or
  hardware-evidence links appropriate to its scope.
- [ ] A clean install can request USB permission, identify the HackRF, and run a
  receive test without exposing TX.
- [ ] Profile changes create versions and comparability warnings are correct.
- [ ] Invalid ranges, unsupported rates, and unsafe implicit settings are rejected.
- [ ] A 30-minute 902–928 MHz screen-off survey completes through the foreground
  service with a persistent notification.
- [ ] GPS fixes retain accuracy and stale/missing/interpolated states.
- [ ] USB detach produces a visible recoverable gap and closes native resources.
- [ ] Forced process interruption leaves a recoverable survey, not corrupt state.
- [ ] Overrun and queue-pressure injection produces counted, user-visible health
  events without silent setting changes.
- [ ] Database migration, state-machine, profile, service, and native lifecycle
  tests pass.
- [ ] M0 regression tests still pass.
- [ ] The M1 hardware report names all equipment, versions, duration, rates, and
  observed errors.
- [ ] `scripts/Test-Planning.ps1` passes after handoff and roadmap updates.

## Validation

At minimum:

```powershell
cd android
.\gradlew.bat lint test assembleDebug
.\gradlew.bat connectedDebugAndroidTest
cd ..
.\scripts\Test-Planning.ps1
```

Run the documented 30-minute survey, detach/reconnect sequence, process-death
recovery, low-storage simulation, and queue-pressure test. Store evidence under
`docs/evidence/M1/`; do not commit precise home coordinates or full serials.

## Out of scope

- Signal detection, ranking, and clustering.
- Heat maps and geographic signal cells.
- User-facing focused IQ capture.
- Share-sheet export or bundle import.
- Protocol or payload decoding.

## Handoff

Complete this section before marking M1 complete:

- Commit and branch:
- Delivered behavior:
- Validation summary:
- Hardware evidence:
- Schema version and migration notes:
- ADRs:
- Known limitations:
- M2 starting point and cautions:
