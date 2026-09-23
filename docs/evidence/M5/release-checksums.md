# RF Field Notebook 1.0.0-rc1 release checksums

Release date: 2026-09-23. Distribution: private signed sideload under
[ADR 009](../../adr/009-private-sideload-release.md). Release artifacts are
generated under gitignored `.state/releases/1.0.0-rc1/`; the private key and its
password are never part of the release set or repository.

## Identity

- Version: `1.0.0-rc1`
- Package: `dev.rfnotebook`
- Source revision: `f2f4fcf61790ad23131c976ea2f3b893b4a5d31e`
- APK size: 26,032,383 bytes
- APK signature: Android APK Signature Scheme v3, one signer
- Signing-certificate SHA-256:
  `f3f6c30370a556ddcfe9495a3c1248e7fc277e1167c818814719fbbbc176924e`
- Reviewed ABIs: `arm64-v8a`, `x86_64`

The signed APK was installed as an in-place update on the physical Pixel 8a,
reported `versionName=1.0.0-rc1`, and was confirmed non-debuggable. The final
physical capture/export/reimport and strength-map checks used an APK with the
same deterministic binary hash shown below.

## SHA-256 inventory

```text
a57dea4919156d2eca018957dfb384c55f5ae933b63a338889f9563d24d1e347  rf-field-notebook-1.0.0-rc1.apk
ecf32c07ffb81b37dd696ea3088684c97647f3c4f1f52fa42a51a1557155dcc8  rf-field-notebook-1.0.0-rc1-source.zip
32eec4c9f7a2623cc9e617f45f04f84a55b655e9d9e3d850c391f5f93a06fd12  SOURCE_REVISION
3415f390df8d841f275c14f4b3b5ca5a4fca893d36ba1d845538e4caa54c2f30  libusb-1.0.29-source.zip
33b90bcc62c5798f576af64139c23553eb74fd65aee60a467c63152b1982b976  hackrf-2026.01.3-source.zip
db3cc41e2c79f394a1dddd890c55c263426175029a898d5167820498ddebf152  maplibre-android-13.6.1-LICENSE.md
5adf72fc7326c9386aaa28e2f2bee9718b840ddf6347eacb7b3cd3496a1298c2  THIRD_PARTY_NOTICES.md
50273da6469d8a0d5fc61b9ac0fb571c084ef74ef05025b90c3253eda9341e3a  NATIVE_THIRD_PARTY_NOTICES.md
```

## Reproduction

The reviewed toolchain is JDK 17, Gradle 9.4.0 from the checksum-pinned wrapper,
Android compile SDK 36/build tools from the configured SDK, CMake 3.22.1, NDK
28.2.13676358, Kotlin 2.3.21, and the dependency/native pins in the source tree.
From the repository root:

```powershell
.\scripts\Build-M5Release.ps1 `
  -OutputDirectory '.state/releases/1.0.0-rc1' `
  -Keystore '<private-p12-path>' `
  -StorePasswordFile '<private-password-file>' `
  -SourceRevision f2f4fcf61790ad23131c976ea2f3b893b4a5d31e
```

The command performs a clean `lint test assembleDebug assembleRelease`, the
receive-only and JNI export audits, schema/example validation, the OSV scan,
release inspection, planning validation, signing, signature verification,
source/notices collection, and checksum generation. The final evidence gate is
also independently run with `scripts/Test-M5Evidence.ps1`.

## Installation, update, and rollback

1. Verify the APK SHA-256 and signing-certificate SHA-256 above on the receiving
   computer before copying it to the phone.
2. Install with Android's package installer or `adb install
   rf-field-notebook-1.0.0-rc1.apk`.
3. An in-place update must be signed by the same private key; verify hashes
   again, then use the package installer or `adb install -r`.
4. Rollback requires uninstall/reinstall. Export any wanted local surveys first,
   because uninstalling deliberately removes app-private data.

No updater, telemetry, cloud account, or release-server dependency is included.
