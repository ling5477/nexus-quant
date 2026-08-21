package com.guidinglight.nexusquant.scheduler.validationevidence;

import com.guidinglight.nexusquant.scheduler.lock.SchedulerExecutionLock;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewQueryService;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

/**
 * Validation Evidence Scheduler 的隔离装配边界。
 *
 * <p>Properties 始终绑定并 fail-fast；实际 scheduler 与 scheduling processor 只在显式 enabled=true
 * 时注册。自定义 processor 只处理本任务 Bean，避免启用 GateV-3 时意外启动仓库内既有 Paper、exchange、
 * recovery 或 ledger 的历史 {@code @Scheduled} 方法。
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

        /**
         * 注册标准 processor 名称以复用 Spring scheduling infrastructure，但只处理本任务 scheduler。
         */
        @Bean(name = TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME)
        static ScheduledAnnotationBeanPostProcessor validationEvidenceScheduledAnnotationProcessor() {
            return new ScheduledAnnotationBeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof ValidationEvidenceScheduler) {
                        return super.postProcessAfterInitialization(bean, beanName);
                    }
                    return bean;
                }
            };
        }

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
                SchedulerExecutionLock executionLock
        ) {
            return new ValidationEvidenceScheduler(
                    properties,
                    refreshService,
                    executionLock,
                    Clock.systemUTC()
            );
        }
    }
}
