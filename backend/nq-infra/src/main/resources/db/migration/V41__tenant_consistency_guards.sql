SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

-- 普通角色只有控制表 SELECT；经济触发器通过该受限函数持有 SHARE 锁，而不取得控制表 UPDATE。
ALTER FUNCTION nq_require_tenant_execution(uuid) SECURITY DEFINER;
ALTER FUNCTION nq_require_tenant_execution(uuid) SET row_security TO on;

CREATE FUNCTION nq_check_additional_tenant_links() RETURNS trigger
LANGUAGE plpgsql SET search_path TO pg_catalog, public AS $$
DECLARE data jsonb:=to_jsonb(NEW); identity uuid; parent_data jsonb;
BEGIN
    CASE TG_TABLE_NAME
        WHEN 'backtest_configs' THEN
            IF NEW.strategy_version_id IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM research_configs r JOIN strategy_definitions s ON s.strategy_id=r.source_strategy_id
                    JOIN strategy_versions v ON v.strategy_code=s.strategy_code AND v.tenant_id=s.tenant_id
                WHERE r.research_config_id=NEW.research_config_id AND v.strategy_version_id=NEW.strategy_version_id) THEN
                RAISE EXCEPTION 'TENANT_ACCESS_DENIED: version source mismatch' USING ERRCODE='23514';
            END IF;
        WHEN 'backtest_publish_records' THEN
            IF NOT EXISTS (SELECT 1 FROM backtest_runs r WHERE r.backtest_run_id=NEW.backtest_run_id
                AND r.backtest_config_id=NEW.backtest_config_id AND r.research_config_id=NEW.research_config_id
                AND r.source_strategy_id=NEW.source_strategy_id
                AND (NEW.strategy_version_id IS NULL OR r.strategy_version_id=NEW.strategy_version_id)) THEN
                RAISE EXCEPTION 'TENANT_ACCESS_DENIED: publication parent mismatch' USING ERRCODE='23514';
            END IF;
        WHEN 'paper_trading_runs' THEN
            IF NEW.strategy_version_id IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM backtest_publish_records p WHERE p.publish_record_id=NEW.publish_id
                    AND p.strategy_version_id=NEW.strategy_version_id) THEN
                RAISE EXCEPTION 'TENANT_ACCESS_DENIED: paper version mismatch' USING ERRCODE='23514';
            END IF;
        WHEN 'paper_run_schedule_fires' THEN
            IF NOT EXISTS(SELECT 1 FROM paper_run_schedules s WHERE s.schedule_id=NEW.schedule_id AND s.paper_run_id=NEW.paper_run_id) THEN
                RAISE EXCEPTION 'TENANT_ACCESS_DENIED: schedule run mismatch' USING ERRCODE='23514';
            END IF;
        WHEN 'ledger_entries' THEN
            IF NEW.ref_type='TRADE' AND NOT EXISTS(SELECT 1 FROM trades t WHERE t.trade_id=NEW.ref_id AND t.account_id=NEW.account_id) THEN
                RAISE EXCEPTION 'TENANT_ACCESS_DENIED: ledger trade mismatch' USING ERRCODE='23514';
            ELSIF NEW.ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA') AND NOT EXISTS(
                SELECT 1 FROM paper_trading_runs p WHERE p.paper_run_id=NEW.ref_id AND p.canonical_account_id=NEW.account_id) THEN
                RAISE EXCEPTION 'TENANT_ACCESS_DENIED: ledger funding mismatch' USING ERRCODE='23514';
            END IF;
        WHEN 'sim_asset_funding_receipts' THEN
            IF NOT EXISTS(SELECT 1 FROM paper_trading_runs p WHERE p.paper_run_id=NEW.paper_run_id AND p.canonical_account_id=NEW.account_id)
                OR (SELECT count(*) FROM ledger_entries e WHERE e.entry_id IN (NEW.cash_entry_id,NEW.cash_contra_entry_id,NEW.asset_entry_id,NEW.asset_contra_entry_id)
                    AND e.account_id=NEW.account_id AND e.ref_id=NEW.paper_run_id AND e.ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA'))<>4 THEN
                RAISE EXCEPTION 'TENANT_ACCESS_DENIED: funding receipt mismatch' USING ERRCODE='23514';
            END IF;
    END CASE;
    RETURN NEW;
END;
$$;
DO $$
DECLARE relation_name text;
BEGIN
    FOREACH relation_name IN ARRAY ARRAY['backtest_configs','backtest_publish_records','paper_trading_runs',
        'paper_run_schedule_fires','ledger_entries','sim_asset_funding_receipts'] LOOP
        EXECUTE format('CREATE CONSTRAINT TRIGGER trg_tenant_additional_links AFTER INSERT OR UPDATE ON %I DEFERRABLE INITIALLY IMMEDIATE FOR EACH ROW EXECUTE FUNCTION nq_check_additional_tenant_links()',relation_name);
    END LOOP;
END;
$$;

-- 补齐旧工作投影的资格，不改订单或运行状态；暂停租户的身份仍保留以便显式恢复。
DO $$
DECLARE identity uuid;
BEGIN
    FOR identity IN SELECT tenant_id FROM tenants LOOP
        PERFORM set_config('nq.tenant_id',identity::text,true);
        UPDATE tenant_background_work w SET eligible=(r.status IN ('CREATED','DISPATCHING','RUNNING'))
            FROM strategy_runs r WHERE w.work_kind='STRATEGY' AND w.work_id=r.strategy_run_id;
        UPDATE tenant_background_work w SET eligible=(o.status='ACCEPTED')
            FROM orders o WHERE w.work_kind='ORDER' AND w.work_id=o.order_id;
    END LOOP;
    PERFORM set_config('nq.tenant_id','',true);
END;
$$;

-- 未提交订单与暂停的旧 Paper schedule 不消耗有界扫描窗口。
CREATE FUNCTION nq_refresh_work_eligibility() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $$
BEGIN
    IF TG_TABLE_NAME='orders' THEN
        UPDATE tenant_background_work SET eligible=(NEW.status='ACCEPTED') WHERE work_kind='ORDER' AND work_id=NEW.order_id;
    ELSIF TG_TABLE_NAME='paper_run_schedules' THEN
        UPDATE tenant_background_work SET eligible=(NEW.status='ENABLED') WHERE work_kind='PAPER_SCHEDULE' AND work_id=NEW.schedule_id;
    END IF;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION nq_refresh_work_eligibility() FROM PUBLIC;
CREATE TRIGGER trg_zz_refresh_order_work AFTER INSERT OR UPDATE OF status ON orders FOR EACH ROW EXECUTE FUNCTION nq_refresh_work_eligibility();
CREATE TRIGGER trg_zz_refresh_paper_schedule_work AFTER INSERT OR UPDATE OF status ON paper_run_schedules FOR EACH ROW EXECUTE FUNCTION nq_refresh_work_eligibility();
