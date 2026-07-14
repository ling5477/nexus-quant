-- GateW-4 blocker remediation: durable global kill switch with append-only state-change evidence.
-- The only seeded scope starts ENGAGED. This migration does not create a release/disengage operation.

CREATE TABLE kill_switch_states (
    scope VARCHAR(64) PRIMARY KEY,
    status VARCHAR(16) NOT NULL DEFAULT 'ENGAGED',
    version BIGINT NOT NULL DEFAULT 1,
    reason_code VARCHAR(64) NOT NULL,
    source VARCHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    CONSTRAINT chk_kill_switch_states_scope
        CHECK (scope = 'GLOBAL_TRADING'),
    CONSTRAINT chk_kill_switch_states_status
        CHECK (status IN ('ENGAGED', 'DISENGAGED')),
    CONSTRAINT chk_kill_switch_states_version
        CHECK (version > 0),
    CONSTRAINT chk_kill_switch_states_reason
        CHECK (BTRIM(reason_code) <> ''),
    CONSTRAINT chk_kill_switch_states_source
        CHECK (BTRIM(source) <> ''),
    CONSTRAINT chk_kill_switch_states_updated_by
        CHECK (BTRIM(updated_by) <> ''),
    CONSTRAINT chk_kill_switch_states_trace
        CHECK (BTRIM(trace_id) <> '')
);

CREATE TABLE kill_switch_events (
    id UUID PRIMARY KEY,
    scope VARCHAR(64) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    state_version BIGINT NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    source VARCHAR(64) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_kill_switch_events_scope
        FOREIGN KEY (scope) REFERENCES kill_switch_states (scope) ON DELETE RESTRICT,
    CONSTRAINT uq_kill_switch_events_scope_version
        UNIQUE (scope, state_version),
    CONSTRAINT chk_kill_switch_events_from_status
        CHECK (from_status IS NULL OR from_status IN ('ENGAGED', 'DISENGAGED')),
    CONSTRAINT chk_kill_switch_events_to_status
        CHECK (to_status IN ('ENGAGED', 'DISENGAGED')),
    CONSTRAINT chk_kill_switch_events_version
        CHECK (state_version > 0),
    CONSTRAINT chk_kill_switch_events_reason
        CHECK (BTRIM(reason_code) <> ''),
    CONSTRAINT chk_kill_switch_events_source
        CHECK (BTRIM(source) <> ''),
    CONSTRAINT chk_kill_switch_events_actor
        CHECK (BTRIM(actor_id) <> ''),
    CONSTRAINT chk_kill_switch_events_trace
        CHECK (BTRIM(trace_id) <> '')
);

CREATE INDEX idx_kill_switch_events_scope_occurred
    ON kill_switch_events (scope, occurred_at DESC, id DESC);

INSERT INTO kill_switch_states (
    scope, status, version, reason_code, source, updated_by, trace_id
) VALUES (
    'GLOBAL_TRADING', 'ENGAGED', 1, 'DEFAULT_SAFE_BOOTSTRAP',
    'FLYWAY_MIGRATION', 'SYSTEM', 'migration-v35'
);

INSERT INTO kill_switch_events (
    id, scope, from_status, to_status, state_version, reason_code,
    source, actor_id, trace_id, occurred_at
)
SELECT
    '00000000-0000-0000-0000-000000000035'::UUID,
    scope,
    NULL,
    status,
    version,
    reason_code,
    source,
    updated_by,
    trace_id,
    updated_at
FROM kill_switch_states
WHERE scope = 'GLOBAL_TRADING';

COMMENT ON TABLE kill_switch_states IS '全局交易 kill switch 当前安全状态；缺记录、读取失败或非法值必须在应用层按 UNKNOWN 阻断。';
COMMENT ON COLUMN kill_switch_states.scope IS '稳定安全作用域；当前只允许 GLOBAL_TRADING。';
COMMENT ON COLUMN kill_switch_states.status IS '当前状态：ENGAGED 表示阻断；DISENGAGED 仅表示可继续下一只读检查，不表示交易授权。';
COMMENT ON COLUMN kill_switch_states.version IS 'optimistic-lock 版本；每次真实状态变化递增。';
COMMENT ON COLUMN kill_switch_states.reason_code IS '最近状态变化的脱敏原因码，不保存 credential 或 provider payload。';
COMMENT ON COLUMN kill_switch_states.source IS '状态事实来源，例如 FLYWAY_MIGRATION 或 OPERATOR_ENGAGE。';
COMMENT ON COLUMN kill_switch_states.updated_at IS '当前状态的权威更新时间，UTC；不得以请求时间覆盖。';
COMMENT ON COLUMN kill_switch_states.updated_by IS '最近状态变化操作者标识；不得保存敏感身份材料。';
COMMENT ON COLUMN kill_switch_states.trace_id IS '最近状态变化的脱敏追踪标识。';

COMMENT ON TABLE kill_switch_events IS 'Kill switch 状态变化的 append-only 审计事实；应用只追加，不更新或删除。';
COMMENT ON COLUMN kill_switch_events.id IS '审计事件 UUID 主键。';
COMMENT ON COLUMN kill_switch_events.scope IS '事件所属 kill switch 安全作用域。';
COMMENT ON COLUMN kill_switch_events.from_status IS '变化前状态；初始 seed 事件为空。';
COMMENT ON COLUMN kill_switch_events.to_status IS '变化后状态：ENGAGED 或 DISENGAGED；本任务生产代码只写 ENGAGED。';
COMMENT ON COLUMN kill_switch_events.state_version IS '事件对应的 current-state optimistic-lock 版本。';
COMMENT ON COLUMN kill_switch_events.reason_code IS '状态变化的脱敏原因码。';
COMMENT ON COLUMN kill_switch_events.source IS '状态变化事实来源。';
COMMENT ON COLUMN kill_switch_events.actor_id IS '触发状态变化的操作者或系统标识。';
COMMENT ON COLUMN kill_switch_events.trace_id IS '状态变化的脱敏追踪标识。';
COMMENT ON COLUMN kill_switch_events.occurred_at IS '状态变化发生时间，UTC。';

COMMENT ON CONSTRAINT chk_kill_switch_states_scope ON kill_switch_states IS '当前 schema 只允许全局交易安全作用域。';
COMMENT ON CONSTRAINT chk_kill_switch_states_status ON kill_switch_states IS '持久化状态只允许 ENGAGED 或 DISENGAGED；UNKNOWN 仅是读取失败的应用态。';
COMMENT ON CONSTRAINT chk_kill_switch_states_version ON kill_switch_states IS 'optimistic-lock 版本必须为正数。';
COMMENT ON CONSTRAINT fk_kill_switch_events_scope ON kill_switch_events IS '安全状态存在时才能追加事件，且禁止级联删除安全证据。';
COMMENT ON CONSTRAINT uq_kill_switch_events_scope_version ON kill_switch_events IS '每个 scope/version 最多一个状态变化事件。';
