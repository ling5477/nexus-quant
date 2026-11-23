package com.guidinglight.nexusquant.marketdata.domain;

/**
 * MarketdataBackendSupportLevel explains how much backend evidence supports a readiness response.
 * <p>
 * 原因：行情就绪聚合不依赖新增 migration，客户端必须知道摘要来源于
 * existing bars and ingestion tables rather than a persisted source-health table.
 */
public enum MarketdataBackendSupportLevel {
    NO_MIGRATION_MVP,
    UNAVAILABLE,
    FUTURE_PERSISTED_SOURCE_HEALTH_REQUIRED
}
