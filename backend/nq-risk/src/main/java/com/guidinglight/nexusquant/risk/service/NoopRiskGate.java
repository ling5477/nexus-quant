package com.guidinglight.nexusquant.risk.service;

import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;

/**
 * NoopRiskGate 是仅供测试或显式本地 profile 使用的放行实现。
 * <p>
 * Why:
 * PRE-CLEAN-1 保留该类只是为了 local/test 兼容，不允许把它视为正式风控策略实现。
 */
public class NoopRiskGate implements RiskGate {

    @Override
    public RiskDecisionResult evaluate(RiskContext context) {
        return RiskDecisionResult.allow("NO_RULE_CONFIGURED", "NoopRiskGate", context.traceId());
    }
}
