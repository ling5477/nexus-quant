package com.guidinglight.nexusquant.demo.platform;

import com.zaxxer.hikari.HikariDataSource;
import java.util.function.Function;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** 独立登录角色只执行受控投影；不会借用业务连接、切换租户或持有经济写入权限。 */
@Component
public final class PlatformReadSource implements DisposableBean {
    private final HikariDataSource pool;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PlatformReadSource(Environment environment) {
        String url = environment.getProperty("nq.demo.platform-read.jdbc-url", "");
        String username = environment.getProperty("nq.demo.platform-read.username", "");
        String password = environment.getProperty("nq.demo.platform-read.password", "");
        if (url.isBlank() || username.isBlank() || password.isBlank()) {
            pool = null; jdbc = null; transactions = null;
            return;
        }
        pool = new HikariDataSource();
        pool.setJdbcUrl(url); pool.setUsername(username); pool.setPassword(password);
        pool.setMaximumPoolSize(3); pool.setMinimumIdle(0); pool.setConnectionTimeout(5000);
        pool.setReadOnly(true); pool.setPoolName("platform-read");
        jdbc = new JdbcTemplate(pool); jdbc.setQueryTimeout(5);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(pool));
        transactions.setReadOnly(true); transactions.setTimeout(8);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public <T> T read(Function<JdbcTemplate,T> operation) {
        if (transactions == null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"PLATFORM_READ_DATABASE_NOT_CONFIGURED");
        return transactions.execute(status -> {
            // 角色漂移必须失败，不把被误授的基础表权限藏在应用层的“只读”标记后面。
            Boolean safe = jdbc.queryForObject("""
                SELECT NOT (r.rolsuper OR r.rolbypassrls OR r.rolcreaterole OR r.rolcreatedb)
                    AND NOT has_schema_privilege(current_user,'public','CREATE')
                    AND NOT EXISTS (SELECT 1 FROM pg_auth_members WHERE member=r.oid)
                    AND (SELECT count(*)=3 AND count(DISTINCT p.proowner)=1 FROM pg_proc p
                        JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'
                        AND p.proname IN ('nq_platform_tenants','nq_platform_tenant_summary','nq_platform_sim_facts'))
                    AND NOT EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                        JOIN pg_roles function_owner ON function_owner.oid=p.proowner
                        WHERE n.nspname='public' AND p.proname IN ('nq_platform_tenants','nq_platform_tenant_summary','nq_platform_sim_facts')
                        AND (function_owner.rolcanlogin OR function_owner.rolsuper OR function_owner.rolbypassrls
                            OR function_owner.rolcreatedb OR function_owner.rolcreaterole
                            OR has_schema_privilege(function_owner.rolname,'public','CREATE')
                            OR EXISTS(SELECT 1 FROM pg_auth_members WHERE member=function_owner.oid)
                            OR EXISTS(SELECT 1 FROM pg_class owned_table JOIN pg_namespace tn ON tn.oid=owned_table.relnamespace
                                WHERE tn.nspname='public' AND owned_table.relkind IN ('r','p')
                                AND (has_any_column_privilege(function_owner.rolname,owned_table.oid,'INSERT')
                                    OR has_any_column_privilege(function_owner.rolname,owned_table.oid,'UPDATE')
                                    OR has_table_privilege(function_owner.rolname,owned_table.oid,'DELETE')
                                    OR has_table_privilege(function_owner.rolname,owned_table.oid,'TRUNCATE')))))
                    AND NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                        WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m')
                        AND (has_table_privilege(current_user,c.oid,'INSERT') OR has_table_privilege(current_user,c.oid,'UPDATE')
                            OR has_table_privilege(current_user,c.oid,'DELETE') OR has_table_privilege(current_user,c.oid,'TRUNCATE')
                            OR (c.relname IN ('users','auth_sessions','ledger_entries','orders','trades','positions',
                                'account_snapshots','equity_curve_snapshots','research_configs','backtest_runs')
                                AND has_any_column_privilege(current_user,c.oid,'SELECT'))))
                FROM pg_roles r WHERE r.rolname=current_user
                """, Boolean.class);
            if (!Boolean.TRUE.equals(safe))
                throw new IllegalStateException("PLATFORM_READ_ROLE_NOT_RESTRICTED");
            jdbc.execute("SET LOCAL statement_timeout='5s'");
            return operation.apply(jdbc);
        });
    }

    @Override public void destroy() { if (pool != null) pool.close(); }
}
