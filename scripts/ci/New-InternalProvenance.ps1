[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-f]{40}$')][string] $SourceCommit,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9]+$')][string] $CiRunId,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9]+$')][string] $CiRunAttempt,
    [Parameter(Mandatory = $true)][string] $Repository,
    [Parameter(Mandatory = $true)][string] $Ref,
    [Parameter(Mandatory = $true)][string] $WorkflowName,
    [Parameter(Mandatory = $true)][string] $WorkflowPath,
    [Parameter(Mandatory = $true)][string] $WorkflowFilePath,
    [Parameter(Mandatory = $true)][string] $SupplyChainLockPath,
    [Parameter(Mandatory = $true)][string] $JavaVersion,
    [Parameter(Mandatory = $true)][string] $NodeVersion,
    [Parameter(Mandatory = $true)][string] $NpmVersion,
    [Parameter(Mandatory = $true)][string] $BackendEvidenceRoot,
    [Parameter(Mandatory = $true)][string] $FrontendEvidenceRoot,
    [Parameter(Mandatory = $true)][string] $BackendSbomPath,
    [Parameter(Mandatory = $true)][string] $FrontendSbomPath,
    [Parameter(Mandatory = $true)][string] $BackendArtifactManifestPath,
    [Parameter(Mandatory = $true)][string] $FrontendArtifactManifestPath,
    [Parameter(Mandatory = $true)][string] $OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-Sha256File([string] $Path) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Get-Sha256Text([string] $Text) {
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        $hash = $algorithm.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text))
        return ([BitConverter]::ToString($hash) -replace '-', '').ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
    }
}

function Read-Json([string] $Path) {
    try { return Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json }
    catch { throw "Invalid JSON path=$Path reason=$($_.Exception.Message)" }
}

function Assert-WithinRoot([string] $Root, [string] $Target) {
    $prefix = $Root.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $comparison = if ($env:OS -eq 'Windows_NT') { [StringComparison]::OrdinalIgnoreCase } else { [StringComparison]::Ordinal }
    if (-not $Target.StartsWith($prefix, $comparison)) {
        throw "Evidence path escapes root: $Target"
    }
}

function Assert-ArtifactManifest([string] $ManifestPath, [string] $EvidenceRoot) {
    $root = (Resolve-Path -LiteralPath $EvidenceRoot).Path
    $manifest = Read-Json $ManifestPath
    if ([string]$manifest.schemaVersion -cne 'nq-delivery-artifact-manifest-v1') {
        throw "Unsupported artifact manifest: $ManifestPath"
    }
    $files = @($manifest.files)
    if ($files.Count -eq 0 -or [int]$manifest.fileCount -ne $files.Count) {
        throw "Artifact manifest file count is invalid: $ManifestPath"
    }
    foreach ($file in $files) {
        $relative = [string]$file.relativePath
        if ([IO.Path]::IsPathRooted($relative) -or $relative -match '(^|/)\.\.(/|$)') {
            throw "Unsafe artifact relative path: $relative"
        }
        $target = [IO.Path]::GetFullPath((Join-Path $root $relative))
        Assert-WithinRoot $root $target
        if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
            throw "Artifact is missing: $relative"
        }
        $item = Get-Item -LiteralPath $target -Force
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
                [long]$file.size -ne [long]$item.Length -or
                [string]$file.sha256 -cne (Get-Sha256File $target)) {
            throw "Artifact integrity mismatch: $relative"
        }
    }
    $projection = (@($files | Sort-Object relativePath | ForEach-Object {
        '{0}|{1}|{2}' -f [string]$_.relativePath, [long]$_.size, [string]$_.sha256
    }) -join "`n") + "`n"
    if ([string]$manifest.aggregateSha256 -cne (Get-Sha256Text $projection)) {
        throw "Artifact aggregate digest mismatch: $ManifestPath"
    }
    return $manifest
}

function Assert-CycloneDx([string] $Path) {
    $sbom = Read-Json $Path
    if ([string]$sbom.bomFormat -cne 'CycloneDX' -or @($sbom.components).Count -eq 0) {
        throw "Invalid CycloneDX SBOM: $Path"
    }
}

function Write-Utf8LfJson([string] $Path, [object] $Value) {
    $parent = Split-Path -Parent $Path
    if (-not [string]::IsNullOrWhiteSpace($parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    $json = ($Value | ConvertTo-Json -Depth 30).Replace("`r`n", "`n").TrimEnd() + "`n"
    [IO.File]::WriteAllText($Path, $json, (New-Object Text.UTF8Encoding($false)))
}

$resolvedWorkflow = (Resolve-Path -LiteralPath $WorkflowFilePath).Path
$resolvedSupplyChainLock = (Resolve-Path -LiteralPath $SupplyChainLockPath).Path
$resolvedBackendSbom = (Resolve-Path -LiteralPath $BackendSbomPath).Path
$resolvedFrontendSbom = (Resolve-Path -LiteralPath $FrontendSbomPath).Path
$resolvedBackendManifest = (Resolve-Path -LiteralPath $BackendArtifactManifestPath).Path
$resolvedFrontendManifest = (Resolve-Path -LiteralPath $FrontendArtifactManifestPath).Path

Assert-CycloneDx $resolvedBackendSbom
Assert-CycloneDx $resolvedFrontendSbom
$backendManifest = Assert-ArtifactManifest $resolvedBackendManifest $BackendEvidenceRoot
$frontendManifest = Assert-ArtifactManifest $resolvedFrontendManifest $FrontendEvidenceRoot

$materials = @(
    [ordered]@{ name = 'workflow'; sha256 = Get-Sha256File $resolvedWorkflow },
    [ordered]@{ name = 'supply-chain-lock'; sha256 = Get-Sha256File $resolvedSupplyChainLock },
    [ordered]@{ name = 'backend-sbom'; sha256 = Get-Sha256File $resolvedBackendSbom },
    [ordered]@{ name = 'frontend-sbom'; sha256 = Get-Sha256File $resolvedFrontendSbom },
    [ordered]@{ name = 'backend-artifact-manifest'; sha256 = Get-Sha256File $resolvedBackendManifest },
    [ordered]@{ name = 'frontend-artifact-manifest'; sha256 = Get-Sha256File $resolvedFrontendManifest }
)
$subjects = @(
    [ordered]@{ name = [string]$backendManifest.artifactSetName; sha256 = [string]$backendManifest.aggregateSha256 },
    [ordered]@{ name = [string]$frontendManifest.artifactSetName; sha256 = [string]$frontendManifest.aggregateSha256 }
)
$digestProjection = (@($materials | ForEach-Object { "material|$($_.name)|$($_.sha256)" }) +
        @($subjects | ForEach-Object { "subject|$($_.name)|$($_.sha256)" })) -join "`n"

$provenance = [ordered]@{
    schemaVersion = 'nq-internal-provenance-v1'
    attestationType = 'INTERNAL_PROVENANCE'
    source = [ordered]@{ repository = $Repository; ref = $Ref; commit = $SourceCommit }
    ci = [ordered]@{
        runId = $CiRunId
        runAttempt = $CiRunAttempt
        runIdentity = "$Repository/actions/runs/$CiRunId/attempts/$CiRunAttempt"
        workflowName = $WorkflowName
        workflowPath = $WorkflowPath
    }
    toolchain = [ordered]@{ java = $JavaVersion; node = $NodeVersion; npm = $NpmVersion }
    materials = $materials
    subjects = $subjects
    evidenceSetSha256 = Get-Sha256Text ($digestProjection + "`n")
    platformAttestation = [ordered]@{
        status = 'DEFERRED_UNTIL_EXPLICIT_AUTHORIZATION'
        idTokenPermission = 'NOT_GRANTED'
    }
}
Write-Utf8LfJson $OutputPath $provenance
Write-Output "INTERNAL_PROVENANCE sourceCommit=$SourceCommit runId=$CiRunId evidenceSetSha256=$($provenance.evidenceSetSha256)"
