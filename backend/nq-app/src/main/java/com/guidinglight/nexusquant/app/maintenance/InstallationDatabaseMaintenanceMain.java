package com.guidinglight.nexusquant.app.maintenance;

import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcAuthUserRepository;
import com.guidinglight.nexusquant.tenant.infra.jdbc.TenantTransactionManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** 安装器专用一次性入口；不启动 Spring、HTTP、调度或交易组件，也不向业务容器传入维护凭据。 */
public final class InstallationDatabaseMaintenanceMain {
    private static final String OWNER = "nq_schema_owner";
    private static final String RUNTIME = "nq_runtime";
    private static final Set<String> INTERNAL = Set.of("flyway_schema_history", "tenant_background_work",
            "tenant_continuous_slots", "tenant_work_scan_cursors", "platform_user_operations", "platform_identity_initializations");
    private static final Set<String> READ_ONLY = Set.of("tenants", "tenant_memberships", "roles", "user_roles",
            "tenant_execution_controls", "audit_log_tenant_scopes", "event_tenant_scopes", "risk_event_tenant_scopes",
            "marketdata_dataset_publications");
    // 经济写入触发器也以运行角色调用执行守卫；仅授予该守卫，不开放平台管理函数。
    private static final Set<String> EXECUTABLE = Set.of("nq_admit_strategy_work", "nq_begin_strategy_dispatch",
            "nq_project_strategy_run", "nq_bind_strategy_effective", "nq_create_ordinary_place_order",
            "nq_decide_ordinary_place", "nq_finish_strategy_cancel", "nq_require_continuous_capacity",
            "nq_resume_tenant_execution", "nq_poll_tenant_work", "nq_restore_tenant_work", "nq_require_tenant_execution");

    private InstallationDatabaseMaintenanceMain() {}

    public static void main(String[] args) {
        int result = 0;
        try {
            if (args.length != 1 || !Set.of("--provision-and-migrate", "--verify-runtime").contains(args[0]))
                throw new IllegalArgumentException("explicit installation action required");
            java.util.logging.LogManager.getLogManager().reset();
            String url = required("NQ_INSTALL_DB_URL");
            String runtimePassword = required("NQ_RUNTIME_DB_PASSWORD");
            String schema = required("SCHEMA_VERSION");
            if (!url.matches("jdbc:postgresql://postgres:5432/nexus_quant")
                    || !schema.matches("[1-9][0-9]{0,5}") || !runtimePassword.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("invalid installation identity");
            DriverManagerDataSource maintenance = source(url, "nexusquant", required("NQ_INSTALL_DB_PASSWORD"));
            if ("--provision-and-migrate".equals(args[0])) prepare(maintenance, runtimePassword, schema);
            else verify(source(url, RUNTIME, runtimePassword));
            System.out.println("DATABASE_RUNTIME_RESTRICTED");
        } catch (Exception failure) {
            // JDBC/Flyway 异常可能包含连接和业务内容；安装边界只输出固定结果。
            System.out.println("INSTALLATION_DATABASE_MAINTENANCE_FAILED");
            result = 4;
        }
        System.exit(result);
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("installation state incomplete");
        return value;
    }

    static DriverManagerDataSource source(String url, String user, String password) {
        DriverManagerDataSource source = new DriverManagerDataSource(url, user, password);
        Properties properties = new Properties();
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "180");
        properties.setProperty("ApplicationName", "nexusquant-installation-maintenance");
        source.setConnectionProperties(properties);
        return source;
    }

    static void prepare(DataSource maintenance, String password, String target) throws Exception {
        if (!password.matches("[a-f0-9]{64}") || !target.matches("[1-9][0-9]{0,5}"))
            throw new IllegalArgumentException("invalid protected installation state");
        // 跨连接的会话锁覆盖角色准备、Flyway 与授权；中断释放锁，已提交 migration 仍由原 Flyway 记录续跑。
        try (Connection lock = maintenance.getConnection(); var statement = lock.createStatement()) {
            statement.setQueryTimeout(15);
            try (var result = statement.executeQuery("SELECT pg_try_advisory_lock(72341859)")) {
                if (!result.next() || !result.getBoolean(1)) throw new SQLException("installation already active");
            }
            try {
                JdbcTemplate jdbc = new JdbcTemplate(maintenance);
                jdbc.setQueryTimeout(120);
                new TransactionTemplate(new DataSourceTransactionManager(maintenance)).executeWithoutResult(status -> {
                    jdbc.execute("SET LOCAL lock_timeout='5s'");
                    jdbc.execute("""
                            DO $$ BEGIN
                              IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='nq_schema_owner') THEN
                                CREATE ROLE nq_schema_owner NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOINHERIT;
                              END IF;
                              IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='nq_runtime') THEN
                                CREATE ROLE nq_runtime LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOINHERIT;
                              END IF;
                            END $$
                            """);
                    Boolean invalid = jdbc.queryForObject("""
                            SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname IN ('nq_schema_owner','nq_runtime')
                                AND (rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole OR rolinherit
                                  OR (rolname='nq_schema_owner' AND rolcanlogin)
                                  OR (rolname='nq_runtime' AND NOT rolcanlogin)))
                              OR EXISTS(SELECT 1 FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.member
                                  WHERE r.rolname='nq_runtime')
                            """, Boolean.class);
                    if (!Boolean.FALSE.equals(invalid)) throw new IllegalStateException("unsafe installation role");
                    // 仅独立运行口令可恢复或重复设置；原 nexusquant 维护账号及其密码不修改。
                    jdbc.execute("ALTER ROLE nq_runtime PASSWORD '" + password + "'");
                    jdbc.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC, nq_runtime");
                    jdbc.execute("ALTER SCHEMA public OWNER TO nq_schema_owner");
                    jdbc.execute(jdbc.queryForObject("SELECT format('GRANT CREATE ON DATABASE %I TO nq_schema_owner',current_database())", String.class));
                    for (String command : ownership(jdbc)) jdbc.execute(command);
                });
                Flyway flyway = Flyway.configure().dataSource(maintenance).locations("classpath:db/migration")
                        .cleanDisabled(true).baselineOnMigrate(false).validateOnMigrate(true)
                        .initSql("SET ROLE " + OWNER).target(target).load();
                flyway.migrate();
                flyway.validate();
                if (flyway.info().current() == null
                        || !target.equals(flyway.info().current().getVersion().getVersion()))
                    throw new IllegalStateException("schema identity mismatch");
                // 复用原空库管理员语义与本地角色触发器；存在任意用户时绝不覆盖认证事实。
                new TransactionTemplate(new DataSourceTransactionManager(maintenance)).executeWithoutResult(status -> {
                    new JdbcAuthUserRepository(jdbc).createInitialAdminIfAbsent(new SeedUserCommand("admin",
                            new BCryptPasswordEncoder().encode("123456"), List.of("ADMIN", "OPERATOR", "VIEWER"), true));
                    grantRuntime(jdbc);
                });
                try (Connection connection = maintenance.getConnection()) {
                    String url = connection.getMetaData().getURL();
                    verify(source(url, RUNTIME, password));
                }
            } finally { statement.execute("SELECT pg_advisory_unlock(72341859)"); }
        }
    }

    private static List<String> ownership(JdbcTemplate jdbc) {
        // pg_restore --no-owner 不携带原 Owner；对本应用 public 对象确定性重建，不使用集群级 REASSIGN OWNED。
        List<String> commands = new ArrayList<>(jdbc.queryForList("""
                SELECT format('ALTER %s %I.%I OWNER TO nq_schema_owner',
                    CASE c.relkind WHEN 'S' THEN 'SEQUENCE' WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'MATERIALIZED VIEW' ELSE 'TABLE' END,
                    n.nspname,c.relname)
                FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='public' AND c.relkind IN ('r','p','S','v','m')
                ORDER BY CASE WHEN c.relkind='S' THEN 1 ELSE 0 END,c.oid
                """, String.class));
        commands.addAll(jdbc.queryForList("""
                SELECT format('ALTER ROUTINE %I.%I(%s) OWNER TO nq_schema_owner',n.nspname,p.proname,pg_get_function_identity_arguments(p.oid))
                FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                WHERE n.nspname='public'
                """, String.class));
        commands.addAll(jdbc.queryForList("""
                SELECT format('ALTER %s %I.%I OWNER TO nq_schema_owner',CASE t.typtype WHEN 'd' THEN 'DOMAIN' ELSE 'TYPE' END,n.nspname,t.typname)
                FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace
                WHERE n.nspname='public' AND t.typtype IN ('e','d')
                """, String.class));
        if (commands.size() > 2000) throw new IllegalStateException("installation object limit exceeded");
        return commands;
    }

    private static void grantRuntime(JdbcTemplate jdbc) {
        jdbc.execute("REVOKE ALL ON ALL TABLES IN SCHEMA public FROM nq_runtime");
        jdbc.execute("REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC");
        jdbc.execute("REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM nq_runtime");
        jdbc.execute("REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM nq_runtime");
        // --no-acl 恢复会丢失函数对 PUBLIC 的 REVOKE；必须显式重建，不能使受限 SECURITY DEFINER 意外公开。
        jdbc.execute("REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC");
        jdbc.execute("GRANT USAGE ON SCHEMA public TO nq_runtime");
        for (String table : jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public'", String.class)) {
            if (INTERNAL.contains(table)) continue;
            if (!table.matches("[a-z_]+")) throw new IllegalStateException("unexpected application table");
            jdbc.execute("GRANT " + (READ_ONLY.contains(table) ? "SELECT" : "SELECT,INSERT,UPDATE,DELETE")
                    + " ON public.\"" + table + "\" TO nq_runtime");
        }
        jdbc.execute("GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO nq_runtime");
        for (var function : jdbc.queryForList("""
                SELECT p.oid::regprocedure::text AS signature,p.proname AS name,p.prosecdef AS definer
                FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'
                """)) {
            if (Boolean.FALSE.equals(function.get("definer")) || EXECUTABLE.contains(function.get("name")))
                jdbc.execute("GRANT EXECUTE ON FUNCTION " + function.get("signature") + " TO nq_runtime");
        }
    }

    static void verify(DataSource runtime) {
        TenantTransactionManager.verifyRuntime(runtime);
        Boolean unsafe = new JdbcTemplate(runtime).queryForObject("""
                SELECT EXISTS(SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                    JOIN pg_roles r ON r.oid=p.proowner WHERE n.nspname='public' AND p.prosecdef
                    AND (r.rolcanlogin OR r.rolsuper OR r.rolbypassrls OR r.rolcreatedb OR r.rolcreaterole))
                """, Boolean.class);
        if (!Boolean.FALSE.equals(unsafe)) throw new IllegalStateException("unsafe function owner");
    }
}
