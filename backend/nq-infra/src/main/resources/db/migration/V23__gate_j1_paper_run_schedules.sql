-- GateJ-1: Paper run schedules, schedule fires, and heartbeats
-- Supports Paper Trading stable operation: scheduling, trigger records, and health monitoring.

------------------------------------------------------------
-- Table: paper_run_schedules
------------------------------------------------------------
CREATE TABLE paper_run_schedules (
    schedule_id     VARCHAR(64)   PRIMARY KEY,
    paper_run_id    VARCHAR(64)   NOT NULL,
    schedule_name   VARCHAR(256)  NOT NULL,
    cron_expr       VARCHAR(128)  NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    timezone        VARCHAR(64)   NOT NULL DEFAULT 'UTC',
    next_fire_time  TIMESTAMPTZ,
    last_fire_time  TIMESTAMPTZ,
    created_by      VARCHAR(512)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    request_json    JSONB         NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT chk_schedules_status CHECK (status IN ('ENABLED', 'DISABLED', 'PAUSED')),
    CONSTRAINT fk_schedules_run FOREIGN KEY (paper_run_id)
        REFERENCES paper_trading_runs (paper_run_id)
);

CREATE INDEX idx_paper_run_schedules_run_id ON paper_run_schedules (paper_run_id);
CREATE INDEX idx_paper_run_schedules_status ON paper_run_schedules (status);
CREATE INDEX idx_paper_run_schedules_next_fire ON paper_run_schedules (next_fire_time) WHERE status = 'ENABLED';

COMMENT ON TABLE paper_run_schedules IS 'Paper run 调度计划，定义 cron 表达式和调度状态';
COMMENT ON COLUMN paper_run_schedules.schedule_id IS '调度计划 ID，格式 sch-<uuid>';
COMMENT ON COLUMN paper_run_schedules.paper_run_id IS '关联 Paper run ID';
COMMENT ON COLUMN paper_run_schedules.schedule_name IS '调度名称';
COMMENT ON COLUMN paper_run_schedules.cron_expr IS 'cron 表达式，如 0 */5 * * * *';
COMMENT ON COLUMN paper_run_schedules.status IS '调度状态：ENABLED / DISABLED / PAUSED';
COMMENT ON COLUMN paper_run_schedules.timezone IS '时区，默认 UTC';
COMMENT ON COLUMN paper_run_schedules.next_fire_time IS '下次触发时间';
COMMENT ON COLUMN paper_run_schedules.last_fire_time IS '上次触发时间';
COMMENT ON COLUMN paper_run_schedules.created_by IS '创建人';
COMMENT ON COLUMN paper_run_schedules.created_at IS '创建时间';
COMMENT ON COLUMN paper_run_schedules.updated_at IS '更新时间';
COMMENT ON COLUMN paper_run_schedules.request_json IS '调度创建请求快照，用于审计和排障，不保存密钥/token/cookie';

------------------------------------------------------------
-- Table: paper_run_schedule_fires
------------------------------------------------------------
CREATE TABLE paper_run_schedule_fires (
    fire_id         VARCHAR(64)   PRIMARY KEY,
    schedule_id     VARCHAR(64)   NOT NULL,
    paper_run_id    VARCHAR(64)   NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    fired_at        TIMESTAMPTZ   NOT NULL,
    finished_at     TIMESTAMPTZ,
    duration_ms     BIGINT,
    result_json     JSONB         NOT NULL DEFAULT '{}'::jsonb,
    error_message   TEXT,
    created_at      TIMESTAMPTZ   NOT NULL,

    CONSTRAINT chk_fires_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED')),
    CONSTRAINT fk_fires_schedule FOREIGN KEY (schedule_id)
        REFERENCES paper_run_schedules (schedule_id),
    CONSTRAINT fk_fires_run FOREIGN KEY (paper_run_id)
        REFERENCES paper_trading_runs (paper_run_id)
);

CREATE INDEX idx_schedule_fires_schedule_id ON paper_run_schedule_fires (schedule_id, fired_at DESC);
CREATE INDEX idx_schedule_fires_run_id ON paper_run_schedule_fires (paper_run_id);
CREATE INDEX idx_schedule_fires_fired_at ON paper_run_schedule_fires (fired_at DESC);

COMMENT ON TABLE paper_run_schedule_fires IS '调度触发记录，每次调度触发产生一条记录';
COMMENT ON COLUMN paper_run_schedule_fires.fire_id IS '触发记录 ID，格式 fir-<uuid>';
COMMENT ON COLUMN paper_run_schedule_fires.schedule_id IS '关联调度计划 ID';
COMMENT ON COLUMN paper_run_schedule_fires.paper_run_id IS '关联 Paper run ID';
COMMENT ON COLUMN paper_run_schedule_fires.status IS '触发状态：RUNNING / SUCCEEDED / FAILED / SKIPPED';
COMMENT ON COLUMN paper_run_schedule_fires.fired_at IS '触发时间';
COMMENT ON COLUMN paper_run_schedule_fires.finished_at IS '完成时间';
COMMENT ON COLUMN paper_run_schedule_fires.duration_ms IS '执行耗时（毫秒）';
COMMENT ON COLUMN paper_run_schedule_fires.result_json IS '执行结果快照，不保存密钥/token/cookie';
COMMENT ON COLUMN paper_run_schedule_fires.error_message IS '错误信息';
COMMENT ON COLUMN paper_run_schedule_fires.created_at IS '创建时间';

------------------------------------------------------------
-- Table: paper_run_heartbeats
------------------------------------------------------------
CREATE TABLE paper_run_heartbeats (
    heartbeat_id    VARCHAR(64)   PRIMARY KEY,
    paper_run_id    VARCHAR(64)   NOT NULL,
    heartbeat_time  TIMESTAMPTZ   NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    last_event_time TIMESTAMPTZ,
    last_order_time TIMESTAMPTZ,
    last_trade_time TIMESTAMPTZ,
    lag_seconds     BIGINT,
    summary_json    JSONB         NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ   NOT NULL,

    CONSTRAINT chk_heartbeats_status CHECK (status IN ('OK', 'LAGGING', 'STOPPED', 'UNKNOWN')),
    CONSTRAINT fk_heartbeats_run FOREIGN KEY (paper_run_id)
        REFERENCES paper_trading_runs (paper_run_id)
);

CREATE INDEX idx_heartbeats_run_id_time ON paper_run_heartbeats (paper_run_id, heartbeat_time DESC);

COMMENT ON TABLE paper_run_heartbeats IS 'Paper run 心跳记录，定期记录运行健康状态';
COMMENT ON COLUMN paper_run_heartbeats.heartbeat_id IS '心跳 ID，格式 hbt-<uuid>';
COMMENT ON COLUMN paper_run_heartbeats.paper_run_id IS '关联 Paper run ID';
COMMENT ON COLUMN paper_run_heartbeats.heartbeat_time IS '心跳时间';
COMMENT ON COLUMN paper_run_heartbeats.status IS '心跳状态：OK / LAGGING / STOPPED / UNKNOWN';
COMMENT ON COLUMN paper_run_heartbeats.last_event_time IS '最近事件时间';
COMMENT ON COLUMN paper_run_heartbeats.last_order_time IS '最近订单时间';
COMMENT ON COLUMN paper_run_heartbeats.last_trade_time IS '最近成交时间';
COMMENT ON COLUMN paper_run_heartbeats.lag_seconds IS '延迟秒数';
COMMENT ON COLUMN paper_run_heartbeats.summary_json IS '心跳摘要（当前持仓数、未完成订单数等），不保存密钥/token/cookie';
COMMENT ON COLUMN paper_run_heartbeats.created_at IS '创建时间';
