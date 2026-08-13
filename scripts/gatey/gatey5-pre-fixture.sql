\set ON_ERROR_STOP on
SET TIME ZONE 'UTC';
SET client_min_messages = warning;

-- Why: all IDs and timestamps are pure functions of the frozen seed and ordinal.
-- This smoke/full fixture never reads production data or credential material.
-- The V12 migration creates synthetic bootstrap identities. The disposable capacity
-- clone replaces those rows so manifest counts remain exact; CASCADE only reaches
-- other empty/disposable fixture tables in this randomly created database.
TRUNCATE TABLE
    orders, strategy_release_admission_state, backtest_publish_records,
    backtest_runs, backtest_configs, research_configs, strategy_versions, strategy_definitions,
    exchange_account_credentials, exchange_accounts, strategy_runs, user_roles, roles, users, accounts
RESTART IDENTITY CASCADE;

INSERT INTO users (id, username, password_hash, enabled, created_at, updated_at)
SELECT n, 'gatey5-user-' || n, 'synthetic-disabled-hash', false,
       TIMESTAMPTZ '2025-01-01 00:00:00+00' + (n % 86400) * INTERVAL '1 second',
       TIMESTAMPTZ '2025-01-01 00:00:00+00' + (n % 86400) * INTERVAL '1 second'
FROM generate_series(1, :users) n
ON CONFLICT (id) DO NOTHING;

INSERT INTO roles (id, role_code, description, created_at)
SELECT n, CASE WHEN n = 1 THEN 'LIVE_APPROVER' ELSE 'GATEY5_ROLE_' || n END,
       'synthetic role', TIMESTAMPTZ '2025-01-01 00:00:00+00'
FROM generate_series(1, :roles) n
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id, granted_at)
SELECT ((n - 1) % :users) + 1, ((n - 1) / :users) + 1,
       TIMESTAMPTZ '2025-01-01 00:00:00+00' + (n % 86400) * INTERVAL '1 second'
FROM generate_series(1, :user_roles) n
ON CONFLICT DO NOTHING;

INSERT INTO accounts (account_id, account_code, venue, status, created_at)
SELECT n, 'gatey5-account-' || n, 'OKX', 'ACTIVE',
       TIMESTAMPTZ '2025-01-01 00:00:00+00' + (n % 86400) * INTERVAL '1 second'
FROM generate_series(1, :accounts) n
ON CONFLICT (account_id) DO NOTHING;

INSERT INTO strategy_runs (
    strategy_run_id, strategy_id, account_id, status, started_at, finished_at, trace_id,
    created_at, trigger_type, exchange_code, trade_env, config_snapshot, request_id)
SELECT 'gy5-run-' || n, 'gy5-strategy-' || (((n - 1) % :strategy_definitions) + 1),
       ((n - 1) % :accounts) + 1, 'SUCCEEDED',
       TIMESTAMPTZ '2025-01-01 00:00:00+00' + (n % 31536000) * INTERVAL '1 second',
       TIMESTAMPTZ '2025-01-01 00:01:00+00' + (n % 31536000) * INTERVAL '1 second',
       'gy5-trace-' || n, TIMESTAMPTZ '2025-01-01 00:00:00+00', 'MANUAL', 'OKX', 'SIM', '{}',
       'gy5-request-' || n
FROM generate_series(1, :strategy_runs) n;

INSERT INTO exchange_accounts (
    exchange_account_id, owner_user_id, exchange_code, trade_env, account_alias,
    external_account_ref, legacy_account_id, is_default, status, created_at, updated_at)
SELECT n, ((n - 1) % :users) + 1, 'OKX', 'SIM', 'gatey5-alias-' || n,
       NULL, n, false, 'ACTIVE', TIMESTAMPTZ '2025-01-01 00:00:00+00', TIMESTAMPTZ '2025-01-01 00:00:00+00'
FROM generate_series(1, :exchange_accounts) n
ON CONFLICT (exchange_account_id) DO NOTHING;

INSERT INTO exchange_account_credentials (
    credential_id, exchange_account_id, credential_type, encrypted_payload, key_version,
    cipher_suite, masked_access_key, verification_status, is_active, revoked_at,
    rotated_from_credential_id, created_at, updated_at)
SELECT n, ((n - 1) % :exchange_accounts) + 1,
       CASE (n - 1) % 3 WHEN 0 THEN 'OKX_API_V5' WHEN 1 THEN 'BINANCE_HMAC' ELSE 'BINANCE_ED25519' END,
       decode(encode(digest('gatey-production-like-scale-v1:credential:' || n, 'sha256'), 'hex'), 'hex'),
       1, 'PGP_SYM_AES256', 'synthetic-' || n, 'REVOKED', false,
       TIMESTAMPTZ '2025-01-02 00:00:00+00', NULL,
       TIMESTAMPTZ '2025-01-01 00:00:00+00', TIMESTAMPTZ '2025-01-02 00:00:00+00'
FROM generate_series(1, :exchange_account_credentials) n
ON CONFLICT (credential_id) DO NOTHING;

INSERT INTO strategy_definitions (
    strategy_id, strategy_code, strategy_name, strategy_type, exchange_code, account_id,
    trade_env, enabled, config_snapshot, version, created_at, updated_at)
SELECT 'gy5-strategy-' || n, 'GY5_' || n, 'GateY5 strategy ' || n, 'FIXTURE', 'OKX',
       ((n - 1) % :accounts) + 1, 'SIM', false, '{}', 1,
       TIMESTAMPTZ '2025-01-01 00:00:00+00', TIMESTAMPTZ '2025-01-01 00:00:00+00'
FROM generate_series(1, :strategy_definitions) n;

INSERT INTO strategy_versions (
    strategy_version_id, strategy_code, version, version_name, status, param_snapshot_json,
    config_snapshot_json, source_snapshot_json, checksum, created_by, created_at, updated_at)
SELECT 'gy5-version-' || n, 'GY5_' || (((n - 1) % :strategy_definitions) + 1),
       ((n - 1) / :strategy_definitions) + 1, 'v' || n, 'ACTIVE', '{}', '{}', '{}',
       encode(digest('gatey-production-like-scale-v1:strategy-version:' || n, 'sha256'), 'hex'),
       'gatey5', TIMESTAMPTZ '2025-01-01 00:00:00+00', TIMESTAMPTZ '2025-01-01 00:00:00+00'
FROM generate_series(1, :strategy_versions) n;

INSERT INTO research_configs (
    research_config_id, source_strategy_id, name, description, strategy_snapshot,
    config_json, created_at, updated_at)
SELECT 'gy5-research-' || n, 'gy5-strategy-' || (((n - 1) % :strategy_definitions) + 1),
       'research ' || n, 'synthetic', '{}', '{}',
       TIMESTAMPTZ '2025-01-01 00:00:00+00', TIMESTAMPTZ '2025-01-01 00:00:00+00'
FROM generate_series(1, :research_configs) n;

INSERT INTO backtest_configs (
    backtest_config_id, research_config_id, name, description, config_json,
    evaluation_spec_json, created_at, updated_at)
SELECT 'gy5-btcfg-' || n, 'gy5-research-' || (((n - 1) % :research_configs) + 1),
       'config ' || n, 'synthetic', '{}', '{}',
       TIMESTAMPTZ '2025-01-01 00:00:00+00', TIMESTAMPTZ '2025-01-01 00:00:00+00'
FROM generate_series(1, :backtest_configs) n;

INSERT INTO backtest_runs (
    backtest_run_id, backtest_config_id, research_config_id, source_strategy_id, status,
    strategy_snapshot, backtest_config_snapshot, summary_json, requested_at, started_at,
    finished_at, created_at, updated_at, strategy_version_id, strategy_version_snapshot_json,
    dataset_snapshot_json)
SELECT 'gy5-btrun-' || n, 'gy5-btcfg-' || (((n - 1) % :backtest_configs) + 1),
       'gy5-research-' || (((n - 1) % :research_configs) + 1),
       'gy5-strategy-' || (((n - 1) % :strategy_definitions) + 1), 'SUCCEEDED', '{}', '{}', '{}',
       TIMESTAMPTZ '2025-01-01 00:00:00+00' + (n % 31536000) * INTERVAL '1 second',
       TIMESTAMPTZ '2025-01-01 00:00:01+00' + (n % 31536000) * INTERVAL '1 second',
       TIMESTAMPTZ '2025-01-01 00:00:02+00' + (n % 31536000) * INTERVAL '1 second',
       TIMESTAMPTZ '2025-01-01 00:00:00+00', TIMESTAMPTZ '2025-01-01 00:00:02+00',
       'gy5-version-' || (((n - 1) % :strategy_versions) + 1), '{}', '{}'
FROM generate_series(1, :backtest_runs) n;

INSERT INTO backtest_publish_records (
    publish_record_id, backtest_run_id, research_config_id, backtest_config_id,
    source_strategy_id, publish_status, publish_name, publish_snapshot_json,
    evaluation_summary_json, published_at, created_at, updated_at, strategy_version_id,
    version_snapshot_json)
SELECT 'gy5-publish-' || n, 'gy5-btrun-' || n,
       'gy5-research-' || (((n - 1) % :research_configs) + 1),
       'gy5-btcfg-' || (((n - 1) % :backtest_configs) + 1),
       'gy5-strategy-' || (((n - 1) % :strategy_definitions) + 1), 'SUCCEEDED',
       'publish ' || n, '{}', '{}', TIMESTAMPTZ '2025-01-02 00:00:00+00',
       TIMESTAMPTZ '2025-01-01 00:00:00+00', TIMESTAMPTZ '2025-01-02 00:00:00+00',
       'gy5-version-' || (((n - 1) % :strategy_versions) + 1), '{}'
FROM generate_series(1, :backtest_publish_records) n;

INSERT INTO orders (
    order_id, account_id, strategy_run_id, symbol, client_order_id, side, type, price, qty,
    status, reason, trace_id, created_at, updated_at, request_id, dedup_key, exchange_code,
    trade_env, exchange_order_id, venue, external_order_id)
SELECT 'gy5-order-' || n, ((n - 1) % :accounts) + 1,
       'gy5-run-' || (((n - 1) % :strategy_runs) + 1), 'BTC-USDT', 'gy5-client-' || n,
       CASE n % 2 WHEN 0 THEN 'BUY' ELSE 'SELL' END, 'LIMIT', 100, 1, 'CREATED',
       'synthetic', 'gy5-trace-' || n,
       TIMESTAMPTZ '2025-01-01 00:00:00+00' + (n % 31536000) * INTERVAL '1 second',
       TIMESTAMPTZ '2025-01-01 00:00:00+00' + (n % 31536000) * INTERVAL '1 second',
       'gy5-request-' || n, 'gy5-dedup-' || n, 'OKX', 'SIM', NULL, 'OKX', NULL
FROM generate_series(1, :orders) n;

SELECT setval(pg_get_serial_sequence('users','id'), GREATEST(:users, 1), true);
SELECT setval(pg_get_serial_sequence('roles','id'), GREATEST(:roles, 1), true);
SELECT setval(pg_get_serial_sequence('accounts','account_id'), GREATEST(:accounts, 1), true);
SELECT setval(pg_get_serial_sequence('exchange_accounts','exchange_account_id'), GREATEST(:exchange_accounts, 1), true);
SELECT setval(pg_get_serial_sequence('exchange_account_credentials','credential_id'), GREATEST(:exchange_account_credentials, 1), true);
