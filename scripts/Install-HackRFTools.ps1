[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$installRoot = Join-Path $repoRoot '.tools\radioconda'
$zadigRoot = Join-Path $repoRoot '.tools\zadig'
$hackrfVersion = '2026.01.3'
$releaseRoot = Join-Path $repoRoot ".tools\hackrf-release-$hackrfVersion"
$downloadRoot = Join-Path $repoRoot '.tools\downloads'
$releaseArchive = Join-Path $downloadRoot "hackrf-$hackrfVersion.zip"
$releaseUrl = "https://github.com/greatscottgadgets/hackrf/releases/download/v$hackrfVersion/hackrf-$hackrfVersion.zip"
$releaseSha256 = '601599e14d03cc1900cf9a1c832957a997dc0d488f8299581bd73b40718481a5'

if (-not (Get-Command winget.exe -ErrorAction SilentlyContinue)) {
    throw 'Windows Package Manager (winget) is required.'
}

if (-not (Test-Path -LiteralPath (Join-Path $installRoot 'Library\bin\hackrf_info.exe'))) {
    winget install `
        --id ryanvolz.radioconda `
        --exact `
        --version 2025.03.14 `
        --location $installRoot `
        --silent `
        --force `
        --accept-package-agreements `
        --accept-source-agreements `
        --disable-interactivity

    if ($LASTEXITCODE -ne 0) {
        throw "Radioconda installation failed with exit code $LASTEXITCODE."
    }
}

$installedHackRF = Get-ChildItem -LiteralPath (Join-Path $installRoot 'conda-meta') `
    -Filter "hackrf-$hackrfVersion-*.json" -ErrorAction SilentlyContinue
if (-not $installedHackRF) {
    $mamba = Join-Path $installRoot 'Scripts\mamba.exe'
    & $mamba install -p $installRoot -c conda-forge -c ryanvolz `
        --override-channels --yes "hackrf=$hackrfVersion"
    if ($LASTEXITCODE -ne 0) {
        throw "HackRF Tools update failed with exit code $LASTEXITCODE."
    }
}

if (-not (Get-ChildItem -LiteralPath $zadigRoot -Filter 'zadig*.exe' -ErrorAction SilentlyContinue)) {
    winget install `
        --id akeo.ie.Zadig `
        --exact `
        --location $zadigRoot `
        --silent `
        --force `
        --accept-package-agreements `
        --accept-source-agreements `
        --disable-interactivity

    if ($LASTEXITCODE -ne 0) {
        throw "Zadig installation failed with exit code $LASTEXITCODE."
    }
}

$firmware = Get-ChildItem -LiteralPath $releaseRoot -Recurse `
    -Filter 'hackrf_one_usb.bin' -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $firmware) {
    New-Item -ItemType Directory -Force -Path $downloadRoot | Out-Null
    Invoke-WebRequest -Uri $releaseUrl -OutFile $releaseArchive
    $actualHash = (Get-FileHash -LiteralPath $releaseArchive -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $releaseSha256) {
        throw "HackRF release archive failed its SHA-256 check: $actualHash"
    }
    Expand-Archive -LiteralPath $releaseArchive -DestinationPath $releaseRoot -Force
}

Write-Host "HackRF Tools ${hackrfVersion}: $installRoot"
Write-Host "Zadig:                    $zadigRoot"
Write-Host "Matching firmware files:  $releaseRoot"
exit 0
