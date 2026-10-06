-- NexusQuant 正式产品数据库结构；仅适用于全新空数据库。

-- 产品加密能力与序列。

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

CREATE SEQUENCE account_snapshots_snapshot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE accounts_account_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE credential_audit_logs_credential_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE exchange_account_credentials_credential_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE exchange_accounts_exchange_account_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE instrument_catalog_instrument_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE ledger_events_ledger_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE marketdata_bars_marketdata_bar_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE positions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- 表内表达式所需的纯函数。

CREATE FUNCTION require_canonical_trading_symbols(p_symbols text[]) RETURNS boolean
    LANGUAGE plpgsql IMMUTABLE
    AS $_$
DECLARE
    v_symbol TEXT;
    v_previous TEXT;
BEGIN
    IF p_symbols IS NULL OR array_ndims(p_symbols) <> 1
        OR cardinality(p_symbols) NOT BETWEEN 1 AND 2
        OR array_position(p_symbols, NULL) IS NOT NULL THEN
        RETURN FALSE;
    END IF;
    FOREACH v_symbol IN ARRAY p_symbols LOOP
        IF v_symbol !~ '^[A-Z0-9]{2,20}-USDT$' OR (v_previous IS NOT NULL AND v_symbol <= v_previous) THEN
            RETURN FALSE;
        END IF;
        v_previous := v_symbol;
    END LOOP;
    RETURN TRUE;
END;
$_$;

-- 业务表直接声明最终列与行级约束。

CREATE TABLE account_snapshots (
    snapshot_id bigint NOT NULL DEFAULT nextval('account_snapshots_snapshot_id_seq'::regclass),
    account_id bigint NOT NULL,
    currency character varying(32) NOT NULL,
    balance numeric(38,8) NOT NULL,
    available numeric(38,8) NOT NULL,
    frozen numeric(38,8) NOT NULL,
    ts timestamp with time zone NOT NULL,
    trace_id character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    trade_env character varying(8),
    balance_basis character varying(32),
    balance_scope character varying(32),
    recorded_at timestamp with time zone,
    CONSTRAINT chk_account_snapshots_balance_basis CHECK (((balance_basis IS NULL) OR ((balance_basis)::text = ANY ((ARRAY['POSITION_PROJECTION'::character varying, 'LEDGER_CASH_PROJECTION'::character varying])::text[])))),
    CONSTRAINT chk_account_snapshots_balance_scope CHECK (((balance_scope IS NULL) OR ((balance_scope)::text = 'NQ_MANAGED_ACCOUNT'::text))),
    CONSTRAINT chk_account_snapshots_provenance_complete CHECK ((((trade_env IS NULL) AND (balance_basis IS NULL) AND (balance_scope IS NULL) AND (recorded_at IS NULL)) OR ((trade_env IS NOT NULL) AND (balance_basis IS NOT NULL) AND (balance_scope IS NOT NULL) AND (recorded_at IS NOT NULL)))),
    CONSTRAINT chk_account_snapshots_trade_env CHECK (((trade_env IS NULL) OR ((trade_env)::text = ANY ((ARRAY['SIM'::character varying, 'LIVE'::character varying])::text[]))))
);

CREATE TABLE accounts (
    account_id bigint NOT NULL DEFAULT nextval('accounts_account_id_seq'::regclass),
    account_code character varying(64) NOT NULL,
    venue character varying(32) NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_accounts_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DISABLED'::character varying])::text[])))
);

CREATE TABLE audit_logs (
    id bigint NOT NULL DEFAULT nextval('audit_logs_id_seq'::regclass),
    domain character varying(64) NOT NULL,
    action character varying(64) NOT NULL,
    actor_id character varying(64),
    trace_id character varying(64) NOT NULL,
    detail_json jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE backtest_configs (
    backtest_config_id character varying(128) NOT NULL,
    research_config_id character varying(128) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    config_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    evaluation_spec_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    dataset_id uuid,
    dataset_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    strategy_version_id character varying(128),
    strategy_version_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    param_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    config_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    archived_at timestamp with time zone,
    archived_by character varying(128),
    archive_reason text,
    CONSTRAINT chk_backtest_configs_archive_metadata CHECK (((((status)::text = 'ARCHIVED'::text) AND (archived_at IS NOT NULL)) OR (((status)::text <> 'ARCHIVED'::text) AND (archived_at IS NULL) AND (archived_by IS NULL) AND (archive_reason IS NULL)))),
    CONSTRAINT chk_backtest_configs_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'ARCHIVED'::character varying, 'DISABLED'::character varying])::text[])))
);

CREATE TABLE backtest_eval_reports (
    eval_report_id character varying(128) NOT NULL,
    backtest_run_id character varying(128) NOT NULL,
    evaluation_status character varying(32) NOT NULL,
    initial_capital numeric(36,18),
    final_cash_balance numeric(36,18),
    final_position_market_value numeric(36,18),
    final_equity numeric(36,18),
    realized_pnl numeric(36,18),
    unrealized_pnl numeric(36,18),
    net_pnl numeric(36,18),
    total_return_rate numeric(36,18),
    total_fee numeric(36,18),
    total_slippage numeric(36,18),
    order_count integer,
    trade_count integer,
    winning_trade_count integer,
    losing_trade_count integer,
    flat_trade_count integer,
    win_rate numeric(36,18),
    max_drawdown numeric(36,18),
    max_drawdown_rate numeric(36,18),
    sharpe_ratio numeric(36,18),
    report_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    failure_code character varying(128),
    failure_message text,
    evaluated_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    total_return numeric(36,18),
    annualized_return numeric(36,18),
    profit_loss_ratio numeric(36,18),
    metrics_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    CONSTRAINT chk_backtest_eval_reports_status CHECK (((evaluation_status)::text = ANY ((ARRAY['SUCCEEDED'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE backtest_publish_records (
    publish_record_id character varying(128) NOT NULL,
    backtest_run_id character varying(128) NOT NULL,
    research_config_id character varying(128) NOT NULL,
    backtest_config_id character varying(128) NOT NULL,
    source_strategy_id character varying(128) NOT NULL,
    eval_report_id character varying(128),
    target_strategy_definition_id character varying(128),
    publish_status character varying(32) NOT NULL,
    publish_name character varying(255) NOT NULL,
    publish_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    evaluation_summary_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    failure_code character varying(128),
    failure_message text,
    published_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    strategy_version_id character varying(128),
    version_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    artifact_storage_key character varying(128),
    manifest_storage_key character varying(128),
    CONSTRAINT chk_backtest_publish_artifact_keys_pair CHECK ((((artifact_storage_key IS NULL) AND (manifest_storage_key IS NULL)) OR ((artifact_storage_key IS NOT NULL) AND (manifest_storage_key IS NOT NULL)))),
    CONSTRAINT chk_backtest_publish_artifact_storage_key CHECK (((artifact_storage_key IS NULL) OR (((artifact_storage_key)::text ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'::text) AND (POSITION(('..'::text) IN (artifact_storage_key)) = 0)))),
    CONSTRAINT chk_backtest_publish_manifest_storage_key CHECK (((manifest_storage_key IS NULL) OR (((manifest_storage_key)::text ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'::text) AND (POSITION(('..'::text) IN (manifest_storage_key)) = 0)))),
    CONSTRAINT chk_backtest_publish_records_status CHECK (((publish_status)::text = ANY ((ARRAY['SUCCEEDED'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE backtest_runs (
    backtest_run_id character varying(128) NOT NULL,
    backtest_config_id character varying(128) NOT NULL,
    research_config_id character varying(128) NOT NULL,
    source_strategy_id character varying(128) NOT NULL,
    status character varying(32) NOT NULL,
    strategy_snapshot jsonb NOT NULL,
    backtest_config_snapshot jsonb NOT NULL,
    summary_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    failure_code character varying(128),
    failure_message text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    dataset_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    strategy_version_id character varying(128),
    strategy_version_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    param_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    config_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    CONSTRAINT chk_backtest_runs_status CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'PREPARING'::character varying, 'RUNNING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[])))
);

CREATE TABLE continuous_sim_bars (
    paper_run_id character varying(64) NOT NULL,
    open_time timestamp with time zone NOT NULL,
    close_time timestamp with time zone NOT NULL,
    available_at timestamp with time zone NOT NULL,
    bar_sha256 character varying(64) NOT NULL,
    bar_json jsonb NOT NULL,
    observed_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_continuous_sim_bar_time CHECK (((close_time > open_time) AND (available_at >= close_time))),
    CONSTRAINT continuous_sim_bars_bar_sha256_check CHECK (((bar_sha256)::text ~ '^[0-9a-f]{64}$'::text))
);

CREATE TABLE continuous_sim_runs (
    paper_run_id character varying(64) NOT NULL,
    status character varying(16) NOT NULL,
    strategy_version_id character varying(128) NOT NULL,
    strategy_checksum character varying(64) NOT NULL,
    cost_sha256 character varying(64) NOT NULL,
    seed_last_open_time timestamp with time zone NOT NULL,
    last_processed_open_time timestamp with time zone NOT NULL,
    last_processed_close_time timestamp with time zone NOT NULL,
    last_processed_sha256 character varying(64) NOT NULL,
    last_observed_open_time timestamp with time zone NOT NULL,
    gap_start_open_time timestamp with time zone,
    started_at timestamp with time zone NOT NULL,
    last_poll_at timestamp with time zone,
    last_successful_poll_at timestamp with time zone,
    updated_at timestamp with time zone NOT NULL,
    block_reason character varying(64),
    consecutive_poll_failures integer DEFAULT 0 NOT NULL,
    CONSTRAINT chk_continuous_sim_cursor_order CHECK (((last_processed_open_time >= seed_last_open_time) AND (last_observed_open_time >= last_processed_open_time) AND (last_processed_close_time > last_processed_open_time))),
    CONSTRAINT continuous_sim_runs_consecutive_poll_failures_check CHECK ((consecutive_poll_failures >= 0)),
    CONSTRAINT continuous_sim_runs_cost_sha256_check CHECK (((cost_sha256)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT continuous_sim_runs_last_processed_sha256_check CHECK (((last_processed_sha256)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT continuous_sim_runs_status_check CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'STALLED'::character varying, 'STOPPED'::character varying])::text[]))),
    CONSTRAINT continuous_sim_runs_strategy_checksum_check CHECK (((strategy_checksum)::text ~ '^[0-9a-f]{64}$'::text))
);

CREATE TABLE controlled_execution_lease_events (
    event_id uuid NOT NULL,
    lease_id uuid NOT NULL,
    from_status character varying(16),
    to_status character varying(16) NOT NULL,
    lease_version bigint NOT NULL,
    reason_code character varying(128) NOT NULL,
    request_id character varying(128) NOT NULL,
    trace_id character varying(128) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_controlled_execution_lease_events_status CHECK ((((from_status IS NULL) OR ((from_status)::text = ANY ((ARRAY['CREATED'::character varying, 'ACTIVE'::character varying, 'CONSUMED'::character varying, 'EXPIRED'::character varying, 'CLOSED'::character varying, 'FAILED'::character varying])::text[]))) AND ((to_status)::text = ANY ((ARRAY['CREATED'::character varying, 'ACTIVE'::character varying, 'CONSUMED'::character varying, 'EXPIRED'::character varying, 'CLOSED'::character varying, 'FAILED'::character varying])::text[])))),
    CONSTRAINT chk_controlled_execution_lease_events_text CHECK (((btrim((reason_code)::text) <> ''::text) AND (btrim((request_id)::text) <> ''::text) AND (btrim((trace_id)::text) <> ''::text))),
    CONSTRAINT chk_controlled_execution_lease_events_version CHECK ((lease_version > 0))
);

CREATE TABLE controlled_execution_lease_intents (
    lease_id uuid NOT NULL,
    intent_id uuid NOT NULL,
    action character varying(16) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_controlled_execution_lease_intents_action CHECK (((action)::text = ANY ((ARRAY['PLACE'::character varying, 'CANCEL'::character varying])::text[])))
);

CREATE TABLE controlled_execution_leases (
    lease_id uuid NOT NULL,
    live_session_id uuid NOT NULL,
    binding_id uuid NOT NULL,
    binding_digest character varying(64) NOT NULL,
    status character varying(16) NOT NULL,
    max_notional numeric(38,8) NOT NULL,
    valid_from timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    closed_at timestamp with time zone,
    created_by bigint NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    operator_execution_authority_id uuid,
    predecessor_lease_id uuid,
    recovery_decision_id uuid,
    replacement_ordinal integer DEFAULT 0 NOT NULL,
    replacement_reason character varying(64),
    CONSTRAINT chk_controlled_execution_leases_digest CHECK (((binding_digest)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT chk_controlled_execution_leases_lifecycle_times CHECK (((((status)::text = ANY ((ARRAY['CREATED'::character varying, 'ACTIVE'::character varying])::text[])) AND (consumed_at IS NULL) AND (closed_at IS NULL)) OR (((status)::text = 'CONSUMED'::text) AND (consumed_at IS NOT NULL) AND (closed_at IS NULL)) OR (((status)::text = ANY ((ARRAY['EXPIRED'::character varying, 'CLOSED'::character varying, 'FAILED'::character varying])::text[])) AND (closed_at IS NOT NULL)))),
    CONSTRAINT chk_controlled_execution_leases_notional CHECK ((max_notional > (0)::numeric)),
    CONSTRAINT chk_controlled_execution_leases_replacement CHECK ((((replacement_ordinal = 0) AND (predecessor_lease_id IS NULL) AND (recovery_decision_id IS NULL) AND (replacement_reason IS NULL)) OR ((replacement_ordinal > 0) AND (predecessor_lease_id IS NOT NULL) AND (recovery_decision_id IS NOT NULL) AND ((replacement_reason)::text = ANY ((ARRAY['PRE_PLACE_ZERO_INTENT_FAILURE'::character varying, 'PRE_PLACE_TERMINAL_REGENERATION'::character varying])::text[]))))),
    CONSTRAINT chk_controlled_execution_leases_status CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'ACTIVE'::character varying, 'CONSUMED'::character varying, 'EXPIRED'::character varying, 'CLOSED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT chk_controlled_execution_leases_version CHECK ((version > 0)),
    CONSTRAINT chk_controlled_execution_leases_window CHECK ((expires_at > valid_from))
);

CREATE TABLE credential_audit_logs (
    credential_audit_log_id bigint NOT NULL DEFAULT nextval('credential_audit_logs_credential_audit_log_id_seq'::regclass),
    credential_id bigint NOT NULL,
    exchange_account_id bigint NOT NULL,
    event_type character varying(32) NOT NULL,
    actor character varying(128),
    reason text,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_credential_audit_logs_event_type CHECK (((event_type)::text = ANY ((ARRAY['CREATED'::character varying, 'VERIFIED'::character varying, 'FAILED_VERIFICATION'::character varying, 'DISABLED'::character varying, 'ENABLED'::character varying, 'REVOKED'::character varying, 'ROTATED'::character varying, 'EXPIRED'::character varying, 'USED'::character varying, 'ACCESS_DENIED'::character varying, 'PERMISSION_PROBE_STARTED'::character varying, 'PERMISSION_PROBE_SUCCEEDED'::character varying, 'PERMISSION_PROBE_FAILED'::character varying, 'PERMISSION_PROBE_SKIPPED'::character varying])::text[])))
);

CREATE TABLE emergency_stop_events (
    emergency_stop_id character varying(64) NOT NULL,
    paper_run_id character varying(64) NOT NULL,
    trigger_type character varying(32) NOT NULL,
    status character varying(16) NOT NULL,
    reason character varying(512),
    triggered_by character varying(128),
    triggered_at timestamp with time zone NOT NULL,
    resolved_at timestamp with time zone,
    request_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    result_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_estop_status CHECK (((status)::text = ANY ((ARRAY['TRIGGERED'::character varying, 'APPLIED'::character varying, 'FAILED'::character varying, 'RESOLVED'::character varying])::text[]))),
    CONSTRAINT chk_estop_trigger_type CHECK (((trigger_type)::text = ANY ((ARRAY['MANUAL'::character varying, 'RISK_LIMIT'::character varying, 'SYSTEM_ERROR'::character varying])::text[])))
);

CREATE TABLE equity_curve_snapshots (
    equity_snapshot_id character varying(64) NOT NULL,
    paper_run_id character varying(64) NOT NULL,
    snapshot_time timestamp with time zone NOT NULL,
    total_equity numeric(36,18) NOT NULL,
    cash_balance numeric(36,18) NOT NULL,
    position_value numeric(36,18) NOT NULL,
    unrealized_pnl numeric(36,18) DEFAULT 0 NOT NULL,
    realized_pnl numeric(36,18) DEFAULT 0 NOT NULL,
    drawdown numeric(36,18) DEFAULT 0 NOT NULL,
    source character varying(32) NOT NULL,
    created_at timestamp with time zone NOT NULL
);

CREATE TABLE event_store (
    event_id character varying(64) NOT NULL,
    topic character varying(128) NOT NULL,
    schema_version integer NOT NULL,
    event_type character varying(128) NOT NULL,
    payload_json jsonb NOT NULL,
    key_value character varying(128) NOT NULL,
    trace_id character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE exchange_account_credentials (
    credential_id bigint NOT NULL DEFAULT nextval('exchange_account_credentials_credential_id_seq'::regclass),
    exchange_account_id bigint NOT NULL,
    credential_type character varying(32) NOT NULL,
    encrypted_payload bytea NOT NULL,
    key_version integer NOT NULL,
    cipher_suite character varying(32) DEFAULT 'PGP_SYM_AES256'::character varying NOT NULL,
    masked_access_key character varying(64),
    verification_status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    revoked_at timestamp with time zone,
    rotated_from_credential_id bigint,
    last_verified_at timestamp with time zone,
    last_verification_error text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    credential_status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    revoked_by character varying(128),
    revoke_reason text,
    rotated_at timestamp with time zone,
    rotated_by character varying(128),
    last_used_at timestamp with time zone,
    failed_auth_count integer DEFAULT 0 NOT NULL,
    permission_scope character varying(64),
    withdraw_enabled boolean DEFAULT false NOT NULL,
    ip_allowlist_required boolean DEFAULT true NOT NULL,
    external_secret_ref character varying(256),
    key_alias character varying(128),
    permission_probe_status character varying(32) DEFAULT 'NOT_PROBED'::character varying NOT NULL,
    last_permission_probe_at timestamp with time zone,
    last_permission_probe_error text,
    ip_allowlist_probe_status character varying(32) DEFAULT 'NOT_CHECKED'::character varying NOT NULL,
    CONSTRAINT chk_exchange_account_credentials_credential_status CHECK (((credential_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DISABLED'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying, 'ROTATED'::character varying])::text[]))),
    CONSTRAINT chk_exchange_account_credentials_failed_auth_count CHECK ((failed_auth_count >= 0)),
    CONSTRAINT chk_exchange_account_credentials_ip_allowlist_probe_status CHECK (((ip_allowlist_probe_status)::text = ANY ((ARRAY['NOT_CHECKED'::character varying, 'PASSED'::character varying, 'FAILED'::character varying, 'UNKNOWN'::character varying, 'SKIPPED'::character varying])::text[]))),
    CONSTRAINT chk_exchange_account_credentials_permission_probe_status CHECK (((permission_probe_status)::text = ANY ((ARRAY['NOT_PROBED'::character varying, 'IN_PROGRESS'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying, 'SKIPPED'::character varying])::text[]))),
    CONSTRAINT chk_exchange_account_credentials_permission_scope CHECK (((permission_scope IS NULL) OR ((permission_scope)::text = ANY ((ARRAY['READ_ONLY'::character varying, 'TRADE'::character varying, 'FUNDING'::character varying])::text[])))),
    CONSTRAINT chk_exchange_account_credentials_revoked_at_required CHECK ((((credential_status)::text <> 'REVOKED'::text) OR (revoked_at IS NOT NULL))),
    CONSTRAINT chk_exchange_account_credentials_status CHECK (((verification_status)::text = ANY ((ARRAY['PENDING'::character varying, 'VERIFIED'::character varying, 'FAILED'::character varying, 'REVOKED'::character varying])::text[]))),
    CONSTRAINT chk_exchange_account_credentials_type CHECK (((credential_type)::text = ANY ((ARRAY['OKX_API_V5'::character varying, 'BINANCE_HMAC'::character varying, 'BINANCE_ED25519'::character varying])::text[])))
);

CREATE TABLE exchange_accounts (
    exchange_account_id bigint NOT NULL DEFAULT nextval('exchange_accounts_exchange_account_id_seq'::regclass),
    owner_user_id bigint NOT NULL,
    exchange_code character varying(32) NOT NULL,
    trade_env character varying(8) NOT NULL,
    account_alias character varying(64) NOT NULL,
    external_account_ref character varying(128),
    legacy_account_id bigint,
    is_default boolean DEFAULT false NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_exchange_accounts_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DISABLED'::character varying])::text[]))),
    CONSTRAINT chk_exchange_accounts_trade_env CHECK (((trade_env)::text = ANY ((ARRAY['SIM'::character varying, 'LIVE'::character varying])::text[])))
);

CREATE TABLE execution_instrument_observation_items (
    observation_id uuid NOT NULL,
    observation_type character varying(32) NOT NULL,
    symbol character varying(64) NOT NULL,
    trading_status character varying(16) NOT NULL,
    tick_size numeric(38,18) NOT NULL,
    lot_size numeric(38,18) NOT NULL,
    minimum_order_size numeric(38,18) NOT NULL,
    minimum_order_value numeric(38,18),
    minimum_order_value_currency character varying(16),
    minimum_order_value_evidence_class character varying(32) NOT NULL,
    CONSTRAINT chk_execution_instrument_observation_item_amounts CHECK (((tick_size > (0)::numeric) AND (lot_size > (0)::numeric) AND (minimum_order_size > (0)::numeric))),
    CONSTRAINT chk_execution_instrument_observation_item_status CHECK (((trading_status)::text = ANY ((ARRAY['LIVE'::character varying, 'SUSPEND'::character varying, 'PREOPEN'::character varying, 'TEST'::character varying])::text[]))),
    CONSTRAINT chk_execution_instrument_observation_item_symbol CHECK (((symbol)::text ~ '^[A-Z0-9]{2,20}-USDT$'::text)),
    CONSTRAINT chk_execution_instrument_observation_item_type CHECK (((observation_type)::text = 'INSTRUMENT_METADATA'::text)),
    CONSTRAINT chk_execution_instrument_observation_item_value_evidence CHECK (((((minimum_order_value_evidence_class)::text = 'VENUE_PUBLISHED'::text) AND (minimum_order_value > (0)::numeric) AND (minimum_order_value_currency IS NOT NULL) AND (btrim((minimum_order_value_currency)::text) <> ''::text)) OR (((minimum_order_value_evidence_class)::text = 'VENUE_NOT_PUBLISHED'::text) AND (minimum_order_value IS NULL) AND (minimum_order_value_currency IS NULL)) OR (((minimum_order_value_evidence_class)::text = 'LEGACY_MINIMUM_EVIDENCE_REQUIRED'::text) AND (minimum_order_value > (0)::numeric) AND ((minimum_order_value_currency)::text = 'USDT'::text))))
);

CREATE TABLE execution_intents (
    intent_id uuid NOT NULL,
    session_id uuid NOT NULL,
    sequence bigint NOT NULL,
    action character varying(16) NOT NULL,
    symbol character varying(64) NOT NULL,
    side character varying(8),
    order_type character varying(16),
    quantity numeric(38,8),
    limit_price numeric(38,8),
    payload_hash_schema_version character varying(64) NOT NULL,
    payload_hash character varying(64) NOT NULL,
    client_order_id character varying(128) NOT NULL,
    local_order_id character varying(64) NOT NULL,
    state character varying(32) NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    claimed_by character varying(128),
    claim_token uuid,
    claimed_at timestamp with time zone,
    lease_expires_at timestamp with time zone,
    send_started_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_execution_intents_action CHECK (((action)::text = ANY ((ARRAY['PLACE'::character varying, 'CANCEL'::character varying])::text[]))),
    CONSTRAINT chk_execution_intents_action_fields CHECK (((((action)::text = 'PLACE'::text) AND ((side)::text = ANY ((ARRAY['BUY'::character varying, 'SELL'::character varying])::text[])) AND ((order_type)::text = 'LIMIT'::text) AND (quantity > (0)::numeric) AND (limit_price > (0)::numeric)) OR (((action)::text = 'CANCEL'::text) AND (side IS NULL) AND (order_type IS NULL) AND (quantity IS NULL) AND (limit_price IS NULL)))),
    CONSTRAINT chk_execution_intents_claim CHECK (((((state)::text = ANY ((ARRAY['CREATED'::character varying, 'CANCELLED'::character varying])::text[])) AND (claimed_by IS NULL) AND (claim_token IS NULL) AND (claimed_at IS NULL) AND (lease_expires_at IS NULL)) OR (((state)::text = ANY ((ARRAY['CLAIMED'::character varying, 'SEND_STARTED'::character varying, 'SEND_SUCCEEDED'::character varying, 'UNKNOWN'::character varying, 'FAILED'::character varying, 'RECONCILED'::character varying])::text[])) AND (claimed_by IS NOT NULL) AND (claim_token IS NOT NULL) AND (claimed_at IS NOT NULL) AND (lease_expires_at > claimed_at)))),
    CONSTRAINT chk_execution_intents_payload CHECK ((((payload_hash_schema_version)::text = 'execution-intent-payload.v1'::text) AND ((payload_hash)::text ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT chk_execution_intents_send_started CHECK (((((state)::text = ANY ((ARRAY['SEND_STARTED'::character varying, 'SEND_SUCCEEDED'::character varying, 'UNKNOWN'::character varying, 'RECONCILED'::character varying])::text[])) AND (send_started_at IS NOT NULL)) OR (((state)::text = 'FAILED'::text) AND (send_started_at IS NOT NULL)) OR (((state)::text = ANY ((ARRAY['CREATED'::character varying, 'CLAIMED'::character varying, 'CANCELLED'::character varying])::text[])) AND (send_started_at IS NULL)))),
    CONSTRAINT chk_execution_intents_sequence CHECK ((sequence > 0)),
    CONSTRAINT chk_execution_intents_state CHECK (((state)::text = ANY ((ARRAY['CREATED'::character varying, 'CLAIMED'::character varying, 'SEND_STARTED'::character varying, 'SEND_SUCCEEDED'::character varying, 'UNKNOWN'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'RECONCILED'::character varying])::text[]))),
    CONSTRAINT chk_execution_intents_text CHECK (((btrim((symbol)::text) <> ''::text) AND (btrim((client_order_id)::text) <> ''::text) AND (btrim((local_order_id)::text) <> ''::text))),
    CONSTRAINT chk_execution_intents_version CHECK ((version > 0))
);

CREATE TABLE execution_pre_place_recovery_decisions (
    decision_id uuid NOT NULL,
    predecessor_lease_id uuid NOT NULL,
    predecessor_session_id uuid NOT NULL,
    decision character varying(64) NOT NULL,
    place_intent_count integer NOT NULL,
    send_started_count integer NOT NULL,
    execution_intent_count integer NOT NULL,
    execution_receipt_count integer NOT NULL,
    order_count integer NOT NULL,
    trade_count integer NOT NULL,
    ledger_count integer NOT NULL,
    decided_by bigint NOT NULL,
    request_id character varying(128) NOT NULL,
    trace_id character varying(128) NOT NULL,
    decided_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_execution_pre_place_recovery_decision CHECK (((decision)::text = ANY ((ARRAY['REPLACEMENT_ALLOWED_ZERO_INTENT'::character varying, 'PRE_PLACE_REGENERATION_ALLOWED'::character varying])::text[]))),
    CONSTRAINT chk_execution_pre_place_recovery_text CHECK (((btrim((request_id)::text) <> ''::text) AND (btrim((trace_id)::text) <> ''::text))),
    CONSTRAINT chk_execution_pre_place_recovery_zero_proof CHECK (((place_intent_count = 0) AND (send_started_count = 0) AND (execution_intent_count = 0) AND (execution_receipt_count = 0) AND (order_count = 0) AND (trade_count = 0) AND (ledger_count = 0)))
);

CREATE TABLE execution_prerequisite_observations (
    observation_id uuid NOT NULL,
    execution_scope_id uuid NOT NULL,
    observation_set_id uuid NOT NULL,
    observation_type character varying(32) NOT NULL,
    observation_schema_version character varying(64) NOT NULL,
    observation_identity character varying(128) NOT NULL,
    source_identity character varying(128) NOT NULL,
    source_schema_version character varying(64) NOT NULL,
    observed_at timestamp with time zone NOT NULL,
    recorded_at timestamp with time zone DEFAULT now() NOT NULL,
    recorder_identity character varying(128) NOT NULL,
    observation_payload_hash character varying(64) NOT NULL,
    instrument_metadata_digest character varying(64),
    fee_schedule_digest character varying(64),
    balance_snapshot_digest character varying(64),
    clock_sync_observation_digest character varying(64),
    fee_tier character varying(64),
    fee_evidence_class character varying(32),
    maker_fee_rate numeric(20,12),
    taker_fee_rate numeric(20,12),
    fee_loss_treatment character varying(64),
    balance_currency character varying(16),
    available_balance numeric(38,8),
    signed_timestamp_source character varying(64),
    observed_skew_ms bigint,
    market_snapshot_digest character varying(64),
    market_instrument character varying(64),
    best_ask numeric(38,18),
    CONSTRAINT chk_execution_observation_hashes CHECK ((((observation_payload_hash)::text ~ '^[0-9a-f]{64}$'::text) AND ((instrument_metadata_digest IS NULL) OR ((instrument_metadata_digest)::text ~ '^[0-9a-f]{64}$'::text)) AND ((fee_schedule_digest IS NULL) OR ((fee_schedule_digest)::text ~ '^[0-9a-f]{64}$'::text)) AND ((balance_snapshot_digest IS NULL) OR ((balance_snapshot_digest)::text ~ '^[0-9a-f]{64}$'::text)) AND ((clock_sync_observation_digest IS NULL) OR ((clock_sync_observation_digest)::text ~ '^[0-9a-f]{64}$'::text)) AND ((market_snapshot_digest IS NULL) OR ((market_snapshot_digest)::text ~ '^[0-9a-f]{64}$'::text)))),
    CONSTRAINT chk_execution_observation_text CHECK (((btrim((observation_identity)::text) <> ''::text) AND (btrim((source_identity)::text) <> ''::text) AND (btrim((source_schema_version)::text) <> ''::text) AND (btrim((recorder_identity)::text) <> ''::text))),
    CONSTRAINT chk_execution_observation_type CHECK (((observation_type)::text = ANY ((ARRAY['INSTRUMENT_METADATA'::character varying, 'FEE_SCHEDULE'::character varying, 'BALANCE_SNAPSHOT'::character varying, 'CLOCK_SYNC'::character varying, 'MARKET_SNAPSHOT'::character varying])::text[]))),
    CONSTRAINT chk_execution_observation_variant CHECK (((((observation_type)::text = 'INSTRUMENT_METADATA'::text) AND ((observation_schema_version)::text = ANY ((ARRAY['instrument-metadata-observation.v1'::character varying, 'instrument-metadata-observation.v2'::character varying])::text[])) AND (instrument_metadata_digest IS NOT NULL) AND (fee_schedule_digest IS NULL) AND (balance_snapshot_digest IS NULL) AND (clock_sync_observation_digest IS NULL) AND (market_snapshot_digest IS NULL) AND (market_instrument IS NULL) AND (best_ask IS NULL) AND (fee_tier IS NULL) AND (fee_evidence_class IS NULL) AND (maker_fee_rate IS NULL) AND (taker_fee_rate IS NULL) AND (fee_loss_treatment IS NULL) AND (balance_currency IS NULL) AND (available_balance IS NULL) AND (signed_timestamp_source IS NULL) AND (observed_skew_ms IS NULL)) OR (((observation_type)::text = 'FEE_SCHEDULE'::text) AND ((observation_schema_version)::text = 'fee-schedule-observation.v1'::text) AND (instrument_metadata_digest IS NULL) AND (fee_schedule_digest IS NOT NULL) AND (balance_snapshot_digest IS NULL) AND (clock_sync_observation_digest IS NULL) AND (market_snapshot_digest IS NULL) AND (market_instrument IS NULL) AND (best_ask IS NULL) AND (btrim((fee_tier)::text) <> ''::text) AND ((fee_evidence_class)::text = ANY ((ARRAY['OBSERVED_PRIVATE'::character varying, 'ESTIMATED_PUBLIC'::character varying])::text[])) AND ((maker_fee_rate >= ('-1'::integer)::numeric) AND (maker_fee_rate <= (1)::numeric)) AND ((taker_fee_rate >= ('-1'::integer)::numeric) AND (taker_fee_rate <= (1)::numeric)) AND ((fee_loss_treatment)::text = 'INCLUDE_IN_DAILY_LOSS_AND_CAPITAL_USAGE'::text) AND (balance_currency IS NULL) AND (available_balance IS NULL) AND (signed_timestamp_source IS NULL) AND (observed_skew_ms IS NULL)) OR (((observation_type)::text = 'BALANCE_SNAPSHOT'::text) AND ((observation_schema_version)::text = 'balance-snapshot-observation.v1'::text) AND (instrument_metadata_digest IS NULL) AND (fee_schedule_digest IS NULL) AND (balance_snapshot_digest IS NOT NULL) AND (clock_sync_observation_digest IS NULL) AND (market_snapshot_digest IS NULL) AND (market_instrument IS NULL) AND (best_ask IS NULL) AND (fee_tier IS NULL) AND (fee_evidence_class IS NULL) AND (maker_fee_rate IS NULL) AND (taker_fee_rate IS NULL) AND (fee_loss_treatment IS NULL) AND ((balance_currency)::text = 'USDT'::text) AND (available_balance >= (0)::numeric) AND (signed_timestamp_source IS NULL) AND (observed_skew_ms IS NULL)) OR (((observation_type)::text = 'CLOCK_SYNC'::text) AND ((observation_schema_version)::text = 'clock-sync-observation.v1'::text) AND (instrument_metadata_digest IS NULL) AND (fee_schedule_digest IS NULL) AND (balance_snapshot_digest IS NULL) AND (clock_sync_observation_digest IS NOT NULL) AND (market_snapshot_digest IS NULL) AND (market_instrument IS NULL) AND (best_ask IS NULL) AND (fee_tier IS NULL) AND (fee_evidence_class IS NULL) AND (maker_fee_rate IS NULL) AND (taker_fee_rate IS NULL) AND (fee_loss_treatment IS NULL) AND (balance_currency IS NULL) AND (available_balance IS NULL) AND ((signed_timestamp_source)::text = 'NTP_DISCIPLINED_SYSTEM_CLOCK'::text) AND ((observed_skew_ms >= '-1000'::integer) AND (observed_skew_ms <= 1000))) OR (((observation_type)::text = 'MARKET_SNAPSHOT'::text) AND ((observation_schema_version)::text = 'market-snapshot-observation.v1'::text) AND (market_snapshot_digest IS NOT NULL) AND ((market_instrument)::text ~ '^[A-Z0-9]{2,20}-USDT$'::text) AND (best_ask > (0)::numeric) AND (best_ask = round(best_ask, 8)) AND (instrument_metadata_digest IS NULL) AND (fee_schedule_digest IS NULL) AND (balance_snapshot_digest IS NULL) AND (clock_sync_observation_digest IS NULL) AND (fee_tier IS NULL) AND (fee_evidence_class IS NULL) AND (maker_fee_rate IS NULL) AND (taker_fee_rate IS NULL) AND (fee_loss_treatment IS NULL) AND (balance_currency IS NULL) AND (available_balance IS NULL) AND (signed_timestamp_source IS NULL) AND (observed_skew_ms IS NULL))))
);

CREATE TABLE execution_receipts (
    receipt_id uuid NOT NULL,
    intent_id uuid NOT NULL,
    receipt_ordinal integer NOT NULL,
    outcome character varying(32) NOT NULL,
    exchange_request_id character varying(128),
    exchange_order_id character varying(128),
    error_category character varying(64),
    error_code character varying(128),
    received_at timestamp with time zone NOT NULL,
    payload_digest character varying(64) NOT NULL,
    payload_digest_schema_version character varying(64) NOT NULL,
    CONSTRAINT chk_execution_receipts_digest CHECK ((((payload_digest)::text ~ '^[0-9a-f]{64}$'::text) AND ((payload_digest_schema_version)::text = 'execution-receipt-envelope.v1'::text))),
    CONSTRAINT chk_execution_receipts_ordinal CHECK ((receipt_ordinal > 0)),
    CONSTRAINT chk_execution_receipts_outcome CHECK (((outcome)::text = ANY ((ARRAY['ACKNOWLEDGED'::character varying, 'REJECTED'::character varying, 'TIMEOUT'::character varying, 'TRANSPORT_ERROR'::character varying, 'UNKNOWN'::character varying, 'QUERY_CONFIRMED'::character varying, 'QUERY_NOT_FOUND'::character varying])::text[])))
);

CREATE TABLE execution_scope_bindings (
    execution_scope_id uuid NOT NULL,
    session_id uuid NOT NULL,
    scope_schema_version character varying(64) NOT NULL,
    instrument_metadata_digest character varying(64) NOT NULL,
    instrument_source_identity character varying(128) NOT NULL,
    instrument_source_schema_version character varying(64) NOT NULL,
    instrument_maximum_age_ms bigint NOT NULL,
    fee_schedule_digest character varying(64) NOT NULL,
    fee_tier character varying(64) NOT NULL,
    fee_evidence_class character varying(32) NOT NULL,
    fee_source_identity character varying(128) NOT NULL,
    fee_source_schema_version character varying(64) NOT NULL,
    fee_maximum_age_ms bigint NOT NULL,
    balance_source_identity character varying(128) NOT NULL,
    balance_source_schema_version character varying(64) NOT NULL,
    balance_maximum_age_ms bigint NOT NULL,
    clock_source_identity character varying(128) NOT NULL,
    clock_source_schema_version character varying(64) NOT NULL,
    clock_maximum_age_ms bigint NOT NULL,
    signed_timestamp_source character varying(64) NOT NULL,
    maximum_tolerated_skew_ms bigint NOT NULL,
    endpoint_policy_version character varying(64) NOT NULL,
    endpoint_policy_digest character varying(64) NOT NULL,
    provider_contract_identity character varying(128) NOT NULL,
    provider_artifact_digest character varying(64) NOT NULL,
    worker_identity character varying(128) NOT NULL,
    worker_release_digest character varying(64) NOT NULL,
    execution_scope_hash character varying(64) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_execution_scope_bindings_age_skew CHECK ((((instrument_maximum_age_ms >= 1) AND (instrument_maximum_age_ms <= 300000)) AND ((fee_maximum_age_ms >= 1) AND (fee_maximum_age_ms <= 3600000)) AND ((balance_maximum_age_ms >= 1) AND (balance_maximum_age_ms <= 10000)) AND ((clock_maximum_age_ms >= 1) AND (clock_maximum_age_ms <= 60000)) AND ((maximum_tolerated_skew_ms >= 0) AND (maximum_tolerated_skew_ms <= 1000)))),
    CONSTRAINT chk_execution_scope_bindings_digests CHECK ((((instrument_metadata_digest)::text ~ '^[0-9a-f]{64}$'::text) AND ((fee_schedule_digest)::text ~ '^[0-9a-f]{64}$'::text) AND ((endpoint_policy_digest)::text ~ '^[0-9a-f]{64}$'::text) AND ((provider_artifact_digest)::text ~ '^[0-9a-f]{64}$'::text) AND ((worker_release_digest)::text ~ '^[0-9a-f]{64}$'::text) AND ((execution_scope_hash)::text ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT chk_execution_scope_bindings_fee_evidence CHECK (((fee_evidence_class)::text = ANY ((ARRAY['OBSERVED_PRIVATE'::character varying, 'ESTIMATED_PUBLIC'::character varying])::text[]))),
    CONSTRAINT chk_execution_scope_bindings_schema CHECK (((scope_schema_version)::text = 'execution-scope.v1'::text)),
    CONSTRAINT chk_execution_scope_bindings_text CHECK (((btrim((instrument_source_identity)::text) <> ''::text) AND (btrim((instrument_source_schema_version)::text) <> ''::text) AND (btrim((fee_tier)::text) <> ''::text) AND (btrim((fee_source_identity)::text) <> ''::text) AND (btrim((fee_source_schema_version)::text) <> ''::text) AND (btrim((balance_source_identity)::text) <> ''::text) AND (btrim((balance_source_schema_version)::text) <> ''::text) AND (btrim((clock_source_identity)::text) <> ''::text) AND (btrim((clock_source_schema_version)::text) <> ''::text) AND (btrim((endpoint_policy_version)::text) <> ''::text) AND (btrim((provider_contract_identity)::text) <> ''::text) AND (btrim((worker_identity)::text) <> ''::text))),
    CONSTRAINT chk_execution_scope_bindings_timestamp_source CHECK (((signed_timestamp_source)::text = 'NTP_DISCIPLINED_SYSTEM_CLOCK'::text))
);

CREATE TABLE instrument_catalog (
    instrument_id bigint NOT NULL DEFAULT nextval('instrument_catalog_instrument_id_seq'::regclass),
    exchange_code character varying(32) NOT NULL,
    instrument_type character varying(32) NOT NULL,
    exchange_symbol character varying(64) NOT NULL,
    internal_symbol character varying(64) NOT NULL,
    base_asset character varying(32) NOT NULL,
    quote_asset character varying(32) NOT NULL,
    status character varying(32) NOT NULL,
    tick_size numeric(38,18),
    step_size numeric(38,18),
    min_quantity numeric(38,18),
    source character varying(64) NOT NULL,
    synced_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    max_limit_quantity numeric(38,18),
    max_market_size numeric(38,18),
    max_market_size_unit character varying(16),
    max_limit_notional_usd numeric(38,18),
    max_market_notional_usd numeric(38,18),
    source_schema_version character varying(64),
    observed_at timestamp with time zone,
    next_rule_effective_at timestamp with time zone,
    rule_checksum character varying(64),
    CONSTRAINT chk_instrument_catalog_instrument_type CHECK (((instrument_type)::text = 'SPOT'::text)),
    CONSTRAINT chk_instrument_catalog_max_limit_notional_positive CHECK (((max_limit_notional_usd IS NULL) OR (max_limit_notional_usd > (0)::numeric))),
    CONSTRAINT chk_instrument_catalog_max_limit_quantity_positive CHECK (((max_limit_quantity IS NULL) OR (max_limit_quantity > (0)::numeric))),
    CONSTRAINT chk_instrument_catalog_max_market_notional_positive CHECK (((max_market_notional_usd IS NULL) OR (max_market_notional_usd > (0)::numeric))),
    CONSTRAINT chk_instrument_catalog_max_market_size_unit CHECK ((((max_market_size IS NULL) AND (max_market_size_unit IS NULL)) OR ((max_market_size IS NOT NULL) AND (max_market_size_unit IS NOT NULL) AND (max_market_size > (0)::numeric) AND ((max_market_size_unit)::text = 'USDT'::text)))),
    CONSTRAINT chk_instrument_catalog_min_quantity_positive CHECK (((min_quantity IS NULL) OR (min_quantity > (0)::numeric))),
    CONSTRAINT chk_instrument_catalog_next_rule_after_observed CHECK (((next_rule_effective_at IS NULL) OR ((observed_at IS NOT NULL) AND (next_rule_effective_at > observed_at)))),
    CONSTRAINT chk_instrument_catalog_observed_before_synced CHECK (((observed_at IS NULL) OR (observed_at <= synced_at))),
    CONSTRAINT chk_instrument_catalog_rule_checksum CHECK (((rule_checksum IS NULL) OR ((rule_checksum)::text ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT chk_instrument_catalog_source_schema_version CHECK (((source_schema_version IS NULL) OR (btrim((source_schema_version)::text) <> ''::text))),
    CONSTRAINT chk_instrument_catalog_status_normalized CHECK ((((status)::text = upper((status)::text)) AND (btrim((status)::text) <> ''::text))),
    CONSTRAINT chk_instrument_catalog_step_size_positive CHECK (((step_size IS NULL) OR (step_size > (0)::numeric))),
    CONSTRAINT chk_instrument_catalog_tick_size_positive CHECK (((tick_size IS NULL) OR (tick_size > (0)::numeric)))
);

CREATE TABLE kill_switch_events (
    id uuid NOT NULL,
    scope character varying(64) NOT NULL,
    from_status character varying(16),
    to_status character varying(16) NOT NULL,
    state_version bigint NOT NULL,
    reason_code character varying(64) NOT NULL,
    source character varying(64) NOT NULL,
    actor_id character varying(128) NOT NULL,
    trace_id character varying(128) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_kill_switch_events_actor CHECK ((btrim((actor_id)::text) <> ''::text)),
    CONSTRAINT chk_kill_switch_events_from_status CHECK (((from_status IS NULL) OR ((from_status)::text = ANY ((ARRAY['ENGAGED'::character varying, 'DISENGAGED'::character varying])::text[])))),
    CONSTRAINT chk_kill_switch_events_reason CHECK ((btrim((reason_code)::text) <> ''::text)),
    CONSTRAINT chk_kill_switch_events_source CHECK ((btrim((source)::text) <> ''::text)),
    CONSTRAINT chk_kill_switch_events_to_status CHECK (((to_status)::text = ANY ((ARRAY['ENGAGED'::character varying, 'DISENGAGED'::character varying])::text[]))),
    CONSTRAINT chk_kill_switch_events_trace CHECK ((btrim((trace_id)::text) <> ''::text)),
    CONSTRAINT chk_kill_switch_events_version CHECK ((state_version > 0))
);

CREATE TABLE kill_switch_states (
    scope character varying(64) NOT NULL,
    status character varying(16) DEFAULT 'ENGAGED'::character varying NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    reason_code character varying(64) NOT NULL,
    source character varying(64) NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(128) NOT NULL,
    trace_id character varying(128) NOT NULL,
    CONSTRAINT chk_kill_switch_states_reason CHECK ((btrim((reason_code)::text) <> ''::text)),
    CONSTRAINT chk_kill_switch_states_scope CHECK (((scope)::text = 'GLOBAL_TRADING'::text)),
    CONSTRAINT chk_kill_switch_states_source CHECK ((btrim((source)::text) <> ''::text)),
    CONSTRAINT chk_kill_switch_states_status CHECK (((status)::text = ANY ((ARRAY['ENGAGED'::character varying, 'DISENGAGED'::character varying])::text[]))),
    CONSTRAINT chk_kill_switch_states_trace CHECK ((btrim((trace_id)::text) <> ''::text)),
    CONSTRAINT chk_kill_switch_states_updated_by CHECK ((btrim((updated_by)::text) <> ''::text)),
    CONSTRAINT chk_kill_switch_states_version CHECK ((version > 0))
);

CREATE TABLE ledger_entries (
    entry_id character varying(64) NOT NULL,
    account_id bigint NOT NULL,
    currency character varying(32) NOT NULL,
    delta numeric(38,8) NOT NULL,
    balance_after numeric(38,8),
    direction character varying(16) NOT NULL,
    ref_type character varying(32) NOT NULL,
    ref_id character varying(128) NOT NULL,
    idempotency_key character varying(128),
    trace_id character varying(64) NOT NULL,
    ts timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_ledger_entries_direction CHECK (((direction)::text = ANY ((ARRAY['DEBIT'::character varying, 'CREDIT'::character varying])::text[])))
);

CREATE TABLE ledger_events (
    ledger_event_id bigint NOT NULL DEFAULT nextval('ledger_events_ledger_event_id_seq'::regclass),
    entry_id character varying(64) NOT NULL,
    event_type character varying(64) NOT NULL,
    payload_json jsonb NOT NULL,
    trace_id character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE live_session_events (
    event_id uuid NOT NULL,
    session_id uuid NOT NULL,
    sequence_no bigint NOT NULL,
    from_state character varying(32),
    to_state character varying(32) NOT NULL,
    command character varying(64) NOT NULL,
    actor_id bigint,
    request_id character varying(128) NOT NULL,
    trace_id character varying(128) NOT NULL,
    reason_code character varying(128) NOT NULL,
    idempotency_key character varying(128) NOT NULL,
    command_payload_hash character varying(64) NOT NULL,
    command_payload_schema_version character varying(64) NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_live_session_events_metadata CHECK (((jsonb_typeof(metadata) = 'object'::text) AND (pg_column_size(metadata) <= 8192))),
    CONSTRAINT chk_live_session_events_payload CHECK ((((command_payload_hash)::text ~ '^[0-9a-f]{64}$'::text) AND ((command_payload_schema_version)::text = 'live-session-command.v1'::text))),
    CONSTRAINT chk_live_session_events_sequence CHECK ((sequence_no > 0)),
    CONSTRAINT chk_live_session_events_text CHECK (((btrim((command)::text) <> ''::text) AND (btrim((request_id)::text) <> ''::text) AND (btrim((trace_id)::text) <> ''::text) AND (btrim((reason_code)::text) <> ''::text) AND (btrim((idempotency_key)::text) <> ''::text)))
);

CREATE TABLE live_sessions (
    session_id uuid NOT NULL,
    owner_id bigint NOT NULL,
    exchange_account_id bigint NOT NULL,
    venue character varying(32) NOT NULL,
    strategy_release_id character varying(128),
    release_digest character varying(64),
    release_admission_revision bigint,
    risk_limit_set_id uuid,
    risk_limit_set_digest character varying(64),
    credential_reference bigint NOT NULL,
    symbol_allowlist text[] NOT NULL,
    capital_cap numeric(38,8) NOT NULL,
    execution_window_start timestamp with time zone NOT NULL,
    execution_window_end timestamp with time zone NOT NULL,
    state character varying(32) NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    approval_scope_hash character varying(64) NOT NULL,
    approval_scope_schema_version character varying(64) NOT NULL,
    next_event_sequence bigint DEFAULT 1 NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    authority_type character varying(32) NOT NULL,
    operator_execution_authority_id uuid,
    operator_execution_authority_digest character varying(64),
    CONSTRAINT chk_live_sessions_authority_semantics CHECK (((((authority_type)::text = 'STRATEGY'::text) AND (strategy_release_id IS NOT NULL) AND (release_digest IS NOT NULL) AND (release_admission_revision IS NOT NULL) AND (risk_limit_set_id IS NOT NULL) AND (risk_limit_set_digest IS NOT NULL) AND (operator_execution_authority_id IS NULL) AND (operator_execution_authority_digest IS NULL) AND ((approval_scope_schema_version)::text = 'approval-scope.v1'::text)) OR (((authority_type)::text = 'OPERATOR_CONTROLLED_EXECUTION'::text) AND (strategy_release_id IS NULL) AND (release_digest IS NULL) AND (release_admission_revision IS NULL) AND (risk_limit_set_id IS NULL) AND (risk_limit_set_digest IS NULL) AND (operator_execution_authority_id IS NOT NULL) AND (operator_execution_authority_digest IS NOT NULL) AND ((approval_scope_schema_version)::text = 'approval-scope.operator.v1'::text)))),
    CONSTRAINT chk_live_sessions_authority_type CHECK (((authority_type)::text = ANY (ARRAY[('STRATEGY'::character varying)::text, ('OPERATOR_CONTROLLED_EXECUTION'::character varying)::text]))),
    CONSTRAINT chk_live_sessions_canonical_symbols CHECK (require_canonical_trading_symbols(symbol_allowlist)),
    CONSTRAINT chk_live_sessions_capital CHECK ((capital_cap > (0)::numeric)),
    CONSTRAINT chk_live_sessions_digests CHECK ((((approval_scope_hash)::text ~ '^[0-9a-f]{64}$'::text) AND ((release_digest IS NULL) OR ((release_digest)::text ~ '^[0-9a-f]{64}$'::text)) AND ((risk_limit_set_digest IS NULL) OR ((risk_limit_set_digest)::text ~ '^[0-9a-f]{64}$'::text)) AND ((operator_execution_authority_digest IS NULL) OR ((operator_execution_authority_digest)::text ~ '^[0-9a-f]{64}$'::text)))),
    CONSTRAINT chk_live_sessions_revision_version CHECK (((version > 0) AND (next_event_sequence > 0) AND ((release_admission_revision IS NULL) OR (release_admission_revision > 0)))),
    CONSTRAINT chk_live_sessions_state CHECK (((state)::text = ANY ((ARRAY['APPROVAL_PENDING'::character varying, 'APPROVED'::character varying, 'LIVE_WARMUP'::character varying, 'LIVE_ACTIVE'::character varying, 'LIVE_PAUSED'::character varying, 'LIVE_STOPPED'::character varying, 'LIVE_RECONCILING'::character varying, 'RECONCILIATION_BLOCKED'::character varying, 'REJECTED'::character varying, 'FAILED'::character varying, 'KILLED'::character varying, 'LIVE_RECONCILED'::character varying])::text[]))),
    CONSTRAINT chk_live_sessions_symbols CHECK (((cardinality(symbol_allowlist) >= 1) AND (cardinality(symbol_allowlist) <= 2))),
    CONSTRAINT chk_live_sessions_venue CHECK (((venue)::text = 'OKX_SPOT'::text)),
    CONSTRAINT chk_live_sessions_window CHECK ((execution_window_end > execution_window_start))
);

CREATE TABLE marketdata_bars (
    marketdata_bar_id bigint NOT NULL DEFAULT nextval('marketdata_bars_marketdata_bar_id_seq'::regclass),
    exchange_code character varying(32) NOT NULL,
    symbol character varying(64) NOT NULL,
    "interval" character varying(16) NOT NULL,
    open_time timestamp with time zone NOT NULL,
    close_time timestamp with time zone NOT NULL,
    open_price numeric(38,8) NOT NULL,
    high_price numeric(38,8) NOT NULL,
    low_price numeric(38,8) NOT NULL,
    close_price numeric(38,8) NOT NULL,
    volume numeric(38,8) NOT NULL,
    source character varying(32) DEFAULT 'IMPORT'::character varying NOT NULL,
    ingested_at timestamp with time zone DEFAULT now() NOT NULL,
    market_type character varying(16) DEFAULT 'SPOT'::character varying NOT NULL,
    quote_volume numeric(38,8),
    trade_count bigint,
    quality_status character varying(32) DEFAULT 'OK'::character varying NOT NULL,
    raw_payload_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    available_at timestamp with time zone,
    CONSTRAINT chk_marketdata_bar_available_at CHECK (((available_at IS NULL) OR (available_at >= close_time)))
);

CREATE TABLE marketdata_dataset_coverage (
    coverage_id uuid NOT NULL,
    dataset_id uuid NOT NULL,
    range_start_time timestamp with time zone NOT NULL,
    range_end_time timestamp with time zone NOT NULL,
    expected_bars bigint DEFAULT 0 NOT NULL,
    actual_bars bigint DEFAULT 0 NOT NULL,
    missing_bars bigint DEFAULT 0 NOT NULL,
    duplicate_bars bigint DEFAULT 0 NOT NULL,
    invalid_bars bigint DEFAULT 0 NOT NULL,
    quality_status character varying(32) NOT NULL,
    summary_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_marketdata_dataset_coverage_quality_status CHECK (((quality_status)::text = ANY ((ARRAY['OK'::character varying, 'GAP_DETECTED'::character varying, 'INCOMPLETE'::character varying, 'INVALID'::character varying])::text[]))),
    CONSTRAINT chk_marketdata_dataset_coverage_range CHECK ((range_end_time > range_start_time))
);

CREATE TABLE marketdata_datasets (
    dataset_id uuid NOT NULL,
    dataset_name character varying(255) NOT NULL,
    exchange_code character varying(32) NOT NULL,
    market_type character varying(16) NOT NULL,
    symbol character varying(64) NOT NULL,
    "interval" character varying(16) NOT NULL,
    start_time timestamp with time zone NOT NULL,
    end_time timestamp with time zone NOT NULL,
    status character varying(32) NOT NULL,
    quality_status character varying(32) NOT NULL,
    bar_count bigint DEFAULT 0 NOT NULL,
    gap_count bigint DEFAULT 0 NOT NULL,
    source character varying(64) NOT NULL,
    created_by character varying(512) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    request_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    CONSTRAINT chk_marketdata_datasets_quality_status CHECK (((quality_status)::text = ANY ((ARRAY['OK'::character varying, 'GAP_DETECTED'::character varying, 'INCOMPLETE'::character varying, 'INVALID'::character varying])::text[]))),
    CONSTRAINT chk_marketdata_datasets_scope CHECK ((((exchange_code)::text = ANY ((ARRAY['OKX'::character varying, 'BINANCE'::character varying])::text[])) AND ((market_type)::text = 'SPOT'::text) AND ((symbol)::text = ANY ((ARRAY['BTC-USDT'::character varying, 'ETH-USDT'::character varying, 'SOL-USDT'::character varying])::text[])) AND (("interval")::text = ANY ((ARRAY['1m'::character varying, '5m'::character varying, '15m'::character varying, '1h'::character varying, '4h'::character varying, '1d'::character varying])::text[])))),
    CONSTRAINT chk_marketdata_datasets_status CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'READY'::character varying, 'INVALID'::character varying, 'ARCHIVED'::character varying])::text[]))),
    CONSTRAINT chk_marketdata_datasets_time_range CHECK ((end_time > start_time))
);

CREATE TABLE marketdata_ingestion_jobs (
    job_id uuid NOT NULL,
    exchange_code character varying(32) NOT NULL,
    market_type character varying(16) NOT NULL,
    symbol character varying(64) NOT NULL,
    "interval" character varying(16) NOT NULL,
    start_time timestamp with time zone NOT NULL,
    end_time timestamp with time zone NOT NULL,
    status character varying(32) NOT NULL,
    source character varying(32) NOT NULL,
    created_by character varying(512) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    request_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    CONSTRAINT ck_marketdata_ingestion_jobs_exchange CHECK (((exchange_code)::text = ANY ((ARRAY['OKX'::character varying, 'BINANCE'::character varying])::text[]))),
    CONSTRAINT ck_marketdata_ingestion_jobs_interval CHECK ((("interval")::text = ANY ((ARRAY['1m'::character varying, '5m'::character varying, '15m'::character varying, '1h'::character varying, '4h'::character varying, '1d'::character varying])::text[]))),
    CONSTRAINT ck_marketdata_ingestion_jobs_market_type CHECK (((market_type)::text = 'SPOT'::text)),
    CONSTRAINT ck_marketdata_ingestion_jobs_range CHECK ((end_time >= start_time)),
    CONSTRAINT ck_marketdata_ingestion_jobs_status CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'RUNNING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying, 'PARTIAL'::character varying])::text[]))),
    CONSTRAINT ck_marketdata_ingestion_jobs_symbol CHECK (((symbol)::text = ANY ((ARRAY['BTC-USDT'::character varying, 'ETH-USDT'::character varying, 'SOL-USDT'::character varying])::text[])))
);

CREATE TABLE marketdata_ingestion_runs (
    run_id uuid NOT NULL,
    job_id uuid NOT NULL,
    status character varying(32) NOT NULL,
    started_at timestamp with time zone NOT NULL,
    finished_at timestamp with time zone,
    requested_start_time timestamp with time zone NOT NULL,
    requested_end_time timestamp with time zone NOT NULL,
    actual_start_time timestamp with time zone,
    actual_end_time timestamp with time zone,
    fetched_bars integer DEFAULT 0 NOT NULL,
    inserted_bars integer DEFAULT 0 NOT NULL,
    updated_bars integer DEFAULT 0 NOT NULL,
    skipped_bars integer DEFAULT 0 NOT NULL,
    error_message text,
    raw_summary_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_marketdata_ingestion_runs_counts CHECK (((fetched_bars >= 0) AND (inserted_bars >= 0) AND (updated_bars >= 0) AND (skipped_bars >= 0))),
    CONSTRAINT ck_marketdata_ingestion_runs_requested_range CHECK ((requested_end_time >= requested_start_time)),
    CONSTRAINT ck_marketdata_ingestion_runs_status CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying, 'PARTIAL'::character varying])::text[])))
);

CREATE TABLE operator_approvals (
    approval_id uuid NOT NULL,
    session_id uuid NOT NULL,
    scope_hash character varying(64) NOT NULL,
    release_digest character varying(64) NOT NULL,
    risk_limit_set_digest character varying(64) NOT NULL,
    approver_id bigint NOT NULL,
    approver_role character varying(64) NOT NULL,
    decision character varying(16) NOT NULL,
    reason text NOT NULL,
    approved_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    scope_schema_version character varying(64) NOT NULL,
    execution_scope_id uuid,
    CONSTRAINT chk_operator_approvals_decision CHECK (((decision)::text = ANY ((ARRAY['APPROVED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT chk_operator_approvals_digests CHECK ((((scope_hash)::text ~ '^[0-9a-f]{64}$'::text) AND ((release_digest)::text ~ '^[0-9a-f]{64}$'::text) AND ((risk_limit_set_digest)::text ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT chk_operator_approvals_expiry CHECK ((expires_at > approved_at)),
    CONSTRAINT chk_operator_approvals_reason CHECK (((btrim(reason) <> ''::text) AND (length(reason) <= 1024))),
    CONSTRAINT chk_operator_approvals_role CHECK (((approver_role)::text = 'LIVE_APPROVER'::text)),
    CONSTRAINT chk_operator_approvals_scope_version CHECK (((((scope_schema_version)::text = 'approval-scope.v1'::text) AND (execution_scope_id IS NULL)) OR (((scope_schema_version)::text = 'execution-scope.v1'::text) AND (execution_scope_id IS NOT NULL))))
);

CREATE TABLE operator_execution_authorities (
    authority_id uuid NOT NULL,
    owner_user_id bigint NOT NULL,
    exchange_account_id bigint NOT NULL,
    credential_reference_id bigint NOT NULL,
    instrument character varying(64) NOT NULL,
    side character varying(8) NOT NULL,
    order_type character varying(16) NOT NULL,
    max_notional numeric(38,8) NOT NULL,
    max_place_count integer NOT NULL,
    max_cancel_count integer NOT NULL,
    transfer_allowed boolean NOT NULL,
    withdraw_allowed boolean NOT NULL,
    valid_from timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    status character varying(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    canonical_digest character varying(64) NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    closed_at timestamp with time zone,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_operator_execution_authorities_counts CHECK (((max_place_count = 1) AND (max_cancel_count = 1))),
    CONSTRAINT chk_operator_execution_authorities_digest CHECK (((canonical_digest)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT chk_operator_execution_authorities_funding CHECK (((transfer_allowed = false) AND (withdraw_allowed = false))),
    CONSTRAINT chk_operator_execution_authorities_instrument CHECK (((instrument)::text ~ '^[A-Z0-9]{2,20}-USDT$'::text)),
    CONSTRAINT chk_operator_execution_authorities_lifecycle CHECK (((((status)::text = 'ACTIVE'::text) AND (closed_at IS NULL) AND (updated_at = created_at)) OR (((status)::text = ANY ((ARRAY['CLOSED'::character varying, 'EXPIRED'::character varying])::text[])) AND (closed_at IS NOT NULL) AND (updated_at = closed_at) AND (closed_at >= created_at)))),
    CONSTRAINT chk_operator_execution_authorities_notional CHECK (((max_notional > (0)::numeric) AND (max_notional <= 10.00000000))),
    CONSTRAINT chk_operator_execution_authorities_order_type CHECK (((order_type)::text = 'LIMIT'::text)),
    CONSTRAINT chk_operator_execution_authorities_owner CHECK ((owner_user_id = created_by)),
    CONSTRAINT chk_operator_execution_authorities_side CHECK (((side)::text = ANY ((ARRAY['BUY'::character varying, 'SELL'::character varying])::text[]))),
    CONSTRAINT chk_operator_execution_authorities_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'CLOSED'::character varying, 'EXPIRED'::character varying])::text[]))),
    CONSTRAINT chk_operator_execution_authorities_version CHECK ((version > 0)),
    CONSTRAINT chk_operator_execution_authorities_window CHECK (((expires_at > valid_from) AND (valid_from >= created_at)))
);

CREATE TABLE orders (
    order_id character varying(64) NOT NULL,
    account_id bigint NOT NULL,
    strategy_run_id character varying(64),
    symbol character varying(64) NOT NULL,
    client_order_id character varying(128) NOT NULL,
    side character varying(16) NOT NULL,
    type character varying(16) NOT NULL,
    price numeric(38,8),
    qty numeric(38,8) NOT NULL,
    status character varying(32) NOT NULL,
    reason character varying(255),
    trace_id character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    venue character varying(32) NOT NULL,
    external_order_id character varying(128),
    request_id character varying(64),
    dedup_key character varying(128),
    exchange_code character varying(32) NOT NULL,
    trade_env character varying(8) DEFAULT 'SIM'::character varying NOT NULL,
    exchange_order_id character varying(128),
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_orders_qty_positive CHECK ((qty > (0)::numeric)),
    CONSTRAINT chk_orders_side CHECK (((side)::text = ANY ((ARRAY['BUY'::character varying, 'SELL'::character varying])::text[]))),
    CONSTRAINT chk_orders_trade_env CHECK (((trade_env)::text = ANY ((ARRAY['SIM'::character varying, 'LIVE'::character varying])::text[]))),
    CONSTRAINT chk_orders_type CHECK (((type)::text = ANY ((ARRAY['MARKET'::character varying, 'LIMIT'::character varying])::text[]))),
    CONSTRAINT chk_orders_version_nonnegative CHECK ((version >= 0))
);

CREATE TABLE ordinary_order_cancel_finality (
    order_id character varying(64) NOT NULL,
    observed_order_version bigint NOT NULL,
    executed_quantity numeric(38,8) NOT NULL,
    CONSTRAINT ordinary_order_cancel_finality_executed_quantity_check CHECK ((executed_quantity >= (0)::numeric)),
    CONSTRAINT ordinary_order_cancel_finality_observed_order_version_check CHECK ((observed_order_version >= 0))
);

CREATE TABLE ordinary_place_authorities (
    order_id character varying(64) NOT NULL,
    state character varying(24) NOT NULL,
    decided_at timestamp with time zone,
    CONSTRAINT ck_ordinary_place_decision_time CHECK (((((state)::text = 'NOT_ARMED'::text) AND (decided_at IS NULL)) OR (((state)::text = ANY ((ARRAY['MAY_HAVE_ESCAPED'::character varying, 'REVOKED_BEFORE_SEND'::character varying])::text[])) AND (decided_at IS NOT NULL)))),
    CONSTRAINT ordinary_place_authorities_state_check CHECK (((state)::text = ANY ((ARRAY['NOT_ARMED'::character varying, 'MAY_HAVE_ESCAPED'::character varying, 'REVOKED_BEFORE_SEND'::character varying])::text[])))
);

CREATE TABLE paper_risk_check_results (
    risk_result_id character varying(64) NOT NULL,
    paper_run_id character varying(64) NOT NULL,
    check_type character varying(64) NOT NULL,
    status character varying(16) NOT NULL,
    severity character varying(16) NOT NULL,
    message character varying(512),
    input_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    result_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_risk_results_severity CHECK (((severity)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[]))),
    CONSTRAINT chk_risk_results_status CHECK (((status)::text = ANY ((ARRAY['PASSED'::character varying, 'REJECTED'::character varying, 'WARNING'::character varying])::text[])))
);

CREATE TABLE paper_run_alerts (
    alert_id character varying(64) NOT NULL,
    paper_run_id character varying(64) NOT NULL,
    alert_type character varying(64) NOT NULL,
    severity character varying(16) NOT NULL,
    status character varying(16) NOT NULL,
    title character varying(512) NOT NULL,
    message text,
    source character varying(128),
    event_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    acknowledged_by character varying(512),
    acknowledged_at timestamp with time zone,
    resolved_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_alerts_severity CHECK (((severity)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[]))),
    CONSTRAINT chk_alerts_status CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'ACKED'::character varying, 'RESOLVED'::character varying])::text[])))
);

CREATE TABLE paper_run_daily_reports (
    report_id character varying(64) NOT NULL,
    paper_run_id character varying(64) NOT NULL,
    report_date date NOT NULL,
    status character varying(16) NOT NULL,
    total_equity numeric(20,8),
    daily_pnl numeric(20,8),
    daily_return numeric(12,8),
    max_drawdown numeric(12,8),
    order_count integer DEFAULT 0 NOT NULL,
    trade_count integer DEFAULT 0 NOT NULL,
    alert_count integer DEFAULT 0 NOT NULL,
    risk_reject_count integer DEFAULT 0 NOT NULL,
    report_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    generated_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_daily_reports_status CHECK (((status)::text = ANY ((ARRAY['GENERATED'::character varying, 'PARTIAL'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE paper_run_heartbeats (
    heartbeat_id character varying(64) NOT NULL,
    paper_run_id character varying(64) NOT NULL,
    heartbeat_time timestamp with time zone NOT NULL,
    status character varying(16) NOT NULL,
    last_event_time timestamp with time zone,
    last_order_time timestamp with time zone,
    last_trade_time timestamp with time zone,
    lag_seconds bigint,
    summary_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_heartbeats_status CHECK (((status)::text = ANY ((ARRAY['OK'::character varying, 'LAGGING'::character varying, 'STOPPED'::character varying, 'UNKNOWN'::character varying])::text[])))
);

CREATE TABLE paper_run_recovery_events (
    recovery_event_id character varying(64) NOT NULL,
    paper_run_id character varying(64) NOT NULL,
    recovery_type character varying(64) NOT NULL,
    status character varying(16) NOT NULL,
    reason text,
    request_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    result_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    started_at timestamp with time zone NOT NULL,
    finished_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_recovery_events_status CHECK (((status)::text = ANY ((ARRAY['STARTED'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying, 'SKIPPED'::character varying])::text[]))),
    CONSTRAINT chk_recovery_events_type CHECK (((recovery_type)::text = ANY ((ARRAY['MANUAL_RECOVER'::character varying, 'RETRY_FAILED_STEP'::character varying, 'HEARTBEAT_LAG_RECOVER'::character varying, 'SCHEDULE_FIRE_RECOVER'::character varying])::text[])))
);

CREATE TABLE paper_run_schedule_fires (
    fire_id character varying(64) NOT NULL,
    schedule_id character varying(64) NOT NULL,
    paper_run_id character varying(64) NOT NULL,
    status character varying(16) NOT NULL,
    fired_at timestamp with time zone NOT NULL,
    finished_at timestamp with time zone,
    duration_ms bigint,
    result_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    error_message text,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_fires_status CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying, 'SKIPPED'::character varying])::text[])))
);

CREATE TABLE paper_run_schedules (
    schedule_id character varying(64) NOT NULL,
    paper_run_id character varying(64) NOT NULL,
    schedule_name character varying(256) NOT NULL,
    cron_expr character varying(128) NOT NULL,
    status character varying(16) NOT NULL,
    timezone character varying(64) DEFAULT 'UTC'::character varying NOT NULL,
    next_fire_time timestamp with time zone,
    last_fire_time timestamp with time zone,
    created_by character varying(512) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    request_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    CONSTRAINT chk_schedules_status CHECK (((status)::text = ANY ((ARRAY['ENABLED'::character varying, 'DISABLED'::character varying, 'PAUSED'::character varying])::text[])))
);

CREATE TABLE paper_run_stability_checks (
    stability_check_id character varying(64) NOT NULL,
    paper_run_id character varying(64) NOT NULL,
    check_window_start timestamp with time zone NOT NULL,
    check_window_end timestamp with time zone NOT NULL,
    status character varying(16) NOT NULL,
    uptime_ratio numeric(5,4) DEFAULT 0 NOT NULL,
    heartbeat_count integer DEFAULT 0 NOT NULL,
    alert_count integer DEFAULT 0 NOT NULL,
    failed_fire_count integer DEFAULT 0 NOT NULL,
    recovery_count integer DEFAULT 0 NOT NULL,
    report_count integer DEFAULT 0 NOT NULL,
    summary_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_stability_checks_status CHECK (((status)::text = ANY ((ARRAY['PASSED'::character varying, 'FAILED'::character varying, 'PARTIAL'::character varying])::text[]))),
    CONSTRAINT chk_stability_checks_uptime CHECK (((uptime_ratio >= (0)::numeric) AND (uptime_ratio <= (1)::numeric))),
    CONSTRAINT chk_stability_checks_window CHECK ((check_window_end > check_window_start))
);

CREATE TABLE paper_trading_orders (
    paper_order_id character varying(64) NOT NULL,
    paper_run_id character varying(64) NOT NULL,
    symbol character varying(64) NOT NULL,
    side character varying(8) NOT NULL,
    order_type character varying(16) NOT NULL,
    quantity numeric(36,18) NOT NULL,
    price numeric(36,18),
    status character varying(16) NOT NULL,
    reason character varying(256),
    raw_signal_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_paper_orders_side CHECK (((side)::text = ANY ((ARRAY['BUY'::character varying, 'SELL'::character varying])::text[]))),
    CONSTRAINT chk_paper_orders_status CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'FILLED'::character varying, 'CANCELED'::character varying, 'REJECTED'::character varying])::text[])))
);

CREATE TABLE paper_trading_positions (
    paper_position_id character varying(64) NOT NULL,
    paper_run_id character varying(64) NOT NULL,
    symbol character varying(64) NOT NULL,
    quantity numeric(36,18) DEFAULT 0 NOT NULL,
    avg_price numeric(36,18) DEFAULT 0 NOT NULL,
    unrealized_pnl numeric(36,18) DEFAULT 0 NOT NULL,
    realized_pnl numeric(36,18) DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL
);

CREATE TABLE paper_trading_runs (
    paper_run_id character varying(64) NOT NULL,
    publish_id character varying(64) NOT NULL,
    strategy_version_id character varying(128),
    status character varying(32) NOT NULL,
    trade_env character varying(16) NOT NULL,
    exchange_code character varying(32) NOT NULL,
    market_type character varying(16) NOT NULL,
    symbol character varying(64) NOT NULL,
    interval_code character varying(16) NOT NULL,
    started_at timestamp with time zone,
    stopped_at timestamp with time zone,
    publish_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    strategy_version_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    dataset_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    param_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    config_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_by character varying(128) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    canonical_account_id bigint,
    CONSTRAINT chk_paper_runs_status CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'RUNNING'::character varying, 'STOPPED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT chk_paper_runs_trade_env CHECK (((trade_env)::text = ANY ((ARRAY['SIM'::character varying, 'LIVE'::character varying])::text[])))
);

CREATE TABLE paper_trading_trades (
    paper_trade_id character varying(64) NOT NULL,
    paper_order_id character varying(64) NOT NULL,
    paper_run_id character varying(64) NOT NULL,
    symbol character varying(64) NOT NULL,
    side character varying(8) NOT NULL,
    quantity numeric(36,18) NOT NULL,
    price numeric(36,18) NOT NULL,
    fee numeric(36,18) DEFAULT 0 NOT NULL,
    traded_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_paper_trades_side CHECK (((side)::text = ANY ((ARRAY['BUY'::character varying, 'SELL'::character varying])::text[])))
);

CREATE TABLE position_curve_snapshots (
    position_snapshot_id character varying(64) NOT NULL,
    paper_run_id character varying(64) NOT NULL,
    symbol character varying(64) NOT NULL,
    snapshot_time timestamp with time zone NOT NULL,
    quantity numeric(36,18) NOT NULL,
    avg_price numeric(36,18) NOT NULL,
    mark_price numeric(36,18) NOT NULL,
    position_value numeric(36,18) NOT NULL,
    unrealized_pnl numeric(36,18) DEFAULT 0 NOT NULL,
    realized_pnl numeric(36,18) DEFAULT 0 NOT NULL,
    source character varying(32) NOT NULL,
    created_at timestamp with time zone NOT NULL
);

CREATE TABLE positions (
    id bigint NOT NULL DEFAULT nextval('positions_id_seq'::regclass),
    account_id bigint NOT NULL,
    symbol character varying(64) NOT NULL,
    qty numeric(38,8) DEFAULT 0 NOT NULL,
    available_qty numeric(38,8) DEFAULT 0 NOT NULL,
    frozen_qty numeric(38,8) DEFAULT 0 NOT NULL,
    avg_price numeric(38,8) DEFAULT 0 NOT NULL,
    trace_id character varying(64) NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public_market_captures (
    dataset_id uuid NOT NULL,
    observed_at timestamp with time zone NOT NULL,
    requested_start timestamp with time zone NOT NULL,
    requested_end timestamp with time zone NOT NULL,
    request_path text NOT NULL,
    raw_response text NOT NULL,
    raw_sha256 character(64) NOT NULL,
    normalized_sha256 character(64) NOT NULL,
    consumed_sha256 character(64) NOT NULL,
    replay_visibility_version character varying(64) NOT NULL,
    replay_visibility_source character varying(128) NOT NULL,
    rule_observed_at timestamp with time zone NOT NULL,
    rule_sha256 character(64) NOT NULL,
    rule_json jsonb NOT NULL,
    bars_json jsonb NOT NULL,
    bar_count integer NOT NULL,
    first_open_time timestamp with time zone NOT NULL,
    last_open_time timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    rule_request_path text,
    rule_raw_response text,
    rule_raw_sha256 character(64),
    CONSTRAINT chk_public_market_capture_range CHECK (((first_open_time >= requested_start) AND (last_open_time < requested_end) AND (last_open_time >= first_open_time))),
    CONSTRAINT chk_public_market_capture_replay_source CHECK (((replay_visibility_source)::text = 'EXPERIMENT_ASSUMPTION'::text)),
    CONSTRAINT chk_public_market_capture_window CHECK ((requested_start < requested_end)),
    CONSTRAINT chk_public_market_rule_raw_complete CHECK ((((rule_request_path IS NULL) AND (rule_raw_response IS NULL) AND (rule_raw_sha256 IS NULL)) OR ((rule_request_path IS NOT NULL) AND (rule_raw_response IS NOT NULL) AND (rule_raw_sha256 IS NOT NULL)))),
    CONSTRAINT public_market_captures_bar_count_check CHECK (((bar_count >= 1) AND (bar_count <= 500)))
);

CREATE TABLE reconciliation_scan_cursors (
    venue character varying(32) NOT NULL,
    cursor_created_at timestamp with time zone,
    cursor_order_id character varying(64),
    revision bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_reconciliation_cursor_key CHECK ((((cursor_created_at IS NULL) AND (cursor_order_id IS NULL)) OR ((cursor_created_at IS NOT NULL) AND (cursor_order_id IS NOT NULL)))),
    CONSTRAINT ck_reconciliation_cursor_venue CHECK ((btrim((venue)::text) <> ''::text)),
    CONSTRAINT reconciliation_scan_cursors_revision_check CHECK ((revision >= 0))
);

CREATE TABLE research_configs (
    research_config_id character varying(128) NOT NULL,
    source_strategy_id character varying(128) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    strategy_snapshot jsonb NOT NULL,
    config_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE'::character varying NOT NULL,
    archived_at timestamp with time zone,
    archived_by character varying(128),
    archive_reason text,
    CONSTRAINT chk_research_configs_archive_metadata CHECK (((((status)::text = 'ARCHIVED'::text) AND (archived_at IS NOT NULL)) OR (((status)::text <> 'ARCHIVED'::text) AND (archived_at IS NULL) AND (archived_by IS NULL) AND (archive_reason IS NULL)))),
    CONSTRAINT chk_research_configs_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'ARCHIVED'::character varying, 'DISABLED'::character varying])::text[])))
);

CREATE TABLE risk_events (
    risk_event_id character varying(64) NOT NULL,
    rule_id character varying(128),
    scope character varying(32) NOT NULL,
    scope_id character varying(128) NOT NULL,
    decision character varying(16) NOT NULL,
    reason character varying(255),
    severity character varying(16),
    trace_id character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE risk_limit_sets (
    risk_limit_set_id uuid NOT NULL,
    digest_schema_version character varying(64) NOT NULL,
    version integer NOT NULL,
    effective_scope character varying(64) NOT NULL,
    quote_currency character varying(16) NOT NULL,
    capital_cap numeric(38,8) NOT NULL,
    max_order_notional numeric(38,8) NOT NULL,
    max_symbol_position_notional numeric(38,8) NOT NULL,
    max_daily_realized_loss numeric(38,8) NOT NULL,
    max_daily_total_loss numeric(38,8) NOT NULL,
    max_open_orders integer NOT NULL,
    max_intraday_orders integer NOT NULL,
    symbol_allowlist text[] NOT NULL,
    order_type_allowlist text[] NOT NULL,
    max_session_duration_seconds integer NOT NULL,
    spread_limit_bps numeric(18,8) NOT NULL,
    slippage_limit_bps numeric(18,8) NOT NULL,
    max_market_data_age_ms integer NOT NULL,
    min_data_coverage_bps integer NOT NULL,
    required_data_source character varying(32) NOT NULL,
    data_quality_action character varying(16) NOT NULL,
    canonical_digest character varying(64) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_risk_limit_sets_amounts CHECK (((capital_cap > (0)::numeric) AND (capital_cap <= 10000.00000000) AND (max_order_notional > (0)::numeric) AND (max_order_notional <= capital_cap) AND (max_order_notional <= 1000.00000000) AND (max_symbol_position_notional > (0)::numeric) AND (max_symbol_position_notional <= capital_cap) AND (max_daily_realized_loss > (0)::numeric) AND (max_daily_realized_loss <= capital_cap) AND (max_daily_total_loss >= max_daily_realized_loss) AND (max_daily_total_loss <= capital_cap))),
    CONSTRAINT chk_risk_limit_sets_canonical_symbols CHECK (require_canonical_trading_symbols(symbol_allowlist)),
    CONSTRAINT chk_risk_limit_sets_counts CHECK ((((max_open_orders >= 1) AND (max_open_orders <= 20)) AND ((max_intraday_orders >= max_open_orders) AND (max_intraday_orders <= 200)) AND ((max_session_duration_seconds >= 60) AND (max_session_duration_seconds <= 14400)))),
    CONSTRAINT chk_risk_limit_sets_digest CHECK (((canonical_digest)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT chk_risk_limit_sets_market_data CHECK ((((spread_limit_bps >= (0)::numeric) AND (spread_limit_bps <= 1000.00000000)) AND ((slippage_limit_bps >= (0)::numeric) AND (slippage_limit_bps <= 1000.00000000)) AND ((max_market_data_age_ms >= 1) AND (max_market_data_age_ms <= 5000)) AND ((min_data_coverage_bps >= 1) AND (min_data_coverage_bps <= 10000)) AND ((required_data_source)::text = 'OKX_PRIMARY'::text) AND ((data_quality_action)::text = 'BLOCK'::text))),
    CONSTRAINT chk_risk_limit_sets_order_types CHECK ((order_type_allowlist = ARRAY['LIMIT'::text])),
    CONSTRAINT chk_risk_limit_sets_quote CHECK (((quote_currency)::text = 'USDT'::text)),
    CONSTRAINT chk_risk_limit_sets_schema CHECK (((digest_schema_version)::text = 'risk-limit-set.v1'::text)),
    CONSTRAINT chk_risk_limit_sets_scope CHECK (((effective_scope)::text = 'LIVE_SESSION_OKX_SPOT'::text)),
    CONSTRAINT chk_risk_limit_sets_symbols CHECK (((cardinality(symbol_allowlist) >= 1) AND (cardinality(symbol_allowlist) <= 2))),
    CONSTRAINT chk_risk_limit_sets_version CHECK ((version > 0))
);

CREATE TABLE roles (
    id bigint NOT NULL DEFAULT nextval('roles_id_seq'::regclass),
    role_code character varying(64) NOT NULL,
    description character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE scheduled_job_controls (
    job_key character varying(48) NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    fixed_delay_ms bigint NOT NULL,
    next_run_at timestamp with time zone,
    last_started_at timestamp with time zone,
    last_finished_at timestamp with time zone,
    last_status character varying(16) DEFAULT 'NEVER_RUN'::character varying NOT NULL,
    last_error_code character varying(64),
    active_run_id uuid,
    consecutive_failures integer DEFAULT 0 NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    updated_by character varying(128),
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_scheduled_job_next_run CHECK ((enabled OR (next_run_at IS NULL))),
    CONSTRAINT scheduled_job_controls_consecutive_failures_check CHECK ((consecutive_failures >= 0)),
    CONSTRAINT scheduled_job_controls_fixed_delay_ms_check CHECK (((fixed_delay_ms >= 1000) AND (fixed_delay_ms <= 86400000))),
    CONSTRAINT scheduled_job_controls_job_key_check CHECK (((job_key)::text = ANY ((ARRAY['CONTINUOUS_SIM_POLL'::character varying, 'PAPER_MATCHING'::character varying, 'STRATEGY_RECOVERY'::character varying, 'LEDGER_RECONCILIATION'::character varying, 'OKX_RECOVERY'::character varying, 'OKX_RECONCILIATION'::character varying, 'BINANCE_RECONCILIATION'::character varying, 'VALIDATION_EVIDENCE_REFRESH'::character varying])::text[]))),
    CONSTRAINT scheduled_job_controls_last_status_check CHECK (((last_status)::text = ANY ((ARRAY['NEVER_RUN'::character varying, 'RUNNING'::character varying, 'SUCCESS'::character varying, 'FAILED'::character varying, 'SKIPPED'::character varying])::text[]))),
    CONSTRAINT scheduled_job_controls_version_check CHECK ((version >= 0))
);

CREATE TABLE shadow_consistency_reports (
    id uuid NOT NULL,
    shadow_run_id uuid NOT NULL,
    paper_run_id character varying(64),
    comparison_status character varying(48) NOT NULL,
    metric_delta jsonb DEFAULT '{}'::jsonb NOT NULL,
    divergence_reasons jsonb DEFAULT '[]'::jsonb NOT NULL,
    limitations jsonb DEFAULT '[]'::jsonb NOT NULL,
    generated_at timestamp with time zone DEFAULT now() NOT NULL,
    trace_id character varying(128) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_shadow_consistency_reports_json CHECK (((jsonb_typeof(metric_delta) = 'object'::text) AND (jsonb_typeof(divergence_reasons) = 'array'::text) AND (jsonb_typeof(limitations) = 'array'::text))),
    CONSTRAINT chk_shadow_consistency_reports_status CHECK (((comparison_status)::text = ANY ((ARRAY['CONSISTENT'::character varying, 'DIVERGED'::character varying, 'NOT_COMPARABLE'::character varying, 'PARTIAL'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE shadow_run_events (
    id uuid NOT NULL,
    shadow_run_id uuid NOT NULL,
    event_type character varying(64) NOT NULL,
    from_status character varying(32),
    to_status character varying(32),
    reason_code character varying(128),
    message text,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    request_id character varying(128),
    trace_id character varying(128) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_shadow_run_events_event_type CHECK (((event_type)::text = ANY ((ARRAY['CREATED'::character varying, 'PRECHECK_STARTED'::character varying, 'PRECHECK_PASSED'::character varying, 'PRECHECK_BLOCKED'::character varying, 'RUN_STARTED'::character varying, 'STOP_REQUESTED'::character varying, 'STOPPED'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'ILLEGAL_STATE_TRANSITION_ATTEMPT'::character varying, 'SNAPSHOT_CAPTURED'::character varying, 'CONSISTENCY_REPORT_GENERATED'::character varying])::text[]))),
    CONSTRAINT chk_shadow_run_events_from_status CHECK (((from_status IS NULL) OR ((from_status)::text = ANY ((ARRAY['CREATED'::character varying, 'PRECHECKING'::character varying, 'READY'::character varying, 'RUNNING'::character varying, 'STOP_REQUESTED'::character varying, 'STOPPED'::character varying, 'COMPLETED'::character varying, 'BLOCKED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[])))),
    CONSTRAINT chk_shadow_run_events_metadata_json CHECK ((jsonb_typeof(metadata) = 'object'::text)),
    CONSTRAINT chk_shadow_run_events_to_status CHECK (((to_status IS NULL) OR ((to_status)::text = ANY ((ARRAY['CREATED'::character varying, 'PRECHECKING'::character varying, 'READY'::character varying, 'RUNNING'::character varying, 'STOP_REQUESTED'::character varying, 'STOPPED'::character varying, 'COMPLETED'::character varying, 'BLOCKED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[]))))
);

CREATE TABLE shadow_run_snapshots (
    id uuid NOT NULL,
    shadow_run_id uuid NOT NULL,
    snapshot_type character varying(48) NOT NULL,
    sequence_no integer NOT NULL,
    source character varying(128) NOT NULL,
    schema_version character varying(32) NOT NULL,
    checksum character varying(128) NOT NULL,
    payload jsonb NOT NULL,
    captured_at timestamp with time zone NOT NULL,
    trace_id character varying(128) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_shadow_run_snapshots_checksum CHECK ((length(TRIM(BOTH FROM checksum)) > 0)),
    CONSTRAINT chk_shadow_run_snapshots_payload_json CHECK ((jsonb_typeof(payload) = 'object'::text)),
    CONSTRAINT chk_shadow_run_snapshots_sequence CHECK ((sequence_no >= 0)),
    CONSTRAINT chk_shadow_run_snapshots_type CHECK (((snapshot_type)::text = ANY ((ARRAY['INPUT_MARKETDATA'::character varying, 'STRATEGY_DECISION'::character varying, 'RISK_PREFLIGHT'::character varying, 'ORDER_INTENT_PREVIEW'::character varying])::text[])))
);

CREATE TABLE shadow_runs (
    id uuid NOT NULL,
    strategy_version_id character varying(128) NOT NULL,
    dataset_id uuid NOT NULL,
    evaluation_id character varying(128),
    publish_id character varying(128),
    paper_run_id character varying(64),
    status character varying(32) NOT NULL,
    window_start timestamp with time zone,
    window_end timestamp with time zone,
    side_effect_policy jsonb DEFAULT '{}'::jsonb NOT NULL,
    no_order_submission boolean DEFAULT true NOT NULL,
    no_credential_access boolean DEFAULT true NOT NULL,
    no_private_endpoint boolean DEFAULT true NOT NULL,
    no_ledger_mutation boolean DEFAULT true NOT NULL,
    no_account_mutation boolean DEFAULT true NOT NULL,
    no_external_private_io boolean DEFAULT true NOT NULL,
    authorization_boundary character varying(64) DEFAULT 'DIAGNOSTIC_ONLY'::character varying NOT NULL,
    request_id character varying(128),
    idempotency_key character varying(160) NOT NULL,
    trace_id character varying(128) NOT NULL,
    blockers jsonb DEFAULT '[]'::jsonb NOT NULL,
    warnings jsonb DEFAULT '[]'::jsonb NOT NULL,
    next_steps jsonb DEFAULT '[]'::jsonb NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    started_at timestamp with time zone,
    stopped_at timestamp with time zone,
    completed_at timestamp with time zone,
    artifact_digest character varying(64),
    CONSTRAINT chk_shadow_runs_artifact_digest_sha256 CHECK (((artifact_digest IS NULL) OR ((artifact_digest)::text ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT chk_shadow_runs_artifact_requires_publish CHECK (((artifact_digest IS NULL) OR (publish_id IS NOT NULL))),
    CONSTRAINT chk_shadow_runs_authorization_boundary CHECK (((authorization_boundary)::text = ANY ((ARRAY['DIAGNOSTIC_ONLY'::character varying, 'REVIEW_ONLY'::character varying, 'REPLAY_ONLY'::character varying])::text[]))),
    CONSTRAINT chk_shadow_runs_json_arrays CHECK (((jsonb_typeof(blockers) = 'array'::text) AND (jsonb_typeof(warnings) = 'array'::text) AND (jsonb_typeof(next_steps) = 'array'::text))),
    CONSTRAINT chk_shadow_runs_no_side_effects CHECK (((no_order_submission IS TRUE) AND (no_credential_access IS TRUE) AND (no_private_endpoint IS TRUE) AND (no_ledger_mutation IS TRUE) AND (no_account_mutation IS TRUE) AND (no_external_private_io IS TRUE))),
    CONSTRAINT chk_shadow_runs_side_effect_policy_json CHECK ((jsonb_typeof(side_effect_policy) = 'object'::text)),
    CONSTRAINT chk_shadow_runs_status CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'PRECHECKING'::character varying, 'READY'::character varying, 'RUNNING'::character varying, 'STOP_REQUESTED'::character varying, 'STOPPED'::character varying, 'COMPLETED'::character varying, 'BLOCKED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT chk_shadow_runs_version CHECK ((version >= 0)),
    CONSTRAINT chk_shadow_runs_window CHECK (((window_start IS NULL) OR (window_end IS NULL) OR (window_end >= window_start)))
);

CREATE TABLE sim_orders (
    sim_order_id character varying(128) NOT NULL,
    backtest_run_id character varying(128) NOT NULL,
    symbol character varying(64) NOT NULL,
    side character varying(16) NOT NULL,
    order_type character varying(32) NOT NULL,
    requested_quantity numeric(36,18) NOT NULL,
    requested_price numeric(36,18) NOT NULL,
    status character varying(32) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    filled_at timestamp with time zone,
    reject_reason text,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_sim_orders_status CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'FILLED'::character varying, 'REJECTED'::character varying])::text[])))
);

CREATE TABLE sim_pnl_snapshots (
    sim_pnl_snapshot_id character varying(128) NOT NULL,
    backtest_run_id character varying(128) NOT NULL,
    snapshot_time timestamp with time zone NOT NULL,
    cash_balance numeric(36,18) NOT NULL,
    position_market_value numeric(36,18) NOT NULL,
    realized_pnl numeric(36,18) NOT NULL,
    unrealized_pnl numeric(36,18) NOT NULL,
    total_fee numeric(36,18) NOT NULL,
    total_slippage numeric(36,18) NOT NULL,
    equity numeric(36,18) NOT NULL,
    net_pnl numeric(36,18) NOT NULL,
    created_at timestamp with time zone NOT NULL
);

CREATE TABLE sim_positions (
    sim_position_id character varying(128) NOT NULL,
    backtest_run_id character varying(128) NOT NULL,
    symbol character varying(64) NOT NULL,
    quantity numeric(36,18) NOT NULL,
    average_entry_price numeric(36,18) NOT NULL,
    realized_pnl numeric(36,18) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

CREATE TABLE sim_trades (
    sim_trade_id character varying(128) NOT NULL,
    sim_order_id character varying(128) NOT NULL,
    backtest_run_id character varying(128) NOT NULL,
    symbol character varying(64) NOT NULL,
    side character varying(16) NOT NULL,
    quantity numeric(36,18) NOT NULL,
    trade_price numeric(36,18) NOT NULL,
    fee_amount numeric(36,18) NOT NULL,
    slippage_amount numeric(36,18) NOT NULL,
    traded_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

CREATE TABLE strategy_definitions (
    strategy_id character varying(128) NOT NULL,
    strategy_code character varying(128) NOT NULL,
    strategy_name character varying(255) NOT NULL,
    strategy_type character varying(64) NOT NULL,
    exchange_code character varying(32) NOT NULL,
    account_id bigint NOT NULL,
    trade_env character varying(8) DEFAULT 'SIM'::character varying NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    config_snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_strategy_definitions_trade_env CHECK (((trade_env)::text = ANY ((ARRAY['SIM'::character varying, 'LIVE'::character varying])::text[]))),
    CONSTRAINT chk_strategy_definitions_version CHECK ((version > 0))
);

CREATE TABLE strategy_release_admission_state (
    publish_record_id character varying(128) NOT NULL,
    admission_revision bigint DEFAULT 0 NOT NULL,
    guard_schema_version integer DEFAULT 1 NOT NULL,
    release_artifact_digest character varying(64),
    manifest_fingerprint character varying(64),
    manifest_schema_version character varying(64),
    identity_bound_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_strategy_release_admission_revision CHECK ((admission_revision >= 0)),
    CONSTRAINT chk_strategy_release_artifact_digest_sha256 CHECK (((release_artifact_digest IS NULL) OR ((release_artifact_digest)::text ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT chk_strategy_release_guard_schema_version CHECK ((guard_schema_version = 1)),
    CONSTRAINT chk_strategy_release_identity_quartet CHECK ((((release_artifact_digest IS NULL) AND (manifest_fingerprint IS NULL) AND (manifest_schema_version IS NULL) AND (identity_bound_at IS NULL)) OR ((release_artifact_digest IS NOT NULL) AND (manifest_fingerprint IS NOT NULL) AND (manifest_schema_version IS NOT NULL) AND (identity_bound_at IS NOT NULL)))),
    CONSTRAINT chk_strategy_release_manifest_fingerprint_sha256 CHECK (((manifest_fingerprint IS NULL) OR ((manifest_fingerprint)::text ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT chk_strategy_release_manifest_schema_version CHECK (((manifest_schema_version IS NULL) OR ((manifest_schema_version)::text = 'strategy-release-manifest.v1'::text)))
);

CREATE TABLE strategy_run_dispatch_work (
    strategy_run_id character varying(64) NOT NULL,
    work_schema_version smallint NOT NULL,
    definition_version integer NOT NULL,
    account_id bigint NOT NULL,
    client_order_id character varying(128) NOT NULL,
    symbol character varying(64) NOT NULL,
    side character varying(16) NOT NULL,
    order_type character varying(16) NOT NULL,
    quantity numeric(38,8) NOT NULL,
    price numeric(38,8),
    time_in_force character varying(16) NOT NULL,
    effective_quantity numeric(38,8),
    effective_price numeric(38,8),
    normalization_rejection character varying(128),
    CONSTRAINT ck_strategy_effective_parameters CHECK ((((effective_quantity IS NULL) AND (effective_price IS NULL) AND (normalization_rejection IS NULL)) OR (((normalization_rejection)::text ~ '^[A-Z0-9_]{1,128}$'::text) AND (effective_quantity IS NULL) AND (effective_price IS NULL)) OR ((normalization_rejection IS NULL) AND (effective_quantity IS NOT NULL) AND (effective_quantity > (0)::numeric) AND (effective_quantity <= quantity) AND (effective_quantity < 'NaN'::numeric) AND (((price IS NULL) AND (effective_price IS NULL)) OR ((price IS NOT NULL) AND (effective_price IS NOT NULL) AND (effective_price > (0)::numeric) AND (effective_price <= price)))))),
    CONSTRAINT ck_strategy_work_limit_price CHECK ((((order_type)::text <> 'LIMIT'::text) OR ((price IS NOT NULL) AND (price > (0)::numeric)))),
    CONSTRAINT ck_strategy_work_tif CHECK (((((order_type)::text = 'MARKET'::text) AND ((time_in_force)::text = 'IOC'::text)) OR (((order_type)::text = 'LIMIT'::text) AND ((time_in_force)::text = 'GTC'::text)))),
    CONSTRAINT strategy_run_dispatch_work_client_order_id_check CHECK ((btrim((client_order_id)::text) <> ''::text)),
    CONSTRAINT strategy_run_dispatch_work_definition_version_check CHECK ((definition_version > 0)),
    CONSTRAINT strategy_run_dispatch_work_order_type_check CHECK (((order_type)::text = ANY ((ARRAY['MARKET'::character varying, 'LIMIT'::character varying])::text[]))),
    CONSTRAINT strategy_run_dispatch_work_price_check CHECK ((price < 'NaN'::numeric)),
    CONSTRAINT strategy_run_dispatch_work_quantity_check CHECK (((quantity > (0)::numeric) AND (quantity < 'NaN'::numeric))),
    CONSTRAINT strategy_run_dispatch_work_side_check CHECK (((side)::text = ANY ((ARRAY['BUY'::character varying, 'SELL'::character varying])::text[]))),
    CONSTRAINT strategy_run_dispatch_work_symbol_check CHECK ((btrim((symbol)::text) <> ''::text)),
    CONSTRAINT strategy_run_dispatch_work_work_schema_version_check CHECK ((work_schema_version = 1))
);

CREATE TABLE strategy_run_recovery_scan_cursor (
    cursor_id smallint NOT NULL,
    last_started_at timestamp with time zone,
    last_run_id character varying(64),
    CONSTRAINT strategy_run_recovery_scan_cursor_check CHECK (((last_started_at IS NULL) = (last_run_id IS NULL))),
    CONSTRAINT strategy_run_recovery_scan_cursor_cursor_id_check CHECK ((cursor_id = 1))
);

CREATE TABLE strategy_runs (
    strategy_run_id character varying(64) NOT NULL,
    strategy_id character varying(128) NOT NULL,
    account_id bigint NOT NULL,
    status character varying(32) NOT NULL,
    started_at timestamp with time zone NOT NULL,
    finished_at timestamp with time zone,
    trace_id character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    trigger_type character varying(32) DEFAULT 'MANUAL'::character varying NOT NULL,
    exchange_code character varying(32) NOT NULL,
    trade_env character varying(8) DEFAULT 'SIM'::character varying NOT NULL,
    config_snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    request_id character varying(64),
    error_message text,
    admission_schedule_id character varying(128),
    admission_due_at timestamp with time zone,
    CONSTRAINT chk_strategy_run_admission_pair CHECK ((((admission_schedule_id IS NULL) AND (admission_due_at IS NULL)) OR ((admission_schedule_id IS NOT NULL) AND (admission_due_at IS NOT NULL) AND ((trigger_type)::text = 'SCHEDULER'::text)))),
    CONSTRAINT chk_strategy_runs_trade_env CHECK (((trade_env)::text = ANY ((ARRAY['SIM'::character varying, 'LIVE'::character varying])::text[]))),
    CONSTRAINT chk_strategy_runs_trigger_type CHECK (((trigger_type)::text = ANY ((ARRAY['MANUAL'::character varying, 'SCHEDULER'::character varying, 'RECOVERY'::character varying])::text[])))
);

CREATE TABLE strategy_schedules (
    schedule_job_id character varying(128) NOT NULL,
    strategy_id character varying(128) NOT NULL,
    schedule_type character varying(32) DEFAULT 'CRON'::character varying NOT NULL,
    cron_expr character varying(128),
    timezone character varying(64) DEFAULT 'UTC'::character varying NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    window_config jsonb DEFAULT '{}'::jsonb NOT NULL,
    dedup_scope character varying(32) DEFAULT 'SCHEDULE_WINDOW'::character varying NOT NULL,
    exchange_code character varying(32) NOT NULL,
    account_id bigint NOT NULL,
    trade_env character varying(8) DEFAULT 'SIM'::character varying NOT NULL,
    last_triggered_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_strategy_schedules_dedup_scope CHECK (((dedup_scope)::text = ANY ((ARRAY['SCHEDULE_WINDOW'::character varying, 'REQUEST'::character varying, 'STRATEGY'::character varying])::text[]))),
    CONSTRAINT chk_strategy_schedules_trade_env CHECK (((trade_env)::text = ANY ((ARRAY['SIM'::character varying, 'LIVE'::character varying])::text[]))),
    CONSTRAINT chk_strategy_schedules_type CHECK (((schedule_type)::text = ANY ((ARRAY['CRON'::character varying, 'INTERVAL'::character varying, 'MANUAL'::character varying])::text[])))
);

CREATE TABLE strategy_sim_decisions (
    decision_id character varying(64) NOT NULL,
    paper_run_id character varying(64) NOT NULL,
    canonical_account_id bigint NOT NULL,
    strategy_version_id character varying(128) NOT NULL,
    strategy_checksum character varying(64) NOT NULL,
    signal_open_time timestamp with time zone NOT NULL,
    signal_available_at timestamp with time zone NOT NULL,
    execution_open_time timestamp with time zone,
    input_sha256 character varying(64) NOT NULL,
    execution_bar_sha256 character varying(64),
    input_snapshot_json jsonb NOT NULL,
    target_exposure numeric(10,8) NOT NULL,
    status character varying(24) NOT NULL,
    reason character varying(64) NOT NULL,
    side character varying(4),
    quantity numeric(38,8),
    execution_price numeric(38,8),
    fee_rate numeric(12,8) NOT NULL,
    slippage_bps numeric(12,4) NOT NULL,
    strategy_run_id character varying(64),
    order_id character varying(64),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_strategy_sim_decision_cost CHECK (((fee_rate >= (0)::numeric) AND (fee_rate < (1)::numeric) AND (slippage_bps >= (0)::numeric) AND (slippage_bps < (10000)::numeric))),
    CONSTRAINT chk_strategy_sim_decision_identity CHECK ((((strategy_checksum)::text ~ '^[0-9a-f]{64}$'::text) AND ((input_sha256)::text ~ '^[0-9a-f]{64}$'::text) AND ((execution_bar_sha256 IS NULL) OR ((execution_bar_sha256)::text ~ '^[0-9a-f]{64}$'::text)))),
    CONSTRAINT chk_strategy_sim_decision_order CHECK (((((status)::text = 'ACCEPTED'::text) AND (strategy_run_id IS NOT NULL) AND (order_id IS NOT NULL) AND ((side)::text = ANY ((ARRAY['BUY'::character varying, 'SELL'::character varying])::text[])) AND (quantity > (0)::numeric) AND (execution_price > (0)::numeric)) OR (((status)::text = 'RISK_REJECTED'::text) AND (((strategy_run_id IS NULL) AND (order_id IS NULL)) OR ((strategy_run_id IS NOT NULL) AND (order_id IS NOT NULL)))) OR (((status)::text = ANY ((ARRAY['NO_SIGNAL'::character varying, 'NOT_TRADABLE'::character varying])::text[])) AND (strategy_run_id IS NULL) AND (order_id IS NULL)))),
    CONSTRAINT chk_strategy_sim_decision_status CHECK (((status)::text = ANY ((ARRAY['NO_SIGNAL'::character varying, 'NOT_TRADABLE'::character varying, 'RISK_REJECTED'::character varying, 'ACCEPTED'::character varying])::text[]))),
    CONSTRAINT chk_strategy_sim_decision_target CHECK (((target_exposure >= (0)::numeric) AND (target_exposure <= (1)::numeric))),
    CONSTRAINT chk_strategy_sim_decision_time CHECK (((signal_available_at >= signal_open_time) AND ((execution_open_time IS NULL) OR (execution_open_time > signal_open_time)) AND (((status)::text <> 'ACCEPTED'::text) OR ((execution_open_time > signal_available_at) AND (execution_bar_sha256 IS NOT NULL))))),
    CONSTRAINT chk_strategy_sim_execution_event CHECK ((((execution_open_time IS NULL) AND (execution_bar_sha256 IS NULL) AND (execution_price IS NULL)) OR ((execution_open_time IS NOT NULL) AND (execution_bar_sha256 IS NOT NULL) AND (execution_price > (0)::numeric))))
);

CREATE TABLE strategy_versions (
    strategy_version_id character varying(128) NOT NULL,
    strategy_code character varying(128) NOT NULL,
    version integer NOT NULL,
    version_name character varying(128) NOT NULL,
    status character varying(32) DEFAULT 'DRAFT'::character varying NOT NULL,
    param_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    config_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    source_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    checksum character varying(128) NOT NULL,
    created_by character varying(512) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_strategy_versions_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'ARCHIVED'::character varying])::text[]))),
    CONSTRAINT chk_strategy_versions_version CHECK ((version > 0))
);

CREATE TABLE trade_replay_records (
    replay_record_id character varying(64) NOT NULL,
    paper_run_id character varying(64) NOT NULL,
    paper_order_id character varying(64),
    paper_trade_id character varying(64),
    replay_time timestamp with time zone NOT NULL,
    event_type character varying(32) NOT NULL,
    symbol character varying(64) NOT NULL,
    side character varying(8),
    price numeric(36,18),
    quantity numeric(36,18),
    reason character varying(256),
    decision_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    risk_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    market_snapshot_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL
);

CREATE TABLE trades (
    trade_id character varying(64) NOT NULL,
    order_id character varying(64) NOT NULL,
    account_id bigint NOT NULL,
    symbol character varying(64) NOT NULL,
    exchange character varying(32),
    exchange_trade_id character varying(128),
    price numeric(38,8) NOT NULL,
    qty numeric(38,8) NOT NULL,
    fee numeric(38,8) DEFAULT 0,
    fee_currency character varying(32),
    trace_id character varying(64) NOT NULL,
    ts timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    external_order_id character varying(128),
    strategy_run_id character varying(64),
    exchange_code character varying(32) NOT NULL,
    trade_env character varying(8) DEFAULT 'SIM'::character varying NOT NULL,
    exchange_order_id character varying(128),
    CONSTRAINT chk_trades_price_positive CHECK ((price > (0)::numeric)),
    CONSTRAINT chk_trades_qty_positive CHECK ((qty > (0)::numeric)),
    CONSTRAINT chk_trades_trade_env CHECK (((trade_env)::text = ANY ((ARRAY['SIM'::character varying, 'LIVE'::character varying])::text[])))
);

CREATE TABLE user_roles (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    granted_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE users (
    id bigint NOT NULL DEFAULT nextval('users_id_seq'::regclass),
    username character varying(64) NOT NULL,
    password_hash character varying(255) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE validation_review_cases (
    id uuid NOT NULL,
    tenant_key character varying(64) NOT NULL,
    owner_id bigint NOT NULL,
    evidence_type character varying(64) NOT NULL,
    evidence_source character varying(256) NOT NULL,
    evidence_anchor jsonb NOT NULL,
    severity character varying(16) NOT NULL,
    state character varying(32) NOT NULL,
    title character varying(256) NOT NULL,
    summary text,
    version bigint DEFAULT 0 NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    acknowledged_by bigint,
    acknowledged_at timestamp with time zone,
    escalated_by bigint,
    escalated_at timestamp with time zone,
    resolved_by bigint,
    resolved_at timestamp with time zone,
    closed_by bigint,
    closed_at timestamp with time zone,
    retention_until timestamp with time zone,
    CONSTRAINT chk_validation_review_cases_actor_time_pairs CHECK ((((acknowledged_by IS NULL) = (acknowledged_at IS NULL)) AND ((escalated_by IS NULL) = (escalated_at IS NULL)) AND ((resolved_by IS NULL) = (resolved_at IS NULL)) AND ((closed_by IS NULL) = (closed_at IS NULL)))),
    CONSTRAINT chk_validation_review_cases_evidence_anchor CHECK ((jsonb_typeof(evidence_anchor) = 'object'::text)),
    CONSTRAINT chk_validation_review_cases_evidence_source CHECK ((btrim((evidence_source)::text) <> ''::text)),
    CONSTRAINT chk_validation_review_cases_evidence_type CHECK ((btrim((evidence_type)::text) <> ''::text)),
    CONSTRAINT chk_validation_review_cases_severity CHECK (((severity)::text = ANY ((ARRAY['INFO'::character varying, 'WARNING'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[]))),
    CONSTRAINT chk_validation_review_cases_state CHECK (((state)::text = ANY ((ARRAY['OPEN'::character varying, 'ACKNOWLEDGED'::character varying, 'ESCALATED'::character varying, 'RESOLVED'::character varying, 'CLOSED'::character varying])::text[]))),
    CONSTRAINT chk_validation_review_cases_state_times CHECK (((((state)::text <> 'OPEN'::text) OR ((acknowledged_at IS NULL) AND (escalated_at IS NULL) AND (resolved_at IS NULL) AND (closed_at IS NULL))) AND (((state)::text <> 'ACKNOWLEDGED'::text) OR ((acknowledged_at IS NOT NULL) AND (escalated_at IS NULL) AND (resolved_at IS NULL) AND (closed_at IS NULL))) AND (((state)::text <> 'ESCALATED'::text) OR ((escalated_at IS NOT NULL) AND (resolved_at IS NULL) AND (closed_at IS NULL))) AND (((state)::text <> 'RESOLVED'::text) OR ((resolved_at IS NOT NULL) AND (closed_at IS NULL) AND ((acknowledged_at IS NOT NULL) OR (escalated_at IS NOT NULL)))) AND (((state)::text <> 'CLOSED'::text) OR ((resolved_at IS NOT NULL) AND (closed_at IS NOT NULL))))),
    CONSTRAINT chk_validation_review_cases_tenant_key CHECK ((btrim((tenant_key)::text) <> ''::text)),
    CONSTRAINT chk_validation_review_cases_time_order CHECK (((updated_at >= created_at) AND ((acknowledged_at IS NULL) OR (acknowledged_at >= created_at)) AND ((escalated_at IS NULL) OR (escalated_at >= COALESCE(acknowledged_at, created_at))) AND ((resolved_at IS NULL) OR (resolved_at >= COALESCE(escalated_at, acknowledged_at, created_at))) AND ((closed_at IS NULL) OR (closed_at >= resolved_at)) AND ((retention_until IS NULL) OR (retention_until >= COALESCE(closed_at, created_at))))),
    CONSTRAINT chk_validation_review_cases_title CHECK ((btrim((title)::text) <> ''::text)),
    CONSTRAINT chk_validation_review_cases_version CHECK ((version >= 0))
);

CREATE TABLE validation_review_events (
    id uuid NOT NULL,
    review_case_id uuid NOT NULL,
    tenant_key character varying(64) NOT NULL,
    event_type character varying(32) NOT NULL,
    from_state character varying(32),
    to_state character varying(32) NOT NULL,
    case_version bigint NOT NULL,
    actor_id bigint NOT NULL,
    idempotency_key character varying(128) NOT NULL,
    request_hash character varying(128) NOT NULL,
    request_id character varying(128),
    trace_id character varying(128) NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_validation_review_events_case_version CHECK ((case_version > 0)),
    CONSTRAINT chk_validation_review_events_event_type CHECK (((event_type)::text = ANY ((ARRAY['ACKNOWLEDGED'::character varying, 'ESCALATED'::character varying, 'RESOLVED'::character varying, 'CLOSED'::character varying])::text[]))),
    CONSTRAINT chk_validation_review_events_from_state CHECK (((from_state IS NULL) OR ((from_state)::text = ANY ((ARRAY['OPEN'::character varying, 'ACKNOWLEDGED'::character varying, 'ESCALATED'::character varying, 'RESOLVED'::character varying, 'CLOSED'::character varying])::text[])))),
    CONSTRAINT chk_validation_review_events_idempotency_key CHECK ((btrim((idempotency_key)::text) <> ''::text)),
    CONSTRAINT chk_validation_review_events_legal_transition CHECK (((((from_state)::text = 'OPEN'::text) AND ((to_state)::text = ANY ((ARRAY['ACKNOWLEDGED'::character varying, 'ESCALATED'::character varying])::text[]))) OR (((from_state)::text = 'ACKNOWLEDGED'::text) AND ((to_state)::text = ANY ((ARRAY['ESCALATED'::character varying, 'RESOLVED'::character varying])::text[]))) OR (((from_state)::text = 'ESCALATED'::text) AND ((to_state)::text = 'RESOLVED'::text)) OR (((from_state)::text = 'RESOLVED'::text) AND ((to_state)::text = 'CLOSED'::text)))),
    CONSTRAINT chk_validation_review_events_metadata CHECK ((jsonb_typeof(metadata) = 'object'::text)),
    CONSTRAINT chk_validation_review_events_request_hash CHECK ((btrim((request_hash)::text) <> ''::text)),
    CONSTRAINT chk_validation_review_events_tenant_key CHECK ((btrim((tenant_key)::text) <> ''::text)),
    CONSTRAINT chk_validation_review_events_to_state CHECK (((to_state)::text = ANY ((ARRAY['ACKNOWLEDGED'::character varying, 'ESCALATED'::character varying, 'RESOLVED'::character varying, 'CLOSED'::character varying])::text[]))),
    CONSTRAINT chk_validation_review_events_trace_id CHECK ((btrim((trace_id)::text) <> ''::text)),
    CONSTRAINT chk_validation_review_events_transition_shape CHECK (((from_state IS NOT NULL) AND ((from_state)::text <> (to_state)::text) AND ((event_type)::text = (to_state)::text)))
);

-- 序列归属与表间完整性约束。

ALTER SEQUENCE account_snapshots_snapshot_id_seq OWNED BY account_snapshots.snapshot_id;

ALTER SEQUENCE accounts_account_id_seq OWNED BY accounts.account_id;

ALTER SEQUENCE audit_logs_id_seq OWNED BY audit_logs.id;

ALTER SEQUENCE credential_audit_logs_credential_audit_log_id_seq OWNED BY credential_audit_logs.credential_audit_log_id;

ALTER SEQUENCE exchange_account_credentials_credential_id_seq OWNED BY exchange_account_credentials.credential_id;

ALTER SEQUENCE exchange_accounts_exchange_account_id_seq OWNED BY exchange_accounts.exchange_account_id;

ALTER SEQUENCE instrument_catalog_instrument_id_seq OWNED BY instrument_catalog.instrument_id;

ALTER SEQUENCE ledger_events_ledger_event_id_seq OWNED BY ledger_events.ledger_event_id;

ALTER SEQUENCE marketdata_bars_marketdata_bar_id_seq OWNED BY marketdata_bars.marketdata_bar_id;

ALTER SEQUENCE positions_id_seq OWNED BY positions.id;

ALTER SEQUENCE roles_id_seq OWNED BY roles.id;

ALTER SEQUENCE users_id_seq OWNED BY users.id;

ALTER TABLE ONLY account_snapshots
    ADD CONSTRAINT account_snapshots_pkey PRIMARY KEY (snapshot_id);

ALTER TABLE ONLY accounts
    ADD CONSTRAINT accounts_pkey PRIMARY KEY (account_id);

ALTER TABLE ONLY audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY backtest_configs
    ADD CONSTRAINT backtest_configs_pkey PRIMARY KEY (backtest_config_id);

ALTER TABLE ONLY backtest_eval_reports
    ADD CONSTRAINT backtest_eval_reports_pkey PRIMARY KEY (eval_report_id);

ALTER TABLE ONLY backtest_publish_records
    ADD CONSTRAINT backtest_publish_records_pkey PRIMARY KEY (publish_record_id);

ALTER TABLE ONLY backtest_runs
    ADD CONSTRAINT backtest_runs_pkey PRIMARY KEY (backtest_run_id);

ALTER TABLE ONLY continuous_sim_bars
    ADD CONSTRAINT continuous_sim_bars_pkey PRIMARY KEY (paper_run_id, open_time);

ALTER TABLE ONLY continuous_sim_runs
    ADD CONSTRAINT continuous_sim_runs_pkey PRIMARY KEY (paper_run_id);

ALTER TABLE ONLY controlled_execution_lease_events
    ADD CONSTRAINT controlled_execution_lease_events_pkey PRIMARY KEY (event_id);

ALTER TABLE ONLY controlled_execution_lease_intents
    ADD CONSTRAINT controlled_execution_lease_intents_pkey PRIMARY KEY (lease_id, action);

ALTER TABLE ONLY controlled_execution_leases
    ADD CONSTRAINT controlled_execution_leases_pkey PRIMARY KEY (lease_id);

ALTER TABLE ONLY credential_audit_logs
    ADD CONSTRAINT credential_audit_logs_pkey PRIMARY KEY (credential_audit_log_id);

ALTER TABLE ONLY emergency_stop_events
    ADD CONSTRAINT emergency_stop_events_pkey PRIMARY KEY (emergency_stop_id);

ALTER TABLE ONLY equity_curve_snapshots
    ADD CONSTRAINT equity_curve_snapshots_pkey PRIMARY KEY (equity_snapshot_id);

ALTER TABLE ONLY event_store
    ADD CONSTRAINT event_store_pkey PRIMARY KEY (event_id);

ALTER TABLE ONLY exchange_account_credentials
    ADD CONSTRAINT exchange_account_credentials_pkey PRIMARY KEY (credential_id);

ALTER TABLE ONLY exchange_accounts
    ADD CONSTRAINT exchange_accounts_pkey PRIMARY KEY (exchange_account_id);

ALTER TABLE ONLY execution_pre_place_recovery_decisions
    ADD CONSTRAINT execution_pre_place_recovery_decisions_pkey PRIMARY KEY (decision_id);

ALTER TABLE ONLY execution_receipts
    ADD CONSTRAINT execution_receipts_pkey PRIMARY KEY (receipt_id);

ALTER TABLE ONLY instrument_catalog
    ADD CONSTRAINT instrument_catalog_pkey PRIMARY KEY (instrument_id);

ALTER TABLE ONLY kill_switch_events
    ADD CONSTRAINT kill_switch_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY kill_switch_states
    ADD CONSTRAINT kill_switch_states_pkey PRIMARY KEY (scope);

ALTER TABLE ONLY ledger_entries
    ADD CONSTRAINT ledger_entries_pkey PRIMARY KEY (entry_id);

ALTER TABLE ONLY ledger_events
    ADD CONSTRAINT ledger_events_pkey PRIMARY KEY (ledger_event_id);

ALTER TABLE ONLY live_session_events
    ADD CONSTRAINT live_session_events_pkey PRIMARY KEY (event_id);

ALTER TABLE ONLY live_sessions
    ADD CONSTRAINT live_sessions_pkey PRIMARY KEY (session_id);

ALTER TABLE ONLY marketdata_bars
    ADD CONSTRAINT marketdata_bars_pkey PRIMARY KEY (marketdata_bar_id);

ALTER TABLE ONLY marketdata_dataset_coverage
    ADD CONSTRAINT marketdata_dataset_coverage_pkey PRIMARY KEY (coverage_id);

ALTER TABLE ONLY marketdata_datasets
    ADD CONSTRAINT marketdata_datasets_pkey PRIMARY KEY (dataset_id);

ALTER TABLE ONLY marketdata_ingestion_jobs
    ADD CONSTRAINT marketdata_ingestion_jobs_pkey PRIMARY KEY (job_id);

ALTER TABLE ONLY marketdata_ingestion_runs
    ADD CONSTRAINT marketdata_ingestion_runs_pkey PRIMARY KEY (run_id);

ALTER TABLE ONLY operator_approvals
    ADD CONSTRAINT operator_approvals_pkey PRIMARY KEY (approval_id);

ALTER TABLE ONLY operator_execution_authorities
    ADD CONSTRAINT operator_execution_authorities_pkey PRIMARY KEY (authority_id);

ALTER TABLE ONLY orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (order_id);

ALTER TABLE ONLY ordinary_order_cancel_finality
    ADD CONSTRAINT ordinary_order_cancel_finality_pkey PRIMARY KEY (order_id);

ALTER TABLE ONLY ordinary_place_authorities
    ADD CONSTRAINT ordinary_place_authorities_pkey PRIMARY KEY (order_id);

ALTER TABLE ONLY paper_risk_check_results
    ADD CONSTRAINT paper_risk_check_results_pkey PRIMARY KEY (risk_result_id);

ALTER TABLE ONLY paper_run_alerts
    ADD CONSTRAINT paper_run_alerts_pkey PRIMARY KEY (alert_id);

ALTER TABLE ONLY paper_run_daily_reports
    ADD CONSTRAINT paper_run_daily_reports_pkey PRIMARY KEY (report_id);

ALTER TABLE ONLY paper_run_heartbeats
    ADD CONSTRAINT paper_run_heartbeats_pkey PRIMARY KEY (heartbeat_id);

ALTER TABLE ONLY paper_run_recovery_events
    ADD CONSTRAINT paper_run_recovery_events_pkey PRIMARY KEY (recovery_event_id);

ALTER TABLE ONLY paper_run_schedule_fires
    ADD CONSTRAINT paper_run_schedule_fires_pkey PRIMARY KEY (fire_id);

ALTER TABLE ONLY paper_run_schedules
    ADD CONSTRAINT paper_run_schedules_pkey PRIMARY KEY (schedule_id);

ALTER TABLE ONLY paper_run_stability_checks
    ADD CONSTRAINT paper_run_stability_checks_pkey PRIMARY KEY (stability_check_id);

ALTER TABLE ONLY paper_trading_orders
    ADD CONSTRAINT paper_trading_orders_pkey PRIMARY KEY (paper_order_id);

ALTER TABLE ONLY paper_trading_positions
    ADD CONSTRAINT paper_trading_positions_pkey PRIMARY KEY (paper_position_id);

ALTER TABLE ONLY paper_trading_runs
    ADD CONSTRAINT paper_trading_runs_pkey PRIMARY KEY (paper_run_id);

ALTER TABLE ONLY paper_trading_trades
    ADD CONSTRAINT paper_trading_trades_pkey PRIMARY KEY (paper_trade_id);

ALTER TABLE ONLY execution_instrument_observation_items
    ADD CONSTRAINT pk_execution_instrument_observation_items PRIMARY KEY (observation_id, symbol);

ALTER TABLE ONLY execution_prerequisite_observations
    ADD CONSTRAINT pk_execution_prerequisite_observations PRIMARY KEY (observation_id);

ALTER TABLE ONLY execution_scope_bindings
    ADD CONSTRAINT pk_execution_scope_bindings PRIMARY KEY (execution_scope_id);

ALTER TABLE ONLY position_curve_snapshots
    ADD CONSTRAINT position_curve_snapshots_pkey PRIMARY KEY (position_snapshot_id);

ALTER TABLE ONLY positions
    ADD CONSTRAINT positions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public_market_captures
    ADD CONSTRAINT public_market_captures_pkey PRIMARY KEY (dataset_id);

ALTER TABLE ONLY reconciliation_scan_cursors
    ADD CONSTRAINT reconciliation_scan_cursors_pkey PRIMARY KEY (venue);

ALTER TABLE ONLY research_configs
    ADD CONSTRAINT research_configs_pkey PRIMARY KEY (research_config_id);

ALTER TABLE ONLY risk_events
    ADD CONSTRAINT risk_events_pkey PRIMARY KEY (risk_event_id);

ALTER TABLE ONLY risk_limit_sets
    ADD CONSTRAINT risk_limit_sets_pkey PRIMARY KEY (risk_limit_set_id);

ALTER TABLE ONLY roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY scheduled_job_controls
    ADD CONSTRAINT scheduled_job_controls_pkey PRIMARY KEY (job_key);

ALTER TABLE ONLY shadow_consistency_reports
    ADD CONSTRAINT shadow_consistency_reports_pkey PRIMARY KEY (id);

ALTER TABLE ONLY shadow_run_events
    ADD CONSTRAINT shadow_run_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY shadow_run_snapshots
    ADD CONSTRAINT shadow_run_snapshots_pkey PRIMARY KEY (id);

ALTER TABLE ONLY shadow_runs
    ADD CONSTRAINT shadow_runs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY sim_orders
    ADD CONSTRAINT sim_orders_pkey PRIMARY KEY (sim_order_id);

ALTER TABLE ONLY sim_pnl_snapshots
    ADD CONSTRAINT sim_pnl_snapshots_pkey PRIMARY KEY (sim_pnl_snapshot_id);

ALTER TABLE ONLY sim_positions
    ADD CONSTRAINT sim_positions_pkey PRIMARY KEY (sim_position_id);

ALTER TABLE ONLY sim_trades
    ADD CONSTRAINT sim_trades_pkey PRIMARY KEY (sim_trade_id);

ALTER TABLE ONLY strategy_definitions
    ADD CONSTRAINT strategy_definitions_pkey PRIMARY KEY (strategy_id);

ALTER TABLE ONLY strategy_release_admission_state
    ADD CONSTRAINT strategy_release_admission_state_pkey PRIMARY KEY (publish_record_id);

ALTER TABLE ONLY strategy_run_dispatch_work
    ADD CONSTRAINT strategy_run_dispatch_work_pkey PRIMARY KEY (strategy_run_id);

ALTER TABLE ONLY strategy_run_recovery_scan_cursor
    ADD CONSTRAINT strategy_run_recovery_scan_cursor_pkey PRIMARY KEY (cursor_id);

ALTER TABLE ONLY strategy_runs
    ADD CONSTRAINT strategy_runs_pkey PRIMARY KEY (strategy_run_id);

ALTER TABLE ONLY strategy_schedules
    ADD CONSTRAINT strategy_schedules_pkey PRIMARY KEY (schedule_job_id);

ALTER TABLE ONLY strategy_sim_decisions
    ADD CONSTRAINT strategy_sim_decisions_order_id_key UNIQUE (order_id);

ALTER TABLE ONLY strategy_sim_decisions
    ADD CONSTRAINT strategy_sim_decisions_pkey PRIMARY KEY (decision_id);

ALTER TABLE ONLY strategy_sim_decisions
    ADD CONSTRAINT strategy_sim_decisions_strategy_run_id_key UNIQUE (strategy_run_id);

ALTER TABLE ONLY strategy_versions
    ADD CONSTRAINT strategy_versions_pkey PRIMARY KEY (strategy_version_id);

ALTER TABLE ONLY trade_replay_records
    ADD CONSTRAINT trade_replay_records_pkey PRIMARY KEY (replay_record_id);

ALTER TABLE ONLY trades
    ADD CONSTRAINT trades_pkey PRIMARY KEY (trade_id);

ALTER TABLE ONLY paper_trading_positions
    ADD CONSTRAINT uk_paper_positions_run_symbol UNIQUE (paper_run_id, symbol);

ALTER TABLE ONLY accounts
    ADD CONSTRAINT uq_accounts_account_code UNIQUE (account_code);

ALTER TABLE ONLY backtest_eval_reports
    ADD CONSTRAINT uq_backtest_eval_reports_run UNIQUE (backtest_run_id);

ALTER TABLE ONLY backtest_publish_records
    ADD CONSTRAINT uq_backtest_publish_records_run UNIQUE (backtest_run_id);

ALTER TABLE ONLY controlled_execution_lease_events
    ADD CONSTRAINT uq_controlled_execution_lease_events_version UNIQUE (lease_id, lease_version);

ALTER TABLE ONLY controlled_execution_lease_intents
    ADD CONSTRAINT uq_controlled_execution_lease_intents_intent UNIQUE (intent_id);

ALTER TABLE ONLY controlled_execution_leases
    ADD CONSTRAINT uq_controlled_execution_leases_binding UNIQUE (binding_id);

ALTER TABLE ONLY controlled_execution_leases
    ADD CONSTRAINT uq_controlled_execution_leases_session UNIQUE (live_session_id);

ALTER TABLE ONLY paper_run_daily_reports
    ADD CONSTRAINT uq_daily_reports_run_date UNIQUE (paper_run_id, report_date);

ALTER TABLE ONLY exchange_accounts
    ADD CONSTRAINT uq_exchange_accounts_legacy_account_id UNIQUE (legacy_account_id);

ALTER TABLE ONLY execution_intents
    ADD CONSTRAINT uq_execution_intents_business_id PRIMARY KEY (intent_id);

ALTER TABLE ONLY execution_intents
    ADD CONSTRAINT uq_execution_intents_session_sequence UNIQUE (session_id, sequence);

ALTER TABLE ONLY execution_prerequisite_observations
    ADD CONSTRAINT uq_execution_observation_id_type UNIQUE (observation_id, observation_type);

ALTER TABLE ONLY execution_prerequisite_observations
    ADD CONSTRAINT uq_execution_observation_set_type UNIQUE (execution_scope_id, observation_set_id, observation_type);

ALTER TABLE ONLY execution_prerequisite_observations
    ADD CONSTRAINT uq_execution_observation_source_identity UNIQUE (execution_scope_id, observation_type, source_identity, observation_identity);

ALTER TABLE ONLY execution_pre_place_recovery_decisions
    ADD CONSTRAINT uq_execution_pre_place_recovery_predecessor UNIQUE (predecessor_lease_id);

ALTER TABLE ONLY execution_receipts
    ADD CONSTRAINT uq_execution_receipts_ordinal UNIQUE (intent_id, receipt_ordinal);

ALTER TABLE ONLY execution_scope_bindings
    ADD CONSTRAINT uq_execution_scope_bindings_approval UNIQUE (session_id, execution_scope_id, execution_scope_hash);

ALTER TABLE ONLY execution_scope_bindings
    ADD CONSTRAINT uq_execution_scope_bindings_session UNIQUE (session_id);

ALTER TABLE ONLY instrument_catalog
    ADD CONSTRAINT uq_instrument_catalog_exchange_internal_symbol UNIQUE (exchange_code, internal_symbol);

ALTER TABLE ONLY instrument_catalog
    ADD CONSTRAINT uq_instrument_catalog_exchange_symbol UNIQUE (exchange_code, exchange_symbol);

ALTER TABLE ONLY kill_switch_events
    ADD CONSTRAINT uq_kill_switch_events_scope_version UNIQUE (scope, state_version);

ALTER TABLE ONLY live_session_events
    ADD CONSTRAINT uq_live_session_events_sequence UNIQUE (session_id, sequence_no);

ALTER TABLE ONLY marketdata_bars
    ADD CONSTRAINT uq_marketdata_bars_scope UNIQUE (exchange_code, market_type, symbol, "interval", open_time);

ALTER TABLE ONLY marketdata_datasets
    ADD CONSTRAINT uq_marketdata_datasets_scope UNIQUE (dataset_name, exchange_code, market_type, symbol, "interval", start_time, end_time);

ALTER TABLE ONLY operator_execution_authorities
    ADD CONSTRAINT uq_operator_execution_authorities_digest UNIQUE (canonical_digest);

ALTER TABLE ONLY orders
    ADD CONSTRAINT uq_orders_account_client_order UNIQUE (account_id, client_order_id);

ALTER TABLE ONLY positions
    ADD CONSTRAINT uq_positions_account_symbol UNIQUE (account_id, symbol);

ALTER TABLE ONLY risk_limit_sets
    ADD CONSTRAINT uq_risk_limit_sets_digest UNIQUE (canonical_digest);

ALTER TABLE ONLY risk_limit_sets
    ADD CONSTRAINT uq_risk_limit_sets_scope_version UNIQUE (effective_scope, version);

ALTER TABLE ONLY roles
    ADD CONSTRAINT uq_roles_role_code UNIQUE (role_code);

ALTER TABLE ONLY shadow_run_snapshots
    ADD CONSTRAINT uq_shadow_run_snapshots_run_type_seq UNIQUE (shadow_run_id, snapshot_type, sequence_no);

ALTER TABLE ONLY sim_positions
    ADD CONSTRAINT uq_sim_positions_run_symbol UNIQUE (backtest_run_id, symbol);

ALTER TABLE ONLY paper_run_stability_checks
    ADD CONSTRAINT uq_stability_checks_run_window UNIQUE (paper_run_id, check_window_start, check_window_end);

ALTER TABLE ONLY strategy_definitions
    ADD CONSTRAINT uq_strategy_definitions_strategy_code UNIQUE (strategy_code);

ALTER TABLE ONLY strategy_sim_decisions
    ADD CONSTRAINT uq_strategy_sim_decision_window UNIQUE (paper_run_id, signal_open_time);

ALTER TABLE ONLY strategy_versions
    ADD CONSTRAINT uq_strategy_versions_strategy_code_version UNIQUE (strategy_code, version);

ALTER TABLE ONLY strategy_run_dispatch_work
    ADD CONSTRAINT uq_strategy_work_client UNIQUE (account_id, client_order_id);

ALTER TABLE ONLY trades
    ADD CONSTRAINT uq_trades_exchange_trade UNIQUE (exchange, exchange_trade_id);

ALTER TABLE ONLY users
    ADD CONSTRAINT uq_users_username UNIQUE (username);

ALTER TABLE ONLY validation_review_cases
    ADD CONSTRAINT uq_validation_review_cases_id_tenant UNIQUE (id, tenant_key);

ALTER TABLE ONLY validation_review_events
    ADD CONSTRAINT uq_validation_review_events_case_idempotency UNIQUE (review_case_id, idempotency_key);

ALTER TABLE ONLY user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);

ALTER TABLE ONLY users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

ALTER TABLE ONLY validation_review_cases
    ADD CONSTRAINT validation_review_cases_pkey PRIMARY KEY (id);

ALTER TABLE ONLY validation_review_events
    ADD CONSTRAINT validation_review_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY continuous_sim_bars
    ADD CONSTRAINT continuous_sim_bars_paper_run_id_fkey FOREIGN KEY (paper_run_id) REFERENCES continuous_sim_runs(paper_run_id) ON DELETE RESTRICT;

ALTER TABLE ONLY continuous_sim_runs
    ADD CONSTRAINT continuous_sim_runs_paper_run_id_fkey FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id) ON DELETE RESTRICT;

ALTER TABLE ONLY continuous_sim_runs
    ADD CONSTRAINT continuous_sim_runs_strategy_version_id_fkey FOREIGN KEY (strategy_version_id) REFERENCES strategy_versions(strategy_version_id) ON DELETE RESTRICT;

ALTER TABLE ONLY account_snapshots
    ADD CONSTRAINT fk_account_snapshots_account FOREIGN KEY (account_id) REFERENCES accounts(account_id);

ALTER TABLE ONLY paper_run_alerts
    ADD CONSTRAINT fk_alerts_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY backtest_configs
    ADD CONSTRAINT fk_backtest_configs_dataset FOREIGN KEY (dataset_id) REFERENCES marketdata_datasets(dataset_id);

ALTER TABLE ONLY backtest_configs
    ADD CONSTRAINT fk_backtest_configs_research FOREIGN KEY (research_config_id) REFERENCES research_configs(research_config_id);

ALTER TABLE ONLY backtest_configs
    ADD CONSTRAINT fk_backtest_configs_strategy_version FOREIGN KEY (strategy_version_id) REFERENCES strategy_versions(strategy_version_id);

ALTER TABLE ONLY backtest_eval_reports
    ADD CONSTRAINT fk_backtest_eval_reports_run FOREIGN KEY (backtest_run_id) REFERENCES backtest_runs(backtest_run_id);

ALTER TABLE ONLY backtest_publish_records
    ADD CONSTRAINT fk_backtest_publish_records_backtest FOREIGN KEY (backtest_config_id) REFERENCES backtest_configs(backtest_config_id);

ALTER TABLE ONLY backtest_publish_records
    ADD CONSTRAINT fk_backtest_publish_records_research FOREIGN KEY (research_config_id) REFERENCES research_configs(research_config_id);

ALTER TABLE ONLY backtest_publish_records
    ADD CONSTRAINT fk_backtest_publish_records_run FOREIGN KEY (backtest_run_id) REFERENCES backtest_runs(backtest_run_id);

ALTER TABLE ONLY backtest_publish_records
    ADD CONSTRAINT fk_backtest_publish_records_strategy_version FOREIGN KEY (strategy_version_id) REFERENCES strategy_versions(strategy_version_id);

ALTER TABLE ONLY backtest_runs
    ADD CONSTRAINT fk_backtest_runs_config FOREIGN KEY (backtest_config_id) REFERENCES backtest_configs(backtest_config_id);

ALTER TABLE ONLY backtest_runs
    ADD CONSTRAINT fk_backtest_runs_research FOREIGN KEY (research_config_id) REFERENCES research_configs(research_config_id);

ALTER TABLE ONLY backtest_runs
    ADD CONSTRAINT fk_backtest_runs_source_strategy FOREIGN KEY (source_strategy_id) REFERENCES strategy_definitions(strategy_id);

ALTER TABLE ONLY backtest_runs
    ADD CONSTRAINT fk_backtest_runs_strategy_version FOREIGN KEY (strategy_version_id) REFERENCES strategy_versions(strategy_version_id);

ALTER TABLE ONLY controlled_execution_lease_events
    ADD CONSTRAINT fk_controlled_execution_lease_events_lease FOREIGN KEY (lease_id) REFERENCES controlled_execution_leases(lease_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY controlled_execution_lease_intents
    ADD CONSTRAINT fk_controlled_execution_lease_intents_intent FOREIGN KEY (intent_id) REFERENCES execution_intents(intent_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY controlled_execution_lease_intents
    ADD CONSTRAINT fk_controlled_execution_lease_intents_lease FOREIGN KEY (lease_id) REFERENCES controlled_execution_leases(lease_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY controlled_execution_leases
    ADD CONSTRAINT fk_controlled_execution_leases_creator FOREIGN KEY (created_by) REFERENCES users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY controlled_execution_leases
    ADD CONSTRAINT fk_controlled_execution_leases_operator_authority FOREIGN KEY (operator_execution_authority_id) REFERENCES operator_execution_authorities(authority_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY controlled_execution_leases
    ADD CONSTRAINT fk_controlled_execution_leases_predecessor FOREIGN KEY (predecessor_lease_id) REFERENCES controlled_execution_leases(lease_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY controlled_execution_leases
    ADD CONSTRAINT fk_controlled_execution_leases_recovery_decision FOREIGN KEY (recovery_decision_id) REFERENCES execution_pre_place_recovery_decisions(decision_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY controlled_execution_leases
    ADD CONSTRAINT fk_controlled_execution_leases_session FOREIGN KEY (live_session_id) REFERENCES live_sessions(session_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY credential_audit_logs
    ADD CONSTRAINT fk_credential_audit_logs_credential FOREIGN KEY (credential_id) REFERENCES exchange_account_credentials(credential_id);

ALTER TABLE ONLY credential_audit_logs
    ADD CONSTRAINT fk_credential_audit_logs_exchange_account FOREIGN KEY (exchange_account_id) REFERENCES exchange_accounts(exchange_account_id);

ALTER TABLE ONLY paper_run_daily_reports
    ADD CONSTRAINT fk_daily_reports_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY equity_curve_snapshots
    ADD CONSTRAINT fk_equity_curve_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY emergency_stop_events
    ADD CONSTRAINT fk_estop_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY exchange_account_credentials
    ADD CONSTRAINT fk_exchange_account_credentials_account FOREIGN KEY (exchange_account_id) REFERENCES exchange_accounts(exchange_account_id);

ALTER TABLE ONLY exchange_account_credentials
    ADD CONSTRAINT fk_exchange_account_credentials_rotated_from FOREIGN KEY (rotated_from_credential_id) REFERENCES exchange_account_credentials(credential_id);

ALTER TABLE ONLY exchange_accounts
    ADD CONSTRAINT fk_exchange_accounts_canonical_legacy_account FOREIGN KEY (legacy_account_id) REFERENCES accounts(account_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY exchange_accounts
    ADD CONSTRAINT fk_exchange_accounts_owner_user FOREIGN KEY (owner_user_id) REFERENCES users(id);

ALTER TABLE ONLY execution_instrument_observation_items
    ADD CONSTRAINT fk_execution_instrument_observation_items_parent FOREIGN KEY (observation_id, observation_type) REFERENCES execution_prerequisite_observations(observation_id, observation_type) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY execution_intents
    ADD CONSTRAINT fk_execution_intents_order FOREIGN KEY (local_order_id) REFERENCES orders(order_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY execution_intents
    ADD CONSTRAINT fk_execution_intents_session FOREIGN KEY (session_id) REFERENCES live_sessions(session_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY execution_pre_place_recovery_decisions
    ADD CONSTRAINT fk_execution_pre_place_recovery_actor FOREIGN KEY (decided_by) REFERENCES users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY execution_pre_place_recovery_decisions
    ADD CONSTRAINT fk_execution_pre_place_recovery_lease FOREIGN KEY (predecessor_lease_id) REFERENCES controlled_execution_leases(lease_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY execution_pre_place_recovery_decisions
    ADD CONSTRAINT fk_execution_pre_place_recovery_session FOREIGN KEY (predecessor_session_id) REFERENCES live_sessions(session_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY execution_prerequisite_observations
    ADD CONSTRAINT fk_execution_prerequisite_observations_scope FOREIGN KEY (execution_scope_id) REFERENCES execution_scope_bindings(execution_scope_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY execution_receipts
    ADD CONSTRAINT fk_execution_receipts_intent FOREIGN KEY (intent_id) REFERENCES execution_intents(intent_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY execution_scope_bindings
    ADD CONSTRAINT fk_execution_scope_bindings_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY execution_scope_bindings
    ADD CONSTRAINT fk_execution_scope_bindings_session FOREIGN KEY (session_id) REFERENCES live_sessions(session_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY paper_run_schedule_fires
    ADD CONSTRAINT fk_fires_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY paper_run_schedule_fires
    ADD CONSTRAINT fk_fires_schedule FOREIGN KEY (schedule_id) REFERENCES paper_run_schedules(schedule_id);

ALTER TABLE ONLY paper_run_heartbeats
    ADD CONSTRAINT fk_heartbeats_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY kill_switch_events
    ADD CONSTRAINT fk_kill_switch_events_scope FOREIGN KEY (scope) REFERENCES kill_switch_states(scope) ON DELETE RESTRICT;

ALTER TABLE ONLY ledger_entries
    ADD CONSTRAINT fk_ledger_entries_account FOREIGN KEY (account_id) REFERENCES accounts(account_id);

ALTER TABLE ONLY ledger_events
    ADD CONSTRAINT fk_ledger_events_entry FOREIGN KEY (entry_id) REFERENCES ledger_entries(entry_id);

ALTER TABLE ONLY live_session_events
    ADD CONSTRAINT fk_live_session_events_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY live_session_events
    ADD CONSTRAINT fk_live_session_events_session FOREIGN KEY (session_id) REFERENCES live_sessions(session_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY live_sessions
    ADD CONSTRAINT fk_live_sessions_account FOREIGN KEY (exchange_account_id) REFERENCES exchange_accounts(exchange_account_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY live_sessions
    ADD CONSTRAINT fk_live_sessions_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY live_sessions
    ADD CONSTRAINT fk_live_sessions_credential FOREIGN KEY (credential_reference) REFERENCES exchange_account_credentials(credential_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY live_sessions
    ADD CONSTRAINT fk_live_sessions_operator_execution_authority FOREIGN KEY (operator_execution_authority_id) REFERENCES operator_execution_authorities(authority_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY live_sessions
    ADD CONSTRAINT fk_live_sessions_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY live_sessions
    ADD CONSTRAINT fk_live_sessions_release FOREIGN KEY (strategy_release_id) REFERENCES strategy_release_admission_state(publish_record_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY live_sessions
    ADD CONSTRAINT fk_live_sessions_risk_set FOREIGN KEY (risk_limit_set_id) REFERENCES risk_limit_sets(risk_limit_set_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY marketdata_dataset_coverage
    ADD CONSTRAINT fk_marketdata_dataset_coverage_dataset FOREIGN KEY (dataset_id) REFERENCES marketdata_datasets(dataset_id);

ALTER TABLE ONLY operator_approvals
    ADD CONSTRAINT fk_operator_approvals_approver FOREIGN KEY (approver_id) REFERENCES users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY operator_approvals
    ADD CONSTRAINT fk_operator_approvals_execution_scope FOREIGN KEY (session_id, execution_scope_id, scope_hash) REFERENCES execution_scope_bindings(session_id, execution_scope_id, execution_scope_hash) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY operator_approvals
    ADD CONSTRAINT fk_operator_approvals_session FOREIGN KEY (session_id) REFERENCES live_sessions(session_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY operator_execution_authorities
    ADD CONSTRAINT fk_operator_execution_authorities_account FOREIGN KEY (exchange_account_id) REFERENCES exchange_accounts(exchange_account_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY operator_execution_authorities
    ADD CONSTRAINT fk_operator_execution_authorities_creator FOREIGN KEY (created_by) REFERENCES users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY operator_execution_authorities
    ADD CONSTRAINT fk_operator_execution_authorities_credential FOREIGN KEY (credential_reference_id) REFERENCES exchange_account_credentials(credential_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY operator_execution_authorities
    ADD CONSTRAINT fk_operator_execution_authorities_owner FOREIGN KEY (owner_user_id) REFERENCES users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY orders
    ADD CONSTRAINT fk_orders_account FOREIGN KEY (account_id) REFERENCES accounts(account_id);

ALTER TABLE ONLY orders
    ADD CONSTRAINT fk_orders_strategy_run FOREIGN KEY (strategy_run_id) REFERENCES strategy_runs(strategy_run_id);

ALTER TABLE ONLY paper_trading_orders
    ADD CONSTRAINT fk_paper_orders_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY paper_trading_positions
    ADD CONSTRAINT fk_paper_positions_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY paper_trading_runs
    ADD CONSTRAINT fk_paper_runs_publish FOREIGN KEY (publish_id) REFERENCES backtest_publish_records(publish_record_id);

ALTER TABLE ONLY paper_trading_runs
    ADD CONSTRAINT fk_paper_runs_strategy_version FOREIGN KEY (strategy_version_id) REFERENCES strategy_versions(strategy_version_id);

ALTER TABLE ONLY paper_trading_trades
    ADD CONSTRAINT fk_paper_trades_order FOREIGN KEY (paper_order_id) REFERENCES paper_trading_orders(paper_order_id);

ALTER TABLE ONLY paper_trading_trades
    ADD CONSTRAINT fk_paper_trades_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY position_curve_snapshots
    ADD CONSTRAINT fk_position_curve_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY positions
    ADD CONSTRAINT fk_positions_account FOREIGN KEY (account_id) REFERENCES accounts(account_id);

ALTER TABLE ONLY paper_run_recovery_events
    ADD CONSTRAINT fk_recovery_events_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY trade_replay_records
    ADD CONSTRAINT fk_replay_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY research_configs
    ADD CONSTRAINT fk_research_configs_source_strategy FOREIGN KEY (source_strategy_id) REFERENCES strategy_definitions(strategy_id);

ALTER TABLE ONLY risk_limit_sets
    ADD CONSTRAINT fk_risk_limit_sets_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY paper_risk_check_results
    ADD CONSTRAINT fk_risk_results_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY paper_run_schedules
    ADD CONSTRAINT fk_schedules_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY shadow_consistency_reports
    ADD CONSTRAINT fk_shadow_consistency_reports_paper_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY shadow_consistency_reports
    ADD CONSTRAINT fk_shadow_consistency_reports_run FOREIGN KEY (shadow_run_id) REFERENCES shadow_runs(id);

ALTER TABLE ONLY shadow_run_events
    ADD CONSTRAINT fk_shadow_run_events_run FOREIGN KEY (shadow_run_id) REFERENCES shadow_runs(id);

ALTER TABLE ONLY shadow_run_snapshots
    ADD CONSTRAINT fk_shadow_run_snapshots_run FOREIGN KEY (shadow_run_id) REFERENCES shadow_runs(id);

ALTER TABLE ONLY shadow_runs
    ADD CONSTRAINT fk_shadow_runs_dataset FOREIGN KEY (dataset_id) REFERENCES marketdata_datasets(dataset_id);

ALTER TABLE ONLY shadow_runs
    ADD CONSTRAINT fk_shadow_runs_evaluation FOREIGN KEY (evaluation_id) REFERENCES backtest_eval_reports(eval_report_id);

ALTER TABLE ONLY shadow_runs
    ADD CONSTRAINT fk_shadow_runs_paper_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY shadow_runs
    ADD CONSTRAINT fk_shadow_runs_publish FOREIGN KEY (publish_id) REFERENCES backtest_publish_records(publish_record_id);

ALTER TABLE ONLY shadow_runs
    ADD CONSTRAINT fk_shadow_runs_strategy_version FOREIGN KEY (strategy_version_id) REFERENCES strategy_versions(strategy_version_id);

ALTER TABLE ONLY sim_orders
    ADD CONSTRAINT fk_sim_orders_run FOREIGN KEY (backtest_run_id) REFERENCES backtest_runs(backtest_run_id);

ALTER TABLE ONLY sim_pnl_snapshots
    ADD CONSTRAINT fk_sim_pnl_snapshots_run FOREIGN KEY (backtest_run_id) REFERENCES backtest_runs(backtest_run_id);

ALTER TABLE ONLY sim_positions
    ADD CONSTRAINT fk_sim_positions_run FOREIGN KEY (backtest_run_id) REFERENCES backtest_runs(backtest_run_id);

ALTER TABLE ONLY sim_trades
    ADD CONSTRAINT fk_sim_trades_order FOREIGN KEY (sim_order_id) REFERENCES sim_orders(sim_order_id);

ALTER TABLE ONLY sim_trades
    ADD CONSTRAINT fk_sim_trades_run FOREIGN KEY (backtest_run_id) REFERENCES backtest_runs(backtest_run_id);

ALTER TABLE ONLY paper_run_stability_checks
    ADD CONSTRAINT fk_stability_checks_run FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id);

ALTER TABLE ONLY strategy_definitions
    ADD CONSTRAINT fk_strategy_definitions_account FOREIGN KEY (account_id) REFERENCES accounts(account_id);

ALTER TABLE ONLY strategy_release_admission_state
    ADD CONSTRAINT fk_strategy_release_admission_state_publish FOREIGN KEY (publish_record_id) REFERENCES backtest_publish_records(publish_record_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY strategy_runs
    ADD CONSTRAINT fk_strategy_run_admission_schedule FOREIGN KEY (admission_schedule_id) REFERENCES strategy_schedules(schedule_job_id);

ALTER TABLE ONLY strategy_runs
    ADD CONSTRAINT fk_strategy_runs_account FOREIGN KEY (account_id) REFERENCES accounts(account_id);

ALTER TABLE ONLY strategy_schedules
    ADD CONSTRAINT fk_strategy_schedules_account FOREIGN KEY (account_id) REFERENCES accounts(account_id);

ALTER TABLE ONLY strategy_schedules
    ADD CONSTRAINT fk_strategy_schedules_strategy FOREIGN KEY (strategy_id) REFERENCES strategy_definitions(strategy_id);

ALTER TABLE ONLY strategy_versions
    ADD CONSTRAINT fk_strategy_versions_strategy_code FOREIGN KEY (strategy_code) REFERENCES strategy_definitions(strategy_code);

ALTER TABLE ONLY trades
    ADD CONSTRAINT fk_trades_account FOREIGN KEY (account_id) REFERENCES accounts(account_id);

ALTER TABLE ONLY trades
    ADD CONSTRAINT fk_trades_order FOREIGN KEY (order_id) REFERENCES orders(order_id);

ALTER TABLE ONLY trades
    ADD CONSTRAINT fk_trades_strategy_run FOREIGN KEY (strategy_run_id) REFERENCES strategy_runs(strategy_run_id);

ALTER TABLE ONLY user_roles
    ADD CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id);

ALTER TABLE ONLY user_roles
    ADD CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ONLY validation_review_cases
    ADD CONSTRAINT fk_validation_review_cases_acknowledged_by FOREIGN KEY (acknowledged_by) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE ONLY validation_review_cases
    ADD CONSTRAINT fk_validation_review_cases_closed_by FOREIGN KEY (closed_by) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE ONLY validation_review_cases
    ADD CONSTRAINT fk_validation_review_cases_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE ONLY validation_review_cases
    ADD CONSTRAINT fk_validation_review_cases_escalated_by FOREIGN KEY (escalated_by) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE ONLY validation_review_cases
    ADD CONSTRAINT fk_validation_review_cases_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE ONLY validation_review_cases
    ADD CONSTRAINT fk_validation_review_cases_resolved_by FOREIGN KEY (resolved_by) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE ONLY validation_review_events
    ADD CONSTRAINT fk_validation_review_events_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE ONLY validation_review_events
    ADD CONSTRAINT fk_validation_review_events_case_tenant FOREIGN KEY (review_case_id, tenant_key) REFERENCES validation_review_cases(id, tenant_key) ON DELETE RESTRICT;

ALTER TABLE ONLY marketdata_ingestion_runs
    ADD CONSTRAINT marketdata_ingestion_runs_job_id_fkey FOREIGN KEY (job_id) REFERENCES marketdata_ingestion_jobs(job_id);

ALTER TABLE ONLY ordinary_order_cancel_finality
    ADD CONSTRAINT ordinary_order_cancel_finality_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(order_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY ordinary_place_authorities
    ADD CONSTRAINT ordinary_place_authorities_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(order_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY paper_trading_runs
    ADD CONSTRAINT paper_trading_runs_canonical_account_id_fkey FOREIGN KEY (canonical_account_id) REFERENCES accounts(account_id) ON DELETE RESTRICT;

ALTER TABLE ONLY public_market_captures
    ADD CONSTRAINT public_market_captures_dataset_id_fkey FOREIGN KEY (dataset_id) REFERENCES marketdata_datasets(dataset_id) ON DELETE RESTRICT;

ALTER TABLE ONLY strategy_run_dispatch_work
    ADD CONSTRAINT strategy_run_dispatch_work_account_id_fkey FOREIGN KEY (account_id) REFERENCES accounts(account_id);

ALTER TABLE ONLY strategy_run_dispatch_work
    ADD CONSTRAINT strategy_run_dispatch_work_strategy_run_id_fkey FOREIGN KEY (strategy_run_id) REFERENCES strategy_runs(strategy_run_id) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE ONLY strategy_sim_decisions
    ADD CONSTRAINT strategy_sim_decisions_canonical_account_id_fkey FOREIGN KEY (canonical_account_id) REFERENCES accounts(account_id) ON DELETE RESTRICT;

ALTER TABLE ONLY strategy_sim_decisions
    ADD CONSTRAINT strategy_sim_decisions_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE RESTRICT;

ALTER TABLE ONLY strategy_sim_decisions
    ADD CONSTRAINT strategy_sim_decisions_paper_run_id_fkey FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs(paper_run_id) ON DELETE RESTRICT;

ALTER TABLE ONLY strategy_sim_decisions
    ADD CONSTRAINT strategy_sim_decisions_strategy_run_id_fkey FOREIGN KEY (strategy_run_id) REFERENCES strategy_runs(strategy_run_id) ON DELETE RESTRICT;

ALTER TABLE ONLY strategy_sim_decisions
    ADD CONSTRAINT strategy_sim_decisions_strategy_version_id_fkey FOREIGN KEY (strategy_version_id) REFERENCES strategy_versions(strategy_version_id) ON DELETE RESTRICT;

-- 持久化行为、只读投影和索引。

CREATE FUNCTION bump_strategy_release_admission_revision(p_publish_record_id character varying) RETURNS bigint
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_revision BIGINT;
BEGIN
    IF p_publish_record_id IS NULL OR btrim(p_publish_record_id) = '' THEN
        RAISE EXCEPTION USING
            ERRCODE = '23502',
            MESSAGE = 'publish_record_id is required for admission revision bump';
    END IF;

    -- Raw SQL writers reach this function after locking a source row. NOWAIT prevents a reverse
    -- source-row -> admission-state wait from deadlocking with the canonical state-first writer.
    -- The canonical coordinator already owns this row lock in the same transaction and proceeds.
    PERFORM 1
    FROM strategy_release_admission_state
    WHERE publish_record_id = p_publish_record_id
    FOR UPDATE NOWAIT;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING
            ERRCODE = '23503',
            MESSAGE = 'strategy release admission state is missing';
    END IF;

    UPDATE strategy_release_admission_state
    SET admission_revision = admission_revision + 1,
        updated_at = clock_timestamp()
    WHERE publish_record_id = p_publish_record_id
    RETURNING admission_revision INTO v_revision;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING
            ERRCODE = '23503',
            MESSAGE = 'strategy release admission state is missing';
    END IF;
    RETURN v_revision;
END;
$$;

CREATE FUNCTION bump_strategy_release_admission_revisions(p_publish_record_ids character varying[]) RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_publish_record_ids VARCHAR[];
    v_publish_record_id VARCHAR;
    v_max_fan_out INTEGER;
BEGIN
    v_max_fan_out := COALESCE(
        NULLIF(current_setting('nexusquant.admission.max_fan_out', true), '')::INTEGER,
        256
    );
    IF v_max_fan_out < 1 OR v_max_fan_out > 256 THEN
        RAISE EXCEPTION USING
            ERRCODE = '22023',
            MESSAGE = 'admission revision fan-out limit must be between 1 and 256';
    END IF;

    SELECT COALESCE(array_agg(value ORDER BY value), ARRAY[]::VARCHAR[])
    INTO v_publish_record_ids
    FROM (
        SELECT DISTINCT btrim(value) AS value
        FROM unnest(COALESCE(p_publish_record_ids, ARRAY[]::VARCHAR[])) AS ids(value)
        WHERE value IS NOT NULL AND btrim(value) <> ''
        ORDER BY value
        LIMIT v_max_fan_out + 1
    ) normalized;

    IF cardinality(v_publish_record_ids) > v_max_fan_out THEN
        RAISE EXCEPTION USING
            ERRCODE = '54000',
            MESSAGE = 'admission revision fan-out limit exceeded';
    END IF;

    FOREACH v_publish_record_id IN ARRAY v_publish_record_ids LOOP
        PERFORM bump_strategy_release_admission_revision(v_publish_record_id);
    END LOOP;
END;
$$;

CREATE FUNCTION bump_admission_for_backtest_run_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_ids VARCHAR[];
BEGIN
    SELECT array_agg(p.publish_record_id ORDER BY p.publish_record_id)
    INTO v_ids
    FROM backtest_publish_records p
    WHERE p.backtest_run_id IN (
        CASE WHEN TG_OP <> 'INSERT' THEN OLD.backtest_run_id ELSE NULL END,
        CASE WHEN TG_OP <> 'DELETE' THEN NEW.backtest_run_id ELSE NULL END
    );
    PERFORM bump_strategy_release_admission_revisions(v_ids);
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE FUNCTION bump_admission_for_consistency_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_ids VARCHAR[];
BEGIN
    SELECT array_agg(DISTINCT s.publish_id ORDER BY s.publish_id)
    INTO v_ids
    FROM shadow_runs s
    WHERE s.id IN (
        CASE WHEN TG_OP <> 'INSERT' THEN OLD.shadow_run_id ELSE NULL END,
        CASE WHEN TG_OP <> 'DELETE' THEN NEW.shadow_run_id ELSE NULL END
    )
      AND s.publish_id IS NOT NULL;
    PERFORM bump_strategy_release_admission_revisions(v_ids);
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE FUNCTION bump_admission_for_dataset_delete() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_ids VARCHAR[];
BEGIN
    SELECT array_agg(bounded.publish_record_id ORDER BY bounded.publish_record_id)
    INTO v_ids
    FROM (
        SELECT DISTINCT p.publish_record_id
        FROM backtest_publish_records p
        JOIN backtest_runs r ON r.backtest_run_id = p.backtest_run_id
        JOIN marketdata_dataset_deleted_rows d
          ON d.dataset_id::TEXT = r.dataset_snapshot_json ->> 'datasetId'
        ORDER BY p.publish_record_id
        LIMIT 257
    ) bounded;
    PERFORM bump_strategy_release_admission_revisions(v_ids);
    RETURN NULL;
END;
$$;

CREATE FUNCTION bump_admission_for_dataset_update() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_ids VARCHAR[];
BEGIN
    SELECT array_agg(bounded.publish_record_id ORDER BY bounded.publish_record_id)
    INTO v_ids
    FROM (
        SELECT DISTINCT p.publish_record_id
        FROM backtest_publish_records p
        JOIN backtest_runs r ON r.backtest_run_id = p.backtest_run_id
        WHERE (r.dataset_snapshot_json ->> 'datasetId') IN (
            SELECT n.dataset_id::TEXT
            FROM marketdata_dataset_new_rows n
            JOIN marketdata_dataset_old_rows o USING (dataset_id)
            WHERE n.status IS DISTINCT FROM o.status
               OR n.quality_status IS DISTINCT FROM o.quality_status
               OR n.start_time IS DISTINCT FROM o.start_time
               OR n.end_time IS DISTINCT FROM o.end_time
               OR n.bar_count IS DISTINCT FROM o.bar_count
               OR n.gap_count IS DISTINCT FROM o.gap_count
        )
        ORDER BY p.publish_record_id
        LIMIT 257
    ) bounded;
    PERFORM bump_strategy_release_admission_revisions(v_ids);
    RETURN NULL;
END;
$$;

CREATE FUNCTION bump_admission_for_evaluation_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_ids VARCHAR[];
BEGIN
    SELECT array_agg(p.publish_record_id ORDER BY p.publish_record_id)
    INTO v_ids
    FROM backtest_publish_records p
    WHERE p.backtest_run_id IN (
        CASE WHEN TG_OP <> 'INSERT' THEN OLD.backtest_run_id ELSE NULL END,
        CASE WHEN TG_OP <> 'DELETE' THEN NEW.backtest_run_id ELSE NULL END
    );
    PERFORM bump_strategy_release_admission_revisions(v_ids);
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE FUNCTION bump_admission_for_paper_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    PERFORM bump_strategy_release_admission_revisions(ARRAY[
        CASE WHEN TG_OP <> 'INSERT' THEN OLD.publish_id ELSE NULL END,
        CASE WHEN TG_OP <> 'DELETE' THEN NEW.publish_id ELSE NULL END
    ]);
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE FUNCTION bump_admission_for_publish_update() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    PERFORM bump_strategy_release_admission_revision(NEW.publish_record_id);
    RETURN NEW;
END;
$$;

CREATE FUNCTION bump_admission_for_shadow_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    PERFORM bump_strategy_release_admission_revisions(ARRAY[
        CASE WHEN TG_OP <> 'INSERT' THEN OLD.publish_id ELSE NULL END,
        CASE WHEN TG_OP <> 'DELETE' THEN NEW.publish_id ELSE NULL END
    ]);
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE FUNCTION bump_admission_for_strategy_version_delete() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_ids VARCHAR[];
BEGIN
    SELECT array_agg(bounded.publish_record_id ORDER BY bounded.publish_record_id)
    INTO v_ids
    FROM (
        SELECT DISTINCT p.publish_record_id
        FROM backtest_publish_records p
        JOIN strategy_version_deleted_rows d ON d.strategy_version_id = p.strategy_version_id
        ORDER BY p.publish_record_id
        LIMIT 257
    ) bounded;
    PERFORM bump_strategy_release_admission_revisions(v_ids);
    RETURN NULL;
END;
$$;

CREATE FUNCTION bump_admission_for_strategy_version_update() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_ids VARCHAR[];
BEGIN
    SELECT array_agg(bounded.publish_record_id ORDER BY bounded.publish_record_id)
    INTO v_ids
    FROM (
        SELECT DISTINCT p.publish_record_id
        FROM backtest_publish_records p
        WHERE p.strategy_version_id IN (
            SELECT n.strategy_version_id
            FROM strategy_version_new_rows n
            JOIN strategy_version_old_rows o USING (strategy_version_id)
            WHERE n.status IS DISTINCT FROM o.status
        )
        ORDER BY p.publish_record_id
        LIMIT 257
    ) bounded;
    PERFORM bump_strategy_release_admission_revisions(v_ids);
    RETURN NULL;
END;
$$;

CREATE FUNCTION canonical_legacy_account_code(p_exchange_account_id bigint) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT
    AS $$
    SELECT 'nq-okx-live-' || p_exchange_account_id::TEXT
$$;

CREATE FUNCTION close_operator_execution_authority_with_lease() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.operator_execution_authority_id IS NOT NULL
        AND NEW.status IN ('EXPIRED','CLOSED','FAILED')
        AND OLD.status IS DISTINCT FROM NEW.status THEN
        UPDATE operator_execution_authorities
        SET status = CASE WHEN NEW.status = 'EXPIRED' THEN 'EXPIRED' ELSE 'CLOSED' END,
            version = version + 1,
            closed_at = NEW.closed_at,
            updated_at = NEW.updated_at
        WHERE authority_id = NEW.operator_execution_authority_id AND status = 'ACTIVE';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION controlled_execution_boundary_is_empty() RETURNS boolean
    LANGUAGE sql STABLE
    AS $$
    SELECT
        NOT EXISTS (
            SELECT 1 FROM controlled_execution_lease_intents WHERE action='PLACE'
        )
        AND NOT EXISTS (
            SELECT 1 FROM execution_intents intent
            JOIN live_sessions session ON session.session_id=intent.session_id
            WHERE session.authority_type='OPERATOR_CONTROLLED_EXECUTION'
        )
        AND NOT EXISTS (
            SELECT 1 FROM execution_receipts receipt
            JOIN execution_intents intent ON intent.intent_id=receipt.intent_id
            JOIN live_sessions session ON session.session_id=intent.session_id
            WHERE session.authority_type='OPERATOR_CONTROLLED_EXECUTION'
        )
        AND NOT EXISTS (
            SELECT 1 FROM orders local_order
            JOIN execution_intents intent ON intent.local_order_id=local_order.order_id
            JOIN live_sessions session ON session.session_id=intent.session_id
            WHERE session.authority_type='OPERATOR_CONTROLLED_EXECUTION'
        )
        AND NOT EXISTS (
            SELECT 1 FROM trades trade
            JOIN orders local_order ON local_order.order_id=trade.order_id
            JOIN execution_intents intent ON intent.local_order_id=local_order.order_id
            JOIN live_sessions session ON session.session_id=intent.session_id
            WHERE session.authority_type='OPERATOR_CONTROLLED_EXECUTION'
        )
        AND NOT EXISTS (
            SELECT 1 FROM ledger_entries ledger
            JOIN orders local_order ON ledger.ref_id=local_order.order_id
            JOIN execution_intents intent ON intent.local_order_id=local_order.order_id
            JOIN live_sessions session ON session.session_id=intent.session_id
            WHERE session.authority_type='OPERATOR_CONTROLLED_EXECUTION'
            UNION ALL
            SELECT 1 FROM ledger_entries ledger
            JOIN trades trade ON ledger.ref_id=trade.trade_id
            JOIN orders local_order ON local_order.order_id=trade.order_id
            JOIN execution_intents intent ON intent.local_order_id=local_order.order_id
            JOIN live_sessions session ON session.session_id=intent.session_id
            WHERE session.authority_type='OPERATOR_CONTROLLED_EXECUTION'
        )
$$;

CREATE FUNCTION execution_evidence_instant_canonical(p_value timestamp with time zone) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT
    AS $$
    SELECT to_json(to_char(p_value AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'))::TEXT
$$;

CREATE FUNCTION execution_evidence_numeric_canonical(p_value numeric) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT
    AS $$
    SELECT CASE
        WHEN p_value = 0 THEN '0'
        WHEN position('.' IN p_value::TEXT) = 0 THEN p_value::TEXT
        ELSE rtrim(rtrim(p_value::TEXT, '0'), '.')
    END
$$;

CREATE FUNCTION execution_instrument_items_canonical(p_observation_id uuid) RETURNS text
    LANGUAGE plpgsql STABLE STRICT
    AS $$
DECLARE
    v_schema_version VARCHAR(64);
    v_items TEXT;
BEGIN
    SELECT observation_schema_version INTO STRICT v_schema_version
    FROM execution_prerequisite_observations
    WHERE observation_id = p_observation_id AND observation_type = 'INSTRUMENT_METADATA';

    IF v_schema_version = 'instrument-metadata-observation.v1' THEN
        SELECT string_agg(
            '{"symbol":' || to_json(item.symbol)::TEXT ||
            ',"tradingStatus":' || to_json(item.trading_status)::TEXT ||
            ',"tickSize":' || to_json(execution_evidence_numeric_canonical(item.tick_size))::TEXT ||
            ',"lotSize":' || to_json(execution_evidence_numeric_canonical(item.lot_size))::TEXT ||
            ',"minimumOrderSize":' || to_json(execution_evidence_numeric_canonical(item.minimum_order_size))::TEXT ||
            ',"minimumOrderValue":' || to_json(execution_evidence_numeric_canonical(item.minimum_order_value))::TEXT ||
            ',"minimumOrderValueCurrency":' || to_json(item.minimum_order_value_currency)::TEXT || '}',
            ',' ORDER BY item.symbol
        ) INTO v_items
        FROM execution_instrument_observation_items item
        WHERE item.observation_id = p_observation_id;
    ELSE
        SELECT string_agg(
            '{"symbol":' || to_json(item.symbol)::TEXT ||
            ',"tradingStatus":' || to_json(item.trading_status)::TEXT ||
            ',"tickSize":' || to_json(execution_evidence_numeric_canonical(item.tick_size))::TEXT ||
            ',"lotSize":' || to_json(execution_evidence_numeric_canonical(item.lot_size))::TEXT ||
            ',"minimumOrderSize":' || to_json(execution_evidence_numeric_canonical(item.minimum_order_size))::TEXT ||
            ',"minimumOrderValueEvidenceClass":' ||
                to_json(item.minimum_order_value_evidence_class)::TEXT ||
            CASE WHEN item.minimum_order_value_evidence_class = 'VENUE_PUBLISHED' THEN
                ',"minimumOrderValue":' ||
                    to_json(execution_evidence_numeric_canonical(item.minimum_order_value))::TEXT ||
                ',"minimumOrderValueCurrency":' || to_json(item.minimum_order_value_currency)::TEXT
            ELSE '' END || '}',
            ',' ORDER BY item.symbol
        ) INTO v_items
        FROM execution_instrument_observation_items item
        WHERE item.observation_id = p_observation_id;
    END IF;
    RETURN v_items;
END;
$$;

CREATE FUNCTION execution_instrument_metadata_digest(p_observation_id uuid) RETURNS text
    LANGUAGE sql STABLE STRICT
    AS $$
    SELECT encode(digest(convert_to(
        '{"schemaVersion":' || to_json(observation.observation_schema_version)::TEXT ||
        ',"items":[' || execution_instrument_items_canonical(observation.observation_id) || ']}',
        'UTF8'), 'sha256'), 'hex')
    FROM execution_prerequisite_observations observation
    WHERE observation.observation_id = p_observation_id
      AND observation.observation_type = 'INSTRUMENT_METADATA'
$$;

CREATE FUNCTION execution_market_snapshot_digest(p_instrument text, p_best_ask numeric, p_observed_at timestamp with time zone, p_source_identity text, p_source_schema_version text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT
    AS $$
    SELECT encode(digest(convert_to(
        '{"schemaVersion":"market-snapshot-observation.v1"' ||
        ',"instrument":' || to_json(p_instrument)::TEXT ||
        ',"bestAsk":' || to_json(execution_evidence_numeric_canonical(p_best_ask))::TEXT ||
        ',"observedAt":' || execution_evidence_instant_canonical(p_observed_at) ||
        ',"sourceIdentity":' || to_json(p_source_identity)::TEXT ||
        ',"sourceSchemaVersion":' || to_json(p_source_schema_version)::TEXT || '}',
        'UTF8'), 'sha256'), 'hex')
$$;

CREATE FUNCTION execution_observation_payload_hash(p_observation_id uuid) RETURNS text
    LANGUAGE plpgsql STABLE STRICT
    AS $$
DECLARE
    v_observation execution_prerequisite_observations%ROWTYPE;
    v_payload TEXT;
BEGIN
    SELECT * INTO STRICT v_observation FROM execution_prerequisite_observations
    WHERE observation_id = p_observation_id;
    v_payload := '{"schemaVersion":"prerequisite-observation-envelope.v1"' ||
        ',"observationType":' || to_json(v_observation.observation_type)::TEXT ||
        ',"observationSchemaVersion":' || to_json(v_observation.observation_schema_version)::TEXT ||
        ',"observationIdentity":' || to_json(v_observation.observation_identity)::TEXT ||
        ',"sourceIdentity":' || to_json(v_observation.source_identity)::TEXT ||
        ',"sourceSchemaVersion":' || to_json(v_observation.source_schema_version)::TEXT ||
        ',"observedAt":' || execution_evidence_instant_canonical(v_observation.observed_at) ||
        ',"payload":';
    IF v_observation.observation_type = 'INSTRUMENT_METADATA' THEN
        v_payload := v_payload || '{"instrumentMetadataDigest":' ||
            to_json(v_observation.instrument_metadata_digest)::TEXT || ',"items":[' ||
            execution_instrument_items_canonical(p_observation_id) || ']}';
    ELSIF v_observation.observation_type = 'FEE_SCHEDULE' THEN
        v_payload := v_payload || '{"feeScheduleDigest":' || to_json(v_observation.fee_schedule_digest)::TEXT ||
            ',"feeTier":' || to_json(v_observation.fee_tier)::TEXT ||
            ',"feeEvidenceClass":' || to_json(v_observation.fee_evidence_class)::TEXT ||
            ',"makerFeeRate":' || to_json(execution_evidence_numeric_canonical(v_observation.maker_fee_rate))::TEXT ||
            ',"takerFeeRate":' || to_json(execution_evidence_numeric_canonical(v_observation.taker_fee_rate))::TEXT ||
            ',"feeLossTreatment":' || to_json(v_observation.fee_loss_treatment)::TEXT || '}';
    ELSIF v_observation.observation_type = 'BALANCE_SNAPSHOT' THEN
        v_payload := v_payload || '{"balanceSnapshotDigest":' ||
            to_json(v_observation.balance_snapshot_digest)::TEXT ||
            ',"balanceCurrency":' || to_json(v_observation.balance_currency)::TEXT ||
            ',"availableBalance":' ||
                to_json((v_observation.available_balance::NUMERIC(38,8))::TEXT)::TEXT || '}';
    ELSIF v_observation.observation_type = 'CLOCK_SYNC' THEN
        v_payload := v_payload || '{"clockSyncObservationDigest":' ||
            to_json(v_observation.clock_sync_observation_digest)::TEXT ||
            ',"signedTimestampSource":' || to_json(v_observation.signed_timestamp_source)::TEXT ||
            ',"observedSkewMs":' || v_observation.observed_skew_ms::TEXT || '}';
    ELSIF v_observation.observation_type = 'MARKET_SNAPSHOT' THEN
        v_payload := v_payload || '{"marketSnapshotDigest":' ||
            to_json(v_observation.market_snapshot_digest)::TEXT ||
            ',"instrument":' || to_json(v_observation.market_instrument)::TEXT ||
            ',"bestAsk":' || to_json(execution_evidence_numeric_canonical(v_observation.best_ask))::TEXT || '}';
    ELSE
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='unsupported controlled execution observation type';
    END IF;
    v_payload := v_payload || '}';
    RETURN encode(digest(convert_to(v_payload, 'UTF8'), 'sha256'), 'hex');
END;
$$;

CREATE FUNCTION execution_scope_canonical_payload(p_session_id uuid, p_instrument_metadata_digest text, p_instrument_source_identity text, p_instrument_source_schema_version text, p_instrument_maximum_age_ms bigint, p_fee_schedule_digest text, p_fee_tier text, p_fee_evidence_class text, p_fee_source_identity text, p_fee_source_schema_version text, p_fee_maximum_age_ms bigint, p_balance_source_identity text, p_balance_source_schema_version text, p_balance_maximum_age_ms bigint, p_clock_source_identity text, p_clock_source_schema_version text, p_clock_maximum_age_ms bigint, p_signed_timestamp_source text, p_maximum_tolerated_skew_ms bigint, p_endpoint_policy_version text, p_endpoint_policy_digest text, p_provider_contract_identity text, p_provider_artifact_digest text, p_worker_identity text, p_worker_release_digest text) RETURNS text
    LANGUAGE sql STABLE STRICT
    AS $$
    SELECT '{' ||
        CASE WHEN s.authority_type = 'STRATEGY'
            THEN '"schemaVersion":"execution-scope.v1"'
                || ',"sessionId":' || to_json(s.session_id::TEXT)::TEXT
                || ',"ownerId":' || s.owner_id::TEXT
                || ',"exchangeAccountId":' || s.exchange_account_id::TEXT
                || ',"venue":' || to_json(s.venue)::TEXT
                || ',"strategyReleaseId":' || to_json(s.strategy_release_id)::TEXT
                || ',"releaseArtifactDigest":' || to_json(s.release_digest)::TEXT
                || ',"releaseAdmissionRevision":' || s.release_admission_revision::TEXT
                || ',"riskLimitSetId":' || to_json(s.risk_limit_set_id::TEXT)::TEXT
                || ',"riskLimitSetDigest":' || to_json(s.risk_limit_set_digest)::TEXT
            ELSE '"schemaVersion":"execution-scope.operator.v1"'
                || ',"sessionId":' || to_json(s.session_id::TEXT)::TEXT
                || ',"ownerId":' || s.owner_id::TEXT
                || ',"exchangeAccountId":' || s.exchange_account_id::TEXT
                || ',"venue":' || to_json(s.venue)::TEXT
                || ',"authorityType":"OPERATOR_CONTROLLED_EXECUTION"'
                || ',"operatorExecutionAuthorityId":' || to_json(s.operator_execution_authority_id::TEXT)::TEXT
                || ',"operatorExecutionAuthorityDigest":' || to_json(s.operator_execution_authority_digest)::TEXT
        END ||
        ',"credentialReference":' || s.credential_reference::TEXT ||
        ',"symbolAllowlist":[' || (
            SELECT string_agg(to_json(symbol)::TEXT, ',' ORDER BY ordinal)
            FROM unnest(s.symbol_allowlist) WITH ORDINALITY symbols(symbol, ordinal)
        ) || ']' ||
        ',"capitalCap":' || to_json((s.capital_cap::NUMERIC(38,8))::TEXT)::TEXT ||
        ',"executionWindowStart":' || execution_evidence_instant_canonical(s.execution_window_start) ||
        ',"executionWindowEnd":' || execution_evidence_instant_canonical(s.execution_window_end) ||
        ',"instrumentMetadataDigest":' || to_json(p_instrument_metadata_digest)::TEXT ||
        ',"instrumentSourceIdentity":' || to_json(p_instrument_source_identity)::TEXT ||
        ',"instrumentSourceSchemaVersion":' || to_json(p_instrument_source_schema_version)::TEXT ||
        ',"instrumentMaximumAgeMs":' || p_instrument_maximum_age_ms::TEXT ||
        ',"feeScheduleDigest":' || to_json(p_fee_schedule_digest)::TEXT ||
        ',"feeTier":' || to_json(p_fee_tier)::TEXT ||
        ',"feeEvidenceClass":' || to_json(p_fee_evidence_class)::TEXT ||
        ',"feeSourceIdentity":' || to_json(p_fee_source_identity)::TEXT ||
        ',"feeSourceSchemaVersion":' || to_json(p_fee_source_schema_version)::TEXT ||
        ',"feeMaximumAgeMs":' || p_fee_maximum_age_ms::TEXT ||
        ',"balanceSourceIdentity":' || to_json(p_balance_source_identity)::TEXT ||
        ',"balanceSourceSchemaVersion":' || to_json(p_balance_source_schema_version)::TEXT ||
        ',"balanceMaximumAgeMs":' || p_balance_maximum_age_ms::TEXT ||
        ',"clockSourceIdentity":' || to_json(p_clock_source_identity)::TEXT ||
        ',"clockSourceSchemaVersion":' || to_json(p_clock_source_schema_version)::TEXT ||
        ',"clockMaximumAgeMs":' || p_clock_maximum_age_ms::TEXT ||
        ',"signedTimestampSource":' || to_json(p_signed_timestamp_source)::TEXT ||
        ',"maximumToleratedSkewMs":' || p_maximum_tolerated_skew_ms::TEXT ||
        ',"endpointPolicyVersion":' || to_json(p_endpoint_policy_version)::TEXT ||
        ',"endpointPolicyDigest":' || to_json(p_endpoint_policy_digest)::TEXT ||
        ',"providerContractIdentity":' || to_json(p_provider_contract_identity)::TEXT ||
        ',"providerArtifactDigest":' || to_json(p_provider_artifact_digest)::TEXT ||
        ',"workerIdentity":' || to_json(p_worker_identity)::TEXT ||
        ',"workerReleaseDigest":' || to_json(p_worker_release_digest)::TEXT || '}'
    FROM live_sessions s WHERE s.session_id = p_session_id
$$;

CREATE FUNCTION execution_scope_hash(p_session_id uuid, p_instrument_metadata_digest text, p_instrument_source_identity text, p_instrument_source_schema_version text, p_instrument_maximum_age_ms bigint, p_fee_schedule_digest text, p_fee_tier text, p_fee_evidence_class text, p_fee_source_identity text, p_fee_source_schema_version text, p_fee_maximum_age_ms bigint, p_balance_source_identity text, p_balance_source_schema_version text, p_balance_maximum_age_ms bigint, p_clock_source_identity text, p_clock_source_schema_version text, p_clock_maximum_age_ms bigint, p_signed_timestamp_source text, p_maximum_tolerated_skew_ms bigint, p_endpoint_policy_version text, p_endpoint_policy_digest text, p_provider_contract_identity text, p_provider_artifact_digest text, p_worker_identity text, p_worker_release_digest text) RETURNS text
    LANGUAGE sql STABLE STRICT
    AS $$
    SELECT encode(digest(convert_to(execution_scope_canonical_payload(
        p_session_id, p_instrument_metadata_digest, p_instrument_source_identity,
        p_instrument_source_schema_version, p_instrument_maximum_age_ms,
        p_fee_schedule_digest, p_fee_tier, p_fee_evidence_class, p_fee_source_identity,
        p_fee_source_schema_version, p_fee_maximum_age_ms, p_balance_source_identity,
        p_balance_source_schema_version, p_balance_maximum_age_ms, p_clock_source_identity,
        p_clock_source_schema_version, p_clock_maximum_age_ms, p_signed_timestamp_source,
        p_maximum_tolerated_skew_ms, p_endpoint_policy_version, p_endpoint_policy_digest,
        p_provider_contract_identity, p_provider_artifact_digest, p_worker_identity,
        p_worker_release_digest
    ), 'UTF8'), 'sha256'), 'hex')
$$;

CREATE FUNCTION guard_canonical_account_compatibility_bridge() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_account accounts%ROWTYPE;
    v_expected_code TEXT;
BEGIN
    IF (TG_OP = 'INSERT' AND NEW.legacy_account_id IS NOT NULL)
        OR (TG_OP = 'UPDATE' AND OLD.legacy_account_id IS NULL
            AND NEW.legacy_account_id IS NOT NULL) THEN
        SELECT * INTO STRICT v_account FROM accounts
        WHERE account_id=NEW.legacy_account_id FOR KEY SHARE;
        IF NEW.trade_env = 'LIVE' AND NEW.exchange_code = 'OKX' THEN
            v_expected_code := canonical_legacy_account_code(NEW.exchange_account_id);
        ELSIF NEW.trade_env = 'SIM' AND NEW.exchange_code IN ('OKX', 'BINANCE') THEN
            v_expected_code := 'nq-' || lower(NEW.exchange_code) || '-sim-'
                || NEW.exchange_account_id::TEXT;
        ELSE
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='canonical legacy account bridge is invalid';
        END IF;
        IF NEW.status <> 'ACTIVE' OR v_account.account_code <> v_expected_code
            OR v_account.venue <> NEW.exchange_code OR v_account.status <> 'ACTIVE' THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='canonical legacy account bridge is invalid';
        END IF;
    ELSIF TG_OP = 'UPDATE' AND OLD.legacy_account_id IS NOT NULL
        AND NEW.legacy_account_id IS DISTINCT FROM OLD.legacy_account_id THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='canonical legacy account bridge is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_controlled_execution_lease_authority() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_session live_sessions%ROWTYPE;
    v_authority operator_execution_authorities%ROWTYPE;
BEGIN
    SELECT * INTO STRICT v_session FROM live_sessions
    WHERE session_id = NEW.live_session_id FOR KEY SHARE;
    IF v_session.authority_type = 'STRATEGY' THEN
        IF NEW.operator_execution_authority_id IS NOT NULL THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='strategy lease cannot bind operator authority';
        END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO STRICT v_authority FROM operator_execution_authorities
    WHERE authority_id = NEW.operator_execution_authority_id FOR KEY SHARE;
    IF NEW.operator_execution_authority_id IS DISTINCT FROM v_session.operator_execution_authority_id
        OR v_authority.status <> 'ACTIVE'
        OR transaction_timestamp() < v_authority.valid_from
        OR transaction_timestamp() >= v_authority.expires_at
        OR NEW.created_by <> v_authority.owner_user_id
        OR NEW.max_notional > v_authority.max_notional
        OR NEW.valid_from < v_authority.valid_from
        OR NEW.expires_at > v_authority.expires_at THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='controlled execution lease exceeds operator authority';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_controlled_execution_lease_update() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_legal BOOLEAN;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='controlled execution execution lease cannot be deleted';
    END IF;
    IF OLD.lease_id IS DISTINCT FROM NEW.lease_id
        OR OLD.live_session_id IS DISTINCT FROM NEW.live_session_id
        OR OLD.operator_execution_authority_id IS DISTINCT FROM NEW.operator_execution_authority_id
        OR OLD.binding_id IS DISTINCT FROM NEW.binding_id
        OR OLD.binding_digest IS DISTINCT FROM NEW.binding_digest
        OR OLD.max_notional IS DISTINCT FROM NEW.max_notional
        OR OLD.valid_from IS DISTINCT FROM NEW.valid_from
        OR OLD.expires_at IS DISTINCT FROM NEW.expires_at
        OR OLD.created_by IS DISTINCT FROM NEW.created_by
        OR OLD.created_at IS DISTINCT FROM NEW.created_at
        OR OLD.predecessor_lease_id IS DISTINCT FROM NEW.predecessor_lease_id
        OR OLD.recovery_decision_id IS DISTINCT FROM NEW.recovery_decision_id
        OR OLD.replacement_ordinal IS DISTINCT FROM NEW.replacement_ordinal
        OR OLD.replacement_reason IS DISTINCT FROM NEW.replacement_reason THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='controlled execution execution lease identity is immutable';
    END IF;
    IF NEW.version <> OLD.version + 1 OR NEW.updated_at < OLD.updated_at THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='controlled execution execution lease version is invalid';
    END IF;
    v_legal := (OLD.status, NEW.status) IN (
        ('CREATED','ACTIVE'),('CREATED','FAILED'),('CREATED','EXPIRED'),
        ('ACTIVE','CONSUMED'),('ACTIVE','FAILED'),('ACTIVE','EXPIRED'),
        ('CONSUMED','CLOSED'),('CONSUMED','FAILED'),('CONSUMED','EXPIRED')
    );
    IF NOT v_legal THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='illegal controlled execution execution lease transition';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_execution_instrument_item_evidence_insert() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_schema_version VARCHAR(64);
BEGIN
    SELECT observation_schema_version INTO v_schema_version
    FROM execution_prerequisite_observations
    WHERE observation_id = NEW.observation_id AND observation_type = 'INSTRUMENT_METADATA';
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE='23503', MESSAGE='instrument observation parent does not exist';
    END IF;
    IF v_schema_version = 'instrument-metadata-observation.v1'
        AND NEW.minimum_order_value_evidence_class <> 'LEGACY_MINIMUM_EVIDENCE_REQUIRED' THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='v1 instrument items require legacy evidence marking';
    END IF;
    IF v_schema_version = 'instrument-metadata-observation.v2'
        AND NEW.minimum_order_value_evidence_class = 'LEGACY_MINIMUM_EVIDENCE_REQUIRED' THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='v2 instrument items cannot use legacy evidence marking';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_execution_instrument_observation_schema_insert() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.observation_type = 'INSTRUMENT_METADATA'
        AND NEW.observation_schema_version <> 'instrument-metadata-observation.v2' THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='new instrument observations must use instrument-metadata-observation.v2';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_execution_intent_insert() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.state <> 'CREATED' OR NEW.version <> 1 OR NEW.send_started_at IS NOT NULL
        OR NEW.claimed_by IS NOT NULL OR NEW.claim_token IS NOT NULL
        OR NEW.claimed_at IS NOT NULL OR NEW.lease_expires_at IS NOT NULL THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='new execution intent must start unclaimed';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_execution_intent_update() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE v_legal BOOLEAN;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='execution intent facts cannot be deleted';
    END IF;
    IF OLD.intent_id IS DISTINCT FROM NEW.intent_id OR OLD.session_id IS DISTINCT FROM NEW.session_id
        OR OLD.sequence IS DISTINCT FROM NEW.sequence OR OLD.action IS DISTINCT FROM NEW.action
        OR OLD.symbol IS DISTINCT FROM NEW.symbol OR OLD.side IS DISTINCT FROM NEW.side
        OR OLD.order_type IS DISTINCT FROM NEW.order_type OR OLD.quantity IS DISTINCT FROM NEW.quantity
        OR OLD.limit_price IS DISTINCT FROM NEW.limit_price
        OR OLD.payload_hash_schema_version IS DISTINCT FROM NEW.payload_hash_schema_version
        OR OLD.payload_hash IS DISTINCT FROM NEW.payload_hash
        OR OLD.client_order_id IS DISTINCT FROM NEW.client_order_id
        OR OLD.local_order_id IS DISTINCT FROM NEW.local_order_id
        OR OLD.created_at IS DISTINCT FROM NEW.created_at THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='execution intent business facts are immutable';
    END IF;
    IF NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='execution intent version must increment exactly once';
    END IF;
    IF OLD.send_started_at IS NOT NULL AND NEW.send_started_at IS DISTINCT FROM OLD.send_started_at THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='send_started_at is immutable after first bind';
    END IF;
    IF OLD.state = 'CLAIMED' AND NEW.state = 'CLAIMED' THEN
        IF OLD.lease_expires_at >= CURRENT_TIMESTAMP
            OR NEW.claim_token IS NOT DISTINCT FROM OLD.claim_token THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='execution intent reclaim requires an expired lease and new token';
        END IF;
    ELSIF OLD.state = 'CLAIMED' AND NEW.state = 'SEND_STARTED' THEN
        IF OLD.lease_expires_at <= CURRENT_TIMESTAMP
            OR NEW.claimed_by IS DISTINCT FROM OLD.claimed_by
            OR NEW.claim_token IS DISTINCT FROM OLD.claim_token
            OR NEW.claimed_at IS DISTINCT FROM OLD.claimed_at
            OR NEW.lease_expires_at IS DISTINCT FROM OLD.lease_expires_at THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='execution intent send must retain the current claim';
        END IF;
    ELSIF OLD.state IN ('SEND_STARTED','SEND_SUCCEEDED','UNKNOWN','FAILED','RECONCILED') THEN
        IF NEW.claimed_by IS DISTINCT FROM OLD.claimed_by
            OR NEW.claim_token IS DISTINCT FROM OLD.claim_token
            OR NEW.claimed_at IS DISTINCT FROM OLD.claimed_at
            OR NEW.lease_expires_at IS DISTINCT FROM OLD.lease_expires_at THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='execution intent claim is immutable after send starts';
        END IF;
    END IF;
    IF NEW.state = 'CLAIMED'
        AND NEW.lease_expires_at > CURRENT_TIMESTAMP + INTERVAL '5 minutes' THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='execution intent lease exceeds the hard upper bound';
    END IF;
    v_legal := (OLD.state, NEW.state) IN (
        ('CREATED','CLAIMED'),('CREATED','CANCELLED'),('CLAIMED','CLAIMED'),
        ('CLAIMED','SEND_STARTED'),('CLAIMED','CANCELLED'),
        ('SEND_STARTED','SEND_SUCCEEDED'),('SEND_STARTED','UNKNOWN'),('SEND_STARTED','FAILED'),
        ('UNKNOWN','RECONCILED')
    );
    IF NOT v_legal THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='illegal execution intent transition';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_execution_market_snapshot_insert() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_expected_symbols TEXT[];
BEGIN
    IF NEW.observation_type <> 'MARKET_SNAPSHOT' THEN
        RETURN NEW;
    END IF;
    SELECT session.symbol_allowlist INTO v_expected_symbols
    FROM execution_scope_bindings scope
    JOIN live_sessions session ON session.session_id = scope.session_id
    WHERE scope.execution_scope_id = NEW.execution_scope_id;
    IF NEW.source_identity <> 'OKX_MARKET_TICKER'
        OR NEW.source_schema_version <> 'okx-market-ticker.v5'
        OR NOT NEW.market_instrument = ANY(v_expected_symbols)
        OR NEW.market_snapshot_digest IS DISTINCT FROM execution_market_snapshot_digest(
            NEW.market_instrument, NEW.best_ask, NEW.observed_at,
            NEW.source_identity, NEW.source_schema_version) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='market snapshot is outside canonical controlled execution scope';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_execution_prerequisite_observation_insert() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_scope execution_scope_bindings%ROWTYPE;
    v_quote_currency VARCHAR(16);
BEGIN
    SELECT * INTO v_scope FROM execution_scope_bindings
    WHERE execution_scope_id = NEW.execution_scope_id FOR KEY SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE='23503', MESSAGE='controlled execution scope does not exist';
    END IF;
    IF NEW.recorder_identity <> v_scope.worker_identity THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='observation recorder does not match admitted worker';
    END IF;
    IF NEW.observed_at > NEW.recorded_at + (v_scope.maximum_tolerated_skew_ms * INTERVAL '1 millisecond')
        OR NEW.recorded_at > transaction_timestamp() + (v_scope.maximum_tolerated_skew_ms * INTERVAL '1 millisecond') THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='observation timestamp exceeds tolerated future skew';
    END IF;
    IF NEW.observation_type = 'INSTRUMENT_METADATA' THEN
        IF NEW.source_identity <> v_scope.instrument_source_identity
            OR NEW.source_schema_version <> v_scope.instrument_source_schema_version
            OR NEW.instrument_metadata_digest <> v_scope.instrument_metadata_digest THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='instrument observation is outside immutable controlled execution scope';
        END IF;
    ELSIF NEW.observation_type = 'FEE_SCHEDULE' THEN
        IF NEW.source_identity <> v_scope.fee_source_identity
            OR NEW.source_schema_version <> v_scope.fee_source_schema_version
            OR NEW.fee_schedule_digest <> v_scope.fee_schedule_digest
            OR NEW.fee_tier <> v_scope.fee_tier
            OR NEW.fee_evidence_class <> v_scope.fee_evidence_class THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='fee observation is outside immutable controlled execution scope';
        END IF;
    ELSIF NEW.observation_type = 'BALANCE_SNAPSHOT' THEN
        SELECT CASE WHEN session.authority_type = 'STRATEGY'
                    THEN risk.quote_currency ELSE split_part(authority.instrument, '-', 2) END
        INTO v_quote_currency
        FROM execution_scope_bindings scope
        JOIN live_sessions session ON session.session_id = scope.session_id
        LEFT JOIN risk_limit_sets risk ON risk.risk_limit_set_id = session.risk_limit_set_id
        LEFT JOIN operator_execution_authorities authority
          ON authority.authority_id = session.operator_execution_authority_id
        WHERE scope.execution_scope_id = NEW.execution_scope_id;
        IF NEW.source_identity <> v_scope.balance_source_identity
            OR NEW.source_schema_version <> v_scope.balance_source_schema_version
            OR NEW.balance_currency <> v_quote_currency THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='balance observation is outside immutable controlled execution scope';
        END IF;
    ELSIF NEW.observation_type = 'CLOCK_SYNC' THEN
        IF NEW.source_identity <> v_scope.clock_source_identity
            OR NEW.source_schema_version <> v_scope.clock_source_schema_version
            OR NEW.signed_timestamp_source <> v_scope.signed_timestamp_source
            OR abs(NEW.observed_skew_ms) > v_scope.maximum_tolerated_skew_ms THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='clock observation is outside immutable controlled execution scope';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_execution_recovery_decision_insert() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_lease controlled_execution_leases%ROWTYPE;
    v_place INTEGER;
    v_send INTEGER;
    v_intent INTEGER;
    v_receipt INTEGER;
    v_order INTEGER;
    v_trade INTEGER;
    v_ledger INTEGER;
BEGIN
    SELECT * INTO STRICT v_lease FROM controlled_execution_leases
    WHERE lease_id=NEW.predecessor_lease_id FOR UPDATE;
    IF NEW.decision <> 'PRE_PLACE_REGENERATION_ALLOWED'
        OR v_lease.live_session_id <> NEW.predecessor_session_id
        OR v_lease.created_by <> NEW.decided_by
        OR v_lease.status NOT IN ('EXPIRED','FAILED')
        OR v_lease.consumed_at IS NOT NULL
        OR EXISTS (SELECT 1 FROM controlled_execution_leases
                   WHERE status IN ('CREATED','ACTIVE','CONSUMED'))
        OR EXISTS (SELECT 1 FROM controlled_execution_leases
                   WHERE predecessor_lease_id=v_lease.lease_id)
        OR NOT controlled_execution_boundary_is_empty() THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='PRE_PLACE_REGENERATION_FORBIDDEN';
    END IF;

    SELECT count(*) INTO v_place
    FROM controlled_execution_lease_intents WHERE action='PLACE';
    SELECT count(*) FILTER (WHERE intent.send_started_at IS NOT NULL),
           count(DISTINCT intent.intent_id),count(DISTINCT receipt.receipt_id),
           count(DISTINCT local_order.order_id),count(DISTINCT trade.trade_id),
           count(DISTINCT ledger.entry_id)
    INTO v_send,v_intent,v_receipt,v_order,v_trade,v_ledger
    FROM live_sessions session
    LEFT JOIN execution_intents intent ON intent.session_id=session.session_id
    LEFT JOIN execution_receipts receipt ON receipt.intent_id=intent.intent_id
    LEFT JOIN orders local_order ON local_order.order_id=intent.local_order_id
    LEFT JOIN trades trade ON trade.order_id=local_order.order_id
    LEFT JOIN ledger_entries ledger
      ON ledger.ref_id=local_order.order_id OR ledger.ref_id=trade.trade_id
    WHERE session.authority_type='OPERATOR_CONTROLLED_EXECUTION';
    v_place:=COALESCE(v_place,0); v_send:=COALESCE(v_send,0);
    v_intent:=COALESCE(v_intent,0); v_receipt:=COALESCE(v_receipt,0);
    v_order:=COALESCE(v_order,0); v_trade:=COALESCE(v_trade,0);
    v_ledger:=COALESCE(v_ledger,0);
    IF (NEW.place_intent_count,NEW.send_started_count,NEW.execution_intent_count,
        NEW.execution_receipt_count,NEW.order_count,NEW.trade_count,NEW.ledger_count)
       IS DISTINCT FROM (v_place,v_send,v_intent,v_receipt,v_order,v_trade,v_ledger) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='PRE_PLACE_REGENERATION_PROOF_MISMATCH';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_execution_replacement_lease_insert() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_predecessor controlled_execution_leases%ROWTYPE;
    v_old_session live_sessions%ROWTYPE;
    v_new_session live_sessions%ROWTYPE;
    v_decision execution_pre_place_recovery_decisions%ROWTYPE;
BEGIN
    IF NEW.predecessor_lease_id IS NULL THEN
        IF NEW.replacement_ordinal <> 0 OR EXISTS (SELECT 1 FROM controlled_execution_leases) THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='original controlled execution lease already exists';
        END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO STRICT v_predecessor FROM controlled_execution_leases
    WHERE lease_id=NEW.predecessor_lease_id FOR UPDATE;
    SELECT * INTO STRICT v_decision FROM execution_pre_place_recovery_decisions
    WHERE decision_id=NEW.recovery_decision_id FOR KEY SHARE;
    SELECT * INTO STRICT v_old_session FROM live_sessions
    WHERE session_id=v_predecessor.live_session_id FOR KEY SHARE;
    SELECT * INTO STRICT v_new_session FROM live_sessions
    WHERE session_id=NEW.live_session_id FOR KEY SHARE;
    IF v_decision.predecessor_lease_id <> v_predecessor.lease_id
        OR v_decision.predecessor_session_id <> v_old_session.session_id
        OR v_decision.decision <> 'PRE_PLACE_REGENERATION_ALLOWED'
        OR v_predecessor.status NOT IN ('EXPIRED','FAILED')
        OR v_predecessor.consumed_at IS NOT NULL
        OR v_old_session.state NOT IN ('LIVE_RECONCILED','REJECTED','FAILED','KILLED')
        OR v_old_session.authority_type <> 'OPERATOR_CONTROLLED_EXECUTION'
        OR v_new_session.authority_type <> 'OPERATOR_CONTROLLED_EXECUTION'
        OR v_old_session.owner_id <> v_new_session.owner_id
        OR v_old_session.exchange_account_id <> v_new_session.exchange_account_id
        OR v_old_session.credential_reference <> v_new_session.credential_reference
        OR v_old_session.symbol_allowlist <> v_new_session.symbol_allowlist
        OR v_old_session.capital_cap <> v_new_session.capital_cap
        OR v_predecessor.max_notional <> NEW.max_notional
        OR v_predecessor.created_by <> NEW.created_by
        OR NEW.replacement_ordinal <> v_predecessor.replacement_ordinal + 1
        OR NEW.replacement_reason <> 'PRE_PLACE_TERMINAL_REGENERATION'
        OR EXISTS (SELECT 1 FROM controlled_execution_leases
                   WHERE status IN ('CREATED','ACTIVE','CONSUMED'))
        OR EXISTS (SELECT 1 FROM controlled_execution_leases
                   WHERE predecessor_lease_id=v_predecessor.lease_id)
        OR NOT controlled_execution_boundary_is_empty() THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='terminal lease regeneration proof failed';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_execution_scope_insert() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_state VARCHAR(32);
    v_hash TEXT;
BEGIN
    SELECT state INTO v_state FROM live_sessions WHERE session_id = NEW.session_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE='23503', MESSAGE='controlled execution scope session does not exist';
    END IF;
    IF v_state <> 'APPROVAL_PENDING'
        OR EXISTS (SELECT 1 FROM operator_approvals WHERE session_id = NEW.session_id)
        OR EXISTS (SELECT 1 FROM execution_intents WHERE session_id = NEW.session_id) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='controlled execution scope cannot be bound after approval or execution';
    END IF;
    v_hash := execution_scope_hash(
        NEW.session_id, NEW.instrument_metadata_digest, NEW.instrument_source_identity,
        NEW.instrument_source_schema_version, NEW.instrument_maximum_age_ms,
        NEW.fee_schedule_digest, NEW.fee_tier, NEW.fee_evidence_class,
        NEW.fee_source_identity, NEW.fee_source_schema_version, NEW.fee_maximum_age_ms,
        NEW.balance_source_identity, NEW.balance_source_schema_version, NEW.balance_maximum_age_ms,
        NEW.clock_source_identity, NEW.clock_source_schema_version, NEW.clock_maximum_age_ms,
        NEW.signed_timestamp_source, NEW.maximum_tolerated_skew_ms,
        NEW.endpoint_policy_version, NEW.endpoint_policy_digest,
        NEW.provider_contract_identity, NEW.provider_artifact_digest,
        NEW.worker_identity, NEW.worker_release_digest
    );
    IF v_hash IS NULL OR NEW.execution_scope_hash <> v_hash THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='controlled execution scope hash does not match canonical reconstruction';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_live_session_insert() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_authority operator_execution_authorities%ROWTYPE;
BEGIN
    IF NEW.state <> 'APPROVAL_PENDING' OR NEW.version <> 1 OR NEW.next_event_sequence <> 1 THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='new live session must start approval pending';
    END IF;
    IF NEW.authority_type = 'OPERATOR_CONTROLLED_EXECUTION' THEN
        SELECT * INTO v_authority FROM operator_execution_authorities
        WHERE authority_id = NEW.operator_execution_authority_id FOR KEY SHARE;
        IF NOT FOUND OR v_authority.status <> 'ACTIVE'
            OR transaction_timestamp() < v_authority.valid_from
            OR transaction_timestamp() >= v_authority.expires_at
            OR v_authority.owner_user_id <> NEW.owner_id
            OR v_authority.exchange_account_id <> NEW.exchange_account_id
            OR v_authority.credential_reference_id <> NEW.credential_reference
            OR NEW.symbol_allowlist <> ARRAY[v_authority.instrument]::TEXT[]
            OR NEW.capital_cap > v_authority.max_notional
            OR NEW.execution_window_start < v_authority.valid_from
            OR NEW.execution_window_end > v_authority.expires_at
            OR NEW.operator_execution_authority_digest <> v_authority.canonical_digest THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='operator execution session exceeds explicit authority';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_live_session_update() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_scope_changed BOOLEAN;
    v_only_sequence_changed BOOLEAN;
    v_legal_transition BOOLEAN;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='live session facts cannot be deleted';
    END IF;
    IF OLD.session_id IS DISTINCT FROM NEW.session_id
        OR OLD.owner_id IS DISTINCT FROM NEW.owner_id
        OR OLD.exchange_account_id IS DISTINCT FROM NEW.exchange_account_id
        OR OLD.venue IS DISTINCT FROM NEW.venue
        OR OLD.authority_type IS DISTINCT FROM NEW.authority_type
        OR OLD.operator_execution_authority_id IS DISTINCT FROM NEW.operator_execution_authority_id
        OR OLD.operator_execution_authority_digest IS DISTINCT FROM NEW.operator_execution_authority_digest
        OR OLD.strategy_release_id IS DISTINCT FROM NEW.strategy_release_id
        OR OLD.created_by IS DISTINCT FROM NEW.created_by
        OR OLD.created_at IS DISTINCT FROM NEW.created_at THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='live session identity is immutable';
    END IF;
    v_scope_changed := OLD.release_digest IS DISTINCT FROM NEW.release_digest
        OR OLD.release_admission_revision IS DISTINCT FROM NEW.release_admission_revision
        OR OLD.risk_limit_set_id IS DISTINCT FROM NEW.risk_limit_set_id
        OR OLD.risk_limit_set_digest IS DISTINCT FROM NEW.risk_limit_set_digest
        OR OLD.credential_reference IS DISTINCT FROM NEW.credential_reference
        OR OLD.symbol_allowlist IS DISTINCT FROM NEW.symbol_allowlist
        OR OLD.capital_cap IS DISTINCT FROM NEW.capital_cap
        OR OLD.execution_window_start IS DISTINCT FROM NEW.execution_window_start
        OR OLD.execution_window_end IS DISTINCT FROM NEW.execution_window_end
        OR OLD.approval_scope_schema_version IS DISTINCT FROM NEW.approval_scope_schema_version;
    v_only_sequence_changed := NEW.next_event_sequence = OLD.next_event_sequence + 1
        AND NEW.state = OLD.state AND NEW.version = OLD.version
        AND NOT v_scope_changed AND NEW.approval_scope_hash = OLD.approval_scope_hash;
    IF v_only_sequence_changed THEN RETURN NEW; END IF;
    IF NEW.version <> OLD.version + 1 OR NEW.next_event_sequence <> OLD.next_event_sequence THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='live session version or event sequence is invalid';
    END IF;
    IF v_scope_changed THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='live session scope is immutable';
    ELSIF NEW.approval_scope_hash IS DISTINCT FROM OLD.approval_scope_hash THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='scope hash cannot change without scope mutation';
    END IF;
    IF NEW.state = OLD.state THEN RETURN NEW; END IF;
    IF OLD.state IN ('REJECTED','FAILED','KILLED','LIVE_RECONCILED') THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='terminal live session cannot transition';
    END IF;
    v_legal_transition := (OLD.state, NEW.state) IN (
        ('APPROVAL_PENDING','APPROVED'),('APPROVAL_PENDING','REJECTED'),
        ('APPROVED','APPROVAL_PENDING'),('APPROVED','LIVE_WARMUP'),
        ('LIVE_WARMUP','LIVE_ACTIVE'),('LIVE_WARMUP','LIVE_PAUSED'),
        ('LIVE_ACTIVE','LIVE_PAUSED'),('LIVE_PAUSED','LIVE_ACTIVE'),
        ('LIVE_ACTIVE','LIVE_STOPPED'),('LIVE_PAUSED','LIVE_STOPPED'),
        ('LIVE_STOPPED','LIVE_RECONCILING'),('LIVE_RECONCILING','LIVE_RECONCILED'),
        ('LIVE_RECONCILING','RECONCILIATION_BLOCKED'),
        ('RECONCILIATION_BLOCKED','LIVE_RECONCILED')
    ) OR NEW.state = 'KILLED'
      OR (NEW.state = 'FAILED' AND OLD.state IN ('APPROVED','LIVE_WARMUP','LIVE_ACTIVE','LIVE_PAUSED'));
    IF NOT v_legal_transition THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='illegal live session transition';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_operator_execution_approval_insert() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_session live_sessions%ROWTYPE;
BEGIN
    SELECT * INTO v_session FROM live_sessions WHERE session_id = NEW.session_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE='23503', MESSAGE='approval session does not exist';
    END IF;
    IF NEW.approver_id = v_session.created_by
        OR NEW.expires_at > v_session.execution_window_end
        OR NEW.release_digest <> v_session.release_digest
        OR NEW.risk_limit_set_digest <> v_session.risk_limit_set_digest THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='approval violates session separation, expiry, or digest binding';
    END IF;
    IF NEW.scope_schema_version = 'execution-scope.v1' THEN
        IF v_session.state <> 'APPROVAL_PENDING'
            OR NOT EXISTS (
                SELECT 1 FROM execution_scope_bindings scope
                WHERE scope.session_id = NEW.session_id
                  AND scope.execution_scope_id = NEW.execution_scope_id
                  AND scope.execution_scope_hash = NEW.scope_hash
            ) THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='controlled execution approval is not bound to the exact pending controlled execution scope';
        END IF;
    ELSIF EXISTS (SELECT 1 FROM execution_scope_bindings WHERE session_id = NEW.session_id) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='legacy approval cannot authorize a materialized controlled execution scope';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION operator_execution_authority_digest(p_authority_id uuid, p_owner_user_id bigint, p_exchange_account_id bigint, p_credential_reference_id bigint, p_instrument text, p_side text, p_order_type text, p_max_notional numeric, p_max_place_count integer, p_max_cancel_count integer, p_transfer_allowed boolean, p_withdraw_allowed boolean, p_valid_from timestamp with time zone, p_expires_at timestamp with time zone, p_created_by bigint, p_created_at timestamp with time zone) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT
    AS $$
    SELECT encode(digest(convert_to(
        '{"schemaVersion":"operator-execution-authority.v1"' ||
        ',"authorityId":' || to_json(p_authority_id::TEXT)::TEXT ||
        ',"ownerUserId":' || p_owner_user_id::TEXT ||
        ',"exchangeAccountId":' || p_exchange_account_id::TEXT ||
        ',"credentialReferenceId":' || p_credential_reference_id::TEXT ||
        ',"instrument":' || to_json(p_instrument)::TEXT ||
        ',"side":' || to_json(p_side)::TEXT ||
        ',"orderType":' || to_json(p_order_type)::TEXT ||
        ',"maxNotional":' || to_json((p_max_notional::NUMERIC(38,8))::TEXT)::TEXT ||
        ',"maxPlaceCount":' || p_max_place_count::TEXT ||
        ',"maxCancelCount":' || p_max_cancel_count::TEXT ||
        ',"transferAllowed":' || lower(p_transfer_allowed::TEXT) ||
        ',"withdrawAllowed":' || lower(p_withdraw_allowed::TEXT) ||
        ',"validFrom":' || execution_evidence_instant_canonical(p_valid_from) ||
        ',"expiresAt":' || execution_evidence_instant_canonical(p_expires_at) ||
        ',"createdBy":' || p_created_by::TEXT ||
        ',"createdAt":' || execution_evidence_instant_canonical(p_created_at) || '}',
        'UTF8'), 'sha256'), 'hex')
$$;

CREATE FUNCTION guard_operator_execution_authority_insert() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_reference_count INTEGER;
BEGIN
    IF NEW.status <> 'ACTIVE' OR NEW.version <> 1 OR NEW.closed_at IS NOT NULL
        OR NEW.updated_at <> NEW.created_at
        OR transaction_timestamp() < NEW.valid_from OR transaction_timestamp() >= NEW.expires_at
        OR NEW.canonical_digest IS DISTINCT FROM operator_execution_authority_digest(
            NEW.authority_id, NEW.owner_user_id, NEW.exchange_account_id,
            NEW.credential_reference_id, NEW.instrument, NEW.side, NEW.order_type,
            NEW.max_notional, NEW.max_place_count, NEW.max_cancel_count,
            NEW.transfer_allowed, NEW.withdraw_allowed, NEW.valid_from, NEW.expires_at,
            NEW.created_by, NEW.created_at) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='operator execution authority is not canonical';
    END IF;
    SELECT count(*) INTO v_reference_count
    FROM exchange_accounts account
    JOIN exchange_account_credentials credential
      ON credential.credential_id = NEW.credential_reference_id
     AND credential.exchange_account_id = account.exchange_account_id
    WHERE account.exchange_account_id = NEW.exchange_account_id
      AND account.owner_user_id = NEW.owner_user_id
      AND account.exchange_code = 'OKX' AND account.trade_env = 'LIVE' AND account.status = 'ACTIVE'
      AND credential.credential_type = 'OKX_API_V5'
      AND credential.credential_status = 'ACTIVE' AND credential.is_active = TRUE
      AND credential.verification_status = 'VERIFIED'
      AND credential.permission_probe_status = 'SUCCEEDED'
      AND credential.permission_scope = 'TRADE'
      AND credential.withdraw_enabled = FALSE
      AND credential.ip_allowlist_probe_status = 'PASSED'
      AND credential.last_permission_probe_at IS NOT NULL
      AND credential.last_permission_probe_at <= NEW.created_at + INTERVAL '5 seconds'
      AND credential.last_permission_probe_at + INTERVAL '1 minute' >= NEW.created_at
      AND credential.revoked_at IS NULL AND credential.rotated_at IS NULL;
    IF v_reference_count <> 1 THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='operator execution authority account reference is invalid';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_operator_execution_authority_update() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='operator execution authority cannot be deleted';
    END IF;
    IF OLD.authority_id IS DISTINCT FROM NEW.authority_id
        OR OLD.owner_user_id IS DISTINCT FROM NEW.owner_user_id
        OR OLD.exchange_account_id IS DISTINCT FROM NEW.exchange_account_id
        OR OLD.credential_reference_id IS DISTINCT FROM NEW.credential_reference_id
        OR OLD.instrument IS DISTINCT FROM NEW.instrument
        OR OLD.side IS DISTINCT FROM NEW.side
        OR OLD.order_type IS DISTINCT FROM NEW.order_type
        OR OLD.max_notional IS DISTINCT FROM NEW.max_notional
        OR OLD.max_place_count IS DISTINCT FROM NEW.max_place_count
        OR OLD.max_cancel_count IS DISTINCT FROM NEW.max_cancel_count
        OR OLD.transfer_allowed IS DISTINCT FROM NEW.transfer_allowed
        OR OLD.withdraw_allowed IS DISTINCT FROM NEW.withdraw_allowed
        OR OLD.valid_from IS DISTINCT FROM NEW.valid_from
        OR OLD.expires_at IS DISTINCT FROM NEW.expires_at
        OR OLD.created_by IS DISTINCT FROM NEW.created_by
        OR OLD.created_at IS DISTINCT FROM NEW.created_at
        OR OLD.canonical_digest IS DISTINCT FROM NEW.canonical_digest THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='operator execution authority scope is immutable';
    END IF;
    IF OLD.status <> 'ACTIVE' OR NEW.status NOT IN ('CLOSED','EXPIRED')
        OR NEW.version <> OLD.version + 1 OR NEW.updated_at < OLD.updated_at
        OR NEW.closed_at IS NULL OR NEW.updated_at <> NEW.closed_at
        OR NEW.closed_at < OLD.created_at
        OR (NEW.status = 'EXPIRED' AND NEW.closed_at < OLD.expires_at) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='operator execution authority transition is invalid';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_operator_execution_intent_count() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_limit INTEGER;
    v_status VARCHAR(16);
BEGIN
    SELECT CASE WHEN NEW.action = 'PLACE' THEN authority.max_place_count
                ELSE authority.max_cancel_count END, authority.status
    INTO v_limit, v_status
    FROM controlled_execution_leases lease
    JOIN operator_execution_authorities authority
      ON authority.authority_id = lease.operator_execution_authority_id
    WHERE lease.lease_id = NEW.lease_id;
    IF FOUND AND (v_status <> 'ACTIVE' OR v_limit <> 1 OR NOT EXISTS (
        SELECT 1 FROM operator_execution_authorities authority
        JOIN controlled_execution_leases lease
          ON lease.operator_execution_authority_id = authority.authority_id
        WHERE lease.lease_id = NEW.lease_id
          AND authority.valid_from <= transaction_timestamp()
          AND authority.expires_at > transaction_timestamp()
    ) OR EXISTS (
        SELECT 1 FROM controlled_execution_lease_intents
        WHERE lease_id = NEW.lease_id AND action = NEW.action
    )) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='operator execution action count exceeded';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION initialize_strategy_release_admission_state() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    INSERT INTO strategy_release_admission_state (publish_record_id)
    VALUES (NEW.publish_record_id)
    ON CONFLICT (publish_record_id) DO NOTHING;
    RETURN NEW;
END;
$$;

CREATE FUNCTION nq_admit_strategy_work(p_run text, p_strategy text, p_account bigint, p_venue text, p_env text, p_trigger text, p_config jsonb, p_request text, p_started timestamp with time zone, p_trace text, p_schedule text, p_due timestamp with time zone, p_definition_version integer, p_client text, p_symbol text, p_side text, p_type text, p_quantity numeric, p_price numeric, p_tif text, p_expected_cursor timestamp with time zone) RETURNS text
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
DECLARE d strategy_definitions%ROWTYPE; s strategy_schedules%ROWTYPE; existing text;
BEGIN
    IF NOT has_table_privilege(session_user,'strategy_runs','INSERT')
        OR NOT has_table_privilege(session_user,'strategy_runs','UPDATE') THEN
        RAISE EXCEPTION 'strategy writer privilege required' USING ERRCODE='42501';
    END IF;
    SELECT * INTO STRICT d FROM strategy_definitions WHERE strategy_id=p_strategy FOR UPDATE;
    IF p_schedule IS NOT NULL THEN
        SELECT * INTO STRICT s FROM strategy_schedules WHERE schedule_job_id=p_schedule FOR UPDATE;
        SELECT strategy_run_id INTO existing FROM strategy_runs WHERE strategy_id=p_strategy AND account_id=p_account
            AND admission_schedule_id=p_schedule AND admission_due_at=p_due;
        IF existing IS NOT NULL THEN RETURN existing; END IF;
        IF s.strategy_id<>p_strategy OR s.account_id<>p_account OR NOT s.enabled
            OR upper(btrim(s.exchange_code))<>p_venue OR s.trade_env<>p_env
            OR s.last_triggered_at IS DISTINCT FROM p_expected_cursor OR p_due IS NULL
            OR p_due<>date_trunc('second',p_due) OR p_due>p_started
            OR (s.last_triggered_at IS NOT NULL AND p_due<=s.last_triggered_at)
            OR p_trigger<>'SCHEDULER' THEN
            RAISE EXCEPTION 'stale or invalid strategy schedule admission' USING ERRCODE='23514';
        END IF;
        SELECT strategy_run_id INTO existing FROM strategy_runs WHERE strategy_id=p_strategy AND account_id=p_account
            AND admission_schedule_id IS NULL AND request_id IN (
                'req-schedule-'||p_schedule||'-window-'||(extract(epoch FROM p_due)*1000)::bigint::text,
                'req-schedule-'||p_schedule||'-request-'||(extract(epoch FROM p_due)*1000)::bigint::text,
                'req-schedule-'||p_schedule||'-strategy-'||p_strategy||'-'||(extract(epoch FROM p_due)*1000)::bigint::text)
            ORDER BY started_at,strategy_run_id LIMIT 1;
        IF existing IS NOT NULL THEN
            UPDATE strategy_schedules SET last_triggered_at=p_due,updated_at=CURRENT_TIMESTAMP WHERE schedule_job_id=p_schedule;
            INSERT INTO audit_logs(domain,action,actor_id,trace_id,detail_json) VALUES('STRATEGY','LEGACY_WINDOW_CONSUMPTION_CONFIRMED',
                existing,p_trace,jsonb_build_object('schedule_id',p_schedule,'due_at',p_due));
            RETURN existing;
        END IF;
    ELSIF p_due IS NOT NULL OR p_trigger<>'MANUAL' THEN
        RAISE EXCEPTION 'invalid manual admission identity' USING ERRCODE='23514';
    END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended(p_account::text||':'||p_client,51));
    SELECT strategy_run_id INTO existing FROM strategy_run_dispatch_work WHERE account_id=p_account AND client_order_id=p_client;
    IF existing IS NOT NULL THEN
        IF NOT EXISTS(SELECT 1 FROM strategy_runs r JOIN strategy_run_dispatch_work w USING(strategy_run_id)
            WHERE r.strategy_run_id=existing AND r.strategy_id=p_strategy AND r.account_id=p_account
            AND r.exchange_code=p_venue AND r.trade_env=p_env AND r.request_id=p_request
            AND r.admission_schedule_id IS NOT DISTINCT FROM p_schedule AND r.admission_due_at IS NOT DISTINCT FROM p_due
            AND ROW(w.symbol,w.side,w.order_type,w.quantity,w.price,w.time_in_force)
                IS NOT DISTINCT FROM ROW(p_symbol,p_side,p_type,p_quantity,p_price,p_tif)) THEN
            RAISE EXCEPTION 'strategy idempotency conflict' USING ERRCODE='23514';
        END IF;
        RETURN existing;
    END IF;
    IF NOT d.enabled OR ROW(d.account_id,upper(btrim(d.exchange_code)),d.trade_env,d.version,d.config_snapshot)
        IS DISTINCT FROM ROW(p_account,p_venue,p_env,p_definition_version,p_config)
        OR p_request IS NULL OR btrim(p_request)='' OR p_client IS DISTINCT FROM 'coid-'||p_request
        OR p_quantity<>round(p_quantity,8) OR (p_price IS NOT NULL AND p_price<>round(p_price,8)) THEN
        RAISE EXCEPTION 'stale definition or invalid immutable work' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM strategy_runs WHERE strategy_id=p_strategy AND status IN ('CREATED','DISPATCHING','RUNNING')) THEN
        RAISE EXCEPTION 'strategy_run_active' USING ERRCODE='55000';
    END IF;
    IF EXISTS(SELECT 1 FROM orders WHERE account_id=p_account AND client_order_id=p_client) THEN
        RAISE EXCEPTION 'strategy client belongs to an existing order' USING ERRCODE='23514';
    END IF;
    INSERT INTO strategy_runs(strategy_run_id,strategy_id,account_id,status,trigger_type,exchange_code,trade_env,
        config_snapshot,request_id,started_at,trace_id,admission_schedule_id,admission_due_at)
        VALUES(p_run,p_strategy,p_account,'CREATED',p_trigger,p_venue,p_env,p_config,p_request,p_started,p_trace,p_schedule,p_due);
    INSERT INTO strategy_run_dispatch_work(strategy_run_id,work_schema_version,definition_version,account_id,client_order_id,
        symbol,side,order_type,quantity,price,time_in_force) VALUES(p_run,1,p_definition_version,p_account,p_client,p_symbol,p_side,p_type,
        p_quantity,p_price,p_tif);
    IF p_schedule IS NOT NULL THEN
        UPDATE strategy_schedules SET last_triggered_at=p_due,updated_at=CURRENT_TIMESTAMP WHERE schedule_job_id=p_schedule;
    END IF;
    RETURN p_run;
END $$;

CREATE FUNCTION nq_backfill_ordinary_place_authorities(p_limit integer) RETURNS integer
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
DECLARE changed integer;
BEGIN
    IF p_limit IS NULL OR p_limit < 1 OR p_limit > 500 THEN
        RAISE EXCEPTION 'backfill limit must be 1..500' USING ERRCODE='22023';
    END IF;
    INSERT INTO ordinary_place_authorities(order_id,state,decided_at)
    SELECT o.order_id,'MAY_HAVE_ESCAPED',clock_timestamp() FROM orders o
    WHERE o.venue='OKX' AND NOT EXISTS(SELECT 1 FROM ordinary_place_authorities a WHERE a.order_id=o.order_id)
    ORDER BY o.order_id LIMIT p_limit ON CONFLICT DO NOTHING;
    GET DIAGNOSTICS changed = ROW_COUNT;
    RETURN changed;
END $$;

-- 回填属于受控运维入口，普通数据库角色不得通过 PUBLIC 获得执行权限。
REVOKE ALL ON FUNCTION nq_backfill_ordinary_place_authorities(integer) FROM PUBLIC;

CREATE FUNCTION nq_begin_strategy_dispatch(p_run text) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
DECLARE r strategy_runs%ROWTYPE;
BEGIN
    SELECT * INTO STRICT r FROM strategy_runs WHERE strategy_run_id=p_run FOR NO KEY UPDATE;
    IF NOT has_table_privilege(session_user,'orders','INSERT') THEN
        RAISE EXCEPTION 'ordinary writer privilege required' USING ERRCODE='42501';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM strategy_run_dispatch_work WHERE strategy_run_id=p_run) THEN RETURN false; END IF;
    IF r.status='CREATED' THEN
        UPDATE strategy_runs SET status='DISPATCHING' WHERE strategy_run_id=p_run AND status='CREATED';
        RETURN true;
    END IF;
    RETURN r.status='DISPATCHING';
END $$;

CREATE FUNCTION nq_strategy_execution_proof(p_order text) RETURNS TABLE(executed_quantity numeric, fill_count bigint, valid boolean)
    LANGUAGE sql STABLE
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
    SELECT COALESCE(sum(t.qty),0),count(t.trade_id),
        o.qty>0 AND o.qty<'NaN'::numeric AND NULLIF(btrim(o.external_order_id),'') IS NOT NULL
        AND count(t.trade_id)=count(DISTINCT (t.exchange,t.exchange_trade_id)) FILTER(WHERE t.trade_id IS NOT NULL)
        AND count(*) FILTER(WHERE t.trade_id IS NOT NULL AND (
            t.account_id IS DISTINCT FROM o.account_id OR t.symbol IS DISTINCT FROM o.symbol
            OR t.exchange IS DISTINCT FROM o.venue OR t.trade_env IS DISTINCT FROM o.trade_env
            OR t.strategy_run_id IS DISTINCT FROM o.strategy_run_id
            OR t.external_order_id IS DISTINCT FROM o.external_order_id
            OR t.exchange_trade_id IS NULL OR btrim(t.exchange_trade_id)='' OR t.qty IS NULL OR t.qty<=0))=0
    FROM orders o LEFT JOIN trades t ON t.order_id=o.order_id WHERE o.order_id=p_order GROUP BY o.order_id
$$;

CREATE FUNCTION nq_project_strategy_run(p_run text) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
DECLARE r strategy_runs%ROWTYPE; o orders%ROWTYPE; a text; executed numeric; fills bigint; proof_valid boolean;
    target text; explanation text;
BEGIN
    IF NOT has_table_privilege(session_user,'strategy_runs','UPDATE') THEN
        RAISE EXCEPTION 'strategy writer privilege required' USING ERRCODE='42501';
    END IF;
    SELECT * INTO STRICT r FROM strategy_runs WHERE strategy_run_id=p_run FOR NO KEY UPDATE;
    IF r.status NOT IN ('CREATED','DISPATCHING','RUNNING') THEN RETURN false; END IF;
    SELECT * INTO o FROM orders WHERE strategy_run_id=p_run FOR UPDATE;
    IF o.order_id IS NULL THEN
        SELECT normalization_rejection INTO explanation FROM strategy_run_dispatch_work WHERE strategy_run_id=p_run;
        IF explanation IS NULL THEN RETURN false; END IF;
        target:='FAILED'; explanation:='EXECUTION_NORMALIZATION_REJECTED/'||explanation;
    ELSE
    IF ROW(o.account_id,o.venue,o.trade_env)
        IS DISTINCT FROM ROW(r.account_id,upper(btrim(r.exchange_code)),r.trade_env) THEN RETURN false; END IF;
    SELECT state INTO a FROM ordinary_place_authorities WHERE order_id=o.order_id;
    SELECT * INTO executed,fills,proof_valid FROM nq_strategy_execution_proof(o.order_id);
    IF o.status='FILLED' AND proof_valid AND executed=o.qty THEN
        target:='SUCCEEDED'; explanation:=NULL;
    ELSIF o.status='CANCELLED' AND a='REVOKED_BEFORE_SEND'
        AND o.reason='ORDER_NOT_FOUND/OKX_51603' AND fills=0 THEN
        target:='FAILED'; explanation:='order_status=CANCELLED; no_send=REVOKED_BEFORE_SEND';
    ELSIF o.status='RISK_REJECTED' AND fills=0 AND (a='NOT_ARMED' OR o.venue<>'OKX')
        AND EXISTS(SELECT 1 FROM risk_events WHERE scope='ORDER' AND scope_id=o.order_id AND decision='REJECT') THEN
        target:='FAILED'; explanation:='order_status=RISK_REJECTED; reason='||COALESCE(o.reason,'');
    ELSIF o.status='REJECTED' AND fills=0 THEN
        target:='FAILED'; explanation:='order_status=REJECTED; reason='||COALESCE(o.reason,'');
    ELSIF o.status='CANCELLED' AND proof_valid AND executed<o.qty AND EXISTS(
        SELECT 1 FROM ordinary_order_cancel_finality f WHERE f.order_id=o.order_id AND f.executed_quantity=executed
            AND f.observed_order_version=o.version) THEN
        target:='FAILED'; explanation:='order_status=CANCELLED; executed_quantity='||executed::text;
    ELSIF r.status='DISPATCHING' AND o.status IN ('SENT','ACCEPTED','PARTIALLY_FILLED','CANCEL_REQUESTED','CANCEL_REJECTED') THEN
        target:='RUNNING'; explanation:=NULL;
    ELSE RETURN false;
    END IF;
    END IF;
    UPDATE strategy_runs SET status=target,finished_at=CASE WHEN target IN ('SUCCEEDED','FAILED') THEN clock_timestamp() ELSE NULL END,
        error_message=explanation WHERE strategy_run_id=p_run AND status=r.status;
    IF NOT FOUND THEN RETURN false; END IF;
    INSERT INTO audit_logs(domain,action,actor_id,trace_id,detail_json)
        VALUES('STRATEGY','STRATEGY_RUN_DURABLE_PROGRESSION',p_run,r.trace_id,
            jsonb_build_object('from',r.status,'to',target,'order_id',o.order_id,'order_version',o.version,
                'executed_quantity',executed,'reason',explanation));
    RETURN true;
END $$;

CREATE FUNCTION nq_bind_strategy_effective(p_run text, p_qty numeric, p_price numeric, p_rejection text) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
DECLARE r strategy_runs%ROWTYPE; w strategy_run_dispatch_work%ROWTYPE;
BEGIN
    IF NOT has_table_privilege(session_user,'orders','INSERT')
        OR NOT has_table_privilege(session_user,'strategy_runs','UPDATE') THEN
        RAISE EXCEPTION 'strategy preparation privilege required' USING ERRCODE='42501';
    END IF;
    SELECT * INTO STRICT r FROM strategy_runs WHERE strategy_run_id=p_run FOR NO KEY UPDATE;
    SELECT * INTO STRICT w FROM strategy_run_dispatch_work WHERE strategy_run_id=p_run;
    IF w.effective_quantity IS NOT NULL OR w.normalization_rejection IS NOT NULL THEN RETURN false; END IF;
    IF r.status<>'CREATED' OR (p_qty IS NULL AND p_rejection IS NULL)
        OR (p_qty IS NOT NULL AND p_qty<>round(p_qty,8)) OR (p_price IS NOT NULL AND p_price<>round(p_price,8)) THEN
        RAISE EXCEPTION 'invalid effective execution decision' USING ERRCODE='23514';
    END IF;
    UPDATE strategy_run_dispatch_work SET effective_quantity=p_qty,effective_price=p_price,normalization_rejection=p_rejection
        WHERE strategy_run_id=p_run;
    INSERT INTO audit_logs(domain,action,actor_id,trace_id,detail_json) VALUES('STRATEGY','STRATEGY_EFFECTIVE_EXECUTION_BOUND',p_run,r.trace_id,
        jsonb_build_object('requested_quantity',w.quantity,'effective_quantity',p_qty,'quantity_delta',w.quantity-p_qty,
            'requested_price',w.price,'effective_price',p_price,'normalization_rejection',p_rejection));
    IF p_rejection IS NOT NULL AND NOT nq_project_strategy_run(p_run) THEN
        RAISE EXCEPTION 'normalization rejection must terminalize same run' USING ERRCODE='23514';
    END IF;
    RETURN true;
END $$;

CREATE FUNCTION nq_create_ordinary_place_order(p_id text, p_account bigint, p_run text, p_venue text, p_symbol text, p_client text, p_side text, p_type text, p_price numeric, p_qty numeric, p_status text, p_reason text, p_trace text, p_env text, p_version bigint, p_time timestamp with time zone) RETURNS text
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
BEGIN
    IF NOT has_table_privilege(session_user,'orders','INSERT')
        OR NOT has_table_privilege(session_user,'orders','UPDATE') THEN
        RAISE EXCEPTION 'ordinary writer privilege required' USING ERRCODE='42501';
    END IF;
    IF p_venue <> 'OKX' OR p_status <> 'NEW' OR p_version <> 0 THEN
        RAISE EXCEPTION 'ordinary authority requires a fresh OKX order' USING ERRCODE='23514';
    END IF;
    INSERT INTO orders(order_id,account_id,strategy_run_id,venue,symbol,client_order_id,side,type,
        price,qty,status,reason,trace_id,exchange_code,trade_env,version,created_at,updated_at)
    VALUES(p_id,p_account,p_run,p_venue,p_symbol,p_client,p_side,p_type,p_price,p_qty,
        p_status,p_reason,p_trace,p_venue,p_env,p_version,p_time,p_time);
    INSERT INTO ordinary_place_authorities(order_id,state) VALUES(p_id,'NOT_ARMED');
    RETURN p_id;
END $$;

CREATE FUNCTION nq_decide_ordinary_place(p_id text, p_status text, p_version bigint, p_send boolean) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
DECLARE k text; o orders%ROWTYPE; changed integer;
BEGIN
    IF NOT has_table_privilege(session_user,'orders','INSERT')
        OR NOT has_table_privilege(session_user,'orders','UPDATE') THEN
        RAISE EXCEPTION 'ordinary writer privilege required' USING ERRCODE='42501';
    END IF;
    IF p_send IS NULL THEN RAISE EXCEPTION 'send decision is required' USING ERRCODE='23514'; END IF;
    -- Kill→Order→authority；recovery 从 Order 开始，不反向取得 Kill 锁。
    IF p_send THEN
        SELECT status INTO k FROM kill_switch_states WHERE scope='GLOBAL_TRADING' FOR UPDATE;
        IF k IS DISTINCT FROM 'DISENGAGED' THEN RETURN false; END IF;
    END IF;
    SELECT * INTO o FROM orders WHERE order_id=p_id FOR UPDATE;
    IF NOT FOUND OR o.venue <> 'OKX' OR o.status IS DISTINCT FROM p_status
        OR o.version IS DISTINCT FROM p_version THEN RETURN false; END IF;
    IF p_send AND o.status <> 'SENT' THEN RETURN false; END IF;
    -- SENT 之外仍核对同一 prepare 留下的风险事实；helper 不是绕过 RiskGate 的新入口。
    IF p_send AND NOT EXISTS (SELECT 1 FROM risk_events WHERE scope='ORDER' AND scope_id=o.order_id
        AND trace_id=o.trace_id AND decision='ALLOW') THEN RETURN false; END IF;
    IF NOT p_send AND o.status NOT IN ('SENT','ACCEPTED','PARTIALLY_FILLED','CANCEL_REQUESTED','CANCEL_REJECTED')
        THEN RETURN false; END IF;
    UPDATE ordinary_place_authorities SET state=CASE WHEN p_send THEN 'MAY_HAVE_ESCAPED'
        ELSE 'REVOKED_BEFORE_SEND' END, decided_at=clock_timestamp()
        WHERE order_id=p_id AND state='NOT_ARMED';
    GET DIAGNOSTICS changed = ROW_COUNT;
    RETURN changed=1;
END $$;

CREATE FUNCTION nq_finish_strategy_cancel(p_run text, p_order text, p_version bigint, p_account bigint, p_venue text, p_env text, p_symbol text, p_client text, p_external text, p_original numeric, p_executed numeric) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
DECLARE r strategy_runs%ROWTYPE; o orders%ROWTYPE; actual numeric; n bigint; valid boolean;
BEGIN
    IF NOT has_table_privilege(session_user,'orders','UPDATE') THEN
        RAISE EXCEPTION 'order writer privilege required' USING ERRCODE='42501';
    END IF;
    SELECT * INTO STRICT r FROM strategy_runs WHERE strategy_run_id=p_run FOR NO KEY UPDATE;
    IF r.status NOT IN ('CREATED','DISPATCHING','RUNNING') THEN RETURN false; END IF;
    SELECT * INTO STRICT o FROM orders WHERE order_id=p_order FOR UPDATE;
    IF o.strategy_run_id IS DISTINCT FROM p_run OR o.status<>'CANCELLED' OR o.version<>p_version
        OR ROW(o.account_id,o.venue,o.trade_env,o.symbol,o.client_order_id,o.external_order_id,o.qty)
            IS DISTINCT FROM ROW(p_account,p_venue,p_env,p_symbol,p_client,p_external,p_original)
        OR p_executed IS NULL OR p_executed<0 OR p_executed>=o.qty THEN RETURN false; END IF;
    SELECT * INTO actual,n,valid FROM nq_strategy_execution_proof(p_order);
    IF NOT valid OR actual<>p_executed THEN RETURN false; END IF;
    INSERT INTO ordinary_order_cancel_finality VALUES(p_order,p_version,p_executed) ON CONFLICT(order_id) DO NOTHING;
    IF NOT EXISTS(SELECT 1 FROM ordinary_order_cancel_finality WHERE order_id=p_order
        AND observed_order_version=p_version AND executed_quantity=p_executed) THEN
        RAISE EXCEPTION 'conflicting cancel finality' USING ERRCODE='23514';
    END IF;
    IF NOT nq_project_strategy_run(p_run) THEN
        RAISE EXCEPTION 'cancel finality must commit with run terminalization' USING ERRCODE='23514';
    END IF;
    RETURN true;
END $$;

CREATE FUNCTION nq_guard_cancel_finality_v51() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
BEGIN
    IF TG_OP<>'INSERT' OR current_user<>(SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid=TG_RELID) THEN
        RAISE EXCEPTION 'cancel finality requires verified immutable writer' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION nq_guard_ordinary_order_binding() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM ordinary_place_authorities WHERE order_id=OLD.order_id)
       AND ROW(NEW.order_id,NEW.account_id,NEW.client_order_id,NEW.venue,NEW.trade_env,
               NEW.symbol,NEW.side,NEW.type,NEW.price,NEW.qty)
           IS DISTINCT FROM ROW(OLD.order_id,OLD.account_id,OLD.client_order_id,OLD.venue,OLD.trade_env,
               OLD.symbol,OLD.side,OLD.type,OLD.price,OLD.qty) THEN
        RAISE EXCEPTION 'ordinary PLACE binding is immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION nq_guard_ordinary_place_authority() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
BEGIN
    IF TG_OP IN ('DELETE','TRUNCATE') THEN
        RAISE EXCEPTION 'ordinary PLACE decision cannot be removed' USING ERRCODE='23514';
    END IF;
    IF current_user <> (SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid=TG_RELID) THEN
        RAISE EXCEPTION 'ordinary PLACE authority requires restricted function' USING ERRCODE='42501';
    END IF;
    IF TG_OP='UPDATE' AND (
        NEW.order_id IS DISTINCT FROM OLD.order_id OR OLD.state <> 'NOT_ARMED'
        OR NEW.state NOT IN ('MAY_HAVE_ESCAPED','REVOKED_BEFORE_SEND')
    ) THEN
        RAISE EXCEPTION 'ordinary PLACE decision is irreversible' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION nq_guard_strategy_cursor_v51() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
BEGIN
    IF NEW.last_triggered_at IS DISTINCT FROM OLD.last_triggered_at THEN
        IF current_user<>(SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid=TG_RELID)
            OR NEW.last_triggered_at IS NULL
            OR (OLD.last_triggered_at IS NOT NULL AND NEW.last_triggered_at<=OLD.last_triggered_at)
            OR NOT EXISTS(SELECT 1 FROM strategy_runs r WHERE r.strategy_id=NEW.strategy_id
                AND r.account_id=NEW.account_id AND ((r.admission_schedule_id=NEW.schedule_job_id
                AND r.admission_due_at=NEW.last_triggered_at) OR (r.admission_schedule_id IS NULL AND r.request_id IN (
                    'req-schedule-'||NEW.schedule_job_id||'-window-'||(extract(epoch FROM NEW.last_triggered_at)*1000)::bigint::text,
                    'req-schedule-'||NEW.schedule_job_id||'-request-'||(extract(epoch FROM NEW.last_triggered_at)*1000)::bigint::text,
                    'req-schedule-'||NEW.schedule_job_id||'-strategy-'||NEW.strategy_id||'-'||(extract(epoch FROM NEW.last_triggered_at)*1000)::bigint::text)))) THEN
            RAISE EXCEPTION 'cursor requires monotonic atomic admission' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION nq_guard_strategy_order_v51() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
DECLARE r strategy_runs%ROWTYPE; w strategy_run_dispatch_work%ROWTYPE;
BEGIN
    IF TG_OP='TRUNCATE' THEN
        RAISE EXCEPTION 'order identities cannot be truncated' USING ERRCODE='23514';
    END IF;
    IF TG_OP='DELETE' THEN
        IF OLD.strategy_run_id IS NOT NULL THEN
            RAISE EXCEPTION 'bound strategy order cannot be deleted' USING ERRCODE='23514';
        END IF;
        RETURN OLD;
    END IF;
    IF TG_OP='UPDATE' THEN
        -- 状态写已经持有 Order 锁；这里只校验不变量，不反向获取 run 锁。
        IF NEW.strategy_run_id IS DISTINCT FROM OLD.strategy_run_id
            OR (OLD.strategy_run_id IS NOT NULL AND (
                ROW(NEW.order_id,NEW.account_id,NEW.client_order_id,NEW.venue,NEW.exchange_code,NEW.trade_env,
                    NEW.symbol,NEW.side,NEW.type,NEW.price,NEW.qty)
                IS DISTINCT FROM ROW(OLD.order_id,OLD.account_id,OLD.client_order_id,OLD.venue,OLD.exchange_code,OLD.trade_env,
                    OLD.symbol,OLD.side,OLD.type,OLD.price,OLD.qty)
                OR (NULLIF(btrim(OLD.external_order_id),'') IS NOT NULL
                    AND NEW.external_order_id IS DISTINCT FROM OLD.external_order_id))) THEN
            RAISE EXCEPTION 'strategy order binding is immutable' USING ERRCODE='23514';
        END IF;
        IF OLD.strategy_run_id IS NULL AND ROW(NEW.account_id,NEW.client_order_id) IS DISTINCT FROM ROW(OLD.account_id,OLD.client_order_id) THEN
            PERFORM pg_advisory_xact_lock(hashtextextended(NEW.account_id::text||':'||NEW.client_order_id,51));
            IF EXISTS(SELECT 1 FROM strategy_run_dispatch_work WHERE account_id=NEW.account_id AND client_order_id=NEW.client_order_id) THEN
                RAISE EXCEPTION 'client reserved by immutable strategy work' USING ERRCODE='23514';
            END IF;
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.strategy_run_id IS NULL THEN
        -- 与 admission 串行化同账户/client 的保留，不能抢占已接纳但尚未建单的身份。
        PERFORM pg_advisory_xact_lock(hashtextextended(NEW.account_id::text||':'||NEW.client_order_id,51));
        IF EXISTS(SELECT 1 FROM strategy_run_dispatch_work WHERE account_id=NEW.account_id AND client_order_id=NEW.client_order_id) THEN
            RAISE EXCEPTION 'client reserved by immutable strategy work' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO STRICT r FROM strategy_runs WHERE strategy_run_id=NEW.strategy_run_id FOR NO KEY UPDATE;
    SELECT * INTO STRICT w FROM strategy_run_dispatch_work WHERE strategy_run_id=r.strategy_run_id;
    IF r.status<>'DISPATCHING' OR NEW.exchange_code IS DISTINCT FROM NEW.venue
        OR ROW(NEW.account_id,NEW.venue,NEW.trade_env,NEW.client_order_id,
            NEW.symbol,NEW.side,NEW.type,NEW.price,NEW.qty)
        IS DISTINCT FROM ROW(r.account_id,upper(btrim(r.exchange_code)),r.trade_env,w.client_order_id,
            w.symbol,w.side,w.order_type,w.effective_price,w.effective_quantity)
        OR w.effective_quantity IS NULL OR w.normalization_rejection IS NOT NULL THEN
        RAISE EXCEPTION 'strategy order differs from immutable work' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION nq_guard_strategy_run_v51() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
BEGIN
    IF TG_OP IN ('DELETE','TRUNCATE') THEN
        RAISE EXCEPTION 'strategy run identity cannot be removed' USING ERRCODE='23514';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.status<>'CREATED' OR NEW.finished_at IS NOT NULL THEN
            RAISE EXCEPTION 'new strategy run must start CREATED' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF ROW(NEW.strategy_run_id,NEW.strategy_id,NEW.account_id,NEW.exchange_code,NEW.trade_env,
            NEW.trigger_type,NEW.request_id,NEW.config_snapshot,NEW.started_at,NEW.trace_id,
            NEW.admission_schedule_id,NEW.admission_due_at)
        IS DISTINCT FROM ROW(OLD.strategy_run_id,OLD.strategy_id,OLD.account_id,OLD.exchange_code,OLD.trade_env,
            OLD.trigger_type,OLD.request_id,OLD.config_snapshot,OLD.started_at,OLD.trace_id,
            OLD.admission_schedule_id,OLD.admission_due_at) THEN
        RAISE EXCEPTION 'strategy execution identity is immutable' USING ERRCODE='23514';
    END IF;
    IF ROW(NEW.status,NEW.finished_at,NEW.error_message) IS DISTINCT FROM ROW(OLD.status,OLD.finished_at,OLD.error_message) THEN
        IF current_user<>(SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid=TG_RELID)
            OR OLD.status NOT IN ('CREATED','DISPATCHING','RUNNING')
            OR NOT ((OLD.status='CREATED' AND NEW.status='DISPATCHING')
                OR (OLD.status='DISPATCHING' AND NEW.status='RUNNING')
                OR NEW.status IN ('SUCCEEDED','FAILED'))
            OR ((NEW.status IN ('SUCCEEDED','FAILED'))<>(NEW.finished_at IS NOT NULL)) THEN
            RAISE EXCEPTION 'invalid strategy lifecycle transition' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION nq_guard_strategy_work_v51() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
DECLARE r strategy_runs%ROWTYPE;
BEGIN
    IF TG_OP NOT IN ('INSERT','UPDATE') THEN RAISE EXCEPTION 'strategy work is immutable' USING ERRCODE='23514'; END IF;
    IF current_user<>(SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid=TG_RELID) THEN
        RAISE EXCEPTION 'strategy work requires atomic admission function' USING ERRCODE='42501';
    END IF;
    SELECT * INTO STRICT r FROM strategy_runs WHERE strategy_run_id=NEW.strategy_run_id;
    IF TG_OP='UPDATE' THEN
        IF ROW(NEW.strategy_run_id,NEW.work_schema_version,NEW.definition_version,NEW.account_id,NEW.client_order_id,
                NEW.symbol,NEW.side,NEW.order_type,NEW.quantity,NEW.price,NEW.time_in_force)
            IS DISTINCT FROM ROW(OLD.strategy_run_id,OLD.work_schema_version,OLD.definition_version,OLD.account_id,OLD.client_order_id,
                OLD.symbol,OLD.side,OLD.order_type,OLD.quantity,OLD.price,OLD.time_in_force)
            OR OLD.effective_quantity IS NOT NULL OR OLD.normalization_rejection IS NOT NULL
            OR (NEW.effective_quantity IS NULL AND NEW.normalization_rejection IS NULL) OR r.status<>'CREATED' THEN
            RAISE EXCEPTION 'requested work and decided effective parameters are immutable' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.effective_quantity IS NOT NULL OR NEW.effective_price IS NOT NULL OR NEW.normalization_rejection IS NOT NULL THEN
        RAISE EXCEPTION 'effective parameters require atomic prepare' USING ERRCODE='23514';
    END IF;
    IF NEW.account_id<>r.account_id OR NEW.client_order_id IS DISTINCT FROM 'coid-'||r.request_id THEN
        RAISE EXCEPTION 'strategy work identity mismatch' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION nq_observe_cancel_contradiction_v51() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
DECLARE expected numeric; actual numeric;
BEGIN
    IF NEW.strategy_run_id IS NULL THEN RETURN NEW; END IF;
    SELECT executed_quantity INTO expected FROM ordinary_order_cancel_finality WHERE order_id=NEW.order_id;
    IF NOT FOUND THEN RETURN NEW; END IF;
    SELECT COALESCE(sum(qty),0) INTO actual FROM trades WHERE order_id=NEW.order_id;
    IF actual IS DISTINCT FROM expected THEN
        INSERT INTO audit_logs(domain,action,actor_id,trace_id,detail_json)
            VALUES('RECONCILE','CANCEL_FINALITY_CONTRADICTION',NEW.order_id,NEW.trace_id,
                jsonb_build_object('order_id',NEW.order_id,'trade_id',NEW.trade_id,
                    'expected_executed_quantity',expected,'actual_executed_quantity',actual,
                    'disposition','KEEP_REAL_TRADE_NO_RUN_REOPEN_NO_RESEND'));
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION nq_preserve_strategy_admission_identity() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.admission_schedule_id IS NOT NULL AND
       (NEW.admission_schedule_id IS DISTINCT FROM OLD.admission_schedule_id
        OR NEW.admission_due_at IS DISTINCT FROM OLD.admission_due_at
        OR NEW.strategy_id IS DISTINCT FROM OLD.strategy_id
        OR NEW.account_id IS DISTINCT FROM OLD.account_id) THEN
        RAISE EXCEPTION 'strategy admission identity is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION nq_require_ordinary_no_order_finality() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
BEGIN
    IF NEW.state='REVOKED_BEFORE_SEND' AND NOT EXISTS (
        SELECT 1 FROM orders WHERE order_id=NEW.order_id AND status='CANCELLED'
            AND reason='ORDER_NOT_FOUND/OKX_51603'
    ) THEN
        RAISE EXCEPTION 'ordinary revoke requires atomic no-order finality' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION nq_require_strategy_prepare_v51() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
DECLARE rid text; r strategy_runs%ROWTYPE; o orders%ROWTYPE; w strategy_run_dispatch_work%ROWTYPE;
BEGIN
    rid:=NEW.strategy_run_id;
    IF rid IS NULL OR NOT EXISTS(SELECT 1 FROM strategy_run_dispatch_work WHERE strategy_run_id=rid) THEN RETURN NEW; END IF;
    SELECT * INTO STRICT r FROM strategy_runs WHERE strategy_run_id=rid;
    SELECT * INTO o FROM orders WHERE strategy_run_id=rid;
    SELECT * INTO STRICT w FROM strategy_run_dispatch_work WHERE strategy_run_id=rid;
    IF w.normalization_rejection IS NOT NULL THEN
        IF r.status<>'FAILED' OR o.order_id IS NOT NULL THEN
            RAISE EXCEPTION 'normalization rejection requires atomic run finality without Order' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF (r.status='CREATED' AND o.order_id IS NOT NULL)
        OR (w.effective_quantity IS NOT NULL AND o.order_id IS NULL)
        OR (r.status<>'CREATED' AND o.order_id IS NULL)
        OR (TG_TABLE_NAME='orders' AND o.venue='OKX' AND NOT EXISTS(
            SELECT 1 FROM ordinary_place_authorities WHERE order_id=o.order_id AND state='NOT_ARMED')) THEN
        RAISE EXCEPTION 'strategy prepare facts must commit together' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION nq_require_strategy_work_v51() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public', 'pg_temp'
    SET lock_timeout TO '5s'
    AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM strategy_run_dispatch_work WHERE strategy_run_id=NEW.strategy_run_id) THEN
        RAISE EXCEPTION 'new strategy run requires atomic immutable work' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION prevent_backtest_publish_artifact_locator_rebind() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.artifact_storage_key IS DISTINCT FROM NEW.artifact_storage_key
        OR OLD.manifest_storage_key IS DISTINCT FROM NEW.manifest_storage_key THEN
        IF OLD.artifact_storage_key IS NOT NULL
            OR OLD.manifest_storage_key IS NOT NULL THEN
            RAISE EXCEPTION USING
                ERRCODE = '23514',
                MESSAGE = 'strategy release artifact locator is immutable';
        END IF;
        IF OLD.publish_status <> 'FAILED'
            OR NEW.publish_status <> 'SUCCEEDED'
            OR NEW.artifact_storage_key IS NULL
            OR NEW.manifest_storage_key IS NULL THEN
            RAISE EXCEPTION USING
                ERRCODE = '23514',
                MESSAGE = 'strategy release artifact locator binding is not allowed';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION prevent_strategy_release_identity_rebind() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.release_artifact_digest IS DISTINCT FROM NEW.release_artifact_digest
        OR OLD.manifest_fingerprint IS DISTINCT FROM NEW.manifest_fingerprint
        OR OLD.manifest_schema_version IS DISTINCT FROM NEW.manifest_schema_version
        OR OLD.identity_bound_at IS DISTINCT FROM NEW.identity_bound_at THEN
        IF OLD.release_artifact_digest IS NOT NULL
            OR OLD.manifest_fingerprint IS NOT NULL
            OR OLD.manifest_schema_version IS NOT NULL
            OR OLD.identity_bound_at IS NOT NULL THEN
            RAISE EXCEPTION USING
                ERRCODE = '23514',
                MESSAGE = 'strategy release identity is immutable';
        END IF;
        IF NEW.release_artifact_digest IS NULL
            OR NEW.manifest_fingerprint IS NULL
            OR NEW.manifest_schema_version IS NULL
            OR NEW.identity_bound_at IS NULL THEN
            RAISE EXCEPTION USING
                ERRCODE = '23514',
                MESSAGE = 'strategy release identity must be bound atomically';
        END IF;
        NEW.admission_revision := OLD.admission_revision + 1;
        NEW.updated_at := clock_timestamp();
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION prevent_strategy_release_revision_rewrite() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    -- 合法 revision 写入只有统一 bump function 与 identity first-bind，单次 row update 必须严格 old + 1。
    -- 直接回退会让旧 guard revision 再次命中；同值或跳跃 rewrite 也不属于 authoritative protocol。
    IF NEW.admission_revision IS DISTINCT FROM OLD.admission_revision + 1 THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'strategy release admission revision must advance exactly once per row update';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION reconstruct_execution_scope_hash(p_execution_scope_id uuid) RETURNS text
    LANGUAGE sql STABLE STRICT
    AS $$
    SELECT execution_scope_hash(
        scope.session_id, scope.instrument_metadata_digest, scope.instrument_source_identity,
        scope.instrument_source_schema_version, scope.instrument_maximum_age_ms,
        scope.fee_schedule_digest, scope.fee_tier, scope.fee_evidence_class,
        scope.fee_source_identity, scope.fee_source_schema_version, scope.fee_maximum_age_ms,
        scope.balance_source_identity, scope.balance_source_schema_version, scope.balance_maximum_age_ms,
        scope.clock_source_identity, scope.clock_source_schema_version, scope.clock_maximum_age_ms,
        scope.signed_timestamp_source, scope.maximum_tolerated_skew_ms,
        scope.endpoint_policy_version, scope.endpoint_policy_digest,
        scope.provider_contract_identity, scope.provider_artifact_digest,
        scope.worker_identity, scope.worker_release_digest
    )
    FROM execution_scope_bindings scope
    WHERE scope.execution_scope_id = p_execution_scope_id
$$;

CREATE FUNCTION reject_controlled_execution_fact_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE='23514', MESSAGE=TG_TABLE_NAME || ' is append-only';
END;
$$;

CREATE FUNCTION reject_execution_recovery_decision_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='controlled execution pre-place recovery decision is immutable';
END;
$$;

CREATE FUNCTION reject_live_control_fact_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = TG_TABLE_NAME || ' is append-only or immutable';
END;
$$;

CREATE FUNCTION reject_public_capture_dataset_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.source = 'OKX_PUBLIC_CAPTURE' THEN
        RAISE EXCEPTION 'public capture dataset is immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION reject_public_market_capture_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'public market capture is immutable';
END;
$$;

CREATE FUNCTION require_public_market_rule_raw_identity() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.rule_request_path IS NULL OR NEW.rule_raw_response IS NULL
            OR NEW.rule_raw_sha256 IS NULL THEN
        RAISE EXCEPTION 'public rule raw identity required for new capture';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION strategy_sim_guard_sim_order() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE d strategy_sim_decisions%ROWTYPE; run_status text;
BEGIN
    IF NEW.client_order_id NOT LIKE 'coid-sim-%' THEN RETURN NEW; END IF;
    SELECT * INTO d FROM strategy_sim_decisions
    WHERE 'coid-sim-' || left(decision_id, 60) = NEW.client_order_id;
    IF NOT FOUND OR d.reason <> 'DECIDING' OR d.status <> 'NOT_TRADABLE'
       OR d.canonical_account_id <> NEW.account_id
       OR NEW.venue <> 'PAPER' OR NEW.trade_env <> 'SIM' THEN
        RAISE EXCEPTION 'strategy SIM decision binding invalid' USING ERRCODE='23514';
    END IF;
    SELECT status INTO run_status FROM paper_trading_runs
    WHERE paper_run_id=d.paper_run_id FOR SHARE;
    IF run_status <> 'RUNNING' THEN
        RAISE EXCEPTION 'strategy SIM run stopped' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION strategy_sim_preserve_decision() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'strategy SIM decision is append only';
    END IF;
    IF OLD.status <> 'NOT_TRADABLE' OR OLD.reason <> 'DECIDING'
       OR NEW.status = 'NOT_TRADABLE' AND NEW.reason = 'DECIDING'
       OR ROW(NEW.decision_id,NEW.paper_run_id,NEW.canonical_account_id,
              NEW.strategy_version_id,NEW.strategy_checksum,NEW.signal_open_time,
              NEW.signal_available_at,NEW.execution_open_time,NEW.input_sha256,
              NEW.execution_bar_sha256,NEW.input_snapshot_json,NEW.target_exposure,
              NEW.side,NEW.quantity,NEW.execution_price,NEW.fee_rate,NEW.slippage_bps,
              NEW.created_at)
          IS DISTINCT FROM
          ROW(OLD.decision_id,OLD.paper_run_id,OLD.canonical_account_id,
              OLD.strategy_version_id,OLD.strategy_checksum,OLD.signal_open_time,
              OLD.signal_available_at,OLD.execution_open_time,OLD.input_sha256,
              OLD.execution_bar_sha256,OLD.input_snapshot_json,OLD.target_exposure,
              OLD.side,OLD.quantity,OLD.execution_price,OLD.fee_rate,OLD.slippage_bps,
              OLD.created_at) THEN
        RAISE EXCEPTION 'strategy SIM decision is immutable after completion';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION strategy_sim_preserve_sim_account() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.canonical_account_id IS NOT NULL
       AND NEW.canonical_account_id IS DISTINCT FROM OLD.canonical_account_id THEN
        RAISE EXCEPTION 'Strategy SIM canonical account is immutable';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION sync_order_venue_metadata() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.request_id IS NULL OR BTRIM(NEW.request_id) = '' THEN
        NEW.request_id := NEW.trace_id;
    END IF;
    IF NEW.dedup_key IS NULL OR BTRIM(NEW.dedup_key) = '' THEN
        NEW.dedup_key := NEW.account_id::TEXT || ':' || NEW.client_order_id;
    END IF;
    IF NEW.exchange_code IS NULL OR BTRIM(NEW.exchange_code) = '' THEN
        NEW.exchange_code := NEW.venue;
    END IF;
    IF NEW.trade_env IS NULL OR BTRIM(NEW.trade_env) = '' THEN
        NEW.trade_env := 'SIM';
    END IF;
    IF (NEW.exchange_order_id IS NULL OR BTRIM(NEW.exchange_order_id) = '')
        AND NEW.external_order_id IS NOT NULL AND BTRIM(NEW.external_order_id) <> '' THEN
        NEW.exchange_order_id := NEW.external_order_id;
    END IF;
    IF (NEW.external_order_id IS NULL OR BTRIM(NEW.external_order_id) = '')
        AND NEW.exchange_order_id IS NOT NULL AND BTRIM(NEW.exchange_order_id) <> '' THEN
        NEW.external_order_id := NEW.exchange_order_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION sync_trade_venue_metadata() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    order_exchange_code VARCHAR(32);
    order_trade_env VARCHAR(8);
    order_strategy_run_id VARCHAR(128);
    order_exchange_order_id VARCHAR(128);
BEGIN
    SELECT o.exchange_code, o.trade_env, o.strategy_run_id, o.exchange_order_id
      INTO order_exchange_code, order_trade_env, order_strategy_run_id, order_exchange_order_id
      FROM orders AS o
     WHERE o.order_id = NEW.order_id;

    IF NEW.exchange_code IS NULL OR BTRIM(NEW.exchange_code) = '' THEN
        NEW.exchange_code := COALESCE(order_exchange_code, NEW.exchange);
    END IF;
    IF NEW.trade_env IS NULL OR BTRIM(NEW.trade_env) = '' THEN
        NEW.trade_env := COALESCE(order_trade_env, 'SIM');
    END IF;
    IF (NEW.strategy_run_id IS NULL OR BTRIM(NEW.strategy_run_id) = '') AND order_strategy_run_id IS NOT NULL THEN
        NEW.strategy_run_id := order_strategy_run_id;
    END IF;
    IF (NEW.exchange_order_id IS NULL OR BTRIM(NEW.exchange_order_id) = '') THEN
        NEW.exchange_order_id := COALESCE(NEW.external_order_id, order_exchange_order_id);
    END IF;
    IF (NEW.external_order_id IS NULL OR BTRIM(NEW.external_order_id) = '')
        AND NEW.exchange_order_id IS NOT NULL AND BTRIM(NEW.exchange_order_id) <> '' THEN
        NEW.external_order_id := NEW.exchange_order_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION validate_execution_observation_set() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_execution_scope_id UUID;
    v_observation_set_id UUID;
    v_instrument_observation_id UUID;
    v_market_observation_id UUID;
    v_symbols TEXT[];
    v_expected_symbols TEXT[];
    v_count INTEGER;
    v_invalid_hashes INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'execution_instrument_observation_items' THEN
        SELECT execution_scope_id, observation_set_id
        INTO v_execution_scope_id, v_observation_set_id
        FROM execution_prerequisite_observations WHERE observation_id = NEW.observation_id;
    ELSE
        v_execution_scope_id := NEW.execution_scope_id;
        v_observation_set_id := NEW.observation_set_id;
    END IF;

    SELECT count(*) INTO v_count
    FROM execution_prerequisite_observations
    WHERE execution_scope_id = v_execution_scope_id AND observation_set_id = v_observation_set_id;
    SELECT observation_id INTO v_instrument_observation_id
    FROM execution_prerequisite_observations
    WHERE execution_scope_id = v_execution_scope_id AND observation_set_id = v_observation_set_id
      AND observation_type = 'INSTRUMENT_METADATA';
    SELECT observation_id INTO v_market_observation_id
    FROM execution_prerequisite_observations
    WHERE execution_scope_id = v_execution_scope_id AND observation_set_id = v_observation_set_id
      AND observation_type = 'MARKET_SNAPSHOT';
    IF v_count <> 5 OR v_instrument_observation_id IS NULL OR v_market_observation_id IS NULL THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='controlled execution observation set must contain exactly five typed observations';
    END IF;

    SELECT array_agg(symbol ORDER BY symbol), count(*)
    INTO v_symbols, v_count
    FROM execution_instrument_observation_items
    WHERE observation_id = v_instrument_observation_id;
    SELECT session.symbol_allowlist INTO v_expected_symbols
    FROM execution_scope_bindings scope
    JOIN live_sessions session ON session.session_id = scope.session_id
    WHERE scope.execution_scope_id = v_execution_scope_id;
    IF v_count NOT BETWEEN 1 AND 2 OR v_symbols IS DISTINCT FROM v_expected_symbols THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='instrument observation items must equal the session symbol scope';
    END IF;
    IF execution_instrument_metadata_digest(v_instrument_observation_id) IS DISTINCT FROM (
        SELECT instrument_metadata_digest FROM execution_prerequisite_observations
        WHERE observation_id = v_instrument_observation_id
    ) THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='instrument metadata digest does not match item reconstruction';
    END IF;
    SELECT count(*) INTO v_invalid_hashes
    FROM execution_prerequisite_observations observation
    WHERE observation.execution_scope_id = v_execution_scope_id
      AND observation.observation_set_id = v_observation_set_id
      AND observation.observation_payload_hash IS DISTINCT FROM
          execution_observation_payload_hash(observation.observation_id);
    IF v_invalid_hashes <> 0 THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='observation payload hash does not match typed fact reconstruction';
    END IF;
    RETURN NEW;
END;
$$;

CREATE INDEX idx_account_snapshots_account_env_currency_latest ON account_snapshots USING btree (account_id, trade_env, currency, snapshot_id DESC);

CREATE INDEX idx_account_snapshots_account_ts ON account_snapshots USING btree (account_id, ts DESC);

CREATE INDEX idx_alerts_run_id_created ON paper_run_alerts USING btree (paper_run_id, created_at DESC);

CREATE INDEX idx_alerts_severity ON paper_run_alerts USING btree (severity);

CREATE INDEX idx_alerts_status ON paper_run_alerts USING btree (status);

CREATE INDEX idx_audit_logs_actor_created ON audit_logs USING btree (actor_id, created_at DESC);

CREATE INDEX idx_audit_logs_domain_created ON audit_logs USING btree (domain, created_at DESC);

CREATE INDEX idx_audit_logs_trace_id ON audit_logs USING btree (trace_id);

CREATE INDEX idx_backtest_configs_dataset_id ON backtest_configs USING btree (dataset_id, updated_at DESC);

CREATE INDEX idx_backtest_configs_research ON backtest_configs USING btree (research_config_id, created_at DESC);

CREATE INDEX idx_backtest_configs_strategy_version_id ON backtest_configs USING btree (strategy_version_id, updated_at DESC);

CREATE INDEX idx_backtest_eval_reports_backtest_run_id ON backtest_eval_reports USING btree (backtest_run_id);

CREATE INDEX idx_backtest_eval_reports_status ON backtest_eval_reports USING btree (evaluation_status, evaluated_at DESC);

CREATE INDEX idx_backtest_publish_records_status ON backtest_publish_records USING btree (publish_status, published_at DESC);

CREATE INDEX idx_backtest_publish_records_strategy_version ON backtest_publish_records USING btree (strategy_version_id);

CREATE INDEX idx_backtest_runs_backtest_config_id ON backtest_runs USING btree (backtest_config_id, requested_at DESC);

CREATE INDEX idx_backtest_runs_dataset_snapshot_id ON backtest_runs USING btree (((dataset_snapshot_json ->> 'datasetId'::text)));

CREATE INDEX idx_backtest_runs_research_config_id ON backtest_runs USING btree (research_config_id, requested_at DESC);

CREATE INDEX idx_backtest_runs_status ON backtest_runs USING btree (status, requested_at DESC);

CREATE INDEX idx_backtest_runs_strategy_version_id ON backtest_runs USING btree (strategy_version_id, requested_at DESC);

CREATE INDEX idx_controlled_execution_leases_operator_authority ON controlled_execution_leases USING btree (operator_execution_authority_id) WHERE (operator_execution_authority_id IS NOT NULL);

CREATE INDEX idx_controlled_execution_leases_recovery ON controlled_execution_leases USING btree (status, expires_at, lease_id) WHERE ((status)::text = ANY ((ARRAY['CREATED'::character varying, 'ACTIVE'::character varying, 'CONSUMED'::character varying])::text[]));

CREATE INDEX idx_credential_audit_logs_account_created ON credential_audit_logs USING btree (exchange_account_id, created_at DESC);

CREATE INDEX idx_credential_audit_logs_credential_created ON credential_audit_logs USING btree (credential_id, created_at DESC);

CREATE INDEX idx_credential_audit_logs_event_created ON credential_audit_logs USING btree (event_type, created_at DESC);

CREATE INDEX idx_daily_reports_run_id_date ON paper_run_daily_reports USING btree (paper_run_id, report_date DESC);

CREATE INDEX idx_daily_reports_status ON paper_run_daily_reports USING btree (status);

CREATE INDEX idx_equity_curve_run_time ON equity_curve_snapshots USING btree (paper_run_id, snapshot_time DESC);

CREATE INDEX idx_estop_run_id ON emergency_stop_events USING btree (paper_run_id, triggered_at DESC);

CREATE INDEX idx_estop_status ON emergency_stop_events USING btree (status, triggered_at DESC);

CREATE INDEX idx_event_store_topic_created ON event_store USING btree (topic, created_at DESC);

CREATE INDEX idx_event_store_trace_id ON event_store USING btree (trace_id);

CREATE INDEX idx_event_store_type_created ON event_store USING btree (event_type, created_at DESC);

CREATE INDEX idx_execution_intents_claim ON execution_intents USING btree (state, lease_expires_at, created_at) WHERE (((state)::text = ANY ((ARRAY['CREATED'::character varying, 'CLAIMED'::character varying])::text[])) AND (send_started_at IS NULL));

CREATE INDEX idx_execution_intents_client_order ON execution_intents USING btree (client_order_id);

CREATE INDEX idx_execution_intents_local_order ON execution_intents USING btree (local_order_id);

CREATE INDEX idx_execution_intents_session_state ON execution_intents USING btree (session_id, state, created_at);

CREATE INDEX idx_execution_observation_fresh_lookup ON execution_prerequisite_observations USING btree (execution_scope_id, observation_type, observed_at DESC, observation_id);

CREATE INDEX idx_execution_observation_set ON execution_prerequisite_observations USING btree (execution_scope_id, observation_set_id);

CREATE INDEX idx_execution_receipts_exchange_order ON execution_receipts USING btree (exchange_order_id) WHERE (exchange_order_id IS NOT NULL);

CREATE INDEX idx_execution_receipts_intent_time ON execution_receipts USING btree (intent_id, received_at, receipt_id);

CREATE INDEX idx_execution_receipts_outcome_time ON execution_receipts USING btree (outcome, received_at DESC);

CREATE INDEX idx_execution_scope_bindings_created_at ON execution_scope_bindings USING btree (created_at DESC, execution_scope_id);

CREATE INDEX idx_heartbeats_run_id_time ON paper_run_heartbeats USING btree (paper_run_id, heartbeat_time DESC);

CREATE INDEX idx_instrument_catalog_exchange_status_symbol ON instrument_catalog USING btree (exchange_code, status, internal_symbol);

CREATE INDEX idx_kill_switch_events_scope_occurred ON kill_switch_events USING btree (scope, occurred_at DESC, id DESC);

CREATE INDEX idx_ledger_entries_account_ts ON ledger_entries USING btree (account_id, ts DESC);

CREATE INDEX idx_ledger_entries_ref ON ledger_entries USING btree (ref_type, ref_id);

CREATE INDEX idx_ledger_entries_trace_id ON ledger_entries USING btree (trace_id);

CREATE INDEX idx_ledger_events_trace_id ON ledger_events USING btree (trace_id);

CREATE INDEX idx_live_session_events_timeline ON live_session_events USING btree (session_id, sequence_no);

CREATE INDEX idx_live_session_events_trace ON live_session_events USING btree (trace_id);

CREATE INDEX idx_live_sessions_account_state_updated ON live_sessions USING btree (exchange_account_id, state, updated_at DESC);

CREATE INDEX idx_live_sessions_operator_execution_authority ON live_sessions USING btree (operator_execution_authority_id) WHERE (operator_execution_authority_id IS NOT NULL);

CREATE INDEX idx_live_sessions_owner_created ON live_sessions USING btree (owner_id, created_at DESC);

CREATE INDEX idx_live_sessions_release ON live_sessions USING btree (strategy_release_id);

CREATE INDEX idx_live_sessions_risk_set ON live_sessions USING btree (risk_limit_set_id);

CREATE INDEX idx_marketdata_bars_scope_time_desc ON marketdata_bars USING btree (exchange_code, market_type, symbol, "interval", open_time DESC);

CREATE INDEX idx_marketdata_bars_symbol_interval_time ON marketdata_bars USING btree (exchange_code, symbol, "interval", open_time DESC);

CREATE INDEX idx_marketdata_dataset_coverage_dataset_created ON marketdata_dataset_coverage USING btree (dataset_id, created_at DESC);

CREATE INDEX idx_marketdata_datasets_quality_status ON marketdata_datasets USING btree (quality_status, updated_at DESC);

CREATE INDEX idx_marketdata_datasets_scope_updated ON marketdata_datasets USING btree (exchange_code, market_type, symbol, "interval", updated_at DESC);

CREATE INDEX idx_marketdata_ingestion_jobs_scope_updated ON marketdata_ingestion_jobs USING btree (exchange_code, market_type, symbol, "interval", updated_at DESC);

CREATE INDEX idx_marketdata_ingestion_runs_job_started ON marketdata_ingestion_runs USING btree (job_id, started_at DESC);

CREATE INDEX idx_operator_approvals_active_expiry ON operator_approvals USING btree (expires_at) WHERE ((decision)::text = 'APPROVED'::text);

CREATE INDEX idx_operator_approvals_approver_time ON operator_approvals USING btree (approver_id, approved_at DESC);

CREATE INDEX idx_operator_approvals_session_time ON operator_approvals USING btree (session_id, approved_at DESC);

CREATE INDEX idx_operator_execution_authorities_scope ON operator_execution_authorities USING btree (owner_user_id, exchange_account_id, credential_reference_id, status, expires_at);

CREATE INDEX idx_orders_account_created ON orders USING btree (account_id, created_at DESC);

CREATE INDEX idx_orders_exchange_code_exchange_order_id ON orders USING btree (exchange_code, exchange_order_id) WHERE (exchange_order_id IS NOT NULL);

CREATE INDEX idx_orders_request_id ON orders USING btree (request_id);

CREATE INDEX idx_orders_status_created ON orders USING btree (status, created_at DESC);

CREATE INDEX idx_orders_strategy_run_id ON orders USING btree (strategy_run_id);

CREATE INDEX idx_orders_symbol_created ON orders USING btree (symbol, created_at DESC);

CREATE INDEX idx_orders_trace_id ON orders USING btree (trace_id);

CREATE INDEX idx_orders_venue_external_order_id ON orders USING btree (venue, external_order_id);

CREATE INDEX idx_paper_orders_run_id ON paper_trading_orders USING btree (paper_run_id, created_at DESC);

CREATE INDEX idx_paper_orders_run_symbol_status ON paper_trading_orders USING btree (paper_run_id, symbol, status);

CREATE INDEX idx_paper_positions_run_id ON paper_trading_positions USING btree (paper_run_id, updated_at DESC);

CREATE INDEX idx_paper_run_schedules_next_fire ON paper_run_schedules USING btree (next_fire_time) WHERE ((status)::text = 'ENABLED'::text);

CREATE INDEX idx_paper_run_schedules_run_id ON paper_run_schedules USING btree (paper_run_id);

CREATE INDEX idx_paper_run_schedules_status ON paper_run_schedules USING btree (status);

CREATE INDEX idx_paper_runs_publish_id ON paper_trading_runs USING btree (publish_id, created_at DESC);

CREATE INDEX idx_paper_runs_status ON paper_trading_runs USING btree (status, updated_at DESC);

CREATE INDEX idx_paper_runs_strategy_version_id ON paper_trading_runs USING btree (strategy_version_id, created_at DESC);

CREATE INDEX idx_paper_trades_order_id ON paper_trading_trades USING btree (paper_order_id);

CREATE INDEX idx_paper_trades_run_id ON paper_trading_trades USING btree (paper_run_id, traded_at DESC);

CREATE INDEX idx_paper_trades_symbol_time ON paper_trading_trades USING btree (symbol, traded_at DESC);

CREATE INDEX idx_position_curve_run_symbol ON position_curve_snapshots USING btree (paper_run_id, symbol, snapshot_time DESC);

CREATE INDEX idx_position_curve_run_time ON position_curve_snapshots USING btree (paper_run_id, snapshot_time DESC);

CREATE INDEX idx_positions_account_updated ON positions USING btree (account_id, updated_at DESC);

CREATE INDEX idx_recovery_events_created_at ON paper_run_recovery_events USING btree (created_at DESC);

CREATE INDEX idx_recovery_events_run_id_created ON paper_run_recovery_events USING btree (paper_run_id, created_at DESC);

CREATE INDEX idx_recovery_events_status ON paper_run_recovery_events USING btree (status);

CREATE INDEX idx_recovery_events_type ON paper_run_recovery_events USING btree (recovery_type);

CREATE INDEX idx_replay_run_time ON trade_replay_records USING btree (paper_run_id, replay_time DESC);

CREATE INDEX idx_research_configs_source_strategy ON research_configs USING btree (source_strategy_id, created_at DESC);

CREATE INDEX idx_risk_events_scope_created ON risk_events USING btree (scope, scope_id, created_at DESC);

CREATE INDEX idx_risk_events_trace_id ON risk_events USING btree (trace_id);

CREATE INDEX idx_risk_limit_sets_scope_version ON risk_limit_sets USING btree (effective_scope, version DESC);

CREATE INDEX idx_risk_results_run_id ON paper_risk_check_results USING btree (paper_run_id, created_at DESC);

CREATE INDEX idx_schedule_fires_fired_at ON paper_run_schedule_fires USING btree (fired_at DESC);

CREATE INDEX idx_schedule_fires_run_id ON paper_run_schedule_fires USING btree (paper_run_id);

CREATE INDEX idx_schedule_fires_schedule_id ON paper_run_schedule_fires USING btree (schedule_id, fired_at DESC);

CREATE INDEX idx_scheduled_job_controls_due ON scheduled_job_controls USING btree (next_run_at, job_key) WHERE enabled;

CREATE INDEX idx_shadow_consistency_reports_paper_generated ON shadow_consistency_reports USING btree (paper_run_id, generated_at DESC);

CREATE INDEX idx_shadow_consistency_reports_run_generated ON shadow_consistency_reports USING btree (shadow_run_id, generated_at DESC);

CREATE INDEX idx_shadow_run_events_run_created_at ON shadow_run_events USING btree (shadow_run_id, created_at);

CREATE INDEX idx_shadow_run_snapshots_run_type_sequence ON shadow_run_snapshots USING btree (shadow_run_id, snapshot_type, sequence_no);

CREATE UNIQUE INDEX idx_shadow_runs_idempotency_key ON shadow_runs USING btree (idempotency_key);

CREATE INDEX idx_shadow_runs_paper_run_id ON shadow_runs USING btree (paper_run_id);

CREATE INDEX idx_shadow_runs_status_created_at ON shadow_runs USING btree (status, created_at DESC);

CREATE INDEX idx_shadow_runs_strategy_dataset ON shadow_runs USING btree (strategy_version_id, dataset_id);

CREATE INDEX idx_sim_orders_backtest_run_id ON sim_orders USING btree (backtest_run_id, created_at DESC);

CREATE INDEX idx_sim_pnl_snapshots_run_snapshot_time ON sim_pnl_snapshots USING btree (backtest_run_id, snapshot_time);

CREATE INDEX idx_sim_positions_backtest_run_symbol ON sim_positions USING btree (backtest_run_id, symbol);

CREATE INDEX idx_sim_trades_backtest_run_id ON sim_trades USING btree (backtest_run_id, traded_at DESC);

CREATE INDEX idx_stability_checks_run_id_created ON paper_run_stability_checks USING btree (paper_run_id, created_at DESC);

CREATE INDEX idx_stability_checks_status ON paper_run_stability_checks USING btree (status);

CREATE INDEX idx_stability_checks_window_end ON paper_run_stability_checks USING btree (check_window_end);

CREATE INDEX idx_stability_checks_window_start ON paper_run_stability_checks USING btree (check_window_start);

CREATE INDEX idx_strategy_admitted_schedule_due ON strategy_runs USING btree (admission_schedule_id, admission_due_at DESC) WHERE (admission_schedule_id IS NOT NULL);

CREATE INDEX idx_strategy_definitions_enabled_scan ON strategy_definitions USING btree (exchange_code, account_id, trade_env, enabled, updated_at DESC);

CREATE INDEX idx_strategy_run_recovery_scan ON strategy_runs USING btree (started_at, strategy_run_id) WHERE ((status)::text = ANY ((ARRAY['CREATED'::character varying, 'DISPATCHING'::character varying, 'RUNNING'::character varying])::text[]));

CREATE INDEX idx_strategy_runs_account_started ON strategy_runs USING btree (account_id, started_at DESC);

CREATE INDEX idx_strategy_runs_exchange_account_status ON strategy_runs USING btree (exchange_code, account_id, trade_env, status, started_at DESC);

CREATE INDEX idx_strategy_runs_request_id ON strategy_runs USING btree (request_id) WHERE (request_id IS NOT NULL);

CREATE INDEX idx_strategy_runs_strategy_started ON strategy_runs USING btree (strategy_id, started_at DESC);

CREATE INDEX idx_strategy_schedules_enabled_scan ON strategy_schedules USING btree (enabled, exchange_code, account_id, trade_env, last_triggered_at);

CREATE INDEX idx_strategy_schedules_strategy_enabled ON strategy_schedules USING btree (strategy_id, enabled, updated_at DESC);

CREATE INDEX idx_strategy_sim_decisions_run_time ON strategy_sim_decisions USING btree (paper_run_id, signal_open_time DESC);

CREATE INDEX idx_strategy_versions_created_at ON strategy_versions USING btree (created_at DESC);

CREATE INDEX idx_strategy_versions_status ON strategy_versions USING btree (status, updated_at DESC);

CREATE INDEX idx_strategy_versions_strategy_code ON strategy_versions USING btree (strategy_code, version DESC);

CREATE INDEX idx_trades_account_ts ON trades USING btree (account_id, ts DESC);

CREATE INDEX idx_trades_exchange_code_exchange_order_id ON trades USING btree (exchange_code, exchange_order_id) WHERE (exchange_order_id IS NOT NULL);

CREATE INDEX idx_trades_exchange_external_order_id ON trades USING btree (exchange, external_order_id) WHERE (external_order_id IS NOT NULL);

CREATE INDEX idx_trades_order_ts ON trades USING btree (order_id, ts DESC);

CREATE INDEX idx_trades_strategy_run_id ON trades USING btree (strategy_run_id) WHERE (strategy_run_id IS NOT NULL);

CREATE INDEX idx_trades_symbol_ts ON trades USING btree (symbol, ts DESC);

CREATE INDEX idx_trades_trace_id ON trades USING btree (trace_id);

CREATE INDEX idx_validation_review_cases_evidence_type_source ON validation_review_cases USING btree (evidence_type, evidence_source);

CREATE INDEX idx_validation_review_cases_tenant_owner_state_updated ON validation_review_cases USING btree (tenant_key, owner_id, state, updated_at DESC);

CREATE INDEX idx_validation_review_cases_tenant_owner_updated ON validation_review_cases USING btree (tenant_key, owner_id, updated_at DESC, id DESC);

CREATE INDEX idx_validation_review_cases_tenant_state_severity_updated ON validation_review_cases USING btree (tenant_key, state, severity, updated_at DESC);

CREATE INDEX idx_validation_review_cases_tenant_updated ON validation_review_cases USING btree (tenant_key, updated_at DESC, id DESC);

CREATE INDEX idx_validation_review_events_case_created ON validation_review_events USING btree (review_case_id, created_at, id);

CREATE INDEX idx_validation_review_events_tenant_actor_created ON validation_review_events USING btree (tenant_key, actor_id, created_at DESC);

CREATE INDEX idx_validation_review_events_trace_id ON validation_review_events USING btree (trace_id);

CREATE UNIQUE INDEX uq_controlled_execution_lease_intents_global_cancel ON controlled_execution_lease_intents USING btree ((1)) WHERE ((action)::text = 'CANCEL'::text);

CREATE UNIQUE INDEX uq_controlled_execution_lease_intents_global_place ON controlled_execution_lease_intents USING btree ((1)) WHERE ((action)::text = 'PLACE'::text);

CREATE UNIQUE INDEX uq_controlled_execution_leases_predecessor_successor ON controlled_execution_leases USING btree (predecessor_lease_id) WHERE (predecessor_lease_id IS NOT NULL);

CREATE UNIQUE INDEX uq_controlled_execution_leases_recovery_decision ON controlled_execution_leases USING btree (recovery_decision_id) WHERE (recovery_decision_id IS NOT NULL);

CREATE UNIQUE INDEX uq_controlled_execution_leases_single_open ON controlled_execution_leases USING btree ((1)) WHERE ((status)::text = ANY ((ARRAY['CREATED'::character varying, 'ACTIVE'::character varying, 'CONSUMED'::character varying])::text[]));

CREATE UNIQUE INDEX uq_controlled_execution_leases_single_origin ON controlled_execution_leases USING btree ((1)) WHERE (predecessor_lease_id IS NULL);

CREATE UNIQUE INDEX uq_exchange_account_credentials_active_type ON exchange_account_credentials USING btree (exchange_account_id, credential_type) WHERE (is_active = true);

CREATE UNIQUE INDEX uq_exchange_accounts_default_scope ON exchange_accounts USING btree (owner_user_id, exchange_code, trade_env) WHERE (is_default = true);

CREATE UNIQUE INDEX uq_exchange_accounts_exchange_env_external_ref_not_null ON exchange_accounts USING btree (exchange_code, trade_env, external_account_ref) WHERE (external_account_ref IS NOT NULL);

CREATE UNIQUE INDEX uq_exchange_accounts_owner_exchange_env_alias ON exchange_accounts USING btree (owner_user_id, exchange_code, trade_env, account_alias);

CREATE UNIQUE INDEX uq_execution_intents_place_client_order ON execution_intents USING btree (session_id, client_order_id) WHERE ((action)::text = 'PLACE'::text);

CREATE UNIQUE INDEX uq_ledger_entries_idempotency_key ON ledger_entries USING btree (idempotency_key) WHERE (idempotency_key IS NOT NULL);

CREATE UNIQUE INDEX uq_live_session_events_idempotency ON live_session_events USING btree (session_id, command, COALESCE(actor_id, (0)::bigint), idempotency_key);

CREATE UNIQUE INDEX uq_live_sessions_single_non_terminal ON live_sessions USING btree (exchange_account_id, venue) WHERE ((state)::text = ANY ((ARRAY['APPROVAL_PENDING'::character varying, 'APPROVED'::character varying, 'LIVE_WARMUP'::character varying, 'LIVE_ACTIVE'::character varying, 'LIVE_PAUSED'::character varying, 'LIVE_STOPPED'::character varying, 'LIVE_RECONCILING'::character varying, 'RECONCILIATION_BLOCKED'::character varying])::text[]));

CREATE UNIQUE INDEX uq_operator_execution_authorities_single_active ON operator_execution_authorities USING btree ((1)) WHERE ((status)::text = 'ACTIVE'::text);

CREATE UNIQUE INDEX uq_orders_account_dedup_key ON orders USING btree (account_id, dedup_key) WHERE (dedup_key IS NOT NULL);

CREATE UNIQUE INDEX uq_orders_strategy_run ON orders USING btree (strategy_run_id) WHERE (strategy_run_id IS NOT NULL);

CREATE UNIQUE INDEX uq_strategy_run_window_admission ON strategy_runs USING btree (strategy_id, account_id, admission_schedule_id, admission_due_at) WHERE (admission_schedule_id IS NOT NULL);

CREATE UNIQUE INDEX uq_strategy_sim_decision_request ON strategy_sim_decisions USING btree ("left"((decision_id)::text, 60));

CREATE UNIQUE INDEX uq_strategy_sim_paper_run_canonical_account ON paper_trading_runs USING btree (canonical_account_id) WHERE (canonical_account_id IS NOT NULL);

CREATE UNIQUE INDEX uq_trades_exchange_code_exchange_trade_id ON trades USING btree (exchange_code, exchange_trade_id) WHERE (exchange_trade_id IS NOT NULL);

-- 事实不变量触发器。

CREATE TRIGGER trg_backtest_eval_admission_revision AFTER INSERT OR DELETE OR UPDATE ON backtest_eval_reports FOR EACH ROW EXECUTE FUNCTION bump_admission_for_evaluation_mutation();

CREATE TRIGGER trg_backtest_publish_admission_revision_update AFTER UPDATE ON backtest_publish_records FOR EACH ROW EXECUTE FUNCTION bump_admission_for_publish_update();

CREATE TRIGGER trg_backtest_publish_admission_state_initialize AFTER INSERT ON backtest_publish_records FOR EACH ROW EXECUTE FUNCTION initialize_strategy_release_admission_state();

CREATE TRIGGER trg_backtest_publish_artifact_locator_immutable BEFORE UPDATE OF artifact_storage_key, manifest_storage_key ON backtest_publish_records FOR EACH ROW EXECUTE FUNCTION prevent_backtest_publish_artifact_locator_rebind();

CREATE TRIGGER trg_backtest_run_admission_revision AFTER DELETE OR UPDATE ON backtest_runs FOR EACH ROW EXECUTE FUNCTION bump_admission_for_backtest_run_mutation();

CREATE TRIGGER trg_cancel_contradiction_v51 AFTER INSERT ON trades FOR EACH ROW EXECUTE FUNCTION nq_observe_cancel_contradiction_v51();

CREATE TRIGGER trg_cancel_finality_truncate_v51 BEFORE TRUNCATE ON ordinary_order_cancel_finality FOR EACH STATEMENT EXECUTE FUNCTION nq_guard_cancel_finality_v51();

CREATE TRIGGER trg_cancel_finality_v51 BEFORE INSERT OR DELETE OR UPDATE ON ordinary_order_cancel_finality FOR EACH ROW EXECUTE FUNCTION nq_guard_cancel_finality_v51();

CREATE TRIGGER trg_canonical_legacy_bridge BEFORE UPDATE OF legacy_account_id ON exchange_accounts FOR EACH ROW EXECUTE FUNCTION guard_canonical_account_compatibility_bridge();

CREATE TRIGGER trg_canonical_legacy_bridge_insert BEFORE INSERT ON exchange_accounts FOR EACH ROW EXECUTE FUNCTION guard_canonical_account_compatibility_bridge();

CREATE TRIGGER trg_close_operator_authority_with_lease AFTER UPDATE ON controlled_execution_leases FOR EACH ROW EXECUTE FUNCTION close_operator_execution_authority_with_lease();

CREATE TRIGGER trg_controlled_execution_lease_authority BEFORE INSERT ON controlled_execution_leases FOR EACH ROW EXECUTE FUNCTION guard_controlled_execution_lease_authority();

CREATE TRIGGER trg_controlled_execution_lease_events_append_only BEFORE DELETE OR UPDATE ON controlled_execution_lease_events FOR EACH ROW EXECUTE FUNCTION reject_controlled_execution_fact_mutation();

CREATE TRIGGER trg_controlled_execution_lease_intents_append_only BEFORE DELETE OR UPDATE ON controlled_execution_lease_intents FOR EACH ROW EXECUTE FUNCTION reject_controlled_execution_fact_mutation();

CREATE TRIGGER trg_controlled_execution_leases_guard BEFORE DELETE OR UPDATE ON controlled_execution_leases FOR EACH ROW EXECUTE FUNCTION guard_controlled_execution_lease_update();

CREATE CONSTRAINT TRIGGER trg_execution_instrument_items_complete AFTER INSERT ON execution_instrument_observation_items DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_execution_observation_set();

CREATE TRIGGER trg_execution_instrument_items_evidence_insert_guard BEFORE INSERT ON execution_instrument_observation_items FOR EACH ROW EXECUTE FUNCTION guard_execution_instrument_item_evidence_insert();

CREATE TRIGGER trg_execution_instrument_observation_items_append_only BEFORE DELETE OR UPDATE ON execution_instrument_observation_items FOR EACH ROW EXECUTE FUNCTION reject_live_control_fact_mutation();

CREATE TRIGGER trg_execution_intents_guard BEFORE DELETE OR UPDATE ON execution_intents FOR EACH ROW EXECUTE FUNCTION guard_execution_intent_update();

CREATE TRIGGER trg_execution_intents_insert_guard BEFORE INSERT ON execution_intents FOR EACH ROW EXECUTE FUNCTION guard_execution_intent_insert();

CREATE CONSTRAINT TRIGGER trg_execution_observation_set_complete AFTER INSERT ON execution_prerequisite_observations DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_execution_observation_set();

CREATE TRIGGER trg_execution_prerequisite_observations_append_only BEFORE DELETE OR UPDATE ON execution_prerequisite_observations FOR EACH ROW EXECUTE FUNCTION reject_live_control_fact_mutation();

CREATE TRIGGER trg_execution_prerequisite_observations_insert_guard BEFORE INSERT ON execution_prerequisite_observations FOR EACH ROW EXECUTE FUNCTION guard_execution_prerequisite_observation_insert();

CREATE TRIGGER trg_execution_prerequisite_observations_v2_insert_guard BEFORE INSERT ON execution_prerequisite_observations FOR EACH ROW EXECUTE FUNCTION guard_execution_instrument_observation_schema_insert();

CREATE TRIGGER trg_execution_receipts_append_only BEFORE DELETE OR UPDATE ON execution_receipts FOR EACH ROW EXECUTE FUNCTION reject_live_control_fact_mutation();

CREATE TRIGGER trg_execution_scope_bindings_immutable BEFORE DELETE OR UPDATE ON execution_scope_bindings FOR EACH ROW EXECUTE FUNCTION reject_live_control_fact_mutation();

CREATE TRIGGER trg_execution_scope_bindings_insert_guard BEFORE INSERT ON execution_scope_bindings FOR EACH ROW EXECUTE FUNCTION guard_execution_scope_insert();

CREATE TRIGGER trg_live_session_events_append_only BEFORE DELETE OR UPDATE ON live_session_events FOR EACH ROW EXECUTE FUNCTION reject_live_control_fact_mutation();

CREATE TRIGGER trg_live_sessions_guard BEFORE DELETE OR UPDATE ON live_sessions FOR EACH ROW EXECUTE FUNCTION guard_live_session_update();

CREATE TRIGGER trg_live_sessions_insert_guard BEFORE INSERT ON live_sessions FOR EACH ROW EXECUTE FUNCTION guard_live_session_insert();

CREATE TRIGGER trg_market_snapshot_insert BEFORE INSERT ON execution_prerequisite_observations FOR EACH ROW EXECUTE FUNCTION guard_execution_market_snapshot_insert();

CREATE TRIGGER trg_marketdata_dataset_admission_revision_delete AFTER DELETE ON marketdata_datasets REFERENCING OLD TABLE AS marketdata_dataset_deleted_rows FOR EACH STATEMENT EXECUTE FUNCTION bump_admission_for_dataset_delete();

CREATE TRIGGER trg_marketdata_dataset_admission_revision_update AFTER UPDATE ON marketdata_datasets REFERENCING OLD TABLE AS marketdata_dataset_old_rows NEW TABLE AS marketdata_dataset_new_rows FOR EACH STATEMENT EXECUTE FUNCTION bump_admission_for_dataset_update();

CREATE TRIGGER trg_operator_approvals_append_only BEFORE DELETE OR UPDATE ON operator_approvals FOR EACH ROW EXECUTE FUNCTION reject_live_control_fact_mutation();

CREATE TRIGGER trg_operator_approvals_insert_guard BEFORE INSERT ON operator_approvals FOR EACH ROW EXECUTE FUNCTION guard_operator_execution_approval_insert();

CREATE TRIGGER trg_operator_execution_authorities_insert_guard BEFORE INSERT ON operator_execution_authorities FOR EACH ROW EXECUTE FUNCTION guard_operator_execution_authority_insert();

CREATE TRIGGER trg_operator_execution_authorities_update_guard BEFORE DELETE OR UPDATE ON operator_execution_authorities FOR EACH ROW EXECUTE FUNCTION guard_operator_execution_authority_update();

CREATE TRIGGER trg_operator_execution_intent_count BEFORE INSERT ON controlled_execution_lease_intents FOR EACH ROW EXECUTE FUNCTION guard_operator_execution_intent_count();

CREATE TRIGGER trg_orders_venue_metadata BEFORE INSERT OR UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION sync_order_venue_metadata();

CREATE CONSTRAINT TRIGGER trg_ordinary_no_order_finality AFTER INSERT OR UPDATE ON ordinary_place_authorities DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nq_require_ordinary_no_order_finality();

CREATE TRIGGER trg_ordinary_order_binding BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION nq_guard_ordinary_order_binding();

CREATE TRIGGER trg_ordinary_place_authority_guard BEFORE INSERT OR DELETE OR UPDATE ON ordinary_place_authorities FOR EACH ROW EXECUTE FUNCTION nq_guard_ordinary_place_authority();

CREATE TRIGGER trg_ordinary_place_authority_truncate BEFORE TRUNCATE ON ordinary_place_authorities FOR EACH STATEMENT EXECUTE FUNCTION nq_guard_ordinary_place_authority();

CREATE TRIGGER trg_paper_run_admission_revision AFTER INSERT OR DELETE OR UPDATE ON paper_trading_runs FOR EACH ROW EXECUTE FUNCTION bump_admission_for_paper_mutation();

CREATE TRIGGER trg_preserve_strategy_admission_identity BEFORE UPDATE ON strategy_runs FOR EACH ROW EXECUTE FUNCTION nq_preserve_strategy_admission_identity();

CREATE TRIGGER trg_public_capture_dataset_immutable BEFORE DELETE OR UPDATE ON marketdata_datasets FOR EACH ROW EXECUTE FUNCTION reject_public_capture_dataset_mutation();

CREATE TRIGGER trg_public_market_capture_immutable BEFORE DELETE OR UPDATE ON public_market_captures FOR EACH ROW EXECUTE FUNCTION reject_public_market_capture_mutation();

CREATE TRIGGER trg_public_market_rule_raw_required BEFORE INSERT ON public_market_captures FOR EACH ROW EXECUTE FUNCTION require_public_market_rule_raw_identity();

CREATE TRIGGER trg_recovery_decision_immutable BEFORE DELETE OR UPDATE ON execution_pre_place_recovery_decisions FOR EACH ROW EXECUTE FUNCTION reject_execution_recovery_decision_mutation();

CREATE TRIGGER trg_recovery_decision_insert BEFORE INSERT ON execution_pre_place_recovery_decisions FOR EACH ROW EXECUTE FUNCTION guard_execution_recovery_decision_insert();

CREATE TRIGGER trg_replacement_lease_insert BEFORE INSERT ON controlled_execution_leases FOR EACH ROW EXECUTE FUNCTION guard_execution_replacement_lease_insert();

CREATE CONSTRAINT TRIGGER trg_require_strategy_work_v51 AFTER INSERT ON strategy_runs DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nq_require_strategy_work_v51();

CREATE TRIGGER trg_risk_limit_sets_immutable BEFORE DELETE OR UPDATE ON risk_limit_sets FOR EACH ROW EXECUTE FUNCTION reject_live_control_fact_mutation();

CREATE TRIGGER trg_shadow_consistency_admission_revision AFTER INSERT OR DELETE OR UPDATE ON shadow_consistency_reports FOR EACH ROW EXECUTE FUNCTION bump_admission_for_consistency_mutation();

CREATE TRIGGER trg_shadow_run_admission_revision AFTER INSERT OR DELETE OR UPDATE ON shadow_runs FOR EACH ROW EXECUTE FUNCTION bump_admission_for_shadow_mutation();

CREATE TRIGGER trg_strategy_cursor_v51 BEFORE UPDATE ON strategy_schedules FOR EACH ROW EXECUTE FUNCTION nq_guard_strategy_cursor_v51();

CREATE CONSTRAINT TRIGGER trg_strategy_effective_prepare_v51 AFTER UPDATE ON strategy_run_dispatch_work DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nq_require_strategy_prepare_v51();

CREATE TRIGGER trg_strategy_order_truncate_v51 BEFORE TRUNCATE ON orders FOR EACH STATEMENT EXECUTE FUNCTION nq_guard_strategy_order_v51();

CREATE TRIGGER trg_strategy_order_v51 BEFORE INSERT OR DELETE OR UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION nq_guard_strategy_order_v51();

CREATE CONSTRAINT TRIGGER trg_strategy_prepare_order_v51 AFTER INSERT ON orders DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nq_require_strategy_prepare_v51();

CREATE CONSTRAINT TRIGGER trg_strategy_prepare_run_v51 AFTER UPDATE ON strategy_runs DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nq_require_strategy_prepare_v51();

CREATE TRIGGER trg_strategy_release_identity_immutable BEFORE UPDATE OF release_artifact_digest, manifest_fingerprint, manifest_schema_version, identity_bound_at ON strategy_release_admission_state FOR EACH ROW EXECUTE FUNCTION prevent_strategy_release_identity_rebind();

CREATE TRIGGER trg_strategy_release_revision_monotonic BEFORE UPDATE OF admission_revision ON strategy_release_admission_state FOR EACH ROW EXECUTE FUNCTION prevent_strategy_release_revision_rewrite();

CREATE TRIGGER trg_strategy_run_truncate_v51 BEFORE TRUNCATE ON strategy_runs FOR EACH STATEMENT EXECUTE FUNCTION nq_guard_strategy_run_v51();

CREATE TRIGGER trg_strategy_run_v51 BEFORE INSERT OR DELETE OR UPDATE ON strategy_runs FOR EACH ROW EXECUTE FUNCTION nq_guard_strategy_run_v51();

CREATE TRIGGER trg_strategy_sim_guard_sim_order BEFORE INSERT ON orders FOR EACH ROW EXECUTE FUNCTION strategy_sim_guard_sim_order();

CREATE TRIGGER trg_strategy_sim_preserve_decision BEFORE DELETE OR UPDATE ON strategy_sim_decisions FOR EACH ROW EXECUTE FUNCTION strategy_sim_preserve_decision();

CREATE TRIGGER trg_strategy_sim_preserve_sim_account BEFORE UPDATE ON paper_trading_runs FOR EACH ROW EXECUTE FUNCTION strategy_sim_preserve_sim_account();

CREATE TRIGGER trg_strategy_version_admission_revision_delete AFTER DELETE ON strategy_versions REFERENCING OLD TABLE AS strategy_version_deleted_rows FOR EACH STATEMENT EXECUTE FUNCTION bump_admission_for_strategy_version_delete();

CREATE TRIGGER trg_strategy_version_admission_revision_update AFTER UPDATE ON strategy_versions REFERENCING OLD TABLE AS strategy_version_old_rows NEW TABLE AS strategy_version_new_rows FOR EACH STATEMENT EXECUTE FUNCTION bump_admission_for_strategy_version_update();

CREATE TRIGGER trg_strategy_work_truncate_v51 BEFORE TRUNCATE ON strategy_run_dispatch_work FOR EACH STATEMENT EXECUTE FUNCTION nq_guard_strategy_work_v51();

CREATE TRIGGER trg_strategy_work_v51 BEFORE INSERT OR DELETE OR UPDATE ON strategy_run_dispatch_work FOR EACH ROW EXECUTE FUNCTION nq_guard_strategy_work_v51();

CREATE TRIGGER trg_trades_venue_metadata BEFORE INSERT OR UPDATE ON trades FOR EACH ROW EXECUTE FUNCTION sync_trade_venue_metadata();

-- 业务含义、权限与恢复边界。

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';

COMMENT ON FUNCTION bump_strategy_release_admission_revision(p_publish_record_id character varying) IS '统一单 release revision bump；state row 缺失时 fail-closed，禁止 silent no-op';

COMMENT ON FUNCTION bump_strategy_release_admission_revisions(p_publish_record_ids character varying[]) IS '去重并按 publish_record_id 升序 bump；fan-out 硬上限 256，可通过事务配置收紧但不可放宽，超限 fail-closed';

COMMENT ON FUNCTION canonical_legacy_account_code(p_exchange_account_id bigint) IS 'canonical exchange account到历史trading account的稳定account_code。';

COMMENT ON FUNCTION close_operator_execution_authority_with_lease() IS 'lease终态时同事务关闭或过期operator authority。';

COMMENT ON FUNCTION controlled_execution_boundary_is_empty() IS '判断受控执行边界是否完全为空；发送开始前不得有意图、回执、订单、成交或账务事实。';

COMMENT ON FUNCTION execution_evidence_instant_canonical(p_value timestamp with time zone) IS '输出固定 UTC 六位微秒 canonical JSON string。';

COMMENT ON FUNCTION execution_evidence_numeric_canonical(p_value numeric) IS '输出 observation decimal 的 plain、去尾零 canonical 文本。';

COMMENT ON FUNCTION execution_instrument_items_canonical(p_observation_id uuid) IS '按 v1/v2 schema 重建确定性 instrument item canonical bytes；NOT_PUBLISHED 不编码空值字段。';

COMMENT ON FUNCTION execution_instrument_metadata_digest(p_observation_id uuid) IS '从排序 instrument items 重建 typed instrument metadata digest。';

COMMENT ON FUNCTION execution_market_snapshot_digest(p_instrument text, p_best_ask numeric, p_observed_at timestamp with time zone, p_source_identity text, p_source_schema_version text) IS '按 Java market-snapshot-observation.v1 相同字节合同重建 lowercase SHA-256。';

COMMENT ON FUNCTION execution_observation_payload_hash(p_observation_id uuid) IS '重建五类 typed prerequisite envelope canonical SHA-256。';

COMMENT ON FUNCTION guard_controlled_execution_lease_authority() IS '绑定LiveSession、operator authority、binding lease窗口与金额。';

COMMENT ON FUNCTION guard_execution_instrument_item_evidence_insert() IS '绑定 instrument observation schema 与 evidence class，禁止伪造 published 或 legacy 语义。';

COMMENT ON FUNCTION guard_execution_instrument_observation_schema_insert() IS '禁止 migration 后的新 production instrument observation 继续写入 v1 contract。';

COMMENT ON FUNCTION guard_execution_intent_update() IS '保护执行意图不可变事实、版本和状态迁移；不能绕过租约或发送边界。';

COMMENT ON FUNCTION guard_execution_market_snapshot_insert() IS '绑定固定 OKX ticker source、session instrument 与 canonical digest。';

COMMENT ON FUNCTION guard_live_session_update() IS '保护会话身份、版本、事件序列和合法状态迁移；执行范围不可就地修改。';

COMMENT ON FUNCTION guard_operator_execution_authority_insert() IS '拒绝非canonical或账户引用失配的operator authority。';

COMMENT ON FUNCTION guard_operator_execution_authority_update() IS '只允许ACTIVE到CLOSED/EXPIRED且scope不可变。';

COMMENT ON FUNCTION guard_operator_execution_intent_count() IS '按operator authority限制PLACE/CANCEL各最多一次。';

COMMENT ON FUNCTION operator_execution_authority_digest(p_authority_id uuid, p_owner_user_id bigint, p_exchange_account_id bigint, p_credential_reference_id bigint, p_instrument text, p_side text, p_order_type text, p_max_notional numeric, p_max_place_count integer, p_max_cancel_count integer, p_transfer_allowed boolean, p_withdraw_allowed boolean, p_valid_from timestamp with time zone, p_expires_at timestamp with time zone, p_created_by bigint, p_created_at timestamp with time zone) IS '按 Java operator-execution-authority.v1 字节合同重建 lowercase SHA-256。';

COMMENT ON FUNCTION prevent_backtest_publish_artifact_locator_rebind() IS '禁止 Strategy Release artifact locator 静默重绑；只允许 FAILED 且未绑定的 row 在成功重试时完成首次成对绑定';

COMMENT ON FUNCTION prevent_strategy_release_identity_rebind() IS '数据库层仅允许 identity quartet 从全 NULL 到完整 non-NULL 一次，之后禁止 rebind/clear/partial mutation';

COMMENT ON FUNCTION prevent_strategy_release_revision_rewrite() IS '禁止 direct SQL 回退、同值或跳跃改写 admission revision；每次 row update 只允许 authoritative old + 1';

COMMENT ON FUNCTION reconstruct_execution_scope_hash(p_execution_scope_id uuid) IS '从权威会话与不可变执行范围重建 execution-scope.v1 SHA-256。';

COMMENT ON FUNCTION reject_live_control_fact_mutation() IS '数据库层拒绝不可变及仅追加事实的 UPDATE 和 DELETE。';

COMMENT ON FUNCTION require_canonical_trading_symbols(p_symbols text[]) IS '验证交易标的大写、BASE-USDT、排序与唯一性约束。';

COMMENT ON FUNCTION validate_execution_observation_set() IS '提交时校验五类 observation、instrument items 与全部 payload hash。';


SET default_tablespace = '';

SET default_table_access_method = heap;

COMMENT ON TABLE account_snapshots IS '账户资金快照表。保存账户维度的余额、可用、冻结投影，不直接参与订单幂等。';

COMMENT ON COLUMN account_snapshots.snapshot_id IS '内部账户快照主键。';

COMMENT ON COLUMN account_snapshots.account_id IS '账户维度外键。';

COMMENT ON COLUMN account_snapshots.currency IS '币种标识。';

COMMENT ON COLUMN account_snapshots.balance IS '总余额。';

COMMENT ON COLUMN account_snapshots.available IS '可用余额。';

COMMENT ON COLUMN account_snapshots.frozen IS '冻结余额。';

COMMENT ON COLUMN account_snapshots.ts IS '源成交或注资事件时间，不代表投影生成或对外发布时间。';

COMMENT ON COLUMN account_snapshots.trace_id IS '产出该快照的链路追踪 ID。';

COMMENT ON COLUMN account_snapshots.created_at IS '快照记录创建时间。';

COMMENT ON COLUMN account_snapshots.trade_env IS '写入时由 canonical 成交或 SIM 注资上下文显式提供的交易环境；历史空值表示不可判定。';

COMMENT ON COLUMN account_snapshots.balance_basis IS '仓位投影或 NQ 账本现金投影；均非交易所全账户余额。';

COMMENT ON COLUMN account_snapshots.balance_scope IS 'NQ 自身管理的账户投影范围；不代表交易所全账户资产覆盖。';

COMMENT ON COLUMN account_snapshots.recorded_at IS 'NQ 在数据库中插入投影的实际时间；事务提交可能更晚。';

COMMENT ON TABLE accounts IS '交易账户表。保存账户主键、账户业务编码和账户绑定交易所，不区分策略定义级身份。';

COMMENT ON COLUMN accounts.account_id IS '内部账户主键。';

COMMENT ON COLUMN accounts.account_code IS '账户业务唯一编码。';

COMMENT ON COLUMN accounts.venue IS '账户绑定交易所标识。当前仍沿用历史 venue 命名，语义对应统一交易所标识。';

COMMENT ON COLUMN accounts.status IS 'legacy 账户状态，允许值：ACTIVE / DISABLED；用于兼容旧账户模型的启停语义，不表示删除。';

COMMENT ON COLUMN accounts.created_at IS '账户创建时间。';

COMMENT ON COLUMN accounts.updated_at IS 'legacy 账户配置最后更新时间；新增时从默认值回填，后续由账户配置维护逻辑更新。';

COMMENT ON TABLE audit_logs IS '审计日志表。保存 domain / action / actor / detail 的最小审计证据，不直接承载业务主状态。';

COMMENT ON COLUMN audit_logs.id IS '内部审计日志主键。';

COMMENT ON COLUMN audit_logs.domain IS '审计域，例如 ORDER / RECONCILE / WS。';

COMMENT ON COLUMN audit_logs.action IS '审计动作，例如 CREATED / FAILED / COMPLETED。';

COMMENT ON COLUMN audit_logs.actor_id IS '审计主体标识。通常是内部对象 ID，不是交易所外部标识。';

COMMENT ON COLUMN audit_logs.trace_id IS '链路追踪 ID。';

COMMENT ON COLUMN audit_logs.detail_json IS '审计详情 JSON；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN audit_logs.created_at IS '审计日志创建时间。';

COMMENT ON TABLE backtest_configs IS '回测配置表。保存研究配置派生出的回测运行窗口、执行参数和评估参数。';

COMMENT ON COLUMN backtest_configs.backtest_config_id IS '回测配置主键。';

COMMENT ON COLUMN backtest_configs.research_config_id IS '所属研究配置 ID。';

COMMENT ON COLUMN backtest_configs.name IS '回测配置展示名称。';

COMMENT ON COLUMN backtest_configs.description IS '回测配置描述信息。';

COMMENT ON COLUMN backtest_configs.config_json IS '回测配置快照 JSON，包括时间窗口、初始资金和 executionSpec；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN backtest_configs.evaluation_spec_json IS '评估参数 JSON，用于保存回测评估的指标口径和计算配置；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN backtest_configs.created_at IS '回测配置创建时间。';

COMMENT ON COLUMN backtest_configs.updated_at IS '回测配置元数据最后更新时间；仅表示配置窗口、执行参数、dataset/strategy 绑定或归档状态变化，不表示运行事实、评估结果、发布记录或交易事实更新时间。';

COMMENT ON COLUMN backtest_configs.dataset_id IS '绑定的数据集 ID，对应 marketdata_datasets.dataset_id；为空表示尚未绑定正式数据集。';

COMMENT ON COLUMN backtest_configs.dataset_snapshot_json IS '回测配置绑定数据集时保存的数据集快照 JSONB，用于配置详情和后续 run 溯源；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN backtest_configs.strategy_version_id IS '回测配置绑定的策略版本 ID，对应 strategy_versions.strategy_version_id；为空表示尚未绑定策略版本。';

COMMENT ON COLUMN backtest_configs.strategy_version_snapshot_json IS '回测配置绑定策略版本时固化的策略版本快照 JSONB，包含策略编码、版本号、状态、参数、配置、来源和 checksum；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN backtest_configs.param_snapshot_json IS '回测配置绑定策略版本时固化的参数快照 JSONB，用作后续 run 输入追溯；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN backtest_configs.config_snapshot_json IS '回测配置自身的配置快照 JSONB，从既有 config_json 回填，用于与策略版本配置快照区分；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN backtest_configs.status IS '回测配置状态，允许值：ACTIVE / ARCHIVED / DISABLED；ARCHIVED 表示用户不再使用但仍保留回测输入血缘，DISABLED 表示临时停用，不表示物理删除。';

COMMENT ON COLUMN backtest_configs.archived_at IS '回测配置归档时间；仅当 status=ARCHIVED 时非空，用于记录配置进入归档状态的时间。';

COMMENT ON COLUMN backtest_configs.archived_by IS '回测配置归档操作者标识，可为空；只保存内部用户或系统主体标识，不保存密钥、token、API secret、私钥、助记词或账户访问材料。';

COMMENT ON COLUMN backtest_configs.archive_reason IS '回测配置归档原因，可为空；用于说明归档背景，不得保存密钥、token、API secret、私钥、助记词、cookie 或账户访问材料。';

COMMENT ON TABLE backtest_eval_reports IS '回测评估报告表。保存 run 级评估结果、关键指标与评估失败信息，不改写 sim_* 事实。';

COMMENT ON COLUMN backtest_eval_reports.eval_report_id IS '评估报告主键。';

COMMENT ON COLUMN backtest_eval_reports.backtest_run_id IS '所属回测运行 ID，唯一约束保证同一 run 仅保留一条评估报告。';

COMMENT ON COLUMN backtest_eval_reports.evaluation_status IS '评估状态，固定口径为 SUCCEEDED / FAILED。';

COMMENT ON COLUMN backtest_eval_reports.initial_capital IS '评估使用的初始资金。';

COMMENT ON COLUMN backtest_eval_reports.final_cash_balance IS '最终现金余额。';

COMMENT ON COLUMN backtest_eval_reports.final_position_market_value IS '最终持仓市值。';

COMMENT ON COLUMN backtest_eval_reports.final_equity IS '最终权益。';

COMMENT ON COLUMN backtest_eval_reports.realized_pnl IS '最终已实现 PnL。';

COMMENT ON COLUMN backtest_eval_reports.unrealized_pnl IS '最终未实现 PnL。';

COMMENT ON COLUMN backtest_eval_reports.net_pnl IS '净 PnL。';

COMMENT ON COLUMN backtest_eval_reports.total_return_rate IS '总收益率，固定口径为 netPnl / initialCapital。';

COMMENT ON COLUMN backtest_eval_reports.total_fee IS '累计手续费。';

COMMENT ON COLUMN backtest_eval_reports.total_slippage IS '累计滑点。';

COMMENT ON COLUMN backtest_eval_reports.order_count IS '模拟订单数量。';

COMMENT ON COLUMN backtest_eval_reports.trade_count IS '模拟成交数量。';

COMMENT ON COLUMN backtest_eval_reports.winning_trade_count IS '盈利闭合交易数量。';

COMMENT ON COLUMN backtest_eval_reports.losing_trade_count IS '亏损闭合交易数量。';

COMMENT ON COLUMN backtest_eval_reports.flat_trade_count IS '盈亏为零的闭合交易数量。';

COMMENT ON COLUMN backtest_eval_reports.win_rate IS '胜率。';

COMMENT ON COLUMN backtest_eval_reports.max_drawdown IS '最大回撤。';

COMMENT ON COLUMN backtest_eval_reports.max_drawdown_rate IS '最大回撤率。';

COMMENT ON COLUMN backtest_eval_reports.sharpe_ratio IS '非年化 SharpeRatio。';

COMMENT ON COLUMN backtest_eval_reports.report_json IS '评估明细 JSON；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN backtest_eval_reports.failure_code IS '评估失败码。';

COMMENT ON COLUMN backtest_eval_reports.failure_message IS '评估失败消息。';

COMMENT ON COLUMN backtest_eval_reports.evaluated_at IS '评估执行时间。';

COMMENT ON COLUMN backtest_eval_reports.created_at IS '评估报告创建时间。';

COMMENT ON COLUMN backtest_eval_reports.updated_at IS '评估报告最后更新时间。';

COMMENT ON COLUMN backtest_eval_reports.total_return IS '评估总收益指标，与既有 total_return_rate 同口径，固定为 net_pnl / initial_capital。';

COMMENT ON COLUMN backtest_eval_reports.annualized_return IS '年化收益率指标，按评估权益快照首尾时间差折算；时间差不可用时为空。';

COMMENT ON COLUMN backtest_eval_reports.profit_loss_ratio IS '盈亏比指标，固定口径为闭合盈利交易总收益 / 闭合亏损交易绝对值；亏损为 0 时返回 0。';

COMMENT ON COLUMN backtest_eval_reports.metrics_json IS '评估核心指标汇总 JSONB，保存 total_return、annualized_return、max_drawdown、win_rate、profit_loss_ratio、trade_count、sharpe_ratio 等展示指标；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON TABLE backtest_publish_records IS '研究产物发布记录表。保存回测运行到执行域 strategy_definition 的发布事实、来源快照与失败信息。';

COMMENT ON COLUMN backtest_publish_records.publish_record_id IS '发布记录主键。';

COMMENT ON COLUMN backtest_publish_records.backtest_run_id IS '所属回测运行 ID，唯一约束保证同一 run 仅保留一条发布事实。';

COMMENT ON COLUMN backtest_publish_records.research_config_id IS '来源研究配置 ID。';

COMMENT ON COLUMN backtest_publish_records.backtest_config_id IS '来源回测配置 ID。';

COMMENT ON COLUMN backtest_publish_records.source_strategy_id IS '来源执行域 strategy_definition ID。';

COMMENT ON COLUMN backtest_publish_records.eval_report_id IS '关联评估报告 ID。';

COMMENT ON COLUMN backtest_publish_records.target_strategy_definition_id IS '发布后生成的执行域 strategy_definition ID。';

COMMENT ON COLUMN backtest_publish_records.publish_status IS '发布状态，固定口径为 SUCCEEDED / FAILED。';

COMMENT ON COLUMN backtest_publish_records.publish_name IS '发布展示名。';

COMMENT ON COLUMN backtest_publish_records.publish_snapshot_json IS '发布映射快照 JSON；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN backtest_publish_records.evaluation_summary_json IS '发布时固化的评估摘要 JSON；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN backtest_publish_records.failure_code IS '发布失败码。';

COMMENT ON COLUMN backtest_publish_records.failure_message IS '发布失败消息。';

COMMENT ON COLUMN backtest_publish_records.published_at IS '发布完成时间。';

COMMENT ON COLUMN backtest_publish_records.created_at IS '发布记录创建时间。';

COMMENT ON COLUMN backtest_publish_records.updated_at IS '发布记录最后更新时间。';

COMMENT ON COLUMN backtest_publish_records.strategy_version_id IS '发布记录绑定的策略版本 ID，对应 strategy_versions.strategy_version_id；为空表示历史发布记录尚未绑定版本。';

COMMENT ON COLUMN backtest_publish_records.version_snapshot_json IS '发布时固化的策略版本快照 JSON，包含策略编码、版本号、参数、配置、来源和 checksum；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN backtest_publish_records.artifact_storage_key IS '服务端生成的 Strategy Release artifact-set opaque storage key；为空表示 LEGACY_ARTIFACT_UNBOUND，禁止保存路径、URL、digest、trusted root 或客户端输入';

COMMENT ON COLUMN backtest_publish_records.manifest_storage_key IS '服务端生成的 Strategy Release manifest opaque storage key；与 artifact_storage_key 成对绑定，为空表示 LEGACY_ARTIFACT_UNBOUND，不冻结 filename/layout';

COMMENT ON CONSTRAINT chk_backtest_publish_artifact_keys_pair ON backtest_publish_records IS 'artifact 与 manifest storage key 必须同时为空或同时存在；同时为空表示 LEGACY_ARTIFACT_UNBOUND';

COMMENT ON CONSTRAINT chk_backtest_publish_artifact_storage_key ON backtest_publish_records IS 'artifact_storage_key 只允许最多 128 字符的单段 ASCII opaque identifier，禁止 path、URL、冒号和双点序列';

COMMENT ON CONSTRAINT chk_backtest_publish_manifest_storage_key ON backtest_publish_records IS 'manifest_storage_key 只允许最多 128 字符的单段 ASCII opaque identifier，禁止 path、URL、冒号和双点序列';

COMMENT ON TABLE backtest_runs IS '回测运行表。保存回测运行身份、状态、失败信息与 run 级执行摘要，不保存 sim_* 明细。';

COMMENT ON COLUMN backtest_runs.backtest_run_id IS '回测运行主键。';

COMMENT ON COLUMN backtest_runs.backtest_config_id IS '所属回测配置 ID。';

COMMENT ON COLUMN backtest_runs.research_config_id IS '所属研究配置 ID。';

COMMENT ON COLUMN backtest_runs.source_strategy_id IS '来源策略定义 ID。';

COMMENT ON COLUMN backtest_runs.status IS '回测运行状态，固定口径为 CREATED / PREPARING / RUNNING / SUCCEEDED / FAILED / CANCELLED。';

COMMENT ON COLUMN backtest_runs.strategy_snapshot IS '运行时引用的来源策略快照；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN backtest_runs.backtest_config_snapshot IS '运行时引用的回测配置快照；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN backtest_runs.summary_json IS 'run 级执行摘要 JSON。只保存统计摘要，不保存 sim_* 明细事实、敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN backtest_runs.requested_at IS '回测运行请求创建时间。';

COMMENT ON COLUMN backtest_runs.started_at IS '显式 start run 后的执行开始时间。';

COMMENT ON COLUMN backtest_runs.finished_at IS '回测运行完成或失败时间。';

COMMENT ON COLUMN backtest_runs.failure_code IS '运行失败码。';

COMMENT ON COLUMN backtest_runs.failure_message IS '运行失败消息。';

COMMENT ON COLUMN backtest_runs.created_at IS '回测运行记录创建时间。';

COMMENT ON COLUMN backtest_runs.updated_at IS '回测运行记录最后更新时间。';

COMMENT ON COLUMN backtest_runs.dataset_snapshot_json IS '回测运行创建时从 backtest_configs 固化的数据集快照 JSONB，用于历史运行复盘与评估/发布溯源；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN backtest_runs.strategy_version_id IS '回测运行创建时从 backtest_configs 固化的策略版本 ID；后续配置重新绑定不会改写历史 run。';

COMMENT ON COLUMN backtest_runs.strategy_version_snapshot_json IS '回测运行创建时固化的策略版本快照 JSONB，用于历史运行、评估报告和后续发布追溯；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN backtest_runs.param_snapshot_json IS '回测运行创建时固化的参数快照 JSONB，来自回测配置绑定的策略版本参数快照；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN backtest_runs.config_snapshot_json IS '回测运行创建时固化的回测配置快照 JSONB，从 backtest_config_snapshot 回填；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON TABLE continuous_sim_bars IS '连续 SIM 实际观察到的单根公开 bar 身份；已处理值不得被行情修订覆盖';

COMMENT ON TABLE continuous_sim_runs IS '每条连续策略 SIM run 的持久进度和状态；不共享交易对全局游标';

COMMENT ON TABLE controlled_execution_lease_events IS '租约状态变化append-only审计；不替代Order、Fill、Ledger或ExecutionReceipt。';

COMMENT ON COLUMN controlled_execution_lease_events.event_id IS '不可复用审计事件UUID。';

COMMENT ON COLUMN controlled_execution_lease_events.lease_id IS '所属受控执行租约。';

COMMENT ON COLUMN controlled_execution_lease_events.from_status IS '变化前状态；创建事件为空。';

COMMENT ON COLUMN controlled_execution_lease_events.to_status IS '变化后租约状态。';

COMMENT ON COLUMN controlled_execution_lease_events.lease_version IS '事件对应租约version。';

COMMENT ON COLUMN controlled_execution_lease_events.reason_code IS '稳定脱敏原因码。';

COMMENT ON COLUMN controlled_execution_lease_events.request_id IS '脱敏请求关联标识。';

COMMENT ON COLUMN controlled_execution_lease_events.trace_id IS '脱敏链路关联标识。';

COMMENT ON COLUMN controlled_execution_lease_events.occurred_at IS 'UTC状态变化时间。';

COMMENT ON TABLE controlled_execution_lease_intents IS '租约与ExecutionIntent的append-only一对一动作绑定；数据库保证每个租约最多一次PLACE和一次CANCEL。';

COMMENT ON COLUMN controlled_execution_lease_intents.lease_id IS '所属受控执行租约；动作关联保持幂等与全局次数限制。';

COMMENT ON COLUMN controlled_execution_lease_intents.intent_id IS '既有execution_intents主事实引用。';

COMMENT ON COLUMN controlled_execution_lease_intents.action IS 'PLACE或CANCEL；主键保证每种动作最多一次。';

COMMENT ON COLUMN controlled_execution_lease_intents.created_at IS 'append-only绑定时间。';

COMMENT ON TABLE controlled_execution_leases IS '持久化的一次性受控执行租约；只提供有界执行窗口，不保存凭证或场所原文。';

COMMENT ON COLUMN controlled_execution_leases.lease_id IS '不可复用租约UUID。';

COMMENT ON COLUMN controlled_execution_leases.live_session_id IS '绑定既有LiveSession。';

COMMENT ON COLUMN controlled_execution_leases.binding_id IS '绑定的精确执行身份，不可修改或复用。';

COMMENT ON COLUMN controlled_execution_leases.binding_digest IS '精确执行绑定确定性编码的 lowercase SHA-256。';

COMMENT ON COLUMN controlled_execution_leases.status IS 'CREATED/ACTIVE/CONSUMED/EXPIRED/CLOSED/FAILED。';

COMMENT ON COLUMN controlled_execution_leases.max_notional IS '人工授权的名义金额硬上限，精度为 NUMERIC(38,8)。';

COMMENT ON COLUMN controlled_execution_leases.valid_from IS 'UTC租约生效时间。';

COMMENT ON COLUMN controlled_execution_leases.expires_at IS 'UTC硬过期时间；执行入口每次重新校验。';

COMMENT ON COLUMN controlled_execution_leases.consumed_at IS '唯一PLACE intent被持久绑定的时间。';

COMMENT ON COLUMN controlled_execution_leases.closed_at IS '终态关闭时间。';

COMMENT ON COLUMN controlled_execution_leases.created_by IS '发起受控执行的现有 OPERATOR 用户身份。';

COMMENT ON COLUMN controlled_execution_leases.version IS 'optimistic lifecycle version。';

COMMENT ON COLUMN controlled_execution_leases.created_at IS '数据库创建时间。';

COMMENT ON COLUMN controlled_execution_leases.updated_at IS '最后合法状态变化时间。';

COMMENT ON COLUMN controlled_execution_leases.operator_execution_authority_id IS '人工执行租约绑定的同一显式授权；策略租约为空。';

COMMENT ON COLUMN controlled_execution_leases.predecessor_lease_id IS 'replacement唯一前驱；旧lease保持终态。';

COMMENT ON COLUMN controlled_execution_leases.recovery_decision_id IS '绑定append-only零intent recovery判定。';

COMMENT ON COLUMN controlled_execution_leases.replacement_ordinal IS '不可由caller任意选择；successor insert trigger强制等于predecessor ordinal + 1。';

COMMENT ON COLUMN controlled_execution_leases.replacement_reason IS '再生原因：零执行失败或发送前终态再生；新后继固定为 PRE_PLACE_TERMINAL_REGENERATION。';

COMMENT ON CONSTRAINT chk_controlled_execution_leases_replacement ON controlled_execution_leases IS '序号零为起始租约，正序号为发送前终态再生；原因取值保持既有只读兼容约束。';

COMMENT ON TABLE credential_audit_logs IS '账户凭证 append-only 审计日志表。用于记录创建、校验、禁用、重新启用、撤销、轮换、过期、使用、拒绝访问和权限探活事件；不得 hard delete，不保存密钥、token、API secret、私钥、助记词、cookie、passphrase、签名、request body、raw response、明文 payload 或交易所凭证。';

COMMENT ON COLUMN credential_audit_logs.credential_audit_log_id IS '凭证审计日志主键。';

COMMENT ON COLUMN credential_audit_logs.credential_id IS '关联的账户凭证主键，引用 exchange_account_credentials.credential_id；凭证记录不得 hard delete。';

COMMENT ON COLUMN credential_audit_logs.exchange_account_id IS '关联的交易账户主键，引用 exchange_accounts.exchange_account_id，用于按账户追溯 credential 审计事件。';

COMMENT ON COLUMN credential_audit_logs.event_type IS '凭证审计事件类型，允许值：CREATED / VERIFIED / FAILED_VERIFICATION / DISABLED / ENABLED / REVOKED / ROTATED / EXPIRED / USED / ACCESS_DENIED / PERMISSION_PROBE_STARTED / PERMISSION_PROBE_SUCCEEDED / PERMISSION_PROBE_FAILED / PERMISSION_PROBE_SKIPPED；permission probe 事件仅表示未来权限探活审计语义已准备。';

COMMENT ON COLUMN credential_audit_logs.actor IS '事件操作者标识，可为空；只保存内部用户或系统主体标识，不保存密钥、token、API secret、私钥、助记词、cookie 或交易所凭证。';

COMMENT ON COLUMN credential_audit_logs.reason IS '事件原因或摘要，可为空；用于审计复盘，不得保存密钥、token、API secret、私钥、助记词、cookie、passphrase 或交易所凭证。';

COMMENT ON COLUMN credential_audit_logs.metadata IS '事件脱敏元数据，默认空 JSONB；只允许保存状态、结果码、request id、策略判断等审计上下文，不得保存密钥、token、API key、API secret、私钥、助记词、cookie、passphrase、签名、headers、request body、raw response、明文 payload 或交易所凭证。';

COMMENT ON COLUMN credential_audit_logs.created_at IS '凭证审计事件创建时间；append-only 记录的业务时间以新增日志为准。';

COMMENT ON TABLE emergency_stop_events IS 'Paper Trading 异常停机事件表：记录 Paper run 的紧急停机触发、执行和解除事实；只作用于 SIM/Paper，不触发真实 LIVE 下单或撤单。';

COMMENT ON COLUMN emergency_stop_events.emergency_stop_id IS '异常停机事件主键，业务可读 ID，例如 es-<uuid>';

COMMENT ON COLUMN emergency_stop_events.paper_run_id IS '所属 Paper run ID，外键 paper_trading_runs.paper_run_id';

COMMENT ON COLUMN emergency_stop_events.trigger_type IS '触发类型：MANUAL 手动触发；RISK_LIMIT 风控限额触发；SYSTEM_ERROR 系统异常触发';

COMMENT ON COLUMN emergency_stop_events.status IS '停机状态：TRIGGERED 已触发；APPLIED 已执行（run 已停止）；FAILED 执行失败（run 非 RUNNING）；RESOLVED 已解除';

COMMENT ON COLUMN emergency_stop_events.reason IS '停机原因摘要';

COMMENT ON COLUMN emergency_stop_events.triggered_by IS '触发人标识，来自登录上下文或系统标识';

COMMENT ON COLUMN emergency_stop_events.triggered_at IS '触发时间，UTC';

COMMENT ON COLUMN emergency_stop_events.resolved_at IS '解除时间，UTC；为空表示尚未解除';

COMMENT ON COLUMN emergency_stop_events.request_json IS '停机请求快照 JSONB，保存触发时的请求参数；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN emergency_stop_events.result_json IS '停机结果快照 JSONB，保存执行结果和错误信息；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN emergency_stop_events.created_at IS '记录写入时间，UTC';

COMMENT ON TABLE equity_curve_snapshots IS 'Paper Trading 资金曲线快照表：记录 Paper run 的权益时间序列，用于资金曲线展示和回撤计算。';

COMMENT ON COLUMN equity_curve_snapshots.equity_snapshot_id IS '资金曲线快照主键，业务可读 ID，例如 eqs-<uuid>';

COMMENT ON COLUMN equity_curve_snapshots.paper_run_id IS '所属 Paper run ID，外键 paper_trading_runs.paper_run_id';

COMMENT ON COLUMN equity_curve_snapshots.snapshot_time IS '快照时间点，UTC；按此字段排序形成资金曲线';

COMMENT ON COLUMN equity_curve_snapshots.total_equity IS '总权益 = cash_balance + position_value';

COMMENT ON COLUMN equity_curve_snapshots.cash_balance IS '现金余额';

COMMENT ON COLUMN equity_curve_snapshots.position_value IS '持仓市值';

COMMENT ON COLUMN equity_curve_snapshots.unrealized_pnl IS '未实现盈亏';

COMMENT ON COLUMN equity_curve_snapshots.realized_pnl IS '已实现盈亏累计';

COMMENT ON COLUMN equity_curve_snapshots.drawdown IS '当前回撤值，口径为 (peak_equity - current_equity) / peak_equity。';

COMMENT ON COLUMN equity_curve_snapshots.source IS '快照来源标识，例如 SYSTEM、MANUAL、RECONCILE';

COMMENT ON COLUMN equity_curve_snapshots.created_at IS '快照写入时间，UTC';

COMMENT ON TABLE event_store IS '事件总表。保存跨域事件事实链，key_value 用于主题内幂等与顺序消费。';

COMMENT ON COLUMN event_store.event_id IS '内部事件主键。';

COMMENT ON COLUMN event_store.topic IS '事件主题。';

COMMENT ON COLUMN event_store.schema_version IS '事件 schema 版本号。';

COMMENT ON COLUMN event_store.event_type IS '事件类型。';

COMMENT ON COLUMN event_store.payload_json IS '事件载荷 JSON；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN event_store.key_value IS '事件业务键，用于主题内幂等与聚合定位。';

COMMENT ON COLUMN event_store.trace_id IS '链路追踪 ID。';

COMMENT ON COLUMN event_store.created_at IS '事件写入时间。';

COMMENT ON TABLE exchange_account_credentials IS '交易账户凭证版本表。保存账户对应交易所凭证的密文版本、激活状态、校验状态与轮换关系，不以 env/yml 作为正式主数据源。';

COMMENT ON COLUMN exchange_account_credentials.credential_id IS '账户凭证主键。每次轮换都新增新记录，不覆盖旧版本。';

COMMENT ON COLUMN exchange_account_credentials.exchange_account_id IS '所属交易账户主键。一个交易账户可按 credential_type 拥有多版本凭证。';

COMMENT ON COLUMN exchange_account_credentials.credential_type IS '凭证类型，当前固定支持 OKX_API_V5 / BINANCE_HMAC / BINANCE_ED25519。';

COMMENT ON COLUMN exchange_account_credentials.encrypted_payload IS '凭证明文经数据库加密后的密文字节，不在应用层或仓库中以明文持久化。';

COMMENT ON COLUMN exchange_account_credentials.key_version IS '加密主密钥版本号。用于后续轮换、回放与审计定位。';

COMMENT ON COLUMN exchange_account_credentials.cipher_suite IS '加密算法套件，首版固定为 PGP_SYM_AES256。';

COMMENT ON COLUMN exchange_account_credentials.masked_access_key IS '脱敏后的 access key 或主标识，用于前端展示和审计定位，不可用于恢复明文。';

COMMENT ON COLUMN exchange_account_credentials.verification_status IS '最近一次校验状态，固定口径为 PENDING / VERIFIED / FAILED / REVOKED。';

COMMENT ON COLUMN exchange_account_credentials.is_active IS '是否为当前生效版本。唯一约束保证同一账户同一凭证类型仅一条 active。';

COMMENT ON COLUMN exchange_account_credentials.revoked_at IS '凭证被停用或撤销的时间。active 切换后旧版本应记录该时间。';

COMMENT ON COLUMN exchange_account_credentials.rotated_from_credential_id IS '当前版本所继承或轮换自的旧凭证主键，用于构建轮换链和审计血缘。';

COMMENT ON COLUMN exchange_account_credentials.last_verified_at IS '最近一次对当前凭证执行校验的时间。';

COMMENT ON COLUMN exchange_account_credentials.last_verification_error IS '最近一次校验失败的错误摘要；校验成功时可为空。';

COMMENT ON COLUMN exchange_account_credentials.created_at IS '凭证版本记录创建时间。';

COMMENT ON COLUMN exchange_account_credentials.updated_at IS '凭证版本记录最后更新时间。';

COMMENT ON COLUMN exchange_account_credentials.credential_status IS '凭证生命周期状态，允许值：ACTIVE / DISABLED / REVOKED / EXPIRED / ROTATED；独立于 verification_status，表示凭证是否可用以及不可用原因。';

COMMENT ON COLUMN exchange_account_credentials.revoked_by IS '凭证不可恢复撤销操作者标识，可为空；只保存内部用户或系统主体标识，不保存密钥、token、API secret、私钥、助记词、cookie 或交易所凭证。';

COMMENT ON COLUMN exchange_account_credentials.revoke_reason IS '凭证不可恢复撤销原因，可为空；用于安全审计和复盘，不得保存密钥、token、API secret、私钥、助记词、cookie、passphrase 或交易所凭证。';

COMMENT ON COLUMN exchange_account_credentials.rotated_at IS '凭证被新版本替换的时间，可为空；用于区分 ROTATED 生命周期与不可恢复 REVOKED 撤销。';

COMMENT ON COLUMN exchange_account_credentials.rotated_by IS '凭证轮换操作者标识，可为空；只保存内部用户或系统主体标识，不保存密钥、token、API secret、私钥、助记词、cookie 或交易所凭证。';

COMMENT ON COLUMN exchange_account_credentials.last_used_at IS '凭证最近一次被服务端业务路径使用的时间，可为空；不表示交易所在线校验成功，也不保存请求、签名或凭证明文。';

COMMENT ON COLUMN exchange_account_credentials.failed_auth_count IS '凭证认证或权限校验失败累计次数，最小值为 0；仅记录计数，不保存交易所返回的敏感错误上下文。';

COMMENT ON COLUMN exchange_account_credentials.permission_scope IS '凭证权限范围，可为空；允许值为 READ_ONLY / TRADE / FUNDING，NULL 表示尚未确认真实交易所权限，不代表允许交易、提现、LIVE 或 AI/DH 使用 credential。';

COMMENT ON COLUMN exchange_account_credentials.withdraw_enabled IS '凭证是否允许提现，默认 false；本字段只保存治理元数据，不代表系统实现提现能力、开启 LIVE trading 或允许资金转移；本轮未在未确认现有数据前新增强制 false CHECK。';

COMMENT ON COLUMN exchange_account_credentials.ip_allowlist_required IS '凭证是否要求交易所侧 IP allowlist，默认 true；只记录治理要求，不保存 IP 凭证、token、cookie 或网络访问密钥。';

COMMENT ON COLUMN exchange_account_credentials.external_secret_ref IS '外部密钥系统引用，可为空；只允许保存外部 Secret Manager / KMS 的引用标识，不得保存 secret、token、API key、private key、passphrase、cookie 或助记词。';

COMMENT ON COLUMN exchange_account_credentials.key_alias IS '密钥别名，可为空；只允许保存脱敏别名或外部密钥别名，不得保存 secret、token、API key、private key、passphrase、cookie 或助记词。';

COMMENT ON COLUMN exchange_account_credentials.permission_probe_status IS '真实交易所权限探活状态，允许值：NOT_PROBED / IN_PROGRESS / SUCCEEDED / FAILED / SKIPPED；本字段仅为后续 schema 准备，不表示 permission probe 已实现或真实交易所权限可用。';

COMMENT ON COLUMN exchange_account_credentials.last_permission_probe_at IS '最近一次真实交易所权限探活完成时间，可为空；独立于 last_verified_at 结构性校验时间和 last_used_at 业务使用时间，不保存请求、签名或凭证明文。';

COMMENT ON COLUMN exchange_account_credentials.last_permission_probe_error IS '最近一次权限探活脱敏错误摘要或错误分类，可为空；不得保存 secret、token、API key、API secret、私钥、助记词、cookie、passphrase、签名、headers、request body、raw response、明文 payload 或交易所凭证。';

COMMENT ON COLUMN exchange_account_credentials.ip_allowlist_probe_status IS '交易所侧 IP allowlist 探活状态，允许值：NOT_CHECKED / PASSED / FAILED / UNKNOWN / SKIPPED；只记录脱敏结果，不保存 IP 凭证、token、cookie、headers、签名、raw response 或网络访问密钥。';

COMMENT ON TABLE exchange_accounts IS '交易账户配置表。保存用户维度下的交易所账户、环境、别名与默认账户口径，是 legacy accounts 向正式账户模型迁移后的主承载表。';

COMMENT ON COLUMN exchange_accounts.exchange_account_id IS '交易账户主键。作为前端账户上下文与后端账户管理的 canonical 身份。';

COMMENT ON COLUMN exchange_accounts.owner_user_id IS '所属用户主键。一个用户可绑定多个交易所账户，所有账户数据归属于该用户。';

COMMENT ON COLUMN exchange_accounts.exchange_code IS '交易所编码，固定使用统一 canonical 口径，例如 OKX / BINANCE。';

COMMENT ON COLUMN exchange_accounts.trade_env IS '交易环境，固定枚举为 SIM / LIVE。legacy DOME / REAL 只允许在导入映射层存在。';

COMMENT ON COLUMN exchange_accounts.account_alias IS '用户在同一交易所与同一环境下区分多个账户的业务别名。';

COMMENT ON COLUMN exchange_accounts.external_account_ref IS '交易所侧账户引用或外部账户标识。可为空；非空时在同一交易所与环境范围内必须唯一。';

COMMENT ON COLUMN exchange_accounts.legacy_account_id IS '映射 accounts.account_id 的兼容身份桥接；绑定后不可变，必须满足环境、场所和账户状态约束。';

COMMENT ON COLUMN exchange_accounts.is_default IS '是否为该用户在当前交易所与环境下的默认账户。唯一约束保证同一作用域最多只有一条 true。';

COMMENT ON COLUMN exchange_accounts.status IS '账户状态，当前固定口径为 ACTIVE / DISABLED。';

COMMENT ON COLUMN exchange_accounts.created_at IS '交易账户记录创建时间。';

COMMENT ON COLUMN exchange_accounts.updated_at IS '交易账户记录最后更新时间。';

COMMENT ON TABLE execution_instrument_observation_items IS 'Instrument metadata observation 的可回读明细；父 observation 与本表均禁止修改或删除。';

COMMENT ON COLUMN execution_instrument_observation_items.observation_id IS '所属 INSTRUMENT_METADATA observation。';

COMMENT ON COLUMN execution_instrument_observation_items.observation_type IS '固定 INSTRUMENT_METADATA，用于 composite FK 防错绑。';

COMMENT ON COLUMN execution_instrument_observation_items.symbol IS 'canonical uppercase BASE-USDT symbol。';

COMMENT ON COLUMN execution_instrument_observation_items.trading_status IS 'LIVE/SUSPEND/PREOPEN/TEST；preflight 只接受 LIVE。';

COMMENT ON COLUMN execution_instrument_observation_items.tick_size IS '可回读 tick size，必须大于 0。';

COMMENT ON COLUMN execution_instrument_observation_items.lot_size IS '可回读 lot size，必须大于 0。';

COMMENT ON COLUMN execution_instrument_observation_items.minimum_order_size IS '可回读 minimum order size，必须大于 0。';

COMMENT ON COLUMN execution_instrument_observation_items.minimum_order_value IS '仅 VENUE_PUBLISHED 或 既有兼容证据携带的 minimum order value；VENUE_NOT_PUBLISHED 必须为空。';

COMMENT ON COLUMN execution_instrument_observation_items.minimum_order_value_currency IS '仅在 minimum order value 有正式值或 既有兼容证据时保存的币种。';

COMMENT ON COLUMN execution_instrument_observation_items.minimum_order_value_evidence_class IS 'minimum order value 证据分类：场所发布、场所未发布或仅用于无损标记 既有兼容证据。';

COMMENT ON TABLE execution_intents IS '外部执行意图事实；claim、send 和 reconcile 必须遵守事务、租约与查询恢复约束。';

COMMENT ON COLUMN execution_intents.intent_id IS '未来外部动作唯一业务键。';

COMMENT ON COLUMN execution_intents.session_id IS '所属 LiveSession。';

COMMENT ON COLUMN execution_intents.sequence IS 'session 内意图序号。';

COMMENT ON COLUMN execution_intents.action IS '外部执行动作：PLACE 或 CANCEL；均须经过授权、风控和幂等执行路径。';

COMMENT ON COLUMN execution_intents.symbol IS '目标内部 symbol。';

COMMENT ON COLUMN execution_intents.side IS 'PLACE 的 BUY/SELL；CANCEL 必须为空。';

COMMENT ON COLUMN execution_intents.order_type IS 'PLACE 首版 LIMIT；CANCEL 必须为空。';

COMMENT ON COLUMN execution_intents.quantity IS 'PLACE 数量 NUMERIC(38,8)；CANCEL 为空。';

COMMENT ON COLUMN execution_intents.limit_price IS 'PLACE 限价 NUMERIC(38,8)；CANCEL 为空。';

COMMENT ON COLUMN execution_intents.payload_hash_schema_version IS '意图 canonical schema。';

COMMENT ON COLUMN execution_intents.payload_hash IS '不可变意图 payload SHA-256。';

COMMENT ON COLUMN execution_intents.client_order_id IS '稳定 client order identity；不产生第二订单事实。';

COMMENT ON COLUMN execution_intents.local_order_id IS '既有 orders.order_id，不可后绑或改绑。';

COMMENT ON COLUMN execution_intents.state IS '执行意图的 claim、send 和 reconcile 状态；迁移须满足版本、租约和终态约束。';

COMMENT ON COLUMN execution_intents.version IS 'optimistic version。';

COMMENT ON COLUMN execution_intents.claimed_by IS '有界执行 worker 标识；必须与 claim token 和有效租约一致。';

COMMENT ON COLUMN execution_intents.claim_token IS '本次 claim 的不可复用身份；防止其他执行者跨租约写入。';

COMMENT ON COLUMN execution_intents.claimed_at IS '本次执行 claim 的时间，受租约有效期约束。';

COMMENT ON COLUMN execution_intents.lease_expires_at IS '执行 claim 的到期边界；过期不能继续发送或提交执行事实。';

COMMENT ON COLUMN execution_intents.send_started_at IS '首次网络发送前原子绑定；绑定后不可修改。';

COMMENT ON COLUMN execution_intents.created_at IS '意图创建时间。';

COMMENT ON TABLE execution_pre_place_recovery_decisions IS '仅追加的零执行事实恢复判定；租约再生不授权 PLACE 重试。';

COMMENT ON COLUMN execution_pre_place_recovery_decisions.decision_id IS '不可复用recovery判定UUID。';

COMMENT ON COLUMN execution_pre_place_recovery_decisions.predecessor_lease_id IS '保持终态且不可复活的旧lease。';

COMMENT ON COLUMN execution_pre_place_recovery_decisions.predecessor_session_id IS '必须经既有状态机终态化的旧session。';

COMMENT ON COLUMN execution_pre_place_recovery_decisions.decision IS '恢复判定允许零意图替换或发送前再生；新判定固定为 PRE_PLACE_REGENERATION_ALLOWED。';

COMMENT ON COLUMN execution_pre_place_recovery_decisions.place_intent_count IS '判定时旧session的PLACE lease-intent数量，必须为0。';

COMMENT ON COLUMN execution_pre_place_recovery_decisions.send_started_count IS '判定时进入SEND_STARTED边界的intent数量，必须为0。';

COMMENT ON COLUMN execution_pre_place_recovery_decisions.execution_intent_count IS '判定时ExecutionIntent数量，必须为0。';

COMMENT ON COLUMN execution_pre_place_recovery_decisions.execution_receipt_count IS '判定时ExecutionReceipt数量，必须为0。';

COMMENT ON COLUMN execution_pre_place_recovery_decisions.order_count IS '判定时关联Order数量，必须为0。';

COMMENT ON COLUMN execution_pre_place_recovery_decisions.trade_count IS '判定时关联Trade/Fill数量，必须为0。';

COMMENT ON COLUMN execution_pre_place_recovery_decisions.ledger_count IS '判定时关联Ledger数量，必须为0。';

COMMENT ON COLUMN execution_pre_place_recovery_decisions.decided_by IS '执行recovery判定的canonical operator user ID。';

COMMENT ON COLUMN execution_pre_place_recovery_decisions.request_id IS '脱敏request关联标识。';

COMMENT ON COLUMN execution_pre_place_recovery_decisions.trace_id IS '脱敏trace关联标识。';

COMMENT ON COLUMN execution_pre_place_recovery_decisions.decided_at IS '数据库recovery判定时间。';

COMMENT ON TABLE execution_prerequisite_observations IS '类型化且仅追加的执行前置观测；不得保存凭证或场所原始报文。';

COMMENT ON COLUMN execution_prerequisite_observations.observation_id IS '不可复用 observation UUID。';

COMMENT ON COLUMN execution_prerequisite_observations.execution_scope_id IS '所属不可变执行范围。';

COMMENT ON COLUMN execution_prerequisite_observations.observation_set_id IS '同一事务写入的四类完整 observation set identity。';

COMMENT ON COLUMN execution_prerequisite_observations.observation_type IS 'INSTRUMENT_METADATA/FEE_SCHEDULE/BALANCE_SNAPSHOT/CLOCK_SYNC。';

COMMENT ON COLUMN execution_prerequisite_observations.observation_schema_version IS '按 observation type 固定的 typed schema version。';

COMMENT ON COLUMN execution_prerequisite_observations.observation_identity IS 'source 内稳定 observation identity，用于幂等。';

COMMENT ON COLUMN execution_prerequisite_observations.source_identity IS 'scope-bound prerequisite source identity。';

COMMENT ON COLUMN execution_prerequisite_observations.source_schema_version IS 'scope-bound source schema version。';

COMMENT ON COLUMN execution_prerequisite_observations.observed_at IS 'source fact 被观察的 UTC 时间。';

COMMENT ON COLUMN execution_prerequisite_observations.recorded_at IS 'append-only fact 在数据库记录的时间。';

COMMENT ON COLUMN execution_prerequisite_observations.recorder_identity IS 'scope-bound admitted worker identity；不含 credential。';

COMMENT ON COLUMN execution_prerequisite_observations.observation_payload_hash IS 'prerequisite-observation-envelope.v1 typed payload lowercase SHA-256。';

COMMENT ON COLUMN execution_prerequisite_observations.instrument_metadata_digest IS 'instrument variant exact constraint digest，其他 variant 必须为空。';

COMMENT ON COLUMN execution_prerequisite_observations.fee_schedule_digest IS 'fee variant exact constraint digest，其他 variant 必须为空。';

COMMENT ON COLUMN execution_prerequisite_observations.balance_snapshot_digest IS 'balance variant snapshot digest；实际余额仍须可回读。';

COMMENT ON COLUMN execution_prerequisite_observations.clock_sync_observation_digest IS 'clock variant observation digest；实际 skew 仍须可回读。';

COMMENT ON COLUMN execution_prerequisite_observations.fee_tier IS 'fee variant exact tier。';

COMMENT ON COLUMN execution_prerequisite_observations.fee_evidence_class IS 'fee variant evidence class。';

COMMENT ON COLUMN execution_prerequisite_observations.maker_fee_rate IS 'fee variant maker rate，范围 [-1,1]。';

COMMENT ON COLUMN execution_prerequisite_observations.taker_fee_rate IS 'fee variant taker rate，范围 [-1,1]。';

COMMENT ON COLUMN execution_prerequisite_observations.fee_loss_treatment IS 'fee 纳入 daily loss 与 capital usage 的固定规则。';

COMMENT ON COLUMN execution_prerequisite_observations.balance_currency IS 'balance variant 币种，必须匹配 risk quote currency，首版 USDT。';

COMMENT ON COLUMN execution_prerequisite_observations.available_balance IS '可回读 available balance，NUMERIC(38,8)，不得为负。';

COMMENT ON COLUMN execution_prerequisite_observations.signed_timestamp_source IS 'clock variant 签名时间来源。';

COMMENT ON COLUMN execution_prerequisite_observations.observed_skew_ms IS 'clock variant 实测 signed skew 毫秒值。';

COMMENT ON COLUMN execution_prerequisite_observations.market_snapshot_digest IS '当前市场快照 canonical SHA-256；仅 MARKET_SNAPSHOT 使用。';

COMMENT ON COLUMN execution_prerequisite_observations.market_instrument IS '当前市场快照绑定的规范化 OKX Spot instrument。';

COMMENT ON COLUMN execution_prerequisite_observations.best_ask IS '当前市场快照中的卖一价，必须大于 0。';

COMMENT ON TABLE execution_receipts IS '脱敏且仅追加的外部执行回执；禁止保存原始报文、header、签名或凭证。';

COMMENT ON COLUMN execution_receipts.receipt_id IS '不可复用 append-only 回执 UUID。';

COMMENT ON COLUMN execution_receipts.intent_id IS '所属 execution intent。';

COMMENT ON COLUMN execution_receipts.receipt_ordinal IS '同一意图内的网络回执正整数序号；与意图身份共同保持唯一。';

COMMENT ON COLUMN execution_receipts.outcome IS '归一化、脱敏回执结果；QUERY_* 仅表示只读对账证据。';

COMMENT ON COLUMN execution_receipts.exchange_request_id IS '可空脱敏交易所 request identity。';

COMMENT ON COLUMN execution_receipts.exchange_order_id IS '可空交易所订单 identity；不承载订单生命周期。';

COMMENT ON COLUMN execution_receipts.error_category IS '可空脱敏错误类别。';

COMMENT ON COLUMN execution_receipts.error_code IS '可空脱敏错误码。';

COMMENT ON COLUMN execution_receipts.received_at IS '回执观察时间。';

COMMENT ON COLUMN execution_receipts.payload_digest IS '允许字段 normalized envelope SHA-256。';

COMMENT ON COLUMN execution_receipts.payload_digest_schema_version IS '回执 canonical schema。';

COMMENT ON TABLE execution_scope_bindings IS '会话绑定的不可变执行范围；不替代 LIVE、交易和 kill switch 的独立准入。';

COMMENT ON COLUMN execution_scope_bindings.execution_scope_id IS '不可复用的执行范围 UUID。';

COMMENT ON COLUMN execution_scope_bindings.session_id IS '唯一绑定的 LiveSession；历史 session 不做伪造回填。';

COMMENT ON COLUMN execution_scope_bindings.scope_schema_version IS '确定性编码版本，固定为 execution-scope.v1。';

COMMENT ON COLUMN execution_scope_bindings.instrument_metadata_digest IS 'exact instrument constraint set 的 lowercase SHA-256。';

COMMENT ON COLUMN execution_scope_bindings.instrument_source_identity IS '不可变 instrument source contract identity。';

COMMENT ON COLUMN execution_scope_bindings.instrument_source_schema_version IS 'instrument source contract schema version。';

COMMENT ON COLUMN execution_scope_bindings.instrument_maximum_age_ms IS 'instrument observation freshness 上限，1..300000ms。';

COMMENT ON COLUMN execution_scope_bindings.fee_schedule_digest IS 'exact fee constraint 的 lowercase SHA-256。';

COMMENT ON COLUMN execution_scope_bindings.fee_tier IS '审批绑定的 exact fee tier。';

COMMENT ON COLUMN execution_scope_bindings.fee_evidence_class IS 'OBSERVED_PRIVATE 或 ESTIMATED_PUBLIC；首单 eligibility 仅接受前者。';

COMMENT ON COLUMN execution_scope_bindings.fee_source_identity IS '不可变 fee source contract identity。';

COMMENT ON COLUMN execution_scope_bindings.fee_source_schema_version IS 'fee source contract schema version。';

COMMENT ON COLUMN execution_scope_bindings.fee_maximum_age_ms IS 'fee observation freshness 上限，1..3600000ms。';

COMMENT ON COLUMN execution_scope_bindings.balance_source_identity IS 'private balance source contract identity；不保存 credential。';

COMMENT ON COLUMN execution_scope_bindings.balance_source_schema_version IS 'balance source contract schema version。';

COMMENT ON COLUMN execution_scope_bindings.balance_maximum_age_ms IS 'balance observation freshness 上限，1..10000ms。';

COMMENT ON COLUMN execution_scope_bindings.clock_source_identity IS 'clock observation source contract identity。';

COMMENT ON COLUMN execution_scope_bindings.clock_source_schema_version IS 'clock source contract schema version。';

COMMENT ON COLUMN execution_scope_bindings.clock_maximum_age_ms IS 'clock observation freshness 上限，1..60000ms。';

COMMENT ON COLUMN execution_scope_bindings.signed_timestamp_source IS '签名时间来源，首版固定 NTP_DISCIPLINED_SYSTEM_CLOCK。';

COMMENT ON COLUMN execution_scope_bindings.maximum_tolerated_skew_ms IS 'scope-bound 最大时钟偏差，0..1000ms。';

COMMENT ON COLUMN execution_scope_bindings.endpoint_policy_version IS 'typed method/path/operation/order-type policy version。';

COMMENT ON COLUMN execution_scope_bindings.endpoint_policy_digest IS 'endpoint policy lowercase SHA-256。';

COMMENT ON COLUMN execution_scope_bindings.provider_contract_identity IS 'provider contract identity；不是 real-provider wiring。';

COMMENT ON COLUMN execution_scope_bindings.provider_artifact_digest IS 'provider immutable artifact lowercase SHA-256。';

COMMENT ON COLUMN execution_scope_bindings.worker_identity IS 'admitted worker identity；本 migration 不启动 worker。';

COMMENT ON COLUMN execution_scope_bindings.worker_release_digest IS 'worker immutable release lowercase SHA-256。';

COMMENT ON COLUMN execution_scope_bindings.execution_scope_hash IS 'execution-scope.v1 确定性 UTF-8 编码的 lowercase SHA-256。';

COMMENT ON COLUMN execution_scope_bindings.created_by IS 'materialization 创建者 users.id。';

COMMENT ON COLUMN execution_scope_bindings.created_at IS '不可变 materialization 时间；不进入 scope hash。';

COMMENT ON TABLE instrument_catalog IS 'instrument 主数据目录。统一沉淀交易所原生 symbol 与内部 symbol、精度、资产对等基础信息。';

COMMENT ON COLUMN instrument_catalog.exchange_code IS '交易所编码，例如 OKX / BINANCE。';

COMMENT ON COLUMN instrument_catalog.instrument_type IS '产品类型，当前允许值：SPOT；后续多市场扩展必须先单独扩展枚举和验证范围。';

COMMENT ON COLUMN instrument_catalog.exchange_symbol IS '交易所原生 symbol。';

COMMENT ON COLUMN instrument_catalog.internal_symbol IS '系统统一 symbol。';

COMMENT ON COLUMN instrument_catalog.base_asset IS 'base 资产。';

COMMENT ON COLUMN instrument_catalog.quote_asset IS 'quote 资产。';

COMMENT ON COLUMN instrument_catalog.status IS '交易所原生 instrument 状态代码；必须为非空大写值，当前不抽象为账户 ACTIVE / DISABLED 状态。';

COMMENT ON COLUMN instrument_catalog.tick_size IS '交易所公开 instrument 事实中的价格步长；允许空，非空时必须大于零。';

COMMENT ON COLUMN instrument_catalog.step_size IS '交易所公开 instrument 事实中的数量步长；允许空，非空时必须大于零。';

COMMENT ON COLUMN instrument_catalog.min_quantity IS '交易所公开 instrument 事实中的最小下单数量；允许空，非空时必须大于零，不表示最小名义金额。';

COMMENT ON COLUMN instrument_catalog.source IS '同步来源，例如 OKX_INSTRUMENTS_CACHE / BINANCE_FILTERS_CACHE。';

COMMENT ON COLUMN instrument_catalog.synced_at IS '最近一次同步时间。';

COMMENT ON COLUMN instrument_catalog.max_limit_quantity IS '交易所公开 instrument 事实中的单笔限价单最大数量；OKX Spot 单位为 base currency。';

COMMENT ON COLUMN instrument_catalog.max_market_size IS '交易所公开 instrument 事实中的单笔市价单最大数量；必须与 max_market_size_unit 同时存在。';

COMMENT ON COLUMN instrument_catalog.max_market_size_unit IS 'max_market_size 的单位；OKX Spot 仅允许 USDT。';

COMMENT ON COLUMN instrument_catalog.max_limit_notional_usd IS '交易所公开 instrument 事实中的单笔限价单最大 USD amount；不表示 NQ 内部风险上限。';

COMMENT ON COLUMN instrument_catalog.max_market_notional_usd IS '交易所公开 instrument 事实中的单笔市价单最大 USD amount；不表示 NQ 内部风险上限。';

COMMENT ON COLUMN instrument_catalog.source_schema_version IS 'NQ venue-rule parser/schema contract 版本；不是 OKX 官方 API 版本。';

COMMENT ON COLUMN instrument_catalog.observed_at IS '完整公开响应成功获取并解析校验后的本地观察时间；不得使用 synced_at 或请求时间替代。';

COMMENT ON COLUMN instrument_catalog.next_rule_effective_at IS '已解析的相关 upcoming change 最早生效时间；允许空，非空时必须晚于 observed_at。';

COMMENT ON COLUMN instrument_catalog.rule_checksum IS '按固定字段顺序和 decimal 规范化计算的 lowercase SHA-256；不包含写库时间、请求标识或数据库主键。';

COMMENT ON CONSTRAINT chk_instrument_catalog_max_limit_notional_positive ON instrument_catalog IS '限价单最大 USD amount 只允许空或正数。';

COMMENT ON CONSTRAINT chk_instrument_catalog_max_limit_quantity_positive ON instrument_catalog IS '限价单最大数量只允许空或正数。';

COMMENT ON CONSTRAINT chk_instrument_catalog_max_market_notional_positive ON instrument_catalog IS '市价单最大 USD amount 只允许空或正数。';

COMMENT ON CONSTRAINT chk_instrument_catalog_max_market_size_unit ON instrument_catalog IS '市价单最大数量与单位必须同时为空，或为正数且单位固定为 USDT。';

COMMENT ON CONSTRAINT chk_instrument_catalog_min_quantity_positive ON instrument_catalog IS '最小下单数量只允许空或正数。';

COMMENT ON CONSTRAINT chk_instrument_catalog_next_rule_after_observed ON instrument_catalog IS '下一规则生效时间必须晚于事实观察时间。';

COMMENT ON CONSTRAINT chk_instrument_catalog_observed_before_synced ON instrument_catalog IS '事实观察时间不得晚于数据库同步写入时间。';

COMMENT ON CONSTRAINT chk_instrument_catalog_rule_checksum ON instrument_catalog IS 'checksum 只允许空或 64 位 lowercase hexadecimal SHA-256。';

COMMENT ON CONSTRAINT chk_instrument_catalog_source_schema_version ON instrument_catalog IS 'NQ source schema version 只允许空或非空白值。';

COMMENT ON CONSTRAINT chk_instrument_catalog_step_size_positive ON instrument_catalog IS '数量步长只允许空或正数，禁止以零伪造可用事实。';

COMMENT ON CONSTRAINT chk_instrument_catalog_tick_size_positive ON instrument_catalog IS '价格步长只允许空或正数，禁止以零伪造可用事实。';

COMMENT ON TABLE kill_switch_events IS 'Kill switch 状态变化的 append-only 审计事实；应用只追加，不更新或删除。';

COMMENT ON COLUMN kill_switch_events.id IS '审计事件 UUID 主键。';

COMMENT ON COLUMN kill_switch_events.scope IS '事件所属 kill switch 安全作用域。';

COMMENT ON COLUMN kill_switch_events.from_status IS '变化前状态；初始 seed 事件为空。';

COMMENT ON COLUMN kill_switch_events.to_status IS '变化后状态：ENGAGED 或 DISENGAGED；本任务生产代码只写 ENGAGED。';

COMMENT ON COLUMN kill_switch_events.state_version IS '事件对应的 current-state optimistic-lock 版本。';

COMMENT ON COLUMN kill_switch_events.reason_code IS '状态变化的脱敏原因码。';

COMMENT ON COLUMN kill_switch_events.source IS '状态变化事实来源。';

COMMENT ON COLUMN kill_switch_events.actor_id IS '触发状态变化的操作者或系统标识。';

COMMENT ON COLUMN kill_switch_events.trace_id IS '状态变化的脱敏追踪标识。';

COMMENT ON COLUMN kill_switch_events.occurred_at IS '状态变化发生时间，UTC。';

COMMENT ON TABLE kill_switch_states IS '全局交易 kill switch 当前安全状态；缺记录、读取失败或非法值必须在应用层按 UNKNOWN 阻断。';

COMMENT ON COLUMN kill_switch_states.scope IS '稳定安全作用域；当前只允许 GLOBAL_TRADING。';

COMMENT ON COLUMN kill_switch_states.status IS '当前状态：ENGAGED 表示阻断；DISENGAGED 仅表示可继续下一只读检查，不表示交易授权。';

COMMENT ON COLUMN kill_switch_states.version IS 'optimistic-lock 版本；每次真实状态变化递增。';

COMMENT ON COLUMN kill_switch_states.reason_code IS '最近状态变化的脱敏原因码，不保存 credential 或 provider payload。';

COMMENT ON COLUMN kill_switch_states.source IS '状态事实来源，例如 FLYWAY_MIGRATION 或 OPERATOR_ENGAGE。';

COMMENT ON COLUMN kill_switch_states.updated_at IS '当前状态的权威更新时间，UTC；不得以请求时间覆盖。';

COMMENT ON COLUMN kill_switch_states.updated_by IS '最近状态变化操作者标识；不得保存敏感身份材料。';

COMMENT ON COLUMN kill_switch_states.trace_id IS '最近状态变化的脱敏追踪标识。';

COMMENT ON CONSTRAINT chk_kill_switch_states_scope ON kill_switch_states IS '当前 schema 只允许全局交易安全作用域。';

COMMENT ON CONSTRAINT chk_kill_switch_states_status ON kill_switch_states IS '持久化状态只允许 ENGAGED 或 DISENGAGED；UNKNOWN 仅是读取失败的应用态。';

COMMENT ON CONSTRAINT chk_kill_switch_states_version ON kill_switch_states IS 'optimistic-lock 版本必须为正数。';

COMMENT ON TABLE ledger_entries IS '账本分录表。保存账户余额变更事实，idempotency_key 参与记账幂等。';

COMMENT ON COLUMN ledger_entries.entry_id IS '内部账本分录主键。';

COMMENT ON COLUMN ledger_entries.account_id IS '账户维度外键。';

COMMENT ON COLUMN ledger_entries.currency IS '账本币种。';

COMMENT ON COLUMN ledger_entries.delta IS '本次账本变动值。';

COMMENT ON COLUMN ledger_entries.balance_after IS '变动后余额。';

COMMENT ON COLUMN ledger_entries.direction IS '账本方向，固定为 DEBIT / CREDIT。';

COMMENT ON COLUMN ledger_entries.ref_type IS '账本关联对象类型，例如 TRADE / ORDER。';

COMMENT ON COLUMN ledger_entries.ref_id IS '账本关联对象内部标识。';

COMMENT ON COLUMN ledger_entries.idempotency_key IS '账本幂等键。用于避免重复记账。';

COMMENT ON COLUMN ledger_entries.trace_id IS '链路追踪 ID。';

COMMENT ON COLUMN ledger_entries.ts IS '账本事实时间。';

COMMENT ON COLUMN ledger_entries.created_at IS '账本分录创建时间。';

COMMENT ON TABLE ledger_events IS '账本事件表。保存账本分录衍生出的事件证据，用于审计与重放辅助。';

COMMENT ON COLUMN ledger_events.ledger_event_id IS '内部账本事件主键。';

COMMENT ON COLUMN ledger_events.entry_id IS '所属账本分录主键。';

COMMENT ON COLUMN ledger_events.event_type IS '账本事件类型。';

COMMENT ON COLUMN ledger_events.payload_json IS '账本事件载荷 JSON；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN ledger_events.trace_id IS '链路追踪 ID。';

COMMENT ON COLUMN ledger_events.created_at IS '账本事件创建时间。';

COMMENT ON TABLE live_session_events IS 'LiveSession 有序 append-only 事件；不是通用审计、订单或交易所回执主事实。';

COMMENT ON COLUMN live_session_events.event_id IS 'append-only event UUID。';

COMMENT ON COLUMN live_session_events.session_id IS '所属 LiveSession。';

COMMENT ON COLUMN live_session_events.sequence_no IS 'session 内严格递增序号。';

COMMENT ON COLUMN live_session_events.from_state IS '命令前状态；CREATED 可为空。';

COMMENT ON COLUMN live_session_events.to_state IS '命令后状态。';

COMMENT ON COLUMN live_session_events.command IS 'control-plane 命令。';

COMMENT ON COLUMN live_session_events.actor_id IS '操作者；bounded system actor 可为空。';

COMMENT ON COLUMN live_session_events.request_id IS '脱敏请求追踪标识。';

COMMENT ON COLUMN live_session_events.trace_id IS '脱敏链路追踪标识。';

COMMENT ON COLUMN live_session_events.reason_code IS '稳定、脱敏原因码。';

COMMENT ON COLUMN live_session_events.idempotency_key IS '持久幂等键；不得进入日志或响应。';

COMMENT ON COLUMN live_session_events.command_payload_hash IS '命令 canonical payload SHA-256。';

COMMENT ON COLUMN live_session_events.command_payload_schema_version IS '命令 canonical schema，首版 live-session-command.v1。';

COMMENT ON COLUMN live_session_events.metadata IS '上限 8KiB 的脱敏对象；禁止凭证、raw payload、header、签名。';

COMMENT ON COLUMN live_session_events.created_at IS '事件提交时间。';

COMMENT ON TABLE live_sessions IS 'LIVE 控制会话聚合；记录控制事实，不能替代有效交易授权、风控或执行准入。';

COMMENT ON COLUMN live_sessions.session_id IS '会话 UUID，一次 lifecycle 永不复用。';

COMMENT ON COLUMN live_sessions.owner_id IS '会话 owner，必须与交易账户 owner 一致。';

COMMENT ON COLUMN live_sessions.exchange_account_id IS '候选 LIVE 交易账户引用；存在不表示可交易。';

COMMENT ON COLUMN live_sessions.venue IS '场所范围，首版固定 OKX_SPOT。';

COMMENT ON COLUMN live_sessions.strategy_release_id IS '已验证 Strategy Release admission anchor。';

COMMENT ON COLUMN live_sessions.release_digest IS '绑定时 release artifact SHA-256。';

COMMENT ON COLUMN live_sessions.release_admission_revision IS '绑定时 admission 单调 revision。';

COMMENT ON COLUMN live_sessions.risk_limit_set_id IS '不可变风险规则集引用。';

COMMENT ON COLUMN live_sessions.risk_limit_set_digest IS '绑定时风险规则 canonical digest。';

COMMENT ON COLUMN live_sessions.credential_reference IS '精确 credential record 引用；绝不保存凭证 material。';

COMMENT ON COLUMN live_sessions.symbol_allowlist IS '会话大写、排序、去重 symbol scope。';

COMMENT ON COLUMN live_sessions.capital_cap IS '本会话资本上限，不得超过 risk set。';

COMMENT ON COLUMN live_sessions.execution_window_start IS 'UTC 执行窗口闭区间起点。';

COMMENT ON COLUMN live_sessions.execution_window_end IS 'UTC 执行窗口开区间终点。';

COMMENT ON COLUMN live_sessions.state IS 'LiveSession control-plane 状态。';

COMMENT ON COLUMN live_sessions.version IS '业务状态与 scope 的 optimistic version，从 1 开始。';

COMMENT ON COLUMN live_sessions.approval_scope_hash IS '当前完整审批 scope 的 canonical SHA-256。';

COMMENT ON COLUMN live_sessions.approval_scope_schema_version IS '审批 scope canonical 版本，首版 approval-scope.v1。';

COMMENT ON COLUMN live_sessions.next_event_sequence IS '下一个事件序号；锁 session row 后原子分配，禁止 MAX+1。';

COMMENT ON COLUMN live_sessions.created_by IS '创建操作者，用于 creator 与 approver 职责分离。';

COMMENT ON COLUMN live_sessions.created_at IS '会话创建时间。';

COMMENT ON COLUMN live_sessions.updated_at IS '会话最后合法变更时间。';

COMMENT ON COLUMN live_sessions.authority_type IS '互斥授权类型：STRATEGY 或 OPERATOR_CONTROLLED_EXECUTION。';

COMMENT ON COLUMN live_sessions.operator_execution_authority_id IS '人工受控执行会话的显式授权引用；策略会话必须为空。';

COMMENT ON COLUMN live_sessions.operator_execution_authority_digest IS '会话创建时冻结的operator authority canonical digest。';

COMMENT ON TABLE marketdata_bars IS '历史行情 K 线表。保存交易所、交易对、周期和时间窗口维度下的 OHLCV 数据，作为 historical marketdata 查询主来源。';

COMMENT ON COLUMN marketdata_bars.marketdata_bar_id IS '历史 K 线主键。';

COMMENT ON COLUMN marketdata_bars.exchange_code IS '交易所编码，使用 canonical 口径。';

COMMENT ON COLUMN marketdata_bars.symbol IS '交易对标识，例如 BTC-USDT。';

COMMENT ON COLUMN marketdata_bars."interval" IS 'K 线周期，例如 1m / 5m / 1h。';

COMMENT ON COLUMN marketdata_bars.open_time IS 'K 线开始时间。与 exchange_code + symbol + interval 共同构成唯一时间点。';

COMMENT ON COLUMN marketdata_bars.close_time IS 'K 线结束时间。';

COMMENT ON COLUMN marketdata_bars.open_price IS '开盘价。';

COMMENT ON COLUMN marketdata_bars.high_price IS '最高价。';

COMMENT ON COLUMN marketdata_bars.low_price IS '最低价。';

COMMENT ON COLUMN marketdata_bars.close_price IS '收盘价。';

COMMENT ON COLUMN marketdata_bars.volume IS '该时间窗口内的成交量。';

COMMENT ON COLUMN marketdata_bars.source IS '数据来源标识，例如 IMPORT / BACKFILL / FIXTURE_SYNC。';

COMMENT ON COLUMN marketdata_bars.ingested_at IS '该条历史 K 线被导入正式库的时间。';

COMMENT ON COLUMN marketdata_bars.market_type IS '市场类型，当前业务范围为 SPOT；纳入唯一键以固定不同市场类型的数据隔离语义。';

COMMENT ON COLUMN marketdata_bars.quote_volume IS 'K 线成交额，按交易所返回的 quote asset 数量保存，NUMERIC(38,8) 保留交易所原始精度边界';

COMMENT ON COLUMN marketdata_bars.trade_count IS 'K 线成交笔数，来自交易所原始 K 线响应；交易所不返回时允许为空';

COMMENT ON COLUMN marketdata_bars.quality_status IS 'K 线质量状态，允许值：OK、GAP_DETECTED、DUPLICATE_SKIPPED、INVALID_PRICE、INCOMPLETE';

COMMENT ON COLUMN marketdata_bars.raw_payload_json IS '交易所原始 K 线 payload 快照 JSONB，仅保存当前 bar 的原始数组/对象用于审计和排障，不作为业务查询主结构；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN marketdata_bars.available_at IS '来源事实可证明的 bar 可见时间；旧行 NULL 时读取侧仅以入库时间保守解释';

COMMENT ON TABLE marketdata_dataset_coverage IS '行情数据集覆盖与质量统计表，记录每次 refresh-quality 的覆盖率和缺口摘要。';

COMMENT ON COLUMN marketdata_dataset_coverage.coverage_id IS '覆盖统计 ID，业务主键';

COMMENT ON COLUMN marketdata_dataset_coverage.dataset_id IS '关联数据集 ID，对应 marketdata_datasets.dataset_id';

COMMENT ON COLUMN marketdata_dataset_coverage.range_start_time IS '本次覆盖统计起始时间，按 K 线 open_time 下界解释';

COMMENT ON COLUMN marketdata_dataset_coverage.range_end_time IS '本次覆盖统计结束时间，按 K 线 close_time 上界解释';

COMMENT ON COLUMN marketdata_dataset_coverage.expected_bars IS '按 interval 和时间范围计算的理论 K 线数量，单位为条';

COMMENT ON COLUMN marketdata_dataset_coverage.actual_bars IS 'marketdata_bars 中实际命中的 K 线数量，单位为条';

COMMENT ON COLUMN marketdata_dataset_coverage.missing_bars IS 'expected_bars 与 actual_bars 差值归一后的缺失 K 线数量，单位为条';

COMMENT ON COLUMN marketdata_dataset_coverage.duplicate_bars IS '重复 K 线数量；在 marketdata_bars 唯一约束生效时正常应为 0。';

COMMENT ON COLUMN marketdata_dataset_coverage.invalid_bars IS '价格、数量或 quality_status 非 OK 的异常 K 线数量，单位为条';

COMMENT ON COLUMN marketdata_dataset_coverage.quality_status IS '本次覆盖统计质量状态，允许值：OK、GAP_DETECTED、INCOMPLETE、INVALID';

COMMENT ON COLUMN marketdata_dataset_coverage.summary_json IS '覆盖统计摘要 JSONB，用于记录 expected/actual/missing/invalid/duplicate 和数据来源，不作为主查询字段；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN marketdata_dataset_coverage.created_at IS '覆盖统计创建时间，即 refresh-quality 执行完成时间';

COMMENT ON TABLE marketdata_datasets IS '行情数据集定义表，描述可绑定回测配置的历史 K 线数据范围、质量状态和来源。';

COMMENT ON COLUMN marketdata_datasets.dataset_id IS '数据集 ID，业务主键，供 backtest_configs.dataset_id 绑定';

COMMENT ON COLUMN marketdata_datasets.dataset_name IS '数据集名称，同一范围内应具备人工可识别含义';

COMMENT ON COLUMN marketdata_datasets.exchange_code IS '交易所代码，当前业务范围为 OKX、BINANCE。';

COMMENT ON COLUMN marketdata_datasets.market_type IS '市场类型，当前业务范围为 SPOT。';

COMMENT ON COLUMN marketdata_datasets.symbol IS '系统内部交易对代码，当前业务范围为 BTC-USDT、ETH-USDT、SOL-USDT。';

COMMENT ON COLUMN marketdata_datasets."interval" IS 'K 线周期，当前业务范围为 1m、5m、15m、1h、4h、1d。';

COMMENT ON COLUMN marketdata_datasets.start_time IS '数据集覆盖范围起始时间，按 K 线 open_time 下界解释';

COMMENT ON COLUMN marketdata_datasets.end_time IS '数据集覆盖范围结束时间，按 K 线 close_time 上界解释';

COMMENT ON COLUMN marketdata_datasets.status IS '数据集状态，允许值：CREATED、READY、INVALID、ARCHIVED';

COMMENT ON COLUMN marketdata_datasets.quality_status IS '数据集质量状态，允许值：OK、GAP_DETECTED、INCOMPLETE、INVALID';

COMMENT ON COLUMN marketdata_datasets.bar_count IS '当前质量统计得到的 K 线数量，单位为条';

COMMENT ON COLUMN marketdata_datasets.gap_count IS '当前质量统计得到的缺失 K 线数量，单位为条';

COMMENT ON COLUMN marketdata_datasets.source IS '数据集来源，当前从 marketdata_bars 派生。';

COMMENT ON COLUMN marketdata_datasets.created_by IS '创建数据集的用户或本地执行主体，用于审计';

COMMENT ON COLUMN marketdata_datasets.created_at IS '数据集创建时间';

COMMENT ON COLUMN marketdata_datasets.updated_at IS '数据集最近一次质量刷新或绑定相关更新时间';

COMMENT ON COLUMN marketdata_datasets.request_json IS '数据集创建请求快照 JSONB，仅保存范围、symbol、interval 等审计字段，不保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON TABLE marketdata_ingestion_jobs IS '行情历史数据接入任务表，记录按交易所、市场类型、交易对、周期和时间范围创建的历史 K 线拉取任务。';

COMMENT ON COLUMN marketdata_ingestion_jobs.job_id IS '接入任务 ID，业务主键，使用 UUID 保证 API 与运行记录之间稳定关联';

COMMENT ON COLUMN marketdata_ingestion_jobs.exchange_code IS '交易所代码，当前业务范围为 OKX、BINANCE。';

COMMENT ON COLUMN marketdata_ingestion_jobs.market_type IS '市场类型，当前业务范围为 SPOT。';

COMMENT ON COLUMN marketdata_ingestion_jobs.symbol IS '系统内部交易对代码，当前业务范围为 BTC-USDT、ETH-USDT、SOL-USDT。';

COMMENT ON COLUMN marketdata_ingestion_jobs."interval" IS 'K 线周期，当前业务范围为 1m、5m、15m、1h、4h、1d。';

COMMENT ON COLUMN marketdata_ingestion_jobs.start_time IS '任务计划拉取范围开始时间，闭区间起点，对应 K 线 open_time 下界';

COMMENT ON COLUMN marketdata_ingestion_jobs.end_time IS '任务计划拉取范围结束时间，闭区间终点，对应 K 线 close_time 上界';

COMMENT ON COLUMN marketdata_ingestion_jobs.status IS '任务状态，允许值：CREATED、RUNNING、SUCCEEDED、FAILED、PARTIAL';

COMMENT ON COLUMN marketdata_ingestion_jobs.source IS '任务来源，EXCHANGE_HISTORICAL 表示交易所历史 K 线接入。';

COMMENT ON COLUMN marketdata_ingestion_jobs.created_by IS '创建任务的用户标识，用于审计；允许最长 512 字符以兼容 Spring Security principal name，本字段不保存 token、密钥或 cookie';

COMMENT ON COLUMN marketdata_ingestion_jobs.created_at IS '任务创建时间，由数据库默认当前时间写入';

COMMENT ON COLUMN marketdata_ingestion_jobs.updated_at IS '任务最近更新时间，每次运行状态变化时同步更新';

COMMENT ON COLUMN marketdata_ingestion_jobs.request_json IS '任务创建时的原始请求快照 JSONB，用于审计和复盘；仅保存 API 请求字段，不保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON TABLE marketdata_ingestion_runs IS '行情历史数据接入运行记录表，记录每次 run-once 的执行结果和统计信息。';

COMMENT ON COLUMN marketdata_ingestion_runs.run_id IS '接入运行 ID，业务主键，标识一次 run-once 执行';

COMMENT ON COLUMN marketdata_ingestion_runs.job_id IS '关联的行情接入任务 ID，对应 marketdata_ingestion_jobs.job_id';

COMMENT ON COLUMN marketdata_ingestion_runs.status IS '运行状态，允许值：RUNNING、SUCCEEDED、FAILED、PARTIAL';

COMMENT ON COLUMN marketdata_ingestion_runs.started_at IS '本次运行开始时间，由应用在调用交易所前写入';

COMMENT ON COLUMN marketdata_ingestion_runs.finished_at IS '本次运行完成时间，成功、部分成功或失败结束时写入';

COMMENT ON COLUMN marketdata_ingestion_runs.requested_start_time IS '本次运行实际请求的开始时间；断点续拉时可能晚于任务 start_time';

COMMENT ON COLUMN marketdata_ingestion_runs.requested_end_time IS '本次运行实际请求的结束时间，不超过任务 end_time。';

COMMENT ON COLUMN marketdata_ingestion_runs.actual_start_time IS '本次从交易所返回并通过校验的最早 K 线 open_time；无有效数据时为空';

COMMENT ON COLUMN marketdata_ingestion_runs.actual_end_time IS '本次从交易所返回并通过校验的最晚 K 线 close_time；无有效数据时为空';

COMMENT ON COLUMN marketdata_ingestion_runs.fetched_bars IS '本次从交易所接口获取的 K 线数量';

COMMENT ON COLUMN marketdata_ingestion_runs.inserted_bars IS '本次新增写入 marketdata_bars 的 K 线数量';

COMMENT ON COLUMN marketdata_ingestion_runs.updated_bars IS '本次幂等更新 marketdata_bars 的 K 线数量';

COMMENT ON COLUMN marketdata_ingestion_runs.skipped_bars IS '本次因重复、非法或超出范围而跳过的 K 线数量';

COMMENT ON COLUMN marketdata_ingestion_runs.error_message IS '本次运行失败或部分失败的可读错误摘要，不保存密钥、token 或完整敏感响应';

COMMENT ON COLUMN marketdata_ingestion_runs.raw_summary_json IS '本次运行的原始统计摘要 JSONB，用于排障、审计和复盘；保存计数、断点和非敏感错误码，不保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN marketdata_ingestion_runs.created_at IS '运行记录创建时间，由数据库默认当前时间写入';

COMMENT ON TABLE operator_approvals IS '不可变人工审批事实；审批不等于交易所权限、kill switch 释放或 LIVE 授权。';

COMMENT ON COLUMN operator_approvals.approval_id IS '不可复用审批 UUID。';

COMMENT ON COLUMN operator_approvals.session_id IS '被审批 LiveSession。';

COMMENT ON COLUMN operator_approvals.scope_hash IS '审批时精确 scope hash。';

COMMENT ON COLUMN operator_approvals.release_digest IS '审批时 release digest。';

COMMENT ON COLUMN operator_approvals.risk_limit_set_digest IS '审批时 risk set digest。';

COMMENT ON COLUMN operator_approvals.approver_id IS '审批者 users.id，必须与 creator 不同。';

COMMENT ON COLUMN operator_approvals.approver_role IS '审批时角色快照；实时 RBAC 仍由应用校验。';

COMMENT ON COLUMN operator_approvals.decision IS 'APPROVED 或 REJECTED。';

COMMENT ON COLUMN operator_approvals.reason IS '非空脱敏理由，禁止 credential/private payload。';

COMMENT ON COLUMN operator_approvals.approved_at IS 'decision 发生时间。';

COMMENT ON COLUMN operator_approvals.expires_at IS '审批过期时间，不得晚于 session window end。';

COMMENT ON COLUMN operator_approvals.scope_schema_version IS '审批摘要对应的编码版本；基础审批与精确执行范围审批互斥。';

COMMENT ON COLUMN operator_approvals.execution_scope_id IS '精确执行范围审批的外键；基础审批必须为空，不能授权精确执行范围。';

COMMENT ON TABLE operator_execution_authorities IS '人工受控执行的显式授权；名义金额和动作次数有硬上限，不保存凭证且不替代风控。';

COMMENT ON COLUMN operator_execution_authorities.authority_id IS '不可复用的人工执行授权 UUID。';

COMMENT ON COLUMN operator_execution_authorities.owner_user_id IS '明确授权operator的users.id。';

COMMENT ON COLUMN operator_execution_authorities.exchange_account_id IS '精确OKX LIVE account引用。';

COMMENT ON COLUMN operator_execution_authorities.credential_reference_id IS '精确credential record引用；不保存material。';

COMMENT ON COLUMN operator_execution_authorities.instrument IS '授权的唯一规范化Spot instrument。';

COMMENT ON COLUMN operator_execution_authorities.side IS '允许的订单方向；本批runtime materialization固定BUY。';

COMMENT ON COLUMN operator_execution_authorities.order_type IS '仅允许LIMIT。';

COMMENT ON COLUMN operator_execution_authorities.max_notional IS '人工受控执行名义金额硬上限，不得超过 10 USDT。';

COMMENT ON COLUMN operator_execution_authorities.max_place_count IS '允许PLACE次数，本合同固定1。';

COMMENT ON COLUMN operator_execution_authorities.max_cancel_count IS '允许CANCEL次数，本合同固定1。';

COMMENT ON COLUMN operator_execution_authorities.transfer_allowed IS '资金划转权限，必须为false。';

COMMENT ON COLUMN operator_execution_authorities.withdraw_allowed IS '提现权限，必须为false。';

COMMENT ON COLUMN operator_execution_authorities.valid_from IS 'UTC authority生效时间。';

COMMENT ON COLUMN operator_execution_authorities.expires_at IS 'UTC authority硬过期时间。';

COMMENT ON COLUMN operator_execution_authorities.status IS 'ACTIVE/CLOSED/EXPIRED lifecycle。';

COMMENT ON COLUMN operator_execution_authorities.created_by IS '显式创建authority的operator users.id。';

COMMENT ON COLUMN operator_execution_authorities.created_at IS '数据库物化时间并参与canonical digest。';

COMMENT ON COLUMN operator_execution_authorities.canonical_digest IS 'operator-execution-authority.v1 确定性编码的 lowercase SHA-256。';

COMMENT ON COLUMN operator_execution_authorities.version IS 'lifecycle optimistic version。';

COMMENT ON COLUMN operator_execution_authorities.closed_at IS 'CLOSED或EXPIRED终态时间。';

COMMENT ON COLUMN operator_execution_authorities.updated_at IS '最后合法lifecycle更新时间。';

COMMENT ON TABLE orders IS '订单事实表。order_id 是内部主键，strategy_run_id 负责记录订单所属运行血缘。';

COMMENT ON COLUMN orders.order_id IS '内部订单主键，系统侧唯一身份。';

COMMENT ON COLUMN orders.account_id IS '订单所属账户 ID。';

COMMENT ON COLUMN orders.strategy_run_id IS '所属策略运行 ID，运行级血缘外键，可空。';

COMMENT ON COLUMN orders.symbol IS '内部交易对标识。';

COMMENT ON COLUMN orders.client_order_id IS '客户端订单号 / 幂等业务号。';

COMMENT ON COLUMN orders.side IS '订单方向。';

COMMENT ON COLUMN orders.type IS '订单类型。';

COMMENT ON COLUMN orders.price IS '订单价格。';

COMMENT ON COLUMN orders.qty IS '订单数量。';

COMMENT ON COLUMN orders.status IS '订单状态，遵循订单生命周期状态机。';

COMMENT ON COLUMN orders.reason IS '状态推进原因或拒绝原因。';

COMMENT ON COLUMN orders.trace_id IS '链路追踪 ID。';

COMMENT ON COLUMN orders.created_at IS '订单创建时间。';

COMMENT ON COLUMN orders.updated_at IS '订单最后更新时间。';

COMMENT ON COLUMN orders.venue IS '历史兼容列。当前仍被代码使用，语义等同 exchange_code，后续逐步迁移。';

COMMENT ON COLUMN orders.external_order_id IS '历史兼容列。语义等同 exchange_order_id，后续逐步迁移。';

COMMENT ON COLUMN orders.request_id IS '执行请求级身份。一个策略运行可对应多个 request_id。';

COMMENT ON COLUMN orders.dedup_key IS '订单去重键。当前默认使用 account_id:client_order_id。';

COMMENT ON COLUMN orders.exchange_code IS '统一交易所标识。';

COMMENT ON COLUMN orders.trade_env IS '交易环境，固定枚举为 SIM / LIVE；当前兼容口径默认 SIM。';

COMMENT ON COLUMN orders.exchange_order_id IS '交易所订单号，外部订单身份。';

COMMENT ON COLUMN orders.version IS '订单状态的持久化迁移代际；每次成功状态迁移递增一次，用于拒绝旧快照及ABA回执';

COMMENT ON TABLE ordinary_order_cancel_finality IS '已核对最终取消查询及完整累计成交量的 Order 事实；取消请求接受不是此证明';

COMMENT ON COLUMN ordinary_order_cancel_finality.observed_order_version IS '查询开始前的 Order 版本，迟到响应不得重贴新版本';

COMMENT ON COLUMN ordinary_order_cancel_finality.executed_quantity IS '最终取消累计成交量，提交时必须与唯一 durable fills 相等';

COMMENT ON TABLE ordinary_place_authorities IS '与既有订单绑定的 ordinary PLACE 一次性决定，不是交易事实或可接管 lease';

COMMENT ON COLUMN ordinary_place_authorities.order_id IS '原订单主键，一单一决定，不产生新 logical identity';

COMMENT ON COLUMN ordinary_place_authorities.state IS '未获得发送资格、可能已发出或发送前已撤销；两个决定均不可逆';

COMMENT ON COLUMN ordinary_place_authorities.decided_at IS '数据库决定时间；历史 MAY 为分类时间，不是发送时间或 expiry';

COMMENT ON TABLE paper_risk_check_results IS 'Paper Trading 风控检查结果表：记录 Paper run 的风控检查事实，当前只做基础健康检查，不接复杂风控策略平台。';

COMMENT ON COLUMN paper_risk_check_results.risk_result_id IS '风控结果主键，业务可读 ID，例如 rrc-<uuid>';

COMMENT ON COLUMN paper_risk_check_results.paper_run_id IS '所属 Paper run ID，外键 paper_trading_runs.paper_run_id';

COMMENT ON COLUMN paper_risk_check_results.check_type IS '风控检查类型，当前为 BASIC_HEALTH_CHECK；后续可扩展 POSITION_LIMIT、DRAWDOWN_LIMIT 等。';

COMMENT ON COLUMN paper_risk_check_results.status IS '风控检查结果状态：PASSED 通过；REJECTED 拒绝；WARNING 警告';

COMMENT ON COLUMN paper_risk_check_results.severity IS '风控检查严重程度：LOW 低；MEDIUM 中；HIGH 高；CRITICAL 严重';

COMMENT ON COLUMN paper_risk_check_results.message IS '风控检查结果摘要消息，可空';

COMMENT ON COLUMN paper_risk_check_results.input_snapshot_json IS '风控检查输入快照 JSONB，保存检查时的 run/position/equity 摘要；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN paper_risk_check_results.result_snapshot_json IS '风控检查输出快照 JSONB，保存检查结果详情；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN paper_risk_check_results.created_at IS '风控检查执行时间，UTC';

COMMENT ON TABLE paper_run_alerts IS 'Paper run 告警事件';

COMMENT ON COLUMN paper_run_alerts.alert_id IS '告警 ID，格式 alt-<uuid>';

COMMENT ON COLUMN paper_run_alerts.paper_run_id IS '关联 Paper run ID';

COMMENT ON COLUMN paper_run_alerts.alert_type IS '告警类型：HEARTBEAT_LAG / SCHEDULE_FIRE_FAILED / RISK_WARNING / EMERGENCY_STOP / SYSTEM_NOTICE';

COMMENT ON COLUMN paper_run_alerts.severity IS '严重程度：LOW / MEDIUM / HIGH / CRITICAL';

COMMENT ON COLUMN paper_run_alerts.status IS '告警状态：OPEN / ACKED / RESOLVED';

COMMENT ON COLUMN paper_run_alerts.title IS '告警标题';

COMMENT ON COLUMN paper_run_alerts.message IS '告警详情';

COMMENT ON COLUMN paper_run_alerts.source IS '告警来源：SCHEDULE / HEARTBEAT / RISK / MONITOR / MANUAL';

COMMENT ON COLUMN paper_run_alerts.event_snapshot_json IS '事件快照（触发时上下文），不保存密钥/token/cookie';

COMMENT ON COLUMN paper_run_alerts.acknowledged_by IS '确认人';

COMMENT ON COLUMN paper_run_alerts.acknowledged_at IS '确认时间';

COMMENT ON COLUMN paper_run_alerts.resolved_at IS '解决时间';

COMMENT ON COLUMN paper_run_alerts.created_at IS '创建时间';

COMMENT ON COLUMN paper_run_alerts.updated_at IS '更新时间';

COMMENT ON TABLE paper_run_daily_reports IS 'Paper run 日报，每日运行摘要';

COMMENT ON COLUMN paper_run_daily_reports.report_id IS '日报 ID，格式 rpt-<uuid>';

COMMENT ON COLUMN paper_run_daily_reports.paper_run_id IS '关联 Paper run ID';

COMMENT ON COLUMN paper_run_daily_reports.report_date IS '报告日期';

COMMENT ON COLUMN paper_run_daily_reports.status IS '日报状态：GENERATED / PARTIAL / FAILED';

COMMENT ON COLUMN paper_run_daily_reports.total_equity IS '当日总权益（截止日末），口径为 equity_curve_snapshots 最新快照的 total_equity';

COMMENT ON COLUMN paper_run_daily_reports.daily_pnl IS '当日盈亏（当日末权益 - 前日末权益），无前日数据时为 null';

COMMENT ON COLUMN paper_run_daily_reports.daily_return IS '当日收益率（daily_pnl / 前日末权益），无前日数据时为 null';

COMMENT ON COLUMN paper_run_daily_reports.max_drawdown IS '当日最大回撤（当日 equity_curve 最大回撤值），无数据时为 null';

COMMENT ON COLUMN paper_run_daily_reports.order_count IS '当日订单数';

COMMENT ON COLUMN paper_run_daily_reports.trade_count IS '当日成交数';

COMMENT ON COLUMN paper_run_daily_reports.alert_count IS '当日告警数';

COMMENT ON COLUMN paper_run_daily_reports.risk_reject_count IS '当日风控拒绝数';

COMMENT ON COLUMN paper_run_daily_reports.report_json IS '日报详细数据（各交易对盈亏明细等），不保存密钥/token/cookie';

COMMENT ON COLUMN paper_run_daily_reports.generated_at IS '日报生成时间';

COMMENT ON COLUMN paper_run_daily_reports.created_at IS '创建时间';

COMMENT ON TABLE paper_run_heartbeats IS 'Paper run 心跳记录，定期记录运行健康状态';

COMMENT ON COLUMN paper_run_heartbeats.heartbeat_id IS '心跳 ID，格式 hbt-<uuid>';

COMMENT ON COLUMN paper_run_heartbeats.paper_run_id IS '关联 Paper run ID';

COMMENT ON COLUMN paper_run_heartbeats.heartbeat_time IS '心跳时间';

COMMENT ON COLUMN paper_run_heartbeats.status IS '心跳状态：OK / LAGGING / STOPPED / UNKNOWN';

COMMENT ON COLUMN paper_run_heartbeats.last_event_time IS '最近事件时间';

COMMENT ON COLUMN paper_run_heartbeats.last_order_time IS '最近订单时间';

COMMENT ON COLUMN paper_run_heartbeats.last_trade_time IS '最近成交时间';

COMMENT ON COLUMN paper_run_heartbeats.lag_seconds IS '延迟秒数';

COMMENT ON COLUMN paper_run_heartbeats.summary_json IS '心跳摘要（当前持仓数、未完成订单数等），不保存密钥/token/cookie';

COMMENT ON COLUMN paper_run_heartbeats.created_at IS '创建时间';

COMMENT ON TABLE paper_run_recovery_events IS 'Paper run 恢复与重试事件记录';

COMMENT ON COLUMN paper_run_recovery_events.recovery_event_id IS '恢复事件 ID，格式 rec-<uuid>';

COMMENT ON COLUMN paper_run_recovery_events.paper_run_id IS '关联 Paper run ID';

COMMENT ON COLUMN paper_run_recovery_events.recovery_type IS '恢复类型：MANUAL_RECOVER / RETRY_FAILED_STEP / HEARTBEAT_LAG_RECOVER / SCHEDULE_FIRE_RECOVER';

COMMENT ON COLUMN paper_run_recovery_events.status IS '恢复状态：STARTED / SUCCEEDED / FAILED / SKIPPED';

COMMENT ON COLUMN paper_run_recovery_events.reason IS '恢复原因说明';

COMMENT ON COLUMN paper_run_recovery_events.request_json IS '恢复请求参数快照，不保存密钥/token/cookie';

COMMENT ON COLUMN paper_run_recovery_events.result_json IS '恢复结果摘要快照，不保存密钥/token/cookie';

COMMENT ON COLUMN paper_run_recovery_events.started_at IS '恢复开始时间（UTC）';

COMMENT ON COLUMN paper_run_recovery_events.finished_at IS '恢复完成时间（UTC），未完成时为 null';

COMMENT ON COLUMN paper_run_recovery_events.created_at IS '记录创建时间（UTC）';

COMMENT ON TABLE paper_run_schedule_fires IS '调度触发记录，每次调度触发产生一条记录';

COMMENT ON COLUMN paper_run_schedule_fires.fire_id IS '触发记录 ID，格式 fir-<uuid>';

COMMENT ON COLUMN paper_run_schedule_fires.schedule_id IS '关联调度计划 ID';

COMMENT ON COLUMN paper_run_schedule_fires.paper_run_id IS '关联 Paper run ID';

COMMENT ON COLUMN paper_run_schedule_fires.status IS '触发状态：RUNNING / SUCCEEDED / FAILED / SKIPPED';

COMMENT ON COLUMN paper_run_schedule_fires.fired_at IS '触发时间';

COMMENT ON COLUMN paper_run_schedule_fires.finished_at IS '完成时间';

COMMENT ON COLUMN paper_run_schedule_fires.duration_ms IS '执行耗时（毫秒）';

COMMENT ON COLUMN paper_run_schedule_fires.result_json IS '执行结果快照，不保存密钥/token/cookie';

COMMENT ON COLUMN paper_run_schedule_fires.error_message IS '错误信息';

COMMENT ON COLUMN paper_run_schedule_fires.created_at IS '创建时间';

COMMENT ON TABLE paper_run_schedules IS 'Paper run 调度计划，定义 cron 表达式和调度状态';

COMMENT ON COLUMN paper_run_schedules.schedule_id IS '调度计划 ID，格式 sch-<uuid>';

COMMENT ON COLUMN paper_run_schedules.paper_run_id IS '关联 Paper run ID';

COMMENT ON COLUMN paper_run_schedules.schedule_name IS '调度名称';

COMMENT ON COLUMN paper_run_schedules.cron_expr IS 'cron 表达式，如 0 */5 * * * *';

COMMENT ON COLUMN paper_run_schedules.status IS '调度状态：ENABLED / DISABLED / PAUSED';

COMMENT ON COLUMN paper_run_schedules.timezone IS '时区，默认 UTC';

COMMENT ON COLUMN paper_run_schedules.next_fire_time IS '下次触发时间';

COMMENT ON COLUMN paper_run_schedules.last_fire_time IS '上次触发时间';

COMMENT ON COLUMN paper_run_schedules.created_by IS '创建人';

COMMENT ON COLUMN paper_run_schedules.created_at IS '创建时间';

COMMENT ON COLUMN paper_run_schedules.updated_at IS '更新时间';

COMMENT ON COLUMN paper_run_schedules.request_json IS '调度创建请求快照，用于审计和排障，不保存密钥/token/cookie';

COMMENT ON TABLE paper_run_stability_checks IS 'Paper run 稳定性检查结果，用于记录指定时间窗口内心跳、告警、调度失败和恢复事件的统计判定。';

COMMENT ON COLUMN paper_run_stability_checks.stability_check_id IS '稳定性检查 ID，格式 stb-<uuid>。';

COMMENT ON COLUMN paper_run_stability_checks.paper_run_id IS '关联 Paper run ID';

COMMENT ON COLUMN paper_run_stability_checks.check_window_start IS '检查窗口开始时间（UTC，含）。';

COMMENT ON COLUMN paper_run_stability_checks.check_window_end IS '检查窗口结束时间（UTC，不含），必须严格大于 start。';

COMMENT ON COLUMN paper_run_stability_checks.status IS '检查状态：PASSED / FAILED / PARTIAL；按心跳、未处理严重告警和失败触发数判定，不等同于连续运行最终接受记录。';

COMMENT ON COLUMN paper_run_stability_checks.uptime_ratio IS '在线率（0~1，4 位小数）；heartbeat_count > 0 且无 CRITICAL 未处理告警且 failed_fire_count = 0 视为 1.0，否则按监控口径折算。';

COMMENT ON COLUMN paper_run_stability_checks.heartbeat_count IS '窗口内心跳数量';

COMMENT ON COLUMN paper_run_stability_checks.alert_count IS '窗口内告警数量';

COMMENT ON COLUMN paper_run_stability_checks.failed_fire_count IS '窗口内失败的调度触发数量（paper_run_schedule_fires.status = FAILED）';

COMMENT ON COLUMN paper_run_stability_checks.recovery_count IS '窗口内恢复事件数量（paper_run_recovery_events）';

COMMENT ON COLUMN paper_run_stability_checks.report_count IS '窗口内日报数量（paper_run_daily_reports）';

COMMENT ON COLUMN paper_run_stability_checks.summary_json IS '稳定性检查摘要 JSON（明细计数 / CRITICAL 告警列表 / 判定原因），不保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN paper_run_stability_checks.created_at IS '记录创建时间（UTC）';

COMMENT ON TABLE paper_trading_orders IS 'Paper Trading 模拟订单记录：记录 Paper run 产生的订单事实，用于追踪模拟订单生命周期、成交关联和风控审计，不接入真实交易所下单接口。';

COMMENT ON COLUMN paper_trading_orders.paper_order_id IS 'Paper 订单主键，业务可读 ID，例如 pto-<uuid>';

COMMENT ON COLUMN paper_trading_orders.paper_run_id IS '所属 Paper run ID，外键 paper_trading_runs.paper_run_id';

COMMENT ON COLUMN paper_trading_orders.symbol IS '订单交易对，需与 publish 链路引用的数据集和现货 symbol 对齐。';

COMMENT ON COLUMN paper_trading_orders.side IS '订单方向：BUY 买入，SELL 卖出';

COMMENT ON COLUMN paper_trading_orders.order_type IS '订单类型，当前支持 MARKET 或 LIMIT，不承载合约下单类型。';

COMMENT ON COLUMN paper_trading_orders.quantity IS '订单数量，正数；高精度数值保留 18 位小数';

COMMENT ON COLUMN paper_trading_orders.price IS '订单价格，LIMIT 必填，MARKET 可空；高精度数值保留 18 位小数';

COMMENT ON COLUMN paper_trading_orders.status IS '订单状态：CREATED 已创建未撮合；FILLED 已成交；CANCELED 已撤销；REJECTED 风控或撮合拒绝';

COMMENT ON COLUMN paper_trading_orders.reason IS '订单状态变更原因摘要，例如风控拒绝原因或撤销原因';

COMMENT ON COLUMN paper_trading_orders.raw_signal_json IS 'Paper 订单触发信号快照 JSONB，可保存策略版本/参数摘要；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN paper_trading_orders.created_at IS '订单创建时间，UTC';

COMMENT ON COLUMN paper_trading_orders.updated_at IS '订单最近一次状态更新时间，UTC';

COMMENT ON TABLE paper_trading_positions IS 'Paper Trading 模拟持仓记录：记录 Paper run 当前持仓与已实现/未实现盈亏；按 (paper_run_id, symbol) 唯一。';

COMMENT ON COLUMN paper_trading_positions.paper_position_id IS 'Paper 持仓主键，业务可读 ID，例如 ptp-<uuid>';

COMMENT ON COLUMN paper_trading_positions.paper_run_id IS '持仓所属 Paper run ID，外键 paper_trading_runs.paper_run_id';

COMMENT ON COLUMN paper_trading_positions.symbol IS '持仓交易对';

COMMENT ON COLUMN paper_trading_positions.quantity IS '当前净持仓数量，可为 0；不区分多空，当前只记录现货净仓。';

COMMENT ON COLUMN paper_trading_positions.avg_price IS '当前持仓加权平均成本价；持仓为 0 时回到 0';

COMMENT ON COLUMN paper_trading_positions.unrealized_pnl IS '未实现盈亏，由资金/持仓曲线和撮合结果维护。';

COMMENT ON COLUMN paper_trading_positions.realized_pnl IS '已实现盈亏累计值，由资金/持仓曲线和撮合结果维护。';

COMMENT ON COLUMN paper_trading_positions.updated_at IS '持仓最近一次更新时间，UTC';

COMMENT ON COLUMN paper_trading_positions.created_at IS '持仓首次写入时间，UTC';

COMMENT ON TABLE paper_trading_runs IS 'Paper Trading 运行实例表：记录基于 publish_id 的 SIM/Paper 运行实例，固化 publish/strategy version/dataset/param/config 快照，作为风控回写、资金曲线、复盘和异常停机的输入。';

COMMENT ON COLUMN paper_trading_runs.paper_run_id IS 'Paper run 主键，业务可读 ID，例如 ptr-<uuid>';

COMMENT ON COLUMN paper_trading_runs.publish_id IS 'Paper run 引用的发布记录 ID，对应 backtest_publish_records.publish_record_id；只能引用 SUCCEEDED 的发布记录';

COMMENT ON COLUMN paper_trading_runs.strategy_version_id IS 'Paper run 创建时从 publish 链路固化的策略版本 ID，对应 strategy_versions.strategy_version_id；为空表示发布未绑定策略版本';

COMMENT ON COLUMN paper_trading_runs.status IS 'Paper run 状态机：CREATED 已创建未启动；RUNNING 已启动；STOPPED 主动停止；FAILED 启动或运行失败';

COMMENT ON COLUMN paper_trading_runs.trade_env IS 'Paper run 交易环境，固定为 SIM 或 LIVE；当前只允许 SIM。';

COMMENT ON COLUMN paper_trading_runs.exchange_code IS 'Paper run 目标交易所代码，例如 OKX、BINANCE；与 publish 链路 dataset 一致';

COMMENT ON COLUMN paper_trading_runs.market_type IS 'Paper run 市场类型，当前为 SPOT，不承载合约全量交易。';

COMMENT ON COLUMN paper_trading_runs.symbol IS 'Paper run 交易对，例如 BTC-USDT、ETH-USDT、SOL-USDT；与 dataset snapshot 对齐';

COMMENT ON COLUMN paper_trading_runs.interval_code IS 'Paper run 行情周期，例如 1m、5m、15m、1h、4h、1d；保留字段名避开 PostgreSQL interval 关键字';

COMMENT ON COLUMN paper_trading_runs.started_at IS 'Paper run 启动时间；为空表示尚未启动';

COMMENT ON COLUMN paper_trading_runs.stopped_at IS 'Paper run 停止时间；为空表示尚未停止';

COMMENT ON COLUMN paper_trading_runs.publish_snapshot_json IS 'Paper run 创建时固化的发布记录快照 JSONB；不得保存 token、cookie、密钥';

COMMENT ON COLUMN paper_trading_runs.strategy_version_snapshot_json IS 'Paper run 创建时固化的策略版本快照 JSONB，含策略编码、版本号、状态、参数、配置、来源；不得保存敏感凭证';

COMMENT ON COLUMN paper_trading_runs.dataset_snapshot_json IS 'Paper run 创建时固化的 dataset 快照 JSONB，来自 publish 引用的 backtest run，用于行情对齐；不得保存敏感凭证';

COMMENT ON COLUMN paper_trading_runs.param_snapshot_json IS 'Paper run 创建时固化的参数快照 JSONB，来自 publish 链路的策略版本参数；不得保存敏感凭证';

COMMENT ON COLUMN paper_trading_runs.config_snapshot_json IS 'Paper run 创建时固化的运行配置快照 JSONB，含初始资金、撮合口径、手续费等运行级配置；不得保存敏感凭证';

COMMENT ON COLUMN paper_trading_runs.created_by IS 'Paper run 创建者用户名，来自登录上下文；用于审计';

COMMENT ON COLUMN paper_trading_runs.created_at IS 'Paper run 创建时间，UTC';

COMMENT ON COLUMN paper_trading_runs.updated_at IS 'Paper run 最近一次状态或字段更新时间，UTC';

COMMENT ON COLUMN paper_trading_runs.canonical_account_id IS '策略 SIM 新 SIM run 的隔离 canonical accounts 关联；历史研究侧 Paper run 保持 NULL';

COMMENT ON TABLE paper_trading_trades IS 'Paper Trading 模拟成交记录：记录 Paper run 订单的成交事实。';

COMMENT ON COLUMN paper_trading_trades.paper_trade_id IS 'Paper 成交主键，业务可读 ID，例如 ptt-<uuid>';

COMMENT ON COLUMN paper_trading_trades.paper_order_id IS '成交所属订单 ID，外键 paper_trading_orders.paper_order_id';

COMMENT ON COLUMN paper_trading_trades.paper_run_id IS '成交所属 Paper run ID，外键 paper_trading_runs.paper_run_id；冗余便于查询';

COMMENT ON COLUMN paper_trading_trades.symbol IS '成交交易对，与订单 symbol 对齐';

COMMENT ON COLUMN paper_trading_trades.side IS '成交方向 BUY/SELL，与订单 side 对齐';

COMMENT ON COLUMN paper_trading_trades.quantity IS '成交数量，正数；高精度数值';

COMMENT ON COLUMN paper_trading_trades.price IS '成交价格；高精度数值';

COMMENT ON COLUMN paper_trading_trades.fee IS '成交手续费，口径取自 config_snapshot 的 feeRate；缺省为 0。';

COMMENT ON COLUMN paper_trading_trades.traded_at IS '成交发生时间，UTC';

COMMENT ON COLUMN paper_trading_trades.created_at IS '成交记录写入时间，UTC';

COMMENT ON TABLE position_curve_snapshots IS 'Paper Trading 持仓曲线快照表：记录 Paper run 按 symbol 的持仓时间序列，用于持仓曲线展示。';

COMMENT ON COLUMN position_curve_snapshots.position_snapshot_id IS '持仓曲线快照主键，业务可读 ID，例如 pcs-<uuid>';

COMMENT ON COLUMN position_curve_snapshots.paper_run_id IS '所属 Paper run ID，外键 paper_trading_runs.paper_run_id';

COMMENT ON COLUMN position_curve_snapshots.symbol IS '持仓交易对';

COMMENT ON COLUMN position_curve_snapshots.snapshot_time IS '快照时间点，UTC；按此字段排序形成持仓曲线';

COMMENT ON COLUMN position_curve_snapshots.quantity IS '持仓数量';

COMMENT ON COLUMN position_curve_snapshots.avg_price IS '持仓均价';

COMMENT ON COLUMN position_curve_snapshots.mark_price IS '标记价格（快照时刻市场价）';

COMMENT ON COLUMN position_curve_snapshots.position_value IS '持仓市值 = quantity * mark_price';

COMMENT ON COLUMN position_curve_snapshots.unrealized_pnl IS '未实现盈亏';

COMMENT ON COLUMN position_curve_snapshots.realized_pnl IS '已实现盈亏累计';

COMMENT ON COLUMN position_curve_snapshots.source IS '快照来源标识';

COMMENT ON COLUMN position_curve_snapshots.created_at IS '快照写入时间，UTC';

COMMENT ON TABLE positions IS '持仓投影表。保存账户在某交易对上的当前持仓快照，由成交和账本链路驱动。';

COMMENT ON COLUMN positions.id IS '内部持仓记录主键。';

COMMENT ON COLUMN positions.account_id IS '账户维度外键。';

COMMENT ON COLUMN positions.symbol IS '内部交易对标识。';

COMMENT ON COLUMN positions.qty IS '当前总持仓数量。';

COMMENT ON COLUMN positions.available_qty IS '当前可用持仓数量。';

COMMENT ON COLUMN positions.frozen_qty IS '当前冻结持仓数量。';

COMMENT ON COLUMN positions.avg_price IS '当前持仓均价。';

COMMENT ON COLUMN positions.trace_id IS '最近一次更新该投影的链路追踪 ID。';

COMMENT ON COLUMN positions.updated_at IS '持仓投影最后更新时间。';

COMMENT ON TABLE public_market_captures IS '不可变公开行情响应及规范化回放输入；observed_at 是实际观察事实，bars_json.availableAt 是版本化实验可见时间假设。';

COMMENT ON COLUMN public_market_captures.rule_raw_sha256 IS '公开 instrument 原始 HTTP 响应 UTF-8 字节的 SHA-256；与 rule_sha256 规范化选择字段身份分开。';

COMMENT ON TABLE reconciliation_scan_cursors IS '每 venue 一条 canonical 对账扫描进度；不保存订单、成交、账本或授权事实';

COMMENT ON COLUMN reconciliation_scan_cursors.venue IS '扫描所属 venue，也是并发预留锁的唯一键';

COMMENT ON COLUMN reconciliation_scan_cursors.cursor_created_at IS '上次成功预留的最后订单创建时间，初始为空';

COMMENT ON COLUMN reconciliation_scan_cursors.cursor_order_id IS '同时间订单的确定性全序键，不设订单外键以容忍候选删除';

COMMENT ON COLUMN reconciliation_scan_cursors.revision IS '每次非空成功预留递增的进度代际，不是订单版本';

COMMENT ON COLUMN reconciliation_scan_cursors.updated_at IS '扫描预留事务的 UTC 时间，不改变订单更新时间';

COMMENT ON TABLE research_configs IS '研究配置表。保存研究配置主身份、来源策略快照与数据集规格，不直接承载执行域运行事实。';

COMMENT ON COLUMN research_configs.research_config_id IS '研究配置主键。';

COMMENT ON COLUMN research_configs.source_strategy_id IS '来源执行域 strategy_definition 标识，只用于引用来源，不直接复用执行域主键语义。';

COMMENT ON COLUMN research_configs.name IS '研究配置展示名称。';

COMMENT ON COLUMN research_configs.description IS '研究配置描述信息。';

COMMENT ON COLUMN research_configs.strategy_snapshot IS '来源策略定义快照，JSONB 保存发布前的研究输入事实；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN research_configs.config_json IS '研究配置 JSON，包括参数 schema/defaults 与 datasetSpec；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN research_configs.created_at IS '研究配置创建时间。';

COMMENT ON COLUMN research_configs.updated_at IS '研究配置元数据最后更新时间；仅表示配置名称、参数、数据集规格或归档状态等配置元数据变化，不表示回测运行、评估结果或交易事实更新时间。';

COMMENT ON COLUMN research_configs.status IS '研究配置状态，允许值：ACTIVE / ARCHIVED / DISABLED；ARCHIVED 表示用户不再使用但仍保留可复盘配置血缘，DISABLED 表示临时停用，不表示物理删除。';

COMMENT ON COLUMN research_configs.archived_at IS '研究配置归档时间；仅当 status=ARCHIVED 时非空，用于记录配置进入归档状态的时间。';

COMMENT ON COLUMN research_configs.archived_by IS '研究配置归档操作者标识，可为空；只保存内部用户或系统主体标识，不保存密钥、token、API secret、私钥、助记词或账户访问材料。';

COMMENT ON COLUMN research_configs.archive_reason IS '研究配置归档原因，可为空；用于说明归档背景，不得保存密钥、token、API secret、私钥、助记词、cookie 或账户访问材料。';

COMMENT ON TABLE risk_events IS '风控事件表。保存规则命中、拒绝和放行的审计事实，不直接代表订单状态。';

COMMENT ON COLUMN risk_events.risk_event_id IS '内部风控事件主键。';

COMMENT ON COLUMN risk_events.rule_id IS '风控规则标识。';

COMMENT ON COLUMN risk_events.scope IS '风控作用域，例如 ACCOUNT / ORDER。';

COMMENT ON COLUMN risk_events.scope_id IS '作用域对象标识。';

COMMENT ON COLUMN risk_events.decision IS '风控决策，例如 ALLOW / REJECT。';

COMMENT ON COLUMN risk_events.reason IS '风控原因摘要。';

COMMENT ON COLUMN risk_events.severity IS '风控严重级别。';

COMMENT ON COLUMN risk_events.trace_id IS '链路追踪 ID。';

COMMENT ON COLUMN risk_events.created_at IS '风控事件创建时间。';

COMMENT ON TABLE risk_limit_sets IS 'LIVE 会话的不可变风险规则定义；区别于运行期风险判定，不保存凭证、余额或行情原文。';

COMMENT ON COLUMN risk_limit_sets.risk_limit_set_id IS '不可复用的风险规则集 UUID。';

COMMENT ON COLUMN risk_limit_sets.digest_schema_version IS 'canonical 编码版本，首版固定 risk-limit-set.v1。';

COMMENT ON COLUMN risk_limit_sets.version IS '同一 effective scope 内的正整数版本。';

COMMENT ON COLUMN risk_limit_sets.effective_scope IS '规则生效范围，首版固定 LIVE_SESSION_OKX_SPOT。';

COMMENT ON COLUMN risk_limit_sets.quote_currency IS '金额字段计价币种，首版固定 USDT。';

COMMENT ON COLUMN risk_limit_sets.capital_cap IS '会话累计资本上限，NUMERIC(38,8)。';

COMMENT ON COLUMN risk_limit_sets.max_order_notional IS '单笔订单名义金额上限。';

COMMENT ON COLUMN risk_limit_sets.max_symbol_position_notional IS '单 symbol gross position 名义金额上限。';

COMMENT ON COLUMN risk_limit_sets.max_daily_realized_loss IS 'UTC 日已实现损失绝对值上限。';

COMMENT ON COLUMN risk_limit_sets.max_daily_total_loss IS 'UTC 日已实现加不利未实现损失上限。';

COMMENT ON COLUMN risk_limit_sets.max_open_orders IS '最大同时开放订单数。';

COMMENT ON COLUMN risk_limit_sets.max_intraday_orders IS '会话窗口内最大 PLACE intent 数。';

COMMENT ON COLUMN risk_limit_sets.symbol_allowlist IS '1 至 2 个大写、排序、去重的 BASE-USDT symbol。';

COMMENT ON COLUMN risk_limit_sets.order_type_allowlist IS '允许订单类型，首版仅 LIMIT。';

COMMENT ON COLUMN risk_limit_sets.max_session_duration_seconds IS '会话窗口最大秒数。';

COMMENT ON COLUMN risk_limit_sets.spread_limit_bps IS '允许 spread 上限，0 表示只接受零 spread。';

COMMENT ON COLUMN risk_limit_sets.slippage_limit_bps IS '允许不利 slippage 上限，0 表示不允许。';

COMMENT ON COLUMN risk_limit_sets.max_market_data_age_ms IS '行情最大允许延迟毫秒数。';

COMMENT ON COLUMN risk_limit_sets.min_data_coverage_bps IS '行情最小覆盖率基点。';

COMMENT ON COLUMN risk_limit_sets.required_data_source IS '要求的数据源，首版固定 OKX_PRIMARY。';

COMMENT ON COLUMN risk_limit_sets.data_quality_action IS '数据质量失败动作，首版固定 BLOCK。';

COMMENT ON COLUMN risk_limit_sets.canonical_digest IS '全规则字段确定性 canonical SHA-256。';

COMMENT ON COLUMN risk_limit_sets.created_by IS '创建者 users.id。';

COMMENT ON COLUMN risk_limit_sets.created_at IS '不可变创建时间。';

COMMENT ON TABLE roles IS '后台角色表。定义权限角色编码与描述，不区分交易所或交易环境。';

COMMENT ON COLUMN roles.id IS '内部角色主键。';

COMMENT ON COLUMN roles.role_code IS '角色唯一编码，例如 ADMIN / OPERATOR。';

COMMENT ON COLUMN roles.description IS '角色描述。';

COMMENT ON COLUMN roles.created_at IS '角色创建时间。';

COMMENT ON COLUMN roles.updated_at IS '角色配置最后更新时间；用于追踪权限主数据维护时间，不表示用户授权关系更新时间。';

COMMENT ON TABLE scheduled_job_controls IS '固定 Java 任务注册表的动态启停、间隔和最近执行状态；不存可执行代码或外部请求';

COMMENT ON COLUMN scheduled_job_controls.job_key IS '仅允许已审查的固定代码任务身份，未知身份不得执行';

COMMENT ON COLUMN scheduled_job_controls.next_run_at IS '启用任务的下一次可执行时间，禁用时必须为空';

COMMENT ON COLUMN scheduled_job_controls.active_run_id IS '最近一次执行身份，防止迟到的完成回写覆盖新执行';

COMMENT ON COLUMN scheduled_job_controls.version IS '运维配置乐观锁版本，运行状态更新不改变配置版本';

COMMENT ON TABLE shadow_consistency_reports IS 'Paper vs Shadow 一致性报告表：只表达复盘和差异分析，不表达交易授权，不表示 LIVE ready，不保存 credential material';

COMMENT ON COLUMN shadow_consistency_reports.id IS '一致性报告主键，UUID';

COMMENT ON COLUMN shadow_consistency_reports.shadow_run_id IS '所属 Shadow Run，本地外键 shadow_runs.id';

COMMENT ON COLUMN shadow_consistency_reports.paper_run_id IS '可空 Paper run 引用，仅用于复盘对照，不写 Paper 事实';

COMMENT ON COLUMN shadow_consistency_reports.comparison_status IS '一致性状态：CONSISTENT、DIVERGED、NOT_COMPARABLE、PARTIAL、FAILED；不包含授权或批准语义';

COMMENT ON COLUMN shadow_consistency_reports.metric_delta IS '脱敏指标差异 JSONB，只保存复盘指标，不保存真实账户余额、真实持仓或真实订单 ID';

COMMENT ON COLUMN shadow_consistency_reports.divergence_reasons IS '偏离原因 JSONB 数组，只保存脱敏复盘原因';

COMMENT ON COLUMN shadow_consistency_reports.limitations IS '限制与不可比说明 JSONB 数组；必须显式表达缺失或不可比，不得补造成功态';

COMMENT ON COLUMN shadow_consistency_reports.generated_at IS '报告生成时间';

COMMENT ON COLUMN shadow_consistency_reports.trace_id IS '报告追踪 ID，串联本地审计链';

COMMENT ON COLUMN shadow_consistency_reports.created_at IS '报告写入时间';

COMMENT ON TABLE shadow_run_events IS 'Shadow Run append-only 事件表：记录状态流转、阻断、失败、非法流转尝试和审计事件；不保存 credential material，不调用真实交易';

COMMENT ON COLUMN shadow_run_events.id IS 'Shadow Run 事件主键，UUID';

COMMENT ON COLUMN shadow_run_events.shadow_run_id IS '所属 Shadow Run，本地外键 shadow_runs.id';

COMMENT ON COLUMN shadow_run_events.event_type IS '事件类型枚举：CREATED、PRECHECK_STARTED、PRECHECK_PASSED、PRECHECK_BLOCKED、RUN_STARTED、STOP_REQUESTED、STOPPED、COMPLETED、FAILED、CANCELLED、ILLEGAL_STATE_TRANSITION_ATTEMPT、SNAPSHOT_CAPTURED、CONSISTENCY_REPORT_GENERATED';

COMMENT ON COLUMN shadow_run_events.from_status IS '事件发生前状态；仅用于状态迁移审计';

COMMENT ON COLUMN shadow_run_events.to_status IS '事件目标状态；非法流转事件不更新主表状态';

COMMENT ON COLUMN shadow_run_events.reason_code IS '结构化原因码；不得包含密钥、token、cookie 或真实交易所响应';

COMMENT ON COLUMN shadow_run_events.message IS '脱敏摘要，不保存原始 private request、raw response、签名串或 credential material';

COMMENT ON COLUMN shadow_run_events.metadata IS '脱敏事件元数据 JSONB，只保存本地上下文和计数，不保存 credential、raw request、raw response 或 private endpoint payload';

COMMENT ON COLUMN shadow_run_events.request_id IS '事件来源请求 ID，可空；不保存原始请求体';

COMMENT ON COLUMN shadow_run_events.trace_id IS '事件追踪 ID，串联本地审计链';

COMMENT ON COLUMN shadow_run_events.created_at IS '事件写入时间；事件表按 append-only 使用';

COMMENT ON TABLE shadow_run_snapshots IS 'Shadow Run 快照表：保存输入行情、策略决策、风险预检和订单意图预览的本地脱敏快照；不保存 credential material，不保存真实订单状态，不代表交易授权';

COMMENT ON COLUMN shadow_run_snapshots.id IS 'Shadow Run 快照主键，UUID';

COMMENT ON COLUMN shadow_run_snapshots.shadow_run_id IS '所属 Shadow Run，本地外键 shadow_runs.id';

COMMENT ON COLUMN shadow_run_snapshots.snapshot_type IS '快照类型：INPUT_MARKETDATA、STRATEGY_DECISION、RISK_PREFLIGHT、ORDER_INTENT_PREVIEW';

COMMENT ON COLUMN shadow_run_snapshots.sequence_no IS '同一 Shadow Run 与快照类型内的顺序号，用于 replay / review';

COMMENT ON COLUMN shadow_run_snapshots.source IS '快照来源，例如 dataset、strategy、risk-preflight、order-intent-preview；不保存 private endpoint payload';

COMMENT ON COLUMN shadow_run_snapshots.schema_version IS '快照结构版本，用于后续兼容读取';

COMMENT ON COLUMN shadow_run_snapshots.checksum IS '快照内容校验摘要，不得为空；用于证明本地脱敏快照一致性';

COMMENT ON COLUMN shadow_run_snapshots.payload IS '脱敏快照 JSONB；禁止保存 credential、private request/response、真实账户余额、真实订单状态或交易所私有 payload';

COMMENT ON COLUMN shadow_run_snapshots.captured_at IS '快照捕获时间，表示本地事实时间';

COMMENT ON COLUMN shadow_run_snapshots.trace_id IS '快照追踪 ID，串联本地审计链';

COMMENT ON COLUMN shadow_run_snapshots.created_at IS '快照写入时间';

COMMENT ON TABLE shadow_runs IS 'Shadow Run 本地事实主表：记录一次无真实交易副作用的影子运行状态和追溯链；不代表交易授权，不保存 credential material，不代表 LIVE ready，不产生真实交易副作用';

COMMENT ON COLUMN shadow_runs.id IS 'Shadow Run 本地事实主键，UUID；只用于本地审计和复盘，不是交易所订单 ID';

COMMENT ON COLUMN shadow_runs.strategy_version_id IS '引用 strategy_versions.strategy_version_id；作为只读输入事实，不改写策略版本';

COMMENT ON COLUMN shadow_runs.dataset_id IS '引用 marketdata_datasets.dataset_id；作为只读行情数据集输入事实';

COMMENT ON COLUMN shadow_runs.evaluation_id IS '可空引用 backtest_eval_reports.eval_report_id；用于追溯评估事实，不改写评估状态';

COMMENT ON COLUMN shadow_runs.publish_id IS '可空引用 backtest_publish_records.publish_record_id；用于追溯发布事实，不表示 LIVE 发布';

COMMENT ON COLUMN shadow_runs.paper_run_id IS '可空引用 paper_trading_runs.paper_run_id；仅用于 Paper vs Shadow 复盘对照，不写 Paper 事实';

COMMENT ON COLUMN shadow_runs.status IS 'Shadow Run 状态：CREATED、PRECHECKING、READY、RUNNING、STOP_REQUESTED、STOPPED、COMPLETED、BLOCKED、FAILED、CANCELLED；终态不可回写运行态';

COMMENT ON COLUMN shadow_runs.window_start IS 'Shadow Run 输入窗口开始时间；为空表示本轮尚未绑定窗口';

COMMENT ON COLUMN shadow_runs.window_end IS 'Shadow Run 输入窗口结束时间；存在时不得早于 window_start';

COMMENT ON COLUMN shadow_runs.side_effect_policy IS '无副作用策略 JSONB，仅保存本地诊断策略，不保存密钥、token、cookie、private endpoint payload 或真实请求响应';

COMMENT ON COLUMN shadow_runs.no_order_submission IS '固定为 true，表示 Shadow Run 禁止提交真实订单';

COMMENT ON COLUMN shadow_runs.no_credential_access IS '固定为 true，表示 Shadow Run 禁止读取 credential material';

COMMENT ON COLUMN shadow_runs.no_private_endpoint IS '固定为 true，表示 Shadow Run 禁止调用 private endpoint';

COMMENT ON COLUMN shadow_runs.no_ledger_mutation IS '固定为 true，表示 Shadow Run 禁止修改 ledger 或账务事实';

COMMENT ON COLUMN shadow_runs.no_account_mutation IS '固定为 true，表示 Shadow Run 禁止修改真实账户事实';

COMMENT ON COLUMN shadow_runs.no_external_private_io IS '固定为 true，表示 Shadow Run 禁止外部私有 IO';

COMMENT ON COLUMN shadow_runs.authorization_boundary IS '授权边界枚举：DIAGNOSTIC_ONLY、REVIEW_ONLY、REPLAY_ONLY；只表达诊断/复盘边界，不表达交易授权';

COMMENT ON COLUMN shadow_runs.request_id IS '创建请求 ID，可空；用于本地审计和幂等追踪，不保存原始请求体';

COMMENT ON COLUMN shadow_runs.idempotency_key IS '创建幂等键，唯一；重复创建必须返回同一 Shadow Run';

COMMENT ON COLUMN shadow_runs.trace_id IS '追踪 ID，串联 run、event、snapshot 和 report，不保存 credential 或 token';

COMMENT ON COLUMN shadow_runs.blockers IS '阻断原因 JSONB 数组，仅保存脱敏复盘原因，不保存凭证、私有请求、真实账户余额或真实订单状态';

COMMENT ON COLUMN shadow_runs.warnings IS '警告 JSONB 数组，仅保存脱敏诊断信息';

COMMENT ON COLUMN shadow_runs.next_steps IS '后续动作 JSONB 数组，仅保存人工复核建议，不表示交易批准';

COMMENT ON COLUMN shadow_runs.version IS '乐观锁版本号；状态更新必须带 expected version，防止并发覆盖和终态回写';

COMMENT ON COLUMN shadow_runs.created_at IS 'Shadow Run 本地事实创建时间';

COMMENT ON COLUMN shadow_runs.updated_at IS 'Shadow Run 本地事实最近更新时间';

COMMENT ON COLUMN shadow_runs.started_at IS 'Shadow Run 本地无副作用运行开始时间；不表示真实交易开始';

COMMENT ON COLUMN shadow_runs.stopped_at IS 'Shadow Run 本地无副作用停止时间；不表示撤单或交易停止';

COMMENT ON COLUMN shadow_runs.completed_at IS 'Shadow Run 本地事实完成时间；不表示交易批准或 LIVE ready';

COMMENT ON COLUMN shadow_runs.artifact_digest IS 'Shadow Run 创建时固化的 strategy release artifact SHA-256，小写 64 位十六进制；为空表示历史未绑定或仅绑定 publish_id，不做推测或回填，不表示 admission、交易批准或 LIVE ready';

COMMENT ON CONSTRAINT chk_shadow_runs_artifact_digest_sha256 ON shadow_runs IS 'artifact_digest 为空或严格为 64 位小写十六进制 SHA-256；禁止空字符串、大写和非十六进制值';

COMMENT ON CONSTRAINT chk_shadow_runs_artifact_requires_publish ON shadow_runs IS '存在 artifact_digest 时必须同时存在 publish_id；允许历史无绑定和仅 publish_id 绑定';

COMMENT ON TABLE sim_orders IS '模拟订单事实表。保存回测执行过程中生成的模拟订单，不复用实盘 orders 表。';

COMMENT ON COLUMN sim_orders.sim_order_id IS '模拟订单主键。';

COMMENT ON COLUMN sim_orders.backtest_run_id IS '所属回测运行 ID。';

COMMENT ON COLUMN sim_orders.symbol IS '回测交易对标识。';

COMMENT ON COLUMN sim_orders.side IS '模拟订单方向。';

COMMENT ON COLUMN sim_orders.order_type IS '模拟订单类型。当前主要为 MARKET。';

COMMENT ON COLUMN sim_orders.requested_quantity IS '请求下单数量。';

COMMENT ON COLUMN sim_orders.requested_price IS '请求下单价格。当前 close 成交规则下记录 bar close。';

COMMENT ON COLUMN sim_orders.status IS '模拟订单状态，固定口径为 CREATED / FILLED / REJECTED。';

COMMENT ON COLUMN sim_orders.created_at IS '模拟订单创建时间。';

COMMENT ON COLUMN sim_orders.filled_at IS '模拟订单成交时间。';

COMMENT ON COLUMN sim_orders.reject_reason IS '模拟订单拒绝原因。';

COMMENT ON COLUMN sim_orders.updated_at IS '模拟订单最后更新时间。';

COMMENT ON TABLE sim_pnl_snapshots IS '模拟权益快照表。保存回测运行过程中逐时点的现金、权益与 PnL 原始序列。';

COMMENT ON COLUMN sim_pnl_snapshots.sim_pnl_snapshot_id IS '模拟 PnL 快照主键。';

COMMENT ON COLUMN sim_pnl_snapshots.backtest_run_id IS '所属回测运行 ID。';

COMMENT ON COLUMN sim_pnl_snapshots.snapshot_time IS '快照时间。当前通常与 bar close 或成交后时点一致。';

COMMENT ON COLUMN sim_pnl_snapshots.cash_balance IS '当前现金余额。';

COMMENT ON COLUMN sim_pnl_snapshots.position_market_value IS '当前持仓市值。';

COMMENT ON COLUMN sim_pnl_snapshots.realized_pnl IS '当前已实现 PnL。';

COMMENT ON COLUMN sim_pnl_snapshots.unrealized_pnl IS '当前未实现 PnL。';

COMMENT ON COLUMN sim_pnl_snapshots.total_fee IS '累计手续费。';

COMMENT ON COLUMN sim_pnl_snapshots.total_slippage IS '累计滑点。';

COMMENT ON COLUMN sim_pnl_snapshots.equity IS '当前总权益。';

COMMENT ON COLUMN sim_pnl_snapshots.net_pnl IS '当前净 PnL。';

COMMENT ON COLUMN sim_pnl_snapshots.created_at IS '模拟 PnL 快照记录创建时间。';

COMMENT ON TABLE sim_positions IS '模拟持仓事实表。保存 run + symbol 维度的当前持仓，不复用实盘 positions 投影。';

COMMENT ON COLUMN sim_positions.sim_position_id IS '模拟持仓主键。';

COMMENT ON COLUMN sim_positions.backtest_run_id IS '所属回测运行 ID。';

COMMENT ON COLUMN sim_positions.symbol IS '回测交易对标识。';

COMMENT ON COLUMN sim_positions.quantity IS '当前模拟持仓数量。';

COMMENT ON COLUMN sim_positions.average_entry_price IS '当前模拟持仓均价。';

COMMENT ON COLUMN sim_positions.realized_pnl IS '当前已实现 PnL。';

COMMENT ON COLUMN sim_positions.created_at IS '模拟持仓记录创建时间。';

COMMENT ON COLUMN sim_positions.updated_at IS '模拟持仓最后更新时间。';

COMMENT ON TABLE sim_trades IS '模拟成交事实表。保存由模拟订单撮合生成的成交结果，不复用实盘 trades 表。';

COMMENT ON COLUMN sim_trades.sim_trade_id IS '模拟成交主键。';

COMMENT ON COLUMN sim_trades.sim_order_id IS '所属模拟订单 ID。';

COMMENT ON COLUMN sim_trades.backtest_run_id IS '所属回测运行 ID。';

COMMENT ON COLUMN sim_trades.symbol IS '回测交易对标识。';

COMMENT ON COLUMN sim_trades.side IS '模拟成交方向。';

COMMENT ON COLUMN sim_trades.quantity IS '模拟成交数量。';

COMMENT ON COLUMN sim_trades.trade_price IS '模拟成交价格。当前统一按 bar close 成交。';

COMMENT ON COLUMN sim_trades.fee_amount IS '模拟成交手续费金额。';

COMMENT ON COLUMN sim_trades.slippage_amount IS '模拟成交滑点金额。';

COMMENT ON COLUMN sim_trades.traded_at IS '模拟成交发生时间。';

COMMENT ON COLUMN sim_trades.created_at IS '模拟成交记录创建时间。';

COMMENT ON COLUMN sim_trades.updated_at IS '模拟成交记录最后更新时间。';

COMMENT ON TABLE strategy_definitions IS '策略定义主表。保存策略注册项、启停状态和配置快照，不引入 strategyInstanceId。';

COMMENT ON COLUMN strategy_definitions.strategy_id IS '策略定义主键，定义级身份，不代表单次运行。';

COMMENT ON COLUMN strategy_definitions.strategy_code IS '策略注册业务唯一键。一个启用中的策略注册项按 strategy_code 唯一识别。';

COMMENT ON COLUMN strategy_definitions.strategy_name IS '策略展示名称，用于管理界面和审计定位。';

COMMENT ON COLUMN strategy_definitions.strategy_type IS '策略类型，例如 GRID、DEMO、MOMENTUM。';

COMMENT ON COLUMN strategy_definitions.exchange_code IS '统一交易所标识，固定口径为 OKX / BINANCE / PAPER 等。';

COMMENT ON COLUMN strategy_definitions.account_id IS '策略绑定账户 ID，区分同策略在不同账户下的注册项。';

COMMENT ON COLUMN strategy_definitions.trade_env IS '交易环境，固定枚举为 SIM / LIVE。';

COMMENT ON COLUMN strategy_definitions.enabled IS '策略注册启停开关。默认 FALSE，避免注册后立即进入运行。';

COMMENT ON COLUMN strategy_definitions.config_snapshot IS '策略定义级配置快照，JSONB 保存当前生效配置。';

COMMENT ON COLUMN strategy_definitions.version IS '策略定义版本号。每次配置变更时递增，用于配置快照审计。';

COMMENT ON COLUMN strategy_definitions.created_at IS '策略定义创建时间。';

COMMENT ON COLUMN strategy_definitions.updated_at IS '策略定义最后更新时间。';

COMMENT ON TABLE strategy_release_admission_state IS '策略发布准入的一致性状态；未绑定身份时 revision 为零且身份字段为空，不推测或回填 digest。';

COMMENT ON COLUMN strategy_release_admission_state.publish_record_id IS '唯一 release identity，对应 backtest_publish_records.publish_record_id；同时作为主键和 RESTRICT 外键';

COMMENT ON COLUMN strategy_release_admission_state.admission_revision IS 'admission-sensitive source fact 的单调版本；业务只依赖发生变化，不依赖每次严格加一';

COMMENT ON COLUMN strategy_release_admission_state.guard_schema_version IS '发布准入 guard 持久化结构版本，固定为 1。';

COMMENT ON COLUMN strategy_release_admission_state.release_artifact_digest IS '经服务端验证后的 release artifact-set SHA-256；NULL 表示 LEGACY_RELEASE_IDENTITY_UNBOUND，禁止历史推测';

COMMENT ON COLUMN strategy_release_admission_state.manifest_fingerprint IS 'strategy-release-manifest-fingerprint.v1 canonical encoding 的 SHA-256；不哈希 raw JSON';

COMMENT ON COLUMN strategy_release_admission_state.manifest_schema_version IS '已验证 manifest schema，当前只允许 strategy-release-manifest.v1';

COMMENT ON COLUMN strategy_release_admission_state.identity_bound_at IS 'identity quartet 首次原子绑定时间；绑定后不可清空或修改';

COMMENT ON COLUMN strategy_release_admission_state.created_at IS 'admission state 初始化时间';

COMMENT ON COLUMN strategy_release_admission_state.updated_at IS '最近 revision bump 或 identity first-bind 时间';

COMMENT ON TABLE strategy_run_dispatch_work IS '原 run 的不可变请求意图与一次冻结有效执行参数；不得从最新配置猜测恢复请求';

COMMENT ON COLUMN strategy_run_dispatch_work.work_schema_version IS '指令解释版本；不是 owner、lease 或 generation';

COMMENT ON COLUMN strategy_run_dispatch_work.definition_version IS '产生本次定义快照的版本，只用于血缘，不用于重读当前定义';

COMMENT ON COLUMN strategy_run_dispatch_work.account_id IS '仅为账户与 client 唯一键保留的副本，必须等于原 run 的账户';

COMMENT ON COLUMN strategy_run_dispatch_work.quantity IS '原始 requested 数量，保留策略意图；不能作为规范化后订单的终态比较值';

COMMENT ON COLUMN strategy_run_dispatch_work.time_in_force IS '首次有效请求的显式 TIF，恢复不得重新采用可变默认值';

COMMENT ON COLUMN strategy_run_dispatch_work.effective_quantity IS '事务 B 一次冻结的实际提交数量，与 canonical Order.qty 相等；绑定前可为空';

COMMENT ON COLUMN strategy_run_dispatch_work.effective_price IS '与 effective_quantity 一起冻结的实际提交价格；不在 adapter 外复制取整规则';

COMMENT ON COLUMN strategy_run_dispatch_work.normalization_rejection IS '确定无效的规范化结果，与原 run 的 FAILED 同事务提交，不产生 Order/发送许可';

COMMENT ON TABLE strategy_run_recovery_scan_cursor IS '有界循环检查位置，不代表执行占有或外部 mutation 权限';

COMMENT ON TABLE strategy_runs IS '单次策略运行表。strategy_run_id 是运行级身份，request_id 仅保存首次触发请求身份。';

COMMENT ON COLUMN strategy_runs.strategy_run_id IS '策略运行主键，运行级身份。';

COMMENT ON COLUMN strategy_runs.strategy_id IS '所属策略定义 ID，定义级身份。';

COMMENT ON COLUMN strategy_runs.account_id IS '本次运行绑定账户 ID。';

COMMENT ON COLUMN strategy_runs.status IS '策略运行状态，例如 CREATED / RUNNING / SUCCEEDED / FAILED。';

COMMENT ON COLUMN strategy_runs.started_at IS '运行开始时间。';

COMMENT ON COLUMN strategy_runs.finished_at IS '运行结束时间。历史字段 ended_at 已收口为 finished_at。';

COMMENT ON COLUMN strategy_runs.trace_id IS '链路追踪 ID。';

COMMENT ON COLUMN strategy_runs.created_at IS '运行记录创建时间。';

COMMENT ON COLUMN strategy_runs.trigger_type IS '运行触发来源，固定口径为 MANUAL / SCHEDULER / RECOVERY。';

COMMENT ON COLUMN strategy_runs.exchange_code IS '本次运行对应交易所标识。';

COMMENT ON COLUMN strategy_runs.trade_env IS '本次运行对应交易环境，固定枚举为 SIM / LIVE。';

COMMENT ON COLUMN strategy_runs.config_snapshot IS '运行时配置快照。与策略定义快照分离，便于复盘单次运行。';

COMMENT ON COLUMN strategy_runs.request_id IS '首次接受的执行请求 ID。属于请求级身份，不等同于 strategy_run_id。';

COMMENT ON COLUMN strategy_runs.error_message IS '运行终态错误摘要，仅记录最终可见错误信息。';

COMMENT ON COLUMN strategy_runs.admission_schedule_id IS '原子认领的计划配置身份；历史和独立手动触发保持NULL，不自动回填。';

COMMENT ON COLUMN strategy_runs.admission_due_at IS 'CRON计算的逻辑到期时刻；不是扫描调用时刻或进程身份，消费后不得更换。';

COMMENT ON TABLE strategy_schedules IS '策略调度配置表。保存策略与调度作业的关系、触发规则和去重范围，不承载单次触发实例。';

COMMENT ON COLUMN strategy_schedules.schedule_job_id IS '调度作业主键，调度级身份。';

COMMENT ON COLUMN strategy_schedules.strategy_id IS '所属策略定义 ID，定义级外键。';

COMMENT ON COLUMN strategy_schedules.schedule_type IS '调度类型。当前允许 CRON / INTERVAL / MANUAL。';

COMMENT ON COLUMN strategy_schedules.cron_expr IS 'CRON 表达式。schedule_type=CRON 时使用。';

COMMENT ON COLUMN strategy_schedules.timezone IS '调度时区。默认 UTC。';

COMMENT ON COLUMN strategy_schedules.enabled IS '调度启停开关。默认 FALSE。';

COMMENT ON COLUMN strategy_schedules.window_config IS '运行窗口配置快照，JSONB。';

COMMENT ON COLUMN strategy_schedules.dedup_scope IS '调度去重范围，控制同一策略在窗口内如何去重。';

COMMENT ON COLUMN strategy_schedules.exchange_code IS '统一交易所标识，供调度扫描和路由使用。';

COMMENT ON COLUMN strategy_schedules.account_id IS '调度绑定账户 ID。';

COMMENT ON COLUMN strategy_schedules.trade_env IS '交易环境，固定枚举为 SIM / LIVE。';

COMMENT ON COLUMN strategy_schedules.last_triggered_at IS '新写入为原子 admission 的 dueAt 消费水位；旧 scan-now 值仅作消费下界';

COMMENT ON COLUMN strategy_schedules.created_at IS '调度配置创建时间。';

COMMENT ON COLUMN strategy_schedules.updated_at IS '调度配置最后更新时间。';

COMMENT ON TABLE strategy_sim_decisions IS '策略 SIM 策略决策与既有 strategy_runs/orders 的关联及可重放输入；不是第二套订单或成交事实';

COMMENT ON COLUMN strategy_sim_decisions.execution_open_time IS '历史回放为执行 bar 开盘时刻；连续 SIM 为公开报价的实际观察时刻，具体类型见 input_snapshot_json';

COMMENT ON COLUMN strategy_sim_decisions.execution_bar_sha256 IS '历史回放为执行 bar 身份；连续 SIM 为公开报价时刻和价格身份，具体类型见 input_snapshot_json';

COMMENT ON COLUMN strategy_sim_decisions.input_snapshot_json IS '实际消费的 closed signal bars 与后续执行事件快照，按对应 SHA-256 校验；不得含凭证';

COMMENT ON COLUMN strategy_sim_decisions.reason IS '无信号、不可交易或风控拒绝原因；ACCEPTED 仅表示 canonical 订单已接受，不伪造成交';

COMMENT ON TABLE strategy_versions IS '策略版本表，记录策略定义可用于回测、发布和 Paper Trading 的不可变版本快照。';

COMMENT ON COLUMN strategy_versions.strategy_version_id IS '策略版本 ID，业务主键，格式由应用生成';

COMMENT ON COLUMN strategy_versions.strategy_code IS '关联策略编码，对应 strategy_definitions.strategy_code，表示该版本所属策略定义';

COMMENT ON COLUMN strategy_versions.version IS '策略版本号，同一 strategy_code 下单调递增，用于版本排序和幂等审计';

COMMENT ON COLUMN strategy_versions.version_name IS '策略版本展示名称，用于前端展示和人工审计，不参与策略算法执行';

COMMENT ON COLUMN strategy_versions.status IS '策略版本状态，允许值：DRAFT、ACTIVE、ARCHIVED；用于版本管理和发布引用。';

COMMENT ON COLUMN strategy_versions.param_snapshot_json IS '策略参数快照 JSON，用于回测、发布和后续 Paper run 复现输入；不得保存密钥、token、cookie';

COMMENT ON COLUMN strategy_versions.config_snapshot_json IS '策略配置快照 JSON，通常来自 strategy_definitions.config_snapshot 或创建请求覆盖；不得保存敏感凭证';

COMMENT ON COLUMN strategy_versions.source_snapshot_json IS '策略来源快照 JSON，用于记录策略定义、代码引用或外部来源摘要；不得保存敏感凭证';

COMMENT ON COLUMN strategy_versions.checksum IS '参数、配置、来源快照的 SHA-256 校验摘要，用于识别版本内容是否一致';

COMMENT ON COLUMN strategy_versions.created_by IS '创建人标识，来自 API principal 或系统默认值，用于审计';

COMMENT ON COLUMN strategy_versions.created_at IS '策略版本创建时间';

COMMENT ON COLUMN strategy_versions.updated_at IS '策略版本最后更新时间，通常只随状态维护变化。';

COMMENT ON TABLE trade_replay_records IS 'Paper Trading 交易复盘记录表：保存 Paper run 的交易决策审计链路，用于单笔交易复盘。';

COMMENT ON COLUMN trade_replay_records.replay_record_id IS '复盘记录主键，业务可读 ID，例如 trr-<uuid>';

COMMENT ON COLUMN trade_replay_records.paper_run_id IS '所属 Paper run ID，外键 paper_trading_runs.paper_run_id';

COMMENT ON COLUMN trade_replay_records.paper_order_id IS '关联订单 ID，可空（非订单事件时为空）';

COMMENT ON COLUMN trade_replay_records.paper_trade_id IS '关联成交 ID，可空（非成交事件时为空）';

COMMENT ON COLUMN trade_replay_records.replay_time IS '事件发生时间，UTC';

COMMENT ON COLUMN trade_replay_records.event_type IS '事件类型，例如 SIGNAL、ORDER_CREATED、ORDER_FILLED、RISK_CHECK、POSITION_UPDATE';

COMMENT ON COLUMN trade_replay_records.symbol IS '事件关联交易对';

COMMENT ON COLUMN trade_replay_records.side IS '方向 BUY/SELL，可空';

COMMENT ON COLUMN trade_replay_records.price IS '价格，可空';

COMMENT ON COLUMN trade_replay_records.quantity IS '数量，可空';

COMMENT ON COLUMN trade_replay_records.reason IS '事件原因摘要';

COMMENT ON COLUMN trade_replay_records.decision_snapshot_json IS '决策快照 JSONB，保存策略信号和参数摘要；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN trade_replay_records.risk_snapshot_json IS '风控快照 JSONB，保存风控检查输入输出摘要；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN trade_replay_records.market_snapshot_json IS '市场快照 JSONB，保存事件时刻行情摘要；不得保存敏感凭证、访问令牌、账户私密材料或可用于账户访问的信息。';

COMMENT ON COLUMN trade_replay_records.created_at IS '记录写入时间，UTC';

COMMENT ON TABLE trades IS '成交事实表。trade_id 是内部主键，exchange_trade_id 是交易所成交号。';

COMMENT ON COLUMN trades.trade_id IS '内部成交主键。';

COMMENT ON COLUMN trades.order_id IS '所属内部订单 ID。';

COMMENT ON COLUMN trades.account_id IS '所属账户 ID。';

COMMENT ON COLUMN trades.symbol IS '内部交易对标识。';

COMMENT ON COLUMN trades.exchange IS '历史兼容列。当前仍被代码使用，语义等同 exchange_code。';

COMMENT ON COLUMN trades.exchange_trade_id IS '交易所成交号，参与成交去重。';

COMMENT ON COLUMN trades.price IS '成交价格。';

COMMENT ON COLUMN trades.qty IS '成交数量。';

COMMENT ON COLUMN trades.fee IS '成交手续费。';

COMMENT ON COLUMN trades.fee_currency IS '手续费币种。';

COMMENT ON COLUMN trades.trace_id IS '链路追踪 ID。';

COMMENT ON COLUMN trades.ts IS '成交事实时间。';

COMMENT ON COLUMN trades.created_at IS '成交记录创建时间。';

COMMENT ON COLUMN trades.external_order_id IS '历史兼容列。语义等同 exchange_order_id。';

COMMENT ON COLUMN trades.strategy_run_id IS '所属策略运行 ID，便于按运行直接反查成交血缘。';

COMMENT ON COLUMN trades.exchange_code IS '统一交易所标识。';

COMMENT ON COLUMN trades.trade_env IS '交易环境，固定枚举为 SIM / LIVE；当前兼容口径默认 SIM。';

COMMENT ON COLUMN trades.exchange_order_id IS '交易所订单号，便于按订单维度回溯成交。';

COMMENT ON TABLE user_roles IS '用户与角色关联表。保存后台用户授权关系，不参与交易幂等或去重。';

COMMENT ON COLUMN user_roles.user_id IS '内部用户主键外键。';

COMMENT ON COLUMN user_roles.role_id IS '内部角色主键外键。';

COMMENT ON COLUMN user_roles.granted_at IS '角色授予时间。';

COMMENT ON TABLE users IS '系统用户表。保存后台登录用户的最小身份与启用状态，不承载交易账户或策略身份。';

COMMENT ON COLUMN users.id IS '内部用户主键。';

COMMENT ON COLUMN users.username IS '登录用户名，系统内部唯一标识。';

COMMENT ON COLUMN users.password_hash IS '密码哈希值，不保存明文密码。';

COMMENT ON COLUMN users.enabled IS '用户启用开关，控制是否允许登录后台。';

COMMENT ON COLUMN users.created_at IS '用户创建时间。';

COMMENT ON COLUMN users.updated_at IS '用户最后更新时间。';

COMMENT ON TABLE validation_review_cases IS '本地人工复核 case 主事实；只记录诊断与人工复核生命周期，不代表交易授权，不表示 LIVE ready，不修改策略、Paper、Shadow、risk、account、order 或 ledger，不保存 credential material';

COMMENT ON COLUMN validation_review_cases.id IS '本地人工复核 case UUID 主键';

COMMENT ON COLUMN validation_review_cases.tenant_key IS '服务端提供的租户隔离键，固定为 NQ_LOCAL；客户端不得覆盖。';

COMMENT ON COLUMN validation_review_cases.owner_id IS 'case 所属用户，引用 users.id；OPERATOR 查询和更新必须包含该 owner scope';

COMMENT ON COLUMN validation_review_cases.evidence_type IS '脱敏证据类型；只定位本地 validation、incident 或 replay 事实';

COMMENT ON COLUMN validation_review_cases.evidence_source IS '脱敏证据来源标识；不保存 private endpoint 或真实订单来源';

COMMENT ON COLUMN validation_review_cases.evidence_anchor IS '脱敏本地证据锚点 JSONB；禁止保存 credential、账户余额、真实订单或 private payload';

COMMENT ON COLUMN validation_review_cases.severity IS '人工复核优先级：INFO、WARNING、HIGH、CRITICAL；不表示风险批准或交易放行';

COMMENT ON COLUMN validation_review_cases.state IS '本地复核状态：OPEN、ACKNOWLEDGED、ESCALATED、RESOLVED、CLOSED；任何状态均不代表交易授权';

COMMENT ON COLUMN validation_review_cases.title IS '人工复核 case 标题；必须为脱敏本地摘要';

COMMENT ON COLUMN validation_review_cases.summary IS '可空脱敏复核摘要；不得保存 credential、账户余额、真实订单或原始 private request/response';

COMMENT ON COLUMN validation_review_cases.version IS '乐观锁版本；每个 accepted lifecycle transition 递增 1';

COMMENT ON COLUMN validation_review_cases.created_by IS '创建 case 的本地用户，引用 users.id';

COMMENT ON COLUMN validation_review_cases.created_at IS 'case 创建时间，UTC';

COMMENT ON COLUMN validation_review_cases.updated_at IS 'case 最近 accepted transition 更新时间，UTC';

COMMENT ON COLUMN validation_review_cases.acknowledged_by IS '执行 ACKNOWLEDGED 的本地用户；只表示已查看，不表示批准';

COMMENT ON COLUMN validation_review_cases.acknowledged_at IS '进入 ACKNOWLEDGED 的时间，UTC';

COMMENT ON COLUMN validation_review_cases.escalated_by IS '执行 ESCALATED 的本地用户；只表示升级人工处理';

COMMENT ON COLUMN validation_review_cases.escalated_at IS '进入 ESCALATED 的时间，UTC';

COMMENT ON COLUMN validation_review_cases.resolved_by IS '执行 RESOLVED 的本地用户；只表示复核问题已处理';

COMMENT ON COLUMN validation_review_cases.resolved_at IS '进入 RESOLVED 的时间，UTC';

COMMENT ON COLUMN validation_review_cases.closed_by IS '执行 CLOSED 的本地用户；只表示本地 case 关闭';

COMMENT ON COLUMN validation_review_cases.closed_at IS '进入 CLOSED 的时间，UTC';

COMMENT ON COLUMN validation_review_cases.retention_until IS '关闭后的保留期限；不表示存在自动删除或归档任务。';

COMMENT ON TABLE validation_review_events IS '本地人工复核 append-only lifecycle event；只记录 accepted transition，不代表交易授权，不表示 LIVE ready，不修改任何交易或运行事实，不保存 credential material';

COMMENT ON COLUMN validation_review_events.id IS '人工复核 lifecycle event UUID 主键';

COMMENT ON COLUMN validation_review_events.review_case_id IS '所属本地 review case UUID；删除策略为 RESTRICT';

COMMENT ON COLUMN validation_review_events.tenant_key IS '与 case 一致的服务端租户隔离键';

COMMENT ON COLUMN validation_review_events.event_type IS 'accepted transition 事件类型：ACKNOWLEDGED、ESCALATED、RESOLVED、CLOSED';

COMMENT ON COLUMN validation_review_events.from_state IS 'accepted transition 前状态；不保存非法流转尝试';

COMMENT ON COLUMN validation_review_events.to_state IS '被接受的合法状态迁移结果，不包含批准、授权或可交易语义。';

COMMENT ON COLUMN validation_review_events.case_version IS 'accepted transition 后的 case 乐观锁版本';

COMMENT ON COLUMN validation_review_events.actor_id IS '执行 accepted transition 的本地用户，引用 users.id';

COMMENT ON COLUMN validation_review_events.idempotency_key IS 'case 内 transition 幂等键；同 case 唯一，跨 case 可复用';

COMMENT ON COLUMN validation_review_events.request_hash IS '规范化 transition 请求摘要；用于识别幂等键重用，不保存请求原文';

COMMENT ON COLUMN validation_review_events.request_id IS '可空业务请求 ID；不保存原始请求体';

COMMENT ON COLUMN validation_review_events.trace_id IS '本地审计链 trace ID；不承载 credential 或 private payload';

COMMENT ON COLUMN validation_review_events.metadata IS '脱敏 JSONB metadata；禁止保存 credential、账户余额、真实订单、交易授权或 private request/response';

COMMENT ON COLUMN validation_review_events.created_at IS 'accepted transition event 追加时间；事件按 append-only 使用';

COMMENT ON CONSTRAINT uq_kill_switch_events_scope_version ON kill_switch_events IS '每个 scope/version 最多一个状态变化事件。';

COMMENT ON INDEX idx_backtest_runs_dataset_snapshot_id IS '支持 datasetId -> backtest run snapshot -> publish 的 admission revision reverse mapping';

COMMENT ON INDEX uq_controlled_execution_lease_intents_global_cancel IS '全部lease合计最多一个CANCEL intent。';

COMMENT ON INDEX uq_controlled_execution_lease_intents_global_place IS '全部lease合计最多一个PLACE intent，保证PLACE total<=1。';

COMMENT ON INDEX uq_controlled_execution_leases_predecessor_successor IS '每个terminal predecessor最多一个successor，禁止lineage分叉。';

COMMENT ON INDEX uq_controlled_execution_leases_recovery_decision IS '每个append-only regeneration decision最多物化一个successor lease。';

COMMENT ON INDEX uq_controlled_execution_leases_single_open IS '全局最多一个CREATED/ACTIVE/CONSUMED lease。';

COMMENT ON INDEX uq_controlled_execution_leases_single_origin IS '只允许一个起始租约；后续租约必须形成不可分叉的 lineage。';

COMMENT ON INDEX uq_strategy_run_window_admission IS '同策略、账户、计划和逻辑窗口至多一个StrategyRun；终结和恢复不释放此身份。';

COMMENT ON TRIGGER trg_backtest_publish_artifact_locator_immutable ON backtest_publish_records IS '数据库层保护已绑定的 artifact/manifest storage key 不可清空或重绑';

COMMENT ON TRIGGER trg_market_snapshot_insert ON execution_prerequisite_observations IS 'MARKET_SNAPSHOT insert-time source、instrument 与 digest hard gate。';

COMMENT ON CONSTRAINT fk_exchange_accounts_canonical_legacy_account ON exchange_accounts IS 'canonical legacy bridge必须引用现有accounts行且禁止级联删除。';

COMMENT ON CONSTRAINT fk_kill_switch_events_scope ON kill_switch_events IS '安全状态存在时才能追加事件，且禁止级联删除安全证据。';

-- 默认保持全局交易关闭；初始化事件与当前控制事实具有相同身份。
INSERT INTO roles (id, role_code, description) VALUES
    (1, 'ADMIN', 'System administrator'),
    (2, 'OPERATOR', 'Operations user'),
    (3, 'VIEWER', 'Read-only user');
SELECT setval('roles_id_seq', 3, true);

INSERT INTO kill_switch_states(scope,status,version,reason_code,source,updated_by,trace_id)
VALUES('GLOBAL_TRADING','ENGAGED',1,'DEFAULT_SAFE_BOOTSTRAP','FLYWAY_MIGRATION','SYSTEM','baseline-bootstrap');
INSERT INTO kill_switch_events(id,scope,from_status,to_status,state_version,reason_code,source,actor_id,trace_id,occurred_at)
SELECT '00000000-0000-0000-0000-000000000001'::uuid,scope,NULL,status,version,reason_code,source,updated_by,trace_id,updated_at
FROM kill_switch_states WHERE scope='GLOBAL_TRADING';

INSERT INTO strategy_run_recovery_scan_cursor(cursor_id) VALUES(1);

-- 所有调度入口默认关闭，不产生运行身份、游标消费或外部副作用。
INSERT INTO scheduled_job_controls(job_key,enabled,fixed_delay_ms) VALUES
    ('BINANCE_RECONCILIATION',FALSE,5000),
    ('CONTINUOUS_SIM_POLL',FALSE,300000),
    ('LEDGER_RECONCILIATION',FALSE,30000),
    ('OKX_RECONCILIATION',FALSE,5000),
    ('OKX_RECOVERY',FALSE,15000),
    ('PAPER_MATCHING',FALSE,2000),
    ('STRATEGY_RECOVERY',FALSE,5000),
    ('VALIDATION_EVIDENCE_REFRESH',FALSE,300000);
