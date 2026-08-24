-- GateY minimal live pilot: crash-safe bounded execution lease and database-level one-shot intent binding.

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE TABLE pilot_execution_leases (
    lease_id UUID PRIMARY KEY,
    live_session_id UUID NOT NULL,
    binding_id UUID NOT NULL,
    binding_digest VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    max_notional NUMERIC(38,8) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    created_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pilot_execution_leases_session FOREIGN KEY (live_session_id)
        REFERENCES live_sessions(session_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_pilot_execution_leases_creator FOREIGN KEY (created_by)
        REFERENCES users(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uq_pilot_execution_leases_binding UNIQUE (binding_id),
    CONSTRAINT uq_pilot_execution_leases_session UNIQUE (live_session_id),
    CONSTRAINT chk_pilot_execution_leases_digest CHECK (binding_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_pilot_execution_leases_status CHECK (
        status IN ('CREATED','ACTIVE','CONSUMED','EXPIRED','CLOSED','FAILED')
    ),
    CONSTRAINT chk_pilot_execution_leases_notional CHECK (max_notional > 0),
    CONSTRAINT chk_pilot_execution_leases_window CHECK (expires_at > valid_from),
    CONSTRAINT chk_pilot_execution_leases_version CHECK (version > 0),
    CONSTRAINT chk_pilot_execution_leases_lifecycle_times CHECK (
        (status IN ('CREATED','ACTIVE') AND consumed_at IS NULL AND closed_at IS NULL)
        OR (status = 'CONSUMED' AND consumed_at IS NOT NULL AND closed_at IS NULL)
        OR (status IN ('EXPIRED','CLOSED','FAILED') AND closed_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_pilot_execution_leases_single_pilot
    ON pilot_execution_leases ((1));
CREATE INDEX idx_pilot_execution_leases_recovery
    ON pilot_execution_leases (status, expires_at, lease_id)
    WHERE status IN ('CREATED','ACTIVE','CONSUMED');

CREATE TABLE pilot_execution_lease_intents (
    lease_id UUID NOT NULL,
    intent_id UUID NOT NULL,
    action VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (lease_id, action),
    CONSTRAINT fk_pilot_execution_lease_intents_lease FOREIGN KEY (lease_id)
        REFERENCES pilot_execution_leases(lease_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_pilot_execution_lease_intents_intent FOREIGN KEY (intent_id)
        REFERENCES execution_intents(intent_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uq_pilot_execution_lease_intents_intent UNIQUE (intent_id),
    CONSTRAINT chk_pilot_execution_lease_intents_action CHECK (action IN ('PLACE','CANCEL'))
);

CREATE TABLE pilot_execution_lease_events (
    event_id UUID PRIMARY KEY,
    lease_id UUID NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    lease_version BIGINT NOT NULL,
    reason_code VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_pilot_execution_lease_events_lease FOREIGN KEY (lease_id)
        REFERENCES pilot_execution_leases(lease_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uq_pilot_execution_lease_events_version UNIQUE (lease_id, lease_version),
    CONSTRAINT chk_pilot_execution_lease_events_status CHECK (
        (from_status IS NULL OR from_status IN ('CREATED','ACTIVE','CONSUMED','EXPIRED','CLOSED','FAILED'))
        AND to_status IN ('CREATED','ACTIVE','CONSUMED','EXPIRED','CLOSED','FAILED')
    ),
    CONSTRAINT chk_pilot_execution_lease_events_version CHECK (lease_version > 0),
    CONSTRAINT chk_pilot_execution_lease_events_text CHECK (
        btrim(reason_code) <> '' AND btrim(request_id) <> '' AND btrim(trace_id) <> ''
    )
);

CREATE FUNCTION gate_y_minimal_pilot_reject_fact_mutation()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE='23514', MESSAGE=TG_TABLE_NAME || ' is append-only';
END;
$$;

CREATE TRIGGER trg_pilot_execution_lease_intents_append_only
    BEFORE UPDATE OR DELETE ON pilot_execution_lease_intents
    FOR EACH ROW EXECUTE FUNCTION gate_y_minimal_pilot_reject_fact_mutation();
CREATE TRIGGER trg_pilot_execution_lease_events_append_only
    BEFORE UPDATE OR DELETE ON pilot_execution_lease_events
    FOR EACH ROW EXECUTE FUNCTION gate_y_minimal_pilot_reject_fact_mutation();

CREATE FUNCTION gate_y_minimal_pilot_guard_lease_update()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_legal BOOLEAN;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='pilot execution lease cannot be deleted';
    END IF;
    IF OLD.lease_id IS DISTINCT FROM NEW.lease_id
        OR OLD.live_session_id IS DISTINCT FROM NEW.live_session_id
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

CREATE TRIGGER trg_pilot_execution_leases_guard
    BEFORE UPDATE OR DELETE ON pilot_execution_leases
    FOR EACH ROW EXECUTE FUNCTION gate_y_minimal_pilot_guard_lease_update();

COMMENT ON TABLE pilot_execution_leases IS 'GateY最小真实pilot的durable一次性执行租约；不保存credential或provider原文。';
COMMENT ON TABLE pilot_execution_lease_intents IS '租约与ExecutionIntent的append-only一对一动作绑定；数据库保证每个租约最多一次PLACE和一次CANCEL。';
COMMENT ON TABLE pilot_execution_lease_events IS '租约状态变化append-only审计；不替代Order、Fill、Ledger或ExecutionReceipt。';
COMMENT ON INDEX uq_pilot_execution_leases_single_pilot IS '整个GateY最小实盘合同只允许物化一份pilot lease，终态后也不得创建第二份。';
COMMENT ON COLUMN pilot_execution_leases.lease_id IS '不可复用租约UUID。';
COMMENT ON COLUMN pilot_execution_leases.live_session_id IS '绑定既有LiveSession。';
COMMENT ON COLUMN pilot_execution_leases.binding_id IS '绑定既有ExactPilotBinding identity。';
COMMENT ON COLUMN pilot_execution_leases.binding_digest IS 'ExactPilotBinding canonical lowercase SHA-256。';
COMMENT ON COLUMN pilot_execution_leases.status IS 'CREATED/ACTIVE/CONSUMED/EXPIRED/CLOSED/FAILED。';
COMMENT ON COLUMN pilot_execution_leases.max_notional IS 'operator显式pilot名义金额硬上限，NUMERIC(38,8)。';
COMMENT ON COLUMN pilot_execution_leases.valid_from IS 'UTC租约生效时间。';
COMMENT ON COLUMN pilot_execution_leases.expires_at IS 'UTC硬过期时间；执行入口每次重新校验。';
COMMENT ON COLUMN pilot_execution_leases.consumed_at IS '唯一PLACE intent被持久绑定的时间。';
COMMENT ON COLUMN pilot_execution_leases.closed_at IS '终态关闭时间。';
COMMENT ON COLUMN pilot_execution_leases.created_by IS '发起最小pilot的现有OPERATOR users.id。';
COMMENT ON COLUMN pilot_execution_leases.version IS 'optimistic lifecycle version。';
COMMENT ON COLUMN pilot_execution_leases.created_at IS '数据库创建时间。';
COMMENT ON COLUMN pilot_execution_leases.updated_at IS '最后合法状态变化时间。';
COMMENT ON COLUMN pilot_execution_lease_intents.lease_id IS '所属pilot execution lease。';
COMMENT ON COLUMN pilot_execution_lease_intents.intent_id IS '既有execution_intents主事实引用。';
COMMENT ON COLUMN pilot_execution_lease_intents.action IS 'PLACE或CANCEL；主键保证每种动作最多一次。';
COMMENT ON COLUMN pilot_execution_lease_intents.created_at IS 'append-only绑定时间。';
COMMENT ON COLUMN pilot_execution_lease_events.event_id IS '不可复用审计事件UUID。';
COMMENT ON COLUMN pilot_execution_lease_events.lease_id IS '所属pilot execution lease。';
COMMENT ON COLUMN pilot_execution_lease_events.from_status IS '变化前状态；创建事件为空。';
COMMENT ON COLUMN pilot_execution_lease_events.to_status IS '变化后租约状态。';
COMMENT ON COLUMN pilot_execution_lease_events.lease_version IS '事件对应租约version。';
COMMENT ON COLUMN pilot_execution_lease_events.reason_code IS '稳定脱敏原因码。';
COMMENT ON COLUMN pilot_execution_lease_events.request_id IS '脱敏请求关联标识。';
COMMENT ON COLUMN pilot_execution_lease_events.trace_id IS '脱敏链路关联标识。';
COMMENT ON COLUMN pilot_execution_lease_events.occurred_at IS 'UTC状态变化时间。';
