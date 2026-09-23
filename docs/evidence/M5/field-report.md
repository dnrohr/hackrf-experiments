# M5 integrated field report

Date: 2026-09-22 (America/New_York). Exact coordinates, Wi-Fi identifiers,
Android device identifiers, and the full HackRF serial are intentionally omitted.
This report distinguishes evidence recovered from Android itself from artifacts
that still require a replacement short workflow.

## Equipment and safe operating conditions

- Phone: Google Pixel 8a, Android 17, physical device (not an emulator).
- Radio: HackRF One, firmware `2026.01.3`, API `1.10`; serial recorded only as a
  redacted suffix in the private UI session.
- Receive-only operation with an ambient antenna. No transmitter was connected
  to the HackRF input. Antenna-port power and RF amplifier remained off.
- Survey profile: 902–928 MHz, 4 MS/s, fixed LNA/VGA gains 16/16 dB, 100 kHz
  bins, 1,000 ms target revisit, 8 dB detection threshold, and 100 kHz minimum
  bandwidth.
- The replacement artifact run used a direct phone-hosted USB connection: the
  Pixel bus-powered the HackRF through the operator's data cable, with no hub.
  This describes that short run only; a powered OTG hub remains the documented
  recommendation when direct power is unstable.

## Physical survey and screen-off endurance

The physical survey began at approximately 14:00. The operator carried the
phone and HackRF over a real walking route, returned, and allowed acquisition to
continue stationary. The initial active display reported 2.55 MB/s and 1,300
persisted spectrum aggregates after approximately six seconds, with zero USB
drops, overruns, or malformed frames. Location initially showed missing and
subsequently entered the Android GNSS acquisition path; missing location was
surfaced rather than filled with invented coordinates.

At a direct inspection after 32 minutes 58 seconds, Android reported the survey
foreground service active while the device was in doze and the screen was off.
The notification remained visible and acquisition health still reported zero
drops, overruns, and malformed frames. Retained Android battery history was
re-read during the audit and establishes an uninterrupted screen-off interval
from 14:14:34 to 14:50:01 (35 minutes 27 seconds). It independently retains:

- repeated `NotificationManagerService:post:dev.rfnotebook` activity beginning
  at 14:00;
- the screen-off transition at 14:14:34 and the next screen-on transition at
  14:50:01;
- continuing RF Notebook notification and GNSS activity at 14:29–14:30 and
  again immediately before the display wakes; and
- battery declining from approximately 64% to 56% during that interval, with
  no recorded thermal warning.

This is direct evidence of the required 30-minute screen-off foreground-service
run; representative sanitized events are preserved in
[screen-off-evidence.md](screen-off-evidence.md). The operator later opened the recorded discovery and selected the observed
relative-strength map. Android's crash record reports a process runtime of
9,422,201 ms (2 h 37 min 2.201 s), which is useful extended-duration process
evidence but is not substituted for the stronger 32:58 foreground-service
snapshot above.

## Defect discovered: strength-map crash

Opening the observed-relative-strength map repeatedly crashed the app. Android
Dropbox captured `java.lang.IllegalStateException: Vertically scrollable
component was measured with an infinity maximum height constraints`. The cause
was a `verticalScroll` on both the notebook host and `MapExplorerPage`.

The inner scroller was removed. `MapExplorerUiTest` now mounts every map case in
the same scrolling host used by `MainActivity`; all three cases passed on the
Pixel 8a, including a 20,000-observation filter/zoom/render and map pan. This is
a resolved high-severity reliability defect and has direct regression coverage.
Sanitized Android crash evidence is preserved in
[map-crash-and-fix.md](map-crash-and-fix.md).

## Evidence-retention incident and corrective action

At 19:06:59 the Gradle connected-test harness uninstalled
`dev.rfnotebook` while cleaning up its target application. Android logs explicitly
record `Package no longer installed`, `ACTION_PACKAGE_FULLY_REMOVED`, and a UID
change from the field install to the subsequent install. This deleted the
app-private Room database before it had been exported. A read-only search of
shared storage found no survey bundle, IQ file, or sidecar from this run.

The collected field work was valid; the loss was caused by the validation
procedure. The endurance result above remains independently evidenced by Android
battery and crash records. The deleted database cannot honestly support claims
about a completed M5 bundle round trip, so those rows remain incomplete until a
short replacement capture/export/reimport workflow is performed.

The build is now hardened so debug and instrumentation installs use
`dev.rfnotebook.debug`, while the private-sideload release remains
`dev.rfnotebook`. `aapt2 dump badging` verifies both identities. Future connected
test teardown cannot uninstall or erase the release notebook.

## Network observation

Android UID counters were sampled during acquisition. The only previously
observed traffic belonged to the earlier user-initiated offline-map download;
the counters did not increase during RF acquisition. The application has no
telemetry or account service. Exact network identifiers are not recorded.

## Signed-release replacement artifact run

On 2026-09-23 the release-signed `1.0.0-rc1` preflight build was installed as
the non-debuggable `dev.rfnotebook` package. A direct HackRF compatibility run
sustained approximately 2.00 MS/s, delivered more than 115 MiB, and reported
zero dropped buffers and callback errors before an orderly in-app stop.

A new 902–928 MHz survey then ran at 4 MS/s with the same fixed 16/16 dB gains,
RF amplifier off, and antenna-port power off. Android location was disabled
through the real device setting while acquisition continued. At 1:44 the UI
reported a 79-second-old fix and 27,040 persisted aggregates with zero stage
drops, overruns, or malformed frames. Location was re-enabled; at 2:19 the UI
reported a fresh 49.0 m fix and 36,140 aggregates, again with all loss counters
zero. This was not a mock location or application test hook.

The operator physically detached the HackRF later in the same survey. The app
entered `PAUSED`, USB throughput fell to zero, and it displayed `USB detached;
gap recorded` without increasing any drop/overrun/malformed counter. The survey
was then stopped while detached and finalized with 104,000 observations and two
closed `USB_DETACH` gap rows. The two rows reflect the physical USB lifecycle;
neither is silently collapsed into continuous acquisition.

The operator used the signed-release reviewed-export screen to save a full
bundle. It was copied from the Pixel before the requested device cleanup and is
retained only in gitignored `.state/M5/physical-full-survey.zip`. Its direct
artifact facts are:

- length `1,658,731` bytes;
- SHA-256 `0c6b4374aaafbfd31730a883d5feefe9242477241a361990907b6fb9d767798d`;
- seven ZIP entries and six manifest inventory records;
- 104,000 observation rows, 244 geographic features, one route feature, and two
  `USB_DETACH` gaps; and
- every manifest length/SHA-256, ZIP CRC, and schema check passed on Windows.

The exact private ZIP passed length, SHA-256, CRC, path, and schema inspection,
but it exposed a semantic defect: the reviewed IQ option was selected while the
survey had no linked capture, so the early preflight manifest recorded
`includesIq=true` without a `captures/` entry. The production exporter now
describes actual content, and the hardened importer deliberately rejects that
inconsistent historical flag rather than weakening schema semantics for the old
artifact. This ZIP remains valid evidence for the survey/detach facts above,
but it is not accepted as a current round trip and does not satisfy the focused-
capture acceptance row.

After validation, the exact exported ZIP and all RF Field Notebook app-private
test data were removed from the Pixel at the operator's request. The release app
remains installed with clean storage; unrelated device files were not touched.

## Remaining short physical workflow

The long endurance run and the completed 104,000-observation replacement survey
will not be repeated. Direct evidence is still required for:

1. create a 1–5 second physical IQ capture and validate its sidecar on Windows;
2. create a reviewed redacted bundle from the capture-bearing workflow;
3. reimport both full and redacted bundles on the Pixel and preserve their
   checksums; and
4. traverse discovery, repaired strength map/equivalent list, and revisit in
   that capture-bearing workflow.

No item in this section is reported as passed until its direct artifact is
recorded.
