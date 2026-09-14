# M0 go/no-go assessment

- Status: **Go**
- Date: 2026-09-13

M0 is complete. Direct Android USB, sustained throughput, screen-off operation,
lifecycle handling, sweep parsing, offline mapping, and the required connected
test command are feasible on the named target and recorded topology.

| Spike question | Outcome | Evidence and constraint |
| --- | --- | --- |
| Can the Android project and native ABIs build reproducibly? | Go | Local tests/build pass for SDK 36 and checksum-pinned libusb/libhackrf on arm64-v8a/x86_64. Pixel instrumentation passes. AGP 9.4.0's UTP worker falsely exits `1` for the colon-bearing Wi-Fi ADB serial despite a `PASSED` suite protobuf, while the same required Gradle task passes over colon-free direct USB ADB. |
| Can the app enforce a receive-only boundary? | Go | Public Kotlin interfaces expose no TX operation; source and ELF guards pass; runtime initialization explicitly disables RF amplification and antenna-port power. |
| Can Android grant permission and open this HackRF? | Go | Pixel 8a Android 17 granted permission and read HackRF One hardware revision, firmware 2026.01.3, API 1.10, and the redacted serial suffix. Android libusb uses only the permitted descriptor. |
| Can USB RX sustain 2, 4, and 8 MS/s? | Go with constraint | All three rates ran for at least five minutes without a native callback error or crash. Counted one-slot overwrites rose with rate; M1 must replace the spike handoff with bounded multistage queues. |
| Can the highest rate survive 15 minutes screen-off with location and controls? | Go with constraint | 8 MS/s remained active for 900 seconds of verified non-interactive wakefulness with location registration and notification controls. The spike counted 1,234 cumulative one-slot overwrites, reinforcing the M1 queue requirement. |
| Do detach and cancellation release the device? | Go | Notification Stop and physical active-stream detach stopped RX, closed the session, removed the service/notification, and kept the process alive. Patched physical reattach refreshed permission and redacted identity without restarting the process. Instrumented activity-task removal left explicit foreground RX running and advancing until test cleanup. |
| Can sweep frames become verified observations? | Go | A physical Android 88–108 MHz sweep produced 1,504 frames in eight seconds with zero malformed blocks/errors; a sanitized physical frame has a passing parser regression. An independent zero-gain Windows run produced 10 complete sweeps spanning exactly 88–108 MHz. |
| Can stored observations render with accuracy and offline support? | Go with constraint | Pixel evidence shows local route/accuracy/strength overlays, visible attribution, and a 309-resource / 8,637,828-byte offline region downloading and reopening. A MapLibre demo-style native crash was avoided by the documented OpenFreeMap style. |

## Recommendation

Begin M1. No RF-head pivot is needed: direct Android USB is a measured go.

## Safety and privacy findings

- Source and packaged-ELF guards find no application-facing transmit symbol.
- RF amplifier, survey gains, and antenna-port power remain off by default.
- Committed evidence excludes precise live coordinates, full device serials,
  raw IQ, and timestamps. The small sweep fixture contains a public test range
  and rounded power bins only.
- Map tiles use a network connection; survey overlays remain local and are not
  encoded into tile requests.
