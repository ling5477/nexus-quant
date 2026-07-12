<#
.SYNOPSIS
Read STATUS authority schema v3 and validate frozen Gate, accepted batch, work batch, Git, and current-entry semantics.
.NOTES
This script does not modify docs or Git state. Every conflict ends with BLOCKED / CURRENT_AUTHORITY_CONFLICT.
#>
[CmdletBinding()]
param(
    [string] $StatusPath = 'docs/current/STATUS.md',
    [string] $PlanPath = 'docs/current/GATEV_PLAN.md',
    [string] $RoadmapPath = 'docs/current/ROADMAP.md'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
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

function Test-ExactTokenSet {
    param([string] $Value, [string[]] $Expected, [string] $Diagnostic)
    $tokens = @($Value -split '\|')
    $matches = $tokens.Count -eq $Expected.Count -and @($tokens | Select-Object -Unique).Count -eq $Expected.Count
    foreach ($token in $Expected) { $matches = $matches -and ($tokens -contains $token) }
    if (-not $matches) { Add-AuthorityError ("{0} value={1}" -f $Diagnostic, $Value) }
    return $matches
}

function Test-GitCommitExists {
    param([string] $Commit)
    # A missing object is expected negative-test input; suppress native stderr so Windows PowerShell can continue.
    if ($Commit -notmatch '^[0-9a-fA-F]{7,40}$') { return $false }
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = 'git'
    $startInfo.Arguments = '-C "{0}" cat-file -t "{1}"' -f $repoRoot, $Commit
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    $null = $process.Start()
    $objectType = $process.StandardOutput.ReadToEnd().Trim()
    $null = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    $exitCode = $process.ExitCode
    $process.Dispose()
    return $exitCode -eq 0 -and $objectType -eq 'commit'
}

function Test-GitAncestor {
    param([string] $Ancestor, [string] $Descendant)
    $null = & git -C $repoRoot merge-base --is-ancestor $Ancestor $Descendant 2>$null
    return $LASTEXITCODE -eq 0
}

function Test-StatusPhrase {
    param([string] $Content, [string] $Subject, [string] $StatusPattern)
    # The exact subject boundary prevents GateV-3 from matching GateV-3A.
    $pattern = '(?im)^\s*-\s*{0}(?![A-Za-z0-9])[^\r\n]*{1}' -f [regex]::Escape($Subject), $StatusPattern
    return $Content -match $pattern
}

function Get-WorkStatusPattern {
    param([string] $Status)
    switch ($Status) {
        'NOT_STARTED' { return 'NOT\s*STARTED' }
        'PLAN' { return 'PLAN' }
        'IMPLEMENTED|PENDING_REVIEW' { return 'IMPLEMENTED\s*/\s*PENDING\s*REVIEW' }
        'REVIEW_ACCEPTED|READY_TO_COMMIT' { return 'REVIEW\s*ACCEPTED\s*/\s*READY\s*TO\s*COMMIT' }
        'COMMITTED|CI_PENDING' { return 'COMMITTED\s*/\s*CI\s*PENDING' }
        'ACCEPTED|CI_GREEN' { return 'ACCEPTED\s*/\s*CI\s*GREEN' }
        'BLOCKED' { return 'BLOCKED' }
        default { return '(?!)' }
    }
}

function Get-NextActionType {
    param([string] $Action)
    if ($Action -match '(?i)(^|-)POST-CI-ACTIVE-AUTHORITY-SYNC$') { return 'POST_CI_SYNC' }
    if ($Action -match '(?i)(^|-)REVIEW$') { return 'REVIEW' }
    if ($Action -match '(?i)(^|-)IMPLEMENTATION$') { return 'IMPLEMENTATION' }
    # Match canonical commit/push tokens with explicit boundaries; do not accept arbitrary word combinations.
    if ($Action -match '(?i)(?:^|-)COMMIT(?:-|_)AND(?:-|_)PUSH(?:$|-)|(?:^|-)USER_COMMIT(?:$|-)|(?:^|-)\u7528\u6237\u63d0\u4ea4(?:$|-)') { return 'COMMIT_AND_PUSH' }
    if ($Action -match '(?i)CI_WAIT_OR_INVESTIGATION|WAIT.*CI|CI.*INVESTIGATION') { return 'CI_WAIT_OR_INVESTIGATION' }
    if ($Action -match '(?i)BLOCKED|UNBLOCK') { return 'BLOCKED' }
    return 'UNKNOWN'
}

function Get-ExpectedNextActionType {
    param([string] $Status)
    switch ($Status) {
        'NOT_STARTED' { return 'IMPLEMENTATION' }
        'PLAN' { return 'IMPLEMENTATION' }
        'IMPLEMENTED|PENDING_REVIEW' { return 'REVIEW' }
        'REVIEW_ACCEPTED|READY_TO_COMMIT' { return 'COMMIT_AND_PUSH' }
        'COMMITTED|CI_PENDING' { return 'CI_WAIT_OR_INVESTIGATION' }
        'ACCEPTED|CI_GREEN' { return 'POST_CI_SYNC' }
        'BLOCKED' { return 'BLOCKED' }
        default { return 'UNKNOWN' }
    }
}

$resolvedStatus = Resolve-RepoPath $StatusPath
if (-not (Test-Path -LiteralPath $resolvedStatus)) {
    Add-AuthorityError "STATUS_NOT_FOUND $StatusPath"
} else {
    $statusContent = Read-Utf8File $resolvedStatus
    $blockMatches = [regex]::Matches($statusContent, '(?s)<!--\s*nq-current-authority:start\s*(.*?)\s*nq-current-authority:end\s*-->')
    if ($blockMatches.Count -ne 1) {
        Add-AuthorityError ("AUTHORITY_BLOCK_COUNT expected=1 actual={0}" -f $blockMatches.Count)
    } else {
        $authority = @{}
        foreach ($line in ($blockMatches[0].Groups[1].Value -split '\r?\n')) {
            $trimmed = $line.Trim()
            if ([string]::IsNullOrWhiteSpace($trimmed)) { continue }
            if ($trimmed -notmatch '^(?<key>[a-z0-9_]+)=(?<value>.+)$') {
                Add-AuthorityError "AUTHORITY_LINE_INVALID $trimmed"
                continue
            }
            $key = $Matches.key
            if ($authority.ContainsKey($key)) {
                Add-AuthorityError "AUTHORITY_KEY_DUPLICATE $key"
                continue
            }
            $authority[$key] = $Matches.value.Trim()
        }

        if (-not $authority.ContainsKey('authority_schema') -or $authority.authority_schema -ne '3') {
            $actualSchema = if ($authority.ContainsKey('authority_schema')) { $authority.authority_schema } else { 'missing' }
            Add-AuthorityError "AUTHORITY_SCHEMA_UNSUPPORTED expected=3 actual=$actualSchema"
        }
        foreach ($retiredKey in @(
            'current_gate', 'current_gate_status', 'current_gate_tag', 'next_gate', 'next_gate_status', 'updated_commit',
            'active_batch', 'active_batch_status', 'active_batch_implementation_commit',
            'active_batch_acceptance_head', 'active_batch_ci_run'
        )) {
            if ($authority.ContainsKey($retiredKey)) { Add-AuthorityError "AUTHORITY_SCHEMA_UNSUPPORTED retired_key=$retiredKey" }
        }

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
        $hasRequiredKeys = @($requiredKeys | Where-Object {
            -not $authority.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($authority[$_])
        }).Count -eq 0

        if ($hasRequiredKeys) {
            $null = Test-ExactTokenSet $authority.last_frozen_gate_status @('FROZEN', 'ACCEPTED', 'TAGGED') 'LAST_FROZEN_GATE_STATUS_COMBINATION_INVALID'
            $null = Test-ExactTokenSet $authority.active_gate_status @('IN_PROGRESS', 'NOT_FROZEN') 'ACTIVE_GATE_STATUS_COMBINATION_INVALID'
            $acceptedStatusValid = Test-ExactTokenSet $authority.accepted_batch_status @('ACCEPTED', 'CI_GREEN') 'ACCEPTED_BATCH_STATUS_INVALID'
            $allowedWorkStatuses = @(
                'NOT_STARTED', 'PLAN', 'IMPLEMENTED|PENDING_REVIEW',
                'REVIEW_ACCEPTED|READY_TO_COMMIT', 'COMMITTED|CI_PENDING',
                'ACCEPTED|CI_GREEN', 'BLOCKED'
            )
            $workStatusValid = $authority.work_batch_status -in $allowedWorkStatuses
            if (-not $workStatusValid) { Add-AuthorityError "WORK_BATCH_STATUS_INVALID value=$($authority.work_batch_status)" }
            if ($authority.accepted_batch -eq $authority.work_batch) {
                Add-AuthorityError "WORK_BATCH_STATUS_INVALID accepted_batch_and_work_batch_must_differ batch=$($authority.work_batch)"
            }

            foreach ($safetyFact in @{
                live = 'DISABLED'; shadow_trading = 'NOT_ENABLED'; ai = 'NOT_STARTED'
                dh_runtime = 'NOT_INTEGRATED'; integration_runtime = 'NOT_STARTED'
                real_provider = 'NOT_IMPLEMENTED'; private_trading = 'NOT_IMPLEMENTED'
            }.GetEnumerator()) {
                if ($authority[$safetyFact.Key] -ne $safetyFact.Value) {
                    Add-AuthorityError "SAFETY_FACT_CONTRADICTION key=$($safetyFact.Key) expected=$($safetyFact.Value) actual=$($authority[$safetyFact.Key])"
                }
            }

            $tag = $authority.last_frozen_gate_tag
            $localTag = (& git -C $repoRoot tag --list $tag).Trim()
            if ($LASTEXITCODE -ne 0 -or $localTag -ne $tag) {
                Add-AuthorityError "LAST_FROZEN_GATE_TAG_MISSING $tag"
            } else {
                $peeled = (& git -C $repoRoot rev-parse "$tag^{}").Trim()
                if ($LASTEXITCODE -ne 0 -or $peeled -ne $authority.last_frozen_gate_commit) {
                    Add-AuthorityError "LAST_FROZEN_GATE_TAG_TARGET_MISMATCH expected=$($authority.last_frozen_gate_commit) actual=$peeled"
                }
            }

            $implementationExists = Test-GitCommitExists $authority.accepted_batch_implementation_commit
            if (-not $implementationExists) {
                Add-AuthorityError "ACCEPTED_BATCH_COMMIT_MISSING commit=$($authority.accepted_batch_implementation_commit)"
            }
            $acceptanceExists = Test-GitCommitExists $authority.accepted_batch_acceptance_head
            if (-not $acceptanceExists) {
                Add-AuthorityError "ACCEPTED_BATCH_COMMIT_MISSING acceptance_head=$($authority.accepted_batch_acceptance_head)"
            }
            if ($authority.accepted_batch_ci_run -notmatch '^\d+$') {
                Add-AuthorityError "ACCEPTED_BATCH_STATUS_INVALID ci_run=$($authority.accepted_batch_ci_run)"
            }
            if ($acceptedStatusValid -and $implementationExists -and $acceptanceExists) {
                if (-not (Test-GitAncestor $authority.accepted_batch_implementation_commit $authority.accepted_batch_acceptance_head)) {
                    Add-AuthorityError "ACCEPTED_BATCH_COMMIT_NOT_ANCESTOR implementation=$($authority.accepted_batch_implementation_commit) acceptance=$($authority.accepted_batch_acceptance_head)"
                }
                $head = (& git -C $repoRoot rev-parse HEAD).Trim()
                if (-not (Test-GitAncestor $authority.accepted_batch_acceptance_head $head)) {
                    Add-AuthorityError "ACCEPTED_BATCH_COMMIT_NOT_ANCESTOR acceptance=$($authority.accepted_batch_acceptance_head) head=$head"
                }
            }

            if ($workStatusValid) {
                $commitMatches = $true
                $ciMatches = $true
                switch ($authority.work_batch_status) {
                    { $_ -in @('NOT_STARTED', 'PLAN') } {
                        $commitMatches = $authority.work_batch_commit -eq 'NONE'
                        $ciMatches = $authority.work_batch_ci_run -eq 'NOT_RUN'
                    }
                    { $_ -in @('IMPLEMENTED|PENDING_REVIEW', 'REVIEW_ACCEPTED|READY_TO_COMMIT') } {
                        $commitMatches = $authority.work_batch_commit -eq 'UNCOMMITTED'
                        $ciMatches = $authority.work_batch_ci_run -eq 'NOT_RUN'
                    }
                    'COMMITTED|CI_PENDING' {
                        $commitMatches = Test-GitCommitExists $authority.work_batch_commit
                        $ciMatches = $authority.work_batch_ci_run -eq 'PENDING' -or $authority.work_batch_ci_run -match '^\d+$'
                    }
                    'ACCEPTED|CI_GREEN' {
                        $commitMatches = Test-GitCommitExists $authority.work_batch_commit
                        $ciMatches = $authority.work_batch_ci_run -match '^\d+$'
                    }
                    'BLOCKED' {
                        $commitMatches = $authority.work_batch_commit -match '^(NONE|UNCOMMITTED|[0-9a-fA-F]{7,40})$'
                        $ciMatches = $authority.work_batch_ci_run -match '^(NOT_RUN|PENDING|\d+)$'
                    }
                }
                if (-not $commitMatches) {
                    Add-AuthorityError "WORK_BATCH_COMMIT_STATE_MISMATCH status=$($authority.work_batch_status) commit=$($authority.work_batch_commit)"
                }
                if (-not $ciMatches) {
                    Add-AuthorityError "WORK_BATCH_CI_STATE_MISMATCH status=$($authority.work_batch_status) ci_run=$($authority.work_batch_ci_run)"
                }
                $actualActionType = Get-NextActionType $authority.next_action
                $expectedActionType = Get-ExpectedNextActionType $authority.work_batch_status
                if ($actualActionType -ne $expectedActionType) {
                    Add-AuthorityError "NEXT_ACTION_TYPE_MISMATCH status=$($authority.work_batch_status) expected=$expectedActionType actual=$actualActionType action=$($authority.next_action)"
                }
            }

            $statusBody = [regex]::Replace(
                $statusContent,
                '(?s)<!--\s*nq-current-authority:start.*?nq-current-authority:end\s*-->',
                ''
            )
            $lastFrozenPattern = 'FROZEN\s*/\s*ACCEPTED\s*/\s*TAGGED'
            if (-not (Test-StatusPhrase $statusBody $authority.last_frozen_gate $lastFrozenPattern)) {
                Add-AuthorityError "LAST_FROZEN_GATE_BODY_CONTRADICTION gate=$($authority.last_frozen_gate)"
            }
            if (-not (Test-StatusPhrase $statusBody $authority.active_gate 'IN\s*PROGRESS\s*/\s*NOT\s*FROZEN')) {
                Add-AuthorityError "ACTIVE_GATE_BODY_CONTRADICTION gate=$($authority.active_gate)"
            }
            if (-not (Test-StatusPhrase $statusBody $authority.accepted_batch 'ACCEPTED\s*/\s*CI\s*GREEN')) {
                Add-AuthorityError "ACCEPTED_BATCH_STATUS_INVALID body_batch=$($authority.accepted_batch)"
            }
            foreach ($fact in @(
                $authority.accepted_batch_implementation_commit,
                $authority.accepted_batch_acceptance_head,
                $authority.accepted_batch_ci_run
            )) {
                if (-not $statusBody.Contains($fact)) {
                    Add-AuthorityError "ACCEPTED_BATCH_STATUS_INVALID missing_body_fact=$fact"
                }
            }

            $workStatusPattern = Get-WorkStatusPattern $authority.work_batch_status
            if (-not (Test-StatusPhrase $statusBody $authority.work_batch $workStatusPattern)) {
                Add-AuthorityError "WORK_BATCH_BODY_CONTRADICTION batch=$($authority.work_batch) expected=$($authority.work_batch_status) file=$StatusPath"
            }
            if (-not $statusBody.Contains($authority.next_action)) {
                Add-AuthorityError "NEXT_ACTION_MISMATCH expected=$($authority.next_action) file=$StatusPath"
            }

            $resolvedPlan = Resolve-RepoPath $PlanPath
            if (-not (Test-Path -LiteralPath $resolvedPlan)) {
                Add-AuthorityError "CURRENT_DOC_MISSING $PlanPath"
            } else {
                $planContent = Read-Utf8File $resolvedPlan
                if (-not (Test-StatusPhrase $planContent $authority.active_gate 'IN\s*PROGRESS\s*/\s*NOT\s*FROZEN') -or
                    -not (Test-StatusPhrase $planContent $authority.accepted_batch 'ACCEPTED\s*/\s*CI\s*GREEN') -or
                    -not (Test-StatusPhrase $planContent $authority.work_batch $workStatusPattern)) {
                    Add-AuthorityError "ACTIVE_PLAN_STATUS_MISMATCH file=$PlanPath"
                }
                if (-not $planContent.Contains($authority.next_action)) {
                    Add-AuthorityError "NEXT_ACTION_MISMATCH expected=$($authority.next_action) file=$PlanPath"
                }
            }

            $resolvedRoadmap = Resolve-RepoPath $RoadmapPath
            if (-not (Test-Path -LiteralPath $resolvedRoadmap)) {
                Add-AuthorityError "CURRENT_DOC_MISSING $RoadmapPath"
            } else {
                $roadmapContent = Read-Utf8File $resolvedRoadmap
                if (-not (Test-StatusPhrase $roadmapContent $authority.active_gate 'IN\s*PROGRESS\s*/\s*NOT\s*FROZEN') -or
                    -not (Test-StatusPhrase $roadmapContent $authority.accepted_batch 'ACCEPTED\s*/\s*CI\s*GREEN') -or
                    -not (Test-StatusPhrase $roadmapContent $authority.work_batch $workStatusPattern)) {
                    Add-AuthorityError "ROADMAP_NEXT_ACTION_MISMATCH status"
                }
                if (-not $roadmapContent.Contains($authority.next_action)) {
                    Add-AuthorityError "ROADMAP_NEXT_ACTION_MISMATCH expected=$($authority.next_action)"
                }
            }

            foreach ($relativePath in @('README.md', 'docs/current/README.md', 'docs/current/FACT_SOURCE_INDEX.md')) {
                $path = Resolve-RepoPath $relativePath
                if (-not (Test-Path -LiteralPath $path)) {
                    Add-AuthorityError "CURRENT_DOC_MISSING $relativePath"
                    continue
                }
                $content = Read-Utf8File $path
                if (-not (Test-StatusPhrase $content $authority.last_frozen_gate $lastFrozenPattern) -or
                    -not (Test-StatusPhrase $content $authority.active_gate 'IN\s*PROGRESS\s*/\s*NOT\s*FROZEN')) {
                    Add-AuthorityError "ACTIVE_GATE_BODY_CONTRADICTION file=$relativePath"
                }
                # Entry docs need not copy work status, but any explicit status must agree with STATUS.
                $entryMentionsWorkStatus = Test-StatusPhrase $content $authority.work_batch '(NOT\s*STARTED|PLAN|IMPLEMENTED|PENDING\s*REVIEW|REVIEW\s*ACCEPTED|READY\s*TO\s*COMMIT|COMMITTED|CI\s*PENDING|ACCEPTED|CI\s*GREEN|BLOCKED)'
                if ($entryMentionsWorkStatus -and -not (Test-StatusPhrase $content $authority.work_batch $workStatusPattern)) {
                    Add-AuthorityError "WORK_BATCH_BODY_CONTRADICTION batch=$($authority.work_batch) file=$relativePath"
                }
            }

            # Keep the template/skill hard-code scan so schema v3 does not weaken dynamic authority.
            $hardCodeTargets = @(
                'AGENTS.md',
                '.agents/skills/nq-dh-workflow-router/SKILL.md',
                '.agents/skills/nq-docs-writer/SKILL.md',
                'docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md',
                'docs/current/NQ_DH_WORKFLOW_ROUTER_SKILL.md',
                'docs/current/NQ_DH_CODEX_TASK_TEMPLATES.md',
                'docs/current/CODEX_PROJECT_INSTRUCTIONS.md'
            )
            foreach ($relativePath in $hardCodeTargets) {
                $content = Read-Utf8File (Resolve-RepoPath $relativePath)
                foreach ($pattern in @(
                    'GateJ completed', 'Next:\s*GateK-PLAN', 'Current baseline:\s*GateJ',
                    'Current stage:\s*GateJ', 'Next allowed:\s*GateK-PLAN'
                )) {
                    if ($content -match $pattern) { Add-AuthorityError "HARD_CODED_STAGE file=$relativePath pattern=$pattern" }
                }
            }

            $indexContent = Read-Utf8File (Resolve-RepoPath 'docs/current/FACT_SOURCE_INDEX.md')
            foreach ($heading in @(
                'NQ Current Authority', 'NQ Capability Authority', 'Evidence Ledger',
                'NQ-DH Integration Boundary', 'DH External Authority', 'Gate Archive',
                'Historical Evidence', 'Allowed Residual'
            )) {
                if ($indexContent -notmatch [regex]::Escape($heading)) {
                    Add-AuthorityError "AUTHORITY_LAYER_MISSING $heading"
                }
            }
            if ($indexContent -notmatch 'TESTING\.md' -or
                $indexContent -notmatch 'WORKLOG\.md' -or
                $indexContent -notmatch 'append-only' -or
                $indexContent -notmatch 'Evidence Ledger') {
                Add-AuthorityError 'EVIDENCE_LEDGER_DEFINITION_MISSING'
            }

            Write-Output ("AUTHORITY schema=3 last_frozen_gate={0} frozen_status={1} active_gate={2} active_status={3} accepted_batch={4} accepted_status={5} acceptance_head={6} accepted_ci_run={7} work_batch={8} work_status={9} work_commit={10} work_ci_run={11} next_action={12}" -f
                $authority.last_frozen_gate,
                $authority.last_frozen_gate_status,
                $authority.active_gate,
                $authority.active_gate_status,
                $authority.accepted_batch,
                $authority.accepted_batch_status,
                $authority.accepted_batch_acceptance_head,
                $authority.accepted_batch_ci_run,
                $authority.work_batch,
                $authority.work_batch_status,
                $authority.work_batch_commit,
                $authority.work_batch_ci_run,
                $authority.next_action)
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
