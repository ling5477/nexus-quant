-- 费率是指定账户/环境/现货标的在观察时的证据，不能当作历史或已发生成交费用。
SET LOCAL lock_timeout='3s';
SET LOCAL statement_timeout='15s';
CREATE TABLE account_fee_observations (
    observation_id UUID PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    exchange_account_id BIGINT NOT NULL,
    operation_id UUID NOT NULL,
    credential_id BIGINT NOT NULL,
    exchange_code VARCHAR(16) NOT NULL CHECK(exchange_code='OKX'),
    trade_env VARCHAR(8) NOT NULL CHECK(trade_env IN('SIM','LIVE')),
    symbol VARCHAR(41) NOT NULL CHECK(symbol ~ '^[A-Z0-9]{1,20}-[A-Z0-9]{1,20}$'),
    input_fingerprint VARCHAR(64) NOT NULL CHECK(input_fingerprint ~ '^[a-f0-9]{64}$'),
    maker_provider_rate NUMERIC(24,12) NOT NULL CHECK(abs(maker_provider_rate)<=1),
    taker_provider_rate NUMERIC(24,12) NOT NULL CHECK(abs(taker_provider_rate)<=1),
    maker_cost_rate NUMERIC(24,12) NOT NULL,
    taker_cost_rate NUMERIC(24,12) NOT NULL,
    fee_tier VARCHAR(32) NOT NULL CHECK(fee_tier ~ '^[A-Za-z0-9_-]{1,32}$'),
    provider_at TIMESTAMPTZ NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    source VARCHAR(32) NOT NULL CHECK(source='OKX_PRIVATE_SPOT_FEE_V5'),
    rate_convention VARCHAR(32) NOT NULL CHECK(rate_convention='OKX_NEGATIVE_COST_V1'),
    source_sha256 VARCHAR(64) NOT NULL CHECK(source_sha256 ~ '^[a-f0-9]{64}$'),
    UNIQUE(owner_user_id,operation_id),
    FOREIGN KEY(owner_user_id,exchange_account_id) REFERENCES exchange_accounts(owner_user_id,exchange_account_id) ON DELETE RESTRICT,
    FOREIGN KEY(exchange_account_id,credential_id) REFERENCES exchange_account_credentials(exchange_account_id,credential_id) ON DELETE RESTRICT,
    CHECK(maker_cost_rate=-maker_provider_rate AND taker_cost_rate=-taker_provider_rate),
    CHECK(provider_at<=observed_at AND provider_at>=observed_at-INTERVAL '1 hour'),
    CHECK(expires_at=observed_at+INTERVAL '5 minutes')
);
CREATE INDEX idx_account_fee_history ON account_fee_observations(owner_user_id,exchange_account_id,symbol,observed_at DESC,observation_id DESC);
COMMENT ON TABLE account_fee_observations IS '不可变当前实际适用费率观察及稳定同步回执；不拥有成交费用或经济账务';
COMMENT ON COLUMN account_fee_observations.operation_id IS 'owner范围内稳定同步身份；已有回执重放不重新读取供应方';
COMMENT ON COLUMN account_fee_observations.credential_id IS '仅凭据版本引用，不含材料、认证头或原始响应';
COMMENT ON COLUMN account_fee_observations.maker_provider_rate IS '保留OKX原始符号：负值收费，正值返佣';
COMMENT ON COLUMN account_fee_observations.maker_cost_rate IS 'NQ费用符号：正值成本，负值返佣；不得取绝对值';
COMMENT ON COLUMN account_fee_observations.source_sha256 IS '规范化来源身份及精确值的SHA256；不能用来证明任意历史费率';
CREATE FUNCTION reject_account_fee_observation_change() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'account fee observations are immutable' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER account_fee_observation_immutable BEFORE UPDATE OR DELETE ON account_fee_observations
    FOR EACH ROW EXECUTE FUNCTION reject_account_fee_observation_change();
