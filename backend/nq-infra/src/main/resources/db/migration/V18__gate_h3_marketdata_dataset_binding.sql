-- GateH-3: marketdata dataset, quality coverage, and backtest binding.
-- 本 migration 只新增 GateH-3 所需表/字段，不修改历史 migration，不改变回测算法语义。

CREATE TABLE marketdata_datasets (
    dataset_id UUID PRIMARY KEY,
    dataset_name VARCHAR(255) NOT NULL,
    exchange_code VARCHAR(32) NOT NULL,
    market_type VARCHAR(16) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    "interval" VARCHAR(16) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    bar_count BIGINT NOT NULL DEFAULT 0,
    gap_count BIGINT NOT NULL DEFAULT 0,
    source VARCHAR(64) NOT NULL,
    created_by VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    request_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT chk_marketdata_datasets_scope
        CHECK (
            exchange_code IN ('OKX', 'BINANCE')
            AND market_type = 'SPOT'
            AND symbol IN ('BTC-USDT', 'ETH-USDT', 'SOL-USDT')
            AND "interval" IN ('1m', '5m', '15m', '1h', '4h', '1d')
        ),
    CONSTRAINT chk_marketdata_datasets_status
        CHECK (status IN ('CREATED', 'READY', 'INVALID', 'ARCHIVED')),
    CONSTRAINT chk_marketdata_datasets_quality_status
        CHECK (quality_status IN ('OK', 'GAP_DETECTED', 'INCOMPLETE', 'INVALID')),
    CONSTRAINT chk_marketdata_datasets_time_range
        CHECK (end_time > start_time),
    CONSTRAINT uq_marketdata_datasets_scope
        UNIQUE (dataset_name, exchange_code, market_type, symbol, "interval", start_time, end_time)
);

CREATE INDEX idx_marketdata_datasets_scope_updated
    ON marketdata_datasets (exchange_code, market_type, symbol, "interval", updated_at DESC);

CREATE INDEX idx_marketdata_datasets_quality_status
    ON marketdata_datasets (quality_status, updated_at DESC);

CREATE TABLE marketdata_dataset_coverage (
    coverage_id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL,
    range_start_time TIMESTAMPTZ NOT NULL,
    range_end_time TIMESTAMPTZ NOT NULL,
    expected_bars BIGINT NOT NULL DEFAULT 0,
    actual_bars BIGINT NOT NULL DEFAULT 0,
    missing_bars BIGINT NOT NULL DEFAULT 0,
    duplicate_bars BIGINT NOT NULL DEFAULT 0,
    invalid_bars BIGINT NOT NULL DEFAULT 0,
    quality_status VARCHAR(32) NOT NULL,
    summary_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_marketdata_dataset_coverage_dataset
        FOREIGN KEY (dataset_id) REFERENCES marketdata_datasets (dataset_id),
    CONSTRAINT chk_marketdata_dataset_coverage_quality_status
        CHECK (quality_status IN ('OK', 'GAP_DETECTED', 'INCOMPLETE', 'INVALID')),
    CONSTRAINT chk_marketdata_dataset_coverage_range
        CHECK (range_end_time > range_start_time)
);

CREATE INDEX idx_marketdata_dataset_coverage_dataset_created
    ON marketdata_dataset_coverage (dataset_id, created_at DESC);

ALTER TABLE backtest_configs
    ADD COLUMN dataset_id UUID,
    ADD COLUMN dataset_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD CONSTRAINT fk_backtest_configs_dataset
        FOREIGN KEY (dataset_id) REFERENCES marketdata_datasets (dataset_id);

CREATE INDEX idx_backtest_configs_dataset_id
    ON backtest_configs (dataset_id, updated_at DESC);

ALTER TABLE backtest_runs
    ADD COLUMN dataset_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON TABLE marketdata_datasets IS 'GateH-3 行情数据集定义表，描述可绑定回测配置的历史 K 线数据范围、质量状态和来源';
COMMENT ON COLUMN marketdata_datasets.dataset_id IS '数据集 ID，业务主键，供 backtest_configs.dataset_id 绑定';
COMMENT ON COLUMN marketdata_datasets.dataset_name IS '数据集名称，同一范围内应具备人工可识别含义';
COMMENT ON COLUMN marketdata_datasets.exchange_code IS '交易所代码，GateH-3 仅允许 OKX、BINANCE';
COMMENT ON COLUMN marketdata_datasets.market_type IS '市场类型，GateH-3 仅允许 SPOT';
COMMENT ON COLUMN marketdata_datasets.symbol IS '系统内部交易对代码，GateH-3 仅允许 BTC-USDT、ETH-USDT、SOL-USDT';
COMMENT ON COLUMN marketdata_datasets."interval" IS 'K 线周期，GateH-3 仅允许 1m、5m、15m、1h、4h、1d';
COMMENT ON COLUMN marketdata_datasets.start_time IS '数据集覆盖范围起始时间，按 K 线 open_time 下界解释';
COMMENT ON COLUMN marketdata_datasets.end_time IS '数据集覆盖范围结束时间，按 K 线 close_time 上界解释';
COMMENT ON COLUMN marketdata_datasets.status IS '数据集状态，允许值：CREATED、READY、INVALID、ARCHIVED';
COMMENT ON COLUMN marketdata_datasets.quality_status IS '数据集质量状态，允许值：OK、GAP_DETECTED、INCOMPLETE、INVALID';
COMMENT ON COLUMN marketdata_datasets.bar_count IS '当前质量统计得到的 K 线数量，单位为条';
COMMENT ON COLUMN marketdata_datasets.gap_count IS '当前质量统计得到的缺失 K 线数量，单位为条';
COMMENT ON COLUMN marketdata_datasets.source IS '数据集来源，GateH-3 固定从 marketdata_bars 派生';
COMMENT ON COLUMN marketdata_datasets.created_by IS '创建数据集的用户或本地执行主体，用于审计';
COMMENT ON COLUMN marketdata_datasets.created_at IS '数据集创建时间';
COMMENT ON COLUMN marketdata_datasets.updated_at IS '数据集最近一次质量刷新或绑定相关更新时间';
COMMENT ON COLUMN marketdata_datasets.request_json IS '数据集创建请求快照 JSONB，不保存密钥、token、cookie，仅保存范围、symbol、interval 等审计字段';

COMMENT ON TABLE marketdata_dataset_coverage IS 'GateH-3 行情数据集覆盖与质量统计表，记录每次 refresh-quality 的覆盖率和缺口摘要';
COMMENT ON COLUMN marketdata_dataset_coverage.coverage_id IS '覆盖统计 ID，业务主键';
COMMENT ON COLUMN marketdata_dataset_coverage.dataset_id IS '关联数据集 ID，对应 marketdata_datasets.dataset_id';
COMMENT ON COLUMN marketdata_dataset_coverage.range_start_time IS '本次覆盖统计起始时间，按 K 线 open_time 下界解释';
COMMENT ON COLUMN marketdata_dataset_coverage.range_end_time IS '本次覆盖统计结束时间，按 K 线 close_time 上界解释';
COMMENT ON COLUMN marketdata_dataset_coverage.expected_bars IS '按 interval 和时间范围计算的理论 K 线数量，单位为条';
COMMENT ON COLUMN marketdata_dataset_coverage.actual_bars IS 'marketdata_bars 中实际命中的 K 线数量，单位为条';
COMMENT ON COLUMN marketdata_dataset_coverage.missing_bars IS 'expected_bars 与 actual_bars 差值归一后的缺失 K 线数量，单位为条';
COMMENT ON COLUMN marketdata_dataset_coverage.duplicate_bars IS '重复 K 线数量；GateH-3 依赖 marketdata_bars 唯一约束，正常应为 0';
COMMENT ON COLUMN marketdata_dataset_coverage.invalid_bars IS '价格、数量或 quality_status 非 OK 的异常 K 线数量，单位为条';
COMMENT ON COLUMN marketdata_dataset_coverage.quality_status IS '本次覆盖统计质量状态，允许值：OK、GAP_DETECTED、INCOMPLETE、INVALID';
COMMENT ON COLUMN marketdata_dataset_coverage.summary_json IS '覆盖统计摘要 JSONB，用于记录 expected/actual/missing/invalid/duplicate 和数据来源，不作为主查询字段';
COMMENT ON COLUMN marketdata_dataset_coverage.created_at IS '覆盖统计创建时间，即 refresh-quality 执行完成时间';

COMMENT ON COLUMN backtest_configs.dataset_id IS 'GateH-3 绑定的数据集 ID，对应 marketdata_datasets.dataset_id；为空表示尚未绑定正式数据集';
COMMENT ON COLUMN backtest_configs.dataset_snapshot_json IS 'GateH-3 回测配置绑定数据集时保存的数据集快照 JSONB，用于配置详情和后续 run 溯源';
COMMENT ON COLUMN backtest_runs.dataset_snapshot_json IS 'GateH-3 回测运行创建时从 backtest_configs 固化的数据集快照 JSONB，用于历史运行复盘与评估/发布溯源';
