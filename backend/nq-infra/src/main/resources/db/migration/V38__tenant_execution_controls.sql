SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

CREATE TABLE tenant_execution_controls (
    tenant_id uuid PRIMARY KEY REFERENCES tenants(tenant_id) ON DELETE RESTRICT,
    suspended boolean NOT NULL DEFAULT false,
    requires_manual_resume boolean NOT NULL DEFAULT false,
    execution_epoch bigint NOT NULL DEFAULT 1 CHECK (execution_epoch>0),
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (NOT suspended OR requires_manual_resume)
);
INSERT INTO tenant_execution_controls(tenant_id,suspended,requires_manual_resume)
    SELECT tenant_id,status<>'ACTIVE',status<>'ACTIVE' FROM tenants;
COMMENT ON TABLE tenant_execution_controls IS '租户执行资格；暂停与经济提交以同一控制行锁线性化，重新启用仍需显式继续';
COMMENT ON COLUMN tenant_execution_controls.execution_epoch IS '失效的执行段不可通过新事务或重启盲目重试';

CREATE FUNCTION nq_preserve_execution_control() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.tenant_id<>OLD.tenant_id OR NEW.execution_epoch<OLD.execution_epoch THEN
        RAISE EXCEPTION 'invalid tenant execution control transition' USING ERRCODE='23514';
    END IF;
    IF NEW.suspended IS DISTINCT FROM OLD.suspended THEN
        NEW.execution_epoch := OLD.execution_epoch+1;
        NEW.requires_manual_resume := true;
    END IF;
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_tenant_execution_transition BEFORE UPDATE ON tenant_execution_controls
    FOR EACH ROW EXECUTE FUNCTION nq_preserve_execution_control();

CREATE FUNCTION nq_initialize_tenant_execution() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public AS $$
BEGIN
    INSERT INTO tenant_execution_controls(tenant_id,suspended,requires_manual_resume)
        VALUES(NEW.tenant_id,NEW.status<>'ACTIVE',NEW.status<>'ACTIVE');
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION nq_initialize_tenant_execution() FROM PUBLIC;
CREATE TRIGGER trg_initialize_tenant_execution AFTER INSERT ON tenants
    FOR EACH ROW EXECUTE FUNCTION nq_initialize_tenant_execution();

CREATE FUNCTION nq_suspend_disabled_tenant() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public AS $$
BEGIN
    IF TG_TABLE_NAME='tenants' THEN
        UPDATE tenant_execution_controls SET suspended=(NEW.status<>'ACTIVE'),requires_manual_resume=true
            WHERE tenant_id=NEW.tenant_id AND NEW.status IS DISTINCT FROM OLD.status;
    ELSIF TG_TABLE_NAME='users' AND NOT NEW.enabled AND OLD.enabled THEN
        UPDATE tenant_execution_controls c SET suspended=true,requires_manual_resume=true
            FROM tenant_memberships m WHERE m.user_id=NEW.id AND m.membership_kind='DEMO' AND c.tenant_id=m.tenant_id;
    ELSIF TG_TABLE_NAME='tenant_memberships' AND NEW.status='DISABLED' AND OLD.status<>'DISABLED' THEN
        UPDATE tenant_execution_controls SET suspended=true,requires_manual_resume=true
            WHERE tenant_id=NEW.tenant_id AND NEW.membership_kind='DEMO';
    END IF;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION nq_suspend_disabled_tenant() FROM PUBLIC;
CREATE TRIGGER trg_suspend_tenant_status AFTER UPDATE OF status ON tenants
    FOR EACH ROW EXECUTE FUNCTION nq_suspend_disabled_tenant();
CREATE TRIGGER trg_suspend_tenant_user AFTER UPDATE OF enabled ON users
    FOR EACH ROW EXECUTE FUNCTION nq_suspend_disabled_tenant();
CREATE TRIGGER trg_suspend_tenant_membership AFTER UPDATE OF status ON tenant_memberships
    FOR EACH ROW EXECUTE FUNCTION nq_suspend_disabled_tenant();

CREATE FUNCTION nq_tenant_is_current(identity uuid) RETURNS boolean
LANGUAGE sql STABLE SET search_path TO pg_catalog, public AS $$
    SELECT identity=nq_current_tenant_id()
        AND EXISTS (SELECT 1 FROM tenants t WHERE t.tenant_id=identity AND t.status='ACTIVE')
        AND (NULLIF(current_setting('nq.user_id',true),'') IS NULL OR EXISTS (
            SELECT 1 FROM tenant_memberships m JOIN users u ON u.id=m.user_id
            WHERE m.tenant_id=identity AND m.user_id=NULLIF(current_setting('nq.user_id',true),'')::bigint
                AND m.status='ACTIVE' AND u.enabled))
$$;
CREATE FUNCTION nq_is_local_tenant() RETURNS boolean
LANGUAGE sql STABLE AS $$ SELECT nq_tenant_is_current('00000000-0000-4000-8000-000000000001'::uuid) $$;

CREATE FUNCTION nq_require_tenant_execution(identity uuid) RETURNS void
LANGUAGE plpgsql SET search_path TO pg_catalog, public AS $$
DECLARE control tenant_execution_controls%ROWTYPE; expected text;
BEGIN
    IF NOT COALESCE(nq_tenant_is_current(identity),false) THEN
        RAISE EXCEPTION 'TENANT_ACCESS_DENIED' USING ERRCODE='42501';
    END IF;
    -- SHARE 锁保持到业务提交；暂停更新需要冲突锁，不能在禁用提交后再完成新经济写入。
    SELECT * INTO STRICT control FROM tenant_execution_controls WHERE tenant_id=identity FOR SHARE;
    IF control.suspended OR control.requires_manual_resume THEN
        RAISE EXCEPTION 'TENANT_SUSPENDED' USING ERRCODE='42501';
    END IF;
    expected := NULLIF(current_setting('nq.execution_epoch',true),'');
    IF expected IS NULL OR expected !~ '^[0-9]+$' OR expected::bigint<>control.execution_epoch THEN
        RAISE EXCEPTION 'TENANT_EXECUTION_EPOCH_STALE' USING ERRCODE='42501';
    END IF;
END;
$$;

CREATE FUNCTION nq_guard_tenant_economic_write() RETURNS trigger
LANGUAGE plpgsql SET search_path TO pg_catalog, public AS $$
DECLARE row_data jsonb := to_jsonb(NEW); identity uuid;
BEGIN
    IF TG_TABLE_NAME='ledger_events' THEN
        SELECT nq_entity_tenant('account',account_id::text) INTO identity FROM ledger_entries WHERE entry_id=NEW.entry_id;
    ELSIF TG_TABLE_NAME='strategy_sim_decisions' THEN
        identity := nq_entity_tenant('account',row_data->>'canonical_account_id');
    ELSE identity := nq_entity_tenant('account',row_data->>'account_id');
    END IF;
    PERFORM nq_require_tenant_execution(identity);
    RETURN NEW;
END;
$$;
DO $$
DECLARE relation_name text;
BEGIN
    FOREACH relation_name IN ARRAY ARRAY['strategy_runs','strategy_run_dispatch_work','orders','trades',
        'positions','ledger_entries','ledger_events','strategy_sim_decisions'] LOOP
        EXECUTE format('CREATE TRIGGER trg_tenant_economic_write BEFORE INSERT OR UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION nq_guard_tenant_economic_write()',relation_name);
    END LOOP;
END;
$$;

-- 该表只登记可信工作身份，不能承载订单状态、资金或收益；运行角色只有有界函数入口。
CREATE TABLE tenant_background_work (
    work_kind varchar(16) NOT NULL CHECK (work_kind IN ('PAPER','STRATEGY','ORDER')),
    work_id varchar(128) NOT NULL,
    tenant_id uuid NOT NULL REFERENCES tenants(tenant_id),
    account_id bigint,
    paper_run_id varchar(64) REFERENCES paper_trading_runs(paper_run_id),
    strategy_run_id varchar(64) REFERENCES strategy_runs(strategy_run_id),
    order_id varchar(64) REFERENCES orders(order_id),
    PRIMARY KEY(work_kind,work_id),
    FOREIGN KEY(tenant_id,account_id) REFERENCES accounts(tenant_id,account_id),
    CHECK ((work_kind='PAPER' AND work_id=paper_run_id AND strategy_run_id IS NULL AND order_id IS NULL)
        OR (work_kind='STRATEGY' AND work_id=strategy_run_id AND paper_run_id IS NULL AND order_id IS NULL)
        OR (work_kind='ORDER' AND work_id=order_id AND paper_run_id IS NULL AND strategy_run_id IS NULL))
);
INSERT INTO tenant_background_work SELECT 'PAPER',paper_run_id,tenant_id,canonical_account_id,paper_run_id,NULL,NULL FROM paper_trading_runs;
INSERT INTO tenant_background_work SELECT 'STRATEGY',r.strategy_run_id,a.tenant_id,r.account_id,NULL,r.strategy_run_id,NULL
    FROM strategy_runs r JOIN accounts a USING(account_id);
INSERT INTO tenant_background_work SELECT 'ORDER',o.order_id,a.tenant_id,o.account_id,NULL,NULL,o.order_id
    FROM orders o JOIN accounts a USING(account_id);
CREATE INDEX idx_tenant_background_work ON tenant_background_work(tenant_id,work_kind,work_id);
COMMENT ON TABLE tenant_background_work IS '后台恢复所需最小身份投影；外键绑定 canonical 对象，不是第二套业务事实';

CREATE FUNCTION nq_register_tenant_work() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public AS $$
BEGIN
    CASE TG_TABLE_NAME
        WHEN 'paper_trading_runs' THEN
            INSERT INTO tenant_background_work VALUES('PAPER',NEW.paper_run_id,NEW.tenant_id,NEW.canonical_account_id,NEW.paper_run_id,NULL,NULL)
                ON CONFLICT(work_kind,work_id) DO UPDATE SET account_id=EXCLUDED.account_id
                WHERE tenant_background_work.tenant_id=EXCLUDED.tenant_id;
        WHEN 'strategy_runs' THEN
            INSERT INTO tenant_background_work VALUES('STRATEGY',NEW.strategy_run_id,nq_entity_tenant('account',NEW.account_id::text),NEW.account_id,NULL,NEW.strategy_run_id,NULL)
                ON CONFLICT(work_kind,work_id) DO NOTHING;
        WHEN 'orders' THEN
            INSERT INTO tenant_background_work VALUES('ORDER',NEW.order_id,nq_entity_tenant('account',NEW.account_id::text),NEW.account_id,NULL,NULL,NEW.order_id)
                ON CONFLICT(work_kind,work_id) DO NOTHING;
    END CASE;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION nq_register_tenant_work() FROM PUBLIC;
CREATE TRIGGER trg_register_paper_work AFTER INSERT OR UPDATE OF canonical_account_id ON paper_trading_runs FOR EACH ROW EXECUTE FUNCTION nq_register_tenant_work();
CREATE TRIGGER trg_register_strategy_work AFTER INSERT ON strategy_runs FOR EACH ROW EXECUTE FUNCTION nq_register_tenant_work();
CREATE TRIGGER trg_register_order_work AFTER INSERT ON orders FOR EACH ROW EXECUTE FUNCTION nq_register_tenant_work();

CREATE TABLE tenant_continuous_slots (
    paper_run_id varchar(64) PRIMARY KEY REFERENCES continuous_sim_runs(paper_run_id),
    tenant_id uuid NOT NULL REFERENCES tenants(tenant_id),
    active boolean NOT NULL
);
INSERT INTO tenant_continuous_slots SELECT r.paper_run_id,p.tenant_id,r.status<>'STOPPED'
    FROM continuous_sim_runs r JOIN paper_trading_runs p USING(paper_run_id);
-- 升级前本地可合法同时运行多个实例；保留这些身份和事实，新增准入仍经串行容量函数拒绝第二个实例。
CREATE UNIQUE INDEX uq_tenant_active_continuous ON tenant_continuous_slots(tenant_id)
    WHERE active AND tenant_id<>'00000000-0000-4000-8000-000000000001'::uuid;
COMMENT ON TABLE tenant_continuous_slots IS '同事务维护的 Continuous SIM 容量投影；全局八个、每租户一个，不承载经济事实';

CREATE FUNCTION nq_require_continuous_capacity() RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public AS $$
BEGIN
    IF nq_current_tenant_id() IS NULL THEN RAISE EXCEPTION 'TENANT_ACCESS_DENIED' USING ERRCODE='42501'; END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended('continuous-sim-admission',9253));
    IF (SELECT count(*) FROM tenant_continuous_slots WHERE active)>=8
        OR EXISTS (SELECT 1 FROM tenant_continuous_slots WHERE tenant_id=nq_current_tenant_id() AND active) THEN
        RAISE EXCEPTION 'CAPACITY_LIMIT' USING ERRCODE='54000';
    END IF;
END;
$$;
REVOKE ALL ON FUNCTION nq_require_continuous_capacity() FROM PUBLIC;

CREATE FUNCTION nq_sync_continuous_slot() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public AS $$
DECLARE identity uuid := nq_entity_tenant('paper',NEW.paper_run_id);
BEGIN
    IF NEW.status<>'STOPPED' AND (TG_OP='INSERT' OR OLD.status='STOPPED') THEN
        PERFORM nq_require_tenant_execution(identity);
        PERFORM nq_require_continuous_capacity();
    END IF;
    INSERT INTO tenant_continuous_slots VALUES(NEW.paper_run_id,identity,NEW.status<>'STOPPED')
        ON CONFLICT(paper_run_id) DO UPDATE SET active=EXCLUDED.active
        WHERE tenant_continuous_slots.tenant_id=EXCLUDED.tenant_id;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION nq_sync_continuous_slot() FROM PUBLIC;
CREATE TRIGGER trg_continuous_slot AFTER INSERT OR UPDATE OF status ON continuous_sim_runs FOR EACH ROW EXECUTE FUNCTION nq_sync_continuous_slot();

CREATE FUNCTION nq_resume_tenant_execution(p_run text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public AS $$
DECLARE identity uuid := nq_entity_tenant('paper',p_run); control tenant_execution_controls%ROWTYPE;
BEGIN
    IF NOT COALESCE(nq_tenant_is_current(identity),false) THEN RAISE EXCEPTION 'RUN_NOT_FOUND' USING ERRCODE='42501'; END IF;
    SELECT * INTO STRICT control FROM tenant_execution_controls WHERE tenant_id=identity FOR UPDATE;
    IF control.suspended THEN RAISE EXCEPTION 'TENANT_SUSPENDED' USING ERRCODE='42501'; END IF;
    IF NULLIF(current_setting('nq.execution_epoch',true),'')::bigint IS DISTINCT FROM control.execution_epoch THEN
        RAISE EXCEPTION 'TENANT_EXECUTION_EPOCH_STALE' USING ERRCODE='42501';
    END IF;
    UPDATE tenant_execution_controls SET requires_manual_resume=false WHERE tenant_id=identity;
END;
$$;
REVOKE ALL ON FUNCTION nq_resume_tenant_execution(text) FROM PUBLIC;
