-- GateY minimal pilot：为 current best ask 增加 typed、append-only prerequisite fact。
-- nullable column add 不重写历史行；约束替换受短 lock/statement timeout 保护。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

ALTER TABLE pilot_prerequisite_observations
    ADD COLUMN market_snapshot_digest VARCHAR(64),
    ADD COLUMN market_instrument VARCHAR(64),
    ADD COLUMN best_ask NUMERIC(38,18);

ALTER TABLE pilot_prerequisite_observations
    DROP CONSTRAINT chk_pilot_observation_type;
ALTER TABLE pilot_prerequisite_observations
    ADD CONSTRAINT chk_pilot_observation_type CHECK (observation_type IN (
        'INSTRUMENT_METADATA', 'FEE_SCHEDULE', 'BALANCE_SNAPSHOT', 'CLOCK_SYNC', 'MARKET_SNAPSHOT'
    ));

ALTER TABLE pilot_prerequisite_observations
    DROP CONSTRAINT chk_pilot_observation_hashes;
ALTER TABLE pilot_prerequisite_observations
    ADD CONSTRAINT chk_pilot_observation_hashes CHECK (
        observation_payload_hash ~ '^[0-9a-f]{64}$'
        AND (instrument_metadata_digest IS NULL OR instrument_metadata_digest ~ '^[0-9a-f]{64}$')
        AND (fee_schedule_digest IS NULL OR fee_schedule_digest ~ '^[0-9a-f]{64}$')
        AND (balance_snapshot_digest IS NULL OR balance_snapshot_digest ~ '^[0-9a-f]{64}$')
        AND (clock_sync_observation_digest IS NULL OR clock_sync_observation_digest ~ '^[0-9a-f]{64}$')
        AND (market_snapshot_digest IS NULL OR market_snapshot_digest ~ '^[0-9a-f]{64}$')
    );

-- 完整保留 V41 的四类 variant 语义，并为每类显式排除 market 字段。
ALTER TABLE pilot_prerequisite_observations
    DROP CONSTRAINT chk_pilot_observation_variant;
ALTER TABLE pilot_prerequisite_observations
    ADD CONSTRAINT chk_pilot_observation_variant CHECK (
        (observation_type = 'INSTRUMENT_METADATA'
            AND observation_schema_version IN (
                'instrument-metadata-observation.v1',
                'instrument-metadata-observation.v2'
            )
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
            AND best_ask > 0 AND scale(best_ask) <= 8
            AND instrument_metadata_digest IS NULL AND fee_schedule_digest IS NULL
            AND balance_snapshot_digest IS NULL AND clock_sync_observation_digest IS NULL
            AND fee_tier IS NULL AND fee_evidence_class IS NULL
            AND maker_fee_rate IS NULL AND taker_fee_rate IS NULL AND fee_loss_treatment IS NULL
            AND balance_currency IS NULL AND available_balance IS NULL
            AND signed_timestamp_source IS NULL AND observed_skew_ms IS NULL)
    );

CREATE FUNCTION gate_y43_market_snapshot_digest(
    p_instrument TEXT,
    p_best_ask NUMERIC,
    p_observed_at TIMESTAMPTZ,
    p_source_identity TEXT,
    p_source_schema_version TEXT
) RETURNS TEXT LANGUAGE SQL IMMUTABLE STRICT AS $$
    SELECT encode(digest(convert_to(
        '{"schemaVersion":"market-snapshot-observation.v1"' ||
        ',"instrument":' || to_json(p_instrument)::TEXT ||
        ',"bestAsk":' || to_json(gate_y6d_numeric_canonical(p_best_ask))::TEXT ||
        ',"observedAt":' || gate_y6d_instant_canonical(p_observed_at) ||
        ',"sourceIdentity":' || to_json(p_source_identity)::TEXT ||
        ',"sourceSchemaVersion":' || to_json(p_source_schema_version)::TEXT || '}',
        'UTF8'), 'sha256'), 'hex')
$$;

CREATE OR REPLACE FUNCTION gate_y6d_observation_payload_hash(p_observation_id UUID)
    RETURNS TEXT LANGUAGE plpgsql STABLE STRICT AS $$
DECLARE
    v_observation pilot_prerequisite_observations%ROWTYPE;
    v_payload TEXT;
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
        v_payload := v_payload || '{"instrumentMetadataDigest":' ||
            to_json(v_observation.instrument_metadata_digest)::TEXT || ',"items":[' ||
            gate_y6e_instrument_items_canonical(p_observation_id) || ']}';
    ELSIF v_observation.observation_type = 'FEE_SCHEDULE' THEN
        v_payload := v_payload || '{"feeScheduleDigest":' || to_json(v_observation.fee_schedule_digest)::TEXT ||
            ',"feeTier":' || to_json(v_observation.fee_tier)::TEXT ||
            ',"feeEvidenceClass":' || to_json(v_observation.fee_evidence_class)::TEXT ||
            ',"makerFeeRate":' || to_json(gate_y6d_numeric_canonical(v_observation.maker_fee_rate))::TEXT ||
            ',"takerFeeRate":' || to_json(gate_y6d_numeric_canonical(v_observation.taker_fee_rate))::TEXT ||
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
            ',"bestAsk":' || to_json(gate_y6d_numeric_canonical(v_observation.best_ask))::TEXT || '}';
    ELSE
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='unsupported pilot observation type';
    END IF;
    v_payload := v_payload || '}';
    RETURN encode(digest(convert_to(v_payload, 'UTF8'), 'sha256'), 'hex');
END;
$$;

CREATE FUNCTION gate_y43_guard_market_snapshot_insert()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_expected_symbols TEXT[];
BEGIN
    IF NEW.observation_type <> 'MARKET_SNAPSHOT' THEN
        RETURN NEW;
    END IF;
    SELECT session.symbol_allowlist INTO v_expected_symbols
    FROM pilot_scope_bindings scope
    JOIN live_sessions session ON session.session_id = scope.session_id
    WHERE scope.pilot_scope_id = NEW.pilot_scope_id;
    IF NEW.source_identity <> 'OKX_MARKET_TICKER'
        OR NEW.source_schema_version <> 'okx-market-ticker.v5'
        OR NOT NEW.market_instrument = ANY(v_expected_symbols)
        OR NEW.market_snapshot_digest IS DISTINCT FROM gate_y43_market_snapshot_digest(
            NEW.market_instrument, NEW.best_ask, NEW.observed_at,
            NEW.source_identity, NEW.source_schema_version) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='market snapshot is outside canonical pilot scope';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_gate_y43_market_snapshot_insert
    BEFORE INSERT ON pilot_prerequisite_observations
    FOR EACH ROW EXECUTE FUNCTION gate_y43_guard_market_snapshot_insert();

-- V40/V41 deferred trigger 继续复用同名函数；只把完整集合基数扩展为五类。
CREATE OR REPLACE FUNCTION gate_y6d_validate_observation_set()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_pilot_scope_id UUID;
    v_observation_set_id UUID;
    v_instrument_observation_id UUID;
    v_market_observation_id UUID;
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
      AND observation_type = 'INSTRUMENT_METADATA';
    SELECT observation_id INTO v_market_observation_id
    FROM pilot_prerequisite_observations
    WHERE pilot_scope_id = v_pilot_scope_id AND observation_set_id = v_observation_set_id
      AND observation_type = 'MARKET_SNAPSHOT';
    IF v_count <> 5 OR v_instrument_observation_id IS NULL OR v_market_observation_id IS NULL THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='pilot observation set must contain exactly five typed observations';
    END IF;

    SELECT array_agg(symbol ORDER BY symbol), count(*)
    INTO v_symbols, v_count
    FROM pilot_instrument_observation_items
    WHERE observation_id = v_instrument_observation_id;
    SELECT session.symbol_allowlist INTO v_expected_symbols
    FROM pilot_scope_bindings scope
    JOIN live_sessions session ON session.session_id = scope.session_id
    WHERE scope.pilot_scope_id = v_pilot_scope_id;
    IF v_count NOT BETWEEN 1 AND 2 OR v_symbols IS DISTINCT FROM v_expected_symbols THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='instrument observation items must equal the session symbol scope';
    END IF;
    IF gate_y6d_instrument_metadata_digest(v_instrument_observation_id) IS DISTINCT FROM (
        SELECT instrument_metadata_digest FROM pilot_prerequisite_observations
        WHERE observation_id = v_instrument_observation_id
    ) THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='instrument metadata digest does not match item reconstruction';
    END IF;
    SELECT count(*) INTO v_invalid_hashes
    FROM pilot_prerequisite_observations observation
    WHERE observation.pilot_scope_id = v_pilot_scope_id
      AND observation.observation_set_id = v_observation_set_id
      AND observation.observation_payload_hash IS DISTINCT FROM
          gate_y6d_observation_payload_hash(observation.observation_id);
    IF v_invalid_hashes <> 0 THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='observation payload hash does not match typed fact reconstruction';
    END IF;
    RETURN NEW;
END;
$$;

COMMENT ON COLUMN pilot_prerequisite_observations.market_snapshot_digest IS
    '当前市场快照 canonical SHA-256；仅 MARKET_SNAPSHOT 使用。';
COMMENT ON COLUMN pilot_prerequisite_observations.market_instrument IS
    '当前市场快照绑定的规范化 OKX Spot instrument。';
COMMENT ON COLUMN pilot_prerequisite_observations.best_ask IS
    '当前市场快照中的卖一价，必须大于 0。';
COMMENT ON FUNCTION gate_y43_market_snapshot_digest(TEXT, NUMERIC, TIMESTAMPTZ, TEXT, TEXT) IS
    '按 Java market-snapshot-observation.v1 相同字节合同重建 lowercase SHA-256。';
COMMENT ON FUNCTION gate_y43_guard_market_snapshot_insert() IS
    '绑定固定 OKX ticker source、session instrument 与 canonical digest。';
COMMENT ON FUNCTION gate_y6d_observation_payload_hash(UUID) IS
    '重建五类 typed prerequisite envelope canonical SHA-256。';
COMMENT ON FUNCTION gate_y6d_validate_observation_set() IS
    '提交时校验五类 observation、instrument items 与全部 payload hash。';
COMMENT ON TRIGGER trg_gate_y43_market_snapshot_insert ON pilot_prerequisite_observations IS
    'MARKET_SNAPSHOT insert-time source、instrument 与 digest hard gate。';
