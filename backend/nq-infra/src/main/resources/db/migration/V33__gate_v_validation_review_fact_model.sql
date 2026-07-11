-- GateV-1 durable validation review fact model。
-- 仅保存 NQ 本地人工复核事实与 append-only lifecycle event；不修改任何交易或运行事实。

CREATE TABLE validation_review_cases (
    id UUID PRIMARY KEY,
    tenant_key VARCHAR(64) NOT NULL,
    owner_id BIGINT NOT NULL,
    evidence_type VARCHAR(64) NOT NULL,
    evidence_source VARCHAR(256) NOT NULL,
    evidence_anchor JSONB NOT NULL,
    severity VARCHAR(16) NOT NULL,
    state VARCHAR(32) NOT NULL,
    title VARCHAR(256) NOT NULL,
    summary TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    acknowledged_by BIGINT,
    acknowledged_at TIMESTAMPTZ,
    escalated_by BIGINT,
    escalated_at TIMESTAMPTZ,
    resolved_by BIGINT,
    resolved_at TIMESTAMPTZ,
    closed_by BIGINT,
    closed_at TIMESTAMPTZ,
    retention_until TIMESTAMPTZ,
    CONSTRAINT uq_validation_review_cases_id_tenant UNIQUE (id, tenant_key),
    CONSTRAINT fk_validation_review_cases_owner
        FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_validation_review_cases_created_by
        FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_validation_review_cases_acknowledged_by
        FOREIGN KEY (acknowledged_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_validation_review_cases_escalated_by
        FOREIGN KEY (escalated_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_validation_review_cases_resolved_by
        FOREIGN KEY (resolved_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_validation_review_cases_closed_by
        FOREIGN KEY (closed_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_validation_review_cases_tenant_key
        CHECK (BTRIM(tenant_key) <> ''),
    CONSTRAINT chk_validation_review_cases_evidence_type
        CHECK (BTRIM(evidence_type) <> ''),
    CONSTRAINT chk_validation_review_cases_evidence_source
        CHECK (BTRIM(evidence_source) <> ''),
    CONSTRAINT chk_validation_review_cases_evidence_anchor
        CHECK (jsonb_typeof(evidence_anchor) = 'object'),
    CONSTRAINT chk_validation_review_cases_severity
        CHECK (severity IN ('INFO', 'WARNING', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_validation_review_cases_state
        CHECK (state IN ('OPEN', 'ACKNOWLEDGED', 'ESCALATED', 'RESOLVED', 'CLOSED')),
    CONSTRAINT chk_validation_review_cases_title
        CHECK (BTRIM(title) <> ''),
    CONSTRAINT chk_validation_review_cases_version
        CHECK (version >= 0),
    CONSTRAINT chk_validation_review_cases_actor_time_pairs CHECK (
        (acknowledged_by IS NULL) = (acknowledged_at IS NULL)
        AND (escalated_by IS NULL) = (escalated_at IS NULL)
        AND (resolved_by IS NULL) = (resolved_at IS NULL)
        AND (closed_by IS NULL) = (closed_at IS NULL)
    ),
    CONSTRAINT chk_validation_review_cases_state_times CHECK (
        (state <> 'OPEN' OR (
            acknowledged_at IS NULL AND escalated_at IS NULL
            AND resolved_at IS NULL AND closed_at IS NULL
        ))
        AND (state <> 'ACKNOWLEDGED' OR (
            acknowledged_at IS NOT NULL AND escalated_at IS NULL
            AND resolved_at IS NULL AND closed_at IS NULL
        ))
        AND (state <> 'ESCALATED' OR (
            escalated_at IS NOT NULL AND resolved_at IS NULL AND closed_at IS NULL
        ))
        AND (state <> 'RESOLVED' OR (
            resolved_at IS NOT NULL AND closed_at IS NULL
            AND (acknowledged_at IS NOT NULL OR escalated_at IS NOT NULL)
        ))
        AND (state <> 'CLOSED' OR (resolved_at IS NOT NULL AND closed_at IS NOT NULL))
    ),
    CONSTRAINT chk_validation_review_cases_time_order CHECK (
        updated_at >= created_at
        AND (acknowledged_at IS NULL OR acknowledged_at >= created_at)
        AND (escalated_at IS NULL OR escalated_at >= COALESCE(acknowledged_at, created_at))
        AND (resolved_at IS NULL OR resolved_at >= COALESCE(escalated_at, acknowledged_at, created_at))
        AND (closed_at IS NULL OR closed_at >= resolved_at)
        AND (retention_until IS NULL OR retention_until >= COALESCE(closed_at, created_at))
    )
);

CREATE INDEX idx_validation_review_cases_tenant_owner_state_updated
    ON validation_review_cases (tenant_key, owner_id, state, updated_at DESC);

CREATE INDEX idx_validation_review_cases_tenant_state_severity_updated
    ON validation_review_cases (tenant_key, state, severity, updated_at DESC);

CREATE INDEX idx_validation_review_cases_tenant_owner_updated
    ON validation_review_cases (tenant_key, owner_id, updated_at DESC, id DESC);

CREATE INDEX idx_validation_review_cases_tenant_updated
    ON validation_review_cases (tenant_key, updated_at DESC, id DESC);

CREATE INDEX idx_validation_review_cases_evidence_type_source
    ON validation_review_cases (evidence_type, evidence_source);

CREATE TABLE validation_review_events (
    id UUID PRIMARY KEY,
    review_case_id UUID NOT NULL,
    tenant_key VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    from_state VARCHAR(32),
    to_state VARCHAR(32) NOT NULL,
    case_version BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    request_id VARCHAR(128),
    trace_id VARCHAR(128) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_validation_review_events_case_tenant
        FOREIGN KEY (review_case_id, tenant_key)
        REFERENCES validation_review_cases (id, tenant_key) ON DELETE RESTRICT,
    CONSTRAINT fk_validation_review_events_actor
        FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_validation_review_events_case_idempotency
        UNIQUE (review_case_id, idempotency_key),
    CONSTRAINT chk_validation_review_events_tenant_key
        CHECK (BTRIM(tenant_key) <> ''),
    CONSTRAINT chk_validation_review_events_event_type
        CHECK (event_type IN ('ACKNOWLEDGED', 'ESCALATED', 'RESOLVED', 'CLOSED')),
    CONSTRAINT chk_validation_review_events_from_state
        CHECK (from_state IS NULL OR from_state IN ('OPEN', 'ACKNOWLEDGED', 'ESCALATED', 'RESOLVED', 'CLOSED')),
    CONSTRAINT chk_validation_review_events_to_state
        CHECK (to_state IN ('ACKNOWLEDGED', 'ESCALATED', 'RESOLVED', 'CLOSED')),
    CONSTRAINT chk_validation_review_events_transition_shape CHECK (
        from_state IS NOT NULL
        AND from_state <> to_state
        AND event_type = to_state
    ),
    CONSTRAINT chk_validation_review_events_legal_transition CHECK (
        (from_state = 'OPEN' AND to_state IN ('ACKNOWLEDGED', 'ESCALATED'))
        OR (from_state = 'ACKNOWLEDGED' AND to_state IN ('ESCALATED', 'RESOLVED'))
        OR (from_state = 'ESCALATED' AND to_state = 'RESOLVED')
        OR (from_state = 'RESOLVED' AND to_state = 'CLOSED')
    ),
    CONSTRAINT chk_validation_review_events_case_version
        CHECK (case_version > 0),
    CONSTRAINT chk_validation_review_events_idempotency_key
        CHECK (BTRIM(idempotency_key) <> ''),
    CONSTRAINT chk_validation_review_events_request_hash
        CHECK (BTRIM(request_hash) <> ''),
    CONSTRAINT chk_validation_review_events_trace_id
        CHECK (BTRIM(trace_id) <> ''),
    CONSTRAINT chk_validation_review_events_metadata
        CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX idx_validation_review_events_case_created
    ON validation_review_events (review_case_id, created_at ASC, id ASC);

CREATE INDEX idx_validation_review_events_tenant_actor_created
    ON validation_review_events (tenant_key, actor_id, created_at DESC);

CREATE INDEX idx_validation_review_events_trace_id
    ON validation_review_events (trace_id);

COMMENT ON TABLE validation_review_cases IS 'GateV-1 本地人工复核 case 主事实；只记录诊断与人工复核生命周期，不代表交易授权，不表示 LIVE ready，不修改策略、Paper、Shadow、risk、account、order 或 ledger，不保存 credential material';
COMMENT ON COLUMN validation_review_cases.id IS '本地人工复核 case UUID 主键';
COMMENT ON COLUMN validation_review_cases.tenant_key IS '服务端提供的租户隔离键；GateV 固定为 NQ_LOCAL，客户端不得覆盖';
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
COMMENT ON COLUMN validation_review_cases.retention_until IS '关闭后保留边界；GateV-1 不实现自动删除或归档 job';

COMMENT ON TABLE validation_review_events IS 'GateV-1 本地人工复核 append-only lifecycle event；只记录 accepted transition，不代表交易授权，不表示 LIVE ready，不修改任何交易或运行事实，不保存 credential material';
COMMENT ON COLUMN validation_review_events.id IS '人工复核 lifecycle event UUID 主键';
COMMENT ON COLUMN validation_review_events.review_case_id IS '所属本地 review case UUID；删除策略为 RESTRICT';
COMMENT ON COLUMN validation_review_events.tenant_key IS '与 case 一致的服务端租户隔离键';
COMMENT ON COLUMN validation_review_events.event_type IS 'accepted transition 事件类型：ACKNOWLEDGED、ESCALATED、RESOLVED、CLOSED';
COMMENT ON COLUMN validation_review_events.from_state IS 'accepted transition 前状态；不保存非法流转尝试';
COMMENT ON COLUMN validation_review_events.to_state IS 'accepted transition 后状态；数据库约束仅允许 GateV 固定合法流转，不包含批准、授权或可交易语义';
COMMENT ON COLUMN validation_review_events.case_version IS 'accepted transition 后的 case 乐观锁版本';
COMMENT ON COLUMN validation_review_events.actor_id IS '执行 accepted transition 的本地用户，引用 users.id';
COMMENT ON COLUMN validation_review_events.idempotency_key IS 'case 内 transition 幂等键；同 case 唯一，跨 case 可复用';
COMMENT ON COLUMN validation_review_events.request_hash IS '规范化 transition 请求摘要；用于识别幂等键重用，不保存请求原文';
COMMENT ON COLUMN validation_review_events.request_id IS '可空业务请求 ID；不保存原始请求体';
COMMENT ON COLUMN validation_review_events.trace_id IS '本地审计链 trace ID；不承载 credential 或 private payload';
COMMENT ON COLUMN validation_review_events.metadata IS '脱敏 JSONB metadata；禁止保存 credential、账户余额、真实订单、交易授权或 private request/response';
COMMENT ON COLUMN validation_review_events.created_at IS 'accepted transition event 追加时间；事件按 append-only 使用';
