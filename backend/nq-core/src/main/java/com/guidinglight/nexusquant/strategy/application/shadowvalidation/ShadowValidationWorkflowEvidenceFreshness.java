package com.guidinglight.nexusquant.strategy.application.shadowvalidation;

/**
 * Shadow Validation Workflow 的证据新鲜度枚举。
 *
 * <p>该枚举只表达本地只读 evidence 是否足以进入人工复核；它不表达策略收益、胜率、交易风险是否已经处理，
 * 也不表达 LIVE 或真实交易授权。
 */
public enum ShadowValidationWorkflowEvidenceFreshness {
    FRESH,
    STALE,
    MISSING,
    PARTIAL,
    UNKNOWN
}
