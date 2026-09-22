# M4 schema and interoperability

Schema version `1.0.0` is defined by:

- `schemas/capture-sidecar-1.0.0.schema.json`
- `schemas/survey-manifest-1.0.0.schema.json`

Committed full, minimal/redacted, and invalid examples live in
`schemas/examples/`. `scripts/Test-M4Schemas.ps1` uses Radioconda's Draft 2020-12
validator; valid examples pass and invalid examples are required to fail.

IQ interpretation is signed 8-bit, interleaved `I,Q`, one byte per component,
so byte order is not applicable. Frequencies and sample rates are integer Hz,
wall time is Unix epoch milliseconds, monotonic time is nanoseconds, and
coordinates (when included) are WGS84 / EPSG:4326 degrees with accuracy metres.

For GNU Radio, use a File Source with byte/char items, deinterleave consecutive
I and Q bytes, convert each signed char to float, and form a complex stream. The
captured sample rate and center frequency come from the sidecar. Before opening:

```powershell
.\scripts\Test-M4Capture.ps1 -Sidecar capture.json -Iq capture.cs8
```

The 2026-09-21 Pixel 8a archive passed this check and a Radioconda Python import
of `gnuradio` plus signed-int8 complex inspection.
