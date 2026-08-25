package com.guidinglight.nexusquant.app.config.livecontrol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.infra.okx.readonly.JdbcOkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.JdkOkxPrivateReadTransport;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadTransport;
import com.guidinglight.nexusquant.app.config.ExchangeAdapterConfiguration;
import com.guidinglight.nexusquant.app.config.account.AccountCredentialRuntimeProperties;
import com.guidinglight.nexusquant.livecontrol.application.PilotPrerequisiteObservationAuthority;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentAdmissionService;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotExecutionProviderPort;
import com.guidinglight.nexusquant.livecontrol.infra.KillSwitchGuardedProviderObservationAuthority;
import com.guidinglight.nexusquant.livecontrol.infra.UnavailablePilotPrerequisiteObservationAuthority;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService;
import com.guidinglight.nexusquant.risk.service.KillSwitchEngageCommand;
import com.guidinglight.nexusquant.risk.service.KillSwitchScope;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchState;
import com.guidinglight.nexusquant.risk.service.KillSwitchStateRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;
import com.guidinglight.nexusquant.scheduler.service.BinanceRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.LedgerReconcileScheduler;
import com.guidinglight.nexusquant.scheduler.service.OkxRecoveryService;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.PaperMatchingService;
import com.guidinglight.nexusquant.scheduler.validationevidence.ValidationEvidenceScheduler;
import com.guidinglight.nexusquant.scheduler.validationevidence.ValidationEvidenceSchedulerConfiguration;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GateYReadonlyQualificationConfigurationTest {

    private static final String COMMIT = "1111111111111111111111111111111111111111";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ReadOnlyProviderObservationConfiguration.class,
                    UnavailablePilotPrerequisiteObservationAuthority.class,
                    Dependencies.class
            );

    @Test
    void defaultContextKeepsTrustedRealObservationAuthorityUnbound() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBeansOfType(KillSwitchGuardedProviderObservationAuthority.class).isEmpty());
            assertEquals(1, context.getBeansOfType(PilotPrerequisiteObservationAuthority.class).size());
            assertInstanceOf(
                    UnavailablePilotPrerequisiteObservationAuthority.class,
                    context.getBean(PilotPrerequisiteObservationAuthority.class)
            );
            assertTrue(context.getBeansOfType(OkxPrivateCredentialExecutor.class).isEmpty());
        });
    }

    @Test
    void explicitQualificationContextBindsOnlyGuardedObservationWithoutStartupSideEffects() {
        runner.withUserConfiguration(
                        ExchangeAdapterConfiguration.class,
                        OkxRecoveryService.class,
                        OkxRestReconcileService.class,
                        BinanceRestReconcileService.class,
                        LedgerReconcileScheduler.class,
                        PaperMatchingService.class,
                        ValidationEvidenceSchedulerConfiguration.class
                )
                .withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("gatey-readonly-qualification"))
                .withPropertyValues(qualificationProperties())
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(1, context.getBeansOfType(
                            KillSwitchGuardedProviderObservationAuthority.class).size());
                    assertEquals(2, context.getBeansOfType(PilotPrerequisiteObservationAuthority.class).size());
                    assertInstanceOf(
                            KillSwitchGuardedProviderObservationAuthority.class,
                            context.getBean(PilotPrerequisiteObservationAuthority.class)
                    );
                    assertInstanceOf(
                            JdbcOkxPrivateCredentialExecutor.class,
                            context.getBean(OkxPrivateCredentialExecutor.class)
                    );
                    assertInstanceOf(
                            JdkOkxPrivateReadTransport.class,
                            context.getBean(OkxPrivateReadTransport.class)
                    );
                    assertEquals(0, context.getBeansOfType(SpotExecutionProviderPort.class).size());
                    assertEquals(0, context.getBeansOfType(TradingAdapter.class).size());
                    assertEquals(0, context.getBeansOfType(WorkerDeploymentAdmissionService.class).size());
                    assertEquals(0, context.getBeansOfType(OkxRecoveryService.class).size());
                    assertEquals(0, context.getBeansOfType(OkxRestReconcileService.class).size());
                    assertEquals(0, context.getBeansOfType(BinanceRestReconcileService.class).size());
                    assertEquals(0, context.getBeansOfType(LedgerReconcileScheduler.class).size());
                    assertEquals(0, context.getBeansOfType(PaperMatchingService.class).size());
                    assertEquals(0, context.getBeansOfType(ValidationEvidenceScheduler.class).size());
                    assertEquals(0, context.getBean(CountingDataSource.class).connectionAttempts.get());
                    assertEquals(0, context.getBean(CountingKillSwitchRepository.class).reads.get());
                });
    }

    @Test
    void missingMutationDenialsOrLiveEnabledFailClosedToUnavailableAuthority() {
        runner.withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("gatey-readonly-qualification"))
                .withPropertyValues(
                        "nq.runtime.provider-observation.enabled=true",
                        "nq.runtime.provider-observation.order-submission-enabled=false",
                        "nq.runtime.provider-observation.cancel-enabled=false",
                        "nq.runtime.provider-observation.transfer-enabled=false",
                        "nq.env-safety.ci=false",
                        "nq.env-safety.live-enabled=true",
                        "nq.env-safety.real-exchange-enabled=false",
                        "nq.env-safety.real-client-enabled=false",
                        "nq.env-safety.real-provider-enabled=false",
                        "nq.env-safety.no-outbound=false"
                )
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(1, context.getBeansOfType(PilotPrerequisiteObservationAuthority.class).size());
                    assertTrue(context.getBeansOfType(OkxPrivateCredentialExecutor.class).isEmpty());
                });
    }

    @Test
    void futureSensitiveConsumerRemainsAbsentUntilCapabilityIsExplicitlyEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(FutureSensitiveConsumerConfiguration.class)
                .run(context -> assertTrue(context.getBeansOfType(FutureSensitiveConsumer.class).isEmpty()));
    }

    private static String[] qualificationProperties() {
        return new String[]{
                "nq.runtime.provider-observation.enabled=true",
                "nq.runtime.provider-observation.release-id=" + COMMIT,
                "nq.runtime.provider-observation.source-commit=" + COMMIT,
                "nq.runtime.provider-observation.capability-identity=read-only-provider-observation",
                "nq.runtime.provider-observation.order-submission-enabled=false",
                "nq.runtime.provider-observation.cancel-enabled=false",
                "nq.runtime.provider-observation.transfer-enabled=false",
                "nq.runtime.provider-observation.withdraw-enabled=false",
                "nq.env-safety.ci=false",
                "nq.env-safety.live-enabled=false",
                "nq.env-safety.real-exchange-enabled=false",
                "nq.env-safety.real-client-enabled=false",
                "nq.env-safety.real-provider-enabled=false",
                "nq.env-safety.no-outbound=false",
                "nq.validation-operations.scheduler.enabled=false",
                "server.address=127.0.0.1"
        };
    }

    static final class FutureSensitiveConsumer {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "nq.runtime.trading-components",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = false
    )
    static class FutureSensitiveConsumerConfiguration {

        @Bean
        FutureSensitiveConsumer futureSensitiveConsumer() {
            return new FutureSensitiveConsumer();
        }
    }

    @Configuration
    static class Dependencies {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        CountingDataSource dataSource() {
            return new CountingDataSource();
        }

        @Bean
        JdbcTemplate jdbcTemplate(CountingDataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        AccountCredentialRuntimeProperties accountCredentialRuntimeProperties() {
            AccountCredentialRuntimeProperties properties = new AccountCredentialRuntimeProperties();
            properties.setMasterKey("test-only-non-production-master-key");
            return properties;
        }

        @Bean
        CountingKillSwitchRepository killSwitchStateRepository() {
            return new CountingKillSwitchRepository();
        }

        @Bean
        KillSwitchService killSwitchService(CountingKillSwitchRepository repository) {
            return new KillSwitchService(
                    repository,
                    Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC)
            );
        }

        @Bean
        InstrumentCatalogService instrumentCatalogService() {
            return mock(InstrumentCatalogService.class);
        }
    }

    static final class CountingKillSwitchRepository implements KillSwitchStateRepository {
        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public Optional<KillSwitchState> findByScope(KillSwitchScope scope) {
            reads.incrementAndGet();
            return Optional.of(new KillSwitchState(
                    scope, KillSwitchStatus.ENGAGED, 1, "TEST_ENGAGED", "TEST_FIXTURE",
                    Instant.parse("2026-08-18T23:59:59Z"), "tester", "trace-config"));
        }

        @Override
        public KillSwitchState engage(KillSwitchEngageCommand command) {
            throw new UnsupportedOperationException();
        }
    }

    static final class CountingDataSource implements DataSource {
        private final AtomicInteger connectionAttempts = new AtomicInteger();

        @Override
        public Connection getConnection() throws SQLException {
            connectionAttempts.incrementAndGet();
            throw new SQLException("test datasource must not be accessed during startup");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("unsupported");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
