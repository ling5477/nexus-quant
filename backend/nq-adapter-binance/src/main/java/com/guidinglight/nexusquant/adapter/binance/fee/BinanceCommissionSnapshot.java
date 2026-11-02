package com.guidinglight.nexusquant.adapter.binance.fee;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** 当前账户现货费用配置：完整保留三类费率与优惠条件；不是历史费率、VIP等级或已发生成交费用。 */
public record BinanceCommissionSnapshot(
        String symbol,
        BinanceCommissionRates standard,
        BinanceCommissionRates tax,
        BinanceCommissionRates special,
        BinanceCommissionDiscount discount,
        Instant providerResponseAt,
        Instant observedAt,
        String responseSha256) {
    public BinanceCommissionSnapshot {
        Objects.requireNonNull(standard);
        Objects.requireNonNull(tax);
        Objects.requireNonNull(special);
        Objects.requireNonNull(discount);
        Objects.requireNonNull(observedAt);
        if (!validNativeSymbol(symbol)
                || responseSha256 == null
                || !responseSha256.matches("[a-f0-9]{64}")) {
            throw new BinanceFeeReadException(BinanceFeeReadException.Reason.INVALID_SOURCE);
        }
        observedAt = observedAt.truncatedTo(ChronoUnit.MICROS);
        if (providerResponseAt != null)
            providerResponseAt = providerResponseAt.truncatedTo(ChronoUnit.MICROS);
    }

    /** HTTP Date只表示供应方响应时间；缺失保持null，不能拿客户端时钟代填费率变更时间。 */
    public String providerTimeBasis() {
        return providerResponseAt == null ? "NOT_PROVIDED" : "HTTP_RESPONSE_DATE";
    }

    /** 原生协议允许Unicode字母及数字；只接受有界标的字符，NQ业务层仍独立限制自己的支持目录。 */
    public static boolean validNativeSymbol(String symbol) {
        return symbol != null && symbol.matches("[\\p{L}\\p{N}]{2,40}");
    }

    /**
     * 以明确支付条件计算研究模型比例，供应方正值表示成本。
     * 优惠仅作用于standard，tax/special不优惠；必须由调用者明确选择足额优惠资产假设，不能仅按开关自动选用。
     */
    public BigDecimal costRate(Side side, LiquidityRole role, PaymentCondition condition) {
        Objects.requireNonNull(condition);
        var normal = standard.rate(side, role);
        if (condition == PaymentCondition.DISCOUNT_ASSET_SUFFICIENT) {
            if (!discount.enabled())
                throw new BinanceFeeReadException(
                        BinanceFeeReadException.Reason.DISCOUNT_NOT_AVAILABLE);
            normal = normal.multiply(discount.multiplier());
        }
        return normal.add(tax.rate(side, role)).add(special.rate(side, role));
    }

    public enum Side {
        BUY,
        SELL
    }

    public enum LiquidityRole {
        MAKER,
        TAKER
    }

    public enum PaymentCondition {
        RECEIVED_ASSET,
        DISCOUNT_ASSET_SUFFICIENT
    }

    @Override
    public String toString() {
        return "BinanceCommissionSnapshot[REDACTED]";
    }
}
