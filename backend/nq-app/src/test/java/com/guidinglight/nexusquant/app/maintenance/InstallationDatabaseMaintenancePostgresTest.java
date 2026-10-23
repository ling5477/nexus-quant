package com.guidinglight.nexusquant.app.maintenance;

import static org.junit.jupiter.api.Assertions.*;

import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcAuthUserRepository;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** 每例独立任务数据库；只证明安装维护路径、原认证与权限合同，不替代正式不可变包资格。 */
@EnabledIfSystemProperty(named="nq.installer.pg.required",matches="true")
class InstallationDatabaseMaintenancePostgresTest {
    private static final String PASSWORD = "b".repeat(64);

    @Test void freshInstallIsRestrictedAndRetryPreservesAdministrator() throws Exception {
        try (Fixture fixture = new Fixture()) {
            InstallationDatabaseMaintenanceMain.prepare(fixture.source, PASSWORD, "47");
            String before = fixture.jdbc.queryForObject("SELECT to_jsonb(u)::text FROM users u WHERE username='admin'", String.class);
            assertTrue(new BCryptPasswordEncoder().matches("123456", fixture.jdbc.queryForObject("SELECT password_hash FROM users WHERE username='admin'", String.class)));
            assertTrue(fixture.jdbc.queryForObject("SELECT must_change_password FROM users WHERE username='admin'", Boolean.class));
            assertEquals("NQ_LOCAL", fixture.jdbc.queryForObject("SELECT t.tenant_code FROM tenant_memberships m JOIN tenants t USING(tenant_id)", String.class));
            InstallationDatabaseMaintenanceMain.prepare(fixture.source, PASSWORD, "47");
            assertEquals(before, fixture.jdbc.queryForObject("SELECT to_jsonb(u)::text FROM users u WHERE username='admin'", String.class));
            InstallationDatabaseMaintenanceMain.verify(fixture.runtime());
            JdbcTemplate runtime = new JdbcTemplate(fixture.runtime());
            assertEquals(0, runtime.queryForObject("SELECT count(*) FROM orders", Integer.class));
            assertThrows(Exception.class, () -> runtime.execute("SET ROLE nq_schema_owner"));
            assertThrows(Exception.class, () -> runtime.queryForList("SELECT * FROM tenant_background_work"));
            assertThrows(Exception.class, () -> runtime.execute("UPDATE tenant_execution_controls SET suspended=true"));
            assertFalse(runtime.queryForObject("SELECT has_function_privilege(current_user,'nq_platform_user_operation(uuid,bigint,text,bigint,bigint,text,text,text)','EXECUTE')", Boolean.class));
            try (var connection = fixture.runtime().getConnection(); var statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                statement.execute("SET LOCAL nq.tenant_id='00000000-0000-4000-8000-000000000001'");
                statement.execute("SET LOCAL nq.execution_epoch='1'");
                statement.execute("SELECT nq_require_tenant_execution('00000000-0000-4000-8000-000000000001')");
                connection.rollback();
                statement.execute("SET LOCAL nq.tenant_id='00000000-0000-4000-8000-000000000001'");
                statement.execute("SET LOCAL nq.execution_epoch='1'");
                assertThrows(Exception.class, () -> statement.execute("SELECT nq_require_tenant_execution('00000000-0000-4000-8000-000000000002')"));
                connection.rollback();
            }
            assertFalse(runtime.queryForObject("SELECT rolbypassrls OR rolsuper OR rolcreatedb OR rolcreaterole FROM pg_roles WHERE rolname=current_user", Boolean.class));
        }
    }

    @Test void oldV34AuthenticationAndChecksumsSurviveMigration() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Flyway.configure().dataSource(fixture.source).target("34").load().migrate();
            new TransactionTemplate(new DataSourceTransactionManager(fixture.source)).executeWithoutResult(status -> {
                new JdbcAuthUserRepository(fixture.jdbc).createInitialAdminIfAbsent(new SeedUserCommand("existing-owner",
                        new BCryptPasswordEncoder().encode("preserved-test-password"),List.of("ADMIN","VIEWER"),true));
            });
            String users = fixture.jdbc.queryForObject("SELECT (to_jsonb(u)-'email'-'email_verified_at')::text FROM users u", String.class);
            var roles = fixture.jdbc.queryForList("SELECT to_jsonb(r)::text FROM user_roles r ORDER BY role_id", String.class);
            var history = fixture.jdbc.queryForList("SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank");
            String maintenanceSecret = fixture.jdbc.queryForObject("SELECT rolpassword FROM pg_authid WHERE rolname=current_user", String.class);
            InstallationDatabaseMaintenanceMain.prepare(fixture.source, PASSWORD, "47");
            assertEquals(users,fixture.jdbc.queryForObject("SELECT (to_jsonb(u)-'email'-'email_verified_at')::text FROM users u",String.class));
            assertTrue(fixture.jdbc.queryForObject("SELECT email IS NULL AND email_verified_at IS NULL FROM users",Boolean.class));
            assertEquals(roles,fixture.jdbc.queryForList("SELECT to_jsonb(r)::text FROM user_roles r ORDER BY role_id",String.class));
            assertEquals(history,fixture.jdbc.queryForList("SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank LIMIT 34"));
            assertEquals(maintenanceSecret,fixture.jdbc.queryForObject("SELECT rolpassword FROM pg_authid WHERE rolname=current_user",String.class));
            assertEquals(0,fixture.jdbc.queryForObject("SELECT count(*) FROM tenant_sim_accounts",Integer.class));
            assertEquals("NQ_LOCAL",fixture.jdbc.queryForObject("SELECT t.tenant_code FROM tenants t JOIN tenant_memberships m USING(tenant_id)",String.class));
            assertEquals(0,fixture.jdbc.queryForObject("SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace JOIN pg_roles r ON r.oid=p.proowner WHERE n.nspname='public' AND p.prosecdef AND (r.rolcanlogin OR r.rolsuper OR r.rolbypassrls)",Integer.class));
        }
    }

    @Test void unsafeRolesAndFlywayDriftFailWithoutOverwritingAuthentication() throws Exception {
        try (Fixture fixture = new Fixture()) {
            InstallationDatabaseMaintenanceMain.prepare(fixture.source,PASSWORD,"47");
            String users=fixture.jdbc.queryForObject("SELECT to_jsonb(u)::text FROM users u",String.class);
            fixture.jdbc.execute("ALTER ROLE nq_runtime CREATEDB");
            try { assertThrows(Exception.class,()->InstallationDatabaseMaintenanceMain.prepare(fixture.source,PASSWORD,"47")); }
            finally { fixture.jdbc.execute("ALTER ROLE nq_runtime NOCREATEDB"); }
            Integer checksum=fixture.jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='34'",Integer.class);
            fixture.jdbc.update("UPDATE flyway_schema_history SET checksum=checksum+1 WHERE version='34'");
            assertThrows(Exception.class,()->InstallationDatabaseMaintenanceMain.prepare(fixture.source,PASSWORD,"47"));
            assertEquals(users,fixture.jdbc.queryForObject("SELECT to_jsonb(u)::text FROM users u",String.class));
            fixture.jdbc.update("UPDATE flyway_schema_history SET checksum=? WHERE version='34'",checksum);
            InstallationDatabaseMaintenanceMain.prepare(fixture.source,PASSWORD,"47");
            InstallationDatabaseMaintenanceMain.verify(fixture.runtime());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final String base=System.getProperty("nq.installer.pg.url");
        final String user=System.getProperty("nq.installer.pg.user");
        final String secret=System.getProperty("nq.installer.pg.password");
        final String name="nq_install_test_"+UUID.randomUUID().toString().replace("-","");
        final String url;
        final DriverManagerDataSource source;
        final JdbcTemplate jdbc;
        Fixture() throws Exception {
            if(base==null || !base.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/postgres"))throw new IllegalArgumentException("disposable loopback PG required");
            try(var connection=DriverManager.getConnection(base,user,secret);var statement=connection.createStatement()) {statement.execute("CREATE DATABASE "+name);}
            url=base.substring(0,base.lastIndexOf('/')+1)+name;
            source=InstallationDatabaseMaintenanceMain.source(url,user,secret);jdbc=new JdbcTemplate(source);
        }
        DriverManagerDataSource runtime() {return InstallationDatabaseMaintenanceMain.source(url,"nq_runtime",PASSWORD);}
        @Override public void close() throws Exception {
            try(var connection=DriverManager.getConnection(base,user,secret);var statement=connection.createStatement()) {statement.execute("DROP DATABASE "+name+" WITH (FORCE)");}
        }
    }
}
