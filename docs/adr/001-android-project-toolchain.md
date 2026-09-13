# ADR 001: Android project and toolchain baseline

- Status: Accepted
- Date: 2026-09-13
- Owners: M0
- Related requirements: specification sections 12, 16, 17, and 21
- Supersedes: none

## Context

The product requires Android 10 support, Compose, native code, CI tests, and
clear ownership boundaries. The M0 host has JDK 17; a clean local installation
of Android SDK 36, Build Tools 36.0.0, NDK 28.2.13676358, and CMake 3.22.1
successfully compiled the initial modules and both native ABIs.

## Decision

Use Gradle 9.6.0, Android Gradle Plugin 9.4.0 with built-in Kotlin, the Kotlin
2.3.21 Compose plugin, minSdk 29, target/compile SDK 36, and the eight modules
specified in `AGENTS.md`. Ship `arm64-v8a`; retain `x86_64` only for CI and
emulator-native compilation during M0. Pin dependencies and use the wrapper.

## Alternatives considered

One application module was rejected because it erases safety and lifecycle
boundaries. API 37 was rejected because Android 17 was not the current stable
phone release. Older AGP versions were rejected for a new project.

## Consequences

The project builds with the stable Android 16 SDK while keeping Android 10
compatibility. Eight small modules add configuration overhead but make later
ownership explicit. The Compose BOM is pinned to 2026.06.01 because the
2026.09.00 artifacts require compile SDK 37.

## Validation

Run `android/gradlew.bat lint test assembleDebug` from a clean checkout.

## Follow-up

M1 removes `x86_64` from distributable variants unless emulator testing still
requires it and upgrades the SDK only through a superseding ADR.
