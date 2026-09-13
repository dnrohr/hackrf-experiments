# ADR 003: Android USB permission and file-descriptor integration

- Status: Accepted
- Date: 2026-09-13
- Owners: M0
- Related requirements: FR-USB-001 through FR-USB-006
- Supersedes: none

## Context

Android owns USB permission and device opening. libusb normally discovers and
opens host devices itself, which conflicts with Android's permission model.
The app must open one user-selected HackRF and react to detach without leaking
native resources.

## Decision

Enumerate only USB VID/PID `1d50:6089` with `UsbManager`. Request permission
from a visible activity using an explicit immutable `PendingIntent`, open the
selected `UsbDevice`, duplicate its file descriptor for native ownership, and
wrap it with libusb's supported system-device/file-descriptor facility. The
Kotlin owner closes `UsbDeviceConnection` only after native stop/close. A detach
broadcast triggers the same idempotent stop/close path as cancellation.

Full serials stay in memory; persistence and UI use only a suffix.

## Alternatives considered

Root-only direct USB access, a custom kernel driver, and libusb discovery
without Android permission were rejected. A Java USB transfer loop was kept as
a fallback spike only because it would duplicate mature libusb behavior.

## Consequences

Permission remains explicit and Android-controlled. Descriptor lifetime must
be tested carefully across detach and process teardown. No background USB
permission request is allowed.

## Validation

Physical validation covers deny, grant, open, repeated start/stop, detach,
close, and reattach while checking native handles and process survival.

## Follow-up

Capture target-phone identity, stream, detach, and reattach results before
checking M0's USB acceptance criteria.
