package com.guidinglight.nexusquant.runtime.api;

import com.guidinglight.nexusquant.runtime.api.dto.OperationalReadinessResponse;
import com.guidinglight.nexusquant.runtime.api.dto.OperationalReadinessStatusResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * OperationalReadinessService builds the GateM-6B disabled capability summary.
 *
 * <p>Why: operators need a single read-only summary for why the current runtime remains paper-only
 * and no-real. This service is deliberately static and fail-closed: it has no adapter, permission
 * probe, HTTP, database, file, or exchange client dependency. It only returns stable DTO values that
 * describe current GateM boundaries without changing runtime behavior.
 *
 * <p>Thread safety: the service only stores an immutable {@link Clock} reference and can be called
 * concurrently.
 */
@Service
public class OperationalReadinessService {

    private final Clock clock;

    /**
     * Production constructor using UTC system time.
     *
     * <p>No runtime profile/config value is read here; this endpoint is a safe summary only.
     */
    public OperationalReadinessService() {
        this(Clock.systemUTC());
    }

    /**
     * Test constructor for deterministic generatedAt values.
     *
     * @param clock response timestamp source; no other runtime dependency is accepted
     */
    OperationalReadinessService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Returns the current safe operational readiness summary.
     *
     * <p>Current GateM-6B baseline is intentionally not ready for real runtime use: LIVE is
     * disabled, AI is not started, DH runtime is not integrated, real provider is not implemented,
     * external exchange calls are disabled, and profile/config/log details are exposed only as
     * redacted summary categories.
     *
     * @return safe fail-closed operational readiness response
     */
    public OperationalReadinessResponse currentSummary() {
        return new OperationalReadinessResponse(
                Instant.now(clock),
                status("DISABLED", "LIVE_DISABLED", "LIVE execution is disabled by default."),
                status("NOT_STARTED", "AI_RUNTIME_NOT_STARTED", "AI runtime has not started."),
                status("NOT_INTEGRATED", "DH_RUNTIME_NOT_CONNECTED", "DH runtime is not integrated."),
                status("NOT_IMPLEMENTED", "REAL_PROVIDER_NOT_IMPLEMENTED", "Real provider is not implemented."),
                status("NOT_EXPOSED", "SENSITIVE_MATERIAL_OMITTED",
                        "Runtime-sensitive material is omitted from this summary."),
                status("DISABLED", "EXTERNAL_EXCHANGE_CALL_DISABLED",
                        "This endpoint does not perform external exchange calls."),
                status("SKIPPED", "REAL_PERMISSION_PROBE_NOT_AVAILABLE",
                        "Real permission probe is not available in current GateM."),
                status("SAFE_BY_DEFAULT", "STARTUP_BOUNDARY_FAIL_CLOSED",
                        "Startup boundary remains fail-closed for real runtime capabilities."),
                status("SAFE_SUMMARY_ONLY", "PROFILE_VALUES_OMITTED",
                        "Profile boundary is reported as safe summary categories only."),
                status("SAFE_SUMMARY_ONLY", "CONFIG_VALUES_OMITTED",
                        "Config diagnostics are reported as safe summary categories only."),
                status("SAFE_SUMMARY_ONLY", "LOG_VALUES_OMITTED",
                        "Log diagnostics are reported as safe summary categories only.")
        );
    }

    private static OperationalReadinessStatusResponse status(String status, String reasonCode, String reason) {
        return new OperationalReadinessStatusResponse(status, false, reasonCode, reason);
    }
}
