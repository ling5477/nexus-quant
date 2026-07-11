package com.guidinglight.nexusquant.app.config.scheduler;

import com.guidinglight.nexusquant.scheduler.infra.lock.PostgresAdvisorySchedulerExecutionLock;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerExecutionLock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * SchedulerLockConfiguration 仅负责装配通用 scheduler execution lock port。
 *
 * <p>创建该 Bean 不会获取锁、启动 scheduler 或执行 callback；只有后续显式调用 port 时才在
 * PostgreSQL 上建立独立 transaction-level advisory lock 边界。配置不接触 exchange、
 * credential、review lifecycle 或交易状态。
 */
@Configuration
public class SchedulerLockConfiguration {

    /** 将纯 contract 绑定到 PostgreSQL transaction-level advisory lock implementation。 */
    @Bean
    public SchedulerExecutionLock schedulerExecutionLock(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        return new PostgresAdvisorySchedulerExecutionLock(jdbcTemplate, transactionManager);
    }
}
