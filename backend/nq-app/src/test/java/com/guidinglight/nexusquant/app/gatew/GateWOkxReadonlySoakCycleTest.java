package com.guidinglight.nexusquant.app.gatew;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidinglight.nexusquant.account.infra.gatew.JdbcOkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.gatew.OkxPrivateProbeStatus;
import com.guidinglight.nexusquant.account.infra.gatew.OkxPrivateReadObservation;
import com.guidinglight.nexusquant.account.infra.gatew.OkxPrivateReadonlyProbeService;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcExchangeAccountRepository;
import com.guidinglight.nexusquant.adapter.okx.service.JdkOkxPrivateReadTransport;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadError;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadOperation;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadResult;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadTransport;
import com.guidinglight.nexusquant.risk.infra.jdbc.JdbcKillSwitchStateRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchScope;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GateW 真实 OKX private read-only soak 的 test-only 单周期 launcher。
 *
 * <p>该入口默认 Maven/CI 中始终跳过；只有显式 system property、专用 profile、loopback soak DB、
 * 全部 no-LIVE/no-write gate 与 DB credential metadata 同时通过后才可能访问现有 typed transport。
 * 它不启动完整 Spring context，不注册 API/scheduler/runner，也不输出 credential、account、IP、raw body、
 * headers、signature、完整 URL 或余额。</p>
 */
@Tag("manual-private-readonly")
@Tag("gatew-okx-readonly-soak")
@EnabledIfSystemProperty(named = GateWOkxReadonlySoakCycleTest.REQUIRED_PROPERTY, matches = "true")
@SpringJUnitConfig(classes = GateWOkxReadonlySoakCycleTest.JacksonContext.class)
public class GateWOkxReadonlySoakCycleTest {

    static final String REQUIRED_PROPERTY = "nq.gatew.okxReadonlySoak.required";
    static final String ACTION_PROPERTY = "nq.gatew.okxReadonlySoak.action";
    static final String RESULT_FILE_PROPERTY = "nq.gatew.okxReadonlySoak.resultFile";
    static final String REPO_ROOT_PROPERTY = "nq.gatew.okxReadonlySoak.repoRoot";
    static final String PROFILE = "gatew-okx-readonly-soak";
    static final String LAUNCHER_SCHEMA_VERSION = "gatew-soak-launcher-v2";
    static final String SYSTEMD_CREDENTIAL_SOURCE = "SYSTEMD_CREDENTIALS";
    static final String FORMAL_STATE_ROOT = "/var/lib/nexus-quant/gatew-soak";
    static final String REAL_READONLY_SOAK = "REAL_READONLY_SOAK";
    static final String OFFLINE_ISOLATED_ACCEPTANCE = "OFFLINE_ISOLATED_ACCEPTANCE";
    private static final Set<OkxPrivateReadOperation> SOAK_OPERATIONS = Set.of(
            OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ,
            OkxPrivateReadOperation.OKX_ACCOUNT_BALANCE_READ
    );
    private static final Set<String> TRANSIENT_REASONS = Set.of(
            OkxPrivateReadError.NETWORK_IO_ERROR.name(),
            OkxPrivateReadError.NETWORK_TIMEOUT.name(),
            OkxPrivateReadError.HTTP_RATE_LIMITED.name(),
            OkxPrivateReadError.HTTP_SERVER_ERROR.name(),
            OkxPrivateReadError.HTTP_UNEXPECTED_STATUS.name(),
            OkxPrivateReadError.OKX_BUSINESS_REJECTED.name(),
            OkxPrivateReadError.NETWORK_FAILURE.name(),
            OkxPrivateReadError.TIMEOUT.name(),
            OkxPrivateReadError.RATE_LIMITED.name(),
            OkxPrivateReadError.HTTP_ERROR.name(),
            OkxPrivateReadError.OKX_PROVIDER_ERROR.name()
    );
    private static final Set<String> AUTH_REASONS = Set.of(
            OkxPrivateReadError.HTTP_UNAUTHORIZED.name(),
            OkxPrivateReadError.HTTP_FORBIDDEN.name(),
            OkxPrivateReadError.OKX_AUTHENTICATION_FAILED.name(),
            OkxPrivateReadError.OKX_SIGNATURE_INVALID.name(),
            OkxPrivateReadError.OKX_TIMESTAMP_INVALID.name(),
            OkxPrivateReadError.AUTHENTICATION_FAILURE.name(),
            OkxPrivateReadError.SIGNATURE_FAILURE.name()
    );
    private static final Set<String> BLOCKED_REASONS = Set.of(
            OkxPrivateReadError.PERMISSION_BLOCKED.name(),
            OkxPrivateReadError.IP_ALLOWLIST_FAILED.name(),
            OkxPrivateReadError.OKX_PERMISSION_DENIED.name(),
            OkxPrivateReadError.OKX_IP_NOT_ALLOWED.name(),
            OkxPrivateReadError.ACCOUNT_SCOPE_MISMATCH.name(),
            OkxPrivateReadError.ENVIRONMENT_MISMATCH.name(),
            OkxPrivateReadError.CREDENTIAL_UNAVAILABLE.name(),
            OkxPrivateReadError.CREDENTIAL_CONFLICT.name()
    );
    private static final Set<String> SOAK_CONTROL_TABLES = Set.of(
            "flyway_schema_history",
            "users",
            "roles",
            "user_roles",
            "exchange_accounts",
            "exchange_account_credentials",
            "credential_audit_logs",
            "kill_switch_states",
            "kill_switch_events"
    );
    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern SAFE_CYCLE_ID = Pattern.compile("gatew-cycle-[a-f0-9]{32}");
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("gatew-soak-[a-f0-9-]{36}");
    private static final Pattern SAFE_CLASSIFICATION = Pattern.compile("[A-Z][A-Z0-9_]{1,95}");
    private static final Pattern SAFE_RUN_ID = Pattern.compile("gatew-soak-[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}");
    static final ObjectMapper STANDALONE_OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 正式 systemd worker 的固定 Java 入口。部署阶段已离线编译并冻结 classpath；运行阶段
     * 不再调用 Maven，也不写 repository target。所有配置仍由既有 closed launcher contract 校验。
     *
     * @param args 不接受命令行参数；业务参数只允许来自固定 system properties 与 systemd environment
     */
    public static void main(String[] args) {
        if (args.length != 0 || !"true".equals(System.getProperty(REQUIRED_PROPERTY))) {
            throw new IllegalStateException("GateW soak standalone launcher is not authorized");
        }
        GateWOkxReadonlySoakCycleTest launcher = new GateWOkxReadonlySoakCycleTest();
        CycleResult result = launcher.executeOneSanitizedAction(STANDALONE_OBJECT_MAPPER);
        if (!result.schemaSafe()) {
            throw new IllegalStateException("GateW soak standalone result is outside the closed schema");
        }
    }

    /**
     * 正式 worker 的脱敏 prerequisite 入口。只读取 systemd 注入的 DB password 与治理元数据；
     * 不读取 credential master key、encrypted_payload 或任何交易所 credential material。
     */
    public static final class PrerequisiteMain {
        private PrerequisiteMain() {
        }

        public static void main(String[] args) {
            String action = System.getProperty(ACTION_PROPERTY);
            if (args.length != 0 || !"true".equals(System.getProperty(REQUIRED_PROPERTY))
                    || !("prerequisite".equals(action) || "precreate-prerequisite".equals(action))) {
                throw new IllegalStateException("GateW prerequisite launcher is not authorized");
            }
            SafetyConfig config = SafetyConfig.from(System.getenv(), System.getProperties());
            PrerequisiteReadback result;
            try {
                config.assertSafe();
                DriverManagerDataSource dataSource = new DriverManagerDataSource();
                dataSource.setDriverClassName("org.postgresql.Driver");
                dataSource.setUrl(config.databaseUrl());
                dataSource.setUsername(config.databaseUser());
                dataSource.setPassword(config.databasePassword());
                Properties connectionProperties = new Properties();
                connectionProperties.setProperty("connectTimeout", "2");
                connectionProperties.setProperty("socketTimeout", "3");
                connectionProperties.setProperty("loginTimeout", "3");
                connectionProperties.setProperty("ApplicationName", "nq-gatew-precreate-prerequisite");
                dataSource.setConnectionProperties(connectionProperties);
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                jdbc.setQueryTimeout(3);
                result = readPrerequisite(
                        jdbc,
                        config,
                        () -> managementHealthy(config.managementHealthUrl())
                );
            } catch (RuntimeException ex) {
                result = PrerequisiteReadback.unavailable(classifyPrerequisiteFailure(ex));
            }
            writePrerequisiteResult(config, result, STANDALONE_OBJECT_MAPPER);
            if (!result.ready()) {
                throw new IllegalStateException("GateW prerequisite readback is fail-closed");
            }
        }
    }

    @Test
    void executeOneSanitizedAction() {
        CycleResult result = executeOneSanitizedAction(objectMapper);
        assertTrue(result.schemaSafe(), "cycle result must remain within the sanitized evidence schema");
    }

    private CycleResult executeOneSanitizedAction(ObjectMapper managedObjectMapper) {
        SafetyConfig config = SafetyConfig.from(System.getenv(), System.getProperties());
        CycleResult result;
        try {
            config.assertSafe();
            result = execute(config, managedObjectMapper);
        } catch (SafeBlockException ex) {
            result = CycleResult.blocked(ex.reasonCode(), ex.permissionClassification());
        } catch (RuntimeException ex) {
            // JDBC/Flyway/Jackson/provider cause 可能携带本地连接或 payload 片段，不把 cause 带入 test 日志。
            result = CycleResult.failed("SOAK_INTERNAL_FAILURE", "UNKNOWN");
        }
        writeSanitizedResult(config, result, managedObjectMapper);
        return result;
    }

    private CycleResult execute(SafetyConfig config, ObjectMapper managedObjectMapper) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(config.databaseUrl());
        dataSource.setUsername(config.databaseUser());
        dataSource.setPassword(config.databasePassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway flyway = Flyway.configure()
                .dataSource(config.databaseUrl(), config.databaseUser(), config.databasePassword())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load();
        if ("bootstrap".equals(config.action())) {
            flyway.migrate();
        }
        flyway.validate();
        MigrationInfo current = flyway.info().current();
        if (current == null || !"35".equals(current.getVersion().getVersion())
                || flyway.info().pending().length != 0) {
            throw new SafeBlockException("SOAK_SCHEMA_NOT_AT_V35");
        }

        assertLocalServer(jdbc, config.databaseName());
        assertNoBusinessData(jdbc);
        if ("engage".equals(config.action())) {
            return engage(jdbc);
        }
        return switch (config.action()) {
            case "bootstrap" -> bootstrap(jdbc, config);
            case "sample" -> {
                CredentialGate credential = credentialGate(jdbc, config);
                credential.assertSafe();
                yield sample(jdbc, config, managedObjectMapper);
            }
            default -> throw new SafeBlockException("SOAK_ACTION_INVALID");
        };
    }

    private CycleResult bootstrap(JdbcTemplate jdbc, SafetyConfig config) {
        KillSwitchService killSwitchService = new KillSwitchService(
                new JdbcKillSwitchStateRepository(jdbc),
                Clock.systemUTC()
        );
        KillSwitchStatus requiredStatus;
        if (REAL_READONLY_SOAK.equals(config.runMode())) {
            requiredStatus = KillSwitchStatus.ENGAGED;
        } else {
            prepareOfflineIsolatedFixture(jdbc);
            requiredStatus = KillSwitchStatus.DISENGAGED;
        }
        KillSwitchSnapshot snapshot = killSwitchService.snapshot();
        if (snapshot.status() != requiredStatus) {
            throw new SafeBlockException(requiredStatus == KillSwitchStatus.ENGAGED
                    ? "KILL_SWITCH_NOT_ENGAGED"
                    : "KILL_SWITCH_NOT_DISENGAGED");
        }
        assertNoBusinessData(jdbc);
        return new CycleResult(
                LAUNCHER_SCHEMA_VERSION,
                safeCycleId(),
                Instant.now(),
                0,
                "BOOTSTRAP_READY",
                "SOAK_ISOLATION_READY",
                "NOT_CALLED",
                "METADATA_READ_ONLY",
                requiredStatus.name(),
                false,
                false,
                "NONE",
                ProbeStatus.NOT_RUN,
                ProbeStatus.NOT_RUN,
                safeTraceId()
        );
    }

    private CycleResult sample(
            JdbcTemplate jdbc,
            SafetyConfig config,
            ObjectMapper managedObjectMapper
    ) {
        Instant startedAt = Instant.now();
        KillSwitchService killSwitchService = new KillSwitchService(
                new JdbcKillSwitchStateRepository(jdbc),
                Clock.systemUTC()
        );
        KillSwitchSnapshot before = killSwitchService.snapshot();
        KillSwitchStatus requiredStatus = REAL_READONLY_SOAK.equals(config.runMode())
                ? KillSwitchStatus.ENGAGED
                : KillSwitchStatus.DISENGAGED;
        if (before.status() != requiredStatus) {
            throw new SafeBlockException(requiredStatus == KillSwitchStatus.ENGAGED
                    ? "KILL_SWITCH_NOT_ENGAGED"
                    : "KILL_SWITCH_NOT_DISENGAGED");
        }

        CountingTransport transport = new CountingTransport(
                new JdkOkxPrivateReadTransport(managedObjectMapper, Clock.systemUTC())
        );
        OkxPrivateReadonlyProbeService service = new OkxPrivateReadonlyProbeService(
                new JdbcExchangeAccountRepository(jdbc),
                new JdbcOkxPrivateCredentialExecutor(
                        jdbc,
                        managedObjectMapper,
                        config.masterKey(),
                        transport
                ),
                killSwitchService,
                Clock.systemUTC()
        );

        try {
            OkxPrivateReadObservation observation = requiredStatus == KillSwitchStatus.ENGAGED
                    ? service.probeWhileKillSwitchEngaged(
                    config.ownerId(),
                    config.exchangeAccountId(),
                    JdbcOkxPrivateCredentialExecutor.OKX_API_V5,
                    OkxPrivateEnvironment.PRODUCTION,
                    config.currencies()
            )
                    : service.probe(
                    config.ownerId(),
                    config.exchangeAccountId(),
                    JdbcOkxPrivateCredentialExecutor.OKX_API_V5,
                    OkxPrivateEnvironment.PRODUCTION,
                    config.currencies()
            );
            assertNoBusinessData(jdbc);
            KillSwitchSnapshot after = killSwitchService.snapshot();
            if (after.status() != requiredStatus || after.version() != before.version()) {
                return cycleResult(
                        "BLOCKED",
                        "KILL_SWITCH_CHANGED_DURING_SAMPLE",
                        "NOT_AVAILABLE",
                        "UNKNOWN",
                        after.status().name(),
                        transport,
                        startedAt
                );
            }

            String reason = primaryReason(observation);
            boolean passed = observation.probeStatus() == OkxPrivateProbeStatus.PASSED_READ_ONLY
                    && Set.of("READ_ONLY").equals(observation.normalizedPermissions())
                    && observation.ipAllowlistConfigured()
                    && observation.noSideEffect()
                    && observation.liveDisabled()
                    && !observation.tradingAuthorization()
                    && !observation.orderSubmitted()
                    && transport.operations().equals(List.of(
                    OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ,
                    OkxPrivateReadOperation.OKX_ACCOUNT_BALANCE_READ
            ));
            String resultStatus = passed
                    ? "PASSED_READ_ONLY"
                    : TRANSIENT_REASONS.contains(reason) || AUTH_REASONS.contains(reason)
                    ? "TRANSIENT_FAILURE"
                    : BLOCKED_REASONS.contains(reason) ? "BLOCKED" : "HARD_FAILURE";
            String permissionClassification = passed
                    ? "READ_ONLY_WITH_IP_ALLOWLIST"
                    : observation.normalizedPermissions().isEmpty() ? "UNKNOWN" : "UNSAFE_OR_INCOMPLETE";

            return cycleResult(
                    resultStatus,
                    passed ? "READ_ONLY_SAMPLE_ACCEPTED" : reason,
                    httpStatusCategory(reason, passed),
                    permissionClassification,
                    after.status().name(),
                    transport,
                    startedAt
            );
        } catch (SafeBlockException ex) {
            return cycleResult(
                    "BLOCKED",
                    ex.reasonCode(),
                    "NOT_AVAILABLE",
                    ex.permissionClassification(),
                    before.status().name(),
                    transport,
                    startedAt
            );
        } catch (RuntimeException ex) {
            // provider/JDBC/Jackson cause 不进入 launcher DTO；只保留固定失败分类和已观测 typed operation 状态。
            return cycleResult(
                    "FAILED",
                    "SOAK_INTERNAL_FAILURE",
                    "NOT_AVAILABLE",
                    "UNKNOWN",
                    before.status().name(),
                    transport,
                    startedAt
            );
        }
    }

    private static CycleResult cycleResult(
            String resultStatus,
            String reasonCode,
            String httpStatusCategory,
            String permissionClassification,
            String killSwitchObservedState,
            CountingTransport transport,
            Instant startedAt
    ) {
        return new CycleResult(
                LAUNCHER_SCHEMA_VERSION,
                safeCycleId(),
                Instant.now(),
                Math.max(0, Duration.between(startedAt, Instant.now()).toMillis()),
                resultStatus,
                reasonCode,
                httpStatusCategory,
                permissionClassification,
                killSwitchObservedState,
                transport.calls() > 0,
                transport.calls() > 0,
                transport.endpointCategory(),
                transport.accountConfigProbeStatus(),
                transport.balanceProbeStatus(),
                safeTraceId()
        );
    }

    private CycleResult engage(JdbcTemplate jdbc) {
        KillSwitchService service = new KillSwitchService(new JdbcKillSwitchStateRepository(jdbc), Clock.systemUTC());
        KillSwitchSnapshot current = service.snapshot();
        KillSwitchSnapshot engaged = current.status() == KillSwitchStatus.ENGAGED
                ? current
                : service.engage(current.version(), "SOAK_STOP", "GATEW_SOAK_SUPERVISOR", safeTraceId());
        if (engaged.status() != KillSwitchStatus.ENGAGED) {
            throw new SafeBlockException("KILL_SWITCH_ENGAGE_FAILED");
        }
        return new CycleResult(
                LAUNCHER_SCHEMA_VERSION,
                safeCycleId(),
                Instant.now(),
                0,
                "ENGAGED",
                "SOAK_STOPPED_FAIL_CLOSED",
                "NOT_CALLED",
                "METADATA_READ_ONLY",
                engaged.status().name(),
                false,
                false,
                "NONE",
                ProbeStatus.NOT_RUN,
                ProbeStatus.NOT_RUN,
                safeTraceId()
        );
    }

    private static CredentialGate credentialGate(JdbcTemplate jdbc, SafetyConfig config) {
        List<CredentialGate> rows = jdbc.query(
                """
                                SELECT c.permission_scope, c.withdraw_enabled, c.ip_allowlist_required,
                                       c.permission_probe_status, c.ip_allowlist_probe_status,
                                       a.exchange_code, a.trade_env, a.status
                                FROM exchange_account_credentials c
                                JOIN exchange_accounts a ON a.exchange_account_id = c.exchange_account_id
                                WHERE a.owner_user_id = ?
                                  AND a.exchange_account_id = ?
                                  AND a.exchange_code = 'OKX'
                                  AND a.trade_env = 'LIVE'
                                  AND a.status = 'ACTIVE'
                                  AND c.credential_type = 'OKX_API_V5'
                                  AND c.is_active = TRUE
                                  AND c.credential_status = 'ACTIVE'
                                  AND c.revoked_at IS NULL
                                  AND c.rotated_at IS NULL
                        """,
                (resultSet, rowNum) -> new CredentialGate(
                        resultSet.getString("permission_scope"),
                        resultSet.getBoolean("withdraw_enabled"),
                        resultSet.getBoolean("ip_allowlist_required"),
                        resultSet.getString("permission_probe_status"),
                        resultSet.getString("ip_allowlist_probe_status"),
                        resultSet.getString("exchange_code"),
                        resultSet.getString("trade_env"),
                        resultSet.getString("status")
                ),
                config.ownerId(),
                config.exchangeAccountId()
        );
        if (rows.isEmpty()) {
            throw new SafeBlockException("API_KEY_REQUIRED");
        }
        if (rows.size() != 1) {
            throw new SafeBlockException("CREDENTIAL_CONFLICT");
        }
        return rows.getFirst();
    }

    private static void assertLocalServer(JdbcTemplate jdbc, String expectedDatabase) {
        String actualDatabase = jdbc.queryForObject("SELECT current_database()", String.class);
        Boolean loopback = jdbc.queryForObject(
                """
                        SELECT inet_server_addr() IS NULL
                            OR inet_server_addr() <<= '127.0.0.0/8'::inet
                            OR inet_server_addr() = '::1'::inet
                        """,
                Boolean.class
        );
        if (!expectedDatabase.equals(actualDatabase) || !Boolean.TRUE.equals(loopback)) {
            throw new SafeBlockException("SOAK_DATABASE_NOT_LOCAL");
        }
    }

    private static void assertNoBusinessData(JdbcTemplate jdbc) {
        List<String> tables = jdbc.queryForList(
                """
                                SELECT table_name
                                FROM information_schema.tables
                                WHERE table_schema = current_schema()
                                  AND table_type = 'BASE TABLE'
                                ORDER BY table_name
                        """,
                String.class
        );
        List<String> existenceChecks = new ArrayList<>();
        for (String table : tables) {
            if (SOAK_CONTROL_TABLES.contains(table)) continue;
            if (!SAFE_TABLE_NAME.matcher(table).matches()) {
                throw new SafeBlockException("SOAK_DATABASE_TABLE_NAME_UNSAFE");
            }
            existenceChecks.add("EXISTS (SELECT 1 FROM " + table + " LIMIT 1)");
        }
        if (existenceChecks.isEmpty()) return;
        Boolean containsBusinessData = jdbc.queryForObject(
                "SELECT " + String.join(" OR ", existenceChecks),
                Boolean.class
        );
        if (!Boolean.FALSE.equals(containsBusinessData)) {
            throw new SafeBlockException("SOAK_DATABASE_CONTAINS_BUSINESS_DATA");
        }
    }

    private static long prepareOfflineIsolatedFixture(JdbcTemplate jdbc) {
        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource()))
        );
        Long version = transaction.execute(status -> {
            Map<String, Object> state = jdbc.queryForMap(
                    "SELECT status, version FROM kill_switch_states WHERE scope = 'GLOBAL_TRADING' FOR UPDATE"
            );
            String currentStatus = Objects.toString(state.get("status"), "UNKNOWN");
            long currentVersion = ((Number) state.get("version")).longValue();
            if ("DISENGAGED".equals(currentStatus)) {
                return currentVersion;
            }
            if (!"ENGAGED".equals(currentStatus)) {
                throw new SafeBlockException("SOAK_KILL_SWITCH_FIXTURE_NOT_SAFE");
            }
            long nextVersion = currentVersion + 1;
            Instant now = Instant.now();
            String traceId = safeTraceId();
            int updated = jdbc.update(
                    """
                            UPDATE kill_switch_states
                            SET status = 'DISENGAGED', version = ?, reason_code = 'SOAK_TEST_FIXTURE',
                                source = 'TEST_SUPPORT_FIXTURE', updated_at = ?,
                                updated_by = 'GATEW_SOAK_SUPERVISOR', trace_id = ?
                            WHERE scope = 'GLOBAL_TRADING' AND version = ? AND status = 'ENGAGED'
                            """,
                    nextVersion,
                    java.sql.Timestamp.from(now),
                    traceId,
                    currentVersion
            );
            if (updated != 1) {
                throw new SafeBlockException("SOAK_KILL_SWITCH_FIXTURE_NOT_SAFE");
            }
            jdbc.update(
                    """
                            INSERT INTO kill_switch_events (
                                id, scope, from_status, to_status, state_version, reason_code,
                                source, actor_id, trace_id, occurred_at
                            ) VALUES (?, 'GLOBAL_TRADING', 'ENGAGED', 'DISENGAGED', ?,
                                      'SOAK_TEST_FIXTURE', 'TEST_SUPPORT_FIXTURE',
                                      'GATEW_SOAK_SUPERVISOR', ?, ?)
                            """,
                    UUID.randomUUID(),
                    nextVersion,
                    traceId,
                    java.sql.Timestamp.from(now)
            );
            return nextVersion;
        });
        if (version == null) {
            throw new SafeBlockException("SOAK_KILL_SWITCH_FIXTURE_NOT_SAFE");
        }
        return version;
    }

    private static String primaryReason(OkxPrivateReadObservation observation) {
        if (observation.blockers() != null && !observation.blockers().isEmpty()) {
            return observation.blockers().getFirst();
        }
        if (observation.warnings() != null && !observation.warnings().isEmpty()) {
            return observation.warnings().getFirst();
        }
        return observation.probeStatus() == OkxPrivateProbeStatus.PASSED_READ_ONLY
                ? "READ_ONLY_SAMPLE_ACCEPTED"
                : "UNKNOWN_PROBE_RESULT";
    }

    private static String httpStatusCategory(String reason, boolean passed) {
        if (passed) return "SUCCESS_2XX";
        return switch (reason) {
            case "RATE_LIMITED", "HTTP_RATE_LIMITED" -> "RATE_LIMITED_429";
            case "HTTP_ERROR", "OKX_PROVIDER_ERROR", "HTTP_SERVER_ERROR",
                 "HTTP_UNEXPECTED_STATUS", "OKX_BUSINESS_REJECTED" -> "EXCHANGE_ERROR";
            case "AUTHENTICATION_FAILURE", "SIGNATURE_FAILURE", "HTTP_UNAUTHORIZED", "HTTP_FORBIDDEN",
                 "OKX_AUTHENTICATION_FAILED", "OKX_SIGNATURE_INVALID", "OKX_TIMESTAMP_INVALID",
                 "IP_ALLOWLIST_FAILED", "OKX_IP_NOT_ALLOWED", "OKX_PERMISSION_DENIED" -> "AUTH_ERROR";
            case "NETWORK_FAILURE", "TIMEOUT", "NETWORK_IO_ERROR", "NETWORK_TIMEOUT" -> "NETWORK_ERROR";
            default -> "NOT_AVAILABLE";
        };
    }

    private static String safeTraceId() {
        return "gatew-soak-" + UUID.randomUUID();
    }

    private static String safeCycleId() {
        return "gatew-cycle-" + UUID.randomUUID().toString().replace("-", "");
    }

    static PrerequisiteReadback readPrerequisite(
            JdbcTemplate jdbc,
            SafetyConfig config,
            ManagementHealthProbe managementHealthProbe
    ) {
        return readPrerequisite(jdbc, config, managementHealthProbe, Clock.systemUTC());
    }

    static PrerequisiteReadback readPrerequisite(
            JdbcTemplate jdbc,
            SafetyConfig config,
            ManagementHealthProbe managementHealthProbe,
            Clock clock
    ) {
        Objects.requireNonNull(jdbc, "jdbc must not be null");
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(managementHealthProbe, "managementHealthProbe must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        try {
            String killSwitchStatus = jdbc.queryForObject(
                    "SELECT status FROM kill_switch_states WHERE scope = 'GLOBAL_TRADING'",
                    String.class
            );
            boolean preCreate = "precreate-prerequisite".equals(config.action());
            List<Map<String, Object>> accounts;
            List<Map<String, Object>> credentials;
            if (preCreate) {
                accounts = List.of(jdbc.queryForMap(
                        """
                                SELECT
                                    (SELECT COUNT(*) FROM exchange_accounts) AS total_account_count,
                                    (SELECT COUNT(*) FROM exchange_accounts
                                      WHERE exchange_code = 'OKX' AND trade_env = 'LIVE')
                                        AS scoped_account_count,
                                    (SELECT COUNT(*) FROM exchange_accounts
                                      WHERE exchange_code = 'OKX' AND trade_env = 'LIVE'
                                        AND status = 'ACTIVE') AS active_scoped_account_count,
                                    COUNT(*) FILTER (
                                      WHERE a.exchange_code = 'OKX' AND a.trade_env = 'LIVE'
                                    ) AS configured_credential_count,
                                    COUNT(*) FILTER (
                                      WHERE a.exchange_code = 'OKX' AND a.trade_env = 'LIVE'
                                        AND a.status = 'ACTIVE'
                                        AND c.is_active = TRUE
                                        AND c.revoked_at IS NULL
                                        AND c.rotated_at IS NULL
                                    ) AS active_credential_count,
                                    MAX(c.credential_type) FILTER (
                                      WHERE a.exchange_code = 'OKX' AND a.trade_env = 'LIVE'
                                        AND a.status = 'ACTIVE'
                                        AND c.is_active = TRUE
                                        AND c.revoked_at IS NULL
                                        AND c.rotated_at IS NULL
                                    ) AS credential_type,
                                    MAX(c.credential_status) FILTER (
                                      WHERE a.exchange_code = 'OKX' AND a.trade_env = 'LIVE'
                                        AND a.status = 'ACTIVE'
                                        AND c.is_active = TRUE
                                        AND c.revoked_at IS NULL
                                        AND c.rotated_at IS NULL
                                    ) AS credential_status,
                                    MAX(c.permission_scope) FILTER (
                                      WHERE a.exchange_code = 'OKX' AND a.trade_env = 'LIVE'
                                        AND a.status = 'ACTIVE'
                                        AND c.is_active = TRUE
                                        AND c.revoked_at IS NULL
                                        AND c.rotated_at IS NULL
                                    ) AS permission_scope,
                                    BOOL_OR(c.withdraw_enabled) FILTER (
                                      WHERE a.exchange_code = 'OKX' AND a.trade_env = 'LIVE'
                                        AND a.status = 'ACTIVE'
                                        AND c.is_active = TRUE
                                        AND c.revoked_at IS NULL
                                        AND c.rotated_at IS NULL
                                    ) AS withdraw_enabled,
                                    BOOL_OR(c.ip_allowlist_required) FILTER (
                                      WHERE a.exchange_code = 'OKX' AND a.trade_env = 'LIVE'
                                        AND a.status = 'ACTIVE'
                                        AND c.is_active = TRUE
                                        AND c.revoked_at IS NULL
                                        AND c.rotated_at IS NULL
                                    ) AS ip_allowlist_required,
                                    MAX(c.permission_probe_status) FILTER (
                                      WHERE a.exchange_code = 'OKX' AND a.trade_env = 'LIVE'
                                        AND a.status = 'ACTIVE'
                                        AND c.is_active = TRUE
                                        AND c.revoked_at IS NULL
                                        AND c.rotated_at IS NULL
                                    ) AS permission_probe_status,
                                    MAX(c.last_permission_probe_at) FILTER (
                                      WHERE a.exchange_code = 'OKX' AND a.trade_env = 'LIVE'
                                        AND a.status = 'ACTIVE'
                                        AND c.is_active = TRUE
                                        AND c.revoked_at IS NULL
                                        AND c.rotated_at IS NULL
                                    ) AS last_permission_probe_at,
                                    MAX(c.ip_allowlist_probe_status) FILTER (
                                      WHERE a.exchange_code = 'OKX' AND a.trade_env = 'LIVE'
                                        AND a.status = 'ACTIVE'
                                        AND c.is_active = TRUE
                                        AND c.revoked_at IS NULL
                                        AND c.rotated_at IS NULL
                                    ) AS ip_allowlist_probe_status,
                                    MAX(GREATEST(c.updated_at, a.updated_at)) FILTER (
                                      WHERE a.exchange_code = 'OKX' AND a.trade_env = 'LIVE'
                                        AND a.status = 'ACTIVE'
                                        AND c.is_active = TRUE
                                        AND c.revoked_at IS NULL
                                        AND c.rotated_at IS NULL
                                    ) AS metadata_updated_at
                                FROM exchange_account_credentials c
                                JOIN exchange_accounts a
                                  ON a.exchange_account_id = c.exchange_account_id
                                """
                ));
                credentials = List.of();
            } else {
                accounts = jdbc.queryForList(
                        """
                                SELECT exchange_code, trade_env, status
                                FROM exchange_accounts
                                WHERE owner_user_id = ? AND exchange_account_id = ?
                                """,
                        config.ownerId(),
                        config.exchangeAccountId()
                );
                credentials = jdbc.queryForList(
                        """
                                SELECT credential_type, credential_status, permission_scope, withdraw_enabled
                                FROM exchange_account_credentials
                                WHERE exchange_account_id = ?
                                  AND is_active = TRUE
                                  AND credential_status = 'ACTIVE'
                                ORDER BY credential_id
                                """,
                        config.exchangeAccountId()
                );
            }

            if (preCreate) {
                Map<String, Object> summary = accounts.getFirst();
                long totalAccountCount = longValue(summary.get("total_account_count"));
                long scopedAccountCount = longValue(summary.get("scoped_account_count"));
                long activeScopedAccountCount = longValue(summary.get("active_scoped_account_count"));
                long configuredCredentialCount = longValue(summary.get("configured_credential_count"));
                int activeCredentialCount = Math.toIntExact(longValue(summary.get("active_credential_count")));
                boolean credentialConfigured = configuredCredentialCount > 0;
                String rawCredentialType = Objects.toString(summary.get("credential_type"), "UNKNOWN");
                String credentialType = activeCredentialCount == 1 && "OKX_API_V5".equals(rawCredentialType)
                        ? rawCredentialType : activeCredentialCount > 1 ? "CONFLICT" : "UNKNOWN";
                String rawCredentialStatus = Objects.toString(summary.get("credential_status"), "UNKNOWN");
                String credentialLocalStatus = activeCredentialCount > 1 ? "CONFLICT"
                        : Set.of("ACTIVE", "DISABLED", "REVOKED", "EXPIRED", "ROTATED")
                        .contains(rawCredentialStatus) ? rawCredentialStatus : "UNKNOWN";
                String permissionScope = Objects.toString(summary.get("permission_scope"), "");
                String permissionProbeStatus =
                        Objects.toString(summary.get("permission_probe_status"), "NOT_PROBED");
                Instant permissionProbeAt = instantValue(summary.get("last_permission_probe_at"));
                Instant metadataUpdatedAt = instantValue(summary.get("metadata_updated_at"));
                boolean permissionFactPresent = !"NOT_PROBED".equals(permissionProbeStatus)
                        && permissionProbeAt != null;
                boolean permissionFactFresh = permissionFactPresent
                        && metadataUpdatedAt != null
                        && !permissionProbeAt.isBefore(metadataUpdatedAt)
                        && !permissionProbeAt.isAfter(clock.instant().plus(Duration.ofMinutes(5)));
                boolean readPermissionVerified = permissionFactFresh
                        && "SUCCEEDED".equals(permissionProbeStatus)
                        && "READ_ONLY".equals(permissionScope);
                boolean tradeDisabled = "READ_ONLY".equals(permissionScope);
                boolean withdrawDisabled = !Boolean.TRUE.equals(summary.get("withdraw_enabled"));
                boolean ipAllowlistVerified = Boolean.TRUE.equals(summary.get("ip_allowlist_required"))
                        && "PASSED".equals(Objects.toString(summary.get("ip_allowlist_probe_status"), ""));
                String readPermissionStatus = readPermissionVerified ? "VERIFIED"
                        : permissionFactPresent ? "NOT_VERIFIED" : "UNKNOWN";
                String ipAllowlistStatus = ipAllowlistVerified ? "VERIFIED"
                        : "FAILED".equals(Objects.toString(summary.get("ip_allowlist_probe_status"), ""))
                        ? "FAILED" : "UNKNOWN";
                boolean localManagementHealthy;
                try {
                    localManagementHealthy = managementHealthProbe.isHealthy();
                } catch (RuntimeException ex) {
                    localManagementHealthy = false;
                }
                List<String> blockers = new ArrayList<>();
                if (!"ENGAGED".equals(killSwitchStatus)) blockers.add("KILL_SWITCH_NOT_ENGAGED");
                if (!localManagementHealthy) blockers.add("MANAGEMENT_UNREACHABLE");
                if ((totalAccountCount > 0 && scopedAccountCount == 0) || scopedAccountCount > 1
                        || (scopedAccountCount == 1 && activeScopedAccountCount != 1)) {
                    blockers.add("ACCOUNT_SCOPE_MISMATCH");
                } else if (!credentialConfigured) {
                    blockers.add("CREDENTIAL_NOT_CONFIGURED");
                } else if (activeCredentialCount != 1) {
                    blockers.add("ACTIVE_CREDENTIAL_COUNT_INVALID");
                } else {
                    if (!"OKX_API_V5".equals(rawCredentialType)) blockers.add("CREDENTIAL_TYPE_MISMATCH");
                    if (!"ACTIVE".equals(rawCredentialStatus)) {
                        blockers.add("CREDENTIAL_LOCAL_STATUS_NOT_ACTIVE");
                    }
                    if (!permissionFactPresent) blockers.add("PERMISSION_FACT_MISSING");
                    else if (!permissionFactFresh) blockers.add("PERMISSION_FACT_STALE");
                    else if (!readPermissionVerified) blockers.add("READ_PERMISSION_NOT_VERIFIED");
                    if (!tradeDisabled) blockers.add("TRADE_PERMISSION_NOT_DISABLED");
                    if (!withdrawDisabled) blockers.add("WITHDRAW_PERMISSION_NOT_DISABLED");
                    if (!ipAllowlistVerified) blockers.add("IP_ALLOWLIST_NOT_VERIFIED");
                }
                return new PrerequisiteReadback(
                        "ENGAGED".equals(killSwitchStatus),
                        credentialConfigured,
                        activeCredentialCount,
                        credentialType,
                        credentialLocalStatus,
                        permissionFactPresent,
                        permissionFactFresh,
                        readPermissionStatus,
                        tradeDisabled,
                        withdrawDisabled,
                        ipAllowlistStatus,
                        true,
                        localManagementHealthy,
                        List.copyOf(blockers),
                        safePrerequisiteDiagnosticId()
                );
            }

            boolean singleCredential = credentials.size() == 1;
            Map<String, Object> credential = singleCredential ? credentials.getFirst() : Map.of();
            boolean accountHealthy = preCreate
                    ? singleCredential
                    && "OKX".equals(Objects.toString(credential.get("exchange_code"), ""))
                    && "LIVE".equals(Objects.toString(credential.get("trade_env"), ""))
                    && "ACTIVE".equals(Objects.toString(credential.get("status"), ""))
                    : accounts.size() == 1
                    && "OKX".equals(Objects.toString(accounts.getFirst().get("exchange_code"), ""))
                    && "LIVE".equals(Objects.toString(accounts.getFirst().get("trade_env"), ""))
                    && "ACTIVE".equals(Objects.toString(accounts.getFirst().get("status"), ""));
            String credentialType = singleCredential
                    ? Objects.toString(credential.get("credential_type"), "UNKNOWN")
                    : credentials.isEmpty() ? "UNKNOWN" : "CONFLICT";
            String credentialLocalStatus = singleCredential
                    ? Objects.toString(credential.get("credential_status"), "UNKNOWN")
                    : credentials.isEmpty() ? "UNKNOWN" : "CONFLICT";
            boolean tradeDisabled = singleCredential
                    && "READ_ONLY".equals(Objects.toString(credential.get("permission_scope"), ""));
            boolean withdrawDisabled = singleCredential
                    && Boolean.FALSE.equals(credential.get("withdraw_enabled"));
            boolean localManagementHealthy;
            try {
                localManagementHealthy = managementHealthProbe.isHealthy();
            } catch (RuntimeException ex) {
                localManagementHealthy = false;
            }
            return new PrerequisiteReadback(
                    "ENGAGED".equals(killSwitchStatus),
                    !credentials.isEmpty() && accountHealthy,
                    credentials.size(),
                    credentialType,
                    credentialLocalStatus,
                    true,
                    true,
                    tradeDisabled ? "VERIFIED" : "NOT_VERIFIED",
                    tradeDisabled,
                    withdrawDisabled,
                    "VERIFIED",
                    true,
                    localManagementHealthy,
                    List.of(),
                    safePrerequisiteDiagnosticId()
            );
        } catch (RuntimeException ex) {
            return PrerequisiteReadback.unavailable(classifyPrerequisiteFailure(ex));
        }
    }

    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static Instant instantValue(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        return null;
    }

    private static String classifyPrerequisiteFailure(RuntimeException ex) {
        return ex instanceof DataAccessResourceFailureException
                ? "POSTGRES_UNREACHABLE"
                : "INTERNAL_SANITIZED_READBACK_FAILURE";
    }

    private static String safePrerequisiteDiagnosticId() {
        return "gatew-precreate-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static boolean managementHealthy(String healthUrl) {
        HttpURLConnection connection = null;
        try {
            if (!"http://127.0.0.1:18889/actuator/health".equals(healthUrl)) return false;
            connection = (HttpURLConnection) URI.create(healthUrl)
                    .toURL()
                    .openConnection();
            connection.setConnectTimeout(2_000);
            connection.setReadTimeout(2_000);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200) return false;
            byte[] payload = connection.getInputStream().readNBytes(4_096);
            try {
                String body = new String(payload, StandardCharsets.UTF_8);
                return body.replaceAll("\\s+", "").toUpperCase(Locale.ROOT).contains("\"STATUS\":\"UP\"");
            } finally {
                Arrays.fill(payload, (byte) 0);
            }
        } catch (IOException | RuntimeException ex) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static void writeSanitizedResult(SafetyConfig config, CycleResult result, ObjectMapper managedObjectMapper) {
        byte[] json = null;
        try {
            json = EvidenceSanitizer.serialize(managedObjectMapper, result);
            writeSanitizedPayload(validatedResultOutput(config), json);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to write sanitized cycle result");
        } finally {
            if (json != null) Arrays.fill(json, (byte) 0);
        }
    }

    static void writePrerequisiteResult(
            SafetyConfig config,
            PrerequisiteReadback result,
            ObjectMapper managedObjectMapper
    ) {
        byte[] json = null;
        try {
            PrerequisiteReadback.validate(result);
            json = managedObjectMapper.writeValueAsBytes(result);
            JsonNode tree = managedObjectMapper.readTree(json);
            List<String> fields = new ArrayList<>();
            tree.fieldNames().forEachRemaining(fields::add);
            if (!fields.equals(PrerequisiteReadback.FIELDS)
                    || new String(json, StandardCharsets.UTF_8).matches(
                    "(?is).*(https?://|api[-_]?key|passphrase|signature|encrypted[_-]?payload|jdbc[^\\\"]*password).*"
            )) {
                throw new IllegalArgumentException("prerequisite readback is outside the closed schema");
            }
            writeSanitizedPayload(validatedResultOutput(config), json);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to write sanitized prerequisite result");
        } finally {
            if (json != null) Arrays.fill(json, (byte) 0);
        }
    }

    private static void writeSanitizedPayload(Path output, byte[] json) throws IOException {
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            if (Files.getFileStore(output.getParent()).supportsFileAttributeView("posix")) {
                Files.createFile(
                        temporary,
                        PosixFilePermissions.asFileAttribute(Set.of(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE
                        ))
                );
                Files.write(temporary, json, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                Files.write(temporary, json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (Exception ignored) {
                // 临时文件名与内容均受闭合 DTO 约束；删除失败不覆盖原始 fail-closed 结果。
            }
        }
    }

    /**
     * 仅允许真实 soak 与 offline smoke 的 canonical run root；lexical path 与 real path 必须同时留在边界内。
     */
    private static Path validatedResultOutput(SafetyConfig config) throws IOException {
        if ("precreate-prerequisite".equals(config.action())) {
            return validatedPreCreateResultOutput(config);
        }
        if (config.formalEvidenceRoot() != null) {
            return validatedFormalResultOutput(config);
        }
        Path repoRoot = config.repoRoot().toAbsolutePath().normalize();
        Path output = config.resultFile().toAbsolutePath().normalize();
        Path soakRoot = repoRoot.resolve("target").resolve("gatew-okx-readonly-soak").normalize();
        Path runRoot = canonicalResultRunRoot(soakRoot, output);

        Path realRepoRoot = repoRoot.toRealPath();
        Path realSoakRoot = soakRoot.toRealPath();
        Path realRunRoot = runRoot.toRealPath();
        Path outputParent = output.getParent();
        if (outputParent == null) {
            throw new IllegalArgumentException("resultFile must stay below a canonical soak run root");
        }
        Path realOutputParent = outputParent.toRealPath();
        if (!isStrictlyBelow(realSoakRoot, realRepoRoot)
                || !isStrictlyBelow(realRunRoot, realSoakRoot)
                || !(realOutputParent.equals(realRunRoot) || isStrictlyBelow(realOutputParent, realRunRoot))) {
            throw new IllegalArgumentException("resultFile real path escaped a canonical soak run root");
        }
        if (Files.isSymbolicLink(output)) {
            throw new IllegalArgumentException("resultFile symlink is forbidden");
        }
        if (Files.exists(output)) {
            Path realOutput = output.toRealPath();
            if (!isStrictlyBelow(realOutput, realRunRoot)) {
                throw new IllegalArgumentException("resultFile real path escaped a canonical soak run root");
            }
        }
        return output;
    }

    /**
     * Pre-create 入口不创建 run/runtime/evidence 目录，只允许在既有 /run 根下使用随机、短生命周期结果文件。
     */
    private static Path validatedPreCreateResultOutput(SafetyConfig config) throws IOException {
        Path runtimeRoot = Path.of("/run").toAbsolutePath().normalize();
        Path output = config.resultFile().toAbsolutePath().normalize();
        Path parent = output.getParent();
        if (parent == null || !parent.equals(runtimeRoot)
                || !output.getFileName().toString().matches(
                "nq-gatew-precreate-prerequisite-[a-f0-9]{32}\\.json"
        ) || Files.isSymbolicLink(output)
                || !runtimeRoot.toRealPath().equals(parent.toRealPath())) {
            throw new IllegalArgumentException("precreate result path is outside the fixed runtime root");
        }
        return output;
    }

    /**
     * 正式 Linux worker 只能把 launcher DTO 写入当前 run 的 evidence leaf；control 与 terminal
     * 位于 sibling root-only 目录，不能由 resultFile 或环境变量改写。
     */
    private static Path validatedFormalResultOutput(SafetyConfig config) throws IOException {
        Path output = config.resultFile().toAbsolutePath().normalize();
        Path evidenceRoot = config.formalEvidenceRoot().toAbsolutePath().normalize();
        Path stateRoot = Path.of(FORMAL_STATE_ROOT).toAbsolutePath().normalize();
        if (!evidenceRoot.startsWith(stateRoot) || evidenceRoot.equals(stateRoot)) {
            throw new IllegalArgumentException("formal evidence root is outside the fixed state root");
        }
        Path relative = stateRoot.relativize(evidenceRoot);
        if (relative.getNameCount() != 2
                || !SAFE_RUN_ID.matcher(relative.getName(0).toString()).matches()
                || !"evidence".equals(relative.getName(1).toString())
                || !output.startsWith(evidenceRoot)
                || output.equals(evidenceRoot)) {
            throw new IllegalArgumentException("resultFile must stay below the formal evidence root");
        }
        SafetyConfig.assertNoSymbolicLinkComponents(stateRoot);
        SafetyConfig.assertNoSymbolicLinkComponents(evidenceRoot);
        Path realStateRoot = stateRoot.toRealPath();
        Path realEvidenceRoot = evidenceRoot.toRealPath();
        Path outputParent = output.getParent();
        if (outputParent == null) {
            throw new IllegalArgumentException("resultFile must stay below the formal evidence root");
        }
        SafetyConfig.assertNoSymbolicLinkComponents(outputParent);
        Path realOutputParent = outputParent.toRealPath();
        if (!isStrictlyBelow(realEvidenceRoot, realStateRoot)
                || !(realOutputParent.equals(realEvidenceRoot)
                || isStrictlyBelow(realOutputParent, realEvidenceRoot))
                || Files.isSymbolicLink(output)) {
            throw new IllegalArgumentException("resultFile real path escaped the formal evidence root");
        }
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)
                && !isStrictlyBelow(output.toRealPath(), realEvidenceRoot)) {
            throw new IllegalArgumentException("resultFile real path escaped the formal evidence root");
        }
        return output;
    }

    private static Path canonicalResultRunRoot(Path soakRoot, Path output) {
        if (!output.startsWith(soakRoot)) {
            throw new IllegalArgumentException("resultFile must stay below a canonical soak run root");
        }
        Path relative = soakRoot.relativize(output);
        int runIdIndex = relative.getNameCount() > 0 && "offline-smoke".equals(relative.getName(0).toString())
                ? 1
                : 0;
        if (relative.getNameCount() <= runIdIndex + 1) {
            throw new IllegalArgumentException("resultFile must stay below a canonical soak run root");
        }
        String runId = relative.getName(runIdIndex).toString();
        if (!SAFE_RUN_ID.matcher(runId).matches()) {
            throw new IllegalArgumentException("resultFile must stay below a canonical soak run root");
        }
        Path runRoot = runIdIndex == 1
                ? soakRoot.resolve("offline-smoke").resolve(runId).normalize()
                : soakRoot.resolve(runId).normalize();
        if (!output.startsWith(runRoot) || output.equals(runRoot)) {
            throw new IllegalArgumentException("resultFile must stay below a canonical soak run root");
        }
        return runRoot;
    }

    private static boolean isStrictlyBelow(Path candidate, Path root) {
        return candidate.startsWith(root) && !candidate.equals(root);
    }

    /**
     * 固定 launcher DTO 的序列化前后 allowlist/type/backstop 校验。
     */
    static final class EvidenceSanitizer {
        private static final List<String> ALLOWED_FIELDS = List.of(
                "schemaVersion",
                "cycleId",
                "observedAt",
                "durationMs",
                "resultStatus",
                "reasonCode",
                "httpStatusCategory",
                "permissionClassification",
                "killSwitchObservedState",
                "credentialAccessed",
                "networkCalled",
                "allowedEndpointCategory",
                "accountConfigProbeStatus",
                "balanceProbeStatus",
                "traceId"
        );
        private static final Set<String> RESULT_STATUSES = Set.of(
                "BOOTSTRAP_READY", "ENGAGED", "PASSED_READ_ONLY", "BLOCKED",
                "TRANSIENT_FAILURE", "HARD_FAILURE", "FAILED"
        );
        private static final Set<String> HTTP_STATUS_CATEGORIES = Set.of(
                "SUCCESS_2XX", "RATE_LIMITED_429", "EXCHANGE_ERROR", "AUTH_ERROR",
                "NETWORK_ERROR", "NOT_AVAILABLE", "NOT_CALLED"
        );
        private static final Set<String> PERMISSION_CLASSIFICATIONS = Set.of(
                "METADATA_READ_ONLY", "READ_ONLY_WITH_IP_ALLOWLIST", "UNKNOWN",
                "UNSAFE_OR_INCOMPLETE", "UNSAFE_OR_UNKNOWN", "WITHDRAW_ENABLED",
                "READ_ONLY_UNVERIFIED_IP"
        );
        private static final Set<String> KILL_SWITCH_STATES = Set.of("DISENGAGED", "ENGAGED", "UNKNOWN");
        private static final Set<String> ENDPOINT_CATEGORIES = Set.of(
                "NONE", "ACCOUNT_CONFIGURATION_READ", "ACCOUNT_CONFIG_AND_BALANCE_READ",
                "OFFLINE_LOCAL_FIXTURE_READ", "FORBIDDEN_OR_UNKNOWN"
        );

        private EvidenceSanitizer() {
        }

        static byte[] serialize(ObjectMapper managedObjectMapper, CycleResult result) throws Exception {
            Objects.requireNonNull(managedObjectMapper, "managedObjectMapper must not be null");
            validateDto(result);
            byte[] json = managedObjectMapper.writeValueAsBytes(result);
            validateSerializedPayload(managedObjectMapper, json);
            return json;
        }

        static void validateSerializedPayload(ObjectMapper managedObjectMapper, byte[] json) throws Exception {
            Objects.requireNonNull(managedObjectMapper, "managedObjectMapper must not be null");
            Objects.requireNonNull(json, "json must not be null");
            JsonNode root = managedObjectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("launcher evidence must be a JSON object");
            }
            List<String> actualFields = new ArrayList<>();
            Iterator<String> names = root.fieldNames();
            while (names.hasNext()) {
                String field = names.next();
                actualFields.add(field);
                if (!ALLOWED_FIELDS.contains(field)) {
                    throw new IllegalArgumentException("launcher evidence contains an unknown or forbidden field");
                }
            }
            if (!ALLOWED_FIELDS.equals(actualFields)) {
                throw new IllegalArgumentException("launcher evidence field order or completeness is invalid");
            }
            for (String field : ALLOWED_FIELDS) {
                JsonNode value = root.get(field);
                if (value == null || value.isNull() || value.isContainerNode()) {
                    throw new IllegalArgumentException("launcher evidence field type is invalid");
                }
            }
            if (!root.get("durationMs").isIntegralNumber()
                    || !root.get("credentialAccessed").isBoolean()
                    || !root.get("networkCalled").isBoolean()) {
                throw new IllegalArgumentException("launcher evidence scalar type is invalid");
            }
            for (String field : ALLOWED_FIELDS) {
                if (!Set.of("durationMs", "credentialAccessed", "networkCalled").contains(field)
                        && !root.get(field).isTextual()) {
                    throw new IllegalArgumentException("launcher evidence text field type is invalid");
                }
            }
            String serialized = new String(json, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if (serialized.contains("http://") || serialized.contains("https://") || serialized.contains("/api/v5/")) {
                throw new IllegalArgumentException("launcher evidence contains a forbidden network material shape");
            }
            validateDto(managedObjectMapper.treeToValue(root, CycleResult.class));
        }

        static void validateDto(CycleResult result) {
            Objects.requireNonNull(result, "result must not be null");
            if (!LAUNCHER_SCHEMA_VERSION.equals(result.schemaVersion())
                    || result.cycleId() == null || !SAFE_CYCLE_ID.matcher(result.cycleId()).matches()
                    || result.observedAt() == null
                    || result.durationMs() < 0
                    || result.resultStatus() == null || !RESULT_STATUSES.contains(result.resultStatus())
                    || result.reasonCode() == null || !SAFE_CLASSIFICATION.matcher(result.reasonCode()).matches()
                    || result.httpStatusCategory() == null
                    || !HTTP_STATUS_CATEGORIES.contains(result.httpStatusCategory())
                    || result.permissionClassification() == null
                    || !PERMISSION_CLASSIFICATIONS.contains(result.permissionClassification())
                    || result.killSwitchObservedState() == null
                    || !KILL_SWITCH_STATES.contains(result.killSwitchObservedState())
                    || result.allowedEndpointCategory() == null
                    || !ENDPOINT_CATEGORIES.contains(result.allowedEndpointCategory())
                    || result.accountConfigProbeStatus() == null
                    || result.balanceProbeStatus() == null
                    || result.traceId() == null || !SAFE_TRACE_ID.matcher(result.traceId()).matches()) {
                throw new IllegalArgumentException("launcher evidence DTO is outside the fixed contract");
            }
            if ("PASSED_READ_ONLY".equals(result.resultStatus())) {
                boolean realProviderPass = result.credentialAccessed()
                        && result.networkCalled()
                        && KillSwitchStatus.ENGAGED.name().equals(result.killSwitchObservedState())
                        && "ACCOUNT_CONFIG_AND_BALANCE_READ".equals(result.allowedEndpointCategory())
                        && result.accountConfigProbeStatus() == ProbeStatus.SUCCEEDED
                        && result.balanceProbeStatus() == ProbeStatus.SUCCEEDED;
                boolean offlineFixturePass = !result.credentialAccessed()
                        && !result.networkCalled()
                        && KillSwitchStatus.DISENGAGED.name().equals(result.killSwitchObservedState())
                        && "OFFLINE_LOCAL_FIXTURE_READ".equals(result.allowedEndpointCategory())
                        && result.accountConfigProbeStatus() == ProbeStatus.SUCCEEDED
                        && result.balanceProbeStatus() == ProbeStatus.SUCCEEDED
                        && "OFFLINE_READONLY_FIXTURE_ACCEPTED".equals(result.reasonCode());
                if (!realProviderPass && !offlineFixturePass) {
                    throw new IllegalArgumentException("PASS launcher evidence lacks a proven read-only outcome");
                }
            }
            validateEndpointSemantics(result);
        }

        private static void validateEndpointSemantics(CycleResult result) {
            boolean noEndpoint = "NONE".equals(result.allowedEndpointCategory());
            boolean configOnly = "ACCOUNT_CONFIGURATION_READ".equals(result.allowedEndpointCategory());
            boolean configAndBalance = "ACCOUNT_CONFIG_AND_BALANCE_READ".equals(result.allowedEndpointCategory());
            boolean offlineFixture = "OFFLINE_LOCAL_FIXTURE_READ".equals(result.allowedEndpointCategory());
            if (offlineFixture) {
                if (result.credentialAccessed()
                        || result.networkCalled()
                        || result.accountConfigProbeStatus() != ProbeStatus.SUCCEEDED
                        || result.balanceProbeStatus() != ProbeStatus.SUCCEEDED) {
                    throw new IllegalArgumentException("offline fixture evidence has unsafe provenance");
                }
                return;
            }
            if (noEndpoint && (result.credentialAccessed()
                    || result.networkCalled()
                    || result.accountConfigProbeStatus() != ProbeStatus.NOT_RUN
                    || result.balanceProbeStatus() != ProbeStatus.NOT_RUN)) {
                throw new IllegalArgumentException("no-endpoint launcher evidence contains an impossible probe outcome");
            }
            if (!noEndpoint && (!result.credentialAccessed() || !result.networkCalled())) {
                throw new IllegalArgumentException("endpoint launcher evidence lacks credential/network provenance");
            }
            if (configOnly && (!probeOutcomeKnown(result.accountConfigProbeStatus())
                    || result.balanceProbeStatus() != ProbeStatus.NOT_RUN)) {
                throw new IllegalArgumentException("config-only launcher evidence has inconsistent probe statuses");
            }
            if (configAndBalance && (!probeOutcomeKnown(result.accountConfigProbeStatus())
                    || !probeOutcomeKnown(result.balanceProbeStatus()))) {
                throw new IllegalArgumentException("config-and-balance launcher evidence has incomplete probe statuses");
            }
            if (!configAndBalance && result.balanceProbeStatus() != ProbeStatus.NOT_RUN) {
                throw new IllegalArgumentException("balance probe status is outside the allowed endpoint category");
            }
        }

        private static boolean probeOutcomeKnown(ProbeStatus status) {
            return status != ProbeStatus.NOT_RUN && status != ProbeStatus.UNKNOWN;
        }
    }

    static final class SafetyConfig {
        private static final Pattern CURRENCY = Pattern.compile("[A-Z0-9]{2,12}");
        private static final Set<String> REQUIRED_FALSE = Set.of(
                "NQ_LIVE_ENABLED",
                "NQ_REAL_ORDER_SUBMISSION_ENABLED",
                "NQ_TRANSFER_ENABLED",
                "NQ_WITHDRAW_ENABLED",
                "NQ_AI_ENABLED",
                "NQ_DH_RUNTIME_ENABLED",
                "NQ_REAL_PROVIDER_ENABLED",
                "NQ_REAL_CLIENT_ENABLED",
                "NQ_REAL_EXCHANGE_ENABLED"
        );
        private static final Set<String> FORBIDDEN_DIRECT_CREDENTIALS = Set.of(
                "NQ_OKX_API_KEY",
                "NQ_OKX_API_SECRET",
                "NQ_OKX_API_PASSPHRASE",
                "NQ_OKX_REAL_API_KEY",
                "NQ_OKX_REAL_API_SECRET",
                "NQ_OKX_REAL_API_PASSPHRASE"
        );

        private final String action;
        private final Path resultFile;
        private final Path repoRoot;
        private final String databaseUrl;
        private final String databaseUser;
        private final String databasePassword;
        private final String managementHealthUrl;
        private final String masterKey;
        private final long ownerId;
        private final long exchangeAccountId;
        private final List<String> currencies;
        private final Map<String, String> environment;
        private final Properties properties;
        private final String databaseName;
        private final Path formalEvidenceRoot;

        private SafetyConfig(
                String action,
                Path resultFile,
                Path repoRoot,
                String databaseUrl,
                String databaseUser,
                String databasePassword,
                String managementHealthUrl,
                String masterKey,
                long ownerId,
                long exchangeAccountId,
                List<String> currencies,
                Map<String, String> environment,
                Properties properties,
                String databaseName,
                Path formalEvidenceRoot
        ) {
            this.action = action;
            this.resultFile = resultFile;
            this.repoRoot = repoRoot;
            this.databaseUrl = databaseUrl;
            this.databaseUser = databaseUser;
            this.databasePassword = databasePassword;
            this.managementHealthUrl = managementHealthUrl;
            this.masterKey = masterKey;
            this.ownerId = ownerId;
            this.exchangeAccountId = exchangeAccountId;
            this.currencies = currencies;
            this.environment = environment;
            this.properties = properties;
            this.databaseName = databaseName;
            this.formalEvidenceRoot = formalEvidenceRoot;
        }

        static SafetyConfig from(Map<String, String> environment, Properties properties) {
            String action = property(properties, ACTION_PROPERTY);
            String url = value(environment, "NQ_GATEW_SOAK_DB_URL");
            String databaseName = databaseName(url);
            boolean sampleAction = "sample".equals(action);
            boolean scopedMetadataAction = sampleAction || "prerequisite".equals(action);
            Path formalEvidenceRoot = formalEvidenceRoot(environment);
            boolean systemdCredentials = SYSTEMD_CREDENTIAL_SOURCE.equals(
                    value(environment, "NQ_GATEW_SECRET_SOURCE")
            );
            Path expectedCredentialDirectory = expectedCredentialDirectory(formalEvidenceRoot);
            return new SafetyConfig(
                    action,
                    pathProperty(properties, RESULT_FILE_PROPERTY),
                    pathProperty(properties, REPO_ROOT_PROPERTY),
                    url,
                    value(environment, "NQ_GATEW_SOAK_DB_USER"),
                    secretValue(
                            environment,
                            "NQ_GATEW_SOAK_DB_PASSWORD",
                            "db-password",
                            systemdCredentials,
                            expectedCredentialDirectory
                    ),
                    managementHealthUrl(environment),
                    sampleAction
                            ? secretValue(environment, "NQ_ACCOUNT_CREDENTIALS_MASTER_KEY",
                            "credential-master-key", systemdCredentials, expectedCredentialDirectory) : "",
                    scopedMetadataAction
                            ? positiveLong(value(environment, "NQ_GATEW_SOAK_OWNER_ID"), "NQ_GATEW_SOAK_OWNER_ID") : 0,
                    scopedMetadataAction
                            ? positiveLong(value(environment, "NQ_GATEW_SOAK_ACCOUNT_ID"), "NQ_GATEW_SOAK_ACCOUNT_ID") : 0,
                    sampleAction ? currencies(value(environment, "NQ_GATEW_SOAK_CURRENCIES")) : List.of(),
                    Map.copyOf(environment),
                    properties,
                    databaseName,
                    formalEvidenceRoot
            );
        }

        private static Path formalEvidenceRoot(Map<String, String> environment) {
            String raw = value(environment, "NQ_GATEW_FORMAL_EVIDENCE_ROOT");
            if (raw.isBlank()) return null;
            Path path = Path.of(raw);
            for (Path segment : path) {
                if ("..".equals(segment.toString())) {
                    throw new SafeBlockException("SOAK_PATH_TRAVERSAL");
                }
            }
            return path.toAbsolutePath().normalize();
        }

        private static Path expectedCredentialDirectory(Path formalEvidenceRoot) {
            if (formalEvidenceRoot == null) return null;
            Path stateRoot = Path.of(FORMAL_STATE_ROOT).toAbsolutePath().normalize();
            if (!formalEvidenceRoot.startsWith(stateRoot)) {
                throw new SafeBlockException("SYSTEMD_CREDENTIAL_SOURCE_INVALID");
            }
            Path relative = stateRoot.relativize(formalEvidenceRoot);
            if (relative.getNameCount() != 2
                    || !SAFE_RUN_ID.matcher(relative.getName(0).toString()).matches()
                    || !"evidence".equals(relative.getName(1).toString())) {
                throw new SafeBlockException("SYSTEMD_CREDENTIAL_SOURCE_INVALID");
            }
            return Path.of(
                    "/run/credentials",
                    "nq-gatew-soak@" + relative.getName(0) + ".service"
            ).toAbsolutePath().normalize();
        }

        /**
         * Linux formal service 必须从 systemd credential directory 的固定文件名取 secret；禁止
         * EnvironmentFile、argv 或任意用户路径作为 secret source。测试/Windows legacy self-test
         * 未声明 SYSTEMD_CREDENTIALS 时继续使用现有内存 fixture。
         */
        private static String secretValue(
                Map<String, String> environment,
                String legacyEnvironmentName,
                String credentialName,
                boolean systemdCredentials,
                Path expectedCredentialDirectory
        ) {
            if (!systemdCredentials) return value(environment, legacyEnvironmentName);
            if (!value(environment, legacyEnvironmentName).isBlank()) {
                throw new SafeBlockException("SYSTEMD_CREDENTIAL_ENV_CONFLICT");
            }
            if (!"true".equals(value(environment, "NQ_GATEW_FORMAL_SYSTEMD"))
                    || expectedCredentialDirectory == null) {
                throw new SafeBlockException("SYSTEMD_CREDENTIAL_SOURCE_INVALID");
            }
            String directoryValue = value(environment, "CREDENTIALS_DIRECTORY");
            try {
                Path directory = Path.of(directoryValue).toAbsolutePath().normalize();
                Path credential = directory.resolve(credentialName).normalize();
                if (directoryValue.isBlank()
                        || !directory.equals(expectedCredentialDirectory)
                        || !credential.getParent().equals(directory)
                        || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                        || !Files.isRegularFile(credential, LinkOption.NOFOLLOW_LINKS)) {
                    throw new SafeBlockException("SYSTEMD_CREDENTIAL_SOURCE_INVALID");
                }
                assertNoSymbolicLinkComponents(directory);
                assertNoSymbolicLinkComponents(credential);
                if (!directory.toRealPath().equals(expectedCredentialDirectory.toRealPath())) {
                    throw new SafeBlockException("SYSTEMD_CREDENTIAL_SOURCE_INVALID");
                }
                long size = Files.size(credential);
                if (size <= 0 || size > 16_384) {
                    throw new SafeBlockException("SYSTEMD_CREDENTIAL_SOURCE_INVALID");
                }
                String secret = Files.readString(credential, StandardCharsets.UTF_8).strip();
                if (secret.isBlank() || secret.indexOf('\0') >= 0) {
                    throw new SafeBlockException("SYSTEMD_CREDENTIAL_SOURCE_INVALID");
                }
                return secret;
            } catch (SafeBlockException ex) {
                throw ex;
            } catch (RuntimeException | IOException ex) {
                throw new SafeBlockException("SYSTEMD_CREDENTIAL_SOURCE_INVALID");
            }
        }

        private static void assertNoSymbolicLinkComponents(Path path) throws IOException {
            Path absolute = path.toAbsolutePath().normalize();
            Path current = absolute.getRoot();
            if (current == null) throw new IOException("path root is missing");
            for (Path segment : absolute) {
                current = current.resolve(segment);
                if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(current)) {
                    throw new IOException("path component is not a stable real path");
                }
            }
        }

        void assertSafe() {
            List<String> violations = new ArrayList<>();
            if (!Set.of("bootstrap", "sample", "engage", "prerequisite", "precreate-prerequisite").contains(action)) {
                violations.add("SOAK_ACTION_INVALID");
            }
            if (!Set.of(REAL_READONLY_SOAK, OFFLINE_ISOLATED_ACCEPTANCE).contains(runMode())) {
                violations.add("SOAK_RUN_MODE_INVALID");
            }
            Set<String> profiles = new LinkedHashSet<>(Arrays.stream(value(environment, "SPRING_PROFILES_ACTIVE").split(","))
                    .map(String::trim).filter(value -> !value.isBlank()).toList());
            if (!profiles.equals(Set.of(PROFILE))) violations.add("SOAK_PROFILE_REQUIRED");
            if (!"true".equalsIgnoreCase(value(environment, "NQ_GATEW_OKX_READONLY_SOAK_ENABLED"))) {
                violations.add("SOAK_FEATURE_FLAG_REQUIRED");
            }
            if ("true".equalsIgnoreCase(value(environment, "CI"))
                    || "true".equalsIgnoreCase(value(environment, "NQ_NO_OUTBOUND"))) {
                violations.add("SOAK_OUTBOUND_FORBIDDEN_IN_CI");
            }
            for (String name : REQUIRED_FALSE) {
                if (!"false".equalsIgnoreCase(value(environment, name))) violations.add(name + "_MUST_BE_FALSE");
            }
            for (String name : FORBIDDEN_DIRECT_CREDENTIALS) {
                if (!value(environment, name).isBlank() || !property(properties, name).isBlank()) {
                    violations.add(name + "_DIRECT_INPUT_FORBIDDEN");
                }
            }
            if (databaseUrl.isBlank() || databaseUser.isBlank() || databasePassword.isBlank()) {
                violations.add("SOAK_DATABASE_CONFIG_REQUIRED");
            }
            if ("sample".equals(action) && masterKey.isBlank()) violations.add("CREDENTIAL_MASTER_KEY_REQUIRED");
            boolean safeDatabase = "precreate-prerequisite".equals(action)
                    ? safePreCreateDatabaseTarget(databaseUrl, databaseName)
                    : safeDatabaseTarget(databaseUrl, databaseName);
            if (!safeDatabase) violations.add("SOAK_DATABASE_NOT_LOCAL");
            if ("precreate-prerequisite".equals(action)
                    && !"http://127.0.0.1:18889/actuator/health".equals(managementHealthUrl)) {
                violations.add("MANAGEMENT_HEALTH_TARGET_INVALID");
            }
            if (!violations.isEmpty()) throw new SafeBlockException(violations.getFirst());
        }

        private static boolean safeDatabaseTarget(String url, String databaseName) {
            if (url == null || !url.startsWith("jdbc:postgresql://") || databaseName.isBlank()) return false;
            try {
                URI uri = URI.create(url.substring("jdbc:".length()));
                String host = uri.getHost();
                return uri.getUserInfo() == null
                        && uri.getRawQuery() == null
                        && uri.getRawFragment() == null
                        && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host))
                        && databaseName.toLowerCase(Locale.ROOT).matches(".*(gatew|soak).*");
            } catch (RuntimeException ex) {
                return false;
            }
        }

        private static boolean safePreCreateDatabaseTarget(String url, String databaseName) {
            if (url == null || !url.startsWith("jdbc:postgresql://")
                    || !databaseName.matches("[A-Za-z][A-Za-z0-9_]{0,62}")) return false;
            try {
                URI uri = URI.create(url.substring("jdbc:".length()));
                String host = uri.getHost();
                return uri.getUserInfo() == null
                        && uri.getRawQuery() == null
                        && uri.getRawFragment() == null
                        && uri.getPort() >= 1 && uri.getPort() <= 65535
                        && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host));
            } catch (RuntimeException ex) {
                return false;
            }
        }

        private static String managementHealthUrl(Map<String, String> environment) {
            String configured = value(environment, "NQ_GATEW_MANAGEMENT_HEALTH_URL");
            return configured.isBlank()
                    ? "http://127.0.0.1:18889/actuator/health"
                    : configured;
        }

        private static String databaseName(String url) {
            if (url == null || !url.startsWith("jdbc:")) return "";
            try {
                String path = URI.create(url.substring("jdbc:".length())).getPath();
                return path == null || path.length() <= 1 ? "" : path.substring(1);
            } catch (RuntimeException ex) {
                return "";
            }
        }

        private static List<String> currencies(String raw) {
            if (raw == null || raw.isBlank()) throw new SafeBlockException("SOAK_CURRENCY_ALLOWLIST_REQUIRED");
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (String token : raw.split(",")) {
                String value = token.trim().toUpperCase(Locale.ROOT);
                if (!CURRENCY.matcher(value).matches()) throw new SafeBlockException("SOAK_CURRENCY_ALLOWLIST_INVALID");
                values.add(value);
            }
            if (values.isEmpty() || values.size() > 3) throw new SafeBlockException("SOAK_CURRENCY_ALLOWLIST_INVALID");
            return List.copyOf(values);
        }

        private static long positiveLong(String raw, String name) {
            try {
                long value = Long.parseLong(raw);
                if (value <= 0) throw new NumberFormatException();
                return value;
            } catch (RuntimeException ex) {
                throw new SafeBlockException(name + "_REQUIRED");
            }
        }

        private static Path pathProperty(Properties properties, String name) {
            String value = property(properties, name);
            if (value.isBlank()) throw new SafeBlockException(name + "_REQUIRED");
            Path path = Path.of(value);
            for (Path segment : path) {
                if ("..".equals(segment.toString())) {
                    throw new SafeBlockException("SOAK_PATH_TRAVERSAL");
                }
            }
            return path.toAbsolutePath().normalize();
        }

        private static String property(Properties properties, String name) {
            return Objects.toString(properties.getProperty(name), "").trim();
        }

        private static String value(Map<String, String> environment, String name) {
            return Objects.toString(environment.get(name), "").trim();
        }

        String action() {
            return action;
        }

        String runMode() {
            return value(environment, "NQ_GATEW_RUN_MODE");
        }

        Path resultFile() {
            return resultFile;
        }

        Path repoRoot() {
            return repoRoot;
        }

        String databaseUrl() {
            return databaseUrl;
        }

        String databaseUser() {
            return databaseUser;
        }

        String databasePassword() {
            return databasePassword;
        }

        String managementHealthUrl() {
            return managementHealthUrl;
        }

        String masterKey() {
            return masterKey;
        }

        long ownerId() {
            return ownerId;
        }

        long exchangeAccountId() {
            return exchangeAccountId;
        }

        List<String> currencies() {
            return currencies;
        }

        String databaseName() {
            return databaseName;
        }

        Path formalEvidenceRoot() {
            return formalEvidenceRoot;
        }

        @Override
        public String toString() {
            return "SafetyConfig[REDACTED]";
        }
    }

    record CredentialGate(
            String permissionScope,
            boolean withdrawEnabled,
            boolean ipAllowlistRequired,
            String permissionProbeStatus,
            String ipAllowlistProbeStatus,
            String exchange,
            String tradeEnvironment,
            String accountStatus
    ) {
        void assertSafe() {
            if (!"READ_ONLY".equals(permissionScope)) {
                throw new SafeBlockException("CREDENTIAL_PERMISSION_NOT_READONLY", "UNSAFE_OR_UNKNOWN");
            }
            if (withdrawEnabled) {
                throw new SafeBlockException("UNSAFE_CREDENTIAL_PERMISSIONS", "WITHDRAW_ENABLED");
            }
            if (!ipAllowlistRequired
                    || "FAILED".equals(ipAllowlistProbeStatus)
                    || "SKIPPED".equals(ipAllowlistProbeStatus)) {
                throw new SafeBlockException("IP_ALLOWLIST_REQUIRED", "READ_ONLY_UNVERIFIED_IP");
            }
            if ("FAILED".equals(permissionProbeStatus) || "SKIPPED".equals(permissionProbeStatus)) {
                throw new SafeBlockException("CREDENTIAL_PERMISSION_NOT_READONLY", "UNSAFE_OR_UNKNOWN");
            }
            if (!"OKX".equals(exchange) || !"LIVE".equals(tradeEnvironment) || !"ACTIVE".equals(accountStatus)) {
                throw new SafeBlockException("CREDENTIAL_SCOPE_INVALID");
            }
        }
    }

    @FunctionalInterface
    interface ManagementHealthProbe {
        boolean isHealthy();
    }

    @JsonPropertyOrder({
            "killSwitchEngaged",
            "credentialConfigured",
            "activeCredentialCount",
            "credentialType",
            "credentialLocalStatus",
            "permissionFactPresent",
            "permissionFactFresh",
            "readPermissionStatus",
            "tradePermissionExpectedDisabled",
            "withdrawPermissionExpectedDisabled",
            "ipAllowlistStatus",
            "postgresReachable",
            "managementHealthy",
            "blockerCodes",
            "diagnosticId"
    })
    record PrerequisiteReadback(
            boolean killSwitchEngaged,
            boolean credentialConfigured,
            int activeCredentialCount,
            String credentialType,
            String credentialLocalStatus,
            boolean permissionFactPresent,
            boolean permissionFactFresh,
            String readPermissionStatus,
            boolean tradePermissionExpectedDisabled,
            boolean withdrawPermissionExpectedDisabled,
            String ipAllowlistStatus,
            boolean postgresReachable,
            boolean managementHealthy,
            List<String> blockerCodes,
            String diagnosticId
    ) {
        private static final Set<String> ALLOWED_BLOCKERS = Set.of(
                "MANAGEMENT_UNREACHABLE",
                "POSTGRES_UNREACHABLE",
                "ACCOUNT_SCOPE_MISMATCH",
                "CREDENTIAL_NOT_CONFIGURED",
                "ACTIVE_CREDENTIAL_COUNT_INVALID",
                "CREDENTIAL_TYPE_MISMATCH",
                "CREDENTIAL_LOCAL_STATUS_NOT_ACTIVE",
                "PERMISSION_FACT_MISSING",
                "PERMISSION_FACT_STALE",
                "READ_PERMISSION_NOT_VERIFIED",
                "TRADE_PERMISSION_NOT_DISABLED",
                "WITHDRAW_PERMISSION_NOT_DISABLED",
                "IP_ALLOWLIST_NOT_VERIFIED",
                "INTERNAL_SANITIZED_READBACK_FAILURE",
                "KILL_SWITCH_NOT_ENGAGED"
        );
        static final List<String> FIELDS = List.of(
                "killSwitchEngaged",
                "credentialConfigured",
                "activeCredentialCount",
                "credentialType",
                "credentialLocalStatus",
                "permissionFactPresent",
                "permissionFactFresh",
                "readPermissionStatus",
                "tradePermissionExpectedDisabled",
                "withdrawPermissionExpectedDisabled",
                "ipAllowlistStatus",
                "postgresReachable",
                "managementHealthy",
                "blockerCodes",
                "diagnosticId"
        );

        static PrerequisiteReadback unavailable(String blockerCode) {
            return new PrerequisiteReadback(
                    false, false, 0, "UNKNOWN", "UNKNOWN",
                    false, false, "UNKNOWN", false, false, "UNKNOWN",
                    false, false, List.of(blockerCode), safePrerequisiteDiagnosticId()
            );
        }

        static void validate(PrerequisiteReadback result) {
            Objects.requireNonNull(result, "result must not be null");
            if (result.activeCredentialCount() < 0
                    || !Set.of("OKX_API_V5", "UNKNOWN", "CONFLICT").contains(result.credentialType())
                    || !Set.of(
                    "ACTIVE", "DISABLED", "REVOKED", "EXPIRED", "ROTATED", "UNKNOWN", "CONFLICT"
            ).contains(result.credentialLocalStatus())
                    || !Set.of("VERIFIED", "NOT_VERIFIED", "UNKNOWN").contains(result.readPermissionStatus())
                    || !Set.of("VERIFIED", "FAILED", "UNKNOWN").contains(result.ipAllowlistStatus())
                    || result.blockerCodes() == null
                    || result.blockerCodes().stream().anyMatch(code -> !ALLOWED_BLOCKERS.contains(code))
                    || result.blockerCodes().size() != new LinkedHashSet<>(result.blockerCodes()).size()
                    || result.diagnosticId() == null
                    || !result.diagnosticId().matches("^gatew-precreate-[a-f0-9]{32}$")) {
                throw new IllegalArgumentException("prerequisite readback is outside the closed schema");
            }
        }

        boolean ready() {
            return killSwitchEngaged
                    && credentialConfigured
                    && activeCredentialCount == 1
                    && "OKX_API_V5".equals(credentialType)
                    && "ACTIVE".equals(credentialLocalStatus)
                    && permissionFactPresent
                    && permissionFactFresh
                    && "VERIFIED".equals(readPermissionStatus)
                    && tradePermissionExpectedDisabled
                    && withdrawPermissionExpectedDisabled
                    && "VERIFIED".equals(ipAllowlistStatus)
                    && postgresReachable
                    && managementHealthy
                    && blockerCodes.isEmpty();
        }
    }

    static final class CountingTransport implements OkxPrivateReadTransport {
        private final OkxPrivateReadTransport delegate;
        private final List<OkxPrivateReadOperation> operations = new ArrayList<>();
        private final Map<OkxPrivateReadOperation, ProbeStatus> probeStatuses =
                new EnumMap<>(OkxPrivateReadOperation.class);

        CountingTransport(OkxPrivateReadTransport delegate) {
            this.delegate = Objects.requireNonNull(delegate);
        }

        @Override
        public OkxPrivateReadResult execute(
                OkxPrivateReadRequest request,
                com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateCredentialContext credential,
                OkxPrivateEnvironment environment
        ) {
            if (request == null || !SOAK_OPERATIONS.contains(request.operation())) {
                throw new SafeBlockException("FORBIDDEN_ENDPOINT_ATTEMPTED");
            }
            OkxPrivateReadOperation operation = request.operation();
            operations.add(operation);
            probeStatuses.put(operation, ProbeStatus.UNKNOWN);
            try {
                OkxPrivateReadResult result = delegate.execute(request, credential, environment);
                if (result == null || result.operation() != operation) {
                    probeStatuses.put(operation, ProbeStatus.FAILED);
                    throw new SafeBlockException("SOAK_TRANSPORT_RESULT_INVALID");
                }
                probeStatuses.put(operation, result.complete() ? ProbeStatus.SUCCEEDED : ProbeStatus.FAILED);
                return result;
            } catch (SafeBlockException ex) {
                probeStatuses.put(operation, ProbeStatus.BLOCKED);
                throw ex;
            } catch (RuntimeException ex) {
                probeStatuses.put(operation, ProbeStatus.FAILED);
                throw ex;
            }
        }

        int calls() {
            return operations.size();
        }

        List<OkxPrivateReadOperation> operations() {
            return List.copyOf(operations);
        }

        String endpointCategory() {
            if (operations.isEmpty()) return "NONE";
            if (operations.equals(List.of(OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ))) {
                return "ACCOUNT_CONFIGURATION_READ";
            }
            if (operations.equals(List.of(
                    OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ,
                    OkxPrivateReadOperation.OKX_ACCOUNT_BALANCE_READ))) {
                return "ACCOUNT_CONFIG_AND_BALANCE_READ";
            }
            return "FORBIDDEN_OR_UNKNOWN";
        }

        ProbeStatus accountConfigProbeStatus() {
            return probeStatuses.getOrDefault(
                    OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ,
                    ProbeStatus.NOT_RUN
            );
        }

        ProbeStatus balanceProbeStatus() {
            return probeStatuses.getOrDefault(
                    OkxPrivateReadOperation.OKX_ACCOUNT_BALANCE_READ,
                    ProbeStatus.NOT_RUN
            );
        }
    }

    enum ProbeStatus {
        NOT_RUN,
        SUCCEEDED,
        BLOCKED,
        FAILED,
        UNKNOWN
    }

    @JsonPropertyOrder({
            "schemaVersion",
            "cycleId",
            "observedAt",
            "durationMs",
            "resultStatus",
            "reasonCode",
            "httpStatusCategory",
            "permissionClassification",
            "killSwitchObservedState",
            "credentialAccessed",
            "networkCalled",
            "allowedEndpointCategory",
            "accountConfigProbeStatus",
            "balanceProbeStatus",
            "traceId"
    })
    record CycleResult(
            String schemaVersion,
            String cycleId,
            Instant observedAt,
            long durationMs,
            String resultStatus,
            String reasonCode,
            String httpStatusCategory,
            String permissionClassification,
            String killSwitchObservedState,
            boolean credentialAccessed,
            boolean networkCalled,
            String allowedEndpointCategory,
            ProbeStatus accountConfigProbeStatus,
            ProbeStatus balanceProbeStatus,
            String traceId
    ) {
        static CycleResult blocked(String reasonCode, String permissionClassification) {
            return new CycleResult(
                    LAUNCHER_SCHEMA_VERSION,
                    safeCycleId(),
                    Instant.now(),
                    0,
                    "BLOCKED",
                    reasonCode,
                    "NOT_CALLED",
                    permissionClassification,
                    "UNKNOWN",
                    false,
                    false,
                    "NONE",
                    ProbeStatus.NOT_RUN,
                    ProbeStatus.NOT_RUN,
                    safeTraceId()
            );
        }

        static CycleResult failed(String reasonCode, String permissionClassification) {
            return new CycleResult(
                    LAUNCHER_SCHEMA_VERSION,
                    safeCycleId(),
                    Instant.now(),
                    0,
                    "FAILED",
                    reasonCode,
                    "NOT_AVAILABLE",
                    permissionClassification,
                    "UNKNOWN",
                    false,
                    false,
                    "NONE",
                    ProbeStatus.NOT_RUN,
                    ProbeStatus.NOT_RUN,
                    safeTraceId()
            );
        }

        boolean schemaSafe() {
            try {
                EvidenceSanitizer.validateDto(this);
                return true;
            } catch (RuntimeException ex) {
                return false;
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ImportAutoConfiguration(JacksonAutoConfiguration.class)
    static class JacksonContext {
    }

    static final class SafeBlockException extends RuntimeException {
        private final String reasonCode;
        private final String permissionClassification;

        private SafeBlockException(String reasonCode) {
            this(reasonCode, "UNKNOWN");
        }

        private SafeBlockException(String reasonCode, String permissionClassification) {
            super("GateW soak blocked: " + reasonCode, null, false, false);
            this.reasonCode = reasonCode;
            this.permissionClassification = permissionClassification;
        }

        String reasonCode() {
            return reasonCode;
        }

        String permissionClassification() {
            return permissionClassification;
        }
    }
}
