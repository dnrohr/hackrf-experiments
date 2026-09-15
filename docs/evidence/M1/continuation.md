# M1 continuation handoff

Updated: 2026-09-15

## Safe current state

- The latest 902–928 MHz survey is Complete; no RF Field Notebook acquisition
  service or foreground notification remains.
- The HackRF may be physically disconnected from the phone.
- The debug app was installed with the 32-buffer native-ring build; the final
  post-change 30-minute screen-off gate completed with zero drops and zero
  overruns. After the evidence was committed, the app data was cleared; the
  package now occupies approximately 3.5 KB and the test surveys are no longer
  on the phone.
  Host-side database snapshots and unused archive caches have been deleted;
  committed evidence remains in the repository.
- Work is committed directly on `main`; latest commit `a4f1a01` is pushed to
  `origin/main`.

## Latest completed evidence

- The post-32-buffer 30-minute screen-off gate completed on 2026-09-15. It
  persisted 484,640 aggregates and 1,854 location fixes over 31:04 with
  99.52% location coverage, zero drops, zero overruns, zero malformed/stale
  frames, no acquisition gaps, bounded queues, and a 26.18 GB storage floor.
- A separate 2026-09-15 USB detach/reattach readback completed with one
  92,201 ms closed `USB_DETACH` gap, 0 dropped units, and final status
  COMPLETE. Reattach required fresh Android USB permission; that rejected
  Resume branch is recorded explicitly, while the earlier recovery run covered
  Resume after permission approval.

- Commit `83913c0` fixes worker shutdown after process death and prevents
  `FINALIZING` surveys from being offered as resumable.
- Commit `56c6cb9` preserves the specific detach/recovery warning beside the
  generic acquisition-loss warning.
- A physical detach/reattach plus forced-process recovery sequence completed.
  The final persisted summary reported Complete, 60,060 aggregates, 338 GPS
  fixes, zero drops, zero overruns, and two closed zero-drop gaps:
  `PROCESS_DEATH` and `USB_DETACH`.
- A revised 2 MS/s screen-off rerun completed for 34 minutes 42 seconds with
  1,028,560 aggregates, 3,943 location fixes, 0 dropped buffers, 0 malformed
  frames, bounded queues, and 3 persisted native overruns. It is useful
  evidence of screen-off operation and visible loss accounting, but it does not
  satisfy a clean zero-loss performance claim. The native ring was then
  increased from 8 to 32 buffers and the clean gate above followed.
- The 32-buffer build passes `lint test assembleDebug`, receive-only static
  validation, native export validation, and planning validation. Reproducible
  Android build outputs were then cleaned from the host.
- Full `lint test assembleDebug`, planning validation, receive-only static
  validation, and native export validation passed after the fixes. The
  2026-09-15 connected result XML/report files show 5/5 storage tests and 3/3
  app tests with zero failures; the aggregate Gradle task still returned
  nonzero despite those successful reports and needs a test-runner diagnosis.
- Full details are in `hardware-report.md` in this directory.

## Remaining before M1 can be marked complete

1. Audit the requirement links and acceptance boxes against the clean
   post-32-buffer hardware gate.
2. Preserve the documented limitations: exact USB adapter topology and
   explicit low-storage/queue-pressure hardware scenarios are not claimed by
   this run; the software injection/storage tests remain the applicable evidence.
3. Complete the milestone Handoff section, update `ROADMAP.md`, rerun all
   validation, and commit with an `M1:` message.
4. Phone test-data cleanup is complete; future USB permission will need to be
   granted again if another HackRF run is started.

## Suggested next action

Start by reading `AGENTS.md`, `ROADMAP.md`, the full specification, the M1 brief,
and accepted ADRs as required. Then inspect this handoff and `hardware-report.md`.
Prefer the acceptance audit and validation next; the largest hardware gate is
now complete.
