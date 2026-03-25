-- V9__gate_f4_evaluation_reports.sql
-- 目的：为 GateF-4 建立 run 级评估报告持久化表。
-- Why:
-- 1) GateF-4 必须把评估结果从 backtest_runs.summary_json 中独立出来；
-- 2) 评估报告必须可重复 evaluate 且按 run 唯一覆盖更新；
-- 3) GateF-5 之前，评估域只消费 sim_* 与 run/config 事实，不回写执行事实。

CREATE TABLE backtest_eval_reports (
    eval_report_id VARCHAR(128) PRIMARY KEY,
    backtest_run_id VARCHAR(128) NOT NULL,
    evaluation_status VARCHAR(32) NOT NULL,
    initial_capital NUMERIC(36, 18),
    final_cash_balance NUMERIC(36, 18),
    final_position_market_value NUMERIC(36, 18),
    final_equity NUMERIC(36, 18),
    realized_pnl NUMERIC(36, 18),
    unrealized_pnl NUMERIC(36, 18),
    net_pnl NUMERIC(36, 18),
    total_return_rate NUMERIC(36, 18),
    total_fee NUMERIC(36, 18),
    total_slippage NUMERIC(36, 18),
    order_count INTEGER,
    trade_count INTEGER,
    winning_trade_count INTEGER,
    losing_trade_count INTEGER,
    flat_trade_count INTEGER,
    win_rate NUMERIC(36, 18),
    max_drawdown NUMERIC(36, 18),
    max_drawdown_rate NUMERIC(36, 18),
    sharpe_ratio NUMERIC(36, 18),
    report_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    failure_code VARCHAR(128),
    failure_message TEXT,
    evaluated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_backtest_eval_reports_run UNIQUE (backtest_run_id),
    CONSTRAINT fk_backtest_eval_reports_run
        FOREIGN KEY (backtest_run_id) REFERENCES backtest_runs (backtest_run_id),
    CONSTRAINT chk_backtest_eval_reports_status
        CHECK (evaluation_status IN ('SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_backtest_eval_reports_status
    ON backtest_eval_reports (evaluation_status, evaluated_at DESC);
