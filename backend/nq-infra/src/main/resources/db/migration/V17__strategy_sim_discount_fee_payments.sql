-- 决策支付选择是原账户模型、已可见换算和已结算资金的派生输入；经济事实仍只写 canonical Trade/Ledger。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE strategy_sim_decisions ADD COLUMN fee_charge_json jsonb;
ALTER TABLE strategy_sim_decisions DROP CONSTRAINT chk_strategy_sim_decision_cost;
ALTER TABLE strategy_sim_decisions ADD CONSTRAINT chk_strategy_sim_decision_cost CHECK (
    slippage_bps >= 0 AND slippage_bps < 10000 AND (
        (fee_model_json IS NULL AND fee_rate IS NOT NULL AND fee_rate > -1 AND fee_rate < 1 AND fee_charge_json IS NULL)
        OR (fee_rate IS NULL AND fee_model_json IS NOT NULL
            AND jsonb_typeof(fee_model_json) = 'object'
            AND fee_model_json->>'feeAssumptionVersion' = 'ACCOUNT_FEE_MODEL_V2'
            AND fee_model_json->>'feeExchangeCode' = 'BINANCE'
            AND NOT (fee_model_json ? 'feeRate')
            AND jsonb_typeof(fee_model_json->'feeSourceSnapshotJson') = 'string'
            AND (fee_model_json->>'feeSourceSnapshotSha256') ~ '^[a-f0-9]{64}$'
            AND (
                (fee_model_json->>'feePaymentPolicy' = 'RECEIVED_ASSET' AND fee_charge_json IS NULL)
                OR (fee_model_json->>'feePaymentPolicy' = 'DISCOUNT_ASSET_IF_FUNDED'
                    AND (fee_model_json->>'discountPriceDatasetId') ~ '^[a-f0-9-]{36}$'
                    AND (fee_model_json->>'discountPriceSnapshotId') ~ '^[a-f0-9-]{36}$'
                    AND (fee_model_json->>'discountPriceSnapshotSha256') ~ '^[a-f0-9]{64}$'
                    AND (fee_model_json->>'discountAssetBudgetFraction') ~ '^0\.[0-9]{1,8}$'
                    AND (fee_model_json->>'discountAssetBudgetFraction')::numeric > 0
                    AND (fee_model_json->>'discountAssetBudgetFraction')::numeric < 1
                    AND ((side IS NULL AND (quantity IS NULL OR quantity=0) AND fee_charge_json IS NULL)
                        OR (side IN ('BUY','SELL') AND quantity>0 AND fee_charge_json IS NOT NULL)))
            ))
    ) IS TRUE
);

ALTER TABLE strategy_sim_decisions ADD CONSTRAINT chk_strategy_sim_fee_payment CHECK (
    fee_charge_json IS NULL OR (
        jsonb_typeof(fee_charge_json)='object'
        AND octet_length(fee_charge_json::text)<=131072
        AND fee_charge_json->>'version'='SIM_FEE_PAYMENT_V1'
        AND fee_charge_json->>'symbol'=fee_model_json->>'feeSymbol'
        AND fee_charge_json->>'symbol' ~ '^[A-Z0-9]{1,20}-USDT$'
        AND fee_charge_json->>'side'=side
        AND (fee_charge_json->>'quantity') ~ '^[0-9]{1,30}(\.[0-9]{1,8})?$'
        AND (fee_charge_json->>'quantity')::numeric=quantity
        AND (fee_charge_json->>'price') ~ '^[0-9]{1,30}(\.[0-9]{1,8})?$'
        AND (fee_charge_json->>'price')::numeric=execution_price
        AND (fee_charge_json->>'executionAt')::timestamptz=execution_open_time
        AND fee_charge_json->>'asset' ~ '^[A-Z0-9]{1,20}$'
        AND fee_charge_json->>'asset'<>split_part(fee_charge_json->>'symbol','-',1)
        AND fee_charge_json->>'asset'<>'USDT'
        AND fee_charge_json->>'quoteCurrency'='USDT'
        AND (fee_charge_json->>'quotePerAsset') ~ '^[0-9]{1,20}(\.[0-9]{1,18})?$'
        AND (fee_charge_json->>'quotePerAsset')::numeric>0
        AND (fee_charge_json->>'availableAfterReserves') ~ '^[0-9]{1,30}(\.[0-9]{1,18})?$'
        AND (fee_charge_json->>'amount') ~ '^-?[0-9]{1,30}(\.[0-9]{1,8})?$'
        AND (fee_charge_json->>'quoteValue') ~ '^-?[0-9]{1,30}(\.[0-9]{1,8})?$'
        AND (fee_charge_json->>'positiveReserve')::numeric=greatest((fee_charge_json->>'amount')::numeric,0)
        AND (fee_charge_json->>'observedAt')::timestamptz <= (fee_charge_json->>'visibleAt')::timestamptz
        AND (fee_charge_json->>'visibleAt')::timestamptz <= execution_open_time
        AND (fee_charge_json->>'maxPriceAgeSeconds') ~ '^[0-9]{1,3}$'
        AND (fee_charge_json->>'maxPriceAgeSeconds')::integer BETWEEN 1 AND 300
        AND (fee_charge_json->>'observedAt')::timestamptz >= execution_open_time
            - make_interval(secs => (fee_charge_json->>'maxPriceAgeSeconds')::integer)
        AND (fee_charge_json->>'sourceSha256') ~ '^[a-f0-9]{64}$'
        AND jsonb_typeof(fee_charge_json->'sourceJson')='string'
        AND (
            (fee_charge_json->>'paymentStatus'='DISCOUNT_ASSET_USED'
                AND fee_charge_json->>'currency'=fee_charge_json->>'asset'
                AND (fee_charge_json->>'availableAfterReserves')::numeric >= (fee_charge_json->>'positiveReserve')::numeric
                AND (fee_charge_json->>'quoteValue')::numeric = round(
                    (fee_charge_json->>'amount')::numeric*(fee_charge_json->>'quotePerAsset')::numeric,8))
            OR (fee_charge_json->>'paymentStatus'='DISCOUNT_ASSET_INSUFFICIENT_FALLBACK'
                AND fee_charge_json->>'currency'=CASE WHEN side='BUY' THEN split_part(fee_charge_json->>'symbol','-',1) ELSE 'USDT' END
                AND (fee_charge_json->>'quoteValue')::numeric = round(
                    CASE WHEN side='BUY' THEN (fee_charge_json->>'amount')::numeric*execution_price
                    ELSE (fee_charge_json->>'amount')::numeric END,8))
        )
    ) IS TRUE
);

COMMENT ON COLUMN strategy_sim_decisions.fee_charge_json IS
    '优惠决策冻结的原币费用、quote估值、正费用预留及原换算/可用资金输入；在途和重启复用原选择，不按后来余额重新选币';
COMMENT ON CONSTRAINT chk_strategy_sim_fee_payment ON strategy_sim_decisions IS
    '优惠支付输入完整成组并对应原决策数量/价格/时点；返佣预留为零，未来或过期换算拒绝，旧收到资产与标量保持无此资料';

CREATE OR REPLACE FUNCTION protect_strategy_sim_fee_terms() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.fee_model_json IS DISTINCT FROM OLD.fee_model_json
        OR NEW.fee_charge_json IS DISTINCT FROM OLD.fee_charge_json OR (OLD.fee_model_json IS NOT NULL AND (
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
COMMENT ON FUNCTION protect_strategy_sim_fee_terms() IS
    '保持原方向决策身份保护并冻结优惠币资金与支付选择；仅允许准入状态和canonical事实引用按既有流程完成';
