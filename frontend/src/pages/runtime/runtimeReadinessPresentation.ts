const REASON_KEYS: Record<string, string> = {
    LIVE_DISABLED: 'pages:runtimeReasonLiveDisabled',
    AI_RUNTIME_NOT_STARTED: 'pages:runtimeReasonAiNotStarted',
    DH_RUNTIME_NOT_CONNECTED: 'pages:runtimeReasonDhNotConnected',
    REAL_PROVIDER_NOT_IMPLEMENTED: 'pages:runtimeReasonProviderNotImplemented',
    SENSITIVE_MATERIAL_OMITTED: 'pages:runtimeReasonSensitiveOmitted',
    EXTERNAL_EXCHANGE_CALL_DISABLED: 'pages:runtimeReasonExternalDisabled',
    REAL_PERMISSION_PROBE_NOT_AVAILABLE: 'pages:runtimeReasonProbeUnavailable',
    STARTUP_BOUNDARY_FAIL_CLOSED: 'pages:runtimeReasonStartupBoundary',
    PROFILE_VALUES_OMITTED: 'pages:runtimeReasonProfileOmitted',
    CONFIG_VALUES_OMITTED: 'pages:runtimeReasonConfigOmitted',
    LOG_VALUES_OMITTED: 'pages:runtimeReasonLogOmitted',
    PENDING_BACKEND_SUPPORT: 'pages:runtimeReasonSummaryUnavailable',
};

const STATUS_KEYS: Record<string, string> = {
    DISABLED: 'pages:runtimeStatusDisabled',
    NOT_STARTED: 'pages:runtimeStatusNotStarted',
    NOT_INTEGRATED: 'pages:runtimeStatusNotIntegrated',
    NOT_IMPLEMENTED: 'pages:runtimeStatusNotImplemented',
    NOT_EXPOSED: 'pages:runtimeStatusNotExposed',
    SKIPPED: 'pages:runtimeProbeSkipped',
    SAFE_BY_DEFAULT: 'pages:runtimeStatusSafeDefault',
    SAFE_SUMMARY_ONLY: 'pages:runtimeStatusSummaryOnly',
    UNAVAILABLE: 'pages:runtimeUnavailable',
    REVIEW_REQUIRED: 'pages:runtimeStatusReviewRequired',
};

/** 只映射已知服务端代码；未知值保留代码并引导查看原始详情，不推断就绪或授权。 */
export function runtimeReadinessReasonKey(code: string, kind: 'reason' | 'status' = 'reason'): string {
    const keys = kind === 'status' ? STATUS_KEYS : REASON_KEYS;
    return Object.hasOwn(keys, code) ? keys[code] : 'pages:runtimeReasonUntranslated';
}
