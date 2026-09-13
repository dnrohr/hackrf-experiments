$script:RepoRoot = Split-Path -Parent $PSScriptRoot
$script:RadioCondaRoot = Join-Path $script:RepoRoot '.tools\radioconda'

if (-not (Test-Path -LiteralPath $script:RadioCondaRoot)) {
    throw "Radioconda is not installed at $script:RadioCondaRoot. Run scripts\Install-HackRFTools.ps1 first."
}

$pathEntries = @(
    $script:RadioCondaRoot
    (Join-Path $script:RadioCondaRoot 'Scripts')
    (Join-Path $script:RadioCondaRoot 'Library\bin')
    (Join-Path $script:RadioCondaRoot 'Library\usr\bin')
    (Join-Path $script:RadioCondaRoot 'Library\mingw-w64\bin')
)

$env:CONDA_PREFIX = $script:RadioCondaRoot
$env:CONDA_DEFAULT_ENV = 'radioconda'
$env:PATH = (($pathEntries + $env:PATH) -join [IO.Path]::PathSeparator)
