package com.guidinglight.nexusquant.app.config;

import com.guidinglight.nexusquant.observability.operational.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.util.List;
import org.mockito.Mockito;
import static org.assertj.core.api.Assertions.assertThat;
import static com.guidinglight.nexusquant.observability.operational.OperationalObservation.Operation.*;
import static com.guidinglight.nexusquant.observability.operational.OperationalObservation.Signal.*;

class OperationalObservationConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class))
            .withUserConfiguration(OperationalObservationConfiguration.class);

    @Test
    void actuatorReadsRealMetersAndOperationalFailureDoesNotChangeHealthStatus() {
        runner.withUserConfiguration(OperationalObservationConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(OperationalObservation.class).hasSingleBean(MeterRegistry.class);
            var observation = context.getBean(OperationalObservation.class);
            observation.record(LEDGER_RECONCILE, ATTEMPT, 1);
            observation.record(LEDGER_RECONCILE, FAILURE, 1);
            observation.record(LEDGER_RECONCILE, FAILURE, 1);
            var endpoint = new MetricsEndpoint(context.getBean(MeterRegistry.class));
            assertThat(endpoint.listNames().getNames()).contains("nq.operational.executions", "nq.operational.last.failure");
            assertThat(endpoint.metric("nq.operational.executions", List.of("result:failure")).getMeasurements().getFirst().getValue()).isEqualTo(2);
            var health = context.getBean("operationalHealthIndicator", HealthIndicator.class).health();
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails().toString()).contains("lastFailureEpochSeconds", "observed=false");
        });
    }

    @Test
    void suppliedPortBacksOffWithoutDuplicateBeanOrMeter() {
        runner.withBean(OperationalObservation.class, () -> OperationalObservation.NOOP).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(OperationalObservation.class);
            assertThat(context.getBean(OperationalObservation.class)).isSameAs(OperationalObservation.NOOP);
        });
    }

    @Test
    void summaryReadFailureRemainsAdvisoryAndDoesNotExposeException() {
        var broken = Mockito.mock(MicrometerOperationalObservation.class);
        Mockito.when(broken.snapshot()).thenThrow(new IllegalStateException("private-synthetic-detail"));
        runner.withBean(OperationalObservation.class, () -> broken).run(context -> {
            var health = context.getBean("operationalHealthIndicator", HealthIndicator.class).health();
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("observation", "unavailable");
            assertThat(health.getDetails().toString()).doesNotContain("private-synthetic-detail");
        });
    }
}
