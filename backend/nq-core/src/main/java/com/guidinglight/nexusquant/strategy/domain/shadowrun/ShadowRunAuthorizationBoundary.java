package com.guidinglight.nexusquant.strategy.domain.shadowrun;

/**
 * Shadow Run 授权边界声明。
 *
 * <p>枚举值只用于本地诊断、复核和回放边界，不允许被解释为下单、撤单、LIVE
 * 或真实交易授权。
 */
public enum ShadowRunAuthorizationBoundary {
    DIAGNOSTIC_ONLY,
    REVIEW_ONLY,
    REPLAY_ONLY
}
