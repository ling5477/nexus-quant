<#
.SYNOPSIS
Validates Gate archive manifest roles, README links, and non-role task evidence.
.NOTES
This checker does not read current authority or implement CI, tag, or remote release rules.
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
. (Join-Path $PSScriptRoot 'governance-workflow-lib.ps1')
$errors = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]
$blockingCode = $null

function Resolve-RepoPath {
    param([string] $Path)
    if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
    return (Join-Path $repoRoot $Path)
}

function Read-Utf8File {
    param([string] $Path)
    return [System.IO.File]::ReadAllText($Path, (New-Object System.Text.UTF8Encoding($false)))
}

function Add-ArchiveError {
    param([string] $Code, [string] $Message)
    if (-not $script:blockingCode) { $script:blockingCode = $Code }
    $script:errors.Add($Message)
    Write-Output "ERROR $Message"
}

function Add-ArchiveWarning {
    param([string] $Message)
    $script:warnings.Add($Message)
    Write-Output "WARNING $Message"
}

function Get-GateOrdinal {
    param([string] $GateName)
    $suffix = $GateName.Substring(5).ToLowerInvariant()
    if ($suffix -match '^[a-z]$') { return ([int][char]$suffix - [int][char]'a') }
    return [int]::MaxValue
}

if ($PreTag -and ($RequireRemoteTag -or $RequireCi)) {
    Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' 'PRETAG_MODE_INCOMPATIBLE_WITH_RELEASE_VALIDATION'
}

$resolvedManifest = Resolve-RepoPath $ManifestPath
$resolvedContract = Join-Path $PSScriptRoot 'governance-workflow-contract.json'
if (-not (Test-Path -LiteralPath $resolvedManifest -PathType Leaf)) {
    Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "MANIFEST_NOT_FOUND $ManifestPath"
} else {
    try { $manifest = Read-Utf8File $resolvedManifest | ConvertFrom-Json } catch { $manifest = $null }
    try { $contract = Get-GovernanceWorkflowContract $resolvedContract } catch { $contract = $null }
    if ($null -eq $manifest -or -not $manifest.schemaVersion) {
        Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' 'MANIFEST_INVALID'
    } elseif ($null -eq $contract) {
        Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' 'GOVERNANCE_CONTRACT_INVALID'
    } else {
        foreach ($property in @('mandatoryRoles', 'acceptedAliases', 'defaultStrictPolicy', 'historicalProfiles', 'roleBodyPolicy')) {
            if ($null -eq (Get-GovernancePropertyValue $manifest $property)) {
                Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "MANIFEST_SCHEMA_FIELD_MISSING field=$property"
            }
        }

        $gateRoot = Join-Path $repoRoot "docs/gates/$Gate"
        if (-not (Test-Path -LiteralPath $gateRoot -PathType Container)) {
            Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "GATE_ARCHIVE_NOT_FOUND $Gate"
        } else {
            $historicalConfig = Get-GovernancePropertyValue $manifest.historicalProfiles $Gate
            $isLegacy = (Get-GateOrdinal $Gate) -le (Get-GateOrdinal ([string]$manifest.legacyThroughGate))
            $isStrict = -not $isLegacy
            $effectiveConfig = if ($null -ne $historicalConfig) { $historicalConfig } else { $manifest.defaultStrictPolicy }

            $requiredRoles = New-Object System.Collections.Generic.List[string]
            foreach ($role in @($manifest.mandatoryRoles)) { $requiredRoles.Add([string]$role) }
            if ($null -ne $effectiveConfig) {
                foreach ($role in @($effectiveConfig.conditionalRoles)) { $requiredRoles.Add([string]$role) }
            }
            if (@($requiredRoles | Select-Object -Unique).Count -ne $requiredRoles.Count) {
                Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "DUPLICATE_REQUIRED_ROLE gate=$Gate"
            }
            $profileName = if ($null -ne $historicalConfig) { 'historical' } else { 'default' }
            Write-Output ("POLICY gate={0} legacyThrough={1} legacy={2} strict={3} profile={4}" -f $Gate, $manifest.legacyThroughGate, $isLegacy, $isStrict, $profileName)

            $allItems = @(Get-ChildItem -LiteralPath $gateRoot -Recurse -Force)
            foreach ($item in $allItems) {
                if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                    $relativeLink = $item.FullName.Substring($gateRoot.Length + 1).Replace('\', '/')
                    Add-ArchiveError 'TASK_EVIDENCE_INVALID' "SYMLINK_ARCHIVE_ITEM_FORBIDDEN file=$relativeLink"
                }
            }
            $allFiles = @($allItems | Where-Object { -not $_.PSIsContainer })
            $relativeFiles = @{}
            $evidenceFiles = New-Object System.Collections.Generic.List[object]
            $roleFiles = New-Object System.Collections.Generic.List[object]
            foreach ($file in $allFiles) {
                $relative = $file.FullName.Substring($gateRoot.Length + 1).Replace('\', '/')
                $relativeFiles[$file.FullName] = $relative
                if ($relative.StartsWith([string]$contract.evidence.archiveRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
                    if (-not (Test-GovernanceEvidenceItem $contract $file 'archive' $relative)) {
                        Add-ArchiveError 'TASK_EVIDENCE_INVALID' "TASK_EVIDENCE_INVALID file=$relative"
                    } else {
                        $evidenceFiles.Add($file)
                        Write-Output "EVIDENCE file=$relative role=non-role valid=True"
                    }
                } else {
                    $roleFiles.Add($file)
                }
            }

            foreach ($role in $requiredRoles) {
                $aliasProperty = Get-GovernancePropertyValue $manifest.acceptedAliases $role
                if ($null -eq $aliasProperty) {
                    Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "ROLE_ALIAS_MISSING role=$role"
                    continue
                }
                $patterns = @($aliasProperty)
                $matchedFiles = @($roleFiles | Where-Object {
                    $relative = $relativeFiles[$_.FullName]
                    @($patterns | Where-Object { [regex]::IsMatch($relative, [string]$_, 'IgnoreCase') }).Count -gt 0
                })
                if ($matchedFiles.Count -eq 0) {
                    if ($isStrict) { Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "ROLE_MISSING role=$role gate=$Gate" }
                    else { Add-ArchiveWarning "LEGACY_ROLE_MISSING role=$role gate=$Gate" }
                    continue
                }
                if ($isStrict -and $matchedFiles.Count -gt 1) {
                    Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' ("DUPLICATE_ROLE role={0} files={1}" -f $role, (($matchedFiles | ForEach-Object { $relativeFiles[$_.FullName] }) -join ','))
                }

                $hasIndependentBody = $false
                foreach ($file in $matchedFiles) {
                    $content = Read-Utf8File $file.FullName
                    $nonEmptyLines = @(($content -split '\r?\n') | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
                    $independent = (($content -split '\r?\n') | Where-Object { $_ -notmatch 'docs/current|\.\./\.\./current' }) -join "`n"
                    if ($nonEmptyLines -ge [int]$manifest.roleBodyPolicy.minimumNonEmptyLines -and
                        ($independent -replace '\s', '').Length -ge [int]$manifest.roleBodyPolicy.minimumIndependentCharacters) {
                        $hasIndependentBody = $true
                    }
                }
                if (-not $hasIndependentBody) {
                    if ($isStrict) { Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "THIN_ROLE role=$role gate=$Gate" }
                    else { Add-ArchiveWarning "LEGACY_THIN_ROLE role=$role gate=$Gate" }
                }

                if ($AllowedPaths -and $AllowedPaths.Count -gt 0) {
                    $covered = $false
                    foreach ($allowedPath in $AllowedPaths) {
                        $normalized = $allowedPath.Replace('\', '/')
                        if (@($patterns | Where-Object { [regex]::IsMatch($normalized, [string]$_, 'IgnoreCase') }).Count -gt 0) { $covered = $true; break }
                    }
                    if (-not $covered) { Add-ArchiveError 'ARCHIVE_ALLOWLIST_INCOMPLETE' "ALLOWLIST_ROLE_MISSING role=$role" }
                }
                Write-Output ("ROLE role={0} files={1} independent={2}" -f $role, (($matchedFiles | ForEach-Object { $relativeFiles[$_.FullName] }) -join ','), $hasIndependentBody)
            }

            if ($isStrict) {
                foreach ($file in $roleFiles) {
                    $relative = $relativeFiles[$file.FullName]
                    $matchedRoles = New-Object System.Collections.Generic.List[string]
                    foreach ($role in $requiredRoles) {
                        $patterns = @(Get-GovernancePropertyValue $manifest.acceptedAliases $role)
                        if (@($patterns | Where-Object { [regex]::IsMatch($relative, [string]$_, 'IgnoreCase') }).Count -gt 0) { $matchedRoles.Add($role) }
                    }
                    if ($matchedRoles.Count -eq 0) {
                        Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "UNKNOWN_ARCHIVE_FILE file=$relative"
                    } elseif ($matchedRoles.Count -gt 1) {
                        Add-ArchiveError 'ARCHIVE_MANIFEST_INCOMPLETE' "AMBIGUOUS_ARCHIVE_ROLE file=$relative roles=$($matchedRoles -join ',')"
                    }
                }
            }

            $readme = Join-Path $gateRoot 'README.md'
            if (Test-Path -LiteralPath $readme -PathType Leaf) {
                $inFence = $false
                $lineNumber = 0
                foreach ($line in Get-Content -LiteralPath $readme -Encoding UTF8) {
                    $lineNumber++
                    if ($line -match '^\s*```') { $inFence = -not $inFence; continue }
                    if ($inFence) { continue }
                    foreach ($match in [regex]::Matches($line, '\[[^\]]*\]\(([^)]+)\)')) {
                        $link = $match.Groups[1].Value.Trim().Trim('<', '>').Split('#')[0]
                        if ([string]::IsNullOrWhiteSpace($link) -or $link -match '^(https?://|mailto:|#)') { continue }
                        $target = [System.IO.Path]::GetFullPath((Join-Path $gateRoot ([uri]::UnescapeDataString($link))))
                        if (-not $target.StartsWith($gateRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase) -or
                            -not (Test-Path -LiteralPath $target)) {
                            Add-ArchiveError 'ARCHIVE_LINK_BROKEN' "README_LINK_BROKEN line=$lineNumber link=$link"
                        }
                    }
                }
            }
        }
    }
}

if ($errors.Count -eq 0 -and -not $PreTag -and ($RequireRemoteTag -or $RequireCi)) {
    # Compatibility orchestration delegates the complete release contract; archive code never reimplements partial tag/CI checks.
    $canonicalTag = 'nq-{0}-freeze' -f $Gate.Replace('-', '')
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $peeledOutput = & git -C $repoRoot rev-parse --verify "$canonicalTag^{}" 2>&1
    $peeledExit = $LASTEXITCODE
    $ErrorActionPreference = $previous
    $peeledCommit = (($peeledOutput | ForEach-Object { $_.ToString() }) -join "`n").Trim()
    if ($peeledExit -ne 0 -or $peeledCommit -notmatch '^[0-9a-f]{40}$') {
        Add-ArchiveError 'GATE_RELEASE_INVALID' "CANONICAL_TAG_PEELED_COMMIT_INVALID tag=$canonicalTag"
    }

    $releaseParameters = @{ Gate = $Gate }
    if ($ExpectedTag) { $releaseParameters.ExpectedTag = $ExpectedTag }
    if ($errors.Count -eq 0) {
        $releaseParameters.ExpectedCommit = $peeledCommit
        # Keep release validation in the current PowerShell host so pwsh callers are not downgraded to Windows PowerShell 5.1.
        $releaseOutput = & (Join-Path $PSScriptRoot 'check-gate-release.ps1') @releaseParameters 2>&1
        $releaseExit = $LASTEXITCODE
        $releaseOutput | Write-Output
        if ($releaseExit -ne 0) { Add-ArchiveError 'GATE_RELEASE_INVALID' 'DELEGATED_RELEASE_CHECK_FAILED' }
    }
}

Write-Output ("ARCHIVE_CHECK gate={0} warnings={1} errors={2}" -f $Gate, $warnings.Count, $errors.Count)
if ($errors.Count -gt 0) {
    Write-Output "BLOCKED / $blockingCode"
    exit 1
}
if ($PreTag) { Write-Output 'PASS / GATE_ARCHIVE_PRETAG_VALID' }
else { Write-Output 'PASS / ARCHIVE_MANIFEST_COMPLETE' }
exit 0
