package com.guidinglight.nexusquant.app.smoke;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 本例专有读取角色与不可登录的投影所有者；不复用历史或共享数据库角色。 */
public final class PlatformReadTestRoles implements AutoCloseable {
    private final TenantIsolationTestDatabase db;
    private final String owner="nq_read_owner_"+UUID.randomUUID().toString().replace("-", "");
    private final String reader="nq_read_"+UUID.randomUUID().toString().replace("-", "");
    private final String password=UUID.randomUUID().toString();
    public final JdbcTemplate jdbc;
    private static final List<String> FUNCTIONS=List.of(
            "nq_platform_tenants(bigint,text,bigint,integer,integer,text,boolean,boolean)",
            "nq_platform_tenant_summary(bigint,text,bigint,uuid)",
            "nq_platform_sim_facts(bigint,text,bigint,uuid)");

    private PlatformReadTestRoles(TenantIsolationTestDatabase db) {
        this.db=db;
        db.admin.execute("CREATE ROLE "+owner+" NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS");
        db.admin.execute("CREATE ROLE "+reader+" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS PASSWORD '"+password+"'");
        db.admin.execute("GRANT USAGE ON SCHEMA public TO "+owner+","+reader);
        for (String grant:List.of(
                "SELECT(id,username,email_verified_at,enabled) ON users",
                "SELECT(registration_id,user_id,tenant_id,status,created_at,cash_entry_id,contra_entry_id) ON demo_registrations",
                "SELECT(tenant_id,status) ON tenants",
                "SELECT(tenant_id,user_id,status) ON tenant_memberships",
                "SELECT ON tenant_sim_accounts,tenant_execution_controls",
                "SELECT(account_id,tenant_id,venue) ON accounts",
                "SELECT(exchange_account_id,tenant_id,legacy_account_id,exchange_code,trade_env) ON exchange_accounts",
                "SELECT(paper_run_id,tenant_id,status) ON paper_trading_runs",
                "SELECT(paper_run_id,status) ON continuous_sim_runs",
                "SELECT ON equity_curve_snapshots",
                "SELECT(snapshot_id,account_id,currency,trade_env,balance_basis,balance) ON account_snapshots",
                "SELECT(entry_id,account_id,ref_type,ref_id,currency,delta,ts,created_at) ON ledger_entries",
                "SELECT(account_id,trade_env) ON orders,trades",
                "EXECUTE ON FUNCTION nq_require_platform_session(bigint,text,bigint)"))
            db.admin.execute("GRANT "+grant+" TO "+owner);
        for (String function:FUNCTIONS) {
            db.admin.execute("ALTER FUNCTION "+function+" OWNER TO "+owner);
            db.admin.execute("GRANT EXECUTE ON FUNCTION "+function+" TO "+reader);
        }
        jdbc=new JdbcTemplate(new DriverManagerDataSource(db.url(),reader,password));
        jdbc.setQueryTimeout(5);
    }

    public static PlatformReadTestRoles create(TenantIsolationTestDatabase db) { return new PlatformReadTestRoles(db); }

    public String[] arguments() {
        return new String[]{"--nq.demo.platform-read.jdbc-url="+db.url(),
                "--nq.demo.platform-read.username="+reader,"--nq.demo.platform-read.password="+password};
    }

    @Override public void close() {
        // 先把函数交还本例原迁移所有者，再撤销此例授予的权限，避免 cluster 级角色残留。
        for (String function:FUNCTIONS) db.admin.execute("ALTER FUNCTION "+function+" OWNER TO "+db.owner);
        db.admin.execute("DROP OWNED BY "+reader);
        db.admin.execute("DROP OWNED BY "+owner);
        db.admin.execute("DROP ROLE "+reader);
        db.admin.execute("DROP ROLE "+owner);
    }
}
