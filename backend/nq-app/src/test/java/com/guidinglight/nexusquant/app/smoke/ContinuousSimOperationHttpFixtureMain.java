package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.research.application.paper.command.PaperRunAlertCreateCommand;
import com.guidinglight.nexusquant.research.application.paper.service.PaperRunMonitorService;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimOperationRequest;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimOperationService;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.strategy.domain.PublicReplayAssumptionIdentity;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 只准备合成冻结输入和用户；所有连续SIM命令由父测试通过正式Servlet/JWT发起。 */
public final class ContinuousSimOperationHttpFixtureMain {
    static final String PASSWORD = "continuous-operation-synthetic-login";

    public static void main(String[] args) throws Exception {
        if (args.length != 3
                || !List.of(
                                "create",
                                "emergency",
                                "disabled",
                                "upgrade-source",
                                "alerts",
                                "alert-read",
                                "alert-upgrade-source")
                        .contains(args[0]))
            throw new IllegalArgumentException("bounded continuous fixture mode required");
        String url = System.getenv("TASK_CONTINUOUS_OPERATION_DB_URL");
        if (url == null
                || !url.matches(
                        "jdbc:postgresql://127\\.0\\.0\\.1:15442/continuous_operation_http_[a-f0-9]{32}"))
            throw new IllegalArgumentException("owned random database required");
        Path ready = Path.of(args[1]).toAbsolutePath(), stop = Path.of(args[2]).toAbsolutePath();
        if (!ready.getParent().equals(stop.getParent())
                || Files.exists(ready)
                || Files.exists(stop))
            throw new IllegalArgumentException("fresh process identity required");
        boolean enabled = !List.of("disabled", "alert-read").contains(args[0]);
        var app = new SpringApplication(NexusQuantApplication.class, FeedConfiguration.class);
        try (var context =
                (ServletWebServerApplicationContext)
                        app.run(
                                "--server.address=127.0.0.1",
                                "--server.port=0",
                                "--spring.profiles.active=local,public-marketdata-manual",
                                "--spring.datasource.url="
                                        + url
                                        + "?connectTimeout=5&socketTimeout=30",
                                "--spring.flyway.target="
                                        + (args[0].equals("emergency") ? "33" : args[0].equals("upgrade-source")
                                                ? "20"
                                                : List.of("alerts", "alert-read").contains(args[0])
                                                        ? "22"
                                                        : "21"),
                                "--spring.datasource.username="
                                        + System.getenv("TASK_CONTINUOUS_OPERATION_DB_USER"),
                                "--spring.datasource.password="
                                        + System.getenv("TASK_CONTINUOUS_OPERATION_DB_PASSWORD"),
                                "--nq.account.credentials.master-key=continuous-operation-synthetic-master-key",
                                "--nq.security.secret=FAKE-PLACEHOLDER-CONTROLLED-JWT-SIGNING-KEY",
                                "--nq.security.issuer=continuous-operation-synthetic",
                                "--nq.env-safety.ci=false",
                                "--nq.env-safety.no-outbound=true",
                                "--nq.env-safety.live-enabled=false",
                                "--nq.env-safety.real-provider-enabled=false",
                                "--nq.env-safety.real-client-enabled=false",
                                "--nq.env-safety.real-exchange-enabled=false",
                                "--nq.auth.bootstrap-admin.enabled=false",
                                "--nq.runtime.trading-components.enabled=true",
                                "--nq.instrument.catalog-sync.enabled=false",
                                "--nq.okx.recovery.enabled=false",
                                "--nq.okx.ws.enabled=false",
                                "--nq.binance.ws.enabled=false",
                                "--nq.account-fees.binance.enabled=false",
                                "--nq.okx.private-readonly-diagnostics.enabled=false",
                                "--nq.strategy-sim.enabled=true",
                                "--nq.continuous-sim.enabled=" + enabled,
                                "--nq.account.performance.sampling-enabled=false",
                                "--nq.public-news.enabled=false",
                                "--nq.public-marketdata.outbound.enabled=false",
                                "--nq.public-marketdata.order-book.enabled=false",
                                "--nq.public-marketdata.outbound.base-url=disabled://continuous-operation-fixture",
                                "--nq.public-marketdata.base-url=disabled://continuous-operation-fixture")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            var mapper = context.getBean(ObjectMapper.class);
            var identity =
                    mapper.createObjectNode()
                            .put("pid", ProcessHandle.current().pid())
                            .put("port", context.getWebServer().getPort());
            if (enabled) {
                var passwords = context.getBean(PasswordEncoder.class);
                context.getBean(AuthSeedService.class)
                        .seedUsers(
                                List.of(
                                        new SeedUserCommand(
                                                "continuous-owner",
                                                passwords.encode(PASSWORD),
                                                List.of("OPERATOR", "VIEWER"),
                                                true),
                                        new SeedUserCommand(
                                                "continuous-other",
                                                passwords.encode(PASSWORD),
                                                List.of("OPERATOR", "VIEWER"),
                                                true),
                                        new SeedUserCommand(
                                                "continuous-admin",
                                                passwords.encode(PASSWORD),
                                                List.of("ADMIN"),
                                                true),
                                        new SeedUserCommand(
                                                "continuous-viewer",
                                                passwords.encode(PASSWORD),
                                                List.of("VIEWER"),
                                                true)));
                identity.put(
                        "owner",
                        jdbc.queryForObject(
                                "SELECT id FROM users WHERE username='continuous-owner'",
                                Long.class));
                String publish = seedPublicHourly(jdbc, mapper);
                identity.put("publish", publish);
                if (args[0].equals("upgrade-source")) {
                    // 旧schema下通过原Java业务服务建立真实游标，升级不能丢失其独立账户和资金事实。
                    identity.put(
                            "paperRunId",
                            context.getBean(ContinuousSimRunService.class)
                                    .start(publish, new BigDecimal("100"), "continuous-owner")
                                    .paperRunId());
                }
                if (args[0].equals("alert-upgrade-source")) {
                    // V21先通过原服务建立真实连续回执和资金；旧告警不伪造尚不存在的V22操作历史。
                    var operation =
                            context.getBean(ContinuousSimOperationService.class)
                                    .submit(
                                            new ContinuousSimOperationRequest(
                                                    UUID.randomUUID(),
                                                    ContinuousSimOperationRequest.Type.CREATE,
                                                    publish,
                                                    new BigDecimal("100"),
                                                    null),
                                            identity.path("owner").asLong(),
                                            "continuous-owner",
                                            "alert-upgrade-source-" + UUID.randomUUID());
                    identity.put("paperRunId", operation.paperRunId());
                    identity.put("continuousOperationId", operation.operationId().toString());
                    var monitor = context.getBean(PaperRunMonitorService.class);
                    var alertIds = identity.putArray("alertIds");
                    for (int index = 0; index < 3; index++) {
                        var alert =
                                monitor.createAlert(
                                        new PaperRunAlertCreateCommand(
                                                operation.paperRunId(),
                                                "UPGRADE_FIXTURE",
                                                "LOW",
                                                "Existing alert " + index,
                                                "synthetic pre-upgrade alert",
                                                "TEST",
                                                "{\"before\":true}"));
                        if (index == 1)
                            monitor.ackAlert(
                                    operation.paperRunId(), alert.alertId(), "continuous-owner");
                        if (index == 2)
                            monitor.resolveAlert(
                                    operation.paperRunId(), alert.alertId(), "continuous-owner");
                        alertIds.add(alert.alertId());
                    }
                }
            }
            Files.writeString(ready, identity.toString());
            long deadline = System.nanoTime() + Duration.ofMinutes(4).toNanos();
            while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(100);
            if (!Files.exists(stop))
                throw new IllegalStateException("bounded continuous fixture lifetime exceeded");
        }
    }

    /** 仅作为命令证明的合成前置，不把直接写入的冻结研究记录当作上游发布流程已通过。 */
    private static String seedPublicHourly(JdbcTemplate jdbc, ObjectMapper mapper)
            throws Exception {
        String id = UUID.randomUUID().toString().replace("-", "");
        String strategy = "str-cont-" + id,
                code = "cont-" + id,
                version = "sv-cont-" + id,
                research = "rc-cont-" + id,
                config = "bc-cont-" + id,
                backtest = "br-cont-" + id,
                publish = "pub-cont-" + id;
        String params = "{\"window\":3,\"investedExposure\":\"1\"}";
        String source = "{\"executable\":\"SPOT_SMA_TARGET_V1\"}";
        ObjectNode backtestVersion =
                mapper.createObjectNode()
                        .put("strategyVersionId", version)
                        .put("checksum", "a".repeat(64));
        backtestVersion.set("paramSnapshotJson", mapper.readTree(params));
        backtestVersion.set("sourceSnapshotJson", mapper.readTree(source));
        ObjectNode publishVersion =
                mapper.createObjectNode()
                        .put("strategyVersionId", version)
                        .put("checksum", "a".repeat(64));
        publishVersion.set("paramSnapshot", mapper.readTree(params));
        publishVersion.set("sourceSnapshot", mapper.readTree(source));
        Long account =
                jdbc.queryForObject(
                        "INSERT INTO accounts(account_code,venue,status) VALUES (?,'OKX','ACTIVE') RETURNING account_id",
                        Long.class,
                        "CONTINUOUS-TEST-SOURCE-" + id);
        jdbc.update(
                """
                INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,
                    exchange_code,account_id,trade_env,enabled,config_snapshot)
                VALUES (?,?,?,'SPOT_SMA_TARGET_V1','OKX',?,'SIM',true,'{}'::jsonb)
                """,
                strategy,
                code,
                "Continuous synthetic input",
                account);
        jdbc.update(
                """
                INSERT INTO strategy_versions(strategy_version_id,strategy_code,version,version_name,status,
                    param_snapshot_json,source_snapshot_json,checksum) VALUES (?,?,1,'v1','ACTIVE',?::jsonb,?::jsonb,?)
                """,
                version,
                code,
                params,
                source,
                "a".repeat(64));
        jdbc.update(
                "INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot) VALUES (?,?,?,'{}'::jsonb)",
                research,
                strategy,
                "Continuous synthetic input");
        jdbc.update(
                """
                INSERT INTO backtest_configs(backtest_config_id,research_config_id,name,strategy_version_id,
                    strategy_version_snapshot_json,param_snapshot_json) VALUES (?,?,?,?,?::jsonb,?::jsonb)
                """,
                config,
                research,
                "Continuous synthetic input",
                version,
                backtestVersion.toString(),
                params);
        List<HistoricalBar> bars = new ArrayList<>();
        for (int hour = 0; hour < 5; hour++) {
            Instant open = Instant.parse("2026-09-25T00:00:00Z").plusSeconds(hour * 3600L);
            Instant close = open.plusSeconds(3600).minusMillis(1);
            BigDecimal price = BigDecimal.valueOf(100L + hour);
            bars.add(
                    new HistoricalBar(
                            "OKX",
                            "SPOT",
                            "BTC-USDT",
                            BarInterval.ONE_HOUR,
                            open,
                            close,
                            price,
                            price,
                            price,
                            price,
                            BigDecimal.ONE,
                            null,
                            null,
                            "OK",
                            "{}",
                            close));
        }
        var barIdentity = SpotBarIdentity.capture(bars, mapper);
        ObjectNode assumptions =
                (ObjectNode)
                        mapper.readTree(
                                """
                {"quantityStep":"0.0001","priceTick":"0.01","minimumQuantity":"0.0001",
                 "minimumNotional":"5","feeRate":"0.001","slippageBps":"10"}
                """);
        assumptions
                .put("ruleSha256", "b".repeat(64))
                .put("rulePolicy", "CURRENTLY_OBSERVED_PUBLIC_RULES");
        ObjectNode summary =
                mapper.createObjectNode()
                        .put("strategyVersionId", version)
                        .put("exchangeCode", "OKX")
                        .put("symbol", "BTC-USDT")
                        .put("interval", "1h")
                        .put("barCount", bars.size())
                        .put("barContentSha256", barIdentity.sha256())
                        .put("datasetProvider", "public-capture")
                        .put(
                                "costAndRuleSha256",
                                PublicReplayAssumptionIdentity.sha256(assumptions));
        summary.set("consumedBars", mapper.readTree(barIdentity.canonicalJson()));
        summary.set("costAndRuleAssumptions", assumptions);
        ObjectNode dataset =
                mapper.createObjectNode()
                        .put("provider", "public-capture")
                        .put("source", "OKX_PUBLIC_CAPTURE")
                        .put("exchangeCode", "OKX")
                        .put("symbol", "BTC-USDT")
                        .put("interval", "1h")
                        .put("barCount", bars.size());
        dataset.putObject("capture")
                .put("consumedSha256", barIdentity.sha256())
                .put("replayVisibilityVersion", "CLOSED_HOURLY_BOUNDARY_V1")
                .put("replayVisibilitySource", "EXPERIMENT_ASSUMPTION")
                .put("ruleSha256", "b".repeat(64));
        jdbc.update(
                """
                INSERT INTO backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,
                    status,strategy_snapshot,backtest_config_snapshot,summary_json,requested_at,strategy_version_id,
                    strategy_version_snapshot_json,param_snapshot_json,dataset_snapshot_json)
                VALUES (?,?,?,?,'SUCCEEDED','{}'::jsonb,'{}'::jsonb,?::jsonb,now(),?,?::jsonb,?::jsonb,?::jsonb)
                """,
                backtest,
                config,
                research,
                strategy,
                summary.toString(),
                version,
                backtestVersion.toString(),
                params,
                dataset.toString());
        jdbc.update(
                """
                INSERT INTO backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,
                    source_strategy_id,target_strategy_definition_id,publish_status,publish_name,publish_snapshot_json,
                    strategy_version_id,version_snapshot_json) VALUES (?,?,?,?,?,?,'SUCCEEDED',?,?::jsonb,?,?::jsonb)
                """,
                publish,
                backtest,
                research,
                config,
                strategy,
                strategy,
                "Continuous synthetic input",
                mapper.createObjectNode().put("strategyCode", code).toString(),
                version,
                publishVersion.toString());
        return publish;
    }

    @TestConfiguration
    static class FeedConfiguration {
        @Bean
        static BeanFactoryPostProcessor explicitFixtureUsersOnly() {
            // 本夹具显式准备自身用户；重启不能让local默认种子覆盖旧密码、角色或更新时间。
            return factory -> {
                if (factory instanceof BeanDefinitionRegistry registry
                        && registry.containsBeanDefinition("authSeedRunner"))
                    registry.removeBeanDefinition("authSeedRunner");
            };
        }

        @Bean
        ClosedBarMarketFeed closedFeed() {
            return (firstOpen, limit) -> {
                throw new IllegalStateException("UNEXPECTED_FIXTURE_FEED_READ");
            };
        }
    }
}
