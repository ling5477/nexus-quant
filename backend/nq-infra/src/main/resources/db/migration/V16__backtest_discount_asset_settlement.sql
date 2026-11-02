-- 第三币费用保留逐成交换算事实；不回填旧记录，不修改已经执行的V11。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE sim_trades
    ADD COLUMN fee_conversion_price numeric(36,18),
    ADD COLUMN fee_price_observed_at timestamptz,
    ADD COLUMN fee_price_visible_at timestamptz,
    ADD COLUMN fee_price_source_sha256 char(64),
    ADD COLUMN fee_price_source_json text;

ALTER TABLE sim_trades DROP CONSTRAINT chk_sim_trade_native_fee;
ALTER TABLE sim_trades ADD CONSTRAINT chk_sim_trade_native_fee CHECK ((
    (fee_currency IS NULL AND fee_native_amount IS NULL AND fee_payment_status IS NULL AND fee_valuation_scale IS NULL
     AND fee_conversion_price IS NULL AND fee_price_observed_at IS NULL AND fee_price_visible_at IS NULL
     AND fee_price_source_sha256 IS NULL AND fee_price_source_json IS NULL)
    OR
    (fee_currency IS NOT NULL AND fee_native_amount IS NOT NULL AND fee_payment_status IS NOT NULL AND fee_valuation_scale IS NOT NULL
     AND symbol ~ '^[A-Z0-9]{1,20}-[A-Z0-9]{1,20}$' AND side IN ('BUY','SELL')
     AND fee_valuation_scale BETWEEN 0 AND 18
     AND quantity::text NOT IN ('NaN','Infinity','-Infinity') AND trade_price::text NOT IN ('NaN','Infinity','-Infinity')
     AND fee_amount::text NOT IN ('NaN','Infinity','-Infinity') AND fee_native_amount::text NOT IN ('NaN','Infinity','-Infinity')
     AND quantity > 0 AND trade_price > 0
     AND (
        (fee_payment_status IN ('RECEIVED_ASSET','DISCOUNT_ASSET_INSUFFICIENT_FALLBACK')
         AND fee_conversion_price IS NULL AND fee_price_observed_at IS NULL AND fee_price_visible_at IS NULL
         AND fee_price_source_sha256 IS NULL AND fee_price_source_json IS NULL
         AND fee_currency = CASE WHEN side='BUY' THEN split_part(symbol,'-',1) ELSE split_part(symbol,'-',2) END
         AND fee_native_amount < CASE WHEN side='BUY' THEN quantity ELSE quantity*trade_price END
         AND fee_amount = round(CASE WHEN side='BUY' THEN fee_native_amount*trade_price ELSE fee_native_amount END,fee_valuation_scale))
        OR
        (fee_payment_status = 'DISCOUNT_ASSET_USED' AND fee_currency ~ '^[A-Z0-9]{1,20}$'
         AND fee_currency NOT IN (split_part(symbol,'-',1),split_part(symbol,'-',2))
         AND fee_conversion_price IS NOT NULL AND fee_price_observed_at IS NOT NULL AND fee_price_visible_at IS NOT NULL
         AND fee_price_source_sha256 IS NOT NULL AND fee_price_source_json IS NOT NULL
         AND fee_conversion_price::text NOT IN ('NaN','Infinity','-Infinity') AND fee_conversion_price > 0
         AND fee_price_observed_at <= fee_price_visible_at AND fee_price_visible_at <= traded_at
         AND fee_price_observed_at >= traded_at - interval '5 minutes'
         AND isfinite(fee_price_observed_at) AND isfinite(fee_price_visible_at) AND isfinite(traded_at)
         AND fee_price_source_sha256 ~ '^[a-f0-9]{64}$' AND octet_length(fee_price_source_json) <= 32768
         AND jsonb_typeof(fee_price_source_json::jsonb) = 'object'
         AND jsonb_typeof(fee_price_source_json::jsonb->'symbol') = 'string'
         AND jsonb_typeof(fee_price_source_json::jsonb->'closePrice') = 'string'
         AND jsonb_typeof(fee_price_source_json::jsonb->'observedAt') = 'string'
         AND jsonb_typeof(fee_price_source_json::jsonb->'visibleAt') = 'string'
         AND encode(sha256(convert_to(fee_price_source_json,'UTF8')),'hex') = fee_price_source_sha256
         AND fee_price_source_json::jsonb->>'symbol' = fee_currency || '-' || split_part(symbol,'-',2)
         AND fee_price_source_json::jsonb->>'marketType' = 'SPOT'
         AND (fee_price_source_json::jsonb->>'closePrice')::numeric = fee_conversion_price
         AND (fee_price_source_json::jsonb->>'observedAt')::timestamptz = fee_price_observed_at
         AND (fee_price_source_json::jsonb->>'visibleAt')::timestamptz = fee_price_visible_at
         AND fee_amount = round(fee_native_amount*fee_conversion_price,fee_valuation_scale))
     ))
) IS TRUE);

COMMENT ON COLUMN sim_trades.fee_conversion_price IS '第三资产每单位的quote估值；仅优惠原币支付完整冻结，旧记录保持null';
COMMENT ON COLUMN sim_trades.fee_price_observed_at IS '本次换算点观察时点，必须不晚于可见与成交时点且最多陈旧五分钟';
COMMENT ON COLUMN sim_trades.fee_price_visible_at IS '本次换算点首次可见时点，禁止未来资料影响历史成交';
COMMENT ON COLUMN sim_trades.fee_price_source_sha256 IS '逐成交换算点的规范来源摘要，与完整冻结series摘要分别保存';
COMMENT ON COLUMN sim_trades.fee_price_source_json IS '逐成交换算来源资料；第三币成本可重读，不用当前行情覆盖';
COMMENT ON CONSTRAINT chk_sim_trade_native_fee ON sim_trades IS '旧资料缺失、收到资产及优惠第三币分别完整成组；方向、时间、原币与quote估值一致';

-- HOLD快照也必须解释优惠币价值；只有成交来源无法重建持仓期间的权益曲线。
ALTER TABLE sim_pnl_snapshots
    ADD COLUMN discount_asset_currency varchar(20),
    ADD COLUMN discount_asset_balance numeric(36,18),
    ADD COLUMN discount_asset_quote_value numeric(36,18),
    ADD COLUMN discount_conversion_price numeric(36,18),
    ADD COLUMN discount_price_observed_at timestamptz,
    ADD COLUMN discount_price_visible_at timestamptz,
    ADD COLUMN discount_price_source_sha256 char(64),
    ADD COLUMN discount_price_source_json text;

ALTER TABLE sim_pnl_snapshots ADD CONSTRAINT chk_sim_pnl_discount_asset CHECK ((
    (discount_asset_currency IS NULL AND discount_asset_balance IS NULL AND discount_asset_quote_value IS NULL
     AND discount_conversion_price IS NULL AND discount_price_observed_at IS NULL AND discount_price_visible_at IS NULL
     AND discount_price_source_sha256 IS NULL AND discount_price_source_json IS NULL)
    OR
    (discount_asset_currency IS NOT NULL AND discount_asset_balance IS NOT NULL AND discount_asset_quote_value IS NOT NULL
     AND discount_conversion_price IS NOT NULL AND discount_price_observed_at IS NOT NULL AND discount_price_visible_at IS NOT NULL
     AND discount_price_source_sha256 IS NOT NULL AND discount_price_source_json IS NOT NULL
     AND discount_asset_currency ~ '^[A-Z0-9]{1,20}$' AND discount_asset_balance >= 0 AND discount_conversion_price > 0
     AND discount_asset_balance::text NOT IN ('NaN','Infinity','-Infinity')
     AND discount_asset_quote_value::text NOT IN ('NaN','Infinity','-Infinity')
     AND discount_conversion_price::text NOT IN ('NaN','Infinity','-Infinity')
     AND discount_price_observed_at <= discount_price_visible_at AND discount_price_visible_at <= snapshot_time
     AND discount_price_observed_at >= snapshot_time - interval '5 minutes'
     AND isfinite(discount_price_observed_at) AND isfinite(discount_price_visible_at) AND isfinite(snapshot_time)
     AND cash_balance::text NOT IN ('NaN','Infinity','-Infinity') AND position_market_value::text NOT IN ('NaN','Infinity','-Infinity')
     AND equity::text NOT IN ('NaN','Infinity','-Infinity')
     AND discount_price_source_sha256 ~ '^[a-f0-9]{64}$' AND octet_length(discount_price_source_json) <= 32768
     AND jsonb_typeof(discount_price_source_json::jsonb) = 'object'
     AND jsonb_typeof(discount_price_source_json::jsonb->'symbol') = 'string'
     AND jsonb_typeof(discount_price_source_json::jsonb->'closePrice') = 'string'
     AND jsonb_typeof(discount_price_source_json::jsonb->'observedAt') = 'string'
     AND jsonb_typeof(discount_price_source_json::jsonb->'visibleAt') = 'string'
     AND encode(sha256(convert_to(discount_price_source_json,'UTF8')),'hex') = discount_price_source_sha256
     AND discount_price_source_json::jsonb->>'symbol' = discount_asset_currency || '-USDT'
     AND discount_price_source_json::jsonb->>'marketType' = 'SPOT'
     AND (discount_price_source_json::jsonb->>'closePrice')::numeric = discount_conversion_price
     AND (discount_price_source_json::jsonb->>'observedAt')::timestamptz = discount_price_observed_at
     AND (discount_price_source_json::jsonb->>'visibleAt')::timestamptz = discount_price_visible_at
     AND discount_asset_quote_value = round(discount_asset_balance*discount_conversion_price,8)
     AND equity = round(cash_balance+position_market_value+discount_asset_quote_value,18))
) IS TRUE);

COMMENT ON COLUMN sim_pnl_snapshots.discount_asset_currency IS '参与本权益快照的优惠资产币种；旧单币资料不回填';
COMMENT ON COLUMN sim_pnl_snapshots.discount_asset_balance IS '本时点已结算的优惠资产原币余额，返佣仅在成交后计入';
COMMENT ON COLUMN sim_pnl_snapshots.discount_asset_quote_value IS '优惠资产余额按当时可见来源折算quote，统一canonical八位HALF_UP';
COMMENT ON COLUMN sim_pnl_snapshots.discount_conversion_price IS '本权益估值的第三币quote单价，不使用主交易对价格';
COMMENT ON COLUMN sim_pnl_snapshots.discount_price_observed_at IS '估值价格观察时间，最多陈旧五分钟';
COMMENT ON COLUMN sim_pnl_snapshots.discount_price_visible_at IS '估值价格首次可见时间，不晚于快照时间';
COMMENT ON COLUMN sim_pnl_snapshots.discount_price_source_sha256 IS '本权益换算点的冻结来源摘要';
COMMENT ON COLUMN sim_pnl_snapshots.discount_price_source_json IS '本权益换算点的完整冻结来源资料';
COMMENT ON CONSTRAINT chk_sim_pnl_discount_asset ON sim_pnl_snapshots IS '新增优惠币分项完整成组，余额不透支且权益等于现金、主持仓与优惠币价值之和';
