package com.guidinglight.nexusquant.account.infra.probe;

import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeRequest;
import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeResult;
import com.guidinglight.nexusquant.account.domain.CredentialPermissionExpectation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NoRealExchangeCredentialPermissionProbePortTest {

    @Test
    void shouldSkipWithoutResolvingOrConnectingToRealExchangeHosts() {
        ProxySelector previous = ProxySelector.getDefault();
        ProxySelector.setDefault(new RejectRealExchangeProxySelector());
        try {
            NoRealExchangeCredentialPermissionProbePort port = new NoRealExchangeCredentialPermissionProbePort();

            ExchangeCredentialPermissionProbeResult result = port.probe(new ExchangeCredentialPermissionProbeRequest(
                    1L,
                    900001L,
                    1L,
                    "OKX",
                    "SIM",
                    "OKX_API_V5",
                    CredentialPermissionExpectation.READ_ONLY_DIAGNOSTIC,
                    true,
                    "trace-no-real-exchange"
            ));

            assertEquals("SKIPPED", result.permissionProbeStatus());
            assertEquals("REAL_EXCHANGE_PROBE_DISABLED", result.sanitizedErrorCategory());
            assertEquals("trace-no-real-exchange", result.traceId());
            assertNotNull(result.requestId());
            assertFalse(result.requestId().isBlank());
            assertFalse(result.requestId().contains("not-used"));
            assertFalse(result.requestId().contains("trace-no-real-exchange"));
            assertNotEquals(result.traceId(), result.requestId());
        } finally {
            ProxySelector.setDefault(previous);
        }
    }

    @Test
    void noRealProbeDoesNotDeclareRealHttpClientDependency() {
        boolean hasHttpClientField = Arrays.stream(NoRealExchangeCredentialPermissionProbePort.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(HttpClient.class::isAssignableFrom);

        assertFalse(hasHttpClientField, "NoReal probe must remain pure no-http and must not hold a real HttpClient");
    }

    /**
     * RejectRealExchangeProxySelector 是 no-real-exchange 测试护栏。
     *
     * <p>Why: 如果默认 fake port 未来误引入 HTTP client 或真实 OKX/Binance URL，本测试会在
     * proxy selection 阶段失败，避免 full Maven test 访问真实交易所。</p>
     */
    private static final class RejectRealExchangeProxySelector extends ProxySelector {
        @Override
        public List<java.net.Proxy> select(URI uri) {
            String host = uri == null ? null : uri.getHost();
            if ("www.okx.com".equalsIgnoreCase(host) || "api.binance.com".equalsIgnoreCase(host)) {
                throw new AssertionError("real exchange host access is forbidden in tests: " + host);
            }
            return List.of(java.net.Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            throw new AssertionError("unexpected network connect failure during no-real-exchange test", ioe);
        }
    }
}
