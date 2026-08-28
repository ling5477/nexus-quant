[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'governance-workflow-lib.ps1')
$contractPath = Join-Path $PSScriptRoot 'governance-workflow-contract.json'
$contract = Get-GovernanceWorkflowContract $contractPath

function Assert-True {
    param([bool] $Condition, [string] $Message)
    if (-not $Condition) { throw $Message }
}

$allowed = @(
    @('ordinary', 'NOT_STARTED', 'IMPLEMENTED|SELF_REVIEWED'),
    @('ordinary', 'IMPLEMENTED|SELF_REVIEWED', 'COMMITTED|CI_PENDING'),
    @('ordinary', 'COMMITTED|CI_PENDING', 'ACCEPTED|CI_GREEN'),
    @('highRisk', 'NOT_STARTED', 'IMPLEMENTED|PENDING_REVIEW'),
    @('highRisk', 'IMPLEMENTED|PENDING_REVIEW', 'REVIEW_ACCEPTED|READY_TO_COMMIT'),
    @('highRisk', 'REVIEW_ACCEPTED|READY_TO_COMMIT', 'COMMITTED|CI_PENDING'),
    @('ciFailed', 'COMMITTED|CI_PENDING', 'CI_FAILED'),
    @('blocked', 'NOT_STARTED', 'BLOCKED'),
    @('freeze', 'ACCEPTED|CI_GREEN', 'FROZEN|ACCEPTED|TAGGED'),
    @('release', 'ACCEPTED|CI_GREEN', 'FROZEN|ACCEPTED|TAGGED'),
    @('soak', 'NOT_STARTED', 'IMPLEMENTED|PENDING_REVIEW')
)
foreach ($case in $allowed) {
    Assert-True (Test-GovernanceLifecycleTransition $contract $case[0] $case[1] $case[2]) "Legal transition rejected: $($case -join ' -> ')"
    Write-Output "PASS lifecycle=$($case[0]) from=$($case[1]) to=$($case[2])"
}

$rejected = @(
    @('ordinary', 'NOT_STARTED', 'COMMITTED|CI_PENDING'),
    @('highRisk', 'IMPLEMENTED|PENDING_REVIEW', 'COMMITTED|CI_PENDING'),
    @('freeze', 'NOT_STARTED', 'FROZEN|ACCEPTED|TAGGED'),
    @('release', 'IMPLEMENTED|PENDING_REVIEW', 'FROZEN|ACCEPTED|TAGGED')
)
foreach ($case in $rejected) {
    Assert-True (-not (Test-GovernanceLifecycleTransition $contract $case[0] $case[1] $case[2])) "Illegal transition accepted: $($case -join ' -> ')"
    Write-Output "PASS rejected lifecycle=$($case[0]) from=$($case[1]) to=$($case[2])"
}

Assert-True (Test-GovernanceLifecycleTransitionContext $contract 'highRisk' 'REVIEW_ACCEPTED|READY_TO_COMMIT' 'COMMITTED|CI_PENDING' 'NONE' 'NOT_RUN' '3333333333333333333333333333333333333333' 'PENDING' $false $null) 'Valid committed context rejected.'
Assert-True (-not (Test-GovernanceLifecycleTransitionContext $contract 'highRisk' 'REVIEW_ACCEPTED|READY_TO_COMMIT' 'COMMITTED|CI_PENDING' 'NONE' 'NOT_RUN' 'NONE' 'NOT_RUN' $false $null)) 'Invalid committed context accepted.'

$evidencePath = Join-Path ([System.IO.Path]::GetTempPath()) ("NQ-GENERIC-EVIDENCE-{0}.attempt-01.md" -f [guid]::NewGuid().ToString('N'))
$evidenceBody = ((1..10 | ForEach-Object { "Evidence line $_ records facts, commands, results, risks, and rollback details." }) -join "`n")
[System.IO.File]::WriteAllText($evidencePath, $evidenceBody, (New-Object System.Text.UTF8Encoding($false)))
try {
    $item = Get-Item -LiteralPath $evidencePath
    Assert-True (Test-GovernanceEvidenceItem $contract $item 'current' 'docs/current/evidence/generic/NQ-GENERIC-EVIDENCE.attempt-01.md') 'Valid current evidence rejected.'
    Assert-True (-not (Test-GovernanceEvidencePath $contract 'archive' '../outside.md')) 'Archive path traversal accepted.'
} finally {
    Remove-Item -LiteralPath $evidencePath -Force -ErrorAction SilentlyContinue
}

$runtimeText = [System.IO.File]::ReadAllText($contractPath) + [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'governance-workflow-lib.ps1'))
Assert-True ($runtimeText -notmatch '(?i)Gate[WXY]|Attempt[-_ ]?(?:09|10|11|12|13)|NQ-GATE[WXY]-') 'Task-specific runtime rule remains.'
foreach ($lifecycle in @('ordinary', 'highRisk', 'ciFailed', 'blocked', 'freeze', 'release', 'soak')) {
    Assert-True ($null -ne (Get-GovernancePropertyValue $contract.lifecycles $lifecycle)) "Lifecycle missing: $lifecycle"
}

Write-Output 'SUMMARY governance-workflow-lifecycle passed=20 failed=0 TASK_ID_SPECIFIC_RUNTIME_RULES=0'
exit 0
