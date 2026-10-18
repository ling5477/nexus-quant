package com.guidinglight.nexusquant.strategy.domain;

import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.LiquidityRole;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Side;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Charge;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.DiscountFunding;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Policy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/** 将冻结策略的目标敞口转换为受现金、在途订单和显式 SIM 规则约束的增量订单。 */
public final class SpotTargetSizer {
    private static final BigDecimal BPS = new BigDecimal("10000");

    private SpotTargetSizer() {}

    /** 现金约束先按未到账返佣为零检查，结果中的带符号费用只描述成交后的预期现金。 */
    public static Result size(BigDecimal targetExposure, State state, Rules rules) {
        Objects.requireNonNull(rules, "rules");
        return size(
                targetExposure,
                state,
                rules.quantityStep(),
                rules.priceTick(),
                rules.minimumQuantity(),
                rules.minimumNotional(),
                rules.slippageBps(),
                rules.feeRate(),
                null,
                null,
                null,
                Instant.EPOCH,
                BigDecimal.ZERO);
    }

    /** 方向规则没有全局feeRate；base扣费改变收到数量，quote本金预留不借用尚未到账的返佣。 */
    public static Result size(BigDecimal targetExposure, State state, DirectionalRules rules) {
        Objects.requireNonNull(rules, "rules");
        return size(
                targetExposure,
                state,
                rules.quantityStep(),
                rules.priceTick(),
                rules.minimumQuantity(),
                rules.minimumNotional(),
                rules.slippageBps(),
                null,
                rules.feeModel(),
                rules.symbol(),
                null,
                Instant.EPOCH,
                BigDecimal.ZERO);
    }

    /** 第三资产只计入权益；本金仍限于quote现金，funding须已经扣除其他订单的正费用预留。 */
    public static Result size(
            BigDecimal targetExposure,
            State state,
            DirectionalRules rules,
            DiscountFunding funding,
            Instant executionAt,
            BigDecimal additionalAssetQuoteValue) {
        Objects.requireNonNull(rules, "rules");
        return size(
                targetExposure,
                state,
                rules.quantityStep(),
                rules.priceTick(),
                rules.minimumQuantity(),
                rules.minimumNotional(),
                rules.slippageBps(),
                null,
                rules.feeModel(),
                rules.symbol(),
                funding,
                executionAt,
                additionalAssetQuoteValue);
    }

    private static Result size(
            BigDecimal targetExposure,
            State state,
            BigDecimal quantityStep,
            BigDecimal priceTick,
            BigDecimal minimumQuantity,
            BigDecimal minimumNotional,
            BigDecimal slippageBps,
            BigDecimal scalarRate,
            SpotFeeModel feeModel,
            String symbol,
            DiscountFunding funding,
            Instant executionAt,
            BigDecimal additionalAssetQuoteValue) {
        Objects.requireNonNull(targetExposure, "targetExposure");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(executionAt, "executionAt");
        if (additionalAssetQuoteValue == null || additionalAssetQuoteValue.signum() < 0)
            throw new IllegalArgumentException("FEE_ADDITIONAL_ASSET_VALUE_INVALID");
        if (feeModel != null && feeModel.policy() == Policy.DISCOUNT_ASSET_IF_FUNDED) {
            if (funding == null)
                throw new IllegalArgumentException("FEE_DISCOUNT_FUNDING_REQUIRED");
            if (funding.asset().equals(symbol.substring(0, symbol.indexOf('-')))
                    || funding.asset().equals(symbol.substring(symbol.indexOf('-') + 1)))
                throw new IllegalArgumentException("FEE_DISCOUNT_THIRD_ASSET_REQUIRED");
        }
        if (targetExposure.signum() < 0 || targetExposure.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("targetExposure must be within [0, 1]");
        }
        BigDecimal equity =
                state.cash()
                        .add(state.positionQuantity().multiply(state.referencePrice()))
                        .add(additionalAssetQuoteValue);
        if (equity.signum() <= 0) {
            return Result.rejected("NO_CAPITAL", state.cash());
        }
        BigDecimal targetQuantity =
                floor(
                        equity.multiply(targetExposure)
                                .divide(state.referencePrice(), 18, RoundingMode.DOWN),
                        quantityStep);
        if (targetExposure.signum() > 0 && targetQuantity.signum() == 0) {
            return Result.rejected("DUST", state.cash());
        }
        BigDecimal effectiveQuantity =
                state.positionQuantity()
                        .add(state.pendingBuyQuantity())
                        .subtract(state.pendingSellQuantity());
        BigDecimal delta = targetQuantity.subtract(effectiveQuantity);
        if (delta.signum() == 0) {
            return Result.rejected("ALREADY_AT_TARGET", state.cash());
        }
        boolean buy = delta.signum() > 0;
        BigDecimal slippage = slippageBps.divide(BPS, 18, RoundingMode.HALF_UP);
        BigDecimal rawPrice =
                state.referencePrice()
                        .multiply(
                                buy
                                        ? BigDecimal.ONE.add(slippage)
                                        : BigDecimal.ONE.subtract(slippage));
        BigDecimal fillPrice = buy ? ceil(rawPrice, priceTick) : floor(rawPrice, priceTick);
        if (fillPrice.signum() <= 0) {
            return Result.rejected("INVALID_EXECUTION_PRICE", state.cash());
        }
        BigDecimal quantity = floor(delta.abs(), quantityStep);
        if (buy && feeModel != null) {
            // 收到base扣费时目标数量是净数量；返佣不扩大现金购买力，但在成交模型内仍计入最终净持仓。
            quantity = feeAwareBuyQuantity(delta.abs(), quantityStep, feeModel);
        }
        BigDecimal availableCash = state.cash().subtract(state.pendingBuyCost());
        if (buy) {
            if (availableCash.signum() <= 0) {
                return Result.rejected("INSUFFICIENT_CASH", state.cash());
            }
            BigDecimal affordable =
                    floor(
                            availableCash.divide(
                                    feeModel == null
                                            ? fillPrice.multiply(
                                                    BigDecimal.ONE.add(
                                                            scalarRate.max(BigDecimal.ZERO)))
                                            : fillPrice,
                                    18,
                                    RoundingMode.DOWN),
                            quantityStep);
            quantity = quantity.min(affordable);
            if (feeModel != null && feeModel.policy() == Policy.DISCOUNT_ASSET_IF_FUNDED) {
                // 足额阈值两侧的净base可能跳变；分别验证优惠候选与收到资产候选，不对混合函数盲目二分。
                BigDecimal discountCandidate = floor(delta.abs(), quantityStep).min(affordable);
                quantity =
                        bestDiscountBuyQuantity(
                                quantity,
                                discountCandidate,
                                delta.abs(),
                                feeModel,
                                symbol,
                                fillPrice,
                                funding,
                                executionAt);
            }
        } else {
            quantity =
                    quantity.min(
                            floor(
                                    state.positionQuantity().subtract(state.pendingSellQuantity()),
                                    quantityStep));
        }
        if (quantity.signum() <= 0) {
            return Result.rejected(
                    buy ? "INSUFFICIENT_CASH" : "POSITION_UNAVAILABLE", state.cash());
        }
        if (quantity.compareTo(minimumQuantity) < 0) {
            return Result.rejected("MIN_QUANTITY", state.cash());
        }
        BigDecimal notional = fillPrice.multiply(quantity);
        if (notional.compareTo(minimumNotional) < 0) {
            return Result.rejected("MIN_NOTIONAL", state.cash());
        }
        // 即使成交返佣足以抵消超额，也不能在撮合前动用它；在途预留同样按正费用上取整。
        BigDecimal reserve =
                feeModel == null
                        ? SpotFeeReserve.buyCost(notional, scalarRate)
                        : feeModel.quoteReserve(quantity, fillPrice);
        if (buy && reserve.compareTo(availableCash) > 0) {
            return Result.rejected("FEE_RESERVE_EXCEEDS_CASH", state.cash());
        }
        Charge charge =
                feeModel == null
                        ? null
                        : feeModel.charge(
                                symbol,
                                buy ? Side.BUY : Side.SELL,
                                LiquidityRole.TAKER,
                                quantity,
                                fillPrice,
                                funding,
                                executionAt);
        BigDecimal fee =
                charge == null
                        ? notional.multiply(scalarRate).setScale(18, RoundingMode.HALF_UP)
                        : charge.quoteValue();
        BigDecimal cashFee =
                charge == null ? fee : charge.debit(symbol.substring(symbol.indexOf('-') + 1));
        BigDecimal remainingCash =
                buy
                        ? state.cash().subtract(notional).subtract(cashFee)
                        : state.cash().add(notional).subtract(cashFee);
        if (remainingCash.subtract(state.pendingBuyCost()).signum() < 0) {
            return Result.rejected("FEE_RESERVE_EXCEEDS_CASH", state.cash());
        }
        BigDecimal slippageCost =
                fillPrice.subtract(state.referencePrice()).abs().multiply(quantity);
        return new Result(
                true,
                buy ? "BUY" : "SELL",
                quantity,
                fillPrice,
                fee,
                slippageCost,
                remainingCash,
                "EXECUTABLE",
                charge);
    }

    private static BigDecimal floor(BigDecimal value, BigDecimal step) {
        return value.divideToIntegralValue(step).multiply(step);
    }

    private static BigDecimal ceil(BigDecimal value, BigDecimal step) {
        return value.divide(step, 0, RoundingMode.CEILING).multiply(step);
    }

    /**
     * 连续公式只给出初始上界；HALF_UP后的净数量必须再次比较目标。
     * lot是费用最小单位的整数倍时净数量单调，可以用最多128步的整数二分收敛，避免高费率下逐lot回退。
     */
    private static BigDecimal feeAwareBuyQuantity(
            BigDecimal delta, BigDecimal step, SpotFeeModel model) {
        BigDecimal rate = model.rate(Side.BUY, LiquidityRole.TAKER);
        BigDecimal gross =
                floor(delta.divide(BigDecimal.ONE.subtract(rate), 18, RoundingMode.DOWN), step);
        if (settledBaseQuantity(gross, rate, model.precision().baseScale()).compareTo(delta) <= 0)
            return gross;
        BigInteger low = BigInteger.ZERO,
                high = gross.divideToIntegralValue(step).toBigIntegerExact();
        if (high.bitLength() > 128)
            throw new IllegalArgumentException("FEE_DIRECTIONAL_SIZING_BOUND_EXCEEDED");
        for (int iteration = 0; iteration < 128 && low.compareTo(high) < 0; iteration++) {
            BigInteger mid = low.add(high).add(BigInteger.ONE).shiftRight(1);
            BigDecimal candidate = step.multiply(new BigDecimal(mid));
            if (settledBaseQuantity(candidate, rate, model.precision().baseScale()).compareTo(delta)
                    <= 0) low = mid;
            else high = mid.subtract(BigInteger.ONE);
        }
        if (!low.equals(high))
            throw new IllegalArgumentException("FEE_DIRECTIONAL_SIZING_BOUND_EXCEEDED");
        return step.multiply(new BigDecimal(low));
    }

    private static BigDecimal settledBaseQuantity(
            BigDecimal gross, BigDecimal rate, int baseScale) {
        return gross.subtract(gross.multiply(rate).setScale(baseScale, RoundingMode.HALF_UP));
    }

    private static BigDecimal bestDiscountBuyQuantity(
            BigDecimal receivedCandidate,
            BigDecimal discountCandidate,
            BigDecimal delta,
            SpotFeeModel model,
            String symbol,
            BigDecimal price,
            DiscountFunding funding,
            Instant at) {
        BigDecimal best = BigDecimal.ZERO;
        BigDecimal bestNet = BigDecimal.ZERO;
        boolean bestDiscounted = false;
        String base = symbol.substring(0, symbol.indexOf('-'));
        for (BigDecimal candidate : new BigDecimal[] {receivedCandidate, discountCandidate}) {
            if (candidate.signum() <= 0) continue;
            Charge charge =
                    model.charge(
                            symbol, Side.BUY, LiquidityRole.TAKER, candidate, price, funding, at);
            BigDecimal net = candidate.subtract(charge.debit(base));
            boolean discounted = "DISCOUNT_ASSET_USED".equals(charge.paymentStatus());
            // 比较实际净量而非gross量；不能为了多买gross主动跨过足额阈值，反而丢掉已经可用的优惠。
            if (net.compareTo(delta) <= 0
                    && (net.compareTo(bestNet) > 0
                            || (net.compareTo(bestNet) == 0
                                    && (discounted && !bestDiscounted
                                            || discounted == bestDiscounted
                                                    && (best.signum() == 0
                                                            || candidate.compareTo(best) < 0))))) {
                best = candidate;
                bestNet = net;
                bestDiscounted = discounted;
            }
        }
        return best;
    }

    public record State(
            BigDecimal cash,
            BigDecimal positionQuantity,
            BigDecimal pendingBuyQuantity,
            BigDecimal pendingSellQuantity,
            BigDecimal pendingBuyCost,
            BigDecimal referencePrice) {
        public State {
            Objects.requireNonNull(cash, "cash");
            Objects.requireNonNull(positionQuantity, "positionQuantity");
            Objects.requireNonNull(pendingBuyQuantity, "pendingBuyQuantity");
            Objects.requireNonNull(pendingSellQuantity, "pendingSellQuantity");
            Objects.requireNonNull(pendingBuyCost, "pendingBuyCost");
            Objects.requireNonNull(referencePrice, "referencePrice");
            if (cash.signum() < 0
                    || positionQuantity.signum() < 0
                    || pendingBuyQuantity.signum() < 0
                    || pendingSellQuantity.signum() < 0
                    || pendingBuyCost.signum() < 0
                    || referencePrice.signum() <= 0
                    || pendingSellQuantity.compareTo(positionQuantity) > 0) {
                throw new IllegalArgumentException("invalid spot capital state");
            }
        }
    }

    public record Rules(
            BigDecimal quantityStep,
            BigDecimal priceTick,
            BigDecimal minimumQuantity,
            BigDecimal minimumNotional,
            BigDecimal feeRate,
            BigDecimal slippageBps) {
        public Rules {
            Objects.requireNonNull(quantityStep, "quantityStep");
            Objects.requireNonNull(priceTick, "priceTick");
            Objects.requireNonNull(minimumQuantity, "minimumQuantity");
            Objects.requireNonNull(minimumNotional, "minimumNotional");
            Objects.requireNonNull(feeRate, "feeRate");
            Objects.requireNonNull(slippageBps, "slippageBps");
            if (quantityStep.signum() <= 0
                    || priceTick.signum() <= 0
                    || minimumQuantity.signum() <= 0
                    || minimumNotional.signum() <= 0
                    || feeRate.abs().compareTo(BigDecimal.ONE) >= 0
                    || slippageBps.signum() < 0
                    || slippageBps.compareTo(BPS) >= 0) {
                throw new IllegalArgumentException("invalid explicit SIM rule or cost assumption");
            }
        }
    }

    public record Result(
            boolean executable,
            String side,
            BigDecimal quantity,
            BigDecimal fillPrice,
            BigDecimal fee,
            BigDecimal slippageCost,
            BigDecimal remainingCash,
            String reason,
            Charge feeCharge) {
        /** 旧调用保持fee为quote成本；新调用另外返回原币费用，禁止将估值再次当作现金扣款。 */
        public Result(
                boolean executable,
                String side,
                BigDecimal quantity,
                BigDecimal fillPrice,
                BigDecimal fee,
                BigDecimal slippageCost,
                BigDecimal remainingCash,
                String reason) {
            this(
                    executable,
                    side,
                    quantity,
                    fillPrice,
                    fee,
                    slippageCost,
                    remainingCash,
                    reason,
                    null);
        }

        /** 只在成交后使用的净base增量；pending预算不得把负手续费预支为新的可用持仓。 */
        public BigDecimal settledBaseDelta(String symbol) {
            if (!executable) return BigDecimal.ZERO;
            if (symbol == null || !symbol.matches("[A-Z0-9]{1,20}-[A-Z0-9]{1,20}"))
                throw new IllegalArgumentException("FEE_MODEL_SYMBOL_INVALID");
            BigDecimal signedQuantity = "BUY".equals(side) ? quantity : quantity.negate();
            return feeCharge == null
                    ? signedQuantity
                    : signedQuantity.subtract(
                            feeCharge.debit(symbol.substring(0, symbol.indexOf('-'))));
        }

        static Result rejected(String reason, BigDecimal cash) {
            return new Result(
                    false,
                    null,
                    BigDecimal.ZERO,
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    cash,
                    reason);
        }
    }

    /** 两种原生支付策略共用市场约束；优惠策略必须通过带显式资金与执行时点的重载消费。 */
    public record DirectionalRules(
            String symbol,
            BigDecimal quantityStep,
            BigDecimal priceTick,
            BigDecimal minimumQuantity,
            BigDecimal minimumNotional,
            BigDecimal slippageBps,
            SpotFeeModel feeModel) {
        public DirectionalRules {
            Objects.requireNonNull(quantityStep);
            Objects.requireNonNull(priceTick);
            Objects.requireNonNull(minimumQuantity);
            Objects.requireNonNull(minimumNotional);
            Objects.requireNonNull(slippageBps);
            Objects.requireNonNull(feeModel);
            if (symbol == null
                    || !symbol.matches("[A-Z0-9]{1,20}-[A-Z0-9]{1,20}")
                    || quantityStep.signum() <= 0
                    || priceTick.signum() <= 0
                    || minimumQuantity.signum() <= 0
                    || minimumNotional.signum() <= 0
                    || slippageBps.signum() < 0
                    || slippageBps.compareTo(BPS) >= 0)
                throw new IllegalArgumentException("FEE_DIRECTIONAL_RULE_INVALID");
            if (feeModel.policy() != Policy.RECEIVED_ASSET
                    && feeModel.policy() != Policy.DISCOUNT_ASSET_IF_FUNDED)
                throw new IllegalArgumentException("FEE_DIRECTIONAL_SIZING_POLICY_UNSUPPORTED");
            if (quantityStep.stripTrailingZeros().scale() > feeModel.precision().baseScale())
                throw new IllegalArgumentException("FEE_DIRECTIONAL_QUANTITY_PRECISION_MISMATCH");
        }
    }
}
