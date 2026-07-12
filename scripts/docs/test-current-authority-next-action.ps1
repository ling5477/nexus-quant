<#
.SYNOPSIS
Regression tests for authority schema v3 next-action classification.
.NOTES
Uses the PowerShell parser to exercise the checker's real private functions without adding a test framework.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$checkerPath = Join-Path $PSScriptRoot 'check-current-authority.ps1'
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    $checkerPath,
    [ref] $tokens,
    [ref] $parseErrors
)

if ($parseErrors.Count -gt 0) {
    throw "CHECKER_PARSE_FAILED count=$($parseErrors.Count)"
}

$functionBodies = @{}
$functionAsts = $ast.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst]
}, $true)
foreach ($functionName in @('Get-NextActionType', 'Get-ExpectedNextActionType')) {
    $functionAst = $functionAsts | Where-Object { $_.Name -eq $functionName } | Select-Object -First 1
    if ($null -eq $functionAst) {
        throw "CHECKER_FUNCTION_NOT_FOUND name=$functionName"
    }
    $bodyText = $functionAst.Body.Extent.Text
    $functionBodies[$functionName] = [scriptblock]::Create($bodyText.Substring(1, $bodyText.Length - 2))
}
$nextActionClassifier = $functionBodies['Get-NextActionType']
$expectedActionClassifier = $functionBodies['Get-ExpectedNextActionType']

function Assert-ActionType {
    param(
        [scriptblock] $Classifier,
        [string] $Action,
        [string] $Expected
    )
    $actual = & $Classifier $Action
    if ($actual -ne $Expected) {
        throw "ACTION_TYPE_MISMATCH action=$Action expected=$Expected actual=$actual"
    }
    Write-Output "PASS action=$Action type=$actual"
}

$userCommitAction = -join @([char] 0x7528, [char] 0x6237, [char] 0x63D0, [char] 0x4EA4)
foreach ($action in @(
    'NQ-GATEV-4-COMMIT-AND-PUSH',
    'NQ-GATEV-4-COMMIT_AND_PUSH',
    'NQ-GATEV-4-USER_COMMIT',
    $userCommitAction
)) {
    Assert-ActionType $nextActionClassifier $action 'COMMIT_AND_PUSH'
}

foreach ($case in @(
    @{ Action = 'NQ-GATEV-4-REVIEW-WORKBENCH-REVIEW'; Expected = 'REVIEW' },
    @{ Action = 'NQ-GATEV-4-REVIEW-WORKBENCH-IMPLEMENTATION'; Expected = 'IMPLEMENTATION' },
    @{ Action = 'NQ-GATEV-4-POST-CI-ACTIVE-AUTHORITY-SYNC'; Expected = 'POST_CI_SYNC' },
    @{ Action = 'NQ-GATEV-4-WAIT-CI'; Expected = 'CI_WAIT_OR_INVESTIGATION' },
    @{ Action = 'NQ-GATEV-4-BLOCKED'; Expected = 'BLOCKED' }
)) {
    Assert-ActionType $nextActionClassifier $case.Action $case.Expected
}

foreach ($action in @(
    'NQ-GATEV-4-COMMIT',
    'NQ-GATEV-4-PUSH',
    'NQ-GATEV-4-COMMIT-ONLY',
    'NQ-GATEV-4-PUSH-ONLY',
    'NQ-GATEV-4-COMMIT-SOMETHING-PUSH'
)) {
    Assert-ActionType $nextActionClassifier $action 'UNKNOWN'
}

$authorityFixture = @{
    work_batch_status = 'REVIEW_ACCEPTED|READY_TO_COMMIT'
    next_action = 'NQ-GATEV-4-COMMIT-AND-PUSH'
}
$expectedType = & $expectedActionClassifier $authorityFixture.work_batch_status
$actualType = & $nextActionClassifier $authorityFixture.next_action
if ($expectedType -ne $actualType) {
    throw "NEXT_ACTION_TYPE_MISMATCH status=$($authorityFixture.work_batch_status) expected=$expectedType actual=$actualType action=$($authorityFixture.next_action)"
}

Write-Output "PASS authority_fixture status=$($authorityFixture.work_batch_status) next_action=$($authorityFixture.next_action) type=$actualType"
Write-Output 'PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION'
