# ADR 005: MapLibre rendering, tiles, attribution, and offline regions

- Status: Accepted
- Date: 2026-09-13
- Owners: M0
- Related requirements: FR-MAP-001 through FR-MAP-007; specification sections 4.2 and 12.2
- Supersedes: none

## Context

The app needs route, accuracy, and relative-strength rendering without sending
private observations to a tile service. MapLibre Native provides local overlay
sources and offline regions. A renderer and prototype source must be fixed for
the spike.

## Decision

Use MapLibre Native Android 13.6.1's OpenGL artifact for broad device support.
Keep observations in local GeoJSON sources; tile requests never contain survey
coordinates or signal metadata. Use MapLibre demo tiles only for M0
non-production testing, preserve their visible attribution, and exercise a
small OfflineTilePyramidRegionDefinition. Production tile/provider selection
is deliberately gated before M3 and must permit offline use and required
attribution; the demo endpoint is not a release dependency.

## Alternatives considered

Google Maps was rejected for account/API-key coupling and limited offline
control. A custom renderer was rejected as unnecessary risk. Vulkan-only was
rejected until the target phone is known.

## Consequences

Map rendering is isolated from local observations and can operate from a saved
region. M0 still requires physical visual/offline verification. Network access
exists only in the maps module and can later be disabled for offline surveys.

## Validation

The debug screen renders a route, accuracy halos, and observation-strength
markers, leaves attribution visible, and exposes an offline-region action.
Physical verification must reopen the saved region without network access.

## Follow-up

Choose and license the production tile source before M3 implementation; record
the target device's OpenGL behavior and offline storage size during M0.
