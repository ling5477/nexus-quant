package com.guidinglight.nexusquant.app.config.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.guidinglight.nexusquant.scheduler.infra.lock.PostgresAdvisorySchedulerExecutionLock;
import com.guidinglight.nexusquant.scheduler.lock.SchedulerExecutionLock;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;

/** 验证 composition root 只装配 lock port，不注册或触发任何 scheduled method。 */
class SchedulerLockConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withUserConfiguration(SchedulerLockConfiguration.class);

    @Test
    void shouldBindPortToPostgresImplementationWithoutScheduledMethods() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SchedulerExecutionLock.class);
            assertThat(context.getBean(SchedulerExecutionLock.class))
                    .isInstanceOf(PostgresAdvisorySchedulerExecutionLock.class);
            assertThat(hasScheduledMethod(SchedulerLockConfiguration.class)).isFalse();
            assertThat(hasScheduledMethod(PostgresAdvisorySchedulerExecutionLock.class)).isFalse();
        });
    }

    private static boolean hasScheduledMethod(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .map(Method::getDeclaredAnnotations)
                .flatMap(Arrays::stream)
                .anyMatch(annotation -> annotation.annotationType() == Scheduled.class);
    }
}
