# M1 database schema

Status: In progress

The persistence and backpressure contract is recorded in
[`ADR 006`](../../adr/006-survey-persistence-and-backpressure.md).

RF Field Notebook uses Room with exported JSON schemas under
`android/storage/schemas/dev.rfnotebook.storage.NotebookDatabase/`.

## Version 1

The initial schema stores radio identity, immutable equipment and band profile
versions, normalized included/excluded ranges, surveys, survey state events,
location fixes, one-second spectrum aggregates, explicit acquisition gaps, and
health snapshots. Precise coordinates and serial suffixes stay in the private
application database.

## Version 2

Version 2 adds persisted connection state and revision columns to
`radio_devices` and adds `connection_state_events`. `MIGRATION_1_2` uses additive
SQL, defaults existing devices to `DISCONNECTED`, preserves all version-1 rows,
and creates the foreign-key index. The Pixel 8a migration test creates a real
version-1 database, inserts a radio, migrates it, validates the complete schema,
and verifies the preserved row and defaults.

No destructive migration fallback is configured. Every future schema version
must export its schema and add a migration test from every committed version.
