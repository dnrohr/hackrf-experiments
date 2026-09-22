# ADR 008: Atomic focused capture and defensive bundle format

- Status: Accepted
- Date: 2026-09-21
- Owners: M4
- Related requirements: FR-CAP-001 through FR-CAP-007; FR-EXP-001 through FR-EXP-005; NFR-PERF-003 and NFR-PERF-005; specification sections 15 and 17
- Supersedes: none

## Context

Focused IQ is the product's largest and most sensitive artifact. A detach,
cancellation, overrun, or low-storage event must not leave it looking complete.
Exports additionally cross the app-private boundary and imports process
untrusted ZIP content. Desktop tools need an unambiguous byte contract.

## Decision

Capture signed 8-bit interleaved `I,Q` components into a same-directory `.part`
file and accept only 2, 4, or 8 MS/s. Manual durations are 250–30,000 ms. A
preflight reserves 256 MiB and requires measured delivery of at least 95% of the
requested rate; the margin accounts for one-second host scheduling and polling
measurement overhead, not missing capture bytes. Successful completion still
requires the exact requested byte count and zero observed gaps or overruns.

After stop, compute SHA-256, generate a bounded PGM preview, write a versioned
JSON sidecar, then rename the three `.part` artifacts. Persist `COMPLETE` in
Room only after all final files exist. Cancellation and failure remove partials.
The focused controller stops and closes its receive-only session on exit,
detach/error, replacement operation, or activity disposal. RF amplifier and
antenna-port power remain explicitly off.

Use ZIP bundle schema `1.0.0` with a manifest hash and length for every payload
entry. Export review independently controls IQ, route, coordinate mode
(`full`, `rounded`, `omitted`), notes, and device suffix. Shared cache files
expire after 24 hours. Imports stage outside authoritative state, reject unknown
schemas, unsafe or duplicate paths, directory/symlink-like structure, unknown or
oversized sizes, extreme compression, invalid hashes, and unreasonable schema
values, and rename the staging directory only after complete validation. No
imported executable content is evaluated.

## Alternatives considered

Writing directly to a final filename was rejected because interruption is
indistinguishable from completion. Float IQ was rejected because it expands
files and does not preserve HackRF's native signed-byte evidence. Streaming ZIP
imports directly into Room was rejected because failure could create partial
state. A zero-margin throughput comparison was rejected after the Pixel 8a
delivered a complete 2 MS/s stream while a one-second wall-clock estimate read
1.96 MS/s.

## Consequences

Files are directly consumable as GNU Radio `char`/complex interleaved input and
by tools that support signed 8-bit complex samples. Captures require temporary
free space until finalization and compression can vary widely. Version 1 import
is deliberately strict; future compatible evolution needs a new schema version
and migration policy.

## Validation

Unit tests cover preflight boundaries, byte-exact finalization, hash and preview,
failure non-finalization, redaction combinations, round-trip, traversal,
duplicates, size limits, hash mismatch, and unsupported schemas. Room migration
3→4 is a connected test. JSON Schema validates committed examples. A Pixel 8a /
Android 17 and HackRF One produced an 8,000,000-byte capture that passed the
Windows Radioconda/GNU Radio interpretation and hash checks.

## Follow-up

M5 should exercise detach and low-storage injection on the physical capture
screen, accessibility at large text, and cleanup after process death. Automatic
and pre-trigger capture remain out of scope.
