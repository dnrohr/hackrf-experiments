[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'HackRF-Environment.ps1')

Start-Process -FilePath (Join-Path $script:RadioCondaRoot 'Library\bin\gnuradio-companion.exe') `
    -WorkingDirectory $script:RepoRoot
