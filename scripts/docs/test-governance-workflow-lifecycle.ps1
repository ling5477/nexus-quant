<#
.SYNOPSIS
Regresses lifecycle, authority, evidence paths, and release checks in disposable repositories.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'governance-workflow-lib.ps1')
$contractPath = Join-Path $PSScriptRoot 'governance-workflow-contract.json'
$contract = Get-GovernanceWorkflowContract $contractPath
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("nq-governance-lifecycle-" + [guid]::NewGuid().ToString('N'))
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Assert-Condition { param([bool]$Condition,[string]$Message); if (-not $Condition) { throw "ASSERTION_FAILED $Message" } }
function Write-Utf8File {
    param([string]$Path,[string]$Content)
    [System.IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    [System.IO.File]::WriteAllText($Path,$Content,$utf8NoBom)
}
function Assert-Transition {
    param([string]$Lifecycle,[string]$From,[string]$To,[bool]$Expected,[string]$Scenario)
    $actual=Test-GovernanceLifecycleTransition $contract $Lifecycle $From $To
    Assert-Condition ($actual -eq $Expected) "transition=$Scenario expected=$Expected actual=$actual"
    Write-Output "PASS fixture=$Scenario lifecycle=$Lifecycle from=$From to=$To allowed=$actual"
}
function Invoke-Checker {
    param([string]$Script,[string[]]$Arguments,[string]$WorkingRoot)
    $previous=$ErrorActionPreference; $ErrorActionPreference='Continue'
    Push-Location $WorkingRoot
    try { $output=& powershell -NoProfile -ExecutionPolicy Bypass -File $Script @Arguments 2>&1; $exit=$LASTEXITCODE }
    finally { Pop-Location; $ErrorActionPreference=$previous }
    return [pscustomobject]@{ExitCode=$exit;Text=(($output|ForEach-Object{$_.ToString()})-join "`n")}
}
function Assert-Checker {
    param([object]$Result,[bool]$Pass,[string]$Expected,[string]$Scenario)
    if ($Pass -and $Result.ExitCode -ne 0) { throw "UNEXPECTED_FAILURE scenario=$Scenario output=$($Result.Text)" }
    if (-not $Pass -and $Result.ExitCode -eq 0) { throw "UNEXPECTED_SUCCESS scenario=$Scenario output=$($Result.Text)" }
    if ($Result.Text -notmatch [regex]::Escape($Expected)) { throw "OUTPUT_MISMATCH scenario=$Scenario expected=$Expected output=$($Result.Text)" }
    Write-Output "PASS fixture=$Scenario exit=$($Result.ExitCode) expected=$Expected"
}
function Invoke-FixtureGit {
    param([string]$Root,[string[]]$Arguments)
    $previous=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $output=& git -C $Root @Arguments 2>&1; $exit=$LASTEXITCODE
    $ErrorActionPreference=$previous
    if ($exit -ne 0) { throw "FIXTURE_GIT_FAILED args=$($Arguments -join ' ') output=$($output -join ' ')" }
    return (($output|ForEach-Object{$_.ToString()})-join "`n").Trim()
}

function Write-AuthorityFixture {
    param([string]$Root,[string]$Status,[string]$Action,[string]$Commit,[string]$Ci,[string]$ActiveStatus='IN_PROGRESS|NOT_FROZEN')
    $display = switch ($Status) {
        'NOT_STARTED' { 'NOT STARTED' }
        'IMPLEMENTED|SELF_REVIEWED' { 'IMPLEMENTED / SELF-REVIEWED' }
        'IMPLEMENTED|PENDING_REVIEW' { 'IMPLEMENTED / PENDING REVIEW' }
        'REVIEW_ACCEPTED|READY_TO_COMMIT' { 'REVIEW ACCEPTED / READY TO COMMIT' }
        'COMMITTED|CI_PENDING' { 'COMMITTED / CI PENDING' }
        'ACCEPTED|CI_GREEN' { 'ACCEPTED / CI GREEN' }
        default { $Status }
    }
    $content = @"
# Fixture Status
<!-- nq-current-authority:start
authority_schema=3
last_frozen_gate=GateV
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
last_frozen_gate_tag=nq-gatev-freeze
last_frozen_gate_commit=1111111111111111111111111111111111111111
active_gate=GateW
active_gate_status=$ActiveStatus
accepted_batch=GateV-FREEZE
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=2222222222222222222222222222222222222222
accepted_batch_acceptance_head=3333333333333333333333333333333333333333
accepted_batch_ci_run=100
work_batch=GateW-FIXTURE
work_batch_status=$Status
work_batch_commit=$Commit
work_batch_ci_run=$Ci
next_action=$Action
live=DISABLED
shadow_trading=NOT_ENABLED
ai=NOT_STARTED
dh_runtime=NOT_INTEGRATED
integration_runtime=NOT_STARTED
real_provider=NOT_IMPLEMENTED
private_trading=NOT_IMPLEMENTED
nq-current-authority:end -->

- GateV: FROZEN / ACCEPTED / TAGGED.
- GateW: IN PROGRESS / NOT FROZEN.
- GateV-FREEZE: ACCEPTED / CI GREEN.
- GateW-FIXTURE: $display.
- Next action: $Action.
"@
    Write-Utf8File (Join-Path $Root 'docs/current/STATUS.md') $content
}

try {
    $unsupportedContractPath = Join-Path $tempRoot 'unsupported-contract.json'
    $unsupportedContract = (Get-Content -Raw $contractPath).Replace('"schemaVersion": "1.0.0"', '"schemaVersion": "9.0.0"')
    Write-Utf8File $unsupportedContractPath $unsupportedContract
    $unsupportedRejected = $false
    try { $null = Get-GovernanceWorkflowContract $unsupportedContractPath } catch { $unsupportedRejected = $true }
    Assert-Condition $unsupportedRejected 'unsupported contract version was accepted'
    Write-Output 'PASS fixture=unsupported-contract-version-rejected'

    # Ordinary lifecycle: review states are intentionally absent.
    Assert-Transition 'ordinary' 'NOT_STARTED' 'IMPLEMENTED|SELF_REVIEWED' $true 'ordinary-not-started'
    Assert-Transition 'ordinary' 'IMPLEMENTED|SELF_REVIEWED' 'COMMITTED|CI_PENDING' $true 'ordinary-self-reviewed'
    Assert-Transition 'ordinary' 'COMMITTED|CI_PENDING' 'ACCEPTED|CI_GREEN' $true 'ordinary-ci-green'
    Assert-Transition 'ordinary' 'IMPLEMENTED|SELF_REVIEWED' 'REVIEW_ACCEPTED|READY_TO_COMMIT' $false 'ordinary-review-not-required'
    Assert-Transition 'ordinary' 'ACCEPTED|CI_GREEN' 'COMMITTED|CI_PENDING' $false 'ordinary-regression-rejected'

    # High-risk lifecycle requires the dedicated review edge.
    Assert-Transition 'highRisk' 'NOT_STARTED' 'IMPLEMENTED|PENDING_REVIEW' $true 'high-risk-pending-review'
    Assert-Transition 'highRisk' 'IMPLEMENTED|PENDING_REVIEW' 'REVIEW_ACCEPTED|READY_TO_COMMIT' $true 'high-risk-review-accepted'
    Assert-Transition 'highRisk' 'REVIEW_ACCEPTED|READY_TO_COMMIT' 'COMMITTED|CI_PENDING' $true 'high-risk-commit'
    Assert-Transition 'highRisk' 'COMMITTED|CI_PENDING' 'ACCEPTED|CI_GREEN' $true 'high-risk-ci-green'
    Assert-Transition 'highRisk' 'IMPLEMENTED|PENDING_REVIEW' 'ACCEPTED|CI_GREEN' $false 'high-risk-direct-accepted-rejected'
    Assert-Transition 'highRisk' 'IMPLEMENTED|SELF_REVIEWED' 'ACCEPTED|CI_GREEN' $false 'high-risk-invalid-combination'

    Assert-Condition (-not [bool]$contract.lifecycles.freeze.authorityReviewCommitRequired) 'freeze authority review commit must not be required'
    Assert-Condition (@($contract.lifecycles.freeze.candidateEntryStatuses) -contains 'IMPLEMENTED|PENDING_REVIEW') 'freeze pending-review candidate entry missing'
    Write-Output 'PASS fixture=freeze-without-review-authority-commit'

    foreach ($path in @(
        'docs/current/evidence/gate-w/README.md',
        'docs/current/evidence/gate-w/NQ-GOVERNANCE-WORKFLOW-CONSOLIDATION.attempt-01.md',
        'docs/current/evidence/gate-w/NQ-GOVERNANCE-WORKFLOW-CONSOLIDATION-REVIEW.attempt-01.md'
    )) {
        Assert-Condition (Test-GovernanceEvidencePath $contract 'current' $path) "current evidence rejected path=$path"
        $currentItem = Get-Item -LiteralPath (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path $path)
        Assert-Condition (Test-GovernanceEvidenceItem $contract $currentItem 'current' $path) "current evidence item rejected path=$path"
        Write-Output "PASS fixture=current-evidence path=$path"
    }
    foreach ($path in @('source/task-evidence/README.md','source/task-evidence/NQ-GATEW-TASK.attempt-01.md')) {
        Assert-Condition (Test-GovernanceEvidencePath $contract 'archive' $path) "archive evidence rejected path=$path"; Write-Output "PASS fixture=archive-evidence path=$path"
    }
    foreach ($path in @(
        '../task.attempt-01.md',
        'C:/repo/docs/current/evidence/gate-w/NQ-ABSOLUTE.attempt-01.md',
        'source/task-evidence/../escape.attempt-01.md',
        'source/task-evidence/bad.md',
        'source/task-evidence/NQ-EXECUTABLE.attempt-01.ps1',
        'source/task-evidence/%2e%2e.attempt-01.md',
        'source/task-evidence/%252e%252e.attempt-01.md'
    )) {
        Assert-Condition (-not (Test-GovernanceEvidencePath $contract 'archive' $path)) "invalid evidence accepted path=$path"; Write-Output "PASS fixture=evidence-path-rejected path=$path"
    }
    Assert-Condition (Test-GovernanceEvidencePath $contract 'current' 'docs\current\evidence\gate-w\NQ-GOVERNANCE-WORKFLOW-CONSOLIDATION-REVIEW.attempt-01.md') 'review evidence path or Windows separator rejected'
    Assert-Condition (Test-GovernanceEvidencePath $contract 'archive' 'source/task-evidence/NQ-GATEW-TASK.attempt-02.md') 'second attempt rejected'
    Write-Output 'PASS fixture=evidence-review-path-windows-separator-and-second-attempt'
    $symlinkLike=[pscustomobject]@{Attributes=[System.IO.FileAttributes]::ReparsePoint;PSIsContainer=$false;Length=10}
    Assert-Condition (-not (Test-GovernanceEvidenceItem $contract $symlinkLike 'archive' 'source/task-evidence/NQ-SYMLINK.attempt-01.md')) 'symlink evidence accepted'
    Write-Output 'PASS fixture=symlink-evidence-rejected'

    # Exercise the real authority checker against snapshot combinations without Git/tag/GitHub dependencies.
    $authorityRoot=Join-Path $tempRoot 'authority-repo'
    $authorityScripts=Join-Path $authorityRoot 'scripts/docs'
    New-Item -ItemType Directory -Path $authorityScripts -Force | Out-Null
    Copy-Item $contractPath,(Join-Path $PSScriptRoot 'governance-workflow-lib.ps1'),(Join-Path $PSScriptRoot 'check-current-authority.ps1') -Destination $authorityScripts
    $authorityChecker=Join-Path $authorityScripts 'check-current-authority.ps1'
    foreach ($case in @(
        @{Status='NOT_STARTED';Action='NQ-GATEW-FIXTURE-IMPLEMENTATION';Commit='NONE';Ci='NOT_RUN';Name='authority-not-started'},
        @{Status='IMPLEMENTED|SELF_REVIEWED';Action='NQ-GATEW-FIXTURE-COMMIT-AND-PUSH';Commit='UNCOMMITTED';Ci='NOT_RUN';Name='authority-self-reviewed'},
        @{Status='IMPLEMENTED|PENDING_REVIEW';Action='NQ-GATEW-FIXTURE-REVIEW';Commit='UNCOMMITTED';Ci='NOT_RUN';Name='authority-pending-review'},
        @{Status='REVIEW_ACCEPTED|READY_TO_COMMIT';Action='NQ-GATEW-FIXTURE-COMMIT-AND-PUSH';Commit='UNCOMMITTED';Ci='NOT_RUN';Name='authority-review-accepted'},
        @{Status='COMMITTED|CI_PENDING';Action='NQ-GATEW-FIXTURE-WAIT-CI';Commit='4444444444444444444444444444444444444444';Ci='PENDING';Name='authority-ci-pending'},
        @{Status='ACCEPTED|CI_GREEN';Action='NQ-GATEW-FIXTURE-POST-CI-ACTIVE-AUTHORITY-SYNC';Commit='4444444444444444444444444444444444444444';Ci='101';Name='authority-ci-green'}
    )) {
        Write-AuthorityFixture $authorityRoot $case.Status $case.Action $case.Commit $case.Ci
        Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $true 'PASS / CURRENT_AUTHORITY_CONSISTENT' $case.Name
    }
    Write-AuthorityFixture $authorityRoot 'IMPLEMENTED|SELF_REVIEWED' 'NQ-GATEW-FIXTURE-REVIEW' 'UNCOMMITTED' 'NOT_RUN'
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false 'NEXT_ACTION_TYPE_MISMATCH' 'authority-illegal-status-action'
    Write-AuthorityFixture $authorityRoot 'NOT_STARTED' 'NQ-GATEW-FIXTURE-IMPLEMENTATION' 'NONE' 'NOT_RUN' 'PLAN|NOT_STARTED'
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false 'ACTIVE_GATE_STATUS_COMBINATION_INVALID' 'active-gate-plan-status-rejected'

    # Release checker uses a local bare remote and a PATH-scoped gh fixture; no real tag or GitHub state is touched.
    $releaseRoot=Join-Path $tempRoot 'release-repo'
    $remoteRoot=Join-Path $tempRoot 'remote.git'
    New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
    Invoke-FixtureGit $releaseRoot @('init','-b','dev') | Out-Null
    Invoke-FixtureGit $releaseRoot @('config','user.email','fixture@nexusquant.invalid') | Out-Null
    Invoke-FixtureGit $releaseRoot @('config','user.name','NexusQuant Fixture') | Out-Null
    Write-Utf8File (Join-Path $releaseRoot 'fixture.txt') 'release-1'
    Invoke-FixtureGit $releaseRoot @('add','fixture.txt') | Out-Null
    Invoke-FixtureGit $releaseRoot @('commit','-m','fixture release one') | Out-Null
    $commitOne=Invoke-FixtureGit $releaseRoot @('rev-parse','HEAD')
    & git init --bare $remoteRoot | Out-Null
    Invoke-FixtureGit $releaseRoot @('remote','add','origin',$remoteRoot) | Out-Null
    Invoke-FixtureGit $releaseRoot @('push','-u','origin','dev') | Out-Null
    $releaseScripts=Join-Path $releaseRoot 'scripts/docs'
    New-Item -ItemType Directory -Path $releaseScripts -Force | Out-Null
    Copy-Item $contractPath,(Join-Path $PSScriptRoot 'governance-workflow-lib.ps1'),(Join-Path $PSScriptRoot 'check-gate-release.ps1') -Destination $releaseScripts
    $releaseChecker=Join-Path $releaseScripts 'check-gate-release.ps1'
    Write-Utf8File (Join-Path $releaseRoot 'docs/current/STATUS.md') @"
<!-- nq-current-authority:start
last_frozen_gate=GateZ
last_frozen_gate_tag=nq-gatez-freeze
last_frozen_gate_commit=$commitOne
nq-current-authority:end -->
"@
    $bin=Join-Path $tempRoot 'bin'; New-Item -ItemType Directory -Path $bin -Force | Out-Null
    Write-Utf8File (Join-Path $bin 'gh.cmd') '@echo off
echo [{"databaseId":42,"workflowName":"NQ CI Baseline","status":"completed","conclusion":"success","headSha":"%NQ_FIXTURE_CI_SHA%"}]'
    $oldPath=$env:PATH; $env:PATH="$bin;$oldPath"; $env:NQ_FIXTURE_CI_SHA=$commitOne
    try {
        Invoke-FixtureGit $releaseRoot @('tag','-a','nq-gatez-freeze','-m','fixture annotated',$commitOne) | Out-Null
        Invoke-FixtureGit $releaseRoot @('push','origin','refs/tags/nq-gatez-freeze') | Out-Null
        Assert-Checker (Invoke-Checker $releaseChecker @('-Gate','gate-z','-ExpectedCommit',$commitOne) $releaseRoot) $true 'PASS / GATE_RELEASE_VALID' 'release-annotated-positive'
        Assert-Checker (Invoke-Checker $releaseChecker @('-Gate','gate-z','-ExpectedTag','nq-gatey-freeze','-ExpectedCommit',$commitOne) $releaseRoot) $false 'TAG_NAME_INVALID' 'release-cross-gate-tag-rejected'

        Invoke-FixtureGit $releaseRoot @('push','origin',':refs/tags/nq-gatez-freeze') | Out-Null
        Invoke-FixtureGit $releaseRoot @('tag','-d','nq-gatez-freeze') | Out-Null
        Invoke-FixtureGit $releaseRoot @('tag','nq-gatez-freeze',$commitOne) | Out-Null
        Invoke-FixtureGit $releaseRoot @('push','origin','refs/tags/nq-gatez-freeze') | Out-Null
        Assert-Checker (Invoke-Checker $releaseChecker @('-Gate','gate-z','-ExpectedCommit',$commitOne) $releaseRoot) $false 'TAG_NOT_ANNOTATED' 'release-lightweight-rejected'

        Invoke-FixtureGit $releaseRoot @('push','origin',':refs/tags/nq-gatez-freeze') | Out-Null
        Invoke-FixtureGit $releaseRoot @('tag','-d','nq-gatez-freeze') | Out-Null
        Write-Utf8File (Join-Path $releaseRoot 'fixture.txt') 'release-2'
        Invoke-FixtureGit $releaseRoot @('add','fixture.txt') | Out-Null
        Invoke-FixtureGit $releaseRoot @('commit','-m','fixture release two') | Out-Null
        $commitTwo=Invoke-FixtureGit $releaseRoot @('rev-parse','HEAD')
        Invoke-FixtureGit $releaseRoot @('push','origin','dev') | Out-Null
        Assert-Checker (Invoke-Checker $releaseChecker @('-Gate','gate-z','-ExpectedCommit',$commitTwo) $releaseRoot) $false 'EXPECTED_COMMIT_CONFLICT' 'release-status-commit-conflict-rejected'
        Invoke-FixtureGit $releaseRoot @('tag','-a','nq-gatez-freeze','-m','wrong target',$commitTwo) | Out-Null
        Invoke-FixtureGit $releaseRoot @('push','origin','refs/tags/nq-gatez-freeze') | Out-Null
        Assert-Checker (Invoke-Checker $releaseChecker @('-Gate','gate-z','-ExpectedCommit',$commitOne) $releaseRoot) $false 'TAG_TARGET_MISMATCH' 'release-wrong-target-rejected'

        Invoke-FixtureGit $releaseRoot @('push','origin',':refs/tags/nq-gatez-freeze') | Out-Null
        Invoke-FixtureGit $releaseRoot @('tag','-d','nq-gatez-freeze') | Out-Null
        Invoke-FixtureGit $releaseRoot @('tag','-a','nq-gatez-freeze','-m','local only',$commitOne) | Out-Null
        Assert-Checker (Invoke-Checker $releaseChecker @('-Gate','gate-z','-ExpectedCommit',$commitOne) $releaseRoot) $false 'REMOTE_TAG_MISSING' 'release-remote-missing-rejected'

        Invoke-FixtureGit $releaseRoot @('push','origin','refs/tags/nq-gatez-freeze') | Out-Null
        $env:NQ_FIXTURE_CI_SHA=$commitTwo
        Assert-Checker (Invoke-Checker $releaseChecker @('-Gate','gate-z','-ExpectedCommit',$commitOne) $releaseRoot) $false 'EXACT_HEAD_CI_NOT_GREEN' 'release-exact-head-ci-mismatch'

        $env:NQ_FIXTURE_CI_SHA=$commitOne
        Invoke-FixtureGit $remoteRoot @('update-ref','refs/heads/dev',$commitOne) | Out-Null
        Assert-Checker (Invoke-Checker $releaseChecker @('-Gate','gate-z','-ExpectedCommit',$commitOne) $releaseRoot) $false 'REMOTE_BRANCH_NOT_ALIGNED' 'release-stale-tracking-ref-rejected'
        Invoke-FixtureGit $remoteRoot @('update-ref','refs/heads/dev',$commitTwo) | Out-Null
    }
    finally { $env:PATH=$oldPath; Remove-Item Env:NQ_FIXTURE_CI_SHA -ErrorAction SilentlyContinue }

    Write-Output 'PASS / GOVERNANCE_LIFECYCLE_REGRESSION'
    Write-Output 'PASS / TASK_EVIDENCE_POLICY_VALID'
}
finally {
    $tempBase=[System.IO.Path]::GetTempPath()
    if (Test-Path -LiteralPath $tempRoot) {
        Assert-Condition ($tempRoot.StartsWith($tempBase,[System.StringComparison]::OrdinalIgnoreCase)) "cleanup escaped temp path=$tempRoot"
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
