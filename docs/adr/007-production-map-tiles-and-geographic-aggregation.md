# ADR 007: Production map tiles and uncertainty-aware geographic aggregation

- Status: Accepted
- Date: 2026-09-21
- Owners: M3
- Related requirements: FR-MAP-001 through FR-MAP-007; specification sections 4.2, 9.8, 11.4, 16, and 17
- Supersedes: none

## Context

ADR 005 selected MapLibre Native 13.6.1 and OpenFreeMap Liberty for the M0
prototype, but deliberately left the production tile provider and M3
aggregation contract open. M3 must provide offline regions, attribution,
privacy separation, adaptive cells, robust relative strength, and honest
location uncertainty without implying a transmitter coordinate.

OpenFreeMap's public instance currently permits application and commercial use
without an API key or request quota, supports MapLibre Native clients, requires
OpenMapTiles/OpenStreetMap attribution, and publishes privacy terms that do not
use accounts or cookies. It has no availability SLA and may change or end.

## Decision

Continue with MapLibre Native Android 13.6.1 OpenGL and promote the OpenFreeMap
Liberty style (`https://tiles.openfreemap.org/styles/liberty`) to the initial
production basemap. Keep visible attribution online and offline:
`OpenFreeMap © OpenMapTiles Data from OpenStreetMap`.

Basemap requests and MapLibre's offline database stay inside the `maps` module.
The provider receives ordinary style/tile requests for the viewed or downloaded
area, but no Room access, precise stored-fix payload, fingerprint ID, frequency,
equipment metadata, note, or export capability. Local routes, gaps, accuracy,
and signal cells are supplied as in-memory GeoJSON sources. Offline downloads
require an explicit bounded-area and zoom-range action, expose progress and
stored bytes, resume incomplete regions, and require confirmation before
removal.

Use rebuildable aggregation version `m3-grid-v1`. At each selected zoom, choose
a Web-Mercator grid size no smaller than both the display-scale target and twice
the largest supporting horizontal-accuracy radius. Each cell stores sample and
survey count, median relative dBFS, upper quartile, interquartile spread,
maximum GPS accuracy, interpolated count, time range, equipment versions, and
confidence. High confidence requires at least five samples across at least two
surveys; three samples are medium confidence. The renderer uses the median—not
the maximum—to encode strength. Missing and stale locations remain counted but
are never invented or placed on the map.

## Alternatives considered

Google Maps remains rejected because of key/account coupling and limited
offline control. A custom renderer remains unnecessary. Bundling planet or
regional MBTiles was rejected for application size and update burden. A single
fixed grid was rejected because it would overstate precision at high zoom or
erase useful detail at low zoom. Persisting every zoom-derived cell in Room was
rejected because the authoritative M1/M2 evidence already supports deterministic
rebuilding and the projection changes with filters and zoom.

## Consequences

The app can render and reopen explicit offline regions without exposing RF
metadata to a tile service. Users still reveal the basemap areas they request
while online, and the public provider has no SLA; a later release may self-host
the same open stack without changing local overlay contracts. Large GPS errors
conservatively enlarge all cells in the current filtered layer. Cell confidence
describes support and repeatability, not source-location certainty.

## Validation

Unit tests cover zoom/accuracy sizing, robust statistics, confidence thresholds,
missing/interpolated evidence, every M3 filter, changed-equipment exclusion, and
offline metadata round trips. Pixel 8a connected tests cover map/list UI,
20,000-observation filter/zoom/pan behavior, bounded offline download, saved
resource/byte status, and a successful render with airplane mode enabled and
Wi-Fi disabled.

## Follow-up

M5 must re-check provider terms, attribution, dependency licenses, and public
service suitability before release. A future provider or self-hosted endpoint
requires a superseding ADR if privacy, attribution, offline behavior, or
distribution obligations change.
