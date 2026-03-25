-- V7__gate_f1_research_backtest_skeleton.sql
-- 目的：为 GateF-1 建立研究配置、回测配置、回测运行的最小持久化骨架。
-- Why:
-- 1) GateF-1 必须建立独立于 GateE strategy_runs/orders/trades 的研究域主链；
-- 2) research_config 需要固化 source_strategy_id + strategy_snapshot，避免运行时依赖可变策略定义；
-- 3) backtest_run 必须保存配置快照，确保后续配置变更不会污染历史运行事实。

CREATE TABLE research_configs (
    research_config_id VARCHAR(128) PRIMARY KEY,
    source_strategy_id VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    strategy_snapshot JSONB NOT NULL,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_research_configs_source_strategy
        FOREIGN KEY (source_strategy_id) REFERENCES strategy_definitions (strategy_id)
);

CREATE INDEX idx_research_configs_source_strategy
    ON research_configs (source_strategy_id, created_at DESC);

CREATE TABLE backtest_configs (
    backtest_config_id VARCHAR(128) PRIMARY KEY,
    research_config_id VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    evaluation_spec_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_backtest_configs_research
        FOREIGN KEY (research_config_id) REFERENCES research_configs (research_config_id)
);

CREATE INDEX idx_backtest_configs_research
    ON backtest_configs (research_config_id, created_at DESC);

CREATE TABLE backtest_runs (
    backtest_run_id VARCHAR(128) PRIMARY KEY,
    backtest_config_id VARCHAR(128) NOT NULL,
    research_config_id VARCHAR(128) NOT NULL,
    source_strategy_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    strategy_snapshot JSONB NOT NULL,
    backtest_config_snapshot JSONB NOT NULL,
    summary_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    requested_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    failure_code VARCHAR(128),
    failure_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_backtest_runs_config
        FOREIGN KEY (backtest_config_id) REFERENCES backtest_configs (backtest_config_id),
    CONSTRAINT fk_backtest_runs_research
        FOREIGN KEY (research_config_id) REFERENCES research_configs (research_config_id),
    CONSTRAINT fk_backtest_runs_source_strategy
        FOREIGN KEY (source_strategy_id) REFERENCES strategy_definitions (strategy_id),
    CONSTRAINT chk_backtest_runs_status
        CHECK (status IN ('CREATED', 'PREPARING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_backtest_runs_backtest_config_id
    ON backtest_runs (backtest_config_id, requested_at DESC);

CREATE INDEX idx_backtest_runs_research_config_id
    ON backtest_runs (research_config_id, requested_at DESC);

CREATE INDEX idx_backtest_runs_status
    ON backtest_runs (status, requested_at DESC);
