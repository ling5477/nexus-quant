[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]$Revision = 'HEAD'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$migrationPath = 'backend/nq-infra/src/main/resources/db/migration/V40__gate_y6d_pilot_scope_prerequisite_fact_model.sql'
$expectedBlobSha1 = '63052fcd7473e1b6e8a8975c1be45679010b01bb'
$expectedRawSha256 = '1c0e486db0f3db4cdf250cb99ab0ed1e289f42d1ed522981272ee8b4c4da25e3'

function Assert-V40Condition([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "V40_GIT_BLOB_CONTRACT_MISMATCH: $Message" }
}

function Get-GitBlobBytes([string]$Root, [string]$ObjectSpec) {
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'git'
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in @('-C', $Root, 'cat-file', 'blob', $ObjectSpec)) {
        $startInfo.ArgumentList.Add($argument)
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $buffer = [IO.MemoryStream]::new()
    try {
        if (-not $process.Start()) { throw 'git cat-file process did not start' }
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.StandardOutput.BaseStream.CopyTo($buffer)
        $process.WaitForExit()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw "git cat-file failed: $stderr" }
        return ,([byte[]]$buffer.ToArray())
    }
    finally {
        $buffer.Dispose()
        $process.Dispose()
    }
}

function Get-Sha256([byte[]]$Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

try {
    Assert-V40Condition ($Revision -match '^(HEAD|[0-9a-f]{40})$') 'revision must be HEAD or a full lowercase commit SHA'
    $root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
    $resolvedRoot = (& git -C $root rev-parse --show-toplevel).Trim()
    Assert-V40Condition ($LASTEXITCODE -eq 0) 'repository root resolution failed'
    $comparison = if ([IO.Path]::DirectorySeparatorChar -eq [char]'\') { [StringComparison]::OrdinalIgnoreCase } else { [StringComparison]::Ordinal }
    Assert-V40Condition ([string]::Equals([IO.Path]::GetFullPath($root), [IO.Path]::GetFullPath($resolvedRoot), $comparison)) 'RepositoryRoot is not a Git root'

    $objectSpec = "${Revision}:$migrationPath"
    $blobSha1 = (& git -C $root rev-parse --verify $objectSpec).Trim()
    Assert-V40Condition ($LASTEXITCODE -eq 0 -and $blobSha1 -match '^[0-9a-f]{40}$') 'Git blob identity resolution failed'
    Assert-V40Condition ($blobSha1 -eq $expectedBlobSha1) "blob expected=$expectedBlobSha1 actual=$blobSha1"

    $blobBytes = Get-GitBlobBytes $root $objectSpec
    $rawSha256 = Get-Sha256 $blobBytes
    Assert-V40Condition ($rawSha256 -eq $expectedRawSha256) "raw SHA-256 expected=$expectedRawSha256 actual=$rawSha256"
    Assert-V40Condition (-not ($blobBytes.Length -ge 3 -and $blobBytes[0] -eq 0xef -and $blobBytes[1] -eq 0xbb -and $blobBytes[2] -eq 0xbf)) 'committed blob contains UTF-8 BOM'
    Assert-V40Condition (-not ($blobBytes -contains [byte]13)) 'committed blob contains CR bytes'
    Assert-V40Condition ($blobBytes.Length -gt 0 -and $blobBytes[$blobBytes.Length - 1] -eq 10) 'committed blob must end with one reviewed LF byte'

    Write-Output 'V40_GIT_BLOB_CONTRACT=PASS'
    Write-Output "V40_MIGRATION_PATH=$migrationPath"
    Write-Output "V40_GIT_BLOB_SHA1=$blobSha1"
    Write-Output "V40_RAW_SHA256=$rawSha256"
    Write-Output "V40_RAW_BYTES=$($blobBytes.Length)"
    exit 0
}
catch {
    $message = $_.Exception.Message
    Write-Output 'V40_GIT_BLOB_CONTRACT=FAIL'
    if ($message.StartsWith('V40_GIT_BLOB_CONTRACT_MISMATCH:', [StringComparison]::Ordinal)) {
        [Console]::Error.WriteLine($message)
        exit 2
    }
    [Console]::Error.WriteLine("V40_GIT_BLOB_CONTRACT_EXECUTION_FAILED: $message")
    exit 3
}
