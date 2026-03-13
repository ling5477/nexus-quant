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
 * OrderPrecisionRule 统一校验价格、数量与订单类型的最小数值边界。
 * <p>
 * Why:
 * GateD 第一批先用平台默认精度兜底，避免不合法 scale 的请求直接打到 adapter，再在后续阶段接入交易所 symbol metadata。
 */
public class OrderPrecisionRule implements RiskRule {

    private static final String RULE_CODE = "INVALID_PRECISION";

    private final PreTradeRiskSettings settings;

    public OrderPrecisionRule(PreTradeRiskSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    @Override
    public String ruleCode() {
        return RULE_CODE;
    }

    @Override
    public String ruleName() {
        return "OrderPrecisionRule";
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public Optional<RiskDecisionResult> evaluate(RiskContext context) {
        BigDecimal quantity = context.command().quantity();
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return rejected(context, "quantity must be positive");
        }
        if (!hasSupportedScale(quantity, settings.maxQuantityScale())
                || NumericPolicy.normalize(NumericType.QTY, quantity).compareTo(quantity) != 0) {
            return rejected(context, "quantity precision exceeds configured scale");
        }
        if ("LIMIT".equalsIgnoreCase(context.command().type())) {
            BigDecimal price = context.command().price();
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                return rejected(context, "limit order price must be positive");
            }
            if (!hasSupportedScale(price, settings.maxPriceScale())
                    || NumericPolicy.normalize(NumericType.PRICE, price).compareTo(price) != 0) {
                return rejected(context, "price precision exceeds configured scale");
            }
        }
        return Optional.empty();
    }

    private Optional<RiskDecisionResult> rejected(RiskContext context, String reason) {
        return Optional.of(RiskDecisionResult.reject(
                RULE_CODE,
                ruleName(),
                reason,
                true,
                RiskSeverity.HIGH,
                context.traceId()
        ));
    }

    private boolean hasSupportedScale(BigDecimal value, int maxScale) {
        return Math.max(value.stripTrailingZeros().scale(), 0) <= maxScale;
    }
}
