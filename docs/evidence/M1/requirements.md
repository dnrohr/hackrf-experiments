# M1 requirement traceability

Status: In progress. Software evidence is current as of 2026-09-15. Rows marked
hardware pending are not acceptance claims.

| Requirement | Implementation | Automated evidence | Hardware evidence |
| --- | --- | --- | --- |
| FR-USB-001 | `AndroidHackrfRadio.supportedDevices`; setup USB filter | `ReceiveOnlyContractTest`; receive-only script | M0 complete; M1 HackRF identified in hardware report |
| FR-USB-002 | `MainActivity.requestUsbPermission` and attachment receiver | `PhysicalDeviceGateTest` permission-denial regression | USB permission granted on Pixel 8a; clean-install repeat remains pending |
| FR-USB-003 | exact suffix selection plus `OpenSessionRegistry` | `OpenSessionRegistryTest` | Hardware survey opened the selected suffix; full serial remains omitted |
| FR-USB-004 | `RadioDeviceEntity`; active-survey identity | Room connected tests | Active survey displayed HackRF model, firmware/API, and serial suffix |
| FR-USB-005 | selected-device broadcasts, stall handling, recoverable gaps | coordinator/state tests | Physical detach/reattach and process-recovery gaps persisted; instrumented `RADIO_STALL` gap persisted; physical removal remains `USB_DETACH` |
| FR-USB-006 | `NativeRadioSession.close`; service `finally`/stop/detach paths | `NativeSessionStateTest`; coordinator tests | Detach drained queues and stop removed the foreground service; direct native-handle inspection remains pending |
| FR-USB-007 | `RadioLimits`; profile validation against M0-supported rates | `ReceiveOnlyContractTest`; `ProfileTest` | M0 2/4/8 MS/s evidence; M1 4 MS/s run measured 2.54–2.78 MB/s |
| FR-EQP-001 | versioned equipment table and editor fields | Room profile-version test | Not hardware-dependent |
| FR-EQP-002 | equipment gains/filter/rate/power columns | schema validation and profile tests | Hardware preflight and summary recorded 4 MS/s, 3.5 MHz filter, LNA/VGA 16 dB, RF amp off, antenna power off |
| FR-EQP-003 | `EquipmentProfile.compareWith` with reason set | `ProfileTest.comparabilityReturnsEveryMeasurementChangingReason` | Not hardware-dependent |
| FR-EQP-004 | `conservativeDefault`; UI relative-power warning | `ProfileTest.conservativeDefaultsNeverEnablePoweredRfFeatures` | Active and preflight UI displayed receive-only/relative-power language and both powered RF options off |
| FR-BAND-001 | `FrequencyRange`; normalized band-range table; one-to-ten-range native sweep | range parsing/effective-range/radio validation tests; Room schema test | Not hardware-dependent |
| FR-BAND-002 | versioned ranges/exclusions/bin/revisit/threshold/minimum fields; exact exclusion filtering | `ProfileTest`; connected launch-range test | Not hardware-dependent |
| FR-BAND-003 | cycle estimator and preflight display | `ProfileTest.cycleEstimatorAccountsForExcludedSpectrum` | Pixel 8a preflight displayed 130 ms estimated cycle for 902–928 MHz |
| FR-BAND-004 | `NotebookSetupRepository` creates/retires immutable versions | connected profile-version test | Not hardware-dependent |
| FR-BAND-005 | five editable starter definitions and receive-only disclaimer | `ProfileTest.starterProfilesIncludeAllSpecifiedExplorationRegions` | Not hardware-dependent |
| FR-LOC-001 | full `LocationFix` model/table and service listener | schema test; `LocationAssociationTest` | Final 31:04 survey persisted 1,854 fixes with accuracy values |
| FR-LOC-002 | configurable monotonic-age policy | stale-fix unit test | GPS gap run pending |
| FR-LOC-003 | bounded interpolation with explicit marker/provider | interpolation unit test | GPS gap run pending |
| FR-LOC-004 | nullable aggregate location plus `MISSING`/`STALE` state | Room unlocated-aggregate test | GPS denial/gap run pending |
| FR-LOC-005 | activity-launched combined foreground service | connected permission regression | 31:04 screen-off survey retained foreground service and persisted GPS state |
| FR-ACQ-001 | receive-only `startSweep` and `startRx` APIs | receive-only contract/static checks | M1 production survey ran sweep acquisition; no TX path exposed |
| FR-ACQ-002 | immutable profile version per survey; no auto-setting path | domain/Room version tests | Completed survey retained the fixed 902–928 MHz and equipment settings |
| FR-ACQ-003 | callback monotonic time carried through each bucket | accumulator and location tests | Process-death and USB-detach summaries preserved ordered timed gaps |
| FR-ACQ-004 | native, malformed, overrun, stale-fix, service-gap counters | bounded-pipeline tests | Detach and process-death gaps were counted; final 31:04 gate persisted zero drops, overruns, malformed frames, stale fixes, and gaps |
| FR-ACQ-005 | independent bounded native/processing/persistence stages; atomic fix/batch writes; 1 Hz UI | bounded-pipeline and connected Room tests | Final health maxima were native 1, processing 31, persistence 1 with zero stage drops |
| FR-ACQ-006 | survey pipeline persists only summaries/fixes/health | receive-only script and schema inspection | Final summary persisted aggregates, fixes, gaps, and health with no survey IQ |
| NFR-PERF-001 | callback only offers to memory queue; database on IO workers | coordinator/pipeline unit tests | Final 31:04 screen-off gate completed with zero persisted drops |
| NFR-PERF-002 | health UI state published at 1 Hz | source inspection | Active UI displayed live queue, rate, GPS, battery, thermal, and storage health |
| NFR-PERF-003 | bounded nonblocking callback path | M0 8 MS/s evidence; queue tests | M1 8 MS/s check pending |
| NFR-PERF-004 | three bounded stages and per-stage counters | `BoundedPipelineTest` | Final health showed bounded queues and zero stage drops; injection evidence remains in automated tests |
| NFR-PERF-005 | `StorageGuard` estimate, 256 MiB reserve, and orderly automatic stop below reserve | storage-guard test | Instrumented active-service low-storage stop persisted `LOW_STORAGE` and finalized `COMPLETE` without consuming storage |
| NFR-PERF-006 | battery/thermal snapshots and visible warnings without retuning | source inspection; coordinator tests | Final health persisted battery and thermal snapshots; thermal status 0 |

## Validation evidence

- `./gradlew.bat lint test assembleDebug`: passed 2026-09-14 after the multi-range, atomic-persistence, health-UI, and orderly low-storage-stop changes.
- Pixel 8a, Android 17, direct USB-C: storage connected tests passed (three
  tests, including schema v1→v2) and app regression tests passed (three tests).
- `scripts/Test-ReceiveOnly.ps1`, `scripts/Test-NativeExports.ps1`, and
  `scripts/Test-Planning.ps1`: passed 2026-09-14 after the multi-range,
  persistence, health, and recovery changes.
- `connectedDebugAndroidTest` was run on Pixel 8a / Android 17 after the final
  build. Its result files report 5/5 storage tests and 3/3 app tests passed;
  the Gradle task nevertheless returned failure during test APK uninstall, so
  this is recorded as test-runner infrastructure failure rather than a clean
  command pass.
