# RF Field Notebook quick start

RF Field Notebook is a receive-only, local-first Android field survey tool. It
does not identify a transmitter location, calibrate field strength, decode
payloads, upload data, or provide authority to monitor a signal.

1. Verify the APK SHA-256 and signing-certificate SHA-256 against
   `docs/evidence/M5/release-checksums.md`, then install the private release.
2. Fit a suitable passive antenna before connecting the HackRF. Use a powered
   USB-C hub if the phone cannot supply stable power. Never connect a
   transmitter directly to the receiver input.
3. Open the app, connect the HackRF, approve permission for that device, and
   verify the displayed board, firmware, API, and redacted serial suffix.
4. Run the receive compatibility test. It discards samples after measuring the
   receive path; it does not record IQ.
5. Choose the 902–928 MHz starter range or enter another lawful receive range.
   Review the fixed sample rate, filter, LNA/VGA gain, scan estimate, detector
   threshold, storage, and GPS state.
6. Start from the visible preflight screen. Android asks for location only when
   a survey needs it. Explicitly continuing with degraded GPS preserves
   observations as stale or unlocated; the app never invents coordinates.
7. Monitor the persistent notification and the active screen. Pause or Stop is
   always explicit. Screen-off operation continues in the foreground service.
8. Stop and review the summary, Discoveries, uncertainty-aware map or equivalent
   list, then revisit a candidate for a short focused capture if appropriate.
9. Review every export manifest. Prefer rounded or omitted coordinates, omit
   device suffixes and notes, and include IQ only when the recipient needs it.

The RF amplifier and antenna-port power default off. Survey values are relative
dBFS under fixed equipment settings; changing antenna, adapters, gains, sample
rate, or filter makes observations incomparable unless a separate profile is
used.

See [equipment safety](equipment-safety.md), [survey guide](survey-guide.md),
[export guide](export-guide.md), [privacy statement](privacy.md), and
[troubleshooting](troubleshooting.md).
