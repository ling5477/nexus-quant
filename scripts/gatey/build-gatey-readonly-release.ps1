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
$minimalLivePilotControlPath = Join-Path $PSScriptRoot 'invoke-gatey-minimal-live-pilot.ps1'
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

function Assert-ExactCommitMaterialization
{
    param(
        [Parameter(Mandatory = $true)][string]$SourceRoot,
        [Parameter(Mandatory = $true)][string]$Commit
    )
    $entries = @(& git -C $repo ls-tree -r --full-tree $Commit)
    if ($LASTEXITCODE -ne 0 -or $entries.Count -eq 0)
    {
        throw 'FAIL / RELEASE_SOURCE_TREE_INVENTORY_FAILED'
    }
    $paths = [Collections.Generic.List[string]]::new()
    $expectedHashes = [Collections.Generic.List[string]]::new()
    foreach ($entry in $entries)
    {
        $parts = ([string]$entry).Split("`t", 2)
        if ($parts.Count -ne 2 -or $parts[0] -notmatch '^([0-9]{6}) blob ([0-9a-f]{40})$' -or
                $Matches[1] -eq '120000' -or $parts[1].Contains("`n") -or $parts[1].Contains("`r"))
        {
            throw 'BLOCKED / RELEASE_SOURCE_TREE_UNSUPPORTED'
        }
        $fullPath = Join-Path $SourceRoot $parts[1]
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf) -or
                ((Get-Item -LiteralPath $fullPath -Force).Attributes -band [IO.FileAttributes]::ReparsePoint))
        {
            throw 'FAIL / RELEASE_SOURCE_FILE_MISSING'
        }
        $paths.Add($parts[1].Replace('\', '/'))
        $expectedHashes.Add($Matches[2])
    }
    Push-Location $SourceRoot
    try
    {
        if ($PSVersionTable.PSVersion.Major -lt 7)
        {
            if (@($paths | Where-Object { $_ -match '[^\x00-\x7F]' }).Count -ne 0)
            {
                throw 'BLOCKED / RELEASE_SOURCE_PATH_INVALID'
            }
            $git = (Get-Command git -ErrorAction Stop).Source
            $hashTempRoot = Join-Path ([IO.Path]::GetTempPath()) `
                ('nqg-hash-' + [Guid]::NewGuid().ToString('N'))
            try
            {
                [IO.Directory]::CreateDirectory($hashTempRoot) | Out-Null
                $pathFile = Join-Path $hashTempRoot 'paths.txt'
                $outputFile = Join-Path $hashTempRoot 'hashes.txt'
                $errorFile = Join-Path $hashTempRoot 'error.txt'
                [IO.File]::WriteAllLines($pathFile, [string[]]$paths, [Text.Encoding]::ASCII)
                $hashProcess = Start-Process -FilePath $git `
                    -ArgumentList @('hash-object', '--no-filters', '--stdin-paths') `
                    -WorkingDirectory $SourceRoot -RedirectStandardInput $pathFile `
                    -RedirectStandardOutput $outputFile -RedirectStandardError $errorFile `
                    -WindowStyle Hidden -Wait -PassThru
                $hashError = if (Test-Path -LiteralPath $errorFile) {
                    Get-Content -LiteralPath $errorFile -Raw
                } else { '' }
                if ($hashProcess.ExitCode -ne 0 -or -not [string]::IsNullOrWhiteSpace($hashError))
                {
                    throw 'FAIL / RELEASE_SOURCE_HASH_PROCESS_FAILED'
                }
                $actualHashes = @(Get-Content -LiteralPath $outputFile)
            }
            finally
            {
                if (Test-Path -LiteralPath $hashTempRoot)
                {
                    $resolvedHashTempRoot = [IO.Path]::GetFullPath($hashTempRoot)
                    $expectedHashTempPrefix = Join-Path ([IO.Path]::GetFullPath([IO.Path]::GetTempPath())) 'nqg-hash-'
                    if (-not $resolvedHashTempRoot.StartsWith(
                            $expectedHashTempPrefix, [StringComparison]::OrdinalIgnoreCase))
                    {
                        throw 'FAIL / RELEASE_SOURCE_CLEANUP_PATH_INVALID'
                    }
                    Remove-Item -LiteralPath $resolvedHashTempRoot -Recurse -Force
                }
            }
        }
        else
        {
            $actualHashes = @($paths | & git hash-object --no-filters --stdin-paths)
        }
    }
    finally
    {
        Pop-Location
    }
    if ($LASTEXITCODE -ne 0 -or $actualHashes.Count -ne $expectedHashes.Count)
    {
        throw 'FAIL / RELEASE_SOURCE_HASH_PROCESS_FAILED'
    }
    for ($index = 0; $index -lt $expectedHashes.Count; $index++)
    {
        if ([string]$actualHashes[$index] -cne [string]$expectedHashes[$index])
        {
            Write-Verbose (
                'materialized blob mismatch path={0} expected={1} actual={2}' -f
                $paths[$index], $expectedHashes[$index], $actualHashes[$index]
            )
            throw 'FAIL / RELEASE_SOURCE_BLOB_HASH_MISMATCH'
        }
    }
    $materializedFiles = @(Get-ChildItem -LiteralPath $SourceRoot -Recurse -File -Force)
    if ($materializedFiles.Count -ne $paths.Count)
    {
        throw 'FAIL / RELEASE_SOURCE_FILE_COUNT_MISMATCH'
    }
    return $paths.Count
}

function Read-GitBatchAsciiLine
{
    param([Parameter(Mandatory = $true)][IO.Stream]$Stream)
    $bytes = [Collections.Generic.List[byte]]::new()
    while ($true)
    {
        $value = $Stream.ReadByte()
        if ($value -lt 0) { throw 'FAIL / RELEASE_SOURCE_BLOB_STREAM_TRUNCATED' }
        if ($value -eq 10) { break }
        $bytes.Add([byte]$value)
        if ($bytes.Count -gt 256) { throw 'FAIL / RELEASE_SOURCE_BLOB_HEADER_INVALID' }
    }
    return [Text.Encoding]::ASCII.GetString($bytes.ToArray())
}

function Copy-GitBatchBlob
{
    param(
        [Parameter(Mandatory = $true)][IO.Stream]$Source,
        [Parameter(Mandatory = $true)][IO.Stream]$Destination,
        [Parameter(Mandatory = $true)][long]$Length
    )
    $remaining = $Length
    $buffer = [byte[]]::new(65536)
    while ($remaining -gt 0)
    {
        $requested = [int][Math]::Min([long]$buffer.Length, $remaining)
        $read = $Source.Read($buffer, 0, $requested)
        if ($read -le 0) { throw 'FAIL / RELEASE_SOURCE_BLOB_STREAM_TRUNCATED' }
        $Destination.Write($buffer, 0, $read)
        $remaining -= $read
    }
    if ($Source.ReadByte() -ne 10)
    {
        throw 'FAIL / RELEASE_SOURCE_BLOB_STREAM_INVALID'
    }
}

function Write-ExactCommitBlobTreeWindowsPowerShell
{
    param(
        [Parameter(Mandatory = $true)][string]$SourceRoot,
        [Parameter(Mandatory = $true)][string]$Commit
    )
    $entries = @(& git -C $repo ls-tree -r --full-tree $Commit)
    if ($LASTEXITCODE -ne 0 -or $entries.Count -eq 0)
    {
        throw 'FAIL / RELEASE_SOURCE_TREE_INVENTORY_FAILED'
    }
    $hashes = [Collections.Generic.List[string]]::new()
    foreach ($entry in $entries)
    {
        $parts = ([string]$entry).Split("`t", 2)
        if ($parts.Count -ne 2 -or $parts[0] -notmatch '^([0-9]{6}) blob ([0-9a-f]{40})$' -or
                $Matches[1] -eq '120000')
        {
            throw 'BLOCKED / RELEASE_SOURCE_TREE_UNSUPPORTED'
        }
        $hashes.Add($Matches[2])
    }
    $batchTempRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('nqg-batch-' + [Guid]::NewGuid().ToString('N'))
    $batchOutputStream = $null
    try
    {
        [IO.Directory]::CreateDirectory($batchTempRoot) | Out-Null
        $inputPath = Join-Path $batchTempRoot 'hashes.txt'
        $outputPath = Join-Path $batchTempRoot 'batch.bin'
        $errorPath = Join-Path $batchTempRoot 'error.txt'
        [IO.File]::WriteAllLines($inputPath, [string[]]$hashes, [Text.Encoding]::ASCII)
        $git = (Get-Command git -ErrorAction Stop).Source
        $batchProcess = Start-Process -FilePath $git `
            -ArgumentList @('-C', ('"' + $repo + '"'), 'cat-file', '--batch') `
            -RedirectStandardInput $inputPath -RedirectStandardOutput $outputPath `
            -RedirectStandardError $errorPath -WindowStyle Hidden -Wait -PassThru
        $batchError = if (Test-Path -LiteralPath $errorPath) {
            Get-Content -LiteralPath $errorPath -Raw
        } else { '' }
        if ($batchProcess.ExitCode -ne 0 -or -not [string]::IsNullOrWhiteSpace($batchError))
        {
            throw 'FAIL / RELEASE_SOURCE_BLOB_PROCESS_FAILED'
        }
        $batchOutputStream = [IO.File]::OpenRead($outputPath)
        $count = 0
        foreach ($entry in $entries)
        {
            $parts = ([string]$entry).Split("`t", 2)
            $hash = $hashes[$count]
            $relativePath = $parts[1]
            $segments = @($relativePath.Split('/'))
            if ([string]::IsNullOrWhiteSpace($relativePath) -or
                    [IO.Path]::IsPathRooted($relativePath) -or $relativePath.Contains('\') -or
                    @($segments | Where-Object { $_ -eq '' -or $_ -eq '.' -or $_ -eq '..' }).Count -gt 0)
            {
                throw 'BLOCKED / RELEASE_SOURCE_PATH_INVALID'
            }
            $fullPath = [IO.Path]::GetFullPath((Join-Path $SourceRoot $relativePath))
            if (-not $fullPath.StartsWith(
                    [IO.Path]::GetFullPath($SourceRoot) + [IO.Path]::DirectorySeparatorChar,
                    [StringComparison]::OrdinalIgnoreCase))
            {
                throw 'BLOCKED / RELEASE_SOURCE_PATH_INVALID'
            }
            $parentPath = [IO.Path]::GetDirectoryName($fullPath)
            if ([string]::IsNullOrWhiteSpace($parentPath))
            {
                throw 'BLOCKED / RELEASE_SOURCE_PATH_INVALID'
            }
            [IO.Directory]::CreateDirectory($parentPath) | Out-Null
            $header = Read-GitBatchAsciiLine $batchOutputStream
            if ($header -notmatch ('^' + $hash + ' blob ([0-9]+)$'))
            {
                throw 'FAIL / RELEASE_SOURCE_BLOB_HEADER_INVALID'
            }
            $length = [long]$Matches[1]
            $file = [IO.File]::Open(
                $fullPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
            try { Copy-GitBatchBlob $batchOutputStream $file $length }
            finally { $file.Dispose() }
            $count++
        }
        if ($batchOutputStream.Position -ne $batchOutputStream.Length -or $count -ne $entries.Count)
        {
            throw 'FAIL / RELEASE_SOURCE_BLOB_PROCESS_FAILED'
        }
        return $count
    }
    finally
    {
        if ($null -ne $batchOutputStream) { $batchOutputStream.Dispose() }
        if (Test-Path -LiteralPath $batchTempRoot)
        {
            $resolvedBatchTempRoot = [IO.Path]::GetFullPath($batchTempRoot)
            $expectedBatchTempPrefix = Join-Path `
                ([IO.Path]::GetFullPath([IO.Path]::GetTempPath())) 'nqg-batch-'
            if (-not $resolvedBatchTempRoot.StartsWith(
                    $expectedBatchTempPrefix, [StringComparison]::OrdinalIgnoreCase))
            {
                throw 'FAIL / RELEASE_SOURCE_CLEANUP_PATH_INVALID'
            }
            Remove-Item -LiteralPath $resolvedBatchTempRoot -Recurse -Force
        }
    }
}

function Write-ExactCommitBlobTree
{
    param(
        [Parameter(Mandatory = $true)][string]$SourceRoot,
        [Parameter(Mandatory = $true)][string]$Commit
    )
    if ($PSVersionTable.PSVersion.Major -lt 7)
    {
        return Write-ExactCommitBlobTreeWindowsPowerShell $SourceRoot $Commit
    }
    $entries = @(& git -C $repo ls-tree -r --full-tree $Commit)
    if ($LASTEXITCODE -ne 0 -or $entries.Count -eq 0)
    {
        throw 'FAIL / RELEASE_SOURCE_TREE_INVENTORY_FAILED'
    }
    $git = (Get-Command git -ErrorAction Stop).Source
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $git
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    if ($startInfo.PSObject.Properties.Name -contains 'StandardInputEncoding')
    {
        $startInfo.StandardInputEncoding = [Text.Encoding]::ASCII
    }
    if ($startInfo.PSObject.Properties.Name -contains 'ArgumentList')
    {
        foreach ($argument in @('-C', $repo, 'cat-file', '--batch'))
        {
            $null = $startInfo.ArgumentList.Add($argument)
        }
    }
    else
    {
        if ($repo.Contains('"'))
        {
            throw 'BLOCKED / RELEASE_SOURCE_PATH_INVALID'
        }
        # Windows PowerShell 5.1 使用 .NET Framework；这里不经 shell，且 repo 已是受控绝对路径。
        $startInfo.Arguments = '-C "' + $repo + '" cat-file --batch'
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $processStarted = $false
    $previousConsoleInputEncoding = [Console]::InputEncoding
    [Console]::InputEncoding = [Text.Encoding]::ASCII
    try
    {
        if (-not $process.Start()) { throw 'FAIL / RELEASE_SOURCE_BLOB_PROCESS_FAILED' }
        $processStarted = $true
        $batchInputStream = $process.StandardInput.BaseStream
        $batchOutputStream = $process.StandardOutput.BaseStream
        # 直接写 ASCII + LF，避免 PS5 StreamWriter 的 BOM/CRLF 污染 git batch object name。
        $count = 0
        foreach ($entry in $entries)
        {
            $parts = ([string]$entry).Split("`t", 2)
            if ($parts.Count -ne 2 -or $parts[0] -notmatch '^([0-9]{6}) blob ([0-9a-f]{40})$' -or
                    $Matches[1] -eq '120000')
            {
                throw 'BLOCKED / RELEASE_SOURCE_TREE_UNSUPPORTED'
            }
            $hash = $Matches[2]
            $relativePath = $parts[1]
            $segments = @($relativePath.Split('/'))
            if ([string]::IsNullOrWhiteSpace($relativePath) -or
                    [IO.Path]::IsPathRooted($relativePath) -or $relativePath.Contains('\') -or
                    @($segments | Where-Object { $_ -eq '' -or $_ -eq '.' -or $_ -eq '..' }).Count -gt 0)
            {
                throw 'BLOCKED / RELEASE_SOURCE_PATH_INVALID'
            }
            $fullPath = [IO.Path]::GetFullPath((Join-Path $SourceRoot $relativePath))
            if (-not $fullPath.StartsWith(
                    [IO.Path]::GetFullPath($SourceRoot) + [IO.Path]::DirectorySeparatorChar,
                    [StringComparison]::OrdinalIgnoreCase))
            {
                throw 'BLOCKED / RELEASE_SOURCE_PATH_INVALID'
            }
            $parentPath = [IO.Path]::GetDirectoryName($fullPath)
            if ([string]::IsNullOrWhiteSpace($parentPath))
            {
                throw 'BLOCKED / RELEASE_SOURCE_PATH_INVALID'
            }
            [IO.Directory]::CreateDirectory($parentPath) | Out-Null
            $request = [Text.Encoding]::ASCII.GetBytes($hash + "`n")
            $batchInputStream.Write($request, 0, $request.Length)
            $batchInputStream.Flush()
            $header = Read-GitBatchAsciiLine $batchOutputStream
            if ($header -notmatch ('^' + $hash + ' blob ([0-9]+)$'))
            {
                throw 'FAIL / RELEASE_SOURCE_BLOB_HEADER_INVALID'
            }
            $length = [long]$Matches[1]
            $file = [IO.File]::Open(
                $fullPath,
                [IO.FileMode]::CreateNew,
                [IO.FileAccess]::Write,
                [IO.FileShare]::None
            )
            try { Copy-GitBatchBlob $batchOutputStream $file $length }
            finally { $file.Dispose() }
            $count++
        }
        $batchInputStream.Close()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0 -or $count -ne $entries.Count)
        {
            throw 'FAIL / RELEASE_SOURCE_BLOB_PROCESS_FAILED'
        }
        return $count
    }
    finally
    {
        [Console]::InputEncoding = $previousConsoleInputEncoding
        if ($processStarted -and -not $process.HasExited)
        {
            $process.Kill()
        }
        $process.Dispose()
    }
}

function New-ExactCommitSourceMaterialization
{
    param([Parameter(Mandatory = $true)][string]$Commit)
    $base = Join-Path ([IO.Path]::GetTempPath()) `
        ('nqg-' + [Guid]::NewGuid().ToString('N').Substring(0, 12))
    $source = Join-Path $base 'source'
    try
    {
        [IO.Directory]::CreateDirectory($source) | Out-Null
        $writtenFiles = Write-ExactCommitBlobTree $source $Commit
        $trackedFiles = Assert-ExactCommitMaterialization $source $Commit
        if ($writtenFiles -ne $trackedFiles)
        {
            throw 'FAIL / RELEASE_SOURCE_FILE_COUNT_MISMATCH'
        }
        return [pscustomobject][ordered]@{
            baseRoot = $base
            sourceRoot = $source
            trackedFiles = $trackedFiles
            sourceMode = 'EXACT_GIT_COMMIT_BLOB_BYTES'
        }
    }
    catch
    {
        if (Test-Path -LiteralPath $base)
        {
            Remove-Item -LiteralPath $base -Recurse -Force
        }
        throw
    }
}

function Remove-ExactCommitSourceMaterialization
{
    param([Parameter(Mandatory = $true)]$Materialization)
    $base = [IO.Path]::GetFullPath([string]$Materialization.baseRoot)
    $temp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if (-not $base.StartsWith(
            (Join-Path $temp 'nqg-'),
            [StringComparison]::OrdinalIgnoreCase))
    {
        throw 'FAIL / RELEASE_SOURCE_CLEANUP_PATH_INVALID'
    }
    if (Test-Path -LiteralPath $base)
    {
        Remove-Item -LiteralPath $base -Recurse -Force
    }
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
    param(
        [Parameter(Mandatory = $true)][string]$OutputTimestamp,
        [Parameter(Mandatory = $true)][string]$SourceRoot,
        [Parameter(Mandatory = $true)][string]$SourceMigrationRoot
    )
    $maven = (Get-Command mvn -ErrorAction Stop).Source
    $arguments = @(
        '-f', (Join-Path $SourceRoot 'backend/pom.xml'), '-pl', 'nq-app', '-am',
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
    $target = Join-Path $SourceRoot 'backend/nq-app/target'
    $jars = @(Get-ChildItem -LiteralPath $target -File -Filter 'nq-app-*.jar' |
        Where-Object { $_.Name -notlike '*.original' -and $_.Name -notlike '*-sources.jar' -and
                $_.Name -notlike '*-javadoc.jar' })
    if ($jars.Count -ne 1 -or ($jars[0].Attributes -band [IO.FileAttributes]::ReparsePoint))
    {
        throw 'BLOCKED / RELEASE_APPLICATION_ARTIFACT_AMBIGUOUS'
    }
    Assert-ApplicationJarContract $jars[0].FullName $SourceMigrationRoot
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
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [string]$SourceMigrationRoot = $migrationRoot
    )
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
            $inventory = Get-GateYMigrationInventory $SourceMigrationRoot
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
    $materializationA = $null
    $materializationB = $null
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
        $head = (& git -C $repo rev-parse HEAD).Trim()
        $materializationA = New-ExactCommitSourceMaterialization $head
        $materializationB = New-ExactCommitSourceMaterialization $head
        foreach ($relativePath in @(
            'scripts/gatey/build-gatey-readonly-release.ps1',
            'scripts/gatey/invoke-gatey-minimal-live-pilot.ps1',
            'deploy/systemd/nq-gatey-readonly-qualification.service',
            'deploy/gatey/gatey-readonly-runtime-target.json',
            'backend/nq-infra/src/main/resources/db/migration/V43__gate_y_current_market_snapshot.sql'
        ))
        {
            $hashA = Get-GateYReadonlySha256File (Join-Path $materializationA.sourceRoot $relativePath)
            $hashB = Get-GateYReadonlySha256File (Join-Path $materializationB.sourceRoot $relativePath)
            if ($hashA -cne $hashB)
            {
                throw 'FAIL / RELEASE_SOURCE_MATERIALIZATION_NOT_DETERMINISTIC'
            }
        }
        return [pscustomobject][ordered]@{
            decision = 'PASS / GATEY_READONLY_RELEASE_BUILDER_SELF_TEST'
            migrationCount = @((Get-GateYMigrationInventory $migrationRoot).migrations).Count
            validJarAccepted = $true
            tamperedMigrationRejected = $true
            canonicalMaterializationVerified = $true
            trackedFiles = [int]$materializationA.trackedFiles
            sourceMode = [string]$materializationA.sourceMode
            serverMutation = $false
        }
    }
    finally
    {
        if ($null -ne $materializationA) { Remove-ExactCommitSourceMaterialization $materializationA }
        if ($null -ne $materializationB) { Remove-ExactCommitSourceMaterialization $materializationB }
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
    $materialization = $null
    try
    {
        $materialization = New-ExactCommitSourceMaterialization $head
        $sourceRepo = [string]$materialization.sourceRoot
        $sourceMigrationRoot = Join-Path $sourceRepo 'backend/nq-infra/src/main/resources/db/migration'
        $applicationJar = Invoke-ExactSourceApplicationBuild `
            $commitTimestamp $sourceRepo $sourceMigrationRoot
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
                Copy-Artifact (Join-Path $sourceRepo 'backend/nq-app/src/main/resources/application-gatey-readonly-qualification.yml') $stage 'config/application-gatey-readonly-qualification.yml' '0644' 'runtime-profile'
                Copy-Artifact (Join-Path $sourceRepo 'scripts/gatey/gatey-readonly-release-contract.psm1') $stage 'bin/gatey-readonly-release-contract.psm1' '0644' 'release-contract'
                Copy-Artifact (Join-Path $sourceRepo 'scripts/gatey/invoke-gatey-readonly-deployment-contract.ps1') $stage 'bin/invoke-gatey-readonly-deployment-contract.ps1' '0755' 'deployment-contract'
                Copy-Artifact (Join-Path $sourceRepo 'scripts/gatey/install-gatey-readonly-release.ps1') $stage 'bin/install-gatey-readonly-release.ps1' '0755' 'release-installer'
                Copy-Artifact (Join-Path $sourceRepo 'scripts/gatey/invoke-gatey-readonly-runtime-deployment.ps1') $stage 'bin/invoke-gatey-readonly-runtime-deployment.ps1' '0755' 'runtime-deployment-orchestrator'
                Copy-Artifact (Join-Path $sourceRepo 'scripts/gatey/invoke-gatey-exact-pilot-scope.ps1') $stage 'bin/invoke-gatey-exact-pilot-scope.ps1' '0755' 'exact-pilot-control-surface'
                Copy-Artifact (Join-Path $sourceRepo 'scripts/gatey/invoke-gatey-minimal-live-pilot.ps1') $stage 'bin/invoke-gatey-minimal-live-pilot.ps1' '0755' 'minimal-live-pilot-control-surface'
                Copy-Artifact (Join-Path $sourceRepo 'deploy/systemd/nq-gatey-readonly-qualification.service') $stage 'config/nq-gatey-readonly-qualification.service' '0644' 'systemd-runtime-contract'
                Copy-Artifact (Join-Path $sourceRepo 'deploy/gatey/gatey-readonly-runtime.env.example') $stage 'config/gatey-readonly-runtime.env.example' '0644' 'runtime-environment-template'
                Copy-Artifact (Join-Path $sourceRepo 'deploy/gatey/gatey-readonly-runtime.secrets.env.example') $stage 'config/gatey-readonly-runtime.secrets.env.example' '0600' 'runtime-secret-environment-template'
                Copy-Artifact (Join-Path $sourceRepo 'deploy/gatey/gatey-readonly-db.pgpass.example') $stage 'config/gatey-readonly-db.pgpass.example' '0600' 'database-credential-reference-template'
                Copy-Artifact (Join-Path $sourceRepo 'deploy/gatey/gatey-readonly-runtime-target.json') $stage 'config/gatey-readonly-runtime-target.json' '0644' 'runtime-target-contract'
                Copy-Artifact (Join-Path $sourceRepo 'scripts/gatew/verify-gatew-release.ps1') $stage 'bin/verify-gatew-release.ps1' '0755' 'gatew-rollback-verifier'
                Copy-Artifact (Join-Path $sourceRepo 'scripts/gatew/gatew-release-contract.psm1') $stage 'bin/gatew-release-contract.psm1' '0644' 'gatew-rollback-contract'
            )
            $manifest = New-GateYReadonlyReleaseManifest `
                $head $commitTimestamp $artifacts $sourceMigrationRoot
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
                sourceMode = [string]$materialization.sourceMode
                trackedSourceFiles = [int]$materialization.trackedFiles
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
                if (-not $resolved.StartsWith(
                        [IO.Path]::GetFullPath($root) + [IO.Path]::DirectorySeparatorChar,
                        [StringComparison]::OrdinalIgnoreCase))
                {
                    throw 'FAIL / RELEASE_STAGE_CLEANUP_PATH_INVALID'
                }
                Remove-Item -LiteralPath $resolved -Recurse -Force
            }
        }
    }
    finally
    {
        if ($null -ne $materialization) { Remove-ExactCommitSourceMaterialization $materialization }
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
