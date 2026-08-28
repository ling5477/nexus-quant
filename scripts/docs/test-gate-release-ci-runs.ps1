[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'governance-workflow-lib.ps1')

function Assert-True {
    param([bool] $Condition, [string] $Message)
    if (-not $Condition) { throw $Message }
}

$workflow = 'NQ CI Baseline'
$head = '1111111111111111111111111111111111111111'
$negativeJson = @'
[
  {
    "databaseId": 1001,
    "workflowName": "NQ CI Baseline",
    "headSha": "1111111111111111111111111111111111111111",
    "status": "completed",
    "conclusion": "failure"
  },
  {
    "databaseId": 1002,
    "workflowName": "Other Workflow",
    "headSha": "2222222222222222222222222222222222222222",
    "status": "completed",
    "conclusion": "success"
  }
]
'@
$negativeRuns = @(ConvertFrom-GovernanceJsonArray $negativeJson)
Assert-True ($negativeRuns.Count -eq 2) "Top-level CI array was not enumerated into independent runs: count=$($negativeRuns.Count)"
$negativeSelection = Select-GovernanceReleaseCiRun $negativeRuns $workflow $head
Assert-True ($null -eq $negativeSelection) 'Fields from separate CI runs were aggregated into a green run.'
Write-Output 'PASS release-ci-negative split-run-aggregation=REJECTED'

$positiveJson = @'
[
  {
    "databaseId": 2001,
    "workflowName": "NQ CI Baseline",
    "headSha": "1111111111111111111111111111111111111111",
    "status": "completed",
    "conclusion": "success"
  }
]
'@
$positiveRuns = @(ConvertFrom-GovernanceJsonArray $positiveJson)
Assert-True ($positiveRuns.Count -eq 1) "Single CI run parsed incorrectly: count=$($positiveRuns.Count)"
$positiveSelection = Select-GovernanceReleaseCiRun $positiveRuns $workflow $head
Assert-True ($null -ne $positiveSelection -and [string]$positiveSelection.databaseId -ceq '2001') 'Valid exact-head CI run was rejected.'
Write-Output 'PASS release-ci-positive exact-run=ACCEPTED databaseId=2001'

$arrayPropertyJson = @'
[
  {
    "databaseId": [3001, 3002],
    "workflowName": ["NQ CI Baseline", "Other Workflow"],
    "headSha": ["1111111111111111111111111111111111111111", "2222222222222222222222222222222222222222"],
    "status": ["completed", "completed"],
    "conclusion": ["failure", "success"]
  }
]
'@
$arrayPropertyRuns = @(ConvertFrom-GovernanceJsonArray $arrayPropertyJson)
Assert-True ($null -eq (Select-GovernanceReleaseCiRun $arrayPropertyRuns $workflow $head)) 'Array-valued CI properties were accepted.'
Write-Output 'PASS release-ci-array-properties=REJECTED'

Write-Output 'SUMMARY gate-release-ci-runs negative=PASS positive=PASS array-properties=PASS'
exit 0
