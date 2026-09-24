package com.guidinglight.nexusquant.account.infra.okx.readonly;

/**
 * 只读诊断 diagnostic-only probe 状态；不含 READY 或交易授权语义。
 */
public enum OkxPrivateProbeStatus {
    PASSED_READ_ONLY,
    BLOCKED,
    PARTIAL,
    UNKNOWN,
    NOT_READY
}
