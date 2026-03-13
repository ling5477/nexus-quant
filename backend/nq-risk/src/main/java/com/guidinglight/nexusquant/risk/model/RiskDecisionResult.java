package com.guidinglight.nexusquant.risk.model;

import com.guidinglight.nexusquant.contracts.model.RiskDecision;
import com.guidinglight.nexusquant.contracts.model.RiskSeverity;

/**
 * RiskDecisionResult 表示风控结果。
 * <p>
 * Why:
 * GateD 要求风控拒绝必须输出 `ruleCode / ruleName / rejectReason / hardReject`，
 * 这样 core 才能把拒绝证据统一写入 audit_logs 与 event_store。
 */
public record RiskDecisionResult(
        RiskDecision decision,
        String ruleCode,
        String ruleName,
        String rejectReason,
        boolean hardReject,
        RiskSeverity severity,
        String traceId
) {

    public static RiskDecisionResult allow(String ruleCode, String ruleName, String traceId) {
        return new RiskDecisionResult(RiskDecision.ALLOW, ruleCode, ruleName, "risk rules passed", false, RiskSeverity.LOW, traceId);
    }

    public static RiskDecisionResult reject(
            String ruleCode,
            String ruleName,
            String rejectReason,
            boolean hardReject,
            RiskSeverity severity,
            String traceId
    ) {
        return new RiskDecisionResult(RiskDecision.REJECT, ruleCode, ruleName, rejectReason, hardReject, severity, traceId);
    }
}
