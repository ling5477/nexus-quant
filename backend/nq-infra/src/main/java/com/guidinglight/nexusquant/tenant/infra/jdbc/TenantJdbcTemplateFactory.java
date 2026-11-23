package com.guidinglight.nexusquant.tenant.infra.jdbc;

import com.guidinglight.nexusquant.common.tenant.TenantContext;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import javax.sql.DataSource;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 为既有 JDBC 端口提供统一事务边界，避免裸读在独立连接上丢失 RLS 身份。 */
public final class TenantJdbcTemplateFactory {
    private static final Set<String> OPERATIONS = Set.of("execute", "query", "queryForObject", "queryForMap",
            "queryForList", "queryForRowSet", "queryForStream", "update", "batchUpdate", "call");

    private TenantJdbcTemplateFactory() {}

    public static JdbcTemplate create(DataSource dataSource, PlatformTransactionManager manager) {
        var target = new JdbcTemplate(dataSource);
        var transactions = new TransactionTemplate(manager);
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice((MethodInterceptor) invocation -> {
            if (!OPERATIONS.contains(invocation.getMethod().getName())) return invocation.proceed();
            if (invocation.getMethod().getName().equals("queryForStream")) {
                throw new IllegalStateException("tenant JDBC streams require an explicit bounded transaction");
            }
            return transactions.execute(status -> {
                var connection = DataSourceUtils.getConnection(dataSource);
                try (var check = connection.createStatement();
                        var result = check.executeQuery("SELECT current_setting('nq.tenant_id',true)")) {
                    result.next();
                    String actual = result.getString(1);
                    String expected = TenantContext.currentTenantId().map(value -> value.value().toString()).orElse("");
                    if (!expected.equals(actual == null ? "" : actual)) {
                        throw new IllegalStateException("TENANT_ACCESS_DENIED: tenant changed inside transaction");
                    }
                    // 每个公开 JDBC 调用仅进入一次；target 内部调用不再次代理或借新连接。
                    return invocation.getMethod().invoke(target, invocation.getArguments());
                } catch (InvocationTargetException failure) {
                    if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
                    if (failure.getCause() instanceof Error error) throw error;
                    throw new IllegalStateException("tenant JDBC invocation failed", failure.getCause());
                } catch (ReflectiveOperationException | java.sql.SQLException failure) {
                    throw new IllegalStateException("tenant JDBC transaction failed", failure);
                } finally {
                    DataSourceUtils.releaseConnection(connection, dataSource);
                }
            });
        });
        return (JdbcTemplate) proxy.getProxy();
    }
}
