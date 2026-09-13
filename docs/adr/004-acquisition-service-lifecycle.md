# ADR 004: Foreground acquisition service ownership

- Status: Accepted
- Date: 2026-09-13
- Owners: M0
- Related requirements: FR-LOC-005, NFR-PERF-001, NFR-PERF-004, specification sections 12.4 and 13
- Supersedes: none

## Context

Active surveys must keep USB RX and foreground location alive with the screen
off, while modern Android restricts foreground-service starts and types.

## Decision

Start acquisition only from a visible activity. A non-exported service owns the
radio session, location subscription, bounded queues, and health counters.
Measure whether the target requires a wake lock before adding one. Declare
`connectedDevice|location`, publish the ongoing
RX-only notification immediately, and provide a Stop action. Use
`START_NOT_STICKY`; after process death M1 will recover persisted state rather
than silently resuming collection. Do not request background-location access.

## Alternatives considered

WorkManager is unsuitable for live USB. Activity ownership fails on screen-off
and configuration changes. A sticky service risks resuming without an explicit
user action.

## Consequences

Acquisition has one lifecycle owner and an always-visible user control.
Notification and foreground-service permissions vary by OS and require target
phone evidence. The M0 probe uses a one-slot latest-buffer queue and reports
every overwrite; M1 replaces it with the production queue policy.

## Validation

Instrumented/manual tests cover permission denial, screen-off, Stop, detach,
swipe-away, and service teardown. Local builds verify manifest merging.

## Follow-up

Record a 15-minute physical screen-off run and implement M1's persisted state
machine only after the spike is evidenced.
