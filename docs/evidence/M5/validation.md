# M5 validation record

Validation dates: 2026-09-22 through 2026-09-23. Commands are run from the repository root unless a
working directory is shown. Exact device identifiers and private locations are
not retained.

## Clean host regression

The following completed successfully after the M5 code changes:

```powershell
cd android
.\gradlew.bat clean lint test assembleDebug assembleRelease
```

Result: **BUILD SUCCESSFUL**, 717 actionable tasks (391 executed, 254 from
cache, 72 up-to-date). This covers every module's JVM tests, Android lint,
debug packaging, release compilation, native builds for the reviewed ABIs, R8
minification, and resource shrinking.

Targeted regression runs also passed for:

- `SurveyBundleTest`, including streamed IQ, redaction, atomic replacement,
  archive limits, hostile paths, and round trip;
- `InterruptedArtifactRecoveryTest`, including partial cleanup and 24-hour
  share-cache expiry;
- `NotebookDatabaseTest`, including complete Room-derived bundle content and
  fail-closed corrupt-database handling; and
- the three production-shaped `MapExplorerUiTest` cases on the Pixel 8a,
  including 20,000 observations.

## Repository and release checks

The following checks pass:

| Check | Result |
| --- | --- |
| `scripts/Test-ReceiveOnly.ps1` | Pass; 47 reviewed source files and no accessible transmit path |
| `scripts/Test-NativeExports.ps1` | Pass; exactly nine project JNI functions for both packaged ABIs |
| `scripts/Test-M4Schemas.ps1` | Pass; capture/bundle schemas and committed valid/invalid examples; hostile archive cases also pass in `SurveyBundleTest` |
| `scripts/Test-M5Dependencies.ps1` | Pass; zero OSV advisories for 18 reviewed release packages |
| `scripts/Test-M5Release.ps1` | Pass on the minified release APK |
| `scripts/Test-Planning.ps1` | Pass; all 64 requirement IDs have exactly one primary owner |

## Connected device regression

The most recent complete aggregate connected suite passed on the unlocked Pixel
8a before the final duration/rotation/archive additions:

```powershell
cd android
.\gradlew.bat connectedDebugAndroidTest
```

That result after the completed-survey export/navigation change was **BUILD
SUCCESSFUL** in 58 seconds, 412 actionable tasks. The two modules with device
tests reported 24/24 passing: 14 app/UI tests and 10
Room/storage tests, with zero failures, errors, or skips. This includes
`M5HardeningUiTest` (2 tests), the production-shaped map regression (3 tests),
permission-denial/task-removal behavior, completed-survey summary reopening,
discovery UI, migrations, corrupt database handling, and complete Room-derived
export content. Modules without
instrumented cases packaged and completed their connected tasks. The debug
package identity is `dev.rfnotebook.debug`, isolated from the release package;
post-run package inspection confirmed `dev.rfnotebook` remained installed. The
final connected rerun remains required and this historical pass is not used to
claim that the newly added connected cases have executed.

## Hardware and field validation

The integrated physical results are recorded in [field-report.md](field-report.md),
[screen-off-evidence.md](screen-off-evidence.md), and
[adverse-conditions.md](adverse-conditions.md). The long M5 endurance run is
accepted and will not be repeated. A short final workflow remains solely to
exercise the changed map/export path and preserve an integrated bundle/capture
artifact after the earlier private database was removed by the old test
identity.

## Release gate

The signed release set and final checksums are recorded in
[release-checksums.md](release-checksums.md) only after the connected and short
hardware workflows complete. `Test-M5Evidence.ps1` intentionally fails before
then because incomplete acceptance rows and an unfinished handoff must not be
mistaken for a release candidate.
