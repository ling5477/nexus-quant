-- GateY minimal pilot：显式 operator authority 与 LiveSession 互斥 authority model。
-- 本 migration 不物化任何生产 authority、不启用 LIVE，也不调用交易所。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE TABLE operator_pilot_authorities (
    authority_id UUID PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    exchange_account_id BIGINT NOT NULL,
    credential_reference_id BIGINT NOT NULL,
    instrument VARCHAR(64) NOT NULL,
    side VARCHAR(8) NOT NULL,
    order_type VARCHAR(16) NOT NULL,
    max_notional NUMERIC(38,8) NOT NULL,
    max_place_count INTEGER NOT NULL,
    max_cancel_count INTEGER NOT NULL,
    transfer_allowed BOOLEAN NOT NULL,
    withdraw_allowed BOOLEAN NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    canonical_digest VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    closed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_operator_pilot_authorities_owner FOREIGN KEY (owner_user_id)
        REFERENCES users(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_operator_pilot_authorities_account FOREIGN KEY (exchange_account_id)
        REFERENCES exchange_accounts(exchange_account_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_operator_pilot_authorities_credential FOREIGN KEY (credential_reference_id)
        REFERENCES exchange_account_credentials(credential_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_operator_pilot_authorities_creator FOREIGN KEY (created_by)
        REFERENCES users(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uq_operator_pilot_authorities_digest UNIQUE (canonical_digest),
    CONSTRAINT chk_operator_pilot_authorities_owner CHECK (owner_user_id = created_by),
    CONSTRAINT chk_operator_pilot_authorities_instrument CHECK (instrument ~ '^[A-Z0-9]{2,20}-USDT$'),
    CONSTRAINT chk_operator_pilot_authorities_side CHECK (side IN ('BUY','SELL')),
    CONSTRAINT chk_operator_pilot_authorities_order_type CHECK (order_type = 'LIMIT'),
    CONSTRAINT chk_operator_pilot_authorities_notional CHECK (
        max_notional > 0 AND max_notional <= 10.00000000
    ),
    CONSTRAINT chk_operator_pilot_authorities_counts CHECK (
        max_place_count = 1 AND max_cancel_count = 1
    ),
    CONSTRAINT chk_operator_pilot_authorities_funding CHECK (
        transfer_allowed = FALSE AND withdraw_allowed = FALSE
    ),
    CONSTRAINT chk_operator_pilot_authorities_window CHECK (
        expires_at > valid_from AND valid_from >= created_at
    ),
    CONSTRAINT chk_operator_pilot_authorities_status CHECK (status IN ('ACTIVE','CLOSED','EXPIRED')),
    CONSTRAINT chk_operator_pilot_authorities_digest CHECK (canonical_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_operator_pilot_authorities_version CHECK (version > 0),
    CONSTRAINT chk_operator_pilot_authorities_lifecycle CHECK (
        (status = 'ACTIVE' AND closed_at IS NULL AND updated_at = created_at)
        OR (status IN ('CLOSED','EXPIRED') AND closed_at IS NOT NULL
            AND updated_at = closed_at AND closed_at >= created_at)
    )
);

CREATE UNIQUE INDEX uq_operator_pilot_authorities_single_active
    ON operator_pilot_authorities ((1)) WHERE status = 'ACTIVE';
CREATE INDEX idx_operator_pilot_authorities_scope
    ON operator_pilot_authorities (owner_user_id, exchange_account_id, credential_reference_id, status, expires_at);

CREATE FUNCTION gate_y44_operator_pilot_authority_digest(
    p_authority_id UUID,
    p_owner_user_id BIGINT,
    p_exchange_account_id BIGINT,
    p_credential_reference_id BIGINT,
    p_instrument TEXT,
    p_side TEXT,
    p_order_type TEXT,
    p_max_notional NUMERIC,
    p_max_place_count INTEGER,
    p_max_cancel_count INTEGER,
    p_transfer_allowed BOOLEAN,
    p_withdraw_allowed BOOLEAN,
    p_valid_from TIMESTAMPTZ,
    p_expires_at TIMESTAMPTZ,
    p_created_by BIGINT,
    p_created_at TIMESTAMPTZ
) RETURNS TEXT LANGUAGE SQL IMMUTABLE STRICT AS $$
    SELECT encode(digest(convert_to(
        '{"schemaVersion":"operator-pilot-authority.v1"' ||
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
        ',"validFrom":' || gate_y6d_instant_canonical(p_valid_from) ||
        ',"expiresAt":' || gate_y6d_instant_canonical(p_expires_at) ||
        ',"createdBy":' || p_created_by::TEXT ||
        ',"createdAt":' || gate_y6d_instant_canonical(p_created_at) || '}',
        'UTF8'), 'sha256'), 'hex')
$$;

CREATE FUNCTION gate_y44_guard_operator_pilot_authority_insert()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_reference_count INTEGER;
BEGIN
    IF NEW.status <> 'ACTIVE' OR NEW.version <> 1 OR NEW.closed_at IS NOT NULL
        OR NEW.updated_at <> NEW.created_at
        OR transaction_timestamp() < NEW.valid_from OR transaction_timestamp() >= NEW.expires_at
        OR NEW.canonical_digest IS DISTINCT FROM gate_y44_operator_pilot_authority_digest(
            NEW.authority_id, NEW.owner_user_id, NEW.exchange_account_id,
            NEW.credential_reference_id, NEW.instrument, NEW.side, NEW.order_type,
            NEW.max_notional, NEW.max_place_count, NEW.max_cancel_count,
            NEW.transfer_allowed, NEW.withdraw_allowed, NEW.valid_from, NEW.expires_at,
            NEW.created_by, NEW.created_at) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='operator pilot authority is not canonical';
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
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='operator pilot authority account reference is invalid';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION gate_y44_guard_operator_pilot_authority_update()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='operator pilot authority cannot be deleted';
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
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='operator pilot authority scope is immutable';
    END IF;
    IF OLD.status <> 'ACTIVE' OR NEW.status NOT IN ('CLOSED','EXPIRED')
        OR NEW.version <> OLD.version + 1 OR NEW.updated_at < OLD.updated_at
        OR NEW.closed_at IS NULL OR NEW.updated_at <> NEW.closed_at
        OR NEW.closed_at < OLD.created_at
        OR (NEW.status = 'EXPIRED' AND NEW.closed_at < OLD.expires_at) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='operator pilot authority transition is invalid';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_operator_pilot_authorities_insert_guard
    BEFORE INSERT ON operator_pilot_authorities
    FOR EACH ROW EXECUTE FUNCTION gate_y44_guard_operator_pilot_authority_insert();
CREATE TRIGGER trg_operator_pilot_authorities_update_guard
    BEFORE UPDATE OR DELETE ON operator_pilot_authorities
    FOR EACH ROW EXECUTE FUNCTION gate_y44_guard_operator_pilot_authority_update();

ALTER TABLE live_sessions
    ADD COLUMN authority_type VARCHAR(32),
    ADD COLUMN operator_pilot_authority_id UUID,
    ADD COLUMN operator_pilot_authority_digest VARCHAR(64);

-- 仅为历史行补 authority discriminator；V39 update guard 不认识该新增列，故在同一事务内
-- 精确暂停并立即恢复这一张表的既有 guard，其他 trigger 与约束保持启用。
ALTER TABLE live_sessions DISABLE TRIGGER trg_live_sessions_guard;
UPDATE live_sessions SET authority_type = 'STRATEGY' WHERE authority_type IS NULL;
ALTER TABLE live_sessions ENABLE TRIGGER trg_live_sessions_guard;

ALTER TABLE live_sessions
    ALTER COLUMN authority_type SET NOT NULL,
    ALTER COLUMN strategy_release_id DROP NOT NULL,
    ALTER COLUMN release_digest DROP NOT NULL,
    ALTER COLUMN release_admission_revision DROP NOT NULL,
    ALTER COLUMN risk_limit_set_id DROP NOT NULL,
    ALTER COLUMN risk_limit_set_digest DROP NOT NULL,
    ADD CONSTRAINT fk_live_sessions_operator_pilot_authority FOREIGN KEY (operator_pilot_authority_id)
        REFERENCES operator_pilot_authorities(authority_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    DROP CONSTRAINT chk_live_sessions_digests,
    DROP CONSTRAINT chk_live_sessions_revision_version,
    DROP CONSTRAINT chk_live_sessions_scope_schema,
    ADD CONSTRAINT chk_live_sessions_authority_type CHECK (authority_type IN ('STRATEGY','OPERATOR_PILOT')),
    ADD CONSTRAINT chk_live_sessions_digests CHECK (
        approval_scope_hash ~ '^[0-9a-f]{64}$'
        AND (release_digest IS NULL OR release_digest ~ '^[0-9a-f]{64}$')
        AND (risk_limit_set_digest IS NULL OR risk_limit_set_digest ~ '^[0-9a-f]{64}$')
        AND (operator_pilot_authority_digest IS NULL
             OR operator_pilot_authority_digest ~ '^[0-9a-f]{64}$')
    ),
    ADD CONSTRAINT chk_live_sessions_revision_version CHECK (
        version > 0 AND next_event_sequence > 0
        AND (release_admission_revision IS NULL OR release_admission_revision > 0)
    ),
    ADD CONSTRAINT chk_live_sessions_authority_semantics CHECK (
        (authority_type = 'STRATEGY'
            AND strategy_release_id IS NOT NULL AND release_digest IS NOT NULL
            AND release_admission_revision IS NOT NULL AND risk_limit_set_id IS NOT NULL
            AND risk_limit_set_digest IS NOT NULL AND operator_pilot_authority_id IS NULL
            AND operator_pilot_authority_digest IS NULL
            AND approval_scope_schema_version = 'approval-scope.v1')
        OR (authority_type = 'OPERATOR_PILOT'
            AND strategy_release_id IS NULL AND release_digest IS NULL
            AND release_admission_revision IS NULL AND risk_limit_set_id IS NULL
            AND risk_limit_set_digest IS NULL AND operator_pilot_authority_id IS NOT NULL
            AND operator_pilot_authority_digest IS NOT NULL
            AND approval_scope_schema_version = 'approval-scope.operator-pilot.v1')
    );

CREATE INDEX idx_live_sessions_operator_pilot_authority
    ON live_sessions(operator_pilot_authority_id) WHERE operator_pilot_authority_id IS NOT NULL;

-- V43 的 NUMERIC(38,18) typmod 会把合法 8 位输入补齐到 18 位，scale() 因而错误拒绝正常值。
-- forward-only 改为比较 round(8)，仍拒绝第 9 位及之后的非零精度。
ALTER TABLE pilot_prerequisite_observations
    DROP CONSTRAINT chk_pilot_observation_variant;
ALTER TABLE pilot_prerequisite_observations
    ADD CONSTRAINT chk_pilot_observation_variant CHECK (
        (observation_type = 'INSTRUMENT_METADATA'
            AND observation_schema_version IN (
                'instrument-metadata-observation.v1', 'instrument-metadata-observation.v2')
            AND instrument_metadata_digest IS NOT NULL
            AND fee_schedule_digest IS NULL AND balance_snapshot_digest IS NULL
            AND clock_sync_observation_digest IS NULL AND market_snapshot_digest IS NULL
            AND market_instrument IS NULL AND best_ask IS NULL
            AND fee_tier IS NULL AND fee_evidence_class IS NULL
            AND maker_fee_rate IS NULL AND taker_fee_rate IS NULL
            AND fee_loss_treatment IS NULL AND balance_currency IS NULL AND available_balance IS NULL
            AND signed_timestamp_source IS NULL AND observed_skew_ms IS NULL)
        OR (observation_type = 'FEE_SCHEDULE'
            AND observation_schema_version = 'fee-schedule-observation.v1'
            AND instrument_metadata_digest IS NULL AND fee_schedule_digest IS NOT NULL
            AND balance_snapshot_digest IS NULL AND clock_sync_observation_digest IS NULL
            AND market_snapshot_digest IS NULL AND market_instrument IS NULL AND best_ask IS NULL
            AND btrim(fee_tier) <> '' AND fee_evidence_class IN ('OBSERVED_PRIVATE', 'ESTIMATED_PUBLIC')
            AND maker_fee_rate BETWEEN -1 AND 1 AND taker_fee_rate BETWEEN -1 AND 1
            AND fee_loss_treatment = 'INCLUDE_IN_DAILY_LOSS_AND_CAPITAL_USAGE'
            AND balance_currency IS NULL AND available_balance IS NULL
            AND signed_timestamp_source IS NULL AND observed_skew_ms IS NULL)
        OR (observation_type = 'BALANCE_SNAPSHOT'
            AND observation_schema_version = 'balance-snapshot-observation.v1'
            AND instrument_metadata_digest IS NULL AND fee_schedule_digest IS NULL
            AND balance_snapshot_digest IS NOT NULL AND clock_sync_observation_digest IS NULL
            AND market_snapshot_digest IS NULL AND market_instrument IS NULL AND best_ask IS NULL
            AND fee_tier IS NULL AND fee_evidence_class IS NULL
            AND maker_fee_rate IS NULL AND taker_fee_rate IS NULL AND fee_loss_treatment IS NULL
            AND balance_currency = 'USDT' AND available_balance >= 0
            AND signed_timestamp_source IS NULL AND observed_skew_ms IS NULL)
        OR (observation_type = 'CLOCK_SYNC'
            AND observation_schema_version = 'clock-sync-observation.v1'
            AND instrument_metadata_digest IS NULL AND fee_schedule_digest IS NULL
            AND balance_snapshot_digest IS NULL AND clock_sync_observation_digest IS NOT NULL
            AND market_snapshot_digest IS NULL AND market_instrument IS NULL AND best_ask IS NULL
            AND fee_tier IS NULL AND fee_evidence_class IS NULL
            AND maker_fee_rate IS NULL AND taker_fee_rate IS NULL AND fee_loss_treatment IS NULL
            AND balance_currency IS NULL AND available_balance IS NULL
            AND signed_timestamp_source = 'NTP_DISCIPLINED_SYSTEM_CLOCK'
            AND observed_skew_ms BETWEEN -1000 AND 1000)
        OR (observation_type = 'MARKET_SNAPSHOT'
            AND observation_schema_version = 'market-snapshot-observation.v1'
            AND market_snapshot_digest IS NOT NULL
            AND market_instrument ~ '^[A-Z0-9]{2,20}-USDT$'
            AND best_ask > 0 AND best_ask = round(best_ask, 8)
            AND instrument_metadata_digest IS NULL AND fee_schedule_digest IS NULL
            AND balance_snapshot_digest IS NULL AND clock_sync_observation_digest IS NULL
            AND fee_tier IS NULL AND fee_evidence_class IS NULL
            AND maker_fee_rate IS NULL AND taker_fee_rate IS NULL AND fee_loss_treatment IS NULL
            AND balance_currency IS NULL AND available_balance IS NULL
            AND signed_timestamp_source IS NULL AND observed_skew_ms IS NULL)
    );

CREATE OR REPLACE FUNCTION gate_y2_guard_live_session_insert()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_authority operator_pilot_authorities%ROWTYPE;
BEGIN
    IF NEW.state <> 'APPROVAL_PENDING' OR NEW.version <> 1 OR NEW.next_event_sequence <> 1 THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='new live session must start approval pending';
    END IF;
    IF NEW.authority_type = 'OPERATOR_PILOT' THEN
        SELECT * INTO v_authority FROM operator_pilot_authorities
        WHERE authority_id = NEW.operator_pilot_authority_id FOR KEY SHARE;
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
            OR NEW.operator_pilot_authority_digest <> v_authority.canonical_digest THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='operator pilot session exceeds explicit authority';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION gate_y2_guard_live_session_update()
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
        OR OLD.authority_type IS DISTINCT FROM NEW.authority_type
        OR OLD.operator_pilot_authority_id IS DISTINCT FROM NEW.operator_pilot_authority_id
        OR OLD.operator_pilot_authority_digest IS DISTINCT FROM NEW.operator_pilot_authority_digest
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
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='live session scope is immutable in GateY';
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

CREATE OR REPLACE FUNCTION gate_y6d_pilot_scope_canonical_payload(
    p_session_id UUID,
    p_instrument_metadata_digest TEXT,
    p_instrument_source_identity TEXT,
    p_instrument_source_schema_version TEXT,
    p_instrument_maximum_age_ms BIGINT,
    p_fee_schedule_digest TEXT,
    p_fee_tier TEXT,
    p_fee_evidence_class TEXT,
    p_fee_source_identity TEXT,
    p_fee_source_schema_version TEXT,
    p_fee_maximum_age_ms BIGINT,
    p_balance_source_identity TEXT,
    p_balance_source_schema_version TEXT,
    p_balance_maximum_age_ms BIGINT,
    p_clock_source_identity TEXT,
    p_clock_source_schema_version TEXT,
    p_clock_maximum_age_ms BIGINT,
    p_signed_timestamp_source TEXT,
    p_maximum_tolerated_skew_ms BIGINT,
    p_endpoint_policy_version TEXT,
    p_endpoint_policy_digest TEXT,
    p_provider_contract_identity TEXT,
    p_provider_artifact_digest TEXT,
    p_worker_identity TEXT,
    p_worker_release_digest TEXT
) RETURNS TEXT LANGUAGE SQL STABLE STRICT AS $$
    SELECT '{' ||
        CASE WHEN s.authority_type = 'STRATEGY'
            THEN '"schemaVersion":"pilot-scope.v1"'
                || ',"sessionId":' || to_json(s.session_id::TEXT)::TEXT
                || ',"ownerId":' || s.owner_id::TEXT
                || ',"exchangeAccountId":' || s.exchange_account_id::TEXT
                || ',"venue":' || to_json(s.venue)::TEXT
                || ',"strategyReleaseId":' || to_json(s.strategy_release_id)::TEXT
                || ',"releaseArtifactDigest":' || to_json(s.release_digest)::TEXT
                || ',"releaseAdmissionRevision":' || s.release_admission_revision::TEXT
                || ',"riskLimitSetId":' || to_json(s.risk_limit_set_id::TEXT)::TEXT
                || ',"riskLimitSetDigest":' || to_json(s.risk_limit_set_digest)::TEXT
            ELSE '"schemaVersion":"pilot-scope.operator-pilot.v1"'
                || ',"sessionId":' || to_json(s.session_id::TEXT)::TEXT
                || ',"ownerId":' || s.owner_id::TEXT
                || ',"exchangeAccountId":' || s.exchange_account_id::TEXT
                || ',"venue":' || to_json(s.venue)::TEXT
                || ',"authorityType":"OPERATOR_PILOT"'
                || ',"operatorPilotAuthorityId":' || to_json(s.operator_pilot_authority_id::TEXT)::TEXT
                || ',"operatorPilotAuthorityDigest":' || to_json(s.operator_pilot_authority_digest)::TEXT
        END ||
        ',"credentialReference":' || s.credential_reference::TEXT ||
        ',"symbolAllowlist":[' || (
            SELECT string_agg(to_json(symbol)::TEXT, ',' ORDER BY ordinal)
            FROM unnest(s.symbol_allowlist) WITH ORDINALITY symbols(symbol, ordinal)
        ) || ']' ||
        ',"capitalCap":' || to_json((s.capital_cap::NUMERIC(38,8))::TEXT)::TEXT ||
        ',"executionWindowStart":' || gate_y6d_instant_canonical(s.execution_window_start) ||
        ',"executionWindowEnd":' || gate_y6d_instant_canonical(s.execution_window_end) ||
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

CREATE OR REPLACE FUNCTION gate_y6d_guard_prerequisite_observation_insert()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_scope pilot_scope_bindings%ROWTYPE;
    v_quote_currency VARCHAR(16);
BEGIN
    SELECT * INTO v_scope FROM pilot_scope_bindings
    WHERE pilot_scope_id = NEW.pilot_scope_id FOR KEY SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE='23503', MESSAGE='pilot scope does not exist';
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
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='instrument observation is outside immutable pilot scope';
        END IF;
    ELSIF NEW.observation_type = 'FEE_SCHEDULE' THEN
        IF NEW.source_identity <> v_scope.fee_source_identity
            OR NEW.source_schema_version <> v_scope.fee_source_schema_version
            OR NEW.fee_schedule_digest <> v_scope.fee_schedule_digest
            OR NEW.fee_tier <> v_scope.fee_tier
            OR NEW.fee_evidence_class <> v_scope.fee_evidence_class THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='fee observation is outside immutable pilot scope';
        END IF;
    ELSIF NEW.observation_type = 'BALANCE_SNAPSHOT' THEN
        SELECT CASE WHEN session.authority_type = 'STRATEGY'
                    THEN risk.quote_currency ELSE split_part(authority.instrument, '-', 2) END
        INTO v_quote_currency
        FROM pilot_scope_bindings scope
        JOIN live_sessions session ON session.session_id = scope.session_id
        LEFT JOIN risk_limit_sets risk ON risk.risk_limit_set_id = session.risk_limit_set_id
        LEFT JOIN operator_pilot_authorities authority
          ON authority.authority_id = session.operator_pilot_authority_id
        WHERE scope.pilot_scope_id = NEW.pilot_scope_id;
        IF NEW.source_identity <> v_scope.balance_source_identity
            OR NEW.source_schema_version <> v_scope.balance_source_schema_version
            OR NEW.balance_currency <> v_quote_currency THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='balance observation is outside immutable pilot scope';
        END IF;
    ELSIF NEW.observation_type = 'CLOCK_SYNC' THEN
        IF NEW.source_identity <> v_scope.clock_source_identity
            OR NEW.source_schema_version <> v_scope.clock_source_schema_version
            OR NEW.signed_timestamp_source <> v_scope.signed_timestamp_source
            OR abs(NEW.observed_skew_ms) > v_scope.maximum_tolerated_skew_ms THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='clock observation is outside immutable pilot scope';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

ALTER TABLE pilot_execution_leases
    ADD COLUMN operator_pilot_authority_id UUID,
    ADD CONSTRAINT fk_pilot_execution_leases_operator_authority
        FOREIGN KEY (operator_pilot_authority_id) REFERENCES operator_pilot_authorities(authority_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

CREATE INDEX idx_pilot_execution_leases_operator_authority
    ON pilot_execution_leases(operator_pilot_authority_id)
    WHERE operator_pilot_authority_id IS NOT NULL;

CREATE OR REPLACE FUNCTION gate_y_minimal_pilot_guard_lease_update()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_legal BOOLEAN;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='pilot execution lease cannot be deleted';
    END IF;
    IF OLD.lease_id IS DISTINCT FROM NEW.lease_id
        OR OLD.live_session_id IS DISTINCT FROM NEW.live_session_id
        OR OLD.operator_pilot_authority_id IS DISTINCT FROM NEW.operator_pilot_authority_id
        OR OLD.binding_id IS DISTINCT FROM NEW.binding_id
        OR OLD.binding_digest IS DISTINCT FROM NEW.binding_digest
        OR OLD.max_notional IS DISTINCT FROM NEW.max_notional
        OR OLD.valid_from IS DISTINCT FROM NEW.valid_from
        OR OLD.expires_at IS DISTINCT FROM NEW.expires_at
        OR OLD.created_by IS DISTINCT FROM NEW.created_by
        OR OLD.created_at IS DISTINCT FROM NEW.created_at THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='pilot execution lease identity is immutable';
    END IF;
    IF NEW.version <> OLD.version + 1 OR NEW.updated_at < OLD.updated_at THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='pilot execution lease version is invalid';
    END IF;
    v_legal := (OLD.status, NEW.status) IN (
        ('CREATED','ACTIVE'),('CREATED','FAILED'),('CREATED','EXPIRED'),
        ('ACTIVE','CONSUMED'),('ACTIVE','FAILED'),('ACTIVE','EXPIRED'),
        ('CONSUMED','CLOSED'),('CONSUMED','FAILED'),('CONSUMED','EXPIRED')
    );
    IF NOT v_legal THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='illegal pilot execution lease transition';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION gate_y44_guard_pilot_lease_authority()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_session live_sessions%ROWTYPE;
    v_authority operator_pilot_authorities%ROWTYPE;
BEGIN
    SELECT * INTO STRICT v_session FROM live_sessions
    WHERE session_id = NEW.live_session_id FOR KEY SHARE;
    IF v_session.authority_type = 'STRATEGY' THEN
        IF NEW.operator_pilot_authority_id IS NOT NULL THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='strategy lease cannot bind operator authority';
        END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO STRICT v_authority FROM operator_pilot_authorities
    WHERE authority_id = NEW.operator_pilot_authority_id FOR KEY SHARE;
    IF NEW.operator_pilot_authority_id IS DISTINCT FROM v_session.operator_pilot_authority_id
        OR v_authority.status <> 'ACTIVE'
        OR transaction_timestamp() < v_authority.valid_from
        OR transaction_timestamp() >= v_authority.expires_at
        OR NEW.created_by <> v_authority.owner_user_id
        OR NEW.max_notional > v_authority.max_notional
        OR NEW.valid_from < v_authority.valid_from
        OR NEW.expires_at > v_authority.expires_at THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='pilot lease exceeds operator authority';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_gate_y44_pilot_lease_authority
    BEFORE INSERT ON pilot_execution_leases
    FOR EACH ROW EXECUTE FUNCTION gate_y44_guard_pilot_lease_authority();

CREATE FUNCTION gate_y44_guard_operator_pilot_intent_count()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_limit INTEGER;
    v_status VARCHAR(16);
BEGIN
    SELECT CASE WHEN NEW.action = 'PLACE' THEN authority.max_place_count
                ELSE authority.max_cancel_count END, authority.status
    INTO v_limit, v_status
    FROM pilot_execution_leases lease
    JOIN operator_pilot_authorities authority
      ON authority.authority_id = lease.operator_pilot_authority_id
    WHERE lease.lease_id = NEW.lease_id;
    IF FOUND AND (v_status <> 'ACTIVE' OR v_limit <> 1 OR NOT EXISTS (
        SELECT 1 FROM operator_pilot_authorities authority
        JOIN pilot_execution_leases lease
          ON lease.operator_pilot_authority_id = authority.authority_id
        WHERE lease.lease_id = NEW.lease_id
          AND authority.valid_from <= transaction_timestamp()
          AND authority.expires_at > transaction_timestamp()
    ) OR EXISTS (
        SELECT 1 FROM pilot_execution_lease_intents
        WHERE lease_id = NEW.lease_id AND action = NEW.action
    )) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='operator pilot action count exceeded';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_gate_y44_operator_pilot_intent_count
    BEFORE INSERT ON pilot_execution_lease_intents
    FOR EACH ROW EXECUTE FUNCTION gate_y44_guard_operator_pilot_intent_count();

CREATE FUNCTION gate_y44_close_operator_authority_with_lease()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.operator_pilot_authority_id IS NOT NULL
        AND NEW.status IN ('EXPIRED','CLOSED','FAILED')
        AND OLD.status IS DISTINCT FROM NEW.status THEN
        UPDATE operator_pilot_authorities
        SET status = CASE WHEN NEW.status = 'EXPIRED' THEN 'EXPIRED' ELSE 'CLOSED' END,
            version = version + 1,
            closed_at = NEW.closed_at,
            updated_at = NEW.updated_at
        WHERE authority_id = NEW.operator_pilot_authority_id AND status = 'ACTIVE';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_gate_y44_close_operator_authority_with_lease
    AFTER UPDATE ON pilot_execution_leases
    FOR EACH ROW EXECUTE FUNCTION gate_y44_close_operator_authority_with_lease();

COMMENT ON TABLE operator_pilot_authorities IS '单次人工最小真实pilot的显式authority；不保存credential material且不替代strategy或risk engine。';
COMMENT ON COLUMN operator_pilot_authorities.authority_id IS '不可复用operator pilot authority UUID。';
COMMENT ON COLUMN operator_pilot_authorities.owner_user_id IS '明确授权operator的users.id。';
COMMENT ON COLUMN operator_pilot_authorities.exchange_account_id IS '精确OKX LIVE account引用。';
COMMENT ON COLUMN operator_pilot_authorities.credential_reference_id IS '精确credential record引用；不保存material。';
COMMENT ON COLUMN operator_pilot_authorities.instrument IS '授权的唯一规范化Spot instrument。';
COMMENT ON COLUMN operator_pilot_authorities.side IS '允许的订单方向；本批runtime materialization固定BUY。';
COMMENT ON COLUMN operator_pilot_authorities.order_type IS '仅允许LIMIT。';
COMMENT ON COLUMN operator_pilot_authorities.max_notional IS 'operator pilot名义金额硬上限，不得超过10 USDT。';
COMMENT ON COLUMN operator_pilot_authorities.max_place_count IS '允许PLACE次数，本合同固定1。';
COMMENT ON COLUMN operator_pilot_authorities.max_cancel_count IS '允许CANCEL次数，本合同固定1。';
COMMENT ON COLUMN operator_pilot_authorities.transfer_allowed IS '资金划转权限，必须为false。';
COMMENT ON COLUMN operator_pilot_authorities.withdraw_allowed IS '提现权限，必须为false。';
COMMENT ON COLUMN operator_pilot_authorities.valid_from IS 'UTC authority生效时间。';
COMMENT ON COLUMN operator_pilot_authorities.expires_at IS 'UTC authority硬过期时间。';
COMMENT ON COLUMN operator_pilot_authorities.status IS 'ACTIVE/CLOSED/EXPIRED lifecycle。';
COMMENT ON COLUMN operator_pilot_authorities.created_by IS '显式创建authority的operator users.id。';
COMMENT ON COLUMN operator_pilot_authorities.created_at IS '数据库物化时间并参与canonical digest。';
COMMENT ON COLUMN operator_pilot_authorities.canonical_digest IS 'operator-pilot-authority.v1 lowercase SHA-256。';
COMMENT ON COLUMN operator_pilot_authorities.version IS 'lifecycle optimistic version。';
COMMENT ON COLUMN operator_pilot_authorities.closed_at IS 'CLOSED或EXPIRED终态时间。';
COMMENT ON COLUMN operator_pilot_authorities.updated_at IS '最后合法lifecycle更新时间。';
COMMENT ON COLUMN live_sessions.authority_type IS '互斥authority类型：STRATEGY或OPERATOR_PILOT。';
COMMENT ON COLUMN live_sessions.operator_pilot_authority_id IS 'OPERATOR_PILOT会话的显式authority引用；STRATEGY必须为空。';
COMMENT ON COLUMN live_sessions.operator_pilot_authority_digest IS '会话创建时冻结的operator authority canonical digest。';
COMMENT ON COLUMN pilot_execution_leases.operator_pilot_authority_id IS 'OPERATOR_PILOT租约绑定的同一显式authority；STRATEGY租约为空。';
COMMENT ON FUNCTION gate_y44_operator_pilot_authority_digest(UUID,BIGINT,BIGINT,BIGINT,TEXT,TEXT,TEXT,NUMERIC,INTEGER,INTEGER,BOOLEAN,BOOLEAN,TIMESTAMPTZ,TIMESTAMPTZ,BIGINT,TIMESTAMPTZ) IS '按Java operator-pilot-authority.v1相同字节合同重建lowercase SHA-256。';
COMMENT ON FUNCTION gate_y44_guard_operator_pilot_authority_insert() IS '拒绝非canonical或账户引用失配的operator authority。';
COMMENT ON FUNCTION gate_y44_guard_operator_pilot_authority_update() IS '只允许ACTIVE到CLOSED/EXPIRED且scope不可变。';
COMMENT ON FUNCTION gate_y44_guard_pilot_lease_authority() IS '绑定LiveSession、operator authority、binding lease窗口与金额。';
COMMENT ON FUNCTION gate_y44_guard_operator_pilot_intent_count() IS '按operator authority限制PLACE/CANCEL各最多一次。';
COMMENT ON FUNCTION gate_y44_close_operator_authority_with_lease() IS 'lease终态时同事务关闭或过期operator authority。';
