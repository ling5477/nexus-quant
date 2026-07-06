package com.guidinglight.nexusquant.strategy.application.shadowrun;

/**
 * GateR-4 risk preflight 单条规则结果。
 *
 * <p>该结果只用于 Shadow Run 本地风险预览，不调用真实 risk engine，不放行真实交易，也不写真实订单。
 *
 * @param ruleId   本地规则 id
 * @param status   规则结果，例如 ALLOW / WARN / BLOCK
 * @param severity 风险严重度，例如 INFO / WARN / BLOCK
 * @param message  脱敏说明
 */
public record RiskPreflightRuleResult(
        String ruleId,
        String status,
        String severity,
        String message
) {

    public RiskPreflightRuleResult {
        ruleId = StrategyDecisionTrace.requireText(ruleId, "ruleId");
        status = StrategyDecisionTrace.requireText(status, "status");
        severity = StrategyDecisionTrace.requireText(severity, "severity");
        message = StrategyDecisionTrace.requireText(message, "message");
    }
}
