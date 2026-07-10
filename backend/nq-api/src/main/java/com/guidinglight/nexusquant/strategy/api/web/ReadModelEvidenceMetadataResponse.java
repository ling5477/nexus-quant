package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;

import java.time.Instant;

/**
 * ReadModelEvidenceMetadataResponse 是统一只读证据元数据的 HTTP 表达。
 *
 * <p>该 DTO 仅映射 core 的诊断事实，不推导交易 readiness，不包含 credential、private endpoint、
 * 真实账户或真实订单信息。所有安全 flags 由 core 固定为 fail-closed 值。
 */
public record ReadModelEvidenceMetadataResponse(
        String source,
        String availability,
        Instant lastCalculatedAt,
        String freshnessStatus,
        Long ageSeconds,
        Long staleAfterSeconds,
        String staleReason,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled
) {

    /**
     * 从 core 值对象映射 HTTP DTO。
     *
     * @param metadata 非空的统一证据元数据
     * @return 向后兼容的新 response 字段
     */
    public static ReadModelEvidenceMetadataResponse from(ReadModelEvidenceMetadata metadata) {
        return new ReadModelEvidenceMetadataResponse(
                metadata.source(),
                metadata.availability().name(),
                metadata.lastCalculatedAt(),
                metadata.freshnessStatus().name(),
                metadata.ageSeconds(),
                metadata.staleAfterSeconds(),
                metadata.staleReason(),
                metadata.diagnosticOnly(),
                metadata.noSideEffect(),
                metadata.notTradingAuthorization(),
                metadata.liveDisabled()
        );
    }
}
