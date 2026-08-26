-- GateY Attempt-01：只允许一次零intent的pre-PLACE recovery；不授权第二次PLACE。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

ALTER TABLE exchange_accounts
    ADD CONSTRAINT fk_exchange_accounts_canonical_legacy_account
        FOREIGN KEY (legacy_account_id) REFERENCES accounts(account_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE pilot_execution_leases
    ADD COLUMN predecessor_lease_id UUID,
    ADD COLUMN recovery_decision_id UUID,
    ADD COLUMN replacement_ordinal SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN replacement_reason VARCHAR(64),
    ADD CONSTRAINT fk_pilot_execution_leases_predecessor
        FOREIGN KEY (predecessor_lease_id) REFERENCES pilot_execution_leases(lease_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT chk_pilot_execution_leases_replacement CHECK (
        (replacement_ordinal = 0 AND predecessor_lease_id IS NULL
            AND recovery_decision_id IS NULL AND replacement_reason IS NULL)
        OR
        (replacement_ordinal = 1 AND predecessor_lease_id IS NOT NULL
            AND recovery_decision_id IS NOT NULL
            AND replacement_reason = 'PRE_PLACE_ZERO_INTENT_FAILURE')
    );

DROP INDEX uq_pilot_execution_leases_single_pilot;
CREATE UNIQUE INDEX uq_pilot_execution_leases_single_open
    ON pilot_execution_leases ((1))
    WHERE status IN ('CREATED','ACTIVE','CONSUMED');
CREATE UNIQUE INDEX uq_pilot_execution_leases_single_replacement
    ON pilot_execution_leases ((1)) WHERE predecessor_lease_id IS NOT NULL;
CREATE UNIQUE INDEX uq_pilot_execution_leases_predecessor_successor
    ON pilot_execution_leases(predecessor_lease_id) WHERE predecessor_lease_id IS NOT NULL;
CREATE UNIQUE INDEX uq_pilot_execution_lease_intents_global_place
    ON pilot_execution_lease_intents ((1)) WHERE action = 'PLACE';
CREATE UNIQUE INDEX uq_pilot_execution_lease_intents_global_cancel
    ON pilot_execution_lease_intents ((1)) WHERE action = 'CANCEL';

CREATE TABLE pilot_pre_place_recovery_decisions (
    decision_id UUID PRIMARY KEY,
    predecessor_lease_id UUID NOT NULL,
    predecessor_session_id UUID NOT NULL,
    decision VARCHAR(64) NOT NULL,
    place_intent_count INTEGER NOT NULL,
    send_started_count INTEGER NOT NULL,
    execution_intent_count INTEGER NOT NULL,
    execution_receipt_count INTEGER NOT NULL,
    order_count INTEGER NOT NULL,
    trade_count INTEGER NOT NULL,
    ledger_count INTEGER NOT NULL,
    decided_by BIGINT NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_pilot_pre_place_recovery_lease FOREIGN KEY (predecessor_lease_id)
        REFERENCES pilot_execution_leases(lease_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_pilot_pre_place_recovery_session FOREIGN KEY (predecessor_session_id)
        REFERENCES live_sessions(session_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_pilot_pre_place_recovery_actor FOREIGN KEY (decided_by)
        REFERENCES users(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uq_pilot_pre_place_recovery_predecessor UNIQUE (predecessor_lease_id),
    CONSTRAINT chk_pilot_pre_place_recovery_decision CHECK (
        decision = 'REPLACEMENT_ALLOWED_ZERO_INTENT'
    ),
    CONSTRAINT chk_pilot_pre_place_recovery_zero_proof CHECK (
        place_intent_count = 0 AND send_started_count = 0
        AND execution_intent_count = 0 AND execution_receipt_count = 0
        AND order_count = 0 AND trade_count = 0 AND ledger_count = 0
    ),
    CONSTRAINT chk_pilot_pre_place_recovery_text CHECK (
        btrim(request_id) <> '' AND btrim(trace_id) <> ''
    )
);

ALTER TABLE pilot_execution_leases
    ADD CONSTRAINT fk_pilot_execution_leases_recovery_decision
        FOREIGN KEY (recovery_decision_id)
        REFERENCES pilot_pre_place_recovery_decisions(decision_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

CREATE FUNCTION gate_y45_guard_recovery_decision_insert()
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
    WHERE lease_id = NEW.predecessor_lease_id FOR UPDATE;
    IF v_lease.live_session_id <> NEW.predecessor_session_id
        OR v_lease.created_by <> NEW.decided_by
        OR v_lease.status NOT IN ('EXPIRED','FAILED')
        OR v_lease.consumed_at IS NOT NULL
        OR EXISTS (SELECT 1 FROM pilot_execution_leases
                   WHERE status IN ('CREATED','ACTIVE','CONSUMED'))
        OR EXISTS (SELECT 1 FROM pilot_execution_leases
                   WHERE predecessor_lease_id IS NOT NULL) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='REPLACEMENT_FORBIDDEN_STATE_AMBIGUOUS';
    END IF;

    SELECT count(*) FILTER (WHERE link.action='PLACE'),
           count(*) FILTER (WHERE intent.send_started_at IS NOT NULL),
           count(DISTINCT intent.intent_id),
           count(DISTINCT receipt.receipt_id),
           count(DISTINCT orders.order_id),
           count(DISTINCT trade.trade_id),
           count(DISTINCT ledger.entry_id)
    INTO v_place,v_send,v_intent,v_receipt,v_order,v_trade,v_ledger
    FROM live_sessions session
    LEFT JOIN execution_intents intent ON intent.session_id=session.session_id
    LEFT JOIN pilot_execution_lease_intents link ON link.intent_id=intent.intent_id
    LEFT JOIN execution_receipts receipt ON receipt.intent_id=intent.intent_id
    LEFT JOIN orders ON orders.order_id=intent.local_order_id
    LEFT JOIN trades trade ON trade.order_id=orders.order_id
    LEFT JOIN ledger_entries ledger
      ON ledger.ref_id=orders.order_id OR ledger.ref_id=trade.trade_id
    WHERE session.session_id=v_lease.live_session_id;
    v_place := COALESCE(v_place,0); v_send := COALESCE(v_send,0);
    v_intent := COALESCE(v_intent,0); v_receipt := COALESCE(v_receipt,0);
    v_order := COALESCE(v_order,0); v_trade := COALESCE(v_trade,0);
    v_ledger := COALESCE(v_ledger,0);
    IF v_place <> 0 OR v_send <> 0 OR v_intent <> 0 OR v_receipt <> 0
        OR v_order <> 0 OR v_trade <> 0 OR v_ledger <> 0 THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='REPLACEMENT_FORBIDDEN_SIDE_EFFECT_STARTED';
    END IF;
    IF (NEW.place_intent_count,NEW.send_started_count,NEW.execution_intent_count,
        NEW.execution_receipt_count,NEW.order_count,NEW.trade_count,NEW.ledger_count)
       IS DISTINCT FROM (v_place,v_send,v_intent,v_receipt,v_order,v_trade,v_ledger) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='REPLACEMENT_FORBIDDEN_STATE_AMBIGUOUS';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_gate_y45_recovery_decision_insert
    BEFORE INSERT ON pilot_pre_place_recovery_decisions
    FOR EACH ROW EXECUTE FUNCTION gate_y45_guard_recovery_decision_insert();

CREATE FUNCTION gate_y45_reject_recovery_decision_mutation()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='pilot pre-place recovery decision is immutable';
END;
$$;

CREATE TRIGGER trg_gate_y45_recovery_decision_immutable
    BEFORE UPDATE OR DELETE ON pilot_pre_place_recovery_decisions
    FOR EACH ROW EXECUTE FUNCTION gate_y45_reject_recovery_decision_mutation();

CREATE FUNCTION gate_y45_guard_replacement_lease_insert()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_predecessor pilot_execution_leases%ROWTYPE;
    v_old_session live_sessions%ROWTYPE;
    v_new_session live_sessions%ROWTYPE;
    v_decision pilot_pre_place_recovery_decisions%ROWTYPE;
BEGIN
    IF NEW.predecessor_lease_id IS NULL THEN
        IF EXISTS (SELECT 1 FROM pilot_execution_leases) THEN
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
        OR v_decision.decision <> 'REPLACEMENT_ALLOWED_ZERO_INTENT'
        OR v_predecessor.status NOT IN ('EXPIRED','FAILED')
        OR v_predecessor.consumed_at IS NOT NULL
        OR v_old_session.state <> 'LIVE_RECONCILED'
        OR v_old_session.authority_type <> 'OPERATOR_PILOT'
        OR v_new_session.authority_type <> 'OPERATOR_PILOT'
        OR v_old_session.owner_id <> v_new_session.owner_id
        OR v_old_session.exchange_account_id <> v_new_session.exchange_account_id
        OR v_old_session.credential_reference <> v_new_session.credential_reference
        OR v_old_session.symbol_allowlist <> v_new_session.symbol_allowlist
        OR v_old_session.capital_cap <> v_new_session.capital_cap
        OR v_predecessor.max_notional <> NEW.max_notional
        OR v_predecessor.created_by <> NEW.created_by
        OR EXISTS (SELECT 1 FROM pilot_execution_lease_intents)
        OR EXISTS (SELECT 1 FROM execution_intents intent
                   JOIN live_sessions session ON session.session_id=intent.session_id
                   WHERE session.authority_type='OPERATOR_PILOT')
        OR EXISTS (SELECT 1 FROM execution_receipts receipt
                   JOIN execution_intents intent ON intent.intent_id=receipt.intent_id
                   JOIN live_sessions session ON session.session_id=intent.session_id
                   WHERE session.authority_type='OPERATOR_PILOT')
        OR EXISTS (SELECT 1 FROM pilot_execution_leases
                   WHERE status IN ('CREATED','ACTIVE','CONSUMED'))
        OR EXISTS (SELECT 1 FROM pilot_execution_leases
                   WHERE predecessor_lease_id IS NOT NULL) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='replacement lease zero-intent proof failed';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_gate_y45_replacement_lease_insert
    BEFORE INSERT ON pilot_execution_leases
    FOR EACH ROW EXECUTE FUNCTION gate_y45_guard_replacement_lease_insert();

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
        OR OLD.created_at IS DISTINCT FROM NEW.created_at
        OR OLD.predecessor_lease_id IS DISTINCT FROM NEW.predecessor_lease_id
        OR OLD.recovery_decision_id IS DISTINCT FROM NEW.recovery_decision_id
        OR OLD.replacement_ordinal IS DISTINCT FROM NEW.replacement_ordinal
        OR OLD.replacement_reason IS DISTINCT FROM NEW.replacement_reason THEN
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

CREATE FUNCTION gate_y45_canonical_legacy_account_code(p_exchange_account_id BIGINT)
    RETURNS TEXT LANGUAGE SQL IMMUTABLE STRICT AS $$
    SELECT 'nq-okx-live-' || p_exchange_account_id::TEXT
$$;

CREATE FUNCTION gate_y45_guard_canonical_legacy_bridge()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_account accounts%ROWTYPE;
BEGIN
    IF (TG_OP = 'INSERT' AND NEW.legacy_account_id IS NOT NULL)
        OR (TG_OP = 'UPDATE' AND OLD.legacy_account_id IS NULL
            AND NEW.legacy_account_id IS NOT NULL) THEN
        SELECT * INTO STRICT v_account FROM accounts
        WHERE account_id=NEW.legacy_account_id FOR KEY SHARE;
        IF NEW.exchange_code <> 'OKX' OR NEW.trade_env <> 'LIVE' OR NEW.status <> 'ACTIVE'
            OR v_account.account_code <> gate_y45_canonical_legacy_account_code(NEW.exchange_account_id)
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

CREATE TRIGGER trg_gate_y45_canonical_legacy_bridge
    BEFORE UPDATE OF legacy_account_id ON exchange_accounts
    FOR EACH ROW EXECUTE FUNCTION gate_y45_guard_canonical_legacy_bridge();
CREATE TRIGGER trg_gate_y45_canonical_legacy_bridge_insert
    BEFORE INSERT ON exchange_accounts
    FOR EACH ROW EXECUTE FUNCTION gate_y45_guard_canonical_legacy_bridge();

COMMENT ON TABLE pilot_pre_place_recovery_decisions IS 'GateY Attempt-01零intent pre-PLACE recovery的append-only数据库判定；不授权PLACE重试。';
COMMENT ON COLUMN pilot_pre_place_recovery_decisions.decision_id IS '不可复用recovery判定UUID。';
COMMENT ON COLUMN pilot_pre_place_recovery_decisions.predecessor_lease_id IS '保持终态且不可复活的旧lease。';
COMMENT ON COLUMN pilot_pre_place_recovery_decisions.predecessor_session_id IS '必须经既有状态机终态化的旧session。';
COMMENT ON COLUMN pilot_pre_place_recovery_decisions.decision IS '仅允许REPLACEMENT_ALLOWED_ZERO_INTENT；拒绝判定fail closed且不产生successor。';
COMMENT ON COLUMN pilot_pre_place_recovery_decisions.place_intent_count IS '判定时旧session的PLACE lease-intent数量，必须为0。';
COMMENT ON COLUMN pilot_pre_place_recovery_decisions.send_started_count IS '判定时进入SEND_STARTED边界的intent数量，必须为0。';
COMMENT ON COLUMN pilot_pre_place_recovery_decisions.execution_intent_count IS '判定时ExecutionIntent数量，必须为0。';
COMMENT ON COLUMN pilot_pre_place_recovery_decisions.execution_receipt_count IS '判定时ExecutionReceipt数量，必须为0。';
COMMENT ON COLUMN pilot_pre_place_recovery_decisions.order_count IS '判定时关联Order数量，必须为0。';
COMMENT ON COLUMN pilot_pre_place_recovery_decisions.trade_count IS '判定时关联Trade/Fill数量，必须为0。';
COMMENT ON COLUMN pilot_pre_place_recovery_decisions.ledger_count IS '判定时关联Ledger数量，必须为0。';
COMMENT ON COLUMN pilot_pre_place_recovery_decisions.decided_by IS '执行recovery判定的canonical operator user ID。';
COMMENT ON COLUMN pilot_pre_place_recovery_decisions.request_id IS '脱敏request关联标识。';
COMMENT ON COLUMN pilot_pre_place_recovery_decisions.trace_id IS '脱敏trace关联标识。';
COMMENT ON COLUMN pilot_pre_place_recovery_decisions.decided_at IS '数据库recovery判定时间。';
COMMENT ON COLUMN pilot_execution_leases.predecessor_lease_id IS 'replacement唯一前驱；旧lease保持终态。';
COMMENT ON COLUMN pilot_execution_leases.recovery_decision_id IS '绑定append-only零intent recovery判定。';
COMMENT ON COLUMN pilot_execution_leases.replacement_ordinal IS '0为原lease，1为Attempt-01唯一replacement；禁止更大值。';
COMMENT ON COLUMN pilot_execution_leases.replacement_reason IS 'replacement固定PRE_PLACE_ZERO_INTENT_FAILURE。';
COMMENT ON INDEX uq_pilot_execution_leases_single_open IS '全局最多一个CREATED/ACTIVE/CONSUMED lease。';
COMMENT ON INDEX uq_pilot_execution_leases_single_replacement IS 'Attempt-01最多一次pre-PLACE replacement。';
COMMENT ON INDEX uq_pilot_execution_lease_intents_global_place IS '全部lease合计最多一个PLACE intent，保证PLACE total<=1。';
COMMENT ON INDEX uq_pilot_execution_lease_intents_global_cancel IS '全部lease合计最多一个CANCEL intent。';
COMMENT ON FUNCTION gate_y45_canonical_legacy_account_code(BIGINT) IS 'canonical exchange account到历史trading account的稳定account_code。';
COMMENT ON CONSTRAINT fk_exchange_accounts_canonical_legacy_account ON exchange_accounts IS 'canonical legacy bridge必须引用现有accounts行且禁止级联删除。';
