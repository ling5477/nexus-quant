package com.guidinglight.nexusquant.ledger.contracts.model;

import com.guidinglight.nexusquant.common.numeric.NumericPolicy;
import com.guidinglight.nexusquant.common.numeric.NumericType;
import com.guidinglight.nexusquant.contracts.model.OrderSide;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * PositionProjection 表示 positions 投影快照。
 */
public record PositionProjection(
        Long accountId,
        String symbol,
        BigDecimal qty,
        BigDecimal availableQty,
        BigDecimal avgPrice,
        String traceId
) {
    /** 正式入账与投影重放共用同一笔成交的持仓转移和舍入规则。 */
    public PositionProjection afterTrade(TradeLedgerRequest request, String baseCurrency, BigDecimal signedFee) {
        PositionProjection current = this;
        BigDecimal qtyChange =
                request.side() == OrderSide.BUY ? request.qty() : request.qty().negate();
        if (signedFee.signum() != 0
                && request.feeCurrency() != null
                && request.feeCurrency().equalsIgnoreCase(baseCurrency)) {
            // base币种收费减持仓、返佣加持仓；与分录使用同一舍入值，避免精度差造成资产不守恒。
            qtyChange = qtyChange.subtract(signedFee);
        }
        BigDecimal nextQty = NumericPolicy.normalize(NumericType.QTY, current.qty().add(qtyChange));
        BigDecimal nextAvgPrice = calculateNextAvgPrice(current, request, nextQty);
        return new PositionProjection(
                request.accountId(), request.symbol(), nextQty, nextQty, nextAvgPrice, request.traceId());
    }

    private static BigDecimal calculateNextAvgPrice(
            PositionProjection current, TradeLedgerRequest request, BigDecimal nextQty) {
        if (request.side() == OrderSide.SELL) {
            if (nextQty.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO.setScale(8);
            }
            return NumericPolicy.normalize(NumericType.PRICE, current.avgPrice());
        }
        BigDecimal currentQty = current.qty();
        if (currentQty.compareTo(BigDecimal.ZERO) <= 0) {
            // 首次买入也必须按扣费或返佣后的实际base数量分摊本金，否则返佣会虚增持仓成本。
            if (nextQty.signum() <= 0) return BigDecimal.ZERO.setScale(8);
            return NumericPolicy.normalize(
                    NumericType.PRICE,
                    request.price()
                            .multiply(request.qty())
                            .divide(nextQty, 8, RoundingMode.HALF_UP));
        }
        BigDecimal weightedAmount =
                current.avgPrice()
                        .multiply(currentQty)
                        .add(request.price().multiply(request.qty()));
        return NumericPolicy.normalize(
                NumericType.PRICE, weightedAmount.divide(nextQty, 8, RoundingMode.HALF_UP));
    }
}
