[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'HackRF-Environment.ps1')

$stateRoot = Join-Path $script:RepoRoot '.state'
$captureRoot = Join-Path $script:RepoRoot 'captures'
New-Item -ItemType Directory -Force -Path $stateRoot, $captureRoot | Out-Null

$env:XDG_CONFIG_HOME = $stateRoot
$env:XDG_CACHE_HOME = Join-Path $stateRoot 'cache'

Start-Process -FilePath (Join-Path $script:RadioCondaRoot 'Library\bin\gqrx.exe') `
    -WorkingDirectory $captureRoot
