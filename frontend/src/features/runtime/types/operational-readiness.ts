/**
 * 运行就绪安全摘要类型
 * `GET /api/runtime/operational-readiness`.
 *
 * Why:
 * The frontend only renders the backend's explicit safe DTO. These fields are status evidence,
 * 不表示执行能力，所有条目均不得被解释为已授权。
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
    fakeDryRunOperations: FakeDryRunOperationsResponse;
}

export interface FakeDryRunOperationsResponse {
    observedAt: string;
    mode: string;
    liveState: string;
    killState: string;
    sessionId: string;
    sessionState: string;
    approvalState: string;
    riskDigest: string;
    workerHealth: string;
    workerIdentity: string;
    releaseIdentity: string;
    releaseDigest: string;
    intentId: string;
    intentState: string;
    receiptState: string;
    tradingAuthorization: boolean;
    productionStartAuthorization: boolean;
}
