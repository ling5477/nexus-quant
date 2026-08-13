[CmdletBinding()]
param(
    [string]$ReleaseRoot,
    [string]$ExpectedReleaseId,
    [string]$ExpectedManifestSha256,
    [string]$ServiceUser = 'nq-live-worker',
    [switch]$ContractSelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:GateWVerifier = Join-Path $PSScriptRoot '../gatew/verify-gatew-release.ps1'
$script:ReleaseIdPattern = '^(?:[a-f0-9]{40}|candidate-[a-f0-9]{12}-[a-f0-9]{16})$'
$script:Sha256Pattern = '^[a-f0-9]{64}$'

function Test-LinuxPlatform
{
    $platform = Get-Variable -Name IsLinux -ErrorAction SilentlyContinue
    return $null -ne $platform -and [bool]$platform.Value
}

function Assert-ServiceIdentity
{
    param([Parameter(Mandatory = $true)][string]$Value)
    if ($Value -cnotmatch '^[a-z_][a-z0-9_-]{0,30}$' -or $Value -eq 'root')
    {
        throw 'BLOCKED / WORKER_PROCESS_IDENTITY_INVALID'
    }
}

function Assert-GateWVerificationEvidence
{
    param(
        [Parameter(Mandatory = $true)]$Evidence,
        [Parameter(Mandatory = $true)][string]$ReleaseId,
        [Parameter(Mandatory = $true)][string]$ManifestSha256
    )
    if ([string]$Evidence.decision -cne 'PASS / IMMUTABLE_RELEASE_VERIFIED' -or
            [string]$Evidence.releaseId -cne $ReleaseId -or
            [string]$Evidence.manifestSha256 -cne $ManifestSha256 -or
            $Evidence.posixVerified -isnot [bool] -or -not [bool]$Evidence.posixVerified)
    {
        throw 'BLOCKED / GATEW_RELEASE_EVIDENCE_INVALID'
    }
    $expectedRoot = "/opt/nexus-quant/releases/$ReleaseId"
    $actualRoot = if (Test-LinuxPlatform)
    {
        [IO.Path]::GetFullPath([string]$Evidence.releaseRoot)
    }
    else
    {
        [string]$Evidence.releaseRoot
    }
    if ($actualRoot -cne $expectedRoot)
    {
        throw 'BLOCKED / RELEASE_PROCESS_IDENTITY_MISMATCH'
    }
}

function Invoke-ContractSelfTest
{
    $releaseId = '1111111111111111111111111111111111111111'
    $manifest = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
    Assert-ServiceIdentity 'nq-live-worker'
    Assert-GateWVerificationEvidence ([pscustomobject]@{
        decision = 'PASS / IMMUTABLE_RELEASE_VERIFIED'
        releaseId = $releaseId
        manifestSha256 = $manifest
        posixVerified = $true
        releaseRoot = "/opt/nexus-quant/releases/$releaseId"
    }) $releaseId $manifest
    foreach ($unsafeUser in @('root', '', 'NQ Live Worker', 'nq-live-worker;id'))
    {
        try
        {
            Assert-ServiceIdentity $unsafeUser
            throw 'self-test accepted unsafe worker identity'
        }
        catch
        {
            if ($_.Exception.Message -eq 'self-test accepted unsafe worker identity') { throw }
        }
    }
    Write-Output 'PASS / GATEY4_DEPLOYMENT_BOUNDARY_CONTRACT_SELF_TEST'
}

try
{
    if ($ContractSelfTest)
    {
        Invoke-ContractSelfTest
        exit 0
    }
    if (-not (Test-LinuxPlatform))
    {
        throw 'BLOCKED / OTHER_OS_DEV_RUNTIME_NOT_AUTHORIZED'
    }
    if ([Environment]::UserName -ne 'root')
    {
        throw 'BLOCKED / ROOT_RELEASE_VERIFY_REQUIRED'
    }
    if ([string]::IsNullOrWhiteSpace($ReleaseRoot) -or
            $ExpectedReleaseId -cnotmatch $script:ReleaseIdPattern -or
            $ExpectedManifestSha256 -cnotmatch $script:Sha256Pattern)
    {
        throw 'BLOCKED / DEPLOYMENT_EVIDENCE_REQUIRED'
    }
    Assert-ServiceIdentity $ServiceUser
    if (-not (Test-Path -LiteralPath $script:GateWVerifier -PathType Leaf))
    {
        throw 'BLOCKED / GATEW_RELEASE_VERIFIER_MISSING'
    }

    # GateY-4 intentionally delegates release/root/POSIX/systemd verification to the existing GateW authority.
    $powershellPath = if (Test-LinuxPlatform) { '/usr/bin/pwsh' } else { 'powershell' }
    $raw = & $powershellPath -NoProfile -ExecutionPolicy Bypass -File $script:GateWVerifier `
        -ReleaseRoot $ReleaseRoot `
        -ExpectedReleaseId $ExpectedReleaseId `
        -ExpectedManifestSha256 $ExpectedManifestSha256 2>&1
    if ($LASTEXITCODE -ne 0)
    {
        throw 'BLOCKED / GATEW_RELEASE_VERIFICATION_FAILED'
    }
    $evidence = ($raw -join "`n") | ConvertFrom-Json
    Assert-GateWVerificationEvidence $evidence $ExpectedReleaseId $ExpectedManifestSha256

    [pscustomobject]@{
        decision = 'PASS / GATEY4_RELEASE_PREREQUISITES_VERIFIED'
        releaseId = $ExpectedReleaseId
        manifestSha256 = $ExpectedManifestSha256
        serviceUser = $ServiceUser
        posixVerified = $true
        stableHandleStatus = 'RUNTIME_JAVA_ADMISSION_REQUIRED'
        currentKillStatus = 'RUNTIME_JAVA_ADMISSION_REQUIRED'
        credentialCapabilityStatus = 'RUNTIME_JAVA_ADMISSION_REQUIRED'
        startAuthorization = $false
        tradingAuthorization = $false
    } | ConvertTo-Json -Depth 4
}
catch
{
    $message = if ($_.Exception.Message -match '^(BLOCKED|FAIL) / [A-Z0-9_]+$')
    {
        $_.Exception.Message
    }
    else
    {
        'FAIL / GATEY4_DEPLOYMENT_BOUNDARY_INTERNAL_ERROR'
    }
    [Console]::Error.WriteLine($message)
    exit 1
}
