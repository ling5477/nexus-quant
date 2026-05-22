-- GateJ-3: Paper run recovery events and stability checks
-- Supports Paper Trading stable operation: recovery/retry events and minimal stability acceptance checks.

------------------------------------------------------------
-- Table: paper_run_recovery_events
------------------------------------------------------------
CREATE TABLE paper_run_recovery_events (
    recovery_event_id   VARCHAR(64)   PRIMARY KEY,
    paper_run_id        VARCHAR(64)   NOT NULL,
    recovery_type       VARCHAR(64)   NOT NULL,
    status              VARCHAR(16)   NOT NULL,
    reason              TEXT,
    request_json        JSONB         NOT NULL DEFAULT '{}'::jsonb,
    result_json         JSONB         NOT NULL DEFAULT '{}'::jsonb,
    started_at          TIMESTAMPTZ   NOT NULL,
    finished_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL,

    CONSTRAINT chk_recovery_events_type CHECK (recovery_type IN (
        'MANUAL_RECOVER', 'RETRY_FAILED_STEP', 'HEARTBEAT_LAG_RECOVER', 'SCHEDULE_FIRE_RECOVER'
    )),
    CONSTRAINT chk_recovery_events_status CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED', 'SKIPPED')),
    CONSTRAINT fk_recovery_events_run FOREIGN KEY (paper_run_id)
        REFERENCES paper_trading_runs (paper_run_id)
);

CREATE INDEX idx_recovery_events_run_id_created ON paper_run_recovery_events (paper_run_id, created_at DESC);
CREATE INDEX idx_recovery_events_status ON paper_run_recovery_events (status);
CREATE INDEX idx_recovery_events_type ON paper_run_recovery_events (recovery_type);
CREATE INDEX idx_recovery_events_created_at ON paper_run_recovery_events (created_at DESC);

COMMENT ON TABLE paper_run_recovery_events IS 'Paper run 恢复与重试事件记录';
COMMENT ON COLUMN paper_run_recovery_events.recovery_event_id IS '恢复事件 ID，格式 rec-<uuid>';
COMMENT ON COLUMN paper_run_recovery_events.paper_run_id IS '关联 Paper run ID';
COMMENT ON COLUMN paper_run_recovery_events.recovery_type IS '恢复类型：MANUAL_RECOVER / RETRY_FAILED_STEP / HEARTBEAT_LAG_RECOVER / SCHEDULE_FIRE_RECOVER';
COMMENT ON COLUMN paper_run_recovery_events.status IS '恢复状态：STARTED / SUCCEEDED / FAILED / SKIPPED';
COMMENT ON COLUMN paper_run_recovery_events.reason IS '恢复原因说明';
COMMENT ON COLUMN paper_run_recovery_events.request_json IS '恢复请求参数快照，不保存密钥/token/cookie';
COMMENT ON COLUMN paper_run_recovery_events.result_json IS '恢复结果摘要快照，不保存密钥/token/cookie';
COMMENT ON COLUMN paper_run_recovery_events.started_at IS '恢复开始时间（UTC）';
COMMENT ON COLUMN paper_run_recovery_events.finished_at IS '恢复完成时间（UTC），未完成时为 null';
COMMENT ON COLUMN paper_run_recovery_events.created_at IS '记录创建时间（UTC）';

------------------------------------------------------------
-- Table: paper_run_stability_checks
------------------------------------------------------------
CREATE TABLE paper_run_stability_checks (
    stability_check_id   VARCHAR(64)   PRIMARY KEY,
    paper_run_id         VARCHAR(64)   NOT NULL,
    check_window_start   TIMESTAMPTZ   NOT NULL,
    check_window_end     TIMESTAMPTZ   NOT NULL,
    status               VARCHAR(16)   NOT NULL,
    uptime_ratio         NUMERIC(5,4)  NOT NULL DEFAULT 0,
    heartbeat_count      INT           NOT NULL DEFAULT 0,
    alert_count          INT           NOT NULL DEFAULT 0,
    failed_fire_count    INT           NOT NULL DEFAULT 0,
    recovery_count       INT           NOT NULL DEFAULT 0,
    report_count         INT           NOT NULL DEFAULT 0,
    summary_json         JSONB         NOT NULL DEFAULT '{}'::jsonb,
    created_at           TIMESTAMPTZ   NOT NULL,

    CONSTRAINT chk_stability_checks_status CHECK (status IN ('PASSED', 'FAILED', 'PARTIAL')),
    CONSTRAINT chk_stability_checks_window CHECK (check_window_end > check_window_start),
    CONSTRAINT chk_stability_checks_uptime CHECK (uptime_ratio >= 0 AND uptime_ratio <= 1),
    CONSTRAINT uq_stability_checks_run_window UNIQUE (paper_run_id, check_window_start, check_window_end),
    CONSTRAINT fk_stability_checks_run FOREIGN KEY (paper_run_id)
        REFERENCES paper_trading_runs (paper_run_id)
);

CREATE INDEX idx_stability_checks_run_id_created ON paper_run_stability_checks (paper_run_id, created_at DESC);
CREATE INDEX idx_stability_checks_status ON paper_run_stability_checks (status);
CREATE INDEX idx_stability_checks_window_start ON paper_run_stability_checks (check_window_start);
CREATE INDEX idx_stability_checks_window_end ON paper_run_stability_checks (check_window_end);

COMMENT ON TABLE paper_run_stability_checks IS 'Paper run 稳定性验收结果（第一版最小口径，非 GateJ-FREEZE 最终验收）';
COMMENT ON COLUMN paper_run_stability_checks.stability_check_id IS '稳定性验收 ID，格式 stb-<uuid>';
COMMENT ON COLUMN paper_run_stability_checks.paper_run_id IS '关联 Paper run ID';
COMMENT ON COLUMN paper_run_stability_checks.check_window_start IS '验收窗口开始时间（UTC，含）';
COMMENT ON COLUMN paper_run_stability_checks.check_window_end IS '验收窗口结束时间（UTC，不含），必须严格大于 start';
COMMENT ON COLUMN paper_run_stability_checks.status IS '验收状态：PASSED / FAILED / PARTIAL；第一版按心跳/告警/失败触发数粗略判定，不等于 GateJ-FREEZE 7 天最终验收';
COMMENT ON COLUMN paper_run_stability_checks.uptime_ratio IS '在线率（0~1，4 位小数）；第一版口径：heartbeat_count > 0 且无 CRITICAL 未处理告警且 failed_fire_count = 0 视为 1.0，否则按粗略比例打折';
COMMENT ON COLUMN paper_run_stability_checks.heartbeat_count IS '窗口内心跳数量';
COMMENT ON COLUMN paper_run_stability_checks.alert_count IS '窗口内告警数量';
COMMENT ON COLUMN paper_run_stability_checks.failed_fire_count IS '窗口内失败的调度触发数量（paper_run_schedule_fires.status = FAILED）';
COMMENT ON COLUMN paper_run_stability_checks.recovery_count IS '窗口内恢复事件数量（paper_run_recovery_events）';
COMMENT ON COLUMN paper_run_stability_checks.report_count IS '窗口内日报数量（paper_run_daily_reports）';
COMMENT ON COLUMN paper_run_stability_checks.summary_json IS '验收摘要 JSON（明细计数 / CRITICAL 告警列表 / 判定原因），不保存密钥/token/cookie';
COMMENT ON COLUMN paper_run_stability_checks.created_at IS '记录创建时间（UTC）';
