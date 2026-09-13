[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'HackRF-Environment.ps1')

$firmware = Get-ChildItem -LiteralPath (Join-Path $script:RepoRoot '.tools\hackrf-release-2026.01.3') `
    -Recurse -Filter 'hackrf_one_usb.bin' -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $firmware) {
    throw 'Matching HackRF One firmware was not found. Run scripts\Install-HackRFTools.ps1.'
}

Write-Host 'Current device information:'
& hackrf_info.exe
if ($LASTEXITCODE -ne 0) {
    throw 'HackRF could not be opened. Do not attempt a firmware update until Test-HackRF.ps1 succeeds.'
}

Write-Host ''
Write-Warning 'Do not disconnect the HackRF or close this window while firmware is being written.'
$confirmation = Read-Host 'Type UPDATE to install HackRF One firmware 2026.01.3'
if ($confirmation -cne 'UPDATE') {
    Write-Host 'Firmware update cancelled.'
    exit 1
}

& hackrf_spiflash.exe -w $firmware.FullName
if ($LASTEXITCODE -ne 0) {
    throw "Firmware update failed with exit code $LASTEXITCODE."
}

Write-Host 'Firmware written. Unplug and reconnect the HackRF, then run Test-HackRF.ps1.' -ForegroundColor Green
