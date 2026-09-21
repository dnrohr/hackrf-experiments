# M2 fixture evaluation

Date: 2026-09-21

## Dataset and tolerances

The CC0 synthetic fixture set and golden contract are under `test-data/M2/`.
Generation is deterministic in `SyntheticFixtureGenerator.kt`. No coordinates,
device identifiers, payload samples, or over-the-air recordings are included.

- Frequency tolerance: 100 kHz (one fixture bin).
- Bandwidth tolerance: 100 kHz.
- Start/end tolerance: 1,000 ms (one M1 aggregate bucket).
- SNR tolerance: 1 dB.
- Grouping: exact expected detection count and discrete/persistent kind.

## Results

`FixtureEvaluationTest` passed noise-only, continuous-carrier, repeating-burst,
center/DC, broadband-overload, corrupt/reordered, and shuffled-order cases. The
20-second noise-only window produced zero detections and therefore zero
sustained false fingerprints. The continuous carrier remained above its seeded
baseline and transitioned to persistent state. Repeating bursts produced three
deterministically closed detections.

Two shuffled evaluations produced the same normalized start, end, frequency,
bandwidth, kind, and flag output. `FingerprintClustererTest` separately proved
stable results under reversed input and separation of incompatible equipment
profiles.

On the Windows development host, a repeated workload of 13,200 aggregate
frames completed in 281 ms with a measured JVM live-heap delta of 6,834,320
bytes. This is a JVM unit-test measurement, not an Android device performance
claim.

The full connected suite subsequently passed on the Pixel 8a (Android 17 / API
37): 13 application and Room tests with zero failures, errors, or skips. This
includes the 500-fingerprint UI case, Room 2→3 migration, offline completed-
survey reprocessing, correction persistence, and earlier M0/M1 regressions.
See `docs/evidence/M2/ui-review.md` for target and screenshot evidence.

## Confusion and false-positive notes

- A strong signal present at estimator startup is deliberately treated as
  occupied; the seeded baseline prevents it from vanishing. This can flag a
  genuinely elevated local floor until lower observations arrive.
- Center/DC, mirror symmetry, broadband impulse, overload, and corrupt-frame
  evidence is retained as flags. Flags do not delete detections.
- Classification is shape-based and cautious. One-second aggregates cannot
  prove OOK, FSK, or analog FM, so hints explicitly request later focused-IQ
  confirmation and never assert protocol or ownership.
- No sanitized real sweep corpus was added because the repository does not yet
  contain a reviewed redistributable M1 survey. The shipped fixtures are fully
  synthetic.
