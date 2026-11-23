-- 四价发生时点由明确来源提供，独立保留记录；不改写原始行情及任何冻结研究输入。
CREATE TABLE marketdata_price_event_sources (
    source_id uuid PRIMARY KEY,
    marketdata_bar_id bigint NOT NULL REFERENCES marketdata_bars(marketdata_bar_id) ON DELETE RESTRICT,
    bar_identity_sha256 varchar(64) NOT NULL,
    source_name varchar(128) NOT NULL,
    document_sha256 varchar(64) NOT NULL,
    document_json jsonb NOT NULL,
    bar_snapshot_json jsonb NOT NULL,
    open_price_at timestamptz NOT NULL,
    high_price_at timestamptz NOT NULL,
    low_price_at timestamptz NOT NULL,
    close_price_at timestamptz NOT NULL,
    created_by_user_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_marketdata_price_event_source_version UNIQUE(marketdata_bar_id,bar_identity_sha256),
    CONSTRAINT ck_marketdata_price_event_source_hashes CHECK(bar_identity_sha256 ~ '^[a-f0-9]{64}$' AND document_sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_marketdata_price_event_source_name CHECK(length(btrim(source_name))>0 AND position('/' in source_name)=0 AND position(chr(92) in source_name)=0),
    CONSTRAINT ck_marketdata_price_event_source_shapes CHECK(jsonb_typeof(document_json)='object' AND jsonb_typeof(bar_snapshot_json)='object'
        AND octet_length(document_json::text)<=32768 AND octet_length(bar_snapshot_json::text)<=32768
        AND NOT jsonb_path_exists(document_json,'$.* ? (@.type() != "string")')
        AND NOT jsonb_path_exists(bar_snapshot_json,'$.* ? (@.type() != "string")')
        AND document_json ?& ARRAY['exchangeCode','marketType','symbol','interval','openTime','closeTime','openPrice','highPrice','lowPrice','closePrice','openPriceAt','highPriceAt','lowPriceAt','closePriceAt']
        AND document_json - ARRAY['exchangeCode','marketType','symbol','interval','openTime','closeTime','openPrice','highPrice','lowPrice','closePrice','openPriceAt','highPriceAt','lowPriceAt','closePriceAt']='{}'::jsonb
        AND bar_snapshot_json ?& ARRAY['barId','source','qualityStatus','exchangeCode','marketType','symbol','interval','openTime','closeTime','openPrice','highPrice','lowPrice','closePrice']
        AND bar_snapshot_json->>'qualityStatus'='OK' AND marketdata_bar_id>0 AND created_by_user_id>0),
    CONSTRAINT ck_marketdata_price_event_source_scope CHECK((bar_snapshot_json->>'barId')::bigint=marketdata_bar_id
        AND document_json->>'exchangeCode'=bar_snapshot_json->>'exchangeCode'
        AND document_json->>'marketType'=bar_snapshot_json->>'marketType'
        AND document_json->>'symbol'=bar_snapshot_json->>'symbol'
        AND document_json->>'interval'=bar_snapshot_json->>'interval'
        AND document_json->>'openTime'=bar_snapshot_json->>'openTime'
        AND document_json->>'closeTime'=bar_snapshot_json->>'closeTime'
        AND document_json->>'openPrice'=bar_snapshot_json->>'openPrice'
        AND document_json->>'highPrice'=bar_snapshot_json->>'highPrice'
        AND document_json->>'lowPrice'=bar_snapshot_json->>'lowPrice'
        AND document_json->>'closePrice'=bar_snapshot_json->>'closePrice'),
    CONSTRAINT ck_marketdata_price_event_source_times CHECK(open_price_at=(document_json->>'openPriceAt')::timestamptz
        AND high_price_at=(document_json->>'highPriceAt')::timestamptz AND low_price_at=(document_json->>'lowPriceAt')::timestamptz
        AND close_price_at=(document_json->>'closePriceAt')::timestamptz AND open_price_at<=(high_price_at)
        AND open_price_at<=low_price_at AND high_price_at<=close_price_at AND low_price_at<=close_price_at
        AND open_price_at>=(bar_snapshot_json->>'openTime')::timestamptz AND close_price_at<=(bar_snapshot_json->>'closeTime')::timestamptz
        AND close_price_at<=created_at)
);

COMMENT ON TABLE marketdata_price_event_sources IS '同版K线四价发生时点的不可变供应记录，不拥有行情价格、交易或研究事实';
COMMENT ON COLUMN marketdata_price_event_sources.source_id IS '稳定来源提交身份，用于重放及未知保存结果读取';
COMMENT ON COLUMN marketdata_price_event_sources.source_name IS '来源记录名称，不能存储本机完整路径；不宣称交易所认证';
COMMENT ON COLUMN marketdata_price_event_sources.marketdata_bar_id IS '当前行情事实引用，原始行情由既有owner维护';
COMMENT ON COLUMN marketdata_price_event_sources.bar_identity_sha256 IS '关联范围、四价、来源及质量的版本摘要，当前版本不符不得显示旧时点';
COMMENT ON COLUMN marketdata_price_event_sources.document_sha256 IS '规范化供应记录摘要，不宣称是原始文件字节摘要或交易所认证';
COMMENT ON COLUMN marketdata_price_event_sources.document_json IS '范围、四价与明确提供的四个时点；没有时点的OHLCV不得推断';
COMMENT ON COLUMN marketdata_price_event_sources.bar_snapshot_json IS '写入时锁定的行情版本，保持来源匹配与恢复可追溯';
COMMENT ON COLUMN marketdata_price_event_sources.created_by_user_id IS '服务端认证操作者，客户端不能指定作者';
COMMENT ON COLUMN marketdata_price_event_sources.created_at IS '来源记录保存时间，区别于价格发生时间及行情可见时间';
COMMENT ON COLUMN marketdata_price_event_sources.open_price_at IS '明确供应的开盘价发生时点，不默认等于K线区间起点';
COMMENT ON COLUMN marketdata_price_event_sources.high_price_at IS '明确供应的最高价发生时点，不从OHLCV推算';
COMMENT ON COLUMN marketdata_price_event_sources.low_price_at IS '明确供应的最低价发生时点，不从OHLCV推算';
COMMENT ON COLUMN marketdata_price_event_sources.close_price_at IS '明确供应的收盘价发生时点，不默认等于区间终点或可见时间';

-- 保留第一次已确认的来源事实；冲突走查询和解释，不用更新或删除改写历史。
CREATE FUNCTION preserve_marketdata_price_event_source() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'marketdata_price_event_source_immutable' USING ERRCODE='55000';
END;
$$;
CREATE TRIGGER trg_marketdata_price_event_source_immutable BEFORE UPDATE OR DELETE ON marketdata_price_event_sources
    FOR EACH ROW EXECUTE FUNCTION preserve_marketdata_price_event_source();
