package com.guidinglight.nexusquant.strategy.application.consistencyevidence;

/**
 * ConsistencyEvidenceFreshness 描述 consistency evidence 的新鲜度。
 *
 * <p>Freshness 只表示本地证据是否适合继续人工诊断。`FRESH` 不是交易准入；`STALE`、`MISSING`
 * 或 `UNKNOWN` 必须 fail-closed，只能生成 warning / nextStep，不会自动生成新 report。
 */
public enum ConsistencyEvidenceFreshness {
    FRESH,
    STALE,
    MISSING,
    UNKNOWN
}
