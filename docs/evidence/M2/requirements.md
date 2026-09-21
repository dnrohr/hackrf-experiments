# M2 requirement traceability

Status: Complete on 2026-09-21. Host and Pixel 8a connected validation passed.

| Requirement | Implementation | Automated evidence | Visible behavior |
| --- | --- | --- | --- |
| FR-DET-001 | `RollingNoiseEstimator` lower-quartile per-bin/profile window with occupied-sample guard and diagnostics | `RollingNoiseEstimatorTest`; persistent fixture | Baseline confidence remains diagnostic data; no calibrated-power claim |
| FR-DET-002 | `DetectionPipeline` SNR threshold, adjacent-bin join, and minimum bandwidth | `DetectionPipelineTest`; golden fixtures | Detail shows frequency, bandwidth, relative SNR, and evidence |
| FR-DET-003 | Versioned overlap/time-gap merge and deterministic close | burst merge test; shuffled evaluation | Timeline shows merged start/end observations |
| FR-DET-004 | `Detection`/`DetectionEntity` store frequency, bandwidth, peak/median power, SNR, duration, location reference, and version | Room schema 3; detector tests | Detail timeline renders relative SNR and kind |
| FR-DET-005 | `DetectionKind` separates discrete bursts and persistent carriers | persistent and OOK fixture tests | Detail labels `persistent_carrier` or `discrete_burst` |
| FR-DET-006 | Offline `DiscoveryRepository.reprocessSurvey` consumes retained M1 min/median/max/sample aggregates and records a versioned job | connected Room offline-reprocessing test | Survey summary offers “Process discoveries” without live radio |
| FR-FP-001 | `FingerprintClusterer` uses exact equipment version, frequency, bandwidth, duration, discrete/persistent behavior, and recurrence summaries | clustering tests | Discoveries identify their comparable evidence group |
| FR-FP-002 | `SignalFingerprint` and Room schema store first/last seen, count, duty cycle, duration, interval, and geographic extent | clustering and connected Room tests | Detail shows all metrics, location extent, and a relative-SNR timeline |
| FR-FP-003 | Deterministic application UUIDs derived from versioned sorted evidence | reversed/shuffled input tests | Stable discovery identity across identical reprocessing |
| FR-FP-004 | `FingerprintCorrections` plus transactional persisted split/merge provenance; source detections are reassigned, not deleted | split/merge unit test | Detail renders correction history; states remain reversible |
| FR-FP-005 | Novelty score and discovery ranking combine prior occurrence, strength, recurrence, and available location evidence | `DiscoveryPresentationTest` with 500 rows | Discoveries are ranked for review |
| FR-FP-006 | Ranked `ClassificationHint` categories include confidence and human-readable evidence, separate from labels/tags | clustering tests and UI presentation test | Detail explicitly says hints are not protocol identities |

## Supporting evidence

- Threshold rationale: `docs/evidence/M2/threshold-rationale.md`
- Fixture evaluation: `docs/evidence/M2/evaluation.md`
- Known failure modes: `docs/evidence/M2/known-failure-modes.md`
- Fixture license, origin, units, and tolerances: `test-data/M2/manifest.json`
- Golden expectations: `test-data/M2/golden-detections.json`
- Connected UI and Room review: `docs/evidence/M2/ui-review.md`
