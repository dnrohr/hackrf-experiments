# M1 requirement traceability

Status: In progress. Software evidence is current as of 2026-09-14. Rows marked
hardware pending are not acceptance claims.

| Requirement | Implementation | Automated evidence | Hardware evidence |
| --- | --- | --- | --- |
| FR-USB-001 | `AndroidHackrfRadio.supportedDevices`; setup USB filter | `ReceiveOnlyContractTest`; receive-only script | M0 complete; M1 run pending |
| FR-USB-002 | `MainActivity.requestUsbPermission` and attachment receiver | `PhysicalDeviceGateTest` permission-denial regression | M0 complete; M1 clean-install repeat pending |
| FR-USB-003 | exact suffix selection plus `OpenSessionRegistry` | `OpenSessionRegistryTest` | M1 run pending |
| FR-USB-004 | `RadioDeviceEntity`; active-survey identity | Room connected tests | M1 run pending |
| FR-USB-005 | selected-device broadcasts, stall handling, recoverable gaps | coordinator/state tests | Detach/stall run pending |
| FR-USB-006 | `NativeRadioSession.close`; service `finally`/stop/detach paths | `NativeSessionStateTest`; coordinator tests | Detach/stop run pending |
| FR-USB-007 | `RadioLimits`; profile validation against M0-supported rates | `ReceiveOnlyContractTest`; `ProfileTest` | M0 2/4/8 MS/s evidence; M1 throughput pending |
| FR-EQP-001 | versioned equipment table and editor fields | Room profile-version test | Not hardware-dependent |
| FR-EQP-002 | equipment gains/filter/rate/power columns | schema validation and profile tests | M1 settings readback pending |
| FR-EQP-003 | `EquipmentProfile.compareWith` with reason set | `ProfileTest.comparabilityReturnsEveryMeasurementChangingReason` | Not hardware-dependent |
| FR-EQP-004 | `conservativeDefault`; UI relative-power warning | `ProfileTest.conservativeDefaultsNeverEnablePoweredRfFeatures` | M1 settings readback pending |
| FR-BAND-001 | `FrequencyRange`; normalized band-range table | overlap validation tests; Room schema test | Not hardware-dependent |
| FR-BAND-002 | versioned ranges/exclusions/bin/revisit/threshold/minimum fields | `ProfileTest` | Not hardware-dependent |
| FR-BAND-003 | cycle estimator and preflight display | `ProfileTest.cycleEstimatorAccountsForExcludedSpectrum` | M1 observed cycle pending |
| FR-BAND-004 | `NotebookSetupRepository` creates/retires immutable versions | connected profile-version test | Not hardware-dependent |
| FR-BAND-005 | five editable starter definitions and receive-only disclaimer | `ProfileTest.starterProfilesIncludeAllSpecifiedExplorationRegions` | Not hardware-dependent |
| FR-LOC-001 | full `LocationFix` model/table and service listener | schema test; `LocationAssociationTest` | 30-minute run pending |
| FR-LOC-002 | configurable monotonic-age policy | stale-fix unit test | GPS gap run pending |
| FR-LOC-003 | bounded interpolation with explicit marker/provider | interpolation unit test | GPS gap run pending |
| FR-LOC-004 | nullable aggregate location plus `MISSING`/`STALE` state | Room unlocated-aggregate test | GPS denial/gap run pending |
| FR-LOC-005 | activity-launched combined foreground service | connected permission regression | 30-minute run pending |
| FR-ACQ-001 | receive-only `startSweep` and `startRx` APIs | receive-only contract/static checks | M0 RX/sweep complete; M1 sweep pending |
| FR-ACQ-002 | immutable profile version per survey; no auto-setting path | domain/Room version tests | M1 settings readback pending |
| FR-ACQ-003 | callback monotonic time carried through each bucket | accumulator and location tests | M1 run pending |
| FR-ACQ-004 | native, malformed, overrun, stale-fix, service-gap counters | bounded-pipeline tests | pressure/detach run pending |
| FR-ACQ-005 | independent bounded native/processing/persistence stages; 1 Hz UI | bounded-pipeline tests | pressure run pending |
| FR-ACQ-006 | survey pipeline persists only summaries/fixes/health | receive-only script and schema inspection | M1 run pending |
| NFR-PERF-001 | callback only offers to memory queue; database on IO workers | coordinator/pipeline unit tests | 30-minute run pending |
| NFR-PERF-002 | health UI state published at 1 Hz | source inspection | UI observation pending |
| NFR-PERF-003 | bounded nonblocking callback path | M0 8 MS/s evidence; queue tests | M1 8 MS/s check pending |
| NFR-PERF-004 | three bounded stages and per-stage counters | `BoundedPipelineTest` | pressure run pending |
| NFR-PERF-005 | `StorageGuard` estimate and 256 MiB reserve | storage-guard test | low-storage run pending |
| NFR-PERF-006 | battery/thermal snapshots and visible warnings without retuning | source inspection; coordinator tests | thermal/battery observation pending |

## Validation evidence

- `./gradlew.bat lint test assembleDebug`: passed 2026-09-14.
- Pixel 8a, Android 17, direct USB-C: storage connected tests passed (three
  tests, including schema v1→v2) and app regression tests passed (three tests).
- `scripts/Test-ReceiveOnly.ps1`, `scripts/Test-NativeExports.ps1`, and
  `scripts/Test-Planning.ps1`: passed at checkpoint `3381762`; rerun required at
  handoff.

