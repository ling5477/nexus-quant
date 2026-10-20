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
$retiredSourceCache = @{}

function Test-RetiredMigrationSource {
    param([string] $Target, [string] $SourceDocument)
    # 冻结证据继续引用当时源码；只按精确 manifest + Git blob 解析已退休迁移，不豁免其他断链。
    $frozenSource = $SourceDocument.StartsWith('docs/audit/') -or
        $SourceDocument.StartsWith('docs/gates/') -or $SourceDocument.StartsWith('docs/archive/')
    if (-not $frozenSource -and $SourceDocument -notin @('docs/current/WORKLOG.md', 'docs/current/TESTING.md')) {
        return $false
    }
    $absolute = [IO.Path]::GetFullPath($Target)
    if (-not $absolute.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        return $false
    }
    $relative = $absolute.Substring($repoRoot.Length + 1).Replace('\', '/')
    $prefix = 'backend/nq-infra/src/main/resources/db/migration/'
    if (-not $relative.StartsWith($prefix, [StringComparison]::Ordinal)) { return $false }
    if ($retiredSourceCache.ContainsKey($relative)) { return $retiredSourceCache[$relative] }
    $manifestPath = Join-Path $repoRoot 'docs/archive/db-migrations/retired-development-migrations.json'
    if (-not (Test-Path -LiteralPath $manifestPath)) { return $false }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($manifest.disposition -cne 'RETIRED_FROM_ACTIVE_FLYWAY' -or $manifest.pathPrefix -cne $prefix -or
            $manifest.historicalHead -cnotmatch '^[a-f0-9]{40}$') { throw 'Invalid retired migration identity' }
    $filename = $relative.Substring($prefix.Length)
    $property = $manifest.migrations.PSObject.Properties[$filename]
    if ($null -eq $property) { return $false }
    $expected = [string]$property.Value.gitBlob
    if ($expected -cnotmatch '^[a-f0-9]{40}$') { throw 'Invalid retired migration blob identity' }
    $resolved = @(& git -C $repoRoot rev-parse --verify ($manifest.historicalHead + ':' + $relative) 2>$null)
    $valid = $LASTEXITCODE -eq 0 -and $resolved.Count -eq 1 -and $resolved[0] -ceq $expected
    if ($valid) {
        & git -C $repoRoot cat-file -e ($expected + '^{blob}') 2>$null
        $valid = $LASTEXITCODE -eq 0
    }
    $retiredSourceCache[$relative] = $valid
    return $valid
}

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
                if (Test-RetiredMigrationSource -Target $target -SourceDocument $relativeFile) {
                    Write-Output ("HISTORICAL_SOURCE_RESOLVED {0}:{1} -> {2}" -f $relativeFile, $lineNumber, $rawLink)
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
