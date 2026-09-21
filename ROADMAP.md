# RF Field Notebook roadmap

This roadmap turns the
[application specification](docs/android-rf-field-notebook-spec.md) into six
sequential, agent-sized delivery milestones. Each milestone has a self-contained
execution brief under [`docs/milestones/`](docs/milestones/README.md).

## How to use this roadmap

- Assign one milestone at a time unless its brief explicitly identifies work
  that can safely run in parallel.
- Give the implementing agent the milestone file, but require it to follow the
  repository reading order in `AGENTS.md`.
- A checked box means the milestone's acceptance criteria are evidenced in the
  repository, not merely that work was attempted.
- Later milestones may be explored, but they do not enter implementation until
  their dependencies are complete.
- Requirement IDs have one primary owning milestone. Milestone 5 performs the
  final cross-cutting audit without changing that ownership.

Run the planning integrity check with:

```powershell
.\scripts\Test-Planning.ps1
```

## Delivery sequence

```text
M0 Technical spikes
  -> M1 Radio and survey foundation
      -> M2 Detection and fingerprinting
          -> M3 Geographic mapping
              -> M4 Focused capture and export
                  -> M5 Field hardening and MVP release
```

## Milestone status

| Milestone | Status | Outcome | Primary requirements | Brief |
| --- | --- | --- | --- | --- |
| M0 | Complete | Pixel 8a proves direct Android USB, sustained RX, screen-off service, permission denial, detach/reattach, task removal, sweep parsing, offline mapping, and connected-test execution | Readiness gates and open decisions | [Technical spikes](docs/milestones/M0-technical-spikes.md) |
| M1 | Complete (documented evidence exceptions) | Reliable receive-only radio and GPS-tagged survey foundation | USB, equipment, band, location, acquisition, performance | [Radio and survey foundation](docs/milestones/M1-radio-survey-foundation.md) |
| M2 | In progress | Convert spectrum observations into reviewable signal fingerprints | Detection and fingerprinting | [Detection and fingerprinting](docs/milestones/M2-detection-fingerprinting.md) |
| M3 | Blocked by M2 | Uncertainty-aware geographic exploration of selected fingerprints | Mapping | [Geographic mapping](docs/milestones/M3-geographic-mapping.md) |
| M4 | Blocked by M3 | Focused IQ capture and privacy-controlled interoperable exports | Capture and export | [Focused capture and export](docs/milestones/M4-capture-export.md) |
| M5 | Blocked by M4 | Field-tested, accessible, recoverable MVP release candidate | Cross-cutting audit | [Field hardening and release](docs/milestones/M5-field-hardening.md) |

## M0 — Technical spikes

**Objective:** Produce measured evidence that the target Android phone, USB
topology, libhackrf build, receive pipeline, foreground service, and mapping
stack can support the product before committing to the full architecture.

**Key outputs:** Android skeleton, receive-only native boundary prototype,
benchmark report, foreground-service prototype, stored-observation map
prototype, accepted foundational ADRs, and a go/no-go recommendation.

**Gate:** Do not begin M1 until every major risk has measured evidence or an
accepted design response.

See [M0 execution brief](docs/milestones/M0-technical-spikes.md).

## M1 — Radio and survey foundation

**Objective:** Deliver a reliable receive-only system that connects to one
HackRF, applies versioned equipment/band profiles, runs a foreground survey,
records location and spectrum aggregates, and reports acquisition health.

**Key outputs:** Production module boundaries, USB lifecycle, profile storage,
survey state machine, acquisition service, GPS association, persistence,
health UI, and hardware-in-the-loop evidence.

**Gate:** A 30-minute screen-off survey must complete without hidden data loss,
unsafe state, or corrupted persistence.

See [M1 execution brief](docs/milestones/M1-radio-survey-foundation.md).

## M2 — Detection and fingerprinting

**Objective:** Turn survey aggregates into explainable carriers and bursts, then
cluster comparable detections into editable signal fingerprints.

**Key outputs:** Noise estimator, detector, artifact flags, fingerprint model,
clustering/versioning, discoveries list, detail timeline, fixtures, and golden
tests.

**Gate:** Synthetic and recorded fixtures must demonstrate expected detection,
merge, persistent-carrier, artifact, and clustering behavior.

See [M2 execution brief](docs/milestones/M2-detection-fingerprinting.md).

## M3 — Geographic mapping

**Objective:** Show where a selected fingerprint was observed with useful
strength and uncertainty context, without claiming a transmitter location.

**Key outputs:** MapLibre integration, route and accuracy layers, adaptive cell
aggregation, filters, equivalent list view, offline-region workflow, and map
performance evidence.

**Gate:** Repeated-route field data must render accurately, preserve gaps, and
warn when equipment settings make surveys incomparable.

See [M3 execution brief](docs/milestones/M3-geographic-mapping.md).

## M4 — Focused capture and export

**Objective:** Let a user revisit a fingerprint, record a bounded IQ sample, and
export a documented, privacy-controlled bundle for desktop analysis.

**Key outputs:** Focused RX/waterfall, storage estimator, atomic IQ capture,
preview generation, sidecar schema, survey archive, redaction controls, import,
and Windows-toolchain interoperability evidence.

**Gate:** A 1–5 second capture and a redacted survey bundle must round-trip and
open correctly in the repository's Windows analysis environment.

See [M4 execution brief](docs/milestones/M4-capture-export.md).

## M5 — Field hardening and MVP release

**Objective:** Prove the complete product meets the specification under
real-world interruption, resource pressure, accessibility, privacy, and field
survey conditions.

**Key outputs:** Full acceptance audit, long-running tests, recovery paths,
security/privacy review, accessibility review, operator documentation, release
build, and known-limitations report.

**Gate:** All 13 MVP acceptance criteria in specification section 20 are backed
by reproducible evidence; there is no application-accessible transmit path.

See [M5 execution brief](docs/milestones/M5-field-hardening.md).

## Cross-milestone artifacts

Agents maintain these artifacts as the project evolves:

- `docs/android-rf-field-notebook-spec.md` — authoritative product contract.
- `ROADMAP.md` — sequence, status, and milestone outcomes.
- `docs/milestones/` — scoped execution and handoff briefs.
- `docs/adr/` — durable architectural decisions.
- `docs/evidence/` — benchmark, hardware, field, and release evidence created by
  milestones.
- `android/` — application source created by M0.
- `test-data/` — small redistributable sweep/IQ fixtures created by M2.
- `schemas/` — versioned export schemas created by M4.

Directories that do not exist yet are created by the milestone that first owns
them. Do not add placeholder source files solely to make the future tree exist.

## Change control

A material change to scope, safety boundaries, module ownership, schema, minimum
Android version, mapping semantics, or milestone gates requires:

1. An ADR describing context, decision, alternatives, and consequences.
2. Updates to the specification and affected milestone briefs.
3. Updated requirement ownership metadata if responsibility moves.
4. A passing planning integrity check.

Minor implementation choices that remain within the accepted architecture may
be documented in code and milestone handoff notes without an ADR.
