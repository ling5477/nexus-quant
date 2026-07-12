<#
.SYNOPSIS
Validates GateV strict manifest policy plus explicit pre-tag and default post-tag checker behavior.
.NOTES
Uses only a disposable system-temporary Git repository; it never changes real docs/gates, docs/current, tags, or branches.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestPath = Join-Path $PSScriptRoot 'gate-archive-manifest.json'
$checkerPath = Join-Path $PSScriptRoot 'check-gate-archive.ps1'
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("nq-gate-archive-manifest-" + [guid]::NewGuid().ToString('N'))
$fixtureRoot = Join-Path $tempRoot 'fixture-repo'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Assert-Condition {
    param(
        [bool] $Condition,
        [string] $Message
    )

    if (-not $Condition) {
        throw "ASSERTION_FAILED $Message"
    }
}

function Write-Utf8File {
    param(
        [string] $Path,
        [string] $Content
    )

    [System.IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

function Invoke-FixtureGit {
    param([string[]] $Arguments)

    # Git progress can use stderr on success; classify the command by its exit code instead of PowerShell's native stderr wrapper.
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & git -C $fixtureRoot @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    if ($exitCode -ne 0) {
        throw "FIXTURE_GIT_FAILED command=git $($Arguments -join ' ') output=$(($output | ForEach-Object { $_.ToString() }) -join ' ')"
    }
    return (($output | ForEach-Object { $_.ToString() }) -join "`n").Trim()
}

function Get-FixtureEvidenceBody {
    param([string] $Name)

    return @(
        "# $Name",
        '',
        'This file is an archive manifest regression fixture only and never represents a real GateV archive, freeze, commit, or release.',
        'It provides an independent non-empty evidence body for strict role aliases, minimum body length, and path matching rules.',
        'Real GateV lifecycle, scheduler, API, database, and frontend evidence must be re-verified in a separately authorized freeze closeout.',
        'The fixture reads no credentials, calls no network service, executes no trades, and enables no LIVE, Shadow, AI, DH, or Integration runtime.',
        'Python manifest preview remains a No-file residual in real GateV; this fixture does not manufacture Python implementation evidence.',
        'All content is temporary test data, and the temporary directory and temporary Git tag are removed when this test completes.',
        'This independent body deliberately exceeds the manifest minimum non-empty-line and independent-character thresholds.'
    ) -join "`n"
}

function Get-ArchiveFixtureFiles {
    return @(
        'README.md',
        'GATEV_FREEZE_CLOSEOUT.md',
        'GATEV_FREEZE_READINESS_REVIEW.md',
        'GATEV_IMPLEMENTATION_BASELINE.md',
        'GATEV_BATCH_1_4_EVIDENCE_MATRIX.md',
        'GATEV_TESTING_EVIDENCE_SUMMARY.md',
        'GATEV_BACKEND_DB_MIGRATION_EVIDENCE_SUMMARY.md',
        'GATEV_API_EVIDENCE_SUMMARY.md',
        'GATEV_FRONTEND_EVIDENCE_SUMMARY.md',
        'GATEV_RUNTIME_SCHEDULING_BOUNDARY_SUMMARY.md',
        'GATEV_BOUNDARY_STATEMENT.md',
        'GATEV_KNOWN_LIMITATIONS_AND_RESIDUALS.md'
    )
}

function New-ArchiveFixture {
    param([string] $Gate)

    $archiveRoot = Join-Path $fixtureRoot "docs/gates/$Gate"
    foreach ($file in Get-ArchiveFixtureFiles) {
        Write-Utf8File (Join-Path $archiveRoot $file) (Get-FixtureEvidenceBody $file)
    }
}

function Write-PreTagAuthority {
    param(
        [string] $ActiveGate = 'GateV',
        [string] $WorkBatch = 'GateV-FREEZE',
        [string] $WorkStatus = 'NOT_STARTED',
        [string] $Live = 'DISABLED'
    )

    # Keep this fixture block minimal; the independent authority checker owns the complete authority contract.
    Write-Utf8File (Join-Path $fixtureRoot 'docs/current/STATUS.md') @"
# Fixture Current Status

<!-- nq-current-authority:start
authority_schema=3
active_gate=$ActiveGate
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=$WorkBatch
work_batch_status=$WorkStatus
live=$Live
nq-current-authority:end -->
"@
}

function Write-PostTagAuthority {
    param(
        [string] $ExpectedTag,
        [string] $TaggedCommit
    )

    # Preserve the three-field historical post-tag contract while adding the opt-in pre-tag mode.
    Write-Utf8File (Join-Path $fixtureRoot 'docs/current/STATUS.md') @"
current_gate_status=FROZEN|ACCEPTED|TAGGED
current_gate_tag=$ExpectedTag
updated_commit=$TaggedCommit
"@
}

function Invoke-FixtureChecker {
    param(
        [string] $Gate,
        [string[]] $AdditionalArguments = @()
    )

    $fixtureChecker = Join-Path $fixtureRoot 'scripts/docs/check-gate-archive.ps1'
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $fixtureChecker -Gate $Gate @AdditionalArguments 2>&1
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    return [pscustomobject]@{
        ExitCode = $exitCode
        Text = (($output | ForEach-Object { $_.ToString() }) -join "`n")
    }
}

function Assert-CheckerResult {
    param(
        [pscustomobject] $Result,
        [bool] $ShouldPass,
        [string] $ExpectedText,
        [string] $Scenario
    )

    if ($ShouldPass -and $Result.ExitCode -ne 0) {
        throw "CHECKER_UNEXPECTED_FAILURE scenario=$Scenario output=$($Result.Text)"
    }
    if (-not $ShouldPass -and $Result.ExitCode -eq 0) {
        throw "CHECKER_UNEXPECTED_SUCCESS scenario=$Scenario output=$($Result.Text)"
    }
    if ($Result.Text -notmatch [regex]::Escape($ExpectedText)) {
        throw "CHECKER_OUTPUT_MISMATCH scenario=$Scenario expected=$ExpectedText output=$($Result.Text)"
    }
    Write-Output "PASS fixture=$Scenario exit=$($Result.ExitCode) expected=$ExpectedText"
}

try {
    $manifest = Get-Content -Raw $manifestPath | ConvertFrom-Json
    $gateU = $manifest.strictGateOverrides.PSObject.Properties['gate-u'].Value
    $gateV = $manifest.strictGateOverrides.PSObject.Properties['gate-v'].Value

    # GateU/legacy policy assertions protect the existing manifest contract while this task changes checker code only.
    Assert-Condition ($manifest.legacyThroughGate -eq 'gate-t') "legacyThroughGate expected=gate-t actual=$($manifest.legacyThroughGate)"
    Assert-Condition ($null -ne $gateU) 'gate-u strict override missing'
    Assert-Condition ($gateU.expectedTag -eq 'nq-gateu-freeze') "gate-u expectedTag changed actual=$($gateU.expectedTag)"
    Assert-Condition ($gateU.expectedTagTarget -eq '48ef0cdaa97099ae1ff5a66a8c0caeb07aa11fab') 'gate-u expectedTagTarget changed'
    Assert-Condition ($null -ne $gateV) 'gate-v strict override missing'
    Assert-Condition ($gateV.expectedTag -eq 'nq-gatev-freeze') "gate-v expectedTag actual=$($gateV.expectedTag)"
    Assert-Condition ($null -ne $gateV.PSObject.Properties['expectedTagTarget']) 'gate-v expectedTagTarget property missing'
    Assert-Condition ($null -eq $gateV.expectedTagTarget) 'gate-v must not predict a future tag target'
    Assert-Condition ($gateV.allowPreTagArchiveState -eq $true) 'gate-v pre-tag archive state must remain allowed'

    $expectedGateVRoles = @('backend-db-evidence', 'api-evidence', 'frontend-evidence', 'runtime-scheduling-evidence')
    $actualGateVRoles = @($gateV.conditionalRoles | ForEach-Object { [string] $_ })
    Assert-Condition (($actualGateVRoles -join ',') -eq ($expectedGateVRoles -join ',')) "gate-v conditional roles actual=$($actualGateVRoles -join ',')"
    foreach ($role in @($manifest.mandatoryRoles) + $actualGateVRoles) {
        Assert-Condition (-not [string]::IsNullOrWhiteSpace($role)) 'empty mandatory or GateV conditional role'
        Assert-Condition ($role -notmatch '\*') "wildcard role is forbidden role=$role"
        Assert-Condition ($null -ne $manifest.acceptedAliases.PSObject.Properties[$role]) "role alias missing role=$role"
    }
    Write-Output 'PASS manifest=gate-v-strict-override legacy=gate-t gate-u=preserved roles=validated'

    New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'scripts/docs') -Force | Out-Null
    Copy-Item $manifestPath (Join-Path $fixtureRoot 'scripts/docs/gate-archive-manifest.json') -Force
    Copy-Item $checkerPath (Join-Path $fixtureRoot 'scripts/docs/check-gate-archive.ps1') -Force
    New-ArchiveFixture 'gate-v'
    New-ArchiveFixture 'gate-w'
    Write-PreTagAuthority

    Invoke-FixtureGit @('init') | Out-Null
    Invoke-FixtureGit @('config', 'core.autocrlf', 'false') | Out-Null
    Invoke-FixtureGit @('config', 'user.email', 'fixture@nexusquant.invalid') | Out-Null
    Invoke-FixtureGit @('config', 'user.name', 'NexusQuant Fixture') | Out-Null
    Invoke-FixtureGit @('add', '.') | Out-Null
    Invoke-FixtureGit @('commit', '-m', 'fixture archive manifest') | Out-Null
    $tagTarget = Invoke-FixtureGit @('rev-parse', 'HEAD')

    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v' @('-PreTag')) $true 'PASS / GATE_ARCHIVE_PRETAG_VALID' 'gate-v-pretag-positive'
    Write-PreTagAuthority -WorkStatus 'IMPLEMENTED|PENDING_REVIEW'
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v' @('-PreTag')) $true 'PASS / GATE_ARCHIVE_PRETAG_VALID' 'gate-v-pretag-pending-review-positive'
    Write-PreTagAuthority
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v') $false 'LOCAL_TAG_MISSING nq-gatev-freeze' 'gate-v-default-no-tag'

    $gateVRoot = Join-Path $fixtureRoot 'docs/gates/gate-v'
    Remove-Item -LiteralPath (Join-Path $gateVRoot 'GATEV_FREEZE_CLOSEOUT.md') -Force
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v' @('-PreTag')) $false 'ROLE_MISSING role=freeze-closeout gate=gate-v' 'gate-v-pretag-missing-mandatory-role'
    Write-Utf8File (Join-Path $gateVRoot 'GATEV_FREEZE_CLOSEOUT.md') (Get-FixtureEvidenceBody 'GATEV_FREEZE_CLOSEOUT.md')

    Remove-Item -LiteralPath (Join-Path $gateVRoot 'GATEV_FRONTEND_EVIDENCE_SUMMARY.md') -Force
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v' @('-PreTag')) $false 'ROLE_MISSING role=frontend-evidence gate=gate-v' 'gate-v-pretag-missing-conditional-role'
    Write-Utf8File (Join-Path $gateVRoot 'GATEV_FRONTEND_EVIDENCE_SUMMARY.md') (Get-FixtureEvidenceBody 'GATEV_FRONTEND_EVIDENCE_SUMMARY.md')

    Write-Utf8File (Join-Path $gateVRoot 'GATEV_API_EVIDENCE_SUMMARY.md') '# Thin API evidence fixture'
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v' @('-PreTag')) $false 'THIN_ROLE role=api-evidence gate=gate-v' 'gate-v-pretag-thin-role'
    Write-Utf8File (Join-Path $gateVRoot 'GATEV_API_EVIDENCE_SUMMARY.md') (Get-FixtureEvidenceBody 'GATEV_API_EVIDENCE_SUMMARY.md')

    Write-Utf8File (Join-Path $gateVRoot 'README.md') ((Get-FixtureEvidenceBody 'README.md') + "`n[Broken fixture link](MISSING.md)")
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v' @('-PreTag')) $false 'README_LINK_BROKEN' 'gate-v-pretag-broken-link'
    Write-Utf8File (Join-Path $gateVRoot 'README.md') (Get-FixtureEvidenceBody 'README.md')

    Write-Utf8File (Join-Path $gateVRoot 'GATEV_API_SECOND_EVIDENCE.md') (Get-FixtureEvidenceBody 'GATEV_API_SECOND_EVIDENCE.md')
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v' @('-PreTag')) $false 'DUPLICATE_ROLE role=api-evidence' 'gate-v-pretag-duplicate-role'
    Remove-Item -LiteralPath (Join-Path $gateVRoot 'GATEV_API_SECOND_EVIDENCE.md') -Force

    Write-Utf8File (Join-Path $gateVRoot 'GATEV_RELEASE_NOTES.md') (Get-FixtureEvidenceBody 'GATEV_RELEASE_NOTES.md')
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v' @('-PreTag')) $false 'UNKNOWN_ARCHIVE_FILE file=GATEV_RELEASE_NOTES.md' 'gate-v-pretag-unknown-file'
    Remove-Item -LiteralPath (Join-Path $gateVRoot 'GATEV_RELEASE_NOTES.md') -Force

    Assert-CheckerResult (Invoke-FixtureChecker 'gate-w' @('-PreTag')) $false 'STRICT_GATE_OVERRIDE_MISSING gate=gate-w' 'gate-w-pretag-missing-override'

    Write-PreTagAuthority -ActiveGate 'GateW' -WorkBatch 'GateW-FREEZE'
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v' @('-PreTag')) $false 'PRETAG_AUTHORITY_GATE_MISMATCH expected=GateV actual=GateW' 'gate-v-pretag-authority-mismatch'
    Write-PreTagAuthority -WorkBatch 'GateW-FREEZE'
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v' @('-PreTag')) $false 'PRETAG_WORK_BATCH_MISMATCH expected=GateV-FREEZE actual=GateW-FREEZE' 'gate-v-pretag-work-batch-mismatch'
    Write-PreTagAuthority -Live 'ENABLED'
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v' @('-PreTag')) $false 'PRETAG_LIVE_BOUNDARY_INVALID value=ENABLED' 'gate-v-pretag-live-enabled'
    Write-PreTagAuthority

    Invoke-FixtureGit @('tag', '-a', 'nq-gatev-freeze', '-m', 'fixture GateV freeze', $tagTarget) | Out-Null
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v' @('-PreTag')) $false 'PRETAG_MODE_TAG_ALREADY_EXISTS nq-gatev-freeze' 'gate-v-pretag-tag-already-exists'

    Write-PostTagAuthority -ExpectedTag 'nq-gatev-freeze' -TaggedCommit $tagTarget
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v') $true 'PASS / ARCHIVE_MANIFEST_COMPLETE' 'gate-v-posttag-positive'

    # Exercise the unchanged GateU strict target against a real local commit object inside the disposable repository.
    New-ArchiveFixture 'gate-u'
    Write-Utf8File (Join-Path $fixtureRoot 'docs/gates/gate-u/GATEU_PYTHON_BOUNDARY_EVIDENCE.md') (Get-FixtureEvidenceBody 'GATEU_PYTHON_BOUNDARY_EVIDENCE.md')
    Invoke-FixtureGit @('fetch', $repoRoot, $gateU.expectedTagTarget) | Out-Null
    Invoke-FixtureGit @('tag', '-a', 'nq-gateu-freeze', '-m', 'fixture GateU freeze', $gateU.expectedTagTarget) | Out-Null
    Write-PostTagAuthority -ExpectedTag 'nq-gateu-freeze' -TaggedCommit $gateU.expectedTagTarget
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-u') $true 'PASS / ARCHIVE_MANIFEST_COMPLETE' 'gate-u-posttag-positive'

    # Re-assert GateU values after all checker scenarios so accidental fixture-policy mutation cannot pass silently.
    $finalManifest = Get-Content -Raw (Join-Path $fixtureRoot 'scripts/docs/gate-archive-manifest.json') | ConvertFrom-Json
    Assert-Condition ($finalManifest.strictGateOverrides.'gate-u'.expectedTag -eq 'nq-gateu-freeze') 'gate-u regression expectedTag changed'
    Assert-Condition ($finalManifest.strictGateOverrides.'gate-u'.expectedTagTarget -eq '48ef0cdaa97099ae1ff5a66a8c0caeb07aa11fab') 'gate-u regression target changed'
    Write-Output 'PASS fixture=gate-u-policy-preserved exit=0 expected=strict override unchanged'
    Write-Output 'PASS / GATE_ARCHIVE_MANIFEST_REGRESSION'
}
finally {
    $tempBase = [System.IO.Path]::GetTempPath()
    if (Test-Path -LiteralPath $tempRoot) {
        Assert-Condition ($tempRoot.StartsWith($tempBase, [System.StringComparison]::OrdinalIgnoreCase)) "temporary cleanup path escaped temp root path=$tempRoot"
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
