Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$script:Ascii = [Text.Encoding]::ASCII
$script:TarBlockSize = 512
$script:JavaVersionTimeoutMilliseconds = 10000

function Sort-GateWOrdinalStrings
{
    param([Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Values)

    $copy = [string[]]@($Values)
    [Array]::Sort($copy, [StringComparer]::Ordinal)
    return @($copy)
}

function Sort-GateWArtifactsOrdinal
{
    param([Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Artifacts)

    $paths = @(Sort-GateWOrdinalStrings @($Artifacts | ForEach-Object { [string]$_.relativePath }))
    $ordered = @()
    foreach ($path in $paths)
    {
        foreach ($artifact in $Artifacts)
        {
            if ([string]$artifact.relativePath -ceq $path)
            {
                $ordered += $artifact
                break
            }
        }
    }
    return @($ordered)
}

function ConvertFrom-GateWJavaVersionText
{
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text)

    $versionMatch = [regex]::Match(
            $Text,
            '(?m)^(?:openjdk|java) version "([0-9]+)(?:\.([0-9]+))?[^"]*"'
    )
    if (-not $versionMatch.Success)
    {
        throw 'BLOCKED / JAVA_VERSION_UNREADABLE'
    }
    $first = 0
    if (-not [int]::TryParse(
            $versionMatch.Groups[1].Value,
            [Globalization.NumberStyles]::None,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$first
    ))
    {
        throw 'BLOCKED / JAVA_VERSION_UNREADABLE'
    }
    if ($first -eq 1)
    {
        $legacy = 0
        if (-not $versionMatch.Groups[2].Success -or -not [int]::TryParse(
                $versionMatch.Groups[2].Value,
                [Globalization.NumberStyles]::None,
                [Globalization.CultureInfo]::InvariantCulture,
                [ref]$legacy
        ))
        {
            throw 'BLOCKED / JAVA_VERSION_UNREADABLE'
        }
        return $legacy
    }
    return $first
}

function Get-GateWJavaRuntimeMajor
{
    param([string]$JavaPath)

    $resolved = $JavaPath
    if ( [string]::IsNullOrWhiteSpace($resolved))
    {
        $command = Get-Command java -ErrorAction SilentlyContinue
        if ($null -eq $command)
        {
            throw 'BLOCKED / JAVA_RUNTIME_NOT_FOUND'
        }
        $resolved = $command.Source
    }
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf))
    {
        throw 'BLOCKED / JAVA_RUNTIME_NOT_FOUND'
    }

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = [Diagnostics.ProcessStartInfo]::new()
    $process.StartInfo.FileName = [IO.Path]::GetFullPath($resolved)
    $process.StartInfo.Arguments = '-version'
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.CreateNoWindow = $true
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    try
    {
        try
        {
            if (-not $process.Start())
            {
                throw 'BLOCKED / JAVA_RUNTIME_NOT_FOUND'
            }
        }
        catch
        {
            if ($_.Exception.Message -match '^BLOCKED / ')
            {
                throw
            }
            throw 'BLOCKED / JAVA_RUNTIME_NOT_FOUND'
        }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($script:JavaVersionTimeoutMilliseconds))
        {
            try
            {
                $process.Kill()
                $process.WaitForExit(5000) | Out-Null
            }
            catch
            {
                # Version discovery stays fail-closed even when cleanup itself fails.
            }
            throw 'BLOCKED / JAVA_VERSION_UNREADABLE'
        }
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0)
        {
            throw 'BLOCKED / JAVA_VERSION_UNREADABLE'
        }
        return ConvertFrom-GateWJavaVersionText (($stdout, $stderr) -join "`n")
    }
    finally
    {
        $process.Dispose()
    }
}

function ConvertTo-GateWJsonString
{
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value)

    $builder = New-Object Text.StringBuilder
    [void]$builder.Append('"')
    foreach ($character in $Value.ToCharArray())
    {
        $code = [int]$character
        switch ($code)
        {
            8 {
                [void]$builder.Append('\b'); continue
            }
            9 {
                [void]$builder.Append('\t'); continue
            }
            10 {
                [void]$builder.Append('\n'); continue
            }
            12 {
                [void]$builder.Append('\f'); continue
            }
            13 {
                [void]$builder.Append('\r'); continue
            }
            34 {
                [void]$builder.Append('\"'); continue
            }
            92 {
                [void]$builder.Append('\\'); continue
            }
        }
        if ($code -lt 32)
        {
            [void]$builder.Append(('\u{0:x4}' -f $code))
        }
        else
        {
            [void]$builder.Append($character)
        }
    }
    [void]$builder.Append('"')
    return $builder.ToString()
}

function ConvertTo-GateWJsonNullableString
{
    param([AllowNull()]$Value)

    if ($null -eq $Value)
    {
        return 'null'
    }
    return ConvertTo-GateWJsonString ([string]$Value)
}

function ConvertTo-GateWCanonicalManifestJson
{
    param([Parameter(Mandatory = $true)]$Manifest)

    $artifacts = @(Sort-GateWArtifactsOrdinal @($Manifest.artifacts))
    $builder = New-Object Text.StringBuilder
    [void]$builder.Append('{')
    [void]$builder.Append('"schemaVersion":')
    [void]$builder.Append((ConvertTo-GateWJsonString ([string]$Manifest.schemaVersion)))
    [void]$builder.Append(',"releaseId":')
    [void]$builder.Append((ConvertTo-GateWJsonString ([string]$Manifest.releaseId)))
    [void]$builder.Append(',"sourceCommit":')
    [void]$builder.Append((ConvertTo-GateWJsonString ([string]$Manifest.sourceCommit)))
    [void]$builder.Append(',"sourceCommitTimestamp":')
    [void]$builder.Append((ConvertTo-GateWJsonString ([string]$Manifest.sourceCommitTimestamp)))
    [void]$builder.Append(',"sourceTreeMode":')
    [void]$builder.Append((ConvertTo-GateWJsonString ([string]$Manifest.sourceTreeMode)))
    [void]$builder.Append(',"baseCommit":')
    [void]$builder.Append((ConvertTo-GateWJsonNullableString $Manifest.baseCommit))
    [void]$builder.Append(',"candidateDiffSha256":')
    [void]$builder.Append((ConvertTo-GateWJsonNullableString $Manifest.candidateDiffSha256))
    [void]$builder.Append(',"requiredRuntime":{"os":')
    [void]$builder.Append((ConvertTo-GateWJsonString ([string]$Manifest.requiredRuntime.os)))
    [void]$builder.Append(',"powershellMajor":')
    [void]$builder.Append(([int]$Manifest.requiredRuntime.powershellMajor).ToString([Globalization.CultureInfo]::InvariantCulture))
    [void]$builder.Append(',"javaMajor":')
    [void]$builder.Append(([int]$Manifest.requiredRuntime.javaMajor).ToString([Globalization.CultureInfo]::InvariantCulture))
    [void]$builder.Append(',"systemd":')
    [void]$builder.Append($( if ([bool]$Manifest.requiredRuntime.systemd)
    {
        'true'
    }
    else
    {
        'false'
    } ))
    [void]$builder.Append('},"buildProvenance":{"mavenCommand":')
    [void]$builder.Append((ConvertTo-GateWJsonString ([string]$Manifest.buildProvenance.mavenCommand)))
    [void]$builder.Append(',"javaMajor":')
    [void]$builder.Append(([int]$Manifest.buildProvenance.javaMajor).
            ToString([Globalization.CultureInfo]::InvariantCulture))
    [void]$builder.Append(',"cleanDetachedWorktree":')
    [void]$builder.Append($( if ([bool]$Manifest.buildProvenance.cleanDetachedWorktree)
    {
        'true'
    }
    else
    {
        'false'
    } ))
    [void]$builder.Append('},"lineEndingPolicy":')
    [void]$builder.Append((ConvertTo-GateWJsonString ([string]$Manifest.lineEndingPolicy)))
    [void]$builder.Append(',"artifacts":[')
    for ($index = 0; $index -lt $artifacts.Count; $index++)
    {
        if ($index -gt 0)
        {
            [void]$builder.Append(',')
        }
        $artifact = $artifacts[$index]
        [void]$builder.Append('{"relativePath":')
        [void]$builder.Append((ConvertTo-GateWJsonString ([string]$artifact.relativePath)))
        [void]$builder.Append(',"size":')
        [void]$builder.Append(([long]$artifact.size).ToString([Globalization.CultureInfo]::InvariantCulture))
        [void]$builder.Append(',"sha256":')
        [void]$builder.Append((ConvertTo-GateWJsonString ([string]$artifact.sha256)))
        [void]$builder.Append(',"mode":')
        [void]$builder.Append((ConvertTo-GateWJsonString ([string]$artifact.mode)))
        [void]$builder.Append(',"lineEndingPolicy":')
        [void]$builder.Append((ConvertTo-GateWJsonString ([string]$artifact.lineEndingPolicy)))
        [void]$builder.Append(',"entrypoint":')
        [void]$builder.Append($( if ([bool]$artifact.entrypoint)
        {
            'true'
        }
        else
        {
            'false'
        } ))
        [void]$builder.Append(',"role":')
        [void]$builder.Append((ConvertTo-GateWJsonString ([string]$artifact.role)))
        [void]$builder.Append('}')
    }
    [void]$builder.Append(']}')
    [void]$builder.Append("`n")
    return $builder.ToString()
}

function Get-GateWCanonicalManifestBytes
{
    param([Parameter(Mandatory = $true)]$Manifest)

    return $script:Utf8NoBom.GetBytes((ConvertTo-GateWCanonicalManifestJson $Manifest))
}

function Write-GateWCanonicalManifest
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Manifest
    )

    $parent = Split-Path -Parent $Path
    [IO.Directory]::CreateDirectory($parent) | Out-Null
    [IO.File]::WriteAllBytes($Path, (Get-GateWCanonicalManifestBytes $Manifest))
}

function Get-GateWCrc32
{
    param([Parameter(Mandatory = $true)][AllowEmptyCollection()][byte[]]$Bytes)

    [uint64]$mask = 4294967295
    [uint64]$polynomial = 3988292384
    $table = New-Object uint64[] 256
    for ($tableIndex = 0; $tableIndex -lt 256; $tableIndex++)
    {
        [uint64]$value = $tableIndex
        for ($bit = 0; $bit -lt 8; $bit++)
        {
            if (($value -band [uint64]1) -ne 0)
            {
                $value = (($value -shr 1) -bxor $polynomial) -band $mask
            }
            else
            {
                $value = ($value -shr 1) -band $mask
            }
        }
        $table[$tableIndex] = $value
    }
    [uint64]$crc = $mask
    foreach ($byte in $Bytes)
    {
        $lookup = [int](($crc -bxor [uint64]$byte) -band [uint64]255)
        $crc = (($crc -shr 8) -bxor $table[$lookup]) -band $mask
    }
    return [uint32](($crc -bxor $mask) -band $mask)
}

function New-GateWCanonicalZip
{
    param(
        [Parameter(Mandatory = $true)][string]$SourceDirectory,
        [Parameter(Mandatory = $true)][string]$Destination,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$RelativePaths
    )

    if (Test-Path -LiteralPath $Destination)
    {
        throw 'BLOCKED / RELEASE_OUTPUT_ALREADY_EXISTS'
    }
    $sourceRoot = [IO.Path]::GetFullPath($SourceDirectory).TrimEnd(
            [IO.Path]::DirectorySeparatorChar,
            [IO.Path]::AltDirectorySeparatorChar
    )
    $filePaths = @(Sort-GateWOrdinalStrings $RelativePaths)
    if ($filePaths.Count -eq 0)
    {
        throw 'BLOCKED / RELEASE_LAUNCHER_CLASSES_MISSING'
    }
    $directoryIndex = @{ }
    foreach ($relativePath in $filePaths)
    {
        if ([string]::IsNullOrWhiteSpace($relativePath) -or
                $relativePath -match '\\' -or $relativePath.StartsWith('/') -or
                $relativePath -match '(^|/)\.\.(/|$)' -or
                $relativePath -notmatch '^[\x20-\x7e]+$')
        {
            throw 'BLOCKED / RELEASE_ARCHIVE_PATH_INVALID'
        }
        $separator = $relativePath.IndexOf('/')
        while ($separator -ge 0)
        {
            $directoryIndex[$relativePath.Substring(0, $separator + 1)] = $true
            $separator = $relativePath.IndexOf('/', $separator + 1)
        }
    }
    $entryDefinitions = @()
    foreach ($directory in @(Sort-GateWOrdinalStrings @($directoryIndex.Keys)))
    {
        $entryDefinitions += [pscustomobject]@{
            RelativePath = $directory
            IsDirectory = $true
            SourcePath = $null
        }
    }
    foreach ($relativePath in $filePaths)
    {
        $sourcePath = [IO.Path]::GetFullPath((Join-Path $sourceRoot $relativePath))
        if (-not $sourcePath.StartsWith(
                $sourceRoot + [IO.Path]::DirectorySeparatorChar,
                [StringComparison]::OrdinalIgnoreCase
        ) -or -not (Test-Path -LiteralPath $sourcePath -PathType Leaf))
        {
            throw 'BLOCKED / RELEASE_ARCHIVE_PATH_INVALID'
        }
        $entryDefinitions += [pscustomobject]@{
            RelativePath = $relativePath
            IsDirectory = $false
            SourcePath = $sourcePath
        }
    }

    [IO.Directory]::CreateDirectory((Split-Path -Parent $Destination)) | Out-Null
    $stream = [IO.FileStream]::new(
            $Destination,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None
    )
    $writer = [IO.BinaryWriter]::new($stream, $script:Utf8NoBom, $true)
    $centralRecords = @()
    try
    {
        foreach ($definition in $entryDefinitions)
        {
            $nameBytes = $script:Utf8NoBom.GetBytes([string]$definition.RelativePath)
            [byte[]]$data = if ([bool]$definition.IsDirectory)
            {
                New-Object byte[] 0
            }
            else
            {
                [IO.File]::ReadAllBytes([string]$definition.SourcePath)
            }
            if ($nameBytes.Length -gt [uint16]::MaxValue -or
                    $data.LongLength -gt [uint32]::MaxValue -or
                    $stream.Position -gt [uint32]::MaxValue)
            {
                throw 'BLOCKED / RELEASE_ARCHIVE_METADATA_INVALID'
            }
            $crc32 = Get-GateWCrc32 $data
            $offset = [uint32]$stream.Position
            $writer.Write([uint32]0x04034B50)
            $writer.Write([uint16]20)
            $writer.Write([uint16]0x0800)
            $writer.Write([uint16]0)
            $writer.Write([uint16]0)
            $writer.Write([uint16]33)
            $writer.Write([uint32]$crc32)
            $writer.Write([uint32]$data.Length)
            $writer.Write([uint32]$data.Length)
            $writer.Write([uint16]$nameBytes.Length)
            $writer.Write([uint16]0)
            $writer.Write($nameBytes)
            $writer.Write($data)
            $centralRecords += [pscustomobject]@{
                NameBytes = $nameBytes
                Crc32 = [uint32]$crc32
                Size = [uint32]$data.Length
                Offset = $offset
                IsDirectory = [bool]$definition.IsDirectory
            }
        }

        if ($stream.Position -gt [uint32]::MaxValue -or
                $centralRecords.Count -gt [uint16]::MaxValue)
        {
            throw 'BLOCKED / RELEASE_ARCHIVE_METADATA_INVALID'
        }
        $centralOffset = [uint32]$stream.Position
        foreach ($record in $centralRecords)
        {
            $externalAttributes = if ([bool]$record.IsDirectory)
            {
                [uint32](([uint64]16877 * [uint64]65536) -bor [uint64]0x10)
            }
            else
            {
                [uint32]([uint64]33188 * [uint64]65536)
            }
            $writer.Write([uint32]0x02014B50)
            $writer.Write([uint16]0x0314)
            $writer.Write([uint16]20)
            $writer.Write([uint16]0x0800)
            $writer.Write([uint16]0)
            $writer.Write([uint16]0)
            $writer.Write([uint16]33)
            $writer.Write([uint32]$record.Crc32)
            $writer.Write([uint32]$record.Size)
            $writer.Write([uint32]$record.Size)
            $writer.Write([uint16]$record.NameBytes.Length)
            $writer.Write([uint16]0)
            $writer.Write([uint16]0)
            $writer.Write([uint16]0)
            $writer.Write([uint16]0)
            $writer.Write($externalAttributes)
            $writer.Write([uint32]$record.Offset)
            $writer.Write([byte[]]$record.NameBytes)
        }
        $centralSize = [long]$stream.Position - [long]$centralOffset
        if ($centralSize -gt [uint32]::MaxValue)
        {
            throw 'BLOCKED / RELEASE_ARCHIVE_METADATA_INVALID'
        }
        $writer.Write([uint32]0x06054B50)
        $writer.Write([uint16]0)
        $writer.Write([uint16]0)
        $writer.Write([uint16]$centralRecords.Count)
        $writer.Write([uint16]$centralRecords.Count)
        $writer.Write([uint32]$centralSize)
        $writer.Write([uint32]$centralOffset)
        $writer.Write([uint16]0)
        $writer.Flush()
    }
    finally
    {
        $writer.Dispose()
        $stream.Dispose()
    }
}

function ConvertTo-GateWOctal
{
    param(
        [Parameter(Mandatory = $true)][long]$Value,
        [Parameter(Mandatory = $true)][int]$Width
    )

    if ($Value -lt 0)
    {
        throw 'BLOCKED / RELEASE_ARCHIVE_METADATA_INVALID'
    }
    $digits = ''
    $remaining = $Value
    do
    {
        $digit = [int]($remaining % 8)
        $digits = [char](48 + $digit) + $digits
        $remaining = [long][Math]::Floor($remaining / 8)
    } while ($remaining -gt 0)
    if ($digits.Length -gt $Width)
    {
        throw 'BLOCKED / RELEASE_ARCHIVE_METADATA_INVALID'
    }
    return $digits.PadLeft($Width, '0')
}

function Set-GateWTarField
{
    param(
        [Parameter(Mandatory = $true)][byte[]]$Header,
        [Parameter(Mandatory = $true)][int]$Offset,
        [Parameter(Mandatory = $true)][int]$Length,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value
    )

    $bytes = $script:Ascii.GetBytes($Value)
    if ($bytes.Length -gt $Length)
    {
        throw 'BLOCKED / RELEASE_ARCHIVE_PATH_INVALID'
    }
    [Array]::Copy($bytes, 0, $Header, $Offset, $bytes.Length)
}

function Split-GateWTarPath
{
    param([Parameter(Mandatory = $true)][string]$Path)

    if ($Path -notmatch '^[\x20-\x7e]+$')
    {
        throw 'BLOCKED / RELEASE_ARCHIVE_PATH_INVALID'
    }
    if ($script:Ascii.GetByteCount($Path) -le 100)
    {
        return [pscustomobject]@{ Name = $Path; Prefix = '' }
    }
    for ($index = $Path.Length - 1; $index -ge 1; $index--)
    {
        if ($Path[$index] -ne '/')
        {
            continue
        }
        $prefix = $Path.Substring(0, $index)
        $name = $Path.Substring($index + 1)
        if ($script:Ascii.GetByteCount($prefix) -le 155 -and
                $script:Ascii.GetByteCount($name) -le 100)
        {
            return [pscustomobject]@{ Name = $name; Prefix = $prefix }
        }
    }
    throw 'BLOCKED / RELEASE_ARCHIVE_PATH_INVALID'
}

function New-GateWTarHeader
{
    param(
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][long]$Size,
        [Parameter(Mandatory = $true)][string]$Mode,
        [Parameter(Mandatory = $true)][long]$MTimeEpoch
    )

    $path = Split-GateWTarPath $RelativePath
    $header = New-Object byte[] $script:TarBlockSize
    Set-GateWTarField $header 0 100 $path.Name
    Set-GateWTarField $header 100 8 ((ConvertTo-GateWOctal ([Convert]::ToInt32($Mode, 8)) 7) + [char]0)
    Set-GateWTarField $header 108 8 ((ConvertTo-GateWOctal 0 7) + [char]0)
    Set-GateWTarField $header 116 8 ((ConvertTo-GateWOctal 0 7) + [char]0)
    Set-GateWTarField $header 124 12 ((ConvertTo-GateWOctal $Size 11) + [char]0)
    Set-GateWTarField $header 136 12 ((ConvertTo-GateWOctal $MTimeEpoch 11) + [char]0)
    for ($index = 148; $index -lt 156; $index++)
    {
        $header[$index] = 32
    }
    Set-GateWTarField $header 156 1 '0'
    Set-GateWTarField $header 257 6 ('ustar' + [char]0)
    Set-GateWTarField $header 263 2 '00'
    Set-GateWTarField $header 265 32 'root'
    Set-GateWTarField $header 297 32 'root'
    Set-GateWTarField $header 329 8 ((ConvertTo-GateWOctal 0 7) + [char]0)
    Set-GateWTarField $header 337 8 ((ConvertTo-GateWOctal 0 7) + [char]0)
    Set-GateWTarField $header 345 155 $path.Prefix
    $checksum = [long]0
    foreach ($value in $header)
    {
        $checksum += $value
    }
    Set-GateWTarField $header 148 8 ((ConvertTo-GateWOctal $checksum 6) + [char]0 + ' ')
    return $header
}

function ConvertTo-GateWUnixEpoch
{
    param([Parameter(Mandatory = $true)][string]$UtcTimestamp)

    $parsed = [DateTimeOffset]::MinValue
    if (-not [DateTimeOffset]::TryParseExact(
            $UtcTimestamp,
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::AssumeUniversal,
            [ref]$parsed
    ))
    {
        throw 'BLOCKED / RELEASE_SOURCE_TIMESTAMP_INVALID'
    }
    $epoch = [DateTimeOffset]::new(1970, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
    return [long][Math]::Floor(($parsed.ToUniversalTime() - $epoch).TotalSeconds)
}

function New-GateWCanonicalTar
{
    param(
        [Parameter(Mandatory = $true)][string]$ReleaseRoot,
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    if (Test-Path -LiteralPath $Destination)
    {
        throw 'BLOCKED / RELEASE_OUTPUT_ALREADY_EXISTS'
    }
    $root = [IO.Path]::GetFullPath($ReleaseRoot).TrimEnd(
            [IO.Path]::DirectorySeparatorChar,
            [IO.Path]::AltDirectorySeparatorChar
    )
    $entries = @(
        [pscustomobject]@{ relativePath = 'release-manifest.json'; mode = '0644' }
        @($Manifest.artifacts | ForEach-Object {
            [pscustomobject]@{
                relativePath = [string]$_.relativePath
                mode = [string]$_.mode
            }
        })
    )
    $entries = @(Sort-GateWArtifactsOrdinal $entries)
    $mtime = ConvertTo-GateWUnixEpoch ([string]$Manifest.sourceCommitTimestamp)
    $stream = [IO.FileStream]::new(
            $Destination,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None
    )
    try
    {
        foreach ($entry in $entries)
        {
            $relativePath = [string]$entry.relativePath
            $path = [IO.Path]::GetFullPath((Join-Path $root $relativePath))
            if (-not $path.StartsWith(
                    $root + [IO.Path]::DirectorySeparatorChar,
                    [StringComparison]::OrdinalIgnoreCase
            ) -or -not (Test-Path -LiteralPath $path -PathType Leaf))
            {
                throw 'BLOCKED / RELEASE_ARCHIVE_PATH_INVALID'
            }
            $item = Get-Item -LiteralPath $path -Force
            $header = New-GateWTarHeader $relativePath ([long]$item.Length) ([string]$entry.mode) $mtime
            $stream.Write($header, 0, $header.Length)
            $input = [IO.File]::OpenRead($path)
            try
            {
                $input.CopyTo($stream)
            }
            finally
            {
                $input.Dispose()
            }
            $remainder = [int]($item.Length % $script:TarBlockSize)
            if ($remainder -ne 0)
            {
                $padding = New-Object byte[] ($script:TarBlockSize - $remainder)
                $stream.Write($padding, 0, $padding.Length)
            }
        }
        $terminator = New-Object byte[] ($script:TarBlockSize * 2)
        $stream.Write($terminator, 0, $terminator.Length)
    }
    finally
    {
        $stream.Dispose()
    }
}

Export-ModuleMember -Function @(
    'Sort-GateWOrdinalStrings',
    'Sort-GateWArtifactsOrdinal',
    'ConvertFrom-GateWJavaVersionText',
    'Get-GateWJavaRuntimeMajor',
    'ConvertTo-GateWCanonicalManifestJson',
    'Get-GateWCanonicalManifestBytes',
    'Write-GateWCanonicalManifest',
    'New-GateWCanonicalZip',
    'ConvertTo-GateWUnixEpoch',
    'New-GateWCanonicalTar'
)
