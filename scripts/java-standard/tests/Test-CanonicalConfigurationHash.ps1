[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$scannerPath = Join-Path $repoRoot "scripts\java-standard\invoke-java-shadow-scan.ps1"
$verifierPath = Join-Path $repoRoot "scripts\java-standard\verify-java-engineering-standard.ps1"

function Get-CanonicalBlock([string]$Path) {
    $text = [IO.File]::ReadAllText($Path, [Text.UTF8Encoding]::new($false, $true))
    $match = [regex]::Match($text, '(?s)# CANONICAL_CONFIG_HASH_START\r?\n(?<body>.*?)\r?\n# CANONICAL_CONFIG_HASH_END')
    if (-not $match.Success) { throw "CANONICAL_BLOCK_MISSING: $Path" }
    return $match.Groups['body'].Value.Replace("`r`n", "`n")
}

function Assert-Equal([object]$Expected, [object]$Actual, [string]$Name) {
    if ($Expected -cne $Actual) { throw "ASSERT_EQUAL_FAILED: $Name expected=$Expected actual=$Actual" }
}

function Assert-NotEqual([object]$Left, [object]$Right, [string]$Name) {
    if ($Left -ceq $Right) { throw "ASSERT_NOT_EQUAL_FAILED: $Name value=$Left" }
}

function Assert-Throws([scriptblock]$Action, [string]$ExpectedPrefix, [string]$Name) {
    try { & $Action; throw "ASSERT_THROWS_FAILED: $Name did not throw" }
    catch {
        if (-not $_.Exception.Message.StartsWith($ExpectedPrefix, [StringComparison]::Ordinal)) {
            throw "ASSERT_THROWS_FAILED: $Name expectedPrefix=$ExpectedPrefix actual=$($_.Exception.Message)"
        }
    }
}

function Write-Utf8Fixture([string]$Path, [string]$Text) {
    [IO.Directory]::CreateDirectory((Split-Path $Path -Parent)) | Out-Null
    [IO.File]::WriteAllText($Path, $Text, [Text.UTF8Encoding]::new($false))
}

$scannerBlock = Get-CanonicalBlock $scannerPath
$verifierBlock = Get-CanonicalBlock $verifierPath
Assert-Equal $scannerBlock $verifierBlock "scanner/verifier canonical implementation parity"
Invoke-Expression $scannerBlock

$actualInputs = @(Get-CanonicalConfigurationInputPaths $repoRoot)
Assert-Equal 10 $actualInputs.Count "closed input count"
Assert-Equal "git-canonical-v1" (Get-CanonicalConfigurationHashAlgorithm) "algorithm version"

$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ("shadow-canonical-hash-{0}" -f [guid]::NewGuid().ToString("N"))
$aPath = Join-Path $fixtureRoot "a\config.txt"
$bPath = Join-Path $fixtureRoot "b\empty.txt"
$cPath = Join-Path $fixtureRoot "c\config.txt"
$inputs = @("a/config.txt", "b/empty.txt")
try {
    Write-Utf8Fixture $aPath "alpha`nline-2`n"
    Write-Utf8Fixture $bPath ""
    $lfHash = Get-CanonicalConfigurationHash $fixtureRoot $inputs
    Assert-Equal $lfHash (Get-CanonicalConfigurationHash $fixtureRoot @("b/empty.txt", "a/config.txt")) "ordinal input sorting"

    Write-Utf8Fixture $aPath "alpha`r`nline-2`r`n"
    $crlfHash = Get-CanonicalConfigurationHash $fixtureRoot $inputs
    Assert-Equal $lfHash $crlfHash "LF/CRLF checkout parity"

    Write-Utf8Fixture $aPath "Alpha`nline-2`n"
    Assert-NotEqual $lfHash (Get-CanonicalConfigurationHash $fixtureRoot $inputs) "non-EOL content mutation"

    Write-Utf8Fixture $aPath "alpha`n"
    Assert-NotEqual $lfHash (Get-CanonicalConfigurationHash $fixtureRoot $inputs) "line deletion"

    Write-Utf8Fixture $aPath "alpha`nline-2"
    Assert-NotEqual $lfHash (Get-CanonicalConfigurationHash $fixtureRoot $inputs) "trailing newline preservation"

    Write-Utf8Fixture $aPath "alpha`nline-2`n"
    Write-Utf8Fixture $cPath "alpha`nline-2`n"
    Assert-NotEqual $lfHash (Get-CanonicalConfigurationHash $fixtureRoot @("c/config.txt", "b/empty.txt")) "path framing"

    Write-Utf8Fixture $aPath ""
    Write-Utf8Fixture $bPath "alpha`nline-2`n"
    Assert-NotEqual $lfHash (Get-CanonicalConfigurationHash $fixtureRoot $inputs) "content swap framing"

    Write-Utf8Fixture $aPath "alpha`rline-2`n"
    Write-Utf8Fixture $bPath ""
    Assert-Throws { Get-CanonicalConfigurationHash $fixtureRoot $inputs } "CONFIG_INVALID: bare CR" "bare CR fail closed"

    $bomPayload = [Text.UTF8Encoding]::new($false).GetBytes("alpha`n")
    $bomBytes = [byte[]]::new($bomPayload.Length + 3)
    $bomBytes[0] = 0xef; $bomBytes[1] = 0xbb; $bomBytes[2] = 0xbf
    [Array]::Copy($bomPayload, 0, $bomBytes, 3, $bomPayload.Length)
    [IO.File]::WriteAllBytes($aPath, $bomBytes)
    Assert-Throws { Get-CanonicalConfigurationHash $fixtureRoot $inputs } "CONFIG_INVALID: UTF-8 BOM" "BOM fail closed"

    [IO.File]::WriteAllBytes($aPath, [byte[]]@(0xc3, 0x28))
    Assert-Throws { Get-CanonicalConfigurationHash $fixtureRoot $inputs } "CONFIG_INVALID: invalid UTF-8" "invalid UTF-8 fail closed"

    Write-Output "CANONICAL_HASH_CONTRACT_TEST=PASS"
    Write-Output "CONFIGURATION_HASH_ALGORITHM=$(Get-CanonicalConfigurationHashAlgorithm)"
    Write-Output "FIXTURE_LF_HASH=$lfHash"
    Write-Output "FIXTURE_CRLF_HASH=$crlfHash"
    Write-Output "CONFIG_HASH_INPUTS=$($actualInputs -join ',')"
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
}
