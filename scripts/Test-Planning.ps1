[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$failures = [Collections.Generic.List[string]]::new()

function Add-Failure {
    param([Parameter(Mandatory)][string]$Message)
    $script:failures.Add($Message)
}

$requiredFiles = @(
    'AGENTS.md'
    'ROADMAP.md'
    'docs/android-rf-field-notebook-spec.md'
    'docs/milestones/README.md'
    'docs/adr/README.md'
    'docs/adr/000-template.md'
)

$milestones = [ordered]@{
    M0 = 'docs/milestones/M0-technical-spikes.md'
    M1 = 'docs/milestones/M1-radio-survey-foundation.md'
    M2 = 'docs/milestones/M2-detection-fingerprinting.md'
    M3 = 'docs/milestones/M3-geographic-mapping.md'
    M4 = 'docs/milestones/M4-capture-export.md'
    M5 = 'docs/milestones/M5-field-hardening.md'
}

foreach ($relativePath in $requiredFiles + $milestones.Values) {
    $path = Join-Path $repoRoot $relativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Add-Failure "Missing required planning file: $relativePath"
    }
}

if ($failures.Count -eq 0) {
    $specPath = Join-Path $repoRoot 'docs/android-rf-field-notebook-spec.md'
    $specText = Get-Content -LiteralPath $specPath -Raw
    $requirementPattern = '(?:N?FR)-[A-Z]+-[0-9]{3}'
    $specIds = [regex]::Matches($specText, $requirementPattern) |
        ForEach-Object Value |
        Sort-Object -Unique

    if ($specIds.Count -eq 0) {
        Add-Failure 'The application specification contains no requirement IDs.'
    }

    $roadmapText = Get-Content -LiteralPath (Join-Path $repoRoot 'ROADMAP.md') -Raw
    $owners = @{}
    $requiredSections = @(
        '## Objective'
        '## Inputs'
        '## Deliverables'
        '## Tasks'
        '## Acceptance criteria'
        '## Validation'
        '## Out of scope'
        '## Handoff'
    )
    $requiredMetadata = @('Status', 'Depends on', 'Produces', 'Next milestone')

    foreach ($entry in $milestones.GetEnumerator()) {
        $milestoneId = $entry.Key
        $relativePath = $entry.Value
        $path = Join-Path $repoRoot $relativePath
        $text = Get-Content -LiteralPath $path -Raw

        if ($text -notmatch "(?m)^# $milestoneId\b") {
            Add-Failure "$relativePath does not start with the expected $milestoneId title."
        }

        foreach ($section in $requiredSections) {
            if (-not $text.Contains($section)) {
                Add-Failure "$relativePath is missing required section '$section'."
            }
        }

        foreach ($label in $requiredMetadata) {
            $escapedLabel = [regex]::Escape($label)
            if ($text -notmatch "(?m)^- ${escapedLabel}:\s+\S") {
                Add-Failure "$relativePath is missing non-empty '$label' metadata."
            }
        }

        if ($text -notmatch '(?m)^###\s+\S') {
            Add-Failure "$relativePath has no actionable task subsections."
        }
        if ($text -notmatch '(?m)^- \[ \]\s+\S') {
            Add-Failure "$relativePath has no checkable acceptance criteria."
        }

        $metadata = [regex]::Match(
            $text,
            '<!--\s*REQUIREMENTS:\s*(.*?)\s*-->',
            [Text.RegularExpressions.RegexOptions]::Singleline
        )
        if (-not $metadata.Success) {
            Add-Failure "$relativePath is missing REQUIREMENTS ownership metadata."
            continue
        }

        $claimed = [regex]::Matches($metadata.Groups[1].Value, $requirementPattern) |
            ForEach-Object Value
        $duplicateClaims = $claimed | Group-Object | Where-Object Count -gt 1
        foreach ($duplicate in $duplicateClaims) {
            Add-Failure "$relativePath claims $($duplicate.Name) more than once."
        }

        foreach ($id in ($claimed | Sort-Object -Unique)) {
            if ($id -notin $specIds) {
                Add-Failure "$relativePath claims unknown requirement $id."
                continue
            }
            if (-not $owners.ContainsKey($id)) {
                $owners[$id] = [Collections.Generic.List[string]]::new()
            }
            $owners[$id].Add($milestoneId)
        }

        $roadmapLink = $relativePath.Replace('\', '/')
        if (-not $roadmapText.Contains($roadmapLink)) {
            Add-Failure "ROADMAP.md does not link to $relativePath."
        }
    }

    foreach ($id in $specIds) {
        if (-not $owners.ContainsKey($id)) {
            Add-Failure "Requirement $id has no primary milestone owner."
        } elseif ($owners[$id].Count -ne 1) {
            Add-Failure "Requirement $id has multiple owners: $($owners[$id] -join ', ')."
        }
    }
}

if ($failures.Count -gt 0) {
    Write-Host 'Planning validation failed:' -ForegroundColor Red
    foreach ($failure in $failures) {
        Write-Host "- $failure" -ForegroundColor Red
    }
    exit 1
}

Write-Host 'Planning validation passed.' -ForegroundColor Green
Write-Host "Milestones:   $($milestones.Count)"
Write-Host "Requirements: $($specIds.Count) (exactly one primary owner each)"
exit 0
