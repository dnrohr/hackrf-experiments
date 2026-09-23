# RF Field Notebook user manual

This manual describes the supported RF Field Notebook 1.0 release-candidate
workflow, from installing the Android application through collecting,
interpreting, exporting, and reimporting field observations. It is written for
operators using a HackRF One with an Android phone or tablet.

RF Field Notebook is a receive-only, local-first tool. It surveys selected
frequency ranges, stores GPS-tagged spectrum aggregates, groups recurring
observations, maps relative observed strength, and can create short focused IQ
captures for later analysis. It does not transmit, locate a transmitter,
calibrate absolute field strength, decode payloads, or grant authority to
monitor any signal.

## 1. Read this before operating

### RF and equipment safety

- Never connect a transmitter directly to the HackRF input. Use ambient signals
  or a properly shielded and attenuated laboratory fixture.
- Keep the HackRF input below its documented maximum of -5 dBm. When signal
  level is uncertain, add suitable attenuation before connecting the source.
- Fit the intended passive antenna before connecting the HackRF to the phone.
- Antenna-port power and the RF amplifier start off. The MVP does not provide a
  control to enable antenna-port power. Gain changes are deliberate and become
  part of the equipment record.
- Stop acquisition and let the app close the device before changing USB
  topology, updating firmware, or unplugging equipment intentionally.
- Keep the phone and HackRF ventilated. Stop if either becomes unusually hot,
  or if Android or the app reports a thermal or battery warning.

See [Equipment and RF safety](equipment-safety.md) for the field checklist.

### Legal and ethical use

Operate only where reception, recording, possession, and disclosure are lawful.
Rules vary by jurisdiction, signal type, and intended use. The application does
not decrypt, descramble, bypass authentication, or turn technical capability
into legal permission.

### Privacy model

The application has no account, cloud synchronization, or automatic telemetry.
Coordinates, routes, frequencies, device details, notes, detections, and IQ
remain in app-private storage unless you explicitly export them. A selected map
provider can observe the ordinary basemap tile areas requested while online; it
does not receive the private survey overlay.

Exports are deliberate disclosure events. Treat a full route, precise
coordinates, free-text notes, HackRF serial suffix, and raw IQ as potentially
sensitive. Rounded coordinates can still identify a neighborhood or work site.

## 2. What you need

- An Android 10 or newer phone or tablet with USB host support. The release
  candidate was physically exercised with a Pixel 8a; other devices may differ
  in USB power, thermal behavior, and background-service policy.
- A HackRF One with a suitable receive antenna and USB data cable.
- A powered USB-C hub when the phone cannot supply stable power, or when the
  chosen topology is more reliable through a hub.
- Enough free storage for the survey database, offline map tiles, exports, and
  any focused capture. Focused capture requires an additional 256 MiB safety
  reserve.
- A location view of the sky when route mapping matters. RF observations can be
  retained without a location fix, but unlocated or stale observations are not
  placed on the map.

The reviewed release contains `arm64-v8a` and `x86_64` native libraries. Most
physical Android phones use `arm64-v8a`.

## 3. Install, verify, update, or remove the app

### Verify and install a private release

1. Obtain the APK and the release checksum record from a trusted channel.
2. Compare the APK SHA-256 and signing-certificate SHA-256 with
   [Release checksums](../evidence/M5/release-checksums.md). Do not install when
   either value differs.
3. On Android, allow installation from the specific installer or file manager
   only if prompted. Open the APK and complete installation.
4. Revoke the install-from-unknown-sources permission afterward if it is not
   otherwise needed.
5. Open RF Field Notebook. Android will ask for USB and location permissions at
   the point those capabilities are used; the app does not need a cloud login.

### Update without losing data

Install a newer APK signed with the same signing key over the existing app.
Android preserves app-private data during a compatible update. Export any
irreplaceable survey first and verify the new release checksum before updating.

### Roll back or uninstall

Android generally requires uninstalling the newer app before installing an
older build. Uninstalling or clearing app storage removes the app-private
database, captures, cached exports, and offline map regions. Export anything you
need first. Do not manually alter files in the app-private storage area.

## 4. Understand the application workflow

The normal workflow is:

1. Connect and identify the HackRF.
2. Run a receive compatibility test for a new phone, cable, hub, or HackRF.
3. Select or create an equipment profile and survey ranges.
4. Review preflight health and start the visible survey.
5. Walk or remain at planned observation points while monitoring health.
6. Stop and let the application drain and finalize stored observations.
7. Process the survey into discoveries.
8. Review a discovery in the detail, map, or equivalent list views.
9. Optionally revisit a signal with focused RX and create a short IQ capture.
10. Review privacy choices and explicitly save or share an export.

The application distinguishes **collection time** from **elapsed span**.
Collection time advances only while usable receive data is being collected.
Elapsed span includes pauses, detachments, and other gaps. A survey that sits
paused or disconnected may therefore have a much longer elapsed span than
collection time.

## 5. Connect the HackRF and check compatibility

1. Attach the antenna, cable, and any required powered hub.
2. Connect the HackRF to the Android device.
3. Open the app and approve Android's USB permission for this device.
4. Confirm that the displayed board, firmware, API version, and redacted serial
   suffix describe the expected receiver.
5. For a new USB topology, run the **receive compatibility test**.

The compatibility test receives and discards samples while measuring delivered
throughput, drops, and errors. It does not save an IQ recording. Stop it in the
app if the cable, hub, phone, or receiver becomes unstable or hot. A failure is
useful evidence that the selected sample rate or topology is not dependable; do
not treat it as a survey pass.

If the device does not appear, see [Troubleshooting and recovery](troubleshooting.md).

## 6. Create equipment and survey profiles

### Equipment profile

Record the physical setup accurately:

- antenna name or description;
- adapters, filters, attenuators, hub, and cable details;
- optional local photo reference;
- sample rate: 2, 4, or 8 MS/s;
- fixed LNA and VGA gains; and
- recommended filter setting.

After a survey references a profile, edits create a new version so historical
comparability is preserved. Create separate profiles when changing the antenna,
adapter chain, gain, sample rate, or filter. Relative levels collected under
materially different profiles should not be compared as if they were calibrated
measurements.

### Survey frequency plan

The built-in starter ranges include 315 MHz, 433 MHz, 150-174 MHz, 450-470 MHz,
and 902-928 MHz. Choose only ranges you may lawfully receive. You can add ranges
and exclusions, select a 50, 100, or 200 kHz analysis-bin width, and configure
the target revisit interval, detector threshold, and minimum bandwidth.

Narrower bins and wider frequency coverage demand more scan work. Review the
estimated cycle time in preflight; a revisit target that is shorter than the
estimated scan cycle cannot be achieved simply by leaving the survey running
longer.

## 7. Plan a repeatable field survey

For comparable results:

- Use the same equipment-profile version for all comparable runs.
- Keep antenna orientation, carrying position, gain, sample rate, and filter
  fixed.
- Plan a repeatable route or fixed observation points.
- Allow the phone to obtain a fresh location fix before starting when mapping
  matters.
- Download an offline map region before leaving connectivity.
- Keep notes factual and avoid personal information you do not intend to
  disclose later.
- Avoid changing USB topology during a run.

Stationary surveys are valid. They characterize variation at one location, not
spatial coverage. Walking a route improves spatial support but still does not
locate a transmitter: obstructions, reflections, antenna orientation, receiver
gain, and timing all affect observed strength.

## 8. Review preflight and start a survey

Before starting, the preflight screen shows:

- HackRF readiness and selected device;
- bands, included ranges, and exclusions;
- analysis resolution;
- sample rate, filter, and LNA/VGA gains;
- estimated scan-cycle duration;
- GPS state and accuracy;
- free storage and storage estimate; and
- receive-path health information available for the current topology.

Resolve red failures before continuing. A degraded or missing GPS state does not
require discarding otherwise valid RF data. If the app offers a deliberate
continue-with-degraded-location choice, use it only after accepting that those
observations may be stored as stale or unlocated. The app never invents a
coordinate.

Start with **Start visible survey**. Android may ask for location permission at
this point. Denying permission keeps location unavailable and is surfaced in the
result rather than silently substituted.

## 9. Operate an active survey

Keep the app's persistent foreground-service notification visible. The active
screen reports:

- current state, device, and active frequency range;
- collection time and total elapsed span;
- distance traveled;
- USB throughput and persisted aggregate count;
- GPS accuracy and fix age;
- queue depth and stage drops;
- drops, overruns, and malformed-frame counts;
- free storage and estimated remaining duration; and
- battery and thermal warnings.

### Screen off and background behavior

After starting from the visible app, the foreground service continues collection
with the screen off and shows an ongoing notification. The app does not request
unrestricted background location. Device-vendor battery restrictions can still
affect long runs, so validate an unfamiliar Android device before relying on it.

### Pause, resume, and stop

- **Pause** stops collection without pretending the interval was observed.
- **Resume** restarts collection and preserves the gap.
- **Stop** ends acquisition, drains pending work, and finalizes the survey.

Wait for finalization to finish. Do not unplug the HackRF during the drain unless
hardware safety requires it. If the duration counter continues while collection
is paused or disconnected, check whether it is the elapsed span; collection time
should remain distinct.

### Interpreting active warnings

Do not hide or subtract drops, overruns, malformed frames, stale GPS, or missing
location. They are part of the evidence quality. If rates climb persistently:

1. Pause or stop the survey.
2. Check the cable, hub power, connector strain, phone temperature, and storage.
3. Re-run compatibility testing after changing topology.
4. Start a new survey when the equipment profile or acquisition settings change.

## 10. Review the completed survey

The summary should show `COMPLETE` after an orderly stop and finalization. Review:

- aggregate and location counts;
- collection time versus elapsed span;
- route and location coverage;
- acquisition gaps and their reasons;
- drops, overruns, malformed frames, and queue health; and
- storage, battery, and thermal events.

An incomplete location association is not the same as lost RF data. For example,
“93 detections have no associated location fix” means those detections remain
available for non-spatial review but are excluded from the map. Record that
limitation in any field report or export interpretation.

## 11. Process and review discoveries

Discovery processing runs from stored survey aggregates and can be performed
offline. Partial results remain useful when some observations lack locations.

Use the discovery filters to narrow by minimum/maximum frequency, epoch time,
classification hint, survey, comparable equipment, or review state. Open a
discovery to review:

- center frequency and estimated bandwidth;
- equipment comparability;
- first/last seen times, occurrences, and duty estimate;
- location extent, or an explicit unavailable state;
- duration and repetition features;
- relative SNR history; and
- cautious classification hints.

Classification hints are leads, not decoded identities. The app does not claim
that a signal belongs to a particular person, device, or transmitter.

You can add a user label, tags, and notes; set the review state to
**Interesting**, **Ignore**, or **Artifact**; split the last grouping; or select
two or more compatible discoveries to merge. Split and merge actions preserve
provenance. Reprocess from the survey summary when updated processing is needed.

## 12. Use the map and equivalent list

The map is titled **Observed relative strength** because it visualizes where the
receiver observed a discovery more or less strongly. It is not a transmitter
location estimate.

1. Select the intended discovery or explicit fingerprint comparison.
2. Apply survey, equipment, detection, time, frequency, bandwidth, and confidence
   filters as needed.
3. Switch between map and list views. Zoom to inspect a cell rather than reading
   a single colored area as a precise source.
4. Read the legend: color represents median relative dBFS and opacity represents
   support or confidence. Route, GPS-uncertainty halos, and gaps provide context.

Confidence is support-based:

- low: fewer than 3 supporting samples;
- medium: at least 3 samples; and
- high: at least 5 samples across at least 2 surveys.

The equivalent list is the accessible, non-map representation. It reports the
median, upper quartile, spread, sample support, GPS uncertainty, interpolated
count, time, and equipment context. Prefer it when precise comparison or screen
reader use is more practical than the map.

Stale or unlocated observations are never placed on the map. Sparse coverage,
GPS uncertainty, interpolation, and changing equipment all limit what can be
inferred.

### Download an offline map region

1. Open the offline-region control while connected.
2. Select a bounded area around an observed or planned route.
3. Choose only the zoom range needed in the field.
4. Start the download and wait for completion.
5. If interrupted, use resume. Remove an unneeded region with the confirmation
   action to recover storage.

The public OpenFreeMap basemap has no service-level guarantee. Downloaded tiles
support the basemap offline; private observation overlays already remain local.

## 13. Revisit a discovery and capture IQ

Focused capture is optional and intentionally bounded. Start from a discovery's
**Revisit in focused RX and capture IQ** action, or choose **Manual focused RX /
IQ capture** from setup.

### Configure focused RX

1. Verify the prominent **RX only** state and confirm RF amplifier and
   antenna-port power remain off.
2. Review the originating equipment profile and any safety-relevant difference.
3. Enter the center frequency in Hz.
4. Select 2, 4, or 8 MS/s. A discovery-originated workflow may lock the sample
   rate to keep the revisit comparable.
5. Select 0.25, 1, 2, 5, or 30 seconds.
6. Review expected bytes, measured USB throughput, and the 256 MiB free-space
   reserve.
7. Select **Start focused preview**. Use the bounded waterfall to confirm timing
   and relative dBFS, then use **Stop / idle** when finished previewing.

The approximate raw `.cs8` data size is sample rate multiplied by two bytes per
complex sample and by duration:

| Sample rate | 0.25 s | 1 s | 2 s | 5 s | 30 s |
|---|---:|---:|---:|---:|---:|
| 2 MS/s | 1 MB | 4 MB | 8 MB | 20 MB | 120 MB |
| 4 MS/s | 2 MB | 8 MB | 16 MB | 40 MB | 240 MB |
| 8 MS/s | 4 MB | 16 MB | 32 MB | 80 MB | 480 MB |

These are decimal raw-data estimates and exclude metadata, preview, filesystem
overhead, and the required safety reserve.

### Make the capture

You may add a note of up to 4096 characters. The capture action is enabled only
when measured receive throughput is at least 95% of the requested rate. Keep the
phone still enough to protect the connector, then start the capture and wait for
completion.

A valid result reports **Complete**, the exact byte count, a hash prefix, and
**sidecar + PGM preview**. The application requires the exact byte count with no
capture gaps or overruns. A failed or partial attempt is not presented as
complete.

Each complete capture contains:

- `.cs8`: signed 8-bit interleaved I, Q samples;
- JSON sidecar: acquisition settings and integrity metadata; and
- PGM preview: a portable grayscale overview.

The app publishes the trio atomically. Review the capture's export controls
before sharing: include only the needed files and decide separately whether to
include route, notes, identifiers, and full, rounded, or omitted coordinates.

## 14. Export a survey safely

Open the completed survey summary and review every option:

- **Include linked IQ**;
- **Include route**;
- **Include notes/user labels**;
- **Include device identifier suffix**; and
- coordinates: full, rounded, or omitted.

Read the manifest review line before choosing **Create reviewed survey bundle
and open share sheet** or **Save reviewed survey bundle**. The first creates an
explicit share action; the second saves without sending it to another app.

Suggested disclosure profiles:

| Purpose | Coordinates | Route | Notes/labels | Device suffix | IQ |
|---|---|---|---|---|---|
| Private archival backup | As needed | As needed | As needed | As needed | As needed |
| Technical collaboration | Rounded or omitted | Usually omit | Redact | Omit | Only if required |
| Safest minimal share | Omit | Omit | Omit | Omit | Omit unless essential |

An export bundle uses schema version 1.0.0 and can contain JSON, CSV, GeoJSON,
gap records, capture files, and a manifest of hashes. Share-cache copies expire
after 24 hours; the authoritative private survey remains until you delete app
data. Moving an exported file into email, cloud storage, messaging, or another
application places it under that destination's privacy and retention rules.

See [Export and reimport](export-guide.md) for the file-level checklist.

## 15. Reimport a survey bundle

1. Transfer the unchanged bundle onto the Android device through a channel you
   trust.
2. From setup, choose **Import validated survey bundle**.
3. Select the bundle and wait for validation and staging to complete.
4. Confirm that the imported survey and its integrity status appear before
   deleting the external copy.

The importer accepts the supported 1.0.0 schema and validates paths, hashes,
sizes, compression bounds, and capture sidecars. Import is staged and atomic:
an unsafe or corrupt archive is rejected without a partially committed survey.
The app treats archive content as data and does not execute scripts.

If an archive was modified by an editor, cloud service, or manual extraction and
repacking, hash validation may fail. Return to the original exported bundle
rather than bypassing validation.

## 16. Recover from interruptions

### HackRF detached or USB path lost

The app records a gap and pauses acquisition rather than filling the interval
with invented observations. Reconnect the same HackRF, approve USB permission if
Android asks again, and use the offered **Recover survey** action. If topology or
equipment settings changed, finish the interrupted survey and begin a new one.

### App process or phone UI was killed

Reopen the app. A recoverable survey returns paused with a `PROCESS_DEATH` gap.
Review the gap, reconnect the same device if necessary, then recover or finalize.
Finalization is idempotent, and incomplete capture artifacts are cleaned up or
reindexed rather than reported as complete.

### GPS is stale, inaccurate, or absent

Move to a clearer location and wait for a fresh fix, or deliberately continue
with degraded location. RF data remains valid as non-spatial evidence. The app
will not map stale or unlocated observations and will report the missing
associations.

### Storage is low

Stop and finalize the survey, export anything valuable, then remove unneeded
offline regions, saved exports, captures, or app data through supported UI and
Android controls. Focused capture is rejected when its expected size plus the
256 MiB reserve cannot fit. Do not continue acquisition by bypassing storage
checks.

### Battery or thermal warning

Stop or pause, ventilate equipment, and use a safe power source if needed. The
application does not silently reduce sample rate or alter gains to hide the
condition, because doing so would undermine comparability.

### Permission denied

Reconnect and approve USB permission for the HackRF. For location, grant the
requested foreground permission in Android settings if route data is needed.
The app reports denial explicitly; it does not silently claim a fix.

### Database or archive reported corrupt

The app fails closed. Preserve the original export or affected device state,
record the error, and do not edit manifests or database files to force them
open. Reimport a known-good, unchanged bundle when available.

## 17. Manage local data and free phone storage

Before deleting anything, export surveys or captures you need and validate the
saved bundle. Then use the app to remove unneeded offline map regions and saved
artifacts where controls are available. Android's **Clear storage** action or
uninstalling the app removes all app-private data, including surveys, captures,
offline tiles, and local metadata, and is not reversible without an export.

Temporary share-cache copies expire after 24 hours. External copies created by
**Save** or another app's share sheet are outside RF Field Notebook's control and
must be managed in that destination.

## 18. Accessibility and field usability

Screens are scrollable for large font sizes and compact displays. Controls and
status indicators have text labels, and the equivalent results list provides
the map's quantitative information without requiring color or map gestures.
Android screen readers can announce labeled controls and status text.

Before field use:

- set the preferred Android text size and display scaling;
- test TalkBack or the chosen screen reader with the setup and active screens;
- use the equivalent list when map gestures or color distinctions are difficult;
- keep the phone accessible enough to reach **Pause** and **Stop** safely; and
- avoid operating the screen while walking through unsafe terrain or traffic.

## 19. How to interpret results responsibly

- **dBFS is relative.** It is referenced to the receiver's digital full scale,
  not calibrated received power in dBm.
- **Map color is observational.** Stronger color means stronger observations
  under the recorded setup, not proximity to a transmitter.
- **Confidence is support, not certainty.** More samples and surveys improve
  support but do not remove multipath, antenna, timing, or GPS effects.
- **No location is better than false location.** Unlocated detections remain in
  the list and discovery evidence but not on the map.
- **Gaps matter.** A quiet interval that overlaps a USB, process, storage, or
  pause gap is not proof that a signal was absent.
- **Equipment changes matter.** Compare results only when the equipment context
  is equivalent or the difference is explicitly part of the analysis.
- **IQ is not an automatic conclusion.** It is raw evidence for later lawful
  analysis and can be large and sensitive.

## 20. Field checklists

### Before leaving

- Verify the installed release and checksum record.
- Charge the phone and test the selected cable/hub topology.
- Inspect the antenna and connectors; add attenuation when appropriate.
- Run compatibility testing after any hardware change.
- Confirm equipment and survey profiles.
- Free enough storage for the survey and planned captures.
- Download the required offline map region.
- Confirm local authority and disclosure plan.

### Before starting each survey

- Confirm the expected HackRF identity.
- Confirm RX-only state, RF amplifier off, and antenna power off.
- Review frequency ranges, exclusions, gains, rate, filter, and cycle estimate.
- Check fresh GPS state or deliberately accept degraded location.
- Check storage, battery, thermal state, and notification visibility.
- Start only from the visible preflight screen.

### Before sharing

- Open the manifest review.
- Remove precise coordinates and route unless essential.
- Remove notes, labels, and device suffix unless essential.
- Include linked IQ only when the recipient needs raw samples.
- Verify the destination and its retention policy.
- Keep an unchanged archival copy when future reimport is required.

## 21. Glossary

**Aggregate:** A stored statistical summary of received spectrum observations.
A survey does not continuously retain all wideband IQ.

**Collection time:** Time during which usable receive data was actively
collected.

**Elapsed span:** Wall-clock time from survey start through finish, including
pauses and gaps.

**Discovery:** A grouped recurring observation produced by offline processing.
It is not an assertion of transmitter identity.

**dBFS:** Relative digital level below full scale. It is not calibrated dBm.

**Gap:** A recorded interval with missing acquisition or location evidence,
together with its known reason where available.

**IQ / `.cs8`:** Interleaved signed 8-bit in-phase and quadrature samples from
a focused receive capture.

**Location fix age:** How long ago Android produced the location associated with
current status.

**Equipment profile:** The versioned antenna, adapter, rate, filter, and gain
context required to judge comparability.

**Relative strength map:** An uncertainty-aware view of where the receiver
observed a discovery more or less strongly. It is not source localization.

## 22. Known release-candidate limitations

- Physical end-to-end coverage is concentrated on a Pixel 8a and one HackRF One;
  other phones, hubs, and firmware combinations need their own compatibility
  check.
- Relative observations are not calibrated power measurements.
- The app does not decode payloads or locate signal sources.
- Focused IQ capture is manual, limited to 30 seconds, and preserves a 256 MiB
  storage reserve.
- Bundle schema 1.0.0 import is strict; unsupported or modified archives are
  rejected.
- Offline-map storage management is functional but intentionally basic.
- The public basemap service has no uptime guarantee.

For the release evidence and complete limitation record, see
[M5 known limitations](../evidence/M5/known-limitations.md).

## 23. Further reference

- [Operator documentation index](README.md)
- [Quick start](quick-start.md)
- [Equipment and RF safety](equipment-safety.md)
- [Survey and interpretation](survey-guide.md)
- [Export and reimport](export-guide.md)
- [Troubleshooting and recovery](troubleshooting.md)
- [Privacy statement](privacy.md)
- [Authoritative product specification](../android-rf-field-notebook-spec.md)

If this manual and the product specification ever conflict, the specification
is authoritative. Preserve the receive-only and local-data guarantees when
developing local operating procedures.
