# Troubleshooting and recovery

## HackRF not found or permission denied

Use a known data cable and powered hub, unlock the phone, reconnect, and approve
the Android USB prompt for the displayed HackRF. Another SDR app must not hold
the device. A denied/revoked permission is recoverable and must not expose data
or start acquisition. If firmware/API is reported incompatible, stop and use
the supported HackRF `2026.01.3` host/firmware procedure on a computer; never
update firmware while Android has the device open.

## Detach, stall, or overrun

The app stops/pauses and records a gap rather than concealing discontinuity.
Reconnect the same radio, return to the app, and recover the paused survey. For
repeated stalls, replace the cable, power the hub, reduce physical strain, and
check the device temperature. Queue drops and overruns remain part of the
survey record.

## GPS is missing, stale, or inaccurate

Grant precise foreground location only if wanted, enable device location, move
to an open area, and wait for a fresh accuracy value. Continuing degraded is
allowed: unlocated observations remain useful for discovery but are excluded
from false map coordinates. The app does not require or request background
location.

## Low storage

Free app/internal storage before capture. The capture estimate includes a
reserve and failure leaves no complete-looking artifact. Export wanted surveys
before clearing app data. Do not remove files directly from app storage.

## Battery or thermal warning

Connect safe external power if appropriate, shade and ventilate the equipment,
and use the visible Stop option. The app never silently changes gains or sample
settings. Battery saver and screen-off may continue a foreground survey, but
vendor power policies can still stop apps; inspect the persisted state on
return.

## Process interruption or failed operation

Reopen the app. An interrupted active survey is offered paused with a recorded
process-death gap; an interrupted finalization resumes idempotently. Temporary
capture/export/import `.part` artifacts are cleaned. A complete, hash-valid
capture trio in the narrow file-commit/database-commit window is re-indexed;
an incomplete or inconsistent trio is removed and its durable capture row is
marked failed. A corrupt database fails closed instead of destructive
recreation. Keep the original archive when an import is rejected and inspect
the precise error.

## Offline map

Download the region before leaving connectivity and verify its completion.
Previously stored overlays and the equivalent list remain local. The public
OpenFreeMap service has no availability SLA; an unavailable basemap does not
remove observations or justify inventing positions.
