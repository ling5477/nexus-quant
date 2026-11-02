package com.guidinglight.nexusquant.strategy.strategyrelease.domain;

/**
 * Strategy Release 的最小 production 状态。
 *
 * <p>该状态只表达 artifact provenance 验证结果，不表示 Shadow、LIVE、交易或部署授权。
 */
public enum StrategyReleaseStatus {
    UNVERIFIED,
    VERIFIED,
    REJECTED
}
