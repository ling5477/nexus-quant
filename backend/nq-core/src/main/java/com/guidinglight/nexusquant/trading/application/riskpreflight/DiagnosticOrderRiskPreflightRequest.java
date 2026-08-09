package com.guidinglight.nexusquant.trading.application.riskpreflight;

import com.guidinglight.nexusquant.trading.application.orderpreview.DryRunOrderPreviewResult;
import com.guidinglight.nexusquant.trading.application.reconciliation.ReconciliationResult;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * GateW-3 internal-only risk preflight 请求。
 *
 * <p>preview/reconciliation 允许为 null，以显式表达 NOT_EVALUATED。该对象不是 Controller DTO，
 * 不包含 PlaceOrderCommand、credential material、provider endpoint 或 mutable entity。</p>
 *
 * @param traceId               调用链标识，不得包含敏感信息
 * @param evaluationTime        显式 deterministic 评估时间
 * @param diagnosticEnvironment 请求的本地 diagnostic environment，SIM 或 LIVE
 * @param orderPreviewResult    已生成的 immutable preview result；null 表示未评估
 * @param reconciliationResult  已生成的 immutable reconciliation result；null 表示未评估
 * @param facts                 credential-material-free 本地事实快照
 */
public record DiagnosticOrderRiskPreflightRequest(
        String traceId,
        Instant evaluationTime,
        String diagnosticEnvironment,
        DryRunOrderPreviewResult orderPreviewResult,
        ReconciliationResult reconciliationResult,
        RiskPreflightFactBundle facts
) {

    public DiagnosticOrderRiskPreflightRequest {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        Objects.requireNonNull(evaluationTime, "evaluationTime must not be null");
        String normalizedEnvironment = diagnosticEnvironment == null
                ? null
                : diagnosticEnvironment.trim().toUpperCase(Locale.ROOT);
        if (!ListHolder.SUPPORTED_ENVIRONMENTS.contains(normalizedEnvironment)) {
            throw new IllegalArgumentException("diagnosticEnvironment must be SIM or LIVE");
        }
        diagnosticEnvironment = normalizedEnvironment;
        Objects.requireNonNull(facts, "facts must not be null");
    }

    private static final class ListHolder {
        private static final java.util.List<String> SUPPORTED_ENVIRONMENTS = java.util.List.of("SIM", "LIVE");

        private ListHolder() {
        }
    }
}
