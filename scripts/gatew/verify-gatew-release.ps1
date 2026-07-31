[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReleaseRoot,

    [string]$ExpectedReleaseId,
    [string]$ExpectedManifestSha256,
    [string]$BundlePath,
    [string]$ExpectedBundleSha256,
    [switch]$SkipPosix
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ManifestName = 'release-manifest.json'
$script:ReleaseIdPattern = '^(?:[a-f0-9]{40}|candidate-[a-f0-9]{12}-[a-f0-9]{16})$'
$script:Sha256Pattern = '^[a-f0-9]{64}$'
$script:CommitPattern = '^[a-f0-9]{40}$'
$script:RequiredJavaMajor = 21
$script:CanonicalMavenCommand =
'mvn --offline --quiet -f backend/pom.xml -pl nq-app -am -DskipTests clean package'
$script:AllowedRoles = @(
    'worker-helper', 'control-helper', 'failclose-helper', 'contract-library',
    'release-verifier', 'release-installer', 'release-contract',
    'systemd-worker-unit', 'systemd-failclose-unit', 'launcher-test-support',
    'launcher-module', 'runtime-library'
)
$script:ExecutableRoles = @(
    'worker-helper', 'control-helper', 'failclose-helper', 'release-verifier', 'release-installer'
)
$script:LfRoles = @(
    'worker-helper', 'control-helper', 'failclose-helper', 'contract-library',
    'release-verifier', 'release-installer', 'release-contract',
    'systemd-worker-unit', 'systemd-failclose-unit'
)
$script:ReleaseContractPath = Join-Path $PSScriptRoot 'gatew-release-contract.psm1'

Import-Module $script:ReleaseContractPath -Force -DisableNameChecking

function Test-LinuxPlatform
{
    $platform = Get-Variable -Name IsLinux -ErrorAction SilentlyContinue
    return $null -ne $platform -and [bool]$platform.Value
}

function Get-Sha256File
{
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function ConvertFrom-ReleaseJson
{
    param([Parameter(Mandatory = $true)][string]$Text)

    $command = Get-Command ConvertFrom-Json -ErrorAction Stop
    if ( $command.Parameters.ContainsKey('DateKind'))
    {
        return ($Text | ConvertFrom-Json -DateKind String)
    }
    return ($Text | ConvertFrom-Json)
}

function Assert-NoSymlink
{
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path))
    {
        throw 'BLOCKED / RELEASE_PATH_INVALID'
    }
    $item = Get-Item -LiteralPath $Path -Force
    if ($null -ne $item.LinkType -or $item.Attributes.ToString() -match 'ReparsePoint')
    {
        throw 'BLOCKED / RELEASE_SYMLINK_FORBIDDEN'
    }
}

function Assert-PathComponentsNoSymlink
{
    param([Parameter(Mandatory = $true)][string]$Path)

    $normalized = [IO.Path]::GetFullPath($Path)
    $pathRoot = [IO.Path]::GetPathRoot($normalized)
    if ( [string]::IsNullOrWhiteSpace($pathRoot))
    {
        throw 'BLOCKED / RELEASE_PATH_INVALID'
    }
    $current = $pathRoot
    foreach ($segment in @(
    $normalized.Substring($pathRoot.Length) -split '[\\/]' |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    ))
    {
        $current = Join-Path $current $segment
        if (-not (Test-Path -LiteralPath $current))
        {
            throw 'BLOCKED / RELEASE_PATH_INVALID'
        }
        Assert-NoSymlink $current
    }
}

function Resolve-ReleaseRoot
{
    param([Parameter(Mandatory = $true)][string]$Path)

    $normalized = [IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $normalized -PathType Container))
    {
        throw 'BLOCKED / RELEASE_ROOT_MISSING'
    }
    if (Test-LinuxPlatform)
    {
        $resolved = @(& '/usr/bin/readlink' -f -- $normalized 2> $null)
        if ($LASTEXITCODE -ne 0 -or $resolved.Count -ne 1 -or
                [string]::IsNullOrWhiteSpace([string]$resolved[0]))
        {
            throw 'BLOCKED / RELEASE_ROOT_INVALID'
        }
        $normalized = [IO.Path]::GetFullPath(([string]$resolved[0]).Trim())
    }
    else
    {
        $normalized = [IO.Path]::GetFullPath((Get-Item -LiteralPath $normalized -Force).FullName)
    }
    Assert-PathComponentsNoSymlink $normalized
    return $normalized.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
}

function Assert-RelativePath
{
    param([Parameter(Mandatory = $true)][string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -match '\\' -or
            $Value.StartsWith('/') -or $Value -match '(^|/)\.\.(/|$)' -or
            $Value -notmatch '^[A-Za-z0-9][A-Za-z0-9_@./-]*$')
    {
        throw 'BLOCKED / RELEASE_ARTIFACT_PATH_INVALID'
    }
}

function Assert-ExactFields
{
    param(
        [Parameter(Mandatory = $true)]$Value,
        [Parameter(Mandatory = $true)][string[]]$Expected
    )

    if ((@($Value.PSObject.Properties.Name) -join '|') -cne ($Expected -join '|'))
    {
        throw 'BLOCKED / RELEASE_MANIFEST_SCHEMA_INVALID'
    }
}

function Assert-NoSecretFieldNames
{
    param([AllowNull()]$Value)

    if ($null -eq $Value)
    {
        return
    }
    if ($Value -is [string] -or $Value.GetType().IsValueType)
    {
        return
    }
    if ($Value -is [System.Collections.IDictionary])
    {
        foreach ($key in $Value.Keys)
        {
            if ([string]$key -match '(?i)(credential|password|master.?key|api.?key|secret|passphrase|raw.?environment|connection.?string)')
            {
                throw 'BLOCKED / RELEASE_MANIFEST_SECRET_FIELD_FORBIDDEN'
            }
            Assert-NoSecretFieldNames $Value[$key]
        }
        return
    }
    if ($Value -is [System.Collections.IEnumerable])
    {
        foreach ($entry in $Value)
        {
            Assert-NoSecretFieldNames $entry
        }
        return
    }
    foreach ($property in @($Value.PSObject.Properties))
    {
        if ($property.Name -match '(?i)(credential|password|master.?key|api.?key|secret|passphrase|raw.?environment|connection.?string)')
        {
            throw 'BLOCKED / RELEASE_MANIFEST_SECRET_FIELD_FORBIDDEN'
        }
        Assert-NoSecretFieldNames $property.Value
    }
}

function Get-PosixMetadata
{
    param([Parameter(Mandatory = $true)][string]$Path)

    $output = @(& '/usr/bin/stat' -c '%a|%U|%G|%F' -- $Path 2> $null)
    if ($LASTEXITCODE -ne 0 -or $output.Count -ne 1)
    {
        throw 'BLOCKED / RELEASE_POSIX_METADATA_INVALID'
    }
    $parts = ([string]$output[0]).Split('|')
    if ($parts.Count -ne 4)
    {
        throw 'BLOCKED / RELEASE_POSIX_METADATA_INVALID'
    }
    return [pscustomobject]@{
        Mode = $parts[0]
        Owner = $parts[1]
        Group = $parts[2]
        Type = $parts[3]
    }
}

function Assert-PosixContract
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$ExpectedMode,
        [Parameter(Mandatory = $true)][string]$ExpectedType
    )

    $metadata = Get-PosixMetadata $Path
    $normalizedMode = $ExpectedMode.TrimStart('0')
    if ($metadata.Mode -ne $normalizedMode -or $metadata.Owner -ne 'root' -or
            $metadata.Group -ne 'root' -or $metadata.Type -notlike "*$ExpectedType*")
    {
        throw 'BLOCKED / RELEASE_POSIX_CONTRACT_INVALID'
    }
}

function Assert-LineEndingPolicy
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Policy
    )

    if ($Policy -eq 'BINARY')
    {
        return
    }
    if ($Policy -ne 'LF')
    {
        throw 'BLOCKED / RELEASE_LINE_ENDING_POLICY_INVALID'
    }
    $bytes = [IO.File]::ReadAllBytes($Path)
    try
    {
        if ($bytes -contains 13)
        {
            throw 'BLOCKED / RELEASE_CR_FORBIDDEN'
        }
        $text = [Text.Encoding]::UTF8.GetString($bytes)
        $roundTrip = [Text.UTF8Encoding]::new($false).GetBytes($text)
        if ($roundTrip.Length -ne $bytes.Length)
        {
            throw 'BLOCKED / RELEASE_TEXT_ENCODING_INVALID'
        }
        for ($index = 0; $index -lt $bytes.Length; $index++) {
            if ($roundTrip[$index] -ne $bytes[$index])
            {
                throw 'BLOCKED / RELEASE_TEXT_ENCODING_INVALID'
            }
        }
    }
    finally
    {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Assert-SystemdReleaseBinding
{
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$Root
    )

    $expectedRoot = if ([string]$Manifest.sourceTreeMode -eq 'CANDIDATE')
    {
        "/opt/nexus-quant/releases/$( [string]$Manifest.releaseId )"
    }
    else
    {
        '/opt/nexus-quant/current'
    }
    foreach ($relativePath in @(
        'systemd/nq-gatew-soak@.service',
        'systemd/nq-gatew-soak-failclose@.service'
    ))
    {
        $text = [IO.File]::ReadAllText((Join-Path $Root $relativePath))
        if (-not $text.Contains("Documentation=file:$expectedRoot/release-manifest.json") -or
                -not $text.Contains("Environment=NQ_GATEW_RELEASE_ROOT=$expectedRoot"))
        {
            throw 'BLOCKED / RELEASE_SYSTEMD_BINDING_INVALID'
        }
        if ([string]$Manifest.sourceTreeMode -eq 'CANDIDATE' -and
                $text.Contains('/opt/nexus-quant/current'))
        {
            throw 'BLOCKED / RELEASE_SYSTEMD_BINDING_INVALID'
        }
    }
}

function Get-JarEntryDescriptor
{
    param([Parameter(Mandatory = $true)][string]$Name)

    if ([string]::IsNullOrWhiteSpace($Name) -or $Name -match '\\' -or
            $Name.StartsWith('/') -or $Name -match '^[A-Za-z]:' -or
            $Name.IndexOf([char]0) -ge 0)
    {
        throw 'BLOCKED / RELEASE_JAR_ENTRY_PATH_INVALID'
    }
    $isDirectory = $Name.EndsWith('/')
    $segments = @()
    foreach ($segment in @($Name.TrimEnd('/') -split '/'))
    {
        if ($segment -eq '..')
        {
            throw 'BLOCKED / RELEASE_JAR_ENTRY_PATH_INVALID'
        }
        if ([string]::IsNullOrEmpty($segment) -or $segment -eq '.')
        {
            continue
        }
        $segments += $segment
    }
    if ($segments.Count -eq 0)
    {
        throw 'BLOCKED / RELEASE_JAR_ENTRY_PATH_INVALID'
    }
    $canonical = $segments -join '/'
    if ($isDirectory)
    {
        $canonical += '/'
    }
    return [pscustomobject]@{
        Raw = $Name
        Canonical = $canonical
        Folded = $canonical.TrimEnd('/').ToUpperInvariant()
        IsDirectory = $isDirectory
    }
}

function Test-JarDuplicateEntryPolicy
{
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$Root
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $jarCount = 0
    $duplicateDirectoryEntries = 0
    foreach ($artifact in @($Manifest.artifacts | Where-Object {
        [string]$_.relativePath -like '*.jar' -and
                [string]$_.role -in @('launcher-test-support', 'launcher-module', 'runtime-library')
    }))
    {
        $jarCount++
        $path = Join-Path $Root ([string]$artifact.relativePath)
        try
        {
            $archive = [IO.Compression.ZipFile]::OpenRead($path)
        }
        catch
        {
            throw 'BLOCKED / RELEASE_JAR_INVALID'
        }
        try
        {
            $seen = @{ }
            foreach ($entry in @($archive.Entries))
            {
                $descriptor = Get-JarEntryDescriptor ([string]$entry.FullName)
                $key = [string]$descriptor.Folded
                if (-not $seen.ContainsKey($key))
                {
                    $seen[$key] = $descriptor
                    continue
                }
                $existing = $seen[$key]
                if ([bool]$existing.IsDirectory -and [bool]$descriptor.IsDirectory -and
                        [string]$existing.Raw -ceq [string]$descriptor.Raw)
                {
                    $duplicateDirectoryEntries++
                    continue
                }
                if ([string]$existing.Raw -ceq [string]$descriptor.Raw)
                {
                    throw 'BLOCKED / RELEASE_JAR_DUPLICATE_FILE_ENTRY'
                }
                if ([string]$existing.Canonical -cne [string]$descriptor.Canonical -and
                        [string]::Equals(
                                [string]$existing.Canonical,
                                [string]$descriptor.Canonical,
                                [StringComparison]::OrdinalIgnoreCase
                        ))
                {
                    throw 'BLOCKED / RELEASE_JAR_CASE_COLLISION'
                }
                throw 'BLOCKED / RELEASE_JAR_NORMALIZED_PATH_COLLISION'
            }
        }
        finally
        {
            $archive.Dispose()
        }
    }
    if ($jarCount -lt 1)
    {
        throw 'BLOCKED / RELEASE_JAR_INVALID'
    }
    return [pscustomobject]@{
        JarCount = $jarCount
        DuplicateDirectoryEntries = $duplicateDirectoryEntries
    }
}

function Assert-ManifestContract
{
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$Root
    )

    Assert-ExactFields $Manifest @(
        'schemaVersion', 'releaseId', 'sourceCommit', 'sourceCommitTimestamp', 'sourceTreeMode',
        'baseCommit', 'candidateDiffSha256', 'requiredRuntime', 'buildProvenance',
        'lineEndingPolicy', 'artifacts'
    )
    Assert-NoSecretFieldNames $Manifest
    if ([string]$Manifest.schemaVersion -ne 'nq-gatew-release-v3' -or
            [string]$Manifest.releaseId -cnotmatch $script:ReleaseIdPattern -or
            [string]$Manifest.sourceCommit -cnotmatch $script:CommitPattern -or
            [string]$Manifest.sourceTreeMode -notin @('CANDIDATE', 'EXACT_COMMIT') -or
            [string]$Manifest.lineEndingPolicy -ne 'LF')
    {
        throw 'BLOCKED / RELEASE_MANIFEST_SCHEMA_INVALID'
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedReleaseId) -and
            [string]$Manifest.releaseId -cne $ExpectedReleaseId)
    {
        throw 'BLOCKED / RELEASE_ID_MISMATCH'
    }
    $sourceCommitTimestamp = [DateTimeOffset]::MinValue
    if (-not [DateTimeOffset]::TryParseExact(
            [string]$Manifest.sourceCommitTimestamp,
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::AssumeUniversal,
            [ref]$sourceCommitTimestamp
    ))
    {
        throw 'BLOCKED / RELEASE_MANIFEST_SCHEMA_INVALID'
    }
    if ([string]$Manifest.sourceTreeMode -eq 'EXACT_COMMIT')
    {
        if ([string]$Manifest.releaseId -cne [string]$Manifest.sourceCommit -or
                $null -ne $Manifest.baseCommit -or $null -ne $Manifest.candidateDiffSha256)
        {
            throw 'BLOCKED / RELEASE_EXACT_COMMIT_CONTRACT_INVALID'
        }
    }
    else
    {
        if ([string]$Manifest.baseCommit -cnotmatch $script:CommitPattern -or
                [string]$Manifest.baseCommit -cne [string]$Manifest.sourceCommit -or
                [string]$Manifest.candidateDiffSha256 -cnotmatch $script:Sha256Pattern)
        {
            throw 'BLOCKED / RELEASE_CANDIDATE_CONTRACT_INVALID'
        }
    }
    Assert-ExactFields $Manifest.requiredRuntime @('os', 'powershellMajor', 'javaMajor', 'systemd')
    if ([string]$Manifest.requiredRuntime.os -ne 'linux' -or
            [int]$Manifest.requiredRuntime.powershellMajor -ne 7 -or
            $Manifest.requiredRuntime.systemd -isnot [bool] -or -not [bool]$Manifest.requiredRuntime.systemd)
    {
        throw 'BLOCKED / RELEASE_RUNTIME_CONTRACT_INVALID'
    }
    if ([int]$Manifest.requiredRuntime.javaMajor -ne $script:RequiredJavaMajor)
    {
        throw 'BLOCKED / JAVA_MAJOR_VERSION_MISMATCH'
    }
    Assert-ExactFields $Manifest.buildProvenance @(
        'mavenCommand', 'javaMajor', 'cleanDetachedWorktree'
    )
    if ([int]$Manifest.buildProvenance.javaMajor -ne $script:RequiredJavaMajor)
    {
        throw 'BLOCKED / JAVA_MAJOR_VERSION_MISMATCH'
    }
    if ([string]$Manifest.buildProvenance.mavenCommand -cne $script:CanonicalMavenCommand -or
            $Manifest.buildProvenance.cleanDetachedWorktree -isnot [bool] -or
            ([string]$Manifest.sourceTreeMode -eq 'EXACT_COMMIT' -and
                    -not [bool]$Manifest.buildProvenance.cleanDetachedWorktree))
    {
        throw 'BLOCKED / RELEASE_BUILD_PROVENANCE_INVALID'
    }
    if (@($Manifest.artifacts).Count -lt 10)
    {
        throw 'BLOCKED / RELEASE_MANIFEST_INCOMPLETE'
    }

    $seen = @{ }
    $lastPath = ''
    foreach ($artifact in @($Manifest.artifacts))
    {
        Assert-ExactFields $artifact @(
            'relativePath', 'size', 'sha256', 'mode', 'lineEndingPolicy', 'entrypoint', 'role'
        )
        $relativePath = [string]$artifact.relativePath
        Assert-RelativePath $relativePath
        if ($relativePath -eq $script:ManifestName -or $seen.ContainsKey($relativePath) -or
                (-not [string]::IsNullOrEmpty($lastPath) -and
                        [string]::CompareOrdinal($lastPath, $relativePath) -ge 0))
        {
            throw 'BLOCKED / RELEASE_MANIFEST_ARTIFACT_ORDER_INVALID'
        }
        $seen[$relativePath] = $true
        $lastPath = $relativePath
        if ([long]$artifact.size -lt 0 -or [string]$artifact.sha256 -cnotmatch $script:Sha256Pattern -or
                [string]$artifact.role -notin $script:AllowedRoles -or
                $artifact.entrypoint -isnot [bool])
        {
            throw 'BLOCKED / RELEASE_MANIFEST_SCHEMA_INVALID'
        }
        $expectedMode = if ([string]$artifact.role -in $script:ExecutableRoles)
        {
            '0755'
        }
        else
        {
            '0644'
        }
        $expectedLineEnding = if ([string]$artifact.role -in $script:LfRoles)
        {
            'LF'
        }
        else
        {
            'BINARY'
        }
        $expectedEntrypoint = [string]$artifact.role -in $script:ExecutableRoles
        if ([string]$artifact.mode -ne $expectedMode -or
                [string]$artifact.lineEndingPolicy -ne $expectedLineEnding -or
                [bool]$artifact.entrypoint -ne $expectedEntrypoint)
        {
            throw 'BLOCKED / RELEASE_ARTIFACT_ROLE_CONTRACT_INVALID'
        }
        $path = [IO.Path]::GetFullPath((Join-Path $Root $relativePath))
        if (-not $path.StartsWith($Root + [IO.Path]::DirectorySeparatorChar,
                $( if (Test-LinuxPlatform)
                {
                    [StringComparison]::Ordinal
                }
                else
                {
                    [StringComparison]::OrdinalIgnoreCase
                } )) -or
                -not (Test-Path -LiteralPath $path -PathType Leaf))
        {
            throw 'BLOCKED / RELEASE_ARTIFACT_PATH_INVALID'
        }
        Assert-PathComponentsNoSymlink $path
        $item = Get-Item -LiteralPath $path -Force
        if ([long]$item.Length -ne [long]$artifact.size -or
                (Get-Sha256File $path) -cne [string]$artifact.sha256)
        {
            throw 'BLOCKED / RELEASE_ARTIFACT_HASH_MISMATCH'
        }
        Assert-LineEndingPolicy $path ([string]$artifact.lineEndingPolicy)
        if ((Test-LinuxPlatform) -and -not $SkipPosix)
        {
            Assert-PosixContract $path ([string]$artifact.mode) 'regular file'
        }
    }

    foreach ($requiredRole in @(
        'worker-helper', 'control-helper', 'failclose-helper', 'contract-library',
        'release-verifier', 'release-installer', 'release-contract',
        'systemd-worker-unit', 'systemd-failclose-unit', 'launcher-test-support', 'launcher-module', 'runtime-library'
    ))
    {
        if (@($Manifest.artifacts | Where-Object { [string]$_.role -eq $requiredRole }).Count -lt 1)
        {
            throw 'BLOCKED / RELEASE_MANIFEST_INCOMPLETE'
        }
    }
    Assert-SystemdReleaseBinding $Manifest $Root

    $actual = @(
    Get-ChildItem -LiteralPath $Root -File -Recurse -Force |
            ForEach-Object {
                Assert-NoSymlink $_.FullName
                $_.FullName.Substring($Root.Length).TrimStart('/', '\').Replace('\', '/')
            } |
            Where-Object { $_ -ne $script:ManifestName } |
            Sort-Object
    )
    $declared = @($seen.Keys | Sort-Object)
    if (($actual -join '|') -cne ($declared -join '|'))
    {
        throw 'BLOCKED / RELEASE_UNDECLARED_ARTIFACT'
    }

    foreach ($directory in @(Get-ChildItem -LiteralPath $Root -Directory -Recurse -Force))
    {
        Assert-NoSymlink $directory.FullName
        if ((Test-LinuxPlatform) -and -not $SkipPosix)
        {
            Assert-PosixContract $directory.FullName '0755' 'directory'
        }
    }
}

try
{
    $resolvedRoot = Resolve-ReleaseRoot $ReleaseRoot
    $manifestPath = Join-Path $resolvedRoot $script:ManifestName
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf))
    {
        throw 'BLOCKED / RELEASE_MANIFEST_MISSING'
    }
    Assert-PathComponentsNoSymlink $manifestPath
    $manifestSha256 = Get-Sha256File $manifestPath
    if (-not [string]::IsNullOrWhiteSpace($ExpectedManifestSha256) -and
            ($ExpectedManifestSha256 -cnotmatch $script:Sha256Pattern -or
                    $manifestSha256 -cne $ExpectedManifestSha256))
    {
        throw 'BLOCKED / RELEASE_MANIFEST_HASH_MISMATCH'
    }
    if ((Test-LinuxPlatform) -and -not $SkipPosix)
    {
        if ([Environment]::UserName -ne 'root')
        {
            throw 'BLOCKED / ROOT_RELEASE_VERIFY_REQUIRED'
        }
        Assert-PosixContract $resolvedRoot '0755' 'directory'
        Assert-PosixContract $manifestPath '0644' 'regular file'
        foreach ($requiredPath in @('/usr/bin/pwsh', '/usr/bin/java', '/usr/bin/systemctl'))
        {
            if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf))
            {
                throw 'BLOCKED / RELEASE_RUNTIME_MISSING'
            }
        }
        if ($PSVersionTable.PSVersion.Major -lt 7)
        {
            throw 'BLOCKED / RELEASE_RUNTIME_MISSING'
        }
    }
    $manifest = ConvertFrom-ReleaseJson (Get-Content -LiteralPath $manifestPath -Raw)
    Assert-ManifestContract $manifest $resolvedRoot
    $javaPath = if (Test-LinuxPlatform)
    {
        '/usr/bin/java'
    }
    else
    {
        $null
    }
    $actualJavaMajor = Get-GateWJavaRuntimeMajor $javaPath
    if ($actualJavaMajor -ne [int]$manifest.requiredRuntime.javaMajor)
    {
        throw 'BLOCKED / JAVA_MAJOR_VERSION_MISMATCH'
    }
    $jarPolicy = Test-JarDuplicateEntryPolicy $manifest $resolvedRoot
    $actualManifestBytes = [IO.File]::ReadAllBytes($manifestPath)
    $canonicalManifestBytes = Get-GateWCanonicalManifestBytes $manifest
    try
    {
        if ($actualManifestBytes.Length -ne $canonicalManifestBytes.Length)
        {
            throw 'BLOCKED / RELEASE_MANIFEST_NOT_CANONICAL'
        }
        for ($index = 0; $index -lt $actualManifestBytes.Length; $index++)
        {
            if ($actualManifestBytes[$index] -ne $canonicalManifestBytes[$index])
            {
                throw 'BLOCKED / RELEASE_MANIFEST_NOT_CANONICAL'
            }
        }
    }
    finally
    {
        [Array]::Clear($actualManifestBytes, 0, $actualManifestBytes.Length)
        [Array]::Clear($canonicalManifestBytes, 0, $canonicalManifestBytes.Length)
    }
    $bundleSha256 = $null
    if (-not [string]::IsNullOrWhiteSpace($BundlePath) -or
            -not [string]::IsNullOrWhiteSpace($ExpectedBundleSha256))
    {
        if ([string]::IsNullOrWhiteSpace($BundlePath) -or
                -not (Test-Path -LiteralPath $BundlePath -PathType Leaf) -or
                $ExpectedBundleSha256 -cnotmatch $script:Sha256Pattern)
        {
            throw 'BLOCKED / RELEASE_BUNDLE_HASH_MISMATCH'
        }
        $bundleSha256 = Get-Sha256File ([IO.Path]::GetFullPath($BundlePath))
        if ($bundleSha256 -cne $ExpectedBundleSha256)
        {
            throw 'BLOCKED / RELEASE_BUNDLE_HASH_MISMATCH'
        }
    }
    [pscustomobject]@{
        decision = 'PASS / IMMUTABLE_RELEASE_VERIFIED'
        releaseId = [string]$manifest.releaseId
        sourceCommit = [string]$manifest.sourceCommit
        sourceTreeMode = [string]$manifest.sourceTreeMode
        releaseRoot = $resolvedRoot
        manifestSha256 = $manifestSha256
        artifactCount = @($manifest.artifacts).Count
        jarCount = [int]$jarPolicy.JarCount
        duplicateDirectoryEntries = [int]$jarPolicy.DuplicateDirectoryEntries
        requiredJavaMajor = [int]$manifest.requiredRuntime.javaMajor
        actualJavaMajor = $actualJavaMajor
        bundleSha256 = $bundleSha256
        posixVerified = (Test-LinuxPlatform) -and -not $SkipPosix
    } | ConvertTo-Json -Depth 6
}
catch
{
    $message = if ($_.Exception.Message -match '^(BLOCKED|FAIL) / [A-Z0-9_]+$')
    {
        $_.Exception.Message
    }
    else
    {
        'FAIL / RELEASE_VERIFY_INTERNAL_ERROR'
    }
    [pscustomobject]@{ decision = $message } | ConvertTo-Json
    exit 2
}
