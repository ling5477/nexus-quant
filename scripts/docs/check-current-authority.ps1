<#
.SYNOPSIS
只读解析 STATUS machine block，并验证 current entry docs、router 与 authority 分层没有冲突。
.NOTES
脚本不修改文档或 Git 状态；失败固定输出 BLOCKED / CURRENT_AUTHORITY_CONFLICT。
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

        $requiredKeys = @(
            'current_gate', 'current_gate_status', 'current_gate_tag',
            'next_gate', 'next_gate_status', 'live', 'shadow_trading',
            'ai', 'dh_runtime', 'integration_runtime', 'real_provider',
            'private_trading', 'updated_commit'
        )
        foreach ($key in $requiredKeys) {
            if (-not $authority.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($authority[$key])) {
                Add-AuthorityError "AUTHORITY_KEY_MISSING $key"
            }
        }

        if ($errors.Count -eq 0) {
            $currentGate = $authority.current_gate
            $nextGate = $authority.next_gate
            $tag = $authority.current_gate_tag
            $statusTokens = @($authority.current_gate_status -split '\|')

            foreach ($token in $statusTokens) {
                if ($token -notin @('PLAN', 'NOT_STARTED', 'IMPLEMENTED', 'COMPLETED', 'FREEZE_READY', 'FROZEN', 'ACCEPTED', 'TAGGED')) {
                    Add-AuthorityError "CURRENT_GATE_STATUS_TOKEN_INVALID $token"
                }
            }

            if ($authority.next_gate_status -ne 'NOT_STARTED') {
                Add-AuthorityError "NEXT_GATE_STATUS_UNSAFE $($authority.next_gate_status)"
            }

            $statusBody = [regex]::Replace(
                $statusContent,
                '(?s)<!--\s*nq-current-authority:start.*?nq-current-authority:end\s*-->',
                ''
            )
            if (-not $statusBody.Contains($currentGate) -or -not $statusBody.Contains($nextGate)) {
                Add-AuthorityError 'STATUS_BODY_DOES_NOT_EXPLAIN_AUTHORITY_BLOCK'
            }
            if ($statusBody -match ("(?i){0}.*(TAG PENDING|NOT TAGGED|PLAN\s*/\s*NOT STARTED)" -f [regex]::Escape($currentGate))) {
                Add-AuthorityError 'STATUS_BODY_CURRENT_GATE_CONTRADICTION'
            }
            if ($statusTokens -contains 'TAGGED') {
                $localTag = (& git -C $repoRoot tag --list $tag).Trim()
                if ($LASTEXITCODE -ne 0 -or $localTag -ne $tag) {
                    Add-AuthorityError "CURRENT_TAG_MISSING $tag"
                } else {
                    $peeled = (& git -C $repoRoot rev-parse "$tag^{}").Trim()
                    if ($LASTEXITCODE -ne 0 -or $peeled -ne $authority.updated_commit) {
                        Add-AuthorityError "CURRENT_TAG_TARGET_MISMATCH expected=$($authority.updated_commit) actual=$peeled"
                    }
                }
            }

            $currentDocs = @(
                'README.md',
                'docs/current/README.md',
                'docs/current/ROADMAP.md',
                'docs/current/FACT_SOURCE_INDEX.md'
            )
            foreach ($relativePath in $currentDocs) {
                $path = Resolve-RepoPath $relativePath
                if (-not (Test-Path -LiteralPath $path)) {
                    Add-AuthorityError "CURRENT_DOC_MISSING $relativePath"
                    continue
                }
                $content = Read-Utf8File $path
                Write-Verbose ("AUTHORITY_DOC file={0} current={1} next={2} containsCurrent={3} containsNext={4}" -f
                    $relativePath, $currentGate, $nextGate, $content.Contains($currentGate), $content.Contains($nextGate))
                if (-not $content.Contains($currentGate)) {
                    Add-AuthorityError "CURRENT_GATE_NOT_REFERENCED file=$relativePath gate=$currentGate"
                }
                if (-not $content.Contains($nextGate)) {
                    Add-AuthorityError "NEXT_GATE_NOT_REFERENCED file=$relativePath gate=$nextGate"
                }
                if ($content -match ("(?i){0}.*(TAG PENDING|NOT TAGGED|PLAN\s*/\s*NOT STARTED)" -f [regex]::Escape($currentGate))) {
                    Add-AuthorityError "CURRENT_GATE_CONTRADICTION file=$relativePath"
                }
                foreach ($contentLine in ($content -split '\r?\n')) {
                    if ($contentLine -match [regex]::Escape($nextGate) -and
                        $contentLine -match '(?i)(IMPLEMENTED|STARTED|COMPLETED)' -and
                        $contentLine -notmatch '(?i)NOT[_ /-]*(IMPLEMENTED|STARTED|COMPLETED)') {
                        Add-AuthorityError "NEXT_GATE_CONTRADICTION file=$relativePath"
                        break
                    }
                }
            }

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

            Write-Output ("AUTHORITY current_gate={0} status={1} tag={2} next_gate={3} next_status={4} updated_commit={5}" -f
                $currentGate,
                $authority.current_gate_status,
                $tag,
                $nextGate,
                $authority.next_gate_status,
                $authority.updated_commit)
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
