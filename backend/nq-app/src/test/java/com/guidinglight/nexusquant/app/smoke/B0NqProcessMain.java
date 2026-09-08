package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.OkxHttpClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxInstrumentsCache;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.risk.service.RiskGate;
import com.guidinglight.nexusquant.risk.service.PreTradeRiskService;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.AdapterBackedTradingVenueGateway;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.OrderCommandWriteService;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;
import com.guidinglight.nexusquant.trading.application.port.TradingVenueGateway;
import java.math.BigDecimal;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/** 从 stdin 接收有限场景命令的真实 Spring JVM；没有 HTTP 后门或 SQL mutation 命令。 */
public final class B0NqProcessMain {
    public static void main(String[] args) throws Exception {
        B0Fixture.require(args.length == 0);
        String db = System.getenv("NQ_B0_DB");
        String name = URI.create(db.substring(5)).getPath().substring(1);
        String venue = System.getenv("NQ_B0_VENUE");
        B0Fixture.validate(db, name, System.getenv("NQ_B0_PROFILE"), venue, System.getenv(), false);
        // 复用历史交易所 denylist，进一步收紧 JDK HTTP/WebSocket 为唯一合成 venue origin。
        ExchangeNoOutboundGuard.install();
        ProxySelector.setDefault(new ProxySelector() {
            @Override public List<Proxy> select(URI uri) {
                B0Fixture.require(allowedDestination(uri, venue, db));
                return List.of(Proxy.NO_PROXY);
            }
            @Override public void connectFailed(URI uri, SocketAddress address, java.io.IOException error) { }
        });
        try (var context = new SpringApplicationBuilder(NexusQuantApplication.class, VenueConfiguration.class)
                .web(WebApplicationType.NONE).run(
                        "--spring.profiles.active=local,b0-test",
                        "--spring.config.location=classpath:/application.yml,classpath:/application-local.yml",
                        "--spring.datasource.url=" + db, "--spring.datasource.username=" + B0Fixture.APP,
                        "--spring.datasource.password=", "--spring.flyway.enabled=false",
                        "--spring.main.allow-bean-definition-overriding=true",
                        "--spring.task.scheduling.enabled=false", "--nq.runtime.trading-components.enabled=true",
                        "--nq.validation-operations.scheduler.enabled=false", "--nq.okx.recovery.enabled=false",
                        "--nq.okx.ws.enabled=false", "--nq.binance.ws.enabled=false",
                        "--nq.instrument.catalog-sync.enabled=false", "--nq.env-safety.live-enabled=false",
                        "--nq.env-safety.ai-enabled=false", "--nq.env-safety.dh-runtime-enabled=false",
                        "--nq.env-safety.real-provider-enabled=false", "--nq.env-safety.real-client-enabled=false",
                        "--nq.env-safety.real-exchange-enabled=false", "--nq.env-safety.no-outbound=true")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            B0Fixture.require(name.equals(jdbc.queryForObject("SELECT identity FROM b0_fixture_identity", String.class)));
            B0Fixture.require(B0Fixture.APP.equals(jdbc.queryForObject("SELECT current_user", String.class)));
            B0Fixture.require(AopUtils.isAopProxy(context.getBean(OrderCommandWriteService.class)));
            B0Fixture.require(context.getBean(RiskGate.class) instanceof PreTradeRiskService);
            B0Fixture.require(context.getBean(TradingVenueGateway.class) instanceof AdapterBackedTradingVenueGateway);
            System.out.println("B0_READY " + ProcessHandle.current().pid());
            System.out.println("B0_COMPOSITION realRisk=true writeProxy=true gateway=AdapterBackedTradingVenueGateway jdbcUser=" + B0Fixture.APP);
            System.out.flush();
            try (var commands = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))) {
                String command;
                while ((command = commands.readLine()) != null) {
                    if ("STOP".equals(command)) return;
                    if ("PLACE".equals(command)) {
                        Long account = jdbc.queryForObject("SELECT account_id FROM accounts WHERE account_code='b0-account'", Long.class);
                        String client = "b0" + name.substring(name.length() - 30);
                        var result = context.getBean(OrderCommandService.class).placeOrder(new PlaceOrderRequest(
                                "b0-request", account, null, "OKX", "BTC-USDT", client, account + ":" + client,
                                "b0_test", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("100.00000000"),
                                new BigDecimal("0.10000000"), "GTC", "b0-trace"));
                        System.out.println("B0_RESULT PLACE " + result.orderId() + " " + result.status());
                    } else if ("RECOVER".equals(command)) {
                        int count = context.getBean(OkxRestReconcileService.class).reconcileOnce(100);
                        System.out.println("B0_RESULT RECOVER " + count);
                    } else throw new IllegalArgumentException("unsupported B0 command");
                    System.out.flush();
                }
            }
        }
    }

    static boolean allowedDestination(URI uri, String venue, String db) {
        return "127.0.0.1".equals(uri.getHost()) && uri.getUserInfo() == null
                && (("http".equals(uri.getScheme()) && uri.getPort() == URI.create(venue).getPort())
                || ("socket".equals(uri.getScheme()) && uri.getPort() == URI.create(db.substring(5)).getPort()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("b0-test")
    static class VenueConfiguration {
        @Bean(name = "okxTradingAdapter")
        OkxExchangeAdapter adapter(ObjectMapper mapper) {
            String venue = System.getenv("NQ_B0_VENUE");
            B0Fixture.requireVenue(venue);
            var transport = new OkxHttpClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                    mapper, venue, Duration.ofSeconds(2));
            return new OkxExchangeAdapter(new OkxExchangeAdapter.Dependencies(mapper, transport,
                    new OkxInstrumentsCache(transport, Clock.systemUTC(), Duration.ofHours(1)), Clock.systemUTC(), "SIM"));
        }
    }
}
