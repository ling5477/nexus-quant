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
$runtimeDeploymentPath = Join-Path $PSScriptRoot 'invoke-gatey-readonly-runtime-deployment.ps1'
$exactPilotControlPath = Join-Path $PSScriptRoot 'invoke-gatey-exact-pilot-scope.ps1'
$profilePath = Join-Path $repo 'backend/nq-app/src/main/resources/application-gatey-readonly-qualification.yml'
$systemdUnitPath = Join-Path $repo 'deploy/systemd/nq-gatey-readonly-qualification.service'
$runtimeEnvTemplatePath = Join-Path $repo 'deploy/gatey/gatey-readonly-runtime.env.example'
$runtimeSecretsTemplatePath = Join-Path $repo 'deploy/gatey/gatey-readonly-runtime.secrets.env.example'
$runtimePgpassTemplatePath = Join-Path $repo 'deploy/gatey/gatey-readonly-db.pgpass.example'
$runtimeTargetPath = Join-Path $repo 'deploy/gatey/gatey-readonly-runtime-target.json'
$gatewVerifierPath = Join-Path $repo 'scripts/gatew/verify-gatew-release.ps1'
$gatewContractPath = Join-Path $repo 'scripts/gatew/gatew-release-contract.psm1'
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
    $mavenOutput = @(& $maven @arguments)
    $mavenExitCode = [int]$LASTEXITCODE
    if ($mavenExitCode -ne 0)
    {
        $mavenOutput | ForEach-Object { Write-Verbose ([string]$_) }
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
            $infraEntries = @($archive.Entries | Where-Object {
                $_.FullName -match '^BOOT-INF/lib/nq-infra-[A-Za-z0-9._-]+\.jar$'
            })
            if ($infraEntries.Count -ne 1)
            {
                throw 'BLOCKED / RELEASE_APPLICATION_MIGRATION_MISMATCH'
            }
            $infraEntryStream = $infraEntries[0].Open()
            $infraMemory = [IO.MemoryStream]::new()
            try
            {
                $infraEntryStream.CopyTo($infraMemory)
                $infraMemory.Position = 0
                $infraArchive = [IO.Compression.ZipArchive]::new(
                    $infraMemory, [IO.Compression.ZipArchiveMode]::Read, $true
                )
                try
                {
                    $expected = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
                    foreach ($migration in $inventory.migrations)
                    {
                        $entryName = 'db/migration/' + [string]$migration.fileName
                        $null = $expected.Add($entryName)
                        $entry = $infraArchive.GetEntry($entryName)
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
                    $actual = @($infraArchive.Entries | Where-Object {
                        $_.FullName -match '^db/migration/V[1-9][0-9]*__[A-Za-z0-9_]+\.sql$'
                    })
                    if ($actual.Count -ne $expected.Count -or
                            @($actual | Where-Object {
                                -not $expected.Contains($_.FullName)
                            }).Count -ne 0)
                    {
                        throw 'BLOCKED / RELEASE_APPLICATION_MIGRATION_MISMATCH'
                    }
                }
                finally
                {
                    $infraArchive.Dispose()
                }
            }
            finally
            {
                $infraEntryStream.Dispose()
                $infraMemory.Dispose()
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
            $classEntry = $archive.CreateEntry(
                'BOOT-INF/classes/com/guidinglight/nexusquant/app/NexusQuantApplication.class',
                [IO.Compression.CompressionLevel]::NoCompression
            )
            $classOutput = $classEntry.Open()
            try
            {
                $bytes = [Text.Encoding]::ASCII.GetBytes('synthetic-class')
                $classOutput.Write($bytes, 0, $bytes.Length)
            }
            finally
            {
                $classOutput.Dispose()
            }
            $profileEntry = $archive.CreateEntry(
                'BOOT-INF/classes/application-gatey-readonly-qualification.yml',
                [IO.Compression.CompressionLevel]::NoCompression
            )
            $profileOutput = $profileEntry.Open()
            $profileInput = [IO.File]::OpenRead($profilePath)
            try
            {
                $profileInput.CopyTo($profileOutput)
            }
            finally
            {
                $profileInput.Dispose()
                $profileOutput.Dispose()
            }

            $infraMemory = [IO.MemoryStream]::new()
            try
            {
                $infraArchive = [IO.Compression.ZipArchive]::new(
                    $infraMemory, [IO.Compression.ZipArchiveMode]::Create, $true
                )
                try
                {
                    $migrationIndex = 0
                    foreach ($migration in (Get-GateYMigrationInventory $migrationRoot).migrations)
                    {
                        $entry = $infraArchive.CreateEntry(
                            ('db/migration/' + [string]$migration.fileName),
                            [IO.Compression.CompressionLevel]::NoCompression
                        )
                        $output = $entry.Open()
                        $input = [IO.File]::OpenRead(
                            (Join-Path $migrationRoot ([string]$migration.fileName))
                        )
                        try
                        {
                            $input.CopyTo($output)
                            if ($TamperFirstMigration -and $migrationIndex -eq 0)
                            {
                                $tamper = [Text.Encoding]::ASCII.GetBytes('tamper')
                                $output.Write($tamper, 0, $tamper.Length)
                            }
                        }
                        finally
                        {
                            $input.Dispose()
                            $output.Dispose()
                        }
                        $migrationIndex++
                    }
                }
                finally
                {
                    $infraArchive.Dispose()
                }
                $infraMemory.Position = 0
                $infraEntry = $archive.CreateEntry(
                    'BOOT-INF/lib/nq-infra-0.1.0-SNAPSHOT.jar',
                    [IO.Compression.CompressionLevel]::NoCompression
                )
                $infraOutput = $infraEntry.Open()
                try { $infraMemory.CopyTo($infraOutput) } finally { $infraOutput.Dispose() }
            }
            finally
            {
                $infraMemory.Dispose()
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
            Copy-Artifact $runtimeDeploymentPath $stage 'bin/invoke-gatey-readonly-runtime-deployment.ps1' '0755' 'runtime-deployment-orchestrator'
            Copy-Artifact $exactPilotControlPath $stage 'bin/invoke-gatey-exact-pilot-scope.ps1' '0755' 'exact-pilot-control-surface'
            Copy-Artifact $systemdUnitPath $stage 'config/nq-gatey-readonly-qualification.service' '0644' 'systemd-runtime-contract'
            Copy-Artifact $runtimeEnvTemplatePath $stage 'config/gatey-readonly-runtime.env.example' '0644' 'runtime-environment-template'
            Copy-Artifact $runtimeSecretsTemplatePath $stage 'config/gatey-readonly-runtime.secrets.env.example' '0600' 'runtime-secret-environment-template'
            Copy-Artifact $runtimePgpassTemplatePath $stage 'config/gatey-readonly-db.pgpass.example' '0600' 'database-credential-reference-template'
            Copy-Artifact $runtimeTargetPath $stage 'config/gatey-readonly-runtime-target.json' '0644' 'runtime-target-contract'
            Copy-Artifact $gatewVerifierPath $stage 'bin/verify-gatew-release.ps1' '0755' 'gatew-rollback-verifier'
            Copy-Artifact $gatewContractPath $stage 'bin/gatew-release-contract.psm1' '0644' 'gatew-rollback-contract'
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
