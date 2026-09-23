# RF Field Notebook 1.0.0-rc1 release notes

`1.0.0-rc1` is the first privately signed MVP candidate. It is a local-first,
receive-only Android application for one directly attached HackRF One.

## MVP workflow

- Identify compatible HackRF firmware/API and run a receive-only compatibility
  check.
- Create fixed equipment and band profiles, then record foreground,
  GPS-associated spectrum surveys with explicit gaps and health counters.
- Process recurring detections into editable fingerprints and compare only
  compatible equipment versions.
- Explore selected fingerprints as observed relative-strength cells or an
  equivalent uncertainty-aware list, including an offline basemap region.
- Revisit a fingerprint for a bounded CS8 IQ capture with sidecar, hash, and
  preview.
- Save or explicitly share full/rounded/coordinate-omitted survey bundles, and
  defensively reimport schema `1.0.0` archives.

## M5 hardening changes

- Fixed the physical-device strength-map crash caused by nested vertical
  scrolling; production-shaped connected regressions cover large datasets.
- Isolated the debug/instrumentation application identity so test teardown can
  never uninstall the release notebook.
- Added completed-survey summary reopening so a later linked capture can be
  reviewed and included in the survey export.
- Preserved the originating survey/equipment link for revisit capture, locked
  the comparable sample rate, recorded actual application/location metadata,
  and made the capture index recoverable across process death.
- Added explicit local document saving alongside Android sharing, without broad
  storage permission.
- Streamed IQ into ZIP output, redacted nested capture sidecars, limited hostile
  archives, corrected compression-ratio and coordinate-rounding logic, and
  required atomic export replacement.
- Replaced platform-sensitive capture-sidecar redaction regexes with structured
  JSON rewriting after Android 17 exposed an ICU syntax incompatibility during
  the physical reviewed-export workflow; connected and physical regressions
  cover the corrected minified build.
- Strictly validates capture sidecars and complete IQ/sidecar/preview groups on
  import, caps the selected compressed archive, and enforces the storage reserve
  during both staging and extraction.
- Added fail-closed corrupt-database detection and cleanup for interrupted
  capture/export/import staging and expired share-cache copies.
- Separated active collection time from wall-clock span, froze it across pauses,
  removed GPS motion inside combined accuracy bounds from route distance, and
  delayed durable finalization until RX queues are fully drained/accounted.
- Added first-run safety/privacy education, accessible gain labels, visible
  compatibility-test results, and an in-app receive-test Stop action.
- Preserved setup/navigation state across rotation and reloads persisted
  discovery/map data when transient presentation state is recreated.
- Enabled R8/resource shrinking, pinned the Gradle distribution checksum,
  separated release/debug identities, and packaged notices plus corresponding
  native source/relinking material.

## Distribution

This candidate is a private sideload under ADR 009 for Android 10+ and the
reviewed `arm64-v8a` / `x86_64` ABIs. It has no updater, account, telemetry, or
cloud sync. Verify the APK and signing certificate against
[release-checksums.md](release-checksums.md) before installation.

See [known limitations](known-limitations.md), the
[operator guides](../../operator/README.md), and the
[completion audit](completion-audit.md) before field use.
