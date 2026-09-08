[CmdletBinding()]
param([switch] $ExpectVulnerable)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
$scanner = Join-Path $PSScriptRoot '../Test-DeliveryArtifactSafety.ps1'
$tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
$root = Join-Path $tempBase ('f2-repro-' + [guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($root) | Out-Null
function New-Archive([string] $Name, [byte[]] $Payload) {
    $m = [IO.MemoryStream]::new()
    $z = [IO.Compression.ZipArchive]::new($m, [IO.Compression.ZipArchiveMode]::Create, $true)
    $e = $z.CreateEntry($Name, [IO.Compression.CompressionLevel]::NoCompression)
    $s = $e.Open(); $s.Write($Payload, 0, $Payload.Length); $s.Dispose(); $z.Dispose()
    $b = $m.ToArray(); $m.Dispose(); return ,$b
}
try {
    $payload = [Text.Encoding]::UTF8.GetBytes('value=' + 'AKIA' + ('A' * 16))
    $normal = New-Archive 'application.properties' $payload
    $forged = [byte[]]$normal.Clone()
    $central = -1
    for ($i = 0; $i -lt $forged.Length - 4; $i++) {
        if ([BitConverter]::ToUInt32($forged, $i) -eq 0x02014b50) { $central = $i; break }
    }
    if ($central -lt 0) { throw 'Missing fixture directory' }
    [Array]::Copy([BitConverter]::GetBytes([uint32]0), 0, $forged, $central + 24, 4)
    [Array]::Copy([BitConverter]::GetBytes([uint32]0), 0, $forged, $central + 20, 4)
    if (-not [Text.Encoding]::ASCII.GetString($forged).Contains([Text.Encoding]::ASCII.GetString($payload))) { throw 'Payload lost' }
    foreach ($name in @('normal', 'forged', 'nested-forged')) {
        $dir = Join-Path $root $name
        [IO.Directory]::CreateDirectory($dir) | Out-Null
        $bytes = $normal
        if ($name -eq 'forged') { $bytes = $forged }
        if ($name -eq 'nested-forged') { $bytes = New-Archive 'lib/inner.jar' $forged }
        [IO.File]::WriteAllBytes((Join-Path $dir 'app.jar'), $bytes)
        $rejected = $false
        try { & $scanner -EvidenceRoot $dir | Out-Null } catch { $rejected = $true }
        $expected = -not ($ExpectVulnerable -and $name -ne 'normal')
        if ($rejected -ne $expected) { throw "Unexpected reproduction outcome: $name rejected=$rejected" }
        Write-Output "P1_REPRO PS=$($PSVersionTable.PSVersion) case=$name rejected=$rejected payload_physically_preserved=True"
    }
} finally {
    $resolved = [IO.Path]::GetFullPath($root)
    if (-not $resolved.StartsWith($tempBase + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe cleanup' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
