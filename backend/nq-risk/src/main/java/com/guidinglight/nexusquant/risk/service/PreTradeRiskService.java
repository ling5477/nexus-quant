package com.guidinglight.nexusquant.risk.service;

import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;

import java.util.Objects;

/**
 * PreTradeRiskService 负责 GateD 的统一前置硬风控规则链。
 */
public class PreTradeRiskService implements RiskGate {

    private final RiskRuleRegistry riskRuleRegistry;

    public PreTradeRiskService(RiskRuleRegistry riskRuleRegistry) {
        this.riskRuleRegistry = Objects.requireNonNull(riskRuleRegistry, "riskRuleRegistry must not be null");
    }

    @Override
    public RiskDecisionResult evaluate(RiskContext context) {
        Objects.requireNonNull(context, "context must not be null");
        for (RiskRule rule : riskRuleRegistry.rules()) {
            var rejected = rule.evaluate(context);
            if (rejected.isPresent()) {
                return rejected.get();
            }
        }
        return RiskDecisionResult.allow("RISK_RULES_PASSED", "AllRulesPassed", context.traceId());
    }
}
