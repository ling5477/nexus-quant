package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.common.tenant.TenantWorkAccess;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRepository;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** 原研究/发布/连续SIM服务的合成时间夹具；不将压缩推进描述为真实世界长期观察。 */
final class ForwardSimEvaluationFixture {
    static final ObjectMapper JSON = ResearchSimComparisonFixture.JSON;
    static final String SOURCE = "SYNTHETIC_CONTROLLED_PUBLIC_FEED";
    static final String ROOT = "/api/strategy-observations";

    private ForwardSimEvaluationFixture() {}

    static ServletWebServerApplicationContext start(String url, String user, String password) {
        PublicDemoTestHttp.isolatedProcessConfiguration();
        var application = new SpringApplication(NexusQuantApplication.class, ResearchSimComparisonFixture.FeedConfiguration.class);
        application.setRegisterShutdownHook(false);
        return (ServletWebServerApplicationContext) application.run(
                "--server.address=127.0.0.1", "--server.port=0", "--spring.profiles.active=ci,public-marketdata-manual",
                "--spring.main.banner-mode=off", "--logging.level.root=WARN", "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + user, "--spring.datasource.password=" + password,
                "--spring.datasource.hikari.maximum-pool-size=8", "--spring.flyway.enabled=false", "--spring.sql.init.mode=never",
                "--nq.env-safety.no-outbound=true", "--nq.env-safety.live-enabled=false", "--nq.strategy-sim.enabled=true",
                "--nq.continuous-sim.enabled=true", "--nq.public-marketdata.outbound.enabled=false",
                "--nq.instrument.catalog-sync.enabled=false", "--nq.okx.ws.enabled=false", "--nq.binance.ws.enabled=false",
                "--nq.okx.recovery.enabled=false", "--nq.auth.bootstrap-admin.enabled=false", "--nq.public-news.enabled=false",
                "--nq.account.performance.sampling-enabled=false",
                "--nq.account.credentials.master-key=synthetic-experiment-key-1234567890123456789",
                "--nq.security.issuer=experiment-test", "--nq.security.secret=synthetic-experiment-signing-secret-123456789");
    }

    static Scenario seed(ServletWebServerApplicationContext app, TenantIsolationTestDatabase db,
            TenantIdentity owner, String strategyType) {
        var binding = ResearchSimComparisonFixture.seed(app, db, owner, true, strategyType);
        var market = app.getBean(ResearchSimComparisonFixture.MarketServer.class);
        Instant from = market.end().plusSeconds(2 * 3600L);
        Instant to = from.plus(Duration.ofDays(1));
        var scenario = new Scenario(binding, owner, from, to, to.plusSeconds(5), market);
        advance(app, db, scenario, to);
        clock(app, scenario.asOf());
        return scenario;
    }

    static void clock(ServletWebServerApplicationContext app, Instant asOf) {
        Clock fixed = Clock.fixed(asOf, ZoneOffset.UTC);
        // 仅改变本测试新评价服务的时钟，不改变交易、撮合、账务或全局生产Clock。
        for (String className : List.of(
                "com.guidinglight.nexusquant.scheduler.paper.StrategySimForwardEvaluationQuery",
                "com.guidinglight.nexusquant.app.research.StrategyObservationService")) {
            try { ReflectionTestUtils.setField(app.getBean(Class.forName(className)), "clock", fixed); }
            catch (ClassNotFoundException failure) { throw new IllegalStateException("forward evaluation implementation missing", failure); }
        }
    }

    static void advance(ServletWebServerApplicationContext app, TenantIsolationTestDatabase db,
            Scenario scenario, Instant closedBoundary) {
        Instant serverTime = closedBoundary.plusSeconds(5);
        ClosedBarMarketFeed feed = (first, maximum) -> {
            if (maximum < 1 || maximum > 24) throw new IllegalArgumentException("fixture budget exceeded");
            List<HistoricalBar> bars = new ArrayList<>();
            for (Instant at = first; at.isBefore(closedBoundary) && bars.size() < maximum; at = at.plusSeconds(3600))
                bars.add(bar(scenario.market(), at));
            return new ClosedBarMarketFeed.Observation(serverTime, serverTime, bars,
                    new ClosedBarMarketFeed.Rule(serverTime, "LIVE", new BigDecimal("0.01"),
                            new BigDecimal("0.0001"), new BigDecimal("0.0001")),
                    new ClosedBarMarketFeed.Quote(serverTime, new BigDecimal("52200")));
        };
        var driver = new ContinuousSimRunService(app.getBean(ContinuousSimRepository.class),
                app.getBean(StrategySimRunService.class), app.getBean(PaperTradingRunService.class), feed,
                app.getBean(JdbcTemplate.class), Clock.fixed(serverTime, ZoneOffset.UTC));
        ReflectionTestUtils.setField(driver, "tenantWork", app.getBean(TenantWorkAccess.class));
        for (int attempt = 0; attempt < 8; attempt++) {
            var status = db.as(scenario.owner(), () -> driver.pollOnce(scenario.binding().paperRunId()));
            if (!"RUNNING".equals(status.status())) throw new AssertionError("synthetic formal poll failed: " + status);
            db.as(scenario.owner(), () -> app.getBean(PaperMatchingService.class).matchOnce(100));
            if (!status.lastProcessedBar().isBefore(closedBoundary.minusSeconds(3600))) {
                db.as(scenario.owner(), () -> driver.pollOnce(scenario.binding().paperRunId()));
                return;
            }
        }
        throw new AssertionError("bounded synthetic catch-up did not complete");
    }

    private static HistoricalBar bar(ResearchSimComparisonFixture.MarketServer market, Instant at) {
        if (at.isBefore(market.end())) return market.seed.stream().filter(value -> value.openTime().equals(at)).findFirst().orElseThrow();
        long hours = Duration.between(market.end(), at).toHours();
        return ResearchSimComparisonFixture.MarketServer.bar(at,
                BigDecimal.valueOf(hours < 3 ? 52000L + hours * 100L : 52200L));
    }

    static String evaluationPath(String run, Instant from, Instant to, Instant asOf) {
        return "/api/paper-trading/strategy-sim/analysis/runs/" + run
                + "/forward-evaluation?from=" + from + "&to=" + to + "&asOf=" + asOf;
    }

    static Map<String, Object> createBody(TenantIsolationTestDatabase db, Scenario scenario, String kind,
            Instant from, Instant to) {
        var binding = scenario.binding();
        var body = new LinkedHashMap<String, Object>();
        body.put("paperRunId", binding.paperRunId());
        body.put("canonicalAccountId", account(db, binding.paperRunId()));
        body.put("strategyVersionId", binding.strategyVersionId()); body.put("publishId", binding.publishId());
        body.put("validationId", binding.validationId()); body.put("selectedTrialId", binding.selectedTrialId());
        body.put("kind", kind); body.put("observationFrom", from.toString()); body.put("observationTo", to.toString());
        return body;
    }

    static long account(TenantIsolationTestDatabase db, String run) {
        return db.admin.queryForObject("SELECT canonical_account_id FROM paper_trading_runs WHERE paper_run_id=?", Long.class, run);
    }

    static Map<String, String> allFacts(TenantIsolationTestDatabase db) {
        var fingerprints = new LinkedHashMap<>(ResearchSimComparisonPostgresIntegrationTest.economicFacts(db));
        for (String table : List.of("risk_events", "risk_event_tenant_scopes", "strategy_observations", "strategy_observation_reviews", "research_creation_operations"))
            fingerprints.put(table, db.admin.queryForObject("SELECT md5(coalesce(string_agg(row_data::text,'' ORDER BY row_data::text),''))"
                    + " FROM (SELECT to_jsonb(t) row_data FROM " + table + " t) records", String.class));
        return fingerprints;
    }

    static void evidence(String name, Object value) throws Exception {
        Path directory = Path.of("..", "..", "artifacts", "nq-v103-forward");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }

    record Scenario(ResearchSimComparisonFixture.Binding binding, TenantIdentity owner, Instant from,
                    Instant to, Instant asOf, ResearchSimComparisonFixture.MarketServer market) {
        String path() { return evaluationPath(binding.paperRunId(), from, to, asOf); }
    }
}
