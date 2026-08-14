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
$contractPath = Join-Path $PSScriptRoot 'governance-workflow-contract.json'
if ([string]$contract.schemaVersion -cne '1.5.0') { throw "CONTRACT_VERSION_NOT_CURRENT version=$($contract.schemaVersion)" }
Write-Output 'PASS fixture=contract-current-version-accepted version=1.5.0'

function Assert-ActionType {
    param([string] $Action, [string] $Expected)
    $actual = Get-GovernanceNextActionType $contract $Action
    if ($actual -ne $Expected) { throw "ACTION_TYPE_MISMATCH action=$Action expected=$Expected actual=$actual" }
    Write-Output "PASS action=$Action type=$actual"
}

function Write-Utf8File {
    param([string] $Path, [string] $Content)
    [System.IO.File]::WriteAllText($Path, $Content, (New-Object System.Text.UTF8Encoding($false)))
}

function Assert-MachineAttemptAuthorityCase {
    param(
        [string] $Name,
        [string] $WorkBatch,
        [string] $MachineAttempt,
        [bool] $ExpectApplicable,
        [bool] $ExpectValid,
        [Nullable[int]] $ExpectedAttemptId = $null
    )

    $actual = Read-MachineCurrentAttemptAuthority @{
        work_batch = $WorkBatch
        attempt = $MachineAttempt
        attempt_status = 'COMPLETED|ACCEPTED'
    }
    if ([bool]$actual.IsApplicable -ne $ExpectApplicable -or
        [bool]$actual.IsValid -ne $ExpectValid) {
        throw ("MACHINE_ATTEMPT_CASE_MISMATCH case={0} applicable={1}/{2} valid={3}/{4} reason={5}" -f
            $Name, $actual.IsApplicable, $ExpectApplicable, $actual.IsValid, $ExpectValid, $actual.Reason)
    }
    if ($ExpectValid -and $null -ne $ExpectedAttemptId -and
        [int]$actual.AttemptId -ne [int]$ExpectedAttemptId) {
        throw "MACHINE_ATTEMPT_ID_MISMATCH case=$Name expected=$ExpectedAttemptId actual=$($actual.AttemptId)"
    }

    $result = if (-not $ExpectApplicable) { 'NOT_APPLICABLE' } elseif ($ExpectValid) { 'PASS' } else { 'FAIL_CLOSED' }
    Write-Output "PASS machine-attempt-case=$Name result=$result"
}

foreach ($workBatch in @(
    'GateW-ATTEMPT-13',
    'GateW-ATTEMPT-13-168H-ACCEPTANCE',
    'GateW-ATTEMPT-13-PREPARATION-AND-START',
    'GateW-ATTEMPT-13-FAILURE-REMEDIATION-IMPLEMENTATION'
)) {
    Assert-MachineAttemptAuthorityCase $workBatch $workBatch 'Attempt-13' $true $true 13
}
foreach ($case in @(
    @{ Name='attempt-12-docs-13'; Batch='GateW-ATTEMPT-12-168H-ACCEPTANCE' },
    @{ Name='attempt-14-docs-13'; Batch='GateW-ATTEMPT-14-168H-ACCEPTANCE' },
    @{ Name='attempt-suffix-alpha'; Batch='GateW-ATTEMPT-13X-168H-ACCEPTANCE' },
    @{ Name='attempt-lowercase'; Batch='GateW-attempt-13-168H-ACCEPTANCE' },
    @{ Name='attempt-duplicate'; Batch='GateW-ATTEMPT-13-ATTEMPT-14-168H-ACCEPTANCE' },
    @{ Name='attempt-double-hyphen'; Batch='GateW-ATTEMPT--13-168H-ACCEPTANCE' },
    @{ Name='attempt-missing-id'; Batch='GateW-ATTEMPT-' },
    @{ Name='attempt-nondigit'; Batch='GateW-ATTEMPT-X' },
    @{ Name='attempt-leading-zero'; Batch='GateW-ATTEMPT-013-168H-ACCEPTANCE' }
)) {
    Assert-MachineAttemptAuthorityCase $case.Name $case.Batch 'Attempt-13' $true $false
}
$unrelatedMachineAttemptAuthority = Read-MachineCurrentAttemptAuthority @{
    work_batch = 'GateW-4'
    work_batch_status = 'NOT_STARTED'
}
if ($unrelatedMachineAttemptAuthority.IsApplicable -or -not $unrelatedMachineAttemptAuthority.IsValid) {
    throw 'MACHINE_ATTEMPT_UNRELATED_WORK_BATCH_APPLICABLE'
}
Write-Output 'PASS machine-attempt-case=unrelated-work-batch result=NOT_APPLICABLE'

$gateXPlanAuthority = @{
    work_batch = 'GateX-PLAN'
    work_batch_status = 'NOT_STARTED'
    next_action = 'NQ-GATEX-PLAN-IMPLEMENTATION'
}
$gateXConsolidationAction = 'NQ-POST-GATEW-BRANCH-CONSOLIDATION-AND-DEV-REBASE'
$gateXPlanMachineAttempt = Read-MachineCurrentAttemptAuthority $gateXPlanAuthority
if ($gateXPlanMachineAttempt.IsApplicable -or -not $gateXPlanMachineAttempt.IsValid) {
    throw 'GATEX_PLAN_MACHINE_ATTEMPT_MUST_BE_NOT_APPLICABLE'
}
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $gateXPlanAuthority.work_batch_status `
        $gateXPlanAuthority.work_batch $gateXPlanAuthority.next_action)) {
    throw 'GATEX_PLAN_CANONICAL_TRIPLE_REJECTED'
}
Assert-ActionType $gateXPlanAuthority.next_action 'IMPLEMENTATION'
Assert-ActionType $gateXConsolidationAction 'IMPLEMENTATION'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $gateXPlanAuthority.work_batch_status `
        $gateXPlanAuthority.work_batch $gateXConsolidationAction)) {
    throw 'GATEX_BRANCH_CONSOLIDATION_TRIPLE_REJECTED'
}
foreach ($case in @(
    @{ Status='UNKNOWN'; Batch='GateX-PLAN'; Action='NQ-GATEX-PLAN-IMPLEMENTATION' },
    @{ Status='NOT_STARTED'; Batch='GateX-UNKNOWN'; Action='NQ-GATEX-PLAN-IMPLEMENTATION' },
    @{ Status='NOT_STARTED'; Batch='GateX-PLAN'; Action='NQ-GATEX-UNKNOWN' },
    @{ Status='NOT_STARTED'; Batch='GateX-PLAN'; Action='NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION' },
    @{ Status='NOT_STARTED'; Batch='GateX-PLAN'; Action='NQ-POST-GATEW-BRANCH-CONSOLIDATION-AND-DEV-REBASE-REVIEW' },
    @{ Status='NOT_STARTED'; Batch='GateX-PLAN'; Action='NQ-POST-GATEW-UNAUTHORIZED-IMPLEMENTATION' }
)) {
    if (Test-GovernanceNextActionForWorkBatch $contract $case.Status $case.Batch $case.Action) {
        throw ("GATEX_PLAN_NON_CANONICAL_TRIPLE_ACCEPTED status={0} batch={1} action={2}" -f
            $case.Status, $case.Batch, $case.Action)
    }
}
Write-Output 'PASS machine-attempt-case=gatex-plan result=NOT_APPLICABLE ordinary-canonical-validation=PASS'

function Assert-CurrentDocsAuthorityCase {
    param(
        [string] $Name,
        [string] $ReadmeContent,
        [bool] $ExpectSuccess,
        [string] $ExpectedErrorPattern = '',
        [string] $RootReadmeContent = ''
    )

    $caseRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("nq-current-authority-{0}-{1}" -f $Name, [guid]::NewGuid().ToString('N'))
    $currentDocsRoot = Join-Path $caseRoot 'docs/current'
    [System.IO.Directory]::CreateDirectory($currentDocsRoot) | Out-Null
    try {
        Write-Utf8File (Join-Path $currentDocsRoot 'STATUS.md') $script:statusFixture
        Write-Utf8File (Join-Path $currentDocsRoot 'ROADMAP.md') $script:roadmapFixture
        Write-Utf8File (Join-Path $currentDocsRoot 'README.md') $ReadmeContent
        if ([string]::IsNullOrWhiteSpace($RootReadmeContent)) {
            $RootReadmeContent = $ReadmeContent
        }
        Write-Utf8File (Join-Path $caseRoot 'README.md') $RootReadmeContent

        $shellPath = (Get-Process -Id $PID).Path
        $checkerOutput = @(& $shellPath -NoProfile -ExecutionPolicy Bypass `
            -File $script:authorityChecker `
            -StatusPath (Join-Path $currentDocsRoot 'STATUS.md') `
            -RoadmapPath (Join-Path $currentDocsRoot 'ROADMAP.md') `
            -ReadmePath (Join-Path $currentDocsRoot 'README.md') `
            -RootReadmePath (Join-Path $caseRoot 'README.md') `
            -CurrentDocsPath $currentDocsRoot 2>&1)
        $exitCode = $LASTEXITCODE

        if ($ExpectSuccess -and $exitCode -ne 0) {
            throw "CURRENT_DOC_CASE_UNEXPECTED_FAILURE case=$Name output=$($checkerOutput -join ' | ')"
        }
        if (-not $ExpectSuccess -and $exitCode -eq 0) {
            throw "CURRENT_DOC_CASE_UNEXPECTED_PASS case=$Name output=$($checkerOutput -join ' | ')"
        }
        if (-not $ExpectSuccess -and
            -not [string]::IsNullOrWhiteSpace($ExpectedErrorPattern) -and
            -not ($checkerOutput -match $ExpectedErrorPattern)) {
            throw "CURRENT_DOC_CASE_MISSING_CONFLICT case=$Name output=$($checkerOutput -join ' | ')"
        }

        $result = if ($ExpectSuccess) { 'PASS' } else { 'FAIL_CLOSED' }
        Write-Output "PASS current-doc-case=$Name result=$result"
    } finally {
        if (Test-Path -LiteralPath $caseRoot) {
            Remove-Item -LiteralPath $caseRoot -Recurse -Force
        }
    }
}

$canonicalAttempt09Start = 'NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-START'
$canonicalAttempt09WorkBatch = 'GateW-OKX-READONLY-SOAK-ATTEMPT-09'
Assert-ActionType $canonicalAttempt09Start 'IMPLEMENTATION'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract 'NOT_STARTED' $canonicalAttempt09WorkBatch $canonicalAttempt09Start)) {
    throw 'CANONICAL_ATTEMPT_09_START_REJECTED'
}

function Assert-CrossDocumentAuthorityCase {
    param(
        [string] $Name,
        [string] $StatusContent,
        [string] $RoadmapContent,
        [bool] $ExpectSuccess,
        [string] $ExpectedErrorPattern = 'CURRENT_AUTHORITY_CROSS_DOCUMENT_MISMATCH',
        [string] $ReadmeContent = '',
        [string] $RootReadmeContent = ''
    )

    $caseRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("nq-cross-doc-authority-{0}-{1}" -f $Name, [guid]::NewGuid().ToString('N'))
    $currentDocsRoot = Join-Path $caseRoot 'docs/current'
    [System.IO.Directory]::CreateDirectory($currentDocsRoot) | Out-Null
    try {
        if ([string]::IsNullOrWhiteSpace($ReadmeContent)) {
            $ReadmeContent = $script:readmeFixture
        }
        if ([string]::IsNullOrWhiteSpace($RootReadmeContent)) {
            $RootReadmeContent = $script:rootReadmeFixture
        }
        Write-Utf8File (Join-Path $currentDocsRoot 'STATUS.md') $StatusContent
        Write-Utf8File (Join-Path $currentDocsRoot 'ROADMAP.md') $RoadmapContent
        Write-Utf8File (Join-Path $currentDocsRoot 'README.md') $ReadmeContent
        Write-Utf8File (Join-Path $caseRoot 'README.md') $RootReadmeContent

        $shellPath = (Get-Process -Id $PID).Path
        $checkerOutput = @(& $shellPath -NoProfile -ExecutionPolicy Bypass `
            -File $script:authorityChecker `
            -StatusPath (Join-Path $currentDocsRoot 'STATUS.md') `
            -RoadmapPath (Join-Path $currentDocsRoot 'ROADMAP.md') `
            -ReadmePath (Join-Path $currentDocsRoot 'README.md') `
            -RootReadmePath (Join-Path $caseRoot 'README.md') `
            -CurrentDocsPath $currentDocsRoot 2>&1)
        $exitCode = $LASTEXITCODE

        if ($ExpectSuccess -and $exitCode -ne 0) {
            throw "CROSS_DOCUMENT_CASE_UNEXPECTED_FAILURE case=$Name output=$($checkerOutput -join ' | ')"
        }
        if (-not $ExpectSuccess -and $exitCode -eq 0) {
            throw "CROSS_DOCUMENT_CASE_UNEXPECTED_PASS case=$Name output=$($checkerOutput -join ' | ')"
        }
        if (-not $ExpectSuccess -and
            -not ($checkerOutput -match $ExpectedErrorPattern)) {
            throw "CROSS_DOCUMENT_CASE_MISSING_CONFLICT case=$Name output=$($checkerOutput -join ' | ')"
        }

        $result = if ($ExpectSuccess) { 'PASS' } else { 'FAIL_CLOSED' }
        Write-Output "PASS cross-document-case=$Name result=$result"
    } finally {
        if (Test-Path -LiteralPath $caseRoot) {
            Remove-Item -LiteralPath $caseRoot -Recurse -Force
        }
    }
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

$securityReviewAcceptedStatus = 'SECURITY_REVIEW_ACCEPTED|CI_GREEN|DEPLOYMENT_PENDING'
$remediationDeploymentVerification =
        'NQ-GATEW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT-VERIFICATION'
Assert-ActionType $remediationDeploymentVerification `
    'REMEDIATION_IMMUTABLE_RELEASE_DEPLOYMENT_VERIFICATION'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $securityReviewAcceptedStatus $remediationWorkBatch `
        $remediationDeploymentVerification)) {
    throw 'CANONICAL_REMEDIATION_DEPLOYMENT_VERIFICATION_REJECTED'
}
Write-Output 'PASS canonical-remediation-deployment-verification exact-triple=true'

foreach ($status in @(
    'SECURITY_REVIEW_ACCEPTED|CI_GREEN|DEPLOYMENT_PENDNG',
    'SECURITY_REVIEW_ACCEPTED|CI_GREEN|DEPLOYMENT_PENDING|READY',
    'security_review_accepted|ci_green|deployment_pending',
    $remediationImplementedStatus
)) {
    if (Test-GovernanceNextActionForWorkBatch `
            $contract $status $remediationWorkBatch $remediationDeploymentVerification) {
        throw "NON_CANONICAL_DEPLOYMENT_STATUS_ACCEPTED status=$status"
    }
}
foreach ($action in @(
    'NQ-GATEW-ATTEMPT-10-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT-VERIFICATION',
    'NQ-GATEW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT-VERIFICATION-LATER',
    'NQ-GATEW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT-VERIFCATION',
    'nq-gatew-remediation-immutable-release-deployment-verification'
)) {
    $actualType = Get-GovernanceNextActionType $contract $action
    if ($actualType -ceq 'REMEDIATION_IMMUTABLE_RELEASE_DEPLOYMENT_VERIFICATION') {
        throw "NON_CANONICAL_DEPLOYMENT_ACTION_CLASSIFIED_AS_EXACT action=$action"
    }
    if (Test-GovernanceNextActionForWorkBatch `
            $contract $securityReviewAcceptedStatus $remediationWorkBatch $action) {
        throw "NON_CANONICAL_DEPLOYMENT_ACTION_ACCEPTED action=$action"
    }
}
Write-Output 'PASS non-canonical-remediation-deployment-action relation=false'

$deploymentFailedStatus = 'DEPLOYMENT_VERIFICATION_FAILED|REMEDIATION_REQUIRED'
$deploymentWorkBatch = 'GateW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT'
$reproducibleBuildFix = 'NQ-GATEW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT-FIX'
Assert-ActionType $reproducibleBuildFix 'REPRODUCIBLE_BUILD_FIX'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $deploymentFailedStatus $deploymentWorkBatch $reproducibleBuildFix)) {
    throw 'CANONICAL_REPRODUCIBLE_BUILD_FIX_REJECTED'
}
Write-Output 'PASS canonical-reproducible-build-fix exact-triple=true'

$deploymentRetryStatus = 'IMPLEMENTED|CI_GREEN|DEPLOYMENT_RETRY_PENDING'
$deploymentFixWorkBatch = 'GateW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT-FIX'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $deploymentRetryStatus $deploymentFixWorkBatch `
        $remediationDeploymentVerification)) {
    throw 'CANONICAL_REPRODUCIBLE_BUILD_DEPLOYMENT_RETRY_REJECTED'
}
Write-Output 'PASS canonical-reproducible-build-deployment-retry exact-triple=true'

$deploymentVerifiedStatus = 'DEPLOYMENT_VERIFIED|CI_GREEN|ATTEMPT_10_PREPARATION_PENDING'
$attempt10Preparation = 'NQ-GATEW-ATTEMPT-10-PREPARATION-AND-START'
Assert-ActionType $attempt10Preparation 'ATTEMPT_PREPARATION_AND_START'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $deploymentVerifiedStatus $deploymentWorkBatch $attempt10Preparation)) {
    throw 'CANONICAL_ATTEMPT_10_PREPARATION_REJECTED'
}
Write-Output 'PASS canonical-attempt-10-preparation exact-triple=true'

$precreateRemediationStatus = 'IMPLEMENTED|CI_GREEN|DEPLOYMENT_PENDING'
$precreateRemediationWorkBatch = 'GateW-ATTEMPT-10-PRECREATE-PREREQUISITE-REMEDIATION'
$precreateRemediationDeploymentVerification =
        'NQ-GATEW-ATTEMPT-10-PRECREATE-PREREQUISITE-REMEDIATION-DEPLOYMENT-VERIFICATION'
Assert-ActionType $precreateRemediationDeploymentVerification `
    'PRECREATE_PREREQUISITE_REMEDIATION_DEPLOYMENT_VERIFICATION'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $precreateRemediationStatus $precreateRemediationWorkBatch `
        $precreateRemediationDeploymentVerification)) {
    throw 'CANONICAL_PRECREATE_REMEDIATION_DEPLOYMENT_VERIFICATION_REJECTED'
}
Write-Output 'PASS canonical-precreate-remediation-deployment-verification exact-triple=true'

$precreateDeploymentFailedStatus = 'DEPLOYMENT_VERIFICATION_FAILED|CODE_REMEDIATION_REQUIRED'
$precreateInternalReadbackFix =
        'NQ-GATEW-ATTEMPT-10-PRECREATE-PREREQUISITE-INTERNAL-READBACK-FAILURE-RCA-AND-FIX'
Assert-ActionType $precreateInternalReadbackFix `
    'PRECREATE_PREREQUISITE_INTERNAL_READBACK_FAILURE_RCA_AND_FIX'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $precreateDeploymentFailedStatus $precreateRemediationWorkBatch `
        $precreateInternalReadbackFix)) {
    throw 'CANONICAL_PRECREATE_INTERNAL_READBACK_FIX_REJECTED'
}
Write-Output 'PASS canonical-precreate-internal-readback-fix exact-triple=true'

$releaseCandidateStabilizationStatus =
        'IMPLEMENTED|CI_GREEN|DISPOSABLE_LINUX_VALIDATION_PASSED'
$releaseCandidateStabilizationWorkBatch =
        'GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION'
$releaseCandidateStabilizationReview =
        'NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW'
Assert-ActionType $releaseCandidateStabilizationReview `
    'RELEASE_CANDIDATE_STABILIZATION_REVIEW'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $releaseCandidateStabilizationStatus `
        $releaseCandidateStabilizationWorkBatch `
        $releaseCandidateStabilizationReview)) {
    throw 'CANONICAL_RELEASE_CANDIDATE_STABILIZATION_REVIEW_REJECTED'
}
Write-Output 'PASS canonical-release-candidate-stabilization-review exact-triple=true'

$releaseCandidateReviewRejectedStatus = 'REVIEW_REJECTED|REMEDIATION_REQUIRED'
$releaseCandidateStabilizationFix =
        'NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX'
Assert-ActionType $releaseCandidateStabilizationFix `
    'RELEASE_CANDIDATE_STABILIZATION_FIX'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $releaseCandidateReviewRejectedStatus `
        $releaseCandidateStabilizationWorkBatch `
        $releaseCandidateStabilizationFix)) {
    throw 'CANONICAL_RELEASE_CANDIDATE_STABILIZATION_FIX_REJECTED'
}
if (-not (Test-GovernanceLifecycleTransition `
        $contract 'highRisk' $releaseCandidateStabilizationStatus `
        $releaseCandidateReviewRejectedStatus)) {
    throw 'CANONICAL_RELEASE_CANDIDATE_REVIEW_REJECTION_TRANSITION_REJECTED'
}
Write-Output 'PASS canonical-release-candidate-stabilization-fix exact-triple=true'

$releaseCandidateFixCompletedStatus = 'IMPLEMENTED|CI_GREEN|RC_REVIEW_PENDING'
$releaseCandidateFixWorkBatch =
        'GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $releaseCandidateFixCompletedStatus `
        $releaseCandidateFixWorkBatch `
        $releaseCandidateStabilizationReview)) {
    throw 'CANONICAL_RELEASE_CANDIDATE_FIX_REVIEW_REJECTED'
}
if (-not (Test-GovernanceLifecycleTransition `
        $contract 'highRisk' $releaseCandidateReviewRejectedStatus `
        $releaseCandidateFixCompletedStatus)) {
    throw 'CANONICAL_RELEASE_CANDIDATE_FIX_COMPLETION_TRANSITION_REJECTED'
}
Write-Output 'PASS canonical-release-candidate-fix-review exact-triple=true'

$releaseCandidateReviewWorkBatch =
        'GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW'
$releaseCandidateReviewAcceptedStatus = 'REVIEW_ACCEPTED|READY_TO_COMMIT'
$releaseCandidateReviewCommit =
        'NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW-COMMIT-AND-PUSH'
Assert-ActionType $releaseCandidateReviewCommit 'COMMIT_AND_PUSH'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $releaseCandidateReviewAcceptedStatus `
        $releaseCandidateReviewWorkBatch $releaseCandidateReviewCommit)) {
    throw 'CANONICAL_RC_REVIEW_COMMIT_REJECTED'
}

$releaseCandidateReviewCiPendingStatus = 'COMMITTED|CI_PENDING'
$releaseCandidateReviewCiAcceptance =
        'NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW-CI-ACCEPTANCE'
Assert-ActionType $releaseCandidateReviewCiAcceptance 'CI_WAIT_OR_INVESTIGATION'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $releaseCandidateReviewCiPendingStatus `
        $releaseCandidateReviewWorkBatch $releaseCandidateReviewCiAcceptance)) {
    throw 'CANONICAL_RC_REVIEW_CI_ACCEPTANCE_REJECTED'
}

$releaseCandidateDeploymentAuthorizedStatus = 'ACCEPTED|CI_GREEN|DEPLOYMENT_AUTHORIZED'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $releaseCandidateDeploymentAuthorizedStatus `
        $releaseCandidateReviewWorkBatch $attempt10Preparation)) {
    throw 'CANONICAL_RC_REVIEW_DEPLOYMENT_AUTHORIZATION_REJECTED'
}

$attempt10WorkBatch = 'GateW-OKX-READONLY-SOAK-ATTEMPT-10'
$attempt10RunningStatus = 'RUNNING|PENDING_168H'
$attempt10Acceptance = 'NQ-GATEW-ATTEMPT-10-168H-ACCEPTANCE'
Assert-ActionType $attempt10Acceptance 'SOAK_ACCEPTANCE'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $attempt10RunningStatus $attempt10WorkBatch $attempt10Acceptance)) {
    throw 'CANONICAL_ATTEMPT_10_ACCEPTANCE_REJECTED'
}

$attempt11PreparationWorkBatch = 'GateW-ATTEMPT-11-PREPARATION-AND-START'
$attempt11Preparation = 'NQ-GATEW-ATTEMPT-11-PREPARATION-AND-START'
$attempt11WorkBatch = 'GateW-OKX-READONLY-SOAK-ATTEMPT-11'
$attempt11Acceptance = 'NQ-GATEW-ATTEMPT-11-168H-ACCEPTANCE'
$attempt11Blocked = 'NQ-GATEW-ATTEMPT-11-PREPARATION-AND-START-BLOCKED'
Assert-ActionType $attempt11Preparation 'ATTEMPT_PREPARATION_AND_START'
Assert-ActionType $attempt11Acceptance 'SOAK_ACCEPTANCE'
Assert-ActionType $attempt11Blocked 'BLOCKED'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $releaseCandidateDeploymentAuthorizedStatus `
        $attempt11PreparationWorkBatch $attempt11Preparation)) {
    throw 'CANONICAL_ATTEMPT_11_PREPARATION_REJECTED'
}
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $attempt10RunningStatus $attempt11WorkBatch $attempt11Acceptance)) {
    throw 'CANONICAL_ATTEMPT_11_ACCEPTANCE_REJECTED'
}
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract 'BLOCKED' $attempt11PreparationWorkBatch $attempt11Blocked)) {
    throw 'CANONICAL_ATTEMPT_11_BLOCKED_REJECTED'
}
foreach ($case in @(
    @{Status=$releaseCandidateDeploymentAuthorizedStatus;Batch=$attempt11PreparationWorkBatch;Action=$attempt10Preparation},
    @{Status=$releaseCandidateDeploymentAuthorizedStatus;Batch=$releaseCandidateReviewWorkBatch;Action=$attempt11Preparation},
    @{Status=$attempt10RunningStatus;Batch=$attempt11WorkBatch;Action=$attempt10Acceptance},
    @{Status=$attempt10RunningStatus;Batch=$attempt10WorkBatch;Action=$attempt11Acceptance},
    @{Status='BLOCKED';Batch=$attempt11PreparationWorkBatch;Action='NQ-GATEW-ATTEMPT-10-PREPARATION-AND-START-BLOCKED'},
    @{Status=$releaseCandidateDeploymentAuthorizedStatus;Batch=$attempt11PreparationWorkBatch;Action='NQ-GATEW-ATTEMPT-12-PREPARATION-AND-START'}
)) {
    if (Test-GovernanceNextActionForWorkBatch $contract $case.Status $case.Batch $case.Action) {
        throw "CROSS_ATTEMPT_11_MAPPING_ACCEPTED batch=$($case.Batch) action=$($case.Action)"
    }
}
Write-Output 'PASS canonical-attempt-11-preparation-acceptance-and-blocked exact-triples=true'

$attempt12PreparationWorkBatch = 'GateW-ATTEMPT-12-PREPARATION-AND-START'
$attempt12Preparation = 'NQ-GATEW-ATTEMPT-12-PREPARATION-AND-START'
$attempt12WorkBatch = 'GateW-OKX-READONLY-SOAK-ATTEMPT-12'
$attempt12Acceptance = 'NQ-GATEW-ATTEMPT-12-168H-ACCEPTANCE'
$attempt12Blocked = 'NQ-GATEW-ATTEMPT-12-PREPARATION-AND-START-BLOCKED'
Assert-ActionType $attempt12Preparation 'ATTEMPT_PREPARATION_AND_START'
Assert-ActionType $attempt12Acceptance 'SOAK_ACCEPTANCE'
Assert-ActionType $attempt12Blocked 'BLOCKED'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $releaseCandidateDeploymentAuthorizedStatus `
        $attempt12PreparationWorkBatch $attempt12Preparation)) {
    throw 'CANONICAL_ATTEMPT_12_PREPARATION_REJECTED'
}
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $attempt10RunningStatus $attempt12WorkBatch $attempt12Acceptance)) {
    throw 'CANONICAL_ATTEMPT_12_ACCEPTANCE_REJECTED'
}
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract 'BLOCKED' $attempt12PreparationWorkBatch $attempt12Blocked)) {
    throw 'CANONICAL_ATTEMPT_12_BLOCKED_REJECTED'
}
foreach ($case in @(
    @{Status=$releaseCandidateDeploymentAuthorizedStatus;Batch=$attempt12PreparationWorkBatch;Action=$attempt11Preparation},
    @{Status=$releaseCandidateDeploymentAuthorizedStatus;Batch=$attempt11PreparationWorkBatch;Action=$attempt12Preparation},
    @{Status=$attempt10RunningStatus;Batch=$attempt12WorkBatch;Action=$attempt11Acceptance},
    @{Status=$attempt10RunningStatus;Batch=$attempt11WorkBatch;Action=$attempt12Acceptance},
    @{Status='BLOCKED';Batch=$attempt12PreparationWorkBatch;Action=$attempt11Blocked},
    @{Status=$releaseCandidateDeploymentAuthorizedStatus;Batch=$attempt12PreparationWorkBatch;Action='NQ-GATEW-ATTEMPT-13-PREPARATION-AND-START'}
)) {
    if (Test-GovernanceNextActionForWorkBatch $contract $case.Status $case.Batch $case.Action) {
        throw "CROSS_ATTEMPT_12_MAPPING_ACCEPTED batch=$($case.Batch) action=$($case.Action)"
    }
}
Write-Output 'PASS canonical-attempt-12-preparation-acceptance-and-blocked exact-triples=true'

$attempt13PreparationWorkBatch = 'GateW-ATTEMPT-13-PREPARATION-AND-START'
$attempt13Preparation = 'NQ-GATEW-ATTEMPT-13-PREPARATION-AND-START'
$attempt13WorkBatch = 'GateW-OKX-READONLY-SOAK-ATTEMPT-13'
$attempt13Acceptance = 'NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE'
$attempt13Blocked = 'NQ-GATEW-ATTEMPT-13-PREPARATION-AND-START-BLOCKED'
Assert-ActionType $attempt13Preparation 'ATTEMPT_PREPARATION_AND_START'
Assert-ActionType $attempt13Acceptance 'SOAK_ACCEPTANCE'
Assert-ActionType $attempt13Blocked 'BLOCKED'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $releaseCandidateDeploymentAuthorizedStatus `
        $attempt13PreparationWorkBatch $attempt13Preparation)) {
    throw 'CANONICAL_ATTEMPT_13_PREPARATION_REJECTED'
}
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $attempt10RunningStatus $attempt13WorkBatch $attempt13Acceptance)) {
    throw 'CANONICAL_ATTEMPT_13_ACCEPTANCE_REJECTED'
}
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract 'BLOCKED' $attempt13PreparationWorkBatch $attempt13Blocked)) {
    throw 'CANONICAL_ATTEMPT_13_BLOCKED_REJECTED'
}
foreach ($case in @(
    @{Status=$releaseCandidateDeploymentAuthorizedStatus;Batch=$attempt13PreparationWorkBatch;Action=$attempt12Preparation},
    @{Status=$releaseCandidateDeploymentAuthorizedStatus;Batch=$attempt12PreparationWorkBatch;Action=$attempt13Preparation},
    @{Status=$attempt10RunningStatus;Batch=$attempt13WorkBatch;Action=$attempt12Acceptance},
    @{Status=$attempt10RunningStatus;Batch=$attempt12WorkBatch;Action=$attempt13Acceptance},
    @{Status='BLOCKED';Batch=$attempt13PreparationWorkBatch;Action=$attempt12Blocked}
)) {
    if (Test-GovernanceNextActionForWorkBatch $contract $case.Status $case.Batch $case.Action) {
        throw "CROSS_ATTEMPT_13_MAPPING_ACCEPTED batch=$($case.Batch) action=$($case.Action)"
    }
}
Write-Output 'PASS canonical-attempt-13-preparation-acceptance-and-blocked exact-triples=true'

$attempt13AcceptanceWorkBatch = 'GateW-ATTEMPT-13-168H-ACCEPTANCE'
$attempt13AcceptedReadyStatus = 'ACCEPTED|READY_TO_COMMIT'
$attempt13AcceptedReadyAction = 'NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE-COMMIT-AND-PUSH'
$attempt13AcceptanceCiAction = 'NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE-CI-ACCEPTANCE'
$attempt13FreezeReadyStatus = 'ACCEPTED|CI_GREEN|FREEZE_READY'
$attempt13FreezeCloseoutAction = 'NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION'
$attempt13FailureStatus = 'FAILED|ACCEPTANCE_REJECTED|INCIDENT_REVIEW_COMPLETED'
$attempt13FailureAction = 'NQ-GATEW-ATTEMPT-13-FAILURE-REMEDIATION-IMPLEMENTATION'
$attempt13AcceptedAuthority = @{
    active_gate = 'GateW'
    active_gate_status = 'IN_PROGRESS|NOT_FROZEN'
    attempt = 'Attempt-13'
    attempt_status = 'COMPLETED|ACCEPTED'
    production_soak = 'COMPLETED'
    live = 'DISABLED'
    kill_switch = 'ENGAGED'
}

Assert-ActionType $attempt13AcceptedReadyAction 'COMMIT_AND_PUSH'
Assert-ActionType $attempt13AcceptanceCiAction 'CI_WAIT_OR_INVESTIGATION'
Assert-ActionType $attempt13FreezeCloseoutAction 'FREEZE_CLOSEOUT'
Assert-ActionType $attempt13FailureAction 'FAILURE_REMEDIATION_IMPLEMENTATION'
foreach ($case in @(
    @{Status=$attempt13AcceptedReadyStatus;Action=$attempt13AcceptedReadyAction;Name='accepted-ready-to-commit'},
    @{Status='COMMITTED|CI_PENDING';Action=$attempt13AcceptanceCiAction;Name='committed-to-ci-acceptance'},
    @{Status=$attempt13FreezeReadyStatus;Action=$attempt13FreezeCloseoutAction;Name='ci-green-to-freeze-closeout'}
)) {
    if (-not (Test-GovernanceNextActionForWorkBatch `
            $contract $case.Status $attempt13AcceptanceWorkBatch $case.Action $attempt13AcceptedAuthority)) {
        throw "ATTEMPT_13_ACCEPTANCE_MAPPING_REJECTED case=$($case.Name)"
    }
}
if (Test-GovernanceNextActionForWorkBatch `
        $contract $attempt13FreezeReadyStatus $attempt13AcceptanceWorkBatch $attempt13FreezeCloseoutAction) {
    throw 'ATTEMPT_13_FREEZE_MAPPING_ACCEPTED_WITHOUT_AUTHORITY_CONTEXT'
}
foreach ($fact in @('attempt','attempt_status','production_soak','live','kill_switch','active_gate_status')) {
    $badAuthority = @{} + $attempt13AcceptedAuthority
    $badAuthority[$fact] = switch ($fact) {
        'attempt' { 'Attempt-12' }
        'attempt_status' { 'RUNNING|PENDING_168H' }
        'production_soak' { 'RUNNING' }
        'live' { 'ENABLED' }
        'kill_switch' { 'DISENGAGED' }
        default { 'FROZEN|ACCEPTED|TAGGED' }
    }
    if (Test-GovernanceNextActionForWorkBatch `
            $contract $attempt13FreezeReadyStatus $attempt13AcceptanceWorkBatch `
            $attempt13FreezeCloseoutAction $badAuthority) {
        throw "ATTEMPT_13_FREEZE_SAFETY_GATE_BYPASSED fact=$fact"
    }
}
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $attempt13FailureStatus $attempt13WorkBatch $attempt13FailureAction)) {
    throw 'ATTEMPT_13_FAILURE_REMEDIATION_MAPPING_REJECTED'
}
foreach ($crossAttemptCommitAction in @(
    'NQ-GATEW-ATTEMPT-12-168H-ACCEPTANCE-COMMIT-AND-PUSH',
    'NQ-GATEW-ATTEMPT-14-168H-ACCEPTANCE-COMMIT-AND-PUSH'
)) {
    Assert-ActionType $crossAttemptCommitAction 'COMMIT_AND_PUSH'
    if (Test-GovernanceNextActionForWorkBatch `
            $contract $attempt13AcceptedReadyStatus $attempt13AcceptanceWorkBatch `
            $crossAttemptCommitAction $attempt13AcceptedAuthority) {
        throw "CROSS_ATTEMPT_13_COMMIT_MAPPING_ACCEPTED action=$crossAttemptCommitAction"
    }
}
foreach ($crossAttemptWorkBatch in @(
    'GateW-ATTEMPT-12-168H-ACCEPTANCE',
    'GateW-ATTEMPT-14-168H-ACCEPTANCE'
)) {
    if (Test-GovernanceNextActionForWorkBatch `
            $contract $attempt13AcceptedReadyStatus $crossAttemptWorkBatch `
            $attempt13AcceptedReadyAction $attempt13AcceptedAuthority) {
        throw "ATTEMPT_13_ACTION_ACCEPTED_FOR_CROSS_ATTEMPT_BATCH batch=$crossAttemptWorkBatch"
    }
}
if (Test-GovernanceNextActionForWorkBatch `
        $contract 'RUNNING|PENDING_168H' $attempt13WorkBatch `
        'NQ-GATEX-PLAN-IMPLEMENTATION') {
    throw 'ATTEMPT_13_RUNNING_ACCEPTED_DIRECT_GATEX_ACTION'
}
foreach ($invalidAction in @(
    'NQ-GATEW-ATTEMPT-12-168H-ACCEPTANCE-CI-ACCEPTANCE',
    'NQ-GATEW-ATTEMPT-14-168H-ACCEPTANCE-CI-ACCEPTANCE',
    'NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION-LATER',
    'nq-gatew-freeze-closeout-implementation'
)) {
    Assert-ActionType $invalidAction 'UNKNOWN'
}
Write-Output 'PASS canonical-attempt-13-acceptance-success-and-failure-mappings exact=true'

$freezeCloseoutInvalidStatuses = @(
    'NOT_STARTED',
    'IMPLEMENTED|PENDING_REVIEW',
    'REVIEW_ACCEPTED|READY_TO_COMMIT',
    'COMMITTED|CI_PENDING',
    'BLOCKED'
)
foreach ($case in @(
    @{Gate='GateX';WorkBatch='GateX-5'},
    @{Gate='GateW';WorkBatch='GateW-4'},
    @{Gate='GateY';WorkBatch='GateY-9'}
)) {
    $freezeWorkBatch = $case.WorkBatch
    $freezeAction = 'NQ-{0}-FREEZE-CLOSEOUT' -f $case.Gate.ToUpperInvariant()
    Assert-ActionType $freezeAction 'FREEZE_CLOSEOUT'
    if (-not (Test-GovernanceNextActionForWorkBatch `
            $contract $attempt13FreezeReadyStatus $freezeWorkBatch $freezeAction)) {
        throw "GENERIC_FREEZE_CLOSEOUT_MAPPING_REJECTED gate=$($case.Gate) workBatch=$freezeWorkBatch"
    }
    foreach ($status in $freezeCloseoutInvalidStatuses) {
        if (Test-GovernanceNextActionForWorkBatch $contract $status $freezeWorkBatch $freezeAction) {
            throw "GENERIC_FREEZE_CLOSEOUT_INVALID_STATUS_ACCEPTED gate=$($case.Gate) status=$status"
        }
    }
}
foreach ($invalidAction in @(
    'NQ-GATEX-FREEZE-CLOSEOUT-LATER',
    'NQ-GATEX-5-FREEZE-CLOSEOUT',
    'nq-gatex-freeze-closeout'
)) {
    Assert-ActionType $invalidAction 'UNKNOWN'
}
Write-Output 'PASS generic-freeze-closeout gates=GateX,GateW,GateY positive=3 negative-statuses=15'

$releaseCandidateReviewRemediation =
        'NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW-REMEDIATION'
Assert-ActionType $releaseCandidateReviewRemediation 'RELEASE_CANDIDATE_STABILIZATION_FIX'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $releaseCandidateReviewRejectedStatus `
        $releaseCandidateReviewWorkBatch $releaseCandidateReviewRemediation)) {
    throw 'CANONICAL_RC_REVIEW_REMEDIATION_REJECTED'
}

$releaseCandidateReviewCiFailedStatus = 'COMMITTED|CI_FAILED|FIX_REQUIRED'
$releaseCandidateReviewCiBlockerFix =
        'NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW-CI-BLOCKER-FIX'
Assert-ActionType $releaseCandidateReviewCiBlockerFix 'CI_BLOCKER_FIX'
if (-not (Test-GovernanceNextActionForWorkBatch `
        $contract $releaseCandidateReviewCiFailedStatus `
        $releaseCandidateReviewWorkBatch $releaseCandidateReviewCiBlockerFix)) {
    throw 'CANONICAL_RC_REVIEW_CI_BLOCKER_FIX_REJECTED'
}
Write-Output 'PASS canonical-rc-review-lifecycle exact-triples=7'

foreach ($case in @(
    @{Status=$releaseCandidateFixCompletedStatus;Batch=$releaseCandidateFixWorkBatch;Action=$attempt10Preparation;Name='rc-pending-direct-deployment'},
    @{Status=$releaseCandidateReviewAcceptedStatus;Batch=$releaseCandidateReviewWorkBatch;Action=$attempt10Preparation;Name='review-accepted-direct-deployment'},
    @{Status=$releaseCandidateReviewCiPendingStatus;Batch=$releaseCandidateReviewWorkBatch;Action=$attempt10Preparation;Name='ci-pending-direct-deployment'},
    @{Status=$releaseCandidateReviewCiFailedStatus;Batch=$releaseCandidateReviewWorkBatch;Action=$attempt10Preparation;Name='ci-failed-direct-deployment'},
    @{Status=$attempt09RunningStatus;Batch=$canonicalAttempt09WorkBatch;Action=$attempt10Acceptance;Name='attempt-09-to-attempt-10-acceptance'},
    @{Status=$releaseCandidateReviewAcceptedStatus;Batch=$releaseCandidateFixWorkBatch;Action=$releaseCandidateReviewCommit;Name='wrong-work-batch-correct-review-status'},
    @{Status=$releaseCandidateFixCompletedStatus;Batch=$releaseCandidateReviewWorkBatch;Action=$releaseCandidateReviewCommit;Name='correct-review-batch-wrong-status'},
    @{Status=$attempt10RunningStatus;Batch=$attempt10WorkBatch;Action='NQ-GATEW-ATTEMPT-10-FREEZE';Name='freeze-before-168h-acceptance'},
    @{Status=$attempt10RunningStatus;Batch=$attempt10WorkBatch;Action='NQ-GATEW-ATTEMPT-10-168H-ACCEPTANCE-LATER';Name='acceptance-suffix'},
    @{Status=$releaseCandidateReviewCiPendingStatus;Batch=$releaseCandidateReviewWorkBatch;Action='NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW-CI-ACCEPTANCE-LATER';Name='ci-acceptance-suffix'},
    @{Status=$releaseCandidateReviewRejectedStatus;Batch=$releaseCandidateReviewWorkBatch;Action=$attempt10Preparation;Name='remediation-not-deployment'}
)) {
    if (Test-GovernanceNextActionForWorkBatch $contract $case.Status $case.Batch $case.Action) {
        throw "NON_CANONICAL_RC_REVIEW_MAPPING_ACCEPTED case=$($case.Name)"
    }
}
Assert-ActionType 'NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW-CI-ACCEPTANCE-LATER' 'UNKNOWN'
Assert-ActionType 'NQ-GATEW-ATTEMPT-10-168H-ACCEPTANCE-LATER' 'UNKNOWN'
Write-Output 'PASS non-canonical-rc-review-mapping relation=false'

foreach ($case in @(
    @{ Status = 'DEPLOYMENT_VERIFICATION_FAILED|REMEDIATION_REQUIRD'; Batch = $deploymentWorkBatch; Action = $reproducibleBuildFix },
    @{ Status = $deploymentFailedStatus; Batch = 'GateW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT-ATTEMPT-10'; Action = $reproducibleBuildFix },
    @{ Status = $deploymentFailedStatus; Batch = $deploymentWorkBatch; Action = 'NQ-GATEW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT-FIX-LATER' },
    @{ Status = 'implemented|ci_green|deployment_retry_pending'; Batch = $deploymentFixWorkBatch; Action = $remediationDeploymentVerification },
    @{ Status = $deploymentRetryStatus; Batch = 'GateW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT-FIX-ATTEMPT-10'; Action = $remediationDeploymentVerification },
    @{ Status = 'DEPLOYMENT_VERIFIED|CI_GREEN|ATTEMPT_10_PREPARATION_PENDNG'; Batch = $deploymentWorkBatch; Action = $attempt10Preparation },
    @{ Status = $deploymentVerifiedStatus; Batch = 'GateW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT-FIX'; Action = $attempt10Preparation },
    @{ Status = $deploymentVerifiedStatus; Batch = $deploymentWorkBatch; Action = 'NQ-GATEW-ATTEMPT-10-START' },
    @{ Status = $deploymentVerifiedStatus; Batch = $deploymentWorkBatch; Action = 'nq-gatew-attempt-10-preparation-and-start' },
    @{ Status = 'IMPLEMENTED|CI_GREEN|DEPLOYMENT_PENDNG'; Batch = $precreateRemediationWorkBatch; Action = $precreateRemediationDeploymentVerification },
    @{ Status = $precreateRemediationStatus; Batch = 'GateW-ATTEMPT-10-PRECREATE-PREREQUISITE-REMEDIATON'; Action = $precreateRemediationDeploymentVerification },
    @{ Status = $precreateRemediationStatus; Batch = $precreateRemediationWorkBatch; Action = 'NQ-GATEW-ATTEMPT-10-PRECREATE-PREREQUISITE-DEPLOYMENT-VERIFICATION' },
    @{ Status = $precreateRemediationStatus; Batch = $precreateRemediationWorkBatch; Action = 'nq-gatew-attempt-10-precreate-prerequisite-remediation-deployment-verification' },
    @{ Status = 'DEPLOYMENT_VERIFICATION_FAILED|CODE_REMEDIATION_REQUIRD'; Batch = $precreateRemediationWorkBatch; Action = $precreateInternalReadbackFix },
    @{ Status = $precreateDeploymentFailedStatus; Batch = 'GateW-ATTEMPT-10-PRECREATE-PREREQUISITE-REMEDIATION-ATTEMPT-10'; Action = $precreateInternalReadbackFix },
    @{ Status = $precreateDeploymentFailedStatus; Batch = $precreateRemediationWorkBatch; Action = 'NQ-GATEW-ATTEMPT-10-PRECREATE-PREREQUISITE-INTERNAL-READBACK-FAILURE-RCA-AND-FIX-LATER' },
    @{ Status = $precreateDeploymentFailedStatus; Batch = $precreateRemediationWorkBatch; Action = 'nq-gatew-attempt-10-precreate-prerequisite-internal-readback-failure-rca-and-fix' },
    @{ Status = 'IMPLEMENTED|CI_GREEN|DISPOSABLE_LINUX_VALIDATION_PASSED_LATER'; Batch = $releaseCandidateStabilizationWorkBatch; Action = $releaseCandidateStabilizationReview },
    @{ Status = $releaseCandidateStabilizationStatus; Batch = 'GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-LATER'; Action = $releaseCandidateStabilizationReview },
    @{ Status = $releaseCandidateStabilizationStatus; Batch = $releaseCandidateStabilizationWorkBatch; Action = 'NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW-LATER' },
    @{ Status = $releaseCandidateStabilizationStatus; Batch = $releaseCandidateStabilizationWorkBatch; Action = 'nq-gatew-attempt-10-release-candidate-stabilization-review' },
    @{ Status = 'REVIEW_REJECTED|REMEDIATION_REQUIRD'; Batch = $releaseCandidateStabilizationWorkBatch; Action = $releaseCandidateStabilizationFix },
    @{ Status = $releaseCandidateReviewRejectedStatus; Batch = 'GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-LATER'; Action = $releaseCandidateStabilizationFix },
    @{ Status = $releaseCandidateReviewRejectedStatus; Batch = $releaseCandidateStabilizationWorkBatch; Action = 'NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX-LATER' },
    @{ Status = $releaseCandidateReviewRejectedStatus; Batch = $releaseCandidateStabilizationWorkBatch; Action = 'nq-gatew-attempt-10-release-candidate-stabilization-fix' },
    @{ Status = 'IMPLEMENTED|CI_GREEN|RC_REVIEW_PENDNG'; Batch = $releaseCandidateFixWorkBatch; Action = $releaseCandidateStabilizationReview },
    @{ Status = $releaseCandidateFixCompletedStatus; Batch = $releaseCandidateStabilizationWorkBatch; Action = $releaseCandidateStabilizationReview },
    @{ Status = $releaseCandidateStabilizationStatus; Batch = $releaseCandidateFixWorkBatch; Action = $releaseCandidateStabilizationReview },
    @{ Status = $releaseCandidateFixCompletedStatus; Batch = 'GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX-LATER'; Action = $releaseCandidateStabilizationReview },
    @{ Status = $releaseCandidateFixCompletedStatus; Batch = $releaseCandidateFixWorkBatch; Action = 'NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW-LATER' },
    @{ Status = $releaseCandidateFixCompletedStatus; Batch = $releaseCandidateFixWorkBatch; Action = 'nq-gatew-attempt-10-release-candidate-stabilization-review' }
)) {
    if (Test-GovernanceNextActionForWorkBatch `
            $contract $case.Status $case.Batch $case.Action) {
        throw "NON_CANONICAL_REPRODUCIBLE_BUILD_MAPPING_ACCEPTED status=$($case.Status) batch=$($case.Batch) action=$($case.Action)"
    }
}
Write-Output 'PASS non-canonical-reproducible-build-mapping relation=false'

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
if (-not (Test-GovernanceLifecycleTransition `
        $contract 'highRisk' $remediationImplementedStatus $securityReviewAcceptedStatus)) {
    throw 'REMEDIATION_SECURITY_ACCEPTANCE_LIFECYCLE_TRANSITION_REJECTED'
}
Write-Output 'PASS remediation-security-review-to-deployment-pending lifecycle=highRisk'
if (-not (Test-GovernanceLifecycleTransition `
        $contract 'highRisk' $securityReviewAcceptedStatus $deploymentFailedStatus)) {
    throw 'DEPLOYMENT_VERIFICATION_FAILURE_LIFECYCLE_TRANSITION_REJECTED'
}
Write-Output 'PASS deployment-verification-to-remediation-required lifecycle=highRisk'
if (-not (Test-GovernanceLifecycleTransition `
        $contract 'highRisk' $deploymentFailedStatus $deploymentRetryStatus)) {
    throw 'REPRODUCIBLE_BUILD_FIX_LIFECYCLE_TRANSITION_REJECTED'
}
Write-Output 'PASS reproducible-build-fix-to-deployment-retry lifecycle=highRisk'
if (-not (Test-GovernanceLifecycleTransition `
        $contract 'highRisk' $deploymentRetryStatus $deploymentVerifiedStatus)) {
    throw 'DEPLOYMENT_RETRY_TO_VERIFIED_LIFECYCLE_TRANSITION_REJECTED'
}
Write-Output 'PASS deployment-retry-to-verified lifecycle=highRisk'
if (-not (Test-GovernanceLifecycleTransition `
        $contract 'highRisk' $precreateDeploymentFailedStatus `
        $releaseCandidateStabilizationStatus)) {
    throw 'PRECREATE_READBACK_FIX_TO_STABILIZED_LIFECYCLE_TRANSITION_REJECTED'
}
Write-Output 'PASS precreate-readback-fix-to-stabilized lifecycle=highRisk'

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

$gateY6Action = 'NQ-GATEY-6-OKX-SPOT-REAL-PROVIDER-MUTATION-CONTRACT-IMPLEMENTATION'
if ((Get-GovernanceExpectedNextActionType $contract $continuationStatus) -cne 'SECURITY_RISK_REVIEW') {
    throw 'GLOBAL_CONTINUATION_TYPE_CHANGED'
}
if ((Get-GovernanceExpectedNextActionTypeForWorkBatch $contract $continuationStatus 'GateY-6' $gateY6Action) -cne 'IMPLEMENTATION' -or
    -not (Test-GovernanceNextActionForWorkBatch $contract $continuationStatus 'GateY-6' $gateY6Action)) {
    throw 'GATEY6_EXACT_CONTINUATION_OVERRIDE_REJECTED'
}
Write-Output 'PASS fixture=gatey6-exact-continuation-implementation-override'

$rejectedGateY6Cases = @(
    @{ Name='gatew-continuation-arbitrary-implementation'; Status=$continuationStatus; Batch='GateW-3'; Action='NQ-GATEW-3-ARBITRARY-IMPLEMENTATION' },
    @{ Name='gatey5-continuation-gatey6-action'; Status=$continuationStatus; Batch='GateY-5'; Action=$gateY6Action },
    @{ Name='gatey6-wrong-implementation'; Status=$continuationStatus; Batch='GateY-6'; Action='NQ-GATEY-6-OTHER-IMPLEMENTATION' },
    @{ Name='gatey6-near-match'; Status=$continuationStatus; Batch='GateY-6'; Action=($gateY6Action + '-X') },
    @{ Name='gatey6-mutation-implementation'; Status=$continuationStatus; Batch='GateY-6'; Action='NQ-GATEY-6-OKX-SPOT-REAL-PROVIDER-MUTATION-IMPLEMENTATION' },
    @{ Name='gatey6-real-provider-implementation'; Status=$continuationStatus; Batch='GateY-6'; Action='NQ-GATEY-6-OKX-SPOT-REAL-PROVIDER-IMPLEMENTATION' },
    @{ Name='gatey6-lowercase'; Status=$continuationStatus; Batch='GateY-6'; Action=$gateY6Action.ToLowerInvariant() },
    @{ Name='gatey6-implementation-review'; Status=$continuationStatus; Batch='GateY-6'; Action=($gateY6Action + '-REVIEW') },
    @{ Name='gatey06-leading-zero'; Status=$continuationStatus; Batch='GateY-6'; Action=$gateY6Action.Replace('NQ-GATEY-6-', 'NQ-GATEY-06-') },
    @{ Name='gatey6a-sub-batch'; Status=$continuationStatus; Batch='GateY-6'; Action=$gateY6Action.Replace('NQ-GATEY-6-', 'NQ-GATEY-6A-') },
    @{ Name='gatey6-security-risk-review-pretends-override'; Status=$continuationStatus; Batch='GateY-6'; Action='NQ-GATEY-6-OKX-SPOT-REAL-PROVIDER-MUTATION-CONTRACT-SECURITY-RISK-REVIEW' },
    @{ Name='gatey6-arbitrary-suffix-implementation'; Status=$continuationStatus; Batch='GateY-6'; Action='NQ-GATEY-6-ARBITRARY-IMPLEMENTATION' },
    @{ Name='gatey6-wrong-status'; Status='NOT_STARTED'; Batch='GateY-6'; Action=$gateY6Action },
    @{ Name='gatey6-wrong-work-batch'; Status=$continuationStatus; Batch='GateY-7'; Action=$gateY6Action },
    @{ Name='gatey6-wrong-status-and-batch'; Status='NOT_STARTED'; Batch='GateY-7'; Action=$gateY6Action }
)
foreach ($case in $rejectedGateY6Cases) {
    if (Test-GovernanceNextActionForWorkBatch $contract $case.Status $case.Batch $case.Action) {
        throw "GATEY6_OVERRIDE_FAIL_OPEN fixture=$($case.Name)"
    }
    Write-Output "PASS fixture=$($case.Name) rejected=true"
}

$contractValidationRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('nq-governance-contract-' + [guid]::NewGuid().ToString('N'))
[System.IO.Directory]::CreateDirectory($contractValidationRoot) | Out-Null
try {
    $contractJson = [System.IO.File]::ReadAllText($contractPath, (New-Object System.Text.UTF8Encoding($false)))
    foreach ($versionCase in @(
        @{ Name='old-version'; Version='1.4.0' },
        @{ Name='future-version'; Version='9.0.0' }
    )) {
        $versionPath = Join-Path $contractValidationRoot ($versionCase.Name + '.json')
        Write-Utf8File $versionPath ($contractJson.Replace('"schemaVersion": "1.5.0"', ('"schemaVersion": "{0}"' -f $versionCase.Version)))
        $rejected = $false
        try { $null = Get-GovernanceWorkflowContract $versionPath } catch { $rejected = $true }
        if (-not $rejected) { throw "CONTRACT_VERSION_FAIL_OPEN fixture=$($versionCase.Name)" }
        Write-Output "PASS fixture=contract-$($versionCase.Name)-rejected version=$($versionCase.Version)"
    }

    $duplicateContract = $contractJson | ConvertFrom-Json
    $duplicateContract.authority.exactNextActionMappings = @($duplicateContract.authority.exactNextActionMappings) +
        @($duplicateContract.authority.exactNextActionMappings | Where-Object {
            [string]$_.workBatchStatus -ceq $continuationStatus -and [string]$_.workBatch -ceq 'GateY-6'
        })
    $duplicatePath = Join-Path $contractValidationRoot 'duplicate-exact-mapping.json'
    Write-Utf8File $duplicatePath ($duplicateContract | ConvertTo-Json -Depth 100)
    $duplicateRejected = $false
    try { $null = Get-GovernanceWorkflowContract $duplicatePath } catch {
        $duplicateRejected = $_.Exception.Message -match 'duplicate_exact_mapping'
    }
    if (-not $duplicateRejected) { throw 'DUPLICATE_EXACT_MAPPING_FAIL_OPEN' }
    Write-Output 'PASS fixture=duplicate-exact-mapping-rejected'

    $malformedCases = @(
        @{ Name='override-wrong-scope'; Mutate={ param($m) $m.scope='STATUS' } },
        @{ Name='override-unknown-type'; Mutate={ param($m) $m.expectedActionTypeOverride='UNKNOWN_TYPE' } },
        @{ Name='override-blank'; Mutate={ param($m) $m.expectedActionTypeOverride='' } },
        @{ Name='override-action-mismatch'; Mutate={ param($m) $m.expectedActionTypeOverride='REVIEW' } },
        @{ Name='missing-status'; Mutate={ param($m) $m.PSObject.Properties.Remove('workBatchStatus') } },
        @{ Name='missing-work-batch'; Mutate={ param($m) $m.PSObject.Properties.Remove('workBatch') } },
        @{ Name='missing-next-action'; Mutate={ param($m) $m.PSObject.Properties.Remove('nextAction') } },
        @{ Name='unknown-status'; Mutate={ param($m) $m.workBatchStatus='UNKNOWN_STATUS' } },
        @{ Name='unknown-action'; Mutate={ param($m) $m.nextAction='NQ-GATEY-6-UNKNOWN' } }
    )
    foreach ($malformedCase in $malformedCases) {
        $malformedContract = $contractJson | ConvertFrom-Json
        $targetMapping = @($malformedContract.authority.exactNextActionMappings | Where-Object {
            [string]$_.workBatchStatus -ceq $continuationStatus -and [string]$_.workBatch -ceq 'GateY-6'
        })[0]
        & $malformedCase.Mutate $targetMapping
        $malformedPath = Join-Path $contractValidationRoot ($malformedCase.Name + '.json')
        Write-Utf8File $malformedPath ($malformedContract | ConvertTo-Json -Depth 100)
        $malformedRejected = $false
        try { $null = Get-GovernanceWorkflowContract $malformedPath } catch {
            $malformedRejected = $_.Exception.Message -match '^GOVERNANCE_CONTRACT_INVALID'
        }
        if (-not $malformedRejected) { throw "MALFORMED_CONTRACT_FAIL_OPEN fixture=$($malformedCase.Name)" }
        Write-Output "PASS fixture=contract-$($malformedCase.Name)-rejected"
    }
} finally {
    if (Test-Path -LiteralPath $contractValidationRoot) {
        Remove-Item -LiteralPath $contractValidationRoot -Recurse -Force
    }
}

foreach ($status in @($contract.authority.workBatchStatuses)) {
    $expected = Get-GovernanceExpectedNextActionType $contract ([string]$status)
    if ($expected -eq 'UNKNOWN') { throw "STATUS_ACTION_MAPPING_MISSING status=$status" }
    Write-Output "PASS status=$status expectedActionType=$expected"
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$script:authorityChecker = Join-Path $PSScriptRoot 'check-current-authority.ps1'
$script:statusFixture = [System.IO.File]::ReadAllText(
    (Join-Path $repoRoot 'docs/current/STATUS.md'),
    (New-Object System.Text.UTF8Encoding($false)))
$script:roadmapFixture = [System.IO.File]::ReadAllText(
    (Join-Path $repoRoot 'docs/current/ROADMAP.md'),
    (New-Object System.Text.UTF8Encoding($false)))
$script:readmeFixture = [System.IO.File]::ReadAllText(
    (Join-Path $repoRoot 'docs/current/README.md'),
    (New-Object System.Text.UTF8Encoding($false)))
$script:rootReadmeFixture = [System.IO.File]::ReadAllText(
    (Join-Path $repoRoot 'README.md'),
    (New-Object System.Text.UTF8Encoding($false)))
$currentAuthority = Read-GovernanceAuthorityBlock $script:statusFixture
if ($null -eq $currentAuthority -or
        [string]::IsNullOrWhiteSpace([string]$currentAuthority.next_action)) {
    throw 'CURRENT_AUTHORITY_FIXTURE_INVALID'
}
$currentNextAction = [string]$currentAuthority.next_action
$currentMachineAttemptAuthority = Read-MachineCurrentAttemptAuthority $currentAuthority
if (-not $currentMachineAttemptAuthority.IsValid) {
    throw "CURRENT_MACHINE_ATTEMPT_FIXTURE_INVALID reason=$($currentMachineAttemptAuthority.Reason)"
}

$nonAttemptGovernanceActionLabel = [regex]::Unescape(
    '\u6CBB\u7406 authority \u4E2D\u552F\u4E00\u4E0B\u4E00\u52A8\u4F5C\u7CBE\u786E\u4E3A')
$nonAttemptRoadmapActionLabel = [regex]::Unescape(
    '\u5F53\u524D\u552F\u4E00\u6CBB\u7406\u52A8\u4F5C\u662F')
$nonAttemptSummaryActionLabel = [regex]::Unescape(
    '\u5F53\u524D\u552F\u4E00\u52A8\u4F5C\u662F')
$nonAttemptStatusFixture = @(
    '# Current Status',
    '',
    '<!-- nq-current-authority:start',
    'authority_schema=3',
    'last_frozen_gate=GateW',
    'last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED',
    'last_frozen_gate_tag=nq-gatew-freeze',
    'last_frozen_gate_commit=1111111111111111111111111111111111111111',
    'active_gate=GateX',
    'active_gate_status=IN_PROGRESS|NOT_FROZEN',
    'accepted_batch=GateW-ATTEMPT-13-168H-ACCEPTANCE',
    'accepted_batch_status=ACCEPTED|CI_GREEN',
    'accepted_batch_implementation_commit=2222222222222222222222222222222222222222',
    'accepted_batch_acceptance_head=2222222222222222222222222222222222222222',
    'accepted_batch_ci_run=123',
    'work_batch=GateX-PLAN',
    'work_batch_status=NOT_STARTED',
    'work_batch_commit=NONE',
    'work_batch_ci_run=NOT_RUN',
    'next_action=NQ-GATEX-PLAN-IMPLEMENTATION',
    'live=DISABLED',
    'shadow_trading=NOT_ENABLED',
    'ai=NOT_STARTED',
    'dh_runtime=NOT_INTEGRATED',
    'integration_runtime=NOT_STARTED',
    'real_provider=NOT_IMPLEMENTED',
    'private_trading=NOT_IMPLEMENTED',
    'nq-current-authority:end -->',
    '',
    '- GateW: `FROZEN / ACCEPTED / TAGGED`.',
    '- GateX: `IN PROGRESS / NOT FROZEN`.',
    '- GateW-ATTEMPT-13-168H-ACCEPTANCE: `ACCEPTED / CI GREEN`.',
    '- GateX-PLAN: `NOT STARTED`.',
    '- Historical Attempt-10: `FAILED / STOPPED`; production deployment=`STOPPED`.',
    '- Historical Attempt-11: `FAILED / STOPPED`; production deployment=`STOPPED`.',
    '- Historical Attempt-12: `FAILED / STOPPED`; production deployment=`STOPPED`.',
    '- Historical Attempt-13: `COMPLETED / ACCEPTED`; production deployment=`STOPPED`.',
    '',
    ('{0} `NQ-GATEX-PLAN-IMPLEMENTATION`.' -f $nonAttemptGovernanceActionLabel)
) -join "`n"
$nonAttemptRoadmapFixture = @(
    '# Roadmap',
    '',
    '- Historical Attempt-10=`FAILED / STOPPED`; production deployment=`STOPPED`.',
    '- Historical Attempt-11=`FAILED / STOPPED`; production deployment=`STOPPED`.',
    '- Historical Attempt-12=`FAILED / STOPPED`; production deployment=`STOPPED`.',
    '- Historical Attempt-13=`COMPLETED / ACCEPTED`; production deployment=`STOPPED`.',
    ('- {0} `NQ-GATEX-PLAN-IMPLEMENTATION`.' -f $nonAttemptRoadmapActionLabel)
) -join "`n"
$nonAttemptReadmeFixture = @(
    '# Current Docs',
    '',
    '<!-- nq-current-summary:start -->',
    '- GateX: `IN PROGRESS / NOT FROZEN`.',
    ('- {0} `NQ-GATEX-PLAN-IMPLEMENTATION`.' -f $nonAttemptSummaryActionLabel),
    '<!-- nq-current-summary:end -->'
) -join "`n"
Assert-CrossDocumentAuthorityCase `
    'non-attempt-ignores-historical-attempts' $nonAttemptStatusFixture `
    $nonAttemptRoadmapFixture $true '' $nonAttemptReadmeFixture $nonAttemptReadmeFixture

$genericFreezeStatusFixture = $nonAttemptStatusFixture.Replace(
    'accepted_batch=GateW-ATTEMPT-13-168H-ACCEPTANCE',
    'accepted_batch=GateX-5')
$genericFreezeStatusFixture = $genericFreezeStatusFixture.Replace(
    'work_batch=GateX-PLAN',
    'work_batch=GateX-FREEZE')
$genericFreezeStatusFixture = $genericFreezeStatusFixture.Replace(
    'work_batch_status=NOT_STARTED',
    'work_batch_status=ACCEPTED|CI_GREEN|FREEZE_READY')
$genericFreezeStatusFixture = $genericFreezeStatusFixture.Replace(
    'work_batch_commit=NONE',
    'work_batch_commit=3333333333333333333333333333333333333333')
$genericFreezeStatusFixture = $genericFreezeStatusFixture.Replace(
    'work_batch_ci_run=NOT_RUN',
    'work_batch_ci_run=456')
$genericFreezeStatusFixture = $genericFreezeStatusFixture.Replace(
    'NQ-GATEX-PLAN-IMPLEMENTATION',
    'NQ-GATEX-FREEZE-CLOSEOUT')
$genericFreezeStatusFixture = $genericFreezeStatusFixture.Replace(
    '- GateW-ATTEMPT-13-168H-ACCEPTANCE: `ACCEPTED / CI GREEN`.',
    '- GateX-5: `ACCEPTED / CI GREEN`.')
$genericFreezeStatusFixture = $genericFreezeStatusFixture.Replace(
    '- GateX-PLAN: `NOT STARTED`.',
    '- GateX-FREEZE: `ACCEPTED / CI GREEN / FREEZE READY`.')
$genericFreezeRoadmapFixture = $nonAttemptRoadmapFixture.Replace(
    'NQ-GATEX-PLAN-IMPLEMENTATION',
    'NQ-GATEX-FREEZE-CLOSEOUT')
$genericFreezeReadmeFixture = $nonAttemptReadmeFixture.Replace(
    'NQ-GATEX-PLAN-IMPLEMENTATION',
    'NQ-GATEX-FREEZE-CLOSEOUT')
Assert-CrossDocumentAuthorityCase `
    'generic-gatex-freeze-closeout-authority' $genericFreezeStatusFixture `
    $genericFreezeRoadmapFixture $true '' $genericFreezeReadmeFixture $genericFreezeReadmeFixture

$gateXConsolidationStatusFixture = $nonAttemptStatusFixture.Replace(
    'NQ-GATEX-PLAN-IMPLEMENTATION',
    $gateXConsolidationAction)
$gateXConsolidationRoadmapFixture = $nonAttemptRoadmapFixture.Replace(
    'NQ-GATEX-PLAN-IMPLEMENTATION',
    $gateXConsolidationAction)
$gateXConsolidationReadmeFixture = $nonAttemptReadmeFixture.Replace(
    'NQ-GATEX-PLAN-IMPLEMENTATION',
    $gateXConsolidationAction)
Assert-CrossDocumentAuthorityCase `
    'gatex-branch-consolidation-exact-action' $gateXConsolidationStatusFixture `
    $gateXConsolidationRoadmapFixture $true '' `
    $gateXConsolidationReadmeFixture $gateXConsolidationReadmeFixture

$gateXNonImplementationAction = 'NQ-POST-GATEW-BRANCH-CONSOLIDATION-AND-DEV-REBASE-REVIEW'
Assert-CrossDocumentAuthorityCase `
    'gatex-branch-consolidation-non-implementation-rejected' `
    ($nonAttemptStatusFixture.Replace('NQ-GATEX-PLAN-IMPLEMENTATION', $gateXNonImplementationAction)) `
    ($nonAttemptRoadmapFixture.Replace('NQ-GATEX-PLAN-IMPLEMENTATION', $gateXNonImplementationAction)) `
    $false 'NEXT_ACTION_TYPE_MISMATCH' `
    ($nonAttemptReadmeFixture.Replace('NQ-GATEX-PLAN-IMPLEMENTATION', $gateXNonImplementationAction)) `
    ($nonAttemptReadmeFixture.Replace('NQ-GATEX-PLAN-IMPLEMENTATION', $gateXNonImplementationAction))

$gateXWildcardImplementationAction = 'NQ-POST-GATEW-UNAUTHORIZED-IMPLEMENTATION'
Assert-CrossDocumentAuthorityCase `
    'gatex-branch-consolidation-wildcard-rejected' `
    ($nonAttemptStatusFixture.Replace('NQ-GATEX-PLAN-IMPLEMENTATION', $gateXWildcardImplementationAction)) `
    ($nonAttemptRoadmapFixture.Replace('NQ-GATEX-PLAN-IMPLEMENTATION', $gateXWildcardImplementationAction)) `
    $false 'NEXT_ACTION_WORK_BATCH_MISMATCH' `
    ($nonAttemptReadmeFixture.Replace('NQ-GATEX-PLAN-IMPLEMENTATION', $gateXWildcardImplementationAction)) `
    ($nonAttemptReadmeFixture.Replace('NQ-GATEX-PLAN-IMPLEMENTATION', $gateXWildcardImplementationAction))

if (-not $currentMachineAttemptAuthority.IsApplicable) {
    if (-not (Test-GovernanceNextActionForWorkBatch `
            $contract $currentAuthority.work_batch_status `
            $currentAuthority.work_batch $currentNextAction)) {
        throw 'CURRENT_NON_ATTEMPT_CANONICAL_TRIPLE_REJECTED'
    }
    Assert-CrossDocumentAuthorityCase `
        'non-attempt-authority-aligned' $script:statusFixture $script:roadmapFixture $true
    $nonAttemptNextActionMismatchRoadmap = $script:roadmapFixture.Replace(
        $currentNextAction,
        'NQ-GATEW-ATTEMPT-10-168H-ACCEPTANCE')
    Assert-CrossDocumentAuthorityCase `
        'non-attempt-next-action-mismatch' $script:statusFixture `
        $nonAttemptNextActionMismatchRoadmap $false 'CURRENT_NEXT_ACTION_CONFLICT'
    Assert-CurrentDocsAuthorityCase `
        'non-attempt-readmes-consistent' $script:readmeFixture $true '' $script:rootReadmeFixture
    Write-Output 'PASS current-machine-attempt result=NOT_APPLICABLE ordinary-canonical-validation=PASS'
    Write-Output 'PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION'
    exit 0
}
$currentAttemptId = [int]$currentMachineAttemptAuthority.AttemptId

$canonicalRoadmapAttemptPattern = '(?m)^\s*-\s*[^\r\n]*?(?<clause>Attempt-(?<attemptId>[1-9][0-9]*)=`(?<attemptState>[A-Z_]+) / (?<authorizationState>[A-Z0-9_]+)`; production deployment=`(?<deploymentState>[A-Z_]+)`)[^\r\n]*\r?$'
$canonicalRoadmapAttemptMatches = @([regex]::Matches(
    $script:roadmapFixture,
    $canonicalRoadmapAttemptPattern) | Where-Object {
        [int]$_.Groups['attemptId'].Value -eq $currentAttemptId
    })
if ($canonicalRoadmapAttemptMatches.Count -ne 1) {
    throw 'CROSS_DOCUMENT_ROADMAP_FIXTURE_INVALID'
}
$canonicalRoadmapAttemptMatch = $canonicalRoadmapAttemptMatches[0]
$canonicalRoadmapAttemptClause = $canonicalRoadmapAttemptMatch.Groups['clause'].Value
$canonicalAttemptId = [int]$canonicalRoadmapAttemptMatch.Groups['attemptId'].Value
$canonicalAttemptState = $canonicalRoadmapAttemptMatch.Groups['attemptState'].Value
$canonicalAuthorizationState = $canonicalRoadmapAttemptMatch.Groups['authorizationState'].Value
$canonicalDeploymentState = $canonicalRoadmapAttemptMatch.Groups['deploymentState'].Value
Assert-CrossDocumentAuthorityCase `
    'attempt-authority-aligned' $script:statusFixture $script:roadmapFixture $true

$mismatchedAuthorizationState = if ($canonicalAuthorizationState -ceq 'NOT_AUTHORIZED') {
    'AUTHORIZED'
} else {
    'NOT_AUTHORIZED'
}
$authorizationMismatchRoadmap = $script:roadmapFixture.Replace(
    $canonicalRoadmapAttemptClause,
    ('Attempt-{0}=`{1} / {2}`; production deployment=`{3}`' -f
        $canonicalAttemptId, $canonicalAttemptState, $mismatchedAuthorizationState,
        $canonicalDeploymentState))
Assert-CrossDocumentAuthorityCase `
    'authorization-mismatch' $script:statusFixture $authorizationMismatchRoadmap $false

$mismatchedAttemptState = if ($canonicalAttemptState -ceq 'COMPLETED') {
    'RUNNING'
} else {
    'COMPLETED'
}
$attemptStateMismatchRoadmap = $script:roadmapFixture.Replace(
    $canonicalRoadmapAttemptClause,
    ('Attempt-{0}=`{1} / {2}`; production deployment=`{3}`' -f
        $canonicalAttemptId, $mismatchedAttemptState, $canonicalAuthorizationState,
        $canonicalDeploymentState))
Assert-CrossDocumentAuthorityCase `
    'roadmap-attempt-status-conflict' $script:statusFixture $attemptStateMismatchRoadmap $false

$attemptIdMismatchRoadmap = $script:roadmapFixture.Replace(
    $canonicalRoadmapAttemptClause,
    ('Attempt-{0}=`{1} / {2}`; production deployment=`{3}`' -f
        ($canonicalAttemptId + 1), $canonicalAttemptState, $canonicalAuthorizationState,
        $canonicalDeploymentState))
Assert-CrossDocumentAuthorityCase `
    'attempt-id-mismatch' $script:statusFixture $attemptIdMismatchRoadmap $false

$mismatchedDeploymentState = if ($canonicalDeploymentState -ceq 'STARTED') {
    'STOPPED'
} else {
    'STARTED'
}
$deploymentMismatchRoadmap = $script:roadmapFixture.Replace(
    $canonicalRoadmapAttemptClause,
    ('Attempt-{0}=`{1} / {2}`; production deployment=`{3}`' -f
        $canonicalAttemptId, $canonicalAttemptState, $canonicalAuthorizationState,
        $mismatchedDeploymentState))
Assert-CrossDocumentAuthorityCase `
    'production-deployment-mismatch' $script:statusFixture $deploymentMismatchRoadmap $false

$nextActionMismatchRoadmap = $script:roadmapFixture.Replace(
    $currentNextAction,
    'NQ-GATEW-ATTEMPT-10-168H-ACCEPTANCE')
Assert-CrossDocumentAuthorityCase `
    'next-action-mismatch' $script:statusFixture $nextActionMismatchRoadmap $false `
    'CURRENT_NEXT_ACTION_CONFLICT'

$missingAttemptRoadmap = $script:roadmapFixture.Replace(
    $canonicalRoadmapAttemptClause,
    ("Attempt-{0} authority declaration missing" -f $canonicalAttemptId))
Assert-CrossDocumentAuthorityCase `
    'roadmap-attempt-missing' $script:statusFixture $missingAttemptRoadmap $false

$unknownAuthorizationRoadmap = $script:roadmapFixture.Replace(
    $canonicalRoadmapAttemptClause,
    ('Attempt-{0}=`{1} / MAYBE_AUTHORIZED`; production deployment=`{2}`' -f
        $canonicalAttemptId, $canonicalAttemptState, $canonicalDeploymentState))
Assert-CrossDocumentAuthorityCase `
    'unknown-authorization-token' $script:statusFixture $unknownAuthorizationRoadmap $false

$currentUniqueAllowedActionIs = [regex]::Unescape('\u5F53\u524D\u552F\u4E00\u5141\u8BB8\u52A8\u4F5C\u662F')
$currentUniqueActionIs = [regex]::Unescape('\u5F53\u524D\u552F\u4E00\u52A8\u4F5C\u662F')
function New-CurrentSummaryReadme {
    param(
        [int] $AttemptId,
        [string] $AttemptState,
        [string] $AuthorizationState,
        [string] $DeploymentState,
        [string] $NextAction,
        [bool] $IncludeHistoricalAttempt = $false
    )

    $lines = @(
        '# Current Docs',
        '',
        '<!-- nq-current-summary:start -->',
        '- GateW: `IN PROGRESS / NOT FROZEN`.',
        ('- GateW current Attempt-{0}=`{1} / {2}`; production deployment=`{3}`.' -f
            $AttemptId, $AttemptState, $AuthorizationState, $DeploymentState),
        ('- {0} `{1}`; canonical.' -f $currentUniqueActionIs, $NextAction),
        '<!-- nq-current-summary:end -->'
    )
    if ($IncludeHistoricalAttempt) {
        $lines += @(
            '',
            '## Historical Evidence',
            '',
            ('Attempt-09 is `REJECTED`; historical action `{0}` is not current.' -f
                $canonicalAttempt09Acceptance),
            '',
            '```text',
            ('- {0} `{1}`; historical code example.' -f
                $currentUniqueAllowedActionIs, $canonicalAttempt09Acceptance),
            '```'
        )
    }
    return $lines -join "`n"
}

$canonicalReadme = New-CurrentSummaryReadme `
    $canonicalAttemptId $canonicalAttemptState $canonicalAuthorizationState `
    $canonicalDeploymentState $currentNextAction
Assert-CurrentDocsAuthorityCase 'readme-consistent' $canonicalReadme $true

$staleAttemptOutsideSummaryReadme = @(
    $canonicalReadme,
    '',
    '## Stale prose',
    '',
    '- Attempt-12: `NOT_CREATED / AUTHORIZED`; production deployment=`NOT_STARTED`.'
) -join "`n"
Assert-CurrentDocsAuthorityCase `
    'readme-attempt-outside-summary' $canonicalReadme $false `
    'VOLATILE_CURRENT_CLAIM_OUTSIDE_SUMMARY.*kind=attempt_status' `
    $staleAttemptOutsideSummaryReadme
Assert-CurrentDocsAuthorityCase `
    'current-readme-attempt-outside-summary' $staleAttemptOutsideSummaryReadme $false `
    'VOLATILE_CURRENT_CLAIM_OUTSIDE_SUMMARY.*kind=attempt_status' `
    $canonicalReadme

$staleRuntimeOutsideSummaryReadme = @(
    $canonicalReadme,
    '',
    '## Stale prose',
    '',
    '- Commit `1111111111111111111111111111111111111111` remains the server current runtime release.'
) -join "`n"
Assert-CurrentDocsAuthorityCase `
    'readme-runtime-outside-summary' $canonicalReadme $false `
    'VOLATILE_CURRENT_CLAIM_OUTSIDE_SUMMARY.*kind=runtime_release' `
    $staleRuntimeOutsideSummaryReadme

$historicalLinkReadme = @(
    $canonicalReadme,
    '',
    '## Historical Evidence',
    '',
    '- [Attempt-09 evidence](evidence/NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-START.attempt-03.md)'
) -join "`n"
Assert-CurrentDocsAuthorityCase `
    'readme-historical-attempt-link' $historicalLinkReadme $true

$fencedExampleReadme = @(
    $canonicalReadme,
    '',
    '```text',
    '- Attempt-12: `NOT_CREATED / AUTHORIZED`; production deployment=`NOT_STARTED`.',
    '- canonical next_action=NQ-GATEW-ATTEMPT-12-PREPARATION-AND-START',
    '```'
) -join "`n"
Assert-CurrentDocsAuthorityCase 'readme-fenced-example' $fencedExampleReadme $true

$activeGateOutsideSummaryReadme = @(
    $canonicalReadme,
    '',
    '- GateV: `FROZEN / ACCEPTED / TAGGED`.'
) -join "`n"
Assert-CurrentDocsAuthorityCase `
    'readme-active-gate-outside-summary' $canonicalReadme $false `
    'VOLATILE_CURRENT_CLAIM_OUTSIDE_SUMMARY.*kind=active_gate_status' `
    $activeGateOutsideSummaryReadme

$duplicateSummaryReadme = @(
    $canonicalReadme,
    '',
    '<!-- nq-current-summary:start -->',
    '- GateW: `IN PROGRESS / NOT FROZEN`.',
    '<!-- nq-current-summary:end -->'
) -join "`n"
Assert-CurrentDocsAuthorityCase `
    'readme-summary-duplicate' $canonicalReadme $false `
    'CURRENT_SUMMARY_INVALID.*expected=1 actual=2' $duplicateSummaryReadme

$staleAttemptStatusReadme = New-CurrentSummaryReadme `
    $canonicalAttemptId 'NOT_CREATED' 'AUTHORIZED' 'NOT_STARTED' $currentNextAction
Assert-CurrentDocsAuthorityCase `
    'readme-attempt-status-conflict' $staleAttemptStatusReadme $false `
    'CURRENT_ATTEMPT_STATUS_CONFLICT'

$staleSecurityReviewReadme = New-CurrentSummaryReadme `
    $canonicalAttemptId $canonicalAttemptState $canonicalAuthorizationState `
    $canonicalDeploymentState $remediationSecurityReview
Assert-CurrentDocsAuthorityCase `
    'readme-stale-security-review' $staleSecurityReviewReadme $false `
    'CURRENT_NEXT_ACTION_CONFLICT'

$oldAcceptanceReadme = New-CurrentSummaryReadme `
    $canonicalAttemptId $canonicalAttemptState $canonicalAuthorizationState `
    $canonicalDeploymentState $canonicalAttempt09Acceptance
Assert-CurrentDocsAuthorityCase `
    'readme-old-acceptance' $oldAcceptanceReadme $false `
    'CURRENT_NEXT_ACTION_CONFLICT'

$lowercaseReadme = New-CurrentSummaryReadme `
    $canonicalAttemptId $canonicalAttemptState $canonicalAuthorizationState `
    $canonicalDeploymentState $currentNextAction.ToLowerInvariant()
Assert-CurrentDocsAuthorityCase `
    'readme-action-case-error' $lowercaseReadme $false `
    'CURRENT_NEXT_ACTION_CONFLICT'

$attempt10Readme = New-CurrentSummaryReadme `
    $canonicalAttemptId $canonicalAttemptState $canonicalAuthorizationState `
    $canonicalDeploymentState 'NQ-GATEW-ATTEMPT-10-START'
Assert-CurrentDocsAuthorityCase `
    'readme-attempt-10' $attempt10Readme $false `
    'CURRENT_NEXT_ACTION_CONFLICT'

$historicalReadme = New-CurrentSummaryReadme `
    $canonicalAttemptId $canonicalAttemptState $canonicalAuthorizationState `
    $canonicalDeploymentState $currentNextAction $true
Assert-CurrentDocsAuthorityCase 'readme-historical-and-code' $historicalReadme $true

$missingDeclarationReadme = @(
    '# Current Docs',
    '',
    'This index references STATUS and does not duplicate the current action.'
) -join "`n"
Assert-CurrentDocsAuthorityCase `
    'readme-declaration-missing' $missingDeclarationReadme $false `
    'CURRENT_SUMMARY_INVALID'

Write-Output 'PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION'
