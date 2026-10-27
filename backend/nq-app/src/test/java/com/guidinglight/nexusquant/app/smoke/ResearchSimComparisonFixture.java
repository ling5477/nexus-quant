package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.app.marketdata.PublicMarketReplayCaptureService;
import com.guidinglight.nexusquant.app.research.ExperimentValidationService;
import com.guidinglight.nexusquant.app.research.StrategyExperimentService;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.common.tenant.TenantWorkAccess;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.research.application.BacktestPublishService;
import com.guidinglight.nexusquant.research.application.BacktestRunService;
import com.guidinglight.nexusquant.research.application.backtest.BacktestExecutionService;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestRunStartRequest;
import com.guidinglight.nexusquant.research.application.command.BacktestPublishRequest;
import com.guidinglight.nexusquant.research.application.config.BacktestConfigService;
import com.guidinglight.nexusquant.research.application.eval.BacktestEvaluationService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRepository;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/** 研究、发布和连续SIM分别经过原服务；来源为本地受控HTTP，不访问真实交易所。 */
final class ResearchSimComparisonFixture {
    static final ObjectMapper JSON = WalkForwardPostgresIntegrationTest.JSON;

    static ServletWebServerApplicationContext start(TenantIsolationTestDatabase db) {
        PublicDemoTestHttp.isolatedProcessConfiguration();
        var application = new SpringApplication(NexusQuantApplication.class, FeedConfiguration.class);
        application.setRegisterShutdownHook(false);
        return (ServletWebServerApplicationContext) application.run(
                "--server.address=127.0.0.1", "--server.port=0", "--spring.profiles.active=ci,public-marketdata-manual",
                "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                "--spring.datasource.url=" + db.url(), "--spring.datasource.username=" + db.runtime,
                "--spring.datasource.password=" + db.runtimePassword, "--spring.datasource.hikari.maximum-pool-size=8",
                "--spring.flyway.enabled=false", "--spring.sql.init.mode=never",
                "--nq.env-safety.no-outbound=true", "--nq.env-safety.live-enabled=false",
                "--nq.strategy-sim.enabled=true", "--nq.continuous-sim.enabled=true",
                "--nq.public-marketdata.outbound.enabled=false", "--nq.instrument.catalog-sync.enabled=false",
                "--nq.okx.ws.enabled=false", "--nq.binance.ws.enabled=false", "--nq.okx.recovery.enabled=false",
                "--nq.auth.bootstrap-admin.enabled=false", "--nq.public-news.enabled=false",
                "--nq.account.performance.sampling-enabled=false",
                "--nq.account.credentials.master-key=synthetic-experiment-key-1234567890123456789",
                "--nq.security.issuer=experiment-test", "--nq.security.secret=synthetic-experiment-signing-secret-123456789");
    }

    static Binding seed(ServletWebServerApplicationContext app, TenantIsolationTestDatabase db, TenantIdentity owner) {
        return seed(app, db, owner, true);
    }

    static Binding seed(ServletWebServerApplicationContext app, TenantIsolationTestDatabase db, TenantIdentity owner, boolean revealHoldout) {
        return seed(app, db, owner, revealHoldout, "SPOT_SMA_TARGET_V1");
    }

    static Binding seed(ServletWebServerApplicationContext app, TenantIsolationTestDatabase db, TenantIdentity owner,
            boolean revealHoldout, String strategyType) {
        if (!List.of("SPOT_SMA_TARGET_V1", "SPOT_CHANNEL_BREAKOUT_V1").contains(strategyType))
            throw new IllegalArgumentException("bounded comparison strategy fixture required");
        db.admin.update("UPDATE scheduled_job_controls SET enabled=false,next_run_at=NULL");
        var experiments = app.getBean(StrategyExperimentService.class);
        var validations = app.getBean(ExperimentValidationService.class);
        var templates = db.as(owner, () -> WalkForwardPostgresIntegrationTest.seed(app));
        var parameters = JSON.createObjectNode().put("investedExposure", "1");
        if ("SPOT_CHANNEL_BREAKOUT_V1".equals(strategyType)) parameters.put("entryLookbackBars", 4).put("exitLookbackBars", 2);
        else parameters.put("window", 4);
        var request = new StrategyExperimentService.Request(templates.get(strategyType), JSON.createArrayNode().add(parameters), null);
        var source = db.as(owner, () -> experiments.create(UUID.randomUUID(), request, owner.userId()));
        WalkForwardPostgresIntegrationTest.advance(app, source.experimentId());
        var validation = db.as(owner, () -> validations.create(source.experimentId(), UUID.randomUUID(), owner.userId()));
        WalkForwardPostgresIntegrationTest.drain(app, db, owner, validation.validationId(), source.experimentId());
        var completed = db.as(owner, () -> validations.get(validation.validationId(), owner.userId()));
        if (revealHoldout) db.as(owner, () -> validations.revealHoldout(validation.validationId(), UUID.randomUUID(), owner.userId()));
        JsonNode selected = completed.steps().stream().filter(s -> s.number() == 12).findFirst().orElseThrow().selection().path("selected");
        UUID trial = UUID.fromString(selected.path("trialId").asText());
        String version = selected.path("versionId").asText();
        // 产品当前没有草稿激活命令；夹具只设定显式发布前提，不改已冻结Run或评价经济事实。
        db.admin.update("UPDATE strategy_versions SET status='ACTIVE' WHERE strategy_version_id=?", version);
        String publication = db.as(owner, () -> publish(app, source, version));
        var server = app.getBean(MarketServer.class);
        var driver = new ContinuousSimRunService(app.getBean(ContinuousSimRepository.class),
                app.getBean(StrategySimRunService.class), app.getBean(PaperTradingRunService.class),
                server::observation, app.getBean(JdbcTemplate.class), Clock.fixed(server.forwardTime(), ZoneOffset.UTC));
        ReflectionTestUtils.setField(driver, "tenantWork", app.getBean(TenantWorkAccess.class));
        String run = db.as(owner, () -> app.getBean(ContinuousSimRunService.class)
                .start(publication, new BigDecimal("10000"), "experiment-owner").paperRunId());
        db.as(owner, () -> driver.pollOnce(run));
        db.as(owner, () -> app.getBean(PaperMatchingService.class).matchOnce(100));
        db.as(owner, () -> driver.pollOnce(run));
        return new Binding(source.experimentId(), validation.validationId(), trial, version, publication, run, driver);
    }

    static String publish(ServletWebServerApplicationContext app, StrategyExperimentService.View source, String version) {
        var capture = app.getBean(PublicMarketReplayCaptureService.class);
        var server = app.getBean(MarketServer.class);
        var dataset = capture.capture(server.start(), server.end(), "experiment-owner");
        String snapshot = app.getBean(MarketdataDatasetService.class).buildDatasetSnapshot(dataset.datasetId());
        JsonNode captureIdentity;
        try { captureIdentity = JSON.readTree(snapshot).path("capture"); }
        catch (IOException failure) { throw new IllegalStateException(failure); }
        JsonNode rule = captureIdentity.path("rule");
        var costs = JSON.createObjectNode().put("quantityStep", rule.path("lotSize").asText())
                .put("priceTick", rule.path("tickSize").asText()).put("minimumQuantity", rule.path("minimumSize").asText())
                .put("minimumNotional", "1").put("feeRate", "0.001").put("slippageBps", "10")
                .put("ruleSha256", captureIdentity.path("ruleSha256").asText()).put("rulePolicy", "CURRENTLY_OBSERVED_PUBLIC_RULES")
                .put("costSource", "EXPERIMENT_ASSUMPTION").put("feeAssumptionVersion", "COMPARISON_FIXTURE_V1")
                .put("slippageAssumptionVersion", "COMPARISON_FIXTURE_V1").put("minimumNotionalSource", "EXPERIMENT_ASSUMPTION");
        var configs = app.getBean(BacktestConfigService.class);
        var config = configs.create(new BacktestConfigCreateRequest(source.frozenInput().path("researchConfigId").asText(),
                "Selected version full-window public replay", null, server.start(), server.end().minusMillis(1),
                new BigDecimal("10000"), costs.toString(), "{}"));
        configs.bindStrategyVersion(config.backtestConfigId(), version);
        configs.bindDataset(config.backtestConfigId(), dataset.datasetId().toString(), snapshot);
        var run = app.getBean(BacktestRunService.class).create(new BacktestRunStartRequest(config.backtestConfigId()));
        app.getBean(BacktestExecutionService.class).startRun(run.backtestRunId());
        app.getBean(BacktestEvaluationService.class).evaluate(run.backtestRunId());
        return app.getBean(BacktestPublishService.class).publish(new BacktestPublishRequest(run.backtestRunId(),
                "Selected version public replay", version)).publishRecordId();
    }

    record Binding(UUID sourceExperimentId, UUID validationId, UUID selectedTrialId, String strategyVersionId,
            String publishId, String paperRunId, ContinuousSimRunService driver) {
        String path() { return "/api/strategy-experiments/validations/" + validationId + "/sim-comparison?selectedTrialId="
                + selectedTrialId + "&strategyVersionId=" + strategyVersionId; }
        String fullPath() { return path() + "&publishId=" + publishId + "&paperRunId=" + paperRunId; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FeedConfiguration {
        @Bean(destroyMethod = "close") MarketServer comparisonMarketServer() throws Exception { return new MarketServer(); }
        @Bean PublicMarketReplayCaptureService comparisonCapture(MarketServer server, ObjectMapper mapper,
                JdbcTemplate jdbc, TransactionTemplate transaction) {
            return new PublicMarketReplayCaptureService(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                    URI.create("http://127.0.0.1:" + server.server.getAddress().getPort()), mapper, jdbc, transaction, Clock.systemUTC());
        }
        @Bean ClosedBarMarketFeed comparisonFeed() { return (first, limit) -> { throw new IllegalStateException("EXPLICIT_FIXTURE_POLL_ONLY"); }; }
    }

    static final class MarketServer implements AutoCloseable {
        final HttpServer server;
        final Instant end = Instant.now().truncatedTo(ChronoUnit.HOURS).minusSeconds(3600);
        final List<HistoricalBar> seed;
        MarketServer() throws Exception {
            var bars = new ArrayList<HistoricalBar>();
            for (int i = 0; i < 100; i++) bars.add(bar(start().plusSeconds(i * 3600L), BigDecimal.valueOf(50000L + i * 10L)));
            seed = List.copyOf(bars);
            var payload = JSON.createObjectNode().put("code", "0");
            var data = payload.putArray("data");
            for (var b : seed.reversed()) data.addArray().add(Long.toString(b.openTime().toEpochMilli())).add(b.openPrice().toPlainString())
                    .add(b.highPrice().toPlainString()).add(b.lowPrice().toPlainString()).add(b.closePrice().toPlainString())
                    .add("1").add("1").add(b.closePrice().toPlainString()).add("1");
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] candles = JSON.writeValueAsBytes(payload);
            byte[] rules;
            try (var input = getClass().getResourceAsStream("/public-market-replay/okx-btc-usdt-public-instrument-20260925.json")) { rules = input.readAllBytes(); }
            server.createContext("/api/v5/market/history-candles", exchange -> { exchange.sendResponseHeaders(200, candles.length); try(var output=exchange.getResponseBody()){output.write(candles);} });
            server.createContext("/api/v5/public/instruments", exchange -> { exchange.sendResponseHeaders(200, rules.length); try(var output=exchange.getResponseBody()){output.write(rules);} });
            server.start();
        }
        Instant start() { return end.minusSeconds(100 * 3600L); }
        Instant end() { return end; }
        Instant forwardTime() { return end.plusSeconds(3 * 3600 + 5); }
        ClosedBarMarketFeed.Observation observation(Instant first, int limit) {
            var bars = new ArrayList<>(seed.subList(98, 100));
            for (int i=0;i<3;i++) bars.add(bar(end.plusSeconds(i*3600L), BigDecimal.valueOf(52000+i*100L)));
            return new ClosedBarMarketFeed.Observation(forwardTime(), forwardTime(),
                    bars.stream().filter(b -> !b.openTime().isBefore(first)).limit(limit).toList(),
                    new ClosedBarMarketFeed.Rule(forwardTime(), "LIVE", new BigDecimal("0.01"), new BigDecimal("0.0001"), new BigDecimal("0.0001")),
                    new ClosedBarMarketFeed.Quote(forwardTime(), new BigDecimal("52300")));
        }
        static HistoricalBar bar(Instant at, BigDecimal price) { return new HistoricalBar("OKX", "SPOT", "BTC-USDT", BarInterval.ONE_HOUR,
                at, at.plusSeconds(3600).minusMillis(1), price, price.add(BigDecimal.ONE), price.subtract(BigDecimal.ONE), price,
                BigDecimal.ONE, price, null, "OK", "{}", at.plusSeconds(3600)); }
        public void close() { server.stop(0); }
    }
}
