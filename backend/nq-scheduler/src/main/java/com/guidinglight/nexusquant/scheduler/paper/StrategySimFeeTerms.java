package com.guidinglight.nexusquant.scheduler.paper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.LiquidityRole;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Side;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeExecutionSpec;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Charge;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.DiscountFunding;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Precision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** 正式SIM决策的成本消费桥；标量与方向模型互斥，原币费用不以quote估值替代扣款。 */
final class StrategySimFeeTerms {
    private final SpotFeeModel model;

    private StrategySimFeeTerms(SpotFeeModel model) {
        this.model = Objects.requireNonNull(model);
    }

    /** 来源真实性由上游持久观察与run核验负责；此处只重建已冻结且有界的消费合同，不访问供应方。 */
    static StrategySimFeeTerms from(
            BigDecimal scalarRate, String frozenExecutionSpec, ObjectMapper mapper) {
        if ((scalarRate == null) == (frozenExecutionSpec == null)) throw invalid();
        if (scalarRate != null)
            return new StrategySimFeeTerms(
                    SpotFeeModel.quoteNotional(scalarRate, Precision.canonical()));
        if (frozenExecutionSpec.isBlank() || frozenExecutionSpec.length() > 65_536) throw invalid();
        try {
            SpotFeeModel model =
                    SpotFeeExecutionSpec.directional(mapper.readTree(frozenExecutionSpec), mapper);
            if (model == null) throw invalid();
            return new StrategySimFeeTerms(model);
        } catch (JsonProcessingException failure) {
            throw invalid();
        }
    }

    /** 决策冻结的方向、数量、成交价和时点共同确定费用；成交后才交给canonical账务应用符号。 */
    Charge charge(
            String symbol,
            String side,
            BigDecimal quantity,
            BigDecimal price,
            Instant executionAt) {
        return charge(symbol, side, quantity, price, executionAt, null);
    }

    /** 优惠输入只能来自原决策时已核验且扣除其他预留的余额，不能在撮合时换成最新资金。 */
    Charge charge(
            String symbol,
            String side,
            BigDecimal quantity,
            BigDecimal price,
            Instant executionAt,
            DiscountFunding funding) {
        if (!"BUY".equals(side) && !"SELL".equals(side)) throw invalid();
        return model.charge(
                symbol,
                Side.valueOf(side),
                LiquidityRole.TAKER,
                quantity,
                price,
                funding,
                executionAt);
    }

    /** 在途本金逐笔按正式精度预留，负费用不提供现金购买力，base费用的估值也不能重复预留quote。 */
    BigDecimal quoteReserve(BigDecimal quantity, BigDecimal price) {
        return model.quoteReserve(quantity, price);
    }

    /**
     * 投影目标敞口时使用在途买单最终净base以避免重复补仓；这不是可卖持仓。
     * 卖出上限仍由已结算position减在途卖单决定，不能动用预计返佣或未成交数量。
     */
    BigDecimal pendingBuyExposure(
            String symbol, BigDecimal quantity, BigDecimal price, Instant executionAt) {
        Charge fee = charge(symbol, "BUY", quantity, price, executionAt);
        return pendingBuyExposure(symbol, quantity, fee);
    }

    /** 在途原币费用已被冻结时只投影该支付选择；不重新判断优惠资产是否足额。 */
    BigDecimal pendingBuyExposure(String symbol, BigDecimal quantity, Charge fee) {
        String base = symbol.substring(0, symbol.indexOf('-'));
        BigDecimal received = quantity.subtract(fee.debit(base));
        if (received.signum() < 0) throw invalid();
        return received;
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("SIM_FEE_TERMS_INVALID");
    }
}
