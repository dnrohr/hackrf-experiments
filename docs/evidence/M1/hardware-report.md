# M1 hardware report

Status: Production survey recorded; recovery cases pending

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
physical cable/adapter topology, pause/resume, detach/reattach, process-death,
queue-pressure, and low-storage cases remain pending. The native adapter has
since been changed from the M0 one-slot buffer to a bounded eight-buffer ring;
the run above predates that change and must not be used to judge the revised
drop rate.

## Remaining production gate evidence

Still to record or verify:

- Exact USB cable/adapter or powered-hub topology
- Hardware revision and measured sweep-cycle rate
- Queue high-water marks and persisted final gap records
- Pause/Resume, detach/reattach, process-death, pressure, and low-storage
  results

No hardware acceptance criterion is claimed by this placeholder.
