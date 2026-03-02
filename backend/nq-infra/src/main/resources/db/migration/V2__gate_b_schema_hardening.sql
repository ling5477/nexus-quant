-- V2__gate_b_schema_hardening.sql
-- 目的：落实 docs/gates/gate-b/DB_SCHEMA.md 第 3 节建议的 Gate B 最小增量 DDL。
-- Why:
-- 1) 为 strategy_run/account/trace 排查补齐必要索引，降低闭环复盘与恢复扫描成本；
-- 2) 把 Gate B 已经在代码层依赖的最小数据约束下沉到数据库，避免脏数据绕过服务层直接入库。
-- 3) 这里使用 Flyway 版本化 migration，因此不额外包裹 IF NOT EXISTS / DO 块，避免 SQL 方言误报。

CREATE INDEX idx_orders_strategy_run_id
    ON orders (strategy_run_id);

CREATE INDEX idx_trades_account_ts
    ON trades (account_id, ts DESC);

CREATE INDEX idx_ledger_entries_trace_id
    ON ledger_entries (trace_id);

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_qty_positive
        CHECK (qty > 0);

ALTER TABLE trades
    ADD CONSTRAINT chk_trades_qty_positive
        CHECK (qty > 0);

ALTER TABLE trades
    ADD CONSTRAINT chk_trades_price_positive
        CHECK (price > 0);

ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_entries_direction
        CHECK (direction IN ('DEBIT', 'CREDIT'));

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_side
        CHECK (side IN ('BUY', 'SELL'));

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_type
        CHECK (type IN ('MARKET', 'LIMIT'));
