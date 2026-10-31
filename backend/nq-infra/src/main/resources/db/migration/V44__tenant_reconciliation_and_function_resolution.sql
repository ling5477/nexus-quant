SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

-- 对账是只读核验，不属于经济推进；暂停或账户停用不能把未检查伪装为账务一致。
-- 租户本身停用时仍返回最小账户身份，由恢复函数显式报错，避免静默遗漏。
CREATE OR REPLACE FUNCTION nq_poll_tenant_work(p_kind text,p_limit integer) RETURNS TABLE(work_id text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public, pg_temp AS $$
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
        WHERE w.work_kind=p_kind AND w.work_id>last_id AND (p_kind='ACCOUNT' OR (w.eligible AND nq_tenant_background_enabled(w.tenant_id)))
            AND (p_kind<>'PAPER' OR EXISTS (SELECT 1 FROM tenant_continuous_slots s WHERE s.paper_run_id=w.paper_run_id AND s.active))
        ORDER BY w.work_id LIMIT p_limit) x;
    IF ids IS NULL THEN
        SELECT array_agg(x.work_id ORDER BY x.work_id) INTO ids FROM (
            SELECT w.work_id FROM tenant_background_work w WHERE w.work_kind=p_kind AND (p_kind='ACCOUNT' OR (w.eligible
                AND nq_tenant_background_enabled(w.tenant_id)))
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
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public, pg_temp AS $$
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
    IF (p_kind='ACCOUNT' AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.tenant_id=work.tenant_id AND t.status='ACTIVE'))
        OR (p_kind<>'ACCOUNT' AND NOT nq_tenant_background_enabled(work.tenant_id)) THEN
        RAISE EXCEPTION 'TENANT_SUSPENDED' USING ERRCODE='42501';
    END IF;
    expected := NULLIF(current_setting('nq.execution_epoch',true),'');
    IF p_kind<>'ACCOUNT' AND expected IS NOT NULL AND expected::bigint<>control.execution_epoch THEN
        RAISE EXCEPTION 'TENANT_EXECUTION_EPOCH_STALE' USING ERRCODE='42501';
    END IF;
    RETURN QUERY SELECT work.tenant_id,control.execution_epoch;
END;
$$;
REVOKE ALL ON FUNCTION nq_restore_tenant_work(text,text) FROM PUBLIC;

-- PostgreSQL 会隐式优先搜索临时关系；显式置末尾，确保权限函数及其辅助函数绑定正式表。
DO $$
DECLARE item record;
BEGIN
    FOR item IN SELECT p.oid::regprocedure AS identity FROM pg_proc p
        JOIN pg_namespace n ON n.oid=p.pronamespace
        WHERE n.nspname='public' AND (p.prosecdef OR p.proname LIKE 'nq\_%' ESCAPE '\'
            OR p.proname IN ('bump_admission_for_paper_mutation','reject_public_capture_dataset_mutation'))
    LOOP
        EXECUTE format('ALTER FUNCTION %s SET search_path TO pg_catalog,public,pg_temp',item.identity);
    END LOOP;
END;
$$;
