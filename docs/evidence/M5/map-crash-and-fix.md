# Relative-strength map crash and regression evidence

Android's retained `data_app_crash` Dropbox records four foreground crashes of
the physical Pixel 8a release process when the operator opened the observed
relative-strength map. Sanitized excerpts (UID, PID, and private observation
data omitted) are:

```text
Package: dev.rfnotebook v5 (1.0.0-rc1)
Foreground: Yes
Process-Runtime: 9422201
Timestamp: 2026-09-22 16:34:48.420-0400
Build: google/akita/akita:17/CP2A.260805.005/15828068:user/release-keys
java.lang.IllegalStateException: Vertically scrollable component was measured
with an infinity maximum height constraints ... nesting layouts like LazyColumn
and Column(Modifier.verticalScroll()).

Further matching crashes: 16:36:33.997, 16:39:06.547, and 19:00:30.688.
```

The production notebook host already owns vertical scrolling. `MapExplorerPage`
also applied `verticalScroll`, causing the infinite-height measurement. M5
removed the inner scroll modifier. Every `MapExplorerUiTest` case now mounts the
page inside the production-shaped scrolling host, preventing a regression that
would be hidden by testing the page in isolation.

Direct Pixel 8a results after the fix: all three map tests pass, including the
20,000-observation filter/zoom/render budget and map pan, equivalent-list
uncertainty/gap/comparability semantics, and large-text/dark-theme operation.
The final short hardware workflow reopens the retained-data map before this
defect is closed in the completion audit.
