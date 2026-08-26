<#
.SYNOPSIS
Regresses strict manifest roles, links, and archive task evidence in a disposable repository.
.NOTES
The test does not access real archives, remotes, or current authority. Release delegation uses only a disposable local tag.
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
function Invoke-FixtureGit {
    param([string[]]$Arguments)
    $previous=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $output=& git -C $fixtureRoot @Arguments 2>&1
    $exit=$LASTEXITCODE; $ErrorActionPreference=$previous
    if ($exit -ne 0) { throw "FIXTURE_GIT_FAILED args=$($Arguments -join ' ') output=$($output -join ' | ')" }
    return (($output | ForEach-Object { $_.ToString() }) -join "`n").Trim()
}
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
    param([string]$Gate,[string[]]$ConditionalRoles)
    $prefix = $Gate.Replace('-', '').ToUpperInvariant()
    $files = New-Object System.Collections.Generic.List[string]
    foreach ($file in @(
        'README.md',"${prefix}_FREEZE_CLOSEOUT.md","${prefix}_FREEZE_READINESS_REVIEW.md",
        "${prefix}_IMPLEMENTATION_BASELINE.md","${prefix}_BATCH_1_4_EVIDENCE_MATRIX.md",
        "${prefix}_TESTING_EVIDENCE_SUMMARY.md","${prefix}_BOUNDARY_STATEMENT.md",
        "${prefix}_KNOWN_LIMITATIONS_AND_RESIDUALS.md"
    )) { $files.Add($file) }
    $conditionalFiles = @{
        'backend-db-evidence' = "${prefix}_BACKEND_DB_MIGRATION_EVIDENCE_SUMMARY.md"
        'api-evidence' = "${prefix}_API_EVIDENCE_SUMMARY.md"
        'frontend-evidence' = "${prefix}_FRONTEND_EVIDENCE_SUMMARY.md"
        'python-boundary-evidence' = "${prefix}_PYTHON_BOUNDARY_EVIDENCE_SUMMARY.md"
        'runtime-scheduling-evidence' = "${prefix}_RUNTIME_SCHEDULING_BOUNDARY_SUMMARY.md"
        'deployment-evidence' = "${prefix}_DEPLOYMENT_EVIDENCE_SUMMARY.md"
        'live-pilot-evidence' = "${prefix}_MINIMAL_LIVE_PILOT_EVIDENCE_SUMMARY.md"
        'live-session-work-order' = "${prefix}_1_LIVE_SESSION_DATA_MODEL_WORK_ORDER.md"
        'micro-live-work-order' = "${prefix}_6_EXPLICIT_MICRO_LIVE_AUTHORIZATION_WORK_ORDER.md"
    }
    foreach ($role in $ConditionalRoles) { $files.Add([string]$conditionalFiles[$role]) }
    return @($files)
}
function New-ArchiveFixture {
    param([string]$Gate,[string[]]$ConditionalRoles)
    $root = Join-Path $fixtureRoot "docs/gates/$Gate"
    foreach ($file in Get-RoleFiles $Gate $ConditionalRoles) { Write-Utf8File (Join-Path $root $file) (Get-EvidenceBody $file) }
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
function Test-GateWOverrideContract {
    param([object]$Manifest)
    $property = $Manifest.strictGateOverrides.PSObject.Properties['gate-w']
    if ($null -eq $property) { return $false }
    $config = $property.Value
    $expectedRoles = @('backend-db-evidence','api-evidence','frontend-evidence','runtime-scheduling-evidence')
    $actualRoles = @($config.conditionalRoles | ForEach-Object { [string]$_ })
    return (
        $config.expectedTag -eq 'nq-gatew-freeze' -and
        $null -ne $config.PSObject.Properties['expectedTagTarget'] -and
        $null -eq $config.expectedTagTarget -and
        $config.allowPreTagArchiveState -eq $true -and
        ($actualRoles -join '|') -eq ($expectedRoles -join '|')
    )
}
function Test-GateYOverrideContract {
    param([object]$Manifest)
    $property = $Manifest.strictGateOverrides.PSObject.Properties['gate-y']
    if ($null -eq $property) { return $false }
    $config = $property.Value
    $expectedRoles = @(
        'backend-db-evidence','api-evidence','frontend-evidence','runtime-scheduling-evidence',
        'deployment-evidence','live-pilot-evidence','live-session-work-order','micro-live-work-order'
    )
    $actualRoles = @($config.conditionalRoles | ForEach-Object { [string]$_ })
    return (
        $config.expectedTag -eq 'nq-gatey-freeze' -and
        $null -ne $config.PSObject.Properties['expectedTagTarget'] -and
        $null -eq $config.expectedTagTarget -and
        $config.allowPreTagArchiveState -eq $true -and
        ($actualRoles -join '|') -eq ($expectedRoles -join '|')
    )
}
function Copy-ManifestObject {
    param([object]$Manifest)
    return ($Manifest | ConvertTo-Json -Depth 20 | ConvertFrom-Json)
}

try {
    $manifest = Get-Content -Raw $manifestPath | ConvertFrom-Json
    $gateU = $manifest.strictGateOverrides.'gate-u'
    $gateV = $manifest.strictGateOverrides.'gate-v'
    $gateW = $manifest.strictGateOverrides.'gate-w'
    $gateY = $manifest.strictGateOverrides.'gate-y'
    Assert-Condition ($manifest.legacyThroughGate -eq 'gate-t') 'legacyThroughGate changed'
    Assert-Condition ($gateU.expectedTag -eq 'nq-gateu-freeze') 'GateU tag changed'
    Assert-Condition ($gateU.expectedTagTarget -eq '48ef0cdaa97099ae1ff5a66a8c0caeb07aa11fab') 'GateU target changed'
    Assert-Condition ($gateV.expectedTag -eq 'nq-gatev-freeze') 'GateV tag changed'
    Assert-Condition (Test-GateWOverrideContract $manifest) 'GateW strict override contract invalid'
    Write-Output 'PASS fixture=gatew-strict-override-contract'
    Assert-Condition (Test-GateYOverrideContract $manifest) 'GateY strict override contract invalid'
    Write-Output 'PASS fixture=gatey-strict-override-contract'

    $missingTagManifest = Copy-ManifestObject $manifest
    $missingTagManifest.strictGateOverrides.'gate-w'.expectedTag = $null
    Assert-Condition (-not (Test-GateWOverrideContract $missingTagManifest)) 'GateW missing canonical tag accepted'
    Write-Output 'PASS fixture=gatew-canonical-tag-missing-rejected'

    $wrongTagManifest = Copy-ManifestObject $manifest
    $wrongTagManifest.strictGateOverrides.'gate-w'.expectedTag = 'nq-gatex-freeze'
    Assert-Condition (-not (Test-GateWOverrideContract $wrongTagManifest)) 'GateW wrong canonical tag accepted'
    Write-Output 'PASS fixture=gatew-canonical-tag-wrong-rejected'

    $scripts = Join-Path $fixtureRoot 'scripts/docs'
    New-Item -ItemType Directory -Path $scripts -Force | Out-Null
    Copy-Item $manifestPath,$contractPath,$checkerPath,$helperPath -Destination $scripts -Force
    New-ArchiveFixture 'gate-u' @($gateU.conditionalRoles)
    New-ArchiveFixture 'gate-v' @($gateV.conditionalRoles)
    New-ArchiveFixture 'gate-w' @($gateW.conditionalRoles)
    New-ArchiveFixture 'gate-y' @($gateY.conditionalRoles)

    Assert-Checker (Invoke-Checker 'gate-u' @('-PreTag')) $true 'PASS / GATE_ARCHIVE_PRETAG_VALID' 'gateu-compatibility-positive'
    Assert-Checker (Invoke-Checker 'gate-v' @('-PreTag')) $true 'PASS / GATE_ARCHIVE_PRETAG_VALID' 'pretag-structure-positive'
    Assert-Checker (Invoke-Checker 'gate-v') $true 'PASS / ARCHIVE_MANIFEST_COMPLETE' 'posttag-structure-positive'
    Assert-Checker (Invoke-Checker 'gate-w' @('-PreTag')) $true 'PASS / GATE_ARCHIVE_PRETAG_VALID' 'gatew-pretag-structure-positive'
    Assert-Checker (Invoke-Checker 'gate-w') $true 'PASS / ARCHIVE_MANIFEST_COMPLETE' 'gatew-posttag-structure-positive'
    Assert-Checker (Invoke-Checker 'gate-y' @('-PreTag')) $true 'PASS / GATE_ARCHIVE_PRETAG_VALID' 'gatey-pretag-structure-positive'
    Assert-Checker (Invoke-Checker 'gate-y') $true 'PASS / ARCHIVE_MANIFEST_COMPLETE' 'gatey-posttag-structure-positive'

    Invoke-FixtureGit @('init','-q') | Out-Null
    Invoke-FixtureGit @('config','user.name','NQ Governance Fixture') | Out-Null
    Invoke-FixtureGit @('config','user.email','nq-governance-fixture@example.invalid') | Out-Null
    Invoke-FixtureGit @('add','.') | Out-Null
    Invoke-FixtureGit @('commit','-q','-m','fixture') | Out-Null
    $fixtureCommit = Invoke-FixtureGit @('rev-parse','HEAD')
    Invoke-FixtureGit @('tag','-a','nq-gatew-freeze','-m','fixture GateW freeze') | Out-Null
    $expectedCommitPath = Join-Path $fixtureRoot 'expected-release-commit.txt'
    Write-Utf8File $expectedCommitPath $fixtureCommit
    $delegatedReleaseChecker = @'
[CmdletBinding()]
param([string]$Gate,[string]$ExpectedTag,[string]$ExpectedCommit)
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$requiredCommit = (Get-Content -Raw (Join-Path $repoRoot 'expected-release-commit.txt')).Trim()
if ($Gate -cne 'gate-w' -or $ExpectedTag -cne 'nq-gatew-freeze' -or $ExpectedCommit -cne $requiredCommit) {
    Write-Output 'BLOCKED / GATE_RELEASE_INVALID'
    exit 1
}
Write-Output "RELEASE_CHECK gate=$Gate tag=$ExpectedTag commit=$ExpectedCommit errors=0"
Write-Output 'PASS / GATE_RELEASE_VALID'
exit 0
'@
    Write-Utf8File (Join-Path $scripts 'check-gate-release.ps1') $delegatedReleaseChecker
    Assert-Checker `
        (Invoke-Checker 'gate-w' @('-ExpectedTag','nq-gatew-freeze','-RequireRemoteTag','-RequireCi')) `
        $true 'PASS / GATE_RELEASE_VALID' 'gatew-release-expected-commit-delegated'

    Write-Utf8File $expectedCommitPath ('f' * 40)
    Assert-Checker `
        (Invoke-Checker 'gate-w' @('-ExpectedTag','nq-gatew-freeze','-RequireRemoteTag','-RequireCi')) `
        $false 'DELEGATED_RELEASE_CHECK_FAILED' 'gatew-release-expected-commit-mismatch'
    Write-Utf8File $expectedCommitPath $fixtureCommit
    Assert-Checker `
        (Invoke-Checker 'gate-w' @('-ExpectedTag','nq-gatex-freeze','-RequireRemoteTag','-RequireCi')) `
        $false 'DELEGATED_RELEASE_CHECK_FAILED' 'gatew-release-illegal-tag'
    Invoke-FixtureGit @('tag','-d','nq-gatew-freeze') | Out-Null
    Assert-Checker `
        (Invoke-Checker 'gate-w' @('-ExpectedTag','nq-gatew-freeze','-RequireRemoteTag','-RequireCi')) `
        $false 'CANONICAL_TAG_PEELED_COMMIT_INVALID' 'gatew-release-missing-tag'

    $fixtureManifestPath = Join-Path $scripts 'gate-archive-manifest.json'
    $missingOverrideManifest = Copy-ManifestObject $manifest
    $missingOverrideManifest.strictGateOverrides.PSObject.Properties.Remove('gate-w')
    Write-Utf8File $fixtureManifestPath ($missingOverrideManifest | ConvertTo-Json -Depth 20)
    Assert-Checker (Invoke-Checker 'gate-w' @('-PreTag')) $false 'STRICT_GATE_OVERRIDE_MISSING gate=gate-w' 'gatew-strict-override-missing'
    Copy-Item $manifestPath -Destination $fixtureManifestPath -Force

    $gateRoot = Join-Path $fixtureRoot 'docs/gates/gate-w'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'GATEW_FREEZE_CLOSEOUT.md') -Force
    Assert-Checker (Invoke-Checker 'gate-w' @('-PreTag')) $false 'ROLE_MISSING role=freeze-closeout gate=gate-w' 'gatew-missing-role'
    Write-Utf8File (Join-Path $gateRoot 'GATEW_FREEZE_CLOSEOUT.md') (Get-EvidenceBody 'GATEW_FREEZE_CLOSEOUT.md')

    Write-Utf8File (Join-Path $gateRoot 'GATEW_API_SECOND_EVIDENCE.md') (Get-EvidenceBody 'duplicate api')
    Assert-Checker (Invoke-Checker 'gate-w' @('-PreTag')) $false 'DUPLICATE_ROLE role=api-evidence' 'gatew-duplicate-role'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'GATEW_API_SECOND_EVIDENCE.md') -Force

    Write-Utf8File (Join-Path $gateRoot 'GATEW_RELEASE_NOTES.md') (Get-EvidenceBody 'unknown')
    Assert-Checker (Invoke-Checker 'gate-w' @('-PreTag')) $false 'UNKNOWN_ARCHIVE_FILE file=GATEW_RELEASE_NOTES.md' 'gatew-unknown-file'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'GATEW_RELEASE_NOTES.md') -Force

    Write-Utf8File (Join-Path $gateRoot 'README.md') ((Get-EvidenceBody 'README') + "`n[broken](missing.md)")
    Assert-Checker (Invoke-Checker 'gate-w' @('-PreTag')) $false 'README_LINK_BROKEN' 'broken-readme-link'
    Write-Utf8File (Join-Path $gateRoot 'README.md') (Get-EvidenceBody 'README.md')

    Write-Utf8File (Join-Path $gateRoot 'source/task-evidence/README.md') "# Task evidence index`n`nThis index links attempts and is never an archive role."
    Write-Utf8File (Join-Path $gateRoot 'source/task-evidence/NQ-GATEW-ATTEMPT-10-FAILED.attempt-01.md') (Get-EvidenceBody 'FAILED historical attempt')
    Write-Utf8File (Join-Path $gateRoot 'source/task-evidence/NQ-GATEW-ATTEMPT-11-BLOCKED.attempt-01.md') (Get-EvidenceBody 'BLOCKED historical attempt')
    Write-Utf8File (Join-Path $gateRoot 'source/task-evidence/NQ-GATEW-ATTEMPT-13-PASS.attempt-01.md') (Get-EvidenceBody 'PASS accepted attempt')
    $positive = Invoke-Checker 'gate-w' @('-PreTag')
    Assert-Checker $positive $true 'EVIDENCE file=source/task-evidence/README.md role=non-role valid=True' 'gatew-nested-readme-non-role'
    foreach ($state in @('FAILED','BLOCKED','PASS')) {
        Assert-Condition ($positive.Text -match "EVIDENCE file=source/task-evidence/[^`n]*$state[^`n]* role=non-role valid=True") "$state attempt evidence rejected"
    }

    $gateYRoot = Join-Path $fixtureRoot 'docs/gates/gate-y'
    Write-Utf8File (Join-Path $gateYRoot 'source/task-evidence/NQ-GATEY-FIRST-REAL-ORDER-HARD-GATE.manifest.json') '{"schemaVersion":"fixture","gate":"GateY","status":"HISTORICAL","description":"sanitized disposable manifest evidence with no credential or external side effect"}'
    Assert-Checker (Invoke-Checker 'gate-y' @('-PreTag')) $true 'EVIDENCE file=source/task-evidence/NQ-GATEY-FIRST-REAL-ORDER-HARD-GATE.manifest.json role=non-role valid=True' 'gatey-json-manifest-evidence-positive'
    Assert-Condition ($positive.Text -match 'ROLE role=archive-entry files=README.md') 'top-level README must remain sole archive-entry'
    Write-Output 'PASS fixture=gatew-historical-attempts-raw-evidence'

    Remove-Item -LiteralPath (Join-Path $gateRoot 'README.md') -Force
    Assert-Checker (Invoke-Checker 'gate-w' @('-PreTag')) $false 'ROLE_MISSING role=archive-entry gate=gate-w' 'gatew-task-evidence-not-canonical-role'
    Write-Utf8File (Join-Path $gateRoot 'README.md') (Get-EvidenceBody 'README.md')

    Write-Utf8File (Join-Path $gateRoot 'GATEV_API_EVIDENCE_SUMMARY.md') (Get-EvidenceBody 'wrong Gate evidence')
    Assert-Checker (Invoke-Checker 'gate-w' @('-PreTag')) $false 'DUPLICATE_ROLE role=api-evidence' 'gatew-wrong-gate-evidence-rejected'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'GATEV_API_EVIDENCE_SUMMARY.md') -Force

    Write-Utf8File (Join-Path $gateRoot 'source/task-evidence/EMPTY.attempt-01.md') ''
    Assert-Checker (Invoke-Checker 'gate-w' @('-PreTag')) $false 'TASK_EVIDENCE_INVALID file=source/task-evidence/EMPTY.attempt-01.md' 'empty-evidence'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'source/task-evidence/EMPTY.attempt-01.md') -Force

    Write-Utf8File (Join-Path $gateRoot 'source/task-evidence/WHITESPACE.attempt-01.md') " `r`n`t "
    Assert-Checker (Invoke-Checker 'gate-w' @('-PreTag')) $false 'TASK_EVIDENCE_INVALID file=source/task-evidence/WHITESPACE.attempt-01.md' 'whitespace-evidence'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'source/task-evidence/WHITESPACE.attempt-01.md') -Force

    Write-Utf8File (Join-Path $gateRoot 'source/task-evidence/PLACEHOLDER.attempt-01.md') '# TODO'
    Assert-Checker (Invoke-Checker 'gate-w' @('-PreTag')) $false 'TASK_EVIDENCE_INVALID file=source/task-evidence/PLACEHOLDER.attempt-01.md' 'placeholder-evidence'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'source/task-evidence/PLACEHOLDER.attempt-01.md') -Force

    Write-Utf8File (Join-Path $gateRoot 'source/task-evidence/invalid.md') 'invalid name'
    Assert-Checker (Invoke-Checker 'gate-w' @('-PreTag')) $false 'TASK_EVIDENCE_INVALID file=source/task-evidence/invalid.md' 'invalid-evidence-name'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'source/task-evidence/invalid.md') -Force

    Write-Utf8File (Join-Path $gateRoot 'source/UNAPPROVED.md') (Get-EvidenceBody 'outside approved source')
    Assert-Checker (Invoke-Checker 'gate-w' @('-PreTag')) $false 'UNKNOWN_ARCHIVE_FILE file=source/UNAPPROVED.md' 'unknown-source-file'
    Remove-Item -LiteralPath (Join-Path $gateRoot 'source/UNAPPROVED.md') -Force

    New-Item -ItemType Directory -Path (Join-Path $gateRoot 'other') -Force | Out-Null
    Write-Utf8File (Join-Path $gateRoot 'other/README.md') (Get-EvidenceBody 'duplicate archive entry')
    Assert-Checker (Invoke-Checker 'gate-w' @('-PreTag')) $false 'DUPLICATE_ROLE role=archive-entry' 'duplicate-top-level-archive-entry'
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
