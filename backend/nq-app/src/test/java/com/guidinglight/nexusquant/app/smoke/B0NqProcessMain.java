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
import com.guidinglight.nexusquant.ledger.service.port.TradeLedgerPort;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.scheduler.service.OkxRecoveryService;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.trading.application.CancelOrderRequest;
import java.math.BigDecimal;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/** 从 stdin 接收有限场景命令的真实 Spring JVM；没有 HTTP 后门或 SQL mutation 命令。 */
public final class B0NqProcessMain {
    private static volatile String placeVenue = "OKX";
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
            @Override public void connectFailed(URI uri, SocketAddress address, IOException error) { }
        });
        try (var context = new SpringApplicationBuilder(NexusQuantApplication.class, VenueConfiguration.class)
                .web(WebApplicationType.NONE).run(
                        "--spring.profiles.active=local,b0-test",
                        "--spring.config.location=classpath:/application.yml,classpath:/application-local.yml",
                        "--spring.datasource.url=" + db, "--spring.datasource.username=" + B0Fixture.APP,
                        "--spring.datasource.password=", "--spring.flyway.enabled=false",
                        "--spring.main.allow-bean-definition-overriding=true",
                        "--spring.task.scheduling.enabled=" + (B5QualificationControls.strategyRecoveryEnabled || L6QualificationControls.enabled), "--nq.runtime.trading-components.enabled=true",
                        "--nq.validation-operations.scheduler.enabled=" + (B5QualificationControls.schedulerEnabled || L6QualificationControls.enabled),
                        "--nq.validation-operations.scheduler.initial-delay=" + (L6QualificationControls.enabled ? "PT30S" : "PT24H"),
                        "--nq.validation-operations.scheduler.execution-timeout=PT1M", "--nq.okx.recovery.enabled=false",
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
            try (var qualification = new B5QualificationControls(context);
                 var convergence = L6ConvergenceControls.enabled ? new L6ConvergenceControls(context) : null;
                 var l6 = L6QualificationControls.enabled ? new L6QualificationControls(context) : null;
                 var l5 = L5QualificationControls.enabled ? new L5QualificationControls(context) : null;
                 var executor = Executors.newSingleThreadExecutor();
                 var commands = new BufferedReader(new InputStreamReader(System.in))) {
                Future<String> pending = null;
                String command;
                while ((command = commands.readLine()) != null) {
                    if ("STOP".equals(command)) return;
                    String convergenceResult = convergence == null ? null : convergence.handle(command);
                    if (convergenceResult != null) {
                        System.out.println("B0_RESULT " + convergenceResult);
                        System.out.flush();
                        continue;
                    }
                    if (l6 != null) {
                        System.out.println("B0_RESULT " + l6.handle(command));
                        System.out.flush();
                        continue;
                    }
                    if (l5 != null && !L5QualificationControls.isRepeatedFaultCommand(command)) {
                        System.out.println("B0_RESULT " + l5.handle(command));
                        System.out.flush();
                        continue;
                    }
                    String qualificationResult = qualification.handle(command);
                    if (qualificationResult != null) {
                        System.out.println("B0_RESULT " + qualificationResult);
                        System.out.flush();
                        continue;
                    }
                    if (command.startsWith("BEGIN_")) {
                        B0Fixture.require(pending == null);
                        String operation = command.substring(6);
                        B0Fixture.require(Set.of("PLACE_B2", "PLACE_B2_LIVE", "CANCEL").contains(operation));
                        pending = executor.submit(() -> tradingCommand(context.getBean(OrderCommandService.class), jdbc, name, operation));
                        System.out.println("B0_RESULT BEGIN " + operation);
                    } else if ("AWAIT".equals(command)) {
                        B0Fixture.require(pending != null);
                        System.out.println("B0_RESULT " + pending.get(25, TimeUnit.SECONDS));
                        pending = null;
                    } else if (command.startsWith("SET_B5_VENUE ")) {
                        B0Fixture.require(pending == null);
                        placeVenue = new String(Base64.getDecoder().decode(command.substring(13)),
                                StandardCharsets.UTF_8);
                        System.out.println("B0_RESULT VENUE_INPUT_SET");
                    } else if ("TRY_PLACE_B5".equals(command)) {
                        try {
                            System.out.println("B0_RESULT " + tradingCommand(context.getBean(OrderCommandService.class), jdbc, name, "PLACE_B2"));
                        } catch (IllegalArgumentException rejected) {
                            System.out.println("B0_RESULT INVALID_VENUE_REJECTED");
                        }
                    } else if ("ARM_B5_POST_ARM".equals(command)) {
                        B5PreSendBarrier.arm(context.getBean(OrderCommandWriteService.class), "armOrdinaryPlace", "POST_ARM");
                        System.out.println("B0_RESULT ARMED B5_POST_ARM");
                    } else if ("ARM_B5_PRE_SEND".equals(command)) {
                        B5PreSendBarrier.arm(context.getBean(OrderCommandWriteService.class));
                        System.out.println("B0_RESULT ARMED B5_PRE_SEND");
                    } else if ("RELEASE_B5_PRE_SEND".equals(command)) {
                        B5PreSendBarrier.release();
                        System.out.println("B0_RESULT RELEASED B5_PRE_SEND");
                    } else if ("B5_RECOVERY".equals(command)) {
                        var report = context.getBean(OkxRecoveryService.class)
                                .rebuild("b5-recovery");
                        System.out.println("B0_RESULT B5_RECOVERY " + report);
                    } else if ("ENGAGE".equals(command)) {
                        var kill = context.getBean(KillSwitchService.class);
                        var engaged = kill.engage(kill.snapshot().version(), "B3_IN_FLIGHT", "B3_TEST", "b3-kill");
                        System.out.println("B0_RESULT ENGAGE " + engaged.status() + " " + engaged.version());
                    } else if (command.startsWith("ARM_B4_TX ")) {
                        String[] parts = command.split(" ");
                        B0Fixture.require(parts.length == 3);
                        Object target;
                        String method;
                        switch (parts[1]) {
                            case "PREPARE" -> { target = context.getBean(OrderCommandWriteService.class); method = "preparePlaceOrder"; }
                            case "AUTHORITY" -> { target = context.getBean(OrderCommandWriteService.class); method = "armOrdinaryPlace"; }
                            case "ACK" -> { target = context.getBean(OrderCommandWriteService.class); method = "finalizeAcceptedPlaceOrder"; }
                            case "TRADE" -> { target = context.getBean(TradeRepository.class); method = "insertWithRequiredEvent"; }
                            case "LEDGER" -> { target = context.getBean(TradeLedgerPort.class); method = "postTrade"; }
                            default -> throw new IllegalArgumentException("unsupported B4 transaction owner");
                        }
                        B4TransactionFaults.arm(target, jdbc, method, parts[2]);
                        System.out.println("B0_RESULT ARMED " + parts[1] + " " + parts[2]);
                    } else if ("ARM_L5_FILL".equals(command)) {
                        L5FillControls.arm(context.getBean(TradeRepository.class));
                        System.out.println("B0_RESULT ARMED L5_FILL");
                    } else if ("ARM_B4_TRADE_COMMIT".equals(command)) {
                        B4ProcessFaults.armAfterTradeCommit(context.getBean(
                                TradeRepository.class));
                        System.out.println("B0_RESULT ARMED AFTER_TRADE_COMMIT");
                    } else if ("RECOVER".equals(command)) {
                        int count = context.getBean(OkxRestReconcileService.class).reconcileOnce(100);
                        System.out.println("B0_RESULT RECOVER " + count);
                    } else {
                        System.out.println("B0_RESULT " + tradingCommand(context.getBean(OrderCommandService.class), jdbc, name, command));
                    }
                    System.out.flush();
                }
            }
        }
    }

    /** 异步屏障仍调用真实命令服务；主线程可运行普通对账或 canonical ENGAGE，没有直接写表接口。 */
    private static String tradingCommand(OrderCommandService commands, JdbcTemplate jdbc, String name, String command) {
        if ("PLACE".equals(command) || "PLACE_B2".equals(command) || "PLACE_B2_LIVE".equals(command)
                || "PLACE_B3_NEW".equals(command)) {
            Long account = jdbc.queryForObject("SELECT account_id FROM accounts WHERE account_code='b0-account'", Long.class);
            String client = "b0" + name.substring(name.length() - 30);
            if ("PLACE_B3_NEW".equals(command)) client = "b3" + name.substring(name.length() - 30);
            var result = commands.placeOrder(new PlaceOrderRequest(
                    "b0-request", account, null, placeVenue, "BTC-USDT", client, account + ":" + client,
                    "b0_test", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("100.00000000"),
                    new BigDecimal("PLACE".equals(command) ? "0.10000000" : "10.00000000"), "GTC", "b0-trace",
                    "PLACE_B2_LIVE".equals(command) ? "LIVE" : "SIM", null));
            return "PLACE " + result.orderId() + " " + result.status();
        }
        if ("CANCEL".equals(command)) {
            String orderId = jdbc.queryForObject("SELECT order_id FROM orders", String.class);
            var result = commands.cancelOrder(new CancelOrderRequest(
                    "b2-cancel", orderId, null, "OKX", "BTC-USDT", null, null, "B2_CANCEL", "b0-trace"));
            return "CANCEL " + result.orderId() + " " + result.status();
        }
        throw new IllegalArgumentException("unsupported B0 command");
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
                    mapper, venue, Duration.ofSeconds(25));
            return new OkxExchangeAdapter(new OkxExchangeAdapter.Dependencies(mapper, transport,
                    new OkxInstrumentsCache(transport, Clock.systemUTC(), Duration.ofHours(1)), Clock.systemUTC(), "SIM"));
        }
    }
}
