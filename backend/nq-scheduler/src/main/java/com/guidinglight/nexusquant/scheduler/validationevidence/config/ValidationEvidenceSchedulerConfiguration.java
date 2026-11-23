package com.guidinglight.nexusquant.scheduler.validationevidence.config;

import com.guidinglight.nexusquant.scheduler.validationevidence.scheduling.ValidationEvidenceScheduler;
import com.guidinglight.nexusquant.scheduler.validationevidence.service.ValidationEvidenceRefreshService;

import com.guidinglight.nexusquant.observability.operational.OperationalObservation;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerExecutionLock;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewQueryService;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Validation Evidence Scheduler 的隔离装配边界。
 *
 * <p>Properties 始终绑定并 fail-fast；实际运行 Bean 只在显式 enabled=true 时注册。
 * 周期执行统一由代码注册表和 Dispatcher 控制，避免同一个任务有两个调度 owner。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ValidationEvidenceSchedulerProperties.class)
public class ValidationEvidenceSchedulerConfiguration {

    /**
     * 显式开启时才装配运行 Bean；默认、local、test、CI 均不注册 scheduler。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = ValidationEvidenceSchedulerProperties.PREFIX,
            name = "enabled",
            havingValue = "true"
    )
    static class EnabledConfiguration {

        @Bean
        ValidationEvidenceRefreshService validationEvidenceRefreshService(
                ValidationOperationsRuntimeEvidenceOverviewQueryService queryService
        ) {
            return new ValidationEvidenceRefreshService(queryService, Clock.systemUTC());
        }

        @Bean
        ValidationEvidenceScheduler validationEvidenceScheduler(
                ValidationEvidenceSchedulerProperties properties,
                ValidationEvidenceRefreshService refreshService,
                SchedulerExecutionLock executionLock,
                ObjectProvider<OperationalObservation> observation
        ) {
            return new ValidationEvidenceScheduler(
                    properties,
                    refreshService,
                    executionLock,
                    Clock.systemUTC(),
                    observation.getIfAvailable(() -> OperationalObservation.NOOP)
            );
        }
    }
}
