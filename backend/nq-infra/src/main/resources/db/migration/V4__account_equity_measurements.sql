CREATE TABLE account_equity_measurements (
    sample_id uuid PRIMARY KEY,
    exchange_account_id bigint NOT NULL REFERENCES exchange_accounts(exchange_account_id),
    quote_currency varchar(32) NOT NULL CHECK (quote_currency = 'USDT'),
    sampled_at timestamptz NOT NULL,
    sampling_bucket bigint NOT NULL,
    equity numeric(78,16),
    cumulative_net_flow numeric(78,16),
    status varchar(32) NOT NULL CHECK (status IN ('AVAILABLE','NO_BALANCE','VALUATION_INCOMPLETE','FLOW_INCOMPLETE')),
    source_json jsonb NOT NULL CHECK (jsonb_typeof(source_json) = 'object'),
    CONSTRAINT ck_account_equity_available CHECK (status <> 'AVAILABLE' OR (equity IS NOT NULL AND cumulative_net_flow IS NOT NULL)),
    CONSTRAINT ck_account_equity_bucket CHECK (sampling_bucket = floor(extract(epoch FROM sampled_at) / 900)::bigint),
    CONSTRAINT uq_account_equity_sampling_bucket UNIQUE (exchange_account_id, quote_currency, sampling_bucket)
);
CREATE INDEX idx_account_equity_range ON account_equity_measurements(exchange_account_id, quote_currency, sampled_at DESC);
CREATE INDEX idx_account_equity_retention ON account_equity_measurements(sampled_at);
COMMENT ON TABLE account_equity_measurements IS 'NQ管理范围的派生权益测量，不拥有余额、持仓或账务事实，不回填无来源的历史。';
COMMENT ON COLUMN account_equity_measurements.sampled_at IS '实际一致性读取时间，不能改成历史行情时间。';
COMMENT ON COLUMN account_equity_measurements.sampling_bucket IS '十五分钟UTC桶只用于跨进程采样去重；实际采样覆盖以sampled_at为准。';
COMMENT ON COLUMN account_equity_measurements.equity IS '同一账户投影数量按可见已收盘SPOT价格估值，不重复计入持仓，估值不全为空。';
COMMENT ON COLUMN account_equity_measurements.cumulative_net_flow IS 'SIM已知正式注资现金分录累计额，排除对手分录；LIVE或来源不全为空。';
COMMENT ON COLUMN account_equity_measurements.source_json IS '快照ID、余额基础、估值bar身份与实际来源、可见时间及资金流覆盖。';
