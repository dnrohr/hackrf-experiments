# Privacy statement

RF Field Notebook has no account, advertising, analytics, telemetry, or cloud
sync. Precise coordinates, routes, frequencies, HackRF serial suffixes, notes,
detections, fingerprints, and IQ remain in Android app-private storage until
the user explicitly exports them. Android backup is disabled.

The application requests USB permission for the selected HackRF, precise
foreground location when a survey starts, notifications for visible foreground
work, and network access only for MapLibre basemap/offline-region tiles.
Acquisition and analysis do not require a network. The maps module sends normal
tile requests to the configured OpenFreeMap service; private overlays, routes,
frequencies, signal values, notes, and radio identifiers are not placed in tile
URLs or uploaded. OpenFreeMap's current terms/privacy policy are linked from
the M5 dependency review and may change independently.

Local diagnostics may record state transitions, native error codes, throughput,
overruns, queue depth, processing latency, file outcomes, and GPS accuracy
buckets. They exclude coordinates, frequencies, serials, notes, and IQ by
default. There is no automatic diagnostic export.

Before sharing, the app presents an explicit manifest review. The user may
exclude IQ, route, exact coordinates, notes, and device suffix. Rounded
coordinates are less precise but are not anonymous. The receiving person or
system becomes responsible for the exported copy.

Uninstalling or clearing app storage removes local application data. Export any
wanted records first. Release rollback requires uninstall/reinstall, so it has
the same data-loss consequence unless an export is retained.
