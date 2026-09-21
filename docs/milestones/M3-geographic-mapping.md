# M3 — Geographic mapping

- Status: Complete
- Depends on: M2 — Detection and fingerprinting
- Produces: Uncertainty-aware maps and comparable-survey exploration
- Next milestone: M4 — Focused capture and export

<!-- REQUIREMENTS: FR-MAP-001, FR-MAP-002, FR-MAP-003, FR-MAP-004, FR-MAP-005, FR-MAP-006, FR-MAP-007 -->

## Objective

Make fingerprints geographically useful. Render routes, observation coverage,
GPS accuracy, and robust relative-strength cells for a selected fingerprint,
while preserving uncertainty and warning when surveys cannot be compared.

This milestone maps where signals were observed. It does not estimate or claim
the physical transmitter location.

## Inputs

- Completed M2 fingerprints, metrics, and discovery UI.
- M1 location/profile comparability behavior.
- M0 MapLibre ADR and map prototype.
- Application specification sections 9.8, 10–11.4, 16, and 19.4.
- At least two repeat-route test surveys with redacted coordinates or a local
  non-committed evidence procedure.

## Primary requirement ownership

M3 owns all seven `FR-MAP` requirements. Create
`docs/evidence/M3/requirements.md` mapping each requirement to implementation,
tests, screenshots or recordings, and field evidence.

## Deliverables

- Production MapLibre integration with selected tile source and attribution.
- Route, gap, observation-coverage, and GPS-accuracy layers.
- Adaptive geographic aggregation for one fingerprint's relative strength.
- Explicit multi-fingerprint comparison mode with stable visual encoding.
- Filters for survey, time, frequency, bandwidth, type, equipment, and confidence.
- Equivalent accessible list/table representation.
- Offline-region download, status, use, and removal workflow.
- `docs/evidence/M3/` containing aggregation validation, map QA, performance,
  offline behavior, requirement mapping, and repeat-route results.

## Tasks

### 1. Finalize map and tile decisions

- Confirm the M0 tile source still satisfies offline, attribution, rate, privacy,
  and distribution needs.
- Record any changed decision in a superseding ADR.
- Keep mapping functional for previously downloaded areas without network access.
- Ensure map network access cannot read survey storage or initiate exports.

### 2. Implement geographic aggregation

- Associate each detection-strength observation with valid location and accuracy.
- Choose adaptive cell size based on zoom and useful GPS precision.
- Never use cells smaller than their supporting measurement accuracy suggests.
- Calculate count, median, upper quantile, spread, survey count, and confidence.
- Require configurable minimum support before high-confidence presentation.
- Keep algorithm/version metadata so aggregates can be rebuilt.

### 3. Render uncertainty honestly

- Show traveled route and explicit acquisition/location gaps.
- Show GPS accuracy for selected observations or cells without overwhelming the
  map.
- Label the layer `Observed relative strength`.
- Provide legends with relative units and sample support.
- Avoid pins, crosshairs, or language that visually asserts an emitter location.

### 4. Integrate fingerprint selection and filters

- Open a selected fingerprint from Discoveries directly on the map.
- Default to one fingerprint; make comparison mode explicit.
- Retain filter state across map/list navigation for the active session.
- Warn and exclude or separate observations with incompatible equipment settings.
- Explain why a filter produces no comparable data.

### 5. Build accessible alternatives

- Provide a sorted cell/observation list with approximate area, relative
  strength, support, uncertainty, and survey/time context.
- Ensure selection and details are operable without color or map gestures.
- Test large text, screen reader labels, contrast, and touch targets.

### 6. Add offline regions

- Let the user select a bounded region and zoom range.
- Estimate or report download progress and final size.
- Persist status and recover interrupted downloads.
- Allow removal with confirmation.
- Show attribution and cached/offline state.

### 7. Validate field behavior

- Repeat one route with unchanged equipment settings.
- Confirm known persistent signal patterns are broadly repeatable.
- Confirm gaps and degraded accuracy remain visible.
- Repeat with deliberately changed gain and verify comparability warnings.
- Evaluate map responsiveness with a dataset larger than expected MVP use.

## Acceptance criteria

- [x] All seven owned requirements have traceability evidence.
- [x] A fingerprint opens to a route and relative-strength map with uncertainty.
- [x] Cell size and confidence respond correctly to GPS accuracy and support.
- [x] A single extreme sample cannot alone create a high-confidence hot cell.
- [x] Missing and interpolated location remain distinguishable.
- [x] Incompatible surveys are separated or excluded with an explanation.
- [x] The UI never labels a point as a transmitter location.
- [x] Every map result has an equivalent non-map representation.
- [x] A downloaded region works after network access is disabled.
- [x] Required attribution remains visible online and offline.
- [x] Large-dataset pan, zoom, filter, and selection meet the recorded performance
  budget on the target phone.
- [x] Repeat-route results and known limitations are documented without
  committing precise home coordinates.
- [x] Earlier milestone regression suites pass.
- [x] `scripts/Test-Planning.ps1` passes after handoff and roadmap updates.

## Validation

```powershell
cd android
.\gradlew.bat lint test assembleDebug
.\gradlew.bat connectedDebugAndroidTest
cd ..
.\scripts\Test-Planning.ps1
```

Run screenshot or image-diff QA at small/large font scales and light/dark themes.
Record offline, large-dataset, changed-gain, missing-GPS, and repeat-route results
under `docs/evidence/M3/`.

## Out of scope

- Transmitter triangulation or direction finding.
- Turn-by-turn navigation to an inferred emitter.
- IQ recording and export.
- Cloud-hosted or community maps.
- Publishing infrastructure coordinates.

## Handoff

Complete this section before marking M3 complete:

- Commit and branch: `main`; local M3-prefixed commit created at handoff, not pushed.
- Delivered behavior: production MapLibre map; route, gap, accuracy, and adaptive
  strength cells; selected and comparison fingerprints; every required filter;
  comparable-equipment protection; equivalent list; complete offline workflow.
- Validation summary: host `lint test assembleDebug`; Pixel 8a full connected
  suite; receive-only/native-export audits; planning validation. Exact results
  and budgets are under `docs/evidence/M3/`.
- Field/map evidence: deterministic coordinate-redacted repeat-route fixture,
  light/dark and 150% text screenshots, 20,000-observation Pixel run, and
  online-download/airplane-mode cached rendering.
- Aggregation version: `m3-grid-v1`.
- Tile/offline decisions: ADR 007 accepts MapLibre Native 13.6.1 with
  OpenFreeMap Liberty, required attribution, local-only overlays, bounded
  resumable regions, stored byte/resource status, and confirmed removal.
- Known limitations: see `docs/evidence/M3/known-limitations.md`; especially
  uncalibrated strength, public-provider availability, and M5 live-field audit.
- M4 starting point and cautions: M4 may navigate from a fingerprint/map into
  focused RX, but must preserve selected fingerprint/equipment context, local
  privacy, receive-only native boundaries, gaps, and the prohibition on source
  location claims. Do not treat map strength as calibrated capture power.
