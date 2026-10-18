-- 只扩展非经济性的当前费用观察；旧OKX数据、V1摘要、不可变触发器和owner/凭据外键保留。
SET LOCAL lock_timeout='3s';
SET LOCAL statement_timeout='15s';
ALTER TABLE account_fee_observations
    ADD COLUMN provider_time_basis VARCHAR(32) NOT NULL DEFAULT 'PROVIDER_FEE_TIMESTAMP',
    ADD COLUMN commission_terms JSONB,
    ALTER COLUMN maker_provider_rate DROP NOT NULL,
    ALTER COLUMN taker_provider_rate DROP NOT NULL,
    ALTER COLUMN maker_cost_rate DROP NOT NULL,
    ALTER COLUMN taker_cost_rate DROP NOT NULL,
    ALTER COLUMN fee_tier DROP NOT NULL,
    ALTER COLUMN provider_at DROP NOT NULL,
    DROP CONSTRAINT account_fee_observations_exchange_code_check,
    DROP CONSTRAINT account_fee_observations_source_check,
    DROP CONSTRAINT account_fee_observations_rate_convention_check,
    DROP CONSTRAINT account_fee_observations_check,
    DROP CONSTRAINT account_fee_observations_check1;

-- 数据库检查完整的三类/四方向分量；字符串十进制禁止浮点、超精度、未知schema和缺失值。
CREATE FUNCTION valid_spot_commission_terms(terms JSONB) RETURNS BOOLEAN
LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS $$
DECLARE
    category TEXT;
    component TEXT;
    group_value JSONB;
    offer JSONB;
    rate_text TEXT;
BEGIN
    IF jsonb_typeof(terms) IS DISTINCT FROM 'object' OR pg_column_size(terms)>4096 THEN RETURN FALSE; END IF;
    IF (SELECT count(*) FROM jsonb_object_keys(terms))<>6
        OR NOT terms ?& ARRAY['nativeSymbol','standard','tax','special','discount','responseSha256']
        OR jsonb_typeof(terms->'nativeSymbol') IS DISTINCT FROM 'string'
        OR (terms->>'nativeSymbol') !~ '^[A-Z0-9]{2,40}$'
        OR jsonb_typeof(terms->'responseSha256') IS DISTINCT FROM 'string'
        OR (terms->>'responseSha256') !~ '^[a-f0-9]{64}$' THEN RETURN FALSE; END IF;
    FOREACH category IN ARRAY ARRAY['standard','tax','special'] LOOP
        group_value:=terms->category;
        IF jsonb_typeof(group_value) IS DISTINCT FROM 'object' THEN RETURN FALSE; END IF;
        IF (SELECT count(*) FROM jsonb_object_keys(group_value))<>4
            OR NOT group_value ?& ARRAY['maker','taker','buyer','seller'] THEN RETURN FALSE; END IF;
        FOREACH component IN ARRAY ARRAY['maker','taker','buyer','seller'] LOOP
            IF jsonb_typeof(group_value->component) IS DISTINCT FROM 'string' THEN RETURN FALSE; END IF;
            rate_text:=group_value->>component;
            IF rate_text !~ '^-?[0-9]{1,3}(\.[0-9]{1,12})?$' THEN RETURN FALSE; END IF;
            IF abs(rate_text::NUMERIC)>1 THEN RETURN FALSE; END IF;
        END LOOP;
    END LOOP;
    offer:=terms->'discount';
    IF jsonb_typeof(offer) IS DISTINCT FROM 'object' THEN RETURN FALSE; END IF;
    IF (SELECT count(*) FROM jsonb_object_keys(offer))<>4
        OR NOT offer ?& ARRAY['enabledForAccount','enabledForSymbol','asset','multiplier']
        OR jsonb_typeof(offer->'enabledForAccount') IS DISTINCT FROM 'boolean'
        OR jsonb_typeof(offer->'enabledForSymbol') IS DISTINCT FROM 'boolean'
        OR jsonb_typeof(offer->'asset') IS DISTINCT FROM 'string'
        OR (offer->>'asset') !~ '^[A-Z0-9]{1,20}$'
        OR jsonb_typeof(offer->'multiplier') IS DISTINCT FROM 'string' THEN RETURN FALSE; END IF;
    rate_text:=offer->>'multiplier';
    IF rate_text !~ '^[0-9]{1,3}(\.[0-9]{1,12})?$' THEN RETURN FALSE; END IF;
    RETURN rate_text::NUMERIC BETWEEN 0 AND 1;
END;
$$;

-- commission没有全局maker/taker成本或VIP等级；缺失必须为NULL，不能用0或客户端时钟伪装。
ALTER TABLE account_fee_observations ADD CONSTRAINT account_fee_source_contract CHECK ((
    (exchange_code='OKX' AND source='OKX_PRIVATE_SPOT_FEE_V5' AND rate_convention='OKX_NEGATIVE_COST_V1'
        AND provider_time_basis='PROVIDER_FEE_TIMESTAMP' AND commission_terms IS NULL
        AND maker_provider_rate IS NOT NULL AND taker_provider_rate IS NOT NULL
        AND maker_cost_rate IS NOT NULL AND taker_cost_rate IS NOT NULL
        AND maker_cost_rate=-maker_provider_rate AND taker_cost_rate=-taker_provider_rate
        AND fee_tier IS NOT NULL AND provider_at IS NOT NULL
        AND provider_at<=observed_at AND provider_at>=observed_at-INTERVAL '1 hour')
    OR
    (exchange_code='BINANCE' AND source='BINANCE_PRIVATE_SPOT_COMMISSION' AND rate_convention='BINANCE_POSITIVE_COST_V1'
        AND maker_provider_rate IS NULL AND taker_provider_rate IS NULL AND maker_cost_rate IS NULL AND taker_cost_rate IS NULL
        AND fee_tier IS NULL AND commission_terms IS NOT NULL AND valid_spot_commission_terms(commission_terms)
        AND commission_terms->>'nativeSymbol'=replace(symbol,'-','')
        AND ((provider_time_basis='NOT_PROVIDED' AND provider_at IS NULL)
            OR (provider_time_basis='HTTP_RESPONSE_DATE' AND provider_at IS NOT NULL
                AND provider_at<=observed_at+INTERVAL '30 seconds' AND provider_at>=observed_at-INTERVAL '1 hour')))
) IS TRUE);
COMMENT ON COLUMN account_fee_observations.provider_time_basis IS '供应方费率时点/HTTP响应Date/未提供；不把响应时间称为费率变更时间';
COMMENT ON COLUMN account_fee_observations.commission_terms IS '完整原生现货commission条件：standard/tax/special各四分量、优惠条件和响应摘要；不证明实际手续费资产或历史费率';
COMMENT ON FUNCTION valid_spot_commission_terms(JSONB) IS '有界完整schema及精确十进制校验；未知分量、缺失、超精度和数值JSON均失败关闭';
