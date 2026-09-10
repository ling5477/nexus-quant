-- 一次性决定不使用 lease/epoch；缺失历史行按 uncertain 处理，不能补成 NOT_ARMED。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE TABLE ordinary_place_authorities (
    order_id VARCHAR(64) PRIMARY KEY REFERENCES orders(order_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    state VARCHAR(24) NOT NULL CHECK (state IN ('NOT_ARMED','MAY_HAVE_ESCAPED','REVOKED_BEFORE_SEND')),
    decided_at TIMESTAMPTZ,
    CONSTRAINT ck_ordinary_place_decision_time CHECK (
        (state='NOT_ARMED' AND decided_at IS NULL)
        OR (state IN ('MAY_HAVE_ESCAPED','REVOKED_BEFORE_SEND') AND decided_at IS NOT NULL)
    )
);
COMMENT ON TABLE ordinary_place_authorities IS '与既有订单绑定的 ordinary PLACE 一次性决定，不是交易事实或可接管 lease';
COMMENT ON COLUMN ordinary_place_authorities.order_id IS '原订单主键，一单一决定，不产生新 logical identity';
COMMENT ON COLUMN ordinary_place_authorities.state IS '未获得发送资格、可能已发出或发送前已撤销；两个决定均不可逆';
COMMENT ON COLUMN ordinary_place_authorities.decided_at IS '数据库决定时间；历史 MAY 为分类时间，不是发送时间或 expiry';

-- 即使部署脚本误授表写权限，非表 owner 也不能绕过函数直接改决定。
CREATE FUNCTION nq_guard_ordinary_place_authority() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE','TRUNCATE') THEN
        RAISE EXCEPTION 'ordinary PLACE decision cannot be removed' USING ERRCODE='23514';
    END IF;
    IF current_user <> (SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid=TG_RELID) THEN
        RAISE EXCEPTION 'ordinary PLACE authority requires restricted function' USING ERRCODE='42501';
    END IF;
    IF TG_OP='UPDATE' AND (
        NEW.order_id IS DISTINCT FROM OLD.order_id OR OLD.state <> 'NOT_ARMED'
        OR NEW.state NOT IN ('MAY_HAVE_ESCAPED','REVOKED_BEFORE_SEND')
    ) THEN
        RAISE EXCEPTION 'ordinary PLACE decision is irreversible' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_ordinary_place_authority_guard BEFORE INSERT OR UPDATE OR DELETE
    ON ordinary_place_authorities FOR EACH ROW EXECUTE FUNCTION nq_guard_ordinary_place_authority();
CREATE TRIGGER trg_ordinary_place_authority_truncate BEFORE TRUNCATE
    ON ordinary_place_authorities FOR EACH STATEMENT EXECUTE FUNCTION nq_guard_ordinary_place_authority();

CREATE FUNCTION nq_guard_ordinary_order_binding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM ordinary_place_authorities WHERE order_id=OLD.order_id)
       AND ROW(NEW.order_id,NEW.account_id,NEW.client_order_id,NEW.venue,NEW.trade_env,
               NEW.symbol,NEW.side,NEW.type,NEW.price,NEW.qty)
           IS DISTINCT FROM ROW(OLD.order_id,OLD.account_id,OLD.client_order_id,OLD.venue,OLD.trade_env,
               OLD.symbol,OLD.side,OLD.type,OLD.price,OLD.qty) THEN
        RAISE EXCEPTION 'ordinary PLACE binding is immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_ordinary_order_binding BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION nq_guard_ordinary_order_binding();

-- 撤销必须与 canonical Order 终态在同一事务提交，不能留下已撤销却未完成的半事实。
CREATE FUNCTION nq_require_ordinary_no_order_finality() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.state='REVOKED_BEFORE_SEND' AND NOT EXISTS (
        SELECT 1 FROM orders WHERE order_id=NEW.order_id AND status='CANCELLED'
            AND reason='ORDER_NOT_FOUND/OKX_51603'
    ) THEN
        RAISE EXCEPTION 'ordinary revoke requires atomic no-order finality' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER trg_ordinary_no_order_finality AFTER INSERT OR UPDATE
    ON ordinary_place_authorities DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
    EXECUTE FUNCTION nq_require_ordinary_no_order_finality();

CREATE FUNCTION nq_create_ordinary_place_order(
    p_id text, p_account bigint, p_run text, p_venue text, p_symbol text, p_client text,
    p_side text, p_type text, p_price numeric, p_qty numeric, p_status text, p_reason text,
    p_trace text, p_env text, p_version bigint, p_time timestamptz
) RETURNS text LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    IF NOT has_table_privilege(session_user,'orders','INSERT')
        OR NOT has_table_privilege(session_user,'orders','UPDATE') THEN
        RAISE EXCEPTION 'ordinary writer privilege required' USING ERRCODE='42501';
    END IF;
    IF p_venue <> 'OKX' OR p_status <> 'NEW' OR p_version <> 0 THEN
        RAISE EXCEPTION 'ordinary authority requires a fresh OKX order' USING ERRCODE='23514';
    END IF;
    INSERT INTO orders(order_id,account_id,strategy_run_id,venue,symbol,client_order_id,side,type,
        price,qty,status,reason,trace_id,exchange_code,trade_env,version,created_at,updated_at)
    VALUES(p_id,p_account,p_run,p_venue,p_symbol,p_client,p_side,p_type,p_price,p_qty,
        p_status,p_reason,p_trace,p_venue,p_env,p_version,p_time,p_time);
    INSERT INTO ordinary_place_authorities(order_id,state) VALUES(p_id,'NOT_ARMED');
    RETURN p_id;
END $$;

CREATE FUNCTION nq_decide_ordinary_place(p_id text,p_status text,p_version bigint,p_send boolean)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE k text; o orders%ROWTYPE; changed integer;
BEGIN
    IF NOT has_table_privilege(session_user,'orders','INSERT')
        OR NOT has_table_privilege(session_user,'orders','UPDATE') THEN
        RAISE EXCEPTION 'ordinary writer privilege required' USING ERRCODE='42501';
    END IF;
    IF p_send IS NULL THEN RAISE EXCEPTION 'send decision is required' USING ERRCODE='23514'; END IF;
    -- Kill→Order→authority；recovery 从 Order 开始，不反向取得 Kill 锁。
    IF p_send THEN
        SELECT status INTO k FROM kill_switch_states WHERE scope='GLOBAL_TRADING' FOR UPDATE;
        IF k IS DISTINCT FROM 'DISENGAGED' THEN RETURN false; END IF;
    END IF;
    SELECT * INTO o FROM orders WHERE order_id=p_id FOR UPDATE;
    IF NOT FOUND OR o.venue <> 'OKX' OR o.status IS DISTINCT FROM p_status
        OR o.version IS DISTINCT FROM p_version THEN RETURN false; END IF;
    IF p_send AND o.status <> 'SENT' THEN RETURN false; END IF;
    -- SENT 之外仍核对同一 prepare 留下的风险事实；helper 不是绕过 RiskGate 的新入口。
    IF p_send AND NOT EXISTS (SELECT 1 FROM risk_events WHERE scope='ORDER' AND scope_id=o.order_id
        AND trace_id=o.trace_id AND decision='ALLOW') THEN RETURN false; END IF;
    IF NOT p_send AND o.status NOT IN ('SENT','ACCEPTED','PARTIALLY_FILLED','CANCEL_REQUESTED','CANCEL_REJECTED')
        THEN RETURN false; END IF;
    UPDATE ordinary_place_authorities SET state=CASE WHEN p_send THEN 'MAY_HAVE_ESCAPED'
        ELSE 'REVOKED_BEFORE_SEND' END, decided_at=clock_timestamp()
        WHERE order_id=p_id AND state='NOT_ARMED';
    GET DIAGNOSTICS changed = ROW_COUNT;
    RETURN changed=1;
END $$;

-- 不在 Flyway 的一个长事务中扫描历史全表；受控运维可重复调用，每次最多500行。
CREATE FUNCTION nq_backfill_ordinary_place_authorities(p_limit integer) RETURNS integer LANGUAGE plpgsql AS $$
DECLARE changed integer;
BEGIN
    IF p_limit IS NULL OR p_limit < 1 OR p_limit > 500 THEN
        RAISE EXCEPTION 'backfill limit must be 1..500' USING ERRCODE='22023';
    END IF;
    INSERT INTO ordinary_place_authorities(order_id,state,decided_at)
    SELECT o.order_id,'MAY_HAVE_ESCAPED',clock_timestamp() FROM orders o
    WHERE o.venue='OKX' AND NOT EXISTS(SELECT 1 FROM ordinary_place_authorities a WHERE a.order_id=o.order_id)
    ORDER BY o.order_id LIMIT p_limit ON CONFLICT DO NOTHING;
    GET DIAGNOSTICS changed = ROW_COUNT;
    RETURN changed;
END $$;
REVOKE ALL ON ordinary_place_authorities FROM PUBLIC;
REVOKE ALL ON FUNCTION nq_backfill_ordinary_place_authorities(integer) FROM PUBLIC;

-- 安全函数绑定迁移的实际 schema；pg_temp 最后，拒绝调用方 search_path/temp 表劫持。
DO $$
DECLARE s text := current_schema(); f record;
BEGIN
    FOR f IN SELECT p.oid::regprocedure AS signature FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
        WHERE n.nspname=s AND p.proname IN ('nq_guard_ordinary_place_authority','nq_guard_ordinary_order_binding',
            'nq_require_ordinary_no_order_finality','nq_create_ordinary_place_order',
            'nq_decide_ordinary_place','nq_backfill_ordinary_place_authorities')
    LOOP
        EXECUTE format('ALTER FUNCTION %s SET search_path TO pg_catalog, %I, pg_temp',f.signature,s);
        EXECUTE format('ALTER FUNCTION %s SET lock_timeout TO %L',f.signature,'5s');
    END LOOP;
END $$;
