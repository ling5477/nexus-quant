package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.adapter.okx.marketdata.OkxHistoricalKlineAdapter;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.app.marketdata.PublicMarketReplayCaptureService;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataDatasetCommand;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.HistoricalKlineProvider;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import com.guidinglight.nexusquant.research.application.ResearchConfigService;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.command.ResearchConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.config.BacktestConfigService;
import com.guidinglight.nexusquant.strategy.application.StrategyDefinitionService;
import com.guidinglight.nexusquant.strategy.application.StrategyVersionService;
import com.guidinglight.nexusquant.strategy.application.command.StrategyDefinitionCreateRequest;
import com.guidinglight.nexusquant.strategy.application.command.StrategyVersionCreateRequest;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 长历史浏览器夹具显式区分合成响应与真实公开归档回放；业务命令仍由真实页面和正式服务完成。 */
public final class LongHistoricalResearchBrowserFixtureMain {
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    static final Instant START = Instant.parse("2026-04-01T00:00:00Z");
    static final String COST = StrategyExperimentPostgresIntegrationTest.COST;
    static final String USER = "experiment-owner";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("ready and stop paths required");
        Path ready = Path.of(args[0]).toAbsolutePath(), stop = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(ready) || Files.exists(stop) || !ready.getParent().equals(stop.getParent()))
            throw new IllegalArgumentException("fresh fixture identity required");
        try (var db = TenantIsolationTestDatabase.create(null, System.getenv("TASK_EXPERIMENT_PG_URL"),
                "postgres", System.getenv("TASK_EXPERIMENT_PG_PASSWORD"))) {
            var owner = db.local(USER);
            try (var app = start(db)) {
                db.admin.update("UPDATE scheduled_job_controls SET enabled=false,next_run_at=NULL");
                var strategies = db.as(owner, () -> seedStrategies(app, "browser"));
                db.admin.update("UPDATE scheduled_job_controls SET enabled=true,next_run_at=clock_timestamp() WHERE job_key='STRATEGY_EXPERIMENTS'");
                var identity = new LinkedHashMap<String, Object>();
                identity.put("port", app.getWebServer().getPort());
                identity.put("pid", ProcessHandle.current().pid());
                identity.put("username", USER);
                identity.put("password", TenantIsolationTestDatabase.PASSWORD);
                var market = app.getBean(MarketServer.class);
                identity.put("dataSource", market.dataSource());
                if (market.capturePath != null) {
                    identity.put("capturePath", market.capturePath.toString());
                    identity.put("captureSha256", market.captureSha256);
                    identity.put("publicDataConsumption", "ARCHIVED_PUBLIC_CAPTURE_REPLAY_NOT_BROWSER_TIME_EXTERNAL_FETCH");
                }
                identity.put("start", START.toString());
                identity.put("endExclusive", START.plusSeconds(4320L * 3600).toString());
                identity.put("bars", 4320);
                identity.put("strategies", strategies);
                Files.writeString(ready, JSON.writeValueAsString(identity), StandardOpenOption.CREATE_NEW);
                long deadline = System.nanoTime() + Duration.ofMinutes(45).toNanos();
                while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(200);
            }
        }
    }

    static ServletWebServerApplicationContext start(TenantIsolationTestDatabase db) {
        PublicDemoTestHttp.isolatedProcessConfiguration();
        var application = new SpringApplication(NexusQuantApplication.class, SyntheticPublicHistory.class);
        application.setRegisterShutdownHook(false);
        return (ServletWebServerApplicationContext) application.run(
                "--server.address=127.0.0.1", "--server.port=0", "--spring.profiles.active=ci",
                "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                "--spring.datasource.url=" + db.url(), "--spring.datasource.username=" + db.runtime,
                "--spring.datasource.password=" + db.runtimePassword, "--spring.datasource.hikari.maximum-pool-size=8",
                "--spring.flyway.enabled=false", "--spring.sql.init.mode=never",
                "--nq.env-safety.no-outbound=true", "--nq.env-safety.live-enabled=false",
                "--nq.strategy-sim.enabled=false", "--nq.continuous-sim.enabled=false",
                "--nq.instrument.catalog-sync.enabled=false", "--nq.okx.ws.enabled=false", "--nq.binance.ws.enabled=false",
                "--nq.okx.recovery.enabled=false", "--nq.auth.bootstrap-admin.enabled=false",
                "--nq.public-news.enabled=false", "--nq.account.performance.sampling-enabled=false",
                "--nq.account.credentials.master-key=synthetic-history-key-1234567890123456789",
                "--nq.security.issuer=long-history-test", "--nq.security.secret=synthetic-history-signing-secret-123456789");
    }

    static Map<String, StrategySeed> seedStrategies(ConfigurableApplicationContext app, String suffix) {
        var account = app.getBean(com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService.class)
                .create(TenantContext.requireCurrent().userId(),
                        new com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand(
                                "OKX", "SIM", "Synthetic long research " + suffix, null));
        var result = new LinkedHashMap<String, StrategySeed>();
        for (String type : List.of("SPOT_SMA_TARGET_V1", "SPOT_CHANNEL_BREAKOUT_V1")) {
            var strategy = app.getBean(StrategyDefinitionService.class).create(new StrategyDefinitionCreateRequest(
                    "long-history-" + suffix + "-" + (type.contains("SMA") ? "sma" : "channel"),
                    "Synthetic long history " + type, type, "OKX", account.legacyAccountId(), "SIM", "{}"));
            var version = app.getBean(StrategyVersionService.class).create(new StrategyVersionCreateRequest(
                    strategy.strategyCode(), "Synthetic research seed", "DRAFT",
                    type.contains("SMA") ? "{\"window\":20,\"investedExposure\":\"0.5\"}"
                            : "{\"entryLookbackBars\":20,\"exitLookbackBars\":10,\"investedExposure\":\"0.5\"}",
                    "{}", "{\"executable\":\"" + type + "\"}", USER));
            result.put(type, new StrategySeed(strategy.strategyId(), strategy.strategyCode(), version.strategyVersionId()));
        }
        return result;
    }

    static Template seedTemplate(ConfigurableApplicationContext app, String symbol, int count, String type, String suffix) {
        return seedTemplate(app, symbol, count, type, suffix, -1);
    }

    static Template seedTemplate(ConfigurableApplicationContext app, String symbol, int count, String type,
            String suffix, int perturbFrom) {
        List<HistoricalBar> source = bars(symbol, count);
        if (perturbFrom >= 0) {
            var changed = new ArrayList<HistoricalBar>(source);
            for (int i = perturbFrom; i < source.size(); i++) {
                HistoricalBar bar = source.get(i); BigDecimal delta = new BigDecimal("37");
                changed.set(i, new HistoricalBar(bar.exchangeCode(), bar.marketType(), bar.symbol(), bar.interval(),
                        bar.openTime(), bar.closeTime(), bar.openPrice().add(delta), bar.highPrice().add(delta),
                        bar.lowPrice().add(delta), bar.closePrice().add(delta), bar.volume().multiply(BigDecimal.TEN),
                        bar.quoteVolume(), bar.tradeCount(), bar.qualityStatus(), bar.rawPayloadJson(), bar.availableAt()));
            }
            source = changed;
        }
        app.getBean(MarketdataBarRepository.class).upsertBars(source, "SYNTHETIC_LONG_HISTORY", Instant.now());
        var datasets = app.getBean(MarketdataDatasetService.class);
        var dataset = datasets.createDataset(new CreateMarketdataDatasetCommand("Synthetic " + suffix,
                "OKX", "SPOT", symbol, "1h", START, START.plusSeconds(count * 3600L).minusMillis(1), USER));
        String snapshot = datasets.buildDatasetSnapshot(dataset.datasetId());
        var strategy = seedStrategies(app, suffix).get(type);
        var research = app.getBean(ResearchConfigService.class).create(new ResearchConfigCreateRequest(
                strategy.strategyId(), "Synthetic long history " + suffix, null, "{}", "{}", snapshot));
        var configs = app.getBean(BacktestConfigService.class);
        var config = configs.create(new BacktestConfigCreateRequest(research.researchConfigId(), "Long history " + suffix,
                null, START, START.plusSeconds(count * 3600L).minusMillis(1), new BigDecimal("10000"), COST, "{}"));
        configs.bindStrategyVersion(config.backtestConfigId(), strategy.versionId());
        configs.bindDataset(config.backtestConfigId(), dataset.datasetId().toString(), snapshot);
        return new Template(dataset.datasetId(), snapshot, config.backtestConfigId(), strategy.versionId(), count);
    }

    static List<HistoricalBar> bars(String symbol, int count) {
        var result = new ArrayList<HistoricalBar>(count);
        for (int i = 0; i < count; i++) {
            Instant at = START.plusSeconds(i * 3600L);
            BigDecimal price = price(i);
            result.add(new HistoricalBar("OKX", "SPOT", symbol, BarInterval.ONE_HOUR, at,
                    at.plusSeconds(3600).minusMillis(1), price, price.add(BigDecimal.ONE),
                    price.subtract(BigDecimal.ONE), price, BigDecimal.valueOf(10 + i % 17),
                    null, null, "OK", "{}", at.plusSeconds(3600)));
        }
        return result;
    }

    private static BigDecimal price(long index) {
        long cycle = Math.floorMod(index, 240);
        return new BigDecimal("100").add(BigDecimal.valueOf(cycle < 160 ? cycle : 320 - cycle)
                .movePointLeft(1));
    }

    record StrategySeed(String strategyId, String strategyCode, String versionId) {}
    record Template(UUID datasetId, String snapshot, String configId, String versionId, int barCount) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class SyntheticPublicHistory {
        @Bean(destroyMethod = "close") MarketServer longHistoryMarketServer() throws Exception { return new MarketServer(); }

        /** 只在夹具内将本机合成来源接到真实Adapter，生产外联guard与配置保持原样。 */
        @Bean @Primary HistoricalKlineProvider syntheticLongHistoryProvider(MarketServer server) {
            var adapter = new OkxHistoricalKlineAdapter(server.base(), Duration.ofSeconds(8));
            return (job, from, to) -> adapter.fetchHistoricalKlines(
                    new com.guidinglight.nexusquant.adapter.api.model.HistoricalKlineRequest(job.exchangeCode(),
                            job.marketType(), job.symbol(), job.interval().wireValue(), from, to, 300))
                    .stream().map(bar -> new HistoricalBar(bar.exchangeCode(), bar.marketType(), bar.symbol(),
                            BarInterval.fromWireValue(bar.interval()), bar.openTime(), bar.closeTime(),
                            bar.openPrice(), bar.highPrice(), bar.lowPrice(), bar.closePrice(), bar.volume(),
                            bar.quoteVolume(), bar.tradeCount(), "OK", bar.rawPayloadJson())).toList();
        }

        @Bean PublicMarketReplayCaptureService syntheticLongHistoryRules(MarketServer server, ObjectMapper mapper,
                JdbcTemplate jdbc, TransactionTemplate transaction) {
            return new PublicMarketReplayCaptureService(HttpClient.newHttpClient(), URI.create(server.base()),
                    mapper, jdbc, transaction, Clock.systemUTC());
        }
    }

    static final class MarketServer implements AutoCloseable {
        final HttpServer server;
        final AtomicInteger historyRequests = new AtomicInteger();
        final AtomicInteger failingRequests = new AtomicInteger();
        final Path capturePath;
        final String captureSha256;
        final JsonNode capture;

        MarketServer() throws Exception {
            String requested = System.getenv("TASK_LONG_HISTORY_PUBLIC_CAPTURE");
            if (requested == null || requested.isBlank()) {
                capturePath = null; captureSha256 = null; capture = null;
            } else {
                capturePath = Path.of(requested).toAbsolutePath();
                if (!Files.isRegularFile(capturePath) || Files.size(capturePath) > 16 * 1024 * 1024)
                    throw new IllegalArgumentException("bounded public history capture required");
                byte[] archived = Files.readAllBytes(capturePath);
                captureSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archived));
                capture = JSON.readTree(archived);
                if (!"REAL_OKX_PUBLIC_CAPTURE_REPLAY".equals(capture.path("dataSource").asText())
                        || !START.equals(Instant.parse(capture.path("start").asText())))
                    throw new IllegalArgumentException("public capture source or start identity mismatch");
                for (String symbol : List.of("BTC-USDT", "ETH-USDT")) {
                    JsonNode rows = capture.path("bars").path(symbol), rules = capture.path("instruments").path(symbol);
                    if (!rows.isArray() || rows.isEmpty() || rows.size() > 4320
                            || !"0".equals(rules.path("code").asText()) || !rules.path("data").isArray())
                        throw new IllegalArgumentException("public capture symbol coverage or rules invalid");
                    var unique = new java.util.HashSet<Long>();
                    for (JsonNode row : rows) {
                        if (!row.isArray() || row.size() < 9 || !"1".equals(row.path(8).asText())
                                || !unique.add(row.path(0).asLong()))
                            throw new IllegalArgumentException("public capture contains unclosed or duplicate rows");
                    }
                }
            }
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v5/public/instruments", exchange -> {
                var query = parameters(exchange.getRequestURI().getRawQuery());
                String symbol = query.getOrDefault("instId", "BTC-USDT");
                if (capture != null) {
                    JsonNode original = capture.path("instruments").path(symbol);
                    if (original.isMissingNode()) { exchange.sendResponseHeaders(404, -1); exchange.close(); return; }
                    byte[] raw = JSON.writeValueAsBytes(original);
                    exchange.sendResponseHeaders(200, raw.length);
                    try (var output = exchange.getResponseBody()) { output.write(raw); }
                    return;
                }
                ObjectNode payload;
                try (var input = getClass().getResourceAsStream("/public-market-replay/okx-btc-usdt-public-instrument-20260925.json")) {
                    payload = (ObjectNode) JSON.readTree(input);
                }
                var instrument = (ObjectNode) payload.path("data").get(0);
                instrument.put("instId", symbol);
                instrument.put("baseCcy", symbol.split("-")[0]);
                if (symbol.equals("ETH-USDT")) {
                    instrument.put("lotSz", "0.000001"); instrument.put("tickSz", "0.01"); instrument.put("minSz", "0.0001");
                }
                byte[] raw = JSON.writeValueAsBytes(payload);
                exchange.sendResponseHeaders(200, raw.length);
                try (var output = exchange.getResponseBody()) { output.write(raw); }
            });
            server.createContext("/api/v5/market/history-candles", exchange -> {
                historyRequests.incrementAndGet();
                if (failingRequests.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                    byte[] raw = "{\"code\":\"50011\",\"data\":[]}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(503, raw.length);
                    try (var output = exchange.getResponseBody()) { output.write(raw); }
                    return;
                }
                var query = parameters(exchange.getRequestURI().getRawQuery());
                if (!Set.of("BTC-USDT", "ETH-USDT").contains(query.get("instId")) || !"1H".equals(query.get("bar"))) {
                    exchange.sendResponseHeaders(400, -1); exchange.close(); return;
                }
                long after = Long.parseLong(query.get("after")), before = Long.parseLong(query.get("before"));
                int limit = Math.min(300, Integer.parseInt(query.getOrDefault("limit", "100")));
                var payload = JSON.createObjectNode(); payload.put("code", "0");
                var data = payload.putArray("data");
                if (capture != null) {
                    var selected = new ArrayList<JsonNode>();
                    for (JsonNode row : capture.path("bars").path(query.get("instId"))) {
                        long open = row.path(0).asLong();
                        if (open > before && open <= after) selected.add(row);
                    }
                    selected.sort(java.util.Comparator.comparingLong((JsonNode row) -> row.path(0).asLong()).reversed());
                    selected.stream().limit(limit).forEach(data::add);
                    byte[] raw = JSON.writeValueAsBytes(payload);
                    exchange.sendResponseHeaders(200, raw.length);
                    try (var output = exchange.getResponseBody()) { output.write(raw); }
                    return;
                }
                for (int i = 4319; i >= 0 && data.size() < limit; i--) {
                    Instant at = START.plusSeconds(i * 3600L);
                    if (at.toEpochMilli() > before && at.toEpochMilli() <= after) {
                        BigDecimal price = price(i);
                        var row = data.addArray(); row.add(Long.toString(at.toEpochMilli()));
                        row.add(price.toPlainString()); row.add(price.add(BigDecimal.ONE).toPlainString());
                        row.add(price.subtract(BigDecimal.ONE).toPlainString()); row.add(price.toPlainString());
                        row.add(Integer.toString(10 + i % 17)); row.add("0"); row.add("0"); row.add("1");
                    }
                }
                byte[] raw = JSON.writeValueAsBytes(payload);
                exchange.sendResponseHeaders(200, raw.length);
                try (var output = exchange.getResponseBody()) { output.write(raw); }
            });
            server.start();
        }

        String base() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        String dataSource() { return capture == null ? "SYNTHETIC_LONG_HISTORY" : "REAL_OKX_PUBLIC_CAPTURE_REPLAY"; }
        public void close() { server.stop(0); }

        private static Map<String, String> parameters(String raw) {
            var result = new HashMap<String, String>();
            if (raw != null) for (String entry : raw.split("&")) {
                String[] pair = entry.split("=", 2);
                result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8));
            }
            return result;
        }
    }
}
