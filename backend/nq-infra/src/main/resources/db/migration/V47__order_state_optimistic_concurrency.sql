-- 状态代际识别跨事务旧回执；常量默认值使既有行从零开始，identity-only enrichment 不推进代际。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

ALTER TABLE orders
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_orders_version_nonnegative CHECK (version >= 0);

COMMENT ON COLUMN orders.version IS '订单状态的持久化迁移代际；每次成功状态迁移递增一次，用于拒绝旧快照及ABA回执';
