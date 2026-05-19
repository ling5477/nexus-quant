-- V20__gate_i2_backtest_traceability.sql
-- 目的：落实 GateI-2 回测配置、运行结果和评估报告的追溯增强字段。
-- Why:
-- 1) GateI-2 要求一次 backtest run 能追溯到 strategy version、dataset、参数快照、配置快照和评估指标；
-- 2) 本 migration 只新增 GateI-2 必要字段和索引，不修改历史 migration，不改变回测核心算法；
-- 3) JSONB 快照字段只保存可审计业务输入，禁止保存 token、cookie、密钥等敏感信息。

ALTER TABLE backtest_configs
    ADD COLUMN strategy_version_id VARCHAR(128),
    ADD COLUMN strategy_version_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN param_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN config_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE backtest_configs
SET config_snapshot_json = config_json
WHERE config_snapshot_json = '{}'::jsonb;

ALTER TABLE backtest_configs
    ADD CONSTRAINT fk_backtest_configs_strategy_version
        FOREIGN KEY (strategy_version_id) REFERENCES strategy_versions (strategy_version_id);

CREATE INDEX idx_backtest_configs_strategy_version_id
    ON backtest_configs (strategy_version_id, updated_at DESC);

ALTER TABLE backtest_runs
    ADD COLUMN strategy_version_id VARCHAR(128),
    ADD COLUMN strategy_version_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN param_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN config_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE backtest_runs
SET config_snapshot_json = backtest_config_snapshot
WHERE config_snapshot_json = '{}'::jsonb;

ALTER TABLE backtest_runs
    ADD CONSTRAINT fk_backtest_runs_strategy_version
        FOREIGN KEY (strategy_version_id) REFERENCES strategy_versions (strategy_version_id);

CREATE INDEX idx_backtest_runs_strategy_version_id
    ON backtest_runs (strategy_version_id, requested_at DESC);

CREATE INDEX idx_backtest_eval_reports_backtest_run_id
    ON backtest_eval_reports (backtest_run_id);

ALTER TABLE backtest_eval_reports
    ADD COLUMN total_return NUMERIC(36, 18),
    ADD COLUMN annualized_return NUMERIC(36, 18),
    ADD COLUMN profit_loss_ratio NUMERIC(36, 18),
    ADD COLUMN metrics_json JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE backtest_eval_reports
SET total_return = total_return_rate,
    metrics_json = jsonb_build_object(
        'totalReturnRate', total_return_rate,
        'maxDrawdown', max_drawdown,
        'maxDrawdownRate', max_drawdown_rate,
        'winRate', win_rate,
        'tradeCount', trade_count,
        'sharpeRatio', sharpe_ratio
    )
WHERE metrics_json = '{}'::jsonb;

COMMENT ON COLUMN backtest_configs.strategy_version_id IS 'GateI-2 回测配置绑定的策略版本 ID，对应 strategy_versions.strategy_version_id；为空表示尚未绑定策略版本';
COMMENT ON COLUMN backtest_configs.strategy_version_snapshot_json IS 'GateI-2 回测配置绑定策略版本时固化的策略版本快照 JSONB，包含策略编码、版本号、状态、参数、配置、来源和 checksum；不得保存密钥、token、cookie';
COMMENT ON COLUMN backtest_configs.param_snapshot_json IS 'GateI-2 回测配置绑定策略版本时固化的参数快照 JSONB，用作后续 run 输入追溯；不得保存敏感凭证';
COMMENT ON COLUMN backtest_configs.config_snapshot_json IS 'GateI-2 回测配置自身的配置快照 JSONB，第一版从既有 config_json 回填，用于与策略版本配置快照区分；不得保存敏感凭证';

COMMENT ON COLUMN backtest_runs.strategy_version_id IS 'GateI-2 回测运行创建时从 backtest_configs 固化的策略版本 ID；后续配置重新绑定不会改写历史 run';
COMMENT ON COLUMN backtest_runs.strategy_version_snapshot_json IS 'GateI-2 回测运行创建时固化的策略版本快照 JSONB，用于历史运行、评估报告和后续发布追溯；不得保存密钥、token、cookie';
COMMENT ON COLUMN backtest_runs.param_snapshot_json IS 'GateI-2 回测运行创建时固化的参数快照 JSONB，来自回测配置绑定的策略版本参数快照；不得保存敏感凭证';
COMMENT ON COLUMN backtest_runs.config_snapshot_json IS 'GateI-2 回测运行创建时固化的回测配置快照 JSONB，第一版从 backtest_config_snapshot 回填；不得保存敏感凭证';

COMMENT ON COLUMN backtest_eval_reports.total_return IS 'GateI-2 评估总收益指标，第一版与既有 total_return_rate 同口径，固定为 net_pnl / initial_capital';
COMMENT ON COLUMN backtest_eval_reports.annualized_return IS 'GateI-2 年化收益率指标，按评估权益快照首尾时间差折算；时间差不可用时为空';
COMMENT ON COLUMN backtest_eval_reports.profit_loss_ratio IS 'GateI-2 盈亏比指标，固定口径为闭合盈利交易总收益 / 闭合亏损交易绝对值；亏损为 0 时返回 0';
COMMENT ON COLUMN backtest_eval_reports.metrics_json IS 'GateI-2 评估核心指标汇总 JSONB，保存 total_return、annualized_return、max_drawdown、win_rate、profit_loss_ratio、trade_count、sharpe_ratio 等展示指标；不得保存敏感凭证';
