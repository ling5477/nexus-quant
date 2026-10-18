package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunOrderItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunOverview;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRelatedPage;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunTradeItem;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunDetailQueryPort;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRelatedQuery;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcStrategyRunDetailQueryAdapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** 明确的合成目录只用于读取与窗口验证，不宣称这些定义完成过创建、版本发布或交易运行。 */
public final class StrategyRunDetailFixtureMain {
    private static final AtomicInteger OBSERVED_READS = new AtomicInteger();

    private StrategyRunDetailFixtureMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("fresh ready and stop paths required");
        String url = System.getenv("TASK_RUN_DETAIL_DB_URL");
        if (url == null
                || !url.matches(
                        "jdbc:postgresql://127\\.0\\.0\\.1:15442/run_detail_[a-f0-9]{32}")) {
            throw new IllegalArgumentException("owned random loopback database required");
        }
        Path ready = Path.of(args[0]).toAbsolutePath(), stop = Path.of(args[1]).toAbsolutePath();
        if (!ready.getParent().equals(stop.getParent())
                || Files.exists(ready)
                || Files.exists(stop)) {
            throw new IllegalArgumentException("fresh fixture identity required");
        }
        var application = new SpringApplication(NexusQuantApplication.class, ReadObservation.class);
        try (var context =
                (ServletWebServerApplicationContext)
                        application.run(
                                "--server.address=127.0.0.1",
                                "--server.port=0",
                                "--spring.profiles.active=local",
                                "--spring.datasource.url=" + url,
                                "--spring.datasource.username="
                                        + System.getenv("TASK_RUN_DETAIL_DB_USER"),
                                "--spring.datasource.password="
                                        + System.getenv("TASK_RUN_DETAIL_DB_PASSWORD"),
                                "--nq.security.secret=strategy-paging-synthetic-jwt-secret-123456789",
                                "--nq.security.issuer=strategy-paging-synthetic",
                                "--nq.account.credentials.master-key=strategy-paging-synthetic-master-key-123456789",
                                "--nq.auth.bootstrap-admin.enabled=false",
                                "--nq.env-safety.no-outbound=true",
                                // 正式标的目录随控制组件注册；全部执行驱动和真实外联仍显式关闭。
                                "--nq.runtime.trading-components.enabled=true",
                                "--nq.env-safety.live-enabled=false",
                                "--nq.env-safety.real-exchange-enabled=false",
                                "--nq.env-safety.real-client-enabled=false",
                                "--nq.env-safety.real-provider-enabled=false",
                                "--nq.env-safety.no-outbound=true",
                                "--nq.strategy-sim.enabled=false",
                                "--nq.continuous-sim.enabled=false",
                                "--nq.instrument.catalog-sync.enabled=false",
                                "--nq.public-marketdata.outbound.enabled=false",
                                "--nq.public-marketdata.order-book.enabled=false",
                                "--nq.okx.recovery.enabled=false",
                                "--nq.okx.ws.enabled=false",
                                "--nq.binance.ws.enabled=false",
                                "--nq.account.performance.sampling-enabled=false")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            var passwords = context.getBean(PasswordEncoder.class);
            String encoded = passwords.encode("strategy-paging-synthetic-password");
            context.getBean(AuthSeedService.class)
                    .seedUsers(
                            List.of(
                                    new SeedUserCommand(
                                            "paging-owner",
                                            encoded,
                                            List.of("OPERATOR", "VIEWER"),
                                            true),
                                    new SeedUserCommand(
                                            "paging-viewer", encoded, List.of("VIEWER"), true)));
            long owner =
                    jdbc.queryForObject(
                            "SELECT id FROM users WHERE username='paging-owner'", Long.class);
            var accounts = context.getBean(ExchangeAccountCommandService.class);
            long okx =
                    accounts.create(
                                    owner,
                                    new ExchangeAccountCreateCommand(
                                            "OKX", "SIM", "Synthetic OKX catalog account", null))
                            .legacyAccountId();
            long binance =
                    accounts.create(
                                    owner,
                                    new ExchangeAccountCreateCommand(
                                            "BINANCE",
                                            "SIM",
                                            "Synthetic BINANCE catalog account",
                                            null))
                            .legacyAccountId();
            // 同刻记录验证末级ID排序；两类SIM账户匹配各自的交易所，不增加任何LIVE能力。
            jdbc.update(
                    """
                    INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,
                        account_id,trade_env,enabled,config_snapshot,version,created_at,updated_at)
                    SELECT 'str-paging-'||lpad(n::text,4,'0'),
                        CASE WHEN n=527 THEN 'literal-%_!-0527' ELSE 'strategy-paging-'||lpad(n::text,4,'0') END,
                        CASE WHEN n=526 THEN 'Literal %_! Alpha' ELSE 'Synthetic strategy '||lpad(n::text,4,'0') END,
                        CASE WHEN n%5=0 THEN 'GRID' ELSE 'SPOT_SMA_TARGET_V1' END,
                        CASE WHEN n%2=0 THEN 'OKX' ELSE 'BINANCE' END,
                        CASE WHEN n%2=0 THEN ? ELSE ? END, 'SIM', n%3<>0, '{}'::jsonb,1,
                        '2026-10-01T00:00:00Z'::timestamptz,'2026-10-01T00:00:00Z'::timestamptz
                    FROM generate_series(1,2) n
                    """,
                    okx, binance);
            var identity =
                    new ObjectMapper()
                            .createObjectNode()
                            .put("pid", ProcessHandle.current().pid())
                            .put("port", context.getWebServer().getPort())
                            .put("visibleStrategies", 2)
                            .put("schema", 22);
            Files.writeString(ready, identity.toString());
            long deadline = System.nanoTime() + 600_000_000_000L;
            while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(100);
            Files.writeString(
                    ready.resolveSibling("read-observation.json"),
                    "{\"observedQueries\":"
                            + OBSERVED_READS.get()
                            + ",\"readOnlyRepeatableRead\":true}");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ReadObservation {
        /** 观察正式只读快照；服务端存在性、总数及页SQL保持原实现。 */
        @Bean
        @Primary
        StrategyRunDetailQueryPort observedDetailReads(
                JdbcStrategyRunDetailQueryAdapter repository, JdbcTemplate jdbc) {
            return new StrategyRunDetailQueryPort() {
                public Optional<StrategyRunOverview> findOverview(String id) {
                    observe(jdbc);
                    return repository.findOverview(id);
                }

                public boolean runExists(String id) {
                    observe(jdbc);
                    return repository.runExists(id);
                }

                public StrategyRunRelatedPage<StrategyRunOrderItem> readOrders(
                        StrategyRunRelatedQuery query) {
                    observe(jdbc);
                    return repository.readOrders(query);
                }

                public StrategyRunRelatedPage<StrategyRunTradeItem> readTrades(
                        StrategyRunRelatedQuery query) {
                    observe(jdbc);
                    return repository.readTrades(query);
                }
            };
        }

        private static void observe(JdbcTemplate jdbc) {
            if (!"repeatable read"
                            .equals(jdbc.queryForObject("SHOW transaction_isolation", String.class))
                    || !"on"
                            .equals(
                                    jdbc.queryForObject(
                                            "SHOW transaction_read_only", String.class))) {
                throw new IllegalStateException(
                        "run detail must use read-only repeatable-read transaction");
            }
            OBSERVED_READS.incrementAndGet();
        }
    }
}
