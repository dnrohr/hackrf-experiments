# M4 physical capture report

- Date: 2026-09-21
- Phone: Google Pixel 8a (`akita`), Android 17
- Connection: ADB over Wi-Fi at `172.16.134.7:5555`
- USB topology: HackRF One attached to the phone's USB host connection
- Radio mode: receive only; RF amplifier off; antenna-port power off
- Center frequency: 902,000,000 Hz
- Sample rate / filter: 2,000,000 samples/s / 1,750,000 Hz
- Gain: LNA 16 dB, VGA 16 dB
- Requested duration: 2,000 ms
- Measured preview delivery: 1.961687 MS/s over a one-second host interval
- Result: 8,000,000 bytes, 4,000,000 signed-int8 interleaved complex samples
- SHA-256: `ea863a86d9281684ad0212974e6a909af249c99243e12150a93599736b057bd3`
- Gaps / overruns: 0 / 0; capture finalized `COMPLETE`
- Sidecar: 1,017 bytes; preview: 2,061-byte PGM

The default export review omitted route, coordinates, notes, and identifier
while retaining the explicitly selected IQ. Android displayed `Sharing 1 file`
for the reviewed archive. The archive remained app-controlled and was copied to
the ignored `.state/M4/` validation area for the Windows check.

Radioconda Python successfully imported GNU Radio and opened the extracted IQ
as a NumPy `int8` array shaped `4,000,000 × 2`. Observed component ranges were
I `3..5`, Q `-6..-4`; mean relative power was `-25.9609 dBFS`, matching the
focused display. `scripts/Test-M4Capture.ps1` independently matched length,
complex-sample count, encoding, and SHA-256.

No test transmission or direct transmitter connection was used.
