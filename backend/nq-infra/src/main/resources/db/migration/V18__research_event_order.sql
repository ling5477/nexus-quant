-- 只为新事件保存真实执行/估值顺序；历史随机ID不能作为回填依据，冻结报告保持原样。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE sim_trades ADD COLUMN execution_sequence bigint;
ALTER TABLE sim_trades ADD CONSTRAINT chk_sim_trade_execution_sequence
    CHECK (execution_sequence IS NULL OR execution_sequence > 0);
CREATE UNIQUE INDEX idx_sim_trade_run_execution_sequence
    ON sim_trades(backtest_run_id, execution_sequence) WHERE execution_sequence IS NOT NULL;

ALTER TABLE sim_pnl_snapshots ADD COLUMN valuation_sequence bigint;
ALTER TABLE sim_pnl_snapshots ADD CONSTRAINT chk_sim_pnl_valuation_sequence
    CHECK (valuation_sequence IS NULL OR valuation_sequence > 0);
CREATE UNIQUE INDEX idx_sim_pnl_run_valuation_sequence
    ON sim_pnl_snapshots(backtest_run_id, valuation_sequence) WHERE valuation_sequence IS NOT NULL;

COMMENT ON COLUMN sim_trades.execution_sequence IS '本run真实成交追加序列，从1连续递增；旧null记录只在成交时间唯一时可计算，不能按随机ID或买卖方向推断';
COMMENT ON COLUMN sim_pnl_snapshots.valuation_sequence IS '本run逐bar真实估值追加序列，从1连续递增；相同availableAt仍按原bar次序重读，不伪造不同时间';
COMMENT ON INDEX idx_sim_trade_run_execution_sequence IS '同一研究run非空执行序列唯一，防重复事件身份；历史空值不补造';
COMMENT ON INDEX idx_sim_pnl_run_valuation_sequence IS '同一研究run非空估值序列唯一，防同事件被重复追加';
COMMENT ON CONSTRAINT chk_sim_trade_execution_sequence ON sim_trades IS '新执行顺序必须为正值；连续性及与真实时间一致性在完整run评估时复核';
COMMENT ON CONSTRAINT chk_sim_pnl_valuation_sequence ON sim_pnl_snapshots IS '新估值顺序必须为正值；完整run评估复核连续性与冻结bar映射';
