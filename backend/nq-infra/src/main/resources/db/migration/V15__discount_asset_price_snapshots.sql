-- 仅增加优惠币历史行情及不可变换算来源；不改变交易标的、资金余额或LIVE权限。
-- 已执行V1—V13保持原样，V14的预算账务身份由独立资金合同拥有。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE marketdata_datasets DROP CONSTRAINT chk_marketdata_datasets_scope;
ALTER TABLE marketdata_datasets ADD CONSTRAINT chk_marketdata_datasets_scope CHECK (
    exchange_code IN ('OKX','BINANCE') AND market_type='SPOT'
    AND symbol IN ('BTC-USDT','ETH-USDT','SOL-USDT','BNB-USDT')
    AND "interval" IN ('1m','5m','15m','1h','4h','1d'));
ALTER TABLE marketdata_ingestion_jobs DROP CONSTRAINT ck_marketdata_ingestion_jobs_symbol;
ALTER TABLE marketdata_ingestion_jobs ADD CONSTRAINT ck_marketdata_ingestion_jobs_symbol
    CHECK (symbol IN ('BTC-USDT','ETH-USDT','SOL-USDT','BNB-USDT'));
COMMENT ON CONSTRAINT chk_marketdata_datasets_scope ON marketdata_datasets IS
    '正式现货历史行情范围；BNB仅为优惠费用换算提供数据，不扩大下单或真实资金权限';
COMMENT ON CONSTRAINT ck_marketdata_ingestion_jobs_symbol ON marketdata_ingestion_jobs IS
    '历史行情采集币对范围，独立于交易准入及账户交易权限';

-- 文本SHA不能替代结构和数值约束；数据库同样拒绝NaN、未来可见、缺口及错误市场的来源。
CREATE FUNCTION discount_asset_price_series_valid(raw_source text) RETURNS boolean
LANGUAGE plpgsql IMMUTABLE STRICT AS $$
DECLARE
    document jsonb; point jsonb; field_name text; point_open timestamptz;
    observed timestamptz; visible timestamptz; previous_open timestamptz;
    first_observed timestamptz; last_observed timestamptz;
    opening numeric; highest numeric; lowest numeric; closing numeric; value numeric;
BEGIN
    IF octet_length(raw_source)>33554432 THEN RETURN false; END IF;
    document:=raw_source::jsonb;
    IF jsonb_typeof(document->'points')<>'array' OR jsonb_array_length(document->'points') NOT BETWEEN 1 AND 20000 THEN RETURN false; END IF;
    FOREACH field_name IN ARRAY ARRAY['from','to','frozenAt'] LOOP
        IF jsonb_typeof(document->field_name)<>'string'
            OR NOT (document->>field_name ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}([.][0-9]{1,6})?Z$') THEN RETURN false; END IF;
    END LOOP;
    FOR point IN SELECT jsonb_array_elements(document->'points') LOOP
        FOREACH field_name IN ARRAY ARRAY['barId','barIdentitySha256','barSource','exchangeCode','marketType','symbol','interval',
            'openTime','observedAt','visibleAt','openPrice','highPrice','lowPrice','closePrice','volume','qualityStatus','rawPayloadJson','rawPayloadSha256'] LOOP
            IF jsonb_typeof(point->field_name) IS DISTINCT FROM 'string' THEN RETURN false; END IF;
        END LOOP;
        IF point->>'exchangeCode' IS DISTINCT FROM document->>'exchangeCode' OR point->>'marketType'<>'SPOT'
            OR point->>'symbol' IS DISTINCT FROM (document->>'assetCurrency')||'-'||(document->>'quoteCurrency')
            OR point->>'interval'<>'1m' OR point->>'qualityStatus'<>'OK'
            OR (point->>'barId')::bigint<=0 OR NOT (point->>'barIdentitySha256' ~ '^[a-f0-9]{64}$')
            OR length(btrim(point->>'barSource')) NOT BETWEEN 1 AND 128
            OR length(point->>'rawPayloadJson') NOT BETWEEN 1 AND 16384
            OR point->>'rawPayloadSha256' IS DISTINCT FROM encode(public.digest(convert_to(point->>'rawPayloadJson','UTF8'),'sha256'),'hex') THEN RETURN false; END IF;
        FOREACH field_name IN ARRAY ARRAY['openTime','observedAt','visibleAt'] LOOP
            IF NOT (point->>field_name ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}([.][0-9]{1,6})?Z$') THEN RETURN false; END IF;
        END LOOP;
        point_open:=(point->>'openTime')::timestamptz;
        observed:=(point->>'observedAt')::timestamptz; visible:=(point->>'visibleAt')::timestamptz;
        IF observed<=point_open OR observed>point_open+interval '1 minute' OR visible<observed
            OR visible>(document->>'frozenAt')::timestamptz OR observed>(document->>'to')::timestamptz
            OR point_open<(document->'dataset'->>'startTime')::timestamptz
            OR (previous_open IS NOT NULL AND point_open<>previous_open+interval '1 minute') THEN RETURN false; END IF;
        FOREACH field_name IN ARRAY ARRAY['openPrice','highPrice','lowPrice','closePrice','volume'] LOOP
            IF length(point->>field_name)>64 OR NOT (point->>field_name ~ '^-?[0-9]+([.][0-9]+)?$') THEN RETURN false; END IF;
            value:=(point->>field_name)::numeric;
            IF abs(value)>=1e38 OR value<>round(value,18) OR (field_name='volume' AND value<0)
                OR (field_name<>'volume' AND value<=0) THEN RETURN false; END IF;
        END LOOP;
        opening:=(point->>'openPrice')::numeric; highest:=(point->>'highPrice')::numeric;
        lowest:=(point->>'lowPrice')::numeric; closing:=(point->>'closePrice')::numeric;
        IF highest<greatest(opening,closing) OR lowest>least(opening,closing) OR highest<lowest THEN RETURN false; END IF;
        IF point->'quoteVolume' IS DISTINCT FROM 'null'::jsonb THEN
            IF jsonb_typeof(point->'quoteVolume') IS DISTINCT FROM 'string' OR length(point->>'quoteVolume')>64
                OR NOT (point->>'quoteVolume' ~ '^[0-9]+([.][0-9]+)?$') THEN RETURN false; END IF;
            value:=(point->>'quoteVolume')::numeric;
            IF value<0 OR value>=1e38 OR value<>round(value,18) THEN RETURN false; END IF;
        END IF;
        IF point->'tradeCount' IS DISTINCT FROM 'null'::jsonb AND (jsonb_typeof(point->'tradeCount') IS DISTINCT FROM 'string'
            OR (point->>'tradeCount')::bigint<0) THEN RETURN false; END IF;
        IF first_observed IS NULL THEN first_observed:=observed; END IF;
        last_observed:=observed; previous_open:=point_open;
    END LOOP;
    RETURN first_observed<=(document->>'from')::timestamptz-interval '4 minutes'
        AND last_observed>=(document->>'to')::timestamptz-interval '1 minute';
EXCEPTION WHEN OTHERS THEN
    RETURN false;
END;
$$;
COMMENT ON FUNCTION discount_asset_price_series_valid(text) IS
    '有界核验原行情版本、UTC微秒时间、质量、OHLCV、原payload摘要及连续覆盖；可重算SHA不代替来源事实';

CREATE TABLE discount_asset_price_snapshots (
    snapshot_id uuid PRIMARY KEY,
    dataset_id uuid NOT NULL REFERENCES marketdata_datasets(dataset_id) ON DELETE RESTRICT,
    owner_user_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    exchange_code varchar(32) NOT NULL,
    asset_currency varchar(20) NOT NULL,
    quote_currency varchar(20) NOT NULL,
    coverage_from timestamptz NOT NULL,
    coverage_to timestamptz NOT NULL,
    frozen_at timestamptz NOT NULL,
    point_count integer NOT NULL,
    source_sha256 varchar(64) NOT NULL,
    source_json text NOT NULL,
    CONSTRAINT chk_discount_asset_price_scope CHECK (owner_user_id>0 AND exchange_code IN ('OKX','BINANCE')
        AND asset_currency='BNB' AND quote_currency='USDT'),
    CONSTRAINT chk_discount_asset_price_range CHECK (coverage_to>coverage_from AND frozen_at>=coverage_to
        AND coverage_to-coverage_from+interval '5 minutes'<interval '20000 minutes'),
    CONSTRAINT chk_discount_asset_price_size CHECK (point_count BETWEEN 1 AND 20000
        AND octet_length(source_json) BETWEEN 1 AND 33554432),
    CONSTRAINT chk_discount_asset_price_hash CHECK (source_sha256 ~ '^[a-f0-9]{64}$'
        AND source_sha256=encode(public.digest(convert_to(source_json,'UTF8'),'sha256'),'hex')),
    CONSTRAINT chk_discount_asset_price_series CHECK (discount_asset_price_series_valid(source_json) IS TRUE),
    CONSTRAINT chk_discount_asset_price_identity CHECK ((
        jsonb_typeof(source_json::jsonb)='object'
        AND source_json::jsonb->>'schemaVersion'='DISCOUNT_ASSET_PRICE_SNAPSHOT_V1'
        AND source_json::jsonb->>'snapshotId'=snapshot_id::text
        AND source_json::jsonb->>'datasetId'=dataset_id::text
        AND source_json::jsonb->>'ownerUserId'=owner_user_id::text
        AND source_json::jsonb->>'exchangeCode'=exchange_code
        AND source_json::jsonb->>'assetCurrency'=asset_currency
        AND source_json::jsonb->>'quoteCurrency'=quote_currency
        AND (source_json::jsonb->>'from')::timestamptz=coverage_from
        AND (source_json::jsonb->>'to')::timestamptz=coverage_to
        AND (source_json::jsonb->>'frozenAt')::timestamptz=frozen_at
        AND jsonb_typeof(source_json::jsonb->'points')='array'
        AND jsonb_array_length(source_json::jsonb->'points')=point_count
        AND source_json::jsonb->'dataset'->>'provider'='db'
        AND source_json::jsonb->'dataset'->>'source'='marketdata_bars'
        AND source_json::jsonb->'dataset'->>'datasetId'=dataset_id::text
        AND source_json::jsonb->'dataset'->>'exchangeCode'=exchange_code
        AND source_json::jsonb->'dataset'->>'marketType'='SPOT'
        AND source_json::jsonb->'dataset'->>'symbol'=asset_currency||'-'||quote_currency
        AND source_json::jsonb->'dataset'->>'interval'='1m'
        AND source_json::jsonb->'dataset'->>'status'='READY'
        AND source_json::jsonb->'dataset'->>'qualityStatus'='OK'
        AND (source_json::jsonb->'dataset'->>'gapCount')::bigint=0
        AND (source_json::jsonb->'dataset'->>'barCount')::bigint>=point_count
        AND (source_json::jsonb->'dataset'->>'startTime')::timestamptz<=coverage_from-interval '5 minutes'
        AND (source_json::jsonb->'dataset'->>'endTime')::timestamptz>=coverage_to
    ) IS TRUE)
);
CREATE INDEX idx_discount_asset_price_owner ON discount_asset_price_snapshots(owner_user_id,snapshot_id);
COMMENT ON TABLE discount_asset_price_snapshots IS
    '从正式数据集冻结的优惠资产换算历史；只保存行情来源元数据，不拥有余额、成交或第二账本';
COMMENT ON COLUMN discount_asset_price_snapshots.snapshot_id IS '不可变快照身份；重启按原身份读取，不换成最新行情';
COMMENT ON COLUMN discount_asset_price_snapshots.dataset_id IS '正式db行情数据集引用，冻结时须为同交易所BNB-USDT现货一分钟READY/OK数据';
COMMENT ON COLUMN discount_asset_price_snapshots.owner_user_id IS '服务端认证解析的用户主键，客户端不能指定来源所有者';
COMMENT ON COLUMN discount_asset_price_snapshots.exchange_code IS '换算行情真实交易所身份，不借用另一交易所价格';
COMMENT ON COLUMN discount_asset_price_snapshots.asset_currency IS '优惠费用支付币种，本合同仅支持BNB历史换算';
COMMENT ON COLUMN discount_asset_price_snapshots.quote_currency IS '预算及费用估值币种，本合同固定USDT';
COMMENT ON COLUMN discount_asset_price_snapshots.coverage_from IS '允许消费的起点，源数据另覆盖此前五分钟';
COMMENT ON COLUMN discount_asset_price_snapshots.coverage_to IS '允许消费的终点，超出冻结窗口时保持不可用';
COMMENT ON COLUMN discount_asset_price_snapshots.frozen_at IS '来源冻结时点，区分行情观察时点和实际可见时间';
COMMENT ON COLUMN discount_asset_price_snapshots.point_count IS '已消费价格点数，上限二万；不允许截断后伪装完整覆盖';
COMMENT ON COLUMN discount_asset_price_snapshots.source_sha256 IS 'source_json原UTF-8文本摘要，可复算但不能替代正式来源准入';
COMMENT ON COLUMN discount_asset_price_snapshots.source_json IS '不可变原文本：完整数据集身份、OHLCV、原payload摘要、收盘观察时点及实际可见时间';

-- 任何修改或删除都会破坏已发布研究和SIM的费用身份，恢复只能读取原快照。
CREATE FUNCTION preserve_discount_asset_price_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'discount_asset_price_snapshot_immutable' USING ERRCODE='55000';
END;
$$;
CREATE TRIGGER trg_discount_asset_price_snapshot_immutable BEFORE UPDATE OR DELETE ON discount_asset_price_snapshots
    FOR EACH ROW EXECUTE FUNCTION preserve_discount_asset_price_snapshot();
COMMENT ON FUNCTION preserve_discount_asset_price_snapshot() IS '禁止改写或删除已冻结换算来源，保留历史消费身份';
