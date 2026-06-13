package com.guidinglight.nexusquant.account.infra.probe;

import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeRequest;
import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoRealExchangeCredentialPermissionProbePortTest {

    @Test
    void shouldSkipWithoutResolvingOrConnectingToRealExchangeHosts() {
        ProxySelector previous = ProxySelector.getDefault();
        ProxySelector.setDefault(new RejectRealExchangeProxySelector());
        try {
            NoRealExchangeCredentialPermissionProbePort port = new NoRealExchangeCredentialPermissionProbePort();

            ExchangeCredentialPermissionProbeResult result = port.probe(new ExchangeCredentialPermissionProbeRequest(
                    900001L,
                    1L,
                    "OKX",
                    "SIM",
                    "OKX_API_V5",
                    "PAPER",
                    true,
                    "trace-no-real-exchange",
                    "{\"apiKey\":\"not-used\"}"
            ));

            assertEquals("SKIPPED", result.permissionProbeStatus());
            assertEquals("REAL_EXCHANGE_PROBE_DISABLED", result.sanitizedErrorCategory());
        } finally {
            ProxySelector.setDefault(previous);
        }
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
