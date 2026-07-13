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
