-- GateW-3: extend the existing instrument catalog as the single current venue-rule fact source.
-- Historical rows intentionally keep the new fields NULL until an explicit public-only refresh succeeds.

ALTER TABLE instrument_catalog
    ALTER COLUMN tick_size TYPE NUMERIC(38, 18),
    ALTER COLUMN step_size TYPE NUMERIC(38, 18),
    ALTER COLUMN min_quantity TYPE NUMERIC(38, 18),
    ADD COLUMN max_limit_quantity NUMERIC(38, 18),
    ADD COLUMN max_market_size NUMERIC(38, 18),
    ADD COLUMN max_market_size_unit VARCHAR(16),
    ADD COLUMN max_limit_notional_usd NUMERIC(38, 18),
    ADD COLUMN max_market_notional_usd NUMERIC(38, 18),
    ADD COLUMN source_schema_version VARCHAR(64),
    ADD COLUMN observed_at TIMESTAMPTZ,
    ADD COLUMN next_rule_effective_at TIMESTAMPTZ,
    ADD COLUMN rule_checksum VARCHAR(64);

ALTER TABLE instrument_catalog
    ADD CONSTRAINT chk_instrument_catalog_tick_size_positive
        CHECK (tick_size IS NULL OR tick_size > 0),
    ADD CONSTRAINT chk_instrument_catalog_step_size_positive
        CHECK (step_size IS NULL OR step_size > 0),
    ADD CONSTRAINT chk_instrument_catalog_min_quantity_positive
        CHECK (min_quantity IS NULL OR min_quantity > 0),
    ADD CONSTRAINT chk_instrument_catalog_max_limit_quantity_positive
        CHECK (max_limit_quantity IS NULL OR max_limit_quantity > 0),
    ADD CONSTRAINT chk_instrument_catalog_max_market_size_unit
        CHECK (
            (max_market_size IS NULL AND max_market_size_unit IS NULL)
            OR (
                max_market_size IS NOT NULL
                AND max_market_size_unit IS NOT NULL
                AND max_market_size > 0
                AND max_market_size_unit = 'USDT'
            )
        ),
    ADD CONSTRAINT chk_instrument_catalog_max_limit_notional_positive
        CHECK (max_limit_notional_usd IS NULL OR max_limit_notional_usd > 0),
    ADD CONSTRAINT chk_instrument_catalog_max_market_notional_positive
        CHECK (max_market_notional_usd IS NULL OR max_market_notional_usd > 0),
    ADD CONSTRAINT chk_instrument_catalog_source_schema_version
        CHECK (source_schema_version IS NULL OR BTRIM(source_schema_version) <> ''),
    ADD CONSTRAINT chk_instrument_catalog_rule_checksum
        CHECK (rule_checksum IS NULL OR rule_checksum ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT chk_instrument_catalog_observed_before_synced
        CHECK (observed_at IS NULL OR observed_at <= synced_at),
    ADD CONSTRAINT chk_instrument_catalog_next_rule_after_observed
        CHECK (
            next_rule_effective_at IS NULL
            OR (observed_at IS NOT NULL AND next_rule_effective_at > observed_at)
        );

COMMENT ON COLUMN instrument_catalog.tick_size IS '交易所公开 instrument 事实中的价格步长；允许空，非空时必须大于零。';
COMMENT ON COLUMN instrument_catalog.step_size IS '交易所公开 instrument 事实中的数量步长；允许空，非空时必须大于零。';
COMMENT ON COLUMN instrument_catalog.min_quantity IS '交易所公开 instrument 事实中的最小下单数量；允许空，非空时必须大于零，不表示最小名义金额。';
COMMENT ON COLUMN instrument_catalog.max_limit_quantity IS '交易所公开 instrument 事实中的单笔限价单最大数量；OKX Spot 单位为 base currency。';
COMMENT ON COLUMN instrument_catalog.max_market_size IS '交易所公开 instrument 事实中的单笔市价单最大数量；必须与 max_market_size_unit 同时存在。';
COMMENT ON COLUMN instrument_catalog.max_market_size_unit IS 'max_market_size 的单位；GateW-3 OKX Spot 仅允许 USDT。';
COMMENT ON COLUMN instrument_catalog.max_limit_notional_usd IS '交易所公开 instrument 事实中的单笔限价单最大 USD amount；不表示 NQ 内部风险上限。';
COMMENT ON COLUMN instrument_catalog.max_market_notional_usd IS '交易所公开 instrument 事实中的单笔市价单最大 USD amount；不表示 NQ 内部风险上限。';
COMMENT ON COLUMN instrument_catalog.source_schema_version IS 'NQ venue-rule parser/schema contract 版本；不是 OKX 官方 API 版本。';
COMMENT ON COLUMN instrument_catalog.observed_at IS '完整公开响应成功获取并解析校验后的本地观察时间；不得使用 synced_at 或请求时间替代。';
COMMENT ON COLUMN instrument_catalog.next_rule_effective_at IS '已解析的相关 upcoming change 最早生效时间；允许空，非空时必须晚于 observed_at。';
COMMENT ON COLUMN instrument_catalog.rule_checksum IS '按固定字段顺序和 decimal 规范化计算的 lowercase SHA-256；不包含写库时间、请求标识或数据库主键。';

COMMENT ON CONSTRAINT chk_instrument_catalog_tick_size_positive ON instrument_catalog IS '价格步长只允许空或正数，禁止以零伪造可用事实。';
COMMENT ON CONSTRAINT chk_instrument_catalog_step_size_positive ON instrument_catalog IS '数量步长只允许空或正数，禁止以零伪造可用事实。';
COMMENT ON CONSTRAINT chk_instrument_catalog_min_quantity_positive ON instrument_catalog IS '最小下单数量只允许空或正数。';
COMMENT ON CONSTRAINT chk_instrument_catalog_max_limit_quantity_positive ON instrument_catalog IS '限价单最大数量只允许空或正数。';
COMMENT ON CONSTRAINT chk_instrument_catalog_max_market_size_unit ON instrument_catalog IS '市价单最大数量与单位必须同时为空，或为正数且单位固定为 USDT。';
COMMENT ON CONSTRAINT chk_instrument_catalog_max_limit_notional_positive ON instrument_catalog IS '限价单最大 USD amount 只允许空或正数。';
COMMENT ON CONSTRAINT chk_instrument_catalog_max_market_notional_positive ON instrument_catalog IS '市价单最大 USD amount 只允许空或正数。';
COMMENT ON CONSTRAINT chk_instrument_catalog_source_schema_version ON instrument_catalog IS 'NQ source schema version 只允许空或非空白值。';
COMMENT ON CONSTRAINT chk_instrument_catalog_rule_checksum ON instrument_catalog IS 'checksum 只允许空或 64 位 lowercase hexadecimal SHA-256。';
COMMENT ON CONSTRAINT chk_instrument_catalog_observed_before_synced ON instrument_catalog IS '事实观察时间不得晚于数据库同步写入时间。';
COMMENT ON CONSTRAINT chk_instrument_catalog_next_rule_after_observed ON instrument_catalog IS '下一规则生效时间必须晚于事实观察时间。';
