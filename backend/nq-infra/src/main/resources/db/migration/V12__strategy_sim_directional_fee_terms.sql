-- 方向模型没有账户全局fee_rate，保留旧标量决策，新增互斥的冻结费用合同。
-- 已执行V1—V11不改写；不回填旧记录，不建立第二套成交或账务事实源。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE strategy_sim_decisions ADD COLUMN fee_model_json jsonb;
ALTER TABLE strategy_sim_decisions ALTER COLUMN fee_rate DROP NOT NULL;
ALTER TABLE strategy_sim_decisions DROP CONSTRAINT chk_strategy_sim_decision_cost;
ALTER TABLE strategy_sim_decisions ADD CONSTRAINT chk_strategy_sim_decision_cost CHECK (
    slippage_bps >= 0 AND slippage_bps < 10000 AND (
        (fee_model_json IS NULL AND fee_rate IS NOT NULL AND fee_rate > -1 AND fee_rate < 1)
        OR (fee_rate IS NULL AND fee_model_json IS NOT NULL
            AND jsonb_typeof(fee_model_json) = 'object'
            AND fee_model_json->>'feeAssumptionVersion' = 'ACCOUNT_FEE_MODEL_V2'
            AND fee_model_json->>'feeExchangeCode' = 'BINANCE'
            AND fee_model_json->>'feePaymentPolicy' = 'RECEIVED_ASSET'
            AND NOT (fee_model_json ? 'feeRate')
            AND jsonb_typeof(fee_model_json->'feeSourceSnapshotJson') = 'string'
            AND (fee_model_json->>'feeSourceSnapshotSha256') ~ '^[a-f0-9]{64}$')
    ) IS TRUE
);

COMMENT ON COLUMN strategy_sim_decisions.fee_rate IS
    '旧报价币标量成本费率；与fee_model_json互斥，方向费用决策为空，不能用零值伪装全局费率';
COMMENT ON COLUMN strategy_sim_decisions.fee_model_json IS
    '从已核验发布回测继承的完整方向费用执行合同；只保存来源及消费条件，实际费用由canonical Trade/Ledger记录';

-- 准入完成只更新状态和canonical引用，不能在订单在途或重启时换掉费用模型、时点或数量。
CREATE FUNCTION protect_strategy_sim_fee_terms() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.fee_model_json IS DISTINCT FROM OLD.fee_model_json OR (OLD.fee_model_json IS NOT NULL AND (
        NEW.fee_rate IS DISTINCT FROM OLD.fee_rate
        OR NEW.quantity IS DISTINCT FROM OLD.quantity
        OR NEW.side IS DISTINCT FROM OLD.side
        OR NEW.execution_price IS DISTINCT FROM OLD.execution_price
        OR NEW.execution_open_time IS DISTINCT FROM OLD.execution_open_time
        OR NEW.paper_run_id IS DISTINCT FROM OLD.paper_run_id
        OR NEW.canonical_account_id IS DISTINCT FROM OLD.canonical_account_id
    )) THEN
        RAISE EXCEPTION 'strategy SIM directional fee identity is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_strategy_sim_fee_terms_immutable BEFORE UPDATE ON strategy_sim_decisions
FOR EACH ROW EXECUTE FUNCTION protect_strategy_sim_fee_terms();
COMMENT ON FUNCTION protect_strategy_sim_fee_terms() IS
    '禁止已建立方向费用决策在状态完成、在途或恢复时变更费用消费身份；保留原准入状态迁移';
