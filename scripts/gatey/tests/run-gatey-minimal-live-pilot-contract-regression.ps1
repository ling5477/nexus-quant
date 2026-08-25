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
    'NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_WORKER_RELEASE_DIGEST',
    'PILOT_DATABASE_WRITE_WINDOW_REQUIRES_RUNTIME_STOPPED',
    'PILOT_DATABASE_WRITE_WINDOW_OPEN', 'PILOT_DATABASE_WRITE_WINDOW_CLOSED',
    'PILOT_DATABASE_WRITE_WINDOW_GRANT_FAILED', 'PILOT_DATABASE_WRITE_WINDOW_REVOKE_FAILED',
    "'/usr/bin/timeout'", 'PGCONNECT_TIMEOUT=5', '--signal=TERM 45s',
    'GRANT UPDATE ON TABLE public.exchange_account_credentials TO nq_gatey_readonly',
    'GRANT INSERT ON TABLE public.credential_audit_logs TO nq_gatey_readonly',
    'GRANT INSERT, UPDATE ON TABLE public.instrument_catalog TO nq_gatey_readonly',
    'GRANT SELECT, INSERT, UPDATE ON TABLE public.operator_pilot_authorities TO nq_gatey_readonly',
    'GRANT INSERT, UPDATE ON TABLE public.live_sessions TO nq_gatey_readonly',
    'GRANT INSERT, UPDATE ON TABLE public.pilot_execution_leases TO nq_gatey_readonly',
    'GRANT INSERT, UPDATE ON TABLE public.execution_intents TO nq_gatey_readonly',
    'GRANT INSERT, UPDATE ON TABLE public.orders TO nq_gatey_readonly',
    'GRANT INSERT ON TABLE public.event_store TO nq_gatey_readonly',
    'GRANT UPDATE ON TABLE public.kill_switch_states TO nq_gatey_readonly',
    'GRANT INSERT ON TABLE public.kill_switch_events TO nq_gatey_readonly',
    'GRANT UPDATE(exchange_account_id) ON TABLE public.exchange_accounts TO nq_gatey_readonly',
    'GRANT UPDATE(id) ON TABLE public.users TO nq_gatey_readonly',
    'GRANT UPDATE(user_id) ON TABLE public.user_roles TO nq_gatey_readonly',
    'GRANT UPDATE(id) ON TABLE public.roles TO nq_gatey_readonly',
    'GRANT USAGE ON SEQUENCE public.credential_audit_logs_credential_audit_log_id_seq',
    'REVOKE UPDATE ON TABLE public.exchange_account_credentials FROM nq_gatey_readonly',
    'REVOKE SELECT, INSERT, UPDATE ON TABLE public.operator_pilot_authorities FROM nq_gatey_readonly',
    'REVOKE INSERT ON TABLE public.event_store FROM nq_gatey_readonly',
    'REVOKE UPDATE ON TABLE public.kill_switch_states FROM nq_gatey_readonly',
    'REVOKE UPDATE(id) ON TABLE public.users FROM nq_gatey_readonly',
    'REVOKE UPDATE(exchange_account_id) ON TABLE public.exchange_accounts FROM nq_gatey_readonly',
    'REVOKE UPDATE(user_id) ON TABLE public.user_roles FROM nq_gatey_readonly',
    'REVOKE UPDATE(id) ON TABLE public.roles FROM nq_gatey_readonly',
    'REVOKE USAGE ON SEQUENCE public.credential_audit_logs_credential_audit_log_id_seq',
    'PILOT_DATABASE_WRITE_BASELINE_NOT_EMPTY', 'PILOT_DATABASE_COLUMN_WRITE_BASELINE_NOT_EMPTY',
    'PILOT_DATABASE_COLUMN_REVOKE_DIVERGENCE', 'PILOT_DATABASE_SEQUENCE_BASELINE_NOT_EMPTY'
)) {
    if (-not $source.Contains($required)) { throw ('MISSING_CONTRACT:' + $required) }
}
foreach ($forbidden in @(
    'Invoke-WebRequest', 'Invoke-RestMethod', '/api/v5/trade/order',
    'apiKey', 'secretKey', 'passphrase', 'MARKET', 'transfer-enabled=true', 'withdraw-enabled=true',
    '$LimitPrice', '$Quantity', 'minimal-live-pilot.limit-price', 'minimal-live-pilot.quantity',
    'GRANT ALL', 'GRANT CREATE', 'GRANT DELETE', 'GRANT TRUNCATE', 'GRANT ON ALL',
    'ALTER DEFAULT PRIVILEGES',
    'NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_MINIMAL_LIVE_PILOT_STRATEGY_RELEASE_ID',
    'NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_MINIMAL_LIVE_PILOT_RISK_LIMIT_SET_ID',
    'GRANT INSERT ON TABLE public.risk_limit_sets',
    'GRANT INSERT ON TABLE public.operator_approvals'
)) {
    if ($source.IndexOf($forbidden, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
        throw ('FORBIDDEN_SURFACE:' + $forbidden)
    }
}

$grantMatch = [regex]::Match(
    $source,
    '(?s)\$script:PilotDatabaseGrantSql = @''\r?\n(?<sql>.*?)\r?\n''@'
)
$revokeMatch = [regex]::Match(
    $source,
    '(?s)\$script:PilotDatabaseRevokeSql = @''\r?\n(?<sql>.*?)\r?\n''@'
)
if (-not $grantMatch.Success -or -not $revokeMatch.Success) {
    throw 'PILOT_DATABASE_WINDOW_SQL_NOT_EXTRACTABLE'
}
$grantSql = $grantMatch.Groups['sql'].Value
$revokeSql = $revokeMatch.Groups['sql'].Value
$writeTables = @(
    'exchange_account_credentials', 'credential_audit_logs', 'instrument_catalog',
    'operator_pilot_authorities', 'live_sessions', 'live_session_events',
    'pilot_scope_bindings', 'pilot_prerequisite_observations',
    'pilot_instrument_observation_items', 'pilot_execution_leases',
    'pilot_execution_lease_intents', 'pilot_execution_lease_events', 'execution_intents',
    'execution_receipts', 'orders', 'trades', 'ledger_entries', 'ledger_events',
    'account_snapshots', 'positions', 'audit_logs', 'risk_events', 'event_store',
    'kill_switch_states', 'kill_switch_events'
)
foreach ($table in $writeTables) {
    if ($grantSql -cnotmatch ("(?m)^GRANT (INSERT|UPDATE|INSERT, UPDATE|SELECT, INSERT, UPDATE) ON TABLE public\." +
            [regex]::Escape($table) + ' TO nq_gatey_readonly;$')) {
        throw ('PILOT_DATABASE_TABLE_GRANT_MISSING:' + $table)
    }
    if ($revokeSql -cnotmatch ("(?m)^REVOKE (INSERT|UPDATE|INSERT, UPDATE|SELECT, INSERT, UPDATE) ON TABLE public\." +
            [regex]::Escape($table) + ' FROM nq_gatey_readonly;$')) {
        throw ('PILOT_DATABASE_TABLE_REVOKE_MISSING:' + $table)
    }
}
$writeSequences = @(
    'credential_audit_logs_credential_audit_log_id_seq',
    'instrument_catalog_instrument_id_seq', 'account_snapshots_snapshot_id_seq',
    'audit_logs_id_seq', 'ledger_events_ledger_event_id_seq', 'positions_id_seq'
)
foreach ($sequence in $writeSequences) {
    if (-not $grantSql.Contains('public.' + $sequence) -or
            -not $revokeSql.Contains('public.' + $sequence)) {
        throw ('PILOT_DATABASE_SEQUENCE_WINDOW_MISMATCH:' + $sequence)
    }
}
$roleLockColumns = @(
    @('exchange_accounts', 'exchange_account_id'),
    @('users', 'id'), @('user_roles', 'user_id'), @('roles', 'id')
)
foreach ($pair in $roleLockColumns) {
    $table = $pair[0]
    $column = $pair[1]
    if (-not $grantSql.Contains("GRANT UPDATE($column) ON TABLE public.$table TO nq_gatey_readonly;") -or
            -not $revokeSql.Contains("REVOKE UPDATE($column) ON TABLE public.$table FROM nq_gatey_readonly;")) {
        throw ('PILOT_DATABASE_ROLE_LOCK_COLUMN_WINDOW_MISMATCH:' + $table + '.' + $column)
    }
}
if (($grantSql | Select-String -Pattern '(?m)^GRANT .* ON TABLE ' -AllMatches).Matches.Count -ne 29 -or
        ($revokeSql | Select-String -Pattern '(?m)^REVOKE .* ON TABLE ' -AllMatches).Matches.Count -ne 29) {
    throw 'PILOT_DATABASE_TABLE_WINDOW_CARDINALITY_INVALID'
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
    cases = 90
    providerCalls = 0
    place = 0
    cancel = 0
    transfer = 0
    withdraw = 0
} | ConvertTo-Json
