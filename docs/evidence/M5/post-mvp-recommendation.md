# Post-MVP recommendation

## Recommendation

Prioritize a **device-diversity and long-duration reliability milestone** before
adding new RF features.

Expected value: validate USB power, foreground-service policy, thermal behavior,
GPS recovery, storage pressure, and database/archive upgrades across at least
three current Android OEMs and multiple powered hubs. Add an operator-visible
diagnostic package that remains redacted by default and a repeatable 2–4 hour
soak matrix. This directly reduces the largest residual field risk: behavior
outside the single Pixel 8a configuration.

Risk: device-matrix work consumes hardware and field time and may uncover OEM-
specific lifecycle work with little visible feature progress. Diagnostics must
not leak coordinates, frequencies, serials, notes, or IQ.

Prerequisites: M5 release evidence, retained signing key, representative phones
and hubs, and an accepted test-data/privacy protocol. Treat it as a new
milestone with its own acceptance criteria and ADRs.

## Ranked deferred directions

1. Device diversity, multi-hour soak, upgrade/rollback, and redacted support
   diagnostics.
2. Calibrated-reference workflow and richer uncertainty comparison, without
   converting maps into asserted source locations.
3. Improved offline-region/storage management and user-selectable tile sources
   with a fresh privacy/license review.
4. Triggered/pre-trigger capture after byte-integrity and storage-pressure
   behavior is proven across the device matrix.

Do not prioritize decoders, remote RF heads, direction finding, collaboration,
cloud accounts, or community maps. Those materially expand privacy, security,
legal, and product scope and are not prerequisites for hardening the local
receive-only notebook.
