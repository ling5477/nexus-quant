-- 旧标量执行合同冻结十八位费率，金额消费仍为八位；八位费率列会把有效微小费用静默变成零。
-- 仅扩大存储精度，不回算历史费率或账务，不改变符号、预留、互斥模型和状态约束。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE strategy_sim_decisions ALTER COLUMN fee_rate TYPE numeric(38,18);
COMMENT ON COLUMN strategy_sim_decisions.fee_rate IS
    '原冻结报价币标量费率，十八位小数原样保留；成交费用仍按八位金额舍入；与fee_model_json互斥，方向模型为空';
