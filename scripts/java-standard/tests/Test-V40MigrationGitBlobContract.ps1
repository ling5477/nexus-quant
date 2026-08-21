[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$verifierPath = Join-Path $repoRoot 'scripts\java-standard\verify-v40-migration-git-blob.ps1'
$currentPwsh = [Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
$migrationPath = 'backend/nq-infra/src/main/resources/db/migration/V40__gate_y6d_pilot_scope_prerequisite_fact_model.sql'
$expectedRawSha256 = '1c0e486db0f3db4cdf250cb99ab0ed1e289f42d1ed522981272ee8b4c4da25e3'

function Assert-Equal([object]$Expected, [object]$Actual, [string]$Name) {
    if ($Expected -cne $Actual) { throw "ASSERT_EQUAL_FAILED: $Name expected=$Expected actual=$Actual" }
}

function Assert-NotEqual([object]$Left, [object]$Right, [string]$Name) {
    if ($Left -ceq $Right) { throw "ASSERT_NOT_EQUAL_FAILED: $Name value=$Left" }
}

function Assert-Throws([scriptblock]$Action, [string]$Name) {
    try { & $Action; throw "ASSERT_THROWS_FAILED: $Name did not throw" }
    catch {
        if ($_.Exception.Message.StartsWith('ASSERT_THROWS_FAILED:', [StringComparison]::Ordinal)) { throw }
    }
}

function Get-GitBlobBytes([string]$Root, [string]$ObjectSpec) {
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'git'
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in @('-C', $Root, 'cat-file', 'blob', $ObjectSpec)) { $startInfo.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $buffer = [IO.MemoryStream]::new()
    try {
        $null = $process.Start()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.StandardOutput.BaseStream.CopyTo($buffer)
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "git cat-file failed: $($stderrTask.GetAwaiter().GetResult())" }
        return ,([byte[]]$buffer.ToArray())
    }
    finally { $buffer.Dispose(); $process.Dispose() }
}

function Get-CanonicalMigrationHash([byte[]]$Bytes) {
    if ($Bytes.Length -ge 3 -and $Bytes[0] -eq 0xef -and $Bytes[1] -eq 0xbb -and $Bytes[2] -eq 0xbf) { throw 'BOM is forbidden' }
    $text = [Text.UTF8Encoding]::new($false, $true).GetString($Bytes)
    $canonical = $text.Replace("`r`n", "`n")
    if ($canonical.Contains("`r")) { throw 'bare CR is forbidden' }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.UTF8Encoding]::new($false).GetBytes($canonical)))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Invoke-Contract([string]$Root, [bool]$ShouldPass, [string]$Name) {
    $output = @(& $currentPwsh -NoProfile -File $verifierPath -RepositoryRoot $Root 2>&1 | ForEach-Object { $_.ToString() })
    $exitCode = $LASTEXITCODE
    if ($ShouldPass) {
        Assert-Equal 0 $exitCode "$Name exit"
        if (-not ($output -contains 'V40_GIT_BLOB_CONTRACT=PASS')) { throw "ASSERT_PASS_OUTPUT_FAILED: $Name" }
    }
    else {
        Assert-Equal 2 $exitCode "$Name exit"
        if (-not (($output -join "`n").Contains('V40_GIT_BLOB_CONTRACT_MISMATCH:'))) { throw "ASSERT_FAIL_CLOSED_FAILED: $Name" }
    }
}

function Invoke-Git([string]$Root, [string[]]$Arguments) {
    & git -C $Root @Arguments | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "git command failed: $($Arguments -join ' ')" }
}

function Commit-Bytes([string]$Root, [byte[]]$Bytes, [string]$Message) {
    $target = Join-Path $Root ($migrationPath.Replace('/', [IO.Path]::DirectorySeparatorChar))
    [IO.Directory]::CreateDirectory((Split-Path $target -Parent)) | Out-Null
    [IO.File]::WriteAllBytes($target, $Bytes)
    Invoke-Git $Root @('add', '--', $migrationPath)
    Invoke-Git $Root @('commit', '--quiet', '-m', $Message)
}

$sourceBytes = Get-GitBlobBytes $repoRoot "HEAD:$migrationPath"
Assert-Equal $expectedRawSha256 (Get-CanonicalMigrationHash $sourceBytes) 'reviewed LF checksum'
$sourceText = [Text.UTF8Encoding]::new($false, $true).GetString($sourceBytes)
$crlfBytes = [Text.UTF8Encoding]::new($false).GetBytes($sourceText.Replace("`n", "`r`n"))
Assert-Equal $expectedRawSha256 (Get-CanonicalMigrationHash $crlfBytes) 'checkout CRLF portability'

$bomBytes = [byte[]]::new($sourceBytes.Length + 3)
$bomBytes[0] = 0xef; $bomBytes[1] = 0xbb; $bomBytes[2] = 0xbf
[Array]::Copy($sourceBytes, 0, $bomBytes, 3, $sourceBytes.Length)
Assert-Throws { Get-CanonicalMigrationHash $bomBytes } 'BOM fail closed'
$bareCrBytes = [Text.UTF8Encoding]::new($false).GetBytes($sourceText.Replace("`n", "`r"))
Assert-Throws { Get-CanonicalMigrationHash $bareCrBytes } 'bare CR fail closed'
$mutatedBytes = [byte[]]$sourceBytes.Clone()
for ($index = 0; $index -lt $mutatedBytes.Length; $index++) {
    if ($mutatedBytes[$index] -notin @(10, 13, 32, 9)) { $mutatedBytes[$index] = $mutatedBytes[$index] -bxor 1; break }
}
Assert-NotEqual $expectedRawSha256 (Get-CanonicalMigrationHash $mutatedBytes) 'semantic mutation sensitivity'
$withoutTrailingLf = [byte[]]::new($sourceBytes.Length - 1)
[Array]::Copy($sourceBytes, $withoutTrailingLf, $withoutTrailingLf.Length)
Assert-NotEqual $expectedRawSha256 (Get-CanonicalMigrationHash $withoutTrailingLf) 'trailing newline sensitivity'

Invoke-Contract $repoRoot $true 'current repository exact blob'
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ("v40-git-blob-contract-{0}" -f [guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($tempRoot) | Out-Null
try {
    Invoke-Git $tempRoot @('init', '--quiet')
    Invoke-Git $tempRoot @('config', 'user.name', 'NQ Contract Test')
    Invoke-Git $tempRoot @('config', 'user.email', 'nq-contract-test@example.invalid')
    Invoke-Git $tempRoot @('config', 'core.autocrlf', 'false')
    Commit-Bytes $tempRoot $sourceBytes 'reviewed LF blob'
    Invoke-Contract $tempRoot $true 'committed LF blob'
    Commit-Bytes $tempRoot $crlfBytes 'CRLF blob drift'
    Invoke-Contract $tempRoot $false 'committed CRLF blob'
    Commit-Bytes $tempRoot $mutatedBytes 'content drift'
    Invoke-Contract $tempRoot $false 'committed content mutation'
    Commit-Bytes $tempRoot $withoutTrailingLf 'trailing newline drift'
    Invoke-Contract $tempRoot $false 'committed trailing newline mutation'
}
finally {
    if (Test-Path -LiteralPath $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
}

Write-Output 'V40_MIGRATION_GIT_BLOB_CONTRACT_TEST=PASS'
Write-Output "V40_EXPECTED_RAW_SHA256=$expectedRawSha256"
