/**
 * Operational readiness safe summary types for GateM-6B
 * `GET /api/runtime/operational-readiness`.
 *
 * Why:
 * The frontend only renders the backend's explicit safe DTO. These fields are status evidence,
 * not execution capability, and current GateM baseline keeps every item non-authorized.
 */
export interface OperationalReadinessStatusResponse {
    status: string;
    ready: boolean;
    reasonCode: string;
    reason: string;
}

export interface OperationalReadinessResponse {
    generatedAt: string;
    liveStatus: OperationalReadinessStatusResponse;
    aiStatus: OperationalReadinessStatusResponse;
    dhRuntimeStatus: OperationalReadinessStatusResponse;
    realProviderStatus: OperationalReadinessStatusResponse;
    credentialExposureStatus: OperationalReadinessStatusResponse;
    externalExchangeCallStatus: OperationalReadinessStatusResponse;
    permissionProbeStatus: OperationalReadinessStatusResponse;
    startupBoundaryStatus: OperationalReadinessStatusResponse;
    profileBoundaryStatus: OperationalReadinessStatusResponse;
    configDiagnosticsStatus: OperationalReadinessStatusResponse;
    logDiagnosticsStatus: OperationalReadinessStatusResponse;
}
