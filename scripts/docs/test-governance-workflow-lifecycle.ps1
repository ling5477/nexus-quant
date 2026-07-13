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
function Assert-ContextTransition {
    param(
        [string]$Lifecycle,[string]$From,[string]$To,
        [string]$FromCommit,[string]$FromCi,[string]$ToCommit,[string]$ToCi,
        [bool]$AuthorityCatchUp,[bool]$Expected,[string]$Scenario,[object]$Context=$null
    )
    $actual=Test-GovernanceLifecycleTransitionContext $contract $Lifecycle $From $To $FromCommit $FromCi $ToCommit $ToCi $AuthorityCatchUp $Context
    Assert-Condition ($actual -eq $Expected) "transition-context=$Scenario expected=$Expected actual=$actual"
    Write-Output "PASS fixture=$Scenario lifecycle=$Lifecycle from=$From to=$To catchUp=$AuthorityCatchUp allowed=$actual"
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
    param(
        [string]$Root,[string]$Status,[string]$Action,[string]$Commit,[string]$Ci,
        [string]$ActiveStatus='IN_PROGRESS|NOT_FROZEN',
        [string]$AcceptedBatch='GateV-FREEZE',
        [string]$WorkBatch='GateW-FIXTURE'
    )
    $display = switch ($Status) {
        'NOT_STARTED' { 'NOT STARTED' }
        'IMPLEMENTED|SELF_REVIEWED' { 'IMPLEMENTED / SELF-REVIEWED' }
        'IMPLEMENTED|PENDING_REVIEW' { 'IMPLEMENTED / PENDING REVIEW' }
        'REVIEW_ACCEPTED|READY_TO_COMMIT' { 'REVIEW ACCEPTED / READY TO COMMIT' }
        'COMMITTED|CI_PENDING' { 'COMMITTED / CI PENDING' }
        'COMMITTED|CI_FAILED|FIX_REQUIRED' { 'COMMITTED / CI FAILED / FIX REQUIRED' }
        'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' { 'COMMITTED / CI GREEN / CONTINUE REQUIRED' }
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
accepted_batch=$AcceptedBatch
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=2222222222222222222222222222222222222222
accepted_batch_acceptance_head=3333333333333333333333333333333333333333
accepted_batch_ci_run=100
work_batch=$WorkBatch
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
- ${AcceptedBatch}: ACCEPTED / CI GREEN.
- ${WorkBatch}: $display.
- Next action: $Action.
"@
    Write-Utf8File (Join-Path $Root 'docs/current/STATUS.md') $content
}

try {
    $unsupportedContractPath = Join-Path $tempRoot 'unsupported-contract.json'
    $unsupportedContract = (Get-Content -Raw $contractPath).Replace('"schemaVersion": "1.2.0"', '"schemaVersion": "9.0.0"')
    Write-Utf8File $unsupportedContractPath $unsupportedContract
    $unsupportedRejected = $false
    try { $null = Get-GovernanceWorkflowContract $unsupportedContractPath } catch { $unsupportedRejected = $true }
    Assert-Condition $unsupportedRejected 'unsupported contract version was accepted'
    Write-Output 'PASS fixture=unsupported-contract-version-rejected'

    $continuationStatusCount=@($contract.authority.workBatchStatuses | Where-Object { $_ -ceq 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' }).Count
    Assert-Condition ($continuationStatusCount -eq 1) "green continuation canonical status count=$continuationStatusCount"
    Assert-Condition ((Get-GovernanceExpectedNextActionType $contract 'committed|ci_green|continue_required') -ceq 'UNKNOWN') 'lowercase continuation status alias was accepted by library'
    Assert-Condition (-not (Test-GovernanceLifecycleTransition $contract 'highRisk' 'committed|ci_pending' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED')) 'lowercase lifecycle status was accepted by library'
    Write-Output 'PASS fixture=green-continuation-unique-and-strict-token'

    # Ordinary lifecycle: review states are intentionally absent.
    Assert-Transition 'ordinary' 'NOT_STARTED' 'IMPLEMENTED|SELF_REVIEWED' $true 'ordinary-not-started'
    Assert-Transition 'ordinary' 'IMPLEMENTED|SELF_REVIEWED' 'COMMITTED|CI_PENDING' $true 'ordinary-self-reviewed'
    Assert-Transition 'ordinary' 'COMMITTED|CI_PENDING' 'COMMITTED|CI_FAILED|FIX_REQUIRED' $true 'ordinary-ci-failed'
    Assert-Transition 'ordinary' 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'COMMITTED|CI_PENDING' $true 'ordinary-fix-committed'
    Assert-Transition 'ordinary' 'COMMITTED|CI_PENDING' 'ACCEPTED|CI_GREEN' $true 'ordinary-ci-green'
    Assert-Transition 'ordinary' 'IMPLEMENTED|SELF_REVIEWED' 'REVIEW_ACCEPTED|READY_TO_COMMIT' $false 'ordinary-review-not-required'
    Assert-Transition 'ordinary' 'ACCEPTED|CI_GREEN' 'COMMITTED|CI_PENDING' $false 'ordinary-regression-rejected'

    # High-risk lifecycle requires the dedicated review edge.
    Assert-Transition 'highRisk' 'NOT_STARTED' 'IMPLEMENTED|PENDING_REVIEW' $true 'high-risk-pending-review'
    Assert-Transition 'highRisk' 'IMPLEMENTED|PENDING_REVIEW' 'REVIEW_ACCEPTED|READY_TO_COMMIT' $true 'high-risk-review-accepted'
    Assert-Transition 'highRisk' 'REVIEW_ACCEPTED|READY_TO_COMMIT' 'COMMITTED|CI_PENDING' $true 'high-risk-commit'
    Assert-Transition 'highRisk' 'COMMITTED|CI_PENDING' 'COMMITTED|CI_FAILED|FIX_REQUIRED' $true 'high-risk-ci-failed'
    Assert-Transition 'highRisk' 'REVIEW_ACCEPTED|READY_TO_COMMIT' 'COMMITTED|CI_FAILED|FIX_REQUIRED' $true 'high-risk-authority-catch-up'
    Assert-Transition 'highRisk' 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'COMMITTED|CI_PENDING' $true 'high-risk-fix-committed'
    Assert-Transition 'highRisk' 'COMMITTED|CI_PENDING' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $true 'high-risk-ci-pending-to-green-continuation'
    Assert-Transition 'highRisk' 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $true 'high-risk-post-fix-green-continuation'
    Assert-Transition 'highRisk' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' 'IMPLEMENTED|PENDING_REVIEW' $true 'high-risk-green-continuation-to-pending-review'
    Assert-Transition 'highRisk' 'COMMITTED|CI_PENDING' 'ACCEPTED|CI_GREEN' $true 'high-risk-ci-green'
    Assert-Transition 'highRisk' 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'ACCEPTED|CI_GREEN' $false 'high-risk-failed-direct-green-rejected'
    Assert-Transition 'highRisk' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' 'ACCEPTED|CI_GREEN' $false 'high-risk-green-continuation-direct-accepted-rejected'
    Assert-Transition 'highRisk' 'IMPLEMENTED|PENDING_REVIEW' 'ACCEPTED|CI_GREEN' $false 'high-risk-direct-accepted-rejected'
    Assert-Transition 'highRisk' 'IMPLEMENTED|SELF_REVIEWED' 'ACCEPTED|CI_GREEN' $false 'high-risk-invalid-combination'

    $failedCommit='4444444444444444444444444444444444444444'
    $fixCommit='5555555555555555555555555555555555555555'
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_PENDING' 'COMMITTED|CI_FAILED|FIX_REQUIRED' $failedCommit 'PENDING' $failedCommit '101' $false $true 'ci-failure-context'
    Assert-ContextTransition 'highRisk' 'REVIEW_ACCEPTED|READY_TO_COMMIT' 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'UNCOMMITTED' 'NOT_RUN' $failedCommit '101' $true $true 'authority-catch-up-context'
    Assert-ContextTransition 'highRisk' 'REVIEW_ACCEPTED|READY_TO_COMMIT' 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'UNCOMMITTED' 'NOT_RUN' $failedCommit '101' $false $false 'authority-catch-up-flag-required'
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'COMMITTED|CI_PENDING' $failedCommit '101' $fixCommit 'PENDING' $false $true 'ci-fix-new-commit-context'
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'COMMITTED|CI_PENDING' $failedCommit '101' $failedCommit 'PENDING' $false $false 'ci-fix-same-commit-rejected'
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'ACCEPTED|CI_GREEN' $failedCommit '101' $failedCommit '102' $false $false 'ci-failed-direct-green-context-rejected'

    $continuationAction='NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW-ATTEMPT-02'
    $pendingReviewAction='NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-IMPLEMENTATION-REVIEW'
    $successEvidence=[pscustomobject]@{exactHeadMatch=$true;ciConclusion='success'}
    $pendingToContinuationContext=[pscustomobject]@{
        fromWorkBatch='GateW-3';toWorkBatch='GateW-3';fromAcceptedBatch='GateW-2';toAcceptedBatch='GateW-2'
        toNextAction=$continuationAction;externalEvidence=$successEvidence
    }
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_PENDING' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $fixCommit 'PENDING' $fixCommit '102' $false $true 'ci-pending-green-continuation-context' $pendingToContinuationContext
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_PENDING' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $fixCommit '102' $fixCommit '102' $false $true 'ci-bound-run-green-continuation-context' $pendingToContinuationContext
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_PENDING' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $fixCommit '103' $fixCommit '102' $false $false 'ci-pending-run-change-rejected' $pendingToContinuationContext

    $reconciliationContext=[pscustomobject]@{
        mode='POST_FIX_CI_SUCCESS_RECONCILIATION'
        fromWorkBatch='GateW-3';toWorkBatch='GateW-3';fromAcceptedBatch='GateW-2';toAcceptedBatch='GateW-2'
        toNextAction=$continuationAction;externalEvidence=$successEvidence
    }
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $failedCommit '101' $fixCommit '102' $true $true 'post-fix-green-continuation-context' $reconciliationContext
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' '54c7bdd2caee5602441ce983b33c4cd2466ee263' '29253811976' 'fd6a8b2044891fa7edfcba7b5a31cd6dc8636b28' '29260881801' $true $true 'post-fix-green-continuation-real-gatew3' $reconciliationContext
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $failedCommit '101' $failedCommit '102' $true $false 'post-fix-green-continuation-same-commit-rejected' $reconciliationContext
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $failedCommit '101' $fixCommit '101' $true $false 'post-fix-green-continuation-same-run-rejected' $reconciliationContext

    $missingModeContext=[pscustomobject]@{
        fromWorkBatch='GateW-3';toWorkBatch='GateW-3';fromAcceptedBatch='GateW-2';toAcceptedBatch='GateW-2'
        toNextAction=$continuationAction;externalEvidence=$successEvidence
    }
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $failedCommit '101' $fixCommit '102' $true $false 'post-fix-green-continuation-mode-required' $missingModeContext
    $badExactHeadContext=[pscustomobject]@{
        mode='POST_FIX_CI_SUCCESS_RECONCILIATION'
        fromWorkBatch='GateW-3';toWorkBatch='GateW-3';fromAcceptedBatch='GateW-2';toAcceptedBatch='GateW-2'
        toNextAction=$continuationAction;externalEvidence=[pscustomobject]@{exactHeadMatch=$false;ciConclusion='success'}
    }
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $failedCommit '101' $fixCommit '102' $true $false 'post-fix-green-continuation-exact-head-required' $badExactHeadContext
    $badConclusionContext=[pscustomobject]@{
        mode='POST_FIX_CI_SUCCESS_RECONCILIATION'
        fromWorkBatch='GateW-3';toWorkBatch='GateW-3';fromAcceptedBatch='GateW-2';toAcceptedBatch='GateW-2'
        toNextAction=$continuationAction;externalEvidence=[pscustomobject]@{exactHeadMatch=$true;ciConclusion='failure'}
    }
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $failedCommit '101' $fixCommit '102' $true $false 'post-fix-green-continuation-success-conclusion-required' $badConclusionContext

    $continuationToPendingContext=[pscustomobject]@{
        fromWorkBatch='GateW-3';toWorkBatch='GateW-3';fromAcceptedBatch='GateW-2';toAcceptedBatch='GateW-2'
        toNextAction=$pendingReviewAction
    }
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' 'IMPLEMENTED|PENDING_REVIEW' $fixCommit '102' 'UNCOMMITTED' 'NOT_RUN' $false $true 'green-continuation-to-pending-review-context' $continuationToPendingContext
    $changedWorkBatchContext=[pscustomobject]@{
        fromWorkBatch='GateW-3';toWorkBatch='GateW-4';fromAcceptedBatch='GateW-2';toAcceptedBatch='GateW-2'
        toNextAction='NQ-GATEW-4-DRY-RUN-ORDER-PREVIEW-IMPLEMENTATION-REVIEW'
    }
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' 'IMPLEMENTED|PENDING_REVIEW' $fixCommit '102' 'UNCOMMITTED' 'NOT_RUN' $false $false 'green-continuation-work-batch-change-rejected' $changedWorkBatchContext
    $changedAcceptedBatchContext=[pscustomobject]@{
        fromWorkBatch='GateW-3';toWorkBatch='GateW-3';fromAcceptedBatch='GateW-2';toAcceptedBatch='GateW-3'
        toNextAction=$pendingReviewAction
    }
    Assert-ContextTransition 'highRisk' 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' 'IMPLEMENTED|PENDING_REVIEW' $fixCommit '102' 'UNCOMMITTED' 'NOT_RUN' $false $false 'green-continuation-accepted-batch-change-rejected' $changedAcceptedBatchContext

    Assert-Condition (-not [bool]$contract.lifecycles.freeze.authorityReviewCommitRequired) 'freeze authority review commit must not be required'
    Assert-Condition (@($contract.lifecycles.freeze.candidateEntryStatuses) -contains 'IMPLEMENTED|PENDING_REVIEW') 'freeze pending-review candidate entry missing'
    Assert-Condition (@($contract.lifecycles.freeze.candidateEntryStatuses) -cnotcontains 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED') 'green continuation was accepted as freeze/archive candidate'
    Assert-Condition (@($contract.authority.acceptedBatchStatuses) -cnotcontains 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED') 'green continuation was accepted as release-ready batch status'
    Write-Output 'PASS fixture=freeze-without-review-authority-commit'
    Write-Output 'PASS fixture=green-continuation-archive-freeze-readiness-rejected'
    Write-Output 'PASS fixture=green-continuation-release-readiness-rejected'

    foreach ($path in @(
        'docs/current/evidence/gate-w/README.md',
        'docs/current/evidence/gate-w/NQ-GOVERNANCE-WORKFLOW-CONSOLIDATION.attempt-01.md',
        'docs/current/evidence/gate-w/NQ-GOVERNANCE-WORKFLOW-CONSOLIDATION-REVIEW.attempt-01.md',
        'docs/current/evidence/gate-w/NQ-GOVERNANCE-POST-FIX-CI-GREEN-CONTINUATION-HARDENING.attempt-01.md'
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

    foreach ($actionCase in @(
        @{Action='NQ-GATEW-3-CI-BLOCKER-FIX';Name='authority-ci-blocker-fix'},
        @{Action='NQ-GATEW-3-CI-BLOCKER-FIX-REVIEW';Name='authority-ci-blocker-fix-review'},
        @{Action='NQ-GATEW-3-CI-BLOCKER-FIX-COMMIT-AND-PUSH';Name='authority-ci-blocker-fix-commit'}
    )) {
        Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_FAILED|FIX_REQUIRED' $actionCase.Action $failedCommit '101' 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateW-3'
        Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $true 'PASS / CURRENT_AUTHORITY_CONSISTENT' $actionCase.Name
    }
    $uppercaseCommit='ABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD'
    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'NQ-GATEW-3-CI-BLOCKER-FIX' $uppercaseCommit '101' 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateW-3'
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $true 'PASS / CURRENT_AUTHORITY_CONSISTENT' 'failed-uppercase-hex-commit-accepted'

    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_PENDING' 'NQ-GATEW-3-WAIT-CI' $fixCommit 'PENDING' 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateW-3'
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $true 'PASS / CURRENT_AUTHORITY_CONSISTENT' 'authority-failed-to-pending-new-fix-commit'

    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $continuationAction 'fd6a8b2044891fa7edfcba7b5a31cd6dc8636b28' '29260881801' 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateW-3'
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $true 'PASS / CURRENT_AUTHORITY_CONSISTENT' 'authority-green-continuation-gatew3'
    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $continuationAction $uppercaseCommit '29260881801' 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateW-3'
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $true 'PASS / CURRENT_AUTHORITY_CONSISTENT' 'green-continuation-uppercase-hex-commit-accepted'
    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $continuationAction 'fd6a8b2044891fa7edfcba7b5a31cd6dc8636b28' '29260881801' 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateW-3'
    Assert-Checker (Invoke-Checker $authorityChecker @('-ReadinessMode','ARCHIVE_FREEZE') $authorityRoot) $false 'GATE_READINESS_STATUS_INVALID' 'green-continuation-archive-freeze-checker-rejected'
    Assert-Checker (Invoke-Checker $authorityChecker @('-ReadinessMode','RELEASE') $authorityRoot) $false 'GATE_READINESS_STATUS_INVALID' 'green-continuation-release-checker-rejected'

    Write-AuthorityFixture $authorityRoot 'IMPLEMENTED|PENDING_REVIEW' 'NQ-GATEW-FIXTURE-REVIEW' 'UNCOMMITTED' 'NOT_RUN'
    Assert-Checker (Invoke-Checker $authorityChecker @('-ReadinessMode','ARCHIVE_FREEZE') $authorityRoot) $true 'PASS / CURRENT_AUTHORITY_CONSISTENT' 'pending-review-archive-freeze-checker-positive'
    Write-AuthorityFixture $authorityRoot 'ACCEPTED|CI_GREEN' 'NQ-GATEW-FIXTURE-POST-CI-ACTIVE-AUTHORITY-SYNC' $fixCommit '102'
    Assert-Checker (Invoke-Checker $authorityChecker @('-ReadinessMode','RELEASE') $authorityRoot) $true 'PASS / CURRENT_AUTHORITY_CONSISTENT' 'accepted-release-checker-positive'

    foreach ($fieldCase in @(
        @{Commit='UNCOMMITTED';Ci='29260881801';Expected='WORK_BATCH_COMMIT_STATE_MISMATCH';Name='green-continuation-uncommitted-rejected'},
        @{Commit='NONE';Ci='29260881801';Expected='WORK_BATCH_COMMIT_STATE_MISMATCH';Name='green-continuation-none-commit-rejected'},
        @{Commit='fd6a8b2';Ci='29260881801';Expected='WORK_BATCH_COMMIT_STATE_MISMATCH';Name='green-continuation-short-commit-rejected'},
        @{Commit='gggggggggggggggggggggggggggggggggggggggg';Ci='29260881801';Expected='WORK_BATCH_COMMIT_STATE_MISMATCH';Name='green-continuation-nonhex-commit-rejected'},
        @{Commit=$fixCommit;Ci='NOT_RUN';Expected='WORK_BATCH_CI_STATE_MISMATCH';Name='green-continuation-not-run-rejected'},
        @{Commit=$fixCommit;Ci='PENDING';Expected='WORK_BATCH_CI_STATE_MISMATCH';Name='green-continuation-pending-run-rejected'},
        @{Commit=$fixCommit;Ci='NONE';Expected='WORK_BATCH_CI_STATE_MISMATCH';Name='green-continuation-none-run-rejected'},
        @{Commit=$fixCommit;Ci='abc';Expected='WORK_BATCH_CI_STATE_MISMATCH';Name='green-continuation-nonnumeric-run-rejected'},
        @{Commit=$fixCommit;Ci='-1';Expected='WORK_BATCH_CI_STATE_MISMATCH';Name='green-continuation-negative-run-rejected'},
        @{Commit=$fixCommit;Ci='0';Expected='WORK_BATCH_CI_STATE_MISMATCH';Name='green-continuation-zero-run-rejected'}
    )) {
        Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $continuationAction $fieldCase.Commit $fieldCase.Ci 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateW-3'
        Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false $fieldCase.Expected $fieldCase.Name
    }

    foreach ($fieldCase in @(
        @{Commit='UNCOMMITTED';Ci='101';Expected='WORK_BATCH_COMMIT_STATE_MISMATCH';Name='failed-uncommitted-rejected'},
        @{Commit='NONE';Ci='101';Expected='WORK_BATCH_COMMIT_STATE_MISMATCH';Name='failed-none-commit-rejected'},
        @{Commit='4444444';Ci='101';Expected='WORK_BATCH_COMMIT_STATE_MISMATCH';Name='failed-short-commit-rejected'},
        @{Commit='gggggggggggggggggggggggggggggggggggggggg';Ci='101';Expected='WORK_BATCH_COMMIT_STATE_MISMATCH';Name='failed-nonhex-commit-rejected'},
        @{Commit=$failedCommit;Ci='NOT_RUN';Expected='WORK_BATCH_CI_STATE_MISMATCH';Name='failed-not-run-rejected'},
        @{Commit=$failedCommit;Ci='PENDING';Expected='WORK_BATCH_CI_STATE_MISMATCH';Name='failed-pending-run-rejected'},
        @{Commit=$failedCommit;Ci='NONE';Expected='WORK_BATCH_CI_STATE_MISMATCH';Name='failed-none-run-rejected'},
        @{Commit=$failedCommit;Ci='abc';Expected='WORK_BATCH_CI_STATE_MISMATCH';Name='failed-nonnumeric-run-rejected'},
        @{Commit=$failedCommit;Ci='-1';Expected='WORK_BATCH_CI_STATE_MISMATCH';Name='failed-negative-run-rejected'},
        @{Commit=$failedCommit;Ci='0';Expected='WORK_BATCH_CI_STATE_MISMATCH';Name='failed-zero-run-rejected'}
    )) {
        Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'NQ-GATEW-3-CI-BLOCKER-FIX' $fieldCase.Commit $fieldCase.Ci 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateW-3'
        Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false $fieldCase.Expected $fieldCase.Name
    }
    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'NQ-GATEW-3-CI-BLOCKER-FIX' $failedCommit '' 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateW-3'
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false 'AUTHORITY_BLOCK_INVALID' 'failed-empty-run-rejected'

    foreach ($actionCase in @(
        @{Action='NQ-GATEW-3-IMPLEMENTATION';Expected='NEXT_ACTION_TYPE_MISMATCH';Name='failed-implementation-action-rejected'},
        @{Action='NQ-GATEW-4-CI-BLOCKER-FIX';Expected='NEXT_ACTION_WORK_BATCH_MISMATCH';Name='failed-next-batch-action-rejected'},
        @{Action='NQ-GATEW-3-FIX';Expected='NEXT_ACTION_TYPE_MISMATCH';Name='failed-vague-fix-action-rejected'},
        @{Action='NQ-GATEW-3-MIGRATION-FIX';Expected='NEXT_ACTION_TYPE_MISMATCH';Name='failed-migration-fix-action-rejected'},
        @{Action='NQ-GATEW-3-SECURITY-FIX';Expected='NEXT_ACTION_TYPE_MISMATCH';Name='failed-security-fix-action-rejected'},
        @{Action='NQ-GATEW-3-FREEZE';Expected='NEXT_ACTION_TYPE_MISMATCH';Name='failed-freeze-action-rejected'}
    )) {
        Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_FAILED|FIX_REQUIRED' $actionCase.Action $failedCommit '101' 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateW-3'
        Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false $actionCase.Expected $actionCase.Name
    }
    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'NQ-GATEW-3-CI-BLOCKER-FIX' $failedCommit '101' 'IN_PROGRESS|NOT_FROZEN' 'GateW-3' 'GateW-3'
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false 'accepted_batch_and_work_batch_must_differ' 'failed-current-batch-not-accepted'
    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'NQ-GATEW-3-CI-BLOCKER-FIX' $failedCommit '101' 'IN_PROGRESS|NOT_FROZEN' 'GateW-1' 'GateW-3'
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false 'CI_FAILED_ACCEPTED_BATCH_INVALID' 'failed-accepted-predecessor-required'
    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'NQ-GATEW-3-CI-BLOCKER-FIX' $failedCommit '101' 'FROZEN|ACCEPTED|TAGGED' 'GateW-2' 'GateW-3'
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false 'ACTIVE_GATE_STATUS_COMBINATION_INVALID' 'failed-active-gate-frozen-rejected'
    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'NQ-GATEX-1-CI-BLOCKER-FIX' $failedCommit '101' 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateX-1'
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false 'WORK_BATCH_ACTIVE_GATE_MISMATCH' 'failed-next-gate-work-batch-rejected'
    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_PENDING' 'NQ-GATEW-3-CI-BLOCKER-FIX' $fixCommit 'PENDING' 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateW-3'
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false 'NEXT_ACTION_TYPE_MISMATCH' 'pending-failed-only-action-rejected'

    foreach ($actionCase in @(
        @{Action='NQ-GATEW-4-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW-ATTEMPT-02';Expected='NEXT_ACTION_WORK_BATCH_MISMATCH';Name='green-continuation-next-batch-action-rejected'},
        @{Action='NQ-GATEW-3-CI-BLOCKER-FIX';Expected='NEXT_ACTION_TYPE_MISMATCH';Name='green-continuation-ci-blocker-action-rejected'},
        @{Action='NQ-GATEW-3-ARCHIVE-SECURITY-RISK-REVIEW';Expected='NEXT_ACTION_TYPE_MISMATCH';Name='green-continuation-archive-action-rejected'},
        @{Action='NQ-GATEW-3-ARCHIVE-MOVE-BATCH-SECURITY-RISK-REVIEW';Expected='NEXT_ACTION_TYPE_MISMATCH';Name='green-continuation-archive-move-action-rejected'},
        @{Action='NQ-GATEW-3-FREEZE';Expected='NEXT_ACTION_TYPE_MISMATCH';Name='green-continuation-freeze-action-rejected'},
        @{Action='NQ-GATEW-3-RELEASE';Expected='NEXT_ACTION_TYPE_MISMATCH';Name='green-continuation-release-action-rejected'},
        @{Action='NQ-GATEW-3-IMPLEMENTATION';Expected='NEXT_ACTION_TYPE_MISMATCH';Name='green-continuation-implementation-action-rejected'},
        @{Action='NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-REVIEW';Expected='NEXT_ACTION_TYPE_MISMATCH';Name='green-continuation-vague-review-action-rejected'}
    )) {
        Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $actionCase.Action $fixCommit '102' 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateW-3'
        Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false $actionCase.Expected $actionCase.Name
    }
    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $continuationAction $fixCommit '102' 'IN_PROGRESS|NOT_FROZEN' 'GateW-3' 'GateW-3'
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false 'accepted_batch_and_work_batch_must_differ' 'green-continuation-current-batch-not-accepted'
    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $continuationAction $fixCommit '102' 'IN_PROGRESS|NOT_FROZEN' 'GateW-1' 'GateW-3'
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false 'CI_GREEN_CONTINUATION_ACCEPTED_BATCH_INVALID' 'green-continuation-accepted-predecessor-required'
    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' 'NQ-GATEW-4-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW-ATTEMPT-02' $fixCommit '102' 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateW-4'
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false 'CI_GREEN_CONTINUATION_ACCEPTED_BATCH_INVALID' 'green-continuation-work-batch-four-rejected'
    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $continuationAction $fixCommit '102' 'FROZEN|ACCEPTED|TAGGED' 'GateW-2' 'GateW-3'
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false 'ACTIVE_GATE_STATUS_COMBINATION_INVALID' 'green-continuation-active-gate-frozen-rejected'

    foreach ($statusCase in @(
        @{Status='COMMITTED|CI_FAILURE|FIX_REQUIRED';Expected='WORK_BATCH_STATUS_INVALID';Name='failed-alias-rejected'},
        @{Status='committed|ci_failed|fix_required';Expected='WORK_BATCH_STATUS_INVALID';Name='failed-lowercase-rejected'},
        @{Status='COMMITTED |CI_FAILED|FIX_REQUIRED';Expected='WORK_BATCH_STATUS_INVALID';Name='failed-space-variant-rejected'},
        @{Status='COMMITTED|CI_GREEN|CONTINUE';Expected='WORK_BATCH_STATUS_INVALID';Name='green-continuation-alias-rejected'},
        @{Status='committed|ci_green|continue_required';Expected='WORK_BATCH_STATUS_INVALID';Name='green-continuation-lowercase-rejected'},
        @{Status='COMMITTED |CI_GREEN|CONTINUE_REQUIRED';Expected='WORK_BATCH_STATUS_INVALID';Name='green-continuation-space-variant-rejected'},
        @{Status=' COMMITTED|CI_GREEN|CONTINUE_REQUIRED';Expected='AUTHORITY_BLOCK_INVALID';Name='green-continuation-leading-space-rejected'},
        @{Status='COMMITTED|CI_GREEN|CONTINUE_REQUIRED ';Expected='AUTHORITY_BLOCK_INVALID';Name='green-continuation-trailing-space-rejected'}
    )) {
        $statusAction = if ($statusCase.Name.StartsWith('green-continuation')) { $continuationAction } else { 'NQ-GATEW-3-CI-BLOCKER-FIX' }
        Write-AuthorityFixture $authorityRoot $statusCase.Status $statusAction $failedCommit '101' 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateW-3'
        Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false $statusCase.Expected $statusCase.Name
    }
    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_FAILED|FIX_REQUIRED' 'NQ-GATEW-3-CI-BLOCKER-FIX' $failedCommit '101' 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateW-3'
    $conflictingStatusPath=Join-Path $authorityRoot 'docs/current/STATUS.md'
    $conflictingStatus=[System.IO.File]::ReadAllText($conflictingStatusPath,$utf8NoBom).Replace(
        '- GateW-3: COMMITTED / CI FAILED / FIX REQUIRED.',
        "- GateW-3: COMMITTED / CI FAILED / FIX REQUIRED.`n- GateW-3: ACCEPTED / CI GREEN."
    )
    Write-Utf8File $conflictingStatusPath $conflictingStatus
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false 'failed_state_must_not_claim_ci_green' 'failed-body-ci-green-rejected'

    Write-AuthorityFixture $authorityRoot 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' $continuationAction $fixCommit '102' 'IN_PROGRESS|NOT_FROZEN' 'GateW-2' 'GateW-3'
    $continuationStatusPath=Join-Path $authorityRoot 'docs/current/STATUS.md'
    $continuationStatus=[System.IO.File]::ReadAllText($continuationStatusPath,$utf8NoBom).Replace(
        '- GateW-3: COMMITTED / CI GREEN / CONTINUE REQUIRED.',
        "- GateW-3: COMMITTED / CI GREEN / CONTINUE REQUIRED.`n- GateW-3: ACCEPTED / CI GREEN."
    )
    Write-Utf8File $continuationStatusPath $continuationStatus
    Assert-Checker (Invoke-Checker $authorityChecker @() $authorityRoot) $false 'continuation_state_must_not_claim_accepted' 'green-continuation-body-accepted-rejected'

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
