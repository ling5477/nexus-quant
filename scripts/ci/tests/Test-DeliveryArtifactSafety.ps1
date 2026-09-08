[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$scanner = Join-Path $PSScriptRoot '../Test-DeliveryArtifactSafety.ps1'
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)
$runRoot = Join-Path $tempRoot ('artifact-safety-' + [guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($runRoot) | Out-Null
$sentinel = 'AKIA' + ('A' * 16)
$positive = 0
$negative = 0

function Write-Zip([string] $Path, [hashtable] $Entries) {
    $stream = [IO.File]::Create($Path)
    $zip = $null
    try {
        $zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create, $true)
        foreach ($name in $Entries.Keys) {
            $entry = $zip.CreateEntry($name)
            $entryStream = $entry.Open()
            try {
                if ($Entries[$name] -is [byte[]]) { $bytes = $Entries[$name] }
                else { $bytes = [Text.Encoding]::UTF8.GetBytes([string]$Entries[$name]) }
                $entryStream.Write($bytes, 0, $bytes.Length)
            } finally { $entryStream.Dispose() }
        }
    } finally { if ($null -ne $zip) { $zip.Dispose() }; $stream.Dispose() }
}

function Test-Case([string] $Name, [scriptblock] $Setup, [string] $Reject, [hashtable] $Limits = @{}) {
    $root = Join-Path $runRoot $Name
    [IO.Directory]::CreateDirectory($root) | Out-Null
    & $Setup $root
    $failure = $null
    $messages = New-Object System.Collections.Generic.List[string]
    try { & $scanner -EvidenceRoot $root @Limits | ForEach-Object { $messages.Add([string]$_) } }
    catch { $failure = $_.Exception.Message }
    if (($messages -join "`n").Contains($sentinel)) { throw 'Scanner leaked fixture content in output' }
    if ($Reject) {
        if ($null -eq $failure -or $failure -notmatch $Reject) { throw "Expected rejection $Reject in $Name; actual=$failure" }
        if ($failure.Contains($sentinel)) { throw 'Scanner leaked fixture content in exception' }
        $script:negative++
        Write-Output "ARTIFACT_SAFETY_FIXTURE $Name=REJECTED"
    } else {
        if ($null -ne $failure) { throw "Clean input failed: $Name $failure" }
        $script:positive++
        Write-Output "ARTIFACT_SAFETY_FIXTURE $Name=PASS"
    }
}

# 从标准库产物定点修改格式字段；只生成测试所需的单 entry ZIP32。
function Edit-ZipFixture([string] $Path, [string] $Mode) {
    $b = [IO.File]::ReadAllBytes($Path)
    $end = $b.Length - 22
    $cd = [int][BitConverter]::ToUInt32($b, $end + 16)
    switch ($Mode) {
        'central-zero' { [Array]::Clear($b, $cd + 20, 8) }
        'central-expanded' { [Array]::Clear($b, $cd + 24, 4) }
        'both-expanded-zero' { [Array]::Clear($b, 22, 4); [Array]::Clear($b, $cd + 24, 4) }
        'crc-local' { $b[14] = $b[14] -bxor 1 }
        'crc-both' { $b[14] = $b[14] -bxor 1; $b[$cd + 16] = $b[$cd + 16] -bxor 1 }
        'offset' { $b[$cd + 42] = 1 }
        'name' { $b[30] = $b[30] -bxor 1 }
        'flags' { $b[6] = $b[6] -bxor 8 }
        'method' { $b[8] = 99 }
        'truncated' { $b = [byte[]]$b[0..($b.Length - 8)] }
        'descriptor-signed' { $b = Add-Descriptor $b $cd $true }
        'descriptor-unsigned' { $b = Add-Descriptor $b $cd $false }
        'descriptor-bad-crc' { $b = Add-Descriptor $b $cd $true; $b[$cd + 4] = $b[$cd + 4] -bxor 1 }
        'descriptor-bad-size' { $b = Add-Descriptor $b $cd $true; $b[$cd + 12] = $b[$cd + 12] -bxor 1 }
        'zip64' { $b[$end + 10] = 255; $b[$end + 11] = 255 }
        { $_ -in @('local-zip64', 'local-zip64-bad') } {
            $at = 30 + [BitConverter]::ToUInt16($b, 26)
            if ([BitConverter]::ToUInt16($b, 28) -ne 0) { throw 'Expected fixture without extra fields' }
            $new = New-Object byte[] ($b.Length + 20)
            [Array]::Copy($b, 0, $new, 0, $at)
            [Array]::Copy($b, $at, $new, $at + 20, $b.Length - $at)
            $new[28] = 20; $new[$at] = 1; $new[$at + 2] = 16
            [Array]::Copy([BitConverter]::GetBytes([uint64][BitConverter]::ToUInt32($b, $cd + 24)), 0, $new, $at + 4, 8)
            [Array]::Copy([BitConverter]::GetBytes([uint64][BitConverter]::ToUInt32($b, $cd + 20)), 0, $new, $at + 12, 8)
            [Array]::Copy([BitConverter]::GetBytes([uint32]($cd + 20)), 0, $new, $new.Length - 6, 4)
            if ($Mode -eq 'local-zip64-bad') { $new[$at + 4] = $new[$at + 4] -bxor 1 }
            $b = $new
        }
        'gap' {
            $new = New-Object byte[] ($b.Length + 1)
            [Array]::Copy($b, 0, $new, 0, $cd)
            [Array]::Copy($b, $cd, $new, $cd + 1, $b.Length - $cd)
            [Array]::Copy([BitConverter]::GetBytes([uint32]($cd + 1)), 0, $new, $end + 1 + 16, 4)
            $b = $new
        }
        { $_ -in @('deflate-truncated', 'deflate-trailing') } {
            if ([BitConverter]::ToUInt16($b, 8) -ne 8) { throw 'Expected deflate fixture' }
            $delta = 1
            if ($Mode -eq 'deflate-truncated') { $delta = -1 }
            $new = New-Object byte[] ($b.Length + $delta)
            [Array]::Copy($b, 0, $new, 0, [Math]::Min($cd, $cd + $delta))
            [Array]::Copy($b, $cd, $new, $cd + $delta, $b.Length - $cd)
            $compressed = [BitConverter]::ToUInt32($b, $cd + 20)
            $newSize = [BitConverter]::GetBytes([uint32]($compressed + $delta))
            [Array]::Copy($newSize, 0, $new, 18, 4)
            [Array]::Copy($newSize, 0, $new, $cd + $delta + 20, 4)
            [Array]::Copy([BitConverter]::GetBytes([uint32]($cd + $delta)), 0, $new, $new.Length - 6, 4)
            $b = $new
        }
        default { throw "Unknown fixture mutation $Mode" }
    }
    [IO.File]::WriteAllBytes($Path, $b)
}

function Add-Descriptor([byte[]] $Bytes, [int] $Central, [bool] $Signed) {
    $size = 12
    if ($Signed) { $size = 16 }
    $out = New-Object byte[] ($Bytes.Length + $size)
    [Array]::Copy($Bytes, 0, $out, 0, $Central)
    [Array]::Copy($Bytes, $Central, $out, $Central + $size, $Bytes.Length - $Central)
    $p = $Central
    if ($Signed) { [Array]::Copy([BitConverter]::GetBytes([uint32]0x08074b50), 0, $out, $p, 4); $p += 4 }
    [Array]::Copy($Bytes, $Central + 16, $out, $p, 12)
    $out[6] = $out[6] -bor 8
    $out[$Central + $size + 8] = $out[$Central + $size + 8] -bor 8
    [Array]::Clear($out, 14, 12)
    [Array]::Copy([BitConverter]::GetBytes([uint32]($Central + $size)), 0, $out, $out.Length - 6, 4)
    return ,$out
}

try {
    Test-Case 'json-secret' { param($r) [IO.File]::WriteAllText((Join-Path $r 'application.json'), ('{"value":"' + $sentinel + '"}')) } 'violations='
    Test-Case 'properties-secret' { param($r) [IO.File]::WriteAllText((Join-Path $r 'application.properties'), ('value=' + $sentinel)) } 'violations='
    Test-Case 'jar-secret' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'BOOT-INF/classes/application.properties' = ('value=' + $sentinel) } } 'violations='
    Test-Case 'zip-case-insensitive-secret' { param($r) Write-Zip (Join-Path $r 'app.ZIP') @{ 'CONFIG/APP.PROPERTIES' = $sentinel } } 'violations='
    Test-Case 'json-clean' { param($r) [IO.File]::WriteAllText((Join-Path $r 'application.json'), '{"enabled":false}') } ''
    Test-Case 'properties-clean' { param($r) [IO.File]::WriteAllText((Join-Path $r 'application.properties'), 'enabled=false') } ''
    Test-Case 'jar-clean' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'BOOT-INF/classes/application.properties' = 'enabled=false'; 'Example.class' = [byte[]]@(0xca,0xfe,0xba,0xbe) } } ''
    Test-Case 'utf16-properties-secret' { param($r) [IO.File]::WriteAllText((Join-Path $r 'application.properties'), $sentinel, [Text.Encoding]::Unicode) } 'violations='
    Test-Case 'invalid-text' { param($r) [IO.File]::WriteAllBytes((Join-Path $r 'application.properties'), [byte[]]@(0xff,0xff,0xff)) } 'invalid-text'
    Test-Case 'forbidden-file' { param($r) [IO.File]::WriteAllText((Join-Path $r '.env'), 'value=clean') } 'violations='
    Test-Case 'forbidden-entry' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'secrets/config.txt' = 'clean' } } 'violations='
    Test-Case 'entry-traversal' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ '../application.properties' = 'clean' } } 'violations='
    Test-Case 'invalid-archive' { param($r) [IO.File]::WriteAllText((Join-Path $r 'app.jar'), 'not a zip') } 'invalid-archive'
    Test-Case 'entry-count' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'a.txt' = 'a'; 'b.txt' = 'b' } } 'archive-entry-count' @{ MaxArchiveEntries = 1 }
    Test-Case 'entry-size' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'a.properties' = ('a' * 64) } } 'archive-entry-size' @{ MaxEntryBytes = 32 }
    Test-Case 'text-total' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'a.properties' = ('a' * 20); 'b.properties' = ('b' * 20) } } 'expanded-text-bytes' @{ MaxExpandedTextBytes = 32 }
    Test-Case 'archive-size' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'a.txt' = 'clean' } } 'archive-file-size' @{ MaxArchiveBytes = 32 }
    $inner = Join-Path $runRoot 'inner.jar'
    Write-Zip $inner @{ 'application.properties' = 'clean' }
    $cleanNested = [IO.File]::ReadAllBytes($inner)
    Test-Case 'nested-clean' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'BOOT-INF/lib/dependency.jar' = $cleanNested } } ''
    Test-Case 'nested-depth' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'lib/dependency.jar' = $cleanNested } } 'archive-depth' @{ MaxArchiveDepth = 1 }
    Test-Case 'nested-bytes' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'lib/dependency.jar' = $cleanNested } } 'nested-archive-bytes' @{ MaxNestedArchiveBytes = 32 }
    Test-Case 'nested-global-count' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'lib/dependency.jar' = $cleanNested } } 'archive-entry-count' @{ MaxArchiveEntries = 1 }
    Test-Case 'cross-archive-text-total' {
        param($r)
        Write-Zip (Join-Path $r 'a.jar') @{ 'a.properties' = ('a' * 20) }
        Write-Zip (Join-Path $r 'b.jar') @{ 'b.properties' = ('b' * 20) }
    } 'expanded-text-bytes' @{ MaxExpandedTextBytes = 32 }
    Write-Zip $inner @{ 'lib/dependency.jar' = $cleanNested }
    $twoLevels = [IO.File]::ReadAllBytes($inner)
    Test-Case 'three-levels-clean' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'lib/second.jar' = $twoLevels } } ''
    Write-Zip $inner @{ 'lib/second.jar' = $twoLevels }
    $threeLevels = [IO.File]::ReadAllBytes($inner)
    Test-Case 'four-levels-abusive' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'lib/third.jar' = $threeLevels } } 'archive-depth'
    Write-Zip $inner @{ 'application.properties' = $sentinel }
    $secretNested = [IO.File]::ReadAllBytes($inner)
    Test-Case 'nested-secret' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'BOOT-INF/lib/dependency.jar' = $secretNested } } 'violations='
    Test-Case 'exact-text-boundary' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'a.txt' = ('a' * 32) } } '' @{ MaxEntryBytes = 32; MaxExpandedTextBytes = 32; MaxArchiveEntries = 1 }
    Test-Case 'empty-text-clean' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'a.properties' = '' } } ''
    foreach ($mode in @('central-zero', 'central-expanded', 'crc-local', 'crc-both', 'offset', 'name', 'flags', 'method', 'truncated', 'descriptor-bad-crc', 'descriptor-bad-size', 'zip64', 'local-zip64-bad', 'gap', 'deflate-truncated', 'deflate-trailing')) {
        Test-Case "structure-$mode" {
            param($r)
            $p = Join-Path $r 'app.jar'
            Write-Zip $p @{ 'application.properties' = 'enabled=false' }
            Edit-ZipFixture $p $mode
        } 'MALFORMED_OR_UNVERIFIABLE_ARCHIVE'
    }
    Test-Case 'redundant-local-zip64-clean' {
        param($r)
        $p = Join-Path $r 'app.jar'
        Write-Zip $p @{ 'application.properties' = 'enabled=false' }
        Edit-ZipFixture $p 'local-zip64'
    } ''
    foreach ($mode in @('descriptor-signed', 'descriptor-unsigned')) {
        Test-Case "clean-$mode" {
            param($r)
            $p = Join-Path $r 'app.jar'
            Write-Zip $p @{ 'application.properties' = 'enabled=false' }
            Edit-ZipFixture $p $mode
        } ''
        Test-Case "secret-$mode" {
            param($r)
            $p = Join-Path $r 'app.jar'
            Write-Zip $p @{ 'application.properties' = $sentinel }
            Edit-ZipFixture $p $mode
        } 'violations='
    }
    foreach ($kind in @('entry', 'text', 'nested')) {
        $limits = @{ MaxEntryBytes = 32 }; $rule = 'archive-entry-size'; $payloadName = 'data.bin'
        if ($kind -eq 'text') { $limits = @{ MaxExpandedTextBytes = 32 }; $rule = 'expanded-text-bytes'; $payloadName = 'application.properties' }
        if ($kind -eq 'nested') { $limits = @{ MaxNestedArchiveBytes = 32 }; $rule = 'nested-archive-bytes'; $payloadName = 'inner.jar' }
        Test-Case "actual-bytes-zero-declared-$kind" {
            param($r)
            $p = Join-Path $r 'app.jar'
            Write-Zip $p @{ $payloadName = ('a' * 128) }
            Edit-ZipFixture $p 'both-expanded-zero'
        } $rule $limits
    }
    Test-Case 'zero-declared-without-budget-overflow' {
        param($r)
        $p = Join-Path $r 'app.jar'
        Write-Zip $p @{ 'application.properties' = $sentinel }
        Edit-ZipFixture $p 'both-expanded-zero'
    } 'MALFORMED_OR_UNVERIFIABLE_ARCHIVE'
    $badEncodings = @{
        'utf8-bom' = [byte[]]@(0xef,0xbb,0xbf,0xc0,0xaf)
        'utf16le-surrogate' = [byte[]]@(0xff,0xfe,0x00,0xd8)
        'utf16be-surrogate' = [byte[]]@(0xfe,0xff,0xd8,0x00)
        'utf16le-odd' = [byte[]]@(0xff,0xfe,0x41)
        'utf32le-range' = [byte[]]@(0xff,0xfe,0,0,0,0,0x11,0)
        'utf32be-range' = [byte[]]@(0,0,0xfe,0xff,0,0x11,0,0)
        'utf32le-truncated' = [byte[]]@(0xff,0xfe,0,0,0x41)
    }
    foreach ($encodingName in $badEncodings.Keys) {
        Test-Case "invalid-$encodingName" { param($r) [IO.File]::WriteAllBytes((Join-Path $r 'application.properties'), $badEncodings[$encodingName]) } 'INVALID_TEXT_ENCODING'
        Test-Case "archive-invalid-$encodingName" { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'application.properties' = $badEncodings[$encodingName] } } 'INVALID_TEXT_ENCODING'
    }
    foreach ($encoding in @([Text.UTF8Encoding]::new($true,$true), [Text.UnicodeEncoding]::new($false,$true,$true), [Text.UnicodeEncoding]::new($true,$true,$true), [Text.UTF32Encoding]::new($false,$true,$true), [Text.UTF32Encoding]::new($true,$true,$true))) {
        Test-Case "unicode-$($encoding.CodePage)" {
            param($r)
            [IO.File]::WriteAllText((Join-Path $r 'application.properties'), ('name=' + [char]0x4e2d + [char]0x6587 + [char]::ConvertFromUtf32(0x1f600)), $encoding)
        } ''
    }
    Test-Case 'binary-invalid-utf-clean' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'data.bin' = [byte[]]@(0xff,0xff,0xff) } } ''
    foreach ($secretBundle in @($false, $true)) {
        $bundleBytes = [byte[]]@(0x6e,0x61,0x6d,0x65,0x3d,0xe7)
        if ($secretBundle) { $bundleBytes = [byte[]]($bundleBytes + [Text.Encoding]::ASCII.GetBytes($sentinel)) }
        Write-Zip $inner @{ 'org/example/messages_pt_PT.properties' = $bundleBytes }
        $bundleArchive = [IO.File]::ReadAllBytes($inner)
        $rejectBundle = ''; if ($secretBundle) { $rejectBundle = 'violations=' }
        Test-Case "latin1-resource-bundle-secret-$secretBundle" { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'BOOT-INF/lib/dependency.jar' = $bundleArchive } } $rejectBundle
    }
    Write-Zip $inner @{ 'org/example/messages_pt_PT.properties' = $badEncodings['utf16le-surrogate'] }
    $invalidBundleArchive = [IO.File]::ReadAllBytes($inner)
    Test-Case 'bundle-bom-remains-strict' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'BOOT-INF/lib/dependency.jar' = $invalidBundleArchive } } 'INVALID_TEXT_ENCODING'
    Write-Zip $inner @{ 'org/example/messages_pt_PT.properties' = $badEncodings['utf8-bom'] }
    $invalidBundleArchive = [IO.File]::ReadAllBytes($inner)
    Test-Case 'bundle-utf8-bom-remains-strict' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'BOOT-INF/lib/dependency.jar' = $invalidBundleArchive } } 'INVALID_TEXT_ENCODING'
    Test-Case 'application-bundle-no-latin1-policy' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'BOOT-INF/classes/org/example/messages_pt_PT.properties' = [byte[]]@(0xe7) } } 'INVALID_TEXT_ENCODING'
    Test-Case 'bundle-location-spoof-rejected' { param($r) Write-Zip (Join-Path $r 'app.jar') @{ 'fake!BOOT-INF/lib/dependency.jar!org/example/messages_pt_PT.properties' = [byte[]]@(0xe7) } } 'INVALID_TEXT_ENCODING'
    Test-Case 'nested-total-across-entries' {
        param($r)
        Write-Zip (Join-Path $r 'app.jar') @{ 'one.jar' = $cleanNested; 'two.jar' = $cleanNested }
    } 'nested-archive-bytes' @{ MaxNestedArchiveBytes = ($cleanNested.Length * 2 - 1) }
    Test-Case 'spring-boot-jar-clean' {
        param($r)
        Write-Zip (Join-Path $r 'app.jar') @{
            'META-INF/MANIFEST.MF' = "Manifest-Version: 1.0`r`nMain-Class: org.springframework.boot.loader.launch.JarLauncher`r`nStart-Class: example.Application`r`n`r`n"
            'BOOT-INF/classes/application.properties' = "spring.application.name=clean-fixture`nserver.port=8080"
            'BOOT-INF/classes/example/Application.class' = [byte[]]@(0xca,0xfe,0xba,0xbe)
            'BOOT-INF/lib/dependency.jar' = $cleanNested
        }
    } ''
    Test-Case 'empty-archive-clean' { param($r) Write-Zip (Join-Path $r 'app.jar') @{} } ''
    Write-Output "ARTIFACT_SAFETY_TEST positive=$positive negative=$negative credentials=SYNTHETIC_ONLY"
} finally {
    $resolved = (Resolve-Path -LiteralPath $runRoot).Path
    if (-not $resolved.StartsWith($tempRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe safety fixture cleanup path' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
