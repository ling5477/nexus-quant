-- V21__gate_i3_paper_trading.sql
-- 目的：落实 GateI-3 SIM/Paper Trading 运行闭环最小数据模型。
-- Why:
-- 1) GateI-3 需要把已发布的策略版本推进到 SIM/Paper run，并最小化记录订单、成交、持仓事实，作为 GateI-4 风控回写、资金曲线、复盘和异常停机的输入；
-- 2) 本 migration 只新增 GateI-3 必要表与字段，不修改历史 migration，不改交易核心状态机；
-- 3) JSONB 快照字段只保存可审计业务输入，禁止保存 token、cookie、密钥等敏感信息。

CREATE TABLE paper_trading_runs (
    paper_run_id VARCHAR(64) PRIMARY KEY,
    publish_id VARCHAR(64) NOT NULL,
    strategy_version_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    trade_env VARCHAR(16) NOT NULL,
    exchange_code VARCHAR(32) NOT NULL,
    market_type VARCHAR(16) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    interval_code VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ,
    stopped_at TIMESTAMPTZ,
    publish_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    strategy_version_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    dataset_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    param_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    config_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_paper_runs_status CHECK (status IN ('CREATED', 'RUNNING', 'STOPPED', 'FAILED')),
    CONSTRAINT chk_paper_runs_trade_env CHECK (trade_env IN ('SIM', 'LIVE')),
    CONSTRAINT fk_paper_runs_publish FOREIGN KEY (publish_id)
        REFERENCES backtest_publish_records (publish_record_id),
    CONSTRAINT fk_paper_runs_strategy_version FOREIGN KEY (strategy_version_id)
        REFERENCES strategy_versions (strategy_version_id)
);

CREATE INDEX idx_paper_runs_publish_id ON paper_trading_runs (publish_id, created_at DESC);
CREATE INDEX idx_paper_runs_strategy_version_id ON paper_trading_runs (strategy_version_id, created_at DESC);
CREATE INDEX idx_paper_runs_status ON paper_trading_runs (status, updated_at DESC);

COMMENT ON TABLE paper_trading_runs IS 'GateI-3 Paper Trading 运行实例表：记录基于 publish_id 的 SIM/Paper 运行实例，固化 publish/strategy version/dataset/param/config 快照，是 GateI-4 风控回写、资金曲线、复盘和异常停机的输入';
COMMENT ON COLUMN paper_trading_runs.paper_run_id IS 'Paper run 主键，业务可读 ID，例如 ptr-<uuid>';
COMMENT ON COLUMN paper_trading_runs.publish_id IS 'Paper run 引用的发布记录 ID，对应 backtest_publish_records.publish_record_id；只能引用 SUCCEEDED 的发布记录';
COMMENT ON COLUMN paper_trading_runs.strategy_version_id IS 'Paper run 创建时从 publish 链路固化的策略版本 ID，对应 strategy_versions.strategy_version_id；为空表示发布未绑定策略版本';
COMMENT ON COLUMN paper_trading_runs.status IS 'Paper run 状态机：CREATED 已创建未启动；RUNNING 已启动；STOPPED 主动停止；FAILED 启动或运行失败';
COMMENT ON COLUMN paper_trading_runs.trade_env IS 'Paper run 交易环境，固定为 SIM 或 LIVE；GateI-3 第一版只允许 SIM';
COMMENT ON COLUMN paper_trading_runs.exchange_code IS 'Paper run 目标交易所代码，例如 OKX、BINANCE；与 publish 链路 dataset 一致';
COMMENT ON COLUMN paper_trading_runs.market_type IS 'Paper run 市场类型，固定为 SPOT；GateI-3 不进入合约全量';
COMMENT ON COLUMN paper_trading_runs.symbol IS 'Paper run 交易对，例如 BTC-USDT、ETH-USDT、SOL-USDT；与 dataset snapshot 对齐';
COMMENT ON COLUMN paper_trading_runs.interval_code IS 'Paper run 行情周期，例如 1m、5m、15m、1h、4h、1d；保留字段名避开 PostgreSQL interval 关键字';
COMMENT ON COLUMN paper_trading_runs.started_at IS 'Paper run 启动时间；为空表示尚未启动';
COMMENT ON COLUMN paper_trading_runs.stopped_at IS 'Paper run 停止时间；为空表示尚未停止';
COMMENT ON COLUMN paper_trading_runs.publish_snapshot_json IS 'Paper run 创建时固化的发布记录快照 JSONB；不得保存 token、cookie、密钥';
COMMENT ON COLUMN paper_trading_runs.strategy_version_snapshot_json IS 'Paper run 创建时固化的策略版本快照 JSONB，含策略编码、版本号、状态、参数、配置、来源；不得保存敏感凭证';
COMMENT ON COLUMN paper_trading_runs.dataset_snapshot_json IS 'Paper run 创建时固化的 dataset 快照 JSONB，来自 publish 引用的 backtest run，用于行情对齐；不得保存敏感凭证';
COMMENT ON COLUMN paper_trading_runs.param_snapshot_json IS 'Paper run 创建时固化的参数快照 JSONB，来自 publish 链路的策略版本参数；不得保存敏感凭证';
COMMENT ON COLUMN paper_trading_runs.config_snapshot_json IS 'Paper run 创建时固化的运行配置快照 JSONB，含初始资金、撮合口径、手续费等运行级配置；不得保存敏感凭证';
COMMENT ON COLUMN paper_trading_runs.created_by IS 'Paper run 创建者用户名，来自登录上下文；用于审计';
COMMENT ON COLUMN paper_trading_runs.created_at IS 'Paper run 创建时间，UTC';
COMMENT ON COLUMN paper_trading_runs.updated_at IS 'Paper run 最近一次状态或字段更新时间，UTC';

CREATE TABLE paper_trading_orders (
    paper_order_id VARCHAR(64) PRIMARY KEY,
    paper_run_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    side VARCHAR(8) NOT NULL,
    order_type VARCHAR(16) NOT NULL,
    quantity NUMERIC(36, 18) NOT NULL,
    price NUMERIC(36, 18),
    status VARCHAR(16) NOT NULL,
    reason VARCHAR(256),
    raw_signal_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_paper_orders_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT chk_paper_orders_status CHECK (status IN ('CREATED', 'FILLED', 'CANCELED', 'REJECTED')),
    CONSTRAINT fk_paper_orders_run FOREIGN KEY (paper_run_id)
        REFERENCES paper_trading_runs (paper_run_id)
);

CREATE INDEX idx_paper_orders_run_id ON paper_trading_orders (paper_run_id, created_at DESC);
CREATE INDEX idx_paper_orders_run_symbol_status ON paper_trading_orders (paper_run_id, symbol, status);

COMMENT ON TABLE paper_trading_orders IS 'GateI-3 Paper Trading 订单事实表：记录 Paper run 产生的订单最小事实，第一版不接入真实交易所下单接口';
COMMENT ON COLUMN paper_trading_orders.paper_order_id IS 'Paper 订单主键，业务可读 ID，例如 pto-<uuid>';
COMMENT ON COLUMN paper_trading_orders.paper_run_id IS '所属 Paper run ID，外键 paper_trading_runs.paper_run_id';
COMMENT ON COLUMN paper_trading_orders.symbol IS '订单交易对；GateI-3 限定为 publish 链路对齐的现货 symbol';
COMMENT ON COLUMN paper_trading_orders.side IS '订单方向：BUY 买入，SELL 卖出';
COMMENT ON COLUMN paper_trading_orders.order_type IS '订单类型：第一版固定为 MARKET 或 LIMIT；不接合约下单类型';
COMMENT ON COLUMN paper_trading_orders.quantity IS '订单数量，正数；高精度数值保留 18 位小数';
COMMENT ON COLUMN paper_trading_orders.price IS '订单价格，LIMIT 必填，MARKET 可空；高精度数值保留 18 位小数';
COMMENT ON COLUMN paper_trading_orders.status IS '订单状态：CREATED 已创建未撮合；FILLED 已成交；CANCELED 已撤销；REJECTED 风控或撮合拒绝';
COMMENT ON COLUMN paper_trading_orders.reason IS '订单状态变更原因摘要，例如风控拒绝原因或撤销原因';
COMMENT ON COLUMN paper_trading_orders.raw_signal_json IS 'Paper 订单触发信号快照 JSONB，第一版可保存策略版本/参数摘要；不得保存敏感凭证';
COMMENT ON COLUMN paper_trading_orders.created_at IS '订单创建时间，UTC';
COMMENT ON COLUMN paper_trading_orders.updated_at IS '订单最近一次状态更新时间，UTC';

CREATE TABLE paper_trading_trades (
    paper_trade_id VARCHAR(64) PRIMARY KEY,
    paper_order_id VARCHAR(64) NOT NULL,
    paper_run_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    side VARCHAR(8) NOT NULL,
    quantity NUMERIC(36, 18) NOT NULL,
    price NUMERIC(36, 18) NOT NULL,
    fee NUMERIC(36, 18) NOT NULL DEFAULT 0,
    traded_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_paper_trades_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT fk_paper_trades_order FOREIGN KEY (paper_order_id)
        REFERENCES paper_trading_orders (paper_order_id),
    CONSTRAINT fk_paper_trades_run FOREIGN KEY (paper_run_id)
        REFERENCES paper_trading_runs (paper_run_id)
);

CREATE INDEX idx_paper_trades_run_id ON paper_trading_trades (paper_run_id, traded_at DESC);
CREATE INDEX idx_paper_trades_order_id ON paper_trading_trades (paper_order_id);
CREATE INDEX idx_paper_trades_symbol_time ON paper_trading_trades (symbol, traded_at DESC);

COMMENT ON TABLE paper_trading_trades IS 'GateI-3 Paper Trading 成交事实表：记录 Paper run 订单的成交事实，第一版只承载最小成交信息';
COMMENT ON COLUMN paper_trading_trades.paper_trade_id IS 'Paper 成交主键，业务可读 ID，例如 ptt-<uuid>';
COMMENT ON COLUMN paper_trading_trades.paper_order_id IS '成交所属订单 ID，外键 paper_trading_orders.paper_order_id';
COMMENT ON COLUMN paper_trading_trades.paper_run_id IS '成交所属 Paper run ID，外键 paper_trading_runs.paper_run_id；冗余便于查询';
COMMENT ON COLUMN paper_trading_trades.symbol IS '成交交易对，与订单 symbol 对齐';
COMMENT ON COLUMN paper_trading_trades.side IS '成交方向 BUY/SELL，与订单 side 对齐';
COMMENT ON COLUMN paper_trading_trades.quantity IS '成交数量，正数；高精度数值';
COMMENT ON COLUMN paper_trading_trades.price IS '成交价格；高精度数值';
COMMENT ON COLUMN paper_trading_trades.fee IS '成交手续费，第一版口径取自 config_snapshot 的 feeRate；缺省为 0';
COMMENT ON COLUMN paper_trading_trades.traded_at IS '成交发生时间，UTC';
COMMENT ON COLUMN paper_trading_trades.created_at IS '成交记录写入时间，UTC';

CREATE TABLE paper_trading_positions (
    paper_position_id VARCHAR(64) PRIMARY KEY,
    paper_run_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    quantity NUMERIC(36, 18) NOT NULL DEFAULT 0,
    avg_price NUMERIC(36, 18) NOT NULL DEFAULT 0,
    unrealized_pnl NUMERIC(36, 18) NOT NULL DEFAULT 0,
    realized_pnl NUMERIC(36, 18) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_paper_positions_run_symbol UNIQUE (paper_run_id, symbol),
    CONSTRAINT fk_paper_positions_run FOREIGN KEY (paper_run_id)
        REFERENCES paper_trading_runs (paper_run_id)
);

CREATE INDEX idx_paper_positions_run_id ON paper_trading_positions (paper_run_id, updated_at DESC);

COMMENT ON TABLE paper_trading_positions IS 'GateI-3 Paper Trading 持仓事实表：记录 Paper run 当前持仓与已实现/未实现盈亏；按 (paper_run_id, symbol) 唯一';
COMMENT ON COLUMN paper_trading_positions.paper_position_id IS 'Paper 持仓主键，业务可读 ID，例如 ptp-<uuid>';
COMMENT ON COLUMN paper_trading_positions.paper_run_id IS '持仓所属 Paper run ID，外键 paper_trading_runs.paper_run_id';
COMMENT ON COLUMN paper_trading_positions.symbol IS '持仓交易对';
COMMENT ON COLUMN paper_trading_positions.quantity IS '当前净持仓数量，可为 0；不区分多空，第一版只记录现货净仓';
COMMENT ON COLUMN paper_trading_positions.avg_price IS '当前持仓加权平均成本价；持仓为 0 时回到 0';
COMMENT ON COLUMN paper_trading_positions.unrealized_pnl IS '未实现盈亏；第一版仅作为占位字段，由后续 GateI-4 资金/持仓曲线写入';
COMMENT ON COLUMN paper_trading_positions.realized_pnl IS '已实现盈亏累计值；第一版仅作为占位字段，由后续 GateI-4 资金/持仓曲线写入';
COMMENT ON COLUMN paper_trading_positions.updated_at IS '持仓最近一次更新时间，UTC';
COMMENT ON COLUMN paper_trading_positions.created_at IS '持仓首次写入时间，UTC';
