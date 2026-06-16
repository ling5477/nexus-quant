package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.ProxySelector;
import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * NoOutboundExchangeGuardTest 固化 Batch 3B no-outbound guard 的 fail-closed 行为。
 *
 * <p>Why: CI 不能通过“没有日志”来证明未访问交易所；必须有 deterministic guard 和受控探针。
 * 本测试直接对每个 denylisted host 触发 proxy selection，确认 guard 会在真实 DNS/HTTP/WS
 * 之前失败，同时在 CI-required 模式校验没有注入真实交易所 credential env 或 live diagnostic
 * 开关。
 */
class NoOutboundExchangeGuardTest {

    private static final List<String> FORBIDDEN_EXCHANGE_ENV_NAMES = List.of(
            "NQ_OKX_API_KEY",
            "NQ_OKX_API_SECRET",
            "NQ_OKX_API_PASSPHRASE",
            "NQ_OKX_DOME_API_KEY",
            "NQ_OKX_DOME_API_SECRET",
            "NQ_OKX_DOME_API_PASSPHRASE",
            "NQ_BINANCE_DOME_API_KEY",
            "NQ_BINANCE_DOME_API_SECRET",
            "NQ_BINANCE_DOME_PRIVATE_KEY",
            "NQ_BINANCE_DOME_PRIVATE_KEY_PATH",
            "NQ_BINANCE_REAL_API_KEY",
            "NQ_BINANCE_REAL_API_SECRET",
            "NQ_BINANCE_REAL_PRIVATE_KEY",
            "NQ_BINANCE_REAL_PRIVATE_KEY_PATH",
            "NQ_BINANCE_WS_DIAGNOSTIC_ENABLED",
            "NQ_BINANCE_WS_LIVE_DIAGNOSTIC",
            "NQ_LIVE_ENABLED",
            "NQ_REAL_PROVIDER_ENABLED",
            "NQ_REAL_CLIENT_ENABLED"
    );

    @BeforeEach
    void installGuard() {
        ExchangeNoOutboundGuard.install();
    }

    @AfterEach
    void restoreGuard() {
        ExchangeNoOutboundGuard.restoreDefault();
    }

    @Test
    void shouldFailClosedForEveryRequiredExchangeHostBeforeNetworkConnect() {
        ExchangeNoOutboundGuard.assertRequiredDenylistCoverage();

        for (String host : ExchangeNoOutboundGuard.requiredDenylistedHosts()) {
            URI uri = URI.create("https://" + host + "/nq-ci-controlled-deny-probe");
            assertThrows(
                    AssertionError.class,
                    () -> ProxySelector.getDefault().select(uri),
                    () -> "expected no-outbound guard to reject " + host
            );
        }

        assertEquals(
                ExchangeNoOutboundGuard.requiredDenylistedHosts().size(),
                ExchangeNoOutboundGuard.deniedSelections()
        );
    }

    @Test
    void shouldNotRejectLocalhostFakeServerTraffic() {
        ProxySelector.getDefault().select(URI.create("http://127.0.0.1:18080/fake-exchange"));
        ProxySelector.getDefault().select(URI.create("http://localhost:18080/fake-exchange"));

        assertEquals(0, ExchangeNoOutboundGuard.deniedSelections());
    }

    @Test
    void shouldRejectExchangeCredentialEnvWhenCiGuardIsRequired() {
        assumeTrue(isCiGuardRequired(), "env absence is enforced only in CI/no-outbound guard mode");

        for (String envName : FORBIDDEN_EXCHANGE_ENV_NAMES) {
            String value = System.getenv(envName);
            if (value != null && !value.isBlank()) {
                throw new AssertionError("CI no-outbound guard forbids exchange credential/live env: " + envName);
            }
        }
        if (Boolean.getBoolean("nq.binance.ws.live.diagnostic")) {
            throw new AssertionError("CI no-outbound guard forbids nq.binance.ws.live.diagnostic=true");
        }
    }

    private static boolean isCiGuardRequired() {
        return Boolean.getBoolean("nq.no-outbound.guard.required")
                || "true".equalsIgnoreCase(System.getenv("CI"));
    }
}
