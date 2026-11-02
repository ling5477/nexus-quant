SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

ALTER TABLE tenant_background_work ADD COLUMN eligible boolean NOT NULL DEFAULT true;
ALTER TABLE tenant_background_work ADD COLUMN strategy_schedule_id varchar(128) REFERENCES strategy_schedules(schedule_job_id);
ALTER TABLE tenant_background_work ADD COLUMN paper_schedule_id varchar(64) REFERENCES paper_run_schedules(schedule_id);
ALTER TABLE tenant_background_work DROP CONSTRAINT tenant_background_work_work_kind_check;
ALTER TABLE tenant_background_work DROP CONSTRAINT tenant_background_work_check;
ALTER TABLE tenant_background_work ADD CONSTRAINT ck_tenant_work_kind CHECK(work_kind IN ('PAPER','STRATEGY','ORDER','STRATEGY_SCHEDULE','PAPER_SCHEDULE'));
ALTER TABLE tenant_background_work ALTER COLUMN work_kind TYPE varchar(24);
ALTER TABLE tenant_background_work ADD CONSTRAINT ck_tenant_work_identity CHECK(
    num_nonnulls(paper_run_id,strategy_run_id,order_id,strategy_schedule_id,paper_schedule_id)=1
    AND ((work_kind='PAPER' AND work_id=paper_run_id) OR (work_kind='STRATEGY' AND work_id=strategy_run_id)
        OR (work_kind='ORDER' AND work_id=order_id) OR (work_kind='STRATEGY_SCHEDULE' AND work_id=strategy_schedule_id)
        OR (work_kind='PAPER_SCHEDULE' AND work_id=paper_schedule_id)));
CREATE TABLE tenant_work_scan_cursors (
    work_kind varchar(24) PRIMARY KEY CHECK(work_kind IN ('PAPER','STRATEGY','ORDER','STRATEGY_SCHEDULE','PAPER_SCHEDULE')),
    last_work_id varchar(128) NOT NULL DEFAULT '',
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO tenant_work_scan_cursors(work_kind) VALUES('PAPER'),('STRATEGY'),('ORDER'),('STRATEGY_SCHEDULE'),('PAPER_SCHEDULE');
COMMENT ON TABLE tenant_work_scan_cursors IS '有界后台身份扫描游标；不赋予交易资格，不通过 TTL 释放未知业务结果';

CREATE OR REPLACE FUNCTION nq_register_tenant_work() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public AS $$
DECLARE active boolean; row_data jsonb:=to_jsonb(NEW);
BEGIN
    active := COALESCE((row_data->>'status') NOT IN ('SUCCEEDED','FAILED','CANCELLED','REJECTED','RISK_REJECTED','FILLED','EXPIRED','STOPPED','DISABLED'),true);
    CASE TG_TABLE_NAME
        WHEN 'paper_trading_runs' THEN
            INSERT INTO tenant_background_work(work_kind,work_id,tenant_id,account_id,paper_run_id,eligible)
                VALUES('PAPER',NEW.paper_run_id,NEW.tenant_id,NEW.canonical_account_id,NEW.paper_run_id,active)
                ON CONFLICT(work_kind,work_id) DO UPDATE SET account_id=EXCLUDED.account_id,eligible=EXCLUDED.eligible
                WHERE tenant_background_work.tenant_id=EXCLUDED.tenant_id;
        WHEN 'strategy_runs' THEN
            INSERT INTO tenant_background_work(work_kind,work_id,tenant_id,account_id,strategy_run_id,eligible)
                VALUES('STRATEGY',NEW.strategy_run_id,nq_entity_tenant('account',NEW.account_id::text),NEW.account_id,NEW.strategy_run_id,active)
                ON CONFLICT(work_kind,work_id) DO UPDATE SET eligible=EXCLUDED.eligible;
        WHEN 'orders' THEN
            INSERT INTO tenant_background_work(work_kind,work_id,tenant_id,account_id,order_id,eligible)
                VALUES('ORDER',NEW.order_id,nq_entity_tenant('account',NEW.account_id::text),NEW.account_id,NEW.order_id,active)
                ON CONFLICT(work_kind,work_id) DO UPDATE SET eligible=EXCLUDED.eligible;
        WHEN 'strategy_schedules' THEN
            INSERT INTO tenant_background_work(work_kind,work_id,tenant_id,account_id,strategy_schedule_id,eligible)
                VALUES('STRATEGY_SCHEDULE',NEW.schedule_job_id,nq_entity_tenant('account',NEW.account_id::text),NEW.account_id,NEW.schedule_job_id,NEW.enabled)
                ON CONFLICT(work_kind,work_id) DO UPDATE SET eligible=EXCLUDED.eligible;
        WHEN 'paper_run_schedules' THEN
            INSERT INTO tenant_background_work(work_kind,work_id,tenant_id,paper_schedule_id,eligible)
                VALUES('PAPER_SCHEDULE',NEW.schedule_id,nq_entity_tenant('paper',NEW.paper_run_id),NEW.schedule_id,active)
                ON CONFLICT(work_kind,work_id) DO UPDATE SET eligible=EXCLUDED.eligible;
    END CASE;
    RETURN NEW;
END;
$$;
DROP TRIGGER trg_register_paper_work ON paper_trading_runs;
DROP TRIGGER trg_register_strategy_work ON strategy_runs;
DROP TRIGGER trg_register_order_work ON orders;
CREATE TRIGGER trg_register_paper_work AFTER INSERT OR UPDATE OF canonical_account_id,status ON paper_trading_runs FOR EACH ROW EXECUTE FUNCTION nq_register_tenant_work();
CREATE TRIGGER trg_register_strategy_work AFTER INSERT OR UPDATE OF status ON strategy_runs FOR EACH ROW EXECUTE FUNCTION nq_register_tenant_work();
CREATE TRIGGER trg_register_order_work AFTER INSERT OR UPDATE OF status ON orders FOR EACH ROW EXECUTE FUNCTION nq_register_tenant_work();
CREATE TRIGGER trg_register_strategy_schedule_work AFTER INSERT OR UPDATE OF enabled ON strategy_schedules FOR EACH ROW EXECUTE FUNCTION nq_register_tenant_work();
CREATE TRIGGER trg_register_paper_schedule_work AFTER INSERT OR UPDATE OF status ON paper_run_schedules FOR EACH ROW EXECUTE FUNCTION nq_register_tenant_work();

-- 迁移身份从数据库枚举已有本地作用域，不能由 HTTP 参数指定；这里只补工作索引，不改业务行。
DO $$
DECLARE identity uuid;
BEGIN
    FOR identity IN SELECT tenant_id FROM tenants WHERE status='ACTIVE' LOOP
        PERFORM set_config('nq.tenant_id',identity::text,true);
        INSERT INTO tenant_background_work(work_kind,work_id,tenant_id,account_id,strategy_schedule_id,eligible)
            SELECT 'STRATEGY_SCHEDULE',s.schedule_job_id,identity,s.account_id,s.schedule_job_id,s.enabled
            FROM strategy_schedules s JOIN accounts a USING(account_id) WHERE a.tenant_id=identity;
        INSERT INTO tenant_background_work(work_kind,work_id,tenant_id,paper_schedule_id,eligible)
            SELECT 'PAPER_SCHEDULE',s.schedule_id,identity,s.schedule_id,s.status<>'DISABLED'
            FROM paper_run_schedules s JOIN paper_trading_runs p USING(paper_run_id) WHERE p.tenant_id=identity;
    END LOOP;
    PERFORM set_config('nq.tenant_id','',true);
END;
$$;

CREATE FUNCTION nq_tenant_background_enabled(identity uuid) RETURNS boolean
LANGUAGE sql STABLE SET search_path TO pg_catalog, public AS $$
    SELECT EXISTS (SELECT 1 FROM tenants t JOIN tenant_execution_controls c USING(tenant_id)
        WHERE t.tenant_id=identity AND t.status='ACTIVE' AND NOT c.suspended AND NOT c.requires_manual_resume
        AND (t.tenant_kind='LOCAL' OR EXISTS (
            SELECT 1 FROM tenant_memberships m JOIN users u ON u.id=m.user_id
            WHERE m.tenant_id=t.tenant_id AND m.status='ACTIVE' AND u.enabled
                AND NOT EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
                    WHERE ur.user_id=u.id AND r.role_code<>'DEMO_USER'))))
$$;

CREATE FUNCTION nq_poll_tenant_work(p_kind text,p_limit integer) RETURNS TABLE(work_id text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public AS $$
DECLARE last_id text; ids text[];
BEGIN
    IF p_kind NOT IN ('PAPER','STRATEGY','ORDER','STRATEGY_SCHEDULE','PAPER_SCHEDULE') OR p_limit<1 OR p_limit>64 THEN
        RAISE EXCEPTION 'invalid bounded tenant work scan' USING ERRCODE='22023';
    END IF;
    -- 普通用户业务上下文不具备全局枚举权；调度器从空上下文调用固定的最小身份投影。
    IF nq_current_tenant_id() IS NOT NULL THEN RAISE EXCEPTION 'TENANT_ACCESS_DENIED' USING ERRCODE='42501'; END IF;
    SELECT last_work_id INTO STRICT last_id FROM tenant_work_scan_cursors WHERE work_kind=p_kind FOR UPDATE;
    SELECT array_agg(x.work_id ORDER BY x.work_id) INTO ids FROM (
        SELECT w.work_id FROM tenant_background_work w
        WHERE w.work_kind=p_kind AND w.work_id>last_id AND w.eligible AND nq_tenant_background_enabled(w.tenant_id)
            AND (p_kind<>'PAPER' OR EXISTS (SELECT 1 FROM tenant_continuous_slots s WHERE s.paper_run_id=w.paper_run_id AND s.active))
        ORDER BY w.work_id LIMIT p_limit) x;
    IF ids IS NULL THEN
        SELECT array_agg(x.work_id ORDER BY x.work_id) INTO ids FROM (
            SELECT w.work_id FROM tenant_background_work w WHERE w.work_kind=p_kind AND w.eligible
                AND nq_tenant_background_enabled(w.tenant_id)
                AND (p_kind<>'PAPER' OR EXISTS (SELECT 1 FROM tenant_continuous_slots s WHERE s.paper_run_id=w.paper_run_id AND s.active))
            ORDER BY w.work_id LIMIT p_limit) x;
        IF ids IS NULL THEN
            UPDATE tenant_work_scan_cursors SET last_work_id='',updated_at=clock_timestamp() WHERE work_kind=p_kind;
            RETURN;
        END IF;
    END IF;
    UPDATE tenant_work_scan_cursors SET last_work_id=ids[array_length(ids,1)],updated_at=clock_timestamp() WHERE work_kind=p_kind;
    RETURN QUERY SELECT unnest(ids);
END;
$$;
REVOKE ALL ON FUNCTION nq_poll_tenant_work(text,integer) FROM PUBLIC;

CREATE FUNCTION nq_restore_tenant_work(p_kind text,p_id text) RETURNS TABLE(tenant_id uuid,execution_epoch bigint)
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public AS $$
DECLARE work tenant_background_work%ROWTYPE; control tenant_execution_controls%ROWTYPE; expected text;
BEGIN
    IF p_kind NOT IN ('PAPER','STRATEGY','ORDER','STRATEGY_SCHEDULE','PAPER_SCHEDULE') OR length(p_id)>128 THEN
        RAISE EXCEPTION 'RUN_NOT_FOUND' USING ERRCODE='42501';
    END IF;
    SELECT * INTO work FROM tenant_background_work w WHERE w.work_kind=p_kind AND w.work_id=p_id;
    IF NOT FOUND OR (nq_current_tenant_id() IS NOT NULL AND nq_current_tenant_id()<>work.tenant_id) THEN
        RAISE EXCEPTION 'RUN_NOT_FOUND' USING ERRCODE='42501';
    END IF;
    SELECT * INTO STRICT control FROM tenant_execution_controls c WHERE c.tenant_id=work.tenant_id;
    IF NOT nq_tenant_background_enabled(work.tenant_id) THEN
        RAISE EXCEPTION 'TENANT_SUSPENDED' USING ERRCODE='42501';
    END IF;
    expected := NULLIF(current_setting('nq.execution_epoch',true),'');
    IF expected IS NOT NULL AND expected::bigint<>control.execution_epoch THEN
        RAISE EXCEPTION 'TENANT_EXECUTION_EPOCH_STALE' USING ERRCODE='42501';
    END IF;
    RETURN QUERY SELECT work.tenant_id,control.execution_epoch;
END;
$$;
REVOKE ALL ON FUNCTION nq_restore_tenant_work(text,text) FROM PUBLIC;

-- Demo 角色变为混合身份时与暂停使用同一控制行，不能让旧后台租约继续推进。
CREATE FUNCTION nq_suspend_mixed_tenant_role() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public AS $$
BEGIN
    UPDATE tenant_execution_controls c SET suspended=true,requires_manual_resume=true
        FROM tenant_memberships m JOIN roles r ON r.id=NEW.role_id
        WHERE m.user_id=NEW.user_id AND m.membership_kind='DEMO' AND c.tenant_id=m.tenant_id AND r.role_code<>'DEMO_USER';
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION nq_suspend_mixed_tenant_role() FROM PUBLIC;
CREATE TRIGGER trg_suspend_mixed_tenant_role AFTER INSERT ON user_roles FOR EACH ROW EXECUTE FUNCTION nq_suspend_mixed_tenant_role();

CREATE FUNCTION nq_suspend_removed_membership() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public AS $$
BEGIN
    IF OLD.membership_kind='DEMO' THEN
        UPDATE tenant_execution_controls SET suspended=true,requires_manual_resume=true WHERE tenant_id=OLD.tenant_id;
    END IF;
    RETURN OLD;
END;
$$;
REVOKE ALL ON FUNCTION nq_suspend_removed_membership() FROM PUBLIC;
CREATE TRIGGER trg_suspend_removed_membership AFTER DELETE ON tenant_memberships FOR EACH ROW EXECUTE FUNCTION nq_suspend_removed_membership();
