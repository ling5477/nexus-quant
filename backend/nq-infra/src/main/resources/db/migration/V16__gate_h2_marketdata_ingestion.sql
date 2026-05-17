-- V16__gate_h2_marketdata_ingestion.sql
-- GateH-2: OKX / Binance SPOT historical OHLCV ingestion baseline.

ALTER TABLE marketdata_bars
    ADD COLUMN IF NOT EXISTS market_type VARCHAR(16) NOT NULL DEFAULT 'SPOT',
    ADD COLUMN IF NOT EXISTS quote_volume NUMERIC(38, 8),
    ADD COLUMN IF NOT EXISTS trade_count BIGINT,
    ADD COLUMN IF NOT EXISTS quality_status VARCHAR(32) NOT NULL DEFAULT 'OK',
    ADD COLUMN IF NOT EXISTS raw_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE marketdata_bars
    DROP CONSTRAINT IF EXISTS uq_marketdata_bars_scope;

ALTER TABLE marketdata_bars
    ADD CONSTRAINT uq_marketdata_bars_scope
        UNIQUE (exchange_code, market_type, symbol, "interval", open_time);

CREATE INDEX IF NOT EXISTS idx_marketdata_bars_scope_time_desc
    ON marketdata_bars (exchange_code, market_type, symbol, "interval", open_time DESC);

COMMENT ON COLUMN marketdata_bars.market_type IS '市场类型，GateH-2 仅允许 SPOT；纳入唯一键以固定现货/后续其他市场的隔离语义';
COMMENT ON COLUMN marketdata_bars.quote_volume IS 'K 线成交额，按交易所返回的 quote asset 数量保存，NUMERIC(38,8) 保留交易所原始精度边界';
COMMENT ON COLUMN marketdata_bars.trade_count IS 'K 线成交笔数，来自交易所原始 K 线响应；交易所不返回时允许为空';
COMMENT ON COLUMN marketdata_bars.quality_status IS 'K 线质量状态，允许值：OK、GAP_DETECTED、DUPLICATE_SKIPPED、INVALID_PRICE、INCOMPLETE';
COMMENT ON COLUMN marketdata_bars.raw_payload_json IS '交易所原始 K 线 payload 快照 JSONB，仅保存当前 bar 的原始数组/对象用于审计和排障，不作为业务查询主结构';

CREATE TABLE IF NOT EXISTS marketdata_ingestion_jobs (
    job_id UUID PRIMARY KEY,
    exchange_code VARCHAR(32) NOT NULL,
    market_type VARCHAR(16) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    "interval" VARCHAR(16) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    request_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_marketdata_ingestion_jobs_market_type CHECK (market_type IN ('SPOT')),
    CONSTRAINT ck_marketdata_ingestion_jobs_status CHECK (status IN ('CREATED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'PARTIAL')),
    CONSTRAINT ck_marketdata_ingestion_jobs_exchange CHECK (exchange_code IN ('OKX', 'BINANCE')),
    CONSTRAINT ck_marketdata_ingestion_jobs_interval CHECK ("interval" IN ('1m', '5m', '15m', '1h', '4h', '1d')),
    CONSTRAINT ck_marketdata_ingestion_jobs_symbol CHECK (symbol IN ('BTC-USDT', 'ETH-USDT', 'SOL-USDT')),
    CONSTRAINT ck_marketdata_ingestion_jobs_range CHECK (end_time >= start_time)
);

COMMENT ON TABLE marketdata_ingestion_jobs IS 'GateH-2 行情历史数据接入任务表，记录按交易所、市场类型、交易对、周期和时间范围创建的历史 K 线拉取任务';
COMMENT ON COLUMN marketdata_ingestion_jobs.job_id IS '接入任务 ID，业务主键，使用 UUID 保证 API 与运行记录之间稳定关联';
COMMENT ON COLUMN marketdata_ingestion_jobs.exchange_code IS '交易所代码，GateH-2 仅允许 OKX、BINANCE';
COMMENT ON COLUMN marketdata_ingestion_jobs.market_type IS '市场类型，GateH-2 仅允许 SPOT';
COMMENT ON COLUMN marketdata_ingestion_jobs.symbol IS '系统内部交易对代码，GateH-2 仅允许 BTC-USDT、ETH-USDT、SOL-USDT';
COMMENT ON COLUMN marketdata_ingestion_jobs."interval" IS 'K 线周期，GateH-2 仅允许 1m、5m、15m、1h、4h、1d';
COMMENT ON COLUMN marketdata_ingestion_jobs.start_time IS '任务计划拉取范围开始时间，闭区间起点，对应 K 线 open_time 下界';
COMMENT ON COLUMN marketdata_ingestion_jobs.end_time IS '任务计划拉取范围结束时间，闭区间终点，对应 K 线 close_time 上界';
COMMENT ON COLUMN marketdata_ingestion_jobs.status IS '任务状态，允许值：CREATED、RUNNING、SUCCEEDED、FAILED、PARTIAL';
COMMENT ON COLUMN marketdata_ingestion_jobs.source IS '任务来源，GateH-2 使用 EXCHANGE_HISTORICAL 表示交易所历史 K 线接入';
COMMENT ON COLUMN marketdata_ingestion_jobs.created_by IS '创建任务的用户标识，用于审计；本地自动化可写入 local 或 e2e 用户名';
COMMENT ON COLUMN marketdata_ingestion_jobs.created_at IS '任务创建时间，由数据库默认当前时间写入';
COMMENT ON COLUMN marketdata_ingestion_jobs.updated_at IS '任务最近更新时间，每次运行状态变化时同步更新';
COMMENT ON COLUMN marketdata_ingestion_jobs.request_json IS '任务创建时的原始请求快照 JSONB，用于审计和复盘；仅保存 API 请求字段，不保存密钥或敏感信息';

CREATE INDEX IF NOT EXISTS idx_marketdata_ingestion_jobs_scope_updated
    ON marketdata_ingestion_jobs (exchange_code, market_type, symbol, "interval", updated_at DESC);

CREATE TABLE IF NOT EXISTS marketdata_ingestion_runs (
    run_id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES marketdata_ingestion_jobs(job_id),
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    requested_start_time TIMESTAMPTZ NOT NULL,
    requested_end_time TIMESTAMPTZ NOT NULL,
    actual_start_time TIMESTAMPTZ,
    actual_end_time TIMESTAMPTZ,
    fetched_bars INTEGER NOT NULL DEFAULT 0,
    inserted_bars INTEGER NOT NULL DEFAULT 0,
    updated_bars INTEGER NOT NULL DEFAULT 0,
    skipped_bars INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    raw_summary_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_marketdata_ingestion_runs_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'PARTIAL')),
    CONSTRAINT ck_marketdata_ingestion_runs_requested_range CHECK (requested_end_time >= requested_start_time),
    CONSTRAINT ck_marketdata_ingestion_runs_counts CHECK (
        fetched_bars >= 0 AND inserted_bars >= 0 AND updated_bars >= 0 AND skipped_bars >= 0
    )
);

COMMENT ON TABLE marketdata_ingestion_runs IS 'GateH-2 行情历史数据接入运行记录表，记录每次 run-once 的执行结果和统计信息';
COMMENT ON COLUMN marketdata_ingestion_runs.run_id IS '接入运行 ID，业务主键，标识一次 run-once 执行';
COMMENT ON COLUMN marketdata_ingestion_runs.job_id IS '关联的行情接入任务 ID，对应 marketdata_ingestion_jobs.job_id';
COMMENT ON COLUMN marketdata_ingestion_runs.status IS '运行状态，允许值：RUNNING、SUCCEEDED、FAILED、PARTIAL';
COMMENT ON COLUMN marketdata_ingestion_runs.started_at IS '本次运行开始时间，由应用在调用交易所前写入';
COMMENT ON COLUMN marketdata_ingestion_runs.finished_at IS '本次运行完成时间，成功、部分成功或失败结束时写入';
COMMENT ON COLUMN marketdata_ingestion_runs.requested_start_time IS '本次运行实际请求的开始时间；断点续拉时可能晚于任务 start_time';
COMMENT ON COLUMN marketdata_ingestion_runs.requested_end_time IS '本次运行实际请求的结束时间；GateH-2 不超过任务 end_time';
COMMENT ON COLUMN marketdata_ingestion_runs.actual_start_time IS '本次从交易所返回并通过校验的最早 K 线 open_time；无有效数据时为空';
COMMENT ON COLUMN marketdata_ingestion_runs.actual_end_time IS '本次从交易所返回并通过校验的最晚 K 线 close_time；无有效数据时为空';
COMMENT ON COLUMN marketdata_ingestion_runs.fetched_bars IS '本次从交易所接口获取的 K 线数量';
COMMENT ON COLUMN marketdata_ingestion_runs.inserted_bars IS '本次新增写入 marketdata_bars 的 K 线数量';
COMMENT ON COLUMN marketdata_ingestion_runs.updated_bars IS '本次幂等更新 marketdata_bars 的 K 线数量';
COMMENT ON COLUMN marketdata_ingestion_runs.skipped_bars IS '本次因重复、非法或超出范围而跳过的 K 线数量';
COMMENT ON COLUMN marketdata_ingestion_runs.error_message IS '本次运行失败或部分失败的可读错误摘要，不保存密钥、token 或完整敏感响应';
COMMENT ON COLUMN marketdata_ingestion_runs.raw_summary_json IS '本次运行的原始统计摘要 JSONB，用于排障、审计和复盘；保存计数、断点和非敏感错误码';
COMMENT ON COLUMN marketdata_ingestion_runs.created_at IS '运行记录创建时间，由数据库默认当前时间写入';

CREATE INDEX IF NOT EXISTS idx_marketdata_ingestion_runs_job_started
    ON marketdata_ingestion_runs (job_id, started_at DESC);
