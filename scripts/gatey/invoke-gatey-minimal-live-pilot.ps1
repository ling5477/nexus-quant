[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][long]$ExchangeAccountId,
    [Parameter(Mandatory = $true)][long]$CredentialReferenceId,
    [Parameter(Mandatory = $true)][ValidateSet('BTC-USDT')][string]$Instrument,
    [Parameter(Mandatory = $true)][ValidateSet('BUY')][string]$Side,
    [Parameter(Mandatory = $true)][decimal]$ConfiguredPilotMaxNotional,
    [string]$ReleaseRoot = '/opt/nexus-quant/current'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:ContractPath = Join-Path $PSScriptRoot 'gatey-readonly-release-contract.psm1'

function Throw-Blocked([string]$Code) { throw ('BLOCKED / ' + $Code) }

try {
    $linux = Get-Variable -Name IsLinux -ErrorAction SilentlyContinue
    if ($null -eq $linux -or -not [bool]$linux.Value -or (& /usr/bin/id -u) -ne '0') {
        Throw-Blocked 'ROOT_LINUX_REQUIRED'
    }
    if ($ExchangeAccountId -le 0 -or $CredentialReferenceId -le 0 -or
            $ConfiguredPilotMaxNotional -le 0 -or $ConfiguredPilotMaxNotional -gt 10) {
        Throw-Blocked 'OPERATOR_PILOT_PARAMETERS_REQUIRED'
    }
    if ($Instrument -cnotmatch '^[A-Z0-9]{2,20}-USDT$') {
        Throw-Blocked 'OPERATOR_PILOT_PARAMETERS_REQUIRED'
    }
    $release = (& /usr/bin/readlink -f -- $ReleaseRoot).Trim()
    if ($LASTEXITCODE -ne 0 -or $release -cnotmatch '^/opt/nexus-quant/releases/[0-9a-f]{40}$') {
        Throw-Blocked 'CURRENT_RELEASE_MISMATCH'
    }
    if (-not (Test-Path -LiteralPath $script:ContractPath -PathType Leaf)) {
        Throw-Blocked 'RELEASE_CONTRACT_MISSING'
    }
    Import-Module $script:ContractPath -Force -DisableNameChecking
    $verified = Test-GateYReadonlyRelease $ReleaseRoot -RequirePosix
    if ($release -cne ('/opt/nexus-quant/releases/' + [string]$verified.releaseId)) {
        Throw-Blocked 'CURRENT_RELEASE_MISMATCH'
    }
    $machineIdPath = '/etc/machine-id'
    $resolvedMachineId = (& /usr/bin/readlink -f -- $machineIdPath).Trim()
    if ($LASTEXITCODE -ne 0 -or $resolvedMachineId -cne $machineIdPath -or
            -not (Test-Path -LiteralPath $machineIdPath -PathType Leaf)) {
        Throw-Blocked 'SERVER_IDENTITY_UNAVAILABLE'
    }
    $serverIdentity = 'machine-id-sha256:' +
        (Get-FileHash -LiteralPath $machineIdPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $jar = Join-Path $release 'app/nq-app.jar'
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { Throw-Blocked 'RELEASE_ARTIFACT_MISSING' }
    $bash = @'
set -eu
set -a
. /etc/nexus-quant/gatey-readonly-qualification/runtime.env
. /etc/nexus-quant/gatey-readonly-qualification/secrets.env
set +a
PATH=/usr/bin:/bin
export PATH
while IFS='=' read -r name ignored; do
  case "$name" in
    PATH|SPRING_PROFILES_ACTIVE|NQ_APP_BIND_ADDRESS|NQ_APP_PORT|NQ_GATEY_MANAGEMENT_ADDRESS|NQ_GATEY_MANAGEMENT_PORT|NQ_GATEY_RELEASE_ID|NQ_GATEY_SOURCE_COMMIT|NQ_GATEY_RELEASE_MANIFEST_SHA256|NQ_GATEY_QUALIFICATION_DB_URL|NQ_GATEY_QUALIFICATION_DB_USER|NQ_GATEY_DATABASE_TARGET_ID|NQ_GATEY_DATABASE_CREDENTIAL_REFERENCE|NQ_LIVE_ENABLED|NQ_TRADING_COMPONENTS_ENABLED|NQ_RUNTIME_PROVIDER_OBSERVATION_ENABLED|NQ_RUNTIME_PROVIDER_OBSERVATION_ORDER_SUBMISSION_ENABLED|NQ_RUNTIME_PROVIDER_OBSERVATION_CANCEL_ENABLED|NQ_RUNTIME_PROVIDER_OBSERVATION_TRANSFER_ENABLED|NQ_RUNTIME_PROVIDER_OBSERVATION_WITHDRAW_ENABLED|NQ_GATEY_EXPECTED_KILL_SWITCH|NQ_GATEY_OKX_EXPECTED_IP|NQ_GATEW_OKX_EXPECTED_IP|NQ_AUTH_BOOTSTRAP_ADMIN_ENABLED|NQ_SECURITY_ISSUER|NQ_SECURITY_ACCESS_TOKEN_TTL|NQ_ACCOUNT_CREDENTIALS_KEY_VERSION|NQ_ACCOUNT_CREDENTIALS_VERIFICATION_MODE|NQ_GATEY_QUALIFICATION_DB_PASSWORD|NQ_SECURITY_SECRET|NQ_ACCOUNT_CREDENTIALS_MASTER_KEY|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_MINIMAL_LIVE_PILOT_STRATEGY_RELEASE_ID|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_MINIMAL_LIVE_PILOT_RISK_LIMIT_SET_ID|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_INSTRUMENT_METADATA_DIGEST|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_INSTRUMENT_SOURCE_IDENTITY|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_INSTRUMENT_SOURCE_SCHEMA_VERSION|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_INSTRUMENT_MAXIMUM_AGE_MS|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_FEE_SCHEDULE_DIGEST|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_FEE_TIER|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_FEE_EVIDENCE_CLASS|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_FEE_SOURCE_IDENTITY|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_FEE_SOURCE_SCHEMA_VERSION|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_FEE_MAXIMUM_AGE_MS|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_BALANCE_SOURCE_IDENTITY|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_BALANCE_SOURCE_SCHEMA_VERSION|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_BALANCE_MAXIMUM_AGE_MS|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_CLOCK_SOURCE_IDENTITY|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_CLOCK_SOURCE_SCHEMA_VERSION|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_CLOCK_MAXIMUM_AGE_MS|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_SIGNED_TIMESTAMP_SOURCE|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_MAXIMUM_TOLERATED_SKEW_MS|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_ENDPOINT_POLICY_VERSION|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_ENDPOINT_POLICY_DIGEST|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_PROVIDER_CONTRACT_IDENTITY|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_PROVIDER_ARTIFACT_DIGEST|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_WORKER_IDENTITY|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_WORKER_RELEASE_DIGEST) ;;
    *) unset "$name" ;;
  esac
done < <(/usr/bin/env)
cd /opt/nexus-quant
expected_ip="${NQ_GATEY_OKX_EXPECTED_IP:-${NQ_GATEW_OKX_EXPECTED_IP:-}}"
test -n "$expected_ip"
exec /usr/sbin/runuser --preserve-environment -u nq-gatey-readonly -- /usr/bin/java -jar "$1" \
  --spring.main.web-application-type=none \
  --spring.profiles.active=gatey-readonly-qualification \
  --nq.okx.private-readonly-diagnostics.enabled=true \
  --nq.okx.private-readonly-diagnostics.order-submission-enabled=false \
  --nq.okx.private-readonly-diagnostics.transfer-enabled=false \
  --nq.okx.private-readonly-diagnostics.withdraw-enabled=false \
  --nq.okx.private-readonly-diagnostics.permission-probe.enabled=true \
  --nq.okx.private-readonly-diagnostics.permission-probe.expected-ip="$expected_ip" \
  --nq.live-control.exact-pilot-binding.enabled=true \
  --nq.live-control.exact-pilot-binding.manifest-sha256="$7" \
  --nq.live-control.exact-pilot-binding.server-identity="$8" \
  --nq.runtime.provider-observation.release-id="$9" \
  --nq.runtime.provider-observation.source-commit="$9" \
  --nq.runtime.provider-observation.enabled=true \
  --nq.runtime.provider-observation.order-submission-enabled=false \
  --nq.runtime.provider-observation.cancel-enabled=false \
  --nq.runtime.provider-observation.transfer-enabled=false \
  --nq.runtime.provider-observation.withdraw-enabled=false \
  --nq.runtime.minimal-live-pilot.enabled=true \
  --nq.runtime.minimal-live-pilot.order-submission-enabled=true \
  --nq.runtime.minimal-live-pilot.cancel-enabled=true \
  --nq.runtime.minimal-live-pilot.transfer-enabled=false \
  --nq.runtime.minimal-live-pilot.withdraw-enabled=false \
  --nq.runtime.minimal-live-pilot.exchange-account-id="$2" \
  --nq.runtime.minimal-live-pilot.credential-reference-id="$3" \
  --nq.runtime.minimal-live-pilot.instrument="$4" \
  --nq.runtime.minimal-live-pilot.side="$5" \
  --nq.runtime.minimal-live-pilot.configured-max-notional="$6"
'@
    & /usr/bin/bash -c $bash minimal-live-pilot $jar $ExchangeAccountId $CredentialReferenceId `
        $Instrument $Side ([string]$ConfiguredPilotMaxNotional) `
        ([string]$verified.manifestSha256) $serverIdentity ([string]$verified.releaseId)
    if ($LASTEXITCODE -ne 0) { Throw-Blocked 'MINIMAL_LIVE_PILOT_INVOCATION_FAILED' }
}
catch {
    $decision = if ($_.Exception.Message -match '^BLOCKED / [A-Z0-9_]+$') {
        $_.Exception.Message
    } else { 'FAIL / MINIMAL_LIVE_PILOT_INTERNAL_ERROR' }
    [pscustomobject]@{ decision = $decision } | ConvertTo-Json
    exit 2
}
