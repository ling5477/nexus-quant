package com.guidinglight.nexusquant.app.gatew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadOperation;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadResult;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadTransport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * GateW soak test-support 的 fail-closed 配置、credential 与 evidence 单元回归。
 */
@SpringJUnitConfig(classes = GateWOkxReadonlySoakCycleTest.JacksonContext.class)
class GateWOkxReadonlySoakSupportTest {

    private static final String REAL_RUN_ID = "gatew-soak-20260718T000000Z-0123abcd";
    private static final String OFFLINE_RUN_ID = "gatew-soak-20260718T000001Z-1234abcd";
    private static final String SYMLINK_RUN_ID = "gatew-soak-20260718T000002Z-2345abcd";

    @TempDir
    Path tempDir;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void acceptsOnlyDedicatedLoopbackSoakConfiguration() {
        GateWOkxReadonlySoakCycleTest.SafetyConfig config = config(baseEnvironment(), baseProperties());

        config.assertSafe();

        assertEquals("SafetyConfig[REDACTED]", config.toString());
        assertEquals("gatew_soak_unit", config.databaseName());
        assertEquals(List.of("BTC", "USDT"), config.currencies());
    }

    @Test
    void rejectsMissingSoakProfile() {
        assertConfigBlocked(Map.of("SPRING_PROFILES_ACTIVE", ""), Map.of(), "SOAK_PROFILE_REQUIRED");
    }

    @Test
    void rejectsAdditionalProfile() {
        assertConfigBlocked(
                Map.of("SPRING_PROFILES_ACTIVE", "gatew-okx-readonly-soak,local"),
                Map.of(),
                "SOAK_PROFILE_REQUIRED"
        );
    }

    @Test
    void rejectsDisabledSoakFeatureFlag() {
        assertConfigBlocked(
                Map.of("NQ_GATEW_OKX_READONLY_SOAK_ENABLED", "false"),
                Map.of(),
                "SOAK_FEATURE_FLAG_REQUIRED"
        );
    }

    @Test
    void rejectsCiEnvironment() {
        assertConfigBlocked(Map.of("CI", "true"), Map.of(), "SOAK_OUTBOUND_FORBIDDEN_IN_CI");
    }

    @Test
    void rejectsNoOutboundEnvironment() {
        assertConfigBlocked(Map.of("NQ_NO_OUTBOUND", "true"), Map.of(), "SOAK_OUTBOUND_FORBIDDEN_IN_CI");
    }

    @Test
    void rejectsLiveEnablement() {
        assertConfigBlocked(Map.of("NQ_LIVE_ENABLED", "true"), Map.of(), "NQ_LIVE_ENABLED_MUST_BE_FALSE");
    }

    @Test
    void rejectsOrderSubmissionEnablement() {
        assertConfigBlocked(
                Map.of("NQ_REAL_ORDER_SUBMISSION_ENABLED", "true"),
                Map.of(),
                "NQ_REAL_ORDER_SUBMISSION_ENABLED_MUST_BE_FALSE"
        );
    }

    @Test
    void rejectsTransferEnablement() {
        assertConfigBlocked(Map.of("NQ_TRANSFER_ENABLED", "true"), Map.of(), "NQ_TRANSFER_ENABLED_MUST_BE_FALSE");
    }

    @Test
    void rejectsWithdrawEnablement() {
        assertConfigBlocked(Map.of("NQ_WITHDRAW_ENABLED", "true"), Map.of(), "NQ_WITHDRAW_ENABLED_MUST_BE_FALSE");
    }

    @Test
    void rejectsRemoteDatabase() {
        assertConfigBlocked(
                Map.of("NQ_GATEW_SOAK_DB_URL", "jdbc:postgresql://db.example.invalid:5432/gatew_soak_unit"),
                Map.of(),
                "SOAK_DATABASE_NOT_LOCAL"
        );
    }

    @Test
    void rejectsOrdinaryLocalDatabaseName() {
        assertConfigBlocked(
                Map.of("NQ_GATEW_SOAK_DB_URL", "jdbc:postgresql://127.0.0.1:5432/nexus_quant"),
                Map.of(),
                "SOAK_DATABASE_NOT_LOCAL"
        );
    }

    @Test
    void rejectsDatabaseUrlWithQueryParameters() {
        assertConfigBlocked(
                Map.of("NQ_GATEW_SOAK_DB_URL", "jdbc:postgresql://127.0.0.1:5432/gatew_soak_unit?ssl=true"),
                Map.of(),
                "SOAK_DATABASE_NOT_LOCAL"
        );
    }

    @Test
    void rejectsDirectOkxCredentialEnvironmentInput() {
        assertConfigBlocked(Map.of("NQ_OKX_API_KEY", "forbidden"), Map.of(), "NQ_OKX_API_KEY_DIRECT_INPUT_FORBIDDEN");
    }

    @Test
    void rejectsDirectOkxCredentialSystemPropertyInput() {
        assertConfigBlocked(Map.of(), Map.of("NQ_OKX_API_SECRET", "forbidden"), "NQ_OKX_API_SECRET_DIRECT_INPUT_FORBIDDEN");
    }

    @Test
    void rejectsUnsupportedAction() {
        assertConfigBlocked(Map.of(), Map.of(GateWOkxReadonlySoakCycleTest.ACTION_PROPERTY, "trade"), "SOAK_ACTION_INVALID");
    }

    @Test
    void requiresExplicitRealOrOfflineIsolatedRunMode() {
        assertConfigBlocked(Map.of("NQ_GATEW_RUN_MODE", "REAL"), Map.of(), "SOAK_RUN_MODE_INVALID");

        Map<String, String> offline = baseEnvironment();
        offline.put("NQ_GATEW_RUN_MODE", GateWOkxReadonlySoakCycleTest.OFFLINE_ISOLATED_ACCEPTANCE);
        config(offline, baseProperties()).assertSafe();
    }

    @Test
    void rejectsInvalidCurrencyAllowlist() {
        assertFromBlocked(Map.of("NQ_GATEW_SOAK_CURRENCIES", "BTC,USDT,ETH,SOL"), "SOAK_CURRENCY_ALLOWLIST_INVALID");
    }

    @Test
    void rejectsMissingAccountReference() {
        assertFromBlocked(Map.of("NQ_GATEW_SOAK_ACCOUNT_ID", ""), "NQ_GATEW_SOAK_ACCOUNT_ID_REQUIRED");
    }

    @Test
    void engageActionDoesNotRequireCredentialMaterialOrReference() {
        Map<String, String> environment = baseEnvironment();
        environment.remove("NQ_ACCOUNT_CREDENTIALS_MASTER_KEY");
        environment.remove("NQ_GATEW_SOAK_OWNER_ID");
        environment.remove("NQ_GATEW_SOAK_ACCOUNT_ID");
        environment.remove("NQ_GATEW_SOAK_CURRENCIES");
        Properties properties = baseProperties();
        properties.setProperty(GateWOkxReadonlySoakCycleTest.ACTION_PROPERTY, "engage");

        GateWOkxReadonlySoakCycleTest.SafetyConfig config = config(environment, properties);

        config.assertSafe();
        assertEquals(0, config.ownerId());
        assertTrue(config.currencies().isEmpty());
    }

    @Test
    void bootstrapAndPrerequisiteDoNotReadCredentialMasterKey() {
        for (String action : List.of("bootstrap", "prerequisite")) {
            Map<String, String> environment = baseEnvironment();
            environment.remove("NQ_ACCOUNT_CREDENTIALS_MASTER_KEY");
            environment.remove("NQ_GATEW_SOAK_CURRENCIES");
            Properties properties = baseProperties();
            properties.setProperty(GateWOkxReadonlySoakCycleTest.ACTION_PROPERTY, action);

            GateWOkxReadonlySoakCycleTest.SafetyConfig config = config(environment, properties);

            config.assertSafe();
            assertTrue(config.masterKey().isEmpty());
            if ("bootstrap".equals(action)) {
                assertEquals(0, config.ownerId());
            } else {
                assertEquals(7, config.ownerId());
            }
        }
    }

    @Test
    void rejectsSystemdCredentialAndSecretEnvironmentConflict() {
        Map<String, String> environment = formalSystemdEnvironment();

        GateWOkxReadonlySoakCycleTest.SafeBlockException error = assertThrows(
                GateWOkxReadonlySoakCycleTest.SafeBlockException.class,
                () -> config(environment, baseProperties())
        );

        assertEquals("SYSTEMD_CREDENTIAL_ENV_CONFLICT", error.reasonCode());
    }

    @Test
    void rejectsArbitrarySystemdCredentialDirectory() {
        Map<String, String> environment = formalSystemdEnvironment();
        environment.remove("NQ_GATEW_SOAK_DB_PASSWORD");
        environment.remove("NQ_ACCOUNT_CREDENTIALS_MASTER_KEY");
        environment.put("CREDENTIALS_DIRECTORY", tempDir.resolve("arbitrary-credentials").toString());

        GateWOkxReadonlySoakCycleTest.SafeBlockException error = assertThrows(
                GateWOkxReadonlySoakCycleTest.SafeBlockException.class,
                () -> config(environment, baseProperties())
        );

        assertEquals("SYSTEMD_CREDENTIAL_SOURCE_INVALID", error.reasonCode());
    }

    @Test
    void acceptsReadOnlyCredentialMetadata() {
        safeCredential().assertSafe();
    }

    @Test
    void rejectsTradePermissionMetadata() {
        assertCredentialBlocked(credential("TRADE", false, true, "SUCCEEDED", "PASSED"),
                "CREDENTIAL_PERMISSION_NOT_READONLY");
    }

    @Test
    void rejectsWithdrawPermissionMetadata() {
        assertCredentialBlocked(credential("READ_ONLY", true, true, "SUCCEEDED", "PASSED"),
                "UNSAFE_CREDENTIAL_PERMISSIONS");
    }

    @Test
    void rejectsMissingIpAllowlistRequirement() {
        assertCredentialBlocked(credential("READ_ONLY", false, false, "SUCCEEDED", "PASSED"),
                "IP_ALLOWLIST_REQUIRED");
    }

    @Test
    void rejectsFailedIpAllowlistProbeMetadata() {
        assertCredentialBlocked(credential("READ_ONLY", false, true, "SUCCEEDED", "FAILED"),
                "IP_ALLOWLIST_REQUIRED");
    }

    @Test
    void rejectsFailedPermissionProbeMetadata() {
        assertCredentialBlocked(credential("READ_ONLY", false, true, "FAILED", "PASSED"),
                "CREDENTIAL_PERMISSION_NOT_READONLY");
    }

    @Test
    void allowsOnlyAccountConfigurationAndBalanceOperations() {
        OkxPrivateReadTransport delegate = (request, credential, environment) -> new OkxPrivateReadResult(
                request.operation(), Set.of("READ_ONLY"), 0, true, List.of(), List.of(), true, Instant.EPOCH
        );
        GateWOkxReadonlySoakCycleTest.CountingTransport transport =
                new GateWOkxReadonlySoakCycleTest.CountingTransport(delegate);

        transport.execute(OkxPrivateReadRequest.accountConfiguration(), null, OkxPrivateEnvironment.PRODUCTION);
        transport.execute(OkxPrivateReadRequest.accountBalance(List.of("BTC")), null, OkxPrivateEnvironment.PRODUCTION);

        assertEquals(2, transport.calls());
        assertEquals("ACCOUNT_CONFIG_AND_BALANCE_READ", transport.endpointCategory());
        assertEquals(GateWOkxReadonlySoakCycleTest.ProbeStatus.SUCCEEDED, transport.accountConfigProbeStatus());
        assertEquals(GateWOkxReadonlySoakCycleTest.ProbeStatus.SUCCEEDED, transport.balanceProbeStatus());
    }

    @Test
    void classifiesIncompleteBalanceWithoutPersistingBalanceValues() {
        OkxPrivateReadTransport delegate = (request, credential, environment) -> new OkxPrivateReadResult(
                request.operation(), Set.of("READ_ONLY"), 0,
                request.operation() == OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ,
                List.of(), List.of(), true, Instant.EPOCH
        );
        GateWOkxReadonlySoakCycleTest.CountingTransport transport =
                new GateWOkxReadonlySoakCycleTest.CountingTransport(delegate);

        transport.execute(OkxPrivateReadRequest.accountConfiguration(), null, OkxPrivateEnvironment.PRODUCTION);
        transport.execute(OkxPrivateReadRequest.accountBalance(List.of("BTC")), null, OkxPrivateEnvironment.PRODUCTION);

        assertEquals(GateWOkxReadonlySoakCycleTest.ProbeStatus.SUCCEEDED, transport.accountConfigProbeStatus());
        assertEquals(GateWOkxReadonlySoakCycleTest.ProbeStatus.FAILED, transport.balanceProbeStatus());
    }

    @Test
    void rejectsForbiddenReadOperationBeforeDelegateCall() {
        OkxPrivateReadTransport delegate = (request, credential, environment) -> {
            throw new AssertionError("delegate must not be called");
        };
        GateWOkxReadonlySoakCycleTest.CountingTransport transport =
                new GateWOkxReadonlySoakCycleTest.CountingTransport(delegate);

        GateWOkxReadonlySoakCycleTest.SafeBlockException error = assertThrows(
                GateWOkxReadonlySoakCycleTest.SafeBlockException.class,
                () -> transport.execute(
                        OkxPrivateReadRequest.openOrders("BTC-USDT", 1),
                        null,
                        OkxPrivateEnvironment.PRODUCTION
                )
        );

        assertEquals("FORBIDDEN_ENDPOINT_ATTEMPTED", error.reasonCode());
        assertEquals(0, transport.calls());
        assertEquals(GateWOkxReadonlySoakCycleTest.ProbeStatus.NOT_RUN, transport.accountConfigProbeStatus());
        assertEquals(GateWOkxReadonlySoakCycleTest.ProbeStatus.NOT_RUN, transport.balanceProbeStatus());
    }

    @Test
    void writesOnlySanitizedCycleResultBelowCanonicalRealSoakRoot() throws Exception {
        GateWOkxReadonlySoakCycleTest.SafetyConfig config = config(baseEnvironment(), baseProperties());
        GateWOkxReadonlySoakCycleTest.CycleResult result =
                GateWOkxReadonlySoakCycleTest.CycleResult.blocked("API_KEY_REQUIRED", "UNKNOWN");
        Files.createDirectories(config.resultFile().getParent());

        GateWOkxReadonlySoakCycleTest.writeSanitizedResult(config, result, objectMapper);

        String json = Files.readString(config.resultFile(), StandardCharsets.UTF_8).toLowerCase();
        assertTrue(json.contains("api_key_required"));
        assertFalse(json.contains("passphrase"));
        assertFalse(json.contains("rawresponse"));
        assertFalse(json.contains("http://"));
        assertFalse(json.contains("https://"));
    }

    @Test
    void writesOnlySanitizedCycleResultBelowCanonicalOfflineSmokeRoot() throws Exception {
        Path output = tempDir.resolve("target/gatew-okx-readonly-soak/offline-smoke")
                .resolve(OFFLINE_RUN_ID)
                .resolve("cycle.json");
        Files.createDirectories(output.getParent());
        Properties properties = baseProperties();
        properties.setProperty(GateWOkxReadonlySoakCycleTest.RESULT_FILE_PROPERTY, output.toString());
        GateWOkxReadonlySoakCycleTest.SafetyConfig config = config(baseEnvironment(), properties);

        GateWOkxReadonlySoakCycleTest.writeSanitizedResult(
                config,
                GateWOkxReadonlySoakCycleTest.CycleResult.blocked("SAFE_BLOCK", "UNKNOWN"),
                objectMapper
        );

        assertTrue(Files.isRegularFile(output));
    }

    @Test
    void acceptsSafeSuccessBlockedAndFailedLauncherContracts() throws Exception {
        GateWOkxReadonlySoakCycleTest.CycleResult success = result(
                "PASSED_READ_ONLY",
                "READ_ONLY_SAMPLE_ACCEPTED",
                "SUCCESS_2XX",
                "READ_ONLY_WITH_IP_ALLOWLIST",
                "ENGAGED",
                true,
                true,
                "ACCOUNT_CONFIG_AND_BALANCE_READ",
                GateWOkxReadonlySoakCycleTest.ProbeStatus.SUCCEEDED,
                GateWOkxReadonlySoakCycleTest.ProbeStatus.SUCCEEDED
        );
        GateWOkxReadonlySoakCycleTest.CycleResult blocked = result(
                "BLOCKED",
                "PERMISSION_BLOCKED",
                "AUTH_ERROR",
                "UNSAFE_OR_INCOMPLETE",
                "ENGAGED",
                true,
                true,
                "ACCOUNT_CONFIGURATION_READ",
                GateWOkxReadonlySoakCycleTest.ProbeStatus.SUCCEEDED,
                GateWOkxReadonlySoakCycleTest.ProbeStatus.NOT_RUN
        );
        GateWOkxReadonlySoakCycleTest.CycleResult failed = result(
                "HARD_FAILURE",
                "PARTIAL_RESPONSE",
                "NOT_AVAILABLE",
                "UNKNOWN",
                "ENGAGED",
                true,
                true,
                "ACCOUNT_CONFIG_AND_BALANCE_READ",
                GateWOkxReadonlySoakCycleTest.ProbeStatus.SUCCEEDED,
                GateWOkxReadonlySoakCycleTest.ProbeStatus.FAILED
        );

        byte[] successJson = GateWOkxReadonlySoakCycleTest.EvidenceSanitizer.serialize(objectMapper, success);
        byte[] blockedJson = GateWOkxReadonlySoakCycleTest.EvidenceSanitizer.serialize(objectMapper, blocked);
        byte[] failedJson = GateWOkxReadonlySoakCycleTest.EvidenceSanitizer.serialize(objectMapper, failed);

        JsonNode successTree = objectMapper.readTree(successJson);
        List<String> fields = new ArrayList<>();
        successTree.fieldNames().forEachRemaining(fields::add);
        assertEquals(List.of(
                "schemaVersion", "cycleId", "observedAt", "durationMs", "resultStatus", "reasonCode",
                "httpStatusCategory", "permissionClassification", "killSwitchObservedState",
                "credentialAccessed", "networkCalled", "allowedEndpointCategory",
                "accountConfigProbeStatus", "balanceProbeStatus", "traceId"
        ), fields);
        assertEquals("SUCCEEDED", successTree.path("balanceProbeStatus").asText());
        assertEquals("BLOCKED", objectMapper.readTree(blockedJson).path("resultStatus").asText());
        assertEquals("HARD_FAILURE", objectMapper.readTree(failedJson).path("resultStatus").asText());
    }

    @Test
    void rejectsUnknownSensitiveNestedAndVariantFieldsAfterSerialization() throws Exception {
        ObjectNode safe = (ObjectNode) objectMapper.valueToTree(result(
                "PASSED_READ_ONLY",
                "READ_ONLY_SAMPLE_ACCEPTED",
                "SUCCESS_2XX",
                "READ_ONLY_WITH_IP_ALLOWLIST",
                "ENGAGED",
                true,
                true,
                "ACCOUNT_CONFIG_AND_BALANCE_READ",
                GateWOkxReadonlySoakCycleTest.ProbeStatus.SUCCEEDED,
                GateWOkxReadonlySoakCycleTest.ProbeStatus.SUCCEEDED
        ));
        List<ObjectNode> unsafe = new ArrayList<>();
        unsafe.add(withNumber(safe, "balance", 100));
        unsafe.add(withText(safe, "availableBalance", "100"));
        unsafe.add(withObject(safe, "balanceDetail", "equity", "100"));
        unsafe.add(withText(safe, "currency", "USDT"));
        unsafe.add(withText(safe, "asset", "USDT"));
        unsafe.add(withText(safe, "accountId", "account-1"));
        unsafe.add(withText(safe, "rawResponse", "provider-payload"));
        unsafe.add(withText(safe, "raw_request", "provider-request"));
        unsafe.add(withText(safe, "RAW_HEADERS", "provider-headers"));
        unsafe.add(withText(safe, "apiSecret", "credential-material"));
        unsafe.add(withText(safe, "AVAILABLE_BALANCE", "100"));
        unsafe.add(withText(safe, "unknownField", "UNKNOWN"));
        ObjectNode nestedAllowedField = safe.deepCopy();
        nestedAllowedField.putObject("balanceProbeStatus").put("status", "SUCCEEDED");
        unsafe.add(nestedAllowedField);
        ObjectNode unknownStatus = safe.deepCopy();
        unknownStatus.put("balanceProbeStatus", "AVAILABLE");
        unsafe.add(unknownStatus);

        for (ObjectNode candidate : unsafe) {
            byte[] payload = objectMapper.writeValueAsBytes(candidate);
            assertThrows(
                    Exception.class,
                    () -> GateWOkxReadonlySoakCycleTest.EvidenceSanitizer
                            .validateSerializedPayload(objectMapper, payload)
            );
        }
    }

    @Test
    void rejectsPassWithoutBothProvenSafeProbeStatuses() {
        GateWOkxReadonlySoakCycleTest.CycleResult unsafePass = result(
                "PASSED_READ_ONLY",
                "READ_ONLY_SAMPLE_ACCEPTED",
                "SUCCESS_2XX",
                "READ_ONLY_WITH_IP_ALLOWLIST",
                "ENGAGED",
                true,
                true,
                "ACCOUNT_CONFIG_AND_BALANCE_READ",
                GateWOkxReadonlySoakCycleTest.ProbeStatus.SUCCEEDED,
                GateWOkxReadonlySoakCycleTest.ProbeStatus.UNKNOWN
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> GateWOkxReadonlySoakCycleTest.EvidenceSanitizer.validateDto(unsafePass)
        );
    }

    @Test
    void prerequisiteReadbackUsesOnlyAllowlistedAggregatesAndFailsClosed() throws Exception {
        Properties properties = baseProperties();
        properties.setProperty(GateWOkxReadonlySoakCycleTest.ACTION_PROPERTY, "prerequisite");
        Path output = tempDir.resolve("target/gatew-okx-readonly-soak")
                .resolve(REAL_RUN_ID)
                .resolve("prerequisite.json");
        properties.setProperty(GateWOkxReadonlySoakCycleTest.RESULT_FILE_PROPERTY, output.toString());
        Files.createDirectories(output.getParent());
        Map<String, String> environment = baseEnvironment();
        environment.remove("NQ_ACCOUNT_CREDENTIALS_MASTER_KEY");
        environment.remove("NQ_GATEW_SOAK_CURRENCIES");
        GateWOkxReadonlySoakCycleTest.SafetyConfig config = config(environment, properties);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class))).thenReturn("ENGAGED");
        when(jdbc.queryForList(anyString(), eq(7L), eq(9L))).thenReturn(List.of(Map.of(
                "exchange_code", "OKX",
                "trade_env", "LIVE",
                "status", "ACTIVE"
        )));
        when(jdbc.queryForList(anyString(), eq(9L))).thenReturn(List.of(Map.of(
                "credential_type", "OKX_API_V5",
                "credential_status", "ACTIVE",
                "permission_scope", "READ_ONLY",
                "withdraw_enabled", false
        )));

        GateWOkxReadonlySoakCycleTest.PrerequisiteReadback result =
                GateWOkxReadonlySoakCycleTest.readPrerequisite(jdbc, config, () -> true);
        GateWOkxReadonlySoakCycleTest.writePrerequisiteResult(config, result, objectMapper);

        assertTrue(result.ready());
        JsonNode json = objectMapper.readTree(Files.readAllBytes(output));
        List<String> fields = new ArrayList<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertEquals(GateWOkxReadonlySoakCycleTest.PrerequisiteReadback.FIELDS, fields);
        String serialized = json.toString().toLowerCase();
        assertFalse(serialized.contains("encrypted_payload"));
        assertFalse(serialized.contains("api_key"));
        assertFalse(serialized.contains("passphrase"));
        assertFalse(serialized.contains("jdbc"));

        JdbcTemplate unavailable = mock(JdbcTemplate.class);
        when(unavailable.queryForObject(anyString(), eq(String.class)))
                .thenThrow(new DataAccessResourceFailureException("redacted"));
        GateWOkxReadonlySoakCycleTest.PrerequisiteReadback failed =
                GateWOkxReadonlySoakCycleTest.readPrerequisite(unavailable, config, () -> true);
        assertFalse(failed.postgresReachable());
        assertFalse(failed.ready());

        when(jdbc.queryForObject(anyString(), eq(String.class))).thenReturn("DISENGAGED");
        GateWOkxReadonlySoakCycleTest.PrerequisiteReadback unsafeKillSwitch =
                GateWOkxReadonlySoakCycleTest.readPrerequisite(jdbc, config, () -> true);
        assertFalse(unsafeKillSwitch.killSwitchEngaged());
        assertFalse(unsafeKillSwitch.ready());
    }

    @Test
    void preCreatePrerequisiteUsesGlobalSanitizedMetadataWithoutAccountInput() {
        Properties properties = baseProperties();
        properties.setProperty(GateWOkxReadonlySoakCycleTest.ACTION_PROPERTY, "precreate-prerequisite");
        properties.setProperty(
                GateWOkxReadonlySoakCycleTest.RESULT_FILE_PROPERTY,
                "/run/nq-gatew-precreate-prerequisite-0123456789abcdef0123456789abcdef.json"
        );
        Map<String, String> environment = baseEnvironment();
        environment.put("NQ_GATEW_SOAK_DB_URL", "jdbc:postgresql://127.0.0.1:5432/nexus_quant");
        environment.put("NQ_GATEW_MANAGEMENT_HEALTH_URL", "http://127.0.0.1:18889/actuator/health");
        environment.remove("NQ_GATEW_SOAK_OWNER_ID");
        environment.remove("NQ_GATEW_SOAK_ACCOUNT_ID");
        environment.remove("NQ_GATEW_SOAK_CURRENCIES");
        environment.remove("NQ_ACCOUNT_CREDENTIALS_MASTER_KEY");
        GateWOkxReadonlySoakCycleTest.SafetyConfig config = config(environment, properties);
        config.assertSafe();

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class))).thenReturn("ENGAGED");
        Map<String, Object> safeCredential = Map.of(
                "credential_type", "OKX_API_V5",
                "credential_status", "ACTIVE",
                "permission_scope", "READ_ONLY",
                "withdraw_enabled", false,
                "exchange_code", "OKX",
                "trade_env", "LIVE",
                "status", "ACTIVE"
        );
        when(jdbc.queryForList(anyString())).thenReturn(List.of(safeCredential));

        GateWOkxReadonlySoakCycleTest.PrerequisiteReadback passed =
                GateWOkxReadonlySoakCycleTest.readPrerequisite(jdbc, config, () -> true);
        assertTrue(passed.ready());
        assertEquals(1, passed.activeCredentialCount());
        assertTrue(passed.tradePermissionExpectedDisabled());
        assertTrue(passed.withdrawPermissionExpectedDisabled());

        when(jdbc.queryForList(anyString())).thenReturn(List.of(safeCredential, safeCredential));
        GateWOkxReadonlySoakCycleTest.PrerequisiteReadback conflict =
                GateWOkxReadonlySoakCycleTest.readPrerequisite(jdbc, config, () -> true);
        assertFalse(conflict.ready());
        assertEquals("CONFLICT", conflict.credentialType());

        Map<String, Object> disabled = new HashMap<>(safeCredential);
        disabled.put("credential_status", "DISABLED");
        when(jdbc.queryForList(anyString())).thenReturn(List.of(disabled));
        GateWOkxReadonlySoakCycleTest.PrerequisiteReadback inactive =
                GateWOkxReadonlySoakCycleTest.readPrerequisite(jdbc, config, () -> true);
        assertFalse(inactive.ready());
        assertEquals("DISABLED", inactive.credentialLocalStatus());

        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        assertFalse(GateWOkxReadonlySoakCycleTest.readPrerequisite(jdbc, config, () -> true).ready());
        when(jdbc.queryForList(anyString())).thenReturn(List.of(safeCredential));
        assertFalse(GateWOkxReadonlySoakCycleTest.readPrerequisite(jdbc, config, () -> false).ready());
        when(jdbc.queryForObject(anyString(), eq(String.class))).thenReturn("DISENGAGED");
        assertFalse(GateWOkxReadonlySoakCycleTest.readPrerequisite(jdbc, config, () -> true).ready());
    }

    @Test
    void preCreatePrerequisiteRejectsOperatorDatabaseOverrideShapes() {
        Properties properties = baseProperties();
        properties.setProperty(GateWOkxReadonlySoakCycleTest.ACTION_PROPERTY, "precreate-prerequisite");
        properties.setProperty(
                GateWOkxReadonlySoakCycleTest.RESULT_FILE_PROPERTY,
                "/run/nq-gatew-precreate-prerequisite-0123456789abcdef0123456789abcdef.json"
        );
        Map<String, String> environment = baseEnvironment();
        environment.remove("NQ_GATEW_SOAK_OWNER_ID");
        environment.remove("NQ_GATEW_SOAK_ACCOUNT_ID");
        environment.remove("NQ_GATEW_SOAK_CURRENCIES");
        environment.remove("NQ_ACCOUNT_CREDENTIALS_MASTER_KEY");
        environment.put("NQ_GATEW_SOAK_DB_URL", "${NQ_DB_URL}");

        assertThrows(
                GateWOkxReadonlySoakCycleTest.SafeBlockException.class,
                () -> config(environment, properties).assertSafe()
        );
    }

    @Test
    void rejectsContradictoryEndpointAndNetworkEvidence() {
        GateWOkxReadonlySoakCycleTest.CycleResult contradictory = result(
                "BLOCKED",
                "PERMISSION_BLOCKED",
                "AUTH_ERROR",
                "UNKNOWN",
                "DISENGAGED",
                true,
                true,
                "NONE",
                GateWOkxReadonlySoakCycleTest.ProbeStatus.NOT_RUN,
                GateWOkxReadonlySoakCycleTest.ProbeStatus.NOT_RUN
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> GateWOkxReadonlySoakCycleTest.EvidenceSanitizer.validateDto(contradictory)
        );
    }

    @Test
    void springManagedObjectMapperPreservesTemporalEnumBooleanAndNullTypes() throws Exception {
        ManagedMapperFixture fixture = new ManagedMapperFixture(
                Instant.parse("2026-07-16T00:00:00Z"),
                OffsetDateTime.parse("2026-07-16T08:00:00+08:00"),
                LocalDateTime.parse("2026-07-16T08:00:00"),
                GateWOkxReadonlySoakCycleTest.ProbeStatus.SUCCEEDED,
                true,
                null
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(fixture));

        assertTrue(json.path("instant").isTextual());
        assertTrue(json.path("offsetDateTime").isTextual());
        assertTrue(json.path("localDateTime").isTextual());
        assertEquals("SUCCEEDED", json.path("probeStatus").asText());
        assertTrue(json.path("enabled").isBoolean());
        assertTrue(json.path("nullable").isNull());
    }

    @Test
    void rejectsCycleResultOutsideEvidenceRoot() {
        Properties properties = baseProperties();
        properties.setProperty(
                GateWOkxReadonlySoakCycleTest.RESULT_FILE_PROPERTY,
                tempDir.resolve("outside.json").toString()
        );
        GateWOkxReadonlySoakCycleTest.SafetyConfig config = config(baseEnvironment(), properties);

        assertThrows(
                IllegalStateException.class,
                () -> GateWOkxReadonlySoakCycleTest.writeSanitizedResult(
                        config,
                        GateWOkxReadonlySoakCycleTest.CycleResult.blocked("SAFE_BLOCK", "UNKNOWN"),
                        objectMapper
                )
        );
    }

    @Test
    void rejectsPathTraversalBeforeNormalization() {
        Properties properties = baseProperties();
        Path traversing = tempDir.resolve("target/gatew-okx-readonly-soak")
                .resolve(REAL_RUN_ID)
                .resolve("nested")
                .resolve("..")
                .resolve("cycle.json");
        properties.setProperty(GateWOkxReadonlySoakCycleTest.RESULT_FILE_PROPERTY, traversing.toString());

        GateWOkxReadonlySoakCycleTest.SafeBlockException error = assertThrows(
                GateWOkxReadonlySoakCycleTest.SafeBlockException.class,
                () -> config(baseEnvironment(), properties)
        );

        assertEquals("SOAK_PATH_TRAVERSAL", error.reasonCode());
    }

    @Test
    void rejectsUnrelatedTargetAndNonCanonicalSoakSubdirectories() throws Exception {
        assertResultPathRejected(tempDir.resolve("target/unrelated").resolve(REAL_RUN_ID).resolve("cycle.json"));
        assertResultPathRejected(tempDir.resolve("target/gatew-okx-readonly-soak/not-a-run/cycle.json"));
    }

    @Test
    void rejectsSymlinkEscapeWithoutWritingOutsideCanonicalRoot() throws Exception {
        Path soakRoot = tempDir.resolve("target/gatew-okx-readonly-soak");
        Path escapedDirectory = tempDir.resolve("escaped-result-directory");
        Files.createDirectories(soakRoot);
        Files.createDirectories(escapedDirectory);
        Path linkedRunRoot = soakRoot.resolve(SYMLINK_RUN_ID);
        createDirectoryLink(linkedRunRoot, escapedDirectory);

        Properties properties = baseProperties();
        properties.setProperty(
                GateWOkxReadonlySoakCycleTest.RESULT_FILE_PROPERTY,
                linkedRunRoot.resolve("cycle.json").toString()
        );
        GateWOkxReadonlySoakCycleTest.SafetyConfig config = config(baseEnvironment(), properties);

        assertThrows(
                IllegalStateException.class,
                () -> GateWOkxReadonlySoakCycleTest.writeSanitizedResult(
                        config,
                        GateWOkxReadonlySoakCycleTest.CycleResult.blocked("SAFE_BLOCK", "UNKNOWN"),
                        objectMapper
                )
        );
        assertFalse(Files.exists(escapedDirectory.resolve("cycle.json")));
    }

    private static GateWOkxReadonlySoakCycleTest.CycleResult result(
            String resultStatus,
            String reasonCode,
            String httpStatusCategory,
            String permissionClassification,
            String killSwitchObservedState,
            boolean credentialAccessed,
            boolean networkCalled,
            String allowedEndpointCategory,
            GateWOkxReadonlySoakCycleTest.ProbeStatus accountConfigProbeStatus,
            GateWOkxReadonlySoakCycleTest.ProbeStatus balanceProbeStatus
    ) {
        return new GateWOkxReadonlySoakCycleTest.CycleResult(
                GateWOkxReadonlySoakCycleTest.LAUNCHER_SCHEMA_VERSION,
                "gatew-cycle-0123456789abcdef0123456789abcdef",
                Instant.parse("2026-07-16T00:00:00Z"),
                1,
                resultStatus,
                reasonCode,
                httpStatusCategory,
                permissionClassification,
                killSwitchObservedState,
                credentialAccessed,
                networkCalled,
                allowedEndpointCategory,
                accountConfigProbeStatus,
                balanceProbeStatus,
                "gatew-soak-123e4567-e89b-12d3-a456-426614174000"
        );
    }

    private static ObjectNode withNumber(ObjectNode source, String field, int value) {
        ObjectNode copy = source.deepCopy();
        copy.put(field, value);
        return copy;
    }

    private void assertResultPathRejected(Path output) throws Exception {
        Files.createDirectories(output.getParent());
        Properties properties = baseProperties();
        properties.setProperty(GateWOkxReadonlySoakCycleTest.RESULT_FILE_PROPERTY, output.toString());
        GateWOkxReadonlySoakCycleTest.SafetyConfig config = config(baseEnvironment(), properties);

        assertThrows(
                IllegalStateException.class,
                () -> GateWOkxReadonlySoakCycleTest.writeSanitizedResult(
                        config,
                        GateWOkxReadonlySoakCycleTest.CycleResult.blocked("SAFE_BLOCK", "UNKNOWN"),
                        objectMapper
                )
        );
    }

    private static void createDirectoryLink(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException ex) {
            if (!System.getProperty("os.name", "").startsWith("Windows")) {
                throw ex;
            }
            Process process = new ProcessBuilder(
                    "cmd.exe", "/d", "/c", "mklink", "/J", link.toString(), target.toString()
            ).redirectErrorStream(true).start();
            try (var processOutput = process.getInputStream()) {
                processOutput.readAllBytes();
            }
            assertEquals(0, process.waitFor(), "failed to create Windows junction test fixture");
        }
    }

    private static ObjectNode withText(ObjectNode source, String field, String value) {
        ObjectNode copy = source.deepCopy();
        copy.put(field, value);
        return copy;
    }

    private static ObjectNode withObject(ObjectNode source, String field, String nestedField, String value) {
        ObjectNode copy = source.deepCopy();
        copy.putObject(field).put(nestedField, value);
        return copy;
    }

    private record ManagedMapperFixture(
            Instant instant,
            OffsetDateTime offsetDateTime,
            LocalDateTime localDateTime,
            GateWOkxReadonlySoakCycleTest.ProbeStatus probeStatus,
            boolean enabled,
            String nullable
    ) {
    }

    private void assertConfigBlocked(
            Map<String, String> environmentOverrides,
            Map<String, String> propertyOverrides,
            String expectedReason
    ) {
        Map<String, String> environment = baseEnvironment();
        environment.putAll(environmentOverrides);
        Properties properties = baseProperties();
        propertyOverrides.forEach(properties::setProperty);
        GateWOkxReadonlySoakCycleTest.SafetyConfig config = config(environment, properties);

        GateWOkxReadonlySoakCycleTest.SafeBlockException error = assertThrows(
                GateWOkxReadonlySoakCycleTest.SafeBlockException.class,
                config::assertSafe
        );
        assertEquals(expectedReason, error.reasonCode());
    }

    private void assertFromBlocked(Map<String, String> overrides, String expectedReason) {
        Map<String, String> environment = baseEnvironment();
        environment.putAll(overrides);
        GateWOkxReadonlySoakCycleTest.SafeBlockException error = assertThrows(
                GateWOkxReadonlySoakCycleTest.SafeBlockException.class,
                () -> config(environment, baseProperties())
        );
        assertEquals(expectedReason, error.reasonCode());
    }

    private static void assertCredentialBlocked(
            GateWOkxReadonlySoakCycleTest.CredentialGate credential,
            String expectedReason
    ) {
        GateWOkxReadonlySoakCycleTest.SafeBlockException error = assertThrows(
                GateWOkxReadonlySoakCycleTest.SafeBlockException.class,
                credential::assertSafe
        );
        assertEquals(expectedReason, error.reasonCode());
    }

    private GateWOkxReadonlySoakCycleTest.SafetyConfig config(
            Map<String, String> environment,
            Properties properties
    ) {
        return GateWOkxReadonlySoakCycleTest.SafetyConfig.from(environment, properties);
    }

    private Map<String, String> baseEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("SPRING_PROFILES_ACTIVE", GateWOkxReadonlySoakCycleTest.PROFILE);
        environment.put("NQ_GATEW_OKX_READONLY_SOAK_ENABLED", "true");
        environment.put("NQ_GATEW_RUN_MODE", GateWOkxReadonlySoakCycleTest.REAL_READONLY_SOAK);
        environment.put("CI", "false");
        environment.put("NQ_NO_OUTBOUND", "false");
        environment.put("NQ_LIVE_ENABLED", "false");
        environment.put("NQ_REAL_ORDER_SUBMISSION_ENABLED", "false");
        environment.put("NQ_TRANSFER_ENABLED", "false");
        environment.put("NQ_WITHDRAW_ENABLED", "false");
        environment.put("NQ_AI_ENABLED", "false");
        environment.put("NQ_DH_RUNTIME_ENABLED", "false");
        environment.put("NQ_REAL_PROVIDER_ENABLED", "false");
        environment.put("NQ_REAL_CLIENT_ENABLED", "false");
        environment.put("NQ_REAL_EXCHANGE_ENABLED", "false");
        environment.put("NQ_GATEW_SOAK_DB_URL", "jdbc:postgresql://127.0.0.1:5432/gatew_soak_unit");
        environment.put("NQ_GATEW_SOAK_DB_USER", "gatew_soak_user");
        environment.put("NQ_GATEW_SOAK_DB_PASSWORD", "unit-test-db-password");
        environment.put("NQ_ACCOUNT_CREDENTIALS_MASTER_KEY", "unit-test-master-key");
        environment.put("NQ_GATEW_SOAK_OWNER_ID", "7");
        environment.put("NQ_GATEW_SOAK_ACCOUNT_ID", "9");
        environment.put("NQ_GATEW_SOAK_CURRENCIES", "BTC,USDT");
        return environment;
    }

    private Map<String, String> formalSystemdEnvironment() {
        Map<String, String> environment = baseEnvironment();
        environment.put("NQ_GATEW_SECRET_SOURCE", GateWOkxReadonlySoakCycleTest.SYSTEMD_CREDENTIAL_SOURCE);
        environment.put("NQ_GATEW_FORMAL_SYSTEMD", "true");
        environment.put(
                "NQ_GATEW_FORMAL_EVIDENCE_ROOT",
                Path.of("/var/lib/nexus-quant/gatew-soak", REAL_RUN_ID, "evidence").toString()
        );
        return environment;
    }

    private Properties baseProperties() {
        Properties properties = new Properties();
        properties.setProperty(GateWOkxReadonlySoakCycleTest.ACTION_PROPERTY, "sample");
        properties.setProperty(GateWOkxReadonlySoakCycleTest.REPO_ROOT_PROPERTY, tempDir.toString());
        properties.setProperty(
                GateWOkxReadonlySoakCycleTest.RESULT_FILE_PROPERTY,
                tempDir.resolve("target/gatew-okx-readonly-soak").resolve(REAL_RUN_ID).resolve("cycle.json").toString()
        );
        return properties;
    }

    private static GateWOkxReadonlySoakCycleTest.CredentialGate safeCredential() {
        return credential("READ_ONLY", false, true, "SUCCEEDED", "PASSED");
    }

    private static GateWOkxReadonlySoakCycleTest.CredentialGate credential(
            String permissionScope,
            boolean withdrawEnabled,
            boolean ipAllowlistRequired,
            String permissionProbeStatus,
            String ipAllowlistProbeStatus
    ) {
        return new GateWOkxReadonlySoakCycleTest.CredentialGate(
                permissionScope,
                withdrawEnabled,
                ipAllowlistRequired,
                permissionProbeStatus,
                ipAllowlistProbeStatus,
                "OKX",
                "LIVE",
                "ACTIVE"
        );
    }
}
