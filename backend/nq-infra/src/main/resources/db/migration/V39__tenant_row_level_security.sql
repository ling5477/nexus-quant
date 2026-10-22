SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

CREATE FUNCTION nq_is_relation_owner(relation_name regclass) RETURNS boolean
LANGUAGE sql STABLE AS $$ SELECT current_user=pg_get_userbyid(relowner) FROM pg_class WHERE oid=relation_name $$;

-- 根表的 USING/WITH CHECK 都依赖可信事务身份；空身份不回落本地。
DO $$
DECLARE relation_name text;
BEGIN
    FOREACH relation_name IN ARRAY ARRAY['accounts','exchange_accounts','strategy_definitions','strategy_versions',
        'research_configs','backtest_configs','paper_trading_runs'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',relation_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',relation_name);
        EXECUTE format('CREATE POLICY tenant_scope ON %I USING(nq_tenant_is_current(tenant_id)) WITH CHECK(nq_tenant_is_current(tenant_id))',relation_name);
    END LOOP;
END;
$$;

-- 派生事实只沿有索引的父键检查，不冗余回填经济行的 tenant_id。
DO $$
DECLARE link text[];
BEGIN
    FOREACH link SLICE 1 IN ARRAY ARRAY[
        ['account_snapshots','accounts','account_id','account_id'],
        ['account_equity_measurements','exchange_accounts','exchange_account_id','exchange_account_id'],
        ['positions','accounts','account_id','account_id'],
        ['ledger_entries','accounts','account_id','account_id'],
        ['ledger_events','ledger_entries','entry_id','entry_id'],
        ['strategy_runs','accounts','account_id','account_id'],
        ['strategy_schedules','accounts','account_id','account_id'],
        ['strategy_run_dispatch_work','strategy_runs','strategy_run_id','strategy_run_id'],
        ['orders','accounts','account_id','account_id'],
        ['trades','accounts','account_id','account_id'],
        ['ordinary_place_authorities','orders','order_id','order_id'],
        ['ordinary_order_cancel_finality','orders','order_id','order_id'],
        ['backtest_runs','backtest_configs','backtest_config_id','backtest_config_id'],
        ['sim_orders','backtest_runs','backtest_run_id','backtest_run_id'],
        ['sim_trades','backtest_runs','backtest_run_id','backtest_run_id'],
        ['sim_positions','backtest_runs','backtest_run_id','backtest_run_id'],
        ['sim_pnl_snapshots','backtest_runs','backtest_run_id','backtest_run_id'],
        ['backtest_eval_reports','backtest_runs','backtest_run_id','backtest_run_id'],
        ['backtest_publish_records','backtest_runs','backtest_run_id','backtest_run_id'],
        ['continuous_sim_runs','paper_trading_runs','paper_run_id','paper_run_id'],
        ['continuous_sim_bars','paper_trading_runs','paper_run_id','paper_run_id'],
        ['strategy_sim_decisions','paper_trading_runs','paper_run_id','paper_run_id'],
        ['paper_trading_orders','paper_trading_runs','paper_run_id','paper_run_id'],
        ['paper_trading_trades','paper_trading_runs','paper_run_id','paper_run_id'],
        ['paper_trading_positions','paper_trading_runs','paper_run_id','paper_run_id'],
        ['paper_risk_check_results','paper_trading_runs','paper_run_id','paper_run_id'],
        ['equity_curve_snapshots','paper_trading_runs','paper_run_id','paper_run_id'],
        ['position_curve_snapshots','paper_trading_runs','paper_run_id','paper_run_id'],
        ['trade_replay_records','paper_trading_runs','paper_run_id','paper_run_id'],
        ['paper_run_schedules','paper_trading_runs','paper_run_id','paper_run_id'],
        ['paper_run_schedule_fires','paper_trading_runs','paper_run_id','paper_run_id'],
        ['paper_run_heartbeats','paper_trading_runs','paper_run_id','paper_run_id'],
        ['paper_run_daily_reports','paper_trading_runs','paper_run_id','paper_run_id'],
        ['paper_run_stability_checks','paper_trading_runs','paper_run_id','paper_run_id'],
        ['paper_run_alerts','paper_trading_runs','paper_run_id','paper_run_id'],
        ['paper_run_recovery_events','paper_trading_runs','paper_run_id','paper_run_id'],
        ['emergency_stop_events','paper_trading_runs','paper_run_id','paper_run_id'],
        ['sim_asset_funding_receipts','paper_trading_runs','paper_run_id','paper_run_id']
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',link[1]);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',link[1]);
        EXECUTE format('CREATE POLICY tenant_scope ON %1$I USING(EXISTS(SELECT 1 FROM %2$I p WHERE p.%3$I=%1$I.%4$I)) WITH CHECK(EXISTS(SELECT 1 FROM %2$I p WHERE p.%3$I=%1$I.%4$I))',
            link[1],link[2],link[3],link[4]);
    END LOOP;
END;
$$;

DO $$
DECLARE relation_name text;
BEGIN
    FOREACH relation_name IN ARRAY ARRAY['account_command_receipts','research_creation_operations',
        'backtest_evaluation_requests','continuous_sim_operations','paper_alert_operations',
        'strategy_schedule_creations','marketdata_dataset_operations','discount_asset_price_snapshots'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',relation_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',relation_name);
        EXECUTE format('CREATE POLICY tenant_scope ON %1$I USING(EXISTS(SELECT 1 FROM tenant_memberships m WHERE m.user_id=%1$I.owner_user_id AND nq_tenant_is_current(m.tenant_id))) WITH CHECK(EXISTS(SELECT 1 FROM tenant_memberships m WHERE m.user_id=%1$I.owner_user_id AND nq_tenant_is_current(m.tenant_id)))',relation_name);
    END LOOP;
END;
$$;
ALTER TABLE user_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_preferences FORCE ROW LEVEL SECURITY;
CREATE POLICY own_preferences ON user_preferences
    USING(user_id=NULLIF(current_setting('nq.user_id',true),'')::bigint)
    WITH CHECK(user_id=NULLIF(current_setting('nq.user_id',true),'')::bigint);

ALTER TABLE marketdata_datasets ENABLE ROW LEVEL SECURITY;
ALTER TABLE marketdata_datasets FORCE ROW LEVEL SECURITY;
CREATE POLICY dataset_read ON marketdata_datasets FOR SELECT USING(
    nq_tenant_is_current(tenant_id) OR (visibility='SHARED_READONLY' AND nq_current_tenant_id() IS NOT NULL
        AND EXISTS(SELECT 1 FROM marketdata_dataset_publications p WHERE p.dataset_id=marketdata_datasets.dataset_id
            AND p.status='APPROVED' AND p.expires_at>CURRENT_TIMESTAMP)));
CREATE POLICY dataset_shared_owner_read ON marketdata_datasets FOR SELECT
    USING(visibility='SHARED_READONLY' AND nq_is_relation_owner('marketdata_datasets'));
CREATE POLICY dataset_insert ON marketdata_datasets FOR INSERT WITH CHECK(
    (visibility='TENANT_PRIVATE' AND nq_tenant_is_current(tenant_id))
    OR (visibility='SHARED_READONLY' AND tenant_id IS NULL AND nq_is_relation_owner('marketdata_datasets')));
CREATE POLICY dataset_update ON marketdata_datasets FOR UPDATE USING(
    visibility='TENANT_PRIVATE' AND nq_tenant_is_current(tenant_id))
    WITH CHECK(visibility='TENANT_PRIVATE' AND nq_tenant_is_current(tenant_id));
ALTER TABLE marketdata_dataset_publications ENABLE ROW LEVEL SECURITY;
ALTER TABLE marketdata_dataset_publications FORCE ROW LEVEL SECURITY;
CREATE POLICY dataset_publication_read ON marketdata_dataset_publications FOR SELECT
    USING(nq_current_tenant_id() IS NOT NULL AND status='APPROVED' AND expires_at>CURRENT_TIMESTAMP);
CREATE POLICY dataset_publication_owner ON marketdata_dataset_publications
    USING(nq_is_relation_owner('marketdata_dataset_publications'))
    WITH CHECK(nq_is_relation_owner('marketdata_dataset_publications'));
ALTER TABLE marketdata_dataset_coverage ENABLE ROW LEVEL SECURITY;
ALTER TABLE marketdata_dataset_coverage FORCE ROW LEVEL SECURITY;
CREATE POLICY coverage_read ON marketdata_dataset_coverage FOR SELECT
    USING(EXISTS(SELECT 1 FROM marketdata_datasets d WHERE d.dataset_id=marketdata_dataset_coverage.dataset_id));
CREATE POLICY coverage_insert ON marketdata_dataset_coverage FOR INSERT WITH CHECK(EXISTS(
    SELECT 1 FROM marketdata_datasets d WHERE d.dataset_id=marketdata_dataset_coverage.dataset_id AND nq_tenant_is_current(d.tenant_id)));
CREATE POLICY coverage_update ON marketdata_dataset_coverage FOR UPDATE USING(EXISTS(
    SELECT 1 FROM marketdata_datasets d WHERE d.dataset_id=marketdata_dataset_coverage.dataset_id AND nq_tenant_is_current(d.tenant_id)))
    WITH CHECK(EXISTS(SELECT 1 FROM marketdata_datasets d WHERE d.dataset_id=marketdata_dataset_coverage.dataset_id AND nq_tenant_is_current(d.tenant_id)));

-- 共享原始行情不按租户复制；Demo 只读受控目录许可的范围，所有共享写入仍仅本地受控流程。
ALTER TABLE marketdata_bars ENABLE ROW LEVEL SECURITY;
ALTER TABLE marketdata_bars FORCE ROW LEVEL SECURITY;
CREATE POLICY marketdata_read ON marketdata_bars FOR SELECT USING(nq_is_local_tenant() OR EXISTS(
    SELECT 1 FROM marketdata_dataset_publications p JOIN marketdata_datasets d USING(dataset_id)
    WHERE d.visibility='SHARED_READONLY' AND p.status='APPROVED' AND p.expires_at>CURRENT_TIMESTAMP
        AND d.exchange_code=marketdata_bars.exchange_code AND d.market_type=marketdata_bars.market_type
        AND d.symbol=marketdata_bars.symbol AND d."interval"=marketdata_bars."interval"
        AND marketdata_bars.open_time>=p.permitted_from AND marketdata_bars.close_time<=p.permitted_until));
CREATE POLICY marketdata_local_write ON marketdata_bars FOR INSERT WITH CHECK(nq_is_local_tenant());
CREATE POLICY marketdata_local_update ON marketdata_bars FOR UPDATE USING(nq_is_local_tenant()) WITH CHECK(nq_is_local_tenant());

-- 尚未开放的凭据、LIVE、Shadow 与运维业务面即使直接 SQL 也不能由 Demo 读取。
DO $$
DECLARE relation_name text;
BEGIN
    FOREACH relation_name IN ARRAY ARRAY['exchange_account_credentials','credential_operation_receipts',
        'credential_audit_logs','account_fee_observations','strategy_release_admission_state',
        'shadow_runs','shadow_run_events','shadow_run_snapshots','shadow_consistency_reports',
        'validation_review_cases','validation_review_events','live_sessions','live_session_events',
        'operator_approvals','operator_execution_authorities','controlled_execution_leases',
        'controlled_execution_lease_events','controlled_execution_lease_intents','execution_intents',
        'execution_receipts','execution_scope_bindings','execution_prerequisite_observations',
        'execution_instrument_observation_items','execution_pre_place_recovery_decisions',
        'trading_maintenance_operations','marketdata_ingestion_jobs','marketdata_ingestion_runs',
        'public_market_captures','public_market_capture_operations','instrument_catalog_sync_requests',
        'marketdata_price_event_sources','risk_limit_sets'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',relation_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',relation_name);
        EXECUTE format('CREATE POLICY local_only ON %I USING(nq_is_local_tenant()) WITH CHECK(nq_is_local_tenant())',relation_name);
    END LOOP;
END;
$$;

DO $$
DECLARE relation_name text;
BEGIN
    FOREACH relation_name IN ARRAY ARRAY['audit_log_tenant_scopes','event_tenant_scopes','risk_event_tenant_scopes'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',relation_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',relation_name);
        EXECUTE format('CREATE POLICY event_scope_read ON %I FOR SELECT USING(nq_tenant_is_current(tenant_id) OR (classification=''PLATFORM_INTERNAL'' AND nq_is_local_tenant()))',relation_name);
        EXECUTE format('CREATE POLICY event_scope_insert ON %1$I FOR INSERT WITH CHECK(nq_is_relation_owner(%2$L::regclass) AND ((classification=''TENANT_PRIVATE'' AND nq_tenant_is_current(tenant_id)) OR (classification=''PLATFORM_INTERNAL'' AND tenant_id IS NULL)))',relation_name,relation_name);
    END LOOP;
END;
$$;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs FORCE ROW LEVEL SECURITY;
CREATE POLICY audit_read ON audit_logs FOR SELECT USING(EXISTS(SELECT 1 FROM audit_log_tenant_scopes s WHERE s.audit_id=audit_logs.id));
CREATE POLICY audit_insert ON audit_logs FOR INSERT WITH CHECK(true);
ALTER TABLE event_store ENABLE ROW LEVEL SECURITY;
ALTER TABLE event_store FORCE ROW LEVEL SECURITY;
CREATE POLICY event_read ON event_store FOR SELECT USING(EXISTS(SELECT 1 FROM event_tenant_scopes s WHERE s.event_id=event_store.event_id));
CREATE POLICY event_insert ON event_store FOR INSERT WITH CHECK(true);
ALTER TABLE risk_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE risk_events FORCE ROW LEVEL SECURITY;
CREATE POLICY risk_read ON risk_events FOR SELECT USING(EXISTS(SELECT 1 FROM risk_event_tenant_scopes s WHERE s.risk_event_id=risk_events.risk_event_id));
CREATE POLICY risk_insert ON risk_events FOR INSERT WITH CHECK(true);

-- 控制表为平台执行元数据；仅 NOLOGIN owner 的受限函数可写，普通连接只读自身控制。
ALTER TABLE tenant_execution_controls ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_execution_controls FORCE ROW LEVEL SECURITY;
CREATE POLICY own_execution_control ON tenant_execution_controls FOR SELECT USING(nq_tenant_is_current(tenant_id));
CREATE POLICY execution_control_owner ON tenant_execution_controls USING(nq_is_relation_owner('tenant_execution_controls')) WITH CHECK(nq_is_relation_owner('tenant_execution_controls'));

CREATE FUNCTION nq_assert_tenant_account(p_account bigint) RETURNS void
LANGUAGE plpgsql SET search_path TO pg_catalog, public AS $$
BEGIN
    PERFORM nq_require_tenant_execution(nq_entity_tenant('account',p_account::text));
END;
$$;
CREATE FUNCTION nq_assert_tenant_run(p_run text) RETURNS void
LANGUAGE plpgsql SET search_path TO pg_catalog, public AS $$
BEGIN
    PERFORM nq_require_tenant_execution(nq_entity_tenant('strategy_run',p_run));
END;
$$;
CREATE FUNCTION nq_assert_tenant_order(p_id text) RETURNS void
LANGUAGE plpgsql SET search_path TO pg_catalog, public AS $$
BEGIN
    PERFORM nq_require_tenant_execution(nq_entity_tenant('order',p_id));
END;
$$;

-- 保留七个受限函数的原状态机正文、owner 与许可检查，仅在其入口增加租户执行验证。
DO $$
DECLARE item record; definition text; guard_call text; changed integer := 0;
BEGIN
    FOR item IN SELECT p.oid,p.proname,p.oid::regprocedure AS identity FROM pg_proc p
        JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname=current_schema() AND p.prosecdef
        AND p.proname IN ('nq_admit_strategy_work','nq_begin_strategy_dispatch','nq_project_strategy_run',
            'nq_bind_strategy_effective','nq_create_ordinary_place_order','nq_decide_ordinary_place','nq_finish_strategy_cancel') LOOP
        definition := pg_get_functiondef(item.oid);
        guard_call := CASE WHEN item.proname IN ('nq_admit_strategy_work','nq_create_ordinary_place_order','nq_finish_strategy_cancel')
            THEN 'PERFORM nq_assert_tenant_account(p_account);'
            WHEN item.proname='nq_decide_ordinary_place' THEN 'PERFORM nq_assert_tenant_order(p_id);'
            ELSE 'PERFORM nq_assert_tenant_run(p_run);' END;
        IF position(E'\nBEGIN\n' IN definition)=0 THEN RAISE EXCEPTION 'unrecognized execution function body'; END IF;
        definition := regexp_replace(definition,E'\nBEGIN\n',E'\nBEGIN\n    '||guard_call||E'\n');
        EXECUTE definition;
        EXECUTE format('ALTER FUNCTION %s SET search_path TO pg_catalog,public',item.identity);
        EXECUTE format('ALTER FUNCTION %s SET row_security TO on',item.identity);
        EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC',item.identity);
        changed := changed+1;
    END LOOP;
    IF changed<>7 THEN RAISE EXCEPTION 'canonical execution function inventory changed'; END IF;
END;
$$;
