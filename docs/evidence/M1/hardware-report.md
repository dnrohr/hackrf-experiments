# M1 hardware report

Status: Production survey, process-death recovery, detach/reattach recovery,
and the post-32-buffer performance gate recorded; transfer-stall and
low-storage active-service cases remain pending

## Connected software validation

- Date: 2026-09-14
- Phone: Pixel 8a, Android 17; identifying serial omitted
- Test topology: Windows development host directly to Pixel USB-C using a
  USB-C-to-USB-C cable
- Result: three Room/profile/migration device tests and three application
  regression tests passed
- RF hardware: not attached and not exercised by these runs

## Compatibility receive observation

- Date: 2026-09-14
- Phone: Google Pixel 8a, Android 17; full device identifier omitted
- ADB topology: wireless ADB after the phone USB-C port was released for the
  HackRF
- HackRF: Great Scott Gadgets HackRF One; firmware 1.10; full serial omitted
- App state: foreground `RX only` compatibility service
- Receive mode: 2 MS/s test stream; no transmit control or path was used
- Observation: the Android notification byte counter advanced from 187 MB to
  189 MB during verification, with 0 dropped buffers
- Location permission: granted for the combined USB/location service

This is compatibility-test evidence only. It does not claim the M1 production
survey gate, sustained 30-minute behavior, or detach/recovery acceptance.

### Post-buffer-change compatibility retest

- Date: 2026-09-14
- Phone: Google Pixel 8a, Android 17; wireless ADB; full device identifier omitted
- HackRF: Great Scott Gadgets HackRF One; full serial omitted
- App build: M1 APK with the bounded eight-buffer native ring and compatibility
  service firmware/API gate
- Receive mode: 2 MS/s compatibility RX; no transmit control or path was used
- Observation: foreground notification reported 195,559,424 received bytes and
  0 dropped buffers after the short retest interval
- Cleanup: the service process was stopped and no RF Notebook foreground service
  remained afterward. Because Android rejected a shell-issued action for the
  non-exported service, this cleanup used the app-process stop fallback and is
  not evidence for the production survey Stop/finalization path.

This retest is stronger evidence for the revised native buffering path, but it
does not replace the pending production survey, detach/recovery, or clean-stop
evidence.

### Post-persistence and processing-queue retest

- Date: 2026-09-14
- Phone: Google Pixel 8a, Android 17; wireless ADB; full device identifier omitted
- HackRF: Great Scott Gadgets HackRF One; UI-reported firmware 2026.01.3,
  USB API 1.10; full serial omitted
- App build: M1 APK with batched Room persistence, processing-queue capacity
  increased from 16 to 128, and per-stage drop reporting
- Profile: 902–928 MHz, 100 kHz bins, 4 MS/s, LNA 16 dB, VGA 16 dB, RF
  amplifier off, antenna-port power off
- Runtime observation: 52 seconds active; USB rate 2.56 MB/s; 13,520
  aggregates persisted; GPS accuracy 18.9 m with a 0-second fix age
- Health observation: native/processing/disk queues 0/0/0; stage drops 0/0/0;
  total drops 0; overruns 0; malformed frames 0; battery 76%; thermal status 0;
  approximately 25 GB remained free
- Cleanup: visible Stop was used and, after a 25-second drain window, no
  production survey service or foreground notification remained. Direct Room
  status readback remains pending, so this is not promoted to a clean-finalization
  acceptance claim.

This run is evidence that the bounded processing queue and batched persistence
path remove the losses observed in the preceding 4 MS/s run.

### Forced-process recovery retest

- Date: 2026-09-14
- Phone: Google Pixel 8a, Android 17; wireless ADB; full device identifier omitted
- HackRF: Great Scott Gadgets HackRF One; UI-reported firmware 2026.01.3,
  USB API 1.10; full serial omitted
- Profile: 902–928 MHz, 100 kHz bins, 4 MS/s, LNA 16 dB, VGA 16 dB, RF
  amplifier off, antenna-port power off
- Interruption: the application process was force-stopped while the survey was
  Active, then relaunched with the HackRF still attached
- Recovery: the setup screen offered the interrupted survey; its Recover action
  restarted the foreground acquisition service and returned the same survey to
  Active without an application crash
- Post-recovery observation: USB rate 2.78 MB/s; 4,420 new aggregates reported
  persisted; GPS accuracy 17.9 m with a 0-second fix age; native/processing/disk
  stage drops 0/0/0; overruns 0; malformed frames 0
- Regression found and fixed: orderly channel closure had raised an unhandled
  `ClosedReceiveChannelException`; workers now treat channel closure as normal
  completion, with a unit test covering repeated close and waiting receive
- State-selection correction: a `FINALIZING` record is no longer presented as a
  resumable interrupted survey

This is runtime evidence for process-death recovery and continued receive-only
acquisition. Direct database readback of the persisted process-death gap remains
pending and is not inferred from the live UI alone.

### Physical USB-detach observation

- Date: 2026-09-14
- Starting state: the recovered 4 MS/s production survey was Active with the
  foreground service present and no reported stage drops or overruns
- Action: the operator physically disconnected the HackRF from the phone
- USB result: Android removed the HackRF USB device and no HackRF remained in
  the reported USB topology
- Application result: the survey moved to Paused, reported 0.00 MB/s, retained
  its foreground service, and drained native/processing/disk queues to 0/0/0
- Health result: native/processing/disk stage drops remained 0/0/0 and overruns
  and malformed frames remained zero; the service-gap health counter produced
  the visible `Acquisition loss recorded` warning
- UI defect found and fixed: the periodic health update replaced the more useful
  `USB detached; gap recorded. Reattach and Resume.` explanation. Operational
  warnings are now retained alongside aggregate health warnings, with a unit
  regression test.

### Reattach, Resume, and finalization result

- Action: the operator physically reattached the same HackRF; the application
  reacquired USB permission and Recover reopened the paused survey
- Resumed state: Active at 2.56 MB/s with native/processing/disk queues 0/0/0,
  stage drops 0/0/0, overruns 0, malformed frames 0, and a live GPS fix
- Cleanup: the visible Stop control completed finalization; afterward no
  production acquisition service or foreground notification remained
- Persisted summary: status Complete; duration 10 minutes 48 seconds; 60,060
  spectrum aggregates; 338 location fixes; 72.0% located observation batches;
  zero drops and zero overruns
- Persisted gaps: two closed gaps were displayed in the summary:
  `PROCESS_DEATH` (47,867 ms, zero dropped units) and `USB_DETACH` (422,641 ms,
  zero dropped units)
- Final health: USB rate 2.54 MB/s before shutdown; queues 0/0/0; battery 56%;
  thermal status 0; approximately 25 GB free

This completes the physical detach/reattach, forced-process recovery, direct
gap readback, and orderly finalization sequence for this device and build.

## Production survey observation

- Date: 2026-09-14
- Phone: Google Pixel 8a, Android 17 (SDK 37)
- HackRF: Great Scott Gadgets HackRF One; UI-reported firmware 2026.01.3,
  USB API 1.10; full serial omitted
- Profile: 902–928 MHz, 100 kHz bins, 2 MS/s, LNA 16 dB, VGA 16 dB, RF
  amplifier off, antenna-port power off
- Runtime: 53 minutes 22 seconds shown by the active-survey screen; the
  private monitor recorded 30 minutes of screen-off service presence
- GPS: fix reported at ±8.2 m with age 0 seconds during final active-screen
  observation; route distance shown as 0.09 km
- Persistence: 835,920 aggregates reported persisted before orderly Stop
- Final observed health before Stop: native/processing/persistence queues
  0/0/0; malformed frames 0; overruns 5; total drops 411,011; battery 84%;
  thermal status 0; approximately 25 GB remained free on the phone
- Foreground behavior: notification remained present with three actions while
  the screen was off; monitor samples recorded `Dozing` and service present
  throughout the 30-minute interval
- Finalization: the app’s visible Stop control was used after unlocking; the
  foreground service then disappeared. An immediate readback during the
  bounded drain still showed the recoverable prompt; after the service had
  settled, a later app relaunch no longer showed an interrupted-survey prompt.
  This supports eventual completion, but a direct Room status readback is still
  required before promoting it to an acceptance claim.

The run demonstrates screen-off operation and visible loss accounting, but it is
not a clean performance gate because the drop count was nonzero. The exact
physical cable/adapter topology, queue-pressure, and low-storage cases remain
pending. The native adapter has
since been changed from the M0 one-slot buffer to a bounded eight-buffer ring;
the run above predates that change and must not be used to judge the revised
drop rate.

## Remaining production gate evidence

Still to record or verify:

- Exact USB cable/adapter or powered-hub topology
- Hardware revision and measured sweep-cycle rate
- Queue high-water marks
- Explicit queue-pressure and low-storage hardware results
- A clean 30-minute screen-off run on the revised zero-loss pipeline (the prior
  30-minute screen-off run predates the buffering fixes and recorded losses)

## Revised 30-minute screen-off rerun

- Date: 2026-09-14
- Phone: Google Pixel 8a, Android 17 (SDK 37)
- HackRF: Great Scott Gadgets HackRF One; UI-reported firmware 2026.01.3,
  USB API 1.10; full serial omitted
- Profile: 902–928 MHz, 100 kHz bins, 2 MS/s, LNA 16 dB, VGA 16 dB, RF
  amplifier off, antenna-port power off
- Runtime: 34 minutes 42 seconds from persisted start to stop; the screen-off
  monitor covered the required 30-minute interval
- Persistence: 1,028,560 spectrum aggregates and 3,943 location fixes;
  99.7% of observation batches were located
- Final status: COMPLETE; 0 dropped frames, 3 overruns, 0 malformed frames,
  0 stale-fix count, 1,820 unlocated observations, and no explicit acquisition
  gaps
- Health: foreground service remained present in every monitor sample; receive
  rate was approximately 1 MB/s; queue maxima were native 1, processing 31,
  persistence 1; minimum available storage was approximately 24.6 GiB and
  thermal status remained 0

This rerun confirms screen-off foreground operation, bounded persistence, and
explicit overrun accounting on the eight-buffer pipeline. It is not promoted as
a zero-loss performance pass because three native overruns were persisted
during steady-state acquisition and surfaced as an acquisition-loss warning.

The native adapter was subsequently changed to a bounded 32-buffer ring
(approximately 8 MiB per session); the post-change gate is recorded below.

## Post-32-buffer 30-minute screen-off gate

- Date: 2026-09-15
- Phone: Google Pixel 8a, Android 17 (SDK 37), wireless ADB used while the
  HackRF occupied the phone USB port
- HackRF: Great Scott Gadgets HackRF One; UI-reported firmware 2026.01.3,
  USB API 1.10; serial suffix shown in the app and omitted here
- RF USB topology: HackRF directly attached to the Pixel 8a through the
  data-capable phone adapter/cable; exact adapter model was not recorded
- Profile: 902–928 MHz, 100 kHz bins, 2 MS/s, 1.75 MHz filter, LNA 16 dB,
  VGA 16 dB, RF amplifier off, antenna-port power off
- Runtime: 31 minutes 04 seconds from persisted start to orderly stop; the
  screen-off monitor covered the required 30-minute interval
- Persistence: 484,640 spectrum aggregates and 1,854 location fixes;
  location coverage was 99.52%; 2,340 observations remained unlocated
- Final status: COMPLETE; 0 dropped frames, 0 overruns, 0 malformed frames,
  0 stale fixes, and no acquisition gaps
- Health: foreground service remained present in every monitor sample; receive
  rate peaked at 1,539,988 bytes/second; queue maxima were native 1,
  processing 31, persistence 1; all stage-drop maxima were 0; minimum
  available storage was 26,179,584,000 bytes; battery reached 80%; thermal
  status remained 0

This is the clean post-change performance gate for the bounded 32-buffer native
ring. The persisted overrun and drop fields, rather than only the notification,
were used for the acceptance result. The stale GPS state shown in preflight was
preserved, and unlocated observations remained visible in the completed survey.

## USB detach/reattach readback

- Date: 2026-09-15
- Phone: Google Pixel 8a, Android 17 (SDK 37), wireless ADB while the HackRF
  occupied the phone USB port
- HackRF: Great Scott Gadgets HackRF One; UI-reported firmware 2026.01.3,
  USB API 1.10; serial suffix omitted
- Run: 1 minute 47 seconds, 2 MS/s, 902–928 MHz, RF amplifier off,
  antenna-port power off
- Result: physical removal caused the active survey to pause and display
  `USB detached; gap recorded`; after reconnection the app required fresh USB
  permission, so Resume was rejected with `PERMISSION_REQUIRED`. Orderly Stop
  then finalized the survey and closed the persisted `USB_DETACH` gap.
- Persistence: 4,160 aggregates and 96 location fixes; 43.75% located;
  one 92,201 ms gap with 0 dropped units; 0 overruns, malformed frames, or
  stale fixes; final status COMPLETE
- Health: queue maxima native 0, processing 19, persistence 0; stage drops 0;
  minimum available storage 26,574,708,736 bytes; battery 67%; thermal 0

This readback independently confirms visible detach handling, bounded resource
drain, and loss-preserving gap closure. The earlier recovery sequence also
covered reattach plus Resume after permission approval; this run records the
permission-required branch explicitly.

## Transfer-stall test attempt (not claimed as stall evidence)

- Date: 2026-09-15
- A short active 2 MS/s survey was physically disconnected to exercise the
  no-data path. The service classified the event as `USB_DETACH`, paused, and
  displayed the detach warning; it did not enter the distinct transfer-stall
  handler.
- Orderly stop produced COMPLETE status with 2,600 unlocated observations,
  0 drops, 0 overruns, 0 malformed frames, and one closed 38,260 ms
  `USB_DETACH` gap with 0 dropped units.

This attempt is retained to show that physical removal is not being mislabeled
as a transfer stall. A true instrumented transfer-stall case remains pending.
