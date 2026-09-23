# Android RF Field Notebook — Application Specification

- Status: Draft for implementation
- Working title: RF Field Notebook
- Primary platform: Android 10+ (API 29+)
- Primary radio: HackRF One
- Operating mode: Receive-only

## 1. Product summary

RF Field Notebook is a local-first Android application that connects directly
to a HackRF One over USB. It lets a user walk or drive a route, survey selected
radio-frequency bands, attach GPS and time metadata to observations, group
recurring emissions into signal fingerprints, and revisit selected signals for
short IQ captures and offline analysis.

The product is not a general-purpose radio receiver. Its core purpose is to
answer four questions:

1. What signals were observable?
2. Where and when were they observable?
3. Which observations probably came from the same emitter or protocol?
4. Can the user revisit and capture an interesting signal reproducibly?

The first release emphasizes reliable measurement and evidence preservation.
Automatic protocol decoding is deliberately deferred.

## 2. Goals

### 2.1 Primary goals

- Connect a HackRF One directly to an Android device through USB host mode.
- Run repeatable, receive-only spectrum surveys over user-selected bands.
- Geotag measurements with position, accuracy, time, and movement metadata.
- Detect carriers and bursts relative to a locally estimated noise floor.
- Cluster related detections into persistent signal fingerprints.
- Visualize relative signal strength geographically without implying false
  source-location precision.
- Capture short, focused IQ recordings for later inspection.
- Store all survey data locally and export it in documented formats.
- Remain usable with the screen off through a visible foreground service.

### 2.2 Secondary goals

- Help users distinguish persistent, periodic, bursty, and mobile signals.
- Record enough radio configuration metadata to reproduce every observation.
- Provide basic modulation hints such as OOK, FSK-family, or analog FM without
  presenting them as definitive protocol identification.
- Support future remote-radio transports, such as a Raspberry Pi RF head,
  without redesigning the domain or storage model.

## 3. Non-goals for the MVP

- Transmission of any RF signal.
- Trunked-radio following or voice-scanner functionality.
- Decryption, descrambling, authentication bypass, or exploitation.
- Automatic payload decoding for arbitrary proprietary protocols.
- Precise transmitter triangulation or direction finding.
- Calibrated field-strength measurements in dBm or dBµV/m.
- Simultaneous monitoring of the HackRF's entire frequency range.
- Cloud accounts, social features, or automatic uploads.
- Background surveillance that continues without an explicit active survey.
- iOS support.

## 4. Safety, privacy, and ethical boundaries

### 4.1 Receive-only enforcement

- The application-facing native API shall expose no transmit operation.
- Production UI shall contain no TX control or hidden TX mode.
- Antenna-port power shall default to off for every connection and survey.
- Enabling antenna-port power, if added after the MVP, shall require an explicit
  hardware profile and a warning describing its voltage/current constraints.
- The RF amplifier shall default to off. Gain shall be set explicitly by the
  survey profile rather than inherited from a prior unrelated session.
- The application shall display a persistent `RX only` indicator while the
  device is open.

### 4.2 Location privacy

- Precise survey coordinates shall remain in app-private storage by default.
- No analytics, telemetry, or crash report may include coordinates, HackRF
  serial numbers, discovered frequencies, or IQ samples.
- Export shall require an explicit user action and show exactly which files and
  fields will be shared.
- Export shall offer full, rounded, and omitted coordinate modes.
- Home-location redaction shall be supported after the MVP by removing or
  coarsening observations inside a user-defined radius.

### 4.3 Signal-content boundaries

- The MVP shall analyze energy, timing, bandwidth, and modulation shape—not
  communication payload contents.
- Encrypted or scrambled signals may be labeled as such when evident, but the
  app shall not attempt to defeat their protection.
- The UI shall avoid labels that assert ownership, intent, or a precise source
  location without independently supplied evidence.
- Users shall be reminded that interception and disclosure rules vary by
  jurisdiction. In the United States, federal law distinguishes communications
  that are readily accessible to the public from scrambled, encrypted, carrier,
  and other protected communications. See
  [18 U.S.C. §§ 2510–2511](https://www.law.cornell.edu/uscode/text/18/2511).

## 5. Target user and core scenarios

### 5.1 Primary user

A technically curious radio hobbyist who owns a HackRF One and Android phone,
wants to explore non-broadcast RF activity, and is comfortable learning basic
frequency, bandwidth, antenna, and gain concepts.

### 5.2 Core scenarios

#### Neighborhood survey

The user selects a saved 902–928 MHz profile, connects the HackRF through a
powered USB-C OTG hub, starts a survey, walks a route, and later sees recurring
signals grouped and mapped.

#### Revisit an interesting signal

The user opens a signal fingerprint, reviews its strongest prior observations,
navigates back to that area, and watches a focused relative-strength view while
keeping the same antenna and gain configuration.

#### Capture for desktop analysis

The user tunes to one signal, records a short IQ sample, adds a note, and exports
the IQ file plus metadata for GNU Radio, Inspectrum, or Universal Radio Hacker.

#### Compare surveys

The user repeats the same route and profile on a later day, then compares signal
presence, duty cycle, and relative strength using only comparable observations.

## 6. Product principles

1. **Evidence before interpretation.** Preserve raw measurements and settings.
2. **Relative, not absolute.** HackRF power readings are treated as relative
   unless a future calibration workflow establishes otherwise.
3. **Fixed settings create comparable maps.** Automatic gain changes are not
   permitted during a survey segment used for comparison.
4. **Uncertainty stays visible.** GPS accuracy and RF ambiguity must be shown.
5. **Discovery and inspection are different jobs.** Sweeps find candidates;
   focused IQ captures support deeper analysis.
6. **Local first.** The product remains useful without an account or Internet.
7. **Safe by construction.** Transmit functionality is outside the app boundary.

## 7. Information architecture

The primary navigation contains five destinations:

1. **Survey** — create, start, monitor, pause, and stop a field survey.
2. **Map** — view geographic observations for selected fingerprints.
3. **Discoveries** — rank, filter, tag, and compare signal fingerprints.
4. **Captures** — manage focused IQ recordings and exports.
5. **Equipment** — manage HackRF, antenna, gain, and band profiles.

Settings are accessed from the application menu and are not a primary
destination.

## 8. Key workflows

### 8.1 First-run setup

1. Explain receive-only scope and local storage.
2. Request no permissions until the relevant action is initiated.
3. Prompt the user to connect a HackRF through USB.
4. Request Android USB permission for the selected device.
5. Read board ID, serial suffix, firmware, and supported platform.
6. Validate that firmware and native host library APIs are compatible.
7. Create an equipment profile with antenna name and optional notes.
8. Offer a receive-stream test that discards samples after measuring throughput.
9. Request precise foreground location only when the user starts a survey.

### 8.2 Start survey

1. Select or create a band profile.
2. Select an antenna/equipment profile.
3. Review fixed sample rate, gains, sweep resolution, and estimated load.
4. Confirm GPS has an acceptable fix or explicitly continue with degraded
   accuracy.
5. Start the foreground service from the visible activity.
6. Display persistent survey notification with elapsed time, band, and Stop.

### 8.3 Active survey

The active view displays:

- Connection and RX-only state.
- Current frequency subrange.
- GPS accuracy and fix age.
- Survey duration and distance.
- Detections during the last minute.
- USB overrun count.
- Storage consumption and estimate.
- Battery and thermal warnings.
- Pause and Stop actions.

The app may dim its visualization, but it must continue acquisition when the
screen turns off while the foreground service remains active.

### 8.4 End survey

1. Stop acquisition and return the HackRF to idle.
2. Flush pending observations transactionally.
3. Summarize route coverage, duration, GPS quality, detected fingerprints, and
   any gaps caused by USB, location, or thermal failures.
4. Run clustering and aggregation work that was deferred during acquisition.
5. Open the Discoveries view sorted by novelty.

### 8.5 Focused capture

1. Select a fingerprint or enter a frequency manually.
2. Reapply the originating equipment and gain profile when possible.
3. Display a narrow waterfall and live relative power.
4. Configure duration, sample rate, and trigger mode.
5. Estimate file size before capture.
6. Record signed 8-bit interleaved IQ into app-private storage.
7. Save metadata atomically next to the capture.

## 9. Functional requirements

### 9.1 USB and device management

- **FR-USB-001:** Discover attached devices matching supported HackRF USB IDs.
- **FR-USB-002:** Request and persist Android USB permission for the current
  attachment where the operating system permits.
- **FR-USB-003:** Open exactly one device by serial number.
- **FR-USB-004:** Display board, firmware, API, hardware revision, and serial
  suffix without storing the full serial in exports by default.
- **FR-USB-005:** Detect detach, permission loss, stalled transfers, and
  reattachment.
- **FR-USB-006:** Return the radio to idle and close native resources on every
  normal or exceptional stop path.
- **FR-USB-007:** Report measured stream throughput before allowing profiles
  whose sample rate exceeds the demonstrated connection capacity.

The native integration shall use upstream libhackrf behavior and Android USB
host facilities. libhackrf uses asynchronous libusb transfers and requires
firmware/host API compatibility; see the upstream
[libhackrf interface](https://github.com/greatscottgadgets/hackrf/blob/main/host/libhackrf/src/hackrf.h).

### 9.2 Equipment profiles

- **FR-EQP-001:** Store antenna name, connector/adapters, nominal band, notes,
  and optional photo reference.
- **FR-EQP-002:** Store LNA, VGA, RF amplifier, sample rate, baseband filter,
  and antenna-port-power state.
- **FR-EQP-003:** Mark observations incomparable when relevant profile fields
  differ.
- **FR-EQP-004:** Ship with conservative receive-only defaults and no assertion
  that a generic antenna is calibrated.

### 9.3 Band and sweep profiles

- **FR-BAND-001:** Define one or more non-overlapping frequency ranges.
- **FR-BAND-002:** Define bin width, dwell/revisit target, detector threshold,
  and excluded ranges.
- **FR-BAND-003:** Estimate scan-cycle duration before starting.
- **FR-BAND-004:** Version profiles so a changed profile does not silently alter
  the interpretation of an existing survey.
- **FR-BAND-005:** Include editable starter profiles for 315 MHz short-range,
  the 433 MHz region, 150–174 MHz, 450–470 MHz, and 902–928 MHz. Profiles are
  exploration aids, not statements that every frequency is available for use.

### 9.4 Location and route

- **FR-LOC-001:** Record latitude, longitude, horizontal accuracy, provider,
  speed, bearing when available, and monotonic plus wall-clock timestamps.
- **FR-LOC-002:** Reject stale fixes according to a configurable age limit.
- **FR-LOC-003:** Interpolate locations only across short gaps and mark all
  interpolated observations.
- **FR-LOC-004:** Preserve observations with missing location rather than
  inventing coordinates.
- **FR-LOC-005:** Start location-enabled foreground service work only from a
  visible user action.

Android treats location used by a visible activity or foreground service as
foreground access and requires the appropriate service declaration on modern
versions. The implementation shall follow the current
[Android location-permission guidance](https://developer.android.com/develop/sensors-and-location/location/permissions)
and [foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

### 9.5 Spectrum acquisition

- **FR-ACQ-001:** Support sweep acquisition for discovery and continuous RX for
  focused inspection.
- **FR-ACQ-002:** Keep gain and antenna-power settings fixed during a survey
  segment.
- **FR-ACQ-003:** Timestamp each received sweep frame using a monotonic clock.
- **FR-ACQ-004:** Count missing frames, malformed frames, overruns, and native
  errors.
- **FR-ACQ-005:** Apply backpressure by reducing UI refresh and queued analysis
  work before dropping raw acquisition metadata.
- **FR-ACQ-006:** Never save continuous wideband IQ during survey mode.

### 9.6 Detection

- **FR-DET-001:** Estimate a rolling noise floor per frequency bin using robust
  statistics that resist persistent carriers.
- **FR-DET-002:** Create detections when adjacent bins exceed configurable SNR
  and minimum-bandwidth criteria.
- **FR-DET-003:** Merge detections across immediately adjacent sweep frames.
- **FR-DET-004:** Store center frequency, occupied bandwidth, peak power,
  median power, estimated SNR, duration, and detector version.
- **FR-DET-005:** Track persistent carriers separately from discrete bursts.
- **FR-DET-006:** Retain enough aggregated baseline data to re-run improved
  detectors without storing all raw FFT frames indefinitely.

Detector thresholds shall initially be conservative. The UI must make false
positives easy to dismiss without deleting the underlying survey.

### 9.7 Fingerprinting and classification

- **FR-FP-001:** Cluster detections using frequency proximity, bandwidth,
  temporal behavior, and compatible survey settings.
- **FR-FP-002:** Maintain first seen, last seen, occurrence count, duty cycle,
  typical duration, typical repetition interval, and location extent.
- **FR-FP-003:** Assign stable application-generated fingerprint IDs.
- **FR-FP-004:** Permit split and merge corrections while retaining provenance.
- **FR-FP-005:** Calculate novelty relative to prior comparable surveys.
- **FR-FP-006:** Present classification as ranked hints with confidence and
  evidence, never as an unsupported fact.

Initial hints may include:

- Continuous narrowband carrier.
- Repeating short OOK-like burst.
- Two-level FSK-like signal.
- Wider FSK-family signal.
- Analog FM-like signal.
- Frequency-hopping or unresolved intermittent activity.
- Likely local interference or overload artifact.

### 9.8 Mapping

- **FR-MAP-001:** Show route, observation coverage, and GPS accuracy.
- **FR-MAP-002:** Render one selected fingerprint's relative-strength layer by
  default, with an explicit comparison mode for multiple layers.
- **FR-MAP-003:** Aggregate samples into adaptive geographic cells no smaller
  than the useful precision implied by their GPS accuracy.
- **FR-MAP-004:** Use robust aggregate strength, sample count, and uncertainty;
  do not color a cell solely from its single strongest sample.
- **FR-MAP-005:** Filter by survey, time, frequency, bandwidth, detection type,
  equipment profile, and confidence.
- **FR-MAP-006:** Label the visualization `Observed relative strength`, not
  `Transmitter location`.
- **FR-MAP-007:** Support offline map regions for planned survey areas.

MapLibre provides Android map APIs and offline regions; see
[MapLibre Native Android](https://maplibre.org/maplibre-native/android/api/)
and its [offline-region API](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.offline/index.html).
The tile provider and attribution requirements must be selected before release.

ADR 007 selects the OpenFreeMap Liberty style for the initial production
basemap. The app keeps RF and location overlays local, discloses that an online
provider can observe requested basemap tile areas, preserves required
OpenFreeMap/OpenMapTiles/OpenStreetMap attribution, and uses rebuildable
accuracy-bounded aggregation version `m3-grid-v1`.

### 9.9 Focused IQ capture

- **FR-CAP-001:** Capture signed 8-bit interleaved I/Q at an explicit center
  frequency and sample rate.
- **FR-CAP-002:** Support manual duration from 0.25 to 30 seconds, subject to
  storage and throughput checks.
- **FR-CAP-003:** Show estimated bytes before capture. At 8 MS/s, raw 8-bit I/Q
  consumes approximately 16 MB/s.
- **FR-CAP-004:** Support manual start in the MVP; threshold and burst-triggered
  capture may follow.
- **FR-CAP-005:** Store a sidecar JSON document containing all radio, time,
  location, equipment, and app-version metadata.
- **FR-CAP-006:** Generate a reduced-resolution preview waterfall after capture.
- **FR-CAP-007:** Never parse or display application payload contents in the MVP.

### 9.10 Export and interoperability

- **FR-EXP-001:** Export a complete survey bundle as a versioned ZIP archive.
- **FR-EXP-002:** Include JSON metadata, CSV observations, GeoJSON geographic
  aggregates, notes, and selected IQ files.
- **FR-EXP-003:** Document units, coordinate reference system, timestamps, sample
  encoding, endianness, and schema version.
- **FR-EXP-004:** Let users exclude IQ, routes, exact coordinates, or device
  identifiers before invoking Android's share sheet.
- **FR-EXP-005:** Import bundles produced by the same schema version and reject
  unsupported versions without partial mutation.

## 10. Data model

All database identifiers are application-generated UUIDs. Times are stored as
UTC instants plus monotonic offsets where ordering across clock corrections
matters. Frequencies and sample rates are integer hertz.

### 10.1 Entities

#### RadioDevice

- `id`
- `model`
- `serialSuffix`
- `hardwareRevision`
- `firmwareVersion`
- `usbApiVersion`
- `firstSeenAt`
- `lastSeenAt`

#### EquipmentProfile

- `id`, `name`, `version`
- `radioDeviceId`
- `antennaName`, `antennaBands`, `adapterNotes`
- `sampleRateHz`, `basebandFilterHz`
- `lnaGainDb`, `vgaGainDb`, `rfAmpEnabled`
- `antennaPowerEnabled`
- `createdAt`, `retiredAt`

#### BandProfile

- `id`, `name`, `version`
- `ranges[] { startHz, endHz }`
- `excludedRanges[]`
- `binWidthHz`
- `targetRevisitMs`
- `thresholdSnrDb`
- `minimumBandwidthHz`
- `equipmentProfileId`

#### Survey

- `id`, `name`, `startedAt`, `endedAt`
- `bandProfileVersionId`, `equipmentProfileVersionId`
- `status { preparing, active, paused, finalizing, complete, failed }`
- `distanceMeters`, `locationCoverageRatio`
- `droppedFrameCount`, `overrunCount`
- `appVersion`, `detectorVersion`
- `notes`

#### LocationFix

- `id`, `surveyId`
- `timestamp`, `monotonicNs`
- `latitude`, `longitude`, `horizontalAccuracyM`
- `altitudeM?`, `speedMps?`, `bearingDegrees?`
- `provider`, `isInterpolated`

#### SpectrumAggregate

- `surveyId`, `timeBucket`, `frequencyBinHz`
- `minimumPowerDbfs`, `medianPowerDbfs`, `maximumPowerDbfs`
- `noiseEstimateDbfs`, `sampleCount`
- `locationCellId?`

#### Detection

- `id`, `surveyId`, `fingerprintId?`
- `startedAt`, `endedAt`
- `centerFrequencyHz`, `bandwidthHz`
- `peakPowerDbfs`, `medianPowerDbfs`, `snrDb`
- `locationFixId?`
- `detectorVersion`, `qualityFlags`

#### SignalFingerprint

- `id`
- `nominalFrequencyHz`, `typicalBandwidthHz`
- `firstSeenAt`, `lastSeenAt`, `occurrenceCount`
- `dutyCycleEstimate`
- `typicalBurstDurationMs?`, `typicalRepeatIntervalMs?`
- `classificationHints[]`
- `userLabel`, `tags[]`, `notes`
- `state { new, interesting, identified, ignored, artifact }`

#### IQCapture

- `id`, `fingerprintId?`, `surveyId?`
- `fileUri`, `sidecarUri`, `previewUri`
- `startedAt`, `durationMs`
- `centerFrequencyHz`, `sampleRateHz`
- `sampleFormat`
- `equipmentProfileVersionId`
- `locationFixId?`
- `byteCount`, `sha256`
- `notes`

## 11. Detection and mapping methodology

### 11.1 Noise estimation

For each frequency bin, maintain a rolling quantile or median-of-windows
baseline. Persistent occupied bins must not continuously pull the baseline up.
Noise estimates are scoped to compatible equipment profiles.

### 11.2 Detection formation

1. Calculate bin SNR relative to the local baseline.
2. Join adjacent above-threshold bins.
3. Reject groups narrower than configured minimums or known device artifacts.
4. Merge groups across frames using frequency overlap and a short time gap.
5. Emit a detection after it closes or transitions into persistent-carrier
   state.

### 11.3 Artifact handling

The detector shall flag, rather than silently discard:

- Center/DC artifacts.
- Mirror-like symmetric candidates.
- Broadband impulses affecting most of the current span.
- Overload conditions where the noise floor rises across a wide range.
- USB-corrupted or incomplete frames.

### 11.4 Geographic aggregation

- Associate observations with the closest valid fix within a bounded time.
- Aggregate into adaptive map cells influenced by zoom and GPS accuracy.
- Store count, median, upper quantile, and spread per cell.
- Require multiple samples before presenting a cell as high confidence.
- Never infer a transmitter coordinate from a single route.

## 12. Android architecture

### 12.1 Recommended stack

- Kotlin.
- Jetpack Compose for UI.
- Coroutines and Flow for acquisition state and streaming summaries.
- Room/SQLite for structured local data.
- Android NDK/JNI wrapper around libhackrf and its USB dependency.
- MapLibre for map rendering and optional offline regions.
- WorkManager for deferred post-survey aggregation, never for live USB capture.
- App-private files for IQ and previews.
- Android Storage Access Framework for explicit import/export.

Target the latest stable Android SDK available when implementation begins;
retain API 29 as the initial minimum unless native-device testing identifies a
reason to raise it.

### 12.2 Modules

```text
app-ui
  Compose screens, navigation, permissions, notifications

domain
  Survey state machine, profiles, detections, fingerprints, use cases

radio-api
  Receive-only Kotlin interface and data contracts

radio-hackrf-native
  NDK build, JNI boundary, USB/libhackrf lifecycle, native tests

acquisition-service
  Foreground service, buffers, throughput, health monitoring

signal-processing
  Sweep parsing, noise estimation, detection, clustering, previews

storage
  Room database, IQ files, migrations, import/export

maps
  Geographic aggregation, MapLibre sources/layers, offline regions
```

### 12.3 Native boundary

The Kotlin-facing interface exposes only:

```kotlin
interface ReceiveOnlyRadio {
    suspend fun enumerate(): List<RadioDescriptor>
    suspend fun open(serialSuffix: String): RadioSession
}

interface RadioSession : AutoCloseable {
    suspend fun deviceInfo(): RadioDeviceInfo
    suspend fun startSweep(config: SweepConfig, sink: SweepSink)
    suspend fun startRx(config: RxConfig, sink: SampleSink)
    suspend fun stop()
    override fun close()
}
```

No transmit function crosses the JNI boundary. Native buffers shall be bounded,
owned explicitly, and released on cancellation, detach, or process teardown.

### 12.4 Foreground service

The acquisition service requires both connected-device and location behavior
while an active survey is visible to the user. It must be started from a visible
activity and publish its notification immediately. Android 14+ applies explicit
requirements to connected-device foreground services; see
[ServiceInfo foreground-service types](https://developer.android.com/reference/android/content/pm/ServiceInfo).

The service owns:

- Radio session lifecycle.
- Location subscription.
- Wake lock only while acquisition requires it.
- Bounded acquisition and persistence queues.
- Notification actions.
- Health counters and finalization after cancellation.

## 13. State machines

### 13.1 Connection

```text
Disconnected
  -> PermissionRequired
  -> Opening
  -> Ready
  -> Surveying | Capturing
  -> Ready
  -> Closing
  -> Disconnected

Any state -> ErrorRecoverable -> Opening
Any state -> ErrorTerminal -> Disconnected
```

### 13.2 Survey

```text
Draft -> Validating -> Active <-> Paused -> Finalizing -> Complete
                     |                     |
                     +-------> Failed <----+
```

State transitions and failure reasons are persisted so process death cannot
leave an apparently active survey or an unindexed capture.

## 14. Performance and resource requirements

- **NFR-PERF-001:** Acquisition threads shall never block on map rendering or
  database queries.
- **NFR-PERF-002:** UI summaries shall refresh no faster than needed for human
  perception, initially 5–10 Hz.
- **NFR-PERF-003:** The app shall remain responsive during an 8 MS/s focused
  receive stream on supported hardware.
- **NFR-PERF-004:** Survey mode shall bound in-memory queues and report every
  dropped acquisition unit.
- **NFR-PERF-005:** The app shall estimate capture storage before allocation and
  preserve a configurable reserve.
- **NFR-PERF-006:** Thermal or battery pressure shall produce a visible warning
  and an orderly stop option, not silent measurement changes.

## 15. Reliability requirements

- Every capture is written to a temporary filename and atomically finalized.
- Database migrations are versioned and covered by migration tests.
- Survey finalization is idempotent.
- USB detach produces a recoverable, timestamped survey gap.
- Process restart marks interrupted surveys and offers recovery/finalization.
- Changes to detector or fingerprint algorithms retain their version numbers.
- User edits never destroy source observations; ignore and artifact states are
  reversible labels.

## 16. Accessibility and usability

- Core survey actions use labeled controls with large touch targets.
- Color never carries signal strength or status without labels/legend.
- Maps have an equivalent list representation.
- Units are always visible and user-selectable where ambiguity is likely.
- Frequency entry accepts Hz, kHz, MHz, and GHz suffixes.
- Destructive data actions require confirmation and identify affected surveys
  or captures.
- The active survey screen avoids dense SDR terminology; advanced settings live
  in profiles.

## 17. Security requirements

- No network permission is required for acquisition or analysis.
- Map network access is isolated from survey storage and may be disabled.
- Exported ZIP paths and imported metadata are validated against traversal,
  oversized-entry, and malformed-schema attacks.
- Native lengths, frequency ranges, and buffer sizes are validated on both sides
  of JNI.
- The application never executes imported scripts, flowgraphs, or decoders.
- Dependency licenses and native redistribution obligations must be reviewed
  before public distribution.

## 18. Observability

Local diagnostic logs may contain:

- State transitions.
- Native error codes.
- USB throughput and overruns.
- GPS accuracy buckets, without coordinates.
- Queue depths and processing latency.
- Database and file-finalization outcomes.

Diagnostic export excludes coordinates, frequencies, serials, notes, and IQ by
default. There is no automatic remote telemetry in the MVP.

## 19. Testing strategy

### 19.1 Unit tests

- Profile validation and versioning.
- Frequency/unit parsing.
- Rolling noise estimator.
- Detection merging and persistent-carrier transition.
- Fingerprint clustering, split, and merge provenance.
- Map aggregation with accuracy and missing-location cases.
- Capture-size calculation.
- Export redaction and schema validation.
- Survey and connection state transitions.

### 19.2 Recorded-data tests

Maintain small, redistributable synthetic IQ and sweep fixtures for:

- Empty/noise-only span.
- Continuous carrier.
- Repeating OOK bursts.
- Two-level FSK-like bursts.
- Center artifact.
- Broadband overload.
- Missing and reordered frames.

Expected detections are stored as golden JSON and tolerate documented numeric
ranges rather than exact floating-point equality.

### 19.3 Hardware-in-the-loop tests

- Supported HackRF firmware/API detection.
- Attach, permission, open, stream, stop, close, and detach.
- 2, 4, and 8 MS/s sustained RX.
- Repeated start/stop without reconnecting USB.
- Screen-off 30-minute survey.
- Powered and unpowered hub behavior.
- Device reset and re-enumeration.
- Verification that no TX API is invoked.

No over-the-air test transmission is required. Controlled tests use ambient
signals or a shielded/attenuated laboratory source within the HackRF input
limit.

### 19.4 Field tests

- Repeat the same short walking route twice with unchanged equipment settings.
- Confirm the map distinguishes high- and low-observation areas for a persistent
  known signal.
- Confirm GPS gaps remain visible.
- Confirm a short recurring signal can be selected and revisited.
- Confirm exports open on the Windows analysis toolchain in this repository.

## 20. MVP acceptance criteria

The MVP is complete when all of the following are demonstrated on at least one
supported Android phone and the project's HackRF One:

1. The app identifies the HackRF and reports compatible firmware/API versions.
2. A user can create a fixed-gain 902–928 MHz profile and start a survey.
3. The survey runs for 30 minutes with the screen off through a visible
   foreground-service notification.
4. Every acquisition gap and USB overrun is counted and surfaced.
5. Observations are associated with valid GPS fixes and retain accuracy values.
6. Repeating detections are grouped into stable fingerprints.
7. A selected fingerprint renders as an uncertainty-aware relative-strength
   map plus a non-map list.
8. A user can revisit a fingerprint and record a 1–5 second IQ capture.
9. The capture includes a valid sidecar and opens correctly in the Windows
   toolchain.
10. A survey bundle exports and reimports without losing required metadata.
11. Exact coordinates and IQ can be excluded before sharing.
12. Static and runtime checks find no application-accessible transmit path.
13. USB detach, process interruption, and low-storage scenarios end safely
    without corrupting completed surveys.

## 21. Delivery milestones

### Milestone 0 — Technical spikes

- Build current libhackrf for Android ABIs.
- Open the HackRF with Android USB permission.
- Sustain 2, 4, and 8 MS/s receive streams.
- Parse sweep frames into frequency/power bins.
- Validate foreground location plus connected-device service behavior.
- Render stored observations in a MapLibre prototype.

Exit criterion: all major platform risks have measured results on the target
phone before full application construction begins.

### Milestone 1 — Radio and survey foundation

- Project modules and CI.
- Receive-only native API.
- Equipment and band profiles.
- Survey foreground service and state machine.
- Location fixes and raw spectrum aggregates.
- Health and interruption reporting.

### Milestone 2 — Discovery

- Noise estimator and detector.
- Detection persistence.
- Initial fingerprint clustering.
- Discoveries list and signal detail timeline.
- Artifact labeling.

### Milestone 3 — Mapping

- Route and accuracy rendering.
- Adaptive geographic aggregation.
- Per-fingerprint heat layer.
- Filters and comparable-survey warnings.
- Offline-region management.

### Milestone 4 — Capture and export

- Focused waterfall.
- Manual IQ capture and preview.
- Versioned survey bundle.
- Coordinate and content redaction.
- Desktop-toolchain interoperability test.

### Milestone 5 — Field hardening

- Long-running and screen-off tests.
- USB detach/recovery.
- Battery, thermal, and storage behavior.
- Accessibility review.
- Documentation and first-run education.

## 22. Deferred roadmap

- Threshold-triggered and pre-trigger IQ capture.
- More sophisticated periodicity and burst-shape fingerprints.
- Public-protocol decoder plugins with explicit provenance and safety review.
- FCC license-data correlation for licensed emitters.
- Directional-antenna and bearing annotations.
- Multi-route source-probability modeling with conspicuous uncertainty.
- Raspberry Pi or networked RF-head transport.
- Collaborative sharing of deliberately redacted fingerprints.
- Additional receive-only SDR hardware.

## 23. Resolved MVP implementation decisions

1. Pixel 8a on Android 17 is the evidenced MVP phone. Field runs retained at
   least 24 GiB available storage; broader OEM coverage is post-MVP.
2. The evidenced topology directly bus-powers the HackRF from the Pixel through
   the recorded USB-C/USB-A adapter and USB-A/Micro-USB data cable. A powered
   OTG hub is recommended when direct power is unstable; it is not implied to
   have been used in the recorded direct runs.
3. ADR 009 ships `arm64-v8a` and `x86_64`; both ABIs use the same symbol-audited
   receive-only boundary.
4. ADR 002 pins the native source/build strategy and redistribution materials.
5. ADR 007 selects OpenFreeMap Liberty with
   explicit offline regions, attribution, progress/byte reporting, and no
   automatic survey-data access. M5 re-checked current terms before release.
6. The starter field profile uses 100 kHz bins and a 1,000 ms target revisit;
   measured sample-rate/storage estimates remain visible before start.
7. ADR 007 requires that no hard fix is invented or discarded solely by a map
   threshold; cells expand to at least twice the largest supporting reported
   accuracy, while stale and missing fixes stay explicitly unplaced.
8. ADR 009 selects a release-signed private sideload rather than Google Play.
9. ADR 008 selects strict bundle and capture-sidecar schema
   `1.0.0`, with hash/length inventory, staged same-version import, independent
   redaction controls, and explicit future-version migration.
10. The MVP name is **RF Field Notebook**. Visual identity remains deliberately
    utilitarian and does not change the technical contract.

## 24. Definition of ready for implementation

Implementation may begin when:

- The target Android phone and powered USB topology are known.
- Milestone 0 has a written benchmark plan.
- Native dependency licenses have been reviewed.
- The initial MapLibre tile source is selected.
- The MVP acceptance criteria are accepted without adding decoding or
  transmission scope.
- A backlog maps every MVP functional requirement to an implementation issue.
