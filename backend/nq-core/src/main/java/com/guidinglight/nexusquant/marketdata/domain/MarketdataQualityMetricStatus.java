package com.guidinglight.nexusquant.marketdata.domain;

/**
 * MarketdataQualityMetricStatus 描述数据质量中心计数类指标的可解释性状态。
 * <p>
 * Why:
 * GateP Batch 2 不新增 migration，因此 duplicate / out-of-order 等指标可能缺少稳定本地事实。
 * 用显式状态区分“真实可计算的 0”和“当前 schema 不支持”，避免把未知误写成通过。
 */
public enum MarketdataQualityMetricStatus {
    AVAILABLE,
    UNKNOWN,
    NOT_AVAILABLE
}
