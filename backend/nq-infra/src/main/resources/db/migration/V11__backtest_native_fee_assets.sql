-- 原币费用与quote估值并存；旧成交缺少支付资料时保持null，不回填假想历史费用。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE sim_trades
    ADD COLUMN fee_currency varchar(20),
    ADD COLUMN fee_native_amount numeric(36,18),
    ADD COLUMN fee_payment_status varchar(64),
    ADD COLUMN fee_valuation_scale integer;

ALTER TABLE sim_trades ADD CONSTRAINT chk_sim_trade_native_fee
CHECK (
    (fee_currency IS NULL AND fee_native_amount IS NULL AND fee_payment_status IS NULL AND fee_valuation_scale IS NULL)
    OR
    (fee_currency IS NOT NULL AND fee_native_amount IS NOT NULL AND fee_payment_status IS NOT NULL AND fee_valuation_scale IS NOT NULL
     AND symbol ~ '^[A-Z0-9]{1,20}-[A-Z0-9]{1,20}$' AND side IN ('BUY','SELL')
     AND fee_payment_status IN ('RECEIVED_ASSET','DISCOUNT_ASSET_INSUFFICIENT_FALLBACK')
     AND fee_valuation_scale BETWEEN 0 AND 18
     AND quantity::text NOT IN ('NaN','Infinity','-Infinity') AND trade_price::text NOT IN ('NaN','Infinity','-Infinity')
     AND fee_amount::text NOT IN ('NaN','Infinity','-Infinity') AND fee_native_amount::text NOT IN ('NaN','Infinity','-Infinity')
     AND quantity > 0 AND trade_price > 0
     AND fee_currency = CASE WHEN side='BUY' THEN split_part(symbol,'-',1) ELSE split_part(symbol,'-',2) END
     AND fee_native_amount < CASE WHEN side='BUY' THEN quantity ELSE quantity*trade_price END
     AND fee_amount = round(CASE WHEN side='BUY' THEN fee_native_amount*trade_price ELSE fee_native_amount END,fee_valuation_scale))
);

COMMENT ON COLUMN sim_trades.fee_currency IS '新方向费用的实际模型支付资产；旧记录缺失时保持null，不能据此断言真实历史资产';
COMMENT ON COLUMN sim_trades.fee_native_amount IS '支付资产中的signed费用数量：正值收费、负值返佣、零值无费用；成交后按原资产消费';
COMMENT ON COLUMN sim_trades.fee_payment_status IS '采用的收到资产支付条件，或优惠资产不足后的未优惠回落；完整原币字段不可部分缺失';
COMMENT ON COLUMN sim_trades.fee_valuation_scale IS '原币费用折算quote时采用的HALF_UP精度，固定本次模型的估值口径';
COMMENT ON COLUMN sim_trades.fee_amount IS 'quote币种费用估值；旧标量合同不变，新base收费不能再次按本估值扣quote现金';
COMMENT ON CONSTRAINT chk_sim_trade_native_fee ON sim_trades IS '兼容旧缺失资料；新收到资产费用完整成组，保持方向、币种、signed数量和quote估值一致';
