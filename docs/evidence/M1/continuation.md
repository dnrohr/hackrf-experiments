# M1 continuation handoff

Updated: 2026-09-14

## Safe current state

- The latest 902–928 MHz survey is Complete; no RF Field Notebook acquisition
  service or foreground notification remains.
- The HackRF may be physically disconnected from the phone.
- The debug app was installed with the 32-buffer native-ring build; the phone
  retains its local survey database until the final M1 evidence decision.
  Host-side database snapshots and unused archive caches have been deleted;
  committed evidence remains in the repository.
- Work is committed directly on `main`; latest commit `ffe8638` is pushed to
  `origin/main`.

## Latest completed evidence

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
  satisfy a clean zero-loss performance claim. The native ring has since been
  increased from 8 to 32 buffers; a post-change hardware run remains required.
- The 32-buffer build passes `lint test assembleDebug`, receive-only static
  validation, native export validation, and planning validation. Reproducible
  Android build outputs were then cleaned from the host.
- Full `lint test assembleDebug`, planning validation, receive-only static
  validation, and native export validation passed after the fixes. The
  connected test result files report 5/5 storage tests and 3/3 app tests, but
  the Gradle tasks return failure during automatic uninstall and need a
  toolchain-level fix or a clean-device rerun.
- Full details are in `hardware-report.md` in this directory.

## Remaining before M1 can be marked complete

1. Resolve the three-overrun result and record a clean 30-minute 902–928 MHz
   screen-off run using the revised pipeline. Both long runs now have explicit
   loss accounting, but neither qualifies as a zero-loss performance pass.
2. Run and record explicit low-storage and queue-pressure hardware scenarios;
   preserve counted loss and visible warnings without changing RF settings.
3. Record exact USB cable/adapter topology and queue high-water marks if the UI
   or diagnostics expose them.
4. Run the required connected Android test suite for the final build if it has
   not been rerun after the latest changes.
5. Audit M1 requirement links and acceptance boxes, complete the milestone
   Handoff section, update `ROADMAP.md`, rerun all validation, and commit with an
   `M1:` message.
6. After the milestone evidence is complete, verify the phone is clean and
   remove the intentionally retained survey database if the user wants the
   test data reclaimed.

## Suggested next action

Start by reading `AGENTS.md`, `ROADMAP.md`, the full specification, the M1 brief,
and accepted ADRs as required. Then inspect this handoff and `hardware-report.md`.
Prefer the clean 30-minute screen-off run next, because it is the largest
remaining acceptance gate.
