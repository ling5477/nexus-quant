[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ExpectedCommit,
    [Parameter(Mandatory = $true)][string]$BackendArtifactPath,
    [Parameter(Mandatory = $true)][string]$FrontendArtifactRoot,
    [Parameter(Mandatory = $true)][string]$OutputRoot,
    [string]$BackendArtifactManifestPath,
    [string]$FrontendArtifactManifestPath,
    [ValidateSet('DEPLOYABLE', 'TEST_ONLY')][string]$BuildMode = 'DEPLOYABLE'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$modulePath = Join-Path $PSScriptRoot 'nq-canonical-release.psm1'
$migrationRoot = Join-Path $repo 'backend/nq-infra/src/main/resources/db/migration'
Import-Module $modulePath -Force -DisableNameChecking

function Assert-SourceIdentity {
    $head = (& git -C $repo rev-parse HEAD 2>$null).Trim().ToLowerInvariant()
    if ($LASTEXITCODE -ne 0 -or $ExpectedCommit -cnotmatch '^[0-9a-f]{40}$' -or
            $head -cne $ExpectedCommit.ToLowerInvariant()) {
        throw 'BLOCKED / RELEASE_SOURCE_COMMIT_MISMATCH'
    }
    $tree = (& git -C $repo rev-parse 'HEAD^{tree}' 2>$null).Trim().ToLowerInvariant()
    if ($LASTEXITCODE -ne 0 -or $tree -cnotmatch '^[0-9a-f]{40}$') {
        throw 'BLOCKED / RELEASE_SOURCE_TREE_INVALID'
    }
    if ($BuildMode -ceq 'DEPLOYABLE') {
        $status = @(& git -C $repo status --porcelain=v1 --untracked-files=all)
        if ($LASTEXITCODE -ne 0 -or $status.Count -ne 0) {
            throw 'BLOCKED / DEPLOYABLE_RELEASE_REQUIRES_CLEAN_COMMITTED_TREE'
        }
    }
    $script:VerifiedSourceCommit = $head
    $script:VerifiedSourceTree = $tree
}

function Assert-PlainSourceFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "BLOCKED / RELEASE_SOURCE_ARTIFACT_MISSING / $Path"
    }
    $item = Get-Item -LiteralPath $Path -Force
    if ($null -ne $item.LinkType -or (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
        throw 'BLOCKED / RELEASE_SOURCE_LINK_FORBIDDEN'
    }
}

function Assert-DeliveryArtifactManifest([string]$ManifestPath,[string]$ArtifactPath,[bool]$Directory) {
    if ([string]::IsNullOrWhiteSpace($ManifestPath) -or -not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
        throw 'BLOCKED / DEPLOYABLE_ARTIFACT_MANIFEST_REQUIRED'
    }
    try { $manifest=Get-Content -LiteralPath $ManifestPath -Raw|ConvertFrom-Json } catch { throw 'BLOCKED / DEPLOYABLE_ARTIFACT_MANIFEST_INVALID' }
    if ([string]$manifest.schemaVersion -cne 'nq-delivery-artifact-manifest-v1' -or [int]$manifest.fileCount -ne @($manifest.files).Count) {
        throw 'BLOCKED / DEPLOYABLE_ARTIFACT_MANIFEST_INVALID'
    }
    $actual=@()
    if($Directory){
        $root=[IO.Path]::GetFullPath($ArtifactPath)
        $actual=@(Get-ChildItem -LiteralPath $root -File -Recurse -Force|ForEach-Object{
            [pscustomobject]@{relative=$_.FullName.Substring($root.Length+1).Replace('\','/');size=[long]$_.Length;sha256=Get-NqSha256File $_.FullName}
        })
        foreach($item in $actual){
            $matches=@($manifest.files|Where-Object{([string]$_.relativePath).EndsWith('/'+$item.relative,[StringComparison]::Ordinal) -and [long]$_.size -eq $item.size -and [string]$_.sha256 -ceq $item.sha256})
            if($matches.Count-ne1){throw 'BLOCKED / DEPLOYABLE_ARTIFACT_SET_MISMATCH'}
        }
    }else{
        $item=Get-Item -LiteralPath $ArtifactPath
        $actual=@([pscustomobject]@{size=[long]$item.Length;sha256=Get-NqSha256File $item.FullName})
        $matches=@($manifest.files|Where-Object{[long]$_.size-eq$actual[0].size -and [string]$_.sha256-ceq$actual[0].sha256})
        if($matches.Count-ne1){throw 'BLOCKED / DEPLOYABLE_ARTIFACT_SET_MISMATCH'}
    }
    if($actual.Count-ne[int]$manifest.fileCount){throw 'BLOCKED / DEPLOYABLE_ARTIFACT_SET_MISMATCH'}
}

function Copy-ReleaseArtifact {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][string]$Role,
        [Parameter(Mandatory = $true)][string]$UnixMode,
        [Parameter(Mandatory = $true)][string]$StagingRoot
    )
    Assert-PlainSourceFile $Source
    $destination = Join-Path $StagingRoot $RelativePath
    [IO.Directory]::CreateDirectory((Split-Path -Parent $destination)) | Out-Null
    [IO.File]::Copy([IO.Path]::GetFullPath($Source), $destination, $false)
    if ($IsLinux) {
        & /usr/bin/chmod $UnixMode '--' $destination
        if ($LASTEXITCODE -ne 0) { throw 'FAIL / RELEASE_BUILD_MODE_APPLY_FAILED' }
    }
    $item = Get-Item -LiteralPath $destination -Force
    return [pscustomobject][ordered]@{
        relativePath = $RelativePath.Replace('\', '/')
        role = $Role
        size = [long]$item.Length
        sha256 = Get-NqSha256File $destination
        unixMode = $UnixMode
        executable = $UnixMode -ceq '0755'
    }
}

Assert-SourceIdentity
$sourceCommit = [string]$script:VerifiedSourceCommit
$backend = [IO.Path]::GetFullPath($BackendArtifactPath)
$frontend = [IO.Path]::GetFullPath($FrontendArtifactRoot)
if (-not (Test-Path -LiteralPath $frontend -PathType Container)) {
    throw 'BLOCKED / FRONTEND_PRODUCTION_ARTIFACT_MISSING'
}
if ($BuildMode -ceq 'DEPLOYABLE') {
    Assert-DeliveryArtifactManifest $BackendArtifactManifestPath $backend $false
    Assert-DeliveryArtifactManifest $FrontendArtifactManifestPath $frontend $true
}

$output = [IO.Path]::GetFullPath($OutputRoot)
if (Test-Path -LiteralPath $output) { throw 'BLOCKED / RELEASE_OUTPUT_ALREADY_EXISTS' }
$parent = Split-Path -Parent $output
[IO.Directory]::CreateDirectory($parent) | Out-Null
$staging = Join-Path $parent ('.nq-release-build-' + [Guid]::NewGuid().ToString('N'))

try {
    [IO.Directory]::CreateDirectory($staging) | Out-Null
    $artifacts = [Collections.Generic.List[object]]::new()
    $artifacts.Add((Copy-ReleaseArtifact $backend 'app/nq-app.jar' 'application-jar' '0644' $staging))

    foreach ($file in Get-ChildItem -LiteralPath $frontend -File -Recurse -Force | Sort-Object FullName) {
        $relative = $file.FullName.Substring($frontend.Length + 1).Replace('\', '/')
        $artifacts.Add((Copy-ReleaseArtifact $file.FullName "frontend/$relative" 'frontend-production' '0644' $staging))
    }
    if (@($artifacts | Where-Object role -eq 'frontend-production').Count -eq 0) {
        throw 'BLOCKED / FRONTEND_PRODUCTION_ARTIFACT_EMPTY'
    }

    $sources = @(
        @('deploy/canonical/nq-canonical.service', 'deploy/nq-canonical.service', 'deployment-systemd', '0644'),
        @('deploy/canonical/deployment-contract.json', 'deploy/deployment-contract.json', 'deployment-contract', '0644'),
        @('scripts/deployment/New-NqCanonicalRelease.ps1', 'bin/New-NqCanonicalRelease.ps1', 'release-builder-contract', '0644'),
        @('scripts/deployment/New-NqCanonicalReleaseAdmission.ps1', 'bin/New-NqCanonicalReleaseAdmission.ps1', 'admission-producer-contract', '0644'),
        @('scripts/deployment/Test-NqCanonicalReleaseAdmission.ps1', 'bin/Test-NqCanonicalReleaseAdmission.ps1', 'admission-verifier', '0755'),
        @('scripts/deployment/nq-canonical-release.psm1', 'bin/nq-canonical-release.psm1', 'release-contract', '0644'),
        @('scripts/deployment/Test-NqCanonicalRelease.ps1', 'bin/Test-NqCanonicalRelease.ps1', 'release-verifier', '0755'),
        @('scripts/deployment/Install-NqCanonicalRelease.ps1', 'bin/Install-NqCanonicalRelease.ps1', 'release-installer', '0755')
    )
    foreach ($source in $sources) {
        $artifacts.Add((Copy-ReleaseArtifact (Join-Path $repo $source[0]) $source[1] $source[2] $source[3] $staging))
    }

    $migration = Get-NqMigrationInventory $migrationRoot
    $deployable = $BuildMode -ceq 'DEPLOYABLE'
    $sourceState = if ($deployable) { 'COMMITTED_CLEAN' } else { 'UNCOMMITTED_CANDIDATE' }
    $sourceTreeIdentity = if ($deployable) {
        'git-tree:' + [string]$script:VerifiedSourceTree
    } else {
        'candidate-sha256:' + (Get-NqSha256Text (ConvertTo-NqCanonicalJson @($artifacts)))
    }
    $manifest = New-NqCanonicalManifest `
        -SourceCommit $sourceCommit `
        -RequiredJavaMajor 21 `
        -RequiredPostgresqlMajor 16 `
        -MigrationInventory $migration `
        -Artifacts @($artifacts) `
        -Deployable $deployable `
        -SourceState $sourceState `
        -SourceTreeIdentity $sourceTreeIdentity
    Write-NqCanonicalManifest (Join-Path $staging 'release-manifest.json') $manifest
    if ($IsLinux) { & /usr/bin/chmod 0644 '--' (Join-Path $staging 'release-manifest.json') }

    $verification = Test-NqCanonicalRelease $staging `
        -ExpectedSourceCommit $sourceCommit -ExpectedSchemaTarget $migration.targetVersion
    [IO.Directory]::Move($staging, $output)
    [pscustomobject][ordered]@{
        decision = 'PASS / NQ_CANONICAL_RELEASE_BUILT'
        releaseRoot = $output
        releaseId = $verification.releaseId
        sourceCommit = $sourceCommit
        schemaTarget = $migration.targetVersion
        artifactCount = $verification.artifactCount
        deterministicIdentity = $true
        deployable = $deployable
        sourceState = $sourceState
        sourceTreeIdentity = $sourceTreeIdentity
    }
} finally {
    if (Test-Path -LiteralPath $staging -PathType Container) {
        Remove-Item -LiteralPath $staging -Recurse -Force
    }
}
