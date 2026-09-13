[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $repoRoot 'android'
$nativeBuildRoot = Join-Path $androidRoot 'radio-hackrf-native/build/intermediates/stripped_native_libs/debug'

$ndkRoot = $env:ANDROID_NDK_ROOT
if (-not $ndkRoot) {
    $localProperties = Join-Path $androidRoot 'local.properties'
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match '^sdk\.dir=' } |
            Select-Object -First 1
        if ($sdkLine) {
            $sdkRoot = ($sdkLine -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\'
            $ndkParent = Join-Path $sdkRoot 'ndk'
            if (Test-Path -LiteralPath $ndkParent) {
                $ndkRoot = Get-ChildItem -LiteralPath $ndkParent -Directory |
                    Sort-Object Name -Descending |
                    Select-Object -First 1 -ExpandProperty FullName
            }
        }
    }
}

if (-not $ndkRoot -and $env:ANDROID_HOME) {
    $ndkRoot = Get-ChildItem -LiteralPath (Join-Path $env:ANDROID_HOME 'ndk') -Directory |
        Sort-Object Name -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $ndkRoot) {
    throw 'Android NDK not found. Set ANDROID_NDK_ROOT or configure android/local.properties.'
}

$nm = Get-ChildItem -LiteralPath (Join-Path $ndkRoot 'toolchains/llvm/prebuilt') -Recurse -File |
    Where-Object { $_.Name -in 'llvm-nm', 'llvm-nm.exe' } |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $nm) { throw "llvm-nm not found below $ndkRoot" }

$libraries = Get-ChildItem -LiteralPath $nativeBuildRoot -Recurse -Filter 'librfnotebook_radio.so' -File
if ($libraries.Count -ne 2) {
    throw "Expected two stripped ABI libraries below $nativeBuildRoot; found $($libraries.Count). Build assembleDebug first."
}

$expected = @(
    'Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativeClose'
    'Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativeDeviceInfo'
    'Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativeInitialize'
    'Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativeOpen'
    'Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativePollBuffer'
    'Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativeStartRx'
    'Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativeStartSweep'
    'Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativeStats'
    'Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativeStop'
)

foreach ($library in $libraries) {
    $symbols = & $nm -D --defined-only $library.FullName |
        ForEach-Object { (($_ -split '\s+')[-1] -split '@@')[0] } |
        Where-Object { $_ -and $_ -ne 'RFNOTEBOOK_RADIO' } |
        Sort-Object -Unique
    $missing = $expected | Where-Object { $_ -notin $symbols }
    $unexpected = $symbols | Where-Object { $_ -notin $expected }
    if ($missing -or $unexpected) {
        if ($missing) { Write-Host "Missing exports in $($library.FullName): $($missing -join ', ')" -ForegroundColor Red }
        if ($unexpected) { Write-Host "Unexpected exports in $($library.FullName): $($unexpected -join ', ')" -ForegroundColor Red }
        exit 1
    }
}

Write-Host 'Native export check passed: only the nine receive-only JNI functions are visible for both ABIs.' -ForegroundColor Green
