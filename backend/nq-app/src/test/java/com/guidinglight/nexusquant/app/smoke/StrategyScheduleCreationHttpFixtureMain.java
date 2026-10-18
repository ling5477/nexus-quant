package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.audit.infra.jdbc.JdbcAuditLogRepository;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 明确的合成目录只用于读取与窗口验证，不宣称这些定义完成过创建、版本发布或交易运行。 */
public final class StrategyScheduleCreationHttpFixtureMain {

    private StrategyScheduleCreationHttpFixtureMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("fresh ready and stop paths required");
        String url = System.getenv("TASK_SCHEDULE_CREATION_DB_URL");
        if (url == null
                || !url.matches(
                        "jdbc:postgresql://127\\.0\\.0\\.1:15442/schedule_creation_[a-f0-9]{32}")) {
            throw new IllegalArgumentException("owned random loopback database required");
        }
        Path ready = Path.of(args[0]).toAbsolutePath(), stop = Path.of(args[1]).toAbsolutePath();
        if (!ready.getParent().equals(stop.getParent())
                || Files.exists(ready)
                || Files.exists(stop)) {
            throw new IllegalArgumentException("fresh fixture identity required");
        }
        String faultReady = System.getenv("TASK_SCHEDULE_CREATION_READY_PATH");
        if (faultReady != null && !Path.of(faultReady).toAbsolutePath().equals(ready))
            throw new IllegalArgumentException("owned fault marker path required");
        var application =
                new SpringApplication(NexusQuantApplication.class, FaultConfiguration.class);
        try (var context =
                (ServletWebServerApplicationContext)
                        application.run(
                                "--server.address=127.0.0.1",
                                "--server.port=0",
                                "--spring.profiles.active=local",
                                "--spring.datasource.url=" + url,
                                "--spring.datasource.username="
                                        + System.getenv("TASK_SCHEDULE_CREATION_DB_USER"),
                                "--spring.datasource.password="
                                        + System.getenv("TASK_SCHEDULE_CREATION_DB_PASSWORD"),
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
            if (jdbc.queryForObject(
                            "SELECT count(*) FROM users WHERE username='paging-owner'",
                            Integer.class)
                    == 0) {
                var encoded =
                        context.getBean(PasswordEncoder.class)
                                .encode("strategy-paging-synthetic-password");
                context.getBean(AuthSeedService.class)
                        .seedUsers(
                                List.of(
                                        new SeedUserCommand(
                                                "paging-owner",
                                                encoded,
                                                List.of("OPERATOR", "VIEWER"),
                                                true),
                                        new SeedUserCommand(
                                                "paging-other",
                                                encoded,
                                                List.of("OPERATOR", "VIEWER"),
                                                true),
                                        new SeedUserCommand(
                                                "paging-admin",
                                                encoded,
                                                List.of("ADMIN", "VIEWER"),
                                                true),
                                        new SeedUserCommand(
                                                "paging-viewer",
                                                encoded,
                                                List.of("VIEWER"),
                                                true)));
                long owner =
                        jdbc.queryForObject(
                                "SELECT id FROM users WHERE username='paging-owner'", Long.class);
                var accounts = context.getBean(ExchangeAccountCommandService.class);
                long binance =
                        accounts.create(
                                        owner,
                                        new ExchangeAccountCreateCommand(
                                                "BINANCE",
                                                "SIM",
                                                "Synthetic creation BINANCE",
                                                null))
                                .legacyAccountId();
                long okx =
                        accounts.create(
                                        owner,
                                        new ExchangeAccountCreateCommand(
                                                "OKX", "SIM", "Synthetic creation OKX", null))
                                .legacyAccountId();
                // 合成目录用于表单选择与持久化合同，不代表这些策略具备有效交易配置或已经执行。
                jdbc.update(
                        """
                    INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,
                        account_id,trade_env,enabled,config_snapshot,version,created_at,updated_at)
                    SELECT 'str-paging-'||lpad(n::text,4,'0'),'strategy-paging-'||lpad(n::text,4,'0'),
                        'Synthetic strategy '||lpad(n::text,4,'0'),'GRID',CASE WHEN n%2=0 THEN 'OKX' ELSE 'BINANCE' END,
                        CASE WHEN n%2=0 THEN ? ELSE ? END,'SIM',n%3<>0,'{}'::jsonb,1,
                        '2026-10-01T00:00:00Z'::timestamptz,'2026-10-01T00:00:00Z'::timestamptz FROM generate_series(1,527) n
                    """,
                        okx, binance);
            }
            var identity =
                    new ObjectMapper()
                            .createObjectNode()
                            .put("pid", ProcessHandle.current().pid())
                            .put("port", context.getWebServer().getPort())
                            .put("schema", 23)
                            .put(
                                    "owner",
                                    jdbc.queryForObject(
                                            "SELECT id FROM users WHERE username='paging-owner'",
                                            Long.class));
            Files.writeString(ready, identity.toString());
            long deadline = System.nanoTime() + 600_000_000_000L;
            while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(100);
            Files.writeString(
                    ready.resolveSibling("state-at-stop.json"),
                    new ObjectMapper()
                            .createObjectNode()
                            .put(
                                    "creations",
                                    jdbc.queryForObject(
                                            "SELECT count(*) FROM strategy_schedule_creations",
                                            Long.class))
                            .put(
                                    "schedules",
                                    jdbc.queryForObject(
                                            "SELECT count(*) FROM strategy_schedules", Long.class))
                            .toString());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FaultConfiguration {
        /** 只在自有ready目录出现一次性标记时模拟审计失败，其余调用仍委托正式JDBC实现。 */
        @Bean
        @Primary
        AuditLogRepository observedAudit(JdbcAuditLogRepository delegate) {
            return (domain, action, actor, trace, details) -> {
                String readyPath = System.getenv("TASK_SCHEDULE_CREATION_READY_PATH");
                if ("STRATEGY_SCHEDULE_CREATION_COMPLETED".equals(action) && readyPath != null) {
                    try {
                        if (Files.deleteIfExists(Path.of(readyPath).resolveSibling("fail-audit")))
                            throw new IllegalStateException(
                                    "synthetic creation audit completion failure");
                    } catch (IOException failure) {
                        throw new IllegalStateException("owned fault marker failed", failure);
                    }
                }
                delegate.append(domain, action, actor, trace, details);
            };
        }
    }
}
