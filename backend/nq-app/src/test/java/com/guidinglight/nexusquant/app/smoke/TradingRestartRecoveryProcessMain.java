package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.model.OkxInstrument;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.OkxHttpClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxInstrumentsCache;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.ledger.contracts.model.LedgerPostingResult;
import com.guidinglight.nexusquant.ledger.contracts.model.TradeLedgerRequest;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.LedgerModuleTradeLedgerGateway;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.TradeLedgerGateway;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;
import com.guidinglight.nexusquant.trading.application.port.TradingCancelGatewayResult;
import com.guidinglight.nexusquant.trading.application.port.TradingGatewayResultCategory;
import com.guidinglight.nexusquant.trading.application.port.TradingOrderStatusSnapshot;
import com.guidinglight.nexusquant.trading.application.port.TradingPlaceGatewayResult;
import com.guidinglight.nexusquant.trading.application.port.TradingVenueGateway;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** Forked-JVM test helper for the F-002 restart proof. */
public final class TradingRestartRecoveryProcessMain {

    private static final BigDecimal ORDER_PRICE = new BigDecimal("100.00000000");
    private static final BigDecimal ORDER_QUANTITY = new BigDecimal("0.10000000");
    private static final Clock TEST_CLOCK = Clock.fixed(Instant.parse("2026-08-31T00:00:10Z"), ZoneOffset.UTC);

    private TradingRestartRecoveryProcessMain() {
    }

    public static void main(String[] args) {
        long processStart = System.currentTimeMillis();
        long pid = ProcessHandle.current().pid();
        RestartPhase phase = RestartPhase.valueOf(requiredEnv("NQ_F002_PHASE"));
        String caseId = requiredEnv("NQ_F002_CASE_ID");
        String dbUrl = requiredEnv("NQ_F002_DB_URL");
        String dbUser = requiredEnv("NQ_F002_DB_USER");
        String dbPassword = System.getenv().getOrDefault("NQ_F002_DB_PASSWORD", "");

        ExchangeNoOutboundGuard.install();
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                NexusQuantApplication.class,
                RestartTestConfiguration.class
        )
                .profiles("local")
                .web(WebApplicationType.NONE)
                .properties(Map.ofEntries(
                        Map.entry("spring.datasource.url", dbUrl),
                        Map.entry("spring.datasource.username", dbUser),
                        Map.entry("spring.datasource.password", dbPassword),
                        Map.entry("spring.main.allow-bean-definition-overriding", "true"),
                        Map.entry("spring.task.scheduling.enabled", "false"),
                        Map.entry("nq.runtime.trading-components.enabled", "true"),
                        Map.entry("nq.validation-operations.scheduler.enabled", "false"),
                        Map.entry("nq.okx.recovery.enabled", "false"),
                        Map.entry("nq.okx.ws.enabled", "false"),
                        Map.entry("nq.binance.ws.enabled", "false"),
                        Map.entry("nq.instrument.catalog-sync.enabled", "false"),
                        Map.entry("nq.env-safety.live-enabled", "false"),
                        Map.entry("nq.env-safety.ai-enabled", "false"),
                        Map.entry("nq.env-safety.dh-runtime-enabled", "false"),
                        Map.entry("nq.env-safety.real-provider-enabled", "false"),
                        Map.entry("nq.env-safety.real-client-enabled", "false"),
                        Map.entry("nq.env-safety.real-exchange-enabled", "false"),
                        Map.entry("nq.env-safety.no-outbound", "true")
                ))
                .run()) {
            RestartProcessResult result = switch (phase) {
                case R1_A -> runR1ProcessA(context, caseId);
                case R1_B -> runR1ProcessB(context, caseId);
                case R2_A -> runR2ProcessA(context, caseId);
                case R2_B -> runR2ProcessB(context, caseId);
            };
            long processExit = System.currentTimeMillis();
            System.out.printf(
                    "NQ_F002_RESULT phase=%s pid=%d start=%d exit=%d %s%n",
                    phase,
                    pid,
                    processStart,
                    processExit,
                    result.toMarker()
            );
        } finally {
            ExchangeNoOutboundGuard.restoreDefault();
        }
    }

    private static RestartProcessResult runR1ProcessA(ConfigurableApplicationContext context, String caseId) {
        RuntimeAccess runtime = RuntimeAccess.from(context);
        Scenario unrelated = runtime.placeOrder("f002-r1-unrelated-" + caseId);
        int unrelatedTrades = runtime.reconcile().reconcileOnce(100);
        PaperTradeRecord unrelatedTrade = runtime.tradeRepository().findByOrderId(unrelated.orderId()).orElseThrow();
        Facts unrelatedFacts = runtime.facts(unrelated, unrelatedTrade);
        require(unrelatedTrades == 1, "R1 unrelated production Trade missing");
        require(unrelatedFacts.tradeCount() == 1 && unrelatedFacts.ledgerCount() == 2,
                "R1 unrelated production facts incomplete");

        Scenario scenario = runtime.placeOrder("f002-r1-" + caseId);
        runtime.failGateway().failNext(scenario.orderId());
        boolean failed = false;
        try {
            runtime.reconcile().reconcileOnce(100);
        } catch (DeterministicRestartLedgerFailure expected) {
            failed = true;
        }
        PaperTradeRecord trade = runtime.tradeRepository().findByOrderId(scenario.orderId()).orElseThrow();
        Facts facts = runtime.facts(scenario, trade);
        runtime.engageKillSwitch(scenario.traceId());
        require(failed, "expected targeted Ledger failure");
        require(runtime.orderStatus(scenario.orderId()) == OrderStatus.FILLED, "R1 A order not FILLED");
        require(facts.tradeCount() == 1 && facts.ledgerCount() == 0, "R1 A durable boundary invalid");
        require(facts.position().signum() == 0 && facts.account().signum() == 0, "R1 A projection must be absent");
        return new RestartProcessResult(scenario.orderId(), trade.tradeId(), facts, "GRACEFUL_EXIT", false);
    }

    private static RestartProcessResult runR1ProcessB(ConfigurableApplicationContext context, String caseId) {
        RuntimeAccess runtime = RuntimeAccess.from(context);
        Scenario scenario = runtime.findScenario("f002-r1-" + caseId);
        PaperTradeRecord trade = runtime.tradeRepository().findByOrderId(scenario.orderId()).orElseThrow();
        runtime.reconcile().reconcileOnce(100);
        Facts recovered = runtime.facts(scenario, trade);
        long recoveryAudit = runtime.auditCount(scenario.traceId(), "OKX_LEDGER_RECOVERY_COMPLETED");
        runtime.reconcile().reconcileOnce(100);
        Facts repeated = runtime.facts(scenario, trade);
        long repeatedAudit = runtime.auditCount(scenario.traceId(), "OKX_LEDGER_RECOVERY_COMPLETED");
        require(recovered.tradeCount() == 1 && recovered.ledgerCount() == 2, "R1 B did not converge");
        require(runtime.orderStatus(scenario.orderId()) == OrderStatus.FILLED, "R1 B order not FILLED");
        require(recovered.position().compareTo(ORDER_QUANTITY) == 0, "R1 B position mismatch");
        require(recovered.account().compareTo(ORDER_QUANTITY) == 0, "R1 B account mismatch");
        require(recovered.equals(repeated), "R1 B repeated reconcile mutated facts");
        require(recoveryAudit == 1 && repeatedAudit == 1, "R1 B recovery audit duplicated");
        return new RestartProcessResult(scenario.orderId(), trade.tradeId(), repeated, "RECOVERED", false);
    }

    private static RestartProcessResult runR2ProcessA(ConfigurableApplicationContext context, String caseId) {
        RuntimeAccess runtime = RuntimeAccess.from(context);
        Scenario scenario = runtime.placeOrder("f002-r2-" + caseId);
        int newTrades = runtime.reconcile().reconcileOnce(100);
        List<PaperTradeRecord> trades = runtime.tradeRepository().findAllByOrderId(scenario.orderId(), 100);
        runtime.engageKillSwitch(scenario.traceId());
        require(newTrades == 1 && trades.size() == 1, "R2 A first fill missing");
        require(runtime.orderStatus(scenario.orderId()) == OrderStatus.PARTIALLY_FILLED, "R2 A order not PARTIALLY_FILLED");
        Facts facts = runtime.aggregateFacts(scenario, trades);
        require(facts.position().compareTo(new BigDecimal("0.04000000")) == 0, "R2 A partial position mismatch");
        return new RestartProcessResult(scenario.orderId(), trades.getFirst().tradeId(), facts, "PARTIAL", false);
    }

    private static RestartProcessResult runR2ProcessB(ConfigurableApplicationContext context, String caseId) {
        RuntimeAccess runtime = RuntimeAccess.from(context);
        Scenario scenario = runtime.findScenario("f002-r2-" + caseId);
        String externalOrderId = RestartVenue.externalOrderId(scenario.orderId());
        String fillAExchangeTradeId = RestartOkxHttpClient.exchangeTradeId(externalOrderId, "A");
        String fillBExchangeTradeId = RestartOkxHttpClient.exchangeTradeId(externalOrderId, "B");
        int newTrades = runtime.reconcile().reconcileOnce(100);
        List<PaperTradeRecord> trades = runtime.tradeRepository().findAllByOrderId(scenario.orderId(), 100);
        Facts recovered = runtime.aggregateFacts(scenario, trades);
        PaperTradeRecord tradeA = runtime.tradeRepository()
                .findByExchangeAndExchangeTradeId("OKX", fillAExchangeTradeId)
                .orElseThrow();
        PaperTradeRecord tradeB = runtime.tradeRepository()
                .findByExchangeAndExchangeTradeId("OKX", fillBExchangeTradeId)
                .orElseThrow();
        PerTradeLedgerFacts recoveredA = runtime.perTradeLedgerFacts(tradeA);
        PerTradeLedgerFacts recoveredB = runtime.perTradeLedgerFacts(tradeB);
        runtime.reconcile().reconcileOnce(100);
        Facts repeated = runtime.aggregateFacts(scenario, runtime.tradeRepository().findAllByOrderId(scenario.orderId(), 100));
        PerTradeLedgerFacts repeatedA = runtime.perTradeLedgerFacts(tradeA);
        PerTradeLedgerFacts repeatedB = runtime.perTradeLedgerFacts(tradeB);
        require(newTrades == 1 && trades.size() == 2, "R2 B second fill missing");
        require(runtime.orderStatus(scenario.orderId()) == OrderStatus.FILLED, "R2 B order not FILLED");
        require(recovered.position().compareTo(ORDER_QUANTITY) == 0, "R2 B position mismatch");
        require(recovered.account().compareTo(ORDER_QUANTITY) == 0, "R2 B account mismatch");
        require(recovered.equals(repeated), "R2 B repeated reconcile mutated facts");
        require(recoveredA.ledgerEntries() == 2 && recoveredA.ledgerEvents() == 2,
                "R2 B Fill A Ledger distribution mismatch");
        require(recoveredB.ledgerEntries() == 2 && recoveredB.ledgerEvents() == 2,
                "R2 B Fill B Ledger distribution mismatch");
        require(recoveredA.equals(repeatedA), "R2 B second recovery duplicated Fill A Ledger facts");
        require(recoveredB.equals(repeatedB), "R2 B second recovery duplicated Fill B Ledger facts");
        return new RestartProcessResult(scenario.orderId(), tradeB.tradeId(), repeated, "FILLED", false);
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing required environment variable: " + name);
        }
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    enum RestartPhase {
        R1_A,
        R1_B,
        R2_A,
        R2_B
    }

    record Scenario(String orderId, Long accountId, String traceId) {
    }

    record Facts(long tradeCount, long ledgerCount, BigDecimal position, BigDecimal account) {
    }

    record PerTradeLedgerFacts(long ledgerEntries, long ledgerEvents) {
    }

    record RestartProcessResult(String orderId, String tradeId, Facts facts, String state, boolean automatic) {
        String toMarker() {
            return "order=" + orderId
                    + " tradeId=" + tradeId
                    + " trades=" + facts.tradeCount()
                    + " ledger=" + facts.ledgerCount()
                    + " position=" + facts.position().toPlainString()
                    + " account=" + facts.account().toPlainString()
                    + " state=" + state
                    + " automatic=" + automatic;
        }
    }

    record RuntimeAccess(
            JdbcTemplate jdbc,
            OrderCommandService orders,
            OkxRestReconcileService reconcile,
            TradeRepository tradeRepository,
            RestartFailOnceLedgerGateway failGateway
    ) {
        static RuntimeAccess from(ConfigurableApplicationContext context) {
            return new RuntimeAccess(
                    context.getBean(JdbcTemplate.class),
                    context.getBean(OrderCommandService.class),
                    context.getBean(OkxRestReconcileService.class),
                    context.getBean(TradeRepository.class),
                    context.getBean(RestartFailOnceLedgerGateway.class)
            );
        }

        Scenario placeOrder(String accountCode) {
            String traceId = "trc-" + accountCode;
            Long accountId = jdbc.queryForObject(
                    "INSERT INTO accounts(account_code,venue,status) VALUES(?,'OKX','ACTIVE') RETURNING account_id",
                    Long.class,
                    accountCode
            );
            int killUpdated = jdbc.update(
                    "UPDATE kill_switch_states SET status='DISENGAGED',version=version+1,"
                            + "reason_code='F002_TEST',source='TEST_PROCESS',updated_at=CURRENT_TIMESTAMP-INTERVAL '1 second',"
                            + "updated_by='F002_TEST',trace_id=? WHERE scope='GLOBAL_TRADING'",
                    traceId
            );
            require(killUpdated == 1, "kill switch test setup failed");
            String clientOrderId = "coid-" + accountCode;
            var result = orders.placeOrder(new PlaceOrderRequest(
                    "req-" + accountCode,
                    accountId,
                    null,
                    "OKX",
                    "BTC-USDT",
                    clientOrderId,
                    accountId + ":" + clientOrderId,
                    "f002_restart_test",
                    OrderSide.BUY,
                    OrderType.LIMIT,
                    ORDER_PRICE,
                    ORDER_QUANTITY,
                    "GTC",
                    traceId
            ));
            require(result.status() == OrderStatus.ACCEPTED, "restart test order not accepted");
            return new Scenario(result.orderId(), accountId, traceId);
        }

        Scenario findScenario(String accountCode) {
            return jdbc.queryForObject(
                    "SELECT o.order_id,o.account_id,o.trace_id FROM orders o JOIN accounts a ON a.account_id=o.account_id "
                            + "WHERE a.account_code=? ORDER BY o.created_at DESC LIMIT 1",
                    (rs, ignored) -> new Scenario(rs.getString(1), rs.getLong(2), rs.getString(3)),
                    accountCode
            );
        }

        Facts facts(Scenario scenario, PaperTradeRecord trade) {
            return new Facts(
                    count("SELECT COUNT(*) FROM trades WHERE order_id=?", scenario.orderId()),
                    count("SELECT COUNT(*) FROM ledger_entries WHERE ref_id=?", trade.tradeId()),
                    decimal("SELECT COALESCE(MAX(qty),0) FROM positions WHERE account_id=? AND symbol='BTC-USDT'", scenario.accountId()),
                    decimal("SELECT COALESCE((SELECT balance FROM account_snapshots WHERE account_id=? AND currency='BTC' "
                            + "ORDER BY ts DESC,snapshot_id DESC LIMIT 1),0)", scenario.accountId())
            );
        }

        Facts aggregateFacts(Scenario scenario, List<PaperTradeRecord> trades) {
            long ledger = trades.stream().mapToLong(trade -> count(
                    "SELECT COUNT(*) FROM ledger_entries WHERE ref_id=?",
                    trade.tradeId()
            )).sum();
            return new Facts(
                    count("SELECT COUNT(*) FROM trades WHERE order_id=?", scenario.orderId()),
                    ledger,
                    decimal("SELECT COALESCE(MAX(qty),0) FROM positions WHERE account_id=? AND symbol='BTC-USDT'", scenario.accountId()),
                    decimal("SELECT COALESCE((SELECT balance FROM account_snapshots WHERE account_id=? AND currency='BTC' "
                            + "ORDER BY ts DESC,snapshot_id DESC LIMIT 1),0)", scenario.accountId())
            );
        }

        PerTradeLedgerFacts perTradeLedgerFacts(PaperTradeRecord trade) {
            return new PerTradeLedgerFacts(
                    count("SELECT COUNT(*) FROM ledger_entries WHERE ref_type='TRADE' AND ref_id=?", trade.tradeId()),
                    count("SELECT COUNT(*) FROM ledger_events event "
                                    + "JOIN ledger_entries entry ON entry.entry_id=event.entry_id "
                                    + "WHERE entry.ref_type='TRADE' AND entry.ref_id=?",
                            trade.tradeId())
            );
        }

        void engageKillSwitch(String traceId) {
            require(jdbc.update(
                    "UPDATE kill_switch_states SET status='ENGAGED',version=version+1,"
                            + "reason_code='F002_TEST_COMPLETE',source='TEST_PROCESS',updated_at=CURRENT_TIMESTAMP-INTERVAL '1 second',"
                            + "updated_by='F002_TEST',trace_id=? WHERE scope='GLOBAL_TRADING'",
                    traceId
            ) == 1, "kill switch cleanup failed");
        }

        long auditCount(String traceId, String action) {
            return count("SELECT COUNT(*) FROM audit_logs WHERE trace_id=? AND action=?", traceId, action);
        }

        OrderStatus orderStatus(String orderId) {
            return OrderStatus.valueOf(jdbc.queryForObject(
                    "SELECT status FROM orders WHERE order_id=?",
                    String.class,
                    orderId
            ));
        }

        long count(String sql, Object... args) {
            Long value = jdbc.queryForObject(sql, Long.class, args);
            return value == null ? 0 : value;
        }

        BigDecimal decimal(String sql, Object... args) {
            BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
            return value == null ? BigDecimal.ZERO : value;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RestartTestConfiguration {
        @Bean
        @Primary
        RestartVenue restartVenue() {
            return new RestartVenue(RestartPhase.valueOf(requiredEnv("NQ_F002_PHASE")));
        }

        @Bean(name = "okxTradingAdapter")
        @Primary
        OkxExchangeAdapter restartOkxAdapter(RestartVenue venue) {
            return venue.adapter();
        }

        @Bean
        @Primary
        RestartFailOnceLedgerGateway restartFailOnceLedgerGateway(LedgerModuleTradeLedgerGateway delegate) {
            return new RestartFailOnceLedgerGateway(delegate);
        }
    }

    static final class RestartFailOnceLedgerGateway implements TradeLedgerGateway {
        private final LedgerModuleTradeLedgerGateway delegate;
        private final AtomicReference<String> targetOrderId = new AtomicReference<>();
        private final AtomicInteger failures = new AtomicInteger();

        RestartFailOnceLedgerGateway(LedgerModuleTradeLedgerGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public LedgerPostingResult postTrade(TradeLedgerRequest request) {
            if (Objects.equals(targetOrderId.get(), request.orderId())
                    && failures.getAndUpdate(value -> value > 0 ? value - 1 : 0) > 0) {
                throw new DeterministicRestartLedgerFailure();
            }
            return delegate.postTrade(request);
        }

        void failNext(String orderId) {
            targetOrderId.set(orderId);
            failures.set(1);
        }
    }

    static final class DeterministicRestartLedgerFailure extends RuntimeException {
        DeterministicRestartLedgerFailure() {
            super("deterministic F-002 restart ledger failure");
        }
    }

    static final class RestartVenue implements TradingVenueGateway {
        private final RestartPhase phase;
        private final OkxExchangeAdapter adapter;

        RestartVenue(RestartPhase phase) {
            this.phase = phase;
            ObjectMapper mapper = new ObjectMapper();
            RestartOkxHttpClient transport = new RestartOkxHttpClient(mapper, phase);
            this.adapter = new OkxExchangeAdapter(new OkxExchangeAdapter.Dependencies(
                    mapper,
                    transport,
                    new RestartInstrumentsCache(transport),
                    TEST_CLOCK,
                    "SIM"
            ));
        }

        OkxExchangeAdapter adapter() {
            return adapter;
        }

        @Override
        public TradingPlaceGatewayResult placeOrder(OrderRecord order, PlaceOrderRequest request) {
            return new TradingPlaceGatewayResult(
                    true,
                    externalOrderId(order.orderId()),
                    OrderStatus.ACCEPTED.name(),
                    TradingGatewayResultCategory.ACCEPTED,
                    null,
                    Instant.parse("2026-08-31T00:00:00Z"),
                    "SIM"
            );
        }

        @Override
        public TradingCancelGatewayResult cancelOrder(
                OrderRecord order,
                com.guidinglight.nexusquant.trading.application.CancelOrderRequest request
        ) {
            return new TradingCancelGatewayResult(
                    false,
                    TradingGatewayResultCategory.FATAL_FAILURE,
                    null,
                    Instant.parse("2026-08-31T00:00:01Z"),
                    "SIM"
            );
        }

        @Override
        public TradingOrderStatusSnapshot getOrderStatus(OrderRecord order, String traceId) {
            return new TradingOrderStatusSnapshot(
                    order.externalOrderId(),
                    phase == RestartPhase.R2_A ? OrderStatus.PARTIALLY_FILLED.name() : OrderStatus.FILLED.name(),
                    TradingGatewayResultCategory.SUCCESS,
                    null,
                    Instant.parse("2026-08-31T00:00:02Z"),
                    "SIM"
            );
        }

        static String externalOrderId(String orderId) {
            return "f002-order-" + orderId;
        }
    }

    static final class RestartOkxHttpClient extends OkxHttpClient {
        private final ObjectMapper mapper;
        private final RestartPhase phase;

        RestartOkxHttpClient(ObjectMapper mapper, RestartPhase phase) {
            super(HttpClient.newHttpClient(), mapper, "http://127.0.0.1:1", Duration.ofSeconds(1));
            this.mapper = mapper;
            this.phase = phase;
        }

        @Override
        public JsonNode get(String path, String traceId) {
            String externalOrderId = queryParam(path, "ordId");
            if (externalOrderId == null) {
                throw new IllegalStateException("restart transport requires external order identity");
            }
            if (path.startsWith("/api/v5/trade/order?")) {
                return orderResponse(externalOrderId);
            }
            if (path.startsWith("/api/v5/trade/fills?")) {
                return fillResponse(externalOrderId);
            }
            throw new IllegalStateException("unexpected restart transport path");
        }

        private JsonNode orderResponse(String externalOrderId) {
            boolean partial = phase == RestartPhase.R2_A;
            var item = mapper.createObjectNode();
            item.put("instId", "BTC-USDT");
            item.put("clOrdId", "");
            item.put("ordId", externalOrderId);
            item.put("state", partial ? "partially_filled" : "filled");
            item.put("px", ORDER_PRICE.toPlainString());
            item.put("sz", ORDER_QUANTITY.toPlainString());
            item.put("accFillSz", partial ? "0.04000000" : ORDER_QUANTITY.toPlainString());
            item.put("avgPx", partial ? "120.00000000" : "123.00000000");
            var envelope = mapper.createObjectNode();
            envelope.put("code", "0");
            envelope.put("msg", "");
            envelope.putArray("data").add(item);
            return envelope;
        }

        private JsonNode fillResponse(String externalOrderId) {
            var envelope = mapper.createObjectNode();
            envelope.put("code", "0");
            envelope.put("msg", "");
            var data = envelope.putArray("data");
            if (phase == RestartPhase.R2_A || phase == RestartPhase.R2_B) {
                data.add(fill(externalOrderId, "A", "120.00000000", "0.04000000", 3));
                if (phase == RestartPhase.R2_B) {
                    data.add(fill(externalOrderId, "B", "125.00000000", "0.06000000", 4));
                }
            } else {
                data.add(fill(externalOrderId, "R1", "123.45000000", "0.10000000", 3));
            }
            return envelope;
        }

        private JsonNode fill(String externalOrderId, String suffix, String price, String qty, long second) {
            var fill = mapper.createObjectNode();
            fill.put("tradeId", exchangeTradeId(externalOrderId, suffix));
            fill.put("ordId", externalOrderId);
            fill.put("instId", "BTC-USDT");
            fill.put("side", "buy");
            fill.put("fillPx", price);
            fill.put("fillSz", qty);
            fill.put("fee", "0.00000000");
            fill.put("feeCcy", "USDT");
            fill.put("ts", Instant.parse("2026-08-31T00:00:00Z").plusSeconds(second).toEpochMilli());
            return fill;
        }

        static String exchangeTradeId(String externalOrderId, String suffix) {
            return "f002-fill-" + suffix + "-" + externalOrderId;
        }

        private String queryParam(String path, String name) {
            String marker = name + "=";
            int start = path.indexOf(marker);
            if (start < 0) {
                return null;
            }
            start += marker.length();
            int end = path.indexOf('&', start);
            String encoded = end < 0 ? path.substring(start) : path.substring(start, end);
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        }
    }

    static final class RestartInstrumentsCache extends OkxInstrumentsCache {
        RestartInstrumentsCache(OkxHttpClient transport) {
            super(transport, TEST_CLOCK, Duration.ofHours(1));
        }

        @Override
        public OkxInstrument getRequired(String instId, String traceId) {
            return new OkxInstrument(
                    instId,
                    new BigDecimal("0.01"),
                    new BigDecimal("0.001"),
                    new BigDecimal("0.001"),
                    "live"
            );
        }
    }
}
