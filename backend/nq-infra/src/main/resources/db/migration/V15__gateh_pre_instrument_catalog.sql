CREATE TABLE IF NOT EXISTS instrument_catalog (
    instrument_id BIGSERIAL PRIMARY KEY,
    exchange_code VARCHAR(32) NOT NULL,
    instrument_type VARCHAR(32) NOT NULL,
    exchange_symbol VARCHAR(64) NOT NULL,
    internal_symbol VARCHAR(64) NOT NULL,
    base_asset VARCHAR(32) NOT NULL,
    quote_asset VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    tick_size NUMERIC(24, 12),
    step_size NUMERIC(24, 12),
    min_quantity NUMERIC(24, 12),
    source VARCHAR(64) NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_instrument_catalog_exchange_symbol UNIQUE (exchange_code, exchange_symbol),
    CONSTRAINT uq_instrument_catalog_exchange_internal_symbol UNIQUE (exchange_code, internal_symbol)
);

CREATE INDEX IF NOT EXISTS idx_instrument_catalog_exchange_status_symbol
    ON instrument_catalog (exchange_code, status, internal_symbol);

COMMENT ON TABLE instrument_catalog IS 'GateH-PRE instrument 主数据目录。统一沉淀交易所原生 symbol 与内部 symbol、精度、资产对等基础信息。';
COMMENT ON COLUMN instrument_catalog.exchange_code IS '交易所编码，例如 OKX / BINANCE。';
COMMENT ON COLUMN instrument_catalog.instrument_type IS '产品类型；当前固定为 SPOT。';
COMMENT ON COLUMN instrument_catalog.exchange_symbol IS '交易所原生 symbol。';
COMMENT ON COLUMN instrument_catalog.internal_symbol IS '系统统一 symbol。';
COMMENT ON COLUMN instrument_catalog.base_asset IS 'base 资产。';
COMMENT ON COLUMN instrument_catalog.quote_asset IS 'quote 资产。';
COMMENT ON COLUMN instrument_catalog.status IS 'instrument 当前状态。';
COMMENT ON COLUMN instrument_catalog.tick_size IS '价格步长。';
COMMENT ON COLUMN instrument_catalog.step_size IS '数量步长。';
COMMENT ON COLUMN instrument_catalog.min_quantity IS '最小下单数量。';
COMMENT ON COLUMN instrument_catalog.source IS '同步来源，例如 OKX_INSTRUMENTS_CACHE / BINANCE_FILTERS_CACHE。';
COMMENT ON COLUMN instrument_catalog.synced_at IS '最近一次同步时间。';
