package com.guidinglight.nexusquant.strategy.application.evaluationgate;

/**
 * StrategyEvaluationGateReason 是 blocker / warning 的稳定原因项。
 *
 * <p>Why: 前端和审计日志需要按 code 做分类展示。message 只解释业务边界，不包含密钥、token、
 * raw request/response、private endpoint 或任何交易执行材料。
 */
public record StrategyEvaluationGateReason(
        String code,
        String severity,
        String message
) {
    public StrategyEvaluationGateReason {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (severity == null || severity.isBlank()) {
            throw new IllegalArgumentException("severity must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        code = code.trim();
        severity = severity.trim();
        message = message.trim();
    }
}
