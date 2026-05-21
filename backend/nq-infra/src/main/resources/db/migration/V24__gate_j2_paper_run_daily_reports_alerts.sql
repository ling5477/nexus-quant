-- GateJ-2: Paper run daily reports and alerts
-- Supports Paper Trading stable operation: daily monitoring reports and alert events.

------------------------------------------------------------
-- Table: paper_run_daily_reports
------------------------------------------------------------
CREATE TABLE paper_run_daily_reports (
    report_id       VARCHAR(64)    PRIMARY KEY,
    paper_run_id    VARCHAR(64)    NOT NULL,
    report_date     DATE           NOT NULL,
    status          VARCHAR(16)    NOT NULL,
    total_equity    NUMERIC(20,8),
    daily_pnl       NUMERIC(20,8),
    daily_return    NUMERIC(12,8),
    max_drawdown    NUMERIC(12,8),
    order_count     INT            NOT NULL DEFAULT 0,
    trade_count     INT            NOT NULL DEFAULT 0,
    alert_count     INT            NOT NULL DEFAULT 0,
    risk_reject_count INT          NOT NULL DEFAULT 0,
    report_json     JSONB          NOT NULL DEFAULT '{}'::jsonb,
    generated_at    TIMESTAMPTZ    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,

    CONSTRAINT chk_daily_reports_status CHECK (status IN ('GENERATED', 'PARTIAL', 'FAILED')),
    CONSTRAINT uq_daily_reports_run_date UNIQUE (paper_run_id, report_date),
    CONSTRAINT fk_daily_reports_run FOREIGN KEY (paper_run_id)
        REFERENCES paper_trading_runs (paper_run_id)
);

CREATE INDEX idx_daily_reports_run_id_date ON paper_run_daily_reports (paper_run_id, report_date DESC);
CREATE INDEX idx_daily_reports_status ON paper_run_daily_reports (status);

COMMENT ON TABLE paper_run_daily_reports IS 'Paper run 日报，每日运行摘要';
COMMENT ON COLUMN paper_run_daily_reports.report_id IS '日报 ID，格式 rpt-<uuid>';
COMMENT ON COLUMN paper_run_daily_reports.paper_run_id IS '关联 Paper run ID';
COMMENT ON COLUMN paper_run_daily_reports.report_date IS '报告日期';
COMMENT ON COLUMN paper_run_daily_reports.status IS '日报状态：GENERATED / PARTIAL / FAILED';
COMMENT ON COLUMN paper_run_daily_reports.total_equity IS '当日总权益（截止日末），口径为 equity_curve_snapshots 最新快照的 total_equity';
COMMENT ON COLUMN paper_run_daily_reports.daily_pnl IS '当日盈亏（当日末权益 - 前日末权益），无前日数据时为 null';
COMMENT ON COLUMN paper_run_daily_reports.daily_return IS '当日收益率（daily_pnl / 前日末权益），无前日数据时为 null';
COMMENT ON COLUMN paper_run_daily_reports.max_drawdown IS '当日最大回撤（当日 equity_curve 最大回撤值），无数据时为 null';
COMMENT ON COLUMN paper_run_daily_reports.order_count IS '当日订单数';
COMMENT ON COLUMN paper_run_daily_reports.trade_count IS '当日成交数';
COMMENT ON COLUMN paper_run_daily_reports.alert_count IS '当日告警数';
COMMENT ON COLUMN paper_run_daily_reports.risk_reject_count IS '当日风控拒绝数';
COMMENT ON COLUMN paper_run_daily_reports.report_json IS '日报详细数据（各交易对盈亏明细等），不保存密钥/token/cookie';
COMMENT ON COLUMN paper_run_daily_reports.generated_at IS '日报生成时间';
COMMENT ON COLUMN paper_run_daily_reports.created_at IS '创建时间';

------------------------------------------------------------
-- Table: paper_run_alerts
------------------------------------------------------------
CREATE TABLE paper_run_alerts (
    alert_id            VARCHAR(64)   PRIMARY KEY,
    paper_run_id        VARCHAR(64)   NOT NULL,
    alert_type          VARCHAR(64)   NOT NULL,
    severity            VARCHAR(16)   NOT NULL,
    status              VARCHAR(16)   NOT NULL,
    title               VARCHAR(512)  NOT NULL,
    message             TEXT,
    source              VARCHAR(128),
    event_snapshot_json  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    acknowledged_by     VARCHAR(512),
    acknowledged_at     TIMESTAMPTZ,
    resolved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,

    CONSTRAINT chk_alerts_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_alerts_status CHECK (status IN ('OPEN', 'ACKED', 'RESOLVED')),
    CONSTRAINT fk_alerts_run FOREIGN KEY (paper_run_id)
        REFERENCES paper_trading_runs (paper_run_id)
);

CREATE INDEX idx_alerts_run_id_created ON paper_run_alerts (paper_run_id, created_at DESC);
CREATE INDEX idx_alerts_status ON paper_run_alerts (status);
CREATE INDEX idx_alerts_severity ON paper_run_alerts (severity);

COMMENT ON TABLE paper_run_alerts IS 'Paper run 告警事件';
COMMENT ON COLUMN paper_run_alerts.alert_id IS '告警 ID，格式 alt-<uuid>';
COMMENT ON COLUMN paper_run_alerts.paper_run_id IS '关联 Paper run ID';
COMMENT ON COLUMN paper_run_alerts.alert_type IS '告警类型：HEARTBEAT_LAG / SCHEDULE_FIRE_FAILED / RISK_WARNING / EMERGENCY_STOP / SYSTEM_NOTICE';
COMMENT ON COLUMN paper_run_alerts.severity IS '严重程度：LOW / MEDIUM / HIGH / CRITICAL';
COMMENT ON COLUMN paper_run_alerts.status IS '告警状态：OPEN / ACKED / RESOLVED';
COMMENT ON COLUMN paper_run_alerts.title IS '告警标题';
COMMENT ON COLUMN paper_run_alerts.message IS '告警详情';
COMMENT ON COLUMN paper_run_alerts.source IS '告警来源：SCHEDULE / HEARTBEAT / RISK / MONITOR / MANUAL';
COMMENT ON COLUMN paper_run_alerts.event_snapshot_json IS '事件快照（触发时上下文），不保存密钥/token/cookie';
COMMENT ON COLUMN paper_run_alerts.acknowledged_by IS '确认人';
COMMENT ON COLUMN paper_run_alerts.acknowledged_at IS '确认时间';
COMMENT ON COLUMN paper_run_alerts.resolved_at IS '解决时间';
COMMENT ON COLUMN paper_run_alerts.created_at IS '创建时间';
COMMENT ON COLUMN paper_run_alerts.updated_at IS '更新时间';
