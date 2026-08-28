<#
.SYNOPSIS
Validates the current machine authority against a Gate-neutral governance contract and a contract-owned safety profile.
#>
[CmdletBinding()]
param(
    [string] $StatusPath = 'docs/current/STATUS.md',
    [string] $ContractPath = 'scripts/docs/governance-workflow-contract.json'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'governance-workflow-lib.ps1')
$errors = New-Object System.Collections.Generic.List[string]

function Resolve-RepoPath {
    param([string] $Path)
    if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
    return Join-Path $repoRoot $Path
}

function Add-AuthorityError {
    param([string] $Code, [string] $Message)
    $script:errors.Add("$Code $Message")
    Write-Output "ERROR $Code $Message"
}

try { $contract = Get-GovernanceWorkflowContract (Resolve-RepoPath $ContractPath) } catch {
    Add-AuthorityError 'AUTHORITY_CONTRACT_INVALID' $_.Exception.Message
    $contract = $null
}

$statusFile = Resolve-RepoPath $StatusPath
if (-not (Test-Path -LiteralPath $statusFile -PathType Leaf)) {
    Add-AuthorityError 'CURRENT_AUTHORITY_CONFLICT' "STATUS_NOT_FOUND path=$StatusPath"
    $authority = $null
} else {
    $statusContent = [System.IO.File]::ReadAllText($statusFile, (New-Object System.Text.UTF8Encoding($false)))
    try { $authority = Read-GovernanceAuthorityBlock $statusContent } catch {
        Add-AuthorityError 'AUTHORITY_SCHEMA_INVALID' $_.Exception.Message
        $authority = $null
    }
}

if ($null -ne $contract -and $null -ne $authority) {
    $actualFields = @($authority.psobject.Properties.Name)
    foreach ($field in @($contract.authority.requiredFields)) {
        if ($actualFields -cnotcontains [string]$field) { Add-AuthorityError 'AUTHORITY_SCHEMA_INVALID' "FIELD_MISSING field=$field" }
    }
    foreach ($field in $actualFields) {
        if (@($contract.authority.requiredFields) -cnotcontains $field) { Add-AuthorityError 'AUTHORITY_SCHEMA_INVALID' "FIELD_UNDECLARED field=$field" }
    }

    if ([string]$authority.authority_schema -cne [string]$contract.authoritySchema) {
        Add-AuthorityError 'AUTHORITY_SCHEMA_INVALID' "SCHEMA_VERSION expected=$($contract.authoritySchema) actual=$($authority.authority_schema)"
    }
    if (-not (Test-GovernanceExactTokenSet $authority.last_frozen_gate_status @('FROZEN', 'ACCEPTED', 'TAGGED'))) {
        Add-AuthorityError 'CURRENT_AUTHORITY_CONFLICT' "LAST_FROZEN_STATUS_INVALID value=$($authority.last_frozen_gate_status)"
    }
    if (@($contract.authority.activeGateStatuses) -cnotcontains $authority.active_gate_status) {
        Add-AuthorityError 'CURRENT_AUTHORITY_CONFLICT' "ACTIVE_GATE_STATUS_INVALID value=$($authority.active_gate_status)"
    }
    if (@($contract.authority.acceptedBatchStatuses) -cnotcontains $authority.accepted_batch_status) {
        Add-AuthorityError 'CURRENT_AUTHORITY_CONFLICT' "ACCEPTED_BATCH_STATUS_INVALID value=$($authority.accepted_batch_status)"
    }
    if (@($contract.authority.workBatchStatuses) -cnotcontains $authority.work_batch_status) {
        Add-AuthorityError 'CURRENT_AUTHORITY_CONFLICT' "WORK_BATCH_STATUS_INVALID value=$($authority.work_batch_status)"
    } else {
        $fieldPolicy = Get-GovernancePropertyValue $contract.authority.workStatusFieldPolicies $authority.work_batch_status
        if ($null -eq $fieldPolicy -or $authority.work_batch_commit -notmatch [string]$fieldPolicy.commitPattern) {
            Add-AuthorityError 'CURRENT_AUTHORITY_CONFLICT' "WORK_BATCH_COMMIT_INVALID status=$($authority.work_batch_status) value=$($authority.work_batch_commit)"
        }
        if ($null -eq $fieldPolicy -or $authority.work_batch_ci_run -notmatch [string]$fieldPolicy.ciPattern) {
            Add-AuthorityError 'CURRENT_AUTHORITY_CONFLICT' "WORK_BATCH_CI_INVALID status=$($authority.work_batch_status) value=$($authority.work_batch_ci_run)"
        }
        if (-not (Test-GovernanceNextActionForWorkBatch $contract $authority.work_batch_status $authority.work_batch $authority.next_action $authority)) {
            $actualType = Get-GovernanceNextActionType $contract $authority.next_action
            Add-AuthorityError 'CURRENT_AUTHORITY_CONFLICT' "NEXT_ACTION_INVALID status=$($authority.work_batch_status) type=$actualType action=$($authority.next_action)"
        }
    }

    foreach ($field in @('last_frozen_gate_commit', 'accepted_batch_implementation_commit', 'accepted_batch_acceptance_head')) {
        $value = [string](Get-GovernancePropertyValue $authority $field)
        if ($value -notmatch '^[0-9a-f]{40}$') { Add-AuthorityError 'AUTHORITY_SCHEMA_INVALID' "COMMIT_INVALID field=$field" }
    }
    if ($authority.accepted_batch_ci_run -notmatch '^[0-9]+$') { Add-AuthorityError 'AUTHORITY_SCHEMA_INVALID' 'ACCEPTED_BATCH_CI_INVALID' }
    $canonicalTag = 'nq-{0}-freeze' -f (($authority.last_frozen_gate -replace '[^A-Za-z0-9]', '').ToLowerInvariant())
    if ($authority.last_frozen_gate_tag -cne $canonicalTag) {
        Add-AuthorityError 'CURRENT_AUTHORITY_CONFLICT' "LAST_FROZEN_TAG_INVALID expected=$canonicalTag actual=$($authority.last_frozen_gate_tag)"
    }

    $profile = Get-GovernancePropertyValue $contract.authority.safetyProfiles.byActiveGate $authority.active_gate
    if ($null -eq $profile) { $profile = $contract.authority.safetyProfiles.default }
    foreach ($field in @($contract.authority.safetyProfileRequiredFields)) {
        $allowed = @(Get-GovernancePropertyValue $profile $field)
        $value = [string](Get-GovernancePropertyValue $authority $field)
        if ($allowed.Count -eq 0 -or $allowed -cnotcontains $value) {
            Add-AuthorityError 'SAFETY_PROFILE_VIOLATION' "field=$field value=$value activeGate=$($authority.active_gate)"
        }
    }
}

Write-Output "AUTHORITY_CHECK errors=$($errors.Count)"
if ($errors.Count -gt 0) { Write-Output 'BLOCKED / CURRENT_AUTHORITY_CONFLICT'; exit 1 }
Write-Output 'PASS / CURRENT_AUTHORITY_VALID'
exit 0
