# M5 known limitations

These limitations are explicit product boundaries or low-severity operational
constraints; none substitutes for an unmet MVP acceptance criterion.

- Power is relative dBFS for one fixed setup, not calibrated field strength.
  Maps describe observations and never estimate a transmitter position.
- Fingerprint categories are ranked hints. The MVP does not decode, decrypt,
  descramble, bypass authentication, or inspect application payloads.
- The initial private APK supports Android 10+ and the reviewed `arm64-v8a` and
  `x86_64` ABIs. Physical release evidence is on one Pixel 8a / Android 17 and
  one HackRF One; broader OEM USB/power-policy coverage is future work.
- OpenFreeMap's public service has no availability SLA. Previously cached
  regions and local equivalent lists continue to work; new basemap downloads
  need connectivity and remain subject to the provider's terms.
- Long runs depend on the phone/hub power budget, cable retention, available
  app-private storage, and Android vendor policies. The app surfaces and
  preserves interruptions rather than claiming continuity.
- GPS can be stale, inaccurate, or absent indoors. Such observations remain
  explicitly degraded/unlocated and are not assigned invented coordinates.
- Manual capture is supported; threshold/burst-triggered and pre-trigger capture
  remain post-MVP. Maximum duration is 30 seconds and the configurable reserve
  defaults to 256 MiB.
- Bundle schema 1.0.0 deliberately rejects unknown versions and malformed or
  hostile input. A future schema requires a documented compatibility policy.
- Private sideload updates require the same signing key. Rollback requires
  uninstall/reinstall and therefore an export first if local data must survive.
  Debug and instrumentation builds use the separate `dev.rfnotebook.debug`
  identity so test teardown cannot erase a release notebook.
- Offline map storage is managed by MapLibre. Region deletion/reclamation UI is
  basic and storage should be checked before downloading a large area.

The release decision remains open while the completion audit has incomplete
rows. No unresolved critical or high-severity defect may be accepted for the
final `1.0.0-rc1` candidate.
