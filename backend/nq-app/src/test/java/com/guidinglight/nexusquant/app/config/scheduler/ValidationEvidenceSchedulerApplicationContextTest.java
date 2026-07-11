package com.guidinglight.nexusquant.app.config.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.guidinglight.nexusquant.scheduler.infra.lock.PostgresAdvisorySchedulerExecutionLock;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerExecutionLock;
import com.guidinglight.nexusquant.scheduler.validationevidence.ValidationEvidenceScheduler;
import com.guidinglight.nexusquant.scheduler.validationevidence.ValidationEvidenceSchedulerConfiguration;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewQueryService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 验证 app composition root 的 default-disabled 与显式开启装配，不触发 query 或数据库锁。 */
class ValidationEvidenceSchedulerApplicationContextTest {

    private final ValidationOperationsRuntimeEvidenceOverviewQueryService queryService = mock(
            ValidationOperationsRuntimeEvidenceOverviewQueryService.class
    );
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ValidationOperationsRuntimeEvidenceOverviewQueryService.class, () -> queryService)
            .withBean(JdbcTemplate.class, () -> jdbcTemplate)
            .withBean(PlatformTransactionManager.class, () -> transactionManager)
            .withUserConfiguration(
                    SchedulerLockConfiguration.class,
                    ValidationEvidenceSchedulerConfiguration.class
            );

    @Test
    void shouldKeepSchedulerAbsentByDefaultWhileLockPortRemainsAvailable() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ValidationEvidenceScheduler.class);
            assertThat(context).hasSingleBean(SchedulerExecutionLock.class);
            assertThat(context.getBean(SchedulerExecutionLock.class))
                    .isInstanceOf(PostgresAdvisorySchedulerExecutionLock.class);
            verifyNoInteractions(queryService, transactionManager);
        });
    }

    @Test
    void shouldComposeSchedulerOnlyWhenExplicitlyEnabledWithoutInitializationSideEffects() {
        contextRunner.withPropertyValues(
                "nq.validation-operations.scheduler.enabled=true",
                "nq.validation-operations.scheduler.initial-delay=PT24H"
        ).run(context -> {
            assertThat(context).hasSingleBean(ValidationEvidenceScheduler.class);
            assertThat(context).hasSingleBean(SchedulerExecutionLock.class);
            assertThat(context.getBean(SchedulerExecutionLock.class))
                    .isInstanceOf(PostgresAdvisorySchedulerExecutionLock.class);
            verifyNoInteractions(queryService, transactionManager);
        });
    }
}
