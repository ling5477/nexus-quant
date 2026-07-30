Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$script:Ascii = [Text.Encoding]::ASCII
$script:TarBlockSize = 512

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

    $artifacts = @($Manifest.artifacts | Sort-Object relativePath)
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
    ) | Sort-Object relativePath
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
    'ConvertTo-GateWCanonicalManifestJson',
    'Get-GateWCanonicalManifestBytes',
    'Write-GateWCanonicalManifest',
    'ConvertTo-GateWUnixEpoch',
    'New-GateWCanonicalTar'
)
