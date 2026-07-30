<#
.SYNOPSIS
Validates STATUS authority schema v3, Gate/work state, next_action, and fixed safety boundaries.
.NOTES
This checker does not scan Gate archives, tag objects, GitHub Actions, or remote tags.
#>
[CmdletBinding()]
param(
    [string] $StatusPath = 'docs/current/STATUS.md',
    [string] $PlanPath = 'docs/current/GATEV_PLAN.md',
    [string] $RoadmapPath = 'docs/current/ROADMAP.md',
    [string] $CurrentDocsPath = 'docs/current',
    [ValidateSet('NONE', 'ARCHIVE_FREEZE', 'RELEASE')]
    [string] $ReadinessMode = 'NONE'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'governance-workflow-lib.ps1')
$errors = New-Object System.Collections.Generic.List[string]

function Add-AuthorityError {
    param([string] $Message)
    $script:errors.Add($Message)
    Write-Output ("ERROR {0}" -f $Message)
}

function Resolve-RepoPath {
    param([string] $Path)
    if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
    return (Join-Path $repoRoot $Path)
}

function Read-Utf8File {
    param([string] $Path)
    return [System.IO.File]::ReadAllText($Path, (New-Object System.Text.UTF8Encoding($false)))
}

function Test-StatusPhrase {
    param([string] $Content, [string] $Subject, [string] $StatusPattern)
    $pattern = '(?im)^\s*-\s*{0}\s*(?:\x3a|\uff1a)[^\r\n]*{1}' -f [regex]::Escape($Subject), $StatusPattern
    return $Content -match $pattern
}

function Get-CurrentNextActionDeclarations {
    param([string] $RootPath)

    $declarations = New-Object System.Collections.Generic.List[object]
    $declarationPattern = '^\s*(?:-\s*)?(?:\u5F53\u524D\u552F\u4E00(?:\u5141\u8BB8|\u6CBB\u7406|\u4E0B\u4E00)?\u52A8\u4F5C(?:\u7CBE\u786E)?(?:\u4E3A|\u662F|\uFF1A|:)|\u6CBB\u7406 [Aa]uthority \u4E2D\u552F\u4E00\u4E0B\u4E00\u52A8\u4F5C\u7CBE\u786E\u4E3A)\s*`(?<action>[^`\r\n]+)`(?:[\uFF1B;\u3002]|$)'

    foreach ($file in @(Get-ChildItem -LiteralPath $RootPath -Recurse -File -Filter '*.md' | Sort-Object FullName)) {
        $content = Read-Utf8File $file.FullName
        $lines = [regex]::Split($content, '\r?\n')
        $inFence = $false
        $fenceCharacter = $null

        for ($index = 0; $index -lt $lines.Count; $index++) {
            $line = $lines[$index]
            $fenceMatch = [regex]::Match($line, '^\s*(?<fence>`{3,}|~{3,})')
            if ($fenceMatch.Success) {
                $currentFenceCharacter = $fenceMatch.Groups['fence'].Value.Substring(0, 1)
                if (-not $inFence) {
                    $inFence = $true
                    $fenceCharacter = $currentFenceCharacter
                } elseif ($currentFenceCharacter -ceq $fenceCharacter) {
                    $inFence = $false
                    $fenceCharacter = $null
                }
                continue
            }
            if ($inFence) { continue }

            $declarationMatch = [regex]::Match($line, $declarationPattern)
            if ($declarationMatch.Success) {
                $displayPath = $file.FullName
                $repoPrefix = $repoRoot + [System.IO.Path]::DirectorySeparatorChar
                if ($file.FullName.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
                    $displayPath = $file.FullName.Substring($repoPrefix.Length)
                }
                $declarations.Add([pscustomobject]@{
                    Action = $declarationMatch.Groups['action'].Value
                    File = $displayPath.Replace('\', '/')
                    Line = $index + 1
                })
            }
        }
    }

    return $declarations.ToArray()
}

$resolvedContract = Join-Path $PSScriptRoot 'governance-workflow-contract.json'
try {
    $contract = Get-GovernanceWorkflowContract $resolvedContract
} catch {
    Add-AuthorityError $_.Exception.Message
    $contract = $null
}

$resolvedStatus = Resolve-RepoPath $StatusPath
if (-not (Test-Path -LiteralPath $resolvedStatus -PathType Leaf)) {
    Add-AuthorityError "STATUS_NOT_FOUND $StatusPath"
} elseif ($null -ne $contract) {
    $statusContent = Read-Utf8File $resolvedStatus
    $authority = Read-GovernanceAuthorityBlock $statusContent
    if ($null -eq $authority) {
        Add-AuthorityError 'AUTHORITY_BLOCK_INVALID expected_exactly_one_unique_key_block'
    } else {
        $requiredKeys = @(
            'authority_schema',
            'last_frozen_gate', 'last_frozen_gate_status', 'last_frozen_gate_tag', 'last_frozen_gate_commit',
            'active_gate', 'active_gate_status',
            'accepted_batch', 'accepted_batch_status', 'accepted_batch_implementation_commit',
            'accepted_batch_acceptance_head', 'accepted_batch_ci_run',
            'work_batch', 'work_batch_status', 'work_batch_commit', 'work_batch_ci_run',
            'next_action', 'live', 'shadow_trading', 'ai', 'dh_runtime', 'integration_runtime',
            'real_provider', 'private_trading'
        )
        foreach ($key in $requiredKeys) {
            if (-not $authority.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($authority[$key])) {
                Add-AuthorityError "AUTHORITY_KEY_MISSING $key"
            }
        }
        foreach ($retiredKey in @(
            'current_gate', 'current_gate_status', 'current_gate_tag', 'next_gate', 'next_gate_status', 'updated_commit',
            'active_batch', 'active_batch_status', 'active_batch_implementation_commit',
            'active_batch_acceptance_head', 'active_batch_ci_run'
        )) {
            if ($authority.ContainsKey($retiredKey)) { Add-AuthorityError "AUTHORITY_SCHEMA_UNSUPPORTED retired_key=$retiredKey" }
        }

        $hasRequiredKeys = @($requiredKeys | Where-Object {
            -not $authority.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($authority[$_])
        }).Count -eq 0
        if ($hasRequiredKeys) {
            if ($authority.authority_schema -ne [string]$contract.authoritySchema) {
                Add-AuthorityError "AUTHORITY_SCHEMA_UNSUPPORTED expected=$($contract.authoritySchema) actual=$($authority.authority_schema)"
            }
            if (-not (Test-GovernanceExactTokenSet $authority.last_frozen_gate_status @('FROZEN', 'ACCEPTED', 'TAGGED'))) {
                Add-AuthorityError "LAST_FROZEN_GATE_STATUS_COMBINATION_INVALID value=$($authority.last_frozen_gate_status)"
            }
            if (@($contract.authority.activeGateStatuses) -cnotcontains $authority.active_gate_status) {
                Add-AuthorityError "ACTIVE_GATE_STATUS_COMBINATION_INVALID value=$($authority.active_gate_status)"
            }
            if (@($contract.authority.acceptedBatchStatuses) -cnotcontains $authority.accepted_batch_status) {
                Add-AuthorityError "ACCEPTED_BATCH_STATUS_INVALID value=$($authority.accepted_batch_status)"
            }
            if (@($contract.authority.workBatchStatuses) -cnotcontains $authority.work_batch_status) {
                Add-AuthorityError "WORK_BATCH_STATUS_INVALID value=$($authority.work_batch_status)"
            }
            if ($ReadinessMode -cne 'NONE' -and
                -not (Test-GovernanceReadinessStatus $contract $ReadinessMode $authority.work_batch_status)) {
                Add-AuthorityError "GATE_READINESS_STATUS_INVALID mode=$ReadinessMode status=$($authority.work_batch_status)"
            }
            if ($authority.accepted_batch -eq $authority.work_batch) {
                Add-AuthorityError "WORK_BATCH_STATUS_INVALID accepted_batch_and_work_batch_must_differ batch=$($authority.work_batch)"
            }
            if (-not $authority.work_batch.StartsWith($authority.active_gate + '-', [System.StringComparison]::Ordinal)) {
                Add-AuthorityError "WORK_BATCH_ACTIVE_GATE_MISMATCH active_gate=$($authority.active_gate) work_batch=$($authority.work_batch)"
            }

            foreach ($field in @('last_frozen_gate_commit', 'accepted_batch_implementation_commit', 'accepted_batch_acceptance_head')) {
                if ($authority[$field] -notmatch '^[0-9a-f]{40}$') { Add-AuthorityError "COMMIT_FIELD_FORMAT_INVALID field=$field value=$($authority[$field])" }
            }
            if ($authority.accepted_batch_ci_run -notmatch '^\d+$') {
                Add-AuthorityError "CI_FIELD_FORMAT_INVALID field=accepted_batch_ci_run value=$($authority.accepted_batch_ci_run)"
            }

            $fieldPolicy = Get-GovernancePropertyValue $contract.authority.workStatusFieldPolicies $authority.work_batch_status
            if ($null -eq $fieldPolicy) {
                Add-AuthorityError "WORK_BATCH_STATUS_POLICY_MISSING status=$($authority.work_batch_status)"
            } else {
                if ($authority.work_batch_commit -notmatch [string]$fieldPolicy.commitPattern) {
                    Add-AuthorityError "WORK_BATCH_COMMIT_STATE_MISMATCH status=$($authority.work_batch_status) commit=$($authority.work_batch_commit)"
                }
                if ($authority.work_batch_ci_run -notmatch [string]$fieldPolicy.ciPattern) {
                    Add-AuthorityError "WORK_BATCH_CI_STATE_MISMATCH status=$($authority.work_batch_status) ci_run=$($authority.work_batch_ci_run)"
                }
            }

            $expectedActionType = Get-GovernanceExpectedNextActionType $contract $authority.work_batch_status
            $actualActionType = Get-GovernanceNextActionType $contract $authority.next_action
            if ($expectedActionType -eq 'UNKNOWN' -or $actualActionType -ne $expectedActionType) {
                Add-AuthorityError "NEXT_ACTION_TYPE_MISMATCH status=$($authority.work_batch_status) expected=$expectedActionType actual=$actualActionType action=$($authority.next_action)"
            } elseif (-not (Test-GovernanceNextActionForWorkBatch $contract $authority.work_batch_status $authority.work_batch $authority.next_action)) {
                Add-AuthorityError "NEXT_ACTION_WORK_BATCH_MISMATCH work_batch=$($authority.work_batch) action=$($authority.next_action)"
            }

            if ($authority.work_batch_status -ceq 'COMMITTED|CI_FAILED|FIX_REQUIRED' -or
                $authority.work_batch_status -ceq 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED') {
                $workBatchMatch = [regex]::Match($authority.work_batch, '^(?<gate>Gate[A-Z0-9]+)-(?<number>[1-9][0-9]*)$', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
                if (-not $workBatchMatch.Success -or
                    -not [string]::Equals($workBatchMatch.Groups['gate'].Value, $authority.active_gate, [System.StringComparison]::Ordinal)) {
                    $errorCode = if ($authority.work_batch_status -ceq 'COMMITTED|CI_FAILED|FIX_REQUIRED') { 'CI_FAILED_WORK_BATCH_INVALID' } else { 'CI_GREEN_CONTINUATION_WORK_BATCH_INVALID' }
                    Add-AuthorityError "$errorCode active_gate=$($authority.active_gate) work_batch=$($authority.work_batch)"
                } else {
                    $workNumber = [int]$workBatchMatch.Groups['number'].Value
                    $expectedAcceptedBatch = if ($workNumber -eq 1) { "$($authority.active_gate)-PLAN" } else { "$($authority.active_gate)-$($workNumber - 1)" }
                    if (-not [string]::Equals($authority.accepted_batch, $expectedAcceptedBatch, [System.StringComparison]::Ordinal)) {
                        $errorCode = if ($authority.work_batch_status -ceq 'COMMITTED|CI_FAILED|FIX_REQUIRED') { 'CI_FAILED_ACCEPTED_BATCH_INVALID' } else { 'CI_GREEN_CONTINUATION_ACCEPTED_BATCH_INVALID' }
                        Add-AuthorityError "$errorCode expected=$expectedAcceptedBatch actual=$($authority.accepted_batch) work_batch=$($authority.work_batch)"
                    }
                }
            }

            foreach ($safetyFact in @{
                live = 'DISABLED'; shadow_trading = 'NOT_ENABLED'; ai = 'NOT_STARTED';
                dh_runtime = 'NOT_INTEGRATED'; integration_runtime = 'NOT_STARTED';
                real_provider = 'NOT_IMPLEMENTED'; private_trading = 'NOT_IMPLEMENTED'
            }.GetEnumerator()) {
                if ($authority[$safetyFact.Key] -ne $safetyFact.Value) {
                    Add-AuthorityError "SAFETY_FACT_CONTRADICTION key=$($safetyFact.Key) expected=$($safetyFact.Value) actual=$($authority[$safetyFact.Key])"
                }
            }

            $statusBody = [regex]::Replace($statusContent, '(?s)<!--\s*nq-current-authority:start.*?nq-current-authority:end\s*-->', '')
            if (-not (Test-StatusPhrase $statusBody $authority.last_frozen_gate 'FROZEN\s*/\s*ACCEPTED\s*/\s*TAGGED')) {
                Add-AuthorityError "LAST_FROZEN_GATE_BODY_CONTRADICTION gate=$($authority.last_frozen_gate)"
            }
            if (-not (Test-StatusPhrase $statusBody $authority.active_gate 'IN\s*PROGRESS\s*/\s*NOT\s*FROZEN')) {
                Add-AuthorityError "ACTIVE_GATE_BODY_CONTRADICTION gate=$($authority.active_gate)"
            }
            if (-not (Test-StatusPhrase $statusBody $authority.accepted_batch 'ACCEPTED\s*/\s*CI\s*GREEN')) {
                Add-AuthorityError "ACCEPTED_BATCH_STATUS_INVALID body_batch=$($authority.accepted_batch)"
            }
            $workPattern = Get-GovernanceWorkStatusPattern $contract $authority.work_batch_status
            if (-not (Test-StatusPhrase $statusBody $authority.work_batch $workPattern)) {
                Add-AuthorityError "WORK_BATCH_BODY_CONTRADICTION batch=$($authority.work_batch) expected=$($authority.work_batch_status) file=$StatusPath"
            }
            if ($authority.work_batch_status -ceq 'COMMITTED|CI_FAILED|FIX_REQUIRED' -and
                (Test-StatusPhrase $statusBody $authority.work_batch 'CI\s*GREEN')) {
                Add-AuthorityError "WORK_BATCH_BODY_CONTRADICTION batch=$($authority.work_batch) failed_state_must_not_claim_ci_green file=$StatusPath"
            }
            if ($authority.work_batch_status -ceq 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED' -and
                (Test-StatusPhrase $statusBody $authority.work_batch 'ACCEPTED\s*/\s*CI\s*GREEN')) {
                Add-AuthorityError "WORK_BATCH_BODY_CONTRADICTION batch=$($authority.work_batch) continuation_state_must_not_claim_accepted file=$StatusPath"
            }
            if (-not $statusBody.Contains($authority.next_action)) {
                Add-AuthorityError "NEXT_ACTION_MISMATCH expected=$($authority.next_action) file=$StatusPath"
            }

            $resolvedCurrentDocs = Resolve-RepoPath $CurrentDocsPath
            if (-not (Test-Path -LiteralPath $resolvedCurrentDocs -PathType Container)) {
                Add-AuthorityError "CURRENT_DOCS_NOT_FOUND $CurrentDocsPath"
            } else {
                foreach ($declaration in @(Get-CurrentNextActionDeclarations $resolvedCurrentDocs)) {
                    if (-not [string]::Equals(
                            $declaration.Action,
                            $authority.next_action,
                            [System.StringComparison]::Ordinal)) {
                        Add-AuthorityError ("CURRENT_DOC_NEXT_ACTION_MISMATCH expected={0} actual={1} file={2} line={3}" -f
                            $authority.next_action, $declaration.Action, $declaration.File, $declaration.Line)
                    }
                }
            }

            Write-Output ("AUTHORITY schema={0} last_frozen_gate={1} frozen_status={2} active_gate={3} active_status={4} accepted_batch={5} accepted_status={6} acceptance_head={7} accepted_ci_run={8} work_batch={9} work_status={10} work_commit={11} work_ci_run={12} next_action={13}" -f
                $authority.authority_schema, $authority.last_frozen_gate, $authority.last_frozen_gate_status,
                $authority.active_gate, $authority.active_gate_status, $authority.accepted_batch,
                $authority.accepted_batch_status, $authority.accepted_batch_acceptance_head,
                $authority.accepted_batch_ci_run, $authority.work_batch, $authority.work_batch_status,
                $authority.work_batch_commit, $authority.work_batch_ci_run, $authority.next_action)
        }
    }
}

if ($errors.Count -gt 0) {
    Write-Output ("AUTHORITY_CHECK errors={0}" -f $errors.Count)
    Write-Output 'BLOCKED / CURRENT_AUTHORITY_CONFLICT'
    exit 1
}
Write-Output 'AUTHORITY_CHECK errors=0'
Write-Output 'PASS / CURRENT_AUTHORITY_CONSISTENT'
exit 0
