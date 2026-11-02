package com.guidinglight.nexusquant.account.domain.performance;

import com.guidinglight.nexusquant.common.numeric.NumericPolicy;
import com.guidinglight.nexusquant.common.numeric.NumericType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 现货多头按加权买入成本计算每笔卖出成交的净结果；部分平仓计一笔，未平仓不进入胜率分母。 */
public final class ClosedTradeStatistics {
    private ClosedTradeStatistics() {}

    /** 起点及以前的成交仅建立成本基础，结果统计(起点,终点]内的卖出，与两端权益之差对齐；已含滑点的成交价不重复扣除。 */
    public static Result calculate(
            List<Trade> input,
            String quoteCurrency,
            Instant from,
            Instant to,
            boolean completeBasis) {
        if (from == null || to == null || from.isAfter(to))
            throw new IllegalArgumentException("invalid outcome coverage");
        if (!completeBasis) return unavailable("OPENING_BASIS_UNAVAILABLE");
        if (input.size() > 10000) return unavailable("TRADE_WINDOW_LIMITED");
        Map<String, Position> positions = new HashMap<>();
        int wins = 0, losses = 0, flats = 0;
        BigDecimal profit = BigDecimal.ZERO, loss = BigDecimal.ZERO;
        for (var trade :
                input.stream()
                        .filter(t -> !t.at().isAfter(to))
                        .sorted(Comparator.comparing(Trade::at).thenComparing(Trade::tradeId))
                        .toList()) {
            if (trade.symbol() == null
                    || !trade.symbol().matches("[A-Z0-9]+-" + quoteCurrency)
                    || trade.quantity() == null
                    || trade.quantity().signum() <= 0
                    || trade.price() == null
                    || trade.price().signum() <= 0
                    || !("BUY".equals(trade.side()) || "SELL".equals(trade.side())))
                return unavailable("UNSUPPORTED_TRADE_BASIS");
            String baseCurrency = trade.symbol().substring(0, trade.symbol().indexOf('-'));
            boolean thirdAsset =
                    !quoteCurrency.equals(trade.feeCurrency())
                            && !baseCurrency.equals(trade.feeCurrency());
            if (trade.fee() == null
                    || (thirdAsset && trade.fee().signum() != 0 && trade.feeQuoteValue() == null))
                return unavailable("FEE_BASIS_UNAVAILABLE");
            BigDecimal baseFee =
                    baseCurrency.equals(trade.feeCurrency()) ? trade.fee() : BigDecimal.ZERO;
            BigDecimal quoteFee =
                    quoteCurrency.equals(trade.feeCurrency()) ? trade.fee() : BigDecimal.ZERO;
            // 第三币费用的原币余额由账务消费；这里只使用调用方已核验的成交时quote估值分摊成本，不改现金。
            BigDecimal thirdFee =
                    thirdAsset && trade.feeQuoteValue() != null
                            ? trade.feeQuoteValue()
                            : BigDecimal.ZERO;
            if (thirdFee.stripTrailingZeros().scale() > 8
                    || thirdFee.signum() != trade.fee().signum() && thirdFee.signum() != 0)
                return unavailable("FEE_BASIS_UNAVAILABLE");
            // 成交名义金额使用账务相同的AMOUNT精度，避免极小舍入差制造不存在的盈利。
            BigDecimal notional =
                    NumericPolicy.normalize(
                            NumericType.AMOUNT, trade.price().multiply(trade.quantity()));
            Position position = positions.computeIfAbsent(trade.symbol(), key -> new Position());
            if ("BUY".equals(trade.side())) {
                // base扣费/返佣改变实际收到数量，现金本金只消费quote费用；base费用估值不能再加一次成本。
                BigDecimal received = trade.quantity().subtract(baseFee);
                if (received.signum() <= 0) return unavailable("FEE_BASIS_UNAVAILABLE");
                position.cost = position.cost.add(notional).add(quoteFee).add(thirdFee);
                position.quantity = position.quantity.add(received);
                continue;
            }
            BigDecimal consumed = trade.quantity().add(baseFee);
            if (consumed.signum() <= 0) return unavailable("FEE_BASIS_UNAVAILABLE");
            // 不像研究的最小估算那样裁剪超卖量；缺少开仓成本时整段胜率保持未知。
            if (consumed.compareTo(position.quantity) > 0)
                return unavailable("OPENING_BASIS_UNAVAILABLE");
            BigDecimal allocated =
                    consumed.compareTo(position.quantity) == 0
                            ? position.cost
                            : position.cost
                                    .multiply(consumed)
                                    .divide(position.quantity, 18, RoundingMode.HALF_UP);
            BigDecimal outcome = notional.subtract(allocated).subtract(quoteFee).subtract(thirdFee);
            position.quantity = position.quantity.subtract(consumed);
            position.cost = position.cost.subtract(allocated);
            if (trade.at().isAfter(from)) {
                if (outcome.signum() > 0) {
                    wins++;
                    profit = profit.add(outcome);
                } else if (outcome.signum() < 0) {
                    losses++;
                    loss = loss.add(outcome.abs());
                } else flats++;
            }
        }
        int closed = wins + losses + flats;
        return new Result(
                closed == 0 ? "NO_CLOSED_TRADES" : "AVAILABLE",
                closed == 0
                        ? null
                        : BigDecimal.valueOf(wins)
                                .divide(BigDecimal.valueOf(closed), 18, RoundingMode.HALF_UP),
                wins,
                losses,
                flats,
                profit,
                loss,
                loss.signum() == 0 ? null : profit.divide(loss, 18, RoundingMode.HALF_UP),
                "WEIGHTED_ENTRY_COST_NET_SELL_FILL",
                from,
                to);
    }

    public static Result unavailable(String status) {
        return new Result(
                status,
                null,
                0,
                0,
                0,
                null,
                null,
                null,
                "WEIGHTED_ENTRY_COST_NET_SELL_FILL",
                null,
                null);
    }

    private static final class Position {
        private BigDecimal quantity = BigDecimal.ZERO, cost = BigDecimal.ZERO;
    }

    /** 输入只引用 canonical 成交；费用沿用正成本/负返佣，不产生任何新经济事实。 */
    public record Trade(
            String tradeId,
            String symbol,
            String side,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal fee,
            String feeCurrency,
            Instant at,
            BigDecimal feeQuoteValue) {
        /** 旧事实没有第三币换算来源时保持null，不能以当前报价补造过去的费用估值。 */
        public Trade(
                String tradeId,
                String symbol,
                String side,
                BigDecimal quantity,
                BigDecimal price,
                BigDecimal fee,
                String feeCurrency,
                Instant at) {
            this(tradeId, symbol, side, quantity, price, fee, feeCurrency, at, null);
        }
    }

    public record Result(
            String status,
            BigDecimal winRate,
            int winningCount,
            int losingCount,
            int flatCount,
            BigDecimal netProfit,
            BigDecimal netLoss,
            BigDecimal profitLossRatio,
            String method,
            Instant coverageStart,
            Instant coverageEnd) {}
}
