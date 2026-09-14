# M1 hardware report

Status: Pending production survey gate

## Connected software validation

- Date: 2026-09-14
- Phone: Pixel 8a, Android 17; identifying serial omitted
- Test topology: Windows development host directly to Pixel USB-C using a
  USB-C-to-USB-C cable
- Result: three Room/profile/migration device tests and three application
  regression tests passed
- RF hardware: not attached and not exercised by these runs

## Required production gate

Still to record with the HackRF attached directly to the phone:

- HackRF model, hardware revision, firmware, USB API, and redacted suffix
- Exact USB cable/adapter or powered-hub topology
- 902–928 MHz profile settings and measured cycle/USB rates
- 30-minute screen-off start/end timestamps
- location coverage and accuracy summary
- queue high-water marks, all drop/overrun/malformed/gap counters
- battery and thermal observations
- Pause/Resume/Stop, detach/reattach, process-death, pressure, and low-storage
  results

No hardware acceptance criterion is claimed by this placeholder.

