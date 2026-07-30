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

function Write-Utf8File {
    param([string] $Path, [string] $Content)
    [System.IO.File]::WriteAllText($Path, $Content, (New-Object System.Text.UTF8Encoding($false)))
}

function Assert-CurrentDocsAuthorityCase {
    param(
        [string] $Name,
        [string] $ReadmeContent,
        [bool] $ExpectSuccess
    )

    $caseRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("nq-current-authority-{0}-{1}" -f $Name, [guid]::NewGuid().ToString('N'))
    $currentDocsRoot = Join-Path $caseRoot 'docs/current'
    [System.IO.Directory]::CreateDirectory($currentDocsRoot) | Out-Null
    try {
        Write-Utf8File (Join-Path $currentDocsRoot 'STATUS.md') $script:statusFixture
        Write-Utf8File (Join-Path $currentDocsRoot 'ROADMAP.md') $script:roadmapFixture
        Write-Utf8File (Join-Path $currentDocsRoot 'README.md') $ReadmeContent

        $shellPath = (Get-Process -Id $PID).Path
        $checkerOutput = @(& $shellPath -NoProfile -ExecutionPolicy Bypass `
            -File $script:authorityChecker `
            -StatusPath (Join-Path $currentDocsRoot 'STATUS.md') `
            -RoadmapPath (Join-Path $currentDocsRoot 'ROADMAP.md') `
            -CurrentDocsPath $currentDocsRoot 2>&1)
        $exitCode = $LASTEXITCODE

        if ($ExpectSuccess -and $exitCode -ne 0) {
            throw "CURRENT_DOC_CASE_UNEXPECTED_FAILURE case=$Name output=$($checkerOutput -join ' | ')"
        }
        if (-not $ExpectSuccess -and $exitCode -eq 0) {
            throw "CURRENT_DOC_CASE_UNEXPECTED_PASS case=$Name output=$($checkerOutput -join ' | ')"
        }
        if (-not $ExpectSuccess -and
            -not ($checkerOutput -match 'CURRENT_DOC_NEXT_ACTION_MISMATCH')) {
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
Assert-ActionType $attempt10Preparation 'ATTEMPT_10_PREPARATION_AND_START'
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
    @{ Status = $releaseCandidateStabilizationStatus; Batch = $releaseCandidateStabilizationWorkBatch; Action = 'nq-gatew-attempt-10-release-candidate-stabilization-review' }
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
$currentAuthority = Read-GovernanceAuthorityBlock $script:statusFixture
if ($null -eq $currentAuthority -or
        [string]::IsNullOrWhiteSpace([string]$currentAuthority.next_action)) {
    throw 'CURRENT_AUTHORITY_FIXTURE_INVALID'
}
$currentNextAction = [string]$currentAuthority.next_action

$currentUniqueAllowedActionIs = [regex]::Unescape('\u5F53\u524D\u552F\u4E00\u5141\u8BB8\u52A8\u4F5C\u662F')
$canonicalReadme = @(
    '# Current Docs',
    '',
    ('- {0} `{1}`; canonical.' -f
        $currentUniqueAllowedActionIs, $currentNextAction)
) -join "`n"
Assert-CurrentDocsAuthorityCase 'readme-consistent' $canonicalReadme $true

$staleSecurityReviewReadme = @(
    '# Current Docs',
    '',
    ('- {0} `{1}`; stale.' -f $currentUniqueAllowedActionIs, $remediationSecurityReview)
) -join "`n"
Assert-CurrentDocsAuthorityCase 'readme-stale-security-review' $staleSecurityReviewReadme $false

$oldAcceptanceReadme = @(
    '# Current Docs',
    '',
    ('- {0} `{1}`; stale.' -f $currentUniqueAllowedActionIs, $canonicalAttempt09Acceptance)
) -join "`n"
Assert-CurrentDocsAuthorityCase 'readme-old-acceptance' $oldAcceptanceReadme $false

$lowercaseReadme = @(
    '# Current Docs',
    '',
    ('- {0} `{1}`; case error.' -f
        $currentUniqueAllowedActionIs, $currentNextAction.ToLowerInvariant())
) -join "`n"
Assert-CurrentDocsAuthorityCase 'readme-action-case-error' $lowercaseReadme $false

$attempt10Readme = @(
    '# Current Docs',
    '',
    ('- {0} `NQ-GATEW-ATTEMPT-10-START`; forbidden attempt.' -f $currentUniqueAllowedActionIs)
) -join "`n"
Assert-CurrentDocsAuthorityCase 'readme-attempt-10' $attempt10Readme $false

$historicalReadme = @(
    '# Current Docs',
    '',
    ('Historical action `{0}`; this is not a current declaration.' -f $canonicalAttempt09Acceptance),
    '',
    '```text',
    ('- {0} `{1}`; historical code example.' -f $currentUniqueAllowedActionIs, $canonicalAttempt09Acceptance),
    '```'
) -join "`n"
Assert-CurrentDocsAuthorityCase 'readme-historical-and-code' $historicalReadme $true

$missingDeclarationReadme = @(
    '# Current Docs',
    '',
    'This index references STATUS and does not duplicate the current action.'
) -join "`n"
Assert-CurrentDocsAuthorityCase 'readme-declaration-missing' $missingDeclarationReadme $true

Write-Output 'PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION'
