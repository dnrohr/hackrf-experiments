# Agent instructions

These instructions apply to the entire repository.

## Mission

Build RF Field Notebook, a receive-only Android application that connects to a
HackRF One, records GPS-tagged spectrum observations, groups recurring signals,
maps relative observation strength, and creates focused IQ captures for later
analysis.

The authoritative product contract is
[`docs/android-rf-field-notebook-spec.md`](docs/android-rf-field-notebook-spec.md).
The delivery sequence and current milestone status are in
[`ROADMAP.md`](ROADMAP.md).

## Required reading order

Before changing code or documentation:

1. Read this file completely.
2. Read `ROADMAP.md`.
3. Read the full application specification.
4. Read the assigned file in `docs/milestones/`.
5. Read every accepted ADR in `docs/adr/` that affects the task.
6. Inspect the current worktree, recent commits, and existing tests.

Do not implement from the milestone title alone. A milestone brief is a scope
boundary and execution checklist, not a replacement for the product contract.

## Non-negotiable invariants

- The application is receive-only. Do not expose, wrap, test, or call HackRF
  transmit APIs.
- Antenna-port power defaults off. Do not enable it implicitly.
- The RF amplifier defaults off. Survey gain changes must be explicit and
  recorded.
- Precise coordinates, HackRF serials, frequencies, notes, and IQ data remain
  local unless the user explicitly exports them.
- No automatic telemetry or cloud account is introduced.
- Maps describe observed relative strength, never an asserted transmitter
  position.
- Do not add payload decryption, descrambling, authentication bypass, or
  exploitation features.
- Do not silently discard acquisition gaps, overruns, malformed frames, or
  missing location. Preserve and surface uncertainty.
- Do not weaken milestone acceptance criteria to make a task pass.

## Scope discipline

- Implement only the assigned milestone plus prerequisites that are strictly
  necessary for it.
- Preserve forward-compatible interfaces called out by later milestones, but do
  not implement later milestone behavior early without a documented reason.
- If the specification and milestone brief conflict, stop and document the
  conflict. The specification wins unless an accepted ADR explicitly changes it.
- Record material architectural decisions as ADRs using
  `docs/adr/000-template.md`.
- Update the specification, roadmap, requirement ownership, and tests together
  when an accepted scope change affects them.

## Intended project structure

Milestone 0 will create the Android project. Unless an accepted ADR changes it,
use this module layout:

```text
android/
  app-ui/
  domain/
  radio-api/
  radio-hackrf-native/
  acquisition-service/
  signal-processing/
  storage/
  maps/
```

Keep platform-independent algorithms in `domain` or `signal-processing`. Keep
libhackrf, libusb, JNI, and native buffer ownership inside
`radio-hackrf-native`. No module outside the native adapter may depend directly
on a transmit-capable native header.

## Development workflow

1. Confirm the prerequisite milestone is complete in `ROADMAP.md`.
2. Convert the assigned brief into a small checklist in the task conversation.
3. Establish a failing test or measurable spike question before implementation.
4. Make the smallest coherent changes that satisfy the full milestone.
5. Run the milestone's validation commands and relevant earlier tests.
6. Update documentation, ADRs, schemas, fixtures, and requirement traceability.
7. Complete the brief's handoff record and update roadmap status.
8. Commit with a milestone-prefixed message, for example
   `M2: add burst detection and fingerprint clustering`.

Do not push unless the task explicitly asks for a push. Do not rewrite shared
history. Preserve unrelated user changes.

## Testing expectations

- Unit tests cover deterministic domain behavior and boundary cases.
- Recorded-data tests use small redistributable fixtures and tolerant numeric
  assertions.
- Native tests cover cancellation, detach, invalid lengths, and resource
  cleanup—not only the happy path.
- Hardware-in-the-loop evidence names the phone, Android version, USB topology,
  HackRF firmware, sample rate, duration, and observed errors.
- UI tests cover permission denial, degraded GPS, interruption, and empty states.
- Export tests inspect archive contents and round-trip supported schema versions.
- Every bug fixed during a milestone receives a regression test when practical.

Tests that require hardware must be clearly separated from tests suitable for
CI. Never simulate a hardware pass or present an emulator result as USB evidence.

## Definition of done for a milestone

A milestone is complete only when:

- Every deliverable and acceptance criterion in its brief is satisfied.
- Its primary requirement IDs have implementation and test evidence.
- Earlier milestone regression suites still pass.
- New public interfaces and file formats are documented.
- Required ADRs are accepted and linked.
- Known limitations and deferred work are recorded without hiding failures.
- `scripts/Test-Planning.ps1` passes.
- The roadmap status and handoff record reflect authoritative repository state.

## Repository tooling

- `scripts/Test-Planning.ps1` validates planning structure and requirement
  ownership.
- `scripts/Test-HackRF.ps1` validates the Windows HackRF installation.
- `.tools/`, `.state/`, captures, and raw IQ files are intentionally gitignored.
- Use `apply_patch` for hand-authored file changes and `rg` for repository search.

## Safety when working with hardware

- Never transmit during automated or manual validation.
- Never connect a transmitter directly to the HackRF input.
- Keep the device input below its documented maximum and use attenuation for
  controlled sources.
- Use ambient signals or shielded/attenuated laboratory fixtures.
- Stop acquisition and close the native device before firmware, USB, or process
  lifecycle changes.
