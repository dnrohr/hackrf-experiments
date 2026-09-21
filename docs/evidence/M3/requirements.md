# M3 requirement traceability

All geographic overlays are derived locally. The basemap receives tile/style
requests only; it never receives a fingerprint identifier, frequency,
equipment profile, route GeoJSON, note, or precise stored fix.

| Requirement | Implementation | Automated evidence | Visual/connected evidence |
| --- | --- | --- | --- |
| FR-MAP-001 | `MapDataRepository`, `FieldMapView`: route line, dashed acquisition gaps, observation cells, accuracy halos, explicit missing/stale totals | `MapExplorerUiTest.equivalentListExposesUncertaintyGapsAndComparabilityWithoutColor` | `ui/m3-observed-relative-strength-map.png`, `ui/m3-equivalent-list.png` |
| FR-MAP-002 | `MapExplorerPage`: selected fingerprint by default; explicit, stable-color comparison selections, limited to four visible layers | map/list UI test exercises a changed-equipment comparison | map and equivalent-list screenshots |
| FR-MAP-003 | `GeographicAggregator`: Web-Mercator cells adapt by zoom and are at least twice the largest supporting accuracy | `cellSizeAdaptsToZoomButNeverDropsBelowAccuracyDiameter`, `accuracyLimitsCellSizeAndSingleOutlierCannotBecomeHighConfidence` | `aggregation-validation.md` |
| FR-MAP-004 | Cells expose count, survey count, median, p75, IQR, accuracy, interpolation, time, equipment, and support confidence; rendering uses median | robust-statistics, outlier, repeat-survey, missing/interpolated, and deterministic-fixture tests | equivalent-list and repeat-route evidence |
| FR-MAP-005 | Session-retained controls cover survey, time, frequency, bandwidth, detection type, equipment, and minimum confidence | `everyRequiredFilterDimensionIsApplied`; 20,000-row connected filter test | `performance.md` |
| FR-MAP-006 | Screen/layer title is `Observed relative strength`; explanatory copy explicitly rejects source-location inference; renderer has no source pin/crosshair | UI semantics assertions plus source audit in receive-only validation | all UI screenshots |
| FR-MAP-007 | `FieldOfflineRegionManager` provides bounded area/zoom, progress, resources/bytes, persistence, resume, failure state, and confirmed removal | `OfflineRegionConnectedTest`, metadata round-trip test | `offline-behavior.md`, `ui/m3-offline-observed-relative-strength-map.png` |

Primary aggregation tests are in
`android/maps/src/test/kotlin/dev/rfnotebook/maps/GeographicAggregationTest.kt`.
Connected UI tests are in `android/app-ui/src/androidTest/kotlin/dev/rfnotebook/app/`.
