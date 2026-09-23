# M5 completion audit

Audit dates: 2026-09-22 through 2026-09-23. “Pass” means direct implementation plus automated,
hardware, field, schema, or UI evidence exists; a planning reference alone is
not accepted. Exact private coordinates and the full HackRF serial are omitted.

## All 64 numbered requirements

| Requirement | Result | Direct evidence |
| --- | --- | --- |
| FR-USB-001 | Pass | M1 [requirements](../M1/requirements.md), [hardware report](../M1/hardware-report.md), `AndroidHackrfRadio.kt` |
| FR-USB-002 | Pass | M1 [requirements](../M1/requirements.md), [UI review](../M1/ui-review.md), `MainActivity.kt` USB permission receiver |
| FR-USB-003 | Pass | M1 [requirements](../M1/requirements.md), native open tests and M5 physical identity run |
| FR-USB-004 | Pass | M1 [hardware report](../M1/hardware-report.md) and M5 [field report](field-report.md) |
| FR-USB-005 | Pass | M1 [failure injection](../M1/failure-injection.md), M5 [adverse conditions](adverse-conditions.md) |
| FR-USB-006 | Pass | `NativeSessionStateTest`, `OpenSessionRegistryTest`, `SurveyCoordinatorTest`, and M5 detach/process-death evidence |
| FR-USB-007 | Pass | `ReceiveOnlyContractTest`, M1 compatibility evidence, and the M5 physical receive-test result visible in `MainActivity.kt` |
| FR-EQP-001 | Pass | M1 [requirements](../M1/requirements.md), `ProfileTest`, and connected `NotebookDatabaseTest` profile persistence cases |
| FR-EQP-002 | Pass | M1 [schema notes](../M1/schema-notes.md), Room entity/DAO tests |
| FR-EQP-003 | Pass | M3 [repeat-route results](../M3/repeat-route-results.md), `ProfileTest.comparabilityReturnsEveryMeasurementChangingReason`, and `GeographicAggregationTest.incompatibleEquipmentIsExcludedAndExplainedByDefault` |
| FR-EQP-004 | Pass | `ProfileTest.conservativeDefaultsNeverEnablePoweredRfFeatures`, M5 [safety/privacy review](safety-privacy-receive-only.md) |
| FR-BAND-001 | Pass | M1 [requirements](../M1/requirements.md), `ProfileTest` band validation/range coverage |
| FR-BAND-002 | Pass | `ProfileTest.measuredSampleRatesHaveExplicitMatchingFilters`, physical preflight in M5 [field report](field-report.md) |
| FR-BAND-003 | Pass | M0 [benchmark report](../M0/benchmark-report.md), M5 physical preflight estimate |
| FR-BAND-004 | Pass | `NotebookDatabaseTest.referencedEquipmentChangeCreatesNewPersistentVersion` and M3 comparability evidence |
| FR-BAND-005 | Pass | `ProfileTest.starterProfilesIncludeAllSpecifiedExplorationRegions` and setup UI |
| FR-LOC-001 | Pass | M1 [requirements](../M1/requirements.md), Room connected tests |
| FR-LOC-002 | Pass | `LocationAssociationTest.staleFixLeavesObservationUnlocated`; M5 degraded-GPS evidence |
| FR-LOC-003 | Pass | `LocationAssociationTest.interpolatesOnlyAcrossBoundedShortGapAndMarksFix` |
| FR-LOC-004 | Pass | `NotebookDatabaseTest.schemaStoresUnlocatedAggregateAndCompleteHealthCounters`, M5 field run |
| FR-LOC-005 | Pass | `PhysicalDeviceGateTest`, foreground-service manifest/UI start path |
| FR-ACQ-001 | Pass | M1 hardware sweep and M4 physical focused receive/capture reports |
| FR-ACQ-002 | Pass | `ProfileTest.conservativeDefaultsNeverEnablePoweredRfFeatures`, versioned equipment profiles, M5 field preflight |
| FR-ACQ-003 | Pass | `SweepFrameParserTest` and persisted aggregate/health tests |
| FR-ACQ-004 | Pass | M1 [failure injection](../M1/failure-injection.md), M5 [adverse conditions](adverse-conditions.md) |
| FR-ACQ-005 | Pass | `BoundedPipelineTest` queue/drop/load-shedding coverage |
| FR-ACQ-006 | Pass | M1 [schema notes](../M1/schema-notes.md), storage inspection; survey persists aggregates only |
| FR-DET-001 | Pass | M2 [evaluation](../M2/evaluation.md), `RollingNoiseEstimatorTest` |
| FR-DET-002 | Pass | M2 [evaluation](../M2/evaluation.md), `DetectionPipelineTest` |
| FR-DET-003 | Pass | `DetectionPipelineTest` adjacent-frame merge cases |
| FR-DET-004 | Pass | M2 [requirements](../M2/requirements.md), Room detection entity/DAO tests |
| FR-DET-005 | Pass | M2 [evaluation](../M2/evaluation.md), persistent-carrier fixture/test |
| FR-DET-006 | Pass | Connected `NotebookDatabaseTest.completedM1SurveyReprocessesOfflineAndUserStateIsReversible` reprocessing from retained aggregates |
| FR-FP-001 | Pass | M2 [evaluation](../M2/evaluation.md), `FingerprintClustererTest` |
| FR-FP-002 | Pass | M2 [requirements](../M2/requirements.md), `FingerprintClustererTest`, and connected `NotebookDatabaseTest` reprocessing |
| FR-FP-003 | Pass | `FingerprintClustererTest` stable deterministic IDs |
| FR-FP-004 | Pass | `NotebookDatabaseTest.completedM1SurveyReprocessesOfflineAndUserStateIsReversible` |
| FR-FP-005 | Pass | `FingerprintClustererTest` comparable-survey novelty |
| FR-FP-006 | Pass | M2 [UI review](../M2/ui-review.md), ranked hint tests and disclaimer |
| FR-MAP-001 | Pass | M3 [map QA](../M3/map-qa.md), [offline behavior](../M3/offline-behavior.md) |
| FR-MAP-002 | Pass | M3 [aggregation validation](../M3/aggregation-validation.md), physical UI evidence |
| FR-MAP-003 | Pass | `GeographicAggregationTest`, M3 validation |
| FR-MAP-004 | Pass | `GeographicAggregationTest` robust statistic/count/uncertainty cases |
| FR-MAP-005 | Pass | `GeographicAggregationTest.everyRequiredFilterDimensionIsApplied` and `MapExplorerUiTest` list/map workflow |
| FR-MAP-006 | Pass | M3 [map QA](../M3/map-qa.md), accessibility text/UI assertions |
| FR-MAP-007 | Pass | `OfflineRegionConnectedTest` on Pixel 8a and M3 [offline behavior](../M3/offline-behavior.md) |
| FR-CAP-001 | Pass | M4 [hardware report](../M4/hardware-report.md), Windows byte/hash validation |
| FR-CAP-002 | Pass | `CaptureContractsTest.preflightEnforcesDurationThroughputAndStorageReserve` duration/rate/storage boundaries |
| FR-CAP-003 | Pass | `CaptureContractsTest`, focused-capture UI, and M5 low-storage review |
| FR-CAP-004 | Pass | M4 manual-capture UI/hardware report; trigger modes explicitly deferred |
| FR-CAP-005 | Pass | M4 [schema/interoperability](../M4/schema-and-interoperability.md), sidecar schema validation |
| FR-CAP-006 | Pass | `CaptureContractsTest` atomic-writer cases, M4 preview visual/format validation |
| FR-CAP-007 | Pass | receive-only/static review and source audit: no payload parser/decoder |
| FR-EXP-001 | Pass | `SurveyBundleTest` complete bundle and M4 round trip |
| FR-EXP-002 | Pass | M4 [schema/interoperability](../M4/schema-and-interoperability.md), committed schema examples |
| FR-EXP-003 | Pass | bundle/capture schemas and [export guide](../../operator/export-guide.md) |
| FR-EXP-004 | Pass | `SurveyBundleTest` redaction matrix, M4 privacy review UI |
| FR-EXP-005 | Pass | `SurveyBundleTest` same-version round trip/strict hostile-manifest corpus and M4 physical round trip |
| NFR-PERF-001 | Pass | separate bounded workers in `SurveyAcquisitionService`, `BoundedPipelineTest` |
| NFR-PERF-002 | Pass | status flow throttling/unit tests and M1 active UI review |
| NFR-PERF-003 | Pass | M4 8 MS/s focused-capture hardware run and responsive UI evidence |
| NFR-PERF-004 | Pass | `BoundedPipelineTest`, surfaced health counters in M5 field run |
| NFR-PERF-005 | Pass | `BoundedPipelineTest.storageGuardPreservesFixedReserve`, `CaptureContractsTest`, physical low-storage preflight evidence |
| NFR-PERF-006 | Pass | `BoundedPipelineTest` warning/stop policy and M5 safe synthetic thermal evidence |

Count: **64 Pass, 0 incomplete**.

## All 13 section-20 MVP acceptance criteria

| # | Result | Direct evidence |
| --- | --- | --- |
| 1 | Pass | Pixel 8a shows HackRF One, firmware `2026.01.3`, API `1.10`; [field report](field-report.md) |
| 2 | Pass | Physical fixed-gain 902–928 MHz preflight/start; [field report](field-report.md) |
| 3 | Pass | 30-minute screen-off foreground-service run and persisted summary; [field report](field-report.md) |
| 4 | Pass | M1 Pixel/HackRF runs persisted and surfaced three real USB overruns and closed physical `USB_DETACH` / `PROCESS_DEATH` gaps; later gates preserve the healthy zero-count path. |
| 5 | Pass | Pixel/HackRF runs retain accuracy-bearing fixes (including 8.2 m and 18.9 m observations), stale/missing states, and recovery to fresh fixes without invented coordinates; [adverse conditions](adverse-conditions.md). |
| 6 | Pass | M2 recorded-data clustering and physical survey reprocessing; [field report](field-report.md) |
| 7 | Pass | M3 Pixel uncertainty-aware map/equivalent-list evidence; M5 reproduced the nested-scroll crash on the Pixel, fixed it, passed the production-shaped 20,000-observation regression, and reopened the map for the 93-unlocated-detection physical dataset with no new Android crash. |
| 8 | Pass | The Pixel/HackRF revisit workflow completed a one-second focused capture. The release UI reported the completed IQ/sidecar/PGM set; the linked archive contains 8,000,000 CS8 bytes (4,000,000 complex samples at 4 MS/s), zero capture gaps, and zero overruns. See [field report](field-report.md). |
| 9 | Pass | `Test-M4Capture.ps1` validated both full and redacted extracted copies of the physical capture: signed interleaved I,Q bytes, exact byte/sample relationship, sidecar SHA-256, 4 MS/s rate, and center frequency. Every bundle manifest length/hash also passed. |
| 10 | Pass | The release app saved a full capture-bearing bundle (`0cfa3ee4…c2d56`), desktop inspection validated all nine inventory records, and the same signed release reimported it without rejection or partial commit. |
| 11 | Pass | The release app saved a reviewed capture-bearing bundle (`f31b8b27…ca618`) with IQ=true, route=false, coordinates=omitted, notes=false, identifiers=false. Structured sidecar inspection found null location/device suffix and an empty note, all seven inventory records matched, and Pixel reimport passed. |
| 12 | Pass | Source, release, JNI symbol, and physical runtime receive-only audits; [safety/privacy review](safety-privacy-receive-only.md) |
| 13 | Pass | Physical Pixel/HackRF detach/reconnect and force-stop recovery finalized closed gaps; the physical active-service low-storage branch stopped orderly, and corrupt DB/archive fixtures fail closed; [adverse conditions](adverse-conditions.md). |

Count: **13 Pass, 0 incomplete**. No accepted evidence is an emulator or a
simulated substitute for the required Pixel 8a/HackRF scenarios. Thermal stress
alone uses the milestone-authorized safe synthetic equivalent.
