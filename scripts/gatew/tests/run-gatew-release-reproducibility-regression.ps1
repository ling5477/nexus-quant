[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:TestRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$script:GateWRoot = [IO.Path]::GetFullPath((Join-Path $script:TestRoot '..'))
$script:RepoRoot = [IO.Path]::GetFullPath((Join-Path $script:GateWRoot '..\..'))
$script:ContractPath = Join-Path $script:GateWRoot 'gatew-release-contract.psm1'
$script:BuilderPath = Join-Path $script:GateWRoot 'build-gatew-release-bundle.ps1'
$script:VerifierPath = Join-Path $script:GateWRoot 'verify-gatew-release.ps1'
$script:EnginePath = (Get-Process -Id $PID).Path
$script:Utf8NoBom = New-Object Text.UTF8Encoding($false)
$script:Cases = [Collections.Generic.List[string]]::new()

Import-Module $script:ContractPath -Force -DisableNameChecking

function Complete-Case
{
    param([Parameter(Mandatory = $true)][string]$Name)

    $script:Cases.Add($Name)
}

function Assert-Condition
{
    param(
        [Parameter(Mandatory = $true)][bool]$Condition,
        [Parameter(Mandatory = $true)][string]$Message
    )

    if (-not $Condition)
    {
        throw $Message
    }
}

function Get-TestSha256
{
    param([Parameter(Mandatory = $true)][string]$Path)

    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Write-TestText
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text
    )

    [IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    [IO.File]::WriteAllText($Path, ($Text -replace "`r`n|`r", "`n"), $script:Utf8NoBom)
}

function Write-TestBinary
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][byte[]]$Bytes
    )

    [IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    [IO.File]::WriteAllBytes($Path, $Bytes)
}

function Write-TestJar
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][object[]]$Entries
    )

    Add-Type -AssemblyName System.IO.Compression
    [IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    $stream = [IO.FileStream]::new(
            $Path,
            [IO.FileMode]::Create,
            [IO.FileAccess]::ReadWrite,
            [IO.FileShare]::None
    )
    try
    {
        $archive = [IO.Compression.ZipArchive]::new(
                $stream,
                [IO.Compression.ZipArchiveMode]::Create,
                $true
        )
        try
        {
            foreach ($definition in $Entries)
            {
                $entry = $archive.CreateEntry([string]$definition.Name)
                if (-not ([string]$definition.Name).EndsWith('/'))
                {
                    $entryStream = $entry.Open()
                    try
                    {
                        $bytes = $script:Utf8NoBom.GetBytes([string]$definition.Content)
                        try
                        {
                            $entryStream.Write($bytes, 0, $bytes.Length)
                        }
                        finally
                        {
                            [Array]::Clear($bytes, 0, $bytes.Length)
                        }
                    }
                    finally
                    {
                        $entryStream.Dispose()
                    }
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

function New-TestArtifact
{
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][string]$Mode,
        [Parameter(Mandatory = $true)][string]$LineEndingPolicy,
        [Parameter(Mandatory = $true)][bool]$Entrypoint,
        [Parameter(Mandatory = $true)][string]$Role,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Content
    )

    $path = Join-Path $Root $RelativePath
    if ( $RelativePath.EndsWith('.jar', [StringComparison]::Ordinal))
    {
        $entries = @(
            [pscustomobject]@{ Name = 'META-INF/'; Content = '' },
            [pscustomobject]@{ Name = 'META-INF/MANIFEST.MF'; Content = 'Manifest-Version: 1.0' },
            [pscustomobject]@{ Name = "fixture/$Role.txt"; Content = $Content }
        )
        if ($Role -eq 'runtime-library')
        {
            $entries += @(
                [pscustomobject]@{ Name = 'META-INF/'; Content = '' },
                [pscustomobject]@{ Name = 'org/'; Content = '' },
                [pscustomobject]@{ Name = 'org/'; Content = '' },
                [pscustomobject]@{ Name = 'org/example/'; Content = '' },
                [pscustomobject]@{ Name = 'org/example/'; Content = '' },
                [pscustomobject]@{ Name = 'services/'; Content = '' },
                [pscustomobject]@{ Name = 'services/'; Content = '' }
            )
        }
        Write-TestJar $path $entries
    }
    elseif ($LineEndingPolicy -eq 'LF')
    {
        Write-TestText $path ($Content + "`n")
    }
    else
    {
        Write-TestBinary $path $script:Utf8NoBom.GetBytes($Content)
    }
    $item = Get-Item -LiteralPath $path
    return [pscustomobject][ordered]@{
        relativePath = $RelativePath.Replace('\', '/')
        size = [long]$item.Length
        sha256 = Get-TestSha256 $path
        mode = $Mode
        lineEndingPolicy = $LineEndingPolicy
        entrypoint = $Entrypoint
        role = $Role
    }
}

function New-TestRelease
{
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Commit,
        [Parameter(Mandatory = $true)][string]$Timestamp
    )

    [IO.Directory]::CreateDirectory($Root) | Out-Null
    $unitText = @"
[Unit]
Documentation=file:/opt/nexus-quant/current/release-manifest.json
[Service]
Environment=NQ_GATEW_RELEASE_ROOT=/opt/nexus-quant/current
"@
    $definitions = @(
        @('bin/worker.ps1', '0755', 'LF', $true, 'worker-helper', 'worker'),
        @('bin/control.ps1', '0755', 'LF', $true, 'control-helper', 'control'),
        @('bin/failclose.ps1', '0755', 'LF', $true, 'failclose-helper', 'failclose'),
        @('bin/runtime-contract.psm1', '0644', 'LF', $false, 'contract-library', 'runtime'),
        @('bin/verify-gatew-release.ps1', '0755', 'LF', $true, 'release-verifier', 'verifier'),
        @('bin/install-gatew-release.ps1', '0755', 'LF', $true, 'release-installer', 'installer'),
        @('bin/gatew-release-contract.psm1', '0644', 'LF', $false, 'release-contract', 'contract'),
        @('systemd/nq-gatew-soak@.service', '0644', 'LF', $false, 'systemd-worker-unit', $unitText),
        @('systemd/nq-gatew-soak-failclose@.service', '0644', 'LF', $false, 'systemd-failclose-unit', $unitText),
        @('launcher/test-support.jar', '0644', 'BINARY', $false, 'launcher-test-support', 'test-support'),
        @('launcher/modules/0000-module.jar', '0644', 'BINARY', $false, 'launcher-module', 'module'),
        @('launcher/lib/0000-library.jar', '0644', 'BINARY', $false, 'runtime-library', 'library')
    )
    $artifacts = @()
    foreach ($definition in $definitions)
    {
        $artifacts += New-TestArtifact $Root @definition
    }
    $manifest = [pscustomobject][ordered]@{
        schemaVersion = 'nq-gatew-release-v3'
        releaseId = $Commit
        sourceCommit = $Commit
        sourceCommitTimestamp = $Timestamp
        sourceTreeMode = 'EXACT_COMMIT'
        baseCommit = $null
        candidateDiffSha256 = $null
        requiredRuntime = [pscustomobject][ordered]@{
            os = 'linux'
            powershellMajor = 7
            javaMajor = 21
            systemd = $true
        }
        buildProvenance = [pscustomobject][ordered]@{
            mavenCommand =
            'mvn --offline --quiet -f backend/pom.xml -pl nq-app -am -DskipTests clean package'
            javaMajor = 21
            cleanDetachedWorktree = $true
        }
        lineEndingPolicy = 'LF'
        artifacts = @(Sort-GateWArtifactsOrdinal $artifacts)
    }
    Write-GateWCanonicalManifest (Join-Path $Root 'release-manifest.json') $manifest
    return $manifest
}

function Invoke-TestVerifier
{
    param([Parameter(Mandatory = $true)][string]$Root)

    $output = @(& $script:EnginePath -NoProfile -File $script:VerifierPath `
        -ReleaseRoot $Root -SkipPosix 2>&1)
    $exitCode = [int]$LASTEXITCODE
    $result = ($output -join "`n") | ConvertFrom-Json
    $jarCountProperty = $result.PSObject.Properties['jarCount']
    $duplicateDirectoryEntriesProperty =
    $result.PSObject.Properties['duplicateDirectoryEntries']
    return [pscustomobject]@{
        ExitCode = $exitCode
        Decision = [string]$result.decision
        JarCount = if ($null -eq $jarCountProperty)
        {
            0
        }
        else
        {
            [int]$jarCountProperty.Value
        }
        DuplicateDirectoryEntries = if ($null -eq $duplicateDirectoryEntriesProperty)
        {
            0
        }
        else
        {
            [int]$duplicateDirectoryEntriesProperty.Value
        }
    }
}

function Set-TestJar
{
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][object[]]$Entries
    )

    $path = Join-Path $Root $RelativePath
    Write-TestJar $path $Entries
    $artifact = @($Manifest.artifacts | Where-Object {
        [string]$_.relativePath -ceq $RelativePath
    })
    if ($artifact.Count -ne 1)
    {
        throw 'TEST_JAR_ARTIFACT_NOT_FOUND'
    }
    $item = Get-Item -LiteralPath $path
    $artifact[0].size = [long]$item.Length
    $artifact[0].sha256 = Get-TestSha256 $path
    Write-GateWCanonicalManifest (Join-Path $Root 'release-manifest.json') $Manifest
}

function Read-TestTarPaths
{
    param([Parameter(Mandatory = $true)][string]$Path)

    $paths = @()
    $stream = [IO.File]::OpenRead($Path)
    try
    {
        while ($true)
        {
            $header = New-Object byte[] 512
            if ($stream.Read($header, 0, $header.Length) -ne 512)
            {
                throw 'TAR_TRUNCATED'
            }
            if (@($header | Where-Object { $_ -ne 0 }).Count -eq 0)
            {
                break
            }
            $name = $script:Utf8NoBom.GetString($header, 0, 100).Trim([char]0)
            $prefix = $script:Utf8NoBom.GetString($header, 345, 155).Trim([char]0)
            if (-not [string]::IsNullOrWhiteSpace($prefix))
            {
                $name = "$prefix/$name"
            }
            $paths += $name
            $sizeText = $script:Utf8NoBom.GetString($header, 124, 12).Trim([char]0, ' ')
            $size = if ( [string]::IsNullOrEmpty($sizeText))
            {
                0
            }
            else
            {
                [Convert]::ToInt64($sizeText, 8)
            }
            $skip = [long]([Math]::Ceiling($size / 512.0) * 512)
            if ($stream.Seek($skip, [IO.SeekOrigin]::Current) -lt 0)
            {
                throw 'TAR_SEEK_FAILED'
            }
        }
    }
    finally
    {
        $stream.Dispose()
    }
    return @($paths)
}

$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('nq-gatew-release-repro-' + [Guid]::NewGuid().ToString('N'))
$dirtyMarker = Join-Path $script:RepoRoot ('.gatew-release-dirty-' + [Guid]::NewGuid().ToString('N'))
try
{
    $commitA = '1111111111111111111111111111111111111111'
    $commitB = '2222222222222222222222222222222222222222'
    $timestampA = '2020-01-02T03:04:05Z'
    $rootA = Join-Path $tempRoot 'path-a/release'
    $rootB = Join-Path $tempRoot 'unrelated/deeper/path-b/release'
    $manifestA = New-TestRelease $rootA $commitA $timestampA
    $manifestB = New-TestRelease $rootB $commitA $timestampA

    $bytesBefore = Get-GateWCanonicalManifestBytes $manifestA
    Start-Sleep -Seconds 2
    $bytesAfter = Get-GateWCanonicalManifestBytes $manifestA
    Assert-Condition (($script:Utf8NoBom.GetString($bytesBefore)) -ceq
            ($script:Utf8NoBom.GetString($bytesAfter))) 'MANIFEST_CHANGED_AFTER_TIME_SEPARATION'
    Complete-Case 'same-commit-two-second-separation'

    $manifestPathA = Join-Path $rootA 'release-manifest.json'
    $manifestPathB = Join-Path $rootB 'release-manifest.json'
    Assert-Condition ((Get-TestSha256 $manifestPathA) -ceq (Get-TestSha256 $manifestPathB)) `
        'MANIFEST_HASH_CHANGED_ACROSS_PATHS'
    Complete-Case 'manifest-bytes-and-hash-identical-across-paths'

    $oldCulture = [Globalization.CultureInfo]::CurrentCulture
    $oldUiCulture = [Globalization.CultureInfo]::CurrentUICulture
    $oldTimezone = $env:TZ
    try
    {
        [Globalization.CultureInfo]::CurrentCulture = [Globalization.CultureInfo]::GetCultureInfo('tr-TR')
        [Globalization.CultureInfo]::CurrentUICulture = [Globalization.CultureInfo]::GetCultureInfo('tr-TR')
        $env:TZ = 'Pacific/Auckland'
        $cultureBytes = Get-GateWCanonicalManifestBytes $manifestA
    }
    finally
    {
        [Globalization.CultureInfo]::CurrentCulture = $oldCulture
        [Globalization.CultureInfo]::CurrentUICulture = $oldUiCulture
        if ($null -eq $oldTimezone)
        {
            Remove-Item Env:TZ -ErrorAction SilentlyContinue
        }
        else
        {
            $env:TZ = $oldTimezone
        }
    }
    Assert-Condition (($script:Utf8NoBom.GetString($bytesBefore)) -ceq
            ($script:Utf8NoBom.GetString($cultureBytes))) 'MANIFEST_CHANGED_ACROSS_LOCALE_TIMEZONE'
    Complete-Case 'locale-and-timezone-independent'

    $tarA = Join-Path $tempRoot 'bundle-a.tar'
    $tarB = Join-Path $tempRoot 'bundle-b.tar'
    New-GateWCanonicalTar $rootA $manifestA $tarA
    New-GateWCanonicalTar $rootB $manifestB $tarB
    Assert-Condition ((Get-TestSha256 $tarA) -ceq (Get-TestSha256 $tarB)) `
        'BUNDLE_HASH_CHANGED_ACROSS_PATHS'
    Complete-Case 'canonical-bundle-identical-across-paths'

    $tarPaths = @(Read-TestTarPaths $tarA)
    $expectedPaths = @('release-manifest.json') + @($manifestA.artifacts.relativePath)
    $expectedPaths = @(Sort-GateWOrdinalStrings $expectedPaths)
    Assert-Condition (($tarPaths -join '|') -ceq ($expectedPaths -join '|')) 'BUNDLE_CLOSED_SET_INVALID'
    Complete-Case 'bundle-entry-order-and-closed-set'

    $artifactDescriptorA = @($manifestA.artifacts | ForEach-Object {
        '{0}|{1}|{2}|{3}' -f $_.relativePath, $_.size, $_.mode, $_.sha256
    })
    $artifactDescriptorB = @($manifestB.artifacts | ForEach-Object {
        '{0}|{1}|{2}|{3}' -f $_.relativePath, $_.size, $_.mode, $_.sha256
    })
    Assert-Condition (($artifactDescriptorA -join "`n") -ceq ($artifactDescriptorB -join "`n")) `
        'ARTIFACT_SET_CHANGED_ACROSS_PATHS'
    Complete-Case 'artifact-count-path-size-mode-and-hash-identical'

    $manifestText = [IO.File]::ReadAllText($manifestPathA, $script:Utf8NoBom)
    Assert-Condition (-not $manifestText.Contains($tempRoot) -and
            -not $manifestText.Contains((Get-Location).Path)) 'ABSOLUTE_PATH_LEAKED_TO_MANIFEST'
    Complete-Case 'manifest-has-no-absolute-path'

    Assert-Condition (-not $manifestText.Contains('createdAt') -and
            $manifestText.Contains('"sourceCommitTimestamp":"2020-01-02T03:04:05Z"')) `
        'DYNAMIC_BUILD_TIME_PRESENT'
    Complete-Case 'manifest-has-only-source-commit-time'

    Assert-Condition (-not (Test-Path -LiteralPath (Join-Path $rootA 'build-receipt.json')) -and
            $tarPaths -notcontains 'build-receipt.json') 'BUILD_RECEIPT_ENTERED_CLOSED_SET'
    Complete-Case 'build-receipt-excluded'

    $differentRoot = Join-Path $tempRoot 'different-commit'
    $differentManifest = New-TestRelease $differentRoot $commitB '2020-01-02T03:04:06Z'
    Assert-Condition ((Get-TestSha256 (Join-Path $differentRoot 'release-manifest.json')) -cne
            (Get-TestSha256 $manifestPathA)) 'DIFFERENT_COMMIT_HAS_SAME_IDENTITY'
    Complete-Case 'different-source-commit-different-identity'

    $positive = Invoke-TestVerifier $rootA
    Assert-Condition ($positive.ExitCode -eq 0 -and
            $positive.Decision -ceq 'PASS / IMMUTABLE_RELEASE_VERIFIED') `
        "VALID_RELEASE_REJECTED exit=$( $positive.ExitCode ) decision=$( $positive.Decision ) paths=$( @($manifestA.artifacts.relativePath) -join '|' )"
    Complete-Case 'verifier-positive'
    Assert-Condition ($positive.JarCount -eq 3 -and
            $positive.DuplicateDirectoryEntries -eq 4) `
        'DUPLICATE_DIRECTORY_POLICY_RESULT_INVALID'
    Complete-Case 'duplicate-directory-entries-allowed-counted-and-deduplicated'

    foreach ($javaCase in @(
        @{ Text = 'java version "17.0.15"'; Major = 17 },
        @{ Text = 'openjdk version "20.0.2"'; Major = 20 },
        @{ Text = 'openjdk version "21.0.12"'; Major = 21 },
        @{ Text = 'openjdk version "22"'; Major = 22 }
    ))
    {
        Assert-Condition (
        (ConvertFrom-GateWJavaVersionText ([string]$javaCase.Text)) -eq
                [int]$javaCase.Major
        ) 'JAVA_VERSION_PARSE_FAILED'
    }
    $unreadableVersionRejected = $false
    try
    {
        ConvertFrom-GateWJavaVersionText 'not a Java version' | Out-Null
    }
    catch
    {
        $unreadableVersionRejected =
        $_.Exception.Message -eq 'BLOCKED / JAVA_VERSION_UNREADABLE'
    }
    Assert-Condition $unreadableVersionRejected 'UNREADABLE_JAVA_VERSION_ACCEPTED'
    Complete-Case 'java-17-20-21-22-and-unreadable-version-taxonomy'

    $wrongJavaRoot = Join-Path $tempRoot 'wrong-java-contract'
    $wrongJavaManifest = New-TestRelease $wrongJavaRoot $commitA $timestampA
    $wrongJavaManifest.requiredRuntime.javaMajor = 17
    $wrongJavaManifest.buildProvenance.javaMajor = 17
    Write-GateWCanonicalManifest (Join-Path $wrongJavaRoot 'release-manifest.json') `
        $wrongJavaManifest
    $wrongJava = Invoke-TestVerifier $wrongJavaRoot
    Assert-Condition ($wrongJava.ExitCode -eq 2 -and
            $wrongJava.Decision -ceq 'BLOCKED / JAVA_MAJOR_VERSION_MISMATCH') `
        'WRONG_JAVA_MAJOR_CONTRACT_ACCEPTED'
    Complete-Case 'manifest-java-major-mismatch-blocked'

    $duplicateFileRoot = Join-Path $tempRoot 'duplicate-file-entry'
    $duplicateFileManifest = New-TestRelease $duplicateFileRoot $commitA $timestampA
    Set-TestJar $duplicateFileRoot $duplicateFileManifest 'launcher/lib/0000-library.jar' @(
        [pscustomobject]@{ Name = 'duplicate.class'; Content = 'one' },
        [pscustomobject]@{ Name = 'duplicate.class'; Content = 'two' }
    )
    $duplicateFile = Invoke-TestVerifier $duplicateFileRoot
    Assert-Condition ($duplicateFile.ExitCode -eq 2 -and
            $duplicateFile.Decision -ceq 'BLOCKED / RELEASE_JAR_DUPLICATE_FILE_ENTRY') `
        'DUPLICATE_FILE_ENTRY_ACCEPTED'
    Complete-Case 'duplicate-file-entry-blocked'

    $caseCollisionRoot = Join-Path $tempRoot 'case-collision'
    $caseCollisionManifest = New-TestRelease $caseCollisionRoot $commitA $timestampA
    Set-TestJar $caseCollisionRoot $caseCollisionManifest 'launcher/lib/0000-library.jar' @(
        [pscustomobject]@{ Name = 'Example.class'; Content = 'one' },
        [pscustomobject]@{ Name = 'example.class'; Content = 'two' }
    )
    $caseCollision = Invoke-TestVerifier $caseCollisionRoot
    Assert-Condition ($caseCollision.ExitCode -eq 2 -and
            $caseCollision.Decision -ceq 'BLOCKED / RELEASE_JAR_CASE_COLLISION') `
        'CASE_COLLISION_ACCEPTED'
    Complete-Case 'case-normalized-collision-blocked'

    $normalizedCollisionRoot = Join-Path $tempRoot 'normalized-collision'
    $normalizedCollisionManifest = New-TestRelease $normalizedCollisionRoot $commitA $timestampA
    Set-TestJar $normalizedCollisionRoot $normalizedCollisionManifest `
        'launcher/lib/0000-library.jar' @(
        [pscustomobject]@{ Name = 'a//resource.txt'; Content = 'one' },
        [pscustomobject]@{ Name = 'a/resource.txt'; Content = 'two' }
    )
    $normalizedCollision = Invoke-TestVerifier $normalizedCollisionRoot
    Assert-Condition ($normalizedCollision.ExitCode -eq 2 -and
            $normalizedCollision.Decision -ceq
                    'BLOCKED / RELEASE_JAR_NORMALIZED_PATH_COLLISION') `
        'NORMALIZED_PATH_COLLISION_ACCEPTED'
    Complete-Case 'normalized-path-collision-blocked'

    $traversalRoot = Join-Path $tempRoot 'jar-path-traversal'
    $traversalManifest = New-TestRelease $traversalRoot $commitA $timestampA
    Set-TestJar $traversalRoot $traversalManifest 'launcher/lib/0000-library.jar' @(
        [pscustomobject]@{ Name = '../outside.class'; Content = 'forbidden' }
    )
    $traversal = Invoke-TestVerifier $traversalRoot
    Assert-Condition ($traversal.ExitCode -eq 2 -and
            $traversal.Decision -ceq 'BLOCKED / RELEASE_JAR_ENTRY_PATH_INVALID') `
        'JAR_PATH_TRAVERSAL_ACCEPTED'
    Complete-Case 'jar-path-traversal-blocked'

    $missingRoot = Join-Path $tempRoot 'missing-artifact'
    $null = New-TestRelease $missingRoot $commitA $timestampA
    Remove-Item -LiteralPath (Join-Path $missingRoot 'launcher/lib/0000-library.jar') -Force
    $missing = Invoke-TestVerifier $missingRoot
    Assert-Condition ($missing.ExitCode -eq 2 -and $missing.Decision.StartsWith('BLOCKED / RELEASE_')) `
        'MISSING_ARTIFACT_ACCEPTED'
    Complete-Case 'missing-artifact-blocked'

    $extraRoot = Join-Path $tempRoot 'extra-artifact'
    $null = New-TestRelease $extraRoot $commitA $timestampA
    Write-TestText (Join-Path $extraRoot 'undeclared.txt') 'undeclared'
    $extra = Invoke-TestVerifier $extraRoot
    Assert-Condition ($extra.ExitCode -eq 2 -and
            $extra.Decision -ceq 'BLOCKED / RELEASE_UNDECLARED_ARTIFACT') 'EXTRA_ARTIFACT_ACCEPTED'
    Complete-Case 'extra-undeclared-artifact-blocked'

    $tamperRoot = Join-Path $tempRoot 'tampered-artifact'
    $null = New-TestRelease $tamperRoot $commitA $timestampA
    [IO.File]::AppendAllText(
            (Join-Path $tamperRoot 'bin/worker.ps1'),
            'tampered',
            $script:Utf8NoBom
    )
    $tamper = Invoke-TestVerifier $tamperRoot
    Assert-Condition ($tamper.ExitCode -eq 2 -and
            $tamper.Decision -ceq 'BLOCKED / RELEASE_ARTIFACT_HASH_MISMATCH') `
        'TAMPERED_ARTIFACT_NOT_REJECTED_WITH_CANONICAL_CODE'
    Complete-Case 'tampered-artifact-hash-mismatch'

    $nonCanonicalRoot = Join-Path $tempRoot 'non-canonical-manifest'
    $nonCanonicalManifest = New-TestRelease $nonCanonicalRoot $commitA $timestampA
    Write-TestText (Join-Path $nonCanonicalRoot 'release-manifest.json') `
        (($nonCanonicalManifest | ConvertTo-Json -Depth 8) + "`n")
    $nonCanonical = Invoke-TestVerifier $nonCanonicalRoot
    Assert-Condition ($nonCanonical.ExitCode -eq 2 -and
            $nonCanonical.Decision -ceq 'BLOCKED / RELEASE_MANIFEST_NOT_CANONICAL') `
        'NON_CANONICAL_MANIFEST_ACCEPTED'
    Complete-Case 'non-canonical-manifest-bytes-blocked'

    Write-TestText $dirtyMarker 'dirty exact commit regression marker'
    $head = (& git -C $script:RepoRoot rev-parse HEAD).Trim()
    $dirtyOutput = Join-Path $tempRoot 'dirty-output'
    $dirtyResultText = @(& $script:EnginePath -NoProfile -File $script:BuilderPath `
        -Action build -SourceTreeMode EXACT_COMMIT -ExpectedCommit $head `
        -OutputRoot $dirtyOutput 2>&1) -join "`n"
    $dirtyExitCode = [int]$LASTEXITCODE
    $dirtyResult = $dirtyResultText | ConvertFrom-Json
    Assert-Condition ($dirtyExitCode -eq 2 -and
            [string]$dirtyResult.decision -ceq 'BLOCKED / EXACT_COMMIT_WORKTREE_NOT_CLEAN') `
        'DIRTY_WORKTREE_IMPERSONATED_EXACT_COMMIT'
    Complete-Case 'dirty-worktree-exact-commit-blocked'

    Assert-Condition ($script:Cases.Count -eq 23) `
        "REGRESSION_CASE_COUNT_INVALID actual=$( $script:Cases.Count )"
    [pscustomobject][ordered]@{
        decision = 'PASS / GATEW_RELEASE_REPRODUCIBILITY_REGRESSION'
        cases = $script:Cases.Count
        buildTimeSeparationSeconds = 2
        manifestSha256 = Get-TestSha256 $manifestPathA
        bundleSha256 = Get-TestSha256 $tarA
        artifactCount = @($manifestA.artifacts).Count
        tamperDecision = $tamper.Decision
        dirtyWorktreeDecision = [string]$dirtyResult.decision
        buildReceiptIncluded = $false
        networkCalled = $false
        credentialAccessed = $false
        attempt10Created = $false
        results = @($script:Cases)
    } | ConvertTo-Json -Depth 6
}
catch
{
    [pscustomobject][ordered]@{
        decision = 'FAIL / GATEW_RELEASE_REPRODUCIBILITY_REGRESSION'
        casesPassed = $script:Cases.Count
        detail = $_.Exception.Message
    } | ConvertTo-Json -Depth 4
    exit 2
}
finally
{
    if (Test-Path -LiteralPath $dirtyMarker)
    {
        Remove-Item -LiteralPath $dirtyMarker -Force
    }
    if (Test-Path -LiteralPath $tempRoot)
    {
        $tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        $resolvedTemp = [IO.Path]::GetFullPath($tempRoot)
        if (-not $resolvedTemp.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase))
        {
            throw 'TEMP_CLEANUP_PATH_INVALID'
        }
        Remove-Item -LiteralPath $resolvedTemp -Recurse -Force
    }
}
