# M5 receive-only, privacy, and security review

Review date: 2026-09-22. Scope includes Kotlin/Java, JNI/C/C++, generated APK,
dynamic symbols, Android manifest/permissions, physical Pixel/HackRF behavior,
logs, network accounting, exports/imports, maps, and operator documentation.

## Receive-only boundary

- `scripts/Test-ReceiveOnly.ps1` found no application-facing
  `hackrf_start_tx`, TX-gain, `startTx`, or transmit call in 47 production source
  files. No decoder, descrambler, authentication bypass, or payload UI exists.
- `scripts/Test-NativeExports.ps1` inspected both packaged ABIs. Exactly nine
  project-owned JNI functions are dynamically visible: initialize, open,
  identity, start RX, start sweep, poll receive buffer, statistics, stop, and
  close. No libhackrf/libusb or TX symbol is application-accessible.
- The Android native adapter alone sees the pinned transmit-capable upstream
  header. Its ELF version script and Kotlin interface expose only receive
  operations; other modules depend only on `radio-api`.
- Physical Pixel 8a runs opened the attached HackRF One through Android's
  permitted file descriptor and exercised sweep and focused RX. Firmware
  `2026.01.3` / API `1.10` was compatible. There was no transmission request or
  test source.
- RF amplifier and antenna-port power default false in both RX/sweep contracts,
  equipment profile, service intent fallbacks, and focused capture. The MVP UI
  displays them as Off and offers no implicit toggle. Unit tests cover defaults
  and profile incomparability if a future explicit setting changes.

Result: **Pass — no application-accessible transmit path**.

## Local-data and network boundary

- Precise coordinates, routes, frequencies, device suffixes, notes, detections,
  fingerprints, and IQ use app-private Room/files. `allowBackup=false`,
  `fullBackupContent=false`, and `usesCleartextTraffic=false` are present in the
  release manifest. There is no account, cloud sync, analytics, telemetry, or
  remote diagnostic path.
- The acquisition-service manifest has no network permission and its production
  sources contain no URL/socket client. INTERNET/network-state/Wi-Fi permissions
  enter only through the maps module for OpenFreeMap/MapLibre.
- Android UID network accounting was sampled repeatedly during the screen-off
  902–928 MHz run. The existing foreground totals remained exactly
  `rx=9,811,277 bytes` and `tx=83,831 bytes` while acquisition continued; the
  totals were from the earlier map download. Survey acquisition produced no
  network traffic.
- Map requests use the fixed HTTPS OpenFreeMap Liberty style. Private routes,
  RF values, frequencies, notes, and serials are local GeoJSON sources and are
  never placed in tile URLs. Cached regions and the equivalent list support
  offline use. Attribution is visible and language is always “Observed relative
  strength,” never transmitter/source position.
- Production sources contain no log call. Test-only logs contain resource/count
  summaries, not protected values. Local health records use accuracy buckets,
  rates, counts, and state rather than coordinates/serials/notes/IQ.

## Export/share/import boundary

- Full survey export is built from authoritative completed-survey Room rows and
  includes versioned JSON equipment/band metadata, unit-labelled observations
  CSV, route GeoJSON, geographic aggregate GeoJSON, gap CSV, and linked capture
  artifacts. Export uses `.part` then an atomic move and streams IQ instead of loading
  long captures into memory.
- The UI shows one manifest summary controlling IQ, route, coordinate precision,
  notes/antenna labels, and device suffix before the explicit button creates a
  bundle. The user then chooses a local Android document destination or opens
  Android's share sheet. No save or share occurs automatically and no broad
  storage permission is requested.
- M5 found and fixed a high-severity privacy defect: a redacted bundle that kept
  IQ could previously retain protected fields inside the capture sidecar. The
  exporter now rewrites sidecar device suffix, note, and location according to
  the independent redaction choices, hashes the rewritten entry, and regression
  tests inspect the ZIP contents.
- The same reviewed notes/labels choice now removes user-entered survey, band,
  antenna, adapter, survey-note, and capture-note text. Device suffix and
  location choices remain independent.
- M5 also fixed a reliability defect where capture artifacts were read wholly
  into memory during export. Files are now hashed and ZIP-streamed with a 32 MiB
  regression artifact; atomic finalization and failure cleanup are preserved.
- M5 fixed two additional archive-integrity defects: coordinate rounding had
  rounded non-coordinate GeoJSON measurements, and replacement removed an old
  valid export before a new rename was known to succeed. Redaction now changes
  coordinate-pair arrays only, while finalization requires an atomic replace
  and preserves the prior completed archive on failure.
- Rejected imported ZIPs are now removed from the incoming cache in a `finally`
  path. Reviewed share-cache exports expire after 24 hours without deleting the
  authoritative Room survey or app-private capture set.
- Import stages outside authoritative state, validates paths, duplicates,
  links, entry count/size/total, compression ratio, every required manifest
  field/type, unknown or duplicate JSON properties, inclusion flags, inventory,
  lengths, and SHA-256, then atomically renames. M5 replaced a too-permissive
  schema-string/inventory regex with a bounded strict streaming parser and
  hostile-manifest regression cases. Deterministic fuzz covers traversal/path
  variants and extreme compression. Imported content is never executed.
- Import also caps the selected compressed archive before ZIP parsing, checks
  the 256 MiB reserve while staging and extracting, and requires every capture
  to be one strict schema-valid IQ/sidecar/preview trio with matching ID, byte
  count, and IQ hash.

## Threat/disposition summary

| Threat | Control and result |
| --- | --- |
| Accidental transmit | Structural receive-only API + symbol/source audits; pass |
| Hidden radio power/gain change | Fixed recorded profile; RF/antenna power false; pass |
| Automatic disclosure | No telemetry/account/cloud; explicit reviewed export only; pass |
| Redaction bypass | Nested capture-sidecar regression fixed; ZIP inspection passes |
| Malicious archive | Fail-closed staging, schema/hash/path/size/link/compression checks + fuzz; pass |
| Corrupt database | Room migrations only, no destructive fallback; connected corrupt fixture fails closed |
| Cleartext/backup leakage | Release manifest disallows both; pass |
| Asserted transmitter location | UI/docs/tests use observation/uncertainty language; pass |

No unresolved critical or high-severity safety, security, privacy, or
data-integrity defect remains.
