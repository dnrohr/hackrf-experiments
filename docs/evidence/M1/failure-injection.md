# M1 failure-injection record

Status: Software cases complete; active-service instrumented cases complete; physical cases recorded where applicable

| Case | Injection | Expected invariant | Result |
| --- | --- | --- | --- |
| Native/processing/disk pressure | Fill capacity-one stages and offer two units | Every rejected unit counted by its stage | Passed, `BoundedPipelineTest` |
| Malformed data | Increment malformed-frame events alongside pressure | Counter survives in health snapshot model | Passed, `BoundedPipelineTest` |
| Unsupported rate/resolution | Validate unsupported sample rate and invalid DFT resolution | Start rejected with explanation; no implicit retuning | Passed, radio/profile tests |
| Radio start failure | Fake radio throws after Active is persisted | Survey transitions to Failed with reason | Passed, `SurveyCoordinatorTest`; service startup cleanup now also persists `FAILED`, drains bounded workers, and closes the native session |
| Process death | Recover persisted Active state | Radio stopped, survey Paused, process-death gap recorded | Passed, `SurveyCoordinatorTest` |
| Low storage | Available bytes below estimate plus reserve; active-service debug trigger | Preflight denied without allocating; an active survey stops orderly if free space later crosses the fixed reserve | Guard test passed; instrumented active-service run passed with `LOW_STORAGE`, orderly `COMPLETE`, and zero drops |
| USB detach | Physical removal while active | Native device closes, survey pauses, timestamped gap visible | Passed physical detach/reattach recovery run; `USB_DETACH` gap closed with zero drops |
| Transfer stall | Physical/instrumented stall | Session closes, recoverable state and gap visible | Instrumented active-service run passed with `RADIO_STALL`, visible `PAUSED` warning, and zero drops; physical disconnect remains correctly classified as `USB_DETACH` |

The production service does not change sample rate, gains, bin width, or power
settings in response to pressure. It reports pressure and always leaves an
orderly Pause/Stop path. The native adapter now uses a fixed 32-buffer ring
instead of the M0 one-slot latest-buffer shortcut; overflow remains counted as
a native drop and the memory bound is explicit. The post-change physical gate
completed with zero drops and zero overruns. The debug-only active-service
hooks used for the low-storage and transfer-stall cases are guarded by the app
debuggable flag and do not exist as exported production entry points.
