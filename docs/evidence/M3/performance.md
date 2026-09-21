# M3 Pixel 8a performance evidence

- Device: Pixel 8a (`akita`, serial redacted in committed evidence)
- OS: Android 17 / API 37
- Date: 2026-09-21
- Dataset: 20,000 deterministic observations, two repeated surveys
- Operation: Compose screen creation, filter entry, map selection, and cell
  zoom, followed by a labeled map pan
- Measured filter/zoom/render time: **2,555 ms** (budget: 8,000 ms)
- Measured pan time: **237 ms** (budget: 2,000 ms)
- Result: pass

The connected test is
`MapExplorerUiTest.mapSupportsLabeledPanFilterAndSelectionWithinBudget`. It uses
device elapsed-real-time measurements, waits for Compose idle, verifies the
MapLibre surface through its accessibility description, and captures the map
after the filter and zoom operation. The budgets intentionally include test
interaction overhead and are therefore conservative relative to a single
gesture in normal use.
