package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.common.tenant.TenantWorkAccess;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRepository;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
import java.time.Duration;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** 合成每小时接收证据沿用正式推进链；不把压缩测试时间称为自然时间观察。 */
final class ClosedBarForwardEvaluationFixture {
    static final String METHOD = "CONTINUOUS_SIM_CLOSED_BAR_VALUATION_V1";
    private ClosedBarForwardEvaluationFixture() {}

    static ForwardSimEvaluationFixture.Scenario prepare(ServletWebServerApplicationContext app,
            TenantIsolationTestDatabase db, TenantIdentity owner, String strategy) {
        var binding = ResearchSimComparisonFixture.seed(app, db, owner, true, strategy);
        var market = app.getBean(ResearchSimComparisonFixture.MarketServer.class);
        Instant from = market.end().plusSeconds(5 * 3600L), to = from.plusSeconds(24 * 3600L);
        var scenario = new ForwardSimEvaluationFixture.Scenario(binding, owner, from, to, to.plusSeconds(5), market);
        ForwardSimEvaluationFixture.clock(app, from.minusSeconds(3600));
        return scenario;
    }

    static Map<String, Object> request(TenantIsolationTestDatabase db, ForwardSimEvaluationFixture.Scenario scenario) {
        var body = ForwardSimEvaluationFixture.createBody(db, scenario, "FORWARD", scenario.from(), scenario.to());
        body.put("methodVersion", METHOD);
        return body;
    }

    static String path(ForwardSimEvaluationFixture.Scenario scenario) {
        return ForwardSimEvaluationFixture.evaluationPath(scenario.binding().paperRunId(), scenario.from(),
                scenario.to(), scenario.asOf()) + "&methodVersion=" + METHOD;
    }

    static void advance(ServletWebServerApplicationContext app, TenantIsolationTestDatabase db,
            ForwardSimEvaluationFixture.Scenario scenario) {
        advance(app, db, scenario, true);
    }

    static void advance(ServletWebServerApplicationContext app, TenantIsolationTestDatabase db,
            ForwardSimEvaluationFixture.Scenario scenario, boolean priceGrowth) {
        Map<Instant, HistoricalBar> original = new HashMap<>();
        db.as(scenario.owner(), () -> app.getBean(ContinuousSimRepository.class).recentBars(scenario.binding().paperRunId()))
                .forEach(value -> original.put(value.openTime(), value));
        Instant firstBoundary = scenario.market().end().plusSeconds(4 * 3600L);
        for (Instant boundary = firstBoundary; !boundary.isAfter(scenario.to()); boundary = boundary.plusSeconds(3600)) {
            Instant closed = boundary, server = boundary.plusSeconds(2), received = boundary.plusSeconds(5);
            ClosedBarMarketFeed feed = (first, maximum) -> {
                if (maximum < 1 || maximum > 24) throw new AssertionError("bounded feed required");
                List<HistoricalBar> bars = new ArrayList<>();
                // 正式feed会重读最近两根；原内容及首次接收证据必须稳定，不能按当前轮次改价。
                for (Instant at = first; at.isBefore(closed) && bars.size() < maximum; at = at.plusSeconds(3600)) {
                    HistoricalBar saved = original.get(at);
                    if (saved == null) {
                        BigDecimal price = BigDecimal.valueOf(priceGrowth
                                ? 52500L + Duration.between(scenario.from(), at.plusSeconds(3600)).toHours() * 100L : 52200L);
                        saved = bar(at, price, server, received); original.put(at, saved);
                    }
                    bars.add(saved);
                }
                return new ClosedBarMarketFeed.Observation(server, received, bars,
                        new ClosedBarMarketFeed.Rule(server, "LIVE", new BigDecimal("0.01"),
                                new BigDecimal("0.0001"), new BigDecimal("0.0001")),
                        new ClosedBarMarketFeed.Quote(server.plusSeconds(1), new BigDecimal("52300")));
            };
            var driver = new ContinuousSimRunService(app.getBean(ContinuousSimRepository.class),
                    app.getBean(StrategySimRunService.class), app.getBean(PaperTradingRunService.class), feed,
                    app.getBean(JdbcTemplate.class), Clock.fixed(received, ZoneOffset.UTC));
            ReflectionTestUtils.setField(driver, "tenantWork", app.getBean(TenantWorkAccess.class));
            for (int repeat = 0; repeat < 2; repeat++) {
                var state = db.as(scenario.owner(), () -> driver.pollOnce(scenario.binding().paperRunId()));
                if (!"RUNNING".equals(state.status())) throw new AssertionError("formal synthetic poll failed " + state);
                db.as(scenario.owner(), () -> app.getBean(PaperMatchingService.class).matchOnce(100));
            }
        }
        ForwardSimEvaluationFixture.clock(app, scenario.asOf());
    }

    static HistoricalBar bar(Instant open, BigDecimal price, Instant server, Instant received) {
        var raw = ForwardSimEvaluationFixture.JSON.createObjectNode()
                .put("collectionMethod", "RESPONSE_COMPLETED_UTC_V1")
                .put("receivedAt", received.toString()).put("serverTime", server.toString());
        raw.putArray("candle").add(Long.toString(open.toEpochMilli())).add(price.toPlainString())
                .add(price.add(BigDecimal.ONE).toPlainString()).add(price.subtract(BigDecimal.ONE).toPlainString())
                .add(price.toPlainString()).add("1").add("1").add(price.toPlainString()).add("1");
        return new HistoricalBar("OKX", "SPOT", "BTC-USDT",
                BarInterval.ONE_HOUR, open,
                open.plusSeconds(3600).minusMillis(1), price, price.add(BigDecimal.ONE), price.subtract(BigDecimal.ONE),
                price, BigDecimal.ONE, price, null, "OK", raw.toString(), server);
    }
}
