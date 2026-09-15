# M1 production hardware gate runbook

Status: Final 30-minute performance gate recorded; transfer-stall and active
low-storage physical cases remain pending

This runbook completes M1's remaining physical acceptance evidence. Raw monitor
files stay under the gitignored `.state/M1-hardware/` directory. Review every
artifact before promoting a sanitized summary or screenshot into this folder.
Never commit precise coordinates, a complete phone or HackRF serial, raw IQ, or
an unreviewed Android diagnostic dump.

## Equipment and topology

- Google Pixel 8a running Android 17.
- Great Scott Gadgets HackRF One with a receive antenna or a safely
  attenuated/shielded source. Never attach a transmitter directly.
- Preferred proven topology: Pixel USB-C port, USB-C-male/USB-A-female adapter,
  USB-A-male/Micro-USB-B-male data cable, HackRF One. The Pixel directly hosts
  and bus-powers the radio. Use the powered hub only if direct power is unstable,
  and record that change.
- Windows development host connected to the Pixel using wireless ADB while the
  Pixel's USB-C port hosts the HackRF.

## Prepare while the Pixel is connected to Windows

1. Build and install the current debug APK, then run all connected tests:

   ```powershell
   cd android
   .\gradlew.bat :app-ui:installDebug connectedDebugAndroidTest
   ```

2. For the clean-install permission gate, remove the existing app first with
   `adb uninstall dev.rfnotebook`, reinstall it, and confirm that the app—not a
   shell command—requests USB permission after the HackRF is attached.
3. Enable wireless debugging in Android Developer options, pair/connect ADB,
   and verify that `adb devices` shows exactly one authorized device after the
   USB-C cable to Windows is removed.

## Baseline and receive test

1. Direct-connect the HackRF and record the exact cable/adapter/hub topology.
2. Grant USB access. Confirm the setup screen identifies HackRF One and reports
   compatible firmware and USB API versions with only a redacted serial suffix.
3. Run the compatibility receive test. Record firmware, API, hardware revision,
   observed byte rate, drop count, and any error. Confirm RF amplifier and
   antenna-port power remain off.
4. Capture a reviewed setup screenshot in `docs/evidence/M1/ui/`.

## Thirty-minute production survey

1. Select the 902–928 MHz starter profile at 4 MS/s, 100 kHz bins, conservative
   gains, RF amplifier off, and antenna-port power off. Record every setting.
2. Complete preflight with location enabled and sufficient storage. Start only
   from the visible app action.
3. From the repository root, start the private monitor:

   ```powershell
   .\scripts\Measure-M1AndroidSurvey.ps1 -DurationMinutes 30 -IntervalSeconds 10
   ```

4. Capture the active screen, turn the screen off, and leave the survey running
   for the full 30 minutes. The persistent notification and service must remain
   present; measurement settings must not change under load.
5. Wake the phone, capture the active health screen, and use orderly Stop.
   Capture the completion summary. Record duration, distance, location coverage,
   accuracy/fix-age observations, aggregate count, USB rate, queue depths, all
   drop/overrun/malformed/gap counters, free storage, battery, and thermal state.

## Recovery and pressure cases

- Pause and Resume from both the app and notification; verify streaming only in
  Active and an orderly final state after Stop.
- During a short active survey, detach the HackRF. Verify native resources close,
  the survey pauses, and an open `USB_DETACH` gap is visible. Reattach, grant USB
  access if requested, Resume, and verify the gap receives an end time.
- During another short active survey, force-stop the app process from ADB. Reopen
  the app and verify the survey is recoverable as Paused with a process-death gap.
- Run the software queue-pressure test and record its user-visible health result.
  Do not claim physical overload unless the physical counter actually changes.
- Exercise low-storage handling with a controlled test hook or safe filesystem
  condition. Do not fill the phone indiscriminately. Confirm an active survey
  records `LOW_STORAGE` and stops orderly below the fixed reserve.

## Handoff

Sanitize and add the final report to `hardware-report.md`, update requirement and
failure-injection evidence, refresh the three UI screenshots, check all M1 boxes,
complete the milestone handoff, mark M1 complete in `ROADMAP.md`, rerun every
validation command, and commit. M1 remains in progress until all physical cases
are evidenced rather than inferred from M0.
