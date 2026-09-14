[CmdletBinding()]
param(
    [ValidateRange(1, 240)]
    [int] $DurationMinutes = 30,

    [ValidateRange(5, 300)]
    [int] $IntervalSeconds = 10,

    [string] $OutputDirectory = ".state/M1-hardware"
)

$ErrorActionPreference = "Stop"

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path -LiteralPath $adb)) {
    throw "Android Debug Bridge was not found at $adb"
}

$deviceLines = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" })
if ($deviceLines.Count -ne 1) {
    throw "Expected exactly one authorized Android device, found $($deviceLines.Count)."
}

$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\$OutputDirectory"))
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null

$samplesPath = Join-Path $resolvedOutput "survey-monitor.csv"
$metadataPath = Join-Path $resolvedOutput "device-metadata.txt"
$startEpochMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$endAt = [DateTimeOffset]::UtcNow.AddMinutes($DurationMinutes)

@(
    "CapturedUtc=$([DateTimeOffset]::UtcNow.ToString('o'))"
    "PhoneModel=$(& $adb shell getprop ro.product.model)"
    "PhoneDevice=$(& $adb shell getprop ro.product.device)"
    "AndroidRelease=$(& $adb shell getprop ro.build.version.release)"
    "AndroidSdk=$(& $adb shell getprop ro.build.version.sdk)"
    "AppPackage=dev.rfnotebook"
    "DurationMinutes=$DurationMinutes"
    "IntervalSeconds=$IntervalSeconds"
) | Set-Content -LiteralPath $metadataPath

"utc,elapsed_seconds,interactive,wakefulness,battery_percent,thermal_status,service_present" |
    Set-Content -LiteralPath $samplesPath

while ([DateTimeOffset]::UtcNow -lt $endAt) {
    $now = [DateTimeOffset]::UtcNow
    $power = (& $adb shell dumpsys power) -join "`n"
    $battery = (& $adb shell dumpsys battery) -join "`n"
    $thermal = (& $adb shell dumpsys thermalservice) -join "`n"
    $services = (& $adb shell dumpsys activity services dev.rfnotebook) -join "`n"

    $interactive = if ($power -match "mInteractive=(true|false)") { $Matches[1] } else { "unknown" }
    $wakefulness = if ($power -match "mWakefulness=([^\r\n]+)") { $Matches[1].Trim() } else { "unknown" }
    $batteryPercent = if ($battery -match "(?m)^\s*level:\s*(\d+)") { $Matches[1] } else { "unknown" }
    $thermalStatus = if ($thermal -match "(?m)^Thermal Status: (\d+)") { $Matches[1] } else { "unknown" }
    $servicePresent = [bool]($services -match "SurveyAcquisitionService")
    $elapsedSeconds = [math]::Floor(($now.ToUnixTimeMilliseconds() - $startEpochMs) / 1000)

    '"{0}",{1},{2},"{3}",{4},{5},{6}' -f `
        $now.ToString("o"), $elapsedSeconds, $interactive, $wakefulness,
        $batteryPercent, $thermalStatus, $servicePresent.ToString().ToLowerInvariant() |
        Add-Content -LiteralPath $samplesPath

    Start-Sleep -Seconds $IntervalSeconds
}

& $adb exec-out screencap -p > (Join-Path $resolvedOutput "final-screen.png")
& $adb shell uiautomator dump /sdcard/m1-final-ui.xml | Out-Null
& $adb pull /sdcard/m1-final-ui.xml (Join-Path $resolvedOutput "final-ui.xml") | Out-Null
& $adb shell rm /sdcard/m1-final-ui.xml

Write-Host "M1 monitor evidence saved privately under $resolvedOutput"
Write-Host "Review and redact artifacts before copying any evidence into docs/evidence/M1."
