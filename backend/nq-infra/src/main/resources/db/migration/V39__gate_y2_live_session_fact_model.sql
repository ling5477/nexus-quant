-- GateY-2：LIVE control-plane fact model。该 migration 不启用 LIVE，也不实现交易所调用。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE TABLE risk_limit_sets (
    risk_limit_set_id UUID PRIMARY KEY,
    digest_schema_version VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    effective_scope VARCHAR(64) NOT NULL,
    quote_currency VARCHAR(16) NOT NULL,
    capital_cap NUMERIC(38,8) NOT NULL,
    max_order_notional NUMERIC(38,8) NOT NULL,
    max_symbol_position_notional NUMERIC(38,8) NOT NULL,
    max_daily_realized_loss NUMERIC(38,8) NOT NULL,
    max_daily_total_loss NUMERIC(38,8) NOT NULL,
    max_open_orders INTEGER NOT NULL,
    max_intraday_orders INTEGER NOT NULL,
    symbol_allowlist TEXT[] NOT NULL,
    order_type_allowlist TEXT[] NOT NULL,
    max_session_duration_seconds INTEGER NOT NULL,
    spread_limit_bps NUMERIC(18,8) NOT NULL,
    slippage_limit_bps NUMERIC(18,8) NOT NULL,
    max_market_data_age_ms INTEGER NOT NULL,
    min_data_coverage_bps INTEGER NOT NULL,
    required_data_source VARCHAR(32) NOT NULL,
    data_quality_action VARCHAR(16) NOT NULL,
    canonical_digest VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_risk_limit_sets_created_by FOREIGN KEY (created_by) REFERENCES users(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uq_risk_limit_sets_scope_version UNIQUE (effective_scope, version),
    CONSTRAINT uq_risk_limit_sets_digest UNIQUE (canonical_digest),
    CONSTRAINT chk_risk_limit_sets_schema CHECK (digest_schema_version = 'risk-limit-set.v1'),
    CONSTRAINT chk_risk_limit_sets_scope CHECK (effective_scope = 'LIVE_SESSION_OKX_SPOT'),
    CONSTRAINT chk_risk_limit_sets_quote CHECK (quote_currency = 'USDT'),
    CONSTRAINT chk_risk_limit_sets_version CHECK (version > 0),
    CONSTRAINT chk_risk_limit_sets_amounts CHECK (
        capital_cap > 0 AND capital_cap <= 10000.00000000
        AND max_order_notional > 0 AND max_order_notional <= capital_cap
        AND max_order_notional <= 1000.00000000
        AND max_symbol_position_notional > 0 AND max_symbol_position_notional <= capital_cap
        AND max_daily_realized_loss > 0 AND max_daily_realized_loss <= capital_cap
        AND max_daily_total_loss >= max_daily_realized_loss AND max_daily_total_loss <= capital_cap
    ),
    CONSTRAINT chk_risk_limit_sets_counts CHECK (
        max_open_orders BETWEEN 1 AND 20
        AND max_intraday_orders BETWEEN max_open_orders AND 200
        AND max_session_duration_seconds BETWEEN 60 AND 14400
    ),
    CONSTRAINT chk_risk_limit_sets_market_data CHECK (
        spread_limit_bps BETWEEN 0 AND 1000.00000000
        AND slippage_limit_bps BETWEEN 0 AND 1000.00000000
        AND max_market_data_age_ms BETWEEN 1 AND 5000
        AND min_data_coverage_bps BETWEEN 1 AND 10000
        AND required_data_source = 'OKX_PRIMARY'
        AND data_quality_action = 'BLOCK'
    ),
    CONSTRAINT chk_risk_limit_sets_symbols CHECK (cardinality(symbol_allowlist) BETWEEN 1 AND 2),
    CONSTRAINT chk_risk_limit_sets_order_types CHECK (order_type_allowlist = ARRAY['LIMIT']::TEXT[]),
    CONSTRAINT chk_risk_limit_sets_digest CHECK (canonical_digest ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_risk_limit_sets_scope_version ON risk_limit_sets(effective_scope, version DESC);

CREATE TABLE live_sessions (
    session_id UUID PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    exchange_account_id BIGINT NOT NULL,
    venue VARCHAR(32) NOT NULL,
    strategy_release_id VARCHAR(128) NOT NULL,
    release_digest VARCHAR(64) NOT NULL,
    release_admission_revision BIGINT NOT NULL,
    risk_limit_set_id UUID NOT NULL,
    risk_limit_set_digest VARCHAR(64) NOT NULL,
    credential_reference BIGINT NOT NULL,
    symbol_allowlist TEXT[] NOT NULL,
    capital_cap NUMERIC(38,8) NOT NULL,
    execution_window_start TIMESTAMPTZ NOT NULL,
    execution_window_end TIMESTAMPTZ NOT NULL,
    state VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    approval_scope_hash VARCHAR(64) NOT NULL,
    approval_scope_schema_version VARCHAR(64) NOT NULL,
    next_event_sequence BIGINT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_live_sessions_owner FOREIGN KEY (owner_id) REFERENCES users(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_live_sessions_account FOREIGN KEY (exchange_account_id)
        REFERENCES exchange_accounts(exchange_account_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_live_sessions_release FOREIGN KEY (strategy_release_id)
        REFERENCES strategy_release_admission_state(publish_record_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_live_sessions_risk_set FOREIGN KEY (risk_limit_set_id)
        REFERENCES risk_limit_sets(risk_limit_set_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_live_sessions_credential FOREIGN KEY (credential_reference)
        REFERENCES exchange_account_credentials(credential_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_live_sessions_created_by FOREIGN KEY (created_by) REFERENCES users(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_live_sessions_venue CHECK (venue = 'OKX_SPOT'),
    CONSTRAINT chk_live_sessions_digests CHECK (
        release_digest ~ '^[0-9a-f]{64}$'
        AND risk_limit_set_digest ~ '^[0-9a-f]{64}$'
        AND approval_scope_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_live_sessions_revision_version CHECK (
        release_admission_revision > 0 AND version > 0 AND next_event_sequence > 0
    ),
    CONSTRAINT chk_live_sessions_scope_schema CHECK (approval_scope_schema_version = 'approval-scope.v1'),
    CONSTRAINT chk_live_sessions_symbols CHECK (cardinality(symbol_allowlist) BETWEEN 1 AND 2),
    CONSTRAINT chk_live_sessions_capital CHECK (capital_cap > 0),
    CONSTRAINT chk_live_sessions_window CHECK (execution_window_end > execution_window_start),
    CONSTRAINT chk_live_sessions_state CHECK (state IN (
        'APPROVAL_PENDING','APPROVED','LIVE_WARMUP','LIVE_ACTIVE','LIVE_PAUSED','LIVE_STOPPED',
        'LIVE_RECONCILING','RECONCILIATION_BLOCKED','REJECTED','FAILED','KILLED','LIVE_RECONCILED'
    ))
);

CREATE UNIQUE INDEX uq_live_sessions_single_non_terminal
    ON live_sessions(exchange_account_id, venue)
    WHERE state IN ('APPROVAL_PENDING','APPROVED','LIVE_WARMUP','LIVE_ACTIVE','LIVE_PAUSED',
                    'LIVE_STOPPED','LIVE_RECONCILING','RECONCILIATION_BLOCKED');
CREATE INDEX idx_live_sessions_owner_created ON live_sessions(owner_id, created_at DESC);
CREATE INDEX idx_live_sessions_account_state_updated ON live_sessions(exchange_account_id, state, updated_at DESC);
CREATE INDEX idx_live_sessions_release ON live_sessions(strategy_release_id);
CREATE INDEX idx_live_sessions_risk_set ON live_sessions(risk_limit_set_id);

CREATE TABLE live_session_events (
    event_id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    sequence_no BIGINT NOT NULL,
    from_state VARCHAR(32),
    to_state VARCHAR(32) NOT NULL,
    command VARCHAR(64) NOT NULL,
    actor_id BIGINT,
    request_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    reason_code VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_payload_hash VARCHAR(64) NOT NULL,
    command_payload_schema_version VARCHAR(64) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_live_session_events_session FOREIGN KEY (session_id) REFERENCES live_sessions(session_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_live_session_events_actor FOREIGN KEY (actor_id) REFERENCES users(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uq_live_session_events_sequence UNIQUE (session_id, sequence_no),
    CONSTRAINT chk_live_session_events_sequence CHECK (sequence_no > 0),
    CONSTRAINT chk_live_session_events_text CHECK (
        btrim(command) <> '' AND btrim(request_id) <> '' AND btrim(trace_id) <> ''
        AND btrim(reason_code) <> '' AND btrim(idempotency_key) <> ''
    ),
    CONSTRAINT chk_live_session_events_payload CHECK (
        command_payload_hash ~ '^[0-9a-f]{64}$'
        AND command_payload_schema_version = 'live-session-command.v1'
    ),
    CONSTRAINT chk_live_session_events_metadata CHECK (
        jsonb_typeof(metadata) = 'object' AND pg_column_size(metadata) <= 8192
    )
);

CREATE UNIQUE INDEX uq_live_session_events_idempotency
    ON live_session_events(session_id, command, COALESCE(actor_id, 0), idempotency_key);
CREATE INDEX idx_live_session_events_timeline ON live_session_events(session_id, sequence_no);
CREATE INDEX idx_live_session_events_trace ON live_session_events(trace_id);

CREATE TABLE operator_approvals (
    approval_id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    scope_hash VARCHAR(64) NOT NULL,
    release_digest VARCHAR(64) NOT NULL,
    risk_limit_set_digest VARCHAR(64) NOT NULL,
    approver_id BIGINT NOT NULL,
    approver_role VARCHAR(64) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reason TEXT NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_operator_approvals_session FOREIGN KEY (session_id) REFERENCES live_sessions(session_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_operator_approvals_approver FOREIGN KEY (approver_id) REFERENCES users(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_operator_approvals_digests CHECK (
        scope_hash ~ '^[0-9a-f]{64}$' AND release_digest ~ '^[0-9a-f]{64}$'
        AND risk_limit_set_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_operator_approvals_role CHECK (approver_role = 'LIVE_APPROVER'),
    CONSTRAINT chk_operator_approvals_decision CHECK (decision IN ('APPROVED','REJECTED')),
    CONSTRAINT chk_operator_approvals_reason CHECK (btrim(reason) <> '' AND length(reason) <= 1024),
    CONSTRAINT chk_operator_approvals_expiry CHECK (expires_at > approved_at)
);

CREATE INDEX idx_operator_approvals_session_time ON operator_approvals(session_id, approved_at DESC);
CREATE INDEX idx_operator_approvals_approver_time ON operator_approvals(approver_id, approved_at DESC);
CREATE INDEX idx_operator_approvals_active_expiry ON operator_approvals(expires_at)
    WHERE decision = 'APPROVED';

CREATE TABLE execution_intents (
    intent_id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    sequence BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    side VARCHAR(8),
    order_type VARCHAR(16),
    quantity NUMERIC(38,8),
    limit_price NUMERIC(38,8),
    payload_hash_schema_version VARCHAR(64) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    client_order_id VARCHAR(128) NOT NULL,
    local_order_id VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    claimed_by VARCHAR(128),
    claim_token UUID,
    claimed_at TIMESTAMPTZ,
    lease_expires_at TIMESTAMPTZ,
    send_started_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_execution_intents_session FOREIGN KEY (session_id) REFERENCES live_sessions(session_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_execution_intents_order FOREIGN KEY (local_order_id) REFERENCES orders(order_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uq_execution_intents_session_sequence UNIQUE (session_id, sequence),
    CONSTRAINT uq_execution_intents_business_id UNIQUE (intent_id),
    CONSTRAINT chk_execution_intents_sequence CHECK (sequence > 0),
    CONSTRAINT chk_execution_intents_action CHECK (action IN ('PLACE','CANCEL')),
    CONSTRAINT chk_execution_intents_action_fields CHECK (
        (action = 'PLACE' AND side IN ('BUY','SELL') AND order_type = 'LIMIT'
            AND quantity > 0 AND limit_price > 0)
        OR (action = 'CANCEL' AND side IS NULL AND order_type IS NULL
            AND quantity IS NULL AND limit_price IS NULL)
    ),
    CONSTRAINT chk_execution_intents_payload CHECK (
        payload_hash_schema_version = 'execution-intent-payload.v1'
        AND payload_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_execution_intents_text CHECK (
        btrim(symbol) <> '' AND btrim(client_order_id) <> '' AND btrim(local_order_id) <> ''
    ),
    CONSTRAINT chk_execution_intents_state CHECK (state IN (
        'CREATED','CLAIMED','SEND_STARTED','SEND_SUCCEEDED','UNKNOWN','FAILED','CANCELLED','RECONCILED'
    )),
    CONSTRAINT chk_execution_intents_version CHECK (version > 0),
    CONSTRAINT chk_execution_intents_claim CHECK (
        (state IN ('CREATED','CANCELLED')
            AND claimed_by IS NULL AND claim_token IS NULL
            AND claimed_at IS NULL AND lease_expires_at IS NULL)
        OR (state IN ('CLAIMED','SEND_STARTED','SEND_SUCCEEDED','UNKNOWN','FAILED','RECONCILED')
            AND claimed_by IS NOT NULL AND claim_token IS NOT NULL AND claimed_at IS NOT NULL
            AND lease_expires_at > claimed_at)
    ),
    CONSTRAINT chk_execution_intents_send_started CHECK (
        (state IN ('SEND_STARTED','SEND_SUCCEEDED','UNKNOWN','RECONCILED') AND send_started_at IS NOT NULL)
        OR (state = 'FAILED' AND send_started_at IS NOT NULL)
        OR (state IN ('CREATED','CLAIMED','CANCELLED') AND send_started_at IS NULL)
    )
);

CREATE UNIQUE INDEX uq_execution_intents_place_client_order
    ON execution_intents(session_id, client_order_id) WHERE action = 'PLACE';
CREATE INDEX idx_execution_intents_claim
    ON execution_intents(state, lease_expires_at, created_at)
    WHERE state IN ('CREATED','CLAIMED') AND send_started_at IS NULL;
CREATE INDEX idx_execution_intents_session_state ON execution_intents(session_id, state, created_at);
CREATE INDEX idx_execution_intents_local_order ON execution_intents(local_order_id);
CREATE INDEX idx_execution_intents_client_order ON execution_intents(client_order_id);

CREATE TABLE execution_receipts (
    receipt_id UUID PRIMARY KEY,
    intent_id UUID NOT NULL,
    attempt_no INTEGER NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    exchange_request_id VARCHAR(128),
    exchange_order_id VARCHAR(128),
    error_category VARCHAR(64),
    error_code VARCHAR(128),
    received_at TIMESTAMPTZ NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    payload_digest_schema_version VARCHAR(64) NOT NULL,
    CONSTRAINT fk_execution_receipts_intent FOREIGN KEY (intent_id) REFERENCES execution_intents(intent_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uq_execution_receipts_attempt UNIQUE (intent_id, attempt_no),
    CONSTRAINT chk_execution_receipts_attempt CHECK (attempt_no > 0),
    CONSTRAINT chk_execution_receipts_outcome CHECK (outcome IN (
        'ACKNOWLEDGED','REJECTED','TIMEOUT','TRANSPORT_ERROR','UNKNOWN',
        'QUERY_CONFIRMED','QUERY_NOT_FOUND'
    )),
    CONSTRAINT chk_execution_receipts_digest CHECK (
        payload_digest ~ '^[0-9a-f]{64}$'
        AND payload_digest_schema_version = 'execution-receipt-envelope.v1'
    )
);

CREATE INDEX idx_execution_receipts_intent_time ON execution_receipts(intent_id, received_at, receipt_id);
CREATE INDEX idx_execution_receipts_exchange_order ON execution_receipts(exchange_order_id)
    WHERE exchange_order_id IS NOT NULL;
CREATE INDEX idx_execution_receipts_outcome_time ON execution_receipts(outcome, received_at DESC);

CREATE FUNCTION gate_y2_require_canonical_symbol_array(p_symbols TEXT[])
    RETURNS BOOLEAN LANGUAGE plpgsql IMMUTABLE AS $$
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
$$;

ALTER TABLE risk_limit_sets ADD CONSTRAINT chk_risk_limit_sets_canonical_symbols
    CHECK (gate_y2_require_canonical_symbol_array(symbol_allowlist));
ALTER TABLE live_sessions ADD CONSTRAINT chk_live_sessions_canonical_symbols
    CHECK (gate_y2_require_canonical_symbol_array(symbol_allowlist));

CREATE FUNCTION gate_y2_reject_fact_mutation()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = TG_TABLE_NAME || ' is append-only or immutable';
END;
$$;

CREATE TRIGGER trg_risk_limit_sets_immutable BEFORE UPDATE OR DELETE ON risk_limit_sets
    FOR EACH ROW EXECUTE FUNCTION gate_y2_reject_fact_mutation();
CREATE TRIGGER trg_live_session_events_append_only BEFORE UPDATE OR DELETE ON live_session_events
    FOR EACH ROW EXECUTE FUNCTION gate_y2_reject_fact_mutation();
CREATE TRIGGER trg_operator_approvals_append_only BEFORE UPDATE OR DELETE ON operator_approvals
    FOR EACH ROW EXECUTE FUNCTION gate_y2_reject_fact_mutation();
CREATE TRIGGER trg_execution_receipts_append_only BEFORE UPDATE OR DELETE ON execution_receipts
    FOR EACH ROW EXECUTE FUNCTION gate_y2_reject_fact_mutation();

CREATE FUNCTION gate_y2_guard_live_session_update()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
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
        AND NOT v_scope_changed
        AND NEW.approval_scope_hash = OLD.approval_scope_hash;
    IF v_only_sequence_changed THEN RETURN NEW; END IF;

    IF NEW.version <> OLD.version + 1 OR NEW.next_event_sequence <> OLD.next_event_sequence THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='live session version or event sequence is invalid';
    END IF;
    -- GateY-2 没有 scope-mutation application command。先在数据库层 fail-closed，
    -- 避免普通 SQL 伪造 canonical hash；后续 Gate 若运行化该命令，必须以可验证 hash + event 原子替换此 guard。
    IF v_scope_changed THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='live session scope is immutable in GateY-2';
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

CREATE FUNCTION gate_y2_guard_live_session_insert()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.state <> 'APPROVAL_PENDING' OR NEW.version <> 1 OR NEW.next_event_sequence <> 1 THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='new live session must start approval pending';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_live_sessions_insert_guard BEFORE INSERT ON live_sessions
    FOR EACH ROW EXECUTE FUNCTION gate_y2_guard_live_session_insert();

CREATE TRIGGER trg_live_sessions_guard BEFORE UPDATE OR DELETE ON live_sessions
    FOR EACH ROW EXECUTE FUNCTION gate_y2_guard_live_session_update();

CREATE FUNCTION gate_y2_guard_execution_intent_update()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
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

CREATE FUNCTION gate_y2_guard_execution_intent_insert()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.state <> 'CREATED' OR NEW.version <> 1 OR NEW.send_started_at IS NOT NULL
        OR NEW.claimed_by IS NOT NULL OR NEW.claim_token IS NOT NULL
        OR NEW.claimed_at IS NOT NULL OR NEW.lease_expires_at IS NOT NULL THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='new execution intent must start unclaimed';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_execution_intents_insert_guard BEFORE INSERT ON execution_intents
    FOR EACH ROW EXECUTE FUNCTION gate_y2_guard_execution_intent_insert();

CREATE TRIGGER trg_execution_intents_guard BEFORE UPDATE OR DELETE ON execution_intents
    FOR EACH ROW EXECUTE FUNCTION gate_y2_guard_execution_intent_update();

COMMENT ON TABLE risk_limit_sets IS 'GateY LIVE 会话不可变风险规则定义；不是运行期 risk decision，不保存凭证、余额或行情原文。';
COMMENT ON TABLE live_sessions IS 'GateY LIVE control-plane 会话聚合；记录候选控制事实，不表示 LIVE 或真实交易已获授权。';
COMMENT ON TABLE live_session_events IS 'LiveSession 有序 append-only 事件；不是通用审计、订单或交易所回执主事实。';
COMMENT ON TABLE operator_approvals IS '不可变人工审批事实；审批不等于交易所权限、kill switch 释放或 LIVE 授权。';
COMMENT ON TABLE execution_intents IS 'GateY-3 预留的外部变更意图事实；GateY-2 不实现 worker 或 dispatch。';
COMMENT ON TABLE execution_receipts IS 'GateY-3 预留的脱敏 append-only 外部回执；禁止保存 raw request/response、header、签名或凭证。';

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

COMMENT ON COLUMN execution_intents.intent_id IS '未来外部动作唯一业务键。';
COMMENT ON COLUMN execution_intents.session_id IS '所属 LiveSession。';
COMMENT ON COLUMN execution_intents.sequence IS 'session 内意图序号。';
COMMENT ON COLUMN execution_intents.action IS 'PLACE 或 CANCEL；GateY-2 仅落 schema。';
COMMENT ON COLUMN execution_intents.symbol IS '目标内部 symbol。';
COMMENT ON COLUMN execution_intents.side IS 'PLACE 的 BUY/SELL；CANCEL 必须为空。';
COMMENT ON COLUMN execution_intents.order_type IS 'PLACE 首版 LIMIT；CANCEL 必须为空。';
COMMENT ON COLUMN execution_intents.quantity IS 'PLACE 数量 NUMERIC(38,8)；CANCEL 为空。';
COMMENT ON COLUMN execution_intents.limit_price IS 'PLACE 限价 NUMERIC(38,8)；CANCEL 为空。';
COMMENT ON COLUMN execution_intents.payload_hash_schema_version IS '意图 canonical schema。';
COMMENT ON COLUMN execution_intents.payload_hash IS '不可变意图 payload SHA-256。';
COMMENT ON COLUMN execution_intents.client_order_id IS '稳定 client order identity；不产生第二订单事实。';
COMMENT ON COLUMN execution_intents.local_order_id IS '既有 orders.order_id，不可后绑或改绑。';
COMMENT ON COLUMN execution_intents.state IS '未来 worker claim/send/reconcile 状态；GateY-2 不运行。';
COMMENT ON COLUMN execution_intents.version IS 'optimistic version。';
COMMENT ON COLUMN execution_intents.claimed_by IS 'bounded worker 标识；GateY-2 不产生。';
COMMENT ON COLUMN execution_intents.claim_token IS '一次 claim token；GateY-2 不产生。';
COMMENT ON COLUMN execution_intents.claimed_at IS 'claim 时间；GateY-2 不产生。';
COMMENT ON COLUMN execution_intents.lease_expires_at IS 'bounded lease 到期时间；GateY-2 不产生。';
COMMENT ON COLUMN execution_intents.send_started_at IS '首次网络发送前原子绑定；绑定后不可修改。';
COMMENT ON COLUMN execution_intents.created_at IS '意图创建时间。';

COMMENT ON COLUMN execution_receipts.receipt_id IS '不可复用 append-only 回执 UUID。';
COMMENT ON COLUMN execution_receipts.intent_id IS '所属 execution intent。';
COMMENT ON COLUMN execution_receipts.attempt_no IS 'intent 内网络 attempt 正整数序号。';
COMMENT ON COLUMN execution_receipts.outcome IS '归一化、脱敏回执结果；QUERY_* 仅表示只读对账证据。';
COMMENT ON COLUMN execution_receipts.exchange_request_id IS '可空脱敏交易所 request identity。';
COMMENT ON COLUMN execution_receipts.exchange_order_id IS '可空交易所订单 identity；不承载订单生命周期。';
COMMENT ON COLUMN execution_receipts.error_category IS '可空脱敏错误类别。';
COMMENT ON COLUMN execution_receipts.error_code IS '可空脱敏错误码。';
COMMENT ON COLUMN execution_receipts.received_at IS '回执观察时间。';
COMMENT ON COLUMN execution_receipts.payload_digest IS '允许字段 normalized envelope SHA-256。';
COMMENT ON COLUMN execution_receipts.payload_digest_schema_version IS '回执 canonical schema。';

COMMENT ON FUNCTION gate_y2_require_canonical_symbol_array(TEXT[]) IS '验证 GateY symbol 大写、BASE-USDT、排序和去重合同。';
COMMENT ON FUNCTION gate_y2_reject_fact_mutation() IS '数据库层拒绝 GateY immutable/append-only fact 的 UPDATE/DELETE。';
COMMENT ON FUNCTION gate_y2_guard_live_session_update() IS '保护 LiveSession identity、version、事件序列与合法状态迁移；GateY-2 禁止持久化 scope mutation。';
COMMENT ON FUNCTION gate_y2_guard_execution_intent_update() IS '保护 ExecutionIntent immutable business facts、version 和状态迁移；GateY-2 不运行 worker。';
