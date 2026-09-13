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

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Host "- $_" -ForegroundColor Red }
    exit 1
}

Write-Host "Receive-only static check passed ($($files.Count) source files)." -ForegroundColor Green
