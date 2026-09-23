[CmdletBinding()]
param(
    [string]$ApkPath = 'android/app-ui/build/outputs/apk/release/app-ui-release-unsigned.apk',
    [string]$DebugApkPath = 'android/app-ui/build/outputs/apk/debug/app-ui-debug.apk'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$resolvedApk = Join-Path $repoRoot $ApkPath
if (-not (Test-Path -LiteralPath $resolvedApk -PathType Leaf)) {
    throw "Release APK not found: $resolvedApk. Run android/gradlew.bat assembleRelease first."
}
$resolvedDebugApk = Join-Path $repoRoot $DebugApkPath
if (-not (Test-Path -LiteralPath $resolvedDebugApk -PathType Leaf)) {
    throw "Debug APK not found: $resolvedDebugApk. Run android/gradlew.bat assembleDebug first."
}

$localProperties = Join-Path $repoRoot 'android/local.properties'
$sdkRoot = $env:ANDROID_SDK_ROOT
if (-not $sdkRoot -and (Test-Path -LiteralPath $localProperties)) {
    $sdkLine = Get-Content -LiteralPath $localProperties |
        Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if ($sdkLine) {
        $sdkRoot = ($sdkLine -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\'
    }
}
if (-not $sdkRoot) { throw 'Android SDK not found.' }

$aapt = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Recurse -Filter 'aapt.exe' -File |
    Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
if (-not $aapt) { throw 'aapt.exe not found in the Android SDK.' }

$badging = (& $aapt dump badging $resolvedApk) -join "`n"
if ($badging -notmatch "package: name='dev\.rfnotebook' versionCode='5' versionName='1\.0\.0-rc1'") {
    throw 'Release package identity/version does not match the M5 release candidate.'
}
if ($badging -notmatch "native-code: 'arm64-v8a' 'x86_64'") {
    throw 'Release must contain exactly the ADR 002 arm64-v8a and x86_64 ABIs.'
}
$debugBadging = (& $aapt dump badging $resolvedDebugApk) -join "`n"
if ($debugBadging -notmatch "package: name='dev\.rfnotebook\.debug'.*versionName='1\.0\.0-rc1-debug'") {
    throw 'Debug/instrumentation package must remain isolated from the release notebook identity.'
}

$manifest = (& $aapt dump xmltree $resolvedApk AndroidManifest.xml) -join "`n"
foreach ($required in @(
    'android:allowBackup.*0x0',
    'android:usesCleartextTraffic.*0x0'
)) {
    if ($manifest -notmatch $required) { throw "Missing release manifest hardening: $required" }
}
if ($manifest -match 'android:debuggable.*0xffffffff') {
    throw 'Release manifest is debuggable.'
}

$permissions = (& $aapt dump permissions $resolvedApk) -join "`n"
foreach ($forbidden in @('CAMERA', 'RECORD_AUDIO', 'BLUETOOTH', 'READ_CONTACTS', 'WRITE_EXTERNAL_STORAGE')) {
    if ($permissions -match "android\.permission\.$forbidden") {
        throw "Unexpected sensitive permission in release: $forbidden"
    }
}

$sizeMiB = [Math]::Round((Get-Item -LiteralPath $resolvedApk).Length / 1MB, 2)
if ($sizeMiB -gt 75) { throw "Release APK is unexpectedly large: $sizeMiB MiB" }

$checksum = (Get-FileHash -LiteralPath $resolvedApk -Algorithm SHA256).Hash
Write-Host 'M5 release audit passed.' -ForegroundColor Green
Write-Host "Artifact: $resolvedApk"
Write-Host "Size:     $sizeMiB MiB"
Write-Host "SHA-256:  $checksum"
