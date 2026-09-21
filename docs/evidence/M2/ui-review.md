# M2 connected UI and persistence review

Date: 2026-09-21

## Target and command

- Device: Google Pixel 8a (`akita`), Android 17 / API 37.
- Build fingerprint: `google/akita/akita:17/CP2A.260805.005/15828068:user/release-keys`.
- Display: 1080 × 2400 physical pixels at 420 dpi.
- USB state: ADB connected directly; no HackRF was attached or required.
- Command: `android\gradlew.bat connectedDebugAndroidTest`.
- Result: build successful in 37 seconds; 412 Gradle tasks; 13 connected
  tests, zero failures, zero errors, and zero skipped tests.

The six application tests cover the empty discovery state, a filtered partial
dataset with 500 fingerprints, editable detail metadata and reversible review
state, plus the retained M0/M1 task-removal, permission-denial, and USB-host
regressions. The seven Room tests include schema migrations 1→2 and 2→3,
offline reprocessing of a completed M1 survey, geographic extent, corrections,
metadata persistence, and the earlier storage/profile regressions.

## Visual review

- [`m2-discoveries-partial.png`](ui/m2-discoveries-partial.png) shows the
  frequency/time/state/hint/survey/equipment controls, explicit partial-data
  warning, a filtered discovery, comparable-equipment context, and merge
  selection on the physical target.
- [`m2-discovery-detail.png`](ui/m2-discovery-detail.png) shows frequency and
  bandwidth, recurrence and duty cycle, location extent, relative-SNR evidence,
  a cautious classification hint, editable label/tags/notes, reversible states,
  split control, and the explicit map/focused-IQ deferral.

Both captures were produced by the Compose connected tests on the Pixel 8a and
pulled from app-specific external test storage. Text remained legible, controls
had labeled touch targets, and no protocol identity or transmitter-location
claim appeared. The screenshot fixtures use synthetic coordinates and signal
metadata only.
