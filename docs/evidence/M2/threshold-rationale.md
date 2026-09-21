# M2 detector and clustering thresholds

Status: Implemented and covered by deterministic fixtures on 2026-09-21.

## Versioned defaults

`detector-v1` uses the band profile's configured SNR threshold and minimum
bandwidth. The starter profile remains conservative at 8 dB SNR and one
100 kHz bin. Adjacent candidates may remain open for 1,500 ms; activity lasting
15,000 ms becomes a persistent carrier. A candidate occupying at least 70% of
the current span is flagged as a broadband impulse, and a 12 dB span-wide
median rise is additionally flagged as overload.

The rolling baseline uses the lower quartile of a 32-sample per-bin window,
seeded at -90 dBFS. A robust span median bootstraps a higher ordinary startup
floor and three coherent frames permit a bounded floor shift; span medians
above -50 dBFS remain overload candidates rather than trusted baselines.
Samples more than 6 dB above the current baseline do not enter that bin's
window. This intentionally favors false-positive review over
allowing a persistent carrier to train itself out of visibility. Baselines are
keyed by equipment-profile version and expose support, age, and confidence.

`cluster-v1` considers detections only within the exact same equipment-profile
version. It uses 250 kHz center-frequency proximity, a 75% relative bandwidth
tolerance, compatible discrete/persistent behavior, and a bounded duration
tolerance. IDs are name-based UUIDs derived from the algorithm
version, comparable profile, and sorted source detection IDs, making output
stable across input order. Location is descriptive and does not determine
identity.

These values are initial aggregate-domain thresholds, not calibrated RF field
strength. One-second M1 aggregates cannot establish sub-second modulation or
payload identity; every displayed hint therefore includes confidence and a
plain-language evidence statement.
