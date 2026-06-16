package com.guidinglight.nexusquant.app.smoke;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ExchangeNoOutboundGuard 是 Batch 3B test-scope 出站护栏。
 *
 * <p>Why: CI 与 app context smoke 需要证明默认测试不会连接真实交易所，但不能通过真实
 * DNS、真实 HTTP 或真实 credential 来证明。该 guard 安装到 JVM 默认 {@link ProxySelector}，
 * 在 JDK {@code HttpClient} / WebSocket 进入真实连接前拦截 denylisted host；如果未来启动
 * 期、scheduler、recovery、catalog sync、REST adapter 或 WS client 误触发真实交易所 URI，
 * 测试会在 proxy selection 阶段 fail closed。
 *
 * <p>边界：本类只存在于 {@code src/test}; 不阻止 PostgreSQL/JDBC、localhost fake server 或
 * 其他非交易所测试流量；不读取 env secret、不打印 credential material、不替代 Batch 4 secret
 * scan。
 */
final class ExchangeNoOutboundGuard extends ProxySelector {

    private static final Set<String> REQUIRED_DENYLISTED_HOSTS = Set.of(
            "okx.com",
            "www.okx.com",
            "my.okx.com",
            "binance.com",
            "api.binance.com",
            "fapi.binance.com",
            "dapi.binance.com",
            "testnet.binance.vision",
            "ws-api.binance.com",
            "ws-api.testnet.binance.vision",
            "stream.binance.com",
            "stream.binancefuture.com",
            "bybit.com",
            "api.bybit.com",
            "bitget.com",
            "gate.io",
            "api.gateio.ws",
            "coinbase.com",
            "api.coinbase.com",
            "kraken.com",
            "api.kraken.com",
            "crypto.com",
            "hyperliquid.xyz",
            "api.hyperliquid.xyz"
    );

    private static final Set<String> DENYLISTED_HOSTS = new LinkedHashSet<>(REQUIRED_DENYLISTED_HOSTS);
    private static final AtomicInteger DENIED_SELECTIONS = new AtomicInteger();
    private static ProxySelector original;

    private final ProxySelector delegate;

    private ExchangeNoOutboundGuard(ProxySelector delegate) {
        this.delegate = delegate;
    }

    static synchronized void install() {
        if (ProxySelector.getDefault() instanceof ExchangeNoOutboundGuard) {
            DENIED_SELECTIONS.set(0);
            return;
        }
        original = ProxySelector.getDefault();
        DENIED_SELECTIONS.set(0);
        ProxySelector.setDefault(new ExchangeNoOutboundGuard(original));
    }

    static synchronized void restoreDefault() {
        if (ProxySelector.getDefault() instanceof ExchangeNoOutboundGuard) {
            ProxySelector.setDefault(original);
        }
        original = null;
        DENIED_SELECTIONS.set(0);
    }

    static int deniedSelections() {
        return DENIED_SELECTIONS.get();
    }

    static Set<String> requiredDenylistedHosts() {
        return REQUIRED_DENYLISTED_HOSTS;
    }

    static void assertRequiredDenylistCoverage() {
        if (!DENYLISTED_HOSTS.containsAll(REQUIRED_DENYLISTED_HOSTS)) {
            throw new AssertionError("no-outbound denylist is missing required exchange hosts");
        }
    }

    @Override
    public List<Proxy> select(URI uri) {
        String host = normalizeHost(uri);
        if (isDenylisted(host)) {
            DENIED_SELECTIONS.incrementAndGet();
            throw new AssertionError("real exchange outbound is forbidden in CI/test smoke: " + uri);
        }
        return delegate == null ? List.of(Proxy.NO_PROXY) : delegate.select(uri);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress socketAddress, IOException exception) {
        String host = normalizeHost(uri);
        if (isDenylisted(host)) {
            throw new AssertionError("real exchange connect failure proves a forbidden outbound attempt: " + uri, exception);
        }
        if (delegate != null) {
            delegate.connectFailed(uri, socketAddress, exception);
        }
    }

    private static boolean isDenylisted(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        for (String deniedHost : DENYLISTED_HOSTS) {
            if (host.equals(deniedHost) || host.endsWith("." + deniedHost)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeHost(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return null;
        }
        return uri.getHost().toLowerCase(Locale.ROOT);
    }
}
