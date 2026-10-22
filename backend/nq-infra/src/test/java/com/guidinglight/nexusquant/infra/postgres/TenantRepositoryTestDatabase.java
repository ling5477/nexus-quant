package com.guidinglight.nexusquant.infra.postgres;

import com.guidinglight.nexusquant.common.tenant.*;
import com.guidinglight.nexusquant.tenant.infra.jdbc.TenantJdbcTemplateFactory;
import com.guidinglight.nexusquant.tenant.infra.jdbc.TenantTransactionManager;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 仓储 smoke 自有数据库及双身份；安装连接只做 DDL／授权，所有被测仓储使用非 owner。 */
final class TenantRepositoryTestDatabase implements AutoCloseable {
    final String suffix=UUID.randomUUID().toString().replace("-","");
    final String name="nq_repository_"+suffix,owner="nq_owner_"+suffix,runtime="nq_runtime_"+suffix;
    final JdbcTemplate maintenance;
    JdbcTemplate jdbc;
    TenantTransactionManager manager;
    TenantContext.ExecutionScope scope;
    boolean created,ownerCreated,runtimeCreated;

    TenantRepositoryTestDatabase(String url,String user,String password) throws Exception {
        var uri=URI.create(url.substring("jdbc:".length()));
        if(!Set.of("127.0.0.1","localhost","::1").contains(uri.getHost()) || uri.getPort()<=0
                || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
            throw new IllegalArgumentException("explicit disposable loopback PostgreSQL required");
        String base=url.substring(0,url.lastIndexOf('/')+1);
        maintenance=new JdbcTemplate(new DriverManagerDataSource(base+"postgres",user,password));
        try {
            maintenance.execute("CREATE DATABASE "+name);created=true;
            maintenance.execute("CREATE ROLE "+owner+" NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE");ownerCreated=true;
            String runtimePassword=UUID.randomUUID().toString();
            maintenance.execute("CREATE ROLE "+runtime+" LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '"+runtimePassword+"'");runtimeCreated=true;
            var install=new DriverManagerDataSource(base+name,user,password);var admin=new JdbcTemplate(install);
            admin.execute("GRANT CREATE ON DATABASE "+name+" TO "+owner);
            admin.execute("ALTER SCHEMA public OWNER TO "+owner);
            var flyway=Flyway.configure().dataSource(install).locations("classpath:db/migration").initSql("SET ROLE "+owner).cleanDisabled(true).load();
            flyway.migrate();flyway.validate();
            admin.execute("GRANT USAGE ON SCHEMA public TO "+runtime);
            var hidden=Set.of("flyway_schema_history","tenant_background_work","tenant_continuous_slots","tenant_work_scan_cursors");
            var readOnly=Set.of("tenants","tenant_memberships","roles","user_roles","tenant_execution_controls",
                    "audit_log_tenant_scopes","event_tenant_scopes","risk_event_tenant_scopes","marketdata_dataset_publications");
            for(String table:admin.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public'",String.class)) {
                if(hidden.contains(table))continue;
                if(!table.matches("[a-z_]+"))throw new IllegalStateException("unexpected fixture table");
                admin.execute("GRANT "+(readOnly.contains(table)?"SELECT":"SELECT,INSERT,UPDATE,DELETE")+" ON "+table+" TO "+runtime);
            }
            admin.execute("GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO "+runtime);
            for(String function:admin.queryForList("""
                    SELECT p.oid::regprocedure::text FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                    WHERE n.nspname='public' AND p.proname IN ('nq_admit_strategy_work','nq_begin_strategy_dispatch',
                        'nq_project_strategy_run','nq_bind_strategy_effective','nq_create_ordinary_place_order',
                        'nq_decide_ordinary_place','nq_finish_strategy_cancel','nq_require_continuous_capacity',
                        'nq_resume_tenant_execution','nq_poll_tenant_work','nq_restore_tenant_work')
                    """,String.class))admin.execute("GRANT EXECUTE ON FUNCTION "+function+" TO "+runtime);
            var source=new DriverManagerDataSource(base+name,runtime,runtimePassword);
            TenantTransactionManager.verifyRuntime(source);
            manager=new TenantTransactionManager(source);jdbc=TenantJdbcTemplateFactory.create(source,manager);
            scope=TenantContext.openExecution(new TenantExecutionIdentity(TenantId.LOCAL,1));
        } catch(Exception | AssertionError failure) {
            try {close();}catch(Exception cleanup){failure.addSuppressed(cleanup);}throw failure;
        }
    }

    @Override public void close() {
        if(scope!=null)scope.close();
        if(!name.matches("nq_repository_[a-f0-9]{32}"))throw new IllegalStateException("fixture identity drift");
        if(created)maintenance.execute("DROP DATABASE "+name+" WITH (FORCE)");
        if(runtimeCreated)maintenance.execute("DROP ROLE "+runtime);
        if(ownerCreated)maintenance.execute("DROP ROLE "+owner);
    }
}
