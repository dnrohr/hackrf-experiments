[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$evidenceRoot = Join-Path $repoRoot 'docs/evidence/M5'
$required = @(
    'completion-audit.md',
    'field-report.md',
    'adverse-conditions.md',
    'validation.md',
    'safety-privacy-receive-only.md',
    'accessibility-and-usability.md',
    'dependencies-and-licenses.md',
    'release-notes.md',
    'release-checksums.md',
    'known-limitations.md',
    'post-mvp-recommendation.md'
)
foreach ($name in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $evidenceRoot $name) -PathType Leaf)) {
        throw "Missing M5 evidence file: $name"
    }
}

$spec = Get-Content -LiteralPath (Join-Path $repoRoot 'docs/android-rf-field-notebook-spec.md') -Raw
$expectedIds = [regex]::Matches($spec, '(?:N?FR)-[A-Z]+-[0-9]{3}') |
    ForEach-Object Value | Sort-Object -Unique
$audit = Get-Content -LiteralPath (Join-Path $evidenceRoot 'completion-audit.md') -Raw
$auditIds = [regex]::Matches($audit, '(?m)^\| ((?:N?FR)-[A-Z]+-[0-9]{3}) \| Pass \|') |
    ForEach-Object { $_.Groups[1].Value }
if ($auditIds.Count -ne 64 -or ($auditIds | Sort-Object -Unique).Count -ne 64) {
    throw "Completion audit must contain 64 unique passing requirement rows; found $($auditIds.Count)."
}
$missing = $expectedIds | Where-Object { $_ -notin $auditIds }
$unexpected = $auditIds | Where-Object { $_ -notin $expectedIds }
if ($missing -or $unexpected) {
    throw "Completion audit IDs differ from specification. Missing: $missing; unexpected: $unexpected"
}

$acceptanceRows = [regex]::Matches($audit, '(?m)^\| ([1-9]|1[0-3]) \| Pass \|')
if ($acceptanceRows.Count -ne 13) {
    throw "Completion audit must contain 13 passing MVP acceptance rows; found $($acceptanceRows.Count)."
}

$allEvidence = Get-ChildItem -LiteralPath $evidenceRoot -File | ForEach-Object {
    Get-Content -LiteralPath $_.FullName -Raw
}
if (($allEvidence -join "`n") -match '(?i)\b(TBD|TODO|PLACEHOLDER)\b') {
    throw 'M5 evidence contains an unresolved placeholder.'
}

$brief = Get-Content -LiteralPath (Join-Path $repoRoot 'docs/milestones/M5-field-hardening.md') -Raw
if ($brief -notmatch '(?m)^- Status: Complete$') { throw 'M5 brief is not Complete.' }
if ([regex]::Matches($brief, '(?m)^- \[x\] ').Count -ne 13) {
    throw 'M5 brief must have exactly 13 checked acceptance rows.'
}
$roadmap = Get-Content -LiteralPath (Join-Path $repoRoot 'ROADMAP.md') -Raw
if ($roadmap -notmatch '(?m)^\| M5 \| Complete \|') { throw 'ROADMAP does not mark M5 Complete.' }

Write-Host 'M5 evidence validation passed: 64 requirements, 13 MVP criteria, and all release records are complete.' -ForegroundColor Green
