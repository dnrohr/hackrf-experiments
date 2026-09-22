param([Parameter(Mandatory=$true)][string]$Sidecar, [Parameter(Mandatory=$true)][string]$Iq)
$ErrorActionPreference = 'Stop'
$metadata = Get-Content -Raw -LiteralPath $Sidecar | ConvertFrom-Json
$file = Get-Item -LiteralPath $Iq
if ($metadata.sampleFormat -ne 'signed-int8-interleaved-iq') { throw 'Unexpected sample encoding' }
if ($metadata.componentOrder -ne 'I,Q') { throw 'Unexpected component order' }
if ($file.Length -ne $metadata.actualByteCount) { throw 'IQ length does not match sidecar' }
if ($file.Length -ne (2 * $metadata.complexSampleCount)) { throw 'IQ length is not two signed bytes per complex sample' }
$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Iq).Hash.ToLowerInvariant()
if ($hash -ne $metadata.sha256) { throw 'IQ SHA-256 does not match sidecar' }
"Validated $($metadata.complexSampleCount) complex signed int8 samples at $($metadata.sampleRateHz) Hz centered on $($metadata.centerFrequencyHz) Hz"
