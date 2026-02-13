-- V1__init.sql
-- 目的：提供 Gate A 最小可运行表结构，覆盖幂等、回放、审计与恢复所需核心实体。

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_roles_role_code UNIQUE (role_code)
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE IF NOT EXISTS accounts (
    account_id BIGSERIAL PRIMARY KEY,
    account_code VARCHAR(64) NOT NULL,
    venue VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_accounts_account_code UNIQUE (account_code)
);

CREATE TABLE IF NOT EXISTS strategy_runs (
    run_id VARCHAR(64) PRIMARY KEY,
    strategy_id VARCHAR(128) NOT NULL,
    account_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    trace_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_strategy_runs_account FOREIGN KEY (account_id) REFERENCES accounts (account_id)
);

CREATE INDEX IF NOT EXISTS idx_strategy_runs_strategy_started
    ON strategy_runs (strategy_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_strategy_runs_account_started
    ON strategy_runs (account_id, started_at DESC);

CREATE TABLE IF NOT EXISTS orders (
    order_id VARCHAR(64) PRIMARY KEY,
    account_id BIGINT NOT NULL,
    strategy_run_id VARCHAR(64),
    symbol VARCHAR(64) NOT NULL,
    client_order_id VARCHAR(128) NOT NULL,
    side VARCHAR(16) NOT NULL,
    type VARCHAR(16) NOT NULL,
    price NUMERIC(38, 8),
    qty NUMERIC(38, 8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(255),
    trace_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_orders_account_client_order UNIQUE (account_id, client_order_id),
    CONSTRAINT fk_orders_account FOREIGN KEY (account_id) REFERENCES accounts (account_id),
    CONSTRAINT fk_orders_strategy_run FOREIGN KEY (strategy_run_id) REFERENCES strategy_runs (run_id)
);

CREATE INDEX IF NOT EXISTS idx_orders_account_created
    ON orders (account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_symbol_created
    ON orders (symbol, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_status_created
    ON orders (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_trace_id
    ON orders (trace_id);

CREATE TABLE IF NOT EXISTS trades (
    trade_id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    account_id BIGINT NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    exchange VARCHAR(32),
    exchange_trade_id VARCHAR(128),
    price NUMERIC(38, 8) NOT NULL,
    qty NUMERIC(38, 8) NOT NULL,
    fee NUMERIC(38, 8) DEFAULT 0,
    fee_currency VARCHAR(32),
    trace_id VARCHAR(64) NOT NULL,
    ts TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_trades_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_trades_account FOREIGN KEY (account_id) REFERENCES accounts (account_id),
    CONSTRAINT uq_trades_exchange_trade UNIQUE (exchange, exchange_trade_id)
);

CREATE INDEX IF NOT EXISTS idx_trades_order_ts
    ON trades (order_id, ts DESC);
CREATE INDEX IF NOT EXISTS idx_trades_symbol_ts
    ON trades (symbol, ts DESC);
CREATE INDEX IF NOT EXISTS idx_trades_trace_id
    ON trades (trace_id);

CREATE TABLE IF NOT EXISTS positions (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    qty NUMERIC(38, 8) NOT NULL DEFAULT 0,
    available_qty NUMERIC(38, 8) NOT NULL DEFAULT 0,
    frozen_qty NUMERIC(38, 8) NOT NULL DEFAULT 0,
    avg_price NUMERIC(38, 8) NOT NULL DEFAULT 0,
    trace_id VARCHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_positions_account_symbol UNIQUE (account_id, symbol),
    CONSTRAINT fk_positions_account FOREIGN KEY (account_id) REFERENCES accounts (account_id)
);

CREATE INDEX IF NOT EXISTS idx_positions_account_updated
    ON positions (account_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS account_snapshots (
    snapshot_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    currency VARCHAR(32) NOT NULL,
    balance NUMERIC(38, 8) NOT NULL,
    available NUMERIC(38, 8) NOT NULL,
    frozen NUMERIC(38, 8) NOT NULL,
    ts TIMESTAMPTZ NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_account_snapshots_account FOREIGN KEY (account_id) REFERENCES accounts (account_id)
);

CREATE INDEX IF NOT EXISTS idx_account_snapshots_account_ts
    ON account_snapshots (account_id, ts DESC);

CREATE TABLE IF NOT EXISTS ledger_entries (
    entry_id VARCHAR(64) PRIMARY KEY,
    account_id BIGINT NOT NULL,
    currency VARCHAR(32) NOT NULL,
    delta NUMERIC(38, 8) NOT NULL,
    balance_after NUMERIC(38, 8),
    direction VARCHAR(16) NOT NULL,
    ref_type VARCHAR(32) NOT NULL,
    ref_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128),
    trace_id VARCHAR(64) NOT NULL,
    ts TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ledger_entries_account FOREIGN KEY (account_id) REFERENCES accounts (account_id)
);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_account_ts
    ON ledger_entries (account_id, ts DESC);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_ref
    ON ledger_entries (ref_type, ref_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ledger_entries_idempotency_key
    ON ledger_entries (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- 与用户要求对齐：保留 ledger_events 表作为账本事件投影入口（可与 ledger_entries 联动演进）。
CREATE TABLE IF NOT EXISTS ledger_events (
    ledger_event_id BIGSERIAL PRIMARY KEY,
    entry_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_json JSONB NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ledger_events_entry FOREIGN KEY (entry_id) REFERENCES ledger_entries (entry_id)
);

CREATE INDEX IF NOT EXISTS idx_ledger_events_trace_id
    ON ledger_events (trace_id);

CREATE TABLE IF NOT EXISTS risk_events (
    risk_event_id VARCHAR(64) PRIMARY KEY,
    rule_id VARCHAR(128),
    scope VARCHAR(32) NOT NULL,
    scope_id VARCHAR(128) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reason VARCHAR(255),
    severity VARCHAR(16),
    trace_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_risk_events_scope_created
    ON risk_events (scope, scope_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_risk_events_trace_id
    ON risk_events (trace_id);

CREATE TABLE IF NOT EXISTS event_store (
    event_id VARCHAR(64) PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    schema_version INTEGER NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_json JSONB NOT NULL,
    key_value VARCHAR(128) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_event_store_topic_created
    ON event_store (topic, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_event_store_trace_id
    ON event_store (trace_id);
CREATE INDEX IF NOT EXISTS idx_event_store_type_created
    ON event_store (event_type, created_at DESC);

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    domain VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    actor_id VARCHAR(64),
    trace_id VARCHAR(64) NOT NULL,
    detail_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_domain_created
    ON audit_logs (domain, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_created
    ON audit_logs (actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_trace_id
    ON audit_logs (trace_id);
