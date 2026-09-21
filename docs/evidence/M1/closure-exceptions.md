# M1 closure decision and evidence exceptions

- Status: Accepted for milestone closure
- Date: 2026-09-21
- Decision: Proceed to M2 without repeating the checks listed below

M1's core hardware gate passed: a 31:04 screen-off survey completed on the
Pixel 8a with zero drops, overruns, malformed frames, stale fixes, or acquisition
gaps. USB detach, process interruption, and the active-service stall and
low-storage paths also ended safely with persisted, visible outcomes. The
project owner accepted the following additional evidence limitations so work
could move to M2.

| Unperformed extra confirmation | Evidence used at closure | Later treatment |
| --- | --- | --- |
| Repeat the clean-install USB permission sequence on the final 32-buffer build | M0 physical clean-install flow, M1 permission regression, and repeated M1 permission grants | Recheck during M5 if the installer or permission flow changes |
| Instrument native handle counts directly after every stop path | Native lifecycle tests plus observable detach drain, service removal, repeated reopen, and process survival | Add instrumentation only if a leak or reopen regression appears |
| Run dedicated physical GPS denial, stale-gap, and interpolation scenarios | Deterministic location tests, Room unlocated-observation test, stale preflight state, and 2,340 preserved unlocated observations in the final gate | Include focused field cases in M5 hardening if release confidence requires them |
| Repeat the M1 physical gate at 8 MS/s | M0 physical 8 MS/s result, bounded queue tests, and the clean M1 2 MS/s production gate | Reassess higher-rate performance during M5 or when shipping profiles above demonstrated capacity |
| Physically consume phone storage below the reserve | Storage-guard tests and a debuggable-service injection through the production low-storage handler, without filling the phone | Keep as a safe injected test; do not fill user storage merely for evidence |
| Create a physical queue-overload condition | Deterministic pressure tests and persisted queue high-water marks from the final gate | Exercise only with a controlled fixture or safe load generator |
| Capture refreshed setup, active, and completion screenshots | Initial setup screenshot plus recorded visible active/completion text and persisted hardware readbacks | Refresh during later UI work; no M1 behavior depends on the images |

The connected Android result files reported all storage and app tests passing,
but the aggregate `connectedDebugAndroidTest` command returned nonzero during
test-APK uninstall. This is retained as a test-runner limitation and is not
reported as a clean command pass.

These exceptions do not change the receive-only safety boundary, persistence
schema, or M2 input contract. They are disclosed limitations, not claims that
the omitted physical repetitions passed.
