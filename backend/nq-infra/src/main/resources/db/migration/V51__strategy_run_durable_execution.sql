-- 同 run 的工作、订单血缘与恢复进度；不改变 V49 的一次性发送协议。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

-- 历史多单必须使迁移整体失败，不能删单或解绑来伪造一对一。
CREATE UNIQUE INDEX uq_orders_strategy_run ON orders(strategy_run_id) WHERE strategy_run_id IS NOT NULL;

CREATE TABLE strategy_run_dispatch_work (
    strategy_run_id VARCHAR(64) PRIMARY KEY REFERENCES strategy_runs(strategy_run_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    work_schema_version SMALLINT NOT NULL CHECK (work_schema_version=1),
    definition_version INTEGER NOT NULL CHECK (definition_version>0),
    account_id BIGINT NOT NULL REFERENCES accounts(account_id),
    client_order_id VARCHAR(128) NOT NULL CHECK (btrim(client_order_id)<>''),
    symbol VARCHAR(64) NOT NULL CHECK (btrim(symbol)<>''),
    side VARCHAR(16) NOT NULL CHECK (side IN ('BUY','SELL')),
    order_type VARCHAR(16) NOT NULL CHECK (order_type IN ('MARKET','LIMIT')),
    quantity NUMERIC(38,8) NOT NULL CHECK (quantity>0 AND quantity<'NaN'::numeric),
    price NUMERIC(38,8) CHECK (price<'NaN'::numeric),
    time_in_force VARCHAR(16) NOT NULL,
    effective_quantity NUMERIC(38,8),
    effective_price NUMERIC(38,8),
    normalization_rejection VARCHAR(128),
    CONSTRAINT ck_strategy_effective_parameters CHECK (
        (effective_quantity IS NULL AND effective_price IS NULL AND normalization_rejection IS NULL)
        OR (normalization_rejection ~ '^[A-Z0-9_]{1,128}$' AND effective_quantity IS NULL AND effective_price IS NULL)
        OR (normalization_rejection IS NULL AND effective_quantity IS NOT NULL
            AND effective_quantity>0 AND effective_quantity<=quantity AND effective_quantity<'NaN'::numeric
            AND ((price IS NULL AND effective_price IS NULL)
                OR (price IS NOT NULL AND effective_price IS NOT NULL AND effective_price>0 AND effective_price<=price)))),
    CONSTRAINT uq_strategy_work_client UNIQUE(account_id,client_order_id),
    CONSTRAINT ck_strategy_work_tif CHECK ((order_type='MARKET' AND time_in_force='IOC')
        OR (order_type='LIMIT' AND time_in_force='GTC')),
    CONSTRAINT ck_strategy_work_limit_price CHECK (order_type<>'LIMIT' OR (price IS NOT NULL AND price>0))
);
COMMENT ON TABLE strategy_run_dispatch_work IS '原 run 的不可变请求意图与一次冻结有效执行参数；不得从最新配置猜测恢复请求';
COMMENT ON COLUMN strategy_run_dispatch_work.work_schema_version IS '指令解释版本；不是 owner、lease 或 generation';
COMMENT ON COLUMN strategy_run_dispatch_work.definition_version IS '产生本次定义快照的版本，只用于血缘，不用于重读当前定义';
COMMENT ON COLUMN strategy_run_dispatch_work.account_id IS '仅为账户与 client 唯一键保留的副本，必须等于原 run 的账户';
COMMENT ON COLUMN strategy_run_dispatch_work.time_in_force IS '首次有效请求的显式 TIF，恢复不得重新采用可变默认值';
COMMENT ON COLUMN strategy_run_dispatch_work.quantity IS '原始 requested 数量，保留策略意图；不能作为规范化后订单的终态比较值';
COMMENT ON COLUMN strategy_run_dispatch_work.effective_quantity IS '事务 B 一次冻结的实际提交数量，与 canonical Order.qty 相等；绑定前可为空';
COMMENT ON COLUMN strategy_run_dispatch_work.effective_price IS '与 effective_quantity 一起冻结的实际提交价格；不在 adapter 外复制取整规则';
COMMENT ON COLUMN strategy_run_dispatch_work.normalization_rejection IS '确定无效的规范化结果，与原 run 的 FAILED 同事务提交，不产生 Order/发送许可';

CREATE TABLE ordinary_order_cancel_finality (
    order_id VARCHAR(64) PRIMARY KEY REFERENCES orders(order_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    observed_order_version BIGINT NOT NULL CHECK (observed_order_version>=0),
    executed_quantity NUMERIC(38,8) NOT NULL CHECK (executed_quantity>=0)
);
COMMENT ON TABLE ordinary_order_cancel_finality IS '已核对最终取消查询及完整累计成交量的 Order 事实；取消请求接受不是此证明';
COMMENT ON COLUMN ordinary_order_cancel_finality.observed_order_version IS '查询开始前的 Order 版本，迟到响应不得重贴新版本';
COMMENT ON COLUMN ordinary_order_cancel_finality.executed_quantity IS '最终取消累计成交量，提交时必须与唯一 durable fills 相等';

CREATE TABLE strategy_run_recovery_scan_cursor (
    cursor_id SMALLINT PRIMARY KEY CHECK (cursor_id=1),
    last_started_at TIMESTAMPTZ,
    last_run_id VARCHAR(64),
    CHECK ((last_started_at IS NULL)=(last_run_id IS NULL))
);
INSERT INTO strategy_run_recovery_scan_cursor(cursor_id) VALUES(1);
COMMENT ON TABLE strategy_run_recovery_scan_cursor IS '有界循环检查位置，不代表执行占有或外部 mutation 权限';
CREATE INDEX idx_strategy_run_recovery_scan ON strategy_runs(started_at,strategy_run_id)
    WHERE status IN ('CREATED','DISPATCHING','RUNNING');
CREATE INDEX idx_strategy_admitted_schedule_due ON strategy_runs(admission_schedule_id,admission_due_at DESC)
    WHERE admission_schedule_id IS NOT NULL;

CREATE FUNCTION nq_guard_strategy_work_v51() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r strategy_runs%ROWTYPE;
BEGIN
    IF TG_OP NOT IN ('INSERT','UPDATE') THEN RAISE EXCEPTION 'strategy work is immutable' USING ERRCODE='23514'; END IF;
    IF current_user<>(SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid=TG_RELID) THEN
        RAISE EXCEPTION 'strategy work requires atomic admission function' USING ERRCODE='42501';
    END IF;
    SELECT * INTO STRICT r FROM strategy_runs WHERE strategy_run_id=NEW.strategy_run_id;
    IF TG_OP='UPDATE' THEN
        IF ROW(NEW.strategy_run_id,NEW.work_schema_version,NEW.definition_version,NEW.account_id,NEW.client_order_id,
                NEW.symbol,NEW.side,NEW.order_type,NEW.quantity,NEW.price,NEW.time_in_force)
            IS DISTINCT FROM ROW(OLD.strategy_run_id,OLD.work_schema_version,OLD.definition_version,OLD.account_id,OLD.client_order_id,
                OLD.symbol,OLD.side,OLD.order_type,OLD.quantity,OLD.price,OLD.time_in_force)
            OR OLD.effective_quantity IS NOT NULL OR OLD.normalization_rejection IS NOT NULL
            OR (NEW.effective_quantity IS NULL AND NEW.normalization_rejection IS NULL) OR r.status<>'CREATED' THEN
            RAISE EXCEPTION 'requested work and decided effective parameters are immutable' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.effective_quantity IS NOT NULL OR NEW.effective_price IS NOT NULL OR NEW.normalization_rejection IS NOT NULL THEN
        RAISE EXCEPTION 'effective parameters require atomic prepare' USING ERRCODE='23514';
    END IF;
    IF NEW.account_id<>r.account_id OR NEW.client_order_id IS DISTINCT FROM 'coid-'||r.request_id THEN
        RAISE EXCEPTION 'strategy work identity mismatch' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_strategy_work_v51 BEFORE INSERT OR UPDATE OR DELETE ON strategy_run_dispatch_work
    FOR EACH ROW EXECUTE FUNCTION nq_guard_strategy_work_v51();
CREATE TRIGGER trg_strategy_work_truncate_v51 BEFORE TRUNCATE ON strategy_run_dispatch_work
    FOR EACH STATEMENT EXECUTE FUNCTION nq_guard_strategy_work_v51();

CREATE FUNCTION nq_guard_strategy_run_v51() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE','TRUNCATE') THEN
        RAISE EXCEPTION 'strategy run identity cannot be removed' USING ERRCODE='23514';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.status<>'CREATED' OR NEW.finished_at IS NOT NULL THEN
            RAISE EXCEPTION 'new strategy run must start CREATED' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF ROW(NEW.strategy_run_id,NEW.strategy_id,NEW.account_id,NEW.exchange_code,NEW.trade_env,
            NEW.trigger_type,NEW.request_id,NEW.config_snapshot,NEW.started_at,NEW.trace_id,
            NEW.admission_schedule_id,NEW.admission_due_at)
        IS DISTINCT FROM ROW(OLD.strategy_run_id,OLD.strategy_id,OLD.account_id,OLD.exchange_code,OLD.trade_env,
            OLD.trigger_type,OLD.request_id,OLD.config_snapshot,OLD.started_at,OLD.trace_id,
            OLD.admission_schedule_id,OLD.admission_due_at) THEN
        RAISE EXCEPTION 'strategy execution identity is immutable' USING ERRCODE='23514';
    END IF;
    IF ROW(NEW.status,NEW.finished_at,NEW.error_message) IS DISTINCT FROM ROW(OLD.status,OLD.finished_at,OLD.error_message) THEN
        IF current_user<>(SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid=TG_RELID)
            OR OLD.status NOT IN ('CREATED','DISPATCHING','RUNNING')
            OR NOT ((OLD.status='CREATED' AND NEW.status='DISPATCHING')
                OR (OLD.status='DISPATCHING' AND NEW.status='RUNNING')
                OR NEW.status IN ('SUCCEEDED','FAILED'))
            OR ((NEW.status IN ('SUCCEEDED','FAILED'))<>(NEW.finished_at IS NOT NULL)) THEN
            RAISE EXCEPTION 'invalid strategy lifecycle transition' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_strategy_run_v51 BEFORE INSERT OR UPDATE OR DELETE ON strategy_runs
    FOR EACH ROW EXECUTE FUNCTION nq_guard_strategy_run_v51();
CREATE TRIGGER trg_strategy_run_truncate_v51 BEFORE TRUNCATE ON strategy_runs
    FOR EACH STATEMENT EXECUTE FUNCTION nq_guard_strategy_run_v51();

CREATE FUNCTION nq_require_strategy_work_v51() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM strategy_run_dispatch_work WHERE strategy_run_id=NEW.strategy_run_id) THEN
        RAISE EXCEPTION 'new strategy run requires atomic immutable work' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER trg_require_strategy_work_v51 AFTER INSERT ON strategy_runs
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nq_require_strategy_work_v51();

CREATE FUNCTION nq_guard_strategy_order_v51() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r strategy_runs%ROWTYPE; w strategy_run_dispatch_work%ROWTYPE;
BEGIN
    IF TG_OP='TRUNCATE' THEN
        RAISE EXCEPTION 'order identities cannot be truncated' USING ERRCODE='23514';
    END IF;
    IF TG_OP='DELETE' THEN
        IF OLD.strategy_run_id IS NOT NULL THEN
            RAISE EXCEPTION 'bound strategy order cannot be deleted' USING ERRCODE='23514';
        END IF;
        RETURN OLD;
    END IF;
    IF TG_OP='UPDATE' THEN
        -- 状态写已经持有 Order 锁；这里只校验不变量，不反向获取 run 锁。
        IF NEW.strategy_run_id IS DISTINCT FROM OLD.strategy_run_id
            OR (OLD.strategy_run_id IS NOT NULL AND (
                ROW(NEW.order_id,NEW.account_id,NEW.client_order_id,NEW.venue,NEW.exchange_code,NEW.trade_env,
                    NEW.symbol,NEW.side,NEW.type,NEW.price,NEW.qty)
                IS DISTINCT FROM ROW(OLD.order_id,OLD.account_id,OLD.client_order_id,OLD.venue,OLD.exchange_code,OLD.trade_env,
                    OLD.symbol,OLD.side,OLD.type,OLD.price,OLD.qty)
                OR (NULLIF(btrim(OLD.external_order_id),'') IS NOT NULL
                    AND NEW.external_order_id IS DISTINCT FROM OLD.external_order_id))) THEN
            RAISE EXCEPTION 'strategy order binding is immutable' USING ERRCODE='23514';
        END IF;
        IF OLD.strategy_run_id IS NULL AND ROW(NEW.account_id,NEW.client_order_id) IS DISTINCT FROM ROW(OLD.account_id,OLD.client_order_id) THEN
            PERFORM pg_advisory_xact_lock(hashtextextended(NEW.account_id::text||':'||NEW.client_order_id,51));
            IF EXISTS(SELECT 1 FROM strategy_run_dispatch_work WHERE account_id=NEW.account_id AND client_order_id=NEW.client_order_id) THEN
                RAISE EXCEPTION 'client reserved by immutable strategy work' USING ERRCODE='23514';
            END IF;
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.strategy_run_id IS NULL THEN
        -- 与 admission 串行化同账户/client 的保留，不能抢占已接纳但尚未建单的身份。
        PERFORM pg_advisory_xact_lock(hashtextextended(NEW.account_id::text||':'||NEW.client_order_id,51));
        IF EXISTS(SELECT 1 FROM strategy_run_dispatch_work WHERE account_id=NEW.account_id AND client_order_id=NEW.client_order_id) THEN
            RAISE EXCEPTION 'client reserved by immutable strategy work' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO STRICT r FROM strategy_runs WHERE strategy_run_id=NEW.strategy_run_id FOR NO KEY UPDATE;
    SELECT * INTO STRICT w FROM strategy_run_dispatch_work WHERE strategy_run_id=r.strategy_run_id;
    IF r.status<>'DISPATCHING' OR NEW.exchange_code IS DISTINCT FROM NEW.venue
        OR ROW(NEW.account_id,NEW.venue,NEW.trade_env,NEW.client_order_id,
            NEW.symbol,NEW.side,NEW.type,NEW.price,NEW.qty)
        IS DISTINCT FROM ROW(r.account_id,upper(btrim(r.exchange_code)),r.trade_env,w.client_order_id,
            w.symbol,w.side,w.order_type,w.effective_price,w.effective_quantity)
        OR w.effective_quantity IS NULL OR w.normalization_rejection IS NOT NULL THEN
        RAISE EXCEPTION 'strategy order differs from immutable work' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_strategy_order_v51 BEFORE INSERT OR UPDATE OR DELETE ON orders
    FOR EACH ROW EXECUTE FUNCTION nq_guard_strategy_order_v51();
CREATE TRIGGER trg_strategy_order_truncate_v51 BEFORE TRUNCATE ON orders
    FOR EACH STATEMENT EXECUTE FUNCTION nq_guard_strategy_order_v51();

CREATE FUNCTION nq_require_strategy_prepare_v51() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE rid text; r strategy_runs%ROWTYPE; o orders%ROWTYPE; w strategy_run_dispatch_work%ROWTYPE;
BEGIN
    rid:=NEW.strategy_run_id;
    IF rid IS NULL OR NOT EXISTS(SELECT 1 FROM strategy_run_dispatch_work WHERE strategy_run_id=rid) THEN RETURN NEW; END IF;
    SELECT * INTO STRICT r FROM strategy_runs WHERE strategy_run_id=rid;
    SELECT * INTO o FROM orders WHERE strategy_run_id=rid;
    SELECT * INTO STRICT w FROM strategy_run_dispatch_work WHERE strategy_run_id=rid;
    IF w.normalization_rejection IS NOT NULL THEN
        IF r.status<>'FAILED' OR o.order_id IS NOT NULL THEN
            RAISE EXCEPTION 'normalization rejection requires atomic run finality without Order' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF (r.status='CREATED' AND o.order_id IS NOT NULL)
        OR (w.effective_quantity IS NOT NULL AND o.order_id IS NULL)
        OR (r.status<>'CREATED' AND o.order_id IS NULL)
        OR (TG_TABLE_NAME='orders' AND o.venue='OKX' AND NOT EXISTS(
            SELECT 1 FROM ordinary_place_authorities WHERE order_id=o.order_id AND state='NOT_ARMED')) THEN
        RAISE EXCEPTION 'strategy prepare facts must commit together' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER trg_strategy_prepare_run_v51 AFTER UPDATE ON strategy_runs
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nq_require_strategy_prepare_v51();
CREATE CONSTRAINT TRIGGER trg_strategy_prepare_order_v51 AFTER INSERT ON orders
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nq_require_strategy_prepare_v51();
CREATE CONSTRAINT TRIGGER trg_strategy_effective_prepare_v51 AFTER UPDATE ON strategy_run_dispatch_work
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nq_require_strategy_prepare_v51();

CREATE FUNCTION nq_guard_strategy_cursor_v51() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.last_triggered_at IS DISTINCT FROM OLD.last_triggered_at THEN
        IF current_user<>(SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid=TG_RELID)
            OR NEW.last_triggered_at IS NULL
            OR (OLD.last_triggered_at IS NOT NULL AND NEW.last_triggered_at<=OLD.last_triggered_at)
            OR NOT EXISTS(SELECT 1 FROM strategy_runs r WHERE r.strategy_id=NEW.strategy_id
                AND r.account_id=NEW.account_id AND ((r.admission_schedule_id=NEW.schedule_job_id
                AND r.admission_due_at=NEW.last_triggered_at) OR (r.admission_schedule_id IS NULL AND r.request_id IN (
                    'req-schedule-'||NEW.schedule_job_id||'-window-'||(extract(epoch FROM NEW.last_triggered_at)*1000)::bigint::text,
                    'req-schedule-'||NEW.schedule_job_id||'-request-'||(extract(epoch FROM NEW.last_triggered_at)*1000)::bigint::text,
                    'req-schedule-'||NEW.schedule_job_id||'-strategy-'||NEW.strategy_id||'-'||(extract(epoch FROM NEW.last_triggered_at)*1000)::bigint::text)))) THEN
            RAISE EXCEPTION 'cursor requires monotonic atomic admission' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_strategy_cursor_v51 BEFORE UPDATE ON strategy_schedules
    FOR EACH ROW EXECUTE FUNCTION nq_guard_strategy_cursor_v51();
COMMENT ON COLUMN strategy_schedules.last_triggered_at IS '新写入为原子 admission 的 dueAt 消费水位；旧 scan-now 值仅作消费下界';

CREATE FUNCTION nq_begin_strategy_dispatch(p_run text) RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE r strategy_runs%ROWTYPE;
BEGIN
    SELECT * INTO STRICT r FROM strategy_runs WHERE strategy_run_id=p_run FOR NO KEY UPDATE;
    IF NOT has_table_privilege(session_user,'orders','INSERT') THEN
        RAISE EXCEPTION 'ordinary writer privilege required' USING ERRCODE='42501';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM strategy_run_dispatch_work WHERE strategy_run_id=p_run) THEN RETURN false; END IF;
    IF r.status='CREATED' THEN
        UPDATE strategy_runs SET status='DISPATCHING' WHERE strategy_run_id=p_run AND status='CREATED';
        RETURN true;
    END IF;
    RETURN r.status='DISPATCHING';
END $$;

-- 本迁移中的受限入口在文件末尾统一固定 search_path，拒绝调用方临时对象替身。
CREATE FUNCTION nq_admit_strategy_work(
    p_run text,p_strategy text,p_account bigint,p_venue text,p_env text,p_trigger text,p_config jsonb,
    p_request text,p_started timestamptz,p_trace text,p_schedule text,p_due timestamptz,
    p_definition_version integer,p_client text,p_symbol text,p_side text,p_type text,
    p_quantity numeric,p_price numeric,p_tif text,p_expected_cursor timestamptz
) RETURNS text LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE d strategy_definitions%ROWTYPE; s strategy_schedules%ROWTYPE; existing text;
BEGIN
    IF NOT has_table_privilege(session_user,'strategy_runs','INSERT')
        OR NOT has_table_privilege(session_user,'strategy_runs','UPDATE') THEN
        RAISE EXCEPTION 'strategy writer privilege required' USING ERRCODE='42501';
    END IF;
    SELECT * INTO STRICT d FROM strategy_definitions WHERE strategy_id=p_strategy FOR UPDATE;
    IF p_schedule IS NOT NULL THEN
        SELECT * INTO STRICT s FROM strategy_schedules WHERE schedule_job_id=p_schedule FOR UPDATE;
        SELECT strategy_run_id INTO existing FROM strategy_runs WHERE strategy_id=p_strategy AND account_id=p_account
            AND admission_schedule_id=p_schedule AND admission_due_at=p_due;
        IF existing IS NOT NULL THEN RETURN existing; END IF;
        IF s.strategy_id<>p_strategy OR s.account_id<>p_account OR NOT s.enabled
            OR upper(btrim(s.exchange_code))<>p_venue OR s.trade_env<>p_env
            OR s.last_triggered_at IS DISTINCT FROM p_expected_cursor OR p_due IS NULL
            OR p_due<>date_trunc('second',p_due) OR p_due>p_started
            OR (s.last_triggered_at IS NOT NULL AND p_due<=s.last_triggered_at)
            OR p_trigger<>'SCHEDULER' THEN
            RAISE EXCEPTION 'stale or invalid strategy schedule admission' USING ERRCODE='23514';
        END IF;
        SELECT strategy_run_id INTO existing FROM strategy_runs WHERE strategy_id=p_strategy AND account_id=p_account
            AND admission_schedule_id IS NULL AND request_id IN (
                'req-schedule-'||p_schedule||'-window-'||(extract(epoch FROM p_due)*1000)::bigint::text,
                'req-schedule-'||p_schedule||'-request-'||(extract(epoch FROM p_due)*1000)::bigint::text,
                'req-schedule-'||p_schedule||'-strategy-'||p_strategy||'-'||(extract(epoch FROM p_due)*1000)::bigint::text)
            ORDER BY started_at,strategy_run_id LIMIT 1;
        IF existing IS NOT NULL THEN
            UPDATE strategy_schedules SET last_triggered_at=p_due,updated_at=CURRENT_TIMESTAMP WHERE schedule_job_id=p_schedule;
            INSERT INTO audit_logs(domain,action,actor_id,trace_id,detail_json) VALUES('STRATEGY','LEGACY_WINDOW_CONSUMPTION_CONFIRMED',
                existing,p_trace,jsonb_build_object('schedule_id',p_schedule,'due_at',p_due));
            RETURN existing;
        END IF;
    ELSIF p_due IS NOT NULL OR p_trigger<>'MANUAL' THEN
        RAISE EXCEPTION 'invalid manual admission identity' USING ERRCODE='23514';
    END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended(p_account::text||':'||p_client,51));
    SELECT strategy_run_id INTO existing FROM strategy_run_dispatch_work WHERE account_id=p_account AND client_order_id=p_client;
    IF existing IS NOT NULL THEN
        IF NOT EXISTS(SELECT 1 FROM strategy_runs r JOIN strategy_run_dispatch_work w USING(strategy_run_id)
            WHERE r.strategy_run_id=existing AND r.strategy_id=p_strategy AND r.account_id=p_account
            AND r.exchange_code=p_venue AND r.trade_env=p_env AND r.request_id=p_request
            AND r.admission_schedule_id IS NOT DISTINCT FROM p_schedule AND r.admission_due_at IS NOT DISTINCT FROM p_due
            AND ROW(w.symbol,w.side,w.order_type,w.quantity,w.price,w.time_in_force)
                IS NOT DISTINCT FROM ROW(p_symbol,p_side,p_type,p_quantity,p_price,p_tif)) THEN
            RAISE EXCEPTION 'strategy idempotency conflict' USING ERRCODE='23514';
        END IF;
        RETURN existing;
    END IF;
    IF NOT d.enabled OR ROW(d.account_id,upper(btrim(d.exchange_code)),d.trade_env,d.version,d.config_snapshot)
        IS DISTINCT FROM ROW(p_account,p_venue,p_env,p_definition_version,p_config)
        OR p_request IS NULL OR btrim(p_request)='' OR p_client IS DISTINCT FROM 'coid-'||p_request
        OR p_quantity<>round(p_quantity,8) OR (p_price IS NOT NULL AND p_price<>round(p_price,8)) THEN
        RAISE EXCEPTION 'stale definition or invalid immutable work' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM strategy_runs WHERE strategy_id=p_strategy AND status IN ('CREATED','DISPATCHING','RUNNING')) THEN
        RAISE EXCEPTION 'strategy_run_active' USING ERRCODE='55000';
    END IF;
    IF EXISTS(SELECT 1 FROM orders WHERE account_id=p_account AND client_order_id=p_client) THEN
        RAISE EXCEPTION 'strategy client belongs to an existing order' USING ERRCODE='23514';
    END IF;
    INSERT INTO strategy_runs(strategy_run_id,strategy_id,account_id,status,trigger_type,exchange_code,trade_env,
        config_snapshot,request_id,started_at,trace_id,admission_schedule_id,admission_due_at)
        VALUES(p_run,p_strategy,p_account,'CREATED',p_trigger,p_venue,p_env,p_config,p_request,p_started,p_trace,p_schedule,p_due);
    INSERT INTO strategy_run_dispatch_work(strategy_run_id,work_schema_version,definition_version,account_id,client_order_id,
        symbol,side,order_type,quantity,price,time_in_force) VALUES(p_run,1,p_definition_version,p_account,p_client,p_symbol,p_side,p_type,
        p_quantity,p_price,p_tif);
    IF p_schedule IS NOT NULL THEN
        UPDATE strategy_schedules SET last_triggered_at=p_due,updated_at=CURRENT_TIMESTAMP WHERE schedule_job_id=p_schedule;
    END IF;
    RETURN p_run;
END $$;

CREATE FUNCTION nq_strategy_execution_proof(p_order text)
RETURNS TABLE(executed_quantity numeric,fill_count bigint,valid boolean) LANGUAGE sql STABLE AS $$
    SELECT COALESCE(sum(t.qty),0),count(t.trade_id),
        o.qty>0 AND o.qty<'NaN'::numeric AND NULLIF(btrim(o.external_order_id),'') IS NOT NULL
        AND count(t.trade_id)=count(DISTINCT (t.exchange,t.exchange_trade_id)) FILTER(WHERE t.trade_id IS NOT NULL)
        AND count(*) FILTER(WHERE t.trade_id IS NOT NULL AND (
            t.account_id IS DISTINCT FROM o.account_id OR t.symbol IS DISTINCT FROM o.symbol
            OR t.exchange IS DISTINCT FROM o.venue OR t.trade_env IS DISTINCT FROM o.trade_env
            OR t.strategy_run_id IS DISTINCT FROM o.strategy_run_id
            OR t.external_order_id IS DISTINCT FROM o.external_order_id
            OR t.exchange_trade_id IS NULL OR btrim(t.exchange_trade_id)='' OR t.qty IS NULL OR t.qty<=0))=0
    FROM orders o LEFT JOIN trades t ON t.order_id=o.order_id WHERE o.order_id=p_order GROUP BY o.order_id
$$;

-- run 身份不可改；NO KEY UPDATE 保持 writer 互斥，同时允许持有 Order 锁的 ACK/Trade 事务检查 run 外键。
CREATE FUNCTION nq_project_strategy_run(p_run text) RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE r strategy_runs%ROWTYPE; o orders%ROWTYPE; a text; executed numeric; fills bigint; proof_valid boolean;
    target text; explanation text;
BEGIN
    IF NOT has_table_privilege(session_user,'strategy_runs','UPDATE') THEN
        RAISE EXCEPTION 'strategy writer privilege required' USING ERRCODE='42501';
    END IF;
    SELECT * INTO STRICT r FROM strategy_runs WHERE strategy_run_id=p_run FOR NO KEY UPDATE;
    IF r.status NOT IN ('CREATED','DISPATCHING','RUNNING') THEN RETURN false; END IF;
    SELECT * INTO o FROM orders WHERE strategy_run_id=p_run FOR UPDATE;
    IF o.order_id IS NULL THEN
        SELECT normalization_rejection INTO explanation FROM strategy_run_dispatch_work WHERE strategy_run_id=p_run;
        IF explanation IS NULL THEN RETURN false; END IF;
        target:='FAILED'; explanation:='EXECUTION_NORMALIZATION_REJECTED/'||explanation;
    ELSE
    IF ROW(o.account_id,o.venue,o.trade_env)
        IS DISTINCT FROM ROW(r.account_id,upper(btrim(r.exchange_code)),r.trade_env) THEN RETURN false; END IF;
    SELECT state INTO a FROM ordinary_place_authorities WHERE order_id=o.order_id;
    SELECT * INTO executed,fills,proof_valid FROM nq_strategy_execution_proof(o.order_id);
    IF o.status='FILLED' AND proof_valid AND executed=o.qty THEN
        target:='SUCCEEDED'; explanation:=NULL;
    ELSIF o.status='CANCELLED' AND a='REVOKED_BEFORE_SEND'
        AND o.reason='ORDER_NOT_FOUND/OKX_51603' AND fills=0 THEN
        target:='FAILED'; explanation:='order_status=CANCELLED; no_send=REVOKED_BEFORE_SEND';
    ELSIF o.status='RISK_REJECTED' AND fills=0 AND (a='NOT_ARMED' OR o.venue<>'OKX')
        AND EXISTS(SELECT 1 FROM risk_events WHERE scope='ORDER' AND scope_id=o.order_id AND decision='REJECT') THEN
        target:='FAILED'; explanation:='order_status=RISK_REJECTED; reason='||COALESCE(o.reason,'');
    ELSIF o.status='REJECTED' AND fills=0 THEN
        target:='FAILED'; explanation:='order_status=REJECTED; reason='||COALESCE(o.reason,'');
    ELSIF o.status='CANCELLED' AND proof_valid AND executed<o.qty AND EXISTS(
        SELECT 1 FROM ordinary_order_cancel_finality f WHERE f.order_id=o.order_id AND f.executed_quantity=executed
            AND f.observed_order_version=o.version) THEN
        target:='FAILED'; explanation:='order_status=CANCELLED; executed_quantity='||executed::text;
    ELSIF r.status='DISPATCHING' AND o.status IN ('SENT','ACCEPTED','PARTIALLY_FILLED','CANCEL_REQUESTED','CANCEL_REJECTED') THEN
        target:='RUNNING'; explanation:=NULL;
    ELSE RETURN false;
    END IF;
    END IF;
    UPDATE strategy_runs SET status=target,finished_at=CASE WHEN target IN ('SUCCEEDED','FAILED') THEN clock_timestamp() ELSE NULL END,
        error_message=explanation WHERE strategy_run_id=p_run AND status=r.status;
    IF NOT FOUND THEN RETURN false; END IF;
    INSERT INTO audit_logs(domain,action,actor_id,trace_id,detail_json)
        VALUES('STRATEGY','STRATEGY_RUN_DURABLE_PROGRESSION',p_run,r.trace_id,
            jsonb_build_object('from',r.status,'to',target,'order_id',o.order_id,'order_version',o.version,
                'executed_quantity',executed,'reason',explanation));
    RETURN true;
END $$;

-- 只绑定 canonical adapter 已计算的结果，SQL 不实现第二份舍入算法。
CREATE FUNCTION nq_bind_strategy_effective(p_run text,p_qty numeric,p_price numeric,p_rejection text)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE r strategy_runs%ROWTYPE; w strategy_run_dispatch_work%ROWTYPE;
BEGIN
    IF NOT has_table_privilege(session_user,'orders','INSERT')
        OR NOT has_table_privilege(session_user,'strategy_runs','UPDATE') THEN
        RAISE EXCEPTION 'strategy preparation privilege required' USING ERRCODE='42501';
    END IF;
    SELECT * INTO STRICT r FROM strategy_runs WHERE strategy_run_id=p_run FOR NO KEY UPDATE;
    SELECT * INTO STRICT w FROM strategy_run_dispatch_work WHERE strategy_run_id=p_run;
    IF w.effective_quantity IS NOT NULL OR w.normalization_rejection IS NOT NULL THEN RETURN false; END IF;
    IF r.status<>'CREATED' OR (p_qty IS NULL AND p_rejection IS NULL)
        OR (p_qty IS NOT NULL AND p_qty<>round(p_qty,8)) OR (p_price IS NOT NULL AND p_price<>round(p_price,8)) THEN
        RAISE EXCEPTION 'invalid effective execution decision' USING ERRCODE='23514';
    END IF;
    UPDATE strategy_run_dispatch_work SET effective_quantity=p_qty,effective_price=p_price,normalization_rejection=p_rejection
        WHERE strategy_run_id=p_run;
    INSERT INTO audit_logs(domain,action,actor_id,trace_id,detail_json) VALUES('STRATEGY','STRATEGY_EFFECTIVE_EXECUTION_BOUND',p_run,r.trace_id,
        jsonb_build_object('requested_quantity',w.quantity,'effective_quantity',p_qty,'quantity_delta',w.quantity-p_qty,
            'requested_price',w.price,'effective_price',p_price,'normalization_rejection',p_rejection));
    IF p_rejection IS NOT NULL AND NOT nq_project_strategy_run(p_run) THEN
        RAISE EXCEPTION 'normalization rejection must terminalize same run' USING ERRCODE='23514';
    END IF;
    RETURN true;
END $$;

CREATE FUNCTION nq_guard_cancel_finality_v51() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP<>'INSERT' OR current_user<>(SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid=TG_RELID) THEN
        RAISE EXCEPTION 'cancel finality requires verified immutable writer' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_cancel_finality_v51 BEFORE INSERT OR UPDATE OR DELETE ON ordinary_order_cancel_finality
    FOR EACH ROW EXECUTE FUNCTION nq_guard_cancel_finality_v51();
CREATE TRIGGER trg_cancel_finality_truncate_v51 BEFORE TRUNCATE ON ordinary_order_cancel_finality
    FOR EACH STATEMENT EXECUTE FUNCTION nq_guard_cancel_finality_v51();

CREATE FUNCTION nq_finish_strategy_cancel(p_run text,p_order text,p_version bigint,p_account bigint,
    p_venue text,p_env text,p_symbol text,p_client text,p_external text,p_original numeric,p_executed numeric)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE r strategy_runs%ROWTYPE; o orders%ROWTYPE; actual numeric; n bigint; valid boolean;
BEGIN
    IF NOT has_table_privilege(session_user,'orders','UPDATE') THEN
        RAISE EXCEPTION 'order writer privilege required' USING ERRCODE='42501';
    END IF;
    SELECT * INTO STRICT r FROM strategy_runs WHERE strategy_run_id=p_run FOR NO KEY UPDATE;
    IF r.status NOT IN ('CREATED','DISPATCHING','RUNNING') THEN RETURN false; END IF;
    SELECT * INTO STRICT o FROM orders WHERE order_id=p_order FOR UPDATE;
    IF o.strategy_run_id IS DISTINCT FROM p_run OR o.status<>'CANCELLED' OR o.version<>p_version
        OR ROW(o.account_id,o.venue,o.trade_env,o.symbol,o.client_order_id,o.external_order_id,o.qty)
            IS DISTINCT FROM ROW(p_account,p_venue,p_env,p_symbol,p_client,p_external,p_original)
        OR p_executed IS NULL OR p_executed<0 OR p_executed>=o.qty THEN RETURN false; END IF;
    SELECT * INTO actual,n,valid FROM nq_strategy_execution_proof(p_order);
    IF NOT valid OR actual<>p_executed THEN RETURN false; END IF;
    INSERT INTO ordinary_order_cancel_finality VALUES(p_order,p_version,p_executed) ON CONFLICT(order_id) DO NOTHING;
    IF NOT EXISTS(SELECT 1 FROM ordinary_order_cancel_finality WHERE order_id=p_order
        AND observed_order_version=p_version AND executed_quantity=p_executed) THEN
        RAISE EXCEPTION 'conflicting cancel finality' USING ERRCODE='23514';
    END IF;
    IF NOT nq_project_strategy_run(p_run) THEN
        RAISE EXCEPTION 'cancel finality must commit with run terminalization' USING ERRCODE='23514';
    END IF;
    RETURN true;
END $$;

-- 真实成交不能因旧取消证明而丢弃；协议外的后续成交必须留下明确 correctness 告警。
CREATE FUNCTION nq_observe_cancel_contradiction_v51() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE expected numeric; actual numeric;
BEGIN
    IF NEW.strategy_run_id IS NULL THEN RETURN NEW; END IF;
    SELECT executed_quantity INTO expected FROM ordinary_order_cancel_finality WHERE order_id=NEW.order_id;
    IF NOT FOUND THEN RETURN NEW; END IF;
    SELECT COALESCE(sum(qty),0) INTO actual FROM trades WHERE order_id=NEW.order_id;
    IF actual IS DISTINCT FROM expected THEN
        INSERT INTO audit_logs(domain,action,actor_id,trace_id,detail_json)
            VALUES('RECONCILE','CANCEL_FINALITY_CONTRADICTION',NEW.order_id,NEW.trace_id,
                jsonb_build_object('order_id',NEW.order_id,'trade_id',NEW.trade_id,
                    'expected_executed_quantity',expected,'actual_executed_quantity',actual,
                    'disposition','KEEP_REAL_TRADE_NO_RUN_REOPEN_NO_RESEND'));
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_cancel_contradiction_v51 AFTER INSERT ON trades
    FOR EACH ROW EXECUTE FUNCTION nq_observe_cancel_contradiction_v51();

REVOKE ALL ON strategy_run_dispatch_work,ordinary_order_cancel_finality FROM PUBLIC;
DO $$
DECLARE s text:=current_schema(); f record;
BEGIN
    FOR f IN SELECT p.oid::regprocedure AS signature FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
        WHERE n.nspname=s AND (p.proname LIKE 'nq_%_v51' OR p.proname IN ('nq_begin_strategy_dispatch',
            'nq_admit_strategy_work','nq_strategy_execution_proof','nq_project_strategy_run','nq_finish_strategy_cancel','nq_bind_strategy_effective'))
    LOOP
        EXECUTE format('ALTER FUNCTION %s SET search_path TO pg_catalog, %I, pg_temp',f.signature,s);
        EXECUTE format('ALTER FUNCTION %s SET lock_timeout TO %L',f.signature,'5s');
    END LOOP;
END $$;
