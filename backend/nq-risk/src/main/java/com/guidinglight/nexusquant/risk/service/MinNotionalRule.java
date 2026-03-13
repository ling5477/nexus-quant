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
 * MinNotionalRule 校验订单最小名义金额。
 * <p>
 * Edge:
 * 对没有 price 的市价单，本轮不猜测参考价；只有当 minNotional > 0 且 price 缺失时才拒绝，
 * 这样既满足 GateD 文档“参考价必须明确”，也不破坏默认本地回归链路。
 */
public class MinNotionalRule implements RiskRule {

    private static final String RULE_CODE = "MIN_NOTIONAL_NOT_MET";

    private final PreTradeRiskSettings settings;

    public MinNotionalRule(PreTradeRiskSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    @Override
    public String ruleCode() {
        return RULE_CODE;
    }

    @Override
    public String ruleName() {
        return "MinNotionalRule";
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public Optional<RiskDecisionResult> evaluate(RiskContext context) {
        if (settings.minNotional().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        BigDecimal price = context.command().price();
        if (price == null) {
            return Optional.of(RiskDecisionResult.reject(
                    RULE_CODE,
                    ruleName(),
                    "price is required when min notional is enabled",
                    true,
                    RiskSeverity.HIGH,
                    context.traceId()
            ));
        }
        BigDecimal normalizedPrice = NumericPolicy.normalize(NumericType.PRICE, price);
        BigDecimal normalizedQuantity = NumericPolicy.normalize(NumericType.QTY, context.command().quantity());
        BigDecimal notional = normalizedPrice.multiply(normalizedQuantity);
        if (notional.compareTo(settings.minNotional()) >= 0) {
            return Optional.empty();
        }
        return Optional.of(RiskDecisionResult.reject(
                RULE_CODE,
                ruleName(),
                "order notional is below configured minimum",
                true,
                RiskSeverity.HIGH,
                context.traceId()
        ));
    }
}
