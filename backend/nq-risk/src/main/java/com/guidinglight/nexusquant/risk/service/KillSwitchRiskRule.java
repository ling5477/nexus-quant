package com.guidinglight.nexusquant.risk.service;

import com.guidinglight.nexusquant.contracts.model.RiskSeverity;
import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;

import java.util.Objects;
import java.util.Optional;

/**
 * KillSwitchRiskRule 在系统或账户处于紧急停止态时直接拒绝下单。
 */
public class KillSwitchRiskRule implements RiskRule {

    private static final String RULE_CODE = "KILL_SWITCH_TRIGGERED";

    private final KillSwitchService killSwitchService;

    public KillSwitchRiskRule(KillSwitchService killSwitchService) {
        this.killSwitchService = Objects.requireNonNull(killSwitchService, "killSwitchService must not be null");
    }

    @Override
    public String ruleCode() {
        return RULE_CODE;
    }

    @Override
    public String ruleName() {
        return "KillSwitchRule";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Optional<RiskDecisionResult> evaluate(RiskContext context) {
        if (!killSwitchService.isEnabled()) {
            return Optional.empty();
        }
        return Optional.of(RiskDecisionResult.reject(
                RULE_CODE,
                ruleName(),
                "system kill switch is enabled",
                true,
                RiskSeverity.HIGH,
                context.traceId()
        ));
    }
}
