package com.guidinglight.nexusquant.risk.service;

import com.guidinglight.nexusquant.common.numeric.NumericPolicy;
import com.guidinglight.nexusquant.common.numeric.NumericType;
import com.guidinglight.nexusquant.contracts.model.RiskSeverity;
import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * MaxOrderAmountRule 校验单笔最大下单额。
 */
public class MaxOrderAmountRule implements RiskRule {

    private static final String RULE_CODE = "MAX_ORDER_NOTIONAL_EXCEEDED";

    private final PreTradeRiskSettings settings;

    public MaxOrderAmountRule(PreTradeRiskSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    @Override
    public String ruleCode() {
        return RULE_CODE;
    }

    @Override
    public String ruleName() {
        return "MaxOrderAmountRule";
    }

    @Override
    public int order() {
        return 80;
    }

    @Override
    public Optional<RiskDecisionResult> evaluate(RiskContext context) {
        BigDecimal price = context.command().price();
        if (price == null || settings.maxOrderNotional().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        BigDecimal notional = NumericPolicy.normalize(NumericType.PRICE, price)
                .multiply(NumericPolicy.normalize(NumericType.QTY, context.command().quantity()));
        if (notional.compareTo(settings.maxOrderNotional()) <= 0) {
            return Optional.empty();
        }
        return Optional.of(RiskDecisionResult.reject(
                RULE_CODE,
                ruleName(),
                "order notional exceeds configured maximum",
                true,
                RiskSeverity.HIGH,
                context.traceId()
        ));
    }
}
