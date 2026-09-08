[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $EvidenceRoot,
    # 调用方只能收紧上限；测试使用较小预算，生产调用沿用默认硬上限。
    [ValidateRange(1, 50000)][int] $MaxArchiveEntries = 50000,
    [ValidateRange(1, 67108864)][long] $MaxEntryBytes = 67108864,
    [ValidateRange(1, 134217728)][long] $MaxExpandedTextBytes = 134217728,
    [ValidateRange(1, 536870912)][long] $MaxNestedArchiveBytes = 536870912,
    [ValidateRange(1, 268435456)][long] $MaxArchiveBytes = 268435456,
    [ValidateRange(1, 3)][int] $MaxArchiveDepth = 3
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $EvidenceRoot).Path
$violations = New-Object System.Collections.Generic.List[string]
$textExtensions = @('.json', '.html', '.js', '.css', '.txt', '.xml', '.yml', '.yaml', '.toml', '.md', '.properties', '.conf', '.config', '.ini', '.cfg', '.csv', '.map', '.mf')
$archiveExtensions = @('.jar', '.zip', '.war', '.ear')
$budget = @{ entries = 0L; textBytes = 0L; nestedBytes = 0L }
$forbiddenPath = '(?i)(^|[\\/])(secrets?|credentials?)([\\/]|$)|(^|[\\/])\.env($|\.)|\.(pem|key|p12|jks|keystore|dump|backup|log)$'
$contentRules = [ordered]@{
    aws_access_key = 'AKIA[0-9A-Z]{16}'
    github_token = '(github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{30,})'
    provider_key = '(?<![A-Za-z0-9])sk-(proj-|ant-)?[A-Za-z0-9_-]{20,}'
    private_key = '-----BEGIN (RSA |EC |OPENSSH |DSA |PGP )?PRIVATE KEY-----'
    credentials_in_url = '[a-zA-Z][a-zA-Z0-9+.-]*://[^/@\s]+:[^/@\s]+@'
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
. (Join-Path $PSScriptRoot 'DeliveryArchiveIntegrity.ps1')
function Stop-Limit([string] $Rule, [string] $Location) {
    throw "Delivery artifact safety limit=$Rule file=$Location"
}

function Read-Bounded([IO.Stream] $Stream, [long] $Limit, [string] $Location) {
    $buffer = New-Object byte[] 8192
    $output = [IO.MemoryStream]::new()
    try {
        while (($count = $Stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            if ($output.Length + $count -gt $Limit) { Stop-Limit 'expanded-bytes' $Location }
            $output.Write($buffer, 0, $count)
        }
        return ,($output.ToArray())
    } finally { $output.Dispose() }
}

function Test-TextStream([IO.Stream] $Stream, [string] $Location, [bool] $JavaMessageBundle = $false) {
    $remaining = [Math]::Min($MaxEntryBytes, $MaxExpandedTextBytes - $budget.textBytes)
    $bytes = Read-Bounded $Stream $remaining $Location
    $budget.textBytes += $bytes.LongLength
    # BOM 仅选择严格编码器，避免 StreamReader 自动切换到替换式解码器。
    $encoding = [Text.UTF8Encoding]::new($false, $true)
    $offset = 0
    # Java 依赖包中的国际化消息资源束沿用 Latin-1；应用配置不使用该策略，也不进行解码失败回退。
    if ($JavaMessageBundle) {
        $encoding = [Text.Encoding]::GetEncoding(28591, [Text.EncoderFallback]::ExceptionFallback, [Text.DecoderFallback]::ExceptionFallback)
    }
    if ($bytes.Length -ge 4 -and $bytes[0] -eq 0xff -and $bytes[1] -eq 0xfe -and $bytes[2] -eq 0 -and $bytes[3] -eq 0) {
        $encoding = [Text.UTF32Encoding]::new($false, $false, $true); $offset = 4
    } elseif ($bytes.Length -ge 4 -and $bytes[0] -eq 0 -and $bytes[1] -eq 0 -and $bytes[2] -eq 0xfe -and $bytes[3] -eq 0xff) {
        $encoding = [Text.UTF32Encoding]::new($true, $false, $true); $offset = 4
    } elseif ($bytes.Length -ge 3 -and $bytes[0] -eq 0xef -and $bytes[1] -eq 0xbb -and $bytes[2] -eq 0xbf) {
        $encoding = [Text.UTF8Encoding]::new($false, $true); $offset = 3
    } elseif ($bytes.Length -ge 2 -and $bytes[0] -eq 0xff -and $bytes[1] -eq 0xfe) {
        $encoding = [Text.UnicodeEncoding]::new($false, $false, $true); $offset = 2
    } elseif ($bytes.Length -ge 2 -and $bytes[0] -eq 0xfe -and $bytes[1] -eq 0xff) {
        $encoding = [Text.UnicodeEncoding]::new($true, $false, $true); $offset = 2
    }
    try { $content = $encoding.GetString($bytes, $offset, $bytes.Length - $offset) }
    catch { throw "Delivery artifact safety invalid-text INVALID_TEXT_ENCODING file=$Location" }
    foreach ($rule in $contentRules.GetEnumerator()) {
        if ([regex]::IsMatch($content, [string]$rule.Value, [Text.RegularExpressions.RegexOptions]::None, [TimeSpan]::FromSeconds(2))) {
            $violations.Add("$($rule.Key) file=$Location")
        }
    }
}

function Test-ArchiveStream([IO.Stream] $Stream, [int] $Depth, [string] $Location, [bool] $DependencyJar = $false) {
    if ($Depth -gt $MaxArchiveDepth) { Stop-Limit 'archive-depth' $Location }
    try {
        $entries = [DeliveryArchiveIntegrity]::Validate($Stream, $MaxArchiveEntries - $budget.entries)
        $budget.entries += $entries.Count
        foreach ($entry in $entries) {
            $entryLocation = "$Location!$($entry.Name)"
            $entryName = $entry.Name.Replace('\', '/')
            if ($entryName -match $forbiddenPath) { $violations.Add("forbidden_archive_path file=$entryLocation") }
            if ($entryName -match '(^/|^[A-Za-z]:|(^|/)\.\.(/|$))') { $violations.Add("unsafe_archive_path file=$entryLocation") }
            $extension = [IO.Path]::GetExtension($entryName).ToLowerInvariant()
            $nestedEntry = $archiveExtensions -contains $extension
            $textEntry = $textExtensions -contains $extension
            $remaining = $MaxEntryBytes
            $limitRule = 'archive-entry-size'
            if ($nestedEntry) {
                if ($Depth -ge $MaxArchiveDepth) { Stop-Limit 'archive-depth' $entryLocation }
                if ($MaxNestedArchiveBytes - $budget.nestedBytes -lt $remaining) {
                    $remaining = $MaxNestedArchiveBytes - $budget.nestedBytes; $limitRule = 'nested-archive-bytes'
                }
            } elseif ($textEntry -and $MaxExpandedTextBytes - $budget.textBytes -lt $remaining) {
                $remaining = $MaxExpandedTextBytes - $budget.textBytes; $limitRule = 'expanded-text-bytes'
            }
            # 所有 entry（包括二进制、目录和声明为空的 entry）均实际读取并核对长度与 CRC。
            $bytes = [DeliveryArchiveIntegrity]::ReadContent($Stream, $entry, $remaining, $limitRule)
            if ($nestedEntry) {
                $budget.nestedBytes += $bytes.LongLength
                $nested = [IO.MemoryStream]::new($bytes, $false)
                try { Test-ArchiveStream $nested ($Depth + 1) $entryLocation ($entryName -cmatch '^BOOT-INF/lib/[^/!]+\.jar$') }
                finally { $nested.Dispose() }
            } elseif ($textEntry) {
                $textStream = [IO.MemoryStream]::new($bytes, $false)
                try {
                    $javaBundle = $DependencyJar -and $entryName -cmatch '^(?:[A-Za-z_][A-Za-z0-9_]*/)+messages(?:_[a-z]{2}(?:_[A-Z]{2})?)?\.properties$'
                    Test-TextStream $textStream $entryLocation $javaBundle
                }
                finally { $textStream.Dispose() }
            }
        }
    } catch {
        # PowerShell 会包装标准库异常；只保留本工具的拒绝身份，不输出内容。
        $failure = $_.Exception
        while ($null -ne $failure.InnerException) { $failure = $failure.InnerException }
        if ($failure.Message.StartsWith('Delivery artifact safety')) { throw ($failure.Message + " file=$Location") }
        throw "Delivery artifact safety invalid-archive MALFORMED_OR_UNVERIFIABLE_ARCHIVE file=$Location"
    }
}

function Test-Directory([string] $Directory) {
    foreach ($file in Get-ChildItem -LiteralPath $Directory -Force) {
        $relative = $file.FullName.Substring($root.Length).TrimStart('\', '/').Replace('\', '/')
        if (($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            $violations.Add("link file=$relative")
            continue
        }
        if ($relative -match $forbiddenPath) {
            $violations.Add("forbidden_path file=$relative")
            continue
        }
        if ($file.PSIsContainer) { Test-Directory $file.FullName; continue }
        $extension = $file.Extension.ToLowerInvariant()
        if ($archiveExtensions -contains $extension) {
            if ($file.Length -gt $MaxArchiveBytes) { Stop-Limit 'archive-file-size' $relative }
            $stream = [IO.File]::OpenRead($file.FullName)
            try {
                $archiveBytes = Read-Bounded $stream $MaxArchiveBytes $relative
                $archiveStream = [IO.MemoryStream]::new($archiveBytes, $false)
                try { Test-ArchiveStream $archiveStream 1 $relative }
                finally { $archiveStream.Dispose() }
            }
            finally { $stream.Dispose() }
        } elseif ($textExtensions -contains $extension) {
            if ($file.Length -gt $MaxEntryBytes -or $file.Length -gt $MaxExpandedTextBytes - $budget.textBytes) {
                Stop-Limit 'text-size' $relative
            }
            $stream = [IO.File]::OpenRead($file.FullName)
            try { Test-TextStream $stream $relative }
            finally { $stream.Dispose() }
        }
    }
}

if ((Get-Item -LiteralPath $root -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Delivery artifact safety linked-root' }
if (-not (Test-Path -LiteralPath $root -PathType Container)) { throw 'Delivery artifact safety invalid-root' }
Test-Directory $root

if ($violations.Count -gt 0) {
    Write-Output 'Delivery artifact safety gate failed (rule and path only; matched content is suppressed):'
    $violations | Sort-Object -Unique | ForEach-Object { Write-Output "  $_" }
    throw "Delivery artifact safety violations=$($violations.Count)"
}
Write-Output "DELIVERY_ARTIFACT_SAFETY=PASS root=$EvidenceRoot"
