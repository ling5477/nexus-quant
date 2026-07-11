package com.guidinglight.nexusquant.validationreview.api.web;

import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable review case 的安全 API 表示。
 *
 * <p>不暴露 evidenceAnchor、credential、private payload 或任何 trading authorization 字段。
 */
public record ValidationReviewCaseResponse(
        UUID id,
        long ownerId,
        String evidenceType,
        String evidenceSource,
        String severity,
        String state,
        String title,
        String summary,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Long acknowledgedBy,
        Instant acknowledgedAt,
        Long escalatedBy,
        Instant escalatedAt,
        Long resolvedBy,
        Instant resolvedAt,
        Long closedBy,
        Instant closedAt,
        Instant retentionUntil,
        @Schema(description = "仅用于诊断", allowableValues = "true")
        boolean diagnosticOnly,
        @Schema(description = "不会产生交易副作用", allowableValues = "true")
        boolean noSideEffect,
        @Schema(description = "不构成交易授权", allowableValues = "true")
        boolean notTradingAuthorization,
        @Schema(description = "LIVE 始终关闭", allowableValues = "true")
        boolean liveDisabled
) {
    /** @return 只包含 allowlisted case 字段和四个保守 safety flags 的 response */
    public static ValidationReviewCaseResponse from(ValidationReviewCase reviewCase) {
        return new ValidationReviewCaseResponse(
                reviewCase.id(),
                reviewCase.ownerId(),
                reviewCase.evidenceType(),
                reviewCase.evidenceSource(),
                reviewCase.severity().name(),
                reviewCase.state().name(),
                reviewCase.title(),
                reviewCase.summary(),
                reviewCase.version(),
                reviewCase.createdAt(),
                reviewCase.updatedAt(),
                reviewCase.acknowledgedBy(),
                reviewCase.acknowledgedAt(),
                reviewCase.escalatedBy(),
                reviewCase.escalatedAt(),
                reviewCase.resolvedBy(),
                reviewCase.resolvedAt(),
                reviewCase.closedBy(),
                reviewCase.closedAt(),
                reviewCase.retentionUntil(),
                true,
                true,
                true,
                true
        );
    }
}
