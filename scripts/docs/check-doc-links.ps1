<#
.SYNOPSIS
只读校验 Markdown 相对文件链接，忽略 fenced code 与 external URL。
.NOTES
Historical source 和 append-only ledger 断链降级为 warning；current/core archive 断链为 error。
#>
[CmdletBinding()]
param(
    [string[]] $Roots = @(
        'AGENTS.md',
        'CLAUDE.md',
        '.agents',
        'docs/README.md',
        'docs/DOC_RULES.md',
        'docs/audit',
        'docs/current/README.md',
        'docs/current/STATUS.md',
        'docs/current/ROADMAP.md',
        'docs/current/FACT_SOURCE_INDEX.md',
        'docs/current/GOVERNANCE_WORKFLOW.md',
        'docs/current/ARCHITECTURE.md',
        'docs/current/MODULES.md',
        'docs/current/API.md',
        'docs/current/DB_SCHEMA.md',
        'docs/current/RUNBOOK.md',
        'docs/current/FRONTEND_DESIGN_SYSTEM.md',
        'docs/current/TESTING.md',
        'docs/current/WORKLOG.md'
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$checked = 0
$warnings = 0
$errors = 0

function Write-LinkFinding {
    param(
        [ValidateSet('WARNING', 'ERROR')]
        [string] $Level,
        [string] $File,
        [int] $Line,
        [string] $Link
    )

    if ($Level -eq 'ERROR') {
        $script:errors++
    } else {
        $script:warnings++
    }
    Write-Output ("{0} {1}:{2} -> {3}" -f $Level, $File, $Line, $Link)
}

foreach ($rootInput in $Roots) {
    $rootPath = if ([System.IO.Path]::IsPathRooted($rootInput)) {
        $rootInput
    } else {
        Join-Path $repoRoot $rootInput
    }

    if (-not (Test-Path -LiteralPath $rootPath)) {
        Write-LinkFinding -Level ERROR -File $rootInput -Line 0 -Link 'ROOT_NOT_FOUND'
        continue
    }

    $item = Get-Item -LiteralPath $rootPath -Force
    $files = if ($item.PSIsContainer) {
        @(Get-ChildItem -LiteralPath $item.FullName -Recurse -File -Filter '*.md' -Force)
    } else {
        @($item)
    }

    foreach ($file in $files) {
        $relativeFile = $file.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
        $inFence = $false
        $lineNumber = 0

        foreach ($line in Get-Content -LiteralPath $file.FullName -Encoding UTF8) {
            $lineNumber++
            if ($line -match '^\s*```') {
                $inFence = -not $inFence
                continue
            }
            if ($inFence) {
                continue
            }

            foreach ($match in [regex]::Matches($line, '\[[^\]]*\]\(([^)]+)\)')) {
                $rawLink = $match.Groups[1].Value.Trim().Trim('<', '>')
                if ([string]::IsNullOrWhiteSpace($rawLink) -or
                    $rawLink -match '^(https?://|mailto:|javascript:|#)') {
                    continue
                }

                $linkWithoutAnchor = $rawLink.Split('#')[0]
                if ([string]::IsNullOrWhiteSpace($linkWithoutAnchor)) {
                    continue
                }

                $checked++
                if ($linkWithoutAnchor -match '[*?]') {
                    Write-LinkFinding -Level WARNING -File $relativeFile -Line $lineNumber -Link $rawLink
                    continue
                }

                $decoded = [uri]::UnescapeDataString($linkWithoutAnchor)
                $target = if ($decoded.StartsWith('/')) {
                    Join-Path $repoRoot $decoded.TrimStart('/')
                } else {
                    Join-Path $file.DirectoryName $decoded
                }

                if (Test-Path -LiteralPath $target) {
                    continue
                }

                $isHistoricalSource = $relativeFile -match '(^|/)docs/gates/[^/]+/source/' -or
                    $relativeFile -match '(^|/)docs/archive/'
                $isEvidenceLedger = $relativeFile -in @('docs/current/TESTING.md', 'docs/current/WORKLOG.md')
                $level = if ($isHistoricalSource -or $isEvidenceLedger) { 'WARNING' } else { 'ERROR' }
                Write-LinkFinding -Level $level -File $relativeFile -Line $lineNumber -Link $rawLink
            }
        }
    }
}

Write-Output ("LINK_CHECK checked={0} warnings={1} errors={2}" -f $checked, $warnings, $errors)
if ($errors -gt 0) {
    Write-Output 'BLOCKED / ARCHIVE_LINK_BROKEN'
    exit 1
}

Write-Output 'PASS / DOC_LINKS_VALID'
exit 0
