[CmdletBinding()]
param(
    [string]$OutputDirectory = '.state/releases/1.0.0-rc1',
    [string]$Keystore,
    [string]$StorePasswordFile,
    [string]$KeyAlias = 'rf-field-notebook',
    [string]$SourceRevision = 'HEAD',
    [switch]$SkipEvidenceGate
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $repoRoot 'android'
$outputRoot = Join-Path $repoRoot $OutputDirectory

Push-Location $androidRoot
try {
    & .\gradlew.bat clean lint test assembleDebug assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Clean Android release build failed.' }
} finally { Pop-Location }

foreach ($check in @('Test-ReceiveOnly.ps1', 'Test-NativeExports.ps1', 'Test-M4Schemas.ps1', 'Test-M5Dependencies.ps1', 'Test-M5Release.ps1', 'Test-Planning.ps1')) {
    & (Join-Path $PSScriptRoot $check)
}

New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$generatedNames = @(
    'rf-field-notebook-1.0.0-rc1.apk',
    'rf-field-notebook-1.0.0-rc1-unsigned.apk',
    'rf-field-notebook-1.0.0-rc1-source.zip',
    'SOURCE_REVISION',
    'THIRD_PARTY_NOTICES.md',
    'NATIVE_THIRD_PARTY_NOTICES.md',
    'SHA256SUMS'
)
foreach ($name in $generatedNames) {
    Remove-Item -LiteralPath (Join-Path $outputRoot $name) -Force -ErrorAction SilentlyContinue
}
$unsigned = Join-Path $androidRoot 'app-ui/build/outputs/apk/release/app-ui-release-unsigned.apk'
$artifact = $null

if ($Keystore -or $StorePasswordFile) {
    if (-not $Keystore -or -not $StorePasswordFile) {
        throw 'Provide both -Keystore and -StorePasswordFile to sign the release.'
    }
    $localProperties = Join-Path $androidRoot 'local.properties'
    $sdkLine = Get-Content -LiteralPath $localProperties |
        Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    $sdkRoot = ($sdkLine -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\'
    $apksigner = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Recurse -Filter 'apksigner.bat' -File |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    if (-not $apksigner) { throw 'apksigner.bat not found.' }
    $signed = Join-Path $outputRoot 'rf-field-notebook-1.0.0-rc1.apk'
    & $apksigner sign --ks $Keystore --ks-key-alias $KeyAlias --ks-pass "file:$StorePasswordFile" --out $signed $unsigned
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
    & $apksigner verify --verbose --print-certs $signed
    if ($LASTEXITCODE -ne 0) { throw 'Signed APK verification failed.' }
    $artifact = $signed
} else {
    $artifact = Join-Path $outputRoot 'rf-field-notebook-1.0.0-rc1-unsigned.apk'
    Copy-Item -LiteralPath $unsigned -Destination $artifact
}

Copy-Item -LiteralPath (Join-Path $repoRoot 'android/app-ui/src/main/assets/THIRD_PARTY_NOTICES.md') -Destination $outputRoot -Force
Copy-Item -LiteralPath (Join-Path $repoRoot 'android/radio-hackrf-native/THIRD_PARTY_NOTICES.md') -Destination (Join-Path $outputRoot 'NATIVE_THIRD_PARTY_NOTICES.md') -Force
$sourceArchive = Join-Path $outputRoot 'rf-field-notebook-1.0.0-rc1-source.zip'
$sourceCommit = & git -C $repoRoot rev-parse --verify "$SourceRevision^{commit}"
if ($LASTEXITCODE -ne 0 -or -not $sourceCommit) {
    throw "Source revision does not resolve to a commit: $SourceRevision"
}
$sourceCommit = ($sourceCommit | Select-Object -Last 1).Trim()
if ($sourceCommit -notmatch '^[0-9a-f]{40}$') { throw "Unexpected source revision: $sourceCommit" }
& git -C $repoRoot archive --format=zip --output=$sourceArchive $sourceCommit
if ($LASTEXITCODE -ne 0) { throw 'Source archive creation failed.' }
$sourceRevisionFile = Join-Path $outputRoot 'SOURCE_REVISION'
$sourceCommit | Set-Content -LiteralPath $sourceRevisionFile -Encoding ascii -NoNewline

function Get-PinnedReleaseFile {
    param([string]$Uri, [string]$Destination, [string]$ExpectedSha256)
    if (-not (Test-Path -LiteralPath $Destination -PathType Leaf)) {
        $partial = "$Destination.part"
        Remove-Item -LiteralPath $partial -Force -ErrorAction SilentlyContinue
        try {
            Invoke-WebRequest -Uri $Uri -OutFile $partial
            Move-Item -LiteralPath $partial -Destination $Destination
        } finally {
            Remove-Item -LiteralPath $partial -Force -ErrorAction SilentlyContinue
        }
    }
    $actual = (Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash
    if ($actual -ne $ExpectedSha256) {
        throw "Pinned source checksum mismatch for $(Split-Path -Leaf $Destination): $actual"
    }
    return $Destination
}

$libusbSource = Get-PinnedReleaseFile `
    -Uri 'https://codeload.github.com/libusb/libusb/zip/15a7ebb4d426c5ce196684347d2b7cafad862626' `
    -Destination (Join-Path $outputRoot 'libusb-1.0.29-source.zip') `
    -ExpectedSha256 '3415F390DF8D841F275C14F4B3B5CA5A4FCA893D36BA1D845538E4CAA54C2F30'
$hackrfSource = Get-PinnedReleaseFile `
    -Uri 'https://codeload.github.com/greatscottgadgets/hackrf/zip/01f7c8b3509e308c5e17b77a8ed2dbb594a98860' `
    -Destination (Join-Path $outputRoot 'hackrf-2026.01.3-source.zip') `
    -ExpectedSha256 '33B90BCC62C5798F576AF64139C23553EB74FD65AEE60A467C63152B1982B976'
$mapLibreLicenses = Get-PinnedReleaseFile `
    -Uri 'https://raw.githubusercontent.com/maplibre/maplibre-native/android-v13.6.1/platform/android/LICENSE.md' `
    -Destination (Join-Path $outputRoot 'maplibre-android-13.6.1-LICENSE.md') `
    -ExpectedSha256 'DB3CC41E2C79F394A1DDDD890C55C263426175029A898D5167820498DDEBF152'

$releaseFiles = @(
    $artifact,
    $sourceArchive,
    $sourceRevisionFile,
    $libusbSource,
    $hackrfSource,
    $mapLibreLicenses,
    (Join-Path $outputRoot 'THIRD_PARTY_NOTICES.md'),
    (Join-Path $outputRoot 'NATIVE_THIRD_PARTY_NOTICES.md')
)
$checksumLines = $releaseFiles | ForEach-Object {
    $item = Get-Item -LiteralPath $_
    "{0}  {1}" -f (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $item.Name
}
$checksumLines | Set-Content -LiteralPath (Join-Path $outputRoot 'SHA256SUMS') -Encoding ascii

# Run the repository-evidence gate after the complete release set exists. The
# checked-in evidence names this set and its verification procedure.
if ($SkipEvidenceGate) {
    Write-Warning 'M5 evidence gate was explicitly skipped; this release set is not final until Test-M5Evidence.ps1 passes.'
} else {
    & (Join-Path $PSScriptRoot 'Test-M5Evidence.ps1')
}

Write-Host "Release set: $outputRoot" -ForegroundColor Green
$checksumLines | Write-Host
