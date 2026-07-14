package com.guidinglight.nexusquant.app.gatew;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
class GateWOkxReadonlySoakCycleTest {

    static final String REQUIRED_PROPERTY = "nq.gatew.okxReadonlySoak.required";
    static final String ACTION_PROPERTY = "nq.gatew.okxReadonlySoak.action";
    static final String RESULT_FILE_PROPERTY = "nq.gatew.okxReadonlySoak.resultFile";
    static final String REPO_ROOT_PROPERTY = "nq.gatew.okxReadonlySoak.repoRoot";
    static final String PROFILE = "gatew-okx-readonly-soak";
    static final String ENDPOINT_ALLOWLIST_VERSION = "gatew-okx-private-readonly-v1";
    private static final Set<OkxPrivateReadOperation> SOAK_OPERATIONS = Set.of(
            OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ,
            OkxPrivateReadOperation.OKX_ACCOUNT_BALANCE_READ
    );
    private static final Set<String> TRANSIENT_REASONS = Set.of(
            OkxPrivateReadError.NETWORK_FAILURE.name(),
            OkxPrivateReadError.TIMEOUT.name(),
            OkxPrivateReadError.RATE_LIMITED.name(),
            OkxPrivateReadError.HTTP_ERROR.name(),
            OkxPrivateReadError.OKX_PROVIDER_ERROR.name()
    );
    private static final Set<String> AUTH_REASONS = Set.of(
            OkxPrivateReadError.AUTHENTICATION_FAILURE.name(),
            OkxPrivateReadError.SIGNATURE_FAILURE.name()
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

    @Test
    void executeOneSanitizedAction() {
        SafetyConfig config = SafetyConfig.from(System.getenv(), System.getProperties());
        CycleResult result;
        try {
            config.assertSafe();
            result = execute(config);
        } catch (SafeBlockException ex) {
            result = CycleResult.blocked(ex.reasonCode(), ex.permissionClassification());
        } catch (RuntimeException ex) {
            // JDBC/Flyway/Jackson/provider cause 可能携带本地连接或 payload 片段，不把 cause 带入 test 日志。
            result = CycleResult.blocked("SOAK_INTERNAL_FAILURE", "UNKNOWN");
        }
        writeSanitizedResult(config, result);
        assertTrue(result.schemaSafe(), "cycle result must remain within the sanitized evidence schema");
    }

    private CycleResult execute(SafetyConfig config) {
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
            return engage(jdbc, config);
        }
        CredentialGate credential = credentialGate(jdbc, config);
        credential.assertSafe();

        return switch (config.action()) {
            case "bootstrap" -> bootstrap(jdbc, config, credential);
            case "sample" -> sample(jdbc, config, credential);
            default -> throw new SafeBlockException("SOAK_ACTION_INVALID");
        };
    }

    private CycleResult bootstrap(JdbcTemplate jdbc, SafetyConfig config, CredentialGate credential) {
        long version = disengageIsolatedFixture(jdbc);
        assertNoBusinessData(jdbc);
        return new CycleResult(
                "BOOTSTRAP_READY",
                "SOAK_ISOLATION_READY",
                "NOT_CALLED",
                "METADATA_READ_ONLY",
                KillSwitchStatus.DISENGAGED.name(),
                false,
                false,
                "NONE",
                safeTraceId(),
                databaseFingerprint(config),
                credential.fingerprint(),
                "35",
                ENDPOINT_ALLOWLIST_VERSION,
                Instant.now(),
                0,
                false,
                version
        );
    }

    private CycleResult sample(JdbcTemplate jdbc, SafetyConfig config, CredentialGate credential) {
        Instant startedAt = Instant.now();
        KillSwitchService killSwitchService = new KillSwitchService(
                new JdbcKillSwitchStateRepository(jdbc),
                Clock.systemUTC()
        );
        KillSwitchSnapshot before = killSwitchService.snapshot();
        if (before.status() != KillSwitchStatus.DISENGAGED) {
            throw new SafeBlockException("KILL_SWITCH_NOT_DISENGAGED");
        }

        CountingTransport transport = new CountingTransport(
                new JdkOkxPrivateReadTransport(new ObjectMapper(), Clock.systemUTC())
        );
        OkxPrivateReadonlyProbeService service = new OkxPrivateReadonlyProbeService(
                new JdbcExchangeAccountRepository(jdbc),
                new JdbcOkxPrivateCredentialExecutor(
                        jdbc,
                        new ObjectMapper(),
                        config.masterKey(),
                        transport
                ),
                killSwitchService,
                Clock.systemUTC()
        );

        OkxPrivateReadObservation observation = service.probe(
                config.ownerId(),
                config.exchangeAccountId(),
                JdbcOkxPrivateCredentialExecutor.OKX_API_V5,
                OkxPrivateEnvironment.PRODUCTION,
                config.currencies()
        );
        assertNoBusinessData(jdbc);
        KillSwitchSnapshot after = killSwitchService.snapshot();
        if (after.status() != KillSwitchStatus.DISENGAGED || after.version() != before.version()) {
            throw new SafeBlockException("KILL_SWITCH_CHANGED_DURING_SAMPLE");
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
                        : "HARD_FAILURE";
        String permissionClassification = passed
                ? "READ_ONLY_WITH_IP_ALLOWLIST"
                : observation.normalizedPermissions().isEmpty() ? "UNKNOWN" : "UNSAFE_OR_INCOMPLETE";

        return new CycleResult(
                resultStatus,
                passed ? "READ_ONLY_SAMPLE_ACCEPTED" : reason,
                httpStatusCategory(reason, passed),
                permissionClassification,
                after.status().name(),
                transport.calls() > 0,
                transport.calls() > 0,
                transport.endpointCategory(),
                safeTraceId(),
                databaseFingerprint(config),
                credential.fingerprint(),
                "35",
                ENDPOINT_ALLOWLIST_VERSION,
                Instant.now(),
                Math.max(0, Duration.between(startedAt, Instant.now()).toMillis()),
                AUTH_REASONS.contains(reason),
                after.version()
        );
    }

    private CycleResult engage(JdbcTemplate jdbc, SafetyConfig config) {
        KillSwitchService service = new KillSwitchService(new JdbcKillSwitchStateRepository(jdbc), Clock.systemUTC());
        KillSwitchSnapshot current = service.snapshot();
        KillSwitchSnapshot engaged = current.status() == KillSwitchStatus.ENGAGED
                ? current
                : service.engage(current.version(), "SOAK_STOP", "GATEW_SOAK_SUPERVISOR", safeTraceId());
        if (engaged.status() != KillSwitchStatus.ENGAGED) {
            throw new SafeBlockException("KILL_SWITCH_ENGAGE_FAILED");
        }
        return new CycleResult(
                "ENGAGED",
                "SOAK_STOPPED_FAIL_CLOSED",
                "NOT_CALLED",
                "METADATA_READ_ONLY",
                engaged.status().name(),
                false,
                false,
                "NONE",
                safeTraceId(),
                databaseFingerprint(config),
                "UNAVAILABLE",
                "35",
                ENDPOINT_ALLOWLIST_VERSION,
                Instant.now(),
                0,
                false,
                engaged.version()
        );
    }

    private static CredentialGate credentialGate(JdbcTemplate jdbc, SafetyConfig config) {
        List<CredentialGate> rows = jdbc.query(
                """
                        SELECT c.credential_id, c.key_version, c.masked_access_key,
                               c.permission_scope, c.withdraw_enabled, c.ip_allowlist_required,
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
                        sha256(String.join("|",
                                Long.toString(resultSet.getLong("credential_id")),
                                Integer.toString(resultSet.getInt("key_version")),
                                Objects.toString(resultSet.getString("masked_access_key"), "MASKED"),
                                Long.toString(config.exchangeAccountId()))),
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

    private static long disengageIsolatedFixture(JdbcTemplate jdbc) {
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
            case "RATE_LIMITED" -> "RATE_LIMITED_429";
            case "HTTP_ERROR", "OKX_PROVIDER_ERROR" -> "EXCHANGE_ERROR";
            case "AUTHENTICATION_FAILURE", "SIGNATURE_FAILURE", "IP_ALLOWLIST_FAILED" -> "AUTH_ERROR";
            case "NETWORK_FAILURE", "TIMEOUT" -> "NETWORK_ERROR";
            default -> "NOT_AVAILABLE";
        };
    }

    private static String databaseFingerprint(SafetyConfig config) {
        return sha256(config.databaseUrl() + "|" + config.databaseUser() + "|" + config.databaseName());
    }

    private static String safeTraceId() {
        return "gatew-soak-" + UUID.randomUUID();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    static void writeSanitizedResult(SafetyConfig config, CycleResult result) {
        try {
            Path targetRoot = config.repoRoot().resolve("target").resolve("gatew-okx-readonly-soak").normalize();
            Path output = config.resultFile().normalize();
            if (!output.startsWith(targetRoot) || output.equals(targetRoot)) {
                throw new IllegalArgumentException("resultFile must stay below target/gatew-okx-readonly-soak");
            }
            Files.createDirectories(output.getParent());
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            byte[] json = mapper.writeValueAsBytes(result);
            String text = new String(json, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            for (String forbidden : List.of(
                    "secretkey", "passphrase", "signature", "cookie",
                    "rawbody", "rawheaders", "rawresponse", "accountid", "balance", "https://", "http://"
            )) {
                if (text.contains(forbidden)) {
                    throw new IllegalStateException("sanitized result contains a forbidden field");
                }
            }
            Files.write(output, json);
            Arrays.fill(json, (byte) 0);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to write sanitized cycle result");
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
        private final String masterKey;
        private final long ownerId;
        private final long exchangeAccountId;
        private final List<String> currencies;
        private final Map<String, String> environment;
        private final Properties properties;
        private final String databaseName;

        private SafetyConfig(
                String action,
                Path resultFile,
                Path repoRoot,
                String databaseUrl,
                String databaseUser,
                String databasePassword,
                String masterKey,
                long ownerId,
                long exchangeAccountId,
                List<String> currencies,
                Map<String, String> environment,
                Properties properties,
                String databaseName
        ) {
            this.action = action;
            this.resultFile = resultFile;
            this.repoRoot = repoRoot;
            this.databaseUrl = databaseUrl;
            this.databaseUser = databaseUser;
            this.databasePassword = databasePassword;
            this.masterKey = masterKey;
            this.ownerId = ownerId;
            this.exchangeAccountId = exchangeAccountId;
            this.currencies = currencies;
            this.environment = environment;
            this.properties = properties;
            this.databaseName = databaseName;
        }

        static SafetyConfig from(Map<String, String> environment, Properties properties) {
            String action = property(properties, ACTION_PROPERTY);
            String url = value(environment, "NQ_GATEW_SOAK_DB_URL");
            String databaseName = databaseName(url);
            boolean credentialAction = !"engage".equals(action);
            return new SafetyConfig(
                    action,
                    pathProperty(properties, RESULT_FILE_PROPERTY),
                    pathProperty(properties, REPO_ROOT_PROPERTY),
                    url,
                    value(environment, "NQ_GATEW_SOAK_DB_USER"),
                    value(environment, "NQ_GATEW_SOAK_DB_PASSWORD"),
                    credentialAction ? value(environment, "NQ_ACCOUNT_CREDENTIALS_MASTER_KEY") : "",
                    credentialAction
                            ? positiveLong(value(environment, "NQ_GATEW_SOAK_OWNER_ID"), "NQ_GATEW_SOAK_OWNER_ID") : 0,
                    credentialAction
                            ? positiveLong(value(environment, "NQ_GATEW_SOAK_ACCOUNT_ID"), "NQ_GATEW_SOAK_ACCOUNT_ID") : 0,
                    credentialAction ? currencies(value(environment, "NQ_GATEW_SOAK_CURRENCIES")) : List.of(),
                    Map.copyOf(environment),
                    properties,
                    databaseName
            );
        }

        void assertSafe() {
            List<String> violations = new ArrayList<>();
            if (!Set.of("bootstrap", "sample", "engage").contains(action)) violations.add("SOAK_ACTION_INVALID");
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
            if (!"engage".equals(action) && masterKey.isBlank()) violations.add("CREDENTIAL_MASTER_KEY_REQUIRED");
            if (!safeDatabaseTarget(databaseUrl, databaseName)) violations.add("SOAK_DATABASE_NOT_LOCAL");
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
            return Path.of(value).toAbsolutePath().normalize();
        }

        private static String property(Properties properties, String name) {
            return Objects.toString(properties.getProperty(name), "").trim();
        }

        private static String value(Map<String, String> environment, String name) {
            return Objects.toString(environment.get(name), "").trim();
        }

        String action() { return action; }
        Path resultFile() { return resultFile; }
        Path repoRoot() { return repoRoot; }
        String databaseUrl() { return databaseUrl; }
        String databaseUser() { return databaseUser; }
        String databasePassword() { return databasePassword; }
        String masterKey() { return masterKey; }
        long ownerId() { return ownerId; }
        long exchangeAccountId() { return exchangeAccountId; }
        List<String> currencies() { return currencies; }
        String databaseName() { return databaseName; }

        @Override
        public String toString() {
            return "SafetyConfig[REDACTED]";
        }
    }

    record CredentialGate(
            String fingerprint,
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

    static final class CountingTransport implements OkxPrivateReadTransport {
        private final OkxPrivateReadTransport delegate;
        private final List<OkxPrivateReadOperation> operations = new ArrayList<>();

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
            operations.add(request.operation());
            return delegate.execute(request, credential, environment);
        }

        int calls() { return operations.size(); }
        List<OkxPrivateReadOperation> operations() { return List.copyOf(operations); }
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
    }

    record CycleResult(
            String resultStatus,
            String reasonCode,
            String httpStatusCategory,
            String permissionClassification,
            String killSwitchObservedState,
            boolean credentialAccessed,
            boolean networkCalled,
            String allowedEndpointCategory,
            String traceId,
            String databaseFingerprint,
            String credentialReferenceFingerprint,
            String flywayVersion,
            String endpointAllowlistVersion,
            Instant observedAt,
            long durationMs,
            boolean authenticationFailure,
            long killSwitchVersion
    ) {
        static CycleResult blocked(String reasonCode, String permissionClassification) {
            return new CycleResult(
                    "HARD_FAILURE",
                    reasonCode,
                    "NOT_CALLED",
                    permissionClassification,
                    "UNKNOWN",
                    false,
                    false,
                    "NONE",
                    safeTraceId(),
                    "UNAVAILABLE",
                    "UNAVAILABLE",
                    "UNAVAILABLE",
                    ENDPOINT_ALLOWLIST_VERSION,
                    Instant.now(),
                    0,
                    AUTH_REASONS.contains(reasonCode),
                    0
            );
        }

        boolean schemaSafe() {
            return observedAt != null
                    && resultStatus != null
                    && reasonCode != null
                    && traceId != null
                    && !traceId.isBlank()
                    && durationMs >= 0;
        }
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

        String reasonCode() { return reasonCode; }
        String permissionClassification() { return permissionClassification; }
    }
}
