package com.guidinglight.nexusquant.risk.service;

import com.guidinglight.nexusquant.contracts.model.RiskSeverity;
import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;

import java.util.Objects;
import java.util.Optional;

/**
 * AccountTradingEnabledRule 检查账户是否允许交易。
 */
public class AccountTradingEnabledRule implements RiskRule {

    private static final String RULE_CODE = "ACCOUNT_TRADING_DISABLED";

    private final PreTradeRiskSettings settings;

    public AccountTradingEnabledRule(PreTradeRiskSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    @Override
    public String ruleCode() {
        return RULE_CODE;
    }

    @Override
    public String ruleName() {
        return "AccountTradingEnabledRule";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public Optional<RiskDecisionResult> evaluate(RiskContext context) {
        if (settings.isAccountTradingEnabled(context.command().accountId())) {
            return Optional.empty();
        }
        return Optional.of(RiskDecisionResult.reject(
                RULE_CODE,
                ruleName(),
                "account trading is disabled",
                true,
                RiskSeverity.HIGH,
                context.traceId()
        ));
    }
}
