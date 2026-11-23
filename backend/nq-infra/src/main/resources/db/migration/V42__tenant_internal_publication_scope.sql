SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

-- 发布的内部修订锁随所属发布记录隔离；这不授予 Shadow/LIVE 身份绑定权限。
DROP POLICY local_only ON strategy_release_admission_state;
CREATE POLICY admission_tenant_scope ON strategy_release_admission_state
    USING(EXISTS(SELECT 1 FROM backtest_publish_records p WHERE p.publish_record_id=strategy_release_admission_state.publish_record_id))
    WITH CHECK(EXISTS(SELECT 1 FROM backtest_publish_records p WHERE p.publish_record_id=strategy_release_admission_state.publish_record_id));
CREATE FUNCTION nq_guard_demo_release_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT nq_is_local_tenant() AND (NEW.release_artifact_digest IS NOT NULL OR NEW.manifest_fingerprint IS NOT NULL
        OR NEW.manifest_schema_version IS NOT NULL OR NEW.identity_bound_at IS NOT NULL) THEN
        RAISE EXCEPTION 'TENANT_ACCESS_DENIED' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_demo_release_identity BEFORE INSERT OR UPDATE ON strategy_release_admission_state
    FOR EACH ROW EXECUTE FUNCTION nq_guard_demo_release_identity();

-- 共享许可同样要求有效服务器租户，空身份及已禁用身份不能读取许可窗口。
ALTER POLICY dataset_read ON marketdata_datasets USING(
    nq_tenant_is_current(tenant_id) OR (visibility='SHARED_READONLY' AND nq_tenant_is_current(nq_current_tenant_id())
        AND EXISTS(SELECT 1 FROM marketdata_dataset_publications p WHERE p.dataset_id=marketdata_datasets.dataset_id
            AND p.status='APPROVED' AND p.expires_at>CURRENT_TIMESTAMP)));
ALTER POLICY dataset_publication_read ON marketdata_dataset_publications USING(
    nq_tenant_is_current(nq_current_tenant_id()) AND status='APPROVED' AND expires_at>CURRENT_TIMESTAMP);
ALTER POLICY marketdata_read ON marketdata_bars USING(nq_is_local_tenant() OR (
    nq_tenant_is_current(nq_current_tenant_id()) AND EXISTS(
        SELECT 1 FROM marketdata_dataset_publications p JOIN marketdata_datasets d USING(dataset_id)
        WHERE d.visibility='SHARED_READONLY' AND p.status='APPROVED' AND p.expires_at>CURRENT_TIMESTAMP
            AND d.exchange_code=marketdata_bars.exchange_code AND d.market_type=marketdata_bars.market_type
            AND d.symbol=marketdata_bars.symbol AND d."interval"=marketdata_bars."interval"
            AND marketdata_bars.open_time>=p.permitted_from AND marketdata_bars.close_time<=p.permitted_until)));

CREATE FUNCTION nq_suspend_changed_demo_role() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $$
BEGIN
    UPDATE tenant_execution_controls c SET suspended=true,requires_manual_resume=true
        FROM tenant_memberships m WHERE m.user_id=OLD.user_id AND m.membership_kind='DEMO' AND c.tenant_id=m.tenant_id;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION nq_suspend_changed_demo_role() FROM PUBLIC;
CREATE TRIGGER trg_suspend_changed_demo_role AFTER UPDATE OR DELETE ON user_roles FOR EACH ROW EXECUTE FUNCTION nq_suspend_changed_demo_role();
