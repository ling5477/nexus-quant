package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.research.domain.eval.port.BacktestEvaluationListQueryPort;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishListQueryPort;
import com.guidinglight.nexusquant.research.infra.eval.jdbc.JdbcBacktestEvaluationReportRepository;
import com.guidinglight.nexusquant.research.infra.jdbc.JdbcBacktestPublishRecordRepository;
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

/** 合成目录只证明真实分页/所有者读取合同，不冒充配置创建或真实费用来源联调。 */
public final class ResultListPagingFixtureMain {
    private static final AtomicInteger OBSERVED_READS = new AtomicInteger();

    private ResultListPagingFixtureMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("fresh ready and stop paths required");
        String url = System.getenv("TASK_RESULT_LIST_PAGING_DB_URL");
        if (url == null
                || !url.matches(
                        "jdbc:postgresql://127\\.0\\.0\\.1:15442/result_list_paging_[a-f0-9]{32}")) {
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
                                        + System.getenv("TASK_RESULT_LIST_PAGING_DB_USER"),
                                "--spring.datasource.password="
                                        + System.getenv("TASK_RESULT_LIST_PAGING_DB_PASSWORD"),
                                "--nq.security.secret=result-list-paging-synthetic-jwt-secret-123456789",
                                "--nq.security.issuer=result-list-paging-synthetic",
                                "--nq.account.credentials.master-key=backtest-paging-synthetic-master-key-123456789",
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
                                            passwords.encode("research-paging-synthetic-password"),
                                            List.of("OPERATOR", "VIEWER"),
                                            true),
                                    new SeedUserCommand(
                                            "paging-viewer",
                                            passwords.encode("research-paging-synthetic-password"),
                                            List.of("VIEWER"),
                                            true)));
            long owner =
                    jdbc.queryForObject(
                            "SELECT id FROM users WHERE username='paging-owner'", Long.class);
            long viewer =
                    jdbc.queryForObject(
                            "SELECT id FROM users WHERE username='paging-viewer'", Long.class);
            var account =
                    context.getBean(ExchangeAccountCommandService.class)
                            .create(
                                    owner,
                                    new ExchangeAccountCreateCommand(
                                            "OKX",
                                            "SIM",
                                            "Synthetic backtest paging account",
                                            null));
            Instant now = Instant.parse("2026-10-01T00:00:00Z");
            context.getBean(StrategyDefinitionRepository.class)
                    .insert(
                            new StrategyDefinition(
                                    "str-backtest-paging",
                                    "backtest-paging",
                                    "Synthetic paging strategy",
                                    SpotSmaTargetStrategy.EXECUTABLE,
                                    "OKX",
                                    account.legacyAccountId(),
                                    "SIM",
                                    true,
                                    "{}",
                                    1,
                                    now,
                                    now));
            jdbc.update(
                    """
                    INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot,config_json,created_at,updated_at)
                    SELECT 'rcf-paging-'||suffix,'str-backtest-paging','Synthetic parent '||suffix,'{}','{}',?,?
                    FROM unnest(ARRAY['primary','secondary']) suffix
                    """,
                    Timestamp.from(now),
                    Timestamp.from(now));
            // 同刻大目录显式制造末页与排序冲突；超安全整数金额不能在新JSON路径里被舍入。
            jdbc.update(
                    """
                    INSERT INTO backtest_configs(backtest_config_id,research_config_id,name,description,config_json,created_at,updated_at,status)
                    SELECT 'bcf-paging-'||lpad(n::text,4,'0'),'rcf-paging-primary',
                        CASE WHEN n=520 THEN 'Literal %_! Alpha' ELSE 'Synthetic backtest '||lpad(n::text,4,'0') END,
                        'Explicit paging fixture',
                        '{"startTime":"2026-10-01T00:00:00Z","endTime":"2026-10-02T00:00:00Z","initialCapital":"9007199254740993.123456789012345678","executionSpec":{}}'::jsonb,
                        ?,?,CASE WHEN n=521 THEN 'DISABLED' ELSE 'ACTIVE' END
                    FROM generate_series(1,521) n
                    """,
                    Timestamp.from(now), Timestamp.from(now));
            jdbc.update(
                    """
                    INSERT INTO backtest_configs(backtest_config_id,research_config_id,name,config_json,created_at,updated_at)
                    SELECT 'bcf-secondary-'||n,'rcf-paging-secondary','Secondary '||n,
                        '{"startTime":"2026-10-01T00:00:00Z","endTime":"2026-10-02T00:00:00Z","initialCapital":"100","executionSpec":{}}',?,?
                    FROM generate_series(1,3) n
                    """,
                    Timestamp.from(now),
                    Timestamp.from(now));
            // 所有者由正式认证解析；这里的原币来源标记仅是隔离读取负例，不具备费用观察资格。
            for (var identity :
                    List.of(
                            new Object[] {"owner", owner},
                            new Object[] {"viewer", viewer},
                            new Object[] {"malformed", -99L})) {
                String suffix = (String) identity[0];
                String spec =
                        new ObjectMapper()
                                .createObjectNode()
                                .put("costSource", "CURRENT_ACCOUNT_OBSERVATION")
                                .put("feeSourceOwnerUserId", String.valueOf(identity[1]))
                                .put("feeObservationId", "explicit-synthetic-read-scope-only")
                                .toString();
                jdbc.update(
                        """
                        INSERT INTO backtest_configs(backtest_config_id,research_config_id,name,config_json,created_at,updated_at)
                        VALUES(?, 'rcf-paging-primary',?,jsonb_build_object('startTime','2026-10-01T00:00:00Z','endTime','2026-10-02T00:00:00Z',
                            'initialCapital','100','executionSpec',?::jsonb),?,?)
                        """,
                        "bcf-private-" + suffix,
                        "Private " + suffix,
                        spec,
                        Timestamp.from(now.minusSeconds(1)),
                        Timestamp.from(now.minusSeconds(1)));
            }
            jdbc.update(
                    """
                    INSERT INTO backtest_configs(backtest_config_id,research_config_id,name,config_json,created_at,updated_at,status,archived_at,archived_by)
                    VALUES('bcf-paging-archived','rcf-paging-primary','Archived fixture',
                        '{"startTime":"2026-10-01T00:00:00Z","endTime":"2026-10-02T00:00:00Z","initialCapital":"100","executionSpec":{}}',?,?,'ARCHIVED',?,'fixture')
                    """,
                    Timestamp.from(now),
                    Timestamp.from(now),
                    Timestamp.from(now));
            // 以下SQL只提供列表事实前置；不宣称这些合成运行完成过回测、评估或正式发布。
            jdbc.update(
                    """
                    INSERT INTO strategy_versions(strategy_version_id,strategy_code,version,version_name,status,checksum,created_at,updated_at)
                    VALUES('sv-result-paging','backtest-paging',1,'Synthetic list-only version','ACTIVE',repeat('a',64),?,?)
                    """,
                    Timestamp.from(now),
                    Timestamp.from(now));
            jdbc.update(
                    """
                    INSERT INTO backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,
                        strategy_snapshot,backtest_config_snapshot,config_snapshot_json,requested_at,created_at,updated_at,strategy_version_id)
                    SELECT CASE WHEN c.backtest_config_id='bcf-paging-0520' THEN 'brn-literal-%_!-0520' ELSE replace(c.backtest_config_id,'bcf-','brn-') END,
                        c.backtest_config_id,c.research_config_id,'str-backtest-paging','SUCCEEDED','{}',c.config_json,c.config_json,?,?,?,'sv-result-paging'
                    FROM backtest_configs c
                    """,
                    Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
            jdbc.update(
                    """
                    INSERT INTO backtest_eval_reports(eval_report_id,backtest_run_id,evaluation_status,initial_capital,
                        final_equity,net_pnl,metrics_json,failure_code,evaluated_at,created_at,updated_at)
                    SELECT replace(r.backtest_config_id,'bcf-','evr-'),r.backtest_run_id,
                        CASE WHEN r.backtest_config_id='bcf-paging-0400' THEN 'FAILED' ELSE 'SUCCEEDED' END,
                        (r.config_snapshot_json->>'initialCapital')::numeric,(r.config_snapshot_json->>'initialCapital')::numeric,0,'{}',
                        CASE WHEN r.backtest_config_id='bcf-paging-0400' THEN 'SYNTHETIC_LIST_FAILURE' ELSE NULL END,
                        CASE WHEN r.backtest_config_id='bcf-paging-0400' THEN NULL ELSE ?::timestamptz END,?,?
                    FROM backtest_runs r
                    """,
                    Timestamp.from(now),
                    Timestamp.from(now),
                    Timestamp.from(now));
            jdbc.update(
                    """
                    INSERT INTO backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,
                        source_strategy_id,eval_report_id,publish_status,publish_name,published_at,created_at,updated_at,strategy_version_id)
                    SELECT replace(r.backtest_config_id,'bcf-','pub-'),r.backtest_run_id,r.research_config_id,r.backtest_config_id,r.source_strategy_id,
                        e.eval_report_id,e.evaluation_status,'Synthetic result '||r.backtest_config_id,
                        CASE WHEN e.evaluation_status='FAILED' THEN NULL ELSE ?::timestamptz END,?,?,'sv-result-paging'
                    FROM backtest_runs r JOIN backtest_eval_reports e ON e.backtest_run_id=r.backtest_run_id
                    """,
                    Timestamp.from(now),
                    Timestamp.from(now),
                    Timestamp.from(now));
            Files.writeString(
                    ready,
                    new ObjectMapper()
                            .createObjectNode()
                            .put("pid", ProcessHandle.current().pid())
                            .put("port", context.getWebServer().getPort())
                            .put("primarySource", "rcf-paging-primary")
                            .put("primaryVisible", 523)
                            .put("allVisible", 526)
                            .put("backtestRows", 528)
                            .put("strategyVersionId", "sv-result-paging")
                            .put("schema", 22)
                            .toString());
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
        /** 两条正式读取都必须位于只读一致快照；观察后仍委托原JDBC及费用来源范围。 */
        private static void observe(JdbcTemplate jdbc) {
            if (!"repeatable read"
                            .equals(jdbc.queryForObject("SHOW transaction_isolation", String.class))
                    || !"on"
                            .equals(
                                    jdbc.queryForObject(
                                            "SHOW transaction_read_only", String.class))) {
                throw new IllegalStateException("result list requires read-only repeatable-read");
            }
            OBSERVED_READS.incrementAndGet();
        }

        @Bean
        @Primary
        BacktestEvaluationListQueryPort observedEvaluations(
                JdbcBacktestEvaluationReportRepository repository, JdbcTemplate jdbc) {
            return query -> {
                observe(jdbc);
                return repository.readPage(query);
            };
        }

        @Bean
        @Primary
        BacktestPublishListQueryPort observedPublishes(
                JdbcBacktestPublishRecordRepository repository, JdbcTemplate jdbc) {
            return query -> {
                observe(jdbc);
                return repository.readPage(query);
            };
        }
    }
}
