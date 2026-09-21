# M3 validation record

Final validation was run on 2026-09-21 from Windows PowerShell at commit-ready
worktree state.

| Gate | Result |
| --- | --- |
| `cd android; .\gradlew.bat lint test assembleDebug` | Pass; 394 tasks |
| `cd android; .\gradlew.bat connectedDebugAndroidTest` | Pass; 412 tasks |
| Connected XML reconciliation | 18 tests, 0 failures, 0 errors, 0 skipped |
| `scripts/Test-ReceiveOnly.ps1` | Pass; 41 production source files audited |
| `scripts/Test-NativeExports.ps1` | Pass; exactly nine receive-only JNI exports for both ABIs |
| `scripts/Test-Planning.ps1` | Pass; 6 milestones and 64 requirements, each with one primary owner |

The connected total comprises 11 app UI/device tests and 7 Room tests. It
includes all M0–M2 connected regressions plus the M3 map/list, offline-region,
large-text, and 20,000-observation cases. The host command runs all module unit
tests, lint, and debug assembly. A final manual same-install offline sequence
downloaded the bounded region online, disabled airplane/Wi-Fi networking,
passed the cached-map UI test, restored networking, and supplied the committed
offline screenshot.

The HackRF remained disconnected. M3 exercises stored/deterministic observation
data and does not require USB RF hardware; no hardware or transmit claim is
made by this validation.
