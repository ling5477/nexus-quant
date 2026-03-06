-- V4__gate_c_trade_external_order_id_index.sql
-- 目的：落实 GateC DB_SCHEMA 第 5 条建议 DDL，增强按外部订单号回溯成交的能力。
-- Why:
-- 1) reconcile/复盘时经常先拿到交易所 external_order_id，再回查本地 trades；
-- 2) 仅靠 (exchange, exchange_trade_id) 更偏成交维度，订单维度排障仍有缺口；
-- 3) 新索引采用 (exchange, external_order_id) 且 external_order_id 非空条件索引，降低无效索引开销。

ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS external_order_id VARCHAR(128);

UPDATE trades AS t
SET external_order_id = o.external_order_id
FROM orders AS o
WHERE t.order_id = o.order_id
  AND t.external_order_id IS NULL
  AND o.external_order_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_trades_exchange_external_order_id
    ON trades (exchange, external_order_id)
    WHERE external_order_id IS NOT NULL;
