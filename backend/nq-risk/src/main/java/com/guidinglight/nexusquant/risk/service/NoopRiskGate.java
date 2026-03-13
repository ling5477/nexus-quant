package com.guidinglight.nexusquant.risk.service;

import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;

/**
 * NoopRiskGate 是仅供测试或显式本地 profile 使用的放行实现。
 */
public class NoopRiskGate implements RiskGate {

    @Override
    public RiskDecisionResult evaluate(RiskContext context) {
        return RiskDecisionResult.allow("NO_RULE_CONFIGURED", "NoopRiskGate", context.traceId());
    }
}
