package com.guidinglight.nexusquant.app.config;

import com.guidinglight.nexusquant.observability.operational.MicrometerOperationalObservation;
import com.guidinglight.nexusquant.observability.operational.OperationalObservation;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 仅负责装配；运行降级属于诊断信息，不改变进程就绪状态。 */
@Configuration(proxyBeanMethods = false)
public class OperationalObservationConfiguration {
    @Bean
    @ConditionalOnMissingBean(OperationalObservation.class)
    MicrometerOperationalObservation operationalObservation(MeterRegistry registry) {
        return new MicrometerOperationalObservation(registry, Clock.systemUTC());
    }

    @Bean
    HealthIndicator operationalHealthIndicator(OperationalObservation observation) {
        return () -> {
            if (observation instanceof MicrometerOperationalObservation meters) {
                try {
                    return Health.up().withDetail("scope", "process_local_last_observed")
                            .withDetail("operations", meters.snapshot()).build();
                } catch (RuntimeException ex) {
                    // 观测组件读取失败仅标记摘要不可用，不改变进程健康或泄露异常内容。
                    return Health.up().withDetail("observation", "unavailable").build();
                }
            }
            return Health.unknown().withDetail("observation", "unavailable").build();
        };
    }
}
