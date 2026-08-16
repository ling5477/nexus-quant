-- GateY-6E：minimum order value 仅在场所正式发布时才是 prerequisite fact。
-- 历史 V40 行只做无损语义标记；本 migration 不伪造场所事实、不启用 LIVE、也不调用交易所。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

ALTER TABLE pilot_instrument_observation_items
    ADD COLUMN minimum_order_value_evidence_class VARCHAR(32) NOT NULL
        DEFAULT 'LEGACY_V40_REQUIRED';
ALTER TABLE pilot_instrument_observation_items
    ALTER COLUMN minimum_order_value_evidence_class DROP DEFAULT;
ALTER TABLE pilot_instrument_observation_items
    ALTER COLUMN minimum_order_value DROP NOT NULL;
ALTER TABLE pilot_instrument_observation_items
    ALTER COLUMN minimum_order_value_currency DROP NOT NULL;
ALTER TABLE pilot_instrument_observation_items
    DROP CONSTRAINT chk_pilot_instrument_observation_item_amounts;
ALTER TABLE pilot_instrument_observation_items
    DROP CONSTRAINT chk_pilot_instrument_observation_item_currency;
ALTER TABLE pilot_instrument_observation_items
    ADD CONSTRAINT chk_pilot_instrument_observation_item_amounts CHECK (
        tick_size > 0 AND lot_size > 0 AND minimum_order_size > 0
    );
ALTER TABLE pilot_instrument_observation_items
    ADD CONSTRAINT chk_pilot_instrument_observation_item_value_evidence CHECK (
        (minimum_order_value_evidence_class = 'VENUE_PUBLISHED'
            AND minimum_order_value > 0
            AND minimum_order_value_currency IS NOT NULL
            AND btrim(minimum_order_value_currency) <> '')
        OR (minimum_order_value_evidence_class = 'VENUE_NOT_PUBLISHED'
            AND minimum_order_value IS NULL
            AND minimum_order_value_currency IS NULL)
        OR (minimum_order_value_evidence_class = 'LEGACY_V40_REQUIRED'
            AND minimum_order_value > 0
            AND minimum_order_value_currency = 'USDT')
    );

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
    );

CREATE FUNCTION gate_y6e_guard_instrument_observation_schema_insert()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.observation_type = 'INSTRUMENT_METADATA'
        AND NEW.observation_schema_version <> 'instrument-metadata-observation.v2' THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='new instrument observations must use instrument-metadata-observation.v2';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_pilot_prerequisite_observations_v2_insert_guard
    BEFORE INSERT ON pilot_prerequisite_observations
    FOR EACH ROW EXECUTE FUNCTION gate_y6e_guard_instrument_observation_schema_insert();

CREATE FUNCTION gate_y6e_guard_instrument_item_evidence_insert()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_schema_version VARCHAR(64);
BEGIN
    SELECT observation_schema_version INTO v_schema_version
    FROM pilot_prerequisite_observations
    WHERE observation_id = NEW.observation_id AND observation_type = 'INSTRUMENT_METADATA';
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE='23503', MESSAGE='instrument observation parent does not exist';
    END IF;
    IF v_schema_version = 'instrument-metadata-observation.v1'
        AND NEW.minimum_order_value_evidence_class <> 'LEGACY_V40_REQUIRED' THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='v1 instrument items require legacy evidence marking';
    END IF;
    IF v_schema_version = 'instrument-metadata-observation.v2'
        AND NEW.minimum_order_value_evidence_class = 'LEGACY_V40_REQUIRED' THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='v2 instrument items cannot use legacy evidence marking';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_pilot_instrument_items_evidence_insert_guard
    BEFORE INSERT ON pilot_instrument_observation_items
    FOR EACH ROW EXECUTE FUNCTION gate_y6e_guard_instrument_item_evidence_insert();

CREATE FUNCTION gate_y6e_instrument_items_canonical(p_observation_id UUID)
    RETURNS TEXT LANGUAGE plpgsql STABLE STRICT AS $$
DECLARE
    v_schema_version VARCHAR(64);
    v_items TEXT;
BEGIN
    SELECT observation_schema_version INTO STRICT v_schema_version
    FROM pilot_prerequisite_observations
    WHERE observation_id = p_observation_id AND observation_type = 'INSTRUMENT_METADATA';

    IF v_schema_version = 'instrument-metadata-observation.v1' THEN
        SELECT string_agg(
            '{"symbol":' || to_json(item.symbol)::TEXT ||
            ',"tradingStatus":' || to_json(item.trading_status)::TEXT ||
            ',"tickSize":' || to_json(gate_y6d_numeric_canonical(item.tick_size))::TEXT ||
            ',"lotSize":' || to_json(gate_y6d_numeric_canonical(item.lot_size))::TEXT ||
            ',"minimumOrderSize":' || to_json(gate_y6d_numeric_canonical(item.minimum_order_size))::TEXT ||
            ',"minimumOrderValue":' || to_json(gate_y6d_numeric_canonical(item.minimum_order_value))::TEXT ||
            ',"minimumOrderValueCurrency":' || to_json(item.minimum_order_value_currency)::TEXT || '}',
            ',' ORDER BY item.symbol
        ) INTO v_items
        FROM pilot_instrument_observation_items item
        WHERE item.observation_id = p_observation_id;
    ELSE
        SELECT string_agg(
            '{"symbol":' || to_json(item.symbol)::TEXT ||
            ',"tradingStatus":' || to_json(item.trading_status)::TEXT ||
            ',"tickSize":' || to_json(gate_y6d_numeric_canonical(item.tick_size))::TEXT ||
            ',"lotSize":' || to_json(gate_y6d_numeric_canonical(item.lot_size))::TEXT ||
            ',"minimumOrderSize":' || to_json(gate_y6d_numeric_canonical(item.minimum_order_size))::TEXT ||
            ',"minimumOrderValueEvidenceClass":' ||
                to_json(item.minimum_order_value_evidence_class)::TEXT ||
            CASE WHEN item.minimum_order_value_evidence_class = 'VENUE_PUBLISHED' THEN
                ',"minimumOrderValue":' ||
                    to_json(gate_y6d_numeric_canonical(item.minimum_order_value))::TEXT ||
                ',"minimumOrderValueCurrency":' || to_json(item.minimum_order_value_currency)::TEXT
            ELSE '' END || '}',
            ',' ORDER BY item.symbol
        ) INTO v_items
        FROM pilot_instrument_observation_items item
        WHERE item.observation_id = p_observation_id;
    END IF;
    RETURN v_items;
END;
$$;

CREATE OR REPLACE FUNCTION gate_y6d_instrument_metadata_digest(p_observation_id UUID)
    RETURNS TEXT LANGUAGE SQL STABLE STRICT AS $$
    SELECT encode(digest(convert_to(
        '{"schemaVersion":' || to_json(observation.observation_schema_version)::TEXT ||
        ',"items":[' || gate_y6e_instrument_items_canonical(observation.observation_id) || ']}',
        'UTF8'), 'sha256'), 'hex')
    FROM pilot_prerequisite_observations observation
    WHERE observation.observation_id = p_observation_id
      AND observation.observation_type = 'INSTRUMENT_METADATA'
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

COMMENT ON COLUMN pilot_instrument_observation_items.minimum_order_value_evidence_class IS
    'minimum order value 证据分类：场所发布、场所未发布或仅用于无损标记 V40 历史行。';
COMMENT ON COLUMN pilot_instrument_observation_items.minimum_order_value IS
    '仅 VENUE_PUBLISHED 或 V40 历史行携带的 minimum order value；VENUE_NOT_PUBLISHED 必须为空。';
COMMENT ON COLUMN pilot_instrument_observation_items.minimum_order_value_currency IS
    '仅在 minimum order value 有正式值或 V40 历史值时保存的币种。';
COMMENT ON FUNCTION gate_y6e_guard_instrument_observation_schema_insert() IS
    '禁止 migration 后的新 production instrument observation 继续写入 v1 contract。';
COMMENT ON FUNCTION gate_y6e_guard_instrument_item_evidence_insert() IS
    '绑定 instrument observation schema 与 evidence class，禁止伪造 published 或 legacy 语义。';
COMMENT ON FUNCTION gate_y6e_instrument_items_canonical(UUID) IS
    '按 v1/v2 schema 重建确定性 instrument item canonical bytes；NOT_PUBLISHED 不编码空值字段。';
