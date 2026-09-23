# M5 — Field hardening and MVP release

- Status: In progress
- Depends on: M4 — Focused capture and export
- Produces: Audited, field-tested MVP release candidate
- Next milestone: Post-MVP roadmap decision

<!-- REQUIREMENTS: -->

## Objective

Prove the integrated application satisfies the full specification under real
field use, interruption, resource pressure, accessibility needs, privacy review,
and release conditions. Fix discovered defects without weakening requirements,
then produce a reproducible MVP release candidate and evidence-backed handoff.

M5 owns the final cross-cutting audit. Earlier milestones retain primary
requirement ownership and must fix regressions in their areas.

## Inputs

- Completed M0–M4 code, evidence, schemas, fixtures, and handoffs.
- Every accepted/superseded ADR.
- Application specification, especially sections 4, 14–20, and 24.
- Target Android phone, powered USB topology, HackRF, antennas, and safe field
  survey route.
- A sanitized release-test dataset and a private local field dataset.

## Primary requirement ownership

M5 introduces no new numbered requirements. It audits all 64 requirements and
all 13 MVP acceptance criteria. Create `docs/evidence/M5/completion-audit.md`
with one row per requirement/criterion and authoritative evidence.

## Deliverables

- Complete requirement and acceptance-criterion audit.
- Automated regression suite and release CI.
- 30-minute and extended-duration screen-off field reports.
- USB, process-death, low-storage, thermal/battery, permission, GPS, and archive
  recovery evidence.
- Accessibility report and resolved critical issues.
- Privacy/security review, receive-only static/runtime audit, and dependency
  license inventory.
- User quick start, equipment/safety guide, survey guide, export guide,
  troubleshooting, privacy statement, and known limitations.
- Reproducible signed or clearly documented unsigned release build appropriate
  to the selected private-sideload or store distribution decision.
- Post-MVP recommendation that does not enter implementation in M5.

## Tasks

### 1. Build the completion audit

- Enumerate every numbered requirement from the specification.
- Link to implementation, tests, hardware/field evidence, UI evidence, schemas,
  or ADRs as appropriate.
- Mark evidence missing or indirect as incomplete.
- Enumerate all 13 section-20 acceptance criteria separately.
- Resolve every incomplete row before declaring the milestone complete.

### 2. Run integrated field scenarios

- Fresh install and first connection.
- 30-minute 902–928 MHz screen-off survey.
- Repeat-route comparable survey.
- Changed-gain incomparable survey warning.
- Discovery, map, revisit, 1–5 second capture, redacted export, and reimport.
- Airplane/no-network use with previously downloaded map region.
- USB detach/reconnect and orderly recovery.
- GPS degradation and recovery without invented coordinates.

### 3. Exercise adverse resource conditions

- Low storage before and during capture.
- Queue pressure and USB overruns.
- Battery saver and screen off.
- Thermal warning or safe synthetic equivalent.
- Permission denial and revocation.
- Process death during survey, finalization, capture, export, and import.
- Corrupt database migration fixture and corrupt/hostile archive fixtures.

### 4. Audit safety and privacy

- Search Kotlin, Java, JNI, C/C++, build outputs, and symbols for accessible TX
  paths or calls.
- Verify RF amplifier and antenna-power defaults and persistence behavior.
- Inspect app network traffic during acquisition and offline mapping.
- Verify logs and diagnostics exclude protected fields by default.
- Verify every share operation follows explicit manifest review.
- Confirm map language and visuals never assert a transmitter location.

### 5. Complete accessibility and usability review

- Screen reader navigation and labels.
- Large text and display scaling.
- Touch target sizing and orientation changes.
- Light/dark contrast and non-color equivalents.
- Map-equivalent list workflow.
- One-handed active survey Stop/Pause behavior.
- First-run comprehension test for antenna, gain, relative power, and receive-only
  limitations.

### 6. Prepare release engineering

- Pin and inventory dependencies.
- Run vulnerability and license checks.
- Make build and test commands reproducible from a clean checkout.
- Configure release shrinker/obfuscation without breaking JNI.
- Verify native symbols/ABIs and package size.
- Produce checksums, version notes, and install/upgrade/rollback instructions.
- Decide private sideload versus store distribution through an ADR.

### 7. Write operator documentation

- Hardware connection and powered-hub guidance.
- Safe antenna and input-power practices.
- Survey profile creation and comparability.
- Map uncertainty interpretation.
- Capture size/storage guidance.
- Privacy-conscious export.
- USB, firmware/API, GPS, storage, and recovery troubleshooting.

### 8. Recommend post-MVP direction

Use evidence and actual field use to rank deferred work. Do not implement
decoders, remote RF heads, direction finding, or collaboration features inside
M5. Record recommendation, expected value, risk, and prerequisite milestone.

## Acceptance criteria

- [ ] Every numbered requirement has direct completion evidence.
- [ ] Every section-20 MVP acceptance criterion has direct completion evidence.
- [ ] No unresolved critical or high-severity reliability, security, privacy,
  accessibility, or data-integrity defect remains.
- [ ] The receive-only audit finds no application-accessible TX path.
- [ ] A clean device completes the end-to-end field workflow.
- [ ] Screen-off, detach, process-death, low-storage, degraded-GPS, and offline-map
  scenarios preserve truthful state and recover safely.
- [ ] Full and redacted exports pass privacy review and round-trip.
- [ ] Documentation covers setup, use, safety, privacy, uncertainty, and recovery.
- [ ] Release build is reproducible with recorded toolchain and checksums.
- [ ] Dependency licenses and vulnerabilities have recorded dispositions.
- [ ] Known limitations are explicit and do not disguise unmet MVP criteria.
- [ ] Earlier milestone suites and planning validation pass.
- [ ] `ROADMAP.md` and every milestone handoff match final repository evidence.

## Validation

Run all project checks from a clean checkout using the commands established by
the Android build ADR, including at minimum:

```powershell
cd android
.\gradlew.bat clean lint test assembleDebug
.\gradlew.bat connectedDebugAndroidTest
cd ..
.\scripts\Test-Planning.ps1
```

Also run release build, dependency/license/security, schema, archive-fuzz,
hardware, and field procedures documented by prior milestones. Store results in
`docs/evidence/M5/` with precise private coordinates removed.

## Out of scope

- Any new post-MVP feature.
- Protocol/payload decoding.
- Transmit capability.
- Cloud accounts or community maps.
- Direction finding or asserted source coordinates.
- iOS or generalized multi-radio support.

## Handoff

Complete this section before marking M5 complete:

- Commit, branch, and release identifier:
- Release artifact and checksum:
- Completion-audit path:
- Validation summary:
- Hardware/field evidence:
- Safety/privacy/accessibility evidence:
- ADRs and dependency inventory:
- Known limitations:
- Recommended next milestone:
