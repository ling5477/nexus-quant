-- 所有新增检查只验证关联，不重写不可变经济行或冻结研究内容。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

CREATE FUNCTION nq_entity_tenant(kind text, identity text) RETURNS uuid
LANGUAGE plpgsql STABLE SET search_path TO pg_catalog, public AS $$
BEGIN
    IF identity IS NULL THEN RETURN NULL; END IF;
    CASE kind
        WHEN 'account' THEN RETURN (SELECT tenant_id FROM accounts WHERE account_id=identity::bigint);
        WHEN 'exchange_account' THEN RETURN (SELECT tenant_id FROM exchange_accounts WHERE exchange_account_id=identity::bigint);
        WHEN 'strategy' THEN RETURN (SELECT tenant_id FROM strategy_definitions WHERE strategy_id=identity);
        WHEN 'version' THEN RETURN (SELECT tenant_id FROM strategy_versions WHERE strategy_version_id=identity);
        WHEN 'research' THEN RETURN (SELECT tenant_id FROM research_configs WHERE research_config_id=identity);
        WHEN 'backtest_config' THEN RETURN (SELECT tenant_id FROM backtest_configs WHERE backtest_config_id=identity);
        WHEN 'dataset' THEN RETURN (SELECT tenant_id FROM marketdata_datasets WHERE dataset_id=identity::uuid);
        WHEN 'backtest' THEN RETURN (SELECT c.tenant_id FROM backtest_runs r JOIN backtest_configs c USING(backtest_config_id) WHERE r.backtest_run_id=identity);
        WHEN 'evaluation' THEN RETURN (SELECT nq_entity_tenant('backtest',r.backtest_run_id) FROM backtest_eval_reports r WHERE r.eval_report_id=identity);
        WHEN 'publication' THEN RETURN (SELECT nq_entity_tenant('backtest',p.backtest_run_id) FROM backtest_publish_records p WHERE p.publish_record_id=identity);
        WHEN 'paper' THEN RETURN (SELECT tenant_id FROM paper_trading_runs WHERE paper_run_id=identity);
        WHEN 'strategy_run' THEN RETURN (SELECT nq_entity_tenant('account',r.account_id::text) FROM strategy_runs r WHERE r.strategy_run_id=identity);
        WHEN 'order' THEN RETURN (SELECT nq_entity_tenant('account',o.account_id::text) FROM orders o WHERE o.order_id=identity);
        ELSE RAISE EXCEPTION 'unsupported tenant parent kind' USING ERRCODE='22023';
    END CASE;
END;
$$;

CREATE FUNCTION nq_require_tenant_parent(tenant uuid,kind text,identity text) RETURNS void
LANGUAGE plpgsql STABLE SET search_path TO pg_catalog, public AS $$
DECLARE parent uuid;
BEGIN
    IF identity IS NULL THEN RETURN; END IF;
    parent := nq_entity_tenant(kind,identity);
    IF tenant IS NULL OR parent IS NULL OR tenant <> parent THEN
        RAISE EXCEPTION 'TENANT_ACCESS_DENIED: invalid parent' USING ERRCODE='23514';
    END IF;
END;
$$;

CREATE FUNCTION nq_validate_tenant_links(relation_name text,row_data jsonb) RETURNS void
LANGUAGE plpgsql SET search_path TO pg_catalog, public AS $$
DECLARE tenant uuid; linked text; is_demo boolean;
BEGIN
    CASE relation_name
        WHEN 'accounts', 'exchange_accounts', 'strategy_definitions' THEN
            tenant := (row_data->>'tenant_id')::uuid;
        WHEN 'backtest_configs' THEN
            tenant := (row_data->>'tenant_id')::uuid;
            linked := row_data->>'dataset_id';
            IF linked IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM marketdata_datasets d WHERE d.dataset_id=linked::uuid
                    AND (d.tenant_id=tenant OR (d.visibility='SHARED_READONLY' AND EXISTS (
                        SELECT 1 FROM marketdata_dataset_publications p WHERE p.dataset_id=d.dataset_id
                            AND p.status='APPROVED' AND p.expires_at>CURRENT_TIMESTAMP
                            AND p.permitted_from<=d.start_time AND p.permitted_until>=d.end_time)))) THEN
                RAISE EXCEPTION 'TENANT_ACCESS_DENIED: dataset not admitted' USING ERRCODE='23514';
            END IF;
        WHEN 'backtest_runs' THEN
            tenant := nq_entity_tenant('backtest_config',row_data->>'backtest_config_id');
            PERFORM nq_require_tenant_parent(tenant,'research',row_data->>'research_config_id');
            PERFORM nq_require_tenant_parent(tenant,'strategy',row_data->>'source_strategy_id');
            PERFORM nq_require_tenant_parent(tenant,'version',row_data->>'strategy_version_id');
            IF NOT EXISTS (SELECT 1 FROM backtest_configs c JOIN research_configs r USING(research_config_id)
                WHERE c.backtest_config_id=row_data->>'backtest_config_id'
                    AND c.research_config_id=row_data->>'research_config_id'
                    AND r.source_strategy_id=row_data->>'source_strategy_id') THEN
                RAISE EXCEPTION 'inconsistent backtest parents' USING ERRCODE='23514';
            END IF;
        WHEN 'backtest_publish_records' THEN
            tenant := nq_entity_tenant('backtest',row_data->>'backtest_run_id');
            PERFORM nq_require_tenant_parent(tenant,'research',row_data->>'research_config_id');
            PERFORM nq_require_tenant_parent(tenant,'backtest_config',row_data->>'backtest_config_id');
            PERFORM nq_require_tenant_parent(tenant,'strategy',row_data->>'source_strategy_id');
            PERFORM nq_require_tenant_parent(tenant,'strategy',row_data->>'target_strategy_definition_id');
            PERFORM nq_require_tenant_parent(tenant,'version',row_data->>'strategy_version_id');
            PERFORM nq_require_tenant_parent(tenant,'evaluation',row_data->>'eval_report_id');
            IF row_data->>'eval_report_id' IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM backtest_eval_reports e WHERE e.eval_report_id=row_data->>'eval_report_id'
                    AND e.backtest_run_id=row_data->>'backtest_run_id') THEN
                RAISE EXCEPTION 'evaluation belongs to another run' USING ERRCODE='23514';
            END IF;
        WHEN 'paper_trading_runs' THEN
            tenant := (row_data->>'tenant_id')::uuid;
            PERFORM nq_require_tenant_parent(tenant,'publication',row_data->>'publish_id');
        WHEN 'strategy_runs', 'strategy_schedules' THEN
            tenant := nq_entity_tenant('account',row_data->>'account_id');
            PERFORM nq_require_tenant_parent(tenant,'strategy',row_data->>'strategy_id');
            IF NOT EXISTS (SELECT 1 FROM strategy_definitions s WHERE s.strategy_id=row_data->>'strategy_id'
                AND s.account_id=(row_data->>'account_id')::bigint) THEN
                RAISE EXCEPTION 'strategy account mismatch' USING ERRCODE='23514';
            END IF;
        WHEN 'strategy_run_dispatch_work' THEN
            tenant := nq_entity_tenant('account',row_data->>'account_id');
            PERFORM nq_require_tenant_parent(tenant,'strategy_run',row_data->>'strategy_run_id');
            IF NOT EXISTS (SELECT 1 FROM strategy_runs r WHERE r.strategy_run_id=row_data->>'strategy_run_id'
                AND r.account_id=(row_data->>'account_id')::bigint) THEN
                RAISE EXCEPTION 'dispatch account mismatch' USING ERRCODE='23514';
            END IF;
        WHEN 'orders' THEN
            tenant := nq_entity_tenant('account',row_data->>'account_id');
            PERFORM nq_require_tenant_parent(tenant,'strategy_run',row_data->>'strategy_run_id');
            IF row_data->>'strategy_run_id' IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM strategy_runs r WHERE r.strategy_run_id=row_data->>'strategy_run_id'
                    AND r.account_id=(row_data->>'account_id')::bigint) THEN
                RAISE EXCEPTION 'order account mismatch' USING ERRCODE='23514';
            END IF;
        WHEN 'trades' THEN
            tenant := nq_entity_tenant('account',row_data->>'account_id');
            PERFORM nq_require_tenant_parent(tenant,'order',row_data->>'order_id');
            PERFORM nq_require_tenant_parent(tenant,'strategy_run',row_data->>'strategy_run_id');
            IF NOT EXISTS (SELECT 1 FROM orders o WHERE o.order_id=row_data->>'order_id'
                AND o.account_id=(row_data->>'account_id')::bigint
                AND o.strategy_run_id IS NOT DISTINCT FROM row_data->>'strategy_run_id') THEN
                RAISE EXCEPTION 'trade order identity mismatch' USING ERRCODE='23514';
            END IF;
        WHEN 'strategy_sim_decisions' THEN
            tenant := nq_entity_tenant('paper',row_data->>'paper_run_id');
            PERFORM nq_require_tenant_parent(tenant,'account',row_data->>'canonical_account_id');
            PERFORM nq_require_tenant_parent(tenant,'version',row_data->>'strategy_version_id');
            PERFORM nq_require_tenant_parent(tenant,'strategy_run',row_data->>'strategy_run_id');
            PERFORM nq_require_tenant_parent(tenant,'order',row_data->>'order_id');
            IF NOT EXISTS (SELECT 1 FROM paper_trading_runs p WHERE p.paper_run_id=row_data->>'paper_run_id'
                AND p.canonical_account_id=(row_data->>'canonical_account_id')::bigint
                AND p.strategy_version_id=row_data->>'strategy_version_id') THEN
                RAISE EXCEPTION 'decision run identity mismatch' USING ERRCODE='23514';
            END IF;
        WHEN 'sim_trades' THEN
            tenant := nq_entity_tenant('backtest',row_data->>'backtest_run_id');
            IF NOT EXISTS (SELECT 1 FROM sim_orders o WHERE o.sim_order_id=row_data->>'sim_order_id'
                AND o.backtest_run_id=row_data->>'backtest_run_id') THEN
                RAISE EXCEPTION 'sim trade belongs to another run' USING ERRCODE='23514';
            END IF;
        WHEN 'continuous_sim_runs' THEN
            tenant := nq_entity_tenant('paper',row_data->>'paper_run_id');
            PERFORM nq_require_tenant_parent(tenant,'version',row_data->>'strategy_version_id');
        WHEN 'backtest_evaluation_requests' THEN
            tenant := nq_entity_tenant('backtest',row_data->>'backtest_run_id');
            IF NOT EXISTS (SELECT 1 FROM tenant_memberships m WHERE m.user_id=(row_data->>'owner_user_id')::bigint
                AND m.tenant_id=tenant) THEN
                RAISE EXCEPTION 'evaluation owner mismatch' USING ERRCODE='23514';
            END IF;
        ELSE RAISE EXCEPTION 'unsupported tenant relationship' USING ERRCODE='22023';
    END CASE;
    IF tenant IS NULL THEN
        RAISE EXCEPTION 'TENANT_ACCESS_DENIED: missing parent' USING ERRCODE='23514';
    END IF;
    SELECT tenant_kind='DEMO' INTO is_demo FROM tenants WHERE tenant_id=tenant;
    IF is_demo AND row_data ? 'trade_env' AND row_data->>'trade_env' <> 'SIM' THEN
        RAISE EXCEPTION 'TENANT_ACCESS_DENIED: Demo requires SIM' USING ERRCODE='23514';
    END IF;
    IF is_demo AND ((relation_name='accounts' AND row_data->>'venue'<>'PAPER')
        OR (relation_name='exchange_accounts' AND row_data->>'exchange_code'<>'PAPER')) THEN
        RAISE EXCEPTION 'TENANT_ACCESS_DENIED: Demo account requires PAPER' USING ERRCODE='23514';
    END IF;
END;
$$;

CREATE FUNCTION nq_check_tenant_links() RETURNS trigger
LANGUAGE plpgsql SET search_path TO pg_catalog, public AS $$
BEGIN
    PERFORM nq_validate_tenant_links(TG_TABLE_NAME,to_jsonb(NEW));
    RETURN NEW;
END;
$$;

DO $$
DECLARE item text[]; value jsonb; last_id text; upper_id text; batch_last text;
BEGIN
    FOREACH item SLICE 1 IN ARRAY ARRAY[
        ['accounts','account_id'], ['exchange_accounts','exchange_account_id'], ['strategy_definitions','strategy_id'],
        ['backtest_configs','backtest_config_id'], ['backtest_runs','backtest_run_id'],
        ['backtest_publish_records','publish_record_id'], ['paper_trading_runs','paper_run_id'],
        ['strategy_runs','strategy_run_id'], ['strategy_schedules','schedule_job_id'],
        ['strategy_run_dispatch_work','strategy_run_id'], ['orders','order_id'], ['trades','trade_id'],
        ['strategy_sim_decisions','decision_id'], ['sim_trades','sim_trade_id'],
        ['continuous_sim_runs','paper_run_id'], ['backtest_evaluation_requests','request_id']
    ] LOOP
        last_id := '';
        EXECUTE format('SELECT max(%I::text) FROM %I',item[2],item[1]) INTO upper_id;
        LOOP
            batch_last := NULL;
            FOR value IN EXECUTE format('SELECT to_jsonb(t) FROM %I t WHERE %I::text > $1 AND %I::text <= $2 ORDER BY %I::text LIMIT 1000',
                item[1],item[2],item[2],item[2]) USING last_id,upper_id LOOP
                PERFORM nq_validate_tenant_links(item[1],value);
                batch_last := value->>item[2];
            END LOOP;
            EXIT WHEN batch_last IS NULL;
            last_id := batch_last;
        END LOOP;
        EXECUTE format('CREATE CONSTRAINT TRIGGER trg_tenant_links AFTER INSERT OR UPDATE ON %I DEFERRABLE INITIALLY IMMEDIATE FOR EACH ROW EXECUTE FUNCTION nq_check_tenant_links()',item[1]);
    END LOOP;
END;
$$;

CREATE TABLE audit_log_tenant_scopes (
    audit_id bigint PRIMARY KEY REFERENCES audit_logs(id) ON DELETE RESTRICT,
    classification varchar(24) NOT NULL,
    tenant_id uuid REFERENCES tenants(tenant_id),
    CHECK ((classification='TENANT_PRIVATE' AND tenant_id IS NOT NULL)
        OR (classification='PLATFORM_INTERNAL' AND tenant_id IS NULL))
);
CREATE TABLE event_tenant_scopes (
    event_id varchar(64) PRIMARY KEY REFERENCES event_store(event_id) ON DELETE RESTRICT,
    classification varchar(24) NOT NULL,
    tenant_id uuid REFERENCES tenants(tenant_id),
    CHECK ((classification='TENANT_PRIVATE' AND tenant_id IS NOT NULL)
        OR (classification='PLATFORM_INTERNAL' AND tenant_id IS NULL))
);
CREATE TABLE risk_event_tenant_scopes (
    risk_event_id varchar(64) PRIMARY KEY REFERENCES risk_events(risk_event_id) ON DELETE RESTRICT,
    classification varchar(24) NOT NULL,
    tenant_id uuid REFERENCES tenants(tenant_id),
    CHECK ((classification='TENANT_PRIVATE' AND tenant_id IS NOT NULL)
        OR (classification='PLATFORM_INTERNAL' AND tenant_id IS NULL))
);

-- 无持久可信作用域的历史事件保守保留为内部记录，不根据 actor、trace 或 payload 猜测归属。
INSERT INTO audit_log_tenant_scopes SELECT id,'PLATFORM_INTERNAL',NULL FROM audit_logs;
INSERT INTO event_tenant_scopes SELECT event_id,'PLATFORM_INTERNAL',NULL FROM event_store;
INSERT INTO risk_event_tenant_scopes SELECT risk_event_id,'PLATFORM_INTERNAL',NULL FROM risk_events;
CREATE INDEX idx_audit_tenant_scope ON audit_log_tenant_scopes(tenant_id,audit_id);
CREATE INDEX idx_event_tenant_scope ON event_tenant_scopes(tenant_id,event_id);
CREATE INDEX idx_risk_tenant_scope ON risk_event_tenant_scopes(tenant_id,risk_event_id);
COMMENT ON TABLE audit_log_tenant_scopes IS '审计记录的独立可信作用域；与新审计同事务写入，不改历史 payload';
COMMENT ON TABLE event_tenant_scopes IS '事件作用域；历史未证明归属的数据不对 Demo 公开';
COMMENT ON TABLE risk_event_tenant_scopes IS '风险事件作用域；不依赖多态文本或 actor 推断租户';

CREATE FUNCTION nq_record_event_tenant_scope() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public AS $$
DECLARE tenant uuid := nq_current_tenant_id(); category text;
BEGIN
    category := CASE WHEN tenant IS NULL THEN 'PLATFORM_INTERNAL' ELSE 'TENANT_PRIVATE' END;
    CASE TG_TABLE_NAME
        WHEN 'audit_logs' THEN INSERT INTO audit_log_tenant_scopes VALUES(NEW.id,category,tenant);
        WHEN 'event_store' THEN INSERT INTO event_tenant_scopes VALUES(NEW.event_id,category,tenant);
        WHEN 'risk_events' THEN INSERT INTO risk_event_tenant_scopes VALUES(NEW.risk_event_id,category,tenant);
    END CASE;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION nq_record_event_tenant_scope() FROM PUBLIC;
CREATE TRIGGER trg_audit_tenant_scope AFTER INSERT ON audit_logs FOR EACH ROW EXECUTE FUNCTION nq_record_event_tenant_scope();
CREATE TRIGGER trg_event_tenant_scope AFTER INSERT ON event_store FOR EACH ROW EXECUTE FUNCTION nq_record_event_tenant_scope();
CREATE TRIGGER trg_risk_tenant_scope AFTER INSERT ON risk_events FOR EACH ROW EXECUTE FUNCTION nq_record_event_tenant_scope();
