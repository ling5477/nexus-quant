package com.guidinglight.nexusquant.account.infra.probe;

import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeRequest;
import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeResult;
import com.guidinglight.nexusquant.account.infra.gatew.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.adapter.okx.service.OkxIpAllowlistStatus;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadError;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadException;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadOperation;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OkxRealReadonlyPermissionProbePortTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void verifiesOnlyReadPermissionAndExactIpThroughTypedProductionGet() {
        AtomicReference<OkxPrivateReadRequest> observedRequest = new AtomicReference<>();
        AtomicReference<OkxPrivateEnvironment> observedEnvironment = new AtomicReference<>();
        FakeExecutor executor = new FakeExecutor((request, environment) -> {
            observedRequest.set(request);
            observedEnvironment.set(environment);
            return result(Set.of("READ_ONLY"), true, OkxIpAllowlistStatus.MATCHED);
        });
        OkxRealReadonlyPermissionProbePort port = port(executor);

        ExchangeCredentialPermissionProbeResult result = port.probe(request());

        assertTrue(port.supportsControlledLiveReadOnlyProbe());
        assertEquals("SUCCEEDED", result.permissionProbeStatus());
        assertEquals("READ_ONLY", result.detectedPermissionScope());
        assertFalse(result.withdrawEnabledDetected());
        assertEquals("PASSED", result.ipAllowlistProbeStatus());
        assertEquals(OkxPrivateEnvironment.PRODUCTION, observedEnvironment.get());
        assertEquals(OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ, observedRequest.get().operation());
        assertEquals("GET", observedRequest.get().operation().method());
        assertEquals("/api/v5/account/config", observedRequest.get().pathWithQuery());
        assertEquals("203.0.113.8", observedRequest.get().expectedIp());
        assertTrue(java.util.Arrays.stream(ExchangeCredentialPermissionProbeRequest.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("payload")));
    }

    @Test
    void blocksDangerousMissingAndUnknownPermissionCombinations() {
        Map<Set<String>, String> cases = Map.of(
                Set.of("READ_ONLY", "TRADE"), "TRADE_PERMISSION_ENABLED",
                Set.of("READ_ONLY", "WITHDRAW"), "WITHDRAW_PERMISSION_ENABLED",
                Set.of("TRADE"), "READ_PERMISSION_MISSING",
                Set.of("READ_ONLY", "future_permission"), "UNKNOWN_PERMISSION_TOKEN"
        );
        for (Map.Entry<Set<String>, String> entry : cases.entrySet()) {
            OkxRealReadonlyPermissionProbePort port = port(new FakeExecutor((request, environment) ->
                    result(entry.getKey(), true, OkxIpAllowlistStatus.MATCHED)));
            ExchangeCredentialPermissionProbeResult result = port.probe(request());
            assertEquals("FAILED", result.permissionProbeStatus());
            assertEquals(entry.getValue(), result.sanitizedErrorCategory());
        }

        OkxRealReadonlyPermissionProbePort missing = port(new FakeExecutor((request, environment) ->
                result(Set.of(), false, OkxIpAllowlistStatus.UNKNOWN)));
        assertEquals("RESPONSE_FIELDS_MISSING", missing.probe(request()).sanitizedErrorCategory());
    }

    @Test
    void blocksEveryNonMatchedIpClassification() {
        Map<OkxIpAllowlistStatus, String> cases = Map.of(
                OkxIpAllowlistStatus.MISSING, "IP_ALLOWLIST_MISSING",
                OkxIpAllowlistStatus.MISMATCHED, "IP_ALLOWLIST_MISMATCH",
                OkxIpAllowlistStatus.UNKNOWN, "IP_ALLOWLIST_UNKNOWN",
                OkxIpAllowlistStatus.NOT_CHECKED, "IP_ALLOWLIST_UNKNOWN"
        );
        for (Map.Entry<OkxIpAllowlistStatus, String> entry : cases.entrySet()) {
            OkxRealReadonlyPermissionProbePort port = port(new FakeExecutor((request, environment) ->
                    result(Set.of("READ_ONLY"), true, entry.getKey())));
            ExchangeCredentialPermissionProbeResult result = port.probe(request());
            assertEquals("FAILED", result.permissionProbeStatus());
            assertEquals(entry.getValue(), result.sanitizedErrorCategory());
            assertEquals(entry.getKey() == OkxIpAllowlistStatus.MISSING
                            || entry.getKey() == OkxIpAllowlistStatus.MISMATCHED ? "FAILED" : "UNKNOWN",
                    result.ipAllowlistProbeStatus());
        }
    }

    @Test
    void mapsSanitizedAuthenticationClockRateAndTransportFailures() {
        Map<OkxPrivateReadError, String> cases = Map.of(
                OkxPrivateReadError.AUTHENTICATION_FAILURE, "AUTH_FAILED",
                OkxPrivateReadError.INVALID_API_KEY, "INVALID_API_KEY",
                OkxPrivateReadError.SIGNATURE_FAILURE, "SIGNATURE_FAILED",
                OkxPrivateReadError.CLOCK_SKEW, "CLOCK_SKEW",
                OkxPrivateReadError.IP_ALLOWLIST_FAILED, "IP_ALLOWLIST_FAILED",
                OkxPrivateReadError.RATE_LIMITED, "RATE_LIMITED",
                OkxPrivateReadError.TIMEOUT, "TIMEOUT",
                OkxPrivateReadError.REDIRECT_REJECTED, "REDIRECT_REJECTED",
                OkxPrivateReadError.HTTP_ERROR, "HTTP_ERROR"
        );
        for (Map.Entry<OkxPrivateReadError, String> entry : cases.entrySet()) {
            OkxRealReadonlyPermissionProbePort port = port(new FakeExecutor((request, environment) -> {
                throw new OkxPrivateReadException(entry.getKey());
            }));
            ExchangeCredentialPermissionProbeResult result = port.probe(request());
            assertEquals("FAILED", result.permissionProbeStatus());
            assertEquals(entry.getValue(), result.sanitizedErrorCategory());
            assertEquals(result.sanitizedErrorCategory(), result.sanitizedErrorMessage());
        }
    }

    @Test
    void blocksWrongEnvironmentOrModeBeforeCredentialAccessAndRejectsNonLiteralExpectedIp() {
        FakeExecutor executor = new FakeExecutor((request, environment) ->
                result(Set.of("READ_ONLY"), true, OkxIpAllowlistStatus.MATCHED));
        OkxRealReadonlyPermissionProbePort port = port(executor);
        ExchangeCredentialPermissionProbeRequest wrongEnvironment = new ExchangeCredentialPermissionProbeRequest(
                1L, 900001L, 7L, "OKX", "SIM", "OKX_API_V5", "PAPER", true, "trace"
        );

        assertEquals("REQUEST_SCOPE_BLOCKED", port.probe(wrongEnvironment).sanitizedErrorCategory());
        assertEquals(0, executor.calls.get());
        assertThrows(IllegalArgumentException.class,
                () -> new OkxRealReadonlyPermissionProbePort(executor, "example.com", CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> new OkxRealReadonlyPermissionProbePort(executor, "203.0.113.0/24", CLOCK));
        assertFalse(port.toString().contains("203.0.113.8"));
    }

    private static OkxRealReadonlyPermissionProbePort port(FakeExecutor executor) {
        return new OkxRealReadonlyPermissionProbePort(executor, "203.0.113.8", CLOCK);
    }

    private static ExchangeCredentialPermissionProbeRequest request() {
        return new ExchangeCredentialPermissionProbeRequest(
                1L, 900001L, 7L, "OKX", "LIVE", "OKX_API_V5", "PAPER", true, "trace-permission"
        );
    }

    private static OkxPrivateReadResult result(
            Set<String> permissions,
            boolean complete,
            OkxIpAllowlistStatus ipStatus
    ) {
        return new OkxPrivateReadResult(
                OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ,
                permissions,
                0,
                complete,
                List.of(),
                List.of(),
                ipStatus != OkxIpAllowlistStatus.MISSING,
                ipStatus,
                CLOCK.instant()
        );
    }

    private static final class FakeExecutor implements OkxPrivateCredentialExecutor {
        private final FakeSession session;
        private final AtomicInteger calls = new AtomicInteger();

        private FakeExecutor(FakeSession session) {
            this.session = session;
        }

        @Override
        public <T> T withActiveCredential(
                Long ownerId,
                Long exchangeAccountId,
                String credentialType,
                CredentialCallback<T> callback
        ) {
            throw new AssertionError("real permission probe must use exact credential reference");
        }

        @Override
        public <T> T withActiveCredential(
                Long ownerId,
                Long exchangeAccountId,
                Long credentialId,
                String credentialType,
                CredentialCallback<T> callback
        ) {
            calls.incrementAndGet();
            assertEquals(1L, ownerId);
            assertEquals(900001L, exchangeAccountId);
            assertEquals(7L, credentialId);
            assertEquals("OKX_API_V5", credentialType);
            return callback.execute(session::execute);
        }
    }

    @FunctionalInterface
    private interface FakeSession {
        OkxPrivateReadResult execute(OkxPrivateReadRequest request, OkxPrivateEnvironment environment);
    }
}
