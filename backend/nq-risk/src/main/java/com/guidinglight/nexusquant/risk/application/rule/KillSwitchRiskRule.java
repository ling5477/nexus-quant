package com.guidinglight.nexusquant.risk.application.rule;

import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchSnapshot;

import com.guidinglight.nexusquant.contracts.model.RiskSeverity;
import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;
import com.guidinglight.nexusquant.risk.model.TradeEnvironment;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchStatus;

import java.util.Objects;
import java.util.Optional;

/**
 * KillSwitchRiskRule 阻断 LIVE 下单；明确的 SIM 环境可在 ENGAGED 时继续接受其他风控。
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
        Objects.requireNonNull(context, "context must not be null");
        KillSwitchSnapshot snapshot = killSwitchService.snapshot();
        if (context.tradeEnvironment() != null
                && context.tradeEnvironment() != TradeEnvironment.UNKNOWN
                && (!snapshot.blocksOperations()
                || (context.tradeEnvironment() == TradeEnvironment.SIM
                && snapshot.status() == KillSwitchStatus.ENGAGED))) {
            return Optional.empty();
        }
        return Optional.of(RiskDecisionResult.reject(
                RULE_CODE,
                ruleName(),
                "global kill switch blocks trading operations",
                true,
                RiskSeverity.HIGH,
                context.traceId()
        ));
    }
}
