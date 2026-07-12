<#
.SYNOPSIS
Validates the GateV strict manifest override and checker positive and negative fixtures.
.NOTES
Uses only a disposable temporary Git fixture, archive, and annotated tag; it never changes real docs/gates, tags, or Git status.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\\..')).Path
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
    param(
        [string[]] $Arguments
    )

    $output = & git -C $fixtureRoot @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
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

function New-ArchiveFixture {
    param(
        [string] $Gate
    )

    $archiveRoot = Join-Path $fixtureRoot "docs/gates/$Gate"
    $files = @(
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
    foreach ($file in $files) {
        Write-Utf8File (Join-Path $archiveRoot $file) (Get-FixtureEvidenceBody $file)
    }
}

function Invoke-FixtureChecker {
    param([string] $Gate)

    $fixtureChecker = Join-Path $fixtureRoot 'scripts/docs/check-gate-archive.ps1'
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $fixtureChecker -Gate $Gate 2>&1
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

    Invoke-FixtureGit @('init') | Out-Null
    Invoke-FixtureGit @('config', 'core.autocrlf', 'false') | Out-Null
    Invoke-FixtureGit @('config', 'user.email', 'fixture@nexusquant.invalid') | Out-Null
    Invoke-FixtureGit @('config', 'user.name', 'NexusQuant Fixture') | Out-Null
    Invoke-FixtureGit @('add', '.') | Out-Null
    Invoke-FixtureGit @('commit', '-m', 'fixture archive manifest') | Out-Null
    $tagTarget = Invoke-FixtureGit @('rev-parse', 'HEAD')
    Write-Utf8File (Join-Path $fixtureRoot 'docs/current/STATUS.md') @"
current_gate_status=FROZEN|ACCEPTED|TAGGED
current_gate_tag=nq-gatev-freeze
updated_commit=$tagTarget
"@
    Invoke-FixtureGit @('tag', '-a', 'nq-gatev-freeze', '-m', 'fixture GateV freeze', $tagTarget) | Out-Null

    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v') $true 'PASS / ARCHIVE_MANIFEST_COMPLETE' 'gate-v-positive'

    Remove-Item -LiteralPath (Join-Path $fixtureRoot 'docs/gates/gate-v/GATEV_FRONTEND_EVIDENCE_SUMMARY.md') -Force
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v') $false 'ROLE_MISSING role=frontend-evidence gate=gate-v' 'gate-v-missing-role'
    Write-Utf8File (Join-Path $fixtureRoot 'docs/gates/gate-v/GATEV_FRONTEND_EVIDENCE_SUMMARY.md') (Get-FixtureEvidenceBody 'GATEV_FRONTEND_EVIDENCE_SUMMARY.md')

    $fixtureManifestPath = Join-Path $fixtureRoot 'scripts/docs/gate-archive-manifest.json'
    $fixtureManifest = Get-Content -Raw $fixtureManifestPath | ConvertFrom-Json
    $fixtureManifest.strictGateOverrides.'gate-v'.expectedTag = 'nq-gatev-wrong-freeze'
    Write-Utf8File $fixtureManifestPath ($fixtureManifest | ConvertTo-Json -Depth 10)
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v') $false 'LOCAL_TAG_MISSING nq-gatev-wrong-freeze' 'gate-v-wrong-tag'
    Copy-Item $manifestPath $fixtureManifestPath -Force

    $fixtureManifest = Get-Content -Raw $fixtureManifestPath | ConvertFrom-Json
    $fixtureManifest.strictGateOverrides.PSObject.Properties.Remove('gate-v')
    Write-Utf8File $fixtureManifestPath ($fixtureManifest | ConvertTo-Json -Depth 10)
    Assert-CheckerResult (Invoke-FixtureChecker 'gate-v') $false 'STRICT_GATE_OVERRIDE_MISSING gate=gate-v' 'gate-v-missing-override'
    Copy-Item $manifestPath $fixtureManifestPath -Force

    Assert-CheckerResult (Invoke-FixtureChecker 'gate-w') $false 'STRICT_GATE_OVERRIDE_MISSING gate=gate-w' 'gate-w-missing-override'
    Write-Output 'PASS / GATE_ARCHIVE_MANIFEST_REGRESSION'
}
finally {
    $tempBase = [System.IO.Path]::GetTempPath()
    if (Test-Path -LiteralPath $tempRoot) {
        Assert-Condition ($tempRoot.StartsWith($tempBase, [System.StringComparison]::OrdinalIgnoreCase)) "temporary cleanup path escaped temp root path=$tempRoot"
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
