-- 只增加维护请求回执，不回填、不修改任何订单、账务或历史回放事实。
CREATE TABLE trading_maintenance_operations (
    operation_id UUID PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES users(id),
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('RECONCILE','RECOVERY')),
    venue VARCHAR(16) NOT NULL CHECK (venue IN ('OKX','BINANCE')),
    scan_limit INTEGER,
    state VARCHAR(16) NOT NULL CHECK (state IN ('STARTED','COMPLETED','UNKNOWN')),
    trace_id VARCHAR(128) NOT NULL CHECK (length(trace_id)>0),
    detail TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_maintenance_kind_limit CHECK (
        (kind='RECONCILE' AND scan_limit IS NOT NULL AND scan_limit BETWEEN 1 AND 1000)
        OR (kind='RECOVERY' AND scan_limit IS NULL)),
    CONSTRAINT ck_maintenance_result CHECK (
        (state='COMPLETED' AND detail IS NOT NULL AND completed_at IS NOT NULL AND completed_at>=created_at)
        OR (state IN ('STARTED','UNKNOWN') AND detail IS NULL AND completed_at IS NULL))
);
COMMENT ON TABLE trading_maintenance_operations IS '维护原请求的持久回执；不构成订单、成交或账务事实源';
COMMENT ON COLUMN trading_maintenance_operations.operation_id IS '客户端在首次请求前持久保存的稳定身份';
COMMENT ON COLUMN trading_maintenance_operations.owner_user_id IS '提交人的正式用户身份；查询不能跨用户读取';
COMMENT ON COLUMN trading_maintenance_operations.scan_limit IS '原对账候选总上限；恢复不接受该参数';
COMMENT ON COLUMN trading_maintenance_operations.state IS 'STARTED或UNKNOWN均禁止重领；COMPLETED仅代表原服务已完整返回';
COMMENT ON COLUMN trading_maintenance_operations.detail IS '原服务返回的计数摘要，不是资金或订单事实的副本';

-- 恢复服务可能覆盖多个交易所的事件，不能以另一交易所编号并发绕过待定执行。
CREATE UNIQUE INDEX ux_maintenance_unconfirmed ON trading_maintenance_operations ((true))
    WHERE state IN ('STARTED','UNKNOWN');

CREATE FUNCTION protect_trading_maintenance_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'maintenance receipts are retained';
    END IF;
    IF ROW(NEW.operation_id,NEW.owner_user_id,NEW.kind,NEW.venue,NEW.scan_limit,NEW.trace_id,NEW.created_at)
       IS DISTINCT FROM ROW(OLD.operation_id,OLD.owner_user_id,OLD.kind,OLD.venue,OLD.scan_limit,OLD.trace_id,OLD.created_at)
       OR OLD.state IN ('COMPLETED','UNKNOWN') OR NEW.state NOT IN ('COMPLETED','UNKNOWN') THEN
        RAISE EXCEPTION 'maintenance receipt identity and final observation are immutable';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_maintenance_receipt_immutable BEFORE UPDATE OR DELETE
    ON trading_maintenance_operations FOR EACH ROW EXECUTE FUNCTION protect_trading_maintenance_receipt();
