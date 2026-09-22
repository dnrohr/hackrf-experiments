# M4 requirement traceability

| Requirement | Implementation | Test/evidence |
| --- | --- | --- |
| FR-CAP-001 | `IqCapture.kt`, `FocusedCaptureScreen.kt` | `CaptureContractsTest`; `hardware-report.md` |
| FR-CAP-002 | `CapturePreflight` 250–30,000 ms gate | preflight boundary test |
| FR-CAP-003 | UI byte estimate and 256 MiB reserve | preflight test; UI screenshot |
| FR-CAP-004 | explicit manual capture button only | physical UI run |
| FR-CAP-005 | sidecar schema and atomic writer | schema validation; Windows hash check |
| FR-CAP-006 | bounded 64×32 PGM preview | writer test; hardware artifact metadata |
| FR-CAP-007 | focused UI exposes only energy/waterfall evidence | UI review; receive-only static check |
| FR-EXP-001 | versioned ZIP and manifest | `SurveyBundleTest`; round-trip report |
| FR-EXP-002 | survey JSON, CSV, GeoJSON, notes, selected IQ | exporter test; physical bundle listing |
| FR-EXP-003 | JSON Schemas and documented units/CRS/time/encoding | `schemas/`; ADR 008 |
| FR-EXP-004 | independent IQ/route/coordinate/note/identifier controls | redaction test; UI screenshot |
| FR-EXP-005 | staged same-version import with validation before rename | hostile fixture tests; import UI |

Primary sources are `android/storage/src/main/kotlin/dev/rfnotebook/storage/`,
`android/app-ui/src/main/kotlin/dev/rfnotebook/app/FocusedCaptureScreen.kt`, and
the two schemas under `schemas/`.
