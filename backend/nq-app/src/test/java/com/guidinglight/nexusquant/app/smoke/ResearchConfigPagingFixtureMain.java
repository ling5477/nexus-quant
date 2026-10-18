package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.research.domain.port.ResearchConfigListQueryPort;
import com.guidinglight.nexusquant.research.infra.jdbc.JdbcResearchConfigRepository;
import com.guidinglight.nexusquant.strategy.domain.SpotSmaTargetStrategy;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 大目录只作为合成前置；真实Servlet/JWT/PG读取不等于这些配置的业务创建证明。 */
public final class ResearchConfigPagingFixtureMain {
    private static final String PASSWORD = "research-paging-synthetic-password";
    private static final AtomicInteger OBSERVED_READS = new AtomicInteger();

    private ResearchConfigPagingFixtureMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("fresh ready and stop paths required");
        String url = System.getenv("TASK_RESEARCH_PAGING_DB_URL");
        if (url == null
                || !url.matches(
                        "jdbc:postgresql://127\\.0\\.0\\.1:15442/research_paging_[a-f0-9]{32}")) {
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
                                        + System.getenv("TASK_RESEARCH_PAGING_DB_USER"),
                                "--spring.datasource.password="
                                        + System.getenv("TASK_RESEARCH_PAGING_DB_PASSWORD"),
                                "--nq.security.secret=research-paging-synthetic-jwt-secret-123456789",
                                "--nq.security.issuer=research-paging-synthetic",
                                "--nq.account.credentials.master-key=research-paging-synthetic-master-key-123456789",
                                "--nq.auth.bootstrap-admin.enabled=false",
                                "--nq.env-safety.no-outbound=true",
                                "--nq.runtime.trading-components.enabled=false",
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
            context.getBean(AuthSeedService.class)
                    .seedUsers(
                            List.of(
                                    new SeedUserCommand(
                                            "paging-owner",
                                            passwords.encode(PASSWORD),
                                            List.of("OPERATOR", "VIEWER"),
                                            true),
                                    new SeedUserCommand(
                                            "paging-viewer",
                                            passwords.encode(PASSWORD),
                                            List.of("VIEWER"),
                                            true)));
            long owner =
                    jdbc.queryForObject(
                            "SELECT id FROM users WHERE username='paging-owner'", Long.class);
            var account =
                    context.getBean(ExchangeAccountCommandService.class)
                            .create(
                                    owner,
                                    new ExchangeAccountCreateCommand(
                                            "OKX",
                                            "SIM",
                                            "Synthetic research paging account",
                                            null));
            Instant now = Instant.parse("2026-10-01T00:00:00Z");
            var strategies = context.getBean(StrategyDefinitionRepository.class);
            for (String suffix : List.of("primary", "secondary")) {
                strategies.insert(
                        new StrategyDefinition(
                                "str-paging-" + suffix,
                                "paging-" + suffix,
                                "Synthetic paging " + suffix,
                                SpotSmaTargetStrategy.EXECUTABLE,
                                "OKX",
                                account.legacyAccountId(),
                                "SIM",
                                true,
                                "{}",
                                1,
                                now,
                                now));
            }
            // 同一创建时刻制造排序冲突；末页对象与通配符名称必须在SQL筛选后仍可完整定位。
            jdbc.update(
                    """
                    INSERT INTO research_configs(research_config_id,source_strategy_id,name,description,
                        strategy_snapshot,config_json,created_at,updated_at,status)
                    SELECT 'rcf-paging-'||lpad(n::text,4,'0'),'str-paging-primary',
                        CASE WHEN n=520 THEN 'Literal %_! Alpha' ELSE 'Synthetic research '||lpad(n::text,4,'0') END,
                        'Explicit large-list fixture','{}'::jsonb,
                        '{"parameterSchema":"{}","parameterDefaults":"{}","datasetSpec":"{}"}'::jsonb,
                        ?,?,CASE WHEN n=521 THEN 'DISABLED' ELSE 'ACTIVE' END
                    FROM generate_series(1,521) n
                    """,
                    Timestamp.from(now), Timestamp.from(now));
            jdbc.update(
                    """
                    INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot,config_json,
                        created_at,updated_at,status,archived_at,archived_by)
                    VALUES('rcf-paging-archived','str-paging-primary','Archived fixture','{}','{}',?,?,'ARCHIVED',?,'fixture')
                    """,
                    Timestamp.from(now),
                    Timestamp.from(now),
                    Timestamp.from(now));
            jdbc.update(
                    """
                    INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot,config_json,
                        created_at,updated_at)
                    SELECT 'rcf-secondary-'||n,'str-paging-secondary','Secondary fixture '||n,'{}','{}',?,?
                    FROM generate_series(1,3) n
                    """,
                    Timestamp.from(now),
                    Timestamp.from(now));
            var identity =
                    new ObjectMapper()
                            .createObjectNode()
                            .put("pid", ProcessHandle.current().pid())
                            .put("port", context.getWebServer().getPort())
                            .put("primarySource", "str-paging-primary")
                            .put("primaryVisible", 521)
                            .put("allVisible", 524)
                            .put("researchRows", 525)
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
        /** 只观察正式JDBC委托所在事务，不替换数据库筛选或返回值。 */
        @Bean
        @Primary
        ResearchConfigListQueryPort observedResearchReads(
                JdbcResearchConfigRepository repository, JdbcTemplate jdbc) {
            return query -> {
                if (!"repeatable read"
                                .equals(
                                        jdbc.queryForObject(
                                                "SHOW transaction_isolation", String.class))
                        || !"on"
                                .equals(
                                        jdbc.queryForObject(
                                                "SHOW transaction_read_only", String.class))) {
                    throw new IllegalStateException(
                            "research list must use a read-only repeatable-read transaction");
                }
                OBSERVED_READS.incrementAndGet();
                return repository.readPage(query);
            };
        }
    }
}
