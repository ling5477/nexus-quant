package com.guidinglight.nexusquant.research.domain.eval;

import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * TradeOutcomeCalculator 负责基于已闭合成交重建最小 trade outcomes。
 */
public class TradeOutcomeCalculator {

    public Result calculate(List<SimTrade> simTrades) {
        return calculate(simTrades, false);
    }

    /** 冻结策略的成交价已包含滑点；旧独立扣款路径继续保留其原合同，不能仅凭原币字段猜测滑点语义。 */
    public Result calculate(List<SimTrade> simTrades, boolean slippageIncludedInFillPrice) {
        simTrades = orderedTrades(simTrades);
        boolean nativeFees = simTrades.stream().anyMatch(trade -> trade.feeCurrency() != null);
        return nativeFees || slippageIncludedInFillPrice
                ? calculateCashOutcomes(simTrades, slippageIncludedInFillPrice)
                : calculateLegacy(simTrades);
    }

    /** 新事件按真实执行序列复核；旧同时间行没有因果证据，不能用UUID或买卖方向补猜。 */
    public static List<SimTrade> orderedTrades(List<SimTrade> input) {
        boolean hasSequence = input.stream().anyMatch(trade -> trade.executionSequence() != null);
        if (hasSequence && input.stream().anyMatch(trade -> trade.executionSequence() == null))
            throw new IllegalArgumentException("RESEARCH_TRADE_ORDER_UNAVAILABLE");
        List<SimTrade> ordered =
                input.stream()
                        .sorted(
                                hasSequence
                                        ? Comparator.comparing(SimTrade::tradedAt)
                                                .thenComparing(SimTrade::executionSequence)
                                        : Comparator.comparing(SimTrade::tradedAt))
                        .toList();
        for (int index = 0; index < ordered.size(); index++) {
            SimTrade trade = ordered.get(index);
            if (hasSequence && trade.executionSequence() != index + 1L)
                throw new IllegalArgumentException("RESEARCH_TRADE_SEQUENCE_INCONSISTENT");
            if (!hasSequence
                    && index > 0
                    && ordered.get(index - 1).tradedAt().equals(trade.tradedAt()))
                throw new IllegalArgumentException("RESEARCH_TRADE_ORDER_UNAVAILABLE");
            if (index > 0 && !ordered.getFirst().backtestRunId().equals(trade.backtestRunId()))
                throw new IllegalArgumentException("RESEARCH_TRADE_IDENTITY_MISMATCH");
        }
        return ordered;
    }

    /** 按实际现金本金和净收到base分摊闭合成本；base费用估值仅用于统计，不能再次加入现金成本。 */
    private Result calculateCashOutcomes(
            List<SimTrade> simTrades, boolean slippageIncludedInFillPrice) {
        BigDecimal openQuantity = BigDecimal.ZERO;
        BigDecimal openCashCost = BigDecimal.ZERO;
        BigDecimal grossProfit = BigDecimal.ZERO;
        BigDecimal grossLoss = BigDecimal.ZERO;
        int winning = 0, losing = 0, flat = 0;
        for (SimTrade trade : simTrades) {
            BigDecimal extraSlippage =
                    slippageIncludedInFillPrice ? BigDecimal.ZERO : trade.slippageAmount();
            // 第三币已在独立余额结算；闭仓成本消费其冻结quote估值，不对现金再做一次扣款。
            BigDecimal cashCost = trade.outcomeFeeAmount().add(extraSlippage);
            if ("BUY".equals(trade.side())) {
                openQuantity = normalize(openQuantity.add(trade.settledBaseDelta()));
                openCashCost =
                        normalize(
                                openCashCost
                                        .add(trade.quantity().multiply(trade.tradePrice()))
                                        .add(cashCost));
                continue;
            }
            if (!"SELL".equals(trade.side())
                    || openQuantity.signum() <= 0
                    || trade.quantity().compareTo(openQuantity) > 0) {
                // 缺少前置买入或超出净持仓属于不完整事实，不能截断成交数量后伪造可用胜率。
                throw new IllegalArgumentException("TRADE_OUTCOME_POSITION_INCONSISTENT");
            }
            BigDecimal closedQuantity = trade.quantity();
            // 最后一笔完整闭合直接消费剩余本金，避免分摊舍入留下虚假残仓成本或翻转微小盈亏符号。
            BigDecimal allocatedCost =
                    closedQuantity.compareTo(openQuantity) == 0
                            ? openCashCost
                            : openCashCost
                                    .multiply(closedQuantity)
                                    .divide(openQuantity, 18, RoundingMode.HALF_UP);
            BigDecimal outcome =
                    normalize(
                            closedQuantity
                                    .multiply(trade.tradePrice())
                                    .subtract(cashCost)
                                    .subtract(allocatedCost));
            if (outcome.signum() > 0) {
                winning++;
                grossProfit = normalize(grossProfit.add(outcome));
            } else if (outcome.signum() < 0) {
                losing++;
                grossLoss = normalize(grossLoss.add(outcome.abs()));
            } else {
                flat++;
            }
            openQuantity = normalize(openQuantity.subtract(closedQuantity));
            openCashCost =
                    openQuantity.signum() == 0
                            ? BigDecimal.ZERO
                            : normalize(openCashCost.subtract(allocatedCost));
        }
        return new Result(winning, losing, flat, normalize(grossProfit), normalize(grossLoss));
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value.setScale(18, RoundingMode.HALF_UP);
    }

    /** 无原币资料且没有冻结成交价合同的旧记录，沿用既有费用与滑点分摊，避免补猜历史事实。 */
    private Result calculateLegacy(List<SimTrade> simTrades) {
        BigDecimal openQuantity = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        BigDecimal averageEntryPrice = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        BigDecimal openEntryCosts = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        int winning = 0;
        int losing = 0;
        int flat = 0;
        BigDecimal grossProfit = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        BigDecimal grossLoss = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);

        for (SimTrade simTrade : simTrades) {
            BigDecimal tradeCosts =
                    simTrade.feeAmount()
                            .add(simTrade.slippageAmount())
                            .setScale(18, RoundingMode.HALF_UP);
            if ("BUY".equalsIgnoreCase(simTrade.side())) {
                BigDecimal nextQuantity =
                        openQuantity.add(simTrade.quantity()).setScale(18, RoundingMode.HALF_UP);
                averageEntryPrice =
                        nextQuantity.compareTo(BigDecimal.ZERO) == 0
                                ? BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP)
                                : averageEntryPrice
                                        .multiply(openQuantity)
                                        .add(simTrade.tradePrice().multiply(simTrade.quantity()))
                                        .divide(nextQuantity, 18, RoundingMode.HALF_UP);
                openEntryCosts = openEntryCosts.add(tradeCosts).setScale(18, RoundingMode.HALF_UP);
                openQuantity = nextQuantity;
                continue;
            }

            if (openQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal quantityToClose =
                    simTrade.quantity().min(openQuantity).setScale(18, RoundingMode.HALF_UP);
            BigDecimal proportion = quantityToClose.divide(openQuantity, 18, RoundingMode.HALF_UP);
            BigDecimal allocatedEntryCost =
                    openEntryCosts.multiply(proportion).setScale(18, RoundingMode.HALF_UP);
            BigDecimal grossPnl =
                    simTrade.tradePrice()
                            .subtract(averageEntryPrice)
                            .multiply(quantityToClose)
                            .setScale(18, RoundingMode.HALF_UP);
            BigDecimal netOutcome =
                    grossPnl.subtract(allocatedEntryCost)
                            .subtract(tradeCosts)
                            .setScale(18, RoundingMode.HALF_UP);
            if (netOutcome.compareTo(BigDecimal.ZERO) > 0) {
                winning++;
                grossProfit = grossProfit.add(netOutcome).setScale(18, RoundingMode.HALF_UP);
            } else if (netOutcome.compareTo(BigDecimal.ZERO) < 0) {
                losing++;
                grossLoss = grossLoss.add(netOutcome.abs()).setScale(18, RoundingMode.HALF_UP);
            } else {
                flat++;
            }
            openQuantity =
                    openQuantity.subtract(quantityToClose).setScale(18, RoundingMode.HALF_UP);
            openEntryCosts =
                    openEntryCosts.subtract(allocatedEntryCost).setScale(18, RoundingMode.HALF_UP);
            if (openQuantity.compareTo(BigDecimal.ZERO) == 0) {
                averageEntryPrice = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
                openEntryCosts = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
            }
        }
        return new Result(winning, losing, flat, grossProfit, grossLoss);
    }

    public record Result(
            int winningTradeCount,
            int losingTradeCount,
            int flatTradeCount,
            BigDecimal grossProfit,
            BigDecimal grossLoss) {
        public BigDecimal winRate() {
            int total = winningTradeCount + losingTradeCount + flatTradeCount;
            if (total == 0) {
                return BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
            }
            return BigDecimal.valueOf(winningTradeCount)
                    .divide(BigDecimal.valueOf(total), 18, RoundingMode.HALF_UP);
        }

        /**
         * 计算盈亏比。
         * Why:
         * 策略版本需要评估报告直接展示 profit/loss ratio；亏损总额为 0 时返回 0，
         * 避免把无亏损样本误表达成无限大并影响前端数值展示。
         */
        public BigDecimal profitLossRatio() {
            if (grossLoss.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
            }
            return grossProfit.divide(grossLoss, 18, RoundingMode.HALF_UP);
        }
    }
}
