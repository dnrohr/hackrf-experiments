[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$zadig = Get-ChildItem -LiteralPath (Join-Path $repoRoot '.tools\zadig') `
    -Filter 'zadig*.exe' -File -ErrorAction SilentlyContinue |
    Select-Object -First 1

if (-not $zadig) {
    throw 'Zadig is not installed. Re-run the project setup.'
}

# Zadig is an interactive driver installer and may request administrator access.
Start-Process -FilePath $zadig.FullName
