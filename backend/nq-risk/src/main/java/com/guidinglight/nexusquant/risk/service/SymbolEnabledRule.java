package com.guidinglight.nexusquant.risk.service;

import com.guidinglight.nexusquant.contracts.model.RiskSeverity;
import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;

import java.util.Objects;
import java.util.Optional;

/**
 * SymbolEnabledRule 检查 venue 与 symbol 组合是否允许进入执行通道。
 */
public class SymbolEnabledRule implements RiskRule {

    private static final String RULE_CODE = "SYMBOL_NOT_ALLOWED";

    private final PreTradeRiskSettings settings;

    public SymbolEnabledRule(PreTradeRiskSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    @Override
    public String ruleCode() {
        return RULE_CODE;
    }

    @Override
    public String ruleName() {
        return "SymbolEnabledRule";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public Optional<RiskDecisionResult> evaluate(RiskContext context) {
        if (settings.isSymbolEnabled(context.command().symbol())) {
            return Optional.empty();
        }
        return Optional.of(RiskDecisionResult.reject(
                RULE_CODE,
                ruleName(),
                "symbol is not enabled for the target venue",
                true,
                RiskSeverity.HIGH,
                context.traceId()
        ));
    }
}
