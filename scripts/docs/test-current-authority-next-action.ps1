<#
.SYNOPSIS
Regresses next_action classification and status mapping from the canonical contract.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'governance-workflow-lib.ps1')
$contract = Get-GovernanceWorkflowContract (Join-Path $PSScriptRoot 'governance-workflow-contract.json')

function Assert-ActionType {
    param([string] $Action, [string] $Expected)
    $actual = Get-GovernanceNextActionType $contract $Action
    if ($actual -ne $Expected) { throw "ACTION_TYPE_MISMATCH action=$Action expected=$Expected actual=$actual" }
    Write-Output "PASS action=$Action type=$actual"
}

$canonicalAttempt09Start = 'NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-START'
$canonicalAttempt09WorkBatch = 'GateW-OKX-READONLY-SOAK-ATTEMPT-09'
Assert-ActionType $canonicalAttempt09Start 'IMPLEMENTATION'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract 'NOT_STARTED' $canonicalAttempt09WorkBatch $canonicalAttempt09Start)) {
    throw 'CANONICAL_ATTEMPT_09_START_REJECTED'
}
Write-Output 'PASS canonical-attempt-09-start work-batch-match=true'

$invalidAttempt09StartActions = @(
    'NQ-GATEW-OKX-READONLY-SOAK-ATEMPT-09-START',
    'NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09',
    'NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-10-START',
    'nq-gatew-okx-readonly-soak-attempt-09-start'
)
foreach ($action in $invalidAttempt09StartActions) {
    Assert-ActionType $action 'UNKNOWN'
    if (Test-GovernanceNextActionForWorkBatch `
            $contract 'NOT_STARTED' $canonicalAttempt09WorkBatch $action) {
        throw "NON_CANONICAL_ATTEMPT_START_ACCEPTED action=$action"
    }
}
Write-Output 'PASS non-canonical-attempt-start relation=false'

$canonicalAttempt09Acceptance = 'NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-168H-ACCEPTANCE'
$attempt09RunningStatus = 'RUNNING|PENDING_168H'
Assert-ActionType $canonicalAttempt09Acceptance 'SOAK_ACCEPTANCE'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $attempt09RunningStatus $canonicalAttempt09WorkBatch $canonicalAttempt09Acceptance)) {
    throw 'CANONICAL_ATTEMPT_09_ACCEPTANCE_REJECTED'
}
Write-Output 'PASS canonical-attempt-09-acceptance work-batch-match=true'
if (-not (Test-GovernanceLifecycleTransition `
        $contract 'highRisk' 'NOT_STARTED' $attempt09RunningStatus)) {
    throw 'ATTEMPT_09_RUNNING_LIFECYCLE_TRANSITION_REJECTED'
}
Write-Output 'PASS canonical-attempt-09-running lifecycle=highRisk'

$invalidAttempt09AcceptanceActions = @(
    'NQ-GATEW-OKX-READONLY-SOAK-ATEMPT-09-168H-ACCEPTANCE',
    'NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-ACCEPTANCE',
    'NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-10-168H-ACCEPTANCE',
    'nq-gatew-okx-readonly-soak-attempt-09-168h-acceptance'
)
foreach ($action in $invalidAttempt09AcceptanceActions) {
    Assert-ActionType $action 'UNKNOWN'
    if (Test-GovernanceNextActionForWorkBatch `
            $contract $attempt09RunningStatus $canonicalAttempt09WorkBatch $action) {
        throw "NON_CANONICAL_ATTEMPT_ACCEPTANCE_ACCEPTED action=$action"
    }
}
if (Test-GovernanceNextActionForWorkBatch `
        $contract $attempt09RunningStatus 'GateW-OKX-READONLY-SOAK-ATTEMPT-10' $canonicalAttempt09Acceptance) {
    throw 'CROSS_ATTEMPT_ACCEPTANCE_ACCEPTED workBatch=Attempt-10'
}
Write-Output 'PASS non-canonical-attempt-acceptance relation=false'

$attempt09FailureStatus = 'FAILED|ACCEPTANCE_REJECTED|INCIDENT_REVIEW_COMPLETED'
$canonicalAttempt09Remediation = 'NQ-GATEW-ATTEMPT-09-FAILURE-REMEDIATION-IMPLEMENTATION'
Assert-ActionType $canonicalAttempt09Remediation 'FAILURE_REMEDIATION_IMPLEMENTATION'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $attempt09FailureStatus $canonicalAttempt09WorkBatch $canonicalAttempt09Remediation)) {
    throw 'CANONICAL_ATTEMPT_09_FAILURE_REMEDIATION_REJECTED'
}
Write-Output 'PASS canonical-attempt-09-failure-remediation exact-triple=true'

foreach ($status in @(
    $attempt09RunningStatus,
    'BLOCKED',
    'FAILED|ACCEPTANCE_REJECTED|INCIDENT_REVIEW_COMPLETE',
    'failed|acceptance_rejected|incident_review_completed'
)) {
    if (Test-GovernanceNextActionForWorkBatch `
            $contract $status $canonicalAttempt09WorkBatch $canonicalAttempt09Remediation) {
        throw "NON_CANONICAL_FAILURE_STATUS_ACCEPTED status=$status"
    }
}
Write-Output 'PASS non-canonical-attempt-09-failure-status relation=false'

foreach ($workBatch in @(
    'GateW-OKX-READONLY-SOAK-ATEMPT-09',
    'GateW-OKX-READONLY-SOAK-ATTEMPT-10',
    'GateW-ATTEMPT-09',
    'gatew-okx-readonly-soak-attempt-09'
)) {
    if (Test-GovernanceNextActionForWorkBatch `
            $contract $attempt09FailureStatus $workBatch $canonicalAttempt09Remediation) {
        throw "NON_CANONICAL_FAILURE_WORK_BATCH_ACCEPTED workBatch=$workBatch"
    }
}
Write-Output 'PASS non-canonical-attempt-09-failure-work-batch relation=false'

foreach ($action in @(
    'NQ-GATEW-ATEMPT-09-FAILURE-REMEDIATION-IMPLEMENTATION',
    'NQ-GATEW-ATTEMPT-09-FAILURE-REMEDATION-IMPLEMENTATION',
    'NQ-GATEW-ATTEMPT-09-REMEDIATION-IMPLEMENTATION',
    'NQ-GATEW-ATTEMPT-10-FAILURE-REMEDIATION-IMPLEMENTATION',
    'NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-FAILURE-REMEDIATION-IMPLEMENTATION',
    'nq-gatew-attempt-09-failure-remediation-implementation'
)) {
    $actualType = Get-GovernanceNextActionType $contract $action
    if ($actualType -ceq 'FAILURE_REMEDIATION_IMPLEMENTATION') {
        throw "NON_CANONICAL_FAILURE_ACTION_CLASSIFIED_AS_EXACT action=$action"
    }
    if (Test-GovernanceNextActionForWorkBatch `
            $contract $attempt09FailureStatus $canonicalAttempt09WorkBatch $action) {
        throw "NON_CANONICAL_FAILURE_ACTION_ACCEPTED action=$action"
    }
}
Write-Output 'PASS non-canonical-attempt-09-failure-action relation=false'

$remediationImplementedStatus = 'IMPLEMENTED|CI_GREEN|PENDING_SECURITY_REVIEW'
$remediationWorkBatch = 'GateW-ATTEMPT-09-FAILURE-REMEDIATION'
$remediationSecurityReview = 'NQ-GATEW-ATTEMPT-09-FAILURE-REMEDIATION-SECURITY-REVIEW'
Assert-ActionType $remediationSecurityReview 'FAILURE_REMEDIATION_SECURITY_REVIEW'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $remediationImplementedStatus $remediationWorkBatch $remediationSecurityReview)) {
    throw 'CANONICAL_ATTEMPT_09_REMEDIATION_SECURITY_REVIEW_REJECTED'
}
Write-Output 'PASS canonical-attempt-09-remediation-security-review exact-triple=true'

foreach ($status in @(
    'IMPLEMENTED|CI_GREEN|PENDING_REVIEW',
    'IMPLEMENTED|CI_GREEN|PENDING_SECURITY_REVEW',
    'IMPLEMENTED|CI_GREEN|PENDING_SECURITY_REVIEW|READY',
    'implemented|ci_green|pending_security_review',
    $attempt09FailureStatus
)) {
    if (Test-GovernanceNextActionForWorkBatch `
            $contract $status $remediationWorkBatch $remediationSecurityReview) {
        throw "NON_CANONICAL_REMEDIATION_STATUS_ACCEPTED status=$status"
    }
}
Write-Output 'PASS non-canonical-remediation-status relation=false'

foreach ($workBatch in @(
    'GateW-ATEMPT-09-FAILURE-REMEDIATION',
    'GateW-ATTEMPT-10-FAILURE-REMEDIATION',
    'GateW-OKX-READONLY-SOAK-ATTEMPT-09',
    'GateW-ATTEMPT-09-FAILURE-REMEDATION',
    'gatew-attempt-09-failure-remediation'
)) {
    if (Test-GovernanceNextActionForWorkBatch `
            $contract $remediationImplementedStatus $workBatch $remediationSecurityReview) {
        throw "NON_CANONICAL_REMEDIATION_WORK_BATCH_ACCEPTED workBatch=$workBatch"
    }
}
Write-Output 'PASS non-canonical-remediation-work-batch relation=false'

foreach ($action in @(
    'NQ-GATEW-ATEMPT-09-FAILURE-REMEDIATION-SECURITY-REVIEW',
    'NQ-GATEW-ATTEMPT-10-FAILURE-REMEDIATION-SECURITY-REVIEW',
    'NQ-GATEW-ATTEMPT-09-FAILURE-REMEDATION-SECURITY-REVIEW',
    'NQ-GATEW-ATTEMPT-09-FAILURE-REMEDIATION-SECURTY-REVIEW',
    'NQ-GATEW-ATTEMPT-09-FAILURE-REMEDIATION-SECURITY-RISK-REVIEW',
    'NQ-GATEW-ATTEMPT-09-FAILURE-REMEDIATION-SECURITY-REVIEW-LATER',
    'nq-gatew-attempt-09-failure-remediation-security-review'
)) {
    $actualType = Get-GovernanceNextActionType $contract $action
    if ($actualType -ceq 'FAILURE_REMEDIATION_SECURITY_REVIEW') {
        throw "NON_CANONICAL_REMEDIATION_ACTION_CLASSIFIED_AS_EXACT action=$action"
    }
    if (Test-GovernanceNextActionForWorkBatch `
            $contract $remediationImplementedStatus $remediationWorkBatch $action) {
        throw "NON_CANONICAL_REMEDIATION_ACTION_ACCEPTED action=$action"
    }
}
Write-Output 'PASS non-canonical-remediation-action relation=false'

if (-not (Test-GovernanceLifecycleTransition `
        $contract 'highRisk' $attempt09RunningStatus $attempt09FailureStatus)) {
    throw 'ATTEMPT_09_FAILURE_LIFECYCLE_TRANSITION_REJECTED'
}
Write-Output 'PASS attempt-09-running-to-failure lifecycle=highRisk'
if (-not (Test-GovernanceLifecycleTransition `
        $contract 'highRisk' $attempt09FailureStatus $remediationImplementedStatus)) {
    throw 'ATTEMPT_09_REMEDIATION_IMPLEMENTED_LIFECYCLE_TRANSITION_REJECTED'
}
Write-Output 'PASS attempt-09-failure-to-remediation-implemented lifecycle=highRisk'

foreach ($action in @('NQ-GATEW-COMMIT-AND-PUSH', 'NQ-GATEW-COMMIT_AND_PUSH', 'NQ-GATEW-USER_COMMIT')) {
    Assert-ActionType $action 'COMMIT_AND_PUSH'
}
foreach ($case in @(
    @{ Action = 'NQ-GATEW-3-CI-BLOCKER-FIX'; Expected = 'CI_BLOCKER_FIX' },
    @{ Action = 'NQ-GATEW-3-CI-BLOCKER-FIX-REVIEW'; Expected = 'CI_BLOCKER_FIX' },
    @{ Action = 'NQ-GATEW-3-CI-BLOCKER-FIX-COMMIT-AND-PUSH'; Expected = 'CI_BLOCKER_FIX' },
    @{ Action = 'NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW'; Expected = 'SECURITY_RISK_REVIEW' },
    @{ Action = 'NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW-ATTEMPT-2'; Expected = 'SECURITY_RISK_REVIEW' },
    @{ Action = 'NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW-ATTEMPT-02'; Expected = 'SECURITY_RISK_REVIEW' },
    @{ Action = 'NQ-GATEW-REVIEW'; Expected = 'REVIEW' },
    @{ Action = 'NQ-GATEW-PLAN-IMPLEMENTATION'; Expected = 'IMPLEMENTATION' },
    @{ Action = 'NQ-GATEW-POST-CI-ACTIVE-AUTHORITY-SYNC'; Expected = 'POST_CI_SYNC' },
    @{ Action = 'NQ-GATEW-WAIT-CI'; Expected = 'CI_WAIT_OR_INVESTIGATION' },
    @{ Action = 'NQ-GATEW-BLOCKED'; Expected = 'BLOCKED' }
)) { Assert-ActionType $case.Action $case.Expected }
foreach ($action in @(
    'NQ-GATEW-COMMIT',
    'NQ-GATEW-PUSH',
    'NQ-GATEW-COMMIT-SOMETHING-PUSH',
    'NQ-GATEW-COMMIT-AND-PUSH-LATER',
    'NQ-GATEW-WAIT-ANYTHING-CI',
    'NQ-GATEW-UNBLOCKED',
    'NQ-GATEW-3-FIX',
    'NQ-GATEW-3-MIGRATION-FIX',
    'NQ-GATEW-3-SECURITY-FIX',
    'NQ-GATEW-3-BLOCKER-FIX',
    'NQ-GATEW-3-CI-BLOCKER-FIX-LATER',
    'NQ-GATEW-3-CI-BLOCKER-FIX-SECURITY',
    'NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW-ATTEMPT-00',
    'NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW-ATTEMPT-',
    'NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW-ATTEMPT-X',
    'NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW-LATER'
)) {
    Assert-ActionType $action 'UNKNOWN'
}

Assert-ActionType 'NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-REVIEW' 'REVIEW'
Assert-ActionType 'NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-REVIEW' 'REVIEW'
Assert-ActionType 'NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-RISK-REVIEW' 'REVIEW'
Assert-ActionType 'NQ-GATEW-3-CI-BLOCKER-FIX-SECURITY-RISK-REVIEW' 'REVIEW'
Assert-ActionType 'NQ-GATEW-3-IMPLEMENTATION-SECURITY-RISK-REVIEW' 'REVIEW'
Assert-ActionType 'NQ-GATEW-3-ARCHIVE-SECURITY-RISK-REVIEW' 'REVIEW'
Assert-ActionType 'NQ-GATEW-3-ARCHIVE-MOVE-BATCH-SECURITY-RISK-REVIEW' 'REVIEW'
Assert-ActionType 'NQ-GATEW-3-FREEZE-SECURITY-RISK-REVIEW' 'REVIEW'
Assert-ActionType 'NQ-GATEW-3-RELEASE-SECURITY-RISK-REVIEW' 'REVIEW'
$continuationStatus = 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED'
$continuationAction = 'NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW-ATTEMPT-02'
if (-not (Test-GovernanceNextActionForWorkBatch $contract $continuationStatus 'GateW-3' $continuationAction)) {
    throw 'SAME_BATCH_ACTION_REJECTED workBatch=GateW-3'
}
if (Test-GovernanceNextActionForWorkBatch $contract $continuationStatus 'GateW-4' $continuationAction) {
    throw 'CROSS_BATCH_ACTION_ACCEPTED workBatch=GateW-4'
}
Write-Output 'PASS action-same-batch relation=GateW-3'
Write-Output 'PASS action-cross-batch-rejected relation=GateW-4'

foreach ($status in @($contract.authority.workBatchStatuses)) {
    $expected = Get-GovernanceExpectedNextActionType $contract ([string]$status)
    if ($expected -eq 'UNKNOWN') { throw "STATUS_ACTION_MAPPING_MISSING status=$status" }
    Write-Output "PASS status=$status expectedActionType=$expected"
}
Write-Output 'PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION'
