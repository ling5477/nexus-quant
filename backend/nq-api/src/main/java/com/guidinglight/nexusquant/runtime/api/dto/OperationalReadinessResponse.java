package com.guidinglight.nexusquant.runtime.api.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * OperationalReadinessResponse is the read-only response for
 * {@code GET /api/runtime/operational-readiness}.
 *
 * <p>Why: GateM-6B exposes only disabled capability and startup boundary summaries. The DTO is an
 * explicit contract and intentionally excludes raw env, raw config maps, provider payloads, and
 * runtime-sensitive material.
 *
 * @param generatedAt                when the safe summary was generated
 * @param liveStatus                 LIVE runtime boundary
 * @param aiStatus                   AI runtime boundary
 * @param dhRuntimeStatus            DH runtime boundary
 * @param realProviderStatus         real provider / RealClient boundary
 * @param credentialExposureStatus   sensitive material exposure boundary
 * @param externalExchangeCallStatus external exchange call boundary for this endpoint
 * @param permissionProbeStatus      permission probe boundary
 * @param startupBoundaryStatus      startup safety boundary
 * @param profileBoundaryStatus      profile summary boundary
 * @param configDiagnosticsStatus    config diagnostics boundary
 * @param logDiagnosticsStatus       log diagnostics boundary
 */
public record OperationalReadinessResponse(
        Instant generatedAt,
        OperationalReadinessStatusResponse liveStatus,
        OperationalReadinessStatusResponse aiStatus,
        OperationalReadinessStatusResponse dhRuntimeStatus,
        OperationalReadinessStatusResponse realProviderStatus,
        OperationalReadinessStatusResponse credentialExposureStatus,
        OperationalReadinessStatusResponse externalExchangeCallStatus,
        OperationalReadinessStatusResponse permissionProbeStatus,
        OperationalReadinessStatusResponse startupBoundaryStatus,
        OperationalReadinessStatusResponse profileBoundaryStatus,
        OperationalReadinessStatusResponse configDiagnosticsStatus,
        OperationalReadinessStatusResponse logDiagnosticsStatus
) {

    public OperationalReadinessResponse {
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(liveStatus, "liveStatus must not be null");
        Objects.requireNonNull(aiStatus, "aiStatus must not be null");
        Objects.requireNonNull(dhRuntimeStatus, "dhRuntimeStatus must not be null");
        Objects.requireNonNull(realProviderStatus, "realProviderStatus must not be null");
        Objects.requireNonNull(credentialExposureStatus, "credentialExposureStatus must not be null");
        Objects.requireNonNull(externalExchangeCallStatus, "externalExchangeCallStatus must not be null");
        Objects.requireNonNull(permissionProbeStatus, "permissionProbeStatus must not be null");
        Objects.requireNonNull(startupBoundaryStatus, "startupBoundaryStatus must not be null");
        Objects.requireNonNull(profileBoundaryStatus, "profileBoundaryStatus must not be null");
        Objects.requireNonNull(configDiagnosticsStatus, "configDiagnosticsStatus must not be null");
        Objects.requireNonNull(logDiagnosticsStatus, "logDiagnosticsStatus must not be null");
    }
}
