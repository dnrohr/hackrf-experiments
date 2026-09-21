# M2 — Detection and fingerprinting

- Status: In progress
- Depends on: M1 — Radio and survey foundation
- Produces: Explainable detections, fingerprints, and discovery workflows
- Next milestone: M3 — Geographic mapping

<!-- REQUIREMENTS: FR-DET-001, FR-DET-002, FR-DET-003, FR-DET-004, FR-DET-005, FR-DET-006, FR-FP-001, FR-FP-002, FR-FP-003, FR-FP-004, FR-FP-005, FR-FP-006 -->

## Objective

Convert persisted spectrum observations into reviewable evidence. Implement a
versioned detector that distinguishes noise, discrete bursts, and persistent
carriers; flags common artifacts; and clusters comparable detections into stable,
editable signal fingerprints with explainable classification hints.

The output must help a user decide what to revisit. It must not decode payloads
or claim protocol identity without evidence.

## Inputs

- Completed M1 foundation, schema, profiles, and hardware evidence.
- M0/M1 ADRs and the M1 requirement handoff.
- Application specification sections 9.6–9.7, 10–11, 15, and 19.
- Sanitized sweep fixtures plus synthetic fixtures defined in specification 19.2.
- Comparable survey data with fixed equipment profiles.

## Primary requirement ownership

M2 owns every `FR-DET` and `FR-FP` requirement. Create
`docs/evidence/M2/requirements.md` mapping each ID to source, tests, fixtures,
and visible behavior.

## Deliverables

- Platform-independent noise-estimation and detection pipeline.
- Versioned detector configuration and reproducible reprocessing job.
- Artifact flags for center/DC, symmetry, broadband impulse, overload, and bad
  frames.
- Persistent-carrier and discrete-burst models.
- Fingerprint clustering with stable IDs, novelty, split/merge provenance, and
  reversible user states.
- Discoveries list and signal-detail timeline.
- `test-data/` fixture manifest, generators, redistributable fixtures, and
  expected golden detections.
- `docs/evidence/M2/` threshold rationale, evaluation results, requirement map,
  and known failure modes.

## Tasks

### 1. Establish fixture and evaluation contracts

- Define a versioned fixture manifest with sample format, units, origin, license,
  expected phenomena, and redistribution status.
- Generate deterministic noise, carrier, OOK-like burst, two-level FSK-like,
  center artifact, broadband overload, and missing/reordered frame cases.
- Add sanitized real sweep fixtures only when they contain no sensitive location
  or payload information.
- Define tolerances for frequency, bandwidth, time, SNR, and grouping.

### 2. Implement robust noise estimation

- Maintain a rolling per-bin baseline using a robust statistic.
- Prevent persistent occupied bins from raising their own baseline indefinitely.
- Reset or fork estimator state when equipment/profile compatibility changes.
- Expose baseline age, sample support, and confidence for diagnostics.
- Test startup, sparse data, persistent carriers, changing floor, and overload.

### 3. Form detections

- Threshold bins relative to local baseline.
- Join adjacent bins and reject groups below configured criteria.
- Merge across adjacent frames using time/frequency overlap.
- Close discrete detections deterministically after a bounded gap.
- Transition long activity into persistent-carrier state without losing history.
- Store detector version and sufficient metrics for later reprocessing.

### 4. Flag artifacts

- Detect center/DC candidates using hardware-center metadata.
- Identify suspicious symmetric candidates without deleting them.
- Mark broadband impulses and wide noise-floor rises.
- Propagate corrupt/incomplete frame quality flags.
- Provide user-reversible `artifact` labeling and preserve source observations.

### 5. Cluster fingerprints

- Cluster only comparable detections.
- Use frequency proximity, typical bandwidth, duration, repetition, and temporal
  behavior; location may be descriptive but must not dominate identity in M2.
- Assign stable UUIDs and record clustering algorithm/version.
- Compute first/last seen, occurrence count, duty cycle, duration, recurrence,
  and location extent.
- Support split and merge corrections with auditable provenance.

### 6. Produce cautious classification hints

- Implement the hint categories defined in the specification.
- Attach confidence and human-readable evidence.
- Separate algorithmic hints from user labels and tags.
- Never identify ownership, municipality, protocol, or payload semantics from RF
  shape alone.

### 7. Build discovery UX

- Rank by novelty, strength, recurrence, and geographic specificity.
- Filter by band, time, state, hint, survey, and equipment comparability.
- Show empty, processing, partial-data, and failed-reprocessing states.
- Provide signal detail with timeline, frequency/bandwidth, recurrence, SNR,
  evidence, notes, tags, and reversible state controls.
- Defer geographic heat layers and IQ capture actions to later milestones while
  leaving explicit extension points.

## Acceptance criteria

- [ ] All 12 owned requirements have traceability evidence.
- [ ] Golden fixtures pass documented numeric tolerances.
- [ ] Noise-only data does not create sustained false fingerprints at the chosen
  default threshold during the evaluation window.
- [ ] Persistent carriers do not disappear into the rolling baseline.
- [ ] Discrete bursts merge and close deterministically across frame boundaries.
- [ ] Center, broadband-overload, and corrupt-frame fixtures retain observations
  with appropriate artifact flags.
- [ ] Clustering is deterministic for a fixed algorithm version and input order.
- [ ] Split and merge preserve source detections and an audit trail.
- [ ] Incomparable equipment profiles are never silently clustered.
- [ ] Classification hints display confidence and evidence rather than certainty.
- [ ] A completed M1 survey can be reprocessed without live hardware.
- [ ] Discoveries and detail screens are usable with empty, large, and partially
  failed datasets.
- [ ] M0 and M1 regression suites pass.
- [ ] `scripts/Test-Planning.ps1` passes after handoff and roadmap updates.

## Validation

```powershell
cd android
.\gradlew.bat lint test assembleDebug
.\gradlew.bat connectedDebugAndroidTest
cd ..
.\scripts\Test-Planning.ps1
```

Run fixture evaluation twice with shuffled input ordering and compare normalized
outputs. Record thresholds, tolerance ranges, confusion/false-positive notes,
runtime, and memory in `docs/evidence/M2/evaluation.md`.

## Out of scope

- Map rendering and transmitter localization.
- IQ recording or demodulation.
- Payload and proprietary-protocol decoding.
- FCC database correlation.
- Machine-learned opaque classifiers.

## Handoff

Complete this section before marking M2 complete:

- Commit and branch:
- Delivered behavior:
- Validation summary:
- Fixture/evaluation evidence:
- Detector and clustering versions:
- ADRs:
- Known limitations:
- M3 starting point and cautions:
