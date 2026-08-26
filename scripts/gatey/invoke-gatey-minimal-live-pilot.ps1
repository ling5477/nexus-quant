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
$script:PilotDatabaseGrantSql = @'
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

DO $gatey$
DECLARE
    unexpected TEXT;
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname = 'nq_gatey_readonly'
          AND rolcanlogin
          AND NOT rolsuper
          AND NOT rolcreaterole
          AND NOT rolcreatedb
          AND NOT rolreplication
          AND NOT rolbypassrls
    ) THEN
        RAISE EXCEPTION 'PILOT_DATABASE_RUNTIME_ROLE_INVALID';
    END IF;
    IF has_schema_privilege('nq_gatey_readonly', 'public', 'CREATE') THEN
        RAISE EXCEPTION 'PILOT_DATABASE_RUNTIME_ROLE_SCHEMA_CREATE_FORBIDDEN';
    END IF;
    SELECT string_agg(table_name || ':' || privilege_type, ',' ORDER BY table_name, privilege_type)
    INTO unexpected
    FROM information_schema.role_table_grants
    WHERE grantee = 'nq_gatey_readonly'
      AND table_schema = 'public'
      AND privilege_type IN ('INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER');
    IF unexpected IS NOT NULL THEN
        RAISE EXCEPTION 'PILOT_DATABASE_WRITE_BASELINE_NOT_EMPTY:%', unexpected;
    END IF;
    SELECT string_agg(table_name || '.' || column_name, ',' ORDER BY table_name, column_name)
    INTO unexpected
    FROM information_schema.role_column_grants
    WHERE grantee = 'nq_gatey_readonly'
      AND table_schema = 'public'
      AND privilege_type = 'UPDATE';
    IF unexpected IS NOT NULL THEN
        RAISE EXCEPTION 'PILOT_DATABASE_COLUMN_WRITE_BASELINE_NOT_EMPTY:%', unexpected;
    END IF;
    SELECT string_agg(sequence_name, ',' ORDER BY sequence_name)
    INTO unexpected
    FROM information_schema.sequences
    WHERE sequence_schema = 'public'
      AND has_sequence_privilege(
          'nq_gatey_readonly', format('%I.%I', sequence_schema, sequence_name), 'USAGE,UPDATE');
    IF unexpected IS NOT NULL THEN
        RAISE EXCEPTION 'PILOT_DATABASE_SEQUENCE_BASELINE_NOT_EMPTY:%', unexpected;
    END IF;
END
$gatey$;

GRANT UPDATE ON TABLE public.exchange_account_credentials TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.accounts TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.credential_audit_logs TO nq_gatey_readonly;
GRANT INSERT, UPDATE ON TABLE public.instrument_catalog TO nq_gatey_readonly;
GRANT SELECT, INSERT, UPDATE ON TABLE public.operator_pilot_authorities TO nq_gatey_readonly;
GRANT INSERT, UPDATE ON TABLE public.live_sessions TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.live_session_events TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.pilot_scope_bindings TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.pilot_prerequisite_observations TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.pilot_instrument_observation_items TO nq_gatey_readonly;
GRANT INSERT, UPDATE ON TABLE public.pilot_execution_leases TO nq_gatey_readonly;
GRANT SELECT, INSERT ON TABLE public.pilot_pre_place_recovery_decisions TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.pilot_execution_lease_intents TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.pilot_execution_lease_events TO nq_gatey_readonly;
GRANT INSERT, UPDATE ON TABLE public.execution_intents TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.execution_receipts TO nq_gatey_readonly;
GRANT INSERT, UPDATE ON TABLE public.orders TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.trades TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.ledger_entries TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.ledger_events TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.account_snapshots TO nq_gatey_readonly;
GRANT INSERT, UPDATE ON TABLE public.positions TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.audit_logs TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.risk_events TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.event_store TO nq_gatey_readonly;
GRANT UPDATE ON TABLE public.kill_switch_states TO nq_gatey_readonly;
GRANT INSERT ON TABLE public.kill_switch_events TO nq_gatey_readonly;

-- PostgreSQL row-locking clauses require UPDATE privilege. Limit it to one immutable identity column per table.
GRANT UPDATE(exchange_account_id) ON TABLE public.exchange_accounts TO nq_gatey_readonly;
GRANT UPDATE(legacy_account_id, updated_at) ON TABLE public.exchange_accounts TO nq_gatey_readonly;
GRANT UPDATE(account_id) ON TABLE public.accounts TO nq_gatey_readonly;
GRANT UPDATE(pilot_scope_id) ON TABLE public.pilot_scope_bindings TO nq_gatey_readonly;
GRANT UPDATE(decision_id) ON TABLE public.pilot_pre_place_recovery_decisions TO nq_gatey_readonly;
GRANT UPDATE(id) ON TABLE public.users TO nq_gatey_readonly;
GRANT UPDATE(user_id) ON TABLE public.user_roles TO nq_gatey_readonly;
GRANT UPDATE(id) ON TABLE public.roles TO nq_gatey_readonly;

GRANT USAGE ON SEQUENCE public.credential_audit_logs_credential_audit_log_id_seq
    TO nq_gatey_readonly;
GRANT USAGE ON SEQUENCE public.accounts_account_id_seq TO nq_gatey_readonly;
GRANT USAGE ON SEQUENCE public.instrument_catalog_instrument_id_seq TO nq_gatey_readonly;
GRANT USAGE ON SEQUENCE public.account_snapshots_snapshot_id_seq TO nq_gatey_readonly;
GRANT USAGE ON SEQUENCE public.audit_logs_id_seq TO nq_gatey_readonly;
GRANT USAGE ON SEQUENCE public.ledger_events_ledger_event_id_seq TO nq_gatey_readonly;
GRANT USAGE ON SEQUENCE public.positions_id_seq TO nq_gatey_readonly;

DO $gatey$
DECLARE
    mismatch TEXT;
    sequence_name TEXT;
BEGIN
    WITH expected(table_name, privilege_type) AS (VALUES
        ('exchange_account_credentials', 'UPDATE'),
        ('accounts', 'INSERT'),
        ('credential_audit_logs', 'INSERT'),
        ('instrument_catalog', 'INSERT'), ('instrument_catalog', 'UPDATE'),
        ('operator_pilot_authorities', 'INSERT'), ('operator_pilot_authorities', 'UPDATE'),
        ('live_sessions', 'INSERT'), ('live_sessions', 'UPDATE'),
        ('live_session_events', 'INSERT'),
        ('pilot_scope_bindings', 'INSERT'),
        ('pilot_prerequisite_observations', 'INSERT'),
        ('pilot_instrument_observation_items', 'INSERT'),
        ('pilot_execution_leases', 'INSERT'), ('pilot_execution_leases', 'UPDATE'),
        ('pilot_pre_place_recovery_decisions', 'INSERT'),
        ('pilot_execution_lease_intents', 'INSERT'),
        ('pilot_execution_lease_events', 'INSERT'),
        ('execution_intents', 'INSERT'), ('execution_intents', 'UPDATE'),
        ('execution_receipts', 'INSERT'),
        ('orders', 'INSERT'), ('orders', 'UPDATE'),
        ('trades', 'INSERT'),
        ('ledger_entries', 'INSERT'),
        ('ledger_events', 'INSERT'),
        ('account_snapshots', 'INSERT'),
        ('positions', 'INSERT'), ('positions', 'UPDATE'),
        ('audit_logs', 'INSERT'),
        ('risk_events', 'INSERT'),
        ('event_store', 'INSERT'),
        ('kill_switch_states', 'UPDATE'),
        ('kill_switch_events', 'INSERT')
    ), actual AS (
        SELECT table_name, privilege_type
        FROM information_schema.role_table_grants
        WHERE grantee = 'nq_gatey_readonly'
          AND table_schema = 'public'
          AND privilege_type IN ('INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER')
    ), difference AS (
        (SELECT * FROM expected EXCEPT SELECT * FROM actual)
        UNION ALL
        (SELECT * FROM actual EXCEPT SELECT * FROM expected)
    )
    SELECT string_agg(table_name || ':' || privilege_type, ',' ORDER BY table_name, privilege_type)
    INTO mismatch
    FROM difference;
    IF mismatch IS NOT NULL THEN
        RAISE EXCEPTION 'PILOT_DATABASE_TABLE_GRANT_DIVERGENCE:%', mismatch;
    END IF;
    IF NOT has_column_privilege(
                'nq_gatey_readonly', 'public.exchange_accounts', 'exchange_account_id', 'UPDATE')
            OR NOT has_column_privilege(
                'nq_gatey_readonly', 'public.exchange_accounts', 'legacy_account_id', 'UPDATE')
            OR NOT has_column_privilege(
                'nq_gatey_readonly', 'public.exchange_accounts', 'updated_at', 'UPDATE')
            OR NOT has_column_privilege(
                'nq_gatey_readonly', 'public.accounts', 'account_id', 'UPDATE')
            OR NOT has_column_privilege(
                'nq_gatey_readonly', 'public.pilot_scope_bindings', 'pilot_scope_id', 'UPDATE')
            OR NOT has_column_privilege(
                'nq_gatey_readonly', 'public.pilot_pre_place_recovery_decisions',
                'decision_id', 'UPDATE')
            OR NOT has_column_privilege('nq_gatey_readonly', 'public.users', 'id', 'UPDATE')
            OR NOT has_column_privilege(
                'nq_gatey_readonly', 'public.user_roles', 'user_id', 'UPDATE')
            OR NOT has_column_privilege('nq_gatey_readonly', 'public.roles', 'id', 'UPDATE') THEN
        RAISE EXCEPTION 'PILOT_DATABASE_ROLE_LOCK_COLUMN_GRANT_DIVERGENCE';
    END IF;
    FOREACH sequence_name IN ARRAY ARRAY[
        'credential_audit_logs_credential_audit_log_id_seq',
        'instrument_catalog_instrument_id_seq',
        'account_snapshots_snapshot_id_seq',
        'audit_logs_id_seq',
        'ledger_events_ledger_event_id_seq',
        'positions_id_seq',
        'accounts_account_id_seq'
    ] LOOP
        IF NOT has_sequence_privilege('nq_gatey_readonly', 'public.' || sequence_name, 'USAGE')
                OR has_sequence_privilege('nq_gatey_readonly', 'public.' || sequence_name, 'UPDATE') THEN
            RAISE EXCEPTION 'PILOT_DATABASE_SEQUENCE_GRANT_DIVERGENCE:%', sequence_name;
        END IF;
    END LOOP;
END
$gatey$;

COMMIT;
SELECT 'PILOT_DATABASE_WRITE_WINDOW_OPEN';
'@
$script:PilotDatabaseRevokeSql = @'
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

REVOKE UPDATE ON TABLE public.exchange_account_credentials FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.accounts FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.credential_audit_logs FROM nq_gatey_readonly;
REVOKE INSERT, UPDATE ON TABLE public.instrument_catalog FROM nq_gatey_readonly;
REVOKE SELECT, INSERT, UPDATE ON TABLE public.operator_pilot_authorities FROM nq_gatey_readonly;
REVOKE INSERT, UPDATE ON TABLE public.live_sessions FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.live_session_events FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.pilot_scope_bindings FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.pilot_prerequisite_observations FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.pilot_instrument_observation_items FROM nq_gatey_readonly;
REVOKE INSERT, UPDATE ON TABLE public.pilot_execution_leases FROM nq_gatey_readonly;
REVOKE SELECT, INSERT ON TABLE public.pilot_pre_place_recovery_decisions FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.pilot_execution_lease_intents FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.pilot_execution_lease_events FROM nq_gatey_readonly;
REVOKE INSERT, UPDATE ON TABLE public.execution_intents FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.execution_receipts FROM nq_gatey_readonly;
REVOKE INSERT, UPDATE ON TABLE public.orders FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.trades FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.ledger_entries FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.ledger_events FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.account_snapshots FROM nq_gatey_readonly;
REVOKE INSERT, UPDATE ON TABLE public.positions FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.audit_logs FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.risk_events FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.event_store FROM nq_gatey_readonly;
REVOKE UPDATE ON TABLE public.kill_switch_states FROM nq_gatey_readonly;
REVOKE INSERT ON TABLE public.kill_switch_events FROM nq_gatey_readonly;

REVOKE UPDATE(id) ON TABLE public.users FROM nq_gatey_readonly;
REVOKE UPDATE(user_id) ON TABLE public.user_roles FROM nq_gatey_readonly;
REVOKE UPDATE(id) ON TABLE public.roles FROM nq_gatey_readonly;
REVOKE UPDATE(pilot_scope_id) ON TABLE public.pilot_scope_bindings FROM nq_gatey_readonly;
REVOKE UPDATE(decision_id) ON TABLE public.pilot_pre_place_recovery_decisions FROM nq_gatey_readonly;
REVOKE UPDATE(exchange_account_id) ON TABLE public.exchange_accounts FROM nq_gatey_readonly;
REVOKE UPDATE(legacy_account_id, updated_at) ON TABLE public.exchange_accounts FROM nq_gatey_readonly;
REVOKE UPDATE(account_id) ON TABLE public.accounts FROM nq_gatey_readonly;

REVOKE USAGE ON SEQUENCE public.credential_audit_logs_credential_audit_log_id_seq
    FROM nq_gatey_readonly;
REVOKE USAGE ON SEQUENCE public.instrument_catalog_instrument_id_seq FROM nq_gatey_readonly;
REVOKE USAGE ON SEQUENCE public.account_snapshots_snapshot_id_seq FROM nq_gatey_readonly;
REVOKE USAGE ON SEQUENCE public.audit_logs_id_seq FROM nq_gatey_readonly;
REVOKE USAGE ON SEQUENCE public.ledger_events_ledger_event_id_seq FROM nq_gatey_readonly;
REVOKE USAGE ON SEQUENCE public.positions_id_seq FROM nq_gatey_readonly;
REVOKE USAGE ON SEQUENCE public.accounts_account_id_seq FROM nq_gatey_readonly;

DO $gatey$
DECLARE
    unexpected TEXT;
BEGIN
    SELECT string_agg(table_name || ':' || privilege_type, ',' ORDER BY table_name, privilege_type)
    INTO unexpected
    FROM information_schema.role_table_grants
    WHERE grantee = 'nq_gatey_readonly'
      AND table_schema = 'public'
      AND privilege_type IN ('INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER');
    IF unexpected IS NOT NULL THEN
        RAISE EXCEPTION 'PILOT_DATABASE_TABLE_REVOKE_DIVERGENCE:%', unexpected;
    END IF;
    SELECT string_agg(table_name || '.' || column_name, ',' ORDER BY table_name, column_name)
    INTO unexpected
    FROM information_schema.role_column_grants
    WHERE grantee = 'nq_gatey_readonly'
      AND table_schema = 'public'
      AND privilege_type = 'UPDATE';
    IF unexpected IS NOT NULL THEN
        RAISE EXCEPTION 'PILOT_DATABASE_COLUMN_REVOKE_DIVERGENCE:%', unexpected;
    END IF;
    SELECT string_agg(sequence_name, ',' ORDER BY sequence_name)
    INTO unexpected
    FROM information_schema.sequences
    WHERE sequence_schema = 'public'
      AND has_sequence_privilege(
          'nq_gatey_readonly', format('%I.%I', sequence_schema, sequence_name), 'USAGE,UPDATE');
    IF unexpected IS NOT NULL THEN
        RAISE EXCEPTION 'PILOT_DATABASE_SEQUENCE_REVOKE_DIVERGENCE:%', unexpected;
    END IF;
    IF has_schema_privilege('nq_gatey_readonly', 'public', 'CREATE') THEN
        RAISE EXCEPTION 'PILOT_DATABASE_RUNTIME_ROLE_SCHEMA_CREATE_FORBIDDEN';
    END IF;
END
$gatey$;

COMMIT;
SELECT 'PILOT_DATABASE_WRITE_WINDOW_CLOSED';
'@

function Throw-Blocked([string]$Code) { throw ('BLOCKED / ' + $Code) }

function Invoke-PilotDatabaseWindowSql(
    [string]$Sql,
    [string]$ExpectedMarker,
    [string]$FailureCode
) {
    $dockerPath = '/usr/bin/docker'
    $timeoutPath = '/usr/bin/timeout'
    if (-not (Test-Path -LiteralPath $dockerPath -PathType Leaf) -or
            -not (Test-Path -LiteralPath $timeoutPath -PathType Leaf)) {
        Throw-Blocked $FailureCode
    }
    $command = 'PGCONNECT_TIMEOUT=5 PGPASSWORD="$(cat /run/secrets/postgres_password)" ' +
        'exec psql -X -h 127.0.0.1 -p 55432 -U nqgatew -d nexus_quant -At '
    $output = @($Sql | & $timeoutPath --signal=TERM 45s `
            $dockerPath exec -i nq-gatew-postgres /bin/sh -c $command 2>&1)
    if ($LASTEXITCODE -ne 0 -or -not ($output -contains $ExpectedMarker)) {
        Throw-Blocked $FailureCode
    }
}

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
    $active = @(& /usr/bin/systemctl is-active nq-gatey-readonly-qualification.service 2>$null)
    if ($LASTEXITCODE -eq 0 -or ($active -join '').Trim() -ceq 'active') {
        Throw-Blocked 'PILOT_DATABASE_WRITE_WINDOW_REQUIRES_RUNTIME_STOPPED'
    }
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
    PATH|SPRING_PROFILES_ACTIVE|NQ_APP_BIND_ADDRESS|NQ_APP_PORT|NQ_GATEY_MANAGEMENT_ADDRESS|NQ_GATEY_MANAGEMENT_PORT|NQ_GATEY_RELEASE_ID|NQ_GATEY_SOURCE_COMMIT|NQ_GATEY_RELEASE_MANIFEST_SHA256|NQ_GATEY_QUALIFICATION_DB_URL|NQ_GATEY_QUALIFICATION_DB_USER|NQ_GATEY_DATABASE_TARGET_ID|NQ_GATEY_DATABASE_CREDENTIAL_REFERENCE|NQ_LIVE_ENABLED|NQ_TRADING_COMPONENTS_ENABLED|NQ_RUNTIME_PROVIDER_OBSERVATION_ENABLED|NQ_RUNTIME_PROVIDER_OBSERVATION_ORDER_SUBMISSION_ENABLED|NQ_RUNTIME_PROVIDER_OBSERVATION_CANCEL_ENABLED|NQ_RUNTIME_PROVIDER_OBSERVATION_TRANSFER_ENABLED|NQ_RUNTIME_PROVIDER_OBSERVATION_WITHDRAW_ENABLED|NQ_GATEY_EXPECTED_KILL_SWITCH|NQ_GATEY_OKX_EXPECTED_IP|NQ_GATEW_OKX_EXPECTED_IP|NQ_AUTH_BOOTSTRAP_ADMIN_ENABLED|NQ_SECURITY_ISSUER|NQ_SECURITY_ACCESS_TOKEN_TTL|NQ_ACCOUNT_CREDENTIALS_KEY_VERSION|NQ_ACCOUNT_CREDENTIALS_VERIFICATION_MODE|NQ_GATEY_QUALIFICATION_DB_PASSWORD|NQ_SECURITY_SECRET|NQ_ACCOUNT_CREDENTIALS_MASTER_KEY|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_INSTRUMENT_METADATA_DIGEST|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_INSTRUMENT_SOURCE_IDENTITY|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_INSTRUMENT_SOURCE_SCHEMA_VERSION|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_INSTRUMENT_MAXIMUM_AGE_MS|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_FEE_SCHEDULE_DIGEST|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_FEE_TIER|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_FEE_EVIDENCE_CLASS|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_FEE_SOURCE_IDENTITY|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_FEE_SOURCE_SCHEMA_VERSION|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_FEE_MAXIMUM_AGE_MS|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_BALANCE_SOURCE_IDENTITY|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_BALANCE_SOURCE_SCHEMA_VERSION|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_BALANCE_MAXIMUM_AGE_MS|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_CLOCK_SOURCE_IDENTITY|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_CLOCK_SOURCE_SCHEMA_VERSION|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_CLOCK_MAXIMUM_AGE_MS|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_SIGNED_TIMESTAMP_SOURCE|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_MAXIMUM_TOLERATED_SKEW_MS|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_ENDPOINT_POLICY_VERSION|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_ENDPOINT_POLICY_DIGEST|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_PROVIDER_CONTRACT_IDENTITY|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_PROVIDER_ARTIFACT_DIGEST|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_WORKER_IDENTITY|NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_WORKER_RELEASE_DIGEST) ;;
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
    $writeWindowAttempted = $true
    try {
        Invoke-PilotDatabaseWindowSql $script:PilotDatabaseGrantSql `
            'PILOT_DATABASE_WRITE_WINDOW_OPEN' 'PILOT_DATABASE_WRITE_WINDOW_GRANT_FAILED'
        & /usr/bin/bash -c $bash minimal-live-pilot $jar $ExchangeAccountId $CredentialReferenceId `
            $Instrument $Side ([string]$ConfiguredPilotMaxNotional) `
            ([string]$verified.manifestSha256) $serverIdentity ([string]$verified.releaseId)
        if ($LASTEXITCODE -ne 0) { Throw-Blocked 'MINIMAL_LIVE_PILOT_INVOCATION_FAILED' }
    }
    finally {
        if ($writeWindowAttempted) {
            Invoke-PilotDatabaseWindowSql $script:PilotDatabaseRevokeSql `
                'PILOT_DATABASE_WRITE_WINDOW_CLOSED' 'PILOT_DATABASE_WRITE_WINDOW_REVOKE_FAILED'
        }
    }
}
catch {
    $decision = if ($_.Exception.Message -match '^BLOCKED / [A-Z0-9_]+$') {
        $_.Exception.Message
    } else { 'FAIL / MINIMAL_LIVE_PILOT_INTERNAL_ERROR' }
    [pscustomobject]@{ decision = $decision } | ConvertTo-Json
    exit 2
}
