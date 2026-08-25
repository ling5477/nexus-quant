[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$gateyRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$scriptPath = Join-Path $gateyRoot 'invoke-gatey-minimal-live-pilot.ps1'
$source = Get-Content -LiteralPath $scriptPath -Raw

foreach ($required in @(
    '[long]$ExchangeAccountId', '[long]$CredentialReferenceId',
    "[ValidateSet('BTC-USDT')]", "[ValidateSet('BUY')]",
    '[decimal]$ConfiguredPilotMaxNotional', '$ConfiguredPilotMaxNotional -gt 10',
    'Test-GateYReadonlyRelease $ReleaseRoot -RequirePosix',
    "'machine-id-sha256:'", '--nq.live-control.exact-pilot-binding.manifest-sha256',
    '--nq.live-control.exact-pilot-binding.server-identity',
    '--nq.runtime.provider-observation.release-id',
    '--nq.runtime.provider-observation.source-commit',
    '--nq.okx.private-readonly-diagnostics.permission-probe.enabled=true',
    '--nq.okx.private-readonly-diagnostics.permission-probe.expected-ip="$expected_ip"',
    'NQ_GATEY_OKX_EXPECTED_IP', 'NQ_GATEW_OKX_EXPECTED_IP',
    '--nq.runtime.minimal-live-pilot.enabled=true',
    '--nq.runtime.minimal-live-pilot.order-submission-enabled=true',
    '--nq.runtime.minimal-live-pilot.cancel-enabled=true',
    '--nq.runtime.minimal-live-pilot.transfer-enabled=false',
    '--nq.runtime.minimal-live-pilot.withdraw-enabled=false',
    '--nq.runtime.provider-observation.transfer-enabled=false',
    '--nq.runtime.provider-observation.withdraw-enabled=false',
    "while IFS='=' read -r name ignored", '*) unset "$name" ;;',
    'NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_MINIMAL_LIVE_PILOT_STRATEGY_RELEASE_ID',
    'NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_MINIMAL_LIVE_PILOT_RISK_LIMIT_SET_ID',
    'NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_WORKER_RELEASE_DIGEST'
)) {
    if (-not $source.Contains($required)) { throw ('MISSING_CONTRACT:' + $required) }
}
foreach ($forbidden in @(
    'Invoke-WebRequest', 'Invoke-RestMethod', '/api/v5/trade/order',
    'apiKey', 'secretKey', 'passphrase', 'MARKET', 'transfer-enabled=true', 'withdraw-enabled=true',
    '$LimitPrice', '$Quantity', 'minimal-live-pilot.limit-price', 'minimal-live-pilot.quantity'
)) {
    if ($source.IndexOf($forbidden, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
        throw ('FORBIDDEN_SURFACE:' + $forbidden)
    }
}

$engine = (Get-Process -Id $PID).Path
$output = @(& $engine -NoProfile -File $scriptPath `
    -ExchangeAccountId 1 -CredentialReferenceId 2 -Instrument BTC-USDT -Side BUY `
    -ConfiguredPilotMaxNotional 10 2>&1)
if ($LASTEXITCODE -ne 2) { throw 'NON_LINUX_INVOCATION_DID_NOT_BLOCK' }
$result = ($output -join [Environment]::NewLine) | ConvertFrom-Json
if ([string]$result.decision -cne 'BLOCKED / ROOT_LINUX_REQUIRED') {
    throw 'NON_LINUX_BLOCK_DECISION_INVALID'
}

[pscustomobject][ordered]@{
    decision = 'PASS / GATEY_MINIMAL_LIVE_PILOT_CONTRACT_REGRESSION'
    cases = 25
    providerCalls = 0
    place = 0
    cancel = 0
    transfer = 0
    withdraw = 0
} | ConvertTo-Json
