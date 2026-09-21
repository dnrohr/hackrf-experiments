# M3 map and accessibility QA

QA was performed on a Pixel 8a running Android 17/API 37. The connected suite
covered the production MapLibre surface in the light theme, an equivalent list
in the light theme, and the list at 150% font scale in the dark theme.

The final map image shows the traveled route, adaptive relative-strength cell,
GPS-accuracy halo, and a dashed acquisition gap above the basemap. The heading
and semantics say `Observed relative strength`; explanatory text says the view
does not estimate or identify a transmitter location. There is no source pin,
crosshair, bearing, or navigation affordance. Attribution remains visible.

The equivalent view is operable without color or gestures. It reports area,
median, upper quartile, spread, observation and survey support, confidence, GPS
uncertainty, interpolation, time, equipment context, gaps, and missing/stale
counts. At 150% text, map/list/filter/zoom controls were changed to full-width
rows; the rerun passed with readable labels and Material minimum touch targets.
The map surface has a descriptive screen-reader label, while the list exposes
the same facts as text.

Images:

- `ui/m3-observed-relative-strength-map.png`
- `ui/m3-equivalent-list.png`
- `ui/m3-large-text-dark-list.png`
- `ui/m3-offline-observed-relative-strength-map.png`
