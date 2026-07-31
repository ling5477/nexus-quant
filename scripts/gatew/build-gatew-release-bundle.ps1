[CmdletBinding()]
param(
    [ValidateSet('build', 'self-test')]
    [string]$Action = 'build',

    [ValidateSet('CANDIDATE', 'EXACT_COMMIT')]
    [string]$SourceTreeMode = 'CANDIDATE',

    [string]$ExpectedCommit,
    [string]$OutputRoot,

    [switch]$DetachedWorktreeBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:Utf8NoBom = [Text.UTF8Encoding]::new($false)
$script:RepoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$script:BackendPom = Join-Path $script:RepoRoot 'backend/pom.xml'
$script:ArtifactRecords = [System.Collections.ArrayList]::new()
$script:ReleaseContractPath = Join-Path $PSScriptRoot 'gatew-release-contract.psm1'
$script:RequiredJavaMajor = 21
$script:MavenCommand = 'mvn --offline --quiet -f backend/pom.xml -pl nq-app -am -DskipTests clean package'

Import-Module $script:ReleaseContractPath -Force -DisableNameChecking

function Invoke-Native
{
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Arguments
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try
    {
        $ErrorActionPreference = 'SilentlyContinue'
        $output = @(& $FilePath @Arguments 2>&1)
        $exitCode = [int]$LASTEXITCODE
    }
    finally
    {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0)
    {
        throw 'FAIL / RELEASE_BUILD_COMMAND_FAILED'
    }
    return @($output)
}

function Get-Sha256File
{
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-Sha256Text
{
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text)

    $bytes = $script:Utf8NoBom.GetBytes($Text)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try
    {
        return -join ($algorithm.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') })
    }
    finally
    {
        $algorithm.Dispose()
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Get-GitOutput
{
    param([Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Arguments)

    $lines = @(& git -C $script:RepoRoot @Arguments 2> $null)
    if ($LASTEXITCODE -ne 0)
    {
        throw 'BLOCKED / RELEASE_SOURCE_GIT_UNAVAILABLE'
    }
    return @($lines)
}

function Get-HeadCommit
{
    $value = (($( (Get-GitOutput @('rev-parse', 'HEAD')) ) -join "`n").Trim()).ToLowerInvariant()
    if ($value -cnotmatch '^[a-f0-9]{40}$')
    {
        throw 'BLOCKED / RELEASE_SOURCE_COMMIT_INVALID'
    }
    return $value
}

function Get-SourceCommitTimestamp
{
    param([Parameter(Mandatory = $true)][string]$Commit)

    $value = (($( (Get-GitOutput @('show', '-s', '--format=%ct', $Commit)) ) -join "`n").Trim())
    $epochSeconds = [long]0
    if (-not [long]::TryParse(
            $value,
            [Globalization.NumberStyles]::None,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$epochSeconds
    ) -or $epochSeconds -lt 0)
    {
        throw 'BLOCKED / RELEASE_SOURCE_TIMESTAMP_INVALID'
    }
    $epoch = [DateTimeOffset]::new(1970, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
    return $epoch.AddSeconds($epochSeconds).ToUniversalTime().ToString(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            [Globalization.CultureInfo]::InvariantCulture
    )
}

function Assert-ExactCommitSource
{
    param([Parameter(Mandatory = $true)][string]$Commit)

    $status = @(Get-GitOutput @('status', '--porcelain', '--untracked-files=all'))
    if ($status.Count -ne 0)
    {
        throw 'BLOCKED / EXACT_COMMIT_WORKTREE_NOT_CLEAN'
    }
    Assert-ExpectedSourceCommit $Commit $ExpectedCommit
}

function Assert-ExpectedSourceCommit
{
    param(
        [Parameter(Mandatory = $true)][string]$ActualCommit,
        [AllowEmptyString()][string]$Expected
    )

    if (-not [string]::IsNullOrWhiteSpace($Expected) -and
            $ActualCommit -cne $Expected.ToLowerInvariant())
    {
        throw 'BLOCKED / RELEASE_SOURCE_COMMIT_MISMATCH'
    }
}

function Assert-NoPreexistingBuildTargets
{
    param([Parameter(Mandatory = $true)][string]$BackendRoot)

    if (@(Get-ChildItem -LiteralPath $BackendRoot -Directory -Recurse -Filter target).Count -ne 0)
    {
        throw 'BLOCKED / RELEASE_BUILD_TARGET_PREEXISTS'
    }
}

function Get-BuildOutputDescriptor
{
    param([Parameter(Mandatory = $true)][string]$BackendRoot)

    $resolvedRoot = [IO.Path]::GetFullPath($BackendRoot).TrimEnd(
            [IO.Path]::DirectorySeparatorChar,
            [IO.Path]::AltDirectorySeparatorChar
    )
    $filesByRelativePath = @{ }
    foreach ($targetDirectory in @(
        Get-ChildItem -LiteralPath $resolvedRoot -Directory -Recurse -Filter target
    ))
    {
        foreach ($file in @(Get-ChildItem -LiteralPath $targetDirectory.FullName -File -Recurse))
        {
            $relativePath = $file.FullName.Substring($resolvedRoot.Length).
                    TrimStart('/', '\').Replace('\', '/')
            if ($filesByRelativePath.ContainsKey($relativePath))
            {
                throw 'FAIL / RELEASE_BUILD_OUTPUT_MUTATED'
            }
            $filesByRelativePath[$relativePath] = $file
        }
    }
    if ($filesByRelativePath.Count -eq 0)
    {
        throw 'BLOCKED / RELEASE_LAUNCHER_CLASSES_MISSING'
    }
    $descriptor = [Text.StringBuilder]::new()
    foreach ($relativePath in @(Sort-GateWOrdinalStrings @($filesByRelativePath.Keys)))
    {
        $file = $filesByRelativePath[$relativePath]
        [void]$descriptor.Append($relativePath)
        [void]$descriptor.Append('|')
        [void]$descriptor.Append([long]$file.Length)
        [void]$descriptor.Append('|')
        [void]$descriptor.Append((Get-Sha256File $file.FullName))
        [void]$descriptor.Append("`n")
    }
    return Get-Sha256Text $descriptor.ToString()
}

function Assert-BuildOutputUnchanged
{
    param(
        [Parameter(Mandatory = $true)][string]$BackendRoot,
        [Parameter(Mandatory = $true)][string]$ExpectedDescriptor
    )

    if ((Get-BuildOutputDescriptor $BackendRoot) -cne $ExpectedDescriptor)
    {
        throw 'FAIL / RELEASE_BUILD_OUTPUT_MUTATED'
    }
}

function Get-CandidateDiffSha256
{
    $targetRoot = Join-Path $script:RepoRoot 'target/gatew-release-builder'
    [IO.Directory]::CreateDirectory($targetRoot) | Out-Null
    $trackedDiffPath = Join-Path $targetRoot ('.candidate-diff-' + [Guid]::NewGuid().ToString('N'))
    try
    {
        $previousErrorActionPreference = $ErrorActionPreference
        try
        {
            $ErrorActionPreference = 'SilentlyContinue'
            $null = & git -C $script:RepoRoot diff --binary --no-ext-diff HEAD `
                "--output=$trackedDiffPath" -- . 2> $null
            $diffExitCode = [int]$LASTEXITCODE
        }
        finally
        {
            $ErrorActionPreference = $previousErrorActionPreference
        }
        if ($diffExitCode -ne 0 -or -not (Test-Path -LiteralPath $trackedDiffPath -PathType Leaf))
        {
            throw 'BLOCKED / CANDIDATE_DIFF_UNAVAILABLE'
        }
        $descriptor = [Text.StringBuilder]::new()
        [void]$descriptor.Append('trackedDiffSha256=')
        [void]$descriptor.Append((Get-Sha256File $trackedDiffPath))
        [void]$descriptor.Append("`n")
        $untracked = @(Get-GitOutput @('ls-files', '--others', '--exclude-standard', '--', '.'))
        foreach ($relative in @(Sort-GateWOrdinalStrings $untracked))
        {
            $normalized = ([string]$relative).Replace('\', '/')
            if ($normalized -match '^(?:target|build|dist|node_modules|test-results|logs|secrets|credentials)/')
            {
                continue
            }
            $path = [IO.Path]::GetFullPath((Join-Path $script:RepoRoot $normalized))
            if (-not $path.StartsWith($script:RepoRoot + [IO.Path]::DirectorySeparatorChar,
                    [StringComparison]::OrdinalIgnoreCase) -or
                    -not (Test-Path -LiteralPath $path -PathType Leaf))
            {
                throw 'BLOCKED / CANDIDATE_DIFF_UNAVAILABLE'
            }
            $item = Get-Item -LiteralPath $path
            [void]$descriptor.Append('untracked=')
            [void]$descriptor.Append($normalized)
            [void]$descriptor.Append('|')
            [void]$descriptor.Append([long]$item.Length)
            [void]$descriptor.Append('|')
            [void]$descriptor.Append((Get-Sha256File $path))
            [void]$descriptor.Append("`n")
        }
        return Get-Sha256Text $descriptor.ToString()
    }
    finally
    {
        if (Test-Path -LiteralPath $trackedDiffPath)
        {
            Remove-Item -LiteralPath $trackedDiffPath -Force
        }
    }
}

function Write-LfText
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text
    )

    $parent = Split-Path -Parent $Path
    [IO.Directory]::CreateDirectory($parent) | Out-Null
    $normalized = $Text -replace "`r`n|`r", "`n"
    [IO.File]::WriteAllText($Path, $normalized, $script:Utf8NoBom)
}

function Copy-LfText
{
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    if (-not (Test-Path -LiteralPath $Source -PathType Leaf))
    {
        throw 'BLOCKED / RELEASE_BUILD_INPUT_MISSING'
    }
    Write-LfText $Destination ([IO.File]::ReadAllText($Source))
}

function Copy-BinaryFile
{
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    if (-not (Test-Path -LiteralPath $Source -PathType Leaf))
    {
        throw 'BLOCKED / RELEASE_BUILD_INPUT_MISSING'
    }
    [IO.Directory]::CreateDirectory((Split-Path -Parent $Destination)) | Out-Null
    [IO.File]::Copy($Source, $Destination, $false)
}

function Add-ArtifactRecord
{
    param(
        [Parameter(Mandatory = $true)][string]$StageRoot,
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][string]$Mode,
        [Parameter(Mandatory = $true)][ValidateSet('LF', 'BINARY')][string]$LineEndingPolicy,
        [Parameter(Mandatory = $true)][bool]$Entrypoint,
        [Parameter(Mandatory = $true)][string]$Role
    )

    $normalized = $RelativePath.Replace('\', '/')
    $path = Join-Path $StageRoot $normalized
    $item = Get-Item -LiteralPath $path -Force
    [void]$script:ArtifactRecords.Add([pscustomobject][ordered]@{
        relativePath = $normalized
        size = [long]$item.Length
        sha256 = Get-Sha256File $path
        mode = $Mode
        lineEndingPolicy = $LineEndingPolicy
        entrypoint = $Entrypoint
        role = $Role
    })
}

function New-DeterministicJar
{
    param(
        [Parameter(Mandatory = $true)][string]$SourceDirectory,
        [Parameter(Mandatory = $true)][string]$Destination,
        [string[]]$IncludeNamePatterns = @('*')
    )

    $fileRecords = @(
    Get-ChildItem -LiteralPath $SourceDirectory -File -Recurse |
            Where-Object {
                $name = $_.Name
                @($IncludeNamePatterns | Where-Object { $name -like $_ }).Count -gt 0
            } |
            ForEach-Object {
                [pscustomobject]@{
                    RelativePath = $_.FullName.Substring($SourceDirectory.Length).
                            TrimStart('/', '\').Replace('\', '/')
                    File = $_
                }
            }
    )
    if ($fileRecords.Count -eq 0)
    {
        throw 'BLOCKED / RELEASE_LAUNCHER_CLASSES_MISSING'
    }
    $relativePaths = @(Sort-GateWOrdinalStrings @($fileRecords.RelativePath))
    New-GateWCanonicalZip $SourceDirectory $Destination $relativePaths
}

function Get-BackendModules
{
    [xml]$rootPom = Get-Content -LiteralPath $script:BackendPom -Raw
    $modules = @($rootPom.project.modules.module | ForEach-Object { [string]$_ })
    if ($modules.Count -lt 1)
    {
        throw 'BLOCKED / RELEASE_MODULE_LIST_INVALID'
    }
    $records = @()
    foreach ($module in $modules)
    {
        $moduleRoot = Join-Path (Split-Path -Parent $script:BackendPom) $module
        $pomPath = Join-Path $moduleRoot 'pom.xml'
        if (-not (Test-Path -LiteralPath $pomPath -PathType Leaf))
        {
            throw 'BLOCKED / RELEASE_MODULE_LIST_INVALID'
        }
        [xml]$modulePom = Get-Content -LiteralPath $pomPath -Raw
        $artifactId = [string]$modulePom.project.artifactId
        if ($artifactId -notmatch '^[A-Za-z0-9_.-]+$')
        {
            throw 'BLOCKED / RELEASE_MODULE_LIST_INVALID'
        }
        $records += [pscustomobject]@{
            Name = $module
            ArtifactId = $artifactId
            Root = $moduleRoot
            Classes = Join-Path $moduleRoot 'target/classes'
        }
    }
    return @($records)
}

function Build-LauncherArtifacts
{
    param([Parameter(Mandatory = $true)][string]$StageRoot)

    $backendRoot = Split-Path -Parent $script:BackendPom
    $maven = (Get-Command mvn -ErrorAction Stop).Source
    Invoke-Native $maven @(
        '--offline', '--quiet', '-f', $script:BackendPom, '-pl', 'nq-app', '-am',
        '-DskipTests', 'clean', 'package'
    ) | Out-Null
    $classpathPath = Join-Path $script:RepoRoot 'backend/nq-app/target/gatew-release-classpath.txt'
    if (Test-Path -LiteralPath $classpathPath)
    {
        Remove-Item -LiteralPath $classpathPath -Force
    }
    Invoke-Native $maven @(
        '--offline', '--quiet', '-f', $script:BackendPom, '-pl', 'nq-app',
        '-DincludeScope=test', '-Dmdep.outputAbsoluteArtifactFilename=true',
        "-Dmdep.outputFile=$classpathPath", 'dependency:build-classpath'
    ) | Out-Null
    if (-not (Test-Path -LiteralPath $classpathPath -PathType Leaf))
    {
        throw 'FAIL / RELEASE_CLASSPATH_BUILD_FAILED'
    }
    $buildOutputDescriptor = Get-BuildOutputDescriptor $backendRoot

    $modules = @(Get-BackendModules)
    $moduleIndex = 0
    foreach ($module in $modules)
    {
        if (-not (Test-Path -LiteralPath $module.Classes -PathType Container) -or
                @(Get-ChildItem -LiteralPath $module.Classes -File -Recurse).Count -eq 0)
        {
            continue
        }
        $relative = 'launcher/modules/{0:D4}-{1}.jar' -f $moduleIndex, $module.ArtifactId
        $destination = Join-Path $StageRoot $relative
        New-DeterministicJar $module.Classes $destination
        Add-ArtifactRecord $StageRoot $relative '0644' 'BINARY' $false 'launcher-module'
        $moduleIndex++
    }
    if ($moduleIndex -lt 3)
    {
        throw 'BLOCKED / RELEASE_LAUNCHER_CLASSES_MISSING'
    }

    $testClasses = Join-Path $script:RepoRoot 'backend/nq-app/target/test-classes'
    $testSupportRelative = 'launcher/test-support.jar'
    New-DeterministicJar $testClasses (Join-Path $StageRoot $testSupportRelative) @(
        'GateWOkxReadonlySoakCycleTest*.class', 'GateWOkxReadonlySoakFailCloseTest*.class'
    )
    Add-ArtifactRecord $StageRoot $testSupportRelative '0644' 'BINARY' $false 'launcher-test-support'

    $classpathCandidates = @(
    ((Get-Content -LiteralPath $classpathPath -Raw).Trim() -split
            [regex]::Escape([IO.Path]::PathSeparator)) |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
    $classpathEntries = @()
    foreach ($entry in @(Sort-GateWOrdinalStrings $classpathCandidates))
    {
        if ($classpathEntries.Count -eq 0 -or $classpathEntries[-1] -cne $entry)
        {
            $classpathEntries += $entry
        }
    }
    $libraryIndex = 0
    foreach ($entry in $classpathEntries)
    {
        $source = [IO.Path]::GetFullPath([string]$entry)
        if ([IO.Path]::GetExtension($source) -cne '.jar' -or
                -not (Test-Path -LiteralPath $source -PathType Leaf))
        {
            throw 'BLOCKED / RELEASE_RUNTIME_LIBRARY_INVALID'
        }
        $fileName = [IO.Path]::GetFileName($source)
        $isProjectArtifact = @(
        $modules | Where-Object { $fileName -like "$( $_.ArtifactId )-*.jar" }
        ).Count -gt 0
        if ($isProjectArtifact)
        {
            continue
        }
        $sha256 = Get-Sha256File $source
        $safeName = $fileName -replace '[^A-Za-z0-9_.-]', '_'
        $relative = 'launcher/lib/{0:D4}-{1}-{2}' -f $libraryIndex,$sha256.Substring(0, 16), $safeName
        Copy-BinaryFile $source (Join-Path $StageRoot $relative)
        Add-ArtifactRecord $StageRoot $relative '0644' 'BINARY' $false 'runtime-library'
        $libraryIndex++
    }
    if ($libraryIndex -lt 1)
    {
        throw 'BLOCKED / RELEASE_RUNTIME_LIBRARY_INVALID'
    }
    Assert-BuildOutputUnchanged $backendRoot $buildOutputDescriptor
}

function Add-TextArtifact
{
    param(
        [Parameter(Mandatory = $true)][string]$StageRoot,
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][string]$Mode,
        [Parameter(Mandatory = $true)][bool]$Entrypoint,
        [Parameter(Mandatory = $true)][string]$Role
    )

    Copy-LfText $Source (Join-Path $StageRoot $RelativePath)
    Add-ArtifactRecord $StageRoot $RelativePath $Mode 'LF' $Entrypoint $Role
}

function Get-ReleaseExecutionRoot
{
    param(
        [Parameter(Mandatory = $true)][ValidateSet('CANDIDATE', 'EXACT_COMMIT')][string]$Mode,
        [Parameter(Mandatory = $true)][string]$ReleaseId
    )

    if ($Mode -eq 'CANDIDATE')
    {
        return "/opt/nexus-quant/releases/$ReleaseId"
    }
    return '/opt/nexus-quant/current'
}

function Get-ReleaseBoundUnitText
{
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$ReleaseExecutionRoot
    )

    if (-not (Test-Path -LiteralPath $Source -PathType Leaf))
    {
        throw 'BLOCKED / RELEASE_BUILD_INPUT_MISSING'
    }
    $text = [IO.File]::ReadAllText($Source)
    if (-not $text.Contains('/opt/nexus-quant/current'))
    {
        throw 'BLOCKED / RELEASE_UNIT_BINDING_SOURCE_INVALID'
    }
    return $text.Replace('/opt/nexus-quant/current', $ReleaseExecutionRoot)
}

function Add-ReleaseBoundUnitArtifact
{
    param(
        [Parameter(Mandatory = $true)][string]$StageRoot,
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][string]$Role,
        [Parameter(Mandatory = $true)][string]$ReleaseExecutionRoot
    )

    $destination = Join-Path $StageRoot $RelativePath
    Write-LfText $destination (Get-ReleaseBoundUnitText $Source $ReleaseExecutionRoot)
    Add-ArtifactRecord $StageRoot $RelativePath '0644' 'LF' $false $Role
}

function Build-ReleaseBundle
{
    $head = Get-HeadCommit
    $baseCommit = $null
    $candidateDiffSha256 = $null
    $releaseId = $head
    if ($SourceTreeMode -eq 'EXACT_COMMIT')
    {
        if (-not $DetachedWorktreeBuild)
        {
            Assert-ExactCommitSource $head
            return Invoke-ExactCommitDetachedBuild $head
        }
        Assert-DetachedExactCommitSource $head
    }
    else
    {
        if ($DetachedWorktreeBuild)
        {
            throw 'BLOCKED / RELEASE_DETACHED_BUILD_MODE_INVALID'
        }
        $baseCommit = $head
        $candidateDiffSha256 = Get-CandidateDiffSha256
        $releaseId = 'candidate-{0}-{1}' -f $head.Substring(0, 12),
        $candidateDiffSha256.Substring(0, 16)
    }

    $effectiveOutputRoot = if ( [string]::IsNullOrWhiteSpace($OutputRoot))
    {
        Join-Path $script:RepoRoot 'target/gatew-release-bundles'
    }
    else
    {
        [IO.Path]::GetFullPath($OutputRoot)
    }
    [IO.Directory]::CreateDirectory($effectiveOutputRoot) | Out-Null
    $destination = Join-Path $effectiveOutputRoot $releaseId
    $bundlePath = Join-Path $effectiveOutputRoot ($releaseId + '.tar')
    if ((Test-Path -LiteralPath $destination) -or (Test-Path -LiteralPath $bundlePath))
    {
        throw 'BLOCKED / RELEASE_OUTPUT_ALREADY_EXISTS'
    }
    $stageRoot = Join-Path $effectiveOutputRoot ('.build-' + $releaseId + '-' + [Guid]::NewGuid().ToString('N'))
    $script:ArtifactRecords.Clear()
    try
    {
        $releaseExecutionRoot = Get-ReleaseExecutionRoot $SourceTreeMode $releaseId
        [IO.Directory]::CreateDirectory($stageRoot) | Out-Null
        Add-TextArtifact $stageRoot (Join-Path $script:RepoRoot 'scripts/gatew/gatew-okx-readonly-soak.ps1') `
            'bin/gatew-okx-readonly-soak.ps1' '0755' $true 'worker-helper'
        Add-TextArtifact $stageRoot (Join-Path $script:RepoRoot 'scripts/gatew/gatew-okx-readonly-soak-control.ps1') `
            'bin/gatew-okx-readonly-soak-control.ps1' '0755' $true 'control-helper'
        Add-TextArtifact $stageRoot (Join-Path $script:RepoRoot 'scripts/gatew/gatew-okx-readonly-soak-failclose.ps1') `
            'bin/gatew-okx-readonly-soak-failclose.ps1' '0755' $true 'failclose-helper'
        Add-TextArtifact $stageRoot (Join-Path $script:RepoRoot 'scripts/gatew/gatew-soak-remediation-contract.psm1') `
            'bin/gatew-soak-remediation-contract.psm1' '0644' $false 'contract-library'
        Add-TextArtifact $stageRoot $script:ReleaseContractPath `
            'bin/gatew-release-contract.psm1' '0644' $false 'release-contract'
        Add-TextArtifact $stageRoot (Join-Path $script:RepoRoot 'scripts/gatew/verify-gatew-release.ps1') `
            'bin/verify-gatew-release.ps1' '0755' $true 'release-verifier'
        Add-TextArtifact $stageRoot (Join-Path $script:RepoRoot 'scripts/gatew/install-gatew-release.ps1') `
            'bin/install-gatew-release.ps1' '0755' $true 'release-installer'
        Add-ReleaseBoundUnitArtifact $stageRoot `
            (Join-Path $script:RepoRoot 'deploy/systemd/nq-gatew-soak@.service') `
            'systemd/nq-gatew-soak@.service' 'systemd-worker-unit' $releaseExecutionRoot
        Add-ReleaseBoundUnitArtifact $stageRoot `
            (Join-Path $script:RepoRoot 'deploy/systemd/nq-gatew-soak-failclose@.service') `
            'systemd/nq-gatew-soak-failclose@.service' 'systemd-failclose-unit' $releaseExecutionRoot
        Build-LauncherArtifacts $stageRoot

        $actualJavaMajor = Get-GateWJavaRuntimeMajor
        if ($actualJavaMajor -ne $script:RequiredJavaMajor)
        {
            throw 'BLOCKED / JAVA_MAJOR_VERSION_MISMATCH'
        }
        $artifacts = @(Sort-GateWArtifactsOrdinal @($script:ArtifactRecords))
        $manifest = [ordered]@{
            schemaVersion = 'nq-gatew-release-v3'
            releaseId = $releaseId
            sourceCommit = $head
            sourceCommitTimestamp = Get-SourceCommitTimestamp $head
            sourceTreeMode = $SourceTreeMode
            baseCommit = $baseCommit
            candidateDiffSha256 = $candidateDiffSha256
            requiredRuntime = [ordered]@{
                os = 'linux'
                powershellMajor = 7
                javaMajor = $script:RequiredJavaMajor
                systemd = $true
            }
            buildProvenance = [ordered]@{
                mavenCommand = $script:MavenCommand
                javaMajor = $actualJavaMajor
                cleanDetachedWorktree = ($SourceTreeMode -eq 'EXACT_COMMIT')
            }
            lineEndingPolicy = 'LF'
            artifacts = $artifacts
        }
        Write-GateWCanonicalManifest (Join-Path $stageRoot 'release-manifest.json') $manifest
        Move-Item -LiteralPath $stageRoot -Destination $destination
        $verifyScript = Join-Path $destination 'bin/verify-gatew-release.ps1'
        $verifyOutput = @(& $verifyScript -ReleaseRoot $destination -ExpectedReleaseId $releaseId -SkipPosix)
        if ($LASTEXITCODE -ne 0)
        {
            throw 'FAIL / RELEASE_BUILD_VERIFY_FAILED'
        }
        $verification = ($verifyOutput -join "`n") | ConvertFrom-Json
        New-GateWCanonicalTar $destination $manifest $bundlePath
        $bundleSha256 = Get-Sha256File $bundlePath
        $bundleVerifyOutput = @(& $verifyScript -ReleaseRoot $destination `
            -ExpectedReleaseId $releaseId -ExpectedManifestSha256 $verification.manifestSha256 `
            -BundlePath $bundlePath -ExpectedBundleSha256 $bundleSha256 -SkipPosix)
        if ($LASTEXITCODE -ne 0)
        {
            throw 'FAIL / RELEASE_BUILD_VERIFY_FAILED'
        }
        return [pscustomobject]@{
            decision = 'PASS / IMMUTABLE_RELEASE_BUNDLE_BUILT'
            releaseId = $releaseId
            sourceCommit = $head
            sourceTreeMode = $SourceTreeMode
            baseCommit = $baseCommit
            candidateDiffSha256 = $candidateDiffSha256
            bundleRoot = $destination
            bundlePath = $bundlePath
            bundleSha256 = $bundleSha256
            manifestSha256 = [string]$verification.manifestSha256
            artifactCount = [int]$verification.artifactCount
            jarCount = [int]$verification.jarCount
            duplicateDirectoryEntries = [int]$verification.duplicateDirectoryEntries
            requiredJavaMajor = $script:RequiredJavaMajor
            actualJavaMajor = $actualJavaMajor
            mavenCommand = $script:MavenCommand
        }
    }
    finally
    {
        if (Test-Path -LiteralPath $stageRoot)
        {
            $resolvedStage = [IO.Path]::GetFullPath($stageRoot)
            if ( $resolvedStage.StartsWith(
                    [IO.Path]::GetFullPath($effectiveOutputRoot) + [IO.Path]::DirectorySeparatorChar,
                    [StringComparison]::OrdinalIgnoreCase
            ))
            {
                Remove-Item -LiteralPath $resolvedStage -Recurse -Force
            }
        }
    }
}

function Get-ExactCommitWorktreeAddArguments
{
    param(
        [Parameter(Mandatory = $true)][string]$WorktreeRoot,
        [Parameter(Mandatory = $true)][string]$Commit
    )

    return @(
        '-C', $script:RepoRoot,
        '-c', 'core.autocrlf=false',
        '-c', 'core.eol=lf',
        'worktree', 'add', '--detach', $WorktreeRoot, $Commit
    )
}

function Invoke-BuilderSelfTest
{
    $testRoot = Join-Path $script:RepoRoot 'target/gatew-release-builder/self-test'
    $testPath = Join-Path $testRoot ([Guid]::NewGuid().ToString('N') + '.txt')
    $jarSource = Join-Path $testRoot ([Guid]::NewGuid().ToString('N') + '-jar-source')
    $jarPath = Join-Path $testRoot ([Guid]::NewGuid().ToString('N') + '.jar')
    $crcSource = Join-Path $testRoot ([Guid]::NewGuid().ToString('N') + '-crc-source')
    $crcJarPath = Join-Path $testRoot ([Guid]::NewGuid().ToString('N') + '-crc.jar')
    $pollutionRoot = Join-Path $testRoot ([Guid]::NewGuid().ToString('N') + '-pollution')
    try
    {
        Write-LfText $testPath "alpha`r`nbeta`r"
        $bytes = [IO.File]::ReadAllBytes($testPath)
        if ($bytes -contains 13 -or [Text.Encoding]::UTF8.GetString($bytes) -cne "alpha`nbeta`n")
        {
            throw 'LF normalization self-test failed'
        }
        $diffSha256 = Get-CandidateDiffSha256
        if ($diffSha256 -cnotmatch '^[a-f0-9]{64}$')
        {
            throw 'candidate diff self-test failed'
        }
        Write-LfText (Join-Path $jarSource 'db/migration/V1__self_test.sql') 'SELECT 1;'
        New-DeterministicJar $jarSource $jarPath
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [IO.Compression.ZipFile]::OpenRead($jarPath)
        try
        {
            $entryNames = @($archive.Entries | ForEach-Object { $_.FullName })
            if ($entryNames -notcontains 'db/' -or
                    $entryNames -notcontains 'db/migration/' -or
                    $entryNames -notcontains 'db/migration/V1__self_test.sql')
            {
                throw 'deterministic jar directory entry self-test failed'
            }
            $contentEntry = $archive.GetEntry('db/migration/V1__self_test.sql')
            $entryStream = $contentEntry.Open()
            $entryBytes = [IO.MemoryStream]::new()
            try
            {
                $entryStream.CopyTo($entryBytes)
                if ($script:Utf8NoBom.GetString($entryBytes.ToArray()) -cne 'SELECT 1;')
                {
                    throw 'deterministic jar entry readback self-test failed'
                }
            }
            finally
            {
                $entryBytes.Dispose()
                $entryStream.Dispose()
            }
        }
        finally
        {
            $archive.Dispose()
        }
        Write-LfText (Join-Path $crcSource 'crc32-vector.txt') '123456789'
        New-DeterministicJar $crcSource $crcJarPath
        $crcJarBytes = [IO.File]::ReadAllBytes($crcJarPath)
        if ($crcJarBytes.Length -lt 18 -or
                [BitConverter]::ToUInt32($crcJarBytes, 14) -ne [uint32]3421780262)
        {
            throw 'CRC32 standard vector self-test failed'
        }
        $unitSource = Join-Path $script:RepoRoot 'deploy/systemd/nq-gatew-soak@.service'
        $candidateRoot = '/opt/nexus-quant/releases/candidate-0123456789ab-0123456789abcdef-20260719T000000Z'
        $candidateUnit = Get-ReleaseBoundUnitText $unitSource $candidateRoot
        if (-not $candidateUnit.Contains("Environment=NQ_GATEW_RELEASE_ROOT=$candidateRoot") -or
                $candidateUnit.Contains('/opt/nexus-quant/current'))
        {
            throw 'candidate unit binding self-test failed'
        }
        $exactUnit = Get-ReleaseBoundUnitText $unitSource '/opt/nexus-quant/current'
        if (-not $exactUnit.Contains('Environment=NQ_GATEW_RELEASE_ROOT=/opt/nexus-quant/current'))
        {
            throw 'exact unit binding self-test failed'
        }
        $sourceCommitTimestamp = Get-SourceCommitTimestamp (Get-HeadCommit)
        if ($sourceCommitTimestamp -cnotmatch '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$')
        {
            throw 'source commit timestamp self-test failed'
        }
        foreach ($versionCase in @(
            @{ Text = 'openjdk version "21.0.12" 2026-07-15'; Major = 21 },
            @{ Text = 'java version "17.0.15"'; Major = 17 },
            @{ Text = 'openjdk version "20.0.2"'; Major = 20 },
            @{ Text = 'openjdk version "22"'; Major = 22 }
        ))
        {
            if ((ConvertFrom-GateWJavaVersionText ([string]$versionCase.Text)) -ne
                    [int]$versionCase.Major)
            {
                throw 'Java version parser self-test failed'
            }
        }
        $unreadableRejected = $false
        try
        {
            ConvertFrom-GateWJavaVersionText 'unparseable runtime output' | Out-Null
        }
        catch
        {
            $unreadableRejected = $_.Exception.Message -eq 'BLOCKED / JAVA_VERSION_UNREADABLE'
        }
        if (-not $unreadableRejected)
        {
            throw 'Java unreadable version self-test failed'
        }
        $commitMismatchRejected = $false
        try
        {
            Assert-ExpectedSourceCommit ('a' * 40) ('b' * 40)
        }
        catch
        {
            $commitMismatchRejected =
            $_.Exception.Message -eq 'BLOCKED / RELEASE_SOURCE_COMMIT_MISMATCH'
        }
        if (-not $commitMismatchRejected)
        {
            throw 'different source commit self-test failed'
        }
        $pollutionBackend = Join-Path $pollutionRoot 'backend'
        $classesRoot = Join-Path $pollutionBackend 'nq-app/target/classes'
        Write-LfText (Join-Path $classesRoot 'Injected.class') 'forged-class'
        $preexistingClassesRejected = $false
        try
        {
            Assert-NoPreexistingBuildTargets $pollutionBackend
        }
        catch
        {
            $preexistingClassesRejected =
            $_.Exception.Message -eq 'BLOCKED / RELEASE_BUILD_TARGET_PREEXISTS'
        }
        if (-not $preexistingClassesRejected)
        {
            throw 'pre-existing target/classes self-test failed'
        }
        Remove-Item -LiteralPath (Join-Path $pollutionBackend 'nq-app/target') -Recurse -Force
        Write-LfText (Join-Path $pollutionBackend 'nq-app/target/nq-app-old.jar') 'old-jar'
        $preexistingJarRejected = $false
        try
        {
            Assert-NoPreexistingBuildTargets $pollutionBackend
        }
        catch
        {
            $preexistingJarRejected =
            $_.Exception.Message -eq 'BLOCKED / RELEASE_BUILD_TARGET_PREEXISTS'
        }
        if (-not $preexistingJarRejected)
        {
            throw 'pre-existing old JAR self-test failed'
        }
        Remove-Item -LiteralPath (Join-Path $pollutionBackend 'nq-app/target') -Recurse -Force
        Write-LfText (Join-Path $classesRoot 'Canonical.class') 'canonical-class'
        $cleanDescriptor = Get-BuildOutputDescriptor $pollutionBackend
        Write-LfText (Join-Path $classesRoot 'InjectedAfterBuild.class') 'forged-after-build'
        $postBuildMutationRejected = $false
        try
        {
            Assert-BuildOutputUnchanged $pollutionBackend $cleanDescriptor
        }
        catch
        {
            $postBuildMutationRejected =
            $_.Exception.Message -eq 'FAIL / RELEASE_BUILD_OUTPUT_MUTATED'
        }
        if (-not $postBuildMutationRejected)
        {
            throw 'post-build extra class self-test failed'
        }
        $worktreeArguments = @(Get-ExactCommitWorktreeAddArguments `
                '/tmp/nqgw-self-test' ('c' * 40))
        $expectedWorktreeArguments = @(
            '-C', $script:RepoRoot,
            '-c', 'core.autocrlf=false',
            '-c', 'core.eol=lf',
            'worktree', 'add', '--detach', '/tmp/nqgw-self-test', ('c' * 40)
        )
        if (($worktreeArguments -join "`0") -cne ($expectedWorktreeArguments -join "`0"))
        {
            throw 'exact commit checkout normalization self-test failed'
        }
        return [pscustomobject]@{
            decision = 'PASS / RELEASE_BUNDLE_BUILDER_SELF_TEST'
            lfNormalization = 'PASS'
            candidateDiffSha256 = 'PASS'
            deterministicJarDirectoryEntries = 'PASS'
            deterministicJarEntryReadback = 'PASS'
            crc32StandardVector = 'PASS'
            unitReleaseBinding = 'PASS'
            deterministicSourceCommitTimestamp = 'PASS'
            javaVersionContract = 'PASS / 17_20_21_22_AND_UNREADABLE'
            cleanBuildPollutionPolicy =
            'PASS / TARGET_CLASSES_OLD_JAR_POST_BUILD_CLASS_AND_COMMIT_MISMATCH'
            exactCommitCheckoutNormalization =
            'PASS / CORE_AUTOCRLF_FALSE_CORE_EOL_LF'
            credentialAccessed = $false
            networkCalled = $false
        }
    }
    finally
    {
        if (Test-Path -LiteralPath $testPath)
        {
            Remove-Item -LiteralPath $testPath -Force
        }
        if (Test-Path -LiteralPath $jarSource)
        {
            Remove-Item -LiteralPath $jarSource -Recurse -Force
        }
        if (Test-Path -LiteralPath $jarPath)
        {
            Remove-Item -LiteralPath $jarPath -Force
        }
        if (Test-Path -LiteralPath $crcSource)
        {
            Remove-Item -LiteralPath $crcSource -Recurse -Force
        }
        if (Test-Path -LiteralPath $crcJarPath)
        {
            Remove-Item -LiteralPath $crcJarPath -Force
        }
        if (Test-Path -LiteralPath $pollutionRoot)
        {
            Remove-Item -LiteralPath $pollutionRoot -Recurse -Force
        }
    }
}

function Assert-DetachedExactCommitSource
{
    param([Parameter(Mandatory = $true)][string]$Commit)

    Assert-ExactCommitSource $Commit
    $null = & git -C $script:RepoRoot symbolic-ref -q HEAD 2> $null
    if ($LASTEXITCODE -eq 0)
    {
        throw 'BLOCKED / RELEASE_BUILD_NOT_DETACHED'
    }
    Assert-NoPreexistingBuildTargets (Join-Path $script:RepoRoot 'backend')
}

function Invoke-ExactCommitDetachedBuild
{
    param([Parameter(Mandatory = $true)][string]$Commit)

    $effectiveOutputRoot = if ( [string]::IsNullOrWhiteSpace($OutputRoot))
    {
        Join-Path $script:RepoRoot 'target/gatew-release-bundles'
    }
    else
    {
        [IO.Path]::GetFullPath($OutputRoot)
    }
    $worktreeBase = if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT)
    {
        [IO.Path]::GetFullPath((Split-Path -Parent $script:RepoRoot))
    }
    else
    {
        [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    }
    $worktreeRoot = Join-Path $worktreeBase (
        'nqgw-' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
    )
    $worktreeAddAttempted = $false
    try
    {
        $worktreeAddAttempted = $true
        $worktreeArguments = @(Get-ExactCommitWorktreeAddArguments $worktreeRoot $Commit)
        Invoke-Native (Get-Command git -ErrorAction Stop).Source $worktreeArguments | Out-Null
        $childBuilder = Join-Path $worktreeRoot 'scripts/gatew/build-gatew-release-bundle.ps1'
        $engine = (Get-Process -Id $PID).Path
        $childOutput = @(& $engine -NoProfile -File $childBuilder `
            -Action build -SourceTreeMode EXACT_COMMIT -ExpectedCommit $Commit `
            -OutputRoot $effectiveOutputRoot -DetachedWorktreeBuild 2>&1)
        $childExitCode = [int]$LASTEXITCODE
        $childText = ($childOutput -join "`n")
        try
        {
            $result = $childText | ConvertFrom-Json
        }
        catch
        {
            throw 'FAIL / RELEASE_DETACHED_BUILD_FAILED'
        }
        if ($childExitCode -ne 0)
        {
            $decision = [string]$result.decision
            if ($decision -match '^(BLOCKED|FAIL) / [A-Z0-9_]+$')
            {
                throw $decision
            }
            throw 'FAIL / RELEASE_DETACHED_BUILD_FAILED'
        }
        $result | Add-Member -NotePropertyName detachedWorktree `
            -NotePropertyValue 'CREATED_AND_REMOVED'
        return $result
    }
    finally
    {
        if ($worktreeAddAttempted)
        {
            $previousErrorActionPreference = $ErrorActionPreference
            try
            {
                $ErrorActionPreference = 'SilentlyContinue'
                $null = & git -C $script:RepoRoot worktree remove --force $worktreeRoot 2> $null
            }
            finally
            {
                $ErrorActionPreference = $previousErrorActionPreference
            }
        }
        if (Test-Path -LiteralPath $worktreeRoot)
        {
            $resolved = [IO.Path]::GetFullPath($worktreeRoot)
            if (-not $resolved.StartsWith(
                    $worktreeBase + [IO.Path]::DirectorySeparatorChar,
                    [StringComparison]::OrdinalIgnoreCase
            ))
            {
                throw 'FAIL / RELEASE_WORKTREE_CLEANUP_PATH_INVALID'
            }
            Remove-Item -LiteralPath $resolved -Recurse -Force
        }
    }
}

try
{
    $result = if ($Action -eq 'self-test')
    {
        Invoke-BuilderSelfTest
    }
    else
    {
        Build-ReleaseBundle
    }
    $result | ConvertTo-Json -Depth 8
}
catch
{
    $message = if ($_.Exception.Message -match '^(BLOCKED|FAIL) / [A-Z0-9_]+$')
    {
        $_.Exception.Message
    }
    else
    {
        'FAIL / RELEASE_BUILD_INTERNAL_ERROR'
    }
    $failure = [ordered]@{ decision = $message }
    if ($Action -eq 'self-test')
    {
        $failure.selfTestDetail = $_.Exception.Message
    }
    [pscustomobject]$failure | ConvertTo-Json
    exit 2
}
