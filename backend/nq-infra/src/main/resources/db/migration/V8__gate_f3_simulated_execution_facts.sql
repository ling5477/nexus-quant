-- V8__gate_f3_simulated_execution_facts.sql
-- 目的：为 GateF-3 建立独立于实盘执行域的最小模拟执行事实链。
-- Why:
-- 1) GateF-3 必须形成 sim_order / sim_trade / sim_position / sim_pnl 独立事实表；
-- 2) 回测事实不能复用 orders / trades / positions / ledger_entries 等实盘表；
-- 3) GateF-4 的评估逻辑必须基于独立模拟事实继续扩展，而不是把明细继续塞回 backtest_runs.summary_json。

CREATE TABLE sim_orders (
    sim_order_id VARCHAR(128) PRIMARY KEY,
    backtest_run_id VARCHAR(128) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    side VARCHAR(16) NOT NULL,
    order_type VARCHAR(32) NOT NULL,
    requested_quantity NUMERIC(36, 18) NOT NULL,
    requested_price NUMERIC(36, 18) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    filled_at TIMESTAMPTZ,
    reject_reason TEXT,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_sim_orders_run
        FOREIGN KEY (backtest_run_id) REFERENCES backtest_runs (backtest_run_id),
    CONSTRAINT chk_sim_orders_status
        CHECK (status IN ('CREATED', 'FILLED', 'REJECTED'))
);

CREATE INDEX idx_sim_orders_backtest_run_id
    ON sim_orders (backtest_run_id, created_at DESC);

CREATE TABLE sim_trades (
    sim_trade_id VARCHAR(128) PRIMARY KEY,
    sim_order_id VARCHAR(128) NOT NULL,
    backtest_run_id VARCHAR(128) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    side VARCHAR(16) NOT NULL,
    quantity NUMERIC(36, 18) NOT NULL,
    trade_price NUMERIC(36, 18) NOT NULL,
    fee_amount NUMERIC(36, 18) NOT NULL,
    slippage_amount NUMERIC(36, 18) NOT NULL,
    traded_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_sim_trades_order
        FOREIGN KEY (sim_order_id) REFERENCES sim_orders (sim_order_id),
    CONSTRAINT fk_sim_trades_run
        FOREIGN KEY (backtest_run_id) REFERENCES backtest_runs (backtest_run_id)
);

CREATE INDEX idx_sim_trades_backtest_run_id
    ON sim_trades (backtest_run_id, traded_at DESC);

CREATE TABLE sim_positions (
    sim_position_id VARCHAR(128) PRIMARY KEY,
    backtest_run_id VARCHAR(128) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    quantity NUMERIC(36, 18) NOT NULL,
    average_entry_price NUMERIC(36, 18) NOT NULL,
    realized_pnl NUMERIC(36, 18) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_sim_positions_run
        FOREIGN KEY (backtest_run_id) REFERENCES backtest_runs (backtest_run_id),
    CONSTRAINT uq_sim_positions_run_symbol UNIQUE (backtest_run_id, symbol)
);

CREATE INDEX idx_sim_positions_backtest_run_symbol
    ON sim_positions (backtest_run_id, symbol);

CREATE TABLE sim_pnl_snapshots (
    sim_pnl_snapshot_id VARCHAR(128) PRIMARY KEY,
    backtest_run_id VARCHAR(128) NOT NULL,
    snapshot_time TIMESTAMPTZ NOT NULL,
    cash_balance NUMERIC(36, 18) NOT NULL,
    position_market_value NUMERIC(36, 18) NOT NULL,
    realized_pnl NUMERIC(36, 18) NOT NULL,
    unrealized_pnl NUMERIC(36, 18) NOT NULL,
    total_fee NUMERIC(36, 18) NOT NULL,
    total_slippage NUMERIC(36, 18) NOT NULL,
    equity NUMERIC(36, 18) NOT NULL,
    net_pnl NUMERIC(36, 18) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_sim_pnl_snapshots_run
        FOREIGN KEY (backtest_run_id) REFERENCES backtest_runs (backtest_run_id)
);

CREATE INDEX idx_sim_pnl_snapshots_run_snapshot_time
    ON sim_pnl_snapshots (backtest_run_id, snapshot_time);
