package com.guidinglight.nexusquant.strategy.domain;

import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.LiquidityRole;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.PaymentCondition;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Side;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 方向、支付资产及精度明确的现货费用模型；只计算本地模型，不声称预测实际交易所最终收费。 */
public final class SpotFeeModel {
    private final BigDecimal quoteRate;
    private final SpotCommissionTerms terms;
    private final Policy policy;
    private final Precision precision;

    private SpotFeeModel(
            BigDecimal quoteRate, SpotCommissionTerms terms, Policy policy, Precision precision) {
        this.quoteRate = quoteRate;
        this.terms = terms;
        this.policy = Objects.requireNonNull(policy);
        this.precision = Objects.requireNonNull(precision);
        if (terms == null) {
            rate(quoteRate);
            if (policy != Policy.QUOTE_NOTIONAL) throw invalid("FEE_PAYMENT_POLICY_INVALID");
        } else {
            if (quoteRate != null || policy == Policy.QUOTE_NOTIONAL)
                throw invalid("FEE_PAYMENT_POLICY_INVALID");
            // 原生分量可各自有效但相加越界；在采用前拒绝，不能等成交后才发现净持仓为负。
            for (Side side : Side.values())
                for (LiquidityRole role : LiquidityRole.values()) {
                    rate(terms.costRate(side, role, PaymentCondition.RECEIVED_ASSET));
                    if (policy == Policy.DISCOUNT_ASSET_IF_FUNDED)
                        rate(
                                terms.costRate(
                                        side, role, PaymentCondition.DISCOUNT_ASSET_SUFFICIENT));
                }
        }
    }

    /** 兼容既有单一报价币假设；带符号费率不预支返佣，收费资产始终为quote。 */
    public static SpotFeeModel quoteNotional(BigDecimal rate, Precision precision) {
        return new SpotFeeModel(rate, null, Policy.QUOTE_NOTIONAL, precision);
    }

    /** 原生观察保留全部分量；RECEIVED_ASSET明确采用不使用优惠资产的研究/SIM支付条件。 */
    public static SpotFeeModel commission(
            SpotCommissionTerms terms, Policy policy, Precision precision) {
        return new SpotFeeModel(null, Objects.requireNonNull(terms), policy, precision);
    }

    /** 仅返回该方向和流动性角色的费率；禁止称为账户的全局maker/taker费率。 */
    public BigDecimal rate(Side side, LiquidityRole role) {
        Objects.requireNonNull(side);
        Objects.requireNonNull(role);
        return terms == null
                ? quoteRate
                : terms.costRate(side, role, PaymentCondition.RECEIVED_ASSET);
    }

    /**
     * 费用数量在支付资产精度上舍入，quoteValue仅用于解释与估值，不能替代feeCurrency的真实扣款。
     * 优惠资金输入必须已扣除本笔本金与其他在途预留；本方法不会把待到账返佣增加到可用余额。
     */
    public Charge charge(
            String symbol,
            Side side,
            LiquidityRole role,
            BigDecimal quantity,
            BigDecimal price,
            DiscountFunding funding,
            Instant executionAt) {
        String[] currencies = currencies(symbol);
        Objects.requireNonNull(side);
        Objects.requireNonNull(role);
        Objects.requireNonNull(executionAt);
        positive(quantity);
        positive(price);
        if (terms != null && !terms.nativeSymbol().equals(currencies[0] + currencies[1]))
            throw invalid("FEE_MODEL_SYMBOL_MISMATCH");
        BigDecimal received = side == Side.BUY ? quantity : quantity.multiply(price);
        if (policy == Policy.DISCOUNT_ASSET_IF_FUNDED) {
            if (funding == null) throw invalid("FEE_DISCOUNT_FUNDING_REQUIRED");
            funding.validate(terms.discount().asset(), currencies[1], executionAt);
            BigDecimal feeQuote =
                    (side == Side.BUY ? received.multiply(price) : received)
                            .multiply(
                                    terms.costRate(
                                            side,
                                            role,
                                            PaymentCondition.DISCOUNT_ASSET_SUFFICIENT));
            BigDecimal fee =
                    feeQuote.divide(
                            funding.quotePerAsset(),
                            precision.discountAssetScale(),
                            RoundingMode.HALF_UP);
            if (fee.max(BigDecimal.ZERO).compareTo(funding.availableAfterReserves()) <= 0) {
                return new Charge(
                        funding.asset(),
                        fee,
                        roundedQuote(fee.multiply(funding.quotePerAsset())),
                        "DISCOUNT_ASSET_USED",
                        funding.sourceSha256());
            }
            // 足额失败明确回落到原生收到资产费率；不沿用优惠率假装以quote支付。
            return receivedCharge(
                    currencies,
                    side,
                    quantity,
                    price,
                    received,
                    role,
                    "DISCOUNT_ASSET_INSUFFICIENT_FALLBACK");
        }
        if (policy == Policy.QUOTE_NOTIONAL) {
            BigDecimal fee = roundedQuote(quantity.multiply(price).multiply(quoteRate));
            return new Charge(currencies[1], fee, fee, "QUOTE_NOTIONAL", null);
        }
        return receivedCharge(currencies, side, quantity, price, received, role, "RECEIVED_ASSET");
    }

    /** 本金只用现有现金支付；收到base扣费不需要额外quote，负费用也不会减少撮合前的本金预留。 */
    public BigDecimal quoteReserve(BigDecimal quantity, BigDecimal price) {
        positive(quantity);
        positive(price);
        BigDecimal notional = quantity.multiply(price);
        BigDecimal positiveFee =
                policy == Policy.QUOTE_NOTIONAL
                        ? notional.multiply(quoteRate.max(BigDecimal.ZERO))
                                .setScale(precision.quoteScale(), RoundingMode.CEILING)
                        : BigDecimal.ZERO;
        return notional.add(positiveFee);
    }

    private Charge receivedCharge(
            String[] currencies,
            Side side,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal received,
            LiquidityRole role,
            String paymentStatus) {
        BigDecimal rate = rate(side, role);
        boolean base = side == Side.BUY;
        BigDecimal fee =
                received.multiply(rate)
                        .setScale(
                                base ? precision.baseScale() : precision.quoteScale(),
                                RoundingMode.HALF_UP);
        if (fee.signum() > 0 && fee.compareTo(received) >= 0)
            throw invalid("FEE_EXCEEDS_RECEIVED_ASSET");
        return new Charge(
                currencies[base ? 0 : 1],
                fee,
                roundedQuote(base ? fee.multiply(price) : fee),
                paymentStatus,
                null);
    }

    private BigDecimal roundedQuote(BigDecimal value) {
        return value.setScale(precision.quoteScale(), RoundingMode.HALF_UP);
    }

    private static void rate(BigDecimal rate) {
        if (rate == null || rate.scale() > 24 || rate.abs().compareTo(BigDecimal.ONE) >= 0)
            throw invalid("FEE_MODEL_RATE_INVALID");
    }

    private static void positive(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw invalid("FEE_TRADE_VALUE_INVALID");
        // PG十八位价格与滑点相乘会携带三十六位尾零；校验有效数值精度，不把表示长度当作新的小数信息。
        BigDecimal canonical = value.stripTrailingZeros();
        if (canonical.scale() > 18
                || canonical.precision() > 38
                || (long) canonical.precision() - canonical.scale() > 38)
            throw invalid("FEE_TRADE_VALUE_INVALID");
    }

    private static String[] currencies(String symbol) {
        if (symbol == null || !symbol.matches("[A-Z0-9]{1,20}-[A-Z0-9]{1,20}"))
            throw invalid("FEE_MODEL_SYMBOL_INVALID");
        String[] currencies = symbol.split("-");
        if (currencies[0].equals(currencies[1])) throw invalid("FEE_MODEL_SYMBOL_INVALID");
        return currencies;
    }

    private static IllegalArgumentException invalid(String code) {
        return new IllegalArgumentException(code);
    }

    public Policy policy() {
        return policy;
    }

    public Precision precision() {
        return precision;
    }

    public enum Policy {
        QUOTE_NOTIONAL,
        RECEIVED_ASSET,
        DISCOUNT_ASSET_IF_FUNDED
    }

    /** 研究精度和canonical精度显式区分；调用方应冻结实际使用的精度，不能在重启时取新默认值。 */
    public record Precision(int baseScale, int quoteScale, int discountAssetScale) {
        public Precision {
            if (baseScale < 0
                    || baseScale > 18
                    || quoteScale < 0
                    || quoteScale > 18
                    || discountAssetScale < 0
                    || discountAssetScale > 18) throw invalid("FEE_PRECISION_INVALID");
        }

        public static Precision research() {
            return new Precision(18, 18, 18);
        }

        public static Precision canonical() {
            return new Precision(8, 8, 8);
        }
    }

    /** 费用原币数量和quote估值分开保存；signed debit只在成交后消费，负值代表返佣入账。 */
    public record Charge(
            String currency,
            BigDecimal amount,
            BigDecimal quoteValue,
            String paymentStatus,
            String fundingSourceSha256) {
        public BigDecimal debit(String currency) {
            return this.currency.equals(currency) ? amount : BigDecimal.ZERO;
        }

        public BigDecimal positiveReserve() {
            return amount.max(BigDecimal.ZERO);
        }
    }

    /**
     * 显式的优惠资产预算与换算依据；只是消费端提供的已验证输入，不因带摘要就变成真实供应方事实。
     * 币种、观察时间和最大陈旧期由冻结执行合同给出，未来价格、未知余额和过期价格均拒绝。
     */
    public record DiscountFunding(
            String asset,
            String quoteCurrency,
            BigDecimal quotePerAsset,
            BigDecimal availableAfterReserves,
            Instant priceObservedAt,
            Duration maxPriceAge,
            String sourceSha256) {
        public DiscountFunding {
            if (asset == null
                    || !asset.matches("[A-Z0-9]{1,20}")
                    || quoteCurrency == null
                    || !quoteCurrency.matches("[A-Z0-9]{1,20}"))
                throw invalid("FEE_DISCOUNT_FUNDING_INVALID");
            positive(quotePerAsset);
            if (availableAfterReserves == null
                    || availableAfterReserves.signum() < 0
                    || availableAfterReserves.scale() > 18
                    || availableAfterReserves.precision() > 38
                    || priceObservedAt == null
                    || maxPriceAge == null
                    || maxPriceAge.isZero()
                    || maxPriceAge.isNegative()
                    || maxPriceAge.compareTo(Duration.ofMinutes(5)) > 0
                    || sourceSha256 == null
                    || !sourceSha256.matches("[a-f0-9]{64}"))
                throw invalid("FEE_DISCOUNT_FUNDING_INVALID");
        }

        private void validate(String asset, String quote, Instant executionAt) {
            if (!this.asset.equals(asset) || !quoteCurrency.equals(quote))
                throw invalid("FEE_DISCOUNT_FUNDING_SCOPE_MISMATCH");
            if (priceObservedAt.isAfter(executionAt)
                    || priceObservedAt.isBefore(executionAt.minus(maxPriceAge)))
                throw invalid("FEE_DISCOUNT_PRICE_UNAVAILABLE");
            if (asset.equals(quote) && quotePerAsset.compareTo(BigDecimal.ONE) != 0)
                throw invalid("FEE_DISCOUNT_IDENTITY_PRICE_INVALID");
        }
    }
}
