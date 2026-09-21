# M3 aggregation validation

## Red-first evidence

The M3 domain test was introduced before its implementation. Its first run
failed compilation on the intentionally absent `MapObservation`,
`GeographicAggregator`, `MapFilters`, `MapConfidence`, and offline metadata
contracts. The production types were then added and the same suite passed.

## Contract and results

Aggregation version `m3-grid-v1` projects valid observations into adaptive
Web-Mercator cells. Cell width is the next power-of-two meter size that is no
smaller than the zoom display target or twice the largest reported horizontal
accuracy. The renderer encodes the median relative dBFS. The equivalent view
also reports p75, interquartile spread, support, survey count, maximum accuracy,
interpolated count, time range, and equipment versions.

High confidence requires at least five observations spanning at least two
surveys; medium requires three observations. Tests prove that a lone extreme
sample cannot produce high confidence and cannot replace the robust median.
Missing locations are counted but never placed. Interpolated locations remain
placed and explicitly counted. Changed-equipment rows are excluded by default
and produce an explanation.

The deterministic CC0 fixture in `test-data/M3/repeat-route.csv` contains two
passes, one missing fix, one interpolated fix, and a deliberately incompatible
equipment profile. It uses synthetic coordinates near Null Island and contains
no user or home coordinates. The fixture test reports six comparable placed
observations, one missing observation, one interpolated observation, and one
excluded incompatible observation.

Room schema version is unchanged. M3 adds a read query for stored survey fixes;
derived geographic cells remain rebuildable and are not persisted.
