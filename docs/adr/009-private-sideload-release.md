# ADR 009: Private sideload distribution for the MVP

- Status: Accepted
- Date: 2026-09-22
- Owners: M5
- Related requirements: specification sections 17, 20, and open decision 8
- Supersedes: ADR 001's MVP distribution-ABI clause; all other ADR 001
  toolchain decisions remain in force

## Context

The MVP handles precise routes, radio identifiers, notes, and raw IQ while
depending on USB host behavior that needs hands-on setup and support. Public
store distribution would add signing custody, store disclosures, policy review,
and a support promise that the field evidence does not yet justify.

## Decision

Distribute `1.0.0-rc1` as a privately shared, release-signed APK for the two
reviewed ABIs (`arm64-v8a` and `x86_64`). This deliberately expands ADR 001's
arm64-only distribution clause because M5 now builds, symbol-audits, and
packages the same receive-only native boundary for both ABIs. Keep the signing
key outside Git and record the APK SHA-256 and signing-certificate SHA-256 with
each release. Share
the APK, third-party notices, exact source revision, and native rebuild/relink
instructions as one release set. Do not add analytics, an account, cloud sync,
or an updater.

Installation and updates require the operator to verify both recorded hashes.
Rollback is an uninstall/reinstall and therefore requires exporting wanted
local surveys first. A later store release requires a new ADR and fresh privacy,
policy, signing, update, and support review.

## Alternatives considered

Google Play distribution was rejected for this MVP because it expands policy
and lifecycle obligations without improving the receive-only field workflow.
An unsigned APK was rejected because Android cannot install or upgrade it as an
ordinary package. Reusing the debug key was rejected because it does not
provide an appropriate release identity.

## Consequences

The initial audience is deliberately small and installation requires explicit
operator action. The private release key becomes required for in-place updates;
loss of that key forces an uninstall/reinstall. No release material contains
the private key.

## Validation

M5 records package/version/ABI inspection, release shrinker results, APK and
certificate hashes, Pixel 8a installation/update behavior, and exact build
commands in [release-checksums](../evidence/M5/release-checksums.md). The final
candidate is source revision `f2f4fcf61790ad23131c976ea2f3b893b4a5d31e`;
the physical capture/export/reimport workflow used its deterministic APK bytes.

## Follow-up

Reconsider store distribution only after broader device testing, a documented
support policy, stable upgrade migrations, and a dedicated public-release
privacy and compliance review.
