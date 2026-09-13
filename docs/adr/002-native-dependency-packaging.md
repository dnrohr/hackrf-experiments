# ADR 002: Native dependency build and packaging

- Status: Accepted
- Date: 2026-09-13
- Owners: M0
- Related requirements: FR-USB-001 through FR-USB-007; specification sections 12.3 and 17
- Supersedes: none

## Context

libhackrf depends on libusb and exposes transmit as well as receive APIs. No
application module may see a transmit-capable header. Android builds must be
reproducible and license obligations must travel with redistributed binaries.

## Decision

Build checksum-pinned upstream libusb v1.0.29 and libhackrf v2026.01.3 source
revisions with the Android NDK for `arm64-v8a` and `x86_64`. Link them privately
into `radio-hackrf-native`; do not
publish their headers to another module. Export a project-owned C++/JNI surface
containing only enumerate, open, information, RX/sweep start, stop, and close.
Package notices for libusb (LGPL-2.1-or-later) and the three-clause BSD
libhackrf host library, and publish the required source/relinking materials
with distributions. Use an ELF version script so only project-owned JNI
symbols are dynamically visible.

## Alternatives considered

Bundling arbitrary prebuilt binaries was rejected for provenance and ABI risk.
Exposing `hackrf.h` above the native adapter was rejected because it makes
transmit reachable. Reimplementing the USB protocol was rejected as brittle.

## Consequences

Receive-only enforcement is structural and reviewable. The application remains
subject to upstream redistribution obligations. A physical build and identity
read are still required before M0 can close.

## Validation

CI compiles the private bridge for both M0 ABIs, runs
`scripts/Test-ReceiveOnly.ps1`, and inspects the packaged ELF libraries with
`scripts/Test-NativeExports.ps1`. Hardware validation must additionally record
device API compatibility.

## Follow-up

Perform the required physical stream and detach tests, then carry these notices
and LGPL relinking materials into the release packaging milestone.
