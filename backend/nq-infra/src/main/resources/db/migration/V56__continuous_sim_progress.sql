-- 连续 SIM 的游标与已观察 bar 归具体 Paper run，历史冻结数据集保持不可变。
SET lock_timeout = '5s';

CREATE TABLE continuous_sim_runs (
    paper_run_id VARCHAR(64) PRIMARY KEY REFERENCES paper_trading_runs(paper_run_id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING','STALLED','STOPPED')),
    strategy_version_id VARCHAR(128) NOT NULL REFERENCES strategy_versions(strategy_version_id) ON DELETE RESTRICT,
    strategy_checksum VARCHAR(64) NOT NULL CHECK (strategy_checksum ~ '^[0-9a-f]{64}$'),
    cost_sha256 VARCHAR(64) NOT NULL CHECK (cost_sha256 ~ '^[0-9a-f]{64}$'),
    seed_last_open_time TIMESTAMPTZ NOT NULL,
    last_processed_open_time TIMESTAMPTZ NOT NULL,
    last_processed_close_time TIMESTAMPTZ NOT NULL,
    last_processed_sha256 VARCHAR(64) NOT NULL CHECK (last_processed_sha256 ~ '^[0-9a-f]{64}$'),
    last_observed_open_time TIMESTAMPTZ NOT NULL,
    gap_start_open_time TIMESTAMPTZ,
    started_at TIMESTAMPTZ NOT NULL,
    last_poll_at TIMESTAMPTZ,
    last_successful_poll_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    block_reason VARCHAR(64),
    consecutive_poll_failures INTEGER NOT NULL DEFAULT 0 CHECK (consecutive_poll_failures >= 0),
    CONSTRAINT chk_continuous_sim_cursor_order CHECK (
        last_processed_open_time >= seed_last_open_time
        AND last_observed_open_time >= last_processed_open_time
        AND last_processed_close_time > last_processed_open_time)
);

CREATE TABLE continuous_sim_bars (
    paper_run_id VARCHAR(64) NOT NULL REFERENCES continuous_sim_runs(paper_run_id) ON DELETE RESTRICT,
    open_time TIMESTAMPTZ NOT NULL,
    close_time TIMESTAMPTZ NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    bar_sha256 VARCHAR(64) NOT NULL CHECK (bar_sha256 ~ '^[0-9a-f]{64}$'),
    bar_json JSONB NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (paper_run_id, open_time),
    CONSTRAINT chk_continuous_sim_bar_time CHECK (
        close_time > open_time AND available_at >= close_time)
);

COMMENT ON TABLE continuous_sim_runs IS '每条连续策略 SIM run 的持久进度和状态；不共享交易对全局游标';
COMMENT ON TABLE continuous_sim_bars IS '连续 SIM 实际观察到的单根公开 bar 身份；已处理值不得被行情修订覆盖';
COMMENT ON COLUMN strategy_sim_decisions.execution_open_time IS
    '历史回放为执行 bar 开盘时刻；连续 SIM 为公开报价的实际观察时刻，具体类型见 input_snapshot_json';
COMMENT ON COLUMN strategy_sim_decisions.execution_bar_sha256 IS
    '历史回放为执行 bar 身份；连续 SIM 为公开报价时刻和价格身份，具体类型见 input_snapshot_json';
