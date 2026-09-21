# M3 offline-region evidence

On 2026-09-21, the Pixel 8a connected test downloaded a bounded New York test
area at zoom levels 12–14 through the production OpenFreeMap Liberty style. The
terminal MapLibre status was `COMPLETE`, with **309 resources** and **8,639,752
bytes** stored. Progress, resource count, and byte count are exposed by the UI
and persisted in MapLibre's offline database.

Without reinstalling or clearing application data, airplane mode was enabled
and Wi-Fi disabled (`airplane=1`, `wifi=0`). The second connected test found the
same complete region without starting network work. The map UI then rendered
the cached basemap and local relative-strength overlays successfully. The
offline screenshot retains the required `OpenFreeMap © OpenMapTiles Data from
OpenStreetMap` attribution and visibly shows airplane mode. Network state was
restored afterward (`airplane=0`, `wifi=1`).

Evidence:

- `OfflineRegionConnectedTest.a_downloadOrResumeBoundedRegionReportsCompleteResourcesAndBytes`
- `OfflineRegionConnectedTest.b_completedRegionRemainsListedWithoutStartingNetworkWork`
- `ui/m3-offline-observed-relative-strength-map.png`

The product workflow also supports interrupted-region resume and confirmed
removal. Failure text remains visible rather than being converted to a
successful or empty state. A public tile service has no SLA; previously cached
regions remain local, while new downloads necessarily require service/network
availability.
