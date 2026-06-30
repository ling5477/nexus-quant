package com.guidinglight.nexusquant.runtime.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.runtime.api.dto.OperationalReadinessResponse;
import com.guidinglight.nexusquant.runtime.api.dto.OperationalReadinessStatusResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * OperationalReadinessServiceTest fixes GateM-6B fail-closed defaults.
 *
 * <p>Why: disabled / no-real / skipped statuses must remain not-ready and the response must never
 * contain runtime values. These tests protect the read-only startup boundary summary from drifting
 * into a capability authorization signal.
 */
class OperationalReadinessServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-30T00:00:00Z"), ZoneOffset.UTC);
    private static final Set<String> FORBIDDEN_TOKENS = Set.of(
            "secret",
            "token",
            "passphrase",
            "private key",
            "private_key",
            "cookie",
            "signature",
            "raw env",
            "raw_env"
    );

    @Test
    void currentSummaryReturnsDefaultFailClosedStatuses() {
        OperationalReadinessResponse response = new OperationalReadinessService(FIXED_CLOCK).currentSummary();

        assertEquals(Instant.parse("2026-06-30T00:00:00Z"), response.generatedAt());
        assertEquals("DISABLED", response.liveStatus().status());
        assertEquals("NOT_STARTED", response.aiStatus().status());
        assertEquals("NOT_INTEGRATED", response.dhRuntimeStatus().status());
        assertEquals("NOT_IMPLEMENTED", response.realProviderStatus().status());
        assertEquals("NOT_EXPOSED", response.credentialExposureStatus().status());
        assertEquals("DISABLED", response.externalExchangeCallStatus().status());
        assertEquals("SKIPPED", response.permissionProbeStatus().status());
        assertEquals("SAFE_BY_DEFAULT", response.startupBoundaryStatus().status());
        assertEquals("SAFE_SUMMARY_ONLY", response.profileBoundaryStatus().status());
        assertEquals("SAFE_SUMMARY_ONLY", response.configDiagnosticsStatus().status());
        assertEquals("SAFE_SUMMARY_ONLY", response.logDiagnosticsStatus().status());

        assertTrue(allStatuses(response).stream().noneMatch(OperationalReadinessStatusResponse::ready),
                "GateM-6B default summary must not mark any real runtime capability ready");
    }

    @Test
    void eachStatusCarriesReasonCodeAndSafeReason() {
        OperationalReadinessResponse response = new OperationalReadinessService(FIXED_CLOCK).currentSummary();

        for (OperationalReadinessStatusResponse status : allStatuses(response)) {
            assertFalse(status.reasonCode().isBlank(), "reasonCode must be present: " + status);
            assertFalse(status.reason().isBlank(), "reason must be present: " + status);
            assertSafe(status.status());
            assertSafe(status.reasonCode());
            assertSafe(status.reason());
        }
    }

    @Test
    void noRuntimeConfigValuesAreExposed() {
        OperationalReadinessResponse response = new OperationalReadinessService(FIXED_CLOCK).currentSummary();

        String flattened = allStatuses(response).stream()
                .map(status -> status.status() + " " + status.reasonCode() + " " + status.reason())
                .reduce("", (left, right) -> left + " " + right)
                .toLowerCase();

        assertFalse(flattened.contains("="), "summary must not expose assignment-like runtime values");
        assertFalse(flattened.contains("://"), "summary must not expose endpoint-like runtime values");
        for (String token : FORBIDDEN_TOKENS) {
            assertFalse(flattened.contains(token), "summary must not expose '" + token + "': " + flattened);
        }
    }

    private static List<OperationalReadinessStatusResponse> allStatuses(OperationalReadinessResponse response) {
        return List.of(
                response.liveStatus(),
                response.aiStatus(),
                response.dhRuntimeStatus(),
                response.realProviderStatus(),
                response.credentialExposureStatus(),
                response.externalExchangeCallStatus(),
                response.permissionProbeStatus(),
                response.startupBoundaryStatus(),
                response.profileBoundaryStatus(),
                response.configDiagnosticsStatus(),
                response.logDiagnosticsStatus()
        );
    }

    private static void assertSafe(String value) {
        String lower = value.toLowerCase();
        for (String token : FORBIDDEN_TOKENS) {
            assertFalse(lower.contains(token), "field must not expose '" + token + "': " + value);
        }
    }
}
