-- V13__rc1_marketdata_bars.sql
-- RC1 Phase A: introduce canonical historical bar storage.

CREATE TABLE IF NOT EXISTS marketdata_bars (
    marketdata_bar_id BIGSERIAL PRIMARY KEY,
    exchange_code VARCHAR(32) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    interval VARCHAR(16) NOT NULL,
    open_time TIMESTAMPTZ NOT NULL,
    close_time TIMESTAMPTZ NOT NULL,
    open_price NUMERIC(38, 8) NOT NULL,
    high_price NUMERIC(38, 8) NOT NULL,
    low_price NUMERIC(38, 8) NOT NULL,
    close_price NUMERIC(38, 8) NOT NULL,
    volume NUMERIC(38, 8) NOT NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'IMPORT',
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_marketdata_bars_scope UNIQUE (exchange_code, symbol, interval, open_time)
);

CREATE INDEX IF NOT EXISTS idx_marketdata_bars_symbol_interval_time
    ON marketdata_bars (exchange_code, symbol, interval, open_time DESC);
