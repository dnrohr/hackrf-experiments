# M0 go/no-go assessment

- Status: **Needs physical evidence**
- Date: 2026-09-13

M0 is not complete. Software-only risks have initial outcomes; device-dependent
risks remain unevaluated and block M1.

| Spike question | Outcome | Evidence and constraint |
| --- | --- | --- |
| Can the Android project and native ABIs build reproducibly? | Go with constraint | Local tests/build pass for SDK 36 and checksum-pinned upstream libusb/libhackrf on arm64-v8a/x86_64; clean CI must confirm. |
| Can the app enforce a receive-only boundary? | Go with constraint | Public Kotlin interfaces expose no TX operation; an ELF export audit proves the packaged libraries expose only nine project-owned RX JNI functions. Hardware runtime checks remain. |
| Can Android grant permission and open this HackRF? | Not evaluated — blocks M0 | No target phone/topology/HackRF was available. |
| Can USB RX sustain 2, 4, and 8 MS/s? | Not evaluated — blocks M0 | Mandatory 5-minute runs have not occurred. |
| Can the highest rate survive 15 minutes screen-off with location and controls? | Not evaluated — blocks M0 | The service owns native RX, foreground location, health counters, and notification Stop; physical lifecycle evidence is missing. |
| Do detach and cancellation release the device? | Go with constraint | Deterministic lifecycle tests pass and the native adapter has one idempotent stop/close path; physical detach evidence remains. |
| Can sweep frames become verified observations? | Go with constraint | The platform-independent processor extracts HackRF raw-block frequency headers and computes DFT power bins in deterministic tests; device fixture and Windows cross-check remain. |
| Can stored observations render with accuracy and offline support? | Go with constraint | Synthetic observations round-trip through private persistence; MapLibre expressions encode accuracy and strength, and the action downloads or reopens a saved region. Physical visual/offline evidence remains. |

## Recommendation

**Do not begin M1.** Connect the named target Android phone through the powered
topology and complete every pending row in `benchmark-report.md`. A failed
direct-USB result must produce an ADR evaluating the RF-head alternative before
the specification or roadmap changes.

## Safety and privacy findings

- Source and packaged-ELF guards find no application-facing transmit symbol.
- RF amplifier and antenna-port power are not exposed or enabled by the spike.
- Synthetic map observations contain no user data; committed evidence contains
  no precise user coordinates, full serial, IQ, or captured frequencies.
- Map tiles use a network connection; survey overlays remain local.
