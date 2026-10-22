SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

-- 只读对账也必须逐账户恢复租户，不能把 RLS 空结果误报成全局账务一致。
ALTER TABLE tenant_background_work DROP CONSTRAINT ck_tenant_work_kind;
ALTER TABLE tenant_background_work ADD CONSTRAINT ck_tenant_work_kind CHECK(work_kind IN ('PAPER','STRATEGY','ORDER','STRATEGY_SCHEDULE','PAPER_SCHEDULE','ACCOUNT'));
ALTER TABLE tenant_background_work DROP CONSTRAINT ck_tenant_work_identity;
ALTER TABLE tenant_background_work ADD CONSTRAINT ck_tenant_work_identity CHECK(
    (work_kind='ACCOUNT' AND account_id IS NOT NULL AND work_id=account_id::text
        AND num_nonnulls(paper_run_id,strategy_run_id,order_id,strategy_schedule_id,paper_schedule_id)=0)
    OR (work_kind<>'ACCOUNT' AND num_nonnulls(paper_run_id,strategy_run_id,order_id,strategy_schedule_id,paper_schedule_id)=1
        AND ((work_kind='PAPER' AND work_id=paper_run_id) OR (work_kind='STRATEGY' AND work_id=strategy_run_id)
            OR (work_kind='ORDER' AND work_id=order_id) OR (work_kind='STRATEGY_SCHEDULE' AND work_id=strategy_schedule_id)
            OR (work_kind='PAPER_SCHEDULE' AND work_id=paper_schedule_id))));
ALTER TABLE tenant_work_scan_cursors DROP CONSTRAINT tenant_work_scan_cursors_work_kind_check;
ALTER TABLE tenant_work_scan_cursors ADD CONSTRAINT ck_tenant_scan_kind CHECK(work_kind IN ('PAPER','STRATEGY','ORDER','STRATEGY_SCHEDULE','PAPER_SCHEDULE','ACCOUNT'));
INSERT INTO tenant_work_scan_cursors(work_kind) VALUES('ACCOUNT');

CREATE FUNCTION nq_register_tenant_account_work() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $$
BEGIN
    INSERT INTO tenant_background_work(work_kind,work_id,tenant_id,account_id,eligible)
        VALUES('ACCOUNT',NEW.account_id::text,NEW.tenant_id,NEW.account_id,NEW.status='ACTIVE')
        ON CONFLICT(work_kind,work_id) DO UPDATE SET eligible=EXCLUDED.eligible
        WHERE tenant_background_work.tenant_id=EXCLUDED.tenant_id;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION nq_register_tenant_account_work() FROM PUBLIC;
CREATE TRIGGER trg_register_tenant_account_work AFTER INSERT OR UPDATE OF status ON accounts
    FOR EACH ROW EXECUTE FUNCTION nq_register_tenant_account_work();
DO $$
DECLARE identity uuid;
BEGIN
    FOR identity IN SELECT tenant_id FROM tenants WHERE status='ACTIVE' LOOP
        PERFORM set_config('nq.tenant_id',identity::text,true);
        INSERT INTO tenant_background_work(work_kind,work_id,tenant_id,account_id,eligible)
            SELECT 'ACCOUNT',account_id::text,tenant_id,account_id,status='ACTIVE' FROM accounts
            WHERE tenant_id=identity ON CONFLICT(work_kind,work_id) DO NOTHING;
    END LOOP;
    PERFORM set_config('nq.tenant_id','',true);
END;
$$;
-- 已停用租户的经济账户身份沿已有 FK 工作投影保留，不读取或改写其经济行。
INSERT INTO tenant_background_work(work_kind,work_id,tenant_id,account_id)
    SELECT DISTINCT 'ACCOUNT',account_id::text,tenant_id,account_id FROM tenant_background_work WHERE account_id IS NOT NULL
    ON CONFLICT(work_kind,work_id) DO NOTHING;

CREATE OR REPLACE FUNCTION nq_poll_tenant_work(p_kind text,p_limit integer) RETURNS TABLE(work_id text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public AS $$
DECLARE last_id text; ids text[];
BEGIN
    IF p_kind NOT IN ('PAPER','STRATEGY','ORDER','STRATEGY_SCHEDULE','PAPER_SCHEDULE','ACCOUNT') OR p_limit<1 OR p_limit>64 THEN
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

CREATE OR REPLACE FUNCTION nq_restore_tenant_work(p_kind text,p_id text) RETURNS TABLE(tenant_id uuid,execution_epoch bigint)
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public AS $$
DECLARE work tenant_background_work%ROWTYPE; control tenant_execution_controls%ROWTYPE; expected text;
BEGIN
    IF p_kind NOT IN ('PAPER','STRATEGY','ORDER','STRATEGY_SCHEDULE','PAPER_SCHEDULE','ACCOUNT') OR length(p_id)>128 THEN
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
