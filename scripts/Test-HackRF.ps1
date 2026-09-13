[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'HackRF-Environment.ps1')

$device = Get-PnpDevice -PresentOnly -ErrorAction SilentlyContinue |
    Where-Object {
        $_.FriendlyName -match 'HackRF|LPC4330|Great Scott' -or
        $_.InstanceId -match 'VID_1D50&PID_6089|VID_1FC9&PID_000C'
    } |
    Select-Object -First 1

if (-not $device) {
    Write-Host @'
Windows cannot currently see a HackRF. Connect it directly to a USB port with a data-capable cable, then run this script again.
'@ -ForegroundColor Red
    exit 2
}

Write-Host "Windows device: $($device.FriendlyName)"
Write-Host "PnP status:     $($device.Status)"
Write-Host "Instance ID:    $($device.InstanceId)"
Write-Host ''

$output = & hackrf_info.exe 2>&1
$exitCode = $LASTEXITCODE
$output | Write-Host

if ($exitCode -ne 0) {
    Write-Host @'
The USB device is present, but HackRF Tools could not open it. On Windows this usually means the WinUSB driver is missing. Install WinUSB for the HackRF device with Zadig, then reconnect it.
'@ -ForegroundColor Red
    exit $exitCode
}

Write-Host ''
Write-Host 'HackRF is ready.' -ForegroundColor Green
