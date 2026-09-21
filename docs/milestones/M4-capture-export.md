# M4 — Focused capture and export

- Status: Ready
- Depends on: M3 — Geographic mapping
- Produces: Bounded IQ captures and privacy-controlled interoperable bundles
- Next milestone: M5 — Field hardening and release

<!-- REQUIREMENTS: FR-CAP-001, FR-CAP-002, FR-CAP-003, FR-CAP-004, FR-CAP-005, FR-CAP-006, FR-CAP-007, FR-EXP-001, FR-EXP-002, FR-EXP-003, FR-EXP-004, FR-EXP-005 -->

## Objective

Let a user select or manually tune a signal, preview it in a focused receive
view, make a bounded signed 8-bit IQ capture, and export a documented survey
bundle whose location, device, route, and IQ contents are explicitly controlled.

The milestone preserves raw RF evidence for external tools. It does not decode
communication payloads.

## Inputs

- Completed M3 map/discovery navigation.
- Stable M1 continuous RX and equipment-profile behavior.
- M2 fingerprint detail and artifact context.
- Application specification sections 8.5, 9.9–9.10, 10, 15, and 17.
- Windows Radioconda/HackRF toolchain in this repository.

## Primary requirement ownership

M4 owns all `FR-CAP` and `FR-EXP` requirements. Create
`docs/evidence/M4/requirements.md` linking each ID to source, tests, schema,
privacy UI, and interoperability evidence.

## Deliverables

- Focused receive screen with center frequency, sample rate, bounded waterfall,
  relative power, and equipment settings.
- Manual 0.25–30 second IQ capture with preflight storage/throughput checks.
- Atomic capture file, SHA-256, metadata sidecar, and reduced preview.
- Versioned JSON Schema under `schemas/` for sidecars and survey manifests.
- Versioned ZIP survey export containing selected JSON, CSV, GeoJSON, notes, and
  IQ artifacts.
- Export review/redaction UI and explicit Android share-sheet handoff.
- Safe import with schema, path, size, and integrity validation.
- Desktop interoperability scripts or documented commands.
- `docs/evidence/M4/` with requirement map, schema examples, round-trip results,
  privacy cases, fuzz/security results, and Windows-tool screenshots/logs.

## Tasks

### 1. Build focused receive mode

- Enter from a fingerprint or manual frequency.
- Reapply originating equipment settings when available and show differences.
- Keep antenna power and amplifier conservative and explicit.
- Display a bounded-rate waterfall and relative power without starving RX.
- Return radio to idle on navigation, detach, cancellation, or error.

### 2. Implement capture preflight

- Accept duration, frequency, sample rate, and manual trigger.
- Restrict duration to 0.25–30 seconds for the MVP.
- Calculate expected bytes using two signed 8-bit components per sample.
- Check measured USB capacity, free storage, and configured reserve.
- Identify missing/stale location without blocking a location-optional capture.

### 3. Write captures atomically

- Stream to a temporary app-private file through a bounded queue.
- Count expected and actual samples, overruns, and gaps.
- Stop deterministically at the requested limit or explicit cancellation.
- Flush, close, hash, and rename only after success.
- Retain failed partials only through an explicit recovery/diagnostic path.
- Generate a reduced preview without modifying original IQ.

### 4. Define schemas and units

- Create versioned JSON Schemas for capture sidecar and survey manifest.
- Specify signed interleaved I/Q ordering, sample rate, center frequency, byte
  order, timestamps, coordinate reference, gain fields, quality flags, and hash.
- Store app, detector, clustering, aggregation, profile, and schema versions.
- Provide valid, minimal, redacted, and invalid example bundles.

### 5. Implement privacy-controlled export

- Let the user choose surveys, captures, notes, routes, serial suffixes, and
  coordinate precision.
- Show a pre-share manifest summary and estimated archive size.
- Default diagnostic exports to no sensitive RF/location content.
- Create the archive in app-controlled temporary storage.
- Invoke the Android share sheet only after explicit review.
- Expire temporary shared files according to a documented policy.

### 6. Implement defensive import

- Validate schema versions before mutation.
- Block absolute paths, traversal, duplicate paths, symlinks, oversized entries,
  decompression bombs, invalid hashes, and unreasonable numeric values.
- Import transactionally and report rejected content without partial state.
- Do not execute scripts, decoders, or flowgraphs from an archive.

### 7. Prove desktop interoperability

- Capture 1–5 seconds at a rate supported by the target phone.
- Transfer through the normal export workflow.
- Validate length, hash, sample interpretation, and metadata on Windows.
- Open or analyze the IQ with at least one repository-supported tool.
- Round-trip a full and redacted bundle through export/import.

## Acceptance criteria

- [ ] All 12 owned requirements have traceability evidence.
- [ ] Manual captures enforce duration and storage limits.
- [ ] A successful file has exact documented sample encoding, actual counts,
  sidecar, preview, and matching SHA-256.
- [ ] Cancellation, detach, low storage, and overrun cannot produce a capture
  falsely marked complete.
- [ ] Focused mode always returns the radio to idle on exit.
- [ ] No MVP screen parses or displays application payload contents.
- [ ] JSON Schema validates committed examples and rejects documented invalid cases.
- [ ] Export review can independently omit IQ, routes, coordinates, notes, and
  identifiers.
- [ ] Import blocks traversal, oversized archive, invalid hash, and unsupported
  schema fixtures without partial mutation.
- [ ] Full and redacted bundles round-trip.
- [ ] A 1–5 second IQ capture opens correctly in the Windows toolchain and the
  evidence records all interpretation parameters.
- [ ] Earlier milestone regression suites pass.
- [ ] `scripts/Test-Planning.ps1` passes after handoff and roadmap updates.

## Validation

```powershell
cd android
.\gradlew.bat lint test assembleDebug
.\gradlew.bat connectedDebugAndroidTest
cd ..
.\scripts\Test-Planning.ps1
```

Run schema validation, archive-fuzz fixtures, cancellation/detach/low-storage
tests, capture hash checks, Windows import, and full/redacted round trips. Store
only intentionally sanitized examples in Git.

## Out of scope

- Automatic/burst-triggered or pre-trigger capture.
- Demodulation or payload decoding.
- Cloud upload or collaborative bundle sharing.
- Unbounded background capture.
- Conversion to unrelated proprietary sample formats.

## Handoff

Complete this section before marking M4 complete:

- Commit and branch:
- Delivered behavior:
- Validation summary:
- Schema and interoperability evidence:
- Privacy/security evidence:
- ADRs:
- Known limitations:
- M5 starting point and cautions:
