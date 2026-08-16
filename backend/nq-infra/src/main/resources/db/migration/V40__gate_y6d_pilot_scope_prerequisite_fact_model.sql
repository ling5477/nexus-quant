-- GateY-6D：pilot scope 与 prerequisite append-only 事实模型。
-- 本 migration 只增加控制面事实和约束；不启用 LIVE，不创建 ExecutionIntent，也不调用交易所。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE TABLE pilot_scope_bindings (
    pilot_scope_id UUID NOT NULL,
    session_id UUID NOT NULL,
    scope_schema_version VARCHAR(64) NOT NULL,
    instrument_metadata_digest VARCHAR(64) NOT NULL,
    instrument_source_identity VARCHAR(128) NOT NULL,
    instrument_source_schema_version VARCHAR(64) NOT NULL,
    instrument_maximum_age_ms BIGINT NOT NULL,
    fee_schedule_digest VARCHAR(64) NOT NULL,
    fee_tier VARCHAR(64) NOT NULL,
    fee_evidence_class VARCHAR(32) NOT NULL,
    fee_source_identity VARCHAR(128) NOT NULL,
    fee_source_schema_version VARCHAR(64) NOT NULL,
    fee_maximum_age_ms BIGINT NOT NULL,
    balance_source_identity VARCHAR(128) NOT NULL,
    balance_source_schema_version VARCHAR(64) NOT NULL,
    balance_maximum_age_ms BIGINT NOT NULL,
    clock_source_identity VARCHAR(128) NOT NULL,
    clock_source_schema_version VARCHAR(64) NOT NULL,
    clock_maximum_age_ms BIGINT NOT NULL,
    signed_timestamp_source VARCHAR(64) NOT NULL,
    maximum_tolerated_skew_ms BIGINT NOT NULL,
    endpoint_policy_version VARCHAR(64) NOT NULL,
    endpoint_policy_digest VARCHAR(64) NOT NULL,
    provider_contract_identity VARCHAR(128) NOT NULL,
    provider_artifact_digest VARCHAR(64) NOT NULL,
    worker_identity VARCHAR(128) NOT NULL,
    worker_release_digest VARCHAR(64) NOT NULL,
    pilot_scope_hash VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_pilot_scope_bindings PRIMARY KEY (pilot_scope_id),
    CONSTRAINT uq_pilot_scope_bindings_session UNIQUE (session_id),
    CONSTRAINT uq_pilot_scope_bindings_approval UNIQUE (session_id, pilot_scope_id, pilot_scope_hash),
    CONSTRAINT fk_pilot_scope_bindings_session FOREIGN KEY (session_id)
        REFERENCES live_sessions(session_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_pilot_scope_bindings_created_by FOREIGN KEY (created_by)
        REFERENCES users(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_pilot_scope_bindings_schema CHECK (scope_schema_version = 'pilot-scope.v1'),
    CONSTRAINT chk_pilot_scope_bindings_digests CHECK (
        instrument_metadata_digest ~ '^[0-9a-f]{64}$'
        AND fee_schedule_digest ~ '^[0-9a-f]{64}$'
        AND endpoint_policy_digest ~ '^[0-9a-f]{64}$'
        AND provider_artifact_digest ~ '^[0-9a-f]{64}$'
        AND worker_release_digest ~ '^[0-9a-f]{64}$'
        AND pilot_scope_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_pilot_scope_bindings_text CHECK (
        btrim(instrument_source_identity) <> '' AND btrim(instrument_source_schema_version) <> ''
        AND btrim(fee_tier) <> '' AND btrim(fee_source_identity) <> ''
        AND btrim(fee_source_schema_version) <> '' AND btrim(balance_source_identity) <> ''
        AND btrim(balance_source_schema_version) <> '' AND btrim(clock_source_identity) <> ''
        AND btrim(clock_source_schema_version) <> '' AND btrim(endpoint_policy_version) <> ''
        AND btrim(provider_contract_identity) <> '' AND btrim(worker_identity) <> ''
    ),
    CONSTRAINT chk_pilot_scope_bindings_age_skew CHECK (
        instrument_maximum_age_ms BETWEEN 1 AND 300000
        AND fee_maximum_age_ms BETWEEN 1 AND 3600000
        AND balance_maximum_age_ms BETWEEN 1 AND 10000
        AND clock_maximum_age_ms BETWEEN 1 AND 60000
        AND maximum_tolerated_skew_ms BETWEEN 0 AND 1000
    ),
    CONSTRAINT chk_pilot_scope_bindings_fee_evidence CHECK (
        fee_evidence_class IN ('OBSERVED_PRIVATE', 'ESTIMATED_PUBLIC')
    ),
    CONSTRAINT chk_pilot_scope_bindings_timestamp_source CHECK (
        signed_timestamp_source = 'NTP_DISCIPLINED_SYSTEM_CLOCK'
    )
);

CREATE INDEX idx_pilot_scope_bindings_created_at
    ON pilot_scope_bindings(created_at DESC, pilot_scope_id);

CREATE TABLE pilot_prerequisite_observations (
    observation_id UUID NOT NULL,
    pilot_scope_id UUID NOT NULL,
    observation_set_id UUID NOT NULL,
    observation_type VARCHAR(32) NOT NULL,
    observation_schema_version VARCHAR(64) NOT NULL,
    observation_identity VARCHAR(128) NOT NULL,
    source_identity VARCHAR(128) NOT NULL,
    source_schema_version VARCHAR(64) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorder_identity VARCHAR(128) NOT NULL,
    observation_payload_hash VARCHAR(64) NOT NULL,
    instrument_metadata_digest VARCHAR(64),
    fee_schedule_digest VARCHAR(64),
    balance_snapshot_digest VARCHAR(64),
    clock_sync_observation_digest VARCHAR(64),
    fee_tier VARCHAR(64),
    fee_evidence_class VARCHAR(32),
    maker_fee_rate NUMERIC(20,12),
    taker_fee_rate NUMERIC(20,12),
    fee_loss_treatment VARCHAR(64),
    balance_currency VARCHAR(16),
    available_balance NUMERIC(38,8),
    signed_timestamp_source VARCHAR(64),
    observed_skew_ms BIGINT,
    CONSTRAINT pk_pilot_prerequisite_observations PRIMARY KEY (observation_id),
    CONSTRAINT fk_pilot_prerequisite_observations_scope FOREIGN KEY (pilot_scope_id)
        REFERENCES pilot_scope_bindings(pilot_scope_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uq_pilot_observation_source_identity UNIQUE (
        pilot_scope_id, observation_type, source_identity, observation_identity
    ),
    CONSTRAINT uq_pilot_observation_set_type UNIQUE (
        pilot_scope_id, observation_set_id, observation_type
    ),
    CONSTRAINT uq_pilot_observation_id_type UNIQUE (observation_id, observation_type),
    CONSTRAINT chk_pilot_observation_type CHECK (observation_type IN (
        'INSTRUMENT_METADATA', 'FEE_SCHEDULE', 'BALANCE_SNAPSHOT', 'CLOCK_SYNC'
    )),
    CONSTRAINT chk_pilot_observation_text CHECK (
        btrim(observation_identity) <> '' AND btrim(source_identity) <> ''
        AND btrim(source_schema_version) <> '' AND btrim(recorder_identity) <> ''
    ),
    CONSTRAINT chk_pilot_observation_hashes CHECK (
        observation_payload_hash ~ '^[0-9a-f]{64}$'
        AND (instrument_metadata_digest IS NULL OR instrument_metadata_digest ~ '^[0-9a-f]{64}$')
        AND (fee_schedule_digest IS NULL OR fee_schedule_digest ~ '^[0-9a-f]{64}$')
        AND (balance_snapshot_digest IS NULL OR balance_snapshot_digest ~ '^[0-9a-f]{64}$')
        AND (clock_sync_observation_digest IS NULL OR clock_sync_observation_digest ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT chk_pilot_observation_variant CHECK (
        (observation_type = 'INSTRUMENT_METADATA'
            AND observation_schema_version = 'instrument-metadata-observation.v1'
            AND instrument_metadata_digest IS NOT NULL
            AND fee_schedule_digest IS NULL AND balance_snapshot_digest IS NULL
            AND clock_sync_observation_digest IS NULL AND fee_tier IS NULL
            AND fee_evidence_class IS NULL AND maker_fee_rate IS NULL AND taker_fee_rate IS NULL
            AND fee_loss_treatment IS NULL AND balance_currency IS NULL AND available_balance IS NULL
            AND signed_timestamp_source IS NULL AND observed_skew_ms IS NULL)
        OR (observation_type = 'FEE_SCHEDULE'
            AND observation_schema_version = 'fee-schedule-observation.v1'
            AND instrument_metadata_digest IS NULL AND fee_schedule_digest IS NOT NULL
            AND balance_snapshot_digest IS NULL AND clock_sync_observation_digest IS NULL
            AND btrim(fee_tier) <> '' AND fee_evidence_class IN ('OBSERVED_PRIVATE', 'ESTIMATED_PUBLIC')
            AND maker_fee_rate BETWEEN -1 AND 1 AND taker_fee_rate BETWEEN -1 AND 1
            AND fee_loss_treatment = 'INCLUDE_IN_DAILY_LOSS_AND_CAPITAL_USAGE'
            AND balance_currency IS NULL AND available_balance IS NULL
            AND signed_timestamp_source IS NULL AND observed_skew_ms IS NULL)
        OR (observation_type = 'BALANCE_SNAPSHOT'
            AND observation_schema_version = 'balance-snapshot-observation.v1'
            AND instrument_metadata_digest IS NULL AND fee_schedule_digest IS NULL
            AND balance_snapshot_digest IS NOT NULL AND clock_sync_observation_digest IS NULL
            AND fee_tier IS NULL AND fee_evidence_class IS NULL
            AND maker_fee_rate IS NULL AND taker_fee_rate IS NULL AND fee_loss_treatment IS NULL
            AND balance_currency = 'USDT' AND available_balance >= 0
            AND signed_timestamp_source IS NULL AND observed_skew_ms IS NULL)
        OR (observation_type = 'CLOCK_SYNC'
            AND observation_schema_version = 'clock-sync-observation.v1'
            AND instrument_metadata_digest IS NULL AND fee_schedule_digest IS NULL
            AND balance_snapshot_digest IS NULL AND clock_sync_observation_digest IS NOT NULL
            AND fee_tier IS NULL AND fee_evidence_class IS NULL
            AND maker_fee_rate IS NULL AND taker_fee_rate IS NULL AND fee_loss_treatment IS NULL
            AND balance_currency IS NULL AND available_balance IS NULL
            AND signed_timestamp_source = 'NTP_DISCIPLINED_SYSTEM_CLOCK'
            AND observed_skew_ms BETWEEN -1000 AND 1000)
    )
);

CREATE INDEX idx_pilot_observation_fresh_lookup
    ON pilot_prerequisite_observations(pilot_scope_id, observation_type, observed_at DESC, observation_id);
CREATE INDEX idx_pilot_observation_set
    ON pilot_prerequisite_observations(pilot_scope_id, observation_set_id);

CREATE TABLE pilot_instrument_observation_items (
    observation_id UUID NOT NULL,
    observation_type VARCHAR(32) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    trading_status VARCHAR(16) NOT NULL,
    tick_size NUMERIC(38,18) NOT NULL,
    lot_size NUMERIC(38,18) NOT NULL,
    minimum_order_size NUMERIC(38,18) NOT NULL,
    minimum_order_value NUMERIC(38,18) NOT NULL,
    minimum_order_value_currency VARCHAR(16) NOT NULL,
    CONSTRAINT pk_pilot_instrument_observation_items PRIMARY KEY (observation_id, symbol),
    CONSTRAINT fk_pilot_instrument_observation_items_parent FOREIGN KEY (observation_id, observation_type)
        REFERENCES pilot_prerequisite_observations(observation_id, observation_type)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_pilot_instrument_observation_item_type CHECK (
        observation_type = 'INSTRUMENT_METADATA'
    ),
    CONSTRAINT chk_pilot_instrument_observation_item_symbol CHECK (
        symbol ~ '^[A-Z0-9]{2,20}-USDT$'
    ),
    CONSTRAINT chk_pilot_instrument_observation_item_status CHECK (
        trading_status IN ('LIVE', 'SUSPEND', 'PREOPEN', 'TEST')
    ),
    CONSTRAINT chk_pilot_instrument_observation_item_amounts CHECK (
        tick_size > 0 AND lot_size > 0 AND minimum_order_size > 0 AND minimum_order_value > 0
    ),
    CONSTRAINT chk_pilot_instrument_observation_item_currency CHECK (
        minimum_order_value_currency = 'USDT'
    )
);

CREATE FUNCTION gate_y6d_instant_canonical(p_value TIMESTAMPTZ)
    RETURNS TEXT LANGUAGE SQL IMMUTABLE STRICT AS $$
    SELECT to_json(to_char(p_value AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'))::TEXT
$$;

CREATE FUNCTION gate_y6d_numeric_canonical(p_value NUMERIC)
    RETURNS TEXT LANGUAGE SQL IMMUTABLE STRICT AS $$
    SELECT CASE
        WHEN p_value = 0 THEN '0'
        WHEN position('.' IN p_value::TEXT) = 0 THEN p_value::TEXT
        ELSE rtrim(rtrim(p_value::TEXT, '0'), '.')
    END
$$;

CREATE FUNCTION gate_y6d_pilot_scope_canonical_payload(
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
        '"schemaVersion":"pilot-scope.v1"' ||
        ',"sessionId":' || to_json(s.session_id::TEXT)::TEXT ||
        ',"ownerId":' || s.owner_id::TEXT ||
        ',"exchangeAccountId":' || s.exchange_account_id::TEXT ||
        ',"venue":' || to_json(s.venue)::TEXT ||
        ',"strategyReleaseId":' || to_json(s.strategy_release_id)::TEXT ||
        ',"releaseArtifactDigest":' || to_json(s.release_digest)::TEXT ||
        ',"releaseAdmissionRevision":' || s.release_admission_revision::TEXT ||
        ',"riskLimitSetId":' || to_json(s.risk_limit_set_id::TEXT)::TEXT ||
        ',"riskLimitSetDigest":' || to_json(s.risk_limit_set_digest)::TEXT ||
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
    FROM live_sessions s
    WHERE s.session_id = p_session_id
$$;

CREATE FUNCTION gate_y6d_pilot_scope_hash(
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
    SELECT encode(digest(convert_to(gate_y6d_pilot_scope_canonical_payload(
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

CREATE FUNCTION gate_y6d_reconstruct_pilot_scope_hash(p_pilot_scope_id UUID)
    RETURNS TEXT LANGUAGE SQL STABLE STRICT AS $$
    SELECT gate_y6d_pilot_scope_hash(
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
    FROM pilot_scope_bindings scope
    WHERE scope.pilot_scope_id = p_pilot_scope_id
$$;

CREATE FUNCTION gate_y6d_guard_pilot_scope_insert()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_state VARCHAR(32);
    v_hash TEXT;
BEGIN
    SELECT state INTO v_state FROM live_sessions WHERE session_id = NEW.session_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE='23503', MESSAGE='pilot scope session does not exist';
    END IF;
    IF v_state <> 'APPROVAL_PENDING'
        OR EXISTS (SELECT 1 FROM operator_approvals WHERE session_id = NEW.session_id)
        OR EXISTS (SELECT 1 FROM execution_intents WHERE session_id = NEW.session_id) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='pilot scope cannot be bound after approval or execution';
    END IF;
    v_hash := gate_y6d_pilot_scope_hash(
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
    IF v_hash IS NULL OR NEW.pilot_scope_hash <> v_hash THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='pilot scope hash does not match canonical reconstruction';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_pilot_scope_bindings_insert_guard
    BEFORE INSERT ON pilot_scope_bindings
    FOR EACH ROW EXECUTE FUNCTION gate_y6d_guard_pilot_scope_insert();
CREATE TRIGGER trg_pilot_scope_bindings_immutable
    BEFORE UPDATE OR DELETE ON pilot_scope_bindings
    FOR EACH ROW EXECUTE FUNCTION gate_y2_reject_fact_mutation();

CREATE FUNCTION gate_y6d_guard_prerequisite_observation_insert()
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
        SELECT risk.quote_currency INTO v_quote_currency
        FROM pilot_scope_bindings scope
        JOIN live_sessions session ON session.session_id = scope.session_id
        JOIN risk_limit_sets risk ON risk.risk_limit_set_id = session.risk_limit_set_id
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

CREATE TRIGGER trg_pilot_prerequisite_observations_insert_guard
    BEFORE INSERT ON pilot_prerequisite_observations
    FOR EACH ROW EXECUTE FUNCTION gate_y6d_guard_prerequisite_observation_insert();
CREATE TRIGGER trg_pilot_prerequisite_observations_append_only
    BEFORE UPDATE OR DELETE ON pilot_prerequisite_observations
    FOR EACH ROW EXECUTE FUNCTION gate_y2_reject_fact_mutation();
CREATE TRIGGER trg_pilot_instrument_observation_items_append_only
    BEFORE UPDATE OR DELETE ON pilot_instrument_observation_items
    FOR EACH ROW EXECUTE FUNCTION gate_y2_reject_fact_mutation();

CREATE FUNCTION gate_y6d_instrument_metadata_digest(p_observation_id UUID)
    RETURNS TEXT LANGUAGE SQL STABLE STRICT AS $$
    SELECT encode(digest(convert_to(
        '{"schemaVersion":"instrument-metadata-observation.v1","items":[' ||
        string_agg(
            '{"symbol":' || to_json(item.symbol)::TEXT ||
            ',"tradingStatus":' || to_json(item.trading_status)::TEXT ||
            ',"tickSize":' || to_json(gate_y6d_numeric_canonical(item.tick_size))::TEXT ||
            ',"lotSize":' || to_json(gate_y6d_numeric_canonical(item.lot_size))::TEXT ||
            ',"minimumOrderSize":' || to_json(gate_y6d_numeric_canonical(item.minimum_order_size))::TEXT ||
            ',"minimumOrderValue":' || to_json(gate_y6d_numeric_canonical(item.minimum_order_value))::TEXT ||
            ',"minimumOrderValueCurrency":' || to_json(item.minimum_order_value_currency)::TEXT || '}',
            ',' ORDER BY item.symbol
        ) || ']}', 'UTF8'), 'sha256'), 'hex')
    FROM pilot_instrument_observation_items item
    WHERE item.observation_id = p_observation_id
$$;

CREATE FUNCTION gate_y6d_observation_payload_hash(p_observation_id UUID)
    RETURNS TEXT LANGUAGE plpgsql STABLE STRICT AS $$
DECLARE
    v_observation pilot_prerequisite_observations%ROWTYPE;
    v_payload TEXT;
    v_items TEXT;
BEGIN
    SELECT * INTO STRICT v_observation FROM pilot_prerequisite_observations
    WHERE observation_id = p_observation_id;
    v_payload := '{"schemaVersion":"prerequisite-observation-envelope.v1"' ||
        ',"observationType":' || to_json(v_observation.observation_type)::TEXT ||
        ',"observationSchemaVersion":' || to_json(v_observation.observation_schema_version)::TEXT ||
        ',"observationIdentity":' || to_json(v_observation.observation_identity)::TEXT ||
        ',"sourceIdentity":' || to_json(v_observation.source_identity)::TEXT ||
        ',"sourceSchemaVersion":' || to_json(v_observation.source_schema_version)::TEXT ||
        ',"observedAt":' || gate_y6d_instant_canonical(v_observation.observed_at) ||
        ',"payload":';
    IF v_observation.observation_type = 'INSTRUMENT_METADATA' THEN
        SELECT string_agg(
            '{"symbol":' || to_json(item.symbol)::TEXT ||
            ',"tradingStatus":' || to_json(item.trading_status)::TEXT ||
            ',"tickSize":' || to_json(gate_y6d_numeric_canonical(item.tick_size))::TEXT ||
            ',"lotSize":' || to_json(gate_y6d_numeric_canonical(item.lot_size))::TEXT ||
            ',"minimumOrderSize":' || to_json(gate_y6d_numeric_canonical(item.minimum_order_size))::TEXT ||
            ',"minimumOrderValue":' || to_json(gate_y6d_numeric_canonical(item.minimum_order_value))::TEXT ||
            ',"minimumOrderValueCurrency":' || to_json(item.minimum_order_value_currency)::TEXT || '}',
            ',' ORDER BY item.symbol
        ) INTO v_items FROM pilot_instrument_observation_items item
        WHERE item.observation_id = p_observation_id;
        v_payload := v_payload || '{"instrumentMetadataDigest":' ||
            to_json(v_observation.instrument_metadata_digest)::TEXT || ',"items":[' || v_items || ']}';
    ELSIF v_observation.observation_type = 'FEE_SCHEDULE' THEN
        v_payload := v_payload || '{"feeScheduleDigest":' || to_json(v_observation.fee_schedule_digest)::TEXT ||
            ',"feeTier":' || to_json(v_observation.fee_tier)::TEXT ||
            ',"feeEvidenceClass":' || to_json(v_observation.fee_evidence_class)::TEXT ||
            ',"makerFeeRate":' || to_json(gate_y6d_numeric_canonical(v_observation.maker_fee_rate))::TEXT ||
            ',"takerFeeRate":' || to_json(gate_y6d_numeric_canonical(v_observation.taker_fee_rate))::TEXT ||
            ',"feeLossTreatment":' || to_json(v_observation.fee_loss_treatment)::TEXT || '}';
    ELSIF v_observation.observation_type = 'BALANCE_SNAPSHOT' THEN
        v_payload := v_payload || '{"balanceSnapshotDigest":' || to_json(v_observation.balance_snapshot_digest)::TEXT ||
            ',"balanceCurrency":' || to_json(v_observation.balance_currency)::TEXT ||
            ',"availableBalance":' || to_json((v_observation.available_balance::NUMERIC(38,8))::TEXT)::TEXT || '}';
    ELSE
        v_payload := v_payload || '{"clockSyncObservationDigest":' ||
            to_json(v_observation.clock_sync_observation_digest)::TEXT ||
            ',"signedTimestampSource":' || to_json(v_observation.signed_timestamp_source)::TEXT ||
            ',"observedSkewMs":' || v_observation.observed_skew_ms::TEXT || '}';
    END IF;
    v_payload := v_payload || '}';
    RETURN encode(digest(convert_to(v_payload, 'UTF8'), 'sha256'), 'hex');
END;
$$;

CREATE FUNCTION gate_y6d_validate_observation_set()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_pilot_scope_id UUID;
    v_observation_set_id UUID;
    v_instrument_observation_id UUID;
    v_symbols TEXT[];
    v_expected_symbols TEXT[];
    v_count INTEGER;
    v_invalid_hashes INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'pilot_instrument_observation_items' THEN
        SELECT pilot_scope_id, observation_set_id
        INTO v_pilot_scope_id, v_observation_set_id
        FROM pilot_prerequisite_observations WHERE observation_id = NEW.observation_id;
    ELSE
        v_pilot_scope_id := NEW.pilot_scope_id;
        v_observation_set_id := NEW.observation_set_id;
    END IF;

    SELECT count(*) INTO v_count
    FROM pilot_prerequisite_observations
    WHERE pilot_scope_id = v_pilot_scope_id AND observation_set_id = v_observation_set_id;
    SELECT observation_id INTO v_instrument_observation_id
    FROM pilot_prerequisite_observations
    WHERE pilot_scope_id = v_pilot_scope_id AND observation_set_id = v_observation_set_id
      AND observation_type = 'INSTRUMENT_METADATA'
    ORDER BY observation_id LIMIT 1;
    IF v_count <> 4 OR v_instrument_observation_id IS NULL THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='pilot observation set must contain exactly four typed observations';
    END IF;

    SELECT array_agg(symbol ORDER BY symbol), count(*)
    INTO v_symbols, v_count
    FROM pilot_instrument_observation_items
    WHERE observation_id = v_instrument_observation_id;
    SELECT session.symbol_allowlist INTO v_expected_symbols
    FROM pilot_scope_bindings scope JOIN live_sessions session ON session.session_id = scope.session_id
    WHERE scope.pilot_scope_id = v_pilot_scope_id;
    IF v_count NOT BETWEEN 1 AND 2 OR v_symbols IS DISTINCT FROM v_expected_symbols THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='instrument observation items must equal the session symbol scope';
    END IF;
    IF gate_y6d_instrument_metadata_digest(v_instrument_observation_id) IS DISTINCT FROM (
        SELECT instrument_metadata_digest FROM pilot_prerequisite_observations
        WHERE observation_id = v_instrument_observation_id
    ) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='instrument metadata digest does not match item reconstruction';
    END IF;
    SELECT count(*) INTO v_invalid_hashes
    FROM pilot_prerequisite_observations observation
    WHERE observation.pilot_scope_id = v_pilot_scope_id
      AND observation.observation_set_id = v_observation_set_id
      AND observation.observation_payload_hash IS DISTINCT FROM
          gate_y6d_observation_payload_hash(observation.observation_id);
    IF v_invalid_hashes <> 0 THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='observation payload hash does not match typed fact reconstruction';
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_pilot_observation_set_complete
    AFTER INSERT ON pilot_prerequisite_observations
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION gate_y6d_validate_observation_set();
CREATE CONSTRAINT TRIGGER trg_pilot_instrument_items_complete
    AFTER INSERT ON pilot_instrument_observation_items
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION gate_y6d_validate_observation_set();

ALTER TABLE operator_approvals
    ADD COLUMN scope_schema_version VARCHAR(64) NOT NULL DEFAULT 'approval-scope.v1';
ALTER TABLE operator_approvals ALTER COLUMN scope_schema_version DROP DEFAULT;
ALTER TABLE operator_approvals ADD COLUMN pilot_scope_id UUID;
ALTER TABLE operator_approvals ADD CONSTRAINT chk_operator_approvals_scope_version CHECK (
    (scope_schema_version = 'approval-scope.v1' AND pilot_scope_id IS NULL)
    OR (scope_schema_version = 'pilot-scope.v1' AND pilot_scope_id IS NOT NULL)
);
ALTER TABLE operator_approvals ADD CONSTRAINT fk_operator_approvals_pilot_scope
    FOREIGN KEY (session_id, pilot_scope_id, scope_hash)
    REFERENCES pilot_scope_bindings(session_id, pilot_scope_id, pilot_scope_hash)
    ON UPDATE RESTRICT ON DELETE RESTRICT;

CREATE FUNCTION gate_y6d_guard_operator_approval_insert()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
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
    IF NEW.scope_schema_version = 'pilot-scope.v1' THEN
        IF v_session.state <> 'APPROVAL_PENDING'
            OR NOT EXISTS (
                SELECT 1 FROM pilot_scope_bindings scope
                WHERE scope.session_id = NEW.session_id
                  AND scope.pilot_scope_id = NEW.pilot_scope_id
                  AND scope.pilot_scope_hash = NEW.scope_hash
            ) THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='pilot approval is not bound to the exact pending pilot scope';
        END IF;
    ELSIF EXISTS (SELECT 1 FROM pilot_scope_bindings WHERE session_id = NEW.session_id) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='legacy approval cannot authorize a materialized pilot scope';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_operator_approvals_gate_y6d_insert_guard
    BEFORE INSERT ON operator_approvals
    FOR EACH ROW EXECUTE FUNCTION gate_y6d_guard_operator_approval_insert();

COMMENT ON TABLE pilot_scope_bindings IS 'GateY-6D session-bound immutable pilot scope；不表示 LIVE、交易或 kill switch 已获授权。';
COMMENT ON TABLE pilot_prerequisite_observations IS 'GateY-6D typed prerequisite append-only observation；不保存 credential 或交易所 raw payload。';
COMMENT ON TABLE pilot_instrument_observation_items IS 'Instrument metadata observation 的可回读明细；父 observation 与本表均禁止修改或删除。';

COMMENT ON COLUMN pilot_scope_bindings.pilot_scope_id IS '不可复用 pilot scope UUID。';
COMMENT ON COLUMN pilot_scope_bindings.session_id IS '唯一绑定的 LiveSession；历史 session 不做伪造回填。';
COMMENT ON COLUMN pilot_scope_bindings.scope_schema_version IS 'canonical schema，固定 pilot-scope.v1。';
COMMENT ON COLUMN pilot_scope_bindings.instrument_metadata_digest IS 'exact instrument constraint set 的 lowercase SHA-256。';
COMMENT ON COLUMN pilot_scope_bindings.instrument_source_identity IS '不可变 instrument source contract identity。';
COMMENT ON COLUMN pilot_scope_bindings.instrument_source_schema_version IS 'instrument source contract schema version。';
COMMENT ON COLUMN pilot_scope_bindings.instrument_maximum_age_ms IS 'instrument observation freshness 上限，1..300000ms。';
COMMENT ON COLUMN pilot_scope_bindings.fee_schedule_digest IS 'exact fee constraint 的 lowercase SHA-256。';
COMMENT ON COLUMN pilot_scope_bindings.fee_tier IS '审批绑定的 exact fee tier。';
COMMENT ON COLUMN pilot_scope_bindings.fee_evidence_class IS 'OBSERVED_PRIVATE 或 ESTIMATED_PUBLIC；首单 eligibility 仅接受前者。';
COMMENT ON COLUMN pilot_scope_bindings.fee_source_identity IS '不可变 fee source contract identity。';
COMMENT ON COLUMN pilot_scope_bindings.fee_source_schema_version IS 'fee source contract schema version。';
COMMENT ON COLUMN pilot_scope_bindings.fee_maximum_age_ms IS 'fee observation freshness 上限，1..3600000ms。';
COMMENT ON COLUMN pilot_scope_bindings.balance_source_identity IS 'private balance source contract identity；不保存 credential。';
COMMENT ON COLUMN pilot_scope_bindings.balance_source_schema_version IS 'balance source contract schema version。';
COMMENT ON COLUMN pilot_scope_bindings.balance_maximum_age_ms IS 'balance observation freshness 上限，1..10000ms。';
COMMENT ON COLUMN pilot_scope_bindings.clock_source_identity IS 'clock observation source contract identity。';
COMMENT ON COLUMN pilot_scope_bindings.clock_source_schema_version IS 'clock source contract schema version。';
COMMENT ON COLUMN pilot_scope_bindings.clock_maximum_age_ms IS 'clock observation freshness 上限，1..60000ms。';
COMMENT ON COLUMN pilot_scope_bindings.signed_timestamp_source IS '签名时间来源，首版固定 NTP_DISCIPLINED_SYSTEM_CLOCK。';
COMMENT ON COLUMN pilot_scope_bindings.maximum_tolerated_skew_ms IS 'scope-bound 最大时钟偏差，0..1000ms。';
COMMENT ON COLUMN pilot_scope_bindings.endpoint_policy_version IS 'typed method/path/operation/order-type policy version。';
COMMENT ON COLUMN pilot_scope_bindings.endpoint_policy_digest IS 'endpoint policy lowercase SHA-256。';
COMMENT ON COLUMN pilot_scope_bindings.provider_contract_identity IS 'provider contract identity；不是 real-provider wiring。';
COMMENT ON COLUMN pilot_scope_bindings.provider_artifact_digest IS 'provider immutable artifact lowercase SHA-256。';
COMMENT ON COLUMN pilot_scope_bindings.worker_identity IS 'admitted worker identity；本 migration 不启动 worker。';
COMMENT ON COLUMN pilot_scope_bindings.worker_release_digest IS 'worker immutable release lowercase SHA-256。';
COMMENT ON COLUMN pilot_scope_bindings.pilot_scope_hash IS 'pilot-scope.v1 canonical UTF-8 bytes 的 lowercase SHA-256。';
COMMENT ON COLUMN pilot_scope_bindings.created_by IS 'materialization 创建者 users.id。';
COMMENT ON COLUMN pilot_scope_bindings.created_at IS '不可变 materialization 时间；不进入 scope hash。';

COMMENT ON COLUMN pilot_prerequisite_observations.observation_id IS '不可复用 observation UUID。';
COMMENT ON COLUMN pilot_prerequisite_observations.pilot_scope_id IS '所属 immutable pilot scope。';
COMMENT ON COLUMN pilot_prerequisite_observations.observation_set_id IS '同一事务写入的四类完整 observation set identity。';
COMMENT ON COLUMN pilot_prerequisite_observations.observation_type IS 'INSTRUMENT_METADATA/FEE_SCHEDULE/BALANCE_SNAPSHOT/CLOCK_SYNC。';
COMMENT ON COLUMN pilot_prerequisite_observations.observation_schema_version IS '按 observation type 固定的 typed schema version。';
COMMENT ON COLUMN pilot_prerequisite_observations.observation_identity IS 'source 内稳定 observation identity，用于幂等。';
COMMENT ON COLUMN pilot_prerequisite_observations.source_identity IS 'scope-bound prerequisite source identity。';
COMMENT ON COLUMN pilot_prerequisite_observations.source_schema_version IS 'scope-bound source schema version。';
COMMENT ON COLUMN pilot_prerequisite_observations.observed_at IS 'source fact 被观察的 UTC 时间。';
COMMENT ON COLUMN pilot_prerequisite_observations.recorded_at IS 'append-only fact 在数据库记录的时间。';
COMMENT ON COLUMN pilot_prerequisite_observations.recorder_identity IS 'scope-bound admitted worker identity；不含 credential。';
COMMENT ON COLUMN pilot_prerequisite_observations.observation_payload_hash IS 'prerequisite-observation-envelope.v1 typed payload lowercase SHA-256。';
COMMENT ON COLUMN pilot_prerequisite_observations.instrument_metadata_digest IS 'instrument variant exact constraint digest，其他 variant 必须为空。';
COMMENT ON COLUMN pilot_prerequisite_observations.fee_schedule_digest IS 'fee variant exact constraint digest，其他 variant 必须为空。';
COMMENT ON COLUMN pilot_prerequisite_observations.balance_snapshot_digest IS 'balance variant snapshot digest；实际余额仍须可回读。';
COMMENT ON COLUMN pilot_prerequisite_observations.clock_sync_observation_digest IS 'clock variant observation digest；实际 skew 仍须可回读。';
COMMENT ON COLUMN pilot_prerequisite_observations.fee_tier IS 'fee variant exact tier。';
COMMENT ON COLUMN pilot_prerequisite_observations.fee_evidence_class IS 'fee variant evidence class。';
COMMENT ON COLUMN pilot_prerequisite_observations.maker_fee_rate IS 'fee variant maker rate，范围 [-1,1]。';
COMMENT ON COLUMN pilot_prerequisite_observations.taker_fee_rate IS 'fee variant taker rate，范围 [-1,1]。';
COMMENT ON COLUMN pilot_prerequisite_observations.fee_loss_treatment IS 'fee 纳入 daily loss 与 capital usage 的固定规则。';
COMMENT ON COLUMN pilot_prerequisite_observations.balance_currency IS 'balance variant 币种，必须匹配 risk quote currency，首版 USDT。';
COMMENT ON COLUMN pilot_prerequisite_observations.available_balance IS '可回读 available balance，NUMERIC(38,8)，不得为负。';
COMMENT ON COLUMN pilot_prerequisite_observations.signed_timestamp_source IS 'clock variant 签名时间来源。';
COMMENT ON COLUMN pilot_prerequisite_observations.observed_skew_ms IS 'clock variant 实测 signed skew 毫秒值。';

COMMENT ON COLUMN pilot_instrument_observation_items.observation_id IS '所属 INSTRUMENT_METADATA observation。';
COMMENT ON COLUMN pilot_instrument_observation_items.observation_type IS '固定 INSTRUMENT_METADATA，用于 composite FK 防错绑。';
COMMENT ON COLUMN pilot_instrument_observation_items.symbol IS 'canonical uppercase BASE-USDT symbol。';
COMMENT ON COLUMN pilot_instrument_observation_items.trading_status IS 'LIVE/SUSPEND/PREOPEN/TEST；preflight 只接受 LIVE。';
COMMENT ON COLUMN pilot_instrument_observation_items.tick_size IS '可回读 tick size，必须大于 0。';
COMMENT ON COLUMN pilot_instrument_observation_items.lot_size IS '可回读 lot size，必须大于 0。';
COMMENT ON COLUMN pilot_instrument_observation_items.minimum_order_size IS '可回读 minimum order size，必须大于 0。';
COMMENT ON COLUMN pilot_instrument_observation_items.minimum_order_value IS '可回读 minimum order value，必须大于 0。';
COMMENT ON COLUMN pilot_instrument_observation_items.minimum_order_value_currency IS 'minimum order value 币种，首版固定 USDT。';

COMMENT ON COLUMN operator_approvals.scope_schema_version IS '审批 hash 的真实 schema label；历史行固定 approval-scope.v1。';
COMMENT ON COLUMN operator_approvals.pilot_scope_id IS 'pilot-scope.v1 审批的 exact scope FK；历史 approval 保持 NULL。';

COMMENT ON FUNCTION gate_y6d_instant_canonical(TIMESTAMPTZ) IS '输出固定 UTC 六位微秒 canonical JSON string。';
COMMENT ON FUNCTION gate_y6d_numeric_canonical(NUMERIC) IS '输出 observation decimal 的 plain、去尾零 canonical 文本。';
COMMENT ON FUNCTION gate_y6d_reconstruct_pilot_scope_hash(UUID) IS '从 LiveSession SoR 与 immutable pilot scope 重建 pilot-scope.v1 SHA-256。';
COMMENT ON FUNCTION gate_y6d_instrument_metadata_digest(UUID) IS '从排序 instrument items 重建 typed instrument metadata digest。';
COMMENT ON FUNCTION gate_y6d_observation_payload_hash(UUID) IS '从 typed observation 与 items 重建 retry payload hash。';
COMMENT ON FUNCTION gate_y6d_validate_observation_set() IS 'commit 时验证四类 observation 完整、symbol/digest/payload 一致。';
