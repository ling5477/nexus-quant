package com.guidinglight.nexusquant.app.shadowrun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest.ArtifactFile;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.TrustedRootStrategyArtifactVerifier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 完整正式 Servlet 的创建恢复夹具；仅操作随机隔离库和合成文件，不接交易所或调度运行。 */
public final class ShadowRunCreationHttpFixtureMain {
    public static final String PASSWORD = "synthetic-shadow-creation-password";

    private ShadowRunCreationHttpFixtureMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("create", "reopen").contains(args[0]))
            throw new IllegalArgumentException("fresh bounded creation fixture mode required");
        String url = System.getenv("TASK_SHADOW_CREATION_DB_URL");
        if (url == null
                || !url.matches(
                        "jdbc:postgresql://127\\.0\\.0\\.1:15442/shadow_creation_http_[a-f0-9]{32}"))
            throw new IllegalArgumentException("owned loopback creation database required");
        Path ready = Path.of(args[1]).toAbsolutePath(), stop = Path.of(args[2]).toAbsolutePath();
        if (!ready.getParent().equals(stop.getParent())
                || Files.exists(ready)
                || Files.exists(stop))
            throw new IllegalArgumentException("fresh fixture paths required");
        Path artifactRoot = ready.getParent().resolve("artifact-root");
        if (args[0].equals("create")) Files.createDirectories(artifactRoot);
        var application = new SpringApplication(NexusQuantApplication.class);
        application.setAdditionalProfiles("local");
        application.setDefaultProperties(
                Map.of(
                        "spring.datasource.url", url,
                        "spring.datasource.username", System.getenv("TASK_SHADOW_CREATION_DB_USER"),
                        "spring.datasource.password",
                                System.getenv("TASK_SHADOW_CREATION_DB_PASSWORD")));
        try (var context =
                application.run(
                        "--spring.profiles.active=local",
                        "--spring.datasource.url=" + url,
                        "--spring.datasource.username="
                                + System.getenv("TASK_SHADOW_CREATION_DB_USER"),
                        "--spring.datasource.password="
                                + System.getenv("TASK_SHADOW_CREATION_DB_PASSWORD"),
                        "--server.address=127.0.0.1",
                        "--server.port=0",
                        "--nq.security.secret=synthetic-shadow-http-secret-synthetic-shadow-http-secret",
                        "--nq.strategy-release.artifacts.trusted-root=" + artifactRoot,
                        "--nq.strategy-sim.enabled=false",
                        "--nq.continuous-sim.enabled=false",
                        "--nq.env-safety.no-outbound=true",
                        "--nq.env-safety.live-enabled=false",
                        "--nq.env-safety.real-provider-enabled=false",
                        "--nq.env-safety.real-client-enabled=false",
                        "--nq.env-safety.real-exchange-enabled=false",
                        "--nq.instrument.catalog-sync.enabled=false",
                        "--nq.okx.recovery.enabled=false",
                        "--nq.okx.ws.enabled=false",
                        "--nq.binance.ws.enabled=false",
                        "--nq.account.performance.sampling-enabled=false",
                        "--nq.okx.adapter.stub-on-bootstrap-failure=false")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            if (args[0].equals("create")) {
                PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
                context.getBean(AuthSeedService.class)
                        .seedUsers(
                                List.of(
                                        new SeedUserCommand(
                                                "shadow-creation-owner",
                                                encoder.encode(PASSWORD),
                                                List.of("OPERATOR"),
                                                true),
                                        new SeedUserCommand(
                                                "shadow-creation-other",
                                                encoder.encode(PASSWORD),
                                                List.of("OPERATOR"),
                                                true),
                                        new SeedUserCommand(
                                                "shadow-creation-admin",
                                                encoder.encode(PASSWORD),
                                                List.of("ADMIN"),
                                                true),
                                        new SeedUserCommand(
                                                "shadow-creation-viewer",
                                                encoder.encode(PASSWORD),
                                                List.of("VIEWER"),
                                                true)));
                seed(jdbc, mapper, artifactRoot);
            }
            String version =
                    jdbc.queryForObject(
                            "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                            String.class);
            if (!"20".equals(version))
                throw new IllegalStateException("expected V20 fixture schema");
            String publish =
                    jdbc.queryForObject(
                            "SELECT publish_record_id FROM backtest_publish_records", String.class);
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            Files.writeString(
                    ready,
                    mapper.writeValueAsString(
                            Map.of(
                                    "pid",
                                    ProcessHandle.current().pid(),
                                    "port",
                                    port,
                                    "publishRecordId",
                                    publish,
                                    "schema",
                                    version,
                                    "mode",
                                    args[0])));
            long deadline = System.nanoTime() + Duration.ofMinutes(12).toNanos();
            while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(100);
            if (!Files.exists(stop))
                throw new IllegalStateException("bounded creation fixture wait expired");
        }
    }

    /** 仅准备输入、冻结回测和 SIM 验证证据；Shadow 必须由测试随后调用正式创建接口产生。 */
    private static void seed(JdbcTemplate jdbc, ObjectMapper mapper, Path artifactRoot)
            throws Exception {
        // 普通恢复夹具使用时分秒；纳秒证明显式选择下面的固定原反例，二者不互相替代覆盖。
        Instant now = Instant.now().minusSeconds(2).truncatedTo(ChronoUnit.SECONDS),
                start = now.minusSeconds(3600);
        // 仅精度证明启用原反例；普通时分秒夹具和既有恢复覆盖继续可独立运行。
        if ("true".equals(System.getenv("TASK_SHADOW_CREATION_NANOSECOND_WINDOW"))) {
            start = Instant.parse("2026-10-14T23:59:33.567325500Z");
            now = Instant.parse("2026-10-15T00:59:33.999999999Z");
        }
        UUID dataset = UUID.randomUUID();
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String strategy = "shadow-source-" + unique, code = "SHADOW_SOURCE_" + unique;
        String version = "shadow-version-" + unique, research = "shadow-research-" + unique;
        String config = "shadow-config-" + unique, run = "shadow-backtest-" + unique;
        String evaluation = "shadow-evaluation-" + unique, publish = "shadow-publish-" + unique;
        byte[] content = "{\"diagnosticFixture\":true}".getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(artifactRoot.resolve("artifact"));
        Files.write(artifactRoot.resolve("artifact/model.json"), content);
        List<ArtifactFile> files =
                List.of(
                        new ArtifactFile(
                                "model",
                                "model.json",
                                HexFormat.of()
                                        .formatHex(
                                                MessageDigest.getInstance("SHA-256")
                                                        .digest(content)),
                                content.length,
                                "application/json"));
        String digest = TrustedRootStrategyArtifactVerifier.computeArtifactDigest(files);
        StrategyArtifactManifest manifest =
                new StrategyArtifactManifest(
                        StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION,
                        version,
                        dataset,
                        evaluation,
                        files,
                        digest,
                        now,
                        "shadow-creation-fixture");
        Files.writeString(
                artifactRoot.resolve("manifest.json"), mapper.writeValueAsString(manifest));
        Long account =
                jdbc.queryForObject(
                        "INSERT INTO accounts(account_code,venue,status) VALUES (?,'PAPER','ACTIVE') RETURNING account_id",
                        Long.class,
                        "shadow-account-" + unique);
        jdbc.update(
                "INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot,version) "
                        + "VALUES (?,?,'Shadow creation fixture','ADMISSION_GUARD','PAPER',?,'SIM',FALSE,'{}'::jsonb,1)",
                strategy,
                code,
                account);
        jdbc.update(
                "INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot) VALUES (?,?,'Shadow creation fixture','{}'::jsonb)",
                research,
                strategy);
        jdbc.update(
                "INSERT INTO backtest_configs(backtest_config_id,research_config_id,name) VALUES (?,?,'Shadow creation fixture')",
                config,
                research);
        jdbc.update(
                "INSERT INTO marketdata_datasets(dataset_id,dataset_name,exchange_code,market_type,symbol,\"interval\",start_time,end_time,status,quality_status,source,created_by) "
                        + "VALUES (?,?,'OKX','SPOT','BTC-USDT','1m',?,?,'READY','OK','SYNTHETIC_CREATION_TEST','test')",
                dataset,
                "shadow-dataset-" + unique,
                Timestamp.from(start),
                Timestamp.from(now));
        jdbc.update(
                "INSERT INTO strategy_versions(strategy_version_id,strategy_code,version,version_name,status,checksum,created_by) VALUES (?,?,1,'Shadow creation fixture','ACTIVE',?,'test')",
                version,
                code,
                digest);
        jdbc.update(
                "INSERT INTO backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,strategy_snapshot,strategy_version_id,backtest_config_snapshot,config_snapshot_json,dataset_snapshot_json,requested_at) "
                        + "VALUES (?,?,?,?,'SUCCEEDED','{}'::jsonb,?,'{}'::jsonb,jsonb_build_object('startTime',?,'endTime',?),jsonb_build_object('datasetId',?),?)",
                run,
                config,
                research,
                strategy,
                version,
                start.toString(),
                now.toString(),
                dataset.toString(),
                Timestamp.from(now));
        jdbc.update(
                "INSERT INTO backtest_eval_reports(eval_report_id,backtest_run_id,evaluation_status,evaluated_at) VALUES (?,?,'SUCCEEDED',?)",
                evaluation,
                run,
                Timestamp.from(now));
        // 文件位置是服务端已有存储字段，不能由后续 HTTP 创建请求携带或覆盖。
        jdbc.update(
                "INSERT INTO backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,source_strategy_id,eval_report_id,strategy_version_id,publish_status,publish_name,artifact_storage_key,manifest_storage_key) "
                        + "VALUES (?,?,?,?,?,?,?,'SUCCEEDED','Shadow creation fixture',?,?)",
                publish,
                run,
                research,
                config,
                strategy,
                evaluation,
                version,
                "artifact",
                "manifest.json");
        jdbc.update(
                "INSERT INTO paper_trading_runs(paper_run_id,publish_id,strategy_version_id,status,trade_env,exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at) "
                        + "VALUES (?,?,?,'STOPPED','SIM','OKX','SPOT','BTC-USDT','1m','test',?,?)",
                "shadow-paper-" + unique,
                publish,
                version,
                Timestamp.from(now),
                Timestamp.from(now));
    }
}
