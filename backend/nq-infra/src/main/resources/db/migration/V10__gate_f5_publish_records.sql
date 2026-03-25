-- V10__gate_f5_publish_records.sql
-- 目的：为 GateF-5 建立研究产物发布到执行域的独立事实表。
-- Why:
-- 1) publish 必须独立于 backtest_runs / strategy_runs 持久化；
-- 2) 同一 backtestRunId 重复 publish 必须可追踪且幂等；
-- 3) publish 明细不能继续塞进 backtest_runs.summary_json。

CREATE TABLE backtest_publish_records (
    publish_record_id VARCHAR(128) PRIMARY KEY,
    backtest_run_id VARCHAR(128) NOT NULL,
    research_config_id VARCHAR(128) NOT NULL,
    backtest_config_id VARCHAR(128) NOT NULL,
    source_strategy_id VARCHAR(128) NOT NULL,
    eval_report_id VARCHAR(128),
    target_strategy_definition_id VARCHAR(128),
    publish_status VARCHAR(32) NOT NULL,
    publish_name VARCHAR(255) NOT NULL,
    publish_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    evaluation_summary_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    failure_code VARCHAR(128),
    failure_message TEXT,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_backtest_publish_records_run UNIQUE (backtest_run_id),
    CONSTRAINT fk_backtest_publish_records_run
        FOREIGN KEY (backtest_run_id) REFERENCES backtest_runs (backtest_run_id),
    CONSTRAINT fk_backtest_publish_records_research
        FOREIGN KEY (research_config_id) REFERENCES research_configs (research_config_id),
    CONSTRAINT fk_backtest_publish_records_backtest
        FOREIGN KEY (backtest_config_id) REFERENCES backtest_configs (backtest_config_id),
    CONSTRAINT chk_backtest_publish_records_status
        CHECK (publish_status IN ('SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_backtest_publish_records_status
    ON backtest_publish_records (publish_status, published_at DESC);
