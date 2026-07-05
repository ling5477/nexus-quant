package com.guidinglight.nexusquant.strategy.application.evaluationgate;

import java.util.Objects;

/**
 * StrategyEvaluationGateEvidence 描述 gate 必需证据的单项状态。
 *
 * <p>Why: GateQ-1 必须可复盘地说明“缺什么才阻断”。该 record 只保存稳定 code、状态和说明，
 * 不承载 credential material、provider raw payload、交易指令或账户资金信息。
 */
public record StrategyEvaluationGateEvidence(
        String code,
        String status,
        String message
) {
    public StrategyEvaluationGateEvidence {
        code = requireText(code, "code");
        status = requireText(status, "status");
        message = requireText(message, "message");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return Objects.requireNonNull(value, fieldName + " must not be null").trim();
    }
}
