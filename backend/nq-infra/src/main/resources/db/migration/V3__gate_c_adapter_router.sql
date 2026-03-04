-- V3__gate_c_adapter_router.sql
-- 目的：落实 GateC-0 的最小前置改造，让 orders 能稳定关联 venue + external_order_id。
-- Why:
-- 1) docs/current/GATE_CHECKLIST.md 要求 placeOrder 成功回执后把 external_order_id 落库；
-- 2) GateC 的恢复/对账/WS 关联要求按 (venue, external_order_id) 精确定位订单；
-- 3) 现有 orders 基线缺少 venue 列，因此本次一并补齐并从 accounts 回填，避免索引失效。

ALTER TABLE orders
    ADD COLUMN venue VARCHAR(32);

UPDATE orders AS o
SET venue = a.venue
FROM accounts AS a
WHERE o.account_id = a.account_id
  AND o.venue IS NULL;

ALTER TABLE orders
    ALTER COLUMN venue SET NOT NULL;

ALTER TABLE orders
    ADD COLUMN external_order_id VARCHAR(128);

CREATE INDEX idx_orders_venue_external_order_id
    ON orders (venue, external_order_id);
