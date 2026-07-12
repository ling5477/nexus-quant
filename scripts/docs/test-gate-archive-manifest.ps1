<#
.SYNOPSIS
Regresses strict manifest roles, links, and archive task evidence in a disposable repository.
.NOTES
The test does not access real archives, tags, remotes, or current authority.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$manifestPath = Join-Path $PSScriptRoot 'gate-archive-manifest.json'
$contractPath = Join-Path $PSScriptRoot 'governance-workflow-contract.json'
$checkerPath = Join-Path $PSScriptRoot 'check-gate-archive.ps1'
$helperPath = Join-Path $PSScriptRoot 'governance-workflow-lib.ps1'
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("nq-gate-archive-manifest-" + [guid]::NewGuid().ToString('N'))
$fixtureRoot = Join-Path $tempRoot 'fixture-repo'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Assert-Condition { param([bool]$Condition,[string]$Message); if (-not $Condition) { throw "ASSERTION_FAILED $Message" } }
function Write-Utf8File {
    param([string]$Path,[string]$Content)
    [System.IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}
function Get-EvidenceBody {
    param([string]$Name)
    return @(
        "# $Name", '',
        'This disposable archive fixture does not represent a real Gate release.',
        'It provides an independent body for strict role and unknown-file regression.',
        'The fixture reads no credentials, accesses no network, and executes no trade.',
        'All content exists only in the system temporary directory.',
        'README links, role uniqueness, and non-role evidence are tested separately.',
        'The body has enough non-empty lines and characters for manifest policy.',
        'Every invalid fixture must fail closed with a nonzero exit code.'
    ) -join "`n"
}
function Get-RoleFiles {
    return @(
        'README.md','GATEV_FREEZE_CLOSEOUT.md','GATEV_FREEZE_READINESS_REVIEW.md',
        'GATEV_IMPLEMENTATION_BASELINE.md','GATEV_BATCH_1_4_EVIDENCE_MATRIX.md',
        'GATEV_TESTING_EVIDENCE_SUMMARY.md','GATEV_BACKEND_DB_MIGRATION_EVIDENCE_SUMMARY.md',
        'GATEV_API_EVIDENCE_SUMMARY.md','GATEV_FRONTEND_EVIDENCE_SUMMARY.md',
        'GATEV_RUNTIME_SCHEDULING_BOUNDARY_SUMMARY.md','GATEV_BOUNDARY_STATEMENT.md',
        'GATEV_KNOWN_LIMITATIONS_AND_RESIDUALS.md'
    )
}
function New-ArchiveFixture {
    param([string]$Gate)
    $root = Join-Path $fixtureRoot "docs/gates/$Gate"
    foreach ($file in Get-RoleFiles) { Write-Utf8File (Join-Path $root $file) (Get-EvidenceBody $file) }
}
function Invoke-Checker {
    param([string]$Gate,[string[]]$Arguments=@())
    $previous=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $output=& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $fixtureRoot 'scripts/docs/check-gate-archive.ps1') -Gate $Gate @Arguments 2>&1
    $exit=$LASTEXITCODE; $ErrorActionPreference=$previous
    return [pscustomobject]@{ExitCode=$exit;Text=(($output|ForEach-Object{$_.ToString()})-join "`n")}
}
function Assert-Checker {
    param([object]$Result,[bool]$Pass,[string]$Expected,[string]$Scenario)
    if ($Pass -and $Result.ExitCode -ne 0) { throw "CHECKER_UNEXPECTED_FAILURE scenario=$Scenario output=$($Result.Text)" }
    if (-not $Pass -and $Result.ExitCode -eq 0) { throw "CHECKER_UNEXPECTED_SUCCESS scenario=$Scenario output=$($Result.Text)" }
    if ($Result.Text -notmatch [regex]::Escape($Expected)) { throw "CHECKER_OUTPUT_MISMATCH scenario=$Scenario expected=$Expected output=$($Result.Text)" }
    Write-Output "PASS fixture=$Scenario exit=$($Result.ExitCode) expected=$Expected"
}

try {
    $manifest = Get-Content -Raw $manifestPath | ConvertFrom-Json
    $gateU = $manifest.strictGateOverrides.'gate-u'
    $gateV = $manifest.strictGateOverrides.'gate-v'
    Assert-Condition ($manifest.legacyThroughGate -eq 'gate-t') 'legacyThroughGate changed'
    Assert-Condition ($gateU.expectedTag -eq 'nq-gateu-freeze') 'GateU tag changed'
    Assert-Condition ($gateU.expectedTagTarget -eq '48ef0cdaa97099ae1ff5a66a8c0caeb07aa11fab') 'GateU target changed'
    Assert-Condition ($gateV.expectedTag -eq 'nq-gatev-freeze') 'GateV tag changed'

    $scripts = Join-Path $fixtureRoot 'scripts/docs'
    New-Item -ItemType Directory -Path $scripts -Force | Out-Null
    Copy-Item $manifestPath,$contractPath,$checkerPath,$helperPath -Destination $scripts -Force
    New-ArchiveFixture 'gate-v'
    New-ArchiveFixture 'gate-w'

    Assert-Checker (Invoke-Checker 'gate-v' @('-PreTag')) $true 'PASS / GATE_ARCHIVE_PRETAG_VALID' 'pretag-structure-positive'
    Assert-Checker (Invoke-Checker 'gate-v') $true 'PASS / ARCHIVE_MANIFEST_COMPLETE' 'posttag-structure-positive'
    Assert-Checker (Invoke-Checker 'gate-w' @('-PreTag')) $false 'STRICT_GATE_OVERRIDE_MISSING gate=gate-w' 'strict-override-missing'

    $gateRoot = Join-Path $fixtureRoot 'docs/gates/gate-v'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'GATEV_FREEZE_CLOSEOUT.md') -Force
    Assert-Checker (Invoke-Checker 'gate-v' @('-PreTag')) $false 'ROLE_MISSING role=freeze-closeout gate=gate-v' 'missing-role'
    Write-Utf8File (Join-Path $gateRoot 'GATEV_FREEZE_CLOSEOUT.md') (Get-EvidenceBody 'GATEV_FREEZE_CLOSEOUT.md')

    Write-Utf8File (Join-Path $gateRoot 'GATEV_API_SECOND_EVIDENCE.md') (Get-EvidenceBody 'duplicate api')
    Assert-Checker (Invoke-Checker 'gate-v' @('-PreTag')) $false 'DUPLICATE_ROLE role=api-evidence' 'duplicate-role'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'GATEV_API_SECOND_EVIDENCE.md') -Force

    Write-Utf8File (Join-Path $gateRoot 'GATEV_RELEASE_NOTES.md') (Get-EvidenceBody 'unknown')
    Assert-Checker (Invoke-Checker 'gate-v' @('-PreTag')) $false 'UNKNOWN_ARCHIVE_FILE file=GATEV_RELEASE_NOTES.md' 'unknown-file'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'GATEV_RELEASE_NOTES.md') -Force

    Write-Utf8File (Join-Path $gateRoot 'README.md') ((Get-EvidenceBody 'README') + "`n[broken](missing.md)")
    Assert-Checker (Invoke-Checker 'gate-v' @('-PreTag')) $false 'README_LINK_BROKEN' 'broken-readme-link'
    Write-Utf8File (Join-Path $gateRoot 'README.md') (Get-EvidenceBody 'README.md')

    Write-Utf8File (Join-Path $gateRoot 'source/task-evidence/README.md') "# Task evidence index`n`nThis index links attempts and is never an archive role."
    Write-Utf8File (Join-Path $gateRoot 'source/task-evidence/NQ-GATEV-FIXTURE.attempt-01.md') (Get-EvidenceBody 'task attempt')
    $positive = Invoke-Checker 'gate-v' @('-PreTag')
    Assert-Checker $positive $true 'EVIDENCE file=source/task-evidence/README.md role=non-role valid=True' 'nested-readme-non-role'
    Assert-Condition ($positive.Text -match 'ROLE role=archive-entry files=README.md') 'top-level README must remain sole archive-entry'
    Write-Output 'PASS fixture=evidence-not-counted-as-role-or-unknown'

    Write-Utf8File (Join-Path $gateRoot 'source/task-evidence/EMPTY.attempt-01.md') ''
    Assert-Checker (Invoke-Checker 'gate-v' @('-PreTag')) $false 'TASK_EVIDENCE_INVALID file=source/task-evidence/EMPTY.attempt-01.md' 'empty-evidence'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'source/task-evidence/EMPTY.attempt-01.md') -Force

    Write-Utf8File (Join-Path $gateRoot 'source/task-evidence/WHITESPACE.attempt-01.md') " `r`n`t "
    Assert-Checker (Invoke-Checker 'gate-v' @('-PreTag')) $false 'TASK_EVIDENCE_INVALID file=source/task-evidence/WHITESPACE.attempt-01.md' 'whitespace-evidence'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'source/task-evidence/WHITESPACE.attempt-01.md') -Force

    Write-Utf8File (Join-Path $gateRoot 'source/task-evidence/PLACEHOLDER.attempt-01.md') '# TODO'
    Assert-Checker (Invoke-Checker 'gate-v' @('-PreTag')) $false 'TASK_EVIDENCE_INVALID file=source/task-evidence/PLACEHOLDER.attempt-01.md' 'placeholder-evidence'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'source/task-evidence/PLACEHOLDER.attempt-01.md') -Force

    Write-Utf8File (Join-Path $gateRoot 'source/task-evidence/invalid.md') 'invalid name'
    Assert-Checker (Invoke-Checker 'gate-v' @('-PreTag')) $false 'TASK_EVIDENCE_INVALID file=source/task-evidence/invalid.md' 'invalid-evidence-name'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'source/task-evidence/invalid.md') -Force

    Write-Utf8File (Join-Path $gateRoot 'source/UNAPPROVED.md') (Get-EvidenceBody 'outside approved source')
    Assert-Checker (Invoke-Checker 'gate-v' @('-PreTag')) $false 'UNKNOWN_ARCHIVE_FILE file=source/UNAPPROVED.md' 'unknown-source-file'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'source/UNAPPROVED.md') -Force

    New-Item -ItemType Directory -Path (Join-Path $gateRoot 'other') -Force | Out-Null
    Write-Utf8File (Join-Path $gateRoot 'other/README.md') (Get-EvidenceBody 'duplicate archive entry')
    Assert-Checker (Invoke-Checker 'gate-v' @('-PreTag')) $false 'DUPLICATE_ROLE role=archive-entry' 'duplicate-top-level-archive-entry'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'other') -Recurse -Force

    Write-Output 'PASS / GATE_ARCHIVE_MANIFEST_REGRESSION'
    Write-Output 'PASS / TASK_EVIDENCE_POLICY_VALID'
}
finally {
    $tempBase=[System.IO.Path]::GetTempPath()
    if (Test-Path -LiteralPath $tempRoot) {
        Assert-Condition ($tempRoot.StartsWith($tempBase,[System.StringComparison]::OrdinalIgnoreCase)) "cleanup escaped temp path=$tempRoot"
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
