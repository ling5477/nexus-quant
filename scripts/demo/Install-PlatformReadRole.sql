-- 仅由安装管理员通过本机 psql 执行；不是应用迁移，也不是公共 HTTP 能力。
-- 参数：-v projection_owner=<全新NOLOGIN角色> -v reader_role=<全新LOGIN角色>
\set ON_ERROR_STOP on
BEGIN;
SELECT format('CREATE ROLE %I NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS', :'projection_owner') \gexec
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS', :'reader_role') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I,%I', :'projection_owner', :'reader_role') \gexec
SELECT format('GRANT SELECT(id,username,email_verified_at,enabled) ON users TO %I', :'projection_owner') \gexec
SELECT format('GRANT SELECT(registration_id,user_id,tenant_id,status,created_at,cash_entry_id,contra_entry_id) ON demo_registrations TO %I', :'projection_owner') \gexec
-- PostgreSQL 可同时求值原 tenant policy，给其身份检查所需的最小只读列，不修改原隔离规则。
SELECT format('GRANT SELECT(tenant_id,status) ON tenants TO %I', :'projection_owner') \gexec
SELECT format('GRANT SELECT(tenant_id,user_id,status) ON tenant_memberships TO %I', :'projection_owner') \gexec
SELECT format('GRANT SELECT ON tenant_sim_accounts,tenant_execution_controls TO %I', :'projection_owner') \gexec
SELECT format('GRANT SELECT(account_id,tenant_id,venue) ON accounts TO %I', :'projection_owner') \gexec
SELECT format('GRANT SELECT(exchange_account_id,tenant_id,legacy_account_id,exchange_code,trade_env) ON exchange_accounts TO %I', :'projection_owner') \gexec
SELECT format('GRANT SELECT(paper_run_id,tenant_id,status) ON paper_trading_runs TO %I', :'projection_owner') \gexec
SELECT format('GRANT SELECT(paper_run_id,status) ON continuous_sim_runs TO %I', :'projection_owner') \gexec
SELECT format('GRANT SELECT ON equity_curve_snapshots TO %I', :'projection_owner') \gexec
SELECT format('GRANT SELECT(snapshot_id,account_id,currency,trade_env,balance_basis,balance) ON account_snapshots TO %I', :'projection_owner') \gexec
SELECT format('GRANT SELECT(entry_id,account_id,ref_type,ref_id,currency,delta,ts,created_at) ON ledger_entries TO %I', :'projection_owner') \gexec
SELECT format('GRANT SELECT(account_id,trade_env) ON orders,trades TO %I', :'projection_owner') \gexec
SELECT format('GRANT EXECUTE ON FUNCTION nq_require_platform_session(bigint,text,bigint) TO %I', :'projection_owner') \gexec
SELECT format('ALTER FUNCTION %s OWNER TO %I', signature, :'projection_owner')
FROM (VALUES
    ('nq_platform_tenants(bigint,text,bigint,integer,integer,text,boolean,boolean)'),
    ('nq_platform_tenant_summary(bigint,text,bigint,uuid)'),
    ('nq_platform_sim_facts(bigint,text,bigint,uuid)')) f(signature) \gexec
SELECT format('GRANT EXECUTE ON FUNCTION %s TO %I', signature, :'reader_role')
FROM (VALUES
    ('nq_platform_tenants(bigint,text,bigint,integer,integer,text,boolean,boolean)'),
    ('nq_platform_tenant_summary(bigint,text,bigint,uuid)'),
    ('nq_platform_sim_facts(bigint,text,bigint,uuid)')) f(signature) \gexec
COMMIT;
-- psql 的受控口令提示不会把明文放到 SQL 历史或命令行参数。
\password :reader_role
