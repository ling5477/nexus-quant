-- 扫描进度不是订单业务事实；不关联订单外键，删除或状态变化不得破坏循环游标。
CREATE TABLE reconciliation_scan_cursors (
    venue VARCHAR(32) PRIMARY KEY,
    cursor_created_at TIMESTAMPTZ,
    cursor_order_id VARCHAR(64),
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_reconciliation_cursor_key CHECK (
        (cursor_created_at IS NULL AND cursor_order_id IS NULL)
        OR (cursor_created_at IS NOT NULL AND cursor_order_id IS NOT NULL)
    ),
    CONSTRAINT ck_reconciliation_cursor_venue CHECK (BTRIM(venue) <> '')
);

COMMENT ON TABLE reconciliation_scan_cursors IS '每 venue 一条 canonical 对账扫描进度；不保存订单、成交、账本或授权事实';
COMMENT ON COLUMN reconciliation_scan_cursors.venue IS '扫描所属 venue，也是并发预留锁的唯一键';
COMMENT ON COLUMN reconciliation_scan_cursors.cursor_created_at IS '上次成功预留的最后订单创建时间，初始为空';
COMMENT ON COLUMN reconciliation_scan_cursors.cursor_order_id IS '同时间订单的确定性全序键，不设订单外键以容忍候选删除';
COMMENT ON COLUMN reconciliation_scan_cursors.revision IS '每次非空成功预留递增的进度代际，不是订单版本';
COMMENT ON COLUMN reconciliation_scan_cursors.updated_at IS '扫描预留事务的 UTC 时间，不改变订单更新时间';
