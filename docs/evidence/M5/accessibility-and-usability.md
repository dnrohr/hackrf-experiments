# M5 accessibility and usability review

Target: Pixel 8a, Android 17, portrait and landscape, normal and large text,
light and dark system themes. Review combines connected Compose assertions,
Android accessibility-node inspection, physical touch checks, and retained M3
map/list screenshots. No critical or high-severity issue remains.

| Area | Result | Evidence/disposition |
| --- | --- | --- |
| Screen-reader order and labels | Pass | Android node traversal follows visual order. Icon-like LNA/VGA minus/plus controls now expose `Decrease/Increase LNA/VGA gain`; each reviewed-export option is one labelled checkbox target; receive test, permission, Pause, Resume, Stop, map/list, capture, export, and import actions have spoken text. |
| Large text/display scale | Pass | Core setup, active survey, discovery/detail, map-equivalent list, capture, and export screens remain vertically scrollable without clipped required actions. M3 physical large-text/dark evidence retained; M5 connected suite rechecked setup controls. |
| Touch targets | Pass | Primary controls use Material buttons with at least 48 dp target height. Physical active-survey Pause/Resume/Stop targets remain separately selectable one-handed. |
| Orientation | Pass | Setup inputs and navigation identifiers use saved state; completed summaries reload from Room, and restored discovery-detail/map pages reload persisted data instead of retaining a blank transient model. Portrait and landscape expose the same scrollable actions; rotation does not stop acquisition. `M5HardeningUiTest.setupValuesSurviveActivityRecreation` covers activity recreation. |
| Light/dark contrast | Pass | Material theme colors are used rather than fixed low-contrast text; errors use theme error color plus text. M3 physical light/dark evidence covers map/list. |
| Non-color equivalents | Pass | Status, relative strength, GPS quality, gaps, warnings, and map uncertainty have text labels/counts/legend; color is never the sole carrier. |
| Equivalent map workflow | Pass | Discoveries/detail and the geographic-cell list expose count, robust relative dBFS, accuracy, and uncertainty without requiring the map. |
| Active one-handed controls | Pass | Pause and Stop are on the first active-screen viewport and in the persistent notification; actions are textual and do not require a gesture. |
| First-run comprehension | Pass | The first setup section explicitly states receive-only scope, RF/antenna power defaults, deliberate fixed gain/antenna choice, relative-not-calibrated readings, no transmitter-location claim, local protected fields, powered-hub/input safety, and legal responsibility. Connected assertions prevent removal. |

## Defects resolved in M5

1. Gain buttons previously exposed only “−” and “+”. Added explicit semantic
   labels and connected regression assertions.
2. The compatibility receive test ran but its measured result was visible only
   in the notification. Setup now displays phase, hardware/API, measured rate,
   delivered bytes, drops, callback errors, and failure detail.
3. The receive test had no in-app Stop control. An explicitly labelled Stop was
   added while opening/receiving/sweeping, and non-error completion text no
   longer uses the error color.
4. Safety/privacy concepts were distributed across controls and documentation.
   A concise first-survey section now places them before hardware actions.
5. Export checkboxes visually had adjacent labels but separate accessibility
   semantics. Each row is now a single labelled checkbox/touch target with a
   connected semantic regression assertion.
6. Rotation previously restored a transient detail/map page without rebuilding
   its model. Saved identifiers now drive Room-backed restoration, and setup
   fields have an activity-recreation regression.

Automated coverage is in `M5HardeningUiTest`, `DiscoveryUiTest`,
`MapExplorerUiTest`, and `OfflineRegionConnectedTest`. Retained physical UI
evidence is under `docs/evidence/M3/ui/` and `docs/evidence/M4/ui/`; the M5
field report records the final device traversal.
