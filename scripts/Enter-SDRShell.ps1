[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'HackRF-Environment.ps1')

Write-Host 'HackRF/Radioconda environment loaded.' -ForegroundColor Green
Write-Host 'Try: hackrf_info, hackrf_sweep, hackrf_transfer, gqrx, or gnuradio-companion'
Set-Location $script:RepoRoot
powershell.exe -NoExit -NoLogo -Command "`$env:PATH = '$env:PATH'; `$env:CONDA_PREFIX = '$env:CONDA_PREFIX'; `$env:CONDA_DEFAULT_ENV = 'radioconda'; Set-Location '$script:RepoRoot'"
