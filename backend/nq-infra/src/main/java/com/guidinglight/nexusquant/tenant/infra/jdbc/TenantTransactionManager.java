package com.guidinglight.nexusquant.tenant.infra.jdbc;

import com.guidinglight.nexusquant.common.tenant.TenantContext;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;

/** 在 Spring 已绑定的同一连接内设置事务级身份；新事务和 REQUIRES_NEW 都重新验证。 */
public class TenantTransactionManager extends JdbcTransactionManager {
    public TenantTransactionManager(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        var holder = (ConnectionHolder) org.springframework.transaction.support.TransactionSynchronizationManager
                .getResource(obtainDataSource());
        if (holder == null) throw new CannotCreateTransactionException("tenant transaction connection is not bound");
        Connection connection = holder.getConnection();
        try {
            var identity = TenantContext.currentTenantId();
            verifyConnection(connection);
            try (var statement = connection.prepareStatement("SELECT set_config('nq.tenant_id',?,true)")) {
                statement.setString(1, identity.map(value -> value.value().toString()).orElse(""));
                statement.execute();
            }
            try (var statement = connection.prepareStatement("SELECT set_config('nq.user_id',?,true)")) {
                statement.setString(1, TenantContext.current().map(value -> Long.toString(value.userId())).orElse(""));
                statement.execute();
            }
            String epoch = "";
            if (identity.isPresent()) {
                try (var statement = connection.prepareStatement(
                        "SELECT execution_epoch FROM tenant_execution_controls WHERE tenant_id=?")) {
                    statement.setObject(1, identity.get().value());
                    try (var result = statement.executeQuery()) {
                        if (!result.next()) throw new SQLException("TENANT_ACCESS_DENIED: execution control missing", "42501");
                        long current = result.getLong(1);
                        epoch = Long.toString(TenantContext.execution().map(value -> value.executionEpoch()).orElse(current));
                    }
                }
            }
            try (var statement = connection.prepareStatement("SELECT set_config('nq.execution_epoch',?,true)")) {
                statement.setString(1, epoch);
                statement.execute();
            }
        } catch (SQLException | RuntimeException failure) {
            // doBegin 抛错时父事务管理器不会调用常规 cleanup，必须释放本次已经取得的事务。
            try { connection.rollback(); }
            catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            super.doCleanupAfterCompletion(transaction);
            throw new CannotCreateTransactionException("TENANT_ACCESS_DENIED: transaction identity rejected", failure);
        }
    }

    private static void requireRestrictedRole(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT r.rolsuper OR r.rolbypassrls OR r.rolcreaterole OR r.rolcreatedb AS unsafe_runtime,
                    has_schema_privilege(current_user,'public','CREATE') AS writable_namespace,
                    EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                        WHERE n.nspname='public' AND c.relrowsecurity
                        AND pg_has_role(current_user,c.relowner,'MEMBER')) AS owner_access,
                    EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                        JOIN pg_roles owner ON owner.oid=c.relowner
                        WHERE n.nspname='public' AND c.relrowsecurity
                        AND (owner.rolsuper OR owner.rolbypassrls OR owner.rolcanlogin OR owner.rolcreaterole OR owner.rolcreatedb)) AS unsafe_owner
                FROM pg_roles r WHERE r.rolname=current_user
                """); var result = statement.executeQuery()) {
            if (!result.next() || result.getBoolean("unsafe_runtime") || result.getBoolean("writable_namespace")
                    || result.getBoolean("owner_access") || result.getBoolean("unsafe_owner")) {
                throw new SQLException("TENANT_ACCESS_DENIED: runtime/owner role is not restricted", "42501");
            }
        }
    }

    /** 启动时验证独立迁移已经完成；不允许裸 JDBC 消费者在不安全角色下先接收请求。 */
    public static void verifyRuntime(DataSource dataSource) {
        try(var connection=dataSource.getConnection()) {
            verifyConnection(connection);
        } catch(SQLException failure) {throw new IllegalStateException("TENANT_ACCESS_DENIED: restricted tenant database installation required",failure);}
    }

    // 无数据库启动的只读诊断 profile 也不能在首次 JDBC 使用时绕过完整安装检查。
    public static void verifyConnection(Connection connection) throws SQLException {
            requireRestrictedRole(connection);
            try(var statement=connection.createStatement();var result=statement.executeQuery("""
                    SELECT count(*)=8 AND bool_and(relrowsecurity AND relforcerowsecurity) FROM pg_class c
                    JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public'
                    AND c.relname IN ('accounts','exchange_accounts','strategy_definitions','strategy_versions',
                        'research_configs','backtest_configs','paper_trading_runs','marketdata_datasets')
                    """)) {
                if(!result.next() || !result.getBoolean(1))throw new SQLException("tenant schema installation incomplete");
            }
            try(var statement=connection.createStatement();var result=statement.executeQuery("""
                    SELECT EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public'
                        AND c.relname IN ('tenant_background_work','tenant_continuous_slots','tenant_work_scan_cursors','tenant_execution_controls',
                            'audit_log_tenant_scopes','event_tenant_scopes','risk_event_tenant_scopes','marketdata_dataset_publications')
                        AND (has_table_privilege(current_user,c.oid,'INSERT') OR has_table_privilege(current_user,c.oid,'UPDATE')
                            OR has_table_privilege(current_user,c.oid,'DELETE') OR has_table_privilege(current_user,c.oid,'TRUNCATE')
                            OR (c.relname IN ('tenant_background_work','tenant_continuous_slots','tenant_work_scan_cursors')
                                AND has_table_privilege(current_user,c.oid,'SELECT'))))
                    """)) {
                if(!result.next() || result.getBoolean(1))throw new SQLException("runtime role has internal metadata mutation privileges");
            }
    }
}
