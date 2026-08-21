package com.guidinglight.nexusquant.app.config.livecontrol;

import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsClient;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.livecontrol.application.PilotPrerequisiteObservationAuthority;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentAdmissionService;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotExecutionProviderPort;
import com.guidinglight.nexusquant.livecontrol.infra.KillSwitchGuardedProviderObservationAuthority;
import com.guidinglight.nexusquant.scheduler.service.AdapterInstrumentCatalogSyncService;
import com.guidinglight.nexusquant.scheduler.service.BinanceRecoveryService;
import com.guidinglight.nexusquant.scheduler.service.BinanceRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.LedgerReconcileScheduler;
import com.guidinglight.nexusquant.scheduler.service.OkxRecoveryService;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.PaperMatchingService;
import com.guidinglight.nexusquant.scheduler.service.SchedulerTradingMaintenanceService;
import com.guidinglight.nexusquant.scheduler.validationevidence.ValidationEvidenceScheduler;

import java.io.PrintWriter;
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 使用 {@link NexusQuantApplication} 的真实 component scan 验证 GateY qualification 生产图。
 *
 * <p>测试只替换无连接的 DataSource 与 typed read transport 探针，不提供 OKX/Binance
 * production adapter fake。任何 startup DB/credential/decrypt 或 OKX 调用都会使计数断言失败。</p>
 */
@ActiveProfiles("gatey-readonly-qualification")
@SpringBootTest(
        classes = NexusQuantApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ContextConfiguration(
        classes = GateYReadonlyQualificationProductionContextTest.Probes.class,
        initializers = GateYReadonlyQualificationProductionContextTest.NoOkxOutboundInitializer.class
)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.task.scheduling.enabled=false",
        "nq.auth.bootstrap-admin.enabled=false",
        "nq.account.credentials.verification-mode=STRUCTURAL",
        "nq.account.credentials.master-key=qualification-test-only-master-key-change-me",
        "nq.security.issuer=nexus-quant-qualification-test",
        "nq.security.secret=qualification-test-only-change-me-change-me",
        "nq.security.access-token-ttl=PT30M",
        "nq.runtime.trading-components.enabled=false",
        "nq.runtime.provider-observation.enabled=true",
        "nq.runtime.provider-observation.release-id=1111111111111111111111111111111111111111",
        "nq.runtime.provider-observation.source-commit=1111111111111111111111111111111111111111",
        "nq.runtime.provider-observation.capability-identity=read-only-provider-observation",
        "nq.runtime.provider-observation.order-submission-enabled=false",
        "nq.runtime.provider-observation.cancel-enabled=false",
        "nq.runtime.provider-observation.transfer-enabled=false",
        "nq.runtime.provider-observation.withdraw-enabled=false",
        "nq.env-safety.ci=false",
        "nq.env-safety.live-enabled=false",
        "nq.env-safety.ai-enabled=false",
        "nq.env-safety.dh-runtime-enabled=false",
        "nq.env-safety.real-provider-enabled=false",
        "nq.env-safety.real-client-enabled=false",
        "nq.env-safety.real-exchange-enabled=false",
        "nq.env-safety.no-outbound=false",
        "nq.instrument.catalog-sync.enabled=false",
        "nq.okx.recovery.enabled=false",
        "nq.okx.ws.enabled=false",
        "nq.binance.ws.enabled=false",
        "nq.validation-operations.scheduler.enabled=false",
        "server.address=127.0.0.1"
})
class GateYReadonlyQualificationProductionContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CountingDataSource dataSource;

    @AfterAll
    static void restoreNoOutboundGuard() {
        NoOkxOutboundProxySelector.restore();
    }

    @Test
    void fullProductionComponentScanStartsWithOnlyTrustedReadAuthority() {
        assertNotNull(context);
        assertEquals(1, context.getBeansOfType(KillSwitchGuardedProviderObservationAuthority.class).size());
        assertInstanceOf(
                KillSwitchGuardedProviderObservationAuthority.class,
                context.getBean(PilotPrerequisiteObservationAuthority.class)
        );

        assertEquals(0, context.getBeansOfType(SpotExecutionProviderPort.class).size());
        assertEquals(0, context.getBeansOfType(TradingAdapter.class).size());
        assertEquals(0, context.getBeansOfType(WorkerDeploymentAdmissionService.class).size());
        assertEquals(0, context.getBeansOfType(AdapterInstrumentCatalogSyncService.class).size());
        assertEquals(0, context.getBeansOfType(OkxRecoveryService.class).size());
        assertEquals(0, context.getBeansOfType(BinanceRecoveryService.class).size());
        assertEquals(0, context.getBeansOfType(OkxRestReconcileService.class).size());
        assertEquals(0, context.getBeansOfType(BinanceRestReconcileService.class).size());
        assertEquals(0, context.getBeansOfType(LedgerReconcileScheduler.class).size());
        assertEquals(0, context.getBeansOfType(PaperMatchingService.class).size());
        assertEquals(0, context.getBeansOfType(SchedulerTradingMaintenanceService.class).size());
        assertEquals(0, context.getBeansOfType(ValidationEvidenceScheduler.class).size());
        assertEquals(0, context.getBeansOfType(OkxWsClient.class).size());
        assertEquals(0, context.getBeansOfType(BinanceWsClient.class).size());

        assertEquals(0, dataSource.connectionAttempts.get());
        assertEquals(0, NoOkxOutboundProxySelector.selections.get());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Probes {

        @Bean
        @Primary
        CountingDataSource qualificationCountingDataSource() {
            return new CountingDataSource();
        }

    }

    static final class NoOkxOutboundInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            NoOkxOutboundProxySelector.install();
            applicationContext.addApplicationListener(event -> {
                if (event instanceof ContextClosedEvent) {
                    NoOkxOutboundProxySelector.restore();
                }
            });
        }
    }

    static final class NoOkxOutboundProxySelector extends ProxySelector {
        private static final AtomicInteger selections = new AtomicInteger();
        private static ProxySelector original;
        private final ProxySelector delegate;

        private NoOkxOutboundProxySelector(ProxySelector delegate) {
            this.delegate = delegate;
        }

        static synchronized void install() {
            original = ProxySelector.getDefault();
            selections.set(0);
            ProxySelector.setDefault(new NoOkxOutboundProxySelector(original));
        }

        static synchronized void restore() {
            if (ProxySelector.getDefault() instanceof NoOkxOutboundProxySelector) {
                ProxySelector.setDefault(original);
            }
            original = null;
            selections.set(0);
        }

        @Override
        public List<Proxy> select(URI uri) {
            if (uri != null && uri.getHost() != null && uri.getHost().toLowerCase().contains("okx")) {
                selections.incrementAndGet();
                throw new AssertionError("qualification startup attempted OKX outbound: " + uri);
            }
            return delegate == null ? List.of(Proxy.NO_PROXY) : delegate.select(uri);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress socketAddress, IOException exception) {
            if (delegate != null) {
                delegate.connectFailed(uri, socketAddress, exception);
            }
        }
    }

    static final class CountingDataSource implements DataSource {
        private final AtomicInteger connectionAttempts = new AtomicInteger();

        @Override
        public Connection getConnection() throws SQLException {
            connectionAttempts.incrementAndGet();
            throw new SQLException("qualification startup must not read DB or credential material");
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
