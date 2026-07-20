package com.guidinglight.nexusquant.app.gatew;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidinglight.nexusquant.risk.infra.jdbc.JdbcKillSwitchStateRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GateW formal systemd fail-close 与 offline acceptance 的 test-support launcher。
 *
 * <p>该入口没有 provider、account、credential repository 或 endpoint 依赖。恢复只通过
 * {@link KillSwitchService} + {@link JdbcKillSwitchStateRepository} 的既有 optimistic path，
 * 并由显式 {@link TransactionTemplate} 保证 state/event 同一短事务；事务外再次 read-back。
 * offline bootstrap 的 DISENGAGED 写入仅允许全新 disposable schema，用于证明受控失败后的
 * automatic engage，不能用于 REAL mode。</p>
 */
@Tag("gatew-soak-failclose")
public class GateWOkxReadonlySoakFailCloseTest {

    static final String REQUIRED_PROPERTY = "nq.gatew.soakFailClose.required";
    static final String ACTION_PROPERTY = "nq.gatew.soakFailClose.action";
    static final String RESULT_FILE_PROPERTY = "nq.gatew.soakFailClose.resultFile";
    static final String RUN_ID_PROPERTY = "nq.gatew.soakFailClose.runId";
    static final String RESULT_SCHEMA = "gatew-soak-failclose-v1";
    private static final String STATE_ROOT = "/var/lib/nexus-quant/gatew-soak";
    private static final Pattern RUN_ID = Pattern.compile("gatew-soak-[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}");
    private static final Pattern OFFLINE_SCHEMA = Pattern.compile("gatew_offline_[a-f0-9]{8}");
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    /**
     * systemd helper invocation。默认 Maven/CI 不设置 required property，因此不会触碰 PostgreSQL。
     */
    @Test
    @EnabledIfSystemProperty(named = REQUIRED_PROPERTY, matches = "true")
    void executeOneSanitizedAction() {
        Object result = executeOneSanitizedAction(System.getenv(), System.getProperties());
        assertTrue(schemaSafe(result), "fail-close result must stay within its closed sanitized schema");
    }

    /**
     * 独立 fail-close service 的固定 Java 入口。该入口不接受 argv，不加载 provider 或 master key，
     * 只消费 systemd 注入的独立 DB credential，并写入 root-only control leaf。
     *
     * @param args 必须为空；恢复动作由 closed system property allowlist 指定
     */
    public static void main(String[] args) {
        if (args.length != 0 || !"true".equals(System.getProperty(REQUIRED_PROPERTY))) {
            throw new IllegalStateException("GateW fail-close standalone launcher is not authorized");
        }
        Object result = executeOneSanitizedAction(System.getenv(), System.getProperties());
        if (!schemaSafe(result)) {
            throw new IllegalStateException("GateW fail-close standalone result is outside the closed schema");
        }
    }

    private static Object executeOneSanitizedAction(
            Map<String, String> environment,
            Properties properties
    ) {
        RuntimeConfig config = RuntimeConfig.from(environment, properties);
        Object result = switch (config.action()) {
            case "offline-bootstrap" -> offlineBootstrap(config);
            case "offline-sample" -> offlineSample(config);
            case "offline-controlled-failure" -> offlineControlledFailure(config);
            case "verify" -> verify(config);
            case "engage" -> engage(config);
            default -> FailCloseResult.failure("verify", RecoveryStatus.ENGAGE_FAILED_DB_ENV_INVALID);
        };
        writeResult(config, result);
        return result;
    }

    @Test
    void recoveryTaxonomyIsClosedAndProviderFree() throws Exception {
        for (RecoveryStatus status : RecoveryStatus.values()) {
            FailCloseResult result = new FailCloseResult(
                    RESULT_SCHEMA,
                    "engage",
                    Instant.parse("2026-07-18T00:00:00Z"),
                    status,
                    status.safe() ? "ENGAGED" : "UNKNOWN",
                    status.safe() ? 1 : 0,
                    false,
                    false
            );
            assertTrue(schemaSafe(result));
            String json = OBJECT_MAPPER.writeValueAsString(result).toLowerCase(Locale.ROOT);
            assertFalse(json.contains("api_key"));
            assertFalse(json.contains("password"));
            assertFalse(json.contains("/api/v5/"));
        }
    }

    @Test
    void offlinePassHasNoCredentialOrNetworkProvenance() throws Exception {
        GateWOkxReadonlySoakCycleTest.CycleResult result = offlineCycle(KillSwitchStatus.DISENGAGED);
        GateWOkxReadonlySoakCycleTest.EvidenceSanitizer.validateDto(result);
        assertEquals("PASSED_READ_ONLY", result.resultStatus());
        assertEquals("OFFLINE_LOCAL_FIXTURE_READ", result.allowedEndpointCategory());
        assertFalse(result.credentialAccessed());
        assertFalse(result.networkCalled());
        byte[] json = GateWOkxReadonlySoakCycleTest.EvidenceSanitizer.serialize(OBJECT_MAPPER, result);
        assertTrue(OBJECT_MAPPER.readTree(json).path("observedAt").isTextual());
        byte[] cycleMapperJson = GateWOkxReadonlySoakCycleTest.STANDALONE_OBJECT_MAPPER.writeValueAsBytes(
                Map.of("observedAt", Instant.parse("2026-07-19T00:00:00Z"))
        );
        assertTrue(GateWOkxReadonlySoakCycleTest.STANDALONE_OBJECT_MAPPER
                .readTree(cycleMapperJson).path("observedAt").isTextual());
    }

    @Test
    void alreadyEngagedIsIdempotentSuccess() {
        assertEquals(
                RecoveryStatus.ENGAGE_NOT_REQUIRED_ALREADY_ENGAGED,
                statusFor(KillSwitchStatus.ENGAGED, false)
        );
        assertEquals(RecoveryStatus.ENGAGE_SUCCEEDED, statusFor(KillSwitchStatus.DISENGAGED, true));
        assertEquals(RecoveryStatus.ENGAGE_STATUS_UNKNOWN, statusFor(KillSwitchStatus.UNKNOWN, false));
    }

    @Test
    void controlledOfflineFailureHasClosedNoNetworkProvenance() {
        RuntimeConfig config = new RuntimeConfig(
                "offline-controlled-failure",
                "gatew-soak-20260718T000000Z-0123abcd",
                RunMode.OFFLINE_ACCEPTANCE,
                "jdbc:postgresql://127.0.0.1:55432/gatew_soak",
                "gatew_soak",
                "not-used-by-controlled-failure",
                "gatew_offline_0123abcd",
                Path.of(STATE_ROOT,
                        "gatew-soak-20260718T000000Z-0123abcd",
                        "evidence",
                        "controlled-failure.json")
        );

        GateWOkxReadonlySoakCycleTest.CycleResult result = offlineControlledFailure(config);

        GateWOkxReadonlySoakCycleTest.EvidenceSanitizer.validateDto(result);
        assertEquals("FAILED", result.resultStatus());
        assertEquals("CONTROLLED_OFFLINE_CYCLE_3_FAILURE", result.reasonCode());
        assertFalse(result.credentialAccessed());
        assertFalse(result.networkCalled());
    }

    @Test
    void connectionFailureTaxonomyRecognizesSqlStateClass08() {
        RuntimeException wrapped = new RuntimeException(
                new java.sql.SQLException("redacted", "08001")
        );
        assertTrue(isConnectionFailure(wrapped));
        assertFalse(isConnectionFailure(new IllegalStateException("closed failure")));
    }

    @Test
    void authenticationFailureTaxonomyRecognizesSqlStateClass28() {
        RuntimeException wrapped = new RuntimeException(
                new java.sql.SQLException("redacted", "28P01")
        );
        assertTrue(isAuthenticationFailure(wrapped));
        assertFalse(isAuthenticationFailure(new IllegalStateException("closed failure")));
    }

    @Test
    void bootstrapContextFailureTaxonomyIsClosedAndNeverReportedAsWriteAttempt() {
        assertEquals(
                RecoveryStatus.ENGAGE_FAILED_DB_AUTHENTICATION,
                classifyBootstrapContextFailure(new RuntimeException(
                        new java.sql.SQLException("redacted", "28P01")
                ))
        );
        assertEquals(
                RecoveryStatus.ENGAGE_FAILED_DB_UNREACHABLE,
                classifyBootstrapContextFailure(new RuntimeException(
                        new java.sql.SQLException("redacted", "08001")
                ))
        );
        assertEquals(
                RecoveryStatus.ENGAGE_FAILED_DB_CONTEXT_INIT,
                classifyBootstrapContextFailure(new IllegalStateException("closed failure"))
        );
    }

    @Test
    void offlineSchemaSearchPathResolvesPublicExtensionsWithoutChangingRealSchema() {
        String url = "jdbc:postgresql://127.0.0.1:55432/gatew_soak";

        assertEquals(
                url + "?currentSchema=gatew_offline_0123abcd,public&connectTimeout=5&socketTimeout=5",
                withSchema(url, "gatew_offline_0123abcd")
        );
        assertEquals(
                url + "?currentSchema=public&connectTimeout=5&socketTimeout=5",
                withSchema(url, "public")
        );
        assertEquals(
                "CREATE SCHEMA gatew_offline_0123abcd",
                createOfflineSchemaSql("gatew_offline_0123abcd")
        );
        assertThrows(ConfigException.class, () -> createOfflineSchemaSql("public"));
        assertThrows(ConfigException.class, () -> createOfflineSchemaSql("gatew_offline_0123abcd;DROP SCHEMA public"));
    }

    @Test
    void readBackFailureTaxonomyNeverCollapsesIntoWriteSuccess() {
        assertEquals(
                RecoveryStatus.ENGAGE_FAILED_READBACK,
                classifyReadBackFailure(new CannotGetJdbcConnectionException("redacted"))
        );
        assertEquals(
                RecoveryStatus.ENGAGE_FAILED_READBACK,
                classifyReadBackFailure(new IllegalStateException("closed failure"))
        );
    }

    @Test
    void rejectsFailCloseSecretEnvironmentConflict(@TempDir Path temporary) {
        Map<String, String> environment = failCloseEnvironment(temporary.resolve("credentials"));
        environment.put("NQ_GATEW_SOAK_DB_PASSWORD", "forbidden-direct-secret");

        assertThrows(
                ConfigException.class,
                () -> RuntimeConfig.from(environment, failCloseProperties(temporary))
        );
    }

    @Test
    void rejectsArbitraryFailCloseCredentialDirectory(@TempDir Path temporary) {
        Map<String, String> environment = failCloseEnvironment(temporary.resolve("arbitrary-credentials"));

        assertThrows(
                ConfigException.class,
                () -> RuntimeConfig.from(environment, failCloseProperties(temporary))
        );
    }

    private static FailCloseResult offlineBootstrap(RuntimeConfig config) {
        if (config.mode() != RunMode.OFFLINE_ACCEPTANCE) {
            return FailCloseResult.failure("offline-bootstrap", RecoveryStatus.ENGAGE_FAILED_DB_ENV_INVALID);
        }
        try {
            DatabaseContext database = database(config, true);
            try {
                TransactionTemplate transaction = new TransactionTemplate(
                        new DataSourceTransactionManager(database.dataSource())
                );
                transaction.executeWithoutResult(ignored -> seedOfflineDisengaged(database.jdbc()));
            } catch (RuntimeException ex) {
                return FailCloseResult.failure("offline-bootstrap", seedFailureStatus(ex));
            }
            KillSwitchSnapshot readBack;
            try {
                readBack = service(database.jdbc()).snapshot();
            } catch (RuntimeException ex) {
                return FailCloseResult.failure("offline-bootstrap", classifyReadBackFailure(ex));
            }
            if (readBack.status() != KillSwitchStatus.DISENGAGED) {
                return FailCloseResult.failure("offline-bootstrap", RecoveryStatus.ENGAGE_FAILED_READBACK);
            }
            return new FailCloseResult(
                    RESULT_SCHEMA,
                    "offline-bootstrap",
                    Instant.now(),
                    RecoveryStatus.OFFLINE_FIXTURE_DISENGAGED,
                    readBack.status().name(),
                    readBack.version(),
                    false,
                    false
            );
        } catch (DatabaseStageException ex) {
            return FailCloseResult.failure(
                    "offline-bootstrap",
                    isAuthenticationFailure(ex)
                            ? RecoveryStatus.ENGAGE_FAILED_DB_AUTHENTICATION
                            : isConnectionFailure(ex)
                                    ? RecoveryStatus.ENGAGE_FAILED_DB_UNREACHABLE
                                    : ex.recoveryStatus()
            );
        } catch (ConfigException ex) {
            return FailCloseResult.failure("offline-bootstrap", RecoveryStatus.ENGAGE_FAILED_DB_ENV_INVALID);
        } catch (CannotGetJdbcConnectionException ex) {
            return FailCloseResult.failure("offline-bootstrap", RecoveryStatus.ENGAGE_FAILED_DB_UNREACHABLE);
        } catch (RuntimeException ex) {
            return FailCloseResult.failure(
                    "offline-bootstrap",
                    classifyBootstrapContextFailure(ex)
            );
        }
    }

    private static GateWOkxReadonlySoakCycleTest.CycleResult offlineSample(RuntimeConfig config) {
        if (config.mode() != RunMode.OFFLINE_ACCEPTANCE) {
            return GateWOkxReadonlySoakCycleTest.CycleResult.blocked(
                    "OFFLINE_FIXTURE_MODE_REQUIRED",
                    "UNKNOWN"
            );
        }
        try {
            DatabaseContext database = database(config, false);
            KillSwitchSnapshot snapshot = service(database.jdbc()).snapshot();
            if (snapshot.status() != KillSwitchStatus.DISENGAGED) {
                return GateWOkxReadonlySoakCycleTest.CycleResult.blocked(
                        "OFFLINE_FIXTURE_NOT_DISENGAGED",
                        "UNKNOWN"
                );
            }
            return offlineCycle(snapshot.status());
        } catch (RuntimeException ex) {
            return GateWOkxReadonlySoakCycleTest.CycleResult.failed(
                    "OFFLINE_FIXTURE_DATABASE_FAILURE",
                    "UNKNOWN"
            );
        }
    }

    private static GateWOkxReadonlySoakCycleTest.CycleResult offlineControlledFailure(RuntimeConfig config) {
        if (config.mode() != RunMode.OFFLINE_ACCEPTANCE) {
            return GateWOkxReadonlySoakCycleTest.CycleResult.blocked(
                    "OFFLINE_FIXTURE_MODE_REQUIRED",
                    "UNKNOWN"
            );
        }
        return GateWOkxReadonlySoakCycleTest.CycleResult.failed(
                "CONTROLLED_OFFLINE_CYCLE_3_FAILURE",
                "UNKNOWN"
        );
    }

    private static FailCloseResult verify(RuntimeConfig config) {
        try {
            DatabaseContext database = database(config, false);
            KillSwitchSnapshot snapshot = service(database.jdbc()).snapshot();
            RecoveryStatus status = snapshot.status() == KillSwitchStatus.UNKNOWN
                    ? RecoveryStatus.ENGAGE_STATUS_UNKNOWN
                    : RecoveryStatus.DB_LOCALITY_VERIFIED;
            return new FailCloseResult(
                    RESULT_SCHEMA,
                    "verify",
                    Instant.now(),
                    status,
                    snapshot.status().name(),
                    Math.max(0, snapshot.version()),
                    false,
                    false
            );
        } catch (ConfigException ex) {
            return FailCloseResult.failure("verify", RecoveryStatus.ENGAGE_FAILED_DB_ENV_INVALID);
        } catch (CannotGetJdbcConnectionException ex) {
            return FailCloseResult.failure("verify", RecoveryStatus.ENGAGE_FAILED_DB_UNREACHABLE);
        } catch (RuntimeException ex) {
            return FailCloseResult.failure(
                    "verify",
                    isConnectionFailure(ex)
                            ? RecoveryStatus.ENGAGE_FAILED_DB_UNREACHABLE
                            : RecoveryStatus.ENGAGE_STATUS_UNKNOWN
            );
        }
    }

    private static FailCloseResult engage(RuntimeConfig config) {
        try {
            DatabaseContext database = database(config, false);
            KillSwitchService service = service(database.jdbc());
            KillSwitchSnapshot before = service.snapshot();
            if (before.status() == KillSwitchStatus.UNKNOWN) {
                return FailCloseResult.failure("engage", RecoveryStatus.ENGAGE_STATUS_UNKNOWN);
            }
            boolean writeAttempted = before.status() == KillSwitchStatus.DISENGAGED;
            if (writeAttempted) {
                try {
                    TransactionTemplate transaction = new TransactionTemplate(
                            new DataSourceTransactionManager(database.dataSource())
                    );
                    transaction.execute(ignored -> service.engage(
                            before.version(),
                            "GATEW_SOAK_FAILCLOSE",
                            "GATEW_SOAK_FAILCLOSE",
                            "gatew-failclose-" + UUID.randomUUID()
                    ));
                } catch (RuntimeException ex) {
                    if (isConnectionFailure(ex)) {
                        return FailCloseResult.failure("engage", RecoveryStatus.ENGAGE_FAILED_DB_UNREACHABLE);
                    }
                    return FailCloseResult.failure("engage", RecoveryStatus.ENGAGE_FAILED_WRITE);
                }
            }
            // 事务外重新读取，避免把内存返回对象当作 durable recovery 事实。
            KillSwitchSnapshot readBack;
            try {
                readBack = service(database.jdbc()).snapshot();
            } catch (RuntimeException ex) {
                return FailCloseResult.failure("engage", classifyReadBackFailure(ex));
            }
            if (readBack.status() != KillSwitchStatus.ENGAGED) {
                return FailCloseResult.failure("engage", RecoveryStatus.ENGAGE_FAILED_READBACK);
            }
            return new FailCloseResult(
                    RESULT_SCHEMA,
                    "engage",
                    Instant.now(),
                    statusFor(before.status(), writeAttempted),
                    readBack.status().name(),
                    readBack.version(),
                    false,
                    false
            );
        } catch (ConfigException ex) {
            return FailCloseResult.failure("engage", RecoveryStatus.ENGAGE_FAILED_DB_ENV_INVALID);
        } catch (CannotGetJdbcConnectionException ex) {
            return FailCloseResult.failure("engage", RecoveryStatus.ENGAGE_FAILED_DB_UNREACHABLE);
        } catch (RuntimeException ex) {
            return FailCloseResult.failure(
                    "engage",
                    isConnectionFailure(ex)
                            ? RecoveryStatus.ENGAGE_FAILED_DB_UNREACHABLE
                            : RecoveryStatus.ENGAGE_STATUS_UNKNOWN
            );
        }
    }

    private static RecoveryStatus statusFor(KillSwitchStatus before, boolean writeAttempted) {
        if (before == KillSwitchStatus.ENGAGED) {
            return RecoveryStatus.ENGAGE_NOT_REQUIRED_ALREADY_ENGAGED;
        }
        if (before == KillSwitchStatus.DISENGAGED && writeAttempted) {
            return RecoveryStatus.ENGAGE_SUCCEEDED;
        }
        return RecoveryStatus.ENGAGE_STATUS_UNKNOWN;
    }

    private static RecoveryStatus classifyReadBackFailure(RuntimeException ignored) {
        return RecoveryStatus.ENGAGE_FAILED_READBACK;
    }

    private static RecoveryStatus classifyBootstrapContextFailure(RuntimeException failure) {
        if (isAuthenticationFailure(failure)) return RecoveryStatus.ENGAGE_FAILED_DB_AUTHENTICATION;
        if (isConnectionFailure(failure)) return RecoveryStatus.ENGAGE_FAILED_DB_UNREACHABLE;
        return RecoveryStatus.ENGAGE_FAILED_DB_CONTEXT_INIT;
    }

    private static DatabaseContext database(RuntimeConfig config, boolean migrateOffline) {
        config.assertSafe();
        DriverManagerDataSource dataSource;
        try {
            dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
        } catch (RuntimeException ex) {
            throw databaseStageFailure(migrateOffline, RecoveryStatus.ENGAGE_FAILED_DB_DRIVER_INIT, ex);
        }
        try {
            dataSource.setUrl(withSchema(config.databaseUrl(), config.schema()));
            dataSource.setUsername(config.databaseUser());
            dataSource.setPassword(config.databasePassword());
        } catch (RuntimeException ex) {
            throw databaseStageFailure(migrateOffline, RecoveryStatus.ENGAGE_FAILED_DB_DATASOURCE_CONFIG, ex);
        }
        JdbcTemplate jdbc;
        try {
            jdbc = new JdbcTemplate(dataSource);
            jdbc.setQueryTimeout(5);
        } catch (RuntimeException ex) {
            throw databaseStageFailure(migrateOffline, RecoveryStatus.ENGAGE_FAILED_DB_TEMPLATE_INIT, ex);
        }

        if (migrateOffline) {
            try {
                createOfflineSchema(config);
            } catch (RuntimeException ex) {
                throw new DatabaseStageException(RecoveryStatus.ENGAGE_FAILED_DB_MIGRATION_LOAD, ex);
            }
        }
        Flyway flyway;
        try {
            flyway = Flyway.configure()
                    .dataSource(withSchema(config.databaseUrl(), config.schema()),
                            config.databaseUser(), config.databasePassword())
                    .locations("classpath:db/migration")
                    .schemas(config.schema())
                    .defaultSchema(config.schema())
                    .createSchemas(false)
                    .cleanDisabled(true)
                    .load();
        } catch (RuntimeException ex) {
            throw databaseStageFailure(migrateOffline, RecoveryStatus.ENGAGE_FAILED_DB_MIGRATION_LOAD, ex);
        }
        if (migrateOffline) {
            try {
                flyway.migrate();
            } catch (RuntimeException ex) {
                throw new DatabaseStageException(RecoveryStatus.ENGAGE_FAILED_DB_MIGRATION_EXECUTE, ex);
            }
        }
        try {
            flyway.validate();
        } catch (RuntimeException ex) {
            throw databaseStageFailure(migrateOffline, RecoveryStatus.ENGAGE_FAILED_DB_MIGRATION_VALIDATE, ex);
        }
        try {
            MigrationInfo current = flyway.info().current();
            if (current == null || !"35".equals(current.getVersion().getVersion())
                    || flyway.info().pending().length != 0) {
                throw new ConfigException();
            }
        } catch (ConfigException ex) {
            if (migrateOffline) {
                throw new DatabaseStageException(RecoveryStatus.ENGAGE_FAILED_DB_MIGRATION_HISTORY, ex);
            }
            throw ex;
        } catch (RuntimeException ex) {
            throw databaseStageFailure(migrateOffline, RecoveryStatus.ENGAGE_FAILED_DB_MIGRATION_HISTORY, ex);
        }
        try {
            String serverAddress = jdbc.queryForObject("SELECT host(inet_server_addr())", String.class);
            if (!isLoopbackAddress(serverAddress)) throw new ConfigException();
        } catch (ConfigException ex) {
            if (migrateOffline) {
                throw new DatabaseStageException(RecoveryStatus.ENGAGE_FAILED_DB_LOCALITY, ex);
            }
            throw ex;
        } catch (RuntimeException ex) {
            throw databaseStageFailure(migrateOffline, RecoveryStatus.ENGAGE_FAILED_DB_LOCALITY, ex);
        }
        return new DatabaseContext(dataSource, jdbc);
    }

    private static KillSwitchService service(JdbcTemplate jdbc) {
        return new KillSwitchService(new JdbcKillSwitchStateRepository(jdbc), Clock.systemUTC());
    }

    private static void seedOfflineDisengaged(JdbcTemplate jdbc) {
        KillSwitchSnapshot current = service(jdbc).snapshot();
        if (current.status() == KillSwitchStatus.DISENGAGED
                && current.version() == 2
                && "OFFLINE_ACCEPTANCE_FIXTURE".equals(current.reasonCode())) {
            return;
        }
        if (current.status() != KillSwitchStatus.ENGAGED || current.version() != 1) {
            throw new SeedStageException(RecoveryStatus.ENGAGE_FAILED_DB_SEED_INITIAL_STATE);
        }
        Instant occurredAt = Instant.now();
        int updated;
        try {
            updated = jdbc.update(
                    """
                            UPDATE kill_switch_states
                            SET status = 'DISENGAGED', version = 2,
                                reason_code = 'OFFLINE_ACCEPTANCE_FIXTURE',
                                source = 'OFFLINE_ACCEPTANCE_FIXTURE', updated_at = ?,
                                updated_by = 'gatew-offline-acceptance',
                                trace_id = 'gatew-offline-acceptance-fixture'
                            WHERE scope = 'GLOBAL_TRADING' AND version = 1 AND status = 'ENGAGED'
                            """,
                    Timestamp.from(occurredAt)
            );
        } catch (RuntimeException ex) {
            throw new SeedStageException(RecoveryStatus.ENGAGE_FAILED_DB_SEED_UPDATE, ex);
        }
        if (updated != 1) {
            throw new SeedStageException(RecoveryStatus.ENGAGE_FAILED_DB_SEED_UPDATE);
        }
        try {
            jdbc.update(
                    """
                            INSERT INTO kill_switch_events (
                                id, scope, from_status, to_status, state_version, reason_code,
                                source, actor_id, trace_id, occurred_at
                            ) VALUES (?, 'GLOBAL_TRADING', 'ENGAGED', 'DISENGAGED', 2,
                                      'OFFLINE_ACCEPTANCE_FIXTURE', 'OFFLINE_ACCEPTANCE_FIXTURE',
                                      'gatew-offline-acceptance', 'gatew-offline-acceptance-fixture', ?)
                            """,
                    UUID.randomUUID(),
                    Timestamp.from(occurredAt)
            );
        } catch (RuntimeException ex) {
            throw new SeedStageException(RecoveryStatus.ENGAGE_FAILED_DB_SEED_EVENT, ex);
        }
    }

    private static GateWOkxReadonlySoakCycleTest.CycleResult offlineCycle(KillSwitchStatus status) {
        return new GateWOkxReadonlySoakCycleTest.CycleResult(
                GateWOkxReadonlySoakCycleTest.LAUNCHER_SCHEMA_VERSION,
                "gatew-cycle-" + UUID.randomUUID().toString().replace("-", ""),
                Instant.now(),
                0,
                "PASSED_READ_ONLY",
                "OFFLINE_READONLY_FIXTURE_ACCEPTED",
                "NOT_CALLED",
                "METADATA_READ_ONLY",
                status.name(),
                false,
                false,
                "OFFLINE_LOCAL_FIXTURE_READ",
                GateWOkxReadonlySoakCycleTest.ProbeStatus.SUCCEEDED,
                GateWOkxReadonlySoakCycleTest.ProbeStatus.SUCCEEDED,
                "gatew-soak-" + UUID.randomUUID()
        );
    }

    private static void writeResult(RuntimeConfig config, Object result) {
        byte[] json = null;
        Path temporary = null;
        try {
            Path output = config.validatedOutput();
            json = result instanceof GateWOkxReadonlySoakCycleTest.CycleResult cycle
                    ? GateWOkxReadonlySoakCycleTest.EvidenceSanitizer.serialize(OBJECT_MAPPER, cycle)
                    : OBJECT_MAPPER.writeValueAsBytes(result);
            if (!schemaSafe(result)) throw new ConfigException();
            temporary = output.resolveSibling(output.getFileName() + ".tmp-" + UUID.randomUUID());
            Files.write(temporary, json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("failed to write sanitized fail-close result");
        } finally {
            if (json != null) Arrays.fill(json, (byte) 0);
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // 文件名和 payload 均为 closed DTO；删除失败不能覆盖 recovery 分类。
                }
            }
        }
    }

    private static boolean schemaSafe(Object result) {
        if (result instanceof GateWOkxReadonlySoakCycleTest.CycleResult cycle) {
            try {
                GateWOkxReadonlySoakCycleTest.EvidenceSanitizer.validateDto(cycle);
                return true;
            } catch (RuntimeException ex) {
                return false;
            }
        }
        if (!(result instanceof FailCloseResult value)) return false;
        return RESULT_SCHEMA.equals(value.schemaVersion())
                && SetValues.ACTIONS.contains(value.action())
                && value.observedAt() != null
                && value.recoveryStatus() != null
                && SetValues.KILL_SWITCH_STATES.contains(value.killSwitchObservedState())
                && value.killSwitchVersion() >= 0
                && !value.credentialAccessed()
                && !value.networkCalled()
                && (value.recoveryStatus().safe() == "ENGAGED".equals(value.killSwitchObservedState())
                || value.recoveryStatus() == RecoveryStatus.DB_LOCALITY_VERIFIED
                || value.recoveryStatus() == RecoveryStatus.OFFLINE_FIXTURE_DISENGAGED);
    }

    private static String withSchema(String url, String schema) {
        String searchPath = schema.startsWith("gatew_offline_")
                ? schema + ",public"
                : schema;
        return url + "?currentSchema=" + searchPath + "&connectTimeout=5&socketTimeout=5";
    }

    private static void createOfflineSchema(RuntimeConfig config) {
        DriverManagerDataSource schemaDataSource = new DriverManagerDataSource();
        schemaDataSource.setDriverClassName("org.postgresql.Driver");
        schemaDataSource.setUrl(withSchema(config.databaseUrl(), "public"));
        schemaDataSource.setUsername(config.databaseUser());
        schemaDataSource.setPassword(config.databasePassword());
        JdbcTemplate schemaJdbc = new JdbcTemplate(schemaDataSource);
        schemaJdbc.setQueryTimeout(5);
        Boolean exists = schemaJdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_namespace WHERE nspname = ?)",
                Boolean.class,
                config.schema()
        );
        if (Boolean.TRUE.equals(exists)) {
            throw new ConfigException();
        }
        schemaJdbc.execute(createOfflineSchemaSql(config.schema()));
    }

    private static String createOfflineSchemaSql(String schema) {
        if (!OFFLINE_SCHEMA.matcher(schema).matches()) {
            throw new ConfigException();
        }
        return "CREATE SCHEMA " + schema;
    }

    private static RuntimeException databaseStageFailure(
            boolean migrateOffline,
            RecoveryStatus recoveryStatus,
            RuntimeException failure
    ) {
        return migrateOffline ? new DatabaseStageException(recoveryStatus, failure) : failure;
    }

    private static RecoveryStatus seedFailureStatus(Throwable failure) {
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth++ < 12) {
            if (current instanceof SeedStageException seedStageException) {
                return seedStageException.recoveryStatus();
            }
            current = current.getCause();
        }
        return RecoveryStatus.ENGAGE_FAILED_DB_SEED_TRANSACTION;
    }

    private static boolean isConnectionFailure(Throwable failure) {
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth++ < 12) {
            if (current instanceof CannotGetJdbcConnectionException
                    || current instanceof java.sql.SQLNonTransientConnectionException
                    || current instanceof java.sql.SQLTransientConnectionException) {
                return true;
            }
            if (current instanceof java.sql.SQLException sqlException
                    && sqlException.getSQLState() != null
                    && sqlException.getSQLState().startsWith("08")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isAuthenticationFailure(Throwable failure) {
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth++ < 12) {
            if (current instanceof java.sql.SQLException sqlException
                    && sqlException.getSQLState() != null
                    && sqlException.getSQLState().startsWith("28")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isLoopbackAddress(String address) {
        return "127.0.0.1".equals(address) || "::1".equals(address)
                || "0:0:0:0:0:0:0:1".equals(address);
    }

    enum RunMode {
        REAL,
        OFFLINE_ACCEPTANCE
    }

    enum RecoveryStatus {
        DB_LOCALITY_VERIFIED,
        OFFLINE_FIXTURE_DISENGAGED,
        ENGAGE_NOT_REQUIRED_ALREADY_ENGAGED,
        ENGAGE_SUCCEEDED,
        ENGAGE_FAILED_DB_ENV_INVALID,
        ENGAGE_FAILED_DB_AUTHENTICATION,
        ENGAGE_FAILED_DB_UNREACHABLE,
        ENGAGE_FAILED_DB_CONTEXT_INIT,
        ENGAGE_FAILED_DB_DRIVER_INIT,
        ENGAGE_FAILED_DB_DATASOURCE_CONFIG,
        ENGAGE_FAILED_DB_TEMPLATE_INIT,
        ENGAGE_FAILED_DB_LOCALITY,
        ENGAGE_FAILED_DB_MIGRATION_LOAD,
        ENGAGE_FAILED_DB_MIGRATION_EXECUTE,
        ENGAGE_FAILED_DB_MIGRATION_VALIDATE,
        ENGAGE_FAILED_DB_MIGRATION_HISTORY,
        ENGAGE_FAILED_DB_SEED_INITIAL_STATE,
        ENGAGE_FAILED_DB_SEED_UPDATE,
        ENGAGE_FAILED_DB_SEED_EVENT,
        ENGAGE_FAILED_DB_SEED_TRANSACTION,
        ENGAGE_FAILED_WRITE,
        ENGAGE_FAILED_READBACK,
        ENGAGE_STATUS_UNKNOWN;

        boolean safe() {
            return this == ENGAGE_NOT_REQUIRED_ALREADY_ENGAGED || this == ENGAGE_SUCCEEDED;
        }
    }

    @JsonPropertyOrder({
            "schemaVersion", "action", "observedAt", "recoveryStatus", "killSwitchObservedState",
            "killSwitchVersion", "credentialAccessed", "networkCalled"
    })
    record FailCloseResult(
            String schemaVersion,
            String action,
            Instant observedAt,
            RecoveryStatus recoveryStatus,
            String killSwitchObservedState,
            long killSwitchVersion,
            boolean credentialAccessed,
            boolean networkCalled
    ) {
        static FailCloseResult failure(String action, RecoveryStatus status) {
            return new FailCloseResult(
                    RESULT_SCHEMA,
                    action,
                    Instant.now(),
                    status,
                    "UNKNOWN",
                    0,
                    false,
                    false
            );
        }
    }

    private record DatabaseContext(DriverManagerDataSource dataSource, JdbcTemplate jdbc) {
    }

    private record RuntimeConfig(
            String action,
            String runId,
            RunMode mode,
            String databaseUrl,
            String databaseUser,
            String databasePassword,
            String schema,
            Path resultFile
    ) {
        static RuntimeConfig from(Map<String, String> environment, Properties properties) {
            String action = property(properties, ACTION_PROPERTY);
            String runId = property(properties, RUN_ID_PROPERTY);
            RunMode mode;
            try {
                mode = RunMode.valueOf(value(environment, "NQ_GATEW_RUN_MODE"));
            } catch (RuntimeException ex) {
                throw new ConfigException();
            }
            return new RuntimeConfig(
                    action,
                    runId,
                    mode,
                    value(environment, "NQ_GATEW_SOAK_DB_URL"),
                    value(environment, "NQ_GATEW_SOAK_DB_USER"),
                    credential(environment, runId, action),
                    value(environment, "NQ_GATEW_SOAK_DB_SCHEMA"),
                    Path.of(property(properties, RESULT_FILE_PROPERTY)).toAbsolutePath().normalize()
            );
        }

        void assertSafe() {
            if (!SetValues.ACTIONS.contains(action)
                    || !RUN_ID.matcher(runId).matches()
                    || databaseUrl.isBlank()
                    || databaseUser.isBlank()
                    || databasePassword.isBlank()
                    || !safeDatabaseTarget(databaseUrl)
                    || !safeSchema(mode, schema)) {
                throw new ConfigException();
            }
        }

        Path validatedOutput() throws IOException {
            assertSafe();
            Path stateRoot = Path.of(STATE_ROOT).toAbsolutePath().normalize();
            Path runRoot = stateRoot.resolve(runId).normalize();
            boolean workerEvidenceAction = "offline-bootstrap".equals(action)
                    || "offline-sample".equals(action)
                    || "offline-controlled-failure".equals(action);
            Path expectedLeaf = runRoot.resolve(workerEvidenceAction ? "evidence" : "control");
            Path parent = resultFile.getParent();
            if (!runRoot.startsWith(stateRoot)
                    || parent == null
                    || !parent.equals(expectedLeaf)
                    || !parent.toRealPath().equals(expectedLeaf.toRealPath())) {
                throw new ConfigException();
            }
            assertNoSymbolicLinkComponents(stateRoot);
            assertNoSymbolicLinkComponents(runRoot);
            assertNoSymbolicLinkComponents(parent);
            if (Files.exists(resultFile, LinkOption.NOFOLLOW_LINKS)) {
                assertNoSymbolicLinkComponents(resultFile);
            }
            return resultFile;
        }

        private static String credential(Map<String, String> environment, String runId, String action) {
            if (!GateWOkxReadonlySoakCycleTest.SYSTEMD_CREDENTIAL_SOURCE.equals(
                    value(environment, "NQ_GATEW_SECRET_SOURCE")
            ) || !"true".equals(value(environment, "NQ_GATEW_FORMAL_SYSTEMD"))
                    || !value(environment, "NQ_GATEW_SOAK_DB_PASSWORD").isBlank()
                    || !RUN_ID.matcher(runId).matches()) {
                throw new ConfigException();
            }
            try {
                Path root = Path.of(value(environment, "CREDENTIALS_DIRECTORY")).toAbsolutePath().normalize();
                boolean workerAction = action.startsWith("offline-");
                Path expected = Path.of(
                        "/run/credentials",
                        (workerAction ? "nq-gatew-soak@" : "nq-gatew-soak-failclose@")
                                + runId + ".service"
                ).toAbsolutePath().normalize();
                Path path = root.resolve("db-password").normalize();
                if (!root.equals(expected)
                        || !path.getParent().equals(root)
                        || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || Files.size(path) <= 0
                        || Files.size(path) > 16_384) {
                    throw new ConfigException();
                }
                assertNoSymbolicLinkComponents(root);
                assertNoSymbolicLinkComponents(path);
                if (!root.toRealPath().equals(expected.toRealPath())) throw new ConfigException();
                try (var entries = Files.list(root)) {
                    if (entries.count() != 1) throw new ConfigException();
                }
                String secret = Files.readString(path, StandardCharsets.UTF_8).strip();
                if (secret.isBlank() || secret.indexOf('\0') >= 0) throw new ConfigException();
                return secret;
            } catch (RuntimeException | IOException ex) {
                throw new ConfigException();
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

        private static boolean safeDatabaseTarget(String url) {
            try {
                if (!url.startsWith("jdbc:postgresql://")) return false;
                URI uri = URI.create(url.substring("jdbc:".length()));
                String path = uri.getPath();
                return uri.getUserInfo() == null
                        && uri.getRawQuery() == null
                        && uri.getRawFragment() == null
                        && ("127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost()))
                        && path != null
                        && path.length() > 1
                        && path.substring(1).toLowerCase(Locale.ROOT).matches(".*(gatew|soak).*");
            } catch (RuntimeException ex) {
                return false;
            }
        }

        private static boolean safeSchema(RunMode mode, String schema) {
            if (mode == RunMode.OFFLINE_ACCEPTANCE) return OFFLINE_SCHEMA.matcher(schema).matches();
            return "public".equals(schema);
        }
    }

    private static final class SetValues {
        private static final List<String> ACTIONS = List.of(
                "offline-bootstrap", "offline-sample", "offline-controlled-failure", "verify", "engage"
        );
        private static final List<String> KILL_SWITCH_STATES = List.of("UNKNOWN", "DISENGAGED", "ENGAGED");

        private SetValues() {
        }
    }

    private static Map<String, String> failCloseEnvironment(Path credentialDirectory) {
        Map<String, String> environment = new java.util.HashMap<>();
        environment.put("NQ_GATEW_RUN_MODE", "OFFLINE_ACCEPTANCE");
        environment.put("NQ_GATEW_SOAK_DB_URL", "jdbc:postgresql://127.0.0.1:55432/gatew_soak");
        environment.put("NQ_GATEW_SOAK_DB_USER", "gatew_soak");
        environment.put("NQ_GATEW_SOAK_DB_SCHEMA", "gatew_offline_0123abcd");
        environment.put("NQ_GATEW_SECRET_SOURCE", GateWOkxReadonlySoakCycleTest.SYSTEMD_CREDENTIAL_SOURCE);
        environment.put("NQ_GATEW_FORMAL_SYSTEMD", "true");
        environment.put("CREDENTIALS_DIRECTORY", credentialDirectory.toString());
        return environment;
    }

    private static Properties failCloseProperties(Path temporary) {
        Properties properties = new Properties();
        properties.setProperty(ACTION_PROPERTY, "engage");
        properties.setProperty(RUN_ID_PROPERTY, "gatew-soak-20260718T000000Z-0123abcd");
        properties.setProperty(RESULT_FILE_PROPERTY, temporary.resolve("result.json").toString());
        return properties;
    }

    private static String property(Properties properties, String name) {
        String value = Objects.toString(properties.getProperty(name), "").trim();
        if (value.isBlank()) throw new ConfigException();
        return value;
    }

    private static String value(Map<String, String> environment, String name) {
        return Objects.toString(environment.get(name), "").trim();
    }

    private static final class ConfigException extends RuntimeException {
        private ConfigException() {
            super("GateW fail-close configuration is invalid", null, false, false);
        }
    }

    private static final class DatabaseStageException extends RuntimeException {
        private final RecoveryStatus recoveryStatus;

        private DatabaseStageException(RecoveryStatus recoveryStatus, RuntimeException cause) {
            super("GateW offline database stage failed", cause, false, false);
            this.recoveryStatus = recoveryStatus;
        }

        private RecoveryStatus recoveryStatus() {
            return recoveryStatus;
        }
    }

    private static final class SeedStageException extends RuntimeException {
        private final RecoveryStatus recoveryStatus;

        private SeedStageException(RecoveryStatus recoveryStatus) {
            this(recoveryStatus, null);
        }

        private SeedStageException(RecoveryStatus recoveryStatus, RuntimeException cause) {
            super("GateW offline seed stage failed", cause, false, false);
            this.recoveryStatus = recoveryStatus;
        }

        private RecoveryStatus recoveryStatus() {
            return recoveryStatus;
        }
    }
}
