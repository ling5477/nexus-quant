package com.guidinglight.nexusquant.app.gatew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadOperation;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadResult;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadTransport;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** GateW soak test-support 的 fail-closed 配置、credential 与 evidence 单元回归。 */
class GateWOkxReadonlySoakSupportTest {

    @TempDir
    Path tempDir;

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
    }

    @Test
    void writesOnlySanitizedCycleResultBelowEvidenceRoot() throws Exception {
        GateWOkxReadonlySoakCycleTest.SafetyConfig config = config(baseEnvironment(), baseProperties());
        GateWOkxReadonlySoakCycleTest.CycleResult result =
                GateWOkxReadonlySoakCycleTest.CycleResult.blocked("API_KEY_REQUIRED", "UNKNOWN");

        GateWOkxReadonlySoakCycleTest.writeSanitizedResult(config, result);

        String json = Files.readString(config.resultFile(), StandardCharsets.UTF_8).toLowerCase();
        assertTrue(json.contains("api_key_required"));
        assertFalse(json.contains("passphrase"));
        assertFalse(json.contains("rawresponse"));
        assertFalse(json.contains("http://"));
        assertFalse(json.contains("https://"));
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
                        GateWOkxReadonlySoakCycleTest.CycleResult.blocked("SAFE_BLOCK", "UNKNOWN")
                )
        );
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

    private Properties baseProperties() {
        Properties properties = new Properties();
        properties.setProperty(GateWOkxReadonlySoakCycleTest.ACTION_PROPERTY, "sample");
        properties.setProperty(GateWOkxReadonlySoakCycleTest.REPO_ROOT_PROPERTY, tempDir.toString());
        properties.setProperty(
                GateWOkxReadonlySoakCycleTest.RESULT_FILE_PROPERTY,
                tempDir.resolve("target/gatew-okx-readonly-soak/unit/cycle.json").toString()
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
                "fingerprint",
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
