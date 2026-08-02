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
    [string] $ReadmePath = 'docs/current/README.md',
    [string] $RootReadmePath = 'README.md',
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

function Read-AttemptDeploymentAuthority {
    param([string] $Content, [string] $SourceName, [Nullable[int]] $ExpectedAttemptId)

    $pattern = '(?im)^\s*-\s*[^\r\n]*?Attempt-(?<attemptId>[1-9][0-9]*)\s*(?:=|\x3a|\uff1a)\s*`(?<attemptState>[A-Z_]+)\s*/\s*(?<authorizationState>[A-Z0-9_]+)`[^\r\n]*?production deployment\s*=\s*`(?<deploymentState>[A-Z_]+)`'
    $matches = @([regex]::Matches($Content, $pattern))
    if ($null -ne $ExpectedAttemptId) {
        $matches = @($matches | Where-Object {
            [int]$_.Groups['attemptId'].Value -eq [int]$ExpectedAttemptId
        })
    }
    if ($matches.Count -ne 1) {
        return [pscustomobject]@{
            IsValid = $false
            Reason = "field=attempt_declaration attempt=$ExpectedAttemptId expected=1 actual=$($matches.Count)"
        }
    }

    $match = $matches[0]
    $snapshot = [pscustomobject]@{
        IsValid = $true
        Reason = ''
        AttemptId = [int]$match.Groups['attemptId'].Value
        AttemptState = $match.Groups['attemptState'].Value
        AuthorizationState = $match.Groups['authorizationState'].Value
        DeploymentState = $match.Groups['deploymentState'].Value
    }

    $allowedAttemptStates = @('NOT_CREATED', 'CREATED', 'RUNNING', 'FAILED', 'STOPPED', 'ACCEPTED')
    $allowedAuthorizationStates = @(
        'AUTHORIZED', 'NOT_AUTHORIZED', 'PENDING_168H', 'FAILED', 'STOPPED',
        'ACCEPTED', 'COMPLETED_168H'
    )
    $allowedDeploymentStates = @('NOT_STARTED', 'STARTED', 'STOPPED')
    $invalidFields = New-Object System.Collections.Generic.List[string]
    if ($allowedAttemptStates -cnotcontains $snapshot.AttemptState) {
        [void]$invalidFields.Add("field=attempt_state value=$($snapshot.AttemptState)")
    }
    if ($allowedAuthorizationStates -cnotcontains $snapshot.AuthorizationState) {
        [void]$invalidFields.Add("field=authorization_state value=$($snapshot.AuthorizationState)")
    }
    if ($allowedDeploymentStates -cnotcontains $snapshot.DeploymentState) {
        [void]$invalidFields.Add("field=production_deployment value=$($snapshot.DeploymentState)")
    }
    if ($invalidFields.Count -gt 0) {
        $snapshot.IsValid = $false
        $snapshot.Reason = $invalidFields -join ','
    }

    return $snapshot
}

function Read-RoadmapNextAction {
    param([string] $Content)

    $pattern = '(?im)^\s*-\s*\u5F53\u524D\u552F\u4E00\u6CBB\u7406\u52A8\u4F5C\u662F\s*`(?<action>[^`\r\n]+)`'
    $matches = [regex]::Matches($Content, $pattern)
    if ($matches.Count -ne 1) {
        return [pscustomobject]@{
            IsValid = $false
            Reason = "field=next_action expected=1 actual=$($matches.Count)"
            Value = ''
        }
    }
    return [pscustomobject]@{
        IsValid = $true
        Reason = ''
        Value = $matches[0].Groups['action'].Value
    }
}

function Read-CurrentSummaryBlock {
    param([string] $Content)

    $pattern = '(?s)<!--\s*nq-current-summary:start\s*(?<body>.*?)\s*nq-current-summary:end\s*-->'
    $matches = [regex]::Matches($Content, $pattern)
    if ($matches.Count -ne 1) {
        return [pscustomobject]@{
            IsValid = $false
            Reason = "field=current_summary expected=1 actual=$($matches.Count)"
            Body = ''
        }
    }
    return [pscustomobject]@{
        IsValid = $true
        Reason = ''
        Body = $matches[0].Groups['body'].Value
    }
}

function Read-CurrentSummaryNextAction {
    param([string] $Content)

    $pattern = '(?im)^\s*-\s*\u5F53\u524D\u552F\u4E00(?:\u4E0B\u4E00)?\u52A8\u4F5C\u662F\s*`(?<action>[^`\r\n]+)`'
    $matches = [regex]::Matches($Content, $pattern)
    if ($matches.Count -ne 1) {
        return [pscustomobject]@{
            IsValid = $false
            Reason = "field=next_action expected=1 actual=$($matches.Count)"
            Value = ''
        }
    }
    return [pscustomobject]@{
        IsValid = $true
        Reason = ''
        Value = $matches[0].Groups['action'].Value
    }
}

function Read-CurrentSummaryActiveGate {
    param([string] $Content)

    $pattern = '(?im)^\s*-\s*(?<gate>Gate[A-Z0-9]+)\s*(?:\x3a|\uff1a|=)\s*`(?<status>[A-Z][A-Z0-9_ ]*(?:\s*/\s*[A-Z][A-Z0-9_ ]*)+)`'
    $matches = [regex]::Matches($Content, $pattern)
    if ($matches.Count -ne 1) {
        return [pscustomobject]@{
            IsValid = $false
            Reason = "field=active_gate expected=1 actual=$($matches.Count)"
            Gate = ''
            Status = ''
        }
    }
    $normalizedStatus = @($matches[0].Groups['status'].Value -split '/' | ForEach-Object {
        $_.Trim() -replace '\s+', '_'
    }) -join '|'
    return [pscustomobject]@{
        IsValid = $true
        Reason = ''
        Gate = $matches[0].Groups['gate'].Value
        Status = $normalizedStatus
    }
}

function Get-ProseLinesOutsideCurrentSummary {
    param([string] $Content)

    $withoutSummary = [regex]::Replace(
        $Content,
        '(?s)<!--\s*nq-current-summary:start.*?nq-current-summary:end\s*-->',
        '')
    $lines = [regex]::Split($withoutSummary, '\r?\n')
    $result = New-Object System.Collections.Generic.List[object]
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
        if ($line -match '^\s*\[[^\]]+\]:\s*\S+') { continue }

        # Link destinations and HTML comments are references, not prose claims.
        $prose = [regex]::Replace($line, '\]\([^\r\n)]*\)', ']')
        $prose = [regex]::Replace($prose, '<!--.*?-->', '')
        $result.Add([pscustomobject]@{
            Line = $index + 1
            Text = $prose
        })
    }

    return $result.ToArray()
}

function Get-VolatileCurrentClaimsOutsideSummary {
    param([string] $Content)

    $patterns = @(
        @{ Kind = 'attempt_status'; Pattern = '(?i)\bAttempt-[1-9][0-9]*\s*(?:=|\x3a|\uff1a)\s*`?\s*(?:NOT_CREATED|CREATED|RUNNING|FAILED|STOPPED|ACCEPTED)\b' },
        @{ Kind = 'runtime_release'; Pattern = '(?i)(?:\bcurrent\s+(?:runtime\s+)?release\s*(?:=|\x3a|\uff1a|is\b)|(?:\bcommit\s+`?[0-9a-f]{7,40}`?|\bruntime\s+release)[^\r\n]{0,100}(?:\u670d\u52a1\u5668|server)[^\r\n]{0,40}\bcurrent\b)' },
        @{ Kind = 'current_work_commit'; Pattern = '(?i)\bcurrent\s+work\s+commit\s*(?:=|\x3a|\uff1a|is\b)' },
        @{ Kind = 'next_action'; Pattern = '(?i)(?:\bnext_action\s*=|\bcanonical\s+next_action\s*(?:=|\x3a|\uff1a)|\u5f53\u524d\u552f\u4e00(?:\u5141\u8bb8|\u6cbb\u7406|\u4e0b\u4e00)?\u52a8\u4f5c(?:\u7cbe\u786e)?(?:\u4e3a|\u662f|\x3a|\uff1a))' },
        @{ Kind = 'active_gate_status'; Pattern = '(?i)\bGate[A-Z0-9]+\s*(?:=|\x3a|\uff1a)\s*`[A-Z][A-Z0-9_ ]*(?:\s*/\s*[A-Z][A-Z0-9_ ]*)+`' }
    )
    $claims = New-Object System.Collections.Generic.List[object]
    foreach ($line in @(Get-ProseLinesOutsideCurrentSummary $Content)) {
        foreach ($candidate in $patterns) {
            if ($line.Text -match $candidate.Pattern) {
                $claims.Add([pscustomobject]@{
                    Kind = $candidate.Kind
                    Line = $line.Line
                })
            }
        }
    }
    return $claims.ToArray()
}

function Read-MachineCurrentAttemptAuthority {
    param([hashtable] $Authority)

    $workBatchMatch = [regex]::Match(
        $Authority.work_batch,
        '(?-i:^Gate[A-Z0-9]+(?:-[A-Z0-9_]+)*-ATTEMPT-(?<attemptId>[1-9][0-9]*)$)'
    )
    if (-not $workBatchMatch.Success) {
        return [pscustomobject]@{
            IsApplicable = $false
            IsValid = $true
            Reason = ''
        }
    }

    $statusTokens = @($Authority.work_batch_status -split '\|')
    if ($statusTokens.Count -ne 2) {
        return [pscustomobject]@{
            IsApplicable = $false
            IsValid = $true
            Reason = ''
        }
    }

    $allowedAttemptStates = @('NOT_CREATED', 'CREATED', 'RUNNING', 'FAILED', 'STOPPED', 'ACCEPTED')
    $allowedAuthorizationStates = @(
        'AUTHORIZED', 'NOT_AUTHORIZED', 'PENDING_168H', 'FAILED', 'STOPPED',
        'ACCEPTED', 'COMPLETED_168H'
    )
    if ($allowedAttemptStates -cnotcontains $statusTokens[0] -or
        $allowedAuthorizationStates -cnotcontains $statusTokens[1]) {
        return [pscustomobject]@{
            IsApplicable = $false
            IsValid = $true
            Reason = ''
        }
    }

    return [pscustomobject]@{
        IsApplicable = $true
        IsValid = $true
        Reason = ''
        AttemptId = [int]$workBatchMatch.Groups['attemptId'].Value
        AttemptState = $statusTokens[0]
        AuthorizationState = $statusTokens[1]
    }
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

            $hasScopedExactNextAction = Test-GovernanceScopedNextActionMapping `
                $contract $authority.work_batch_status $authority.work_batch $authority.next_action
            if (-not $hasScopedExactNextAction -and
                ($authority.work_batch_status -ceq 'COMMITTED|CI_FAILED|FIX_REQUIRED' -or
                    $authority.work_batch_status -ceq 'COMMITTED|CI_GREEN|CONTINUE_REQUIRED')) {
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
            $machineAttemptAuthority = Read-MachineCurrentAttemptAuthority $authority
            if ($machineAttemptAuthority.IsApplicable -and -not $machineAttemptAuthority.IsValid) {
                Add-AuthorityError "CURRENT_ATTEMPT_STATUS_CONFLICT source=STATUS_MACHINE $($machineAttemptAuthority.Reason)"
            }

            foreach ($readmeSpec in @(
                @{ Source = 'ROOT_README'; Path = $RootReadmePath },
                @{ Source = 'README'; Path = $ReadmePath }
            )) {
                $resolvedReadme = Resolve-RepoPath $readmeSpec.Path
                if (-not (Test-Path -LiteralPath $resolvedReadme -PathType Leaf)) {
                    Add-AuthorityError "CURRENT_SUMMARY_INVALID source=$($readmeSpec.Source) field=file path=$($readmeSpec.Path)"
                    continue
                }

                $readmeContent = Read-Utf8File $resolvedReadme
                $currentSummary = Read-CurrentSummaryBlock $readmeContent
                if (-not $currentSummary.IsValid) {
                    Add-AuthorityError "CURRENT_SUMMARY_INVALID source=$($readmeSpec.Source) $($currentSummary.Reason)"
                } else {
                    $summaryGate = Read-CurrentSummaryActiveGate $currentSummary.Body
                    if (-not $summaryGate.IsValid) {
                        Add-AuthorityError "CURRENT_SUMMARY_INVALID source=$($readmeSpec.Source) $($summaryGate.Reason)"
                    } elseif (-not [string]::Equals(
                            $authority.active_gate,
                            $summaryGate.Gate,
                            [System.StringComparison]::Ordinal) -or
                        -not [string]::Equals(
                            $authority.active_gate_status,
                            $summaryGate.Status,
                            [System.StringComparison]::Ordinal)) {
                        Add-AuthorityError ("CURRENT_ACTIVE_GATE_CONFLICT source={0} status={1}|{2} readme={3}|{4}" -f
                            $readmeSpec.Source, $authority.active_gate, $authority.active_gate_status,
                            $summaryGate.Gate, $summaryGate.Status)
                    }

                    $summaryNextAction = Read-CurrentSummaryNextAction $currentSummary.Body
                    if (-not $summaryNextAction.IsValid) {
                        Add-AuthorityError "CURRENT_SUMMARY_INVALID source=$($readmeSpec.Source) $($summaryNextAction.Reason)"
                    } elseif (-not [string]::Equals(
                            $authority.next_action,
                            $summaryNextAction.Value,
                            [System.StringComparison]::Ordinal)) {
                        Add-AuthorityError "CURRENT_NEXT_ACTION_CONFLICT source=$($readmeSpec.Source) status=$($authority.next_action) readme=$($summaryNextAction.Value)"
                    }

                    if ($machineAttemptAuthority.IsApplicable -and $machineAttemptAuthority.IsValid) {
                        $readmeAttemptAuthority = Read-AttemptDeploymentAuthority `
                            $currentSummary.Body $readmeSpec.Source $machineAttemptAuthority.AttemptId
                        if (-not $readmeAttemptAuthority.IsValid) {
                            Add-AuthorityError "CURRENT_ATTEMPT_STATUS_CONFLICT source=$($readmeSpec.Source) $($readmeAttemptAuthority.Reason)"
                        } else {
                            foreach ($field in @('AttemptId', 'AttemptState', 'AuthorizationState')) {
                                if (-not [string]::Equals(
                                        [string]$machineAttemptAuthority.$field,
                                        [string]$readmeAttemptAuthority.$field,
                                        [System.StringComparison]::Ordinal)) {
                                    Add-AuthorityError ("CURRENT_ATTEMPT_STATUS_CONFLICT source={0} field={1} status={2} readme={3}" -f
                                        $readmeSpec.Source, $field, $machineAttemptAuthority.$field,
                                        $readmeAttemptAuthority.$field)
                                }
                            }
                        }
                    }
                }

                foreach ($claim in @(Get-VolatileCurrentClaimsOutsideSummary $readmeContent)) {
                    Add-AuthorityError ("VOLATILE_CURRENT_CLAIM_OUTSIDE_SUMMARY source={0} kind={1} line={2}" -f
                        $readmeSpec.Source, $claim.Kind, $claim.Line)
                }
            }

            $resolvedRoadmap = Resolve-RepoPath $RoadmapPath
            if (-not (Test-Path -LiteralPath $resolvedRoadmap -PathType Leaf)) {
                Add-AuthorityError "CURRENT_AUTHORITY_CROSS_DOCUMENT_MISMATCH source=ROADMAP field=file path=$RoadmapPath"
            } else {
                $roadmapContent = Read-Utf8File $resolvedRoadmap
                $expectedAttemptId = $null
                $attemptActionMatch = [regex]::Match(
                    $authority.next_action,
                    '(?-i:^NQ-GATEW-ATTEMPT-(?<attemptId>[1-9][0-9]*)-)'
                )
                if ($attemptActionMatch.Success) {
                    $expectedAttemptId = [int]$attemptActionMatch.Groups['attemptId'].Value
                }
                $statusAttemptAuthority = Read-AttemptDeploymentAuthority `
                    $statusBody 'STATUS' $expectedAttemptId
                $roadmapAttemptAuthority = Read-AttemptDeploymentAuthority `
                    $roadmapContent 'ROADMAP' $expectedAttemptId
                if (-not $statusAttemptAuthority.IsValid) {
                    Add-AuthorityError "CURRENT_AUTHORITY_CROSS_DOCUMENT_MISMATCH source=STATUS $($statusAttemptAuthority.Reason)"
                }
                if (-not $roadmapAttemptAuthority.IsValid) {
                    Add-AuthorityError "CURRENT_AUTHORITY_CROSS_DOCUMENT_MISMATCH source=ROADMAP $($roadmapAttemptAuthority.Reason)"
                }
                if ($statusAttemptAuthority.IsValid -and $roadmapAttemptAuthority.IsValid) {
                    foreach ($field in @('AttemptId', 'AttemptState', 'AuthorizationState', 'DeploymentState')) {
                        if (-not [string]::Equals(
                                [string]$statusAttemptAuthority.$field,
                                [string]$roadmapAttemptAuthority.$field,
                                [System.StringComparison]::Ordinal)) {
                            Add-AuthorityError ("CURRENT_AUTHORITY_CROSS_DOCUMENT_MISMATCH field={0} status={1} roadmap={2}" -f
                                $field, $statusAttemptAuthority.$field, $roadmapAttemptAuthority.$field)
                        }
                    }
                }

                if ($machineAttemptAuthority.IsApplicable -and $machineAttemptAuthority.IsValid) {
                    foreach ($source in @(
                        @{ Name = 'STATUS'; Value = $statusAttemptAuthority },
                        @{ Name = 'ROADMAP'; Value = $roadmapAttemptAuthority }
                    )) {
                        if (-not $source.Value.IsValid) { continue }
                        foreach ($field in @('AttemptId', 'AttemptState', 'AuthorizationState')) {
                            if (-not [string]::Equals(
                                    [string]$machineAttemptAuthority.$field,
                                    [string]$source.Value.$field,
                                    [System.StringComparison]::Ordinal)) {
                                Add-AuthorityError ("CURRENT_ATTEMPT_STATUS_CONFLICT source={0} field={1} machine={2} document={3}" -f
                                    $source.Name, $field, $machineAttemptAuthority.$field, $source.Value.$field)
                            }
                        }
                    }
                }

                $roadmapNextAction = Read-RoadmapNextAction $roadmapContent
                if (-not $roadmapNextAction.IsValid) {
                    Add-AuthorityError "CURRENT_AUTHORITY_CROSS_DOCUMENT_MISMATCH source=ROADMAP $($roadmapNextAction.Reason)"
                } elseif (
                    -not [string]::Equals(
                        $authority.next_action,
                        $roadmapNextAction.Value,
                        [System.StringComparison]::Ordinal)) {
                    Add-AuthorityError "CURRENT_NEXT_ACTION_CONFLICT source=ROADMAP status=$($authority.next_action) roadmap=$($roadmapNextAction.Value)"
                }
            }
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
