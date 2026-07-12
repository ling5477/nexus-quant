<#
.SYNOPSIS
只读验证 Gate archive manifest、tag、peeled commit、remote tag 与 tagged-commit CI。
.NOTES
脚本不写文件、不修改 Git index、不创建或移动 tag；失败仅输出 BLOCKED 状态并返回非零退出码。
Default mode preserves strict post-tag validation. -PreTag validates strict archive content only while the target tag is absent.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^gate-[a-z0-9-]+$')]
    [string] $Gate,

    [string] $ExpectedTag,

    [switch] $PreTag,

    [switch] $RequireRemoteTag,

    [switch] $RequireCi,

    [string] $ManifestPath = 'scripts/docs/gate-archive-manifest.json',

    [string[]] $AllowedPaths
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$errors = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]
$blockingCode = $null

function Resolve-RepoPath {
    param([string] $Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return (Join-Path $repoRoot $Path)
}

function Add-ArchiveError {
    param([string] $Code, [string] $Message)
    if (-not $script:blockingCode) {
        $script:blockingCode = $Code
    }
    $script:errors.Add($Message)
    Write-Output ("ERROR {0}" -f $Message)
}

function Add-ArchiveWarning {
    param([string] $Message)
    $script:warnings.Add($Message)
    Write-Output ("WARNING {0}" -f $Message)
}

function Invoke-GitRead {
    param([string[]] $Arguments)
    $output = & git -C $repoRoot @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed: $($output -join [Environment]::NewLine)"
    }
    return (($output | ForEach-Object { $_.ToString() }) -join "`n").Trim()
}

function Read-Utf8File {
    param([string] $Path)
    return [System.IO.File]::ReadAllText($Path, (New-Object System.Text.UTF8Encoding($false)))
}

function Get-GateOrdinal {
    param([string] $GateName)
    $suffix = $GateName.Substring(5).ToLowerInvariant()
    if ($suffix -match '^[a-z]$') {
        return ([int][char]$suffix - [int][char]'a')
    }
    return [int]::MaxValue
}

function Get-GateLabel {
    param([string] $GateName)

    # Convert manifest identifiers such as gate-v to authority labels such as GateV without inferring stage state.
    $segments = @($GateName.Substring(5) -split '-')
    $suffix = ($segments | ForEach-Object {
        if ($_.Length -eq 1) { $_.ToUpperInvariant() }
        else { $_.Substring(0, 1).ToUpperInvariant() + $_.Substring(1) }
    }) -join ''
    return "Gate$suffix"
}

function Read-AuthorityBlock {
    param([string] $StatusPath)

    # Bind the archive to the current Gate/freeze batch; check-current-authority.ps1 owns the complete authority contract.
    if (-not (Test-Path -LiteralPath $StatusPath)) { return $null }
    $content = Read-Utf8File $StatusPath
    $blockMatches = [regex]::Matches($content, '(?s)<!--\s*nq-current-authority:start\s*(.*?)\s*nq-current-authority:end\s*-->')
    if ($blockMatches.Count -ne 1) { return $null }

    $authority = @{}
    foreach ($line in ($blockMatches[0].Groups[1].Value -split '\r?\n')) {
        if ($line -match '^([a-z_]+)=(.*)$') {
            $authority[$Matches[1]] = $Matches[2].Trim()
        }
    }
    return $authority
}

if ($PreTag -and ($RequireRemoteTag -or $RequireCi)) {
    Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' 'PRETAG_MODE_INCOMPATIBLE_WITH_REMOTE_TAG_OR_CI'
}

$resolvedManifest = Resolve-RepoPath $ManifestPath
if (-not (Test-Path -LiteralPath $resolvedManifest)) {
    Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "MANIFEST_NOT_FOUND $ManifestPath"
} else {
    $manifest = Read-Utf8File $resolvedManifest | ConvertFrom-Json
    if (-not $manifest.schemaVersion) {
        Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' 'MANIFEST_SCHEMA_VERSION_MISSING'
    }
    if ($PreTag) {
        # Pre-tag is a freeze hard gate, so every schema field used by this checker must be present and valid.
        foreach ($property in @('mandatoryRoles', 'acceptedAliases', 'strictGateOverrides', 'roleBodyPolicy')) {
            $manifestProperty = $manifest.PSObject.Properties |
                Where-Object { $_.Name -eq $property } |
                Select-Object -First 1
            if ($null -eq $manifestProperty) {
                Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "MANIFEST_SCHEMA_FIELD_MISSING field=$property"
            }
        }
        $roleBodyPolicyProperty = $manifest.PSObject.Properties |
            Where-Object { $_.Name -eq 'roleBodyPolicy' } |
            Select-Object -First 1
        if ($roleBodyPolicyProperty -and
            ($manifest.roleBodyPolicy.minimumNonEmptyLines -lt 1 -or
                $manifest.roleBodyPolicy.minimumIndependentCharacters -lt 1)) {
            Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' 'MANIFEST_ROLE_BODY_POLICY_INVALID'
        }
    }

    $gateRoot = Join-Path $repoRoot "docs/gates/$Gate"
    if (-not (Test-Path -LiteralPath $gateRoot)) {
        Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "GATE_ARCHIVE_NOT_FOUND $Gate"
    } else {
        $strictProperty = $manifest.strictGateOverrides.PSObject.Properties[$Gate]
        $strictConfig = if ($strictProperty) { $strictProperty.Value } else { $null }
        $isLegacy = (Get-GateOrdinal $Gate) -le (Get-GateOrdinal ([string]$manifest.legacyThroughGate))
        $isStrict = ($null -ne $strictConfig) -or (-not $isLegacy)
        if (-not $isLegacy -and $null -eq $strictConfig) {
            Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "STRICT_GATE_OVERRIDE_MISSING gate=$Gate"
        }
        $requiredRoles = New-Object System.Collections.Generic.List[string]
        foreach ($role in $manifest.mandatoryRoles) {
            $requiredRoles.Add([string]$role)
        }
        if ($strictConfig) {
            foreach ($role in $strictConfig.conditionalRoles) {
                $requiredRoles.Add([string]$role)
            }
        }
        if ($PreTag -and $null -eq $strictConfig) {
            Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "PRETAG_STRICT_OVERRIDE_REQUIRED gate=$Gate"
        }
        if ($PreTag -and @($requiredRoles | Select-Object -Unique).Count -ne $requiredRoles.Count) {
            Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "DUPLICATE_REQUIRED_ROLE gate=$Gate"
        }

        Write-Output ("POLICY gate={0} legacyThrough={1} legacy={2} strict={3}" -f
            $Gate, $manifest.legacyThroughGate, $isLegacy, $isStrict)

        $allArchiveFiles = @(Get-ChildItem -LiteralPath $gateRoot -Recurse -File)
        $archiveFiles = @($allArchiveFiles | Where-Object { $_.Extension -eq '.md' })
        $relativeFiles = @{}
        foreach ($file in $allArchiveFiles) {
            $relativeFiles[$file.FullName] = $file.FullName.Substring($gateRoot.Length + 1).Replace('\', '/')
        }

        foreach ($role in $requiredRoles) {
            $aliasProperty = $manifest.acceptedAliases.PSObject.Properties[$role]
            if (-not $aliasProperty) {
                Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "ROLE_ALIAS_MISSING role=$role"
                continue
            }
            $patterns = @($aliasProperty.Value)
            $roleFiles = @($archiveFiles | Where-Object {
                $relative = $relativeFiles[$_.FullName]
                $matched = $false
                foreach ($pattern in $patterns) {
                    if ([regex]::IsMatch($relative, [string]$pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
                        $matched = $true
                        break
                    }
                }
                $matched
            })

            if ($roleFiles.Count -eq 0) {
                if ($isStrict) {
                    Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "ROLE_MISSING role=$role gate=$Gate"
                } else {
                    Add-ArchiveWarning "LEGACY_ROLE_MISSING role=$role gate=$Gate"
                }
                continue
            }
            if ($PreTag -and $roleFiles.Count -gt 1) {
                Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' ("DUPLICATE_ROLE role={0} files={1}" -f
                    $role,
                    (($roleFiles | ForEach-Object { $relativeFiles[$_.FullName] }) -join ','))
            }

            $hasIndependentBody = $false
            foreach ($file in $roleFiles) {
                $content = Read-Utf8File $file.FullName
                $nonEmptyLines = @(($content -split '\r?\n') | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
                $independentContent = (($content -split '\r?\n') |
                    Where-Object { $_ -notmatch 'docs/current|\.\./\.\./current' }) -join "`n"
                $independentCharacters = ($independentContent -replace '\s', '').Length
                if ($nonEmptyLines -ge [int]$manifest.roleBodyPolicy.minimumNonEmptyLines -and
                    $independentCharacters -ge [int]$manifest.roleBodyPolicy.minimumIndependentCharacters) {
                    $hasIndependentBody = $true
                    break
                }
            }
            if (-not $hasIndependentBody) {
                if ($isStrict) {
                    Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "THIN_ROLE role=$role gate=$Gate"
                } else {
                    Add-ArchiveWarning "LEGACY_THIN_ROLE role=$role gate=$Gate"
                }
            }

            if ($AllowedPaths -and $AllowedPaths.Count -gt 0) {
                $allowlistCoversRole = $false
                foreach ($allowedPath in $AllowedPaths) {
                    $normalizedAllowed = $allowedPath.Replace('\', '/')
                    foreach ($pattern in $patterns) {
                        if ([regex]::IsMatch($normalizedAllowed, [string]$pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
                            $allowlistCoversRole = $true
                            break
                        }
                    }
                    if ($allowlistCoversRole) { break }
                }
                if (-not $allowlistCoversRole) {
                    Add-ArchiveError 'ARCHIVE_ALLOWLIST_INCOMPLETE' "ALLOWLIST_ROLE_MISSING role=$role"
                }
            }

            Write-Output ("ROLE role={0} files={1} independent={2}" -f
                $role,
                (($roleFiles | ForEach-Object { $relativeFiles[$_.FullName] }) -join ','),
                $hasIndependentBody)
        }

        if ($PreTag) {
            # A strict pre-tag archive may contain only declared role files, with one unambiguous role per file.
            foreach ($file in $allArchiveFiles) {
                $relative = $relativeFiles[$file.FullName]
                $matchedRoles = New-Object System.Collections.Generic.List[string]
                foreach ($role in $requiredRoles) {
                    # Windows PowerShell 5 can return an unstable collection shape for chained PSObject property indexing.
                    $aliasProperty = $manifest.acceptedAliases.PSObject.Properties |
                        Where-Object { $_.Name -eq $role } |
                        Select-Object -First 1
                    if ($null -eq $aliasProperty) { continue }
                    $patterns = @($aliasProperty.Value)
                    if (@($patterns | Where-Object {
                        [regex]::IsMatch($relative, [string]$_, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
                    }).Count -gt 0) {
                        $matchedRoles.Add($role)
                    }
                }
                if ($matchedRoles.Count -eq 0) {
                    Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "UNKNOWN_ARCHIVE_FILE file=$relative"
                } elseif ($matchedRoles.Count -gt 1) {
                    Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' ("AMBIGUOUS_ARCHIVE_ROLE file={0} roles={1}" -f
                        $relative, ($matchedRoles -join ','))
                }
            }
        }

        $readme = Join-Path $gateRoot 'README.md'
        if (Test-Path -LiteralPath $readme) {
            $inFence = $false
            $lineNumber = 0
            foreach ($line in Get-Content -LiteralPath $readme -Encoding UTF8) {
                $lineNumber++
                if ($line -match '^\s*```') {
                    $inFence = -not $inFence
                    continue
                }
                if ($inFence) { continue }
                foreach ($match in [regex]::Matches($line, '\[[^\]]*\]\(([^)]+)\)')) {
                    $link = $match.Groups[1].Value.Trim().Trim('<', '>').Split('#')[0]
                    if ([string]::IsNullOrWhiteSpace($link) -or $link -match '^(https?://|mailto:|#)') { continue }
                    $target = Join-Path $gateRoot ([uri]::UnescapeDataString($link))
                    if (-not (Test-Path -LiteralPath $target)) {
                        Add-ArchiveError 'ARCHIVE_LINK_BROKEN' "README_LINK_BROKEN line=$lineNumber link=$link"
                    }
                }
            }
        }

        $configuredExpectedTag = $null
        if ($strictConfig) {
            $expectedTagProperty = $strictConfig.PSObject.Properties['expectedTag']
            if ($expectedTagProperty) {
                $configuredExpectedTag = [string]$expectedTagProperty.Value
            } else {
                # Both modes must fail closed when a strict override omits its release-tag binding.
                Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "EXPECTED_TAG_MISSING gate=$Gate"
            }
        }
        if ($isStrict -and $configuredExpectedTag -and -not $ExpectedTag) {
            $ExpectedTag = $configuredExpectedTag
        }
        if ($isStrict -and $ExpectedTag -and $configuredExpectedTag -and
            $ExpectedTag -ne $configuredExpectedTag) {
            Add-ArchiveError 'TAG_TARGET_MISMATCH' "EXPECTED_TAG_CONFLICT manifest=$configuredExpectedTag argument=$ExpectedTag"
        }

        if ($PreTag) {
            $canonicalTag = 'nq-{0}-freeze' -f ($Gate.Replace('-', ''))
            if (-not $ExpectedTag -or $ExpectedTag -notmatch '^nq-gate[a-z0-9]+-freeze$' -or $ExpectedTag -ne $canonicalTag) {
                Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "PRETAG_EXPECTED_TAG_INVALID gate=$Gate expectedTag=$ExpectedTag canonical=$canonicalTag"
            } else {
                $localTag = Invoke-GitRead @('tag', '--list', $ExpectedTag)
                if ($localTag -eq $ExpectedTag) {
                    Add-ArchiveError 'PRETAG_MODE_TAG_ALREADY_EXISTS' "PRETAG_MODE_TAG_ALREADY_EXISTS $ExpectedTag"
                }
            }

            $gateLabel = Get-GateLabel $Gate
            $authority = Read-AuthorityBlock (Join-Path $repoRoot 'docs/current/STATUS.md')
            if ($null -eq $authority) {
                Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' 'PRETAG_AUTHORITY_BLOCK_INVALID'
            } else {
                $expectedWorkBatch = "$gateLabel-FREEZE"
                if ($authority.active_gate -ne $gateLabel) {
                    Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "PRETAG_AUTHORITY_GATE_MISMATCH expected=$gateLabel actual=$($authority.active_gate)"
                }
                if ($authority.active_gate_status -ne 'IN_PROGRESS|NOT_FROZEN') {
                    Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "PRETAG_AUTHORITY_STATUS_INVALID value=$($authority.active_gate_status)"
                }
                if ($authority.work_batch -ne $expectedWorkBatch) {
                    Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "PRETAG_WORK_BATCH_MISMATCH expected=$expectedWorkBatch actual=$($authority.work_batch)"
                }
                if ($authority.work_batch_status -notin @('NOT_STARTED', 'IMPLEMENTED|PENDING_REVIEW')) {
                    Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "PRETAG_WORK_STATUS_INVALID value=$($authority.work_batch_status)"
                }
                if ($authority.live -ne 'DISABLED') {
                    Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "PRETAG_LIVE_BOUNDARY_INVALID value=$($authority.live)"
                }
                Write-Output ("PRETAG gate={0} expectedTag={1} workBatch={2} workStatus={3}" -f
                    $gateLabel, $ExpectedTag, $authority.work_batch, $authority.work_batch_status)
            }
        }

        if ($ExpectedTag -and -not $PreTag) {
            $localTag = Invoke-GitRead @('tag', '--list', $ExpectedTag)
            if ($localTag -ne $ExpectedTag) {
                Add-ArchiveError 'TAG_MISSING' "LOCAL_TAG_MISSING $ExpectedTag"
            } else {
                $tagType = Invoke-GitRead @('cat-file', '-t', $ExpectedTag)
                if ($tagType -ne 'tag') {
                    Add-ArchiveError 'TAG_MISSING' "TAG_NOT_ANNOTATED type=$tagType"
                }
                $tagObject = Invoke-GitRead @('rev-parse', "$ExpectedTag^{tag}")
                $peeledCommit = Invoke-GitRead @('rev-parse', "$ExpectedTag^{}")
                $expectedTarget = if ($isStrict -and $strictConfig.expectedTagTarget) {
                    [string]$strictConfig.expectedTagTarget
                } else {
                    $peeledCommit
                }
                if ($peeledCommit -ne $expectedTarget) {
                    Add-ArchiveError 'TAG_TARGET_MISMATCH' "TAG_TARGET expected=$expectedTarget actual=$peeledCommit"
                }

                if ($RequireRemoteTag) {
                    $remoteOutput = Invoke-GitRead @('ls-remote', '--tags', 'origin')
                    $escapedTag = [regex]::Escape($ExpectedTag)
                    $remoteObjectMatch = [regex]::Match($remoteOutput, "(?m)^([0-9a-f]{40})\s+refs/tags/$escapedTag$")
                    $remotePeeledMatch = [regex]::Match($remoteOutput, "(?m)^([0-9a-f]{40})\s+refs/tags/$escapedTag\^\{\}$")
                    if (-not $remoteObjectMatch.Success -or -not $remotePeeledMatch.Success) {
                        Add-ArchiveError 'TAG_MISSING' "REMOTE_TAG_MISSING $ExpectedTag"
                    } elseif ($remoteObjectMatch.Groups[1].Value -ne $tagObject) {
                        Add-ArchiveError 'TAG_TARGET_MISMATCH' "REMOTE_TAG_OBJECT expected=$tagObject actual=$($remoteObjectMatch.Groups[1].Value)"
                    } elseif ($remotePeeledMatch.Groups[1].Value -ne $peeledCommit) {
                        Add-ArchiveError 'TAG_TARGET_MISMATCH' "REMOTE_TAG_TARGET expected=$peeledCommit actual=$($remotePeeledMatch.Groups[1].Value)"
                    }
                }

                $ciRunId = $null
                if ($RequireCi) {
                    $gh = Get-Command gh -ErrorAction SilentlyContinue
                    if (-not $gh) {
                        Add-ArchiveError 'CI_NOT_GREEN' 'GH_CLI_NOT_FOUND'
                    } else {
                        $json = & gh run list --commit $peeledCommit --limit 20 --json databaseId,workflowName,status,conclusion,headSha 2>&1
                        if ($LASTEXITCODE -ne 0) {
                            Add-ArchiveError 'CI_NOT_GREEN' "GH_RUN_LIST_FAILED $($json -join ' ')"
                        } else {
                            $runs = @($json | ConvertFrom-Json)
                            $green = @($runs | Where-Object {
                                $_.workflowName -eq 'NQ CI Baseline' -and
                                $_.headSha -eq $peeledCommit -and
                                $_.status -eq 'completed' -and
                                $_.conclusion -eq 'success'
                            } | Sort-Object databaseId -Descending | Select-Object -First 1)
                            if ($green.Count -eq 0) {
                                Add-ArchiveError 'CI_NOT_GREEN' "TAGGED_COMMIT_CI_NOT_GREEN headSha=$peeledCommit"
                            } else {
                                $ciRunId = $green[0].databaseId
                            }
                        }
                    }
                }

                $statusContent = Read-Utf8File (Join-Path $repoRoot 'docs/current/STATUS.md')
                if ($statusContent -notmatch '(?s)current_gate_status=.*TAGGED' -or
                    $statusContent -notmatch ("current_gate_tag={0}" -f [regex]::Escape($ExpectedTag)) -or
                    $statusContent -notmatch ("updated_commit={0}" -f [regex]::Escape($peeledCommit))) {
                    Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' 'ARCHIVE_CURRENT_TAG_STATE_CONFLICT'
                }

                $archiveContent = ($archiveFiles | ForEach-Object { Read-Utf8File $_.FullName }) -join "`n"
                if ($archiveContent -match 'TAG PENDING' -and $isStrict -and $strictConfig.allowPreTagArchiveState) {
                    Add-ArchiveWarning 'PRE_TAG_ARCHIVE_STATE_PRESERVED; current STATUS carries verified tag state'
                }

                Write-Output ("TAG name={0} object={1} peeled={2} remoteRequired={3} ciRun={4}" -f
                    $ExpectedTag, $tagObject, $peeledCommit, [bool]$RequireRemoteTag, $ciRunId)
            }
        }
    }
}

Write-Output ("ARCHIVE_CHECK gate={0} warnings={1} errors={2}" -f $Gate, $warnings.Count, $errors.Count)
if ($errors.Count -gt 0) {
    Write-Output ("BLOCKED / {0}" -f $blockingCode)
    exit 1
}

if ($PreTag) {
    Write-Output 'PASS / GATE_ARCHIVE_PRETAG_VALID'
} else {
    Write-Output 'PASS / ARCHIVE_MANIFEST_COMPLETE'
}
exit 0
