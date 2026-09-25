package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
import java.util.Map;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** 两个独立 JVM 使用同一隔离 schema，验证已提交订单的决策关联恢复。 */
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
            } else {
                throw new IllegalArgumentException("unknown restart mode");
            }
        }
    }
}
