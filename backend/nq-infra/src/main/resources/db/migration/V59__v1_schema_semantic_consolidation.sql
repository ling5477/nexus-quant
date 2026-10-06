-- 将受控执行与账户兼容约束迁移到稳定业务名称；不改变类型、状态机、金额或幂等规则。

SET LOCAL lock_timeout = '5s';

SET LOCAL statement_timeout = '60s';

ALTER TABLE operator_pilot_authorities RENAME TO operator_execution_authorities;

ALTER TABLE pilot_execution_lease_events RENAME TO controlled_execution_lease_events;

ALTER TABLE pilot_execution_lease_intents RENAME TO controlled_execution_lease_intents;

ALTER TABLE pilot_execution_leases RENAME TO controlled_execution_leases;

ALTER TABLE pilot_instrument_observation_items RENAME TO execution_instrument_observation_items;

ALTER TABLE pilot_pre_place_recovery_decisions RENAME TO execution_pre_place_recovery_decisions;

ALTER TABLE pilot_prerequisite_observations RENAME TO execution_prerequisite_observations;

ALTER TABLE pilot_scope_bindings RENAME TO execution_scope_bindings;

ALTER TABLE execution_receipts RENAME COLUMN attempt_no TO receipt_ordinal;

ALTER TABLE live_sessions RENAME COLUMN operator_pilot_authority_id TO operator_execution_authority_id;

ALTER TABLE live_sessions RENAME COLUMN operator_pilot_authority_digest TO operator_execution_authority_digest;

ALTER TABLE operator_approvals RENAME COLUMN pilot_scope_id TO execution_scope_id;

ALTER TABLE controlled_execution_leases RENAME COLUMN operator_pilot_authority_id TO operator_execution_authority_id;

ALTER TABLE execution_prerequisite_observations RENAME COLUMN pilot_scope_id TO execution_scope_id;

ALTER TABLE execution_scope_bindings RENAME COLUMN pilot_scope_id TO execution_scope_id;

ALTER TABLE execution_scope_bindings RENAME COLUMN pilot_scope_hash TO execution_scope_hash;

ALTER TABLE execution_receipts RENAME CONSTRAINT chk_execution_receipts_attempt TO chk_execution_receipts_ordinal;

ALTER TABLE execution_receipts RENAME CONSTRAINT uq_execution_receipts_attempt TO uq_execution_receipts_ordinal;

ALTER TABLE live_sessions RENAME CONSTRAINT fk_live_sessions_operator_pilot_authority TO fk_live_sessions_operator_execution_authority;

ALTER TABLE operator_approvals RENAME CONSTRAINT fk_operator_approvals_pilot_scope TO fk_operator_approvals_execution_scope;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT chk_operator_pilot_authorities_counts TO chk_operator_execution_authorities_counts;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT chk_operator_pilot_authorities_digest TO chk_operator_execution_authorities_digest;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT chk_operator_pilot_authorities_funding TO chk_operator_execution_authorities_funding;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT chk_operator_pilot_authorities_instrument TO chk_operator_execution_authorities_instrument;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT chk_operator_pilot_authorities_lifecycle TO chk_operator_execution_authorities_lifecycle;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT chk_operator_pilot_authorities_notional TO chk_operator_execution_authorities_notional;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT chk_operator_pilot_authorities_order_type TO chk_operator_execution_authorities_order_type;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT chk_operator_pilot_authorities_owner TO chk_operator_execution_authorities_owner;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT chk_operator_pilot_authorities_side TO chk_operator_execution_authorities_side;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT chk_operator_pilot_authorities_status TO chk_operator_execution_authorities_status;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT chk_operator_pilot_authorities_version TO chk_operator_execution_authorities_version;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT chk_operator_pilot_authorities_window TO chk_operator_execution_authorities_window;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT fk_operator_pilot_authorities_account TO fk_operator_execution_authorities_account;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT fk_operator_pilot_authorities_creator TO fk_operator_execution_authorities_creator;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT fk_operator_pilot_authorities_credential TO fk_operator_execution_authorities_credential;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT fk_operator_pilot_authorities_owner TO fk_operator_execution_authorities_owner;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT operator_pilot_authorities_pkey TO operator_execution_authorities_pkey;

ALTER TABLE operator_execution_authorities RENAME CONSTRAINT uq_operator_pilot_authorities_digest TO uq_operator_execution_authorities_digest;

ALTER TABLE controlled_execution_lease_events RENAME CONSTRAINT chk_pilot_execution_lease_events_status TO chk_controlled_execution_lease_events_status;

ALTER TABLE controlled_execution_lease_events RENAME CONSTRAINT chk_pilot_execution_lease_events_text TO chk_controlled_execution_lease_events_text;

ALTER TABLE controlled_execution_lease_events RENAME CONSTRAINT chk_pilot_execution_lease_events_version TO chk_controlled_execution_lease_events_version;

ALTER TABLE controlled_execution_lease_events RENAME CONSTRAINT fk_pilot_execution_lease_events_lease TO fk_controlled_execution_lease_events_lease;

ALTER TABLE controlled_execution_lease_events RENAME CONSTRAINT pilot_execution_lease_events_pkey TO controlled_execution_lease_events_pkey;

ALTER TABLE controlled_execution_lease_events RENAME CONSTRAINT uq_pilot_execution_lease_events_version TO uq_controlled_execution_lease_events_version;

ALTER TABLE controlled_execution_lease_intents RENAME CONSTRAINT chk_pilot_execution_lease_intents_action TO chk_controlled_execution_lease_intents_action;

ALTER TABLE controlled_execution_lease_intents RENAME CONSTRAINT fk_pilot_execution_lease_intents_intent TO fk_controlled_execution_lease_intents_intent;

ALTER TABLE controlled_execution_lease_intents RENAME CONSTRAINT fk_pilot_execution_lease_intents_lease TO fk_controlled_execution_lease_intents_lease;

ALTER TABLE controlled_execution_lease_intents RENAME CONSTRAINT pilot_execution_lease_intents_pkey TO controlled_execution_lease_intents_pkey;

ALTER TABLE controlled_execution_lease_intents RENAME CONSTRAINT uq_pilot_execution_lease_intents_intent TO uq_controlled_execution_lease_intents_intent;

ALTER TABLE controlled_execution_leases RENAME CONSTRAINT chk_pilot_execution_leases_digest TO chk_controlled_execution_leases_digest;

ALTER TABLE controlled_execution_leases RENAME CONSTRAINT chk_pilot_execution_leases_lifecycle_times TO chk_controlled_execution_leases_lifecycle_times;

ALTER TABLE controlled_execution_leases RENAME CONSTRAINT chk_pilot_execution_leases_notional TO chk_controlled_execution_leases_notional;

ALTER TABLE controlled_execution_leases RENAME CONSTRAINT chk_pilot_execution_leases_replacement TO chk_controlled_execution_leases_replacement;

ALTER TABLE controlled_execution_leases RENAME CONSTRAINT chk_pilot_execution_leases_status TO chk_controlled_execution_leases_status;

ALTER TABLE controlled_execution_leases RENAME CONSTRAINT chk_pilot_execution_leases_version TO chk_controlled_execution_leases_version;

ALTER TABLE controlled_execution_leases RENAME CONSTRAINT chk_pilot_execution_leases_window TO chk_controlled_execution_leases_window;

ALTER TABLE controlled_execution_leases RENAME CONSTRAINT fk_pilot_execution_leases_creator TO fk_controlled_execution_leases_creator;

ALTER TABLE controlled_execution_leases RENAME CONSTRAINT fk_pilot_execution_leases_operator_authority TO fk_controlled_execution_leases_operator_authority;

ALTER TABLE controlled_execution_leases RENAME CONSTRAINT fk_pilot_execution_leases_predecessor TO fk_controlled_execution_leases_predecessor;

ALTER TABLE controlled_execution_leases RENAME CONSTRAINT fk_pilot_execution_leases_recovery_decision TO fk_controlled_execution_leases_recovery_decision;

ALTER TABLE controlled_execution_leases RENAME CONSTRAINT fk_pilot_execution_leases_session TO fk_controlled_execution_leases_session;

ALTER TABLE controlled_execution_leases RENAME CONSTRAINT pilot_execution_leases_pkey TO controlled_execution_leases_pkey;

ALTER TABLE controlled_execution_leases RENAME CONSTRAINT uq_pilot_execution_leases_binding TO uq_controlled_execution_leases_binding;

ALTER TABLE controlled_execution_leases RENAME CONSTRAINT uq_pilot_execution_leases_session TO uq_controlled_execution_leases_session;

ALTER TABLE execution_instrument_observation_items RENAME CONSTRAINT chk_pilot_instrument_observation_item_amounts TO chk_execution_instrument_observation_item_amounts;

ALTER TABLE execution_instrument_observation_items RENAME CONSTRAINT chk_pilot_instrument_observation_item_status TO chk_execution_instrument_observation_item_status;

ALTER TABLE execution_instrument_observation_items RENAME CONSTRAINT chk_pilot_instrument_observation_item_symbol TO chk_execution_instrument_observation_item_symbol;

ALTER TABLE execution_instrument_observation_items RENAME CONSTRAINT chk_pilot_instrument_observation_item_type TO chk_execution_instrument_observation_item_type;

ALTER TABLE execution_instrument_observation_items RENAME CONSTRAINT chk_pilot_instrument_observation_item_value_evidence TO chk_execution_instrument_observation_item_value_evidence;

ALTER TABLE execution_instrument_observation_items RENAME CONSTRAINT fk_pilot_instrument_observation_items_parent TO fk_execution_instrument_observation_items_parent;

ALTER TABLE execution_instrument_observation_items RENAME CONSTRAINT pk_pilot_instrument_observation_items TO pk_execution_instrument_observation_items;

ALTER TABLE execution_pre_place_recovery_decisions RENAME CONSTRAINT chk_pilot_pre_place_recovery_decision TO chk_execution_pre_place_recovery_decision;

ALTER TABLE execution_pre_place_recovery_decisions RENAME CONSTRAINT chk_pilot_pre_place_recovery_text TO chk_execution_pre_place_recovery_text;

ALTER TABLE execution_pre_place_recovery_decisions RENAME CONSTRAINT chk_pilot_pre_place_recovery_zero_proof TO chk_execution_pre_place_recovery_zero_proof;

ALTER TABLE execution_pre_place_recovery_decisions RENAME CONSTRAINT fk_pilot_pre_place_recovery_actor TO fk_execution_pre_place_recovery_actor;

ALTER TABLE execution_pre_place_recovery_decisions RENAME CONSTRAINT fk_pilot_pre_place_recovery_lease TO fk_execution_pre_place_recovery_lease;

ALTER TABLE execution_pre_place_recovery_decisions RENAME CONSTRAINT fk_pilot_pre_place_recovery_session TO fk_execution_pre_place_recovery_session;

ALTER TABLE execution_pre_place_recovery_decisions RENAME CONSTRAINT pilot_pre_place_recovery_decisions_pkey TO execution_pre_place_recovery_decisions_pkey;

ALTER TABLE execution_pre_place_recovery_decisions RENAME CONSTRAINT uq_pilot_pre_place_recovery_predecessor TO uq_execution_pre_place_recovery_predecessor;

ALTER TABLE execution_prerequisite_observations RENAME CONSTRAINT chk_pilot_observation_hashes TO chk_execution_observation_hashes;

ALTER TABLE execution_prerequisite_observations RENAME CONSTRAINT chk_pilot_observation_text TO chk_execution_observation_text;

ALTER TABLE execution_prerequisite_observations RENAME CONSTRAINT chk_pilot_observation_type TO chk_execution_observation_type;

ALTER TABLE execution_prerequisite_observations RENAME CONSTRAINT chk_pilot_observation_variant TO chk_execution_observation_variant;

ALTER TABLE execution_prerequisite_observations RENAME CONSTRAINT fk_pilot_prerequisite_observations_scope TO fk_execution_prerequisite_observations_scope;

ALTER TABLE execution_prerequisite_observations RENAME CONSTRAINT pk_pilot_prerequisite_observations TO pk_execution_prerequisite_observations;

ALTER TABLE execution_prerequisite_observations RENAME CONSTRAINT uq_pilot_observation_id_type TO uq_execution_observation_id_type;

ALTER TABLE execution_prerequisite_observations RENAME CONSTRAINT uq_pilot_observation_set_type TO uq_execution_observation_set_type;

ALTER TABLE execution_prerequisite_observations RENAME CONSTRAINT uq_pilot_observation_source_identity TO uq_execution_observation_source_identity;

ALTER TABLE execution_scope_bindings RENAME CONSTRAINT chk_pilot_scope_bindings_age_skew TO chk_execution_scope_bindings_age_skew;

ALTER TABLE execution_scope_bindings RENAME CONSTRAINT chk_pilot_scope_bindings_digests TO chk_execution_scope_bindings_digests;

ALTER TABLE execution_scope_bindings RENAME CONSTRAINT chk_pilot_scope_bindings_fee_evidence TO chk_execution_scope_bindings_fee_evidence;

ALTER TABLE execution_scope_bindings RENAME CONSTRAINT chk_pilot_scope_bindings_schema TO chk_execution_scope_bindings_schema;

ALTER TABLE execution_scope_bindings RENAME CONSTRAINT chk_pilot_scope_bindings_text TO chk_execution_scope_bindings_text;

ALTER TABLE execution_scope_bindings RENAME CONSTRAINT chk_pilot_scope_bindings_timestamp_source TO chk_execution_scope_bindings_timestamp_source;

ALTER TABLE execution_scope_bindings RENAME CONSTRAINT fk_pilot_scope_bindings_created_by TO fk_execution_scope_bindings_created_by;

ALTER TABLE execution_scope_bindings RENAME CONSTRAINT fk_pilot_scope_bindings_session TO fk_execution_scope_bindings_session;

ALTER TABLE execution_scope_bindings RENAME CONSTRAINT pk_pilot_scope_bindings TO pk_execution_scope_bindings;

ALTER TABLE execution_scope_bindings RENAME CONSTRAINT uq_pilot_scope_bindings_approval TO uq_execution_scope_bindings_approval;

ALTER TABLE execution_scope_bindings RENAME CONSTRAINT uq_pilot_scope_bindings_session TO uq_execution_scope_bindings_session;

ALTER INDEX idx_live_sessions_operator_pilot_authority RENAME TO idx_live_sessions_operator_execution_authority;

ALTER INDEX idx_operator_pilot_authorities_scope RENAME TO idx_operator_execution_authorities_scope;

ALTER INDEX uq_operator_pilot_authorities_single_active RENAME TO uq_operator_execution_authorities_single_active;

ALTER INDEX uq_pilot_execution_lease_intents_global_cancel RENAME TO uq_controlled_execution_lease_intents_global_cancel;

ALTER INDEX uq_pilot_execution_lease_intents_global_place RENAME TO uq_controlled_execution_lease_intents_global_place;

ALTER INDEX idx_pilot_execution_leases_operator_authority RENAME TO idx_controlled_execution_leases_operator_authority;

ALTER INDEX idx_pilot_execution_leases_recovery RENAME TO idx_controlled_execution_leases_recovery;

ALTER INDEX uq_pilot_execution_leases_predecessor_successor RENAME TO uq_controlled_execution_leases_predecessor_successor;

ALTER INDEX uq_pilot_execution_leases_recovery_decision RENAME TO uq_controlled_execution_leases_recovery_decision;

ALTER INDEX uq_pilot_execution_leases_single_open RENAME TO uq_controlled_execution_leases_single_open;

ALTER INDEX uq_pilot_execution_leases_single_origin RENAME TO uq_controlled_execution_leases_single_origin;

ALTER INDEX idx_pilot_observation_fresh_lookup RENAME TO idx_execution_observation_fresh_lookup;

ALTER INDEX idx_pilot_observation_set RENAME TO idx_execution_observation_set;

ALTER INDEX idx_pilot_scope_bindings_created_at RENAME TO idx_execution_scope_bindings_created_at;

ALTER FUNCTION gate_y2_guard_execution_intent_insert() RENAME TO guard_execution_intent_insert;

ALTER FUNCTION gate_y2_guard_execution_intent_update() RENAME TO guard_execution_intent_update;

ALTER FUNCTION gate_y2_guard_live_session_insert() RENAME TO guard_live_session_insert;

ALTER FUNCTION gate_y2_guard_live_session_update() RENAME TO guard_live_session_update;

ALTER FUNCTION gate_y2_reject_fact_mutation() RENAME TO reject_live_control_fact_mutation;

ALTER FUNCTION gate_y2_require_canonical_symbol_array(p_symbols text[]) RENAME TO require_canonical_trading_symbols;

ALTER FUNCTION gate_y43_guard_market_snapshot_insert() RENAME TO guard_execution_market_snapshot_insert;

ALTER FUNCTION gate_y43_market_snapshot_digest(p_instrument text, p_best_ask numeric, p_observed_at timestamp with time zone, p_source_identity text, p_source_schema_version text) RENAME TO execution_market_snapshot_digest;

ALTER FUNCTION gate_y44_close_operator_authority_with_lease() RENAME TO close_operator_execution_authority_with_lease;

ALTER FUNCTION gate_y44_guard_operator_pilot_authority_insert() RENAME TO guard_operator_execution_authority_insert;

ALTER FUNCTION gate_y44_guard_operator_pilot_authority_update() RENAME TO guard_operator_execution_authority_update;

ALTER FUNCTION gate_y44_guard_operator_pilot_intent_count() RENAME TO guard_operator_execution_intent_count;

ALTER FUNCTION gate_y44_guard_pilot_lease_authority() RENAME TO guard_controlled_execution_lease_authority;

ALTER FUNCTION gate_y44_operator_pilot_authority_digest(p_authority_id uuid, p_owner_user_id bigint, p_exchange_account_id bigint, p_credential_reference_id bigint, p_instrument text, p_side text, p_order_type text, p_max_notional numeric, p_max_place_count integer, p_max_cancel_count integer, p_transfer_allowed boolean, p_withdraw_allowed boolean, p_valid_from timestamp with time zone, p_expires_at timestamp with time zone, p_created_by bigint, p_created_at timestamp with time zone) RENAME TO operator_execution_authority_digest;

ALTER FUNCTION gate_y45_canonical_legacy_account_code(p_exchange_account_id bigint) RENAME TO canonical_legacy_account_code;

ALTER FUNCTION gate_y45_guard_canonical_legacy_bridge() RENAME TO guard_canonical_account_compatibility_bridge;

ALTER FUNCTION gate_y45_guard_recovery_decision_insert() RENAME TO guard_execution_recovery_decision_insert;

ALTER FUNCTION gate_y45_guard_replacement_lease_insert() RENAME TO guard_execution_replacement_lease_insert;

ALTER FUNCTION gate_y45_reject_recovery_decision_mutation() RENAME TO reject_execution_recovery_decision_mutation;

ALTER FUNCTION gate_y46_attempt_execution_boundary_zero() RENAME TO controlled_execution_boundary_is_empty;

ALTER FUNCTION gate_y6d_guard_operator_approval_insert() RENAME TO guard_operator_execution_approval_insert;

ALTER FUNCTION gate_y6d_guard_pilot_scope_insert() RENAME TO guard_execution_scope_insert;

ALTER FUNCTION gate_y6d_guard_prerequisite_observation_insert() RENAME TO guard_execution_prerequisite_observation_insert;

ALTER FUNCTION gate_y6d_instant_canonical(p_value timestamp with time zone) RENAME TO execution_evidence_instant_canonical;

ALTER FUNCTION gate_y6d_instrument_metadata_digest(p_observation_id uuid) RENAME TO execution_instrument_metadata_digest;

ALTER FUNCTION gate_y6d_numeric_canonical(p_value numeric) RENAME TO execution_evidence_numeric_canonical;

ALTER FUNCTION gate_y6d_observation_payload_hash(p_observation_id uuid) RENAME TO execution_observation_payload_hash;

ALTER FUNCTION gate_y6d_pilot_scope_canonical_payload(p_session_id uuid, p_instrument_metadata_digest text, p_instrument_source_identity text, p_instrument_source_schema_version text, p_instrument_maximum_age_ms bigint, p_fee_schedule_digest text, p_fee_tier text, p_fee_evidence_class text, p_fee_source_identity text, p_fee_source_schema_version text, p_fee_maximum_age_ms bigint, p_balance_source_identity text, p_balance_source_schema_version text, p_balance_maximum_age_ms bigint, p_clock_source_identity text, p_clock_source_schema_version text, p_clock_maximum_age_ms bigint, p_signed_timestamp_source text, p_maximum_tolerated_skew_ms bigint, p_endpoint_policy_version text, p_endpoint_policy_digest text, p_provider_contract_identity text, p_provider_artifact_digest text, p_worker_identity text, p_worker_release_digest text) RENAME TO execution_scope_canonical_payload;

ALTER FUNCTION gate_y6d_pilot_scope_hash(p_session_id uuid, p_instrument_metadata_digest text, p_instrument_source_identity text, p_instrument_source_schema_version text, p_instrument_maximum_age_ms bigint, p_fee_schedule_digest text, p_fee_tier text, p_fee_evidence_class text, p_fee_source_identity text, p_fee_source_schema_version text, p_fee_maximum_age_ms bigint, p_balance_source_identity text, p_balance_source_schema_version text, p_balance_maximum_age_ms bigint, p_clock_source_identity text, p_clock_source_schema_version text, p_clock_maximum_age_ms bigint, p_signed_timestamp_source text, p_maximum_tolerated_skew_ms bigint, p_endpoint_policy_version text, p_endpoint_policy_digest text, p_provider_contract_identity text, p_provider_artifact_digest text, p_worker_identity text, p_worker_release_digest text) RENAME TO execution_scope_hash;

ALTER FUNCTION gate_y6d_validate_observation_set() RENAME TO validate_execution_observation_set;

ALTER FUNCTION gate_y6e_guard_instrument_item_evidence_insert() RENAME TO guard_execution_instrument_item_evidence_insert;

ALTER FUNCTION gate_y6e_guard_instrument_observation_schema_insert() RENAME TO guard_execution_instrument_observation_schema_insert;

ALTER FUNCTION gate_y6e_instrument_items_canonical(p_observation_id uuid) RENAME TO execution_instrument_items_canonical;

ALTER FUNCTION gate_y_minimal_pilot_guard_lease_update() RENAME TO guard_controlled_execution_lease_update;

ALTER FUNCTION gate_y_minimal_pilot_reject_fact_mutation() RENAME TO reject_controlled_execution_fact_mutation;

ALTER FUNCTION nq_sync_orders_gatee_metadata() RENAME TO sync_order_venue_metadata;

ALTER FUNCTION nq_sync_trades_gatee_metadata() RENAME TO sync_trade_venue_metadata;

CREATE OR REPLACE FUNCTION guard_execution_intent_insert()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    IF NEW.state <> 'CREATED' OR NEW.version <> 1 OR NEW.send_started_at IS NOT NULL
        OR NEW.claimed_by IS NOT NULL OR NEW.claim_token IS NOT NULL
        OR NEW.claimed_at IS NOT NULL OR NEW.lease_expires_at IS NOT NULL THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='new execution intent must start unclaimed';
    END IF;
    RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION guard_execution_intent_update()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION guard_live_session_insert()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION guard_live_session_update()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION reject_live_control_fact_mutation()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = TG_TABLE_NAME || ' is append-only or immutable';
END;
$function$;

CREATE OR REPLACE FUNCTION require_canonical_trading_symbols(p_symbols text[])
 RETURNS boolean
 LANGUAGE plpgsql
 IMMUTABLE
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION guard_execution_market_snapshot_insert()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION execution_market_snapshot_digest(p_instrument text, p_best_ask numeric, p_observed_at timestamp with time zone, p_source_identity text, p_source_schema_version text)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE STRICT
AS $function$
    SELECT encode(digest(convert_to(
        '{"schemaVersion":"market-snapshot-observation.v1"' ||
        ',"instrument":' || to_json(p_instrument)::TEXT ||
        ',"bestAsk":' || to_json(execution_evidence_numeric_canonical(p_best_ask))::TEXT ||
        ',"observedAt":' || execution_evidence_instant_canonical(p_observed_at) ||
        ',"sourceIdentity":' || to_json(p_source_identity)::TEXT ||
        ',"sourceSchemaVersion":' || to_json(p_source_schema_version)::TEXT || '}',
        'UTF8'), 'sha256'), 'hex')
$function$;

CREATE OR REPLACE FUNCTION close_operator_execution_authority_with_lease()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION guard_operator_execution_authority_insert()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION guard_operator_execution_authority_update()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION guard_operator_execution_intent_count()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION guard_controlled_execution_lease_authority()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION operator_execution_authority_digest(p_authority_id uuid, p_owner_user_id bigint, p_exchange_account_id bigint, p_credential_reference_id bigint, p_instrument text, p_side text, p_order_type text, p_max_notional numeric, p_max_place_count integer, p_max_cancel_count integer, p_transfer_allowed boolean, p_withdraw_allowed boolean, p_valid_from timestamp with time zone, p_expires_at timestamp with time zone, p_created_by bigint, p_created_at timestamp with time zone)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE STRICT
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION canonical_legacy_account_code(p_exchange_account_id bigint)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE STRICT
AS $function$
    SELECT 'nq-okx-live-' || p_exchange_account_id::TEXT
$function$;

CREATE OR REPLACE FUNCTION guard_canonical_account_compatibility_bridge()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION guard_execution_recovery_decision_insert()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION guard_execution_replacement_lease_insert()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION reject_execution_recovery_decision_mutation()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='controlled execution pre-place recovery decision is immutable';
END;
$function$;

CREATE OR REPLACE FUNCTION controlled_execution_boundary_is_empty()
 RETURNS boolean
 LANGUAGE sql
 STABLE
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION guard_operator_execution_approval_insert()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION guard_execution_scope_insert()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION guard_execution_prerequisite_observation_insert()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION execution_evidence_instant_canonical(p_value timestamp with time zone)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE STRICT
AS $function$
    SELECT to_json(to_char(p_value AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'))::TEXT
$function$;

CREATE OR REPLACE FUNCTION execution_instrument_metadata_digest(p_observation_id uuid)
 RETURNS text
 LANGUAGE sql
 STABLE STRICT
AS $function$
    SELECT encode(digest(convert_to(
        '{"schemaVersion":' || to_json(observation.observation_schema_version)::TEXT ||
        ',"items":[' || execution_instrument_items_canonical(observation.observation_id) || ']}',
        'UTF8'), 'sha256'), 'hex')
    FROM execution_prerequisite_observations observation
    WHERE observation.observation_id = p_observation_id
      AND observation.observation_type = 'INSTRUMENT_METADATA'
$function$;

CREATE OR REPLACE FUNCTION execution_evidence_numeric_canonical(p_value numeric)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE STRICT
AS $function$
    SELECT CASE
        WHEN p_value = 0 THEN '0'
        WHEN position('.' IN p_value::TEXT) = 0 THEN p_value::TEXT
        ELSE rtrim(rtrim(p_value::TEXT, '0'), '.')
    END
$function$;

CREATE OR REPLACE FUNCTION execution_observation_payload_hash(p_observation_id uuid)
 RETURNS text
 LANGUAGE plpgsql
 STABLE STRICT
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION execution_scope_canonical_payload(p_session_id uuid, p_instrument_metadata_digest text, p_instrument_source_identity text, p_instrument_source_schema_version text, p_instrument_maximum_age_ms bigint, p_fee_schedule_digest text, p_fee_tier text, p_fee_evidence_class text, p_fee_source_identity text, p_fee_source_schema_version text, p_fee_maximum_age_ms bigint, p_balance_source_identity text, p_balance_source_schema_version text, p_balance_maximum_age_ms bigint, p_clock_source_identity text, p_clock_source_schema_version text, p_clock_maximum_age_ms bigint, p_signed_timestamp_source text, p_maximum_tolerated_skew_ms bigint, p_endpoint_policy_version text, p_endpoint_policy_digest text, p_provider_contract_identity text, p_provider_artifact_digest text, p_worker_identity text, p_worker_release_digest text)
 RETURNS text
 LANGUAGE sql
 STABLE STRICT
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION execution_scope_hash(p_session_id uuid, p_instrument_metadata_digest text, p_instrument_source_identity text, p_instrument_source_schema_version text, p_instrument_maximum_age_ms bigint, p_fee_schedule_digest text, p_fee_tier text, p_fee_evidence_class text, p_fee_source_identity text, p_fee_source_schema_version text, p_fee_maximum_age_ms bigint, p_balance_source_identity text, p_balance_source_schema_version text, p_balance_maximum_age_ms bigint, p_clock_source_identity text, p_clock_source_schema_version text, p_clock_maximum_age_ms bigint, p_signed_timestamp_source text, p_maximum_tolerated_skew_ms bigint, p_endpoint_policy_version text, p_endpoint_policy_digest text, p_provider_contract_identity text, p_provider_artifact_digest text, p_worker_identity text, p_worker_release_digest text)
 RETURNS text
 LANGUAGE sql
 STABLE STRICT
AS $function$
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
$function$;

CREATE FUNCTION reconstruct_execution_scope_hash(p_execution_scope_id uuid)
 RETURNS text
 LANGUAGE sql
 STABLE STRICT
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION validate_execution_observation_set()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION guard_execution_instrument_item_evidence_insert()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION guard_execution_instrument_observation_schema_insert()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    IF NEW.observation_type = 'INSTRUMENT_METADATA'
        AND NEW.observation_schema_version <> 'instrument-metadata-observation.v2' THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='new instrument observations must use instrument-metadata-observation.v2';
    END IF;
    RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION execution_instrument_items_canonical(p_observation_id uuid)
 RETURNS text
 LANGUAGE plpgsql
 STABLE STRICT
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION guard_controlled_execution_lease_update()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION reject_controlled_execution_fact_mutation()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    RAISE EXCEPTION USING ERRCODE='23514', MESSAGE=TG_TABLE_NAME || ' is append-only';
END;
$function$;

CREATE OR REPLACE FUNCTION sync_order_venue_metadata()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION sync_trade_venue_metadata()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$;

ALTER TRIGGER trg_gate_y45_canonical_legacy_bridge ON exchange_accounts RENAME TO trg_canonical_legacy_bridge;

ALTER TRIGGER trg_gate_y45_canonical_legacy_bridge_insert ON exchange_accounts RENAME TO trg_canonical_legacy_bridge_insert;

ALTER TRIGGER trg_operator_approvals_gate_y6d_insert_guard ON operator_approvals RENAME TO trg_operator_approvals_insert_guard;

ALTER TRIGGER trg_operator_pilot_authorities_insert_guard ON operator_execution_authorities RENAME TO trg_operator_execution_authorities_insert_guard;

ALTER TRIGGER trg_operator_pilot_authorities_update_guard ON operator_execution_authorities RENAME TO trg_operator_execution_authorities_update_guard;

ALTER TRIGGER trg_orders_gatee_metadata ON orders RENAME TO trg_orders_venue_metadata;

ALTER TRIGGER trg_pilot_execution_lease_events_append_only ON controlled_execution_lease_events RENAME TO trg_controlled_execution_lease_events_append_only;

ALTER TRIGGER trg_gate_y44_operator_pilot_intent_count ON controlled_execution_lease_intents RENAME TO trg_operator_execution_intent_count;

ALTER TRIGGER trg_pilot_execution_lease_intents_append_only ON controlled_execution_lease_intents RENAME TO trg_controlled_execution_lease_intents_append_only;

ALTER TRIGGER trg_gate_y44_close_operator_authority_with_lease ON controlled_execution_leases RENAME TO trg_close_operator_authority_with_lease;

ALTER TRIGGER trg_gate_y44_pilot_lease_authority ON controlled_execution_leases RENAME TO trg_controlled_execution_lease_authority;

ALTER TRIGGER trg_gate_y45_replacement_lease_insert ON controlled_execution_leases RENAME TO trg_replacement_lease_insert;

ALTER TRIGGER trg_pilot_execution_leases_guard ON controlled_execution_leases RENAME TO trg_controlled_execution_leases_guard;

ALTER TRIGGER trg_pilot_instrument_items_complete ON execution_instrument_observation_items RENAME TO trg_execution_instrument_items_complete;

ALTER TRIGGER trg_pilot_instrument_items_evidence_insert_guard ON execution_instrument_observation_items RENAME TO trg_execution_instrument_items_evidence_insert_guard;

ALTER TRIGGER trg_pilot_instrument_observation_items_append_only ON execution_instrument_observation_items RENAME TO trg_execution_instrument_observation_items_append_only;

ALTER TRIGGER trg_gate_y45_recovery_decision_immutable ON execution_pre_place_recovery_decisions RENAME TO trg_recovery_decision_immutable;

ALTER TRIGGER trg_gate_y45_recovery_decision_insert ON execution_pre_place_recovery_decisions RENAME TO trg_recovery_decision_insert;

ALTER TRIGGER trg_gate_y43_market_snapshot_insert ON execution_prerequisite_observations RENAME TO trg_market_snapshot_insert;

ALTER TRIGGER trg_pilot_observation_set_complete ON execution_prerequisite_observations RENAME TO trg_execution_observation_set_complete;

ALTER TRIGGER trg_pilot_prerequisite_observations_append_only ON execution_prerequisite_observations RENAME TO trg_execution_prerequisite_observations_append_only;

ALTER TRIGGER trg_pilot_prerequisite_observations_insert_guard ON execution_prerequisite_observations RENAME TO trg_execution_prerequisite_observations_insert_guard;

ALTER TRIGGER trg_pilot_prerequisite_observations_v2_insert_guard ON execution_prerequisite_observations RENAME TO trg_execution_prerequisite_observations_v2_insert_guard;

ALTER TRIGGER trg_pilot_scope_bindings_immutable ON execution_scope_bindings RENAME TO trg_execution_scope_bindings_immutable;

ALTER TRIGGER trg_pilot_scope_bindings_insert_guard ON execution_scope_bindings RENAME TO trg_execution_scope_bindings_insert_guard;

ALTER TRIGGER trg_trades_gatee_metadata ON trades RENAME TO trg_trades_venue_metadata;

DROP FUNCTION gate_y6d_reconstruct_pilot_scope_hash(uuid) RESTRICT;

ALTER TABLE execution_instrument_observation_items RENAME CONSTRAINT trg_pilot_instrument_items_complete TO trg_execution_instrument_items_complete;

ALTER TABLE execution_prerequisite_observations RENAME CONSTRAINT trg_pilot_observation_set_complete TO trg_execution_observation_set_complete;

ALTER TABLE live_sessions DROP CONSTRAINT chk_live_sessions_authority_semantics;

ALTER TABLE live_sessions ADD CONSTRAINT chk_live_sessions_authority_semantics CHECK (authority_type::text = 'STRATEGY'::text AND strategy_release_id IS NOT NULL AND release_digest IS NOT NULL AND release_admission_revision IS NOT NULL AND risk_limit_set_id IS NOT NULL AND risk_limit_set_digest IS NOT NULL AND operator_execution_authority_id IS NULL AND operator_execution_authority_digest IS NULL AND approval_scope_schema_version::text = 'approval-scope.v1'::text OR authority_type::text = 'OPERATOR_CONTROLLED_EXECUTION'::text AND strategy_release_id IS NULL AND release_digest IS NULL AND release_admission_revision IS NULL AND risk_limit_set_id IS NULL AND risk_limit_set_digest IS NULL AND operator_execution_authority_id IS NOT NULL AND operator_execution_authority_digest IS NOT NULL AND approval_scope_schema_version::text = 'approval-scope.operator.v1'::text);

ALTER TABLE live_sessions DROP CONSTRAINT chk_live_sessions_authority_type;

ALTER TABLE live_sessions ADD CONSTRAINT chk_live_sessions_authority_type CHECK (authority_type::text = ANY (ARRAY['STRATEGY'::character varying, 'OPERATOR_CONTROLLED_EXECUTION'::character varying]::text[]));

ALTER TABLE operator_approvals DROP CONSTRAINT chk_operator_approvals_scope_version;

ALTER TABLE operator_approvals ADD CONSTRAINT chk_operator_approvals_scope_version CHECK (scope_schema_version::text = 'approval-scope.v1'::text AND execution_scope_id IS NULL OR scope_schema_version::text = 'execution-scope.v1'::text AND execution_scope_id IS NOT NULL);

ALTER TABLE execution_instrument_observation_items DROP CONSTRAINT chk_execution_instrument_observation_item_value_evidence;

ALTER TABLE execution_instrument_observation_items ADD CONSTRAINT chk_execution_instrument_observation_item_value_evidence CHECK (minimum_order_value_evidence_class::text = 'VENUE_PUBLISHED'::text AND minimum_order_value > 0::numeric AND minimum_order_value_currency IS NOT NULL AND btrim(minimum_order_value_currency::text) <> ''::text OR minimum_order_value_evidence_class::text = 'VENUE_NOT_PUBLISHED'::text AND minimum_order_value IS NULL AND minimum_order_value_currency IS NULL OR minimum_order_value_evidence_class::text = 'LEGACY_MINIMUM_EVIDENCE_REQUIRED'::text AND minimum_order_value > 0::numeric AND minimum_order_value_currency::text = 'USDT'::text);

ALTER TABLE execution_scope_bindings DROP CONSTRAINT chk_execution_scope_bindings_schema;

ALTER TABLE execution_scope_bindings ADD CONSTRAINT chk_execution_scope_bindings_schema CHECK (scope_schema_version::text = 'execution-scope.v1'::text);

COMMENT ON TABLE execution_intents IS '外部执行意图事实；claim、send 和 reconcile 必须遵守事务、租约与查询恢复约束。';

COMMENT ON TABLE execution_receipts IS '脱敏且仅追加的外部执行回执；禁止保存原始报文、header、签名或凭证。';

COMMENT ON TABLE live_sessions IS 'LIVE 控制会话聚合；记录控制事实，不能替代有效交易授权、风控或执行准入。';

COMMENT ON TABLE operator_execution_authorities IS '人工受控执行的显式授权；名义金额和动作次数有硬上限，不保存凭证且不替代风控。';

COMMENT ON TABLE controlled_execution_leases IS '持久化的一次性受控执行租约；只提供有界执行窗口，不保存凭证或场所原文。';

COMMENT ON TABLE execution_pre_place_recovery_decisions IS '仅追加的零执行事实恢复判定；租约再生不授权 PLACE 重试。';

COMMENT ON TABLE execution_prerequisite_observations IS '类型化且仅追加的执行前置观测；不得保存凭证或场所原始报文。';

COMMENT ON TABLE execution_scope_bindings IS '会话绑定的不可变执行范围；不替代 LIVE、交易和 kill switch 的独立准入。';

COMMENT ON TABLE risk_limit_sets IS 'LIVE 会话的不可变风险规则定义；区别于运行期风险判定，不保存凭证、余额或行情原文。';

COMMENT ON TABLE shadow_consistency_reports IS 'Paper vs Shadow 一致性报告表：只表达复盘和差异分析，不表达交易授权，不表示 LIVE ready，不保存 credential material';

COMMENT ON TABLE shadow_run_events IS 'Shadow Run append-only 事件表：记录状态流转、阻断、失败、非法流转尝试和审计事件；不保存 credential material，不调用真实交易';

COMMENT ON TABLE shadow_run_snapshots IS 'Shadow Run 快照表：保存输入行情、策略决策、风险预检和订单意图预览的本地脱敏快照；不保存 credential material，不保存真实订单状态，不代表交易授权';

COMMENT ON TABLE shadow_runs IS 'Shadow Run 本地事实主表：记录一次无真实交易副作用的影子运行状态和追溯链；不代表交易授权，不保存 credential material，不代表 LIVE ready，不产生真实交易副作用';

COMMENT ON TABLE strategy_release_admission_state IS '策略发布准入的一致性状态；未绑定身份时 revision 为零且身份字段为空，不推测或回填 digest。';

COMMENT ON TABLE validation_review_cases IS '本地人工复核 case 主事实；只记录诊断与人工复核生命周期，不代表交易授权，不表示 LIVE ready，不修改策略、Paper、Shadow、risk、account、order 或 ledger，不保存 credential material';

COMMENT ON TABLE validation_review_events IS '本地人工复核 append-only lifecycle event；只记录 accepted transition，不代表交易授权，不表示 LIVE ready，不修改任何交易或运行事实，不保存 credential material';

COMMENT ON COLUMN exchange_accounts.legacy_account_id IS '映射 accounts.account_id 的兼容身份桥接；绑定后不可变，必须满足环境、场所和账户状态约束。';

COMMENT ON COLUMN execution_intents.action IS '外部执行动作：PLACE 或 CANCEL；均须经过授权、风控和幂等执行路径。';

COMMENT ON COLUMN execution_intents.state IS '执行意图的 claim、send 和 reconcile 状态；迁移须满足版本、租约和终态约束。';

COMMENT ON COLUMN execution_intents.claimed_by IS '有界执行 worker 标识；必须与 claim token 和有效租约一致。';

COMMENT ON COLUMN execution_intents.claim_token IS '本次 claim 的不可复用身份；防止其他执行者跨租约写入。';

COMMENT ON COLUMN execution_intents.claimed_at IS '本次执行 claim 的时间，受租约有效期约束。';

COMMENT ON COLUMN execution_intents.lease_expires_at IS '执行 claim 的到期边界；过期不能继续发送或提交执行事实。';

COMMENT ON COLUMN execution_receipts.receipt_ordinal IS '同一意图内的网络回执正整数序号；与意图身份共同保持唯一。';

COMMENT ON COLUMN instrument_catalog.max_market_size_unit IS 'max_market_size 的单位；OKX Spot 仅允许 USDT。';

COMMENT ON COLUMN live_sessions.authority_type IS '互斥授权类型：STRATEGY 或 OPERATOR_CONTROLLED_EXECUTION。';

COMMENT ON COLUMN live_sessions.operator_execution_authority_id IS '人工受控执行会话的显式授权引用；策略会话必须为空。';

COMMENT ON COLUMN operator_approvals.scope_schema_version IS '审批摘要对应的编码版本；基础审批与精确执行范围审批互斥。';

COMMENT ON COLUMN operator_approvals.execution_scope_id IS '精确执行范围审批的外键；基础审批必须为空，不能授权精确执行范围。';

COMMENT ON COLUMN operator_execution_authorities.authority_id IS '不可复用的人工执行授权 UUID。';

COMMENT ON COLUMN operator_execution_authorities.max_notional IS '人工受控执行名义金额硬上限，不得超过 10 USDT。';

COMMENT ON COLUMN operator_execution_authorities.canonical_digest IS 'operator-execution-authority.v1 确定性编码的 lowercase SHA-256。';

COMMENT ON COLUMN controlled_execution_lease_events.lease_id IS '所属受控执行租约。';

COMMENT ON COLUMN controlled_execution_lease_intents.lease_id IS '所属受控执行租约；动作关联保持幂等与全局次数限制。';

COMMENT ON COLUMN controlled_execution_leases.binding_id IS '绑定的精确执行身份，不可修改或复用。';

COMMENT ON COLUMN controlled_execution_leases.binding_digest IS '精确执行绑定确定性编码的 lowercase SHA-256。';

COMMENT ON COLUMN controlled_execution_leases.max_notional IS '人工授权的名义金额硬上限，精度为 NUMERIC(38,8)。';

COMMENT ON COLUMN controlled_execution_leases.created_by IS '发起受控执行的现有 OPERATOR 用户身份。';

COMMENT ON COLUMN controlled_execution_leases.operator_execution_authority_id IS '人工执行租约绑定的同一显式授权；策略租约为空。';

COMMENT ON COLUMN controlled_execution_leases.replacement_reason IS '再生原因：零执行失败或发送前终态再生；新后继固定为 PRE_PLACE_TERMINAL_REGENERATION。';

COMMENT ON COLUMN execution_instrument_observation_items.minimum_order_value IS '仅 VENUE_PUBLISHED 或 既有兼容证据携带的 minimum order value；VENUE_NOT_PUBLISHED 必须为空。';

COMMENT ON COLUMN execution_instrument_observation_items.minimum_order_value_currency IS '仅在 minimum order value 有正式值或 既有兼容证据时保存的币种。';

COMMENT ON COLUMN execution_instrument_observation_items.minimum_order_value_evidence_class IS 'minimum order value 证据分类：场所发布、场所未发布或仅用于无损标记 既有兼容证据。';

COMMENT ON COLUMN execution_pre_place_recovery_decisions.decision IS '恢复判定允许零意图替换或发送前再生；新判定固定为 PRE_PLACE_REGENERATION_ALLOWED。';

COMMENT ON COLUMN execution_prerequisite_observations.execution_scope_id IS '所属不可变执行范围。';

COMMENT ON COLUMN execution_scope_bindings.execution_scope_id IS '不可复用的执行范围 UUID。';

COMMENT ON COLUMN execution_scope_bindings.scope_schema_version IS '确定性编码版本，固定为 execution-scope.v1。';

COMMENT ON COLUMN execution_scope_bindings.execution_scope_hash IS 'execution-scope.v1 确定性 UTF-8 编码的 lowercase SHA-256。';

COMMENT ON COLUMN strategy_release_admission_state.guard_schema_version IS '发布准入 guard 持久化结构版本，固定为 1。';

COMMENT ON COLUMN validation_review_cases.tenant_key IS '服务端提供的租户隔离键，固定为 NQ_LOCAL；客户端不得覆盖。';

COMMENT ON COLUMN validation_review_cases.retention_until IS '关闭后的保留期限；不表示存在自动删除或归档任务。';

COMMENT ON COLUMN validation_review_events.to_state IS '被接受的合法状态迁移结果，不包含批准、授权或可交易语义。';

COMMENT ON CONSTRAINT chk_controlled_execution_leases_replacement ON controlled_execution_leases IS '序号零为起始租约，正序号为发送前终态再生；原因取值保持既有只读兼容约束。';

COMMENT ON INDEX uq_controlled_execution_leases_single_origin IS '只允许一个起始租约；后续租约必须形成不可分叉的 lineage。';

COMMENT ON FUNCTION guard_execution_intent_update() IS '保护执行意图不可变事实、版本和状态迁移；不能绕过租约或发送边界。';

COMMENT ON FUNCTION guard_live_session_update() IS '保护会话身份、版本、事件序列和合法状态迁移；执行范围不可就地修改。';

COMMENT ON FUNCTION reject_live_control_fact_mutation() IS '数据库层拒绝不可变及仅追加事实的 UPDATE 和 DELETE。';

COMMENT ON FUNCTION require_canonical_trading_symbols(p_symbols text[]) IS '验证交易标的大写、BASE-USDT、排序与唯一性约束。';

COMMENT ON FUNCTION operator_execution_authority_digest(p_authority_id uuid, p_owner_user_id bigint, p_exchange_account_id bigint, p_credential_reference_id bigint, p_instrument text, p_side text, p_order_type text, p_max_notional numeric, p_max_place_count integer, p_max_cancel_count integer, p_transfer_allowed boolean, p_withdraw_allowed boolean, p_valid_from timestamp with time zone, p_expires_at timestamp with time zone, p_created_by bigint, p_created_at timestamp with time zone) IS '按 Java operator-execution-authority.v1 字节合同重建 lowercase SHA-256。';

COMMENT ON FUNCTION controlled_execution_boundary_is_empty() IS '判断受控执行边界是否完全为空；发送开始前不得有意图、回执、订单、成交或账务事实。';

COMMENT ON FUNCTION reconstruct_execution_scope_hash(p_execution_scope_id uuid) IS '从权威会话与不可变执行范围重建 execution-scope.v1 SHA-256。';
