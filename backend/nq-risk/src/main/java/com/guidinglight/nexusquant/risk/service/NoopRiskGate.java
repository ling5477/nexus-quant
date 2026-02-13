package com.guidinglight.nexusquant.risk.service;

import com.guidinglight.nexusquant.contracts.model.RiskDecision;
import com.guidinglight.nexusquant.contracts.model.RiskSeverity;
import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;

/**
 * NoopRiskGate 是默认放行的占位风控实现。
 */
public class NoopRiskGate implements RiskGate {

    @Override
    public RiskDecisionResult evaluate(RiskContext context) {
        return new RiskDecisionResult(RiskDecision.ALLOW, "NO_RULE_CONFIGURED", RiskSeverity.LOW, context.traceId());
    }
}
