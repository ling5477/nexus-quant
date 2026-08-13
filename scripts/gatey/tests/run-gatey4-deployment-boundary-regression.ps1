[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$target = Join-Path $PSScriptRoot '../verify-gatey4-worker-deployment-boundary.ps1'
if (-not (Test-Path -LiteralPath $target -PathType Leaf))
{
    throw 'GateY-4 deployment boundary script missing'
}

$selfTestOutput = @(& powershell -NoProfile -ExecutionPolicy Bypass -File $target -ContractSelfTest 2>&1)
if ($LASTEXITCODE -ne 0 -or
        ($selfTestOutput -join "`n") -notmatch 'PASS / GATEY4_DEPLOYMENT_BOUNDARY_CONTRACT_SELF_TEST')
{
    throw 'GateY-4 deployment boundary contract self-test failed'
}

$source = Get-Content -LiteralPath $target -Raw
$required = @(
    '../gatew/verify-gatew-release.ps1',
    'OTHER_OS_DEV_RUNTIME_NOT_AUTHORIZED',
    'ROOT_RELEASE_VERIFY_REQUIRED',
    'startAuthorization = $false',
    'tradingAuthorization = $false',
    'RUNTIME_JAVA_ADMISSION_REQUIRED'
)
foreach ($marker in $required)
{
    if (-not $source.Contains($marker))
    {
        throw "deployment boundary marker missing: $marker"
    }
}

$forbidden = @(
    'systemctl start',
    'Start-Process',
    'Invoke-WebRequest',
    'Invoke-RestMethod',
    'apiKey=',
    'passphrase=',
    'secret='
)
foreach ($marker in $forbidden)
{
    if ($source.IndexOf($marker, [StringComparison]::OrdinalIgnoreCase) -ge 0)
    {
        throw "deployment boundary contains forbidden behavior: $marker"
    }
}

Write-Output 'PASS / GATEY4_DEPLOYMENT_BOUNDARY_REGRESSION cases=delegate-release,linux-root,identity,no-start,no-secret,no-network'
