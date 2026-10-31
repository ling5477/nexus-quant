-- 只扩展正式SIM决策的成本符号范围；保留历史费用、滑点与身份事实，不回填或改写旧迁移。
-- 八位成交费用与十八位冻结费率仍由原列承载；返佣不得在撮合前抵减资金预留。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE strategy_sim_decisions DROP CONSTRAINT chk_strategy_sim_decision_cost;
ALTER TABLE strategy_sim_decisions ADD CONSTRAINT chk_strategy_sim_decision_cost
    CHECK (fee_rate > -1 AND fee_rate < 1 AND slippage_bps >= 0 AND slippage_bps < 10000);

COMMENT ON COLUMN strategy_sim_decisions.fee_rate IS
    '冻结的NQ成本费率：正值收费、负值返佣、零值无费用；返佣在正式成交后才影响资产，不能预支';
