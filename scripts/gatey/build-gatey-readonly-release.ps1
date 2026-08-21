[CmdletBinding()]
param(
    [string]$ExpectedCommit,
    [switch]$ContractSelfTest,
    [string]$OutputRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$contractPath = Join-Path $PSScriptRoot 'gatey-readonly-release-contract.psm1'
$deploymentPath = Join-Path $PSScriptRoot 'invoke-gatey-readonly-deployment-contract.ps1'
$installerPath = Join-Path $PSScriptRoot 'install-gatey-readonly-release.ps1'
$profilePath = Join-Path $repo 'backend/nq-app/src/main/resources/application-gatey-readonly-qualification.yml'
$migrationRoot = Join-Path $repo 'backend/nq-infra/src/main/resources/db/migration'
Import-Module $contractPath -Force -DisableNameChecking

function Assert-ExactCleanCommit
{
    $head = (& git -C $repo rev-parse HEAD).Trim()
    $status = @(& git -C $repo status --porcelain=v1)
    if ($LASTEXITCODE -ne 0 -or $ExpectedCommit -cnotmatch '^[0-9a-f]{40}$' -or
            $head -cne $ExpectedCommit -or $status.Count -ne 0)
    {
        throw 'BLOCKED / EXACT_COMMIT_WORKTREE_NOT_CLEAN'
    }
    return $head
}

function Copy-Artifact
{
    param([string]$Source, [string]$Root, [string]$RelativePath, [string]$Mode, [string]$Role)
    if (-not (Test-Path -LiteralPath $Source -PathType Leaf))
    {
        throw 'BLOCKED / RELEASE_ARTIFACT_MISSING'
    }
    $destination = Join-Path $Root $RelativePath
    [IO.Directory]::CreateDirectory((Split-Path -Parent $destination)) | Out-Null
    Copy-Item -LiteralPath $Source -Destination $destination
    return [pscustomobject][ordered]@{
        relativePath = $RelativePath.Replace('\', '/')
        size = (Get-Item -LiteralPath $destination).Length
        sha256 = Get-GateYReadonlySha256File $destination
        mode = $Mode
        role = $Role
    }
}

function Invoke-ExactSourceApplicationBuild
{
    param([Parameter(Mandatory = $true)][string]$OutputTimestamp)
    $maven = (Get-Command mvn -ErrorAction Stop).Source
    $arguments = @(
        '-f', 'backend/pom.xml', '-pl', 'nq-app', '-am',
        'clean', 'package', 'spring-boot:repackage',
        '-DskipTests', "-Dproject.build.outputTimestamp=$OutputTimestamp"
    )
    & $maven @arguments
    if ($LASTEXITCODE -ne 0)
    {
        throw 'FAIL / RELEASE_APPLICATION_BUILD_FAILED'
    }
    if (@(& git -C $repo status --porcelain=v1).Count -ne 0)
    {
        throw 'BLOCKED / RELEASE_BUILD_MODIFIED_SOURCE_TREE'
    }
    $target = Join-Path $repo 'backend/nq-app/target'
    $jars = @(Get-ChildItem -LiteralPath $target -File -Filter 'nq-app-*.jar' |
        Where-Object { $_.Name -notlike '*.original' -and $_.Name -notlike '*-sources.jar' -and
                $_.Name -notlike '*-javadoc.jar' })
    if ($jars.Count -ne 1 -or ($jars[0].Attributes -band [IO.FileAttributes]::ReparsePoint))
    {
        throw 'BLOCKED / RELEASE_APPLICATION_ARTIFACT_AMBIGUOUS'
    }
    Assert-ApplicationJarContract $jars[0].FullName
    return $jars[0].FullName
}

function Get-StreamSha256
{
    param([Parameter(Mandatory = $true)][IO.Stream]$Stream)
    $sha = [Security.Cryptography.SHA256]::Create()
    try
    {
        return [BitConverter]::ToString($sha.ComputeHash($Stream)).Replace('-', '').ToLowerInvariant()
    }
    finally
    {
        $sha.Dispose()
    }
}

function Assert-ApplicationJarContract
{
    param([Parameter(Mandatory = $true)][string]$Path)
    Add-Type -AssemblyName System.IO.Compression
    $stream = [IO.File]::OpenRead($Path)
    try
    {
        $archive = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Read, $false)
        try
        {
            foreach ($required in @(
                'BOOT-INF/classes/com/guidinglight/nexusquant/app/NexusQuantApplication.class',
                'BOOT-INF/classes/application-gatey-readonly-qualification.yml'
            ))
            {
                if ($null -eq $archive.GetEntry($required))
                {
                    throw 'BLOCKED / RELEASE_APPLICATION_CONTENT_INVALID'
                }
            }
            $inventory = Get-GateYMigrationInventory $migrationRoot
            $expected = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
            foreach ($migration in $inventory.migrations)
            {
                $entryName = 'BOOT-INF/classes/db/migration/' + [string]$migration.fileName
                $null = $expected.Add($entryName)
                $entry = $archive.GetEntry($entryName)
                if ($null -eq $entry)
                {
                    throw 'BLOCKED / RELEASE_APPLICATION_MIGRATION_MISMATCH'
                }
                $entryStream = $entry.Open()
                try
                {
                    if ((Get-StreamSha256 $entryStream) -cne [string]$migration.sha256)
                    {
                        throw 'BLOCKED / RELEASE_APPLICATION_MIGRATION_MISMATCH'
                    }
                }
                finally
                {
                    $entryStream.Dispose()
                }
            }
            $actual = @($archive.Entries | Where-Object {
                $_.FullName -match '^BOOT-INF/classes/db/migration/V[1-9][0-9]*__[A-Za-z0-9_]+\.sql$'
            })
            if ($actual.Count -ne $expected.Count -or
                    @($actual | Where-Object { -not $expected.Contains($_.FullName) }).Count -ne 0)
            {
                throw 'BLOCKED / RELEASE_APPLICATION_MIGRATION_MISMATCH'
            }
        }
        finally
        {
            $archive.Dispose()
        }
    }
    finally
    {
        $stream.Dispose()
    }
}

function New-SyntheticApplicationJar
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][bool]$TamperFirstMigration
    )
    Add-Type -AssemblyName System.IO.Compression
    $stream = [IO.FileStream]::new($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try
    {
        $archive = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create, $false)
        try
        {
            $sources = [ordered]@{
                'BOOT-INF/classes/com/guidinglight/nexusquant/app/NexusQuantApplication.class' = $null
                'BOOT-INF/classes/application-gatey-readonly-qualification.yml' = $profilePath
            }
            foreach ($migration in (Get-GateYMigrationInventory $migrationRoot).migrations)
            {
                $sources['BOOT-INF/classes/db/migration/' + [string]$migration.fileName] =
                    Join-Path $migrationRoot ([string]$migration.fileName)
            }
            $migrationIndex = 0
            foreach ($entryName in $sources.Keys)
            {
                $entry = $archive.CreateEntry($entryName, [IO.Compression.CompressionLevel]::NoCompression)
                $output = $entry.Open()
                try
                {
                    $source = $sources[$entryName]
                    if ($null -eq $source)
                    {
                        $bytes = [Text.Encoding]::ASCII.GetBytes('synthetic-class')
                        $output.Write($bytes, 0, $bytes.Length)
                    }
                    else
                    {
                        $input = [IO.File]::OpenRead([string]$source)
                        try { $input.CopyTo($output) } finally { $input.Dispose() }
                        if ($TamperFirstMigration -and $entryName -match '/db/migration/' -and $migrationIndex -eq 0)
                        {
                            $tamper = [Text.Encoding]::ASCII.GetBytes('tamper')
                            $output.Write($tamper, 0, $tamper.Length)
                        }
                        if ($entryName -match '/db/migration/') { $migrationIndex++ }
                    }
                }
                finally
                {
                    $output.Dispose()
                }
            }
        }
        finally
        {
            $archive.Dispose()
        }
    }
    finally
    {
        $stream.Dispose()
    }
}

function Invoke-BuilderContractSelfTest
{
    $tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('nq-gatey-builder-selftest-' + [Guid]::NewGuid().ToString('N'))
    try
    {
        [IO.Directory]::CreateDirectory($tempRoot) | Out-Null
        $valid = Join-Path $tempRoot 'valid.jar'
        $tampered = Join-Path $tempRoot 'tampered.jar'
        New-SyntheticApplicationJar $valid $false
        Assert-ApplicationJarContract $valid
        New-SyntheticApplicationJar $tampered $true
        try
        {
            Assert-ApplicationJarContract $tampered
            throw 'FAIL / BUILDER_SELF_TEST_ACCEPTED_TAMPER'
        }
        catch
        {
            if ($_.Exception.Message -cne 'BLOCKED / RELEASE_APPLICATION_MIGRATION_MISMATCH') { throw }
        }
        return [pscustomobject][ordered]@{
            decision = 'PASS / GATEY_READONLY_RELEASE_BUILDER_SELF_TEST'
            migrationCount = @((Get-GateYMigrationInventory $migrationRoot).migrations).Count
            validJarAccepted = $true
            tamperedMigrationRejected = $true
            serverMutation = $false
        }
    }
    finally
    {
        if (Test-Path -LiteralPath $tempRoot)
        {
            $resolved = [IO.Path]::GetFullPath($tempRoot)
            $tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
            if (-not $resolved.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase))
            {
                throw 'FAIL / BUILDER_SELF_TEST_CLEANUP_PATH_INVALID'
            }
            Remove-Item -LiteralPath $resolved -Recurse -Force
        }
    }
}

try
{
    if ($ContractSelfTest)
    {
        Invoke-BuilderContractSelfTest | ConvertTo-Json -Depth 5
        exit 0
    }
    $head = Assert-ExactCleanCommit
    $javaOutput = @(& java -version 2>&1)
    $javaExitCode = [int]$LASTEXITCODE
    $javaLine = [string]($javaOutput | Select-Object -First 1)
    if ($javaLine -notmatch 'version "([0-9]+)(?:\.|\")')
    {
        throw 'BLOCKED / JAVA_MAJOR_VERSION_MISMATCH'
    }
    $javaMajor = [int]$Matches[1]
    if ($javaExitCode -ne 0 -or $javaMajor -ne 21)
    {
        throw 'BLOCKED / JAVA_MAJOR_VERSION_MISMATCH'
    }
    $commitTimestamp = (& git -C $repo show -s --format=%cI $head).Trim()
    $commitTimestamp = ([DateTimeOffset]::Parse(
        $commitTimestamp,
        [Globalization.CultureInfo]::InvariantCulture,
        [Globalization.DateTimeStyles]::RoundtripKind
    )).ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
    $applicationJar = Invoke-ExactSourceApplicationBuild $commitTimestamp
    $root = if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
        Join-Path $repo 'target/gatey-readonly-releases'
    } else { [IO.Path]::GetFullPath($OutputRoot) }
    [IO.Directory]::CreateDirectory($root) | Out-Null
    $releaseRoot = Join-Path $root $head
    if (Test-Path -LiteralPath $releaseRoot)
    {
        throw 'BLOCKED / RELEASE_OUTPUT_ALREADY_EXISTS'
    }
    $stage = Join-Path $root ('.build-' + $head + '-' + [Guid]::NewGuid().ToString('N'))
    try
    {
        [IO.Directory]::CreateDirectory($stage) | Out-Null
        $artifacts = @(
            Copy-Artifact $applicationJar $stage 'app/nq-app.jar' '0644' 'application'
            Copy-Artifact $profilePath $stage 'config/application-gatey-readonly-qualification.yml' '0644' 'runtime-profile'
            Copy-Artifact $contractPath $stage 'bin/gatey-readonly-release-contract.psm1' '0644' 'release-contract'
            Copy-Artifact $deploymentPath $stage 'bin/invoke-gatey-readonly-deployment-contract.ps1' '0755' 'deployment-contract'
            Copy-Artifact $installerPath $stage 'bin/install-gatey-readonly-release.ps1' '0755' 'release-installer'
        )
        $manifest = New-GateYReadonlyReleaseManifest $head $commitTimestamp $artifacts $migrationRoot
        Write-GateYReadonlyCanonicalManifest (Join-Path $stage 'release-manifest.json') $manifest
        Move-Item -LiteralPath $stage -Destination $releaseRoot
        $stage = $null
        $verified = Test-GateYReadonlyRelease $releaseRoot
        [pscustomobject][ordered]@{
            decision = 'PASS / GATEY_READONLY_RELEASE_BUILT_VERIFIED'
            contractState = 'BUILT_VERIFIED'
            releaseId = $verified.releaseId
            releaseRoot = $releaseRoot
            manifestSha256 = $verified.manifestSha256
            artifactCount = $verified.artifactCount
            schemaTarget = $verified.schemaTarget
            deployable = $false
            installationRequired = $true
            serverMutation = $false
        } | ConvertTo-Json -Depth 5
    }
    finally
    {
        if ($null -ne $stage -and (Test-Path -LiteralPath $stage))
        {
            $resolved = [IO.Path]::GetFullPath($stage)
            if (-not $resolved.StartsWith([IO.Path]::GetFullPath($root) + [IO.Path]::DirectorySeparatorChar,
                    [StringComparison]::OrdinalIgnoreCase))
            {
                throw 'FAIL / RELEASE_STAGE_CLEANUP_PATH_INVALID'
            }
            Remove-Item -LiteralPath $resolved -Recurse -Force
        }
    }
}
catch
{
    $decision = if ($_.Exception.Message -match '^(BLOCKED|FAIL) / [A-Z0-9_]+$') {
        $_.Exception.Message
    } else { 'FAIL / GATEY_READONLY_RELEASE_BUILD_INTERNAL_ERROR' }
    [pscustomobject]@{ decision = $decision } | ConvertTo-Json
    exit 2
}
