package com.guidinglight.nexusquant.adapter.binance.fee;

import java.math.BigDecimal;
import java.util.Objects;

/** 保留供应方四个方向/流动性原始费率，不把buyer/seller并入另一个字段或用绝对值消除符号。 */
public record BinanceCommissionRates(
        BigDecimal maker, BigDecimal taker, BigDecimal buyer, BigDecimal seller) {
    public BinanceCommissionRates {
        for (var rate : new BigDecimal[] {maker, taker, buyer, seller}) {
            Objects.requireNonNull(rate);
            if (rate.scale() > 12 || rate.abs().compareTo(BigDecimal.ONE) > 0) {
                throw new BinanceFeeReadException(BinanceFeeReadException.Reason.INVALID_RATE);
            }
        }
    }

    /** 每类费用按流动性角色加买卖方向；返回精确十进制，不提前舍入交易金额。 */
    public BigDecimal rate(
            BinanceCommissionSnapshot.Side side, BinanceCommissionSnapshot.LiquidityRole role) {
        Objects.requireNonNull(side);
        Objects.requireNonNull(role);
        return (role == BinanceCommissionSnapshot.LiquidityRole.MAKER ? maker : taker)
                .add(side == BinanceCommissionSnapshot.Side.BUY ? buyer : seller);
    }
}
