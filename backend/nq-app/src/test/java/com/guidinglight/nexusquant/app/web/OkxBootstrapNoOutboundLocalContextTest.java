package com.guidinglight.nexusquant.app.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.app.NexusQuantApplication;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * OkxBootstrapNoOutboundLocalContextTest 证明 local full Spring context 启动期不会访问 OKX public instruments。
 * <p>
 * Why:
 * `stub-on-bootstrap-failure=true` 只能吞掉失败，不能证明 no-outbound。这里在 context 初始化前安装
 * `ProxySelector` 探针；如果启动期请求 `https://www.okx.com/api/v5/public/instruments?instType=SPOT`，
 * 测试会直接失败，且不会依赖真实 OKX 网络可用性。
 */
@SpringBootTest(classes = NexusQuantApplication.class)
@ActiveProfiles("local")
@ContextConfiguration(initializers = OkxBootstrapNoOutboundLocalContextTest.NoOkxOutboundInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class OkxBootstrapNoOutboundLocalContextTest {

    @Autowired
    private OkxExchangeAdapter okxExchangeAdapter;

    @AfterAll
    static void restoreProxySelector() {
        NoOkxOutboundProxySelector.restoreDefault();
    }

    @Test
    void shouldBootstrapLocalContextWithoutOkxPublicInstrumentsOutbound(CapturedOutput output) {
        assertNotNull(okxExchangeAdapter);
        assertEquals(0, NoOkxOutboundProxySelector.okxPublicInstrumentsSelections());
        assertFalse(output.toString().contains("okx_adapter_bootstrap_fallback_enabled"));
    }

    static final class NoOkxOutboundInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            NoOkxOutboundProxySelector.install();
            applicationContext.addApplicationListener(event -> {
                if (event instanceof ContextClosedEvent) {
                    NoOkxOutboundProxySelector.restoreDefault();
                }
            });
        }
    }

    private static final class NoOkxOutboundProxySelector extends ProxySelector {

        private static final AtomicInteger OKX_PUBLIC_INSTRUMENTS_SELECTIONS = new AtomicInteger();
        private static ProxySelector original;

        private final ProxySelector delegate;

        private NoOkxOutboundProxySelector(ProxySelector delegate) {
            this.delegate = delegate;
        }

        private static synchronized void install() {
            if (ProxySelector.getDefault() instanceof NoOkxOutboundProxySelector) {
                OKX_PUBLIC_INSTRUMENTS_SELECTIONS.set(0);
                return;
            }
            original = ProxySelector.getDefault();
            OKX_PUBLIC_INSTRUMENTS_SELECTIONS.set(0);
            ProxySelector.setDefault(new NoOkxOutboundProxySelector(original));
        }

        private static synchronized void restoreDefault() {
            if (ProxySelector.getDefault() instanceof NoOkxOutboundProxySelector) {
                ProxySelector.setDefault(original);
            }
            original = null;
            OKX_PUBLIC_INSTRUMENTS_SELECTIONS.set(0);
        }

        private static int okxPublicInstrumentsSelections() {
            return OKX_PUBLIC_INSTRUMENTS_SELECTIONS.get();
        }

        @Override
        public List<Proxy> select(URI uri) {
            if (isOkxPublicInstruments(uri)) {
                OKX_PUBLIC_INSTRUMENTS_SELECTIONS.incrementAndGet();
                throw new AssertionError("Spring context bootstrap attempted OKX public instruments outbound: " + uri);
            }
            return delegate == null ? List.of(Proxy.NO_PROXY) : delegate.select(uri);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress socketAddress, IOException exception) {
            if (delegate != null) {
                delegate.connectFailed(uri, socketAddress, exception);
            }
        }

        private boolean isOkxPublicInstruments(URI uri) {
            if (uri == null) {
                return false;
            }
            String host = uri.getHost();
            String path = uri.getPath();
            String query = uri.getQuery();
            return "www.okx.com".equalsIgnoreCase(host)
                    && "/api/v5/public/instruments".equals(path)
                    && query != null
                    && query.toLowerCase(Locale.ROOT).contains("insttype=spot");
        }
    }
}
