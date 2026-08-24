[CmdletBinding()]
param(
    [ValidateSet('ValidateInput', 'Invoke')]
    [string]$Action = 'ValidateInput',
    [Parameter(Mandatory = $true)][string]$InputPath,
    [string]$ReleaseRoot = '/opt/nexus-quant/current',
    [string]$ExpectedReleaseId,
    [string]$ExpectedManifestSha256,
    [string]$ServerIdentity
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:CommitPattern = '^[0-9a-f]{40}$'
$script:DigestPattern = '^[0-9a-f]{64}$'
$script:InputMaximumBytes = 65536
$script:ContractPath = Join-Path $PSScriptRoot 'gatey-readonly-release-contract.psm1'

function Throw-Blocked([string]$Code)
{
    throw ('BLOCKED / ' + $Code)
}

function Assert-ExactProperties($Value, [string[]]$Allowed, [string]$Name)
{
    if ($null -eq $Value) { Throw-Blocked 'OPERATOR_EXACT_SCOPE_INPUT_REQUIRED' }
    $actual = @($Value.PSObject.Properties.Name)
    if (@($actual | Where-Object { $_ -notin $Allowed }).Count -ne 0 -or
            @($Allowed | Where-Object { $_ -notin $actual }).Count -ne 0)
    {
        Throw-Blocked 'OPERATOR_EXACT_SCOPE_INPUT_SCHEMA_INVALID'
    }
}

function Read-ExactInput([string]$Path)
{
    $fullPath = [IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf))
    {
        Throw-Blocked 'OPERATOR_EXACT_SCOPE_INPUT_REQUIRED'
    }
    $item = Get-Item -LiteralPath $fullPath -Force
    if ($item.Length -lt 2 -or $item.Length -gt $script:InputMaximumBytes -or
            $null -ne $item.LinkType)
    {
        Throw-Blocked 'OPERATOR_EXACT_SCOPE_INPUT_FILE_INVALID'
    }
    $value = Get-Content -LiteralPath $fullPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-ExactProperties $value @(
        'creatorPrincipal', 'approverPrincipal', 'pilotScope', 'pilotApproval',
        'binding', 'exactScopeApproval'
    ) 'root'
    Assert-ExactProperties $value.pilotScope @(
        'sessionId', 'pilotScopeId', 'exchangeAccountId', 'credentialReferenceId',
        'strategyReleaseId', 'releaseDigest', 'releaseAdmissionRevision', 'risk',
        'symbolAllowlist', 'capitalCap', 'pilotWindowStart', 'pilotWindowEnd',
        'expectedPilotScopeHash', 'correlation'
    ) 'pilotScope'
    Assert-ExactProperties $value.pilotScope.risk @(
        'riskPolicyId', 'riskPolicyDigest', 'version', 'capitalCap', 'maxOrderNotional',
        'maxSymbolPositionNotional', 'maxDailyRealizedLoss', 'maxDailyTotalLoss',
        'maxOpenOrders', 'maxIntradayOrders', 'symbolAllowlist', 'maxSessionDurationSeconds',
        'spreadLimitBps', 'slippageLimitBps', 'maxMarketDataAgeMs', 'minDataCoverageBps'
    ) 'risk'
    Assert-ExactProperties $value.pilotScope.correlation @('requestId', 'traceId', 'idempotencyKey') `
        'pilotScopeCorrelation'
    Assert-ExactProperties $value.pilotApproval @(
        'approvalId', 'pilotScopeId', 'expectedPilotScopeHash', 'reason', 'approvedAt', 'expiresAt'
    ) 'pilotApproval'
    Assert-ExactProperties $value.binding @(
        'bindingId', 'instrumentId', 'exchangeInstrumentId', 'side', 'orderType',
        'price', 'quantity', 'notional', 'pilotWindowStart', 'pilotWindowEnd',
        'correlation', 'bindingExpiresAt'
    ) 'binding'
    Assert-ExactProperties $value.binding.correlation @('requestId', 'traceId', 'idempotencyKey') `
        'bindingCorrelation'
    Assert-ExactProperties $value.exactScopeApproval @(
        'creatorCorrelation', 'approverCorrelation', 'reason', 'approvedAt', 'expiresAt'
    ) 'exactScopeApproval'
    Assert-ExactProperties $value.exactScopeApproval.creatorCorrelation `
        @('requestId', 'traceId', 'idempotencyKey') 'creatorCorrelation'
    Assert-ExactProperties $value.exactScopeApproval.approverCorrelation `
        @('requestId', 'traceId', 'idempotencyKey') 'approverCorrelation'
    if ([long]$value.creatorPrincipal -le 0 -or [long]$value.approverPrincipal -le 0 -or
            [long]$value.creatorPrincipal -eq [long]$value.approverPrincipal -or
            [string]$value.binding.orderType -cne 'LIMIT' -or
            [string]$value.exactScopeApproval.reason -cne
                'APPROVED_FOR_EXACT_PILOT_MATERIALIZATION')
    {
        Throw-Blocked 'OPERATOR_EXACT_SCOPE_INPUT_INVALID'
    }
    return [pscustomobject]@{
        path = $fullPath
        sha256 = (Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash.ToLowerInvariant()
        value = $value
    }
}

function Assert-RootLinuxInput($Input)
{
    $linux = Get-Variable -Name IsLinux -ErrorAction SilentlyContinue
    if ($null -eq $linux -or -not [bool]$linux.Value) { Throw-Blocked 'ROOT_LINUX_REQUIRED' }
    if ((& /usr/bin/id -u) -ne '0') { Throw-Blocked 'ROOT_LINUX_REQUIRED' }
    $metadata = (& /usr/bin/stat -Lc '%U:%G:%a' -- $Input.path).Trim()
    if ($LASTEXITCODE -ne 0 -or $metadata -notin @('root:nq-gatey-readonly:640', 'root:root:600'))
    {
        Throw-Blocked 'OPERATOR_EXACT_SCOPE_INPUT_OWNERSHIP_INVALID'
    }
}

function Invoke-ExactControl($Input)
{
    Assert-RootLinuxInput $Input
    if ($ExpectedReleaseId -cnotmatch $script:CommitPattern -or
            $ExpectedManifestSha256 -cnotmatch $script:DigestPattern -or
            [string]::IsNullOrWhiteSpace($ServerIdentity))
    {
        Throw-Blocked 'EXPECTED_RELEASE_IDENTITY_REQUIRED'
    }
    Import-Module $script:ContractPath -Force -DisableNameChecking
    $verified = Test-GateYReadonlyRelease $ReleaseRoot -RequirePosix
    if ([string]$verified.releaseId -cne $ExpectedReleaseId -or
            [string]$verified.manifestSha256 -cne $ExpectedManifestSha256)
    {
        Throw-Blocked 'CURRENT_RELEASE_MISMATCH'
    }
    $resolvedRelease = (& /usr/bin/readlink -f -- $ReleaseRoot).Trim()
    if ($LASTEXITCODE -ne 0 -or $resolvedRelease -cne
            ('/opt/nexus-quant/releases/' + $ExpectedReleaseId))
    {
        Throw-Blocked 'CURRENT_RELEASE_MISMATCH'
    }
    $applicationJar = Join-Path $resolvedRelease 'app/nq-app.jar'
    $runtimeEnvironment = '/etc/nexus-quant/gatey-readonly-qualification/runtime.env'
    $secretEnvironment = '/etc/nexus-quant/gatey-readonly-qualification/secrets.env'
    $bashScript = @'
set -eu
set -a
. /etc/nexus-quant/gatey-readonly-qualification/runtime.env
. /etc/nexus-quant/gatey-readonly-qualification/secrets.env
set +a
PATH=/usr/bin:/bin
export PATH
while IFS='=' read -r name ignored; do
  case "$name" in
    PATH|SPRING_PROFILES_ACTIVE|NQ_APP_BIND_ADDRESS|NQ_APP_PORT|NQ_GATEY_MANAGEMENT_ADDRESS|NQ_GATEY_MANAGEMENT_PORT|NQ_GATEY_RELEASE_ID|NQ_GATEY_SOURCE_COMMIT|NQ_GATEY_RELEASE_MANIFEST_SHA256|NQ_GATEY_QUALIFICATION_DB_URL|NQ_GATEY_QUALIFICATION_DB_USER|NQ_GATEY_DATABASE_TARGET_ID|NQ_GATEY_DATABASE_CREDENTIAL_REFERENCE|NQ_LIVE_ENABLED|NQ_TRADING_COMPONENTS_ENABLED|NQ_RUNTIME_PROVIDER_OBSERVATION_ENABLED|NQ_RUNTIME_PROVIDER_OBSERVATION_ORDER_SUBMISSION_ENABLED|NQ_RUNTIME_PROVIDER_OBSERVATION_CANCEL_ENABLED|NQ_RUNTIME_PROVIDER_OBSERVATION_TRANSFER_ENABLED|NQ_RUNTIME_PROVIDER_OBSERVATION_WITHDRAW_ENABLED|NQ_GATEY_EXPECTED_KILL_SWITCH|NQ_AUTH_BOOTSTRAP_ADMIN_ENABLED|NQ_SECURITY_ISSUER|NQ_SECURITY_ACCESS_TOKEN_TTL|NQ_ACCOUNT_CREDENTIALS_KEY_VERSION|NQ_ACCOUNT_CREDENTIALS_VERIFICATION_MODE|NQ_GATEY_QUALIFICATION_DB_PASSWORD|NQ_SECURITY_SECRET|NQ_ACCOUNT_CREDENTIALS_MASTER_KEY) ;;
    *) unset "$name" ;;
  esac
done < <(/usr/bin/env)
cd /opt/nexus-quant
exec /usr/sbin/runuser --preserve-environment -u nq-gatey-readonly -- /usr/bin/java -jar "$1" \
  --spring.main.web-application-type=none \
  --spring.profiles.active=gatey-readonly-qualification \
  --nq.runtime.exact-pilot-cli.enabled=true \
  --nq.runtime.exact-pilot-cli.input-path="$2" \
  --nq.live-control.exact-pilot-binding.enabled=true \
  --nq.live-control.exact-pilot-binding.manifest-sha256="$3" \
  --nq.live-control.exact-pilot-binding.server-identity="$4" \
  --nq.runtime.provider-observation.release-id="$5" \
  --nq.runtime.provider-observation.source-commit="$5"
'@
    if (-not (Test-Path -LiteralPath $runtimeEnvironment -PathType Leaf) -or
            -not (Test-Path -LiteralPath $secretEnvironment -PathType Leaf))
    {
        Throw-Blocked 'RUNTIME_ENVIRONMENT_NOT_CONFIGURED'
    }
    $output = @(& /usr/bin/bash -c $bashScript exact-pilot-control $applicationJar $Input.path `
        $ExpectedManifestSha256 $ServerIdentity $ExpectedReleaseId 2>&1)
    if ($LASTEXITCODE -ne 0)
    {
        Throw-Blocked 'EXACT_PILOT_CONTROL_INVOCATION_FAILED'
    }
    $marker = 'EXACT_PILOT_CONTROL_RESULT='
    $resultLines = @($output | Where-Object { ([string]$_).Contains($marker) })
    if ($resultLines.Count -ne 1)
    {
        Throw-Blocked 'EXACT_PILOT_CONTROL_RESULT_MISSING'
    }
    $resultLine = [string]$resultLines[0]
    $result = $resultLine.Substring($resultLine.IndexOf($marker) + $marker.Length) | ConvertFrom-Json
    if ([bool]$result.bindingConsumed -or [bool]$result.tradingAuthorized -or
            [bool]$result.exchangeMutation -or [string]$result.lifecycle -cne 'VERIFIED')
    {
        Throw-Blocked 'EXACT_PILOT_CONTROL_RESULT_INVALID'
    }
    return [pscustomobject][ordered]@{
        decision = 'PASS / EXACT_PILOT_SCOPE_MATERIALIZED_AND_BOUND'
        releaseId = $ExpectedReleaseId
        manifestSha256 = $ExpectedManifestSha256
        inputSha256 = $Input.sha256
        sessionId = [string]$result.sessionId
        pilotScopeId = [string]$result.pilotScopeId
        observationSetId = [string]$result.observationSetId
        pilotScopeHash = [string]$result.pilotScopeHash
        exactScopeDigest = [string]$result.exactScopeDigest
        bindingId = [string]$result.bindingId
        bindingDigest = [string]$result.bindingDigest
        lifecycle = [string]$result.lifecycle
        bindingConsumed = $false
        tradingAuthorized = $false
        exchangeMutation = $false
    }
}

try
{
    $input = Read-ExactInput $InputPath
    $result = if ($Action -ceq 'ValidateInput') {
        [pscustomobject][ordered]@{
            decision = 'PASS / OPERATOR_EXACT_SCOPE_INPUT_VALID'
            inputSha256 = $input.sha256
            secretFieldsAccepted = $false
            serverMutation = $false
        }
    } else {
        Invoke-ExactControl $input
    }
    $result | ConvertTo-Json -Depth 6
}
catch
{
    $decision = if ($_.Exception.Message -match '^BLOCKED / [A-Z0-9_]+$') {
        $_.Exception.Message
    } else {
        'FAIL / EXACT_PILOT_SCOPE_CONTROL_INTERNAL_ERROR'
    }
    [pscustomobject]@{ decision = $decision } | ConvertTo-Json
    exit 2
}
