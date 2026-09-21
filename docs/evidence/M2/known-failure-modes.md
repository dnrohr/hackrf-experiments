# M2 known limitations and failure modes

- M1 stores one-second min/median/max aggregates. Sub-second burst shape,
  symbol timing, and two-level modulation cannot be reconstructed; hints remain
  low-confidence aggregate evidence.
- The rolling estimator starts from a conservative -90 dBFS seed. A materially
  different receiver floor can initially over-detect or under-detect until
  enough unoccupied observations support a local baseline.
- A missing hardware-center marker in older stored observations prevents an
  offline reprocessing job from inventing a center/DC flag. Newly supplied
  frames can carry the marker, and the synthetic center fixture verifies the
  behavior.
- Broadband overload and ordinary span-wide activity can look alike in
  aggregates. Both observations are retained and reversibly reviewable.
- Fingerprints never cross equipment-profile versions automatically. A user
  must review any desired cross-profile relationship; the merge operation also
  rejects incompatible versions.
- Reprocessing is transactional for derived M2 rows and records running,
  complete, or failed jobs. Source M1 aggregates are never deleted or mutated.
- The discovery list uses a keyed lazy list and deterministic in-memory
  filtering. Database-backed paging is a future scale optimization, not a
  data-integrity requirement.
