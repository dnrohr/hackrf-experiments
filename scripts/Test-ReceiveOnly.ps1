[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $repoRoot 'android'
$failures = [Collections.Generic.List[string]]::new()

$forbidden = @(
    '(?i)hackrf_start_tx'
    '(?i)hackrf_set_txvga_gain'
    '(?i)\bstartTx\b'
    '(?i)\btransmit\s*\('
)

$files = Get-ChildItem -LiteralPath $sourceRoot -Recurse -File |
    Where-Object {
        $_.FullName -match '[\\/]src[\\/]main[\\/]' -and
        $_.Extension -in '.kt', '.java', '.h', '.hpp', '.c', '.cpp'
    }

foreach ($file in $files) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    foreach ($pattern in $forbidden) {
        if ($text -match $pattern) {
            $failures.Add("Forbidden application-facing transmit symbol in $($file.FullName): $pattern")
        }
    }
}

$nativeBuild = Get-Content -LiteralPath (Join-Path $sourceRoot 'radio-hackrf-native\src\main\cpp\CMakeLists.txt') -Raw
if ($nativeBuild -notmatch 'LIBUSB_OPTION_NO_DEVICE_DISCOVERY' -or
    $nativeBuild -notmatch 'libusb_init_context') {
    $failures.Add('Android libusb initialization must disable device discovery before wrapping the permitted USB descriptor.')
}

$mainActivity = Get-Content -LiteralPath (Join-Path $sourceRoot 'app-ui\src\main\kotlin\dev\rfnotebook\app\MainActivity.kt') -Raw
if ($mainActivity -notmatch 'UsbManager\.ACTION_USB_DEVICE_ATTACHED' -or
    $mainActivity -notmatch 'UsbManager\.ACTION_USB_DEVICE_DETACHED') {
    $failures.Add('The running app must observe USB attach and detach broadcasts so a HackRF can be reopened without restarting the process.')
}
if ($mainActivity -notmatch '(?s)registerReceiver\(this, usbTopologyReceiver, usbTopologyFilter, ContextCompat\.RECEIVER_EXPORTED\)') {
    $failures.Add('System USB topology broadcasts must use a dedicated exported receiver; the app-private permission result remains non-exported.')
}

$mapPrototype = Get-Content -LiteralPath (Join-Path $sourceRoot 'maps\src\main\kotlin\dev\rfnotebook\maps\MapPrototypeView.kt') -Raw
if ($mapPrototype -match 'demotiles\.maplibre\.org/style\.json') {
    $failures.Add('The Android offline-region prototype must not use the MapLibre demo glyph URL that aborts the native offline downloader.')
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Host "- $_" -ForegroundColor Red }
    exit 1
}

Write-Host "Receive-only static check passed ($($files.Count) source files)." -ForegroundColor Green
