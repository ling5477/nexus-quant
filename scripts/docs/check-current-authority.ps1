<#
.SYNOPSIS
只读解析 STATUS authority schema v2，并核验 active Gate、batch、Git 与 current entry 语义。
.NOTES
脚本不修改文档或 Git 状态；任何冲突固定输出 BLOCKED / CURRENT_AUTHORITY_CONFLICT。
#>
[CmdletBinding()]
param(
    [string] $StatusPath = 'docs/current/STATUS.md'
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
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return (Join-Path $repoRoot $Path)
}

function Read-Utf8File {
    param([string] $Path)
    return [System.IO.File]::ReadAllText($Path, (New-Object System.Text.UTF8Encoding($false)))
}

function Test-AllowedTokens {
    param(
        [string] $Field,
        [string] $Value,
        [string[]] $Allowed
    )
    $tokens = @($Value -split '\|')
    foreach ($token in $tokens) {
        if ($token -notin $Allowed) {
            Add-AuthorityError ("{0}_TOKEN_INVALID {1}" -f $Field.ToUpperInvariant(), $token)
        }
    }
    if (@($tokens | Select-Object -Unique).Count -ne $tokens.Count) {
        Add-AuthorityError ("{0}_TOKEN_DUPLICATE value={1}" -f $Field.ToUpperInvariant(), $Value)
    }
}

function Test-GitCommitExists {
    param([string] $Commit)
    # 不存在的对象是治理校验的预期负向输入，不能让 native stderr 在 Windows PowerShell 中提前终止脚本。
    if ($Commit -notmatch '^[0-9a-fA-F]{7,40}$') {
        return $false
    }
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

function Test-StatusPhrase {
    param(
        [string] $Content,
        [string] $Subject,
        [string] $StatusPattern
    )
    $pattern = '(?im)^\s*-\s*{0}[^\r\n]*{1}' -f [regex]::Escape($Subject), $StatusPattern
    return $Content -match $pattern
}

$resolvedStatus = Resolve-RepoPath $StatusPath
if (-not (Test-Path -LiteralPath $resolvedStatus)) {
    Add-AuthorityError "STATUS_NOT_FOUND $StatusPath"
} else {
    $statusContent = Read-Utf8File $resolvedStatus
    $blockMatches = [regex]::Matches(
        $statusContent,
        '(?s)<!--\s*nq-current-authority:start\s*(.*?)\s*nq-current-authority:end\s*-->'
    )
    if ($blockMatches.Count -ne 1) {
        Add-AuthorityError ("AUTHORITY_BLOCK_COUNT expected=1 actual={0}" -f $blockMatches.Count)
    } else {
        $authority = @{}
        foreach ($line in ($blockMatches[0].Groups[1].Value -split '\r?\n')) {
            $trimmed = $line.Trim()
            if ([string]::IsNullOrWhiteSpace($trimmed)) {
                continue
            }
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

        if (-not $authority.ContainsKey('authority_schema') -or $authority.authority_schema -ne '2') {
            $actualSchema = if ($authority.ContainsKey('authority_schema')) { $authority.authority_schema } else { 'missing' }
            Add-AuthorityError "AUTHORITY_SCHEMA_UNSUPPORTED expected=2 actual=$actualSchema"
        }

        $legacyKeys = @('current_gate', 'current_gate_status', 'current_gate_tag', 'next_gate', 'next_gate_status', 'updated_commit')
        foreach ($legacyKey in $legacyKeys) {
            if ($authority.ContainsKey($legacyKey)) {
                Add-AuthorityError "AUTHORITY_SCHEMA_UNSUPPORTED legacy_key=$legacyKey"
            }
        }

        $requiredKeys = @(
            'authority_schema',
            'last_frozen_gate', 'last_frozen_gate_status', 'last_frozen_gate_tag', 'last_frozen_gate_commit',
            'active_gate', 'active_gate_status',
            'active_batch', 'active_batch_status', 'active_batch_implementation_commit',
            'active_batch_acceptance_head', 'active_batch_ci_run', 'next_action',
            'live', 'shadow_trading', 'ai', 'dh_runtime', 'integration_runtime',
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
            $lastFrozenTokens = @($authority.last_frozen_gate_status -split '\|')
            $activeGateTokens = @($authority.active_gate_status -split '\|')
            $activeBatchTokens = @($authority.active_batch_status -split '\|')
            Test-AllowedTokens 'last_frozen_gate_status' $authority.last_frozen_gate_status @('FROZEN', 'ACCEPTED', 'TAGGED')
            Test-AllowedTokens 'active_gate_status' $authority.active_gate_status @('PLAN', 'IN_PROGRESS', 'NOT_FROZEN')
            Test-AllowedTokens 'active_batch_status' $authority.active_batch_status @(
                'NOT_STARTED', 'PLAN', 'IMPLEMENTED', 'PENDING_REVIEW', 'ACCEPTED', 'CI_GREEN'
            )

            foreach ($requiredFrozenToken in @('FROZEN', 'ACCEPTED', 'TAGGED')) {
                if ($lastFrozenTokens -notcontains $requiredFrozenToken) {
                    Add-AuthorityError "LAST_FROZEN_GATE_STATUS_COMBINATION_INVALID missing=$requiredFrozenToken"
                }
            }
            if (($activeGateTokens -contains 'IN_PROGRESS') -xor ($activeGateTokens -contains 'NOT_FROZEN')) {
                Add-AuthorityError "ACTIVE_GATE_STATUS_COMBINATION_INVALID value=$($authority.active_gate_status)"
            }
            if (($activeBatchTokens -contains 'CI_GREEN') -and ($activeBatchTokens -notcontains 'ACCEPTED')) {
                Add-AuthorityError "ACTIVE_BATCH_STATUS_COMBINATION_INVALID value=$($authority.active_batch_status)"
            }
            if ($authority.active_batch_ci_run -notmatch '^\d+$') {
                Add-AuthorityError "ACTIVE_BATCH_CI_FACT_STALE invalid_run=$($authority.active_batch_ci_run)"
            }

            foreach ($safetyFact in @{
                live = 'DISABLED'
                shadow_trading = 'NOT_ENABLED'
                ai = 'NOT_STARTED'
                dh_runtime = 'NOT_INTEGRATED'
                integration_runtime = 'NOT_STARTED'
                real_provider = 'NOT_IMPLEMENTED'
                private_trading = 'NOT_IMPLEMENTED'
            }.GetEnumerator()) {
                if ($authority[$safetyFact.Key] -ne $safetyFact.Value) {
                    Add-AuthorityError "SAFETY_FACT_CONTRADICTION key=$($safetyFact.Key) expected=$($safetyFact.Value) actual=$($authority[$safetyFact.Key])"
                }
            }

            # Git facts are deliberately anchored to already-existing commits. The authority-sync commit never predicts itself.
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

            $implementationExists = Test-GitCommitExists $authority.active_batch_implementation_commit
            if (-not $implementationExists) {
                Add-AuthorityError "ACTIVE_BATCH_COMMIT_MISSING commit=$($authority.active_batch_implementation_commit)"
            }
            $acceptanceExists = Test-GitCommitExists $authority.active_batch_acceptance_head
            if (-not $acceptanceExists) {
                Add-AuthorityError "ACTIVE_BATCH_CI_FACT_STALE acceptance_head_missing=$($authority.active_batch_acceptance_head)"
            }
            if ($implementationExists -and $acceptanceExists) {
                $null = & git -C $repoRoot merge-base --is-ancestor $authority.active_batch_implementation_commit $authority.active_batch_acceptance_head
                if ($LASTEXITCODE -ne 0) {
                    Add-AuthorityError "ACTIVE_BATCH_COMMIT_NOT_ANCESTOR implementation=$($authority.active_batch_implementation_commit) acceptance=$($authority.active_batch_acceptance_head)"
                }
                $head = (& git -C $repoRoot rev-parse HEAD).Trim()
                $null = & git -C $repoRoot merge-base --is-ancestor $authority.active_batch_acceptance_head $head
                if ($LASTEXITCODE -ne 0) {
                    Add-AuthorityError "ACTIVE_BATCH_CI_FACT_STALE acceptance=$($authority.active_batch_acceptance_head) head=$head"
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

            $bodySaysActive = Test-StatusPhrase $statusBody $authority.active_gate 'IN\s*PROGRESS\s*/\s*NOT\s*FROZEN'
            $blockSaysActive = ($activeGateTokens -contains 'IN_PROGRESS') -and ($activeGateTokens -contains 'NOT_FROZEN')
            if ($bodySaysActive -ne $blockSaysActive) {
                Add-AuthorityError "ACTIVE_GATE_BODY_CONTRADICTION gate=$($authority.active_gate) block=$($authority.active_gate_status)"
            }

            $bodySaysAccepted = Test-StatusPhrase $statusBody $authority.active_batch 'ACCEPTED\s*/\s*CI\s*GREEN'
            $blockSaysAccepted = ($activeBatchTokens -contains 'ACCEPTED') -and ($activeBatchTokens -contains 'CI_GREEN')
            $activeBatchPattern = [regex]::Escape($authority.active_batch)
            $staleBatchPattern = '(?i)(尚未形成[^\r\n]*(commit|CI)|pending\s+(CI|review)|等待用户提交\s*' +
                $activeBatchPattern + '|先提交\s*' + $activeBatchPattern + '|等待[^\r\n]*' +
                $activeBatchPattern + '[^\r\n]*CI)'
            if ($bodySaysAccepted -ne $blockSaysAccepted -or ($blockSaysAccepted -and $statusBody -match $staleBatchPattern)) {
                Add-AuthorityError "ACTIVE_BATCH_BODY_CONTRADICTION batch=$($authority.active_batch) block=$($authority.active_batch_status)"
            }
            if ($blockSaysAccepted) {
                foreach ($fact in @(
                    $authority.active_batch_implementation_commit,
                    $authority.active_batch_acceptance_head,
                    $authority.active_batch_ci_run
                )) {
                    if (-not $statusBody.Contains($fact)) {
                        Add-AuthorityError "ACTIVE_BATCH_CI_FACT_STALE missing_body_fact=$fact"
                    }
                }
            }

            if (-not $statusBody.Contains($authority.next_action)) {
                Add-AuthorityError "NEXT_ACTION_MISMATCH expected=$($authority.next_action) file=docs/current/STATUS.md"
            }
            if ($authority.next_action -match '(?i)NQ-(?<batch>GATE[A-Z]+-\d+)-') {
                if (-not (Test-StatusPhrase $statusBody $Matches.batch 'NOT\s*STARTED')) {
                    Add-AuthorityError "NEXT_ACTION_MISMATCH next_batch=$($Matches.batch) status=NOT_STARTED"
                }
            }

            $planPath = Resolve-RepoPath 'docs/current/GATEV_PLAN.md'
            if (-not (Test-Path -LiteralPath $planPath)) {
                Add-AuthorityError 'CURRENT_DOC_MISSING docs/current/GATEV_PLAN.md'
            } else {
                $planContent = Read-Utf8File $planPath
                $planMatches = (Test-StatusPhrase $planContent $authority.active_gate 'IN\s*PROGRESS\s*/\s*NOT\s*FROZEN') -and
                    (Test-StatusPhrase $planContent $authority.active_batch 'ACCEPTED\s*/\s*CI\s*GREEN') -and
                    $planContent.Contains($authority.next_action) -and
                    ($planContent -notmatch $staleBatchPattern)
                if ($authority.next_action -match '(?i)NQ-(?<batch>GATE[A-Z]+-\d+)-') {
                    $planMatches = $planMatches -and (Test-StatusPhrase $planContent $Matches.batch 'NOT\s*STARTED')
                }
                if (-not $planMatches) {
                    Add-AuthorityError 'ACTIVE_PLAN_STATUS_MISMATCH file=docs/current/GATEV_PLAN.md'
                }
            }

            $roadmapPath = Resolve-RepoPath 'docs/current/ROADMAP.md'
            if (-not (Test-Path -LiteralPath $roadmapPath)) {
                Add-AuthorityError 'CURRENT_DOC_MISSING docs/current/ROADMAP.md'
            } else {
                $roadmapContent = Read-Utf8File $roadmapPath
                if (-not $roadmapContent.Contains($authority.next_action) -or $roadmapContent -match $staleBatchPattern) {
                    Add-AuthorityError "ROADMAP_NEXT_ACTION_MISMATCH expected=$($authority.next_action)"
                }
                if (-not (Test-StatusPhrase $roadmapContent $authority.active_gate 'IN\s*PROGRESS\s*/\s*NOT\s*FROZEN') -or
                    -not (Test-StatusPhrase $roadmapContent $authority.active_batch 'ACCEPTED\s*/\s*CI\s*GREEN')) {
                    Add-AuthorityError 'ROADMAP_NEXT_ACTION_MISMATCH active_status'
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
            }

            # 保留原有模板/skill 硬编码扫描，避免 schema 升级反向放宽动态 authority 治理。
            $hardCodeTargets = @(
                'AGENTS.md',
                '.agents/skills/nq-dh-workflow-router/SKILL.md',
                '.agents/skills/nq-docs-writer/SKILL.md',
                'docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md',
                'docs/current/NQ_DH_WORKFLOW_ROUTER_SKILL.md',
                'docs/current/NQ_DH_CODEX_TASK_TEMPLATES.md',
                'docs/current/CODEX_PROJECT_INSTRUCTIONS.md'
            )
            $legacyPatterns = @(
                'GateJ completed',
                'Next:\s*GateK-PLAN',
                'Current baseline:\s*GateJ',
                'Current stage:\s*GateJ',
                'Next allowed:\s*GateK-PLAN'
            )
            foreach ($relativePath in $hardCodeTargets) {
                $content = Read-Utf8File (Resolve-RepoPath $relativePath)
                foreach ($pattern in $legacyPatterns) {
                    if ($content -match $pattern) {
                        Add-AuthorityError "HARD_CODED_STAGE file=$relativePath pattern=$pattern"
                    }
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

            Write-Output ("AUTHORITY schema=2 last_frozen_gate={0} frozen_status={1} active_gate={2} active_status={3} active_batch={4} batch_status={5} acceptance_head={6} ci_run={7} next_action={8}" -f
                $authority.last_frozen_gate,
                $authority.last_frozen_gate_status,
                $authority.active_gate,
                $authority.active_gate_status,
                $authority.active_batch,
                $authority.active_batch_status,
                $authority.active_batch_acceptance_head,
                $authority.active_batch_ci_run,
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
