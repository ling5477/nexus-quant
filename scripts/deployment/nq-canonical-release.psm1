Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:Utf8NoBom = [Text.UTF8Encoding]::new($false)
$script:CommitPattern = '^[0-9a-f]{40}$'
$script:Sha256Pattern = '^[0-9a-f]{64}$'
$script:ReleaseIdPattern = '^(?:nq-[0-9a-f]{12}-[0-9a-f]{16}|nq-test-[0-9a-f]{24})$'
$script:ManifestSchema = 'nq-canonical-release.v1'

function Get-NqSha256Bytes {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        return -join ($algorithm.ComputeHash($Bytes) | ForEach-Object { $_.ToString('x2') })
    } finally {
        $algorithm.Dispose()
    }
}

function Get-NqSha256Text {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text)
    return Get-NqSha256Bytes $script:Utf8NoBom.GetBytes($Text)
}

function Get-NqSha256File {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function ConvertFrom-NqJson {
    param([Parameter(Mandatory = $true)][string]$Text)
    $command = Get-Command ConvertFrom-Json -ErrorAction Stop
    if ($command.Parameters.ContainsKey('DateKind')) {
        return $Text | ConvertFrom-Json -DateKind String
    }
    return $Text | ConvertFrom-Json
}

function ConvertTo-NqCanonicalJson {
    param([Parameter(Mandatory = $true)]$Value)
    return $Value | ConvertTo-Json -Depth 32 -Compress
}

function Get-NqMigrationInventory {
    param([Parameter(Mandatory = $true)][string]$MigrationRoot)
    $root = [IO.Path]::GetFullPath($MigrationRoot)
    if (-not (Test-Path -LiteralPath $root -PathType Container)) {
        throw 'BLOCKED / MIGRATION_ROOT_MISSING'
    }
    $records = [Collections.Generic.List[object]]::new()
    foreach ($file in Get-ChildItem -LiteralPath $root -File -Filter 'V*.sql') {
        if ($file.Name -cnotmatch '^V([1-9][0-9]*)__([A-Za-z0-9_]+)\.sql$') {
            throw "BLOCKED / MIGRATION_NAME_INVALID / $($file.Name)"
        }
        $records.Add([pscustomobject][ordered]@{
            version = [int]$Matches[1]
            file = $file.Name
            size = [long]$file.Length
            sha256 = Get-NqSha256File $file.FullName
        })
    }
    $ordered = @($records | Sort-Object version)
    if ($ordered.Count -eq 0) { throw 'BLOCKED / MIGRATION_INVENTORY_EMPTY' }
    for ($index = 0; $index -lt $ordered.Count; $index++) {
        if ([int]$ordered[$index].version -ne ($index + 1)) {
            throw 'BLOCKED / MIGRATION_INVENTORY_NOT_CONTIGUOUS'
        }
    }
    $inventoryJson = ConvertTo-NqCanonicalJson $ordered
    return [pscustomobject][ordered]@{
        targetVersion = 'V' + [string]$ordered[-1].version
        inventorySha256 = Get-NqSha256Text $inventoryJson
        migrations = $ordered
    }
}

function Assert-NqRelativePath {
    param([Parameter(Mandatory = $true)][string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path) -or
            [IO.Path]::IsPathRooted($Path) -or
            $Path.Contains('\\') -or $Path.Contains('//') -or
            $Path -match '(^|/)\.\.?(?:/|$)' -or
            $Path.Contains("`0") -or $Path.Contains("`r") -or $Path.Contains("`n")) {
        throw 'BLOCKED / RELEASE_ARTIFACT_PATH_INVALID'
    }
}

function Get-NqSortedArtifacts {
    param([Parameter(Mandatory = $true)][object[]]$Artifacts)
    return @($Artifacts | Sort-Object -Property @{ Expression = { [string]$_.relativePath } })
}

function Assert-NqArtifactDescriptors {
    param([Parameter(Mandatory = $true)][object[]]$Artifacts)
    if ($Artifacts.Count -eq 0) { throw 'BLOCKED / RELEASE_ARTIFACTS_EMPTY' }
    $paths = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $roles = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($artifact in $Artifacts) {
        $fields = @($artifact.PSObject.Properties.Name | Sort-Object)
        $expected = @('executable', 'relativePath', 'role', 'sha256', 'size', 'unixMode')
        if (($fields -join '|') -cne (($expected | Sort-Object) -join '|')) {
            throw 'BLOCKED / RELEASE_ARTIFACT_DESCRIPTOR_INVALID'
        }
        $relative = [string]$artifact.relativePath
        Assert-NqRelativePath $relative
        if (-not $paths.Add($relative)) { throw 'BLOCKED / RELEASE_ARTIFACT_PATH_DUPLICATE' }
        if ([string]$artifact.role -cnotmatch '^[a-z][a-z0-9-]{2,63}$') {
            throw 'BLOCKED / RELEASE_ARTIFACT_ROLE_INVALID'
        }
        if ([string]$artifact.sha256 -cnotmatch $script:Sha256Pattern -or [long]$artifact.size -lt 1) {
            throw 'BLOCKED / RELEASE_ARTIFACT_IDENTITY_INVALID'
        }
        $mode = [string]$artifact.unixMode
        if ($mode -notin @('0644', '0755') -or ([bool]$artifact.executable -ne ($mode -ceq '0755'))) {
            throw 'BLOCKED / RELEASE_ARTIFACT_MODE_INVALID'
        }
        [void]$roles.Add([string]$artifact.role)
    }
    foreach ($requiredRole in @('application-jar', 'frontend-production', 'deployment-contract',
            'release-builder-contract', 'admission-producer-contract','admission-verifier',
            'release-verifier', 'release-installer', 'release-contract')) {
        if (-not $roles.Contains($requiredRole)) {
            throw "BLOCKED / RELEASE_REQUIRED_ROLE_MISSING / $requiredRole"
        }
    }
}

function Get-NqManifestIdentityObject {
    param([Parameter(Mandatory = $true)]$Manifest)
    return [pscustomobject][ordered]@{
        contractSchemaVersion = [string]$Manifest.contractSchemaVersion
        deployable = [bool]$Manifest.deployable
        sourceState = [string]$Manifest.sourceState
        sourceCommit = [string]$Manifest.sourceCommit
        sourceTreeIdentity = [string]$Manifest.sourceTreeIdentity
        requiredJavaMajor = [int]$Manifest.requiredJavaMajor
        requiredPostgresqlMajor = [int]$Manifest.requiredPostgresqlMajor
        requiredSchemaTarget = [string]$Manifest.requiredSchemaTarget
        migrationInventorySha256 = [string]$Manifest.migrationInventorySha256
        artifacts = @(Get-NqSortedArtifacts @($Manifest.artifacts))
    }
}

function Get-NqExpectedReleaseId {
    param([Parameter(Mandatory = $true)]$Manifest)
    $identity = ConvertTo-NqCanonicalJson (Get-NqManifestIdentityObject $Manifest)
    $digest = Get-NqSha256Text $identity
    if ([bool]$Manifest.deployable) {
        return 'nq-' + ([string]$Manifest.sourceCommit).Substring(0, 12) + '-' + $digest.Substring(0, 16)
    }
    return 'nq-test-' + $digest.Substring(0, 24)
}

function New-NqCanonicalManifest {
    param(
        [Parameter(Mandatory = $true)][string]$SourceCommit,
        [Parameter(Mandatory = $true)][int]$RequiredJavaMajor,
        [Parameter(Mandatory = $true)][int]$RequiredPostgresqlMajor,
        [Parameter(Mandatory = $true)]$MigrationInventory,
        [Parameter(Mandatory = $true)][object[]]$Artifacts,
        [Parameter(Mandatory = $true)][bool]$Deployable,
        [Parameter(Mandatory = $true)][string]$SourceState,
        [Parameter(Mandatory = $true)][string]$SourceTreeIdentity
    )
    $commit = $SourceCommit.ToLowerInvariant()
    if ($commit -cnotmatch $script:CommitPattern -or $RequiredJavaMajor -ne 21 -or
            $RequiredPostgresqlMajor -ne 16) {
        throw 'BLOCKED / RELEASE_SOURCE_OR_RUNTIME_INVALID'
    }
    if (($Deployable -and ($SourceState -cne 'COMMITTED_CLEAN' -or
                $SourceTreeIdentity -cnotmatch '^git-tree:[0-9a-f]{40}$')) -or
            (-not $Deployable -and ($SourceState -cne 'UNCOMMITTED_CANDIDATE' -or
                $SourceTreeIdentity -cnotmatch '^candidate-sha256:[0-9a-f]{64}$'))) {
        throw 'BLOCKED / RELEASE_SOURCE_STATE_INVALID'
    }
    $target = [string]$MigrationInventory.targetVersion
    $inventorySha = [string]$MigrationInventory.inventorySha256
    if ($target -cnotmatch '^V[1-9][0-9]*$' -or $inventorySha -cnotmatch $script:Sha256Pattern) {
        throw 'BLOCKED / RELEASE_SCHEMA_IDENTITY_INVALID'
    }
    $orderedArtifacts = @(Get-NqSortedArtifacts $Artifacts)
    Assert-NqArtifactDescriptors $orderedArtifacts
    $manifest = [pscustomobject][ordered]@{
        contractSchemaVersion = $script:ManifestSchema
        releaseId = ''
        deployable = $Deployable
        sourceState = $SourceState
        sourceCommit = $commit
        sourceTreeIdentity = $SourceTreeIdentity
        requiredJavaMajor = $RequiredJavaMajor
        requiredPostgresqlMajor = $RequiredPostgresqlMajor
        requiredSchemaTarget = $target
        migrationInventorySha256 = $inventorySha
        artifacts = $orderedArtifacts
    }
    $manifest.releaseId = Get-NqExpectedReleaseId $manifest
    return $manifest
}

function Write-NqCanonicalManifest {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Manifest
    )
    [IO.File]::WriteAllText($Path, (ConvertTo-NqCanonicalJson $Manifest), $script:Utf8NoBom)
}

function Get-NqReleaseRootDigest {
    param([Parameter(Mandatory=$true)][string]$ReleaseRoot)
    $root=[IO.Path]::GetFullPath($ReleaseRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)
    $manifest=ConvertFrom-NqJson ([IO.File]::ReadAllText((Join-Path $root 'release-manifest.json'),[Text.Encoding]::UTF8))
    $records=[Collections.Generic.List[string]]::new()
    $manifestPath=Join-Path $root 'release-manifest.json'
    $manifestItem=Get-Item $manifestPath
    $records.Add("release-manifest.json|$($manifestItem.Length)|$(Get-NqSha256File $manifestPath)")
    foreach($artifact in Get-NqSortedArtifacts @($manifest.artifacts)){
        $path=Join-Path $root ([string]$artifact.relativePath)
        $records.Add("$([string]$artifact.relativePath)|$((Get-Item $path).Length)|$(Get-NqSha256File $path)")
    }
    Get-NqSha256Text (($records -join "`n")+"`n")
}

function Get-NqProducerContractDigest {
    param([Parameter(Mandatory=$true)][string]$ReleaseRoot)
    $root=[IO.Path]::GetFullPath($ReleaseRoot)
    $manifest=ConvertFrom-NqJson ([IO.File]::ReadAllText((Join-Path $root 'release-manifest.json'),[Text.Encoding]::UTF8))
    $roles=@('release-builder-contract','release-contract','release-verifier','release-installer','deployment-contract','admission-producer-contract','admission-verifier')
    $records=@($manifest.artifacts|Where-Object{$roles-ccontains[string]$_.role}|Sort-Object relativePath|ForEach-Object{"$([string]$_.relativePath)|$([string]$_.sha256)"})
    if($records.Count-ne$roles.Count){throw 'BLOCKED / RELEASE_PRODUCER_CONTRACT_INCOMPLETE'}
    Get-NqSha256Text (($records-join"`n")+"`n")
}

function Test-NqLinux {
    $value = Get-Variable -Name IsLinux -ErrorAction SilentlyContinue
    return $null -ne $value -and [bool]$value.Value
}

function Assert-NqPathNoLink {
    param([Parameter(Mandatory = $true)][string]$Path)
    $item = Get-Item -LiteralPath ([IO.Path]::GetFullPath($Path)) -Force -ErrorAction Stop
    while ($null -ne $item) {
        $linkType = $item.PSObject.Properties['LinkType']
        if (($null -ne $linkType -and $null -ne $linkType.Value) -or
                (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
            throw 'BLOCKED / RELEASE_LINK_INTEGRITY_VIOLATION'
        }
        $item = if ($item -is [IO.FileInfo]) { $item.Directory } else { $item.Parent }
    }
}

function Assert-NqRegularFileIdentity {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$ExpectedMode,
        [switch]$RequirePosix
    )
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw 'BLOCKED / RELEASE_ARTIFACT_MISSING'
    }
    Assert-NqPathNoLink $Path
    if (Test-NqLinux) {
        $metadata = @(& /usr/bin/stat '--format=%F|%h|%a' '--' $Path 2>$null)
        if ($LASTEXITCODE -ne 0 -or $metadata.Count -ne 1) {
            throw 'BLOCKED / RELEASE_LINK_IDENTITY_UNAVAILABLE'
        }
        $parts = ([string]$metadata[0]).Split('|')
        if ($parts.Count -ne 3 -or $parts[0] -cne 'regular file' -or [long]$parts[1] -ne 1) {
            throw 'BLOCKED / RELEASE_LINK_INTEGRITY_VIOLATION'
        }
        if ($RequirePosix -and ('0' + $parts[2]) -cne $ExpectedMode) {
            throw 'BLOCKED / RELEASE_POSIX_MODE_INVALID'
        }
        return
    }
    if ($RequirePosix) { throw 'BLOCKED / RELEASE_POSIX_METADATA_UNAVAILABLE' }
    $fsutil = Join-Path $env:SystemRoot 'System32\fsutil.exe'
    $links = @(& $fsutil hardlink list $Path 2>$null | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($LASTEXITCODE -ne 0 -or $links.Count -ne 1) {
        throw 'BLOCKED / RELEASE_LINK_INTEGRITY_VIOLATION'
    }
}

function Initialize-NqJarIntegrityType {
    if ($null -ne ('NexusQuant.Canonical.JarCrc32' -as [type])) { return }
    Add-Type -TypeDefinition @'
namespace NexusQuant.Canonical
{
    public sealed class JarCrc32
    {
        private static readonly uint[] Table = CreateTable();
        private uint value = 0xffffffffu;
        private static uint[] CreateTable()
        {
            var table = new uint[256];
            for (uint i = 0; i < table.Length; i++)
            {
                var current = i;
                for (var bit = 0; bit < 8; bit++)
                    current = (current & 1u) != 0 ? (current >> 1) ^ 0xedb88320u : current >> 1;
                table[i] = current;
            }
            return table;
        }
        public void Append(byte[] buffer, int count)
        {
            for (var i = 0; i < count; i++) value = (value >> 8) ^ Table[(value ^ buffer[i]) & 0xffu];
        }
        public uint Value { get { return value ^ 0xffffffffu; } }
    }
}
'@
}

function Read-NqJarCentralDirectory {
    param([Parameter(Mandatory = $true)][string]$Path)
    $bytes = [IO.File]::ReadAllBytes($Path)
    if ($bytes.Length -lt 22) { throw 'BLOCKED / RELEASE_JAR_INVALID' }
    $eocd = -1
    $minimum = [Math]::Max(0, $bytes.Length - 65557)
    for ($index = $bytes.Length - 22; $index -ge $minimum; $index--) {
        if ($bytes[$index] -eq 0x50 -and $bytes[$index + 1] -eq 0x4b -and
                $bytes[$index + 2] -eq 0x05 -and $bytes[$index + 3] -eq 0x06) {
            $comment = [BitConverter]::ToUInt16($bytes, $index + 20)
            if ($index + 22 + $comment -eq $bytes.Length) { $eocd = $index; break }
        }
    }
    if ($eocd -lt 0) { throw 'BLOCKED / RELEASE_JAR_INVALID' }
    $disk = [BitConverter]::ToUInt16($bytes, $eocd + 4)
    $centralDisk = [BitConverter]::ToUInt16($bytes, $eocd + 6)
    $onDisk = [BitConverter]::ToUInt16($bytes, $eocd + 8)
    $count = [BitConverter]::ToUInt16($bytes, $eocd + 10)
    $centralSize = [BitConverter]::ToUInt32($bytes, $eocd + 12)
    $offset = [BitConverter]::ToUInt32($bytes, $eocd + 16)
    if ($disk -ne 0 -or $centralDisk -ne 0 -or $onDisk -ne $count -or
            $count -eq [uint16]::MaxValue -or $count -gt 20000 -or
            $centralSize -eq [uint32]::MaxValue -or $offset -eq [uint32]::MaxValue -or
            [long]$offset + [long]$centralSize -ne $eocd) {
        throw 'BLOCKED / RELEASE_JAR_INVALID'
    }
    $records = [Collections.Generic.List[object]]::new()
    $position = [int]$offset
    [long]$total = 0
    $strictUtf8 = [Text.UTF8Encoding]::new($false, $true)
    for ($entryIndex = 0; $entryIndex -lt $count; $entryIndex++) {
        if ($position + 46 -gt $bytes.Length -or [BitConverter]::ToUInt32($bytes, $position) -ne 0x02014b50) {
            throw 'BLOCKED / RELEASE_JAR_INVALID'
        }
        $flags = [BitConverter]::ToUInt16($bytes, $position + 8)
        $method = [BitConverter]::ToUInt16($bytes, $position + 10)
        $crc = [BitConverter]::ToUInt32($bytes, $position + 16)
        $compressed = [BitConverter]::ToUInt32($bytes, $position + 20)
        $uncompressed = [BitConverter]::ToUInt32($bytes, $position + 24)
        $nameLength = [BitConverter]::ToUInt16($bytes, $position + 28)
        $extraLength = [BitConverter]::ToUInt16($bytes, $position + 30)
        $commentLength = [BitConverter]::ToUInt16($bytes, $position + 32)
        $entryDisk = [BitConverter]::ToUInt16($bytes, $position + 34)
        $externalAttributes = [BitConverter]::ToUInt32($bytes, $position + 38)
        $localOffset = [BitConverter]::ToUInt32($bytes, $position + 42)
        if (($flags -band 1) -ne 0 -or $method -notin @(0, 8) -or $entryDisk -ne 0 -or
                $compressed -eq [uint32]::MaxValue -or $uncompressed -eq [uint32]::MaxValue -or
                $localOffset -eq [uint32]::MaxValue -or $nameLength -eq 0 -or
                $uncompressed -gt 268435456) {
            throw 'BLOCKED / RELEASE_JAR_INVALID'
        }
        $total += [long]$uncompressed
        if ($total -gt 1073741824) { throw 'BLOCKED / RELEASE_JAR_TOTAL_UNCOMPRESSED_LIMIT_EXCEEDED' }
        $nameStart = $position + 46
        if ($nameStart + $nameLength + $extraLength + $commentLength -gt $bytes.Length) {
            throw 'BLOCKED / RELEASE_JAR_INVALID'
        }
        $nameBytes = [byte[]]::new($nameLength)
        [Array]::Copy($bytes, $nameStart, $nameBytes, 0, $nameLength)
        try {
            if (($flags -band 0x0800) -ne 0) {
                $name = $strictUtf8.GetString($nameBytes)
            } else {
                if (@($nameBytes | Where-Object { $_ -gt 0x7f }).Count -ne 0) {
                    throw 'BLOCKED / RELEASE_JAR_ENTRY_PATH_INVALID'
                }
                $name = [Text.Encoding]::ASCII.GetString($nameBytes)
            }
        } catch {
            if ($_.Exception.Message -like 'BLOCKED / *') { throw }
            throw 'BLOCKED / RELEASE_JAR_ENTRY_PATH_INVALID'
        }
        $records.Add([pscustomobject]@{
            Name = $name
            NameBytesBase64 = [Convert]::ToBase64String($nameBytes)
            Flags = [uint16]$flags
            CompressionMethod = [uint16]$method
            Crc32 = [uint32]$crc
            CompressedSize = [long]$compressed
            UncompressedSize = [long]$uncompressed
            ExternalAttributes = [uint32]$externalAttributes
            LocalHeaderOffset = [long]$localOffset
        })
        $position = $nameStart + $nameLength + $extraLength + $commentLength
    }
    if ($position -ne [int]$offset + [int]$centralSize) { throw 'BLOCKED / RELEASE_JAR_INVALID' }
    $orderedByLocalOffset = @($records | Sort-Object LocalHeaderOffset)
    for ($recordIndex = 0; $recordIndex -lt $orderedByLocalOffset.Count; $recordIndex++) {
        $record = $orderedByLocalOffset[$recordIndex]
        $local = [int64]$record.LocalHeaderOffset
        if ($local + 30 -gt $offset -or [BitConverter]::ToUInt32($bytes, [int]$local) -ne 0x04034b50) {
            throw 'BLOCKED / RELEASE_JAR_LOCAL_HEADER_INVALID'
        }
        $localFlags = [BitConverter]::ToUInt16($bytes, [int]$local + 6)
        $localMethod = [BitConverter]::ToUInt16($bytes, [int]$local + 8)
        $localCrc = [BitConverter]::ToUInt32($bytes, [int]$local + 14)
        $localCompressed = [BitConverter]::ToUInt32($bytes, [int]$local + 18)
        $localUncompressed = [BitConverter]::ToUInt32($bytes, [int]$local + 22)
        $localNameLength = [BitConverter]::ToUInt16($bytes, [int]$local + 26)
        $localExtraLength = [BitConverter]::ToUInt16($bytes, [int]$local + 28)
        $localNameStart = [int]$local + 30
        if ($localNameStart + $localNameLength + $localExtraLength -gt $offset) {
            throw 'BLOCKED / RELEASE_JAR_LOCAL_HEADER_INVALID'
        }
        $localNameBytes = [byte[]]::new($localNameLength)
        [Array]::Copy($bytes, $localNameStart, $localNameBytes, 0, $localNameLength)
        if ($localFlags -ne [uint16]$record.Flags -or
                $localMethod -ne [uint16]$record.CompressionMethod -or
                [Convert]::ToBase64String($localNameBytes) -cne [string]$record.NameBytesBase64) {
            throw 'BLOCKED / RELEASE_JAR_LOCAL_CENTRAL_MISMATCH'
        }
        $dataStart = [int64]$localNameStart + $localNameLength + $localExtraLength
        $dataEnd = $dataStart + [int64]$record.CompressedSize
        $nextBoundary = if ($recordIndex + 1 -lt $orderedByLocalOffset.Count) {
            [int64]$orderedByLocalOffset[$recordIndex + 1].LocalHeaderOffset
        } else { [int64]$offset }
        if ($dataEnd -gt $nextBoundary) { throw 'BLOCKED / RELEASE_JAR_LOCAL_HEADER_INVALID' }
        $usesDescriptor = (($localFlags -band 0x0008) -ne 0)
        if (-not $usesDescriptor) {
            if ($dataEnd -ne $nextBoundary -or $localCrc -ne [uint32]$record.Crc32 -or
                    $localCompressed -ne [uint32]$record.CompressedSize -or
                    $localUncompressed -ne [uint32]$record.UncompressedSize) {
                throw 'BLOCKED / RELEASE_JAR_LOCAL_CENTRAL_MISMATCH'
            }
        } else {
            if (($localCrc -notin @(0, [uint32]$record.Crc32)) -or
                    ($localCompressed -notin @(0, [uint32]$record.CompressedSize)) -or
                    ($localUncompressed -notin @(0, [uint32]$record.UncompressedSize))) {
                throw 'BLOCKED / RELEASE_JAR_LOCAL_CENTRAL_MISMATCH'
            }
            $descriptorLength = $nextBoundary - $dataEnd
            if ($descriptorLength -notin @(12, 16)) { throw 'BLOCKED / RELEASE_JAR_DATA_DESCRIPTOR_INVALID' }
            $descriptor = [int]$dataEnd
            if ($descriptorLength -eq 16) {
                if ([BitConverter]::ToUInt32($bytes, $descriptor) -ne 0x08074b50) {
                    throw 'BLOCKED / RELEASE_JAR_DATA_DESCRIPTOR_INVALID'
                }
                $descriptor += 4
            }
            if ([BitConverter]::ToUInt32($bytes, $descriptor) -ne [uint32]$record.Crc32 -or
                    [BitConverter]::ToUInt32($bytes, $descriptor + 4) -ne [uint32]$record.CompressedSize -or
                    [BitConverter]::ToUInt32($bytes, $descriptor + 8) -ne [uint32]$record.UncompressedSize) {
                throw 'BLOCKED / RELEASE_JAR_DATA_DESCRIPTOR_INVALID'
            }
        }
    }
    [Array]::Clear($bytes, 0, $bytes.Length)
    return @($records)
}

function Assert-NqJarIntegrity {
    param([Parameter(Mandatory = $true)][string]$Path)
    Add-Type -AssemblyName System.IO.Compression
    Initialize-NqJarIntegrityType
    $centralRecords = @(Read-NqJarCentralDirectory $Path)
    $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
    try {
        $archive = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Read, $false)
        try {
            if ($archive.Entries.Count -ne $centralRecords.Count) { throw 'BLOCKED / RELEASE_JAR_INVALID' }
            $entries = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
            $foldedEntries = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
            $buffer = [byte[]]::new(81920)
            for ($entryIndex = 0; $entryIndex -lt $archive.Entries.Count; $entryIndex++) {
                $entry = $archive.Entries[$entryIndex]
                $central = $centralRecords[$entryIndex]
                if ([string]::IsNullOrWhiteSpace($entry.FullName) -or
                        $entry.FullName.Contains('\\') -or
                        $entry.FullName.StartsWith('/') -or $entry.FullName.Contains(':') -or
                        $entry.FullName -match '(^|/)\.\.?(?:/|$)' -or
                        -not $entries.Add($entry.FullName) -or
                        -not $foldedEntries.Add($entry.FullName.TrimEnd('/').ToUpperInvariant()) -or
                        [string]$entry.FullName -cne [string]$central.Name -or
                        [long]$entry.Length -ne [long]$central.UncompressedSize -or
                        [long]$entry.CompressedLength -ne [long]$central.CompressedSize) {
                    throw 'BLOCKED / RELEASE_JAR_ENTRY_INVALID'
                }
                $isDirectory = $entry.FullName.EndsWith('/')
                $metadataDirectory = (($central.ExternalAttributes -band 0x10) -ne 0 -or
                    (($central.ExternalAttributes -shr 16) -band 0xf000) -eq 0x4000)
                if ($isDirectory -ne $metadataDirectory -or ($isDirectory -and $entry.Length -ne 0)) {
                    throw 'BLOCKED / RELEASE_JAR_ENTRY_INVALID'
                }
                if (-not $isDirectory) {
                    $crc = [NexusQuant.Canonical.JarCrc32]::new()
                    [long]$readTotal = 0
                    $entryStream = $entry.Open()
                    try {
                        while (($read = $entryStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                            $crc.Append($buffer, $read)
                            $readTotal += $read
                        }
                    } finally { $entryStream.Dispose() }
                    if ($readTotal -ne [long]$central.UncompressedSize -or
                            [uint32]$crc.Value -ne [uint32]$central.Crc32) {
                        throw 'BLOCKED / RELEASE_JAR_ENTRY_CRC_MISMATCH'
                    }
                }
            }
            if (-not $entries.Contains('META-INF/MANIFEST.MF')) {
                throw 'BLOCKED / RELEASE_JAR_MANIFEST_MISSING'
            }
            if (-not @($archive.Entries | Where-Object { $_.FullName.StartsWith('BOOT-INF/classes/', [StringComparison]::Ordinal) }).Count -or
                    -not @($archive.Entries | Where-Object { $_.FullName.StartsWith('BOOT-INF/lib/', [StringComparison]::Ordinal) }).Count) {
                throw 'BLOCKED / RELEASE_APPLICATION_JAR_NOT_EXECUTABLE'
            }
            $manifestEntry = $archive.GetEntry('META-INF/MANIFEST.MF')
            $reader = [IO.StreamReader]::new($manifestEntry.Open(), [Text.Encoding]::UTF8, $true)
            try { $jarManifest = $reader.ReadToEnd() } finally { $reader.Dispose() }
            if (-not $jarManifest.Contains('Main-Class: org.springframework.boot.loader.launch.JarLauncher') -or
                    -not $jarManifest.Contains('Start-Class: ')) {
                throw 'BLOCKED / RELEASE_APPLICATION_JAR_NOT_EXECUTABLE'
            }
        } finally {
            $archive.Dispose()
        }
    } catch {
        if ($_.Exception.Message -like 'BLOCKED / *') { throw }
        throw 'BLOCKED / RELEASE_JAR_INVALID'
    } finally {
        $stream.Dispose()
    }
}

function Test-NqCanonicalRelease {
    param(
        [Parameter(Mandatory = $true)][string]$ReleaseRoot,
        [string]$ExpectedSourceCommit,
        [string]$ExpectedSchemaTarget,
        [switch]$RequirePosix
    )
    $root = [IO.Path]::GetFullPath($ReleaseRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $root -PathType Container)) {
        throw 'BLOCKED / RELEASE_ROOT_MISSING'
    }
    Assert-NqPathNoLink $root
    $manifestPath = Join-Path $root 'release-manifest.json'
    Assert-NqRegularFileIdentity $manifestPath '0644' -RequirePosix:$RequirePosix
    $manifestText = [IO.File]::ReadAllText($manifestPath, [Text.Encoding]::UTF8)
    $manifest = ConvertFrom-NqJson $manifestText
    $fields = @($manifest.PSObject.Properties.Name | Sort-Object)
    $expectedFields = @('artifacts', 'contractSchemaVersion', 'deployable', 'migrationInventorySha256',
        'releaseId', 'requiredJavaMajor', 'requiredPostgresqlMajor', 'requiredSchemaTarget',
        'sourceCommit', 'sourceState', 'sourceTreeIdentity') | Sort-Object
    if (($fields -join '|') -cne ($expectedFields -join '|') -or
            $manifestText -cne (ConvertTo-NqCanonicalJson $manifest) -or
            [string]$manifest.contractSchemaVersion -cne $script:ManifestSchema -or
            [string]$manifest.releaseId -cnotmatch $script:ReleaseIdPattern -or
            [string]$manifest.sourceCommit -cnotmatch $script:CommitPattern -or
            [int]$manifest.requiredJavaMajor -ne 21 -or
            [int]$manifest.requiredPostgresqlMajor -ne 16 -or
            [string]$manifest.requiredSchemaTarget -cnotmatch '^V[1-9][0-9]*$' -or
            [string]$manifest.migrationInventorySha256 -cnotmatch $script:Sha256Pattern) {
        throw 'BLOCKED / RELEASE_MANIFEST_CONTRACT_INVALID'
    }
    Assert-NqArtifactDescriptors @($manifest.artifacts)
    if (([bool]$manifest.deployable -and
                ([string]$manifest.sourceState -cne 'COMMITTED_CLEAN' -or
                    [string]$manifest.sourceTreeIdentity -cnotmatch '^git-tree:[0-9a-f]{40}$' -or
                    [string]$manifest.releaseId -cnotmatch '^nq-[0-9a-f]{12}-[0-9a-f]{16}$')) -or
            (-not [bool]$manifest.deployable -and
                ([string]$manifest.sourceState -cne 'UNCOMMITTED_CANDIDATE' -or
                    [string]$manifest.sourceTreeIdentity -cnotmatch '^candidate-sha256:[0-9a-f]{64}$' -or
                    [string]$manifest.releaseId -cnotmatch '^nq-test-[0-9a-f]{24}$'))) {
        throw 'BLOCKED / RELEASE_SOURCE_STATE_INVALID'
    }
    if ([string]$manifest.releaseId -cne (Get-NqExpectedReleaseId $manifest)) {
        throw 'BLOCKED / RELEASE_IDENTITY_MISMATCH'
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedSourceCommit) -and
            [string]$manifest.sourceCommit -cne $ExpectedSourceCommit.ToLowerInvariant()) {
        throw 'BLOCKED / RELEASE_SOURCE_COMMIT_MISMATCH'
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedSchemaTarget) -and
            [string]$manifest.requiredSchemaTarget -cne $ExpectedSchemaTarget) {
        throw 'BLOCKED / RELEASE_SCHEMA_TARGET_MISMATCH'
    }
    $declared = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($artifact in $manifest.artifacts) {
        $relative = [string]$artifact.relativePath
        [void]$declared.Add($relative)
        $path = [IO.Path]::GetFullPath((Join-Path $root $relative))
        if (-not $path.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'BLOCKED / RELEASE_ARTIFACT_PATH_ESCAPE'
        }
        Assert-NqRegularFileIdentity $path ([string]$artifact.unixMode) -RequirePosix:$RequirePosix
        $item = Get-Item -LiteralPath $path -Force
        if ([long]$item.Length -ne [long]$artifact.size -or
                (Get-NqSha256File $path) -cne [string]$artifact.sha256) {
            throw 'BLOCKED / RELEASE_ARTIFACT_HASH_OR_SIZE_MISMATCH'
        }
        if ([string]$artifact.role -ceq 'application-jar') { Assert-NqJarIntegrity $path }
    }
    $actual = @(Get-ChildItem -LiteralPath $root -File -Recurse -Force | ForEach-Object {
        Assert-NqPathNoLink $_.FullName
        $_.FullName.Substring($root.Length + 1).Replace('\', '/')
    } | Where-Object { $_ -cne 'release-manifest.json' })
    if ($actual.Count -ne $declared.Count -or @($actual | Where-Object { -not $declared.Contains($_) }).Count -ne 0) {
        throw 'BLOCKED / RELEASE_UNEXPECTED_FILE'
    }
    return [pscustomobject][ordered]@{
        decision = 'PASS / NQ_CANONICAL_RELEASE_VERIFIED'
        releaseId = [string]$manifest.releaseId
        sourceCommit = [string]$manifest.sourceCommit
        schemaTarget = [string]$manifest.requiredSchemaTarget
        postgresqlMajor = [int]$manifest.requiredPostgresqlMajor
        deployable = [bool]$manifest.deployable
        sourceState = [string]$manifest.sourceState
        manifestSha256 = Get-NqSha256File $manifestPath
        artifactCount = @($manifest.artifacts).Count
        posixVerified = [bool]$RequirePosix
    }
}

Export-ModuleMember -Function @(
    'Get-NqSha256Text', 'Get-NqSha256File', 'ConvertFrom-NqJson',
    'ConvertTo-NqCanonicalJson', 'Get-NqMigrationInventory',
    'New-NqCanonicalManifest', 'Write-NqCanonicalManifest',
    'Get-NqExpectedReleaseId', 'Get-NqReleaseRootDigest', 'Get-NqProducerContractDigest',
    'Assert-NqJarIntegrity', 'Test-NqCanonicalRelease'
)
