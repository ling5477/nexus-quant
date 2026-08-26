-- GateY Attempt-01：generalized pre-PLACE terminal lease regeneration。
-- Lease只表示短时执行窗口；全attempt的global PLACE unique index继续承担exactly-once最终防线。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

ALTER TABLE pilot_execution_leases
    DROP CONSTRAINT chk_pilot_execution_leases_replacement;
ALTER TABLE pilot_execution_leases
    ALTER COLUMN replacement_ordinal TYPE INTEGER USING replacement_ordinal::INTEGER;
ALTER TABLE pilot_execution_leases
    ADD CONSTRAINT chk_pilot_execution_leases_replacement CHECK (
        (replacement_ordinal = 0 AND predecessor_lease_id IS NULL
            AND recovery_decision_id IS NULL AND replacement_reason IS NULL)
        OR
        (replacement_ordinal > 0 AND predecessor_lease_id IS NOT NULL
            AND recovery_decision_id IS NOT NULL
            AND replacement_reason IN (
                'PRE_PLACE_ZERO_INTENT_FAILURE',
                'PRE_PLACE_TERMINAL_REGENERATION'
            ))
    );

DROP INDEX uq_pilot_execution_leases_single_replacement;
CREATE UNIQUE INDEX uq_pilot_execution_leases_single_origin
    ON pilot_execution_leases ((1)) WHERE predecessor_lease_id IS NULL;
CREATE UNIQUE INDEX uq_pilot_execution_leases_recovery_decision
    ON pilot_execution_leases(recovery_decision_id) WHERE recovery_decision_id IS NOT NULL;

ALTER TABLE pilot_pre_place_recovery_decisions
    DROP CONSTRAINT chk_pilot_pre_place_recovery_decision;
ALTER TABLE pilot_pre_place_recovery_decisions
    ADD CONSTRAINT chk_pilot_pre_place_recovery_decision CHECK (
        decision IN ('REPLACEMENT_ALLOWED_ZERO_INTENT','PRE_PLACE_REGENERATION_ALLOWED')
    );

CREATE FUNCTION gate_y46_attempt_execution_boundary_zero()
    RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT
        NOT EXISTS (
            SELECT 1 FROM pilot_execution_lease_intents WHERE action='PLACE'
        )
        AND NOT EXISTS (
            SELECT 1 FROM execution_intents intent
            JOIN live_sessions session ON session.session_id=intent.session_id
            WHERE session.authority_type='OPERATOR_PILOT'
        )
        AND NOT EXISTS (
            SELECT 1 FROM execution_receipts receipt
            JOIN execution_intents intent ON intent.intent_id=receipt.intent_id
            JOIN live_sessions session ON session.session_id=intent.session_id
            WHERE session.authority_type='OPERATOR_PILOT'
        )
        AND NOT EXISTS (
            SELECT 1 FROM orders local_order
            JOIN execution_intents intent ON intent.local_order_id=local_order.order_id
            JOIN live_sessions session ON session.session_id=intent.session_id
            WHERE session.authority_type='OPERATOR_PILOT'
        )
        AND NOT EXISTS (
            SELECT 1 FROM trades trade
            JOIN orders local_order ON local_order.order_id=trade.order_id
            JOIN execution_intents intent ON intent.local_order_id=local_order.order_id
            JOIN live_sessions session ON session.session_id=intent.session_id
            WHERE session.authority_type='OPERATOR_PILOT'
        )
        AND NOT EXISTS (
            SELECT 1 FROM ledger_entries ledger
            JOIN orders local_order ON ledger.ref_id=local_order.order_id
            JOIN execution_intents intent ON intent.local_order_id=local_order.order_id
            JOIN live_sessions session ON session.session_id=intent.session_id
            WHERE session.authority_type='OPERATOR_PILOT'
            UNION ALL
            SELECT 1 FROM ledger_entries ledger
            JOIN trades trade ON ledger.ref_id=trade.trade_id
            JOIN orders local_order ON local_order.order_id=trade.order_id
            JOIN execution_intents intent ON intent.local_order_id=local_order.order_id
            JOIN live_sessions session ON session.session_id=intent.session_id
            WHERE session.authority_type='OPERATOR_PILOT'
        )
$$;

CREATE OR REPLACE FUNCTION gate_y45_guard_recovery_decision_insert()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_lease pilot_execution_leases%ROWTYPE;
    v_place INTEGER;
    v_send INTEGER;
    v_intent INTEGER;
    v_receipt INTEGER;
    v_order INTEGER;
    v_trade INTEGER;
    v_ledger INTEGER;
BEGIN
    SELECT * INTO STRICT v_lease FROM pilot_execution_leases
    WHERE lease_id=NEW.predecessor_lease_id FOR UPDATE;
    IF NEW.decision <> 'PRE_PLACE_REGENERATION_ALLOWED'
        OR v_lease.live_session_id <> NEW.predecessor_session_id
        OR v_lease.created_by <> NEW.decided_by
        OR v_lease.status NOT IN ('EXPIRED','FAILED')
        OR v_lease.consumed_at IS NOT NULL
        OR EXISTS (SELECT 1 FROM pilot_execution_leases
                   WHERE status IN ('CREATED','ACTIVE','CONSUMED'))
        OR EXISTS (SELECT 1 FROM pilot_execution_leases
                   WHERE predecessor_lease_id=v_lease.lease_id)
        OR NOT gate_y46_attempt_execution_boundary_zero() THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='PRE_PLACE_REGENERATION_FORBIDDEN';
    END IF;

    SELECT count(*) INTO v_place
    FROM pilot_execution_lease_intents WHERE action='PLACE';
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
    WHERE session.authority_type='OPERATOR_PILOT';
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

CREATE OR REPLACE FUNCTION gate_y45_guard_replacement_lease_insert()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_predecessor pilot_execution_leases%ROWTYPE;
    v_old_session live_sessions%ROWTYPE;
    v_new_session live_sessions%ROWTYPE;
    v_decision pilot_pre_place_recovery_decisions%ROWTYPE;
BEGIN
    IF NEW.predecessor_lease_id IS NULL THEN
        IF NEW.replacement_ordinal <> 0 OR EXISTS (SELECT 1 FROM pilot_execution_leases) THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='original pilot lease already exists';
        END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO STRICT v_predecessor FROM pilot_execution_leases
    WHERE lease_id=NEW.predecessor_lease_id FOR UPDATE;
    SELECT * INTO STRICT v_decision FROM pilot_pre_place_recovery_decisions
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
        OR v_old_session.authority_type <> 'OPERATOR_PILOT'
        OR v_new_session.authority_type <> 'OPERATOR_PILOT'
        OR v_old_session.owner_id <> v_new_session.owner_id
        OR v_old_session.exchange_account_id <> v_new_session.exchange_account_id
        OR v_old_session.credential_reference <> v_new_session.credential_reference
        OR v_old_session.symbol_allowlist <> v_new_session.symbol_allowlist
        OR v_old_session.capital_cap <> v_new_session.capital_cap
        OR v_predecessor.max_notional <> NEW.max_notional
        OR v_predecessor.created_by <> NEW.created_by
        OR NEW.replacement_ordinal <> v_predecessor.replacement_ordinal + 1
        OR NEW.replacement_reason <> 'PRE_PLACE_TERMINAL_REGENERATION'
        OR EXISTS (SELECT 1 FROM pilot_execution_leases
                   WHERE status IN ('CREATED','ACTIVE','CONSUMED'))
        OR EXISTS (SELECT 1 FROM pilot_execution_leases
                   WHERE predecessor_lease_id=v_predecessor.lease_id)
        OR NOT gate_y46_attempt_execution_boundary_zero() THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='terminal lease regeneration proof failed';
    END IF;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION gate_y46_attempt_execution_boundary_zero() IS
    'Attempt-01执行边界全局零事实判定；provider PLACE只能发生在durable SEND_STARTED之后，因此SEND_STARTED=0同时证明provider PLACE=0。';
COMMENT ON CONSTRAINT chk_pilot_execution_leases_replacement ON pilot_execution_leases IS
    'ordinal0为origin；任意正ordinal为terminal pre-PLACE regeneration，历史V45 reason继续只读保留。';
COMMENT ON COLUMN pilot_execution_leases.replacement_ordinal IS
    '不可由caller任意选择；successor insert trigger强制等于predecessor ordinal + 1。';
COMMENT ON COLUMN pilot_execution_leases.replacement_reason IS
    'V45历史值PRE_PLACE_ZERO_INTENT_FAILURE只读保留；V46新successor固定PRE_PLACE_TERMINAL_REGENERATION。';
COMMENT ON COLUMN pilot_pre_place_recovery_decisions.decision IS
    'V45历史REPLACEMENT_ALLOWED_ZERO_INTENT只读保留；V46新判定固定PRE_PLACE_REGENERATION_ALLOWED。';
COMMENT ON INDEX uq_pilot_execution_leases_single_origin IS
    'Attempt-01只有一个origin lease；后续lease必须形成不可分叉lineage。';
COMMENT ON INDEX uq_pilot_execution_leases_recovery_decision IS
    '每个append-only regeneration decision最多物化一个successor lease。';
COMMENT ON INDEX uq_pilot_execution_leases_predecessor_successor IS
    '每个terminal predecessor最多一个successor，禁止lineage分叉。';
