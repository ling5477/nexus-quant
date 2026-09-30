package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRepository;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** 独立 JVM 使用同一隔离 schema，验证决策关联与连续 SIM 游标后的执行收敛。 */
public final class StrategySimRestartProbeMain {
    private StrategySimRestartProbeMain() { }

    public static void main(String[] args) {
        if (args.length != 4 || !args[1].startsWith("jdbc:postgresql://127.0.0.1:")) {
            throw new IllegalArgumentException("disposable loopback PostgreSQL arguments required");
        }
        String mode = args[0];
        String url = args[1] + "?currentSchema=" + args[2];
        String paperRunId = args[3];
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(NexusQuantApplication.class)
                .profiles("ci-app-smoke")
                .web(WebApplicationType.NONE)
                .initializers(new NqAppContextPostgresSmokeTest.NoOutboundInitializer())
                .properties(Map.ofEntries(
                        Map.entry("spring.datasource.url", url),
                        Map.entry("spring.datasource.username", "postgres"),
                        Map.entry("spring.datasource.password", "disposable"),
                        Map.entry("spring.flyway.enabled", "false"),
                        Map.entry("spring.sql.init.mode", "never"),
                        Map.entry("spring.task.scheduling.enabled", "false"),
                        Map.entry("nq.runtime.trading-components.enabled", "true"),
                        Map.entry("nq.strategy-sim.enabled", "true"),
                        Map.entry("nq.auth.bootstrap-admin.enabled", "false"),
                        Map.entry("nq.instrument.catalog-sync.enabled", "false"),
                        Map.entry("nq.okx.recovery.enabled", "false"),
                        Map.entry("nq.okx.ws.enabled", "false"),
                        Map.entry("nq.binance.ws.enabled", "false"),
                        Map.entry("nq.account.credentials.verification-mode", "STRUCTURAL"),
                        Map.entry("nq.account.credentials.master-key", "strategy-sim-synthetic-master-key-123456789"),
                        Map.entry("nq.security.issuer", "nexus-quant-strategy-sim-synthetic"),
                        Map.entry("nq.security.secret", "strategy-sim-synthetic-secret-123456789"),
                        Map.entry("nq.security.access-token-ttl", "PT30M")))
                .run()) {
            StrategySimRunService sim = context.getBean(StrategySimRunService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            if ("A".equals(mode)) {
                var accepted = sim.advance(paperRunId);
                if (!"ACCEPTED".equals(accepted.status()) || accepted.orderId() == null) {
                    throw new AssertionError("first process did not commit canonical order");
                }
                // 模拟订单已提交、决策关联尚未提交时的退出；只修改一次性测试 schema。
                jdbc.execute("ALTER TABLE strategy_sim_decisions DISABLE TRIGGER trg_strategy_sim_preserve_decision");
                try {
                    jdbc.update("""
                            UPDATE strategy_sim_decisions SET status='NOT_TRADABLE',reason='DECIDING',
                                strategy_run_id=NULL,order_id=NULL WHERE decision_id=?
                            """, accepted.decisionId());
                } finally {
                    jdbc.execute("ALTER TABLE strategy_sim_decisions ENABLE TRIGGER trg_strategy_sim_preserve_decision");
                }
                System.out.println("STRATEGY_SIM_RESTART_A " + accepted.orderId());
            } else if ("B".equals(mode)) {
                var recovered = sim.advance(paperRunId);
                if (!"ACCEPTED".equals(recovered.status()) || recovered.orderId() == null) {
                    throw new AssertionError("second process did not recover decision association");
                }
                PaperMatchingService matching = context.getBean(PaperMatchingService.class);
                matching.matchOnce(100);
                matching.matchOnce(100);
                var facts = sim.facts(paperRunId);
                if (facts.orders().size() != 1 || facts.trades().size() != 1
                        || facts.ledgerEntries().size() != 6) {
                    throw new AssertionError("second process duplicated or missed canonical facts");
                }
                System.out.println("STRATEGY_SIM_RESTART_B " + recovered.orderId());
            } else if ("C".equals(mode)) {
                ContinuousSimRepository progress = context.getBean(ContinuousSimRepository.class);
                var previous = progress.recentBars(paperRunId);
                HistoricalBar last = previous.getLast();
                var nextOpen = last.openTime().plus(Duration.ofHours(1));
                var nextClose = nextOpen.plus(Duration.ofHours(1)).minusMillis(1);
                var serverTime = nextClose.plusSeconds(61);
                BigDecimal price = new BigDecimal("90");
                HistoricalBar next = new HistoricalBar(last.exchangeCode(), last.marketType(),
                        last.symbol(), last.interval(), nextOpen, nextClose, price, price, price,
                        price, BigDecimal.ONE, null, null, "OK", "{}", serverTime);
                ClosedBarMarketFeed feed = (firstOpen, maximumBars) -> new ClosedBarMarketFeed.Observation(
                        serverTime, serverTime, List.of(previous.get(previous.size() - 2), last, next)
                        .stream().filter(bar -> !bar.openTime().isBefore(firstOpen)).toList(),
                        new ClosedBarMarketFeed.Rule(serverTime, "LIVE", new BigDecimal("0.01"),
                                new BigDecimal("0.0001"), new BigDecimal("0.0001")),
                        new ClosedBarMarketFeed.Quote(serverTime.plusSeconds(1), price));
                ContinuousSimRunService driver = new ContinuousSimRunService(progress, sim,
                        context.getBean(PaperTradingRunService.class), feed, jdbc,
                        Clock.fixed(serverTime.plusSeconds(2), java.time.ZoneOffset.UTC));
                var status = driver.pollOnce(paperRunId);
                if (!nextOpen.equals(status.lastProcessedBar()) || !"RUNNING".equals(status.status())) {
                    throw new AssertionError("continuous restart failed: " + status);
                }
                System.out.println("STRATEGY_SIM_RESTART_C " + nextOpen);
            } else {
                throw new IllegalArgumentException("unknown restart mode");
            }
        }
    }
}
