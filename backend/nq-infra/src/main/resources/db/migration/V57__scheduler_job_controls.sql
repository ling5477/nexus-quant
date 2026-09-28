-- 仅允许固定代码注册的任务；迁移不授予任何自动执行权限。
SET lock_timeout = '5s';

CREATE TABLE scheduled_job_controls (
    job_key VARCHAR(48) PRIMARY KEY CHECK (job_key IN (
        'CONTINUOUS_SIM_POLL', 'PAPER_MATCHING', 'STRATEGY_RECOVERY',
        'LEDGER_RECONCILIATION', 'OKX_RECOVERY', 'OKX_RECONCILIATION',
        'BINANCE_RECONCILIATION', 'VALIDATION_EVIDENCE_REFRESH')),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    fixed_delay_ms BIGINT NOT NULL CHECK (fixed_delay_ms BETWEEN 1000 AND 86400000),
    next_run_at TIMESTAMPTZ,
    last_started_at TIMESTAMPTZ,
    last_finished_at TIMESTAMPTZ,
    last_status VARCHAR(16) NOT NULL DEFAULT 'NEVER_RUN'
        CHECK (last_status IN ('NEVER_RUN', 'RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    last_error_code VARCHAR(64),
    active_run_id UUID,
    consecutive_failures INTEGER NOT NULL DEFAULT 0 CHECK (consecutive_failures >= 0),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_by VARCHAR(128),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_scheduled_job_next_run CHECK (enabled OR next_run_at IS NULL)
);

CREATE INDEX idx_scheduled_job_controls_due ON scheduled_job_controls (next_run_at, job_key)
    WHERE enabled;

INSERT INTO scheduled_job_controls (job_key, fixed_delay_ms) VALUES
    ('CONTINUOUS_SIM_POLL', 300000),
    ('PAPER_MATCHING', 2000),
    ('STRATEGY_RECOVERY', 5000),
    ('LEDGER_RECONCILIATION', 30000),
    ('OKX_RECOVERY', 15000),
    ('OKX_RECONCILIATION', 5000),
    ('BINANCE_RECONCILIATION', 5000),
    ('VALIDATION_EVIDENCE_REFRESH', 300000);

COMMENT ON TABLE scheduled_job_controls IS '固定 Java 任务注册表的动态启停、间隔和最近执行状态；不存可执行代码或外部请求';
COMMENT ON COLUMN scheduled_job_controls.job_key IS '仅允许已审查的固定代码任务身份，未知身份不得执行';
COMMENT ON COLUMN scheduled_job_controls.next_run_at IS '启用任务的下一次可执行时间，禁用时必须为空';
COMMENT ON COLUMN scheduled_job_controls.version IS '运维配置乐观锁版本，运行状态更新不改变配置版本';
COMMENT ON COLUMN scheduled_job_controls.active_run_id IS '最近一次执行身份，防止迟到的完成回写覆盖新执行';
