# Milestone handoff guide

Each file in this directory is a bounded implementation contract designed to be
assigned to one coding agent. Milestones are sequential because each produces
interfaces and evidence consumed by the next.

## Recommended agent prompt

Replace `<ID>` and `<brief>` with the assigned milestone:

```text
Implement milestone <ID> using <brief>. Follow AGENTS.md, ROADMAP.md, the full
application specification, and all accepted ADRs. Treat the milestone scope and
acceptance criteria as binding. Inspect current repository state before acting.
Implement and test the complete milestone, preserve receive-only and privacy
invariants, add evidence and ADRs where required, update the milestone handoff
record and ROADMAP status, then commit. Do not begin a later milestone.
```

Explicitly add `push the resulting commit` only when the agent is authorized to
push.

## Handoff prerequisites

Before assignment, the coordinator should provide:

- The exact milestone ID.
- Confirmation that its dependency is complete in `ROADMAP.md`.
- Access to any physical Android phone, HackRF, powered hub, or antenna required
  by the milestone.
- Whether hardware interaction is available directly or requires the user.
- Whether the agent should commit, push, or stop with a working tree review.

## Milestone files

- [M0 — Technical spikes](M0-technical-spikes.md)
- [M1 — Radio and survey foundation](M1-radio-survey-foundation.md)
- [M2 — Detection and fingerprinting](M2-detection-fingerprinting.md)
- [M3 — Geographic mapping](M3-geographic-mapping.md)
- [M4 — Focused capture and export](M4-capture-export.md)
- [M5 — Field hardening and release](M5-field-hardening.md)

## Requirement ownership metadata

Each milestone contains one hidden `REQUIREMENTS` comment. It lists the
specification requirement IDs for which that milestone has primary delivery
ownership. `scripts/Test-Planning.ps1` verifies that every numbered requirement
has exactly one owner.

Milestones may test or consume requirements owned elsewhere, but they must not
duplicate ownership without updating the roadmap and validator deliberately.

## Status vocabulary

- **Ready:** All dependencies are complete and required inputs are available.
- **Blocked by Mx:** Work must not start until the named milestone is complete.
- **In progress:** An assigned agent is actively implementing the milestone.
- **Needs evidence:** Code exists, but one or more acceptance gates lack proof.
- **Complete:** All acceptance criteria, tests, evidence, docs, and handoff tasks
  are satisfied.

## Required handoff record

At completion, replace the milestone's empty handoff section with:

- Commit hash and branch.
- Summary of delivered behavior.
- Validation commands and results.
- Hardware evidence paths and tested configuration.
- ADRs created or superseded.
- Known limitations and deliberately deferred work.
- Exact starting point and cautions for the next milestone.
