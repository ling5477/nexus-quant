[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $ProvenancePath,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-f]{40}$')][string] $ExpectedSourceCommit,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9]+$')][string] $ExpectedCiRunId,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9]+$')][string] $ExpectedCiRunAttempt,
    [Parameter(Mandatory = $true)][string] $ExpectedRepository,
    [Parameter(Mandatory = $true)][string] $ExpectedRef,
    [Parameter(Mandatory = $true)][string] $ExpectedWorkflowName,
    [Parameter(Mandatory = $true)][string] $ExpectedWorkflowPath,
    [Parameter(Mandatory = $true)][string] $ExpectedJavaVersion,
    [Parameter(Mandatory = $true)][string] $ExpectedNodeVersion,
    [Parameter(Mandatory = $true)][string] $ExpectedNpmVersion,
    [Parameter(Mandatory = $true)][string] $WorkflowFilePath,
    [Parameter(Mandatory = $true)][string] $SupplyChainLockPath,
    [Parameter(Mandatory = $true)][string] $BackendEvidenceRoot,
    [Parameter(Mandatory = $true)][string] $FrontendEvidenceRoot,
    [Parameter(Mandatory = $true)][string] $BackendSbomPath,
    [Parameter(Mandatory = $true)][string] $FrontendSbomPath,
    [Parameter(Mandatory = $true)][string] $BackendArtifactManifestPath,
    [Parameter(Mandatory = $true)][string] $FrontendArtifactManifestPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-Condition([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}

function Get-RequiredProperty([object] $Object, [string] $Name, [string] $Context) {
    if ($null -eq $Object) { throw "Missing required object: $Context" }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) {
        throw "Missing required field: $Context.$Name"
    }
    return $property.Value
}

function Get-RequiredText([object] $Object, [string] $Name, [string] $Context) {
    $value = [string](Get-RequiredProperty $Object $Name $Context)
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Blank required field: $Context.$Name" }
    return $value
}

function Read-JsonFile([string] $Path, [string] $Context) {
    try { return Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json }
    catch { throw "Invalid $Context JSON path=$Path reason=$($_.Exception.Message)" }
}

function Get-Sha256File([string] $Path) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Get-Sha256Text([string] $Text) {
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($algorithm.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text))) -replace '-', '').ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
    }
}

function Assert-WithinRoot([string] $Root, [string] $Target) {
    $prefix = $Root.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $comparison = if ($env:OS -eq 'Windows_NT') { [StringComparison]::OrdinalIgnoreCase } else { [StringComparison]::Ordinal }
    Assert-Condition ($Target.StartsWith($prefix, $comparison)) "Evidence path escapes root: $Target"
}

function Assert-CycloneDx([string] $Path, [string] $Name) {
    $sbom = Read-JsonFile $Path "$Name SBOM"
    Assert-Condition ((Get-RequiredText $sbom 'bomFormat' "$Name SBOM") -ceq 'CycloneDX') "Invalid CycloneDX identity: $Name"
    [void](Get-RequiredText $sbom 'specVersion' "$Name SBOM")
    Assert-Condition (@(Get-RequiredProperty $sbom 'components' "$Name SBOM").Count -gt 0) "CycloneDX components are empty: $Name"
}

function Assert-ArtifactManifest([string] $ManifestPath, [string] $EvidenceRoot, [string] $Name) {
    $root = (Resolve-Path -LiteralPath $EvidenceRoot).Path
    $manifest = Read-JsonFile $ManifestPath "$Name artifact manifest"
    Assert-Condition ((Get-RequiredText $manifest 'schemaVersion' "$Name manifest") -ceq 'nq-delivery-artifact-manifest-v1') "Unsupported artifact manifest: $Name"
    [void](Get-RequiredText $manifest 'artifactSetName' "$Name manifest")
    $files = @(Get-RequiredProperty $manifest 'files' "$Name manifest")
    $fileCount = [int](Get-RequiredProperty $manifest 'fileCount' "$Name manifest")
    Assert-Condition ($files.Count -gt 0 -and $fileCount -eq $files.Count) "Artifact manifest file count is invalid: $Name"
    $seen = @{}
    foreach ($file in $files) {
        $relative = Get-RequiredText $file 'relativePath' "$Name manifest file"
        Assert-Condition (-not $seen.ContainsKey($relative)) "Duplicate artifact manifest path: $relative"
        $seen[$relative] = $true
        Assert-Condition (-not [IO.Path]::IsPathRooted($relative) -and $relative -notmatch '(^|/)\.\.(/|$)') "Unsafe artifact relative path: $relative"
        $target = [IO.Path]::GetFullPath((Join-Path $root $relative))
        Assert-WithinRoot $root $target
        Assert-Condition (Test-Path -LiteralPath $target -PathType Leaf) "Artifact is missing: $relative"
        $item = Get-Item -LiteralPath $target -Force
        Assert-Condition (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "Artifact must not be a link: $relative"
        Assert-Condition ([long](Get-RequiredProperty $file 'size' "$Name manifest file") -eq [long]$item.Length) "Artifact size mismatch: $relative"
        Assert-Condition ((Get-RequiredText $file 'sha256' "$Name manifest file") -ceq (Get-Sha256File $target)) "Artifact digest mismatch: $relative"
    }
    $projection = (@($files | Sort-Object { [string]$_.relativePath } | ForEach-Object {
        '{0}|{1}|{2}' -f [string]$_.relativePath, [long]$_.size, [string]$_.sha256
    }) -join "`n") + "`n"
    Assert-Condition ((Get-RequiredText $manifest 'aggregateSha256' "$Name manifest") -ceq (Get-Sha256Text $projection)) "Artifact aggregate digest mismatch: $Name"
    return $manifest
}

$resolvedProvenance = (Resolve-Path -LiteralPath $ProvenancePath).Path
$resolvedWorkflow = (Resolve-Path -LiteralPath $WorkflowFilePath).Path
$resolvedLock = (Resolve-Path -LiteralPath $SupplyChainLockPath).Path
$resolvedBackendSbom = (Resolve-Path -LiteralPath $BackendSbomPath).Path
$resolvedFrontendSbom = (Resolve-Path -LiteralPath $FrontendSbomPath).Path
$resolvedBackendManifest = (Resolve-Path -LiteralPath $BackendArtifactManifestPath).Path
$resolvedFrontendManifest = (Resolve-Path -LiteralPath $FrontendArtifactManifestPath).Path

# This is intentionally a fresh disk read. The generator does not expose or pass its in-memory object here.
$provenance = Read-JsonFile $resolvedProvenance 'internal provenance'
Assert-Condition ((Get-RequiredText $provenance 'schemaVersion' 'provenance') -ceq 'nq-internal-provenance-v1') 'Unexpected provenance schema version'
Assert-Condition ((Get-RequiredText $provenance 'attestationType' 'provenance') -ceq 'INTERNAL_PROVENANCE') 'Internal provenance must not claim platform attestation'

$source = Get-RequiredProperty $provenance 'source' 'provenance'
Assert-Condition ((Get-RequiredText $source 'commit' 'provenance.source') -ceq $ExpectedSourceCommit) 'Provenance source commit mismatch'
Assert-Condition ((Get-RequiredText $source 'repository' 'provenance.source') -ceq $ExpectedRepository) 'Provenance repository mismatch'
Assert-Condition ((Get-RequiredText $source 'ref' 'provenance.source') -ceq $ExpectedRef) 'Provenance ref mismatch'

$ci = Get-RequiredProperty $provenance 'ci' 'provenance'
Assert-Condition ((Get-RequiredText $ci 'runId' 'provenance.ci') -ceq $ExpectedCiRunId) 'Provenance runId mismatch'
Assert-Condition ((Get-RequiredText $ci 'runAttempt' 'provenance.ci') -ceq $ExpectedCiRunAttempt) 'Provenance runAttempt mismatch'
Assert-Condition ((Get-RequiredText $ci 'runIdentity' 'provenance.ci') -ceq "$ExpectedRepository/actions/runs/$ExpectedCiRunId/attempts/$ExpectedCiRunAttempt") 'Provenance run identity mismatch'
Assert-Condition ((Get-RequiredText $ci 'workflowName' 'provenance.ci') -ceq $ExpectedWorkflowName) 'Provenance workflow name mismatch'
Assert-Condition ((Get-RequiredText $ci 'workflowPath' 'provenance.ci') -ceq $ExpectedWorkflowPath) 'Provenance workflow path mismatch'

$toolchain = Get-RequiredProperty $provenance 'toolchain' 'provenance'
Assert-Condition ((Get-RequiredText $toolchain 'java' 'provenance.toolchain') -ceq $ExpectedJavaVersion) 'Java identity mismatch'
Assert-Condition ((Get-RequiredText $toolchain 'node' 'provenance.toolchain') -ceq $ExpectedNodeVersion) 'Node identity mismatch'
Assert-Condition ((Get-RequiredText $toolchain 'npm' 'provenance.toolchain') -ceq $ExpectedNpmVersion) 'npm identity mismatch'

Assert-CycloneDx $resolvedBackendSbom 'backend'
Assert-CycloneDx $resolvedFrontendSbom 'frontend'
$backendManifest = Assert-ArtifactManifest $resolvedBackendManifest $BackendEvidenceRoot 'backend'
$frontendManifest = Assert-ArtifactManifest $resolvedFrontendManifest $FrontendEvidenceRoot 'frontend'

$expectedMaterials = [ordered]@{
    workflow = Get-Sha256File $resolvedWorkflow
    'supply-chain-lock' = Get-Sha256File $resolvedLock
    'backend-sbom' = Get-Sha256File $resolvedBackendSbom
    'frontend-sbom' = Get-Sha256File $resolvedFrontendSbom
    'backend-artifact-manifest' = Get-Sha256File $resolvedBackendManifest
    'frontend-artifact-manifest' = Get-Sha256File $resolvedFrontendManifest
}
$materials = @(Get-RequiredProperty $provenance 'materials' 'provenance')
Assert-Condition ($materials.Count -eq $expectedMaterials.Count) 'Provenance materials set is incomplete or contains extras'
$actualMaterials = @{}
foreach ($material in $materials) {
    $name = Get-RequiredText $material 'name' 'provenance.materials[]'
    $digest = Get-RequiredText $material 'sha256' 'provenance.materials[]'
    Assert-Condition (-not $actualMaterials.ContainsKey($name)) "Duplicate provenance material: $name"
    Assert-Condition ($expectedMaterials.Contains($name)) "Unexpected provenance material: $name"
    Assert-Condition ($digest -ceq [string]$expectedMaterials[$name]) "Provenance material digest mismatch: $name"
    $actualMaterials[$name] = $digest
}

$expectedSubjects = [ordered]@{
    ([string]$backendManifest.artifactSetName) = [string]$backendManifest.aggregateSha256
    ([string]$frontendManifest.artifactSetName) = [string]$frontendManifest.aggregateSha256
}
$subjects = @(Get-RequiredProperty $provenance 'subjects' 'provenance')
Assert-Condition ($subjects.Count -eq $expectedSubjects.Count) 'Provenance subjects set is incomplete or contains extras'
$actualSubjects = @{}
foreach ($subject in $subjects) {
    $name = Get-RequiredText $subject 'name' 'provenance.subjects[]'
    $digest = Get-RequiredText $subject 'sha256' 'provenance.subjects[]'
    Assert-Condition (-not $actualSubjects.ContainsKey($name)) "Duplicate provenance subject: $name"
    Assert-Condition ($expectedSubjects.Contains($name)) "Unexpected provenance subject: $name"
    Assert-Condition ($digest -ceq [string]$expectedSubjects[$name]) "Provenance subject digest mismatch: $name"
    $actualSubjects[$name] = $digest
}

$digestProjection = (@($expectedMaterials.GetEnumerator() | ForEach-Object { "material|$($_.Key)|$($_.Value)" }) +
        @($expectedSubjects.GetEnumerator() | ForEach-Object { "subject|$($_.Key)|$($_.Value)" })) -join "`n"
$expectedEvidenceDigest = Get-Sha256Text ($digestProjection + "`n")
Assert-Condition ((Get-RequiredText $provenance 'evidenceSetSha256' 'provenance') -ceq $expectedEvidenceDigest) 'Provenance evidence-set digest mismatch'

$platform = Get-RequiredProperty $provenance 'platformAttestation' 'provenance'
Assert-Condition ((Get-RequiredText $platform 'status' 'provenance.platformAttestation') -ceq 'DEFERRED_UNTIL_EXPLICIT_AUTHORIZATION') 'Platform attestation status mismatch'
Assert-Condition ((Get-RequiredText $platform 'idTokenPermission' 'provenance.platformAttestation') -ceq 'NOT_GRANTED') 'id-token permission must remain denied'

$provenanceDigest = Get-Sha256File $resolvedProvenance
Write-Output "INTERNAL_PROVENANCE_READBACK=PASS sourceCommit=$ExpectedSourceCommit runId=$ExpectedCiRunId evidenceSetSha256=$expectedEvidenceDigest provenanceSha256=$provenanceDigest"
