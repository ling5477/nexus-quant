package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcAuthUserRepository;
import com.guidinglight.nexusquant.common.tenant.*;
import com.guidinglight.nexusquant.tenant.infra.jdbc.TenantJdbcTemplateFactory;
import com.guidinglight.nexusquant.tenant.infra.jdbc.TenantTransactionManager;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 每例独立数据库、NOLOGIN owner 与无角色继承的运行账号；不接触用户数据库。 */
public final class TenantIsolationTestDatabase implements AutoCloseable {
    static final String PASSWORD = "SyntheticTenantIsolation123";
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    final ReleaseBaselineTestDatabase database;
    public final JdbcTemplate admin;
    final String owner = "nq_owner_" + UUID.randomUUID().toString().replace("-", "");
    public final String runtime = "nq_runtime_" + UUID.randomUUID().toString().replace("-", "");
    public final String runtimePassword = UUID.randomUUID().toString();
    private final String maintenanceUrl;
    private final String installerUser;
    private final String installerPassword;
    HikariDataSource pool;
    TenantTransactionManager manager;
    JdbcTemplate jdbc;

    private TenantIsolationTestDatabase(String url,String user,String password) throws Exception {
        maintenanceUrl=url;installerUser=user;installerPassword=password;
        database = ReleaseBaselineTestDatabase.create(url,user,password);
        admin = new JdbcTemplate(database.source());
        admin.execute("CREATE ROLE " + owner + " NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS");
        admin.execute("CREATE ROLE " + runtime + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD '" + runtimePassword + "'");
        String databaseName = admin.queryForObject("SELECT current_database()", String.class);
        admin.execute("GRANT CREATE ON DATABASE " + databaseName + " TO " + owner);
        admin.execute("ALTER SCHEMA public OWNER TO " + owner);
    }

    public static TenantIsolationTestDatabase create(String target) throws Exception {
        return create(target,System.getProperty("nq.tenant.pg.url"),System.getProperty("nq.tenant.pg.user"),System.getProperty("nq.tenant.pg.password"));
    }

    public static TenantIsolationTestDatabase create(String target,String url,String user,String password) throws Exception {
        var db = new TenantIsolationTestDatabase(url,user,password);
        try {
            db.migrate(target);
            if (target == null) db.runtime();
            return db;
        } catch (Exception | AssertionError failure) {
            try { db.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public Flyway migration(String target) {
        var config = Flyway.configure().dataSource(database.source()).locations("classpath:db/migration")
                .cleanDisabled(true).initSql("SET ROLE " + owner);
        if (target != null) config.target(target);
        return config.load();
    }

    public void migrate(String target) { var flyway = migration(target); flyway.migrate(); flyway.validate(); }

    public String url() { return database.url(); }
    public JdbcTemplate jdbc() { return jdbc; }
    public javax.sql.DataSource runtimeDataSource() { return pool; }
    public TenantIsolationTestDatabase historical(String target) throws Exception {
        return create(target,maintenanceUrl,installerUser,installerPassword);
    }

    /** 仅建立估值／快照夹具的同账户元数据父链；经济分录仍必须由原注资服务写入。 */
    public static void attachFundingRun(JdbcTemplate jdbc,long account,String run) {
        jdbc.update("INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env) VALUES(?,?,?,'SPOT_SMA_TARGET_V1','OKX',?,'SIM')",run,run,run,account);
        jdbc.update("INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot) VALUES(?,?,?,'{}')",run,run,run);
        jdbc.update("INSERT INTO backtest_configs(backtest_config_id,research_config_id,name) VALUES(?,?,?)",run,run,run);
        jdbc.update("INSERT INTO backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,strategy_snapshot,backtest_config_snapshot,requested_at) VALUES(?,?,?,?,'SUCCEEDED','{}','{}',now())",run,run,run,run);
        jdbc.update("INSERT INTO backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,source_strategy_id,publish_status,publish_name) VALUES(?,?,?,?,?,'SUCCEEDED',?)",run,run,run,run,run,run);
        jdbc.update("INSERT INTO paper_trading_runs(paper_run_id,publish_id,status,trade_env,exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at,canonical_account_id) VALUES(?,?,'STOPPED','SIM','OKX','SPOT','BTC-USDT','1m','fixture',now(),now(),?)",run,run,account);
    }

    public void runtime() {
        grantRuntimePrivileges(runtime);
        pool = new HikariDataSource();
        pool.setJdbcUrl(database.url()); pool.setUsername(runtime); pool.setPassword(runtimePassword);
        pool.setMaximumPoolSize(1); pool.setMinimumIdle(1); pool.setConnectionTimeout(5000);
        manager = new TenantTransactionManager(pool);
        jdbc = TenantJdbcTemplateFactory.create(pool, manager);
    }

    void grantRuntimePrivileges(String role) {
        if(!role.matches("nq_runtime_[a-f0-9]{32}"))throw new IllegalArgumentException("invalid isolated runtime role");
        admin.execute("GRANT USAGE ON SCHEMA public TO " + role);
        var protectedMetadata = Set.of("flyway_schema_history", "tenant_background_work", "tenant_continuous_slots", "tenant_work_scan_cursors");
        var readOnly = Set.of("tenants", "tenant_memberships", "roles", "user_roles", "tenant_execution_controls",
                "audit_log_tenant_scopes", "event_tenant_scopes", "risk_event_tenant_scopes", "marketdata_dataset_publications");
        for (String table : admin.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public'", String.class)) {
            if (protectedMetadata.contains(table)) continue;
            if (!table.matches("[a-z_]+")) throw new IllegalStateException("unexpected test table name");
            admin.execute("GRANT " + (readOnly.contains(table) ? "SELECT" : "SELECT,INSERT,UPDATE,DELETE")
                    + " ON " + table + " TO " + role);
        }
        admin.execute("GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO " + role);
        for (String function : admin.queryForList("""
                SELECT p.oid::regprocedure::text FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                WHERE n.nspname='public' AND p.proname IN ('nq_admit_strategy_work','nq_begin_strategy_dispatch',
                    'nq_project_strategy_run','nq_bind_strategy_effective','nq_create_ordinary_place_order',
                    'nq_decide_ordinary_place','nq_finish_strategy_cancel','nq_require_continuous_capacity',
                    'nq_resume_tenant_execution','nq_poll_tenant_work','nq_restore_tenant_work')
                """, String.class)) admin.execute("GRANT EXECUTE ON FUNCTION " + function + " TO " + role);
    }

    TenantIdentity demo(String code, String username) {
        long user = user(username, "DEMO_USER");
        var id = new TenantId(UUID.randomUUID());
        admin.update("INSERT INTO tenants(tenant_id,tenant_code,tenant_name,tenant_kind) VALUES(?,?,?,'DEMO')",id.value(),code,code);
        admin.update("INSERT INTO tenant_memberships(tenant_id,user_id,membership_kind,membership_role) VALUES(?,?,'DEMO','DEMO_USER')",id.value(),user);
        return new TenantIdentity(id,code,TenantKind.DEMO,user);
    }

    TenantIdentity local(String username) {
        return new TenantIdentity(TenantId.LOCAL,"NQ_LOCAL",TenantKind.LOCAL,user(username,"ADMIN","OPERATOR","VIEWER"));
    }

    public long user(String username, String... roles) {
        var users = new JdbcAuthUserRepository(admin);
        users.upsertSeedUser(new SeedUserCommand(username,HASH,List.of(roles),true));
        return users.findByUsername(username).orElseThrow().userId();
    }

    <T> T as(TenantIdentity identity, Supplier<T> action) {
        try (var ignored = TenantContext.open(identity)) { return action.get(); }
    }

    @Override public void close() throws Exception {
        TenantContext.clear();
        if (pool != null) pool.close();
        database.close();
        var maintenance = new JdbcTemplate(new DriverManagerDataSource(maintenanceUrl,installerUser,installerPassword));
        maintenance.execute("DROP ROLE " + runtime);
        maintenance.execute("DROP ROLE " + owner);
    }
}
