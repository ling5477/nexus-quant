package com.guidinglight.nexusquant.scheduler.validationevidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.guidinglight.nexusquant.scheduler.lock.SchedulerExecutionLock;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewQueryService;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

/** 验证默认不注册 scheduler，显式开启时只注册本任务的 scheduled method。 */
class ValidationEvidenceSchedulerConfigurationTest {

    @Test
    void shouldRemainDisabledByDefaultWithoutSchedulingProcessor() {
        ValidationOperationsRuntimeEvidenceOverviewQueryService queryService = mock(
                ValidationOperationsRuntimeEvidenceOverviewQueryService.class
        );
        SchedulerExecutionLock executionLock = mock(SchedulerExecutionLock.class);
        try (AnnotationConfigApplicationContext context = context(Map.of(), queryService, executionLock)) {
            assertNotNull(context.getBean(ValidationEvidenceSchedulerProperties.class));
            assertFalse(context.containsBeanDefinition("validationEvidenceScheduler"));
            assertFalse(context.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
            verifyNoInteractions(queryService, executionLock);
        }
    }

    @Test
    void shouldRegisterOnlyValidationSchedulerWhenExplicitlyEnabled() {
        ValidationOperationsRuntimeEvidenceOverviewQueryService queryService = mock(
                ValidationOperationsRuntimeEvidenceOverviewQueryService.class
        );
        SchedulerExecutionLock executionLock = mock(SchedulerExecutionLock.class);
        Map<String, Object> properties = Map.of(
                "nq.validation-operations.scheduler.enabled", "true",
                "nq.validation-operations.scheduler.initial-delay", "PT24H"
        );

        try (AnnotationConfigApplicationContext context = context(properties, queryService, executionLock)) {
            assertNotNull(context.getBean(ValidationEvidenceScheduler.class));
            assertNotNull(context.getBean(ValidationEvidenceRefreshService.class));
            ScheduledAnnotationBeanPostProcessor processor = context.getBean(
                    TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME,
                    ScheduledAnnotationBeanPostProcessor.class
            );
            assertEquals(1, processor.getScheduledTasks().size());
            verifyNoInteractions(queryService, executionLock);
        }
    }

    @Test
    void shouldFailContextForInvalidTimeoutBeforeAnyExecution() {
        ValidationOperationsRuntimeEvidenceOverviewQueryService queryService = mock(
                ValidationOperationsRuntimeEvidenceOverviewQueryService.class
        );
        SchedulerExecutionLock executionLock = mock(SchedulerExecutionLock.class);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "test",
                Map.of("nq.validation-operations.scheduler.execution-timeout", "PT6M")
        ));
        register(context, queryService, executionLock);

        RuntimeException failure = assertThrows(RuntimeException.class, context::refresh);
        assertTrue(rootCause(failure) instanceof IllegalArgumentException);
        verifyNoInteractions(queryService, executionLock);
        context.close();
    }

    private static AnnotationConfigApplicationContext context(
            Map<String, Object> properties,
            ValidationOperationsRuntimeEvidenceOverviewQueryService queryService,
            SchedulerExecutionLock executionLock
    ) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        register(context, queryService, executionLock);
        context.refresh();
        return context;
    }

    private static void register(
            AnnotationConfigApplicationContext context,
            ValidationOperationsRuntimeEvidenceOverviewQueryService queryService,
            SchedulerExecutionLock executionLock
    ) {
        context.registerBean(
                ValidationOperationsRuntimeEvidenceOverviewQueryService.class,
                () -> queryService
        );
        context.registerBean(SchedulerExecutionLock.class, () -> executionLock);
        context.register(
                ValidationEvidenceSchedulerConfiguration.class,
                UnrelatedScheduledConfiguration.class
        );
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @Configuration(proxyBeanMethods = false)
    static class UnrelatedScheduledConfiguration {
        @Bean
        UnrelatedScheduledBean unrelatedScheduledBean() {
            return new UnrelatedScheduledBean();
        }
    }

    static final class UnrelatedScheduledBean {
        @Scheduled(fixedDelay = 1_000)
        void unrelatedScheduledMethod() {
            throw new IllegalStateException("unrelated scheduled method must never be registered or invoked");
        }
    }
}
