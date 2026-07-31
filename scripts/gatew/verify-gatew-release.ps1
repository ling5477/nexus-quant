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

function Initialize-JarIntegrityTypes
{
    if ($null -ne ('NexusQuant.GateW.JarCrc32' -as [type]))
    {
        return
    }
    Add-Type -TypeDefinition @'
namespace NexusQuant.GateW
{
    public sealed class JarCrc32
    {
        private static readonly uint[] Table = CreateTable();
        private uint value = 0xffffffffu;

        private static uint[] CreateTable()
        {
            var table = new uint[256];
            for (uint index = 0; index < table.Length; index++)
            {
                var current = index;
                for (var bit = 0; bit < 8; bit++)
                {
                    current = (current & 1u) != 0
                        ? (current >> 1) ^ 0xedb88320u
                        : current >> 1;
                }
                table[index] = current;
            }
            return table;
        }

        public void Append(byte[] buffer, int count)
        {
            for (var index = 0; index < count; index++)
            {
                value = (value >> 8) ^ Table[(value ^ buffer[index]) & 0xffu];
            }
        }

        public uint Value { get { return value ^ 0xffffffffu; } }
    }
}
'@
}

function Read-JarCentralDirectory
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Limits
    )

    $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
    $reader = [IO.BinaryReader]::new($stream, [Text.Encoding]::UTF8, $true)
    try
    {
        if ($stream.Length -lt 22)
        {
            throw 'BLOCKED / RELEASE_JAR_INVALID'
        }
        $tailLength = [int][Math]::Min([long]65557, $stream.Length)
        [void]$stream.Seek(-$tailLength, [IO.SeekOrigin]::End)
        $tail = $reader.ReadBytes($tailLength)
        if ($tail.Length -ne $tailLength)
        {
            throw 'BLOCKED / RELEASE_JAR_INVALID'
        }
        $eocdInTail = -1
        for ($index = $tail.Length - 22; $index -ge 0; $index--)
        {
            if ($tail[$index] -eq 0x50 -and $tail[$index + 1] -eq 0x4b -and
                    $tail[$index + 2] -eq 0x05 -and $tail[$index + 3] -eq 0x06)
            {
                $commentLength = [BitConverter]::ToUInt16($tail, $index + 20)
                if ($index + 22 + $commentLength -eq $tail.Length)
                {
                    $eocdInTail = $index
                    break
                }
            }
        }
        if ($eocdInTail -lt 0)
        {
            throw 'BLOCKED / RELEASE_JAR_INVALID'
        }
        $eocdOffset = $stream.Length - $tailLength + $eocdInTail
        [void]$stream.Seek($eocdOffset + 4, [IO.SeekOrigin]::Begin)
        $diskNumber = $reader.ReadUInt16()
        $centralDisk = $reader.ReadUInt16()
        $entriesOnDisk = $reader.ReadUInt16()
        $entryCount = $reader.ReadUInt16()
        $centralSize = $reader.ReadUInt32()
        $centralOffset = $reader.ReadUInt32()
        $commentLength = $reader.ReadUInt16()
        if ($diskNumber -ne 0 -or $centralDisk -ne 0 -or $entriesOnDisk -ne $entryCount -or
                $entryCount -eq [uint16]::MaxValue -or $centralSize -eq [uint32]::MaxValue -or
                $centralOffset -eq [uint32]::MaxValue -or
                $eocdOffset + 22 + $commentLength -ne $stream.Length -or
                [long]$centralOffset + [long]$centralSize -ne $eocdOffset)
        {
            throw 'BLOCKED / RELEASE_JAR_INVALID'
        }
        if ([int]$entryCount -gt [int]$Limits.MaxEntryCount)
        {
            throw 'BLOCKED / RELEASE_JAR_ENTRY_COUNT_LIMIT_EXCEEDED'
        }

        [void]$stream.Seek([long]$centralOffset, [IO.SeekOrigin]::Begin)
        $records = [Collections.Generic.List[object]]::new()
        [long]$declaredTotal = 0
        $strictUtf8 = New-Object Text.UTF8Encoding($false, $true)
        for ($entryIndex = 0; $entryIndex -lt [int]$entryCount; $entryIndex++)
        {
            if ($reader.ReadUInt32() -ne [uint32]0x02014b50)
            {
                throw 'BLOCKED / RELEASE_JAR_INVALID'
            }
            [void]$reader.ReadUInt16()
            [void]$reader.ReadUInt16()
            $flags = $reader.ReadUInt16()
            $compressionMethod = $reader.ReadUInt16()
            [void]$reader.ReadUInt16()
            [void]$reader.ReadUInt16()
            $crc32 = $reader.ReadUInt32()
            $compressedSize = $reader.ReadUInt32()
            $uncompressedSize = $reader.ReadUInt32()
            $nameLength = $reader.ReadUInt16()
            $extraLength = $reader.ReadUInt16()
            $entryCommentLength = $reader.ReadUInt16()
            $entryDisk = $reader.ReadUInt16()
            [void]$reader.ReadUInt16()
            $externalAttributes = $reader.ReadUInt32()
            $localHeaderOffset = $reader.ReadUInt32()
            if (($flags -band [uint16]1) -ne 0 -or $compressionMethod -notin @(0, 8) -or
                    $entryDisk -ne 0 -or $compressedSize -eq [uint32]::MaxValue -or
                    $uncompressedSize -eq [uint32]::MaxValue -or
                    $localHeaderOffset -eq [uint32]::MaxValue -or $nameLength -eq 0)
            {
                throw 'BLOCKED / RELEASE_JAR_INVALID'
            }
            $nameBytes = $reader.ReadBytes([int]$nameLength)
            if ($nameBytes.Length -ne [int]$nameLength)
            {
                throw 'BLOCKED / RELEASE_JAR_INVALID'
            }
            try
            {
                if (($flags -band [uint16]0x0800) -ne 0)
                {
                    $name = $strictUtf8.GetString($nameBytes)
                }
                else
                {
                    if (@($nameBytes | Where-Object { $_ -gt 0x7f }).Count -ne 0)
                    {
                        throw 'BLOCKED / RELEASE_JAR_ENTRY_PATH_INVALID'
                    }
                    $name = [Text.Encoding]::ASCII.GetString($nameBytes)
                }
            }
            catch
            {
                if ($_.Exception.Message -match '^BLOCKED / ')
                {
                    throw
                }
                throw 'BLOCKED / RELEASE_JAR_ENTRY_PATH_INVALID'
            }
            if ([long]$uncompressedSize -gt [long]$Limits.MaxEntryUncompressedBytes)
            {
                throw 'BLOCKED / RELEASE_JAR_ENTRY_SIZE_LIMIT_EXCEEDED'
            }
            $declaredTotal += [long]$uncompressedSize
            if ($declaredTotal -gt [long]$Limits.MaxTotalUncompressedBytes)
            {
                throw 'BLOCKED / RELEASE_JAR_TOTAL_UNCOMPRESSED_LIMIT_EXCEEDED'
            }
            $records.Add([pscustomobject]@{
                Name = $name
                Flags = $flags
                CompressionMethod = $compressionMethod
                Crc32 = $crc32
                CompressedSize = [long]$compressedSize
                UncompressedSize = [long]$uncompressedSize
                ExternalAttributes = $externalAttributes
            })
            [void]$stream.Seek([long]$extraLength + [long]$entryCommentLength, [IO.SeekOrigin]::Current)
        }
        if ($stream.Position -ne [long]$centralOffset + [long]$centralSize)
        {
            throw 'BLOCKED / RELEASE_JAR_INVALID'
        }
        return @($records)
    }
    catch
    {
        if ($_.Exception.Message -match '^BLOCKED / ')
        {
            throw
        }
        throw 'BLOCKED / RELEASE_JAR_INVALID'
    }
    finally
    {
        $reader.Dispose()
        $stream.Dispose()
    }
}

function Test-JarDuplicateEntryPolicy
{
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$Root
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    Initialize-JarIntegrityTypes
    $limits = Get-GateWJarIntegrityContract
    $jarCount = 0
    $jarEntryCount = 0
    [long]$jarEntryBytesRead = 0
    $duplicateDirectoryEntries = 0
    $buffer = New-Object byte[] ([int]$limits.ReadBufferBytes)
    try
    {
        foreach ($artifact in @($Manifest.artifacts | Where-Object {
        [string]$_.relativePath -like '*.jar' -and
                [string]$_.role -in @('launcher-test-support', 'launcher-module', 'runtime-library')
        }))
        {
        $jarCount++
        $path = Join-Path $Root ([string]$artifact.relativePath)
        $centralRecords = @(Read-JarCentralDirectory $path $limits)
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
            if ($archive.Entries.Count -ne $centralRecords.Count)
            {
                throw 'BLOCKED / RELEASE_JAR_INVALID'
            }
            $seen = @{ }
            [long]$jarBytesRead = 0
            for ($entryIndex = 0; $entryIndex -lt $archive.Entries.Count; $entryIndex++)
            {
                $entry = $archive.Entries[$entryIndex]
                $central = $centralRecords[$entryIndex]
                if ([string]$entry.FullName -cne [string]$central.Name -or
                        [long]$entry.Length -ne [long]$central.UncompressedSize -or
                        [long]$entry.CompressedLength -ne [long]$central.CompressedSize)
                {
                    throw 'BLOCKED / RELEASE_JAR_INVALID'
                }
                $descriptor = Get-JarEntryDescriptor ([string]$entry.FullName)
                $metadataDirectory = (([uint32]$central.ExternalAttributes -band [uint32]0x10) -ne 0 -or
                        (([uint32]$central.ExternalAttributes -shr 16) -band [uint32]0xf000) -eq
                        [uint32]0x4000)
                if ($metadataDirectory -and -not [bool]$descriptor.IsDirectory)
                {
                    throw 'BLOCKED / RELEASE_JAR_ENTRY_PATH_INVALID'
                }
                if ([bool]$descriptor.IsDirectory -and [long]$entry.Length -ne 0)
                {
                    throw 'BLOCKED / RELEASE_JAR_DIRECTORY_PAYLOAD_NOT_EMPTY'
                }

                $entryStream = $null
                [long]$entryBytesRead = 0
                $crc32 = [NexusQuant.GateW.JarCrc32]::new()
                try
                {
                    try
                    {
                        $entryStream = $entry.Open()
                        while ($true)
                        {
                            $read = $entryStream.Read($buffer, 0, $buffer.Length)
                            if ($read -eq 0)
                            {
                                break
                            }
                            $entryBytesRead += [long]$read
                            $jarBytesRead += [long]$read
                            $jarEntryBytesRead += [long]$read
                            if ($entryBytesRead -gt [long]$limits.MaxEntryUncompressedBytes)
                            {
                                throw 'BLOCKED / RELEASE_JAR_ENTRY_SIZE_LIMIT_EXCEEDED'
                            }
                            if ($jarBytesRead -gt [long]$limits.MaxTotalUncompressedBytes)
                            {
                                throw 'BLOCKED / RELEASE_JAR_TOTAL_UNCOMPRESSED_LIMIT_EXCEEDED'
                            }
                            $crc32.Append($buffer, $read)
                        }
                    }
                    catch
                    {
                        if ($_.Exception.Message -match '^BLOCKED / ')
                        {
                            throw
                        }
                        throw 'BLOCKED / RELEASE_JAR_ENTRY_READ_FAILED'
                    }
                }
                finally
                {
                    if ($null -ne $entryStream)
                    {
                        $entryStream.Dispose()
                    }
                }
                if ($entryBytesRead -ne [long]$central.UncompressedSize)
                {
                    throw 'BLOCKED / RELEASE_JAR_ENTRY_TRUNCATED'
                }
                if ([uint32]$crc32.Value -ne [uint32]$central.Crc32)
                {
                    throw 'BLOCKED / RELEASE_JAR_ENTRY_CRC_MISMATCH'
                }
                if ([bool]$descriptor.IsDirectory -and $entryBytesRead -ne 0)
                {
                    throw 'BLOCKED / RELEASE_JAR_DIRECTORY_PAYLOAD_NOT_EMPTY'
                }
                $jarEntryCount++
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
    }
    finally
    {
        [Array]::Clear($buffer, 0, $buffer.Length)
    }
    if ($jarCount -lt 1)
    {
        throw 'BLOCKED / RELEASE_JAR_INVALID'
    }
    return [pscustomobject]@{
        JarCount = $jarCount
        JarEntryCount = $jarEntryCount
        JarEntryBytesRead = $jarEntryBytesRead
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
        jarEntryCount = [int]$jarPolicy.JarEntryCount
        jarEntryBytesRead = [long]$jarPolicy.JarEntryBytesRead
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
