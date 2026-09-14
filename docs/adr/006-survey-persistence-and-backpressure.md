# ADR 006: Survey persistence, aggregation, and backpressure

- Status: Accepted
- Date: 2026-09-14
- Owners: M1
- Related requirements: FR-LOC-001 through FR-LOC-005; FR-ACQ-002 through FR-ACQ-006; NFR-PERF-001 through NFR-PERF-006
- Supersedes: none

## Context

A phone survey must preserve useful RF evidence without recording continuous
wideband IQ. Native USB delivery, signal processing, and SQLite persistence run
at different rates and can fail independently. Process death and USB removal
must not turn an incomplete survey into apparently complete data. Equipment and
band edits must also remain attributable after a survey references them.

## Decision

Use Room as the app-private source of truth. Store immutable, versioned equipment
and band profiles; a persisted survey and radio-connection state machine; full
location fixes; one-second per-frequency minimum/median/maximum aggregates;
health snapshots; and explicit acquisition gaps. A location fix and its
aggregate batch are written in one transaction. Schema changes use exported
Room JSON plus explicit migrations with no destructive fallback.

The service owns three bounded, non-blocking stages between native delivery,
processing, and persistence. Every rejected or undrained unit is counted at its
stage. Pressure, malformed input, overruns, stale fixes, detach, stalls, process
recovery, and low storage remain visible; none may trigger an implicit sample
rate, filter, gain, amplifier, or antenna-power change. When free storage falls
below the fixed reserve, the service performs an orderly stop.

Band exclusions are converted into at most ten non-overlapping HackRF sweep
ranges. Because the device sweep command uses whole-megahertz limits, adjacent
rounded hardware ranges may be merged, while the processing boundary filters
bins against the exact persisted ranges before aggregation.

## Alternatives considered

Continuous survey IQ was rejected for storage, privacy, and thermal cost. An
unbounded channel was rejected because it hides overload until memory pressure.
A latest-only buffer without counters was rejected because it silently loses
evidence. In-place profile edits were rejected because they alter the meaning of
existing observations. Destructive schema migration was rejected because it can
erase completed field records.

## Consequences

M2 receives bounded, timestamped aggregates rather than survey IQ and can
reprocess them deterministically. Exact RF samples remain an explicit M4 focused
capture. A one-second aggregate cannot reconstruct sub-second waveform detail.
The database contains sensitive locations and radio identity suffixes and must
remain app-private unless a later explicit export redacts or shares them.

## Validation

Unit tests cover profile versions and comparability, range subtraction and
validation, state recovery, location association, bounded queues, health
warnings, storage reserve behavior, and aggregate statistics. Connected Room
tests cover atomic fix/batch persistence and every committed migration. Static
and ELF-export checks enforce the receive-only native boundary. The M1 physical
gate covers a 30-minute screen-off survey, detach/recovery, process interruption,
and health evidence on the Pixel 8a and HackRF One.

## Follow-up

M2 may add derived detections and fingerprints without changing stored M1
aggregates. M4 defines explicit focused-IQ and export schemas. Any change to the
one-second aggregation contract or loss accounting requires a superseding ADR
and migration analysis.
