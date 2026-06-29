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

    public static final String PAPER_RISK_NOT_LIVE_RISK_APPROVAL =
            "PAPER_RISK_NOT_LIVE_RISK_APPROVAL";

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

    /**
     * 风控通过是否构成 LIVE 交易授权。
     *
     * <p>Why:
     * `ALLOW` 只表示本次 NQ 前置风控规则未拒绝，不代表 credential、permission probe、adapter
     * readiness 或 LIVE 授权已成立。Paper risk pass 更不能被提升为 live risk approval。</p>
     *
     * @return 恒为 false；真实 LIVE 授权必须由单独 Gate 和 adapter readiness/permission 证明
     */
    public boolean authorizesLiveTrading() {
        return false;
    }

    /**
     * 返回风控结果不能作为 LIVE 授权的稳定 reason code。
     *
     * @return Paper-to-real 边界 reason code
     */
    public String liveAuthorizationBoundaryReason() {
        return PAPER_RISK_NOT_LIVE_RISK_APPROVAL;
    }
}
