-- 只增加平台专用只读投影；角色由受控安装步骤创建，迁移本身不需要 CREATEROLE。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

CREATE FUNCTION nq_platform_tenants(p_actor bigint,p_session text,p_version bigint,
    p_page integer,p_size integer,p_search text,p_enabled boolean,p_ascending boolean)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public,pg_temp AS $$
DECLARE result jsonb;
BEGIN
    PERFORM nq_require_platform_session(p_actor,p_session,p_version);
    IF p_page IS NULL OR p_page<0 OR p_page>100000 OR p_size IS NULL OR p_size<1 OR p_size>100
        OR p_search IS NULL OR length(p_search)>64 OR p_ascending IS NULL THEN
        RAISE EXCEPTION 'INVALID_PLATFORM_PAGE' USING ERRCODE='22023';
    END IF;
    WITH filtered AS MATERIALIZED (
        SELECT r.tenant_id,u.id user_id,u.username,u.email_verified_at IS NOT NULL email_verified,
            r.created_at registered_at,u.enabled,r.status registration_status
        FROM demo_registrations r JOIN users u ON u.id=r.user_id
        WHERE (p_enabled IS NULL OR u.enabled=p_enabled)
            AND strpos(lower(u.username),lower(p_search))>0
    ), page AS MATERIALIZED (
        SELECT * FROM filtered ORDER BY
            CASE WHEN p_ascending THEN registered_at END ASC,
            CASE WHEN NOT p_ascending THEN registered_at END DESC,user_id
        LIMIT p_size OFFSET p_page::bigint*p_size
    ), items AS (
        SELECT p.*,s.account_id,c.suspended,c.requires_manual_resume,
            coalesce(run.status,paper.status,'NOT_BOUND') raw_sim_status
        FROM page p LEFT JOIN tenant_sim_accounts s ON s.tenant_id=p.tenant_id
        LEFT JOIN tenant_execution_controls c ON c.tenant_id=p.tenant_id
        LEFT JOIN paper_trading_runs paper ON paper.paper_run_id=s.bound_paper_run_id
        LEFT JOIN continuous_sim_runs run ON run.paper_run_id=s.bound_paper_run_id
    )
    SELECT jsonb_build_object('page',p_page,'size',p_size,'total',(SELECT count(*) FROM filtered),
        'items',coalesce((SELECT jsonb_agg(jsonb_build_object(
            'tenantId',tenant_id,'userId',user_id,'username',username,'emailVerified',email_verified,
            'registeredAt',registered_at,'enabled',enabled,
            'simAccountStatus',CASE WHEN account_id IS NOT NULL THEN 'INITIALIZED' ELSE registration_status END,
            'simStatus',CASE WHEN suspended THEN 'SUSPENDED' WHEN requires_manual_resume THEN 'MANUAL_RESUME_REQUIRED'
                ELSE raw_sim_status END)
            ORDER BY CASE WHEN p_ascending THEN registered_at END ASC,
                CASE WHEN NOT p_ascending THEN registered_at END DESC,user_id) FROM items),'[]'::jsonb),
        'overview',(SELECT jsonb_build_object('registeredUsers',count(*),
            'enabledUsers',count(*) FILTER(WHERE u.enabled),'disabledUsers',count(*) FILTER(WHERE NOT u.enabled),
            'runningSimCount',(SELECT count(*) FROM continuous_sim_runs run
                JOIN paper_trading_runs paper ON paper.paper_run_id=run.paper_run_id
                JOIN tenant_execution_controls c ON c.tenant_id=paper.tenant_id
                JOIN tenant_memberships m ON m.tenant_id=paper.tenant_id
                JOIN users owner_user ON owner_user.id=m.user_id
                WHERE run.status='RUNNING' AND NOT c.suspended AND NOT c.requires_manual_resume AND owner_user.enabled),
            'capacity',8,'asOf',CURRENT_TIMESTAMP,'source','DEMO_REGISTRATIONS_AND_CANONICAL_SIM')
            FROM demo_registrations r JOIN users u ON u.id=r.user_id)) INTO result;
    RETURN result;
END;
$$;
REVOKE ALL ON FUNCTION nq_platform_tenants(bigint,text,bigint,integer,integer,text,boolean,boolean) FROM PUBLIC;

CREATE FUNCTION nq_platform_tenant_summary(p_actor bigint,p_session text,p_version bigint,p_tenant uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public,pg_temp AS $$
DECLARE result jsonb;
BEGIN
    PERFORM nq_require_platform_session(p_actor,p_session,p_version);
    SELECT jsonb_build_object('tenantId',r.tenant_id,'userId',u.id,'username',u.username,
        'emailVerified',u.email_verified_at IS NOT NULL,'registeredAt',r.created_at,'enabled',u.enabled,
        'simAccountStatus',CASE WHEN s.account_id IS NOT NULL THEN 'INITIALIZED' ELSE r.status END,
        'simStatus',CASE WHEN c.suspended THEN 'SUSPENDED' WHEN c.requires_manual_resume THEN 'MANUAL_RESUME_REQUIRED'
            ELSE coalesce(run.status,paper.status,'NOT_BOUND') END,
        'suspended',c.suspended,'requiresManualResume',c.requires_manual_resume,'executionEpoch',c.execution_epoch)
    INTO result FROM demo_registrations r JOIN users u ON u.id=r.user_id
    LEFT JOIN tenant_sim_accounts s ON s.tenant_id=r.tenant_id
    LEFT JOIN tenant_execution_controls c ON c.tenant_id=r.tenant_id
    LEFT JOIN paper_trading_runs paper ON paper.paper_run_id=s.bound_paper_run_id
    LEFT JOIN continuous_sim_runs run ON run.paper_run_id=s.bound_paper_run_id
    WHERE r.tenant_id=p_tenant;
    RETURN result;
END;
$$;
REVOKE ALL ON FUNCTION nq_platform_tenant_summary(bigint,text,bigint,uuid) FROM PUBLIC;

CREATE FUNCTION nq_platform_sim_facts(p_actor bigint,p_session text,p_version bigint,p_tenant uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public,pg_temp AS $$
DECLARE result jsonb;
BEGIN
    PERFORM nq_require_platform_session(p_actor,p_session,p_version);
    WITH identity AS MATERIALIZED (
        SELECT s.*,r.cash_entry_id,r.contra_entry_id FROM tenant_sim_accounts s
        JOIN demo_registrations r ON r.tenant_id=s.tenant_id AND r.status='READY'
        JOIN accounts a ON a.account_id=s.account_id AND a.tenant_id=s.tenant_id AND a.venue='PAPER'
        JOIN exchange_accounts e ON e.exchange_account_id=s.exchange_account_id AND e.tenant_id=s.tenant_id
            AND e.legacy_account_id=s.account_id AND e.exchange_code='PAPER' AND e.trade_env='SIM'
        WHERE s.tenant_id=p_tenant
    ), funding AS (
        SELECT i.account_id,e.delta initial_capital,e.ts funded_at FROM identity i
        JOIN ledger_entries e ON e.entry_id=i.cash_entry_id AND e.account_id=i.account_id
            AND e.ref_type='SIM_FUNDING_CASH' AND e.ref_id=i.reserved_paper_run_id AND e.currency='USDT' AND e.delta>0
        JOIN ledger_entries contra ON contra.entry_id=i.contra_entry_id AND contra.account_id=i.account_id
            AND contra.ref_type='SIM_FUNDING_CONTRA' AND contra.ref_id=e.ref_id AND contra.currency=e.currency AND contra.delta=-e.delta
        WHERE (SELECT count(*) FROM ledger_entries all_funding WHERE all_funding.account_id=i.account_id
            AND all_funding.ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA'))=2
    ), snapshots AS MATERIALIZED (
        SELECT e.equity_snapshot_id,e.snapshot_time,e.created_at,e.total_equity,e.cash_balance,e.realized_pnl,e.unrealized_pnl,
            e.drawdown,e.valuation_source,e.valuation_method,e.valuation_status,e.valuation_details
        FROM identity i JOIN equity_curve_snapshots e ON e.paper_run_id=i.bound_paper_run_id
        JOIN funding f ON f.account_id=i.account_id
        WHERE e.source='CANONICAL_SIM_PROJECTION' AND e.valuation_status='VERIFIED' AND e.valuation_currency='USDT'
            AND e.valuation_method='CANONICAL_SIM_LEDGER_AND_FROZEN_MARK_V1'
            AND e.valuation_time=e.snapshot_time AND e.snapshot_time<=CURRENT_TIMESTAMP AND e.snapshot_time>f.funded_at
            AND e.valuation_details->>'paperRunId'=i.bound_paper_run_id
            AND e.valuation_details->>'canonicalAccountId'=i.account_id::text
            AND e.valuation_details->>'fundingBudget' IS NOT NULL
            AND (e.valuation_details->>'fundingBudget')::numeric=f.initial_capital
            AND e.total_equity-f.initial_capital=e.realized_pnl+e.unrealized_pnl
        ORDER BY e.snapshot_time DESC,e.equity_snapshot_id DESC LIMIT 1000
    )
    SELECT jsonb_build_object('tenantId',i.tenant_id,'exchangeAccountId',i.exchange_account_id,
        'paperRunId',i.bound_paper_run_id,'initialCapital',f.initial_capital::text,'fundedAt',f.funded_at,
        'currentCash',(SELECT balance::text FROM account_snapshots cash WHERE cash.account_id=i.account_id
            AND cash.currency='USDT' AND cash.trade_env='SIM' AND cash.balance_basis='LEDGER_CASH_PROJECTION'
            ORDER BY snapshot_id DESC LIMIT 1),
        'orderCount',(SELECT count(*) FROM orders o WHERE o.account_id=i.account_id AND o.trade_env='SIM'),
        'tradeCount',(SELECT count(*) FROM trades t WHERE t.account_id=i.account_id AND t.trade_env='SIM'),
        'latestLedgerAt',(SELECT max(ts) FROM ledger_entries l WHERE l.account_id=i.account_id),
        'latestLedgerRecordedAt',(SELECT max(created_at) FROM ledger_entries l WHERE l.account_id=i.account_id),
        'asOf',CURRENT_TIMESTAMP,
        'snapshots',coalesce((SELECT jsonb_agg(jsonb_build_object('id',equity_snapshot_id,
            'at',snapshot_time,'recordedAt',created_at,'equity',total_equity::text,'cash',cash_balance::text,
            'realizedPnl',realized_pnl::text,'unrealizedPnl',unrealized_pnl::text,'drawdown',drawdown::text,
            'valuationSource',valuation_source,'valuationMethod',valuation_method,'valuationStatus',valuation_status)
            ORDER BY snapshot_time,equity_snapshot_id) FROM snapshots),'[]'::jsonb))
    INTO result FROM identity i LEFT JOIN funding f ON f.account_id=i.account_id;
    RETURN result;
END;
$$;
REVOKE ALL ON FUNCTION nq_platform_sim_facts(bigint,text,bigint,uuid) FROM PUBLIC;

-- 安装后只有不可登录的投影函数所有者能跨租户 SELECT；调用登录角色没有基础表权限或角色继承。
CREATE FUNCTION nq_is_platform_projection_owner() RETURNS boolean
LANGUAGE sql STABLE SET search_path TO pg_catalog,public,pg_temp AS $$
    -- 未完成专用角色安装时，函数仍归迁移所有者，绝不能因此给表所有者增加跨租户读取能力。
    SELECT current_user=r.rolname AND p.proowner<>economic_root.relowner
        AND NOT (r.rolcanlogin OR r.rolsuper OR r.rolbypassrls OR r.rolcreaterole OR r.rolcreatedb)
        AND NOT has_schema_privilege(r.rolname,'public','CREATE')
        AND NOT EXISTS(SELECT 1 FROM pg_auth_members WHERE member=r.oid)
    FROM pg_proc p JOIN pg_roles r ON r.oid=p.proowner
    JOIN pg_class economic_root ON economic_root.oid='public.accounts'::regclass
    WHERE p.oid='public.nq_platform_tenants(bigint,text,bigint,integer,integer,text,boolean,boolean)'::regprocedure
$$;
DO $$
DECLARE relation_name text;
BEGIN
    FOREACH relation_name IN ARRAY ARRAY['accounts','exchange_accounts','tenant_sim_accounts','tenant_execution_controls',
        'paper_trading_runs','continuous_sim_runs','equity_curve_snapshots','account_snapshots','ledger_entries','orders','trades'] LOOP
        EXECUTE format('CREATE POLICY platform_projection_read ON %I FOR SELECT USING(public.nq_is_platform_projection_owner())',relation_name);
    END LOOP;
END;
$$;
COMMENT ON FUNCTION nq_platform_sim_facts(bigint,text,bigint,uuid) IS '只读受控投影；资金仅核验canonical注资身份，绩效仅返回既有VERIFIED冻结估值，不返回原始账务';
