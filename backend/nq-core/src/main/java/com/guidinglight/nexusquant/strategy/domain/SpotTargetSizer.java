package com.guidinglight.nexusquant.strategy.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** 将冻结策略的目标敞口转换为受现金、在途订单和显式 SIM 规则约束的增量订单。 */
public final class SpotTargetSizer {
    private static final BigDecimal BPS = new BigDecimal("10000");

    private SpotTargetSizer() { }

    public static Result size(BigDecimal targetExposure, State state, Rules rules) {
        Objects.requireNonNull(targetExposure, "targetExposure");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rules, "rules");
        if (targetExposure.signum() < 0 || targetExposure.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("targetExposure must be within [0, 1]");
        }
        BigDecimal equity = state.cash().add(state.positionQuantity().multiply(state.referencePrice()));
        if (equity.signum() <= 0) {
            return Result.rejected("NO_CAPITAL", state.cash());
        }
        BigDecimal targetQuantity = floor(equity.multiply(targetExposure)
                .divide(state.referencePrice(), 18, RoundingMode.DOWN), rules.quantityStep());
        if (targetExposure.signum() > 0 && targetQuantity.signum() == 0) {
            return Result.rejected("DUST", state.cash());
        }
        BigDecimal effectiveQuantity = state.positionQuantity().add(state.pendingBuyQuantity())
                .subtract(state.pendingSellQuantity());
        BigDecimal delta = targetQuantity.subtract(effectiveQuantity);
        if (delta.signum() == 0) {
            return Result.rejected("ALREADY_AT_TARGET", state.cash());
        }
        boolean buy = delta.signum() > 0;
        BigDecimal slippage = rules.slippageBps().divide(BPS, 18, RoundingMode.HALF_UP);
        BigDecimal rawPrice = state.referencePrice().multiply(
                buy ? BigDecimal.ONE.add(slippage) : BigDecimal.ONE.subtract(slippage));
        BigDecimal fillPrice = buy ? ceil(rawPrice, rules.priceTick()) : floor(rawPrice, rules.priceTick());
        if (fillPrice.signum() <= 0) {
            return Result.rejected("INVALID_EXECUTION_PRICE", state.cash());
        }
        BigDecimal quantity = floor(delta.abs(), rules.quantityStep());
        BigDecimal availableCash = state.cash().subtract(state.pendingBuyCost());
        if (buy) {
            if (availableCash.signum() <= 0) {
                return Result.rejected("INSUFFICIENT_CASH", state.cash());
            }
            BigDecimal affordable = floor(availableCash.divide(
                    fillPrice.multiply(BigDecimal.ONE.add(rules.feeRate())), 18, RoundingMode.DOWN),
                    rules.quantityStep());
            quantity = quantity.min(affordable);
        } else {
            quantity = quantity.min(floor(state.positionQuantity().subtract(state.pendingSellQuantity()),
                    rules.quantityStep()));
        }
        if (quantity.signum() <= 0) {
            return Result.rejected(buy ? "INSUFFICIENT_CASH" : "POSITION_UNAVAILABLE", state.cash());
        }
        if (quantity.compareTo(rules.minimumQuantity()) < 0) {
            return Result.rejected("MIN_QUANTITY", state.cash());
        }
        BigDecimal notional = fillPrice.multiply(quantity);
        if (notional.compareTo(rules.minimumNotional()) < 0) {
            return Result.rejected("MIN_NOTIONAL", state.cash());
        }
        BigDecimal fee = notional.multiply(rules.feeRate()).setScale(18, RoundingMode.HALF_UP);
        BigDecimal remainingCash = buy ? state.cash().subtract(notional).subtract(fee)
                : state.cash().add(notional).subtract(fee);
        if (remainingCash.subtract(state.pendingBuyCost()).signum() < 0) {
            return Result.rejected("FEE_RESERVE_EXCEEDS_CASH", state.cash());
        }
        BigDecimal slippageCost = fillPrice.subtract(state.referencePrice()).abs().multiply(quantity);
        return new Result(true, buy ? "BUY" : "SELL", quantity, fillPrice, fee,
                slippageCost, remainingCash, "EXECUTABLE");
    }

    private static BigDecimal floor(BigDecimal value, BigDecimal step) {
        return value.divideToIntegralValue(step).multiply(step);
    }

    private static BigDecimal ceil(BigDecimal value, BigDecimal step) {
        return value.divide(step, 0, RoundingMode.CEILING).multiply(step);
    }

    public record State(BigDecimal cash, BigDecimal positionQuantity, BigDecimal pendingBuyQuantity,
                        BigDecimal pendingSellQuantity, BigDecimal pendingBuyCost, BigDecimal referencePrice) {
        public State {
            Objects.requireNonNull(cash, "cash");
            Objects.requireNonNull(positionQuantity, "positionQuantity");
            Objects.requireNonNull(pendingBuyQuantity, "pendingBuyQuantity");
            Objects.requireNonNull(pendingSellQuantity, "pendingSellQuantity");
            Objects.requireNonNull(pendingBuyCost, "pendingBuyCost");
            Objects.requireNonNull(referencePrice, "referencePrice");
            if (cash.signum() < 0 || positionQuantity.signum() < 0 || pendingBuyQuantity.signum() < 0
                    || pendingSellQuantity.signum() < 0 || pendingBuyCost.signum() < 0
                    || referencePrice.signum() <= 0 || pendingSellQuantity.compareTo(positionQuantity) > 0) {
                throw new IllegalArgumentException("invalid spot capital state");
            }
        }
    }

    public record Rules(BigDecimal quantityStep, BigDecimal priceTick, BigDecimal minimumQuantity,
                        BigDecimal minimumNotional, BigDecimal feeRate, BigDecimal slippageBps) {
        public Rules {
            Objects.requireNonNull(quantityStep, "quantityStep");
            Objects.requireNonNull(priceTick, "priceTick");
            Objects.requireNonNull(minimumQuantity, "minimumQuantity");
            Objects.requireNonNull(minimumNotional, "minimumNotional");
            Objects.requireNonNull(feeRate, "feeRate");
            Objects.requireNonNull(slippageBps, "slippageBps");
            if (quantityStep.signum() <= 0 || priceTick.signum() <= 0 || minimumQuantity.signum() <= 0
                    || minimumNotional.signum() <= 0 || feeRate.signum() < 0 || feeRate.compareTo(BigDecimal.ONE) >= 0
                    || slippageBps.signum() < 0 || slippageBps.compareTo(BPS) >= 0) {
                throw new IllegalArgumentException("invalid explicit SIM rule or cost assumption");
            }
        }
    }

    public record Result(boolean executable, String side, BigDecimal quantity, BigDecimal fillPrice,
                         BigDecimal fee, BigDecimal slippageCost, BigDecimal remainingCash, String reason) {
        static Result rejected(String reason, BigDecimal cash) {
            return new Result(false, null, BigDecimal.ZERO, null, BigDecimal.ZERO,
                    BigDecimal.ZERO, cash, reason);
        }
    }
}
