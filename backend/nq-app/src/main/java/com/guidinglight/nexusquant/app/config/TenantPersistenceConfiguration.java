package com.guidinglight.nexusquant.app.config;

import com.guidinglight.nexusquant.tenant.infra.jdbc.TenantJdbcTemplateFactory;
import com.guidinglight.nexusquant.tenant.infra.jdbc.TenantTransactionManager;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 私有查询与写入共用同连接事务身份；认证全局表不依赖虚构的默认租户。 */
@Configuration(proxyBeanMethods = false)
public class TenantPersistenceConfiguration {
    /** 诊断启动不连接数据库；连接取得时仍须执行完整权限检查。 */
    @Bean
    @org.springframework.context.annotation.Profile("scoped-okx-private-readonly")
    public static org.springframework.beans.factory.config.BeanPostProcessor restrictedReadOnlyDataSources() {
        return new org.springframework.beans.factory.config.BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean,String name) {
                if(!(bean instanceof DataSource source))return bean;
                return new RestrictedReadOnlyDataSource(source);
            }
        };
    }

    private static final class RestrictedReadOnlyDataSource extends org.springframework.jdbc.datasource.DelegatingDataSource
            implements AutoCloseable {
        RestrictedReadOnlyDataSource(DataSource source){super(source);}
        @Override public java.sql.Connection getConnection() throws java.sql.SQLException {return checked(super.getConnection());}
        @Override public java.sql.Connection getConnection(String user,String password) throws java.sql.SQLException {
            return checked(super.getConnection(user,password));
        }
        @Override public void close() throws Exception {
            if(getTargetDataSource() instanceof AutoCloseable source)source.close();
        }
    }

    private static java.sql.Connection checked(java.sql.Connection connection) throws java.sql.SQLException {
        try {TenantTransactionManager.verifyConnection(connection);return connection;}
        catch(java.sql.SQLException failure) {
            try {connection.close();}catch(java.sql.SQLException cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }
    }

    @Bean
    @org.springframework.context.annotation.Profile("!scoped-okx-private-readonly")
    public org.springframework.boot.ApplicationRunner tenantRuntimeRoleGuard(DataSource dataSource) {
        return arguments -> TenantTransactionManager.verifyRuntime(dataSource);
    }
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new TenantTransactionManager(dataSource);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource, PlatformTransactionManager transactionManager) {
        return TenantJdbcTemplateFactory.create(dataSource, transactionManager);
    }
}
