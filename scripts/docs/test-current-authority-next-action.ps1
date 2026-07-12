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
    'NQ-GATEW-UNBLOCKED'
)) {
    Assert-ActionType $action 'UNKNOWN'
}

foreach ($status in @($contract.authority.workBatchStatuses)) {
    $expected = Get-GovernanceExpectedNextActionType $contract ([string]$status)
    if ($expected -eq 'UNKNOWN') { throw "STATUS_ACTION_MAPPING_MISSING status=$status" }
    Write-Output "PASS status=$status expectedActionType=$expected"
}
Write-Output 'PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION'
