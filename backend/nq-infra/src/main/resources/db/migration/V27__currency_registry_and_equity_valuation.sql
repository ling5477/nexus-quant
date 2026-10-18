-- 目录只声明身份；注册币种不自动启用估值、兑换或交易能力。
CREATE TABLE currency_definitions (
    currency_code varchar(32) PRIMARY KEY CHECK (currency_code ~ '^[A-Z0-9]{2,32}$'),
    currency_kind varchar(32) NOT NULL CHECK (currency_kind IN ('STABLECOIN','DIGITAL_ASSET','FIAT')),
    display_name varchar(100) NOT NULL CHECK (length(btrim(display_name)) > 0)
);
INSERT INTO currency_definitions(currency_code,currency_kind,display_name) VALUES
('USDT','STABLECOIN','Tether'),('USDC','STABLECOIN','USD Coin'),('USD','FIAT','US Dollar'),
('BTC','DIGITAL_ASSET','Bitcoin'),('ETH','DIGITAL_ASSET','Ether'),('CNY','FIAT','Chinese Yuan');
COMMENT ON TABLE currency_definitions IS '可扩展币种身份目录；不是资产白名单、汇率表或结算/交易权限。';
COMMENT ON COLUMN currency_definitions.currency_code IS '稳定原币标识，不以Java枚举封闭首批币种。';
COMMENT ON COLUMN currency_definitions.currency_kind IS '币种分类，不能作为稳定币固定等价的依据。';
COMMENT ON COLUMN currency_definitions.display_name IS '币种标准名称；能力及界面本地化由实际实现决定。';

-- 不更新历史金额、时间、source或推断币种；原记录仅增加明确的未知状态。
ALTER TABLE equity_curve_snapshots
    ADD COLUMN valuation_currency varchar(32),
    ADD COLUMN valuation_time timestamptz,
    ADD COLUMN valuation_source varchar(128),
    ADD COLUMN valuation_method varchar(128),
    ADD COLUMN valuation_status varchar(16) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN valuation_details jsonb,
    ADD CONSTRAINT ck_paper_equity_valuation_status CHECK (valuation_status IN ('UNKNOWN','PARTIAL','VERIFIED')),
    ADD CONSTRAINT ck_paper_equity_valuation_metadata CHECK (
        (valuation_status='UNKNOWN' AND valuation_currency IS NULL AND valuation_time IS NULL
            AND valuation_source IS NULL AND valuation_method IS NULL AND valuation_details IS NULL)
        OR (valuation_status IN ('PARTIAL','VERIFIED') AND valuation_currency IS NOT NULL
            AND valuation_currency ~ '^[A-Z0-9]{2,32}$' AND valuation_time IS NOT NULL
            AND valuation_time=snapshot_time AND valuation_source IS NOT NULL
            AND length(btrim(valuation_source))>0 AND valuation_method IS NOT NULL
            AND length(btrim(valuation_method))>0 AND valuation_details IS NOT NULL
            AND jsonb_typeof(valuation_details)='object' AND valuation_details<>'{}'::jsonb
            AND octet_length(valuation_details::text)<=262144));
COMMENT ON COLUMN equity_curve_snapshots.valuation_currency IS '权益、现金、持仓市值及金额PnL的统一计价币；历史来源未明时为空，不根据symbol回填。';
COMMENT ON COLUMN equity_curve_snapshots.valuation_time IS '完整估值事实时点，与snapshot_time一致；各价格事件及可见时间留在明细。';
COMMENT ON COLUMN equity_curve_snapshots.valuation_source IS '可追溯的计算链来源；历史SYSTEM/MANUAL等标签不能替代来源证明。';
COMMENT ON COLUMN equity_curve_snapshots.valuation_method IS '计算方法及版本；不得用新结果覆盖旧快照。';
COMMENT ON COLUMN equity_curve_snapshots.valuation_status IS 'UNKNOWN旧来源未核实；PARTIAL依据不完整不可汇总；VERIFIED为完整有源估值。';
COMMENT ON COLUMN equity_curve_snapshots.valuation_details IS '原币、账户、资金/持仓、价格身份/来源/时间、折算和精度依据；比例不带金额单位。';
COMMENT ON COLUMN account_equity_measurements.quote_currency IS '兼容原字段名：实际含义为权益计价币valuationCurrency，不是交易对quoteCurrency；V1只支持USDT。';
