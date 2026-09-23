# Survey and interpretation guide

## Create a comparable survey

Record the antenna, adapters, notes, sample rate, baseband filter, LNA/VGA gain,
RF-amplifier state, and antenna-power state in the equipment profile. A band
profile records non-overlapping included/excluded ranges, bin width, revisit
target, detector threshold, and minimum bandwidth. Editing relevant settings
creates a version; do not compare measurements across versions as though they
were calibrated.

At preflight, confirm the displayed HackRF identity/API compatibility, scan
cycle estimate, storage reserve, and location quality. You may continue through
poor GPS, but those samples remain stale, interpolated only across a bounded
short gap, or unlocated. Survey mode stores spectrum aggregates rather than
continuous wideband IQ.

## Run and recover

Start only from the visible activity. Keep the persistent survey notification
present. The active view surfaces throughput, queue depth, stage drops,
overruns, malformed frames, GPS status, storage, battery, and thermal state.
Pause before changing hardware. Stop performs an orderly drain and finalization.

USB detach creates a timestamped gap. Reconnect the same device and use the
offered paused-survey recovery path; do not treat either side of the gap as
continuous. After process death, reopen the app and recover the paused survey.
Final files are retained; process-local `.part` artifacts are removed.

## Discoveries and maps

Offline processing groups detections by frequency, bandwidth, burst timing,
and comparable equipment profile. Categories are ranked hints, not decoded
identities. Split/merge corrections retain provenance.

The map is labelled **Observed relative strength**. Cells summarize relative
dBFS, sample count, GPS accuracy, and uncertainty. They show where this setup
observed stronger or weaker samples; they do not locate an emitter. GPS gaps
remain visible. Use the equivalent list when a map is inaccessible or offline.

Repeat a route with the same equipment and profile for a meaningful comparison.
If any relevant gain or antenna field changes, start a new version and heed the
incomparable warning.

## Focused capture

Revisit a fingerprint or enter a frequency. Confirm center frequency, sample
rate, duration, fixed gains, predicted bytes, and the 256 MiB reserve. Manual
captures use signed 8-bit interleaved I/Q and atomically finalize the IQ,
sidecar, and preview only after exact bytes, hashes, and zero gaps/overruns are
confirmed. A failed, detached, or storage-starved capture is not presented as
complete.
