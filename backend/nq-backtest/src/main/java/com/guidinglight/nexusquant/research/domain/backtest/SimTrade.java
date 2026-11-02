package com.guidinglight.nexusquant.research.domain.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * SimTrade 表示研究与回测契约的模拟成交事实。
 */
public record SimTrade(
        String simTradeId,
        String simOrderId,
        String backtestRunId,
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal tradePrice,
        BigDecimal feeAmount,
        BigDecimal slippageAmount,
        Instant tradedAt,
        Instant createdAt,
        Instant updatedAt,
        String feeCurrency,
        BigDecimal feeNativeAmount,
        String feePaymentStatus,
        Integer feeValuationScale,
        BigDecimal feeConversionPrice,
        Instant feePriceObservedAt,
        Instant feePriceVisibleAt,
        String feePriceSourceSha256,
        String feePriceSourceJson,
        Long executionSequence) {
    /** 旧成交保留原quote费用合同；原币资料缺失使用null，不能据此补造过去的支付资产。 */
    public SimTrade(
            String simTradeId,
            String simOrderId,
            String backtestRunId,
            String symbol,
            String side,
            BigDecimal quantity,
            BigDecimal tradePrice,
            BigDecimal feeAmount,
            BigDecimal slippageAmount,
            Instant tradedAt,
            Instant createdAt,
            Instant updatedAt) {
        this(
                simTradeId,
                simOrderId,
                backtestRunId,
                symbol,
                side,
                quantity,
                tradePrice,
                feeAmount,
                slippageAmount,
                tradedAt,
                createdAt,
                updatedAt,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** 既有收到资产成交没有第三资产换算资料，保留原构造合同而不补造历史来源。 */
    public SimTrade(
            String simTradeId,
            String simOrderId,
            String backtestRunId,
            String symbol,
            String side,
            BigDecimal quantity,
            BigDecimal tradePrice,
            BigDecimal feeAmount,
            BigDecimal slippageAmount,
            Instant tradedAt,
            Instant createdAt,
            Instant updatedAt,
            String feeCurrency,
            BigDecimal feeNativeAmount,
            String feePaymentStatus,
            Integer feeValuationScale) {
        this(
                simTradeId,
                simOrderId,
                backtestRunId,
                symbol,
                side,
                quantity,
                tradePrice,
                feeAmount,
                slippageAmount,
                tradedAt,
                createdAt,
                updatedAt,
                feeCurrency,
                feeNativeAmount,
                feePaymentStatus,
                feeValuationScale,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** 新原币字段完整成组；feeAmount继续是quote估值，base收费与返佣只改变净持仓，不重复扣现金。 */

    /** 旧完整费用/估值合同没有顺序证据，兼容构造明确保留null。 */
    public SimTrade(
            String simTradeId,
            String simOrderId,
            String backtestRunId,
            String symbol,
            String side,
            BigDecimal quantity,
            BigDecimal tradePrice,
            BigDecimal feeAmount,
            BigDecimal slippageAmount,
            Instant tradedAt,
            Instant createdAt,
            Instant updatedAt,
            String feeCurrency,
            BigDecimal feeNativeAmount,
            String feePaymentStatus,
            Integer feeValuationScale,
            BigDecimal feeConversionPrice,
            Instant feePriceObservedAt,
            Instant feePriceVisibleAt,
            String feePriceSourceSha256,
            String feePriceSourceJson) {
        this(
                simTradeId,
                simOrderId,
                backtestRunId,
                symbol,
                side,
                quantity,
                tradePrice,
                feeAmount,
                slippageAmount,
                tradedAt,
                createdAt,
                updatedAt,
                feeCurrency,
                feeNativeAmount,
                feePaymentStatus,
                feeValuationScale,
                feeConversionPrice,
                feePriceObservedAt,
                feePriceVisibleAt,
                feePriceSourceSha256,
                feePriceSourceJson,
                null);
    }

    /** 仅执行器按真实事件追加次序赋值；不能从随机ID或买卖方向推导。 */
    public SimTrade withExecutionSequence(long sequence) {
        return new SimTrade(
                simTradeId,
                simOrderId,
                backtestRunId,
                symbol,
                side,
                quantity,
                tradePrice,
                feeAmount,
                slippageAmount,
                tradedAt,
                createdAt,
                updatedAt,
                feeCurrency,
                feeNativeAmount,
                feePaymentStatus,
                feeValuationScale,
                feeConversionPrice,
                feePriceObservedAt,
                feePriceVisibleAt,
                feePriceSourceSha256,
                feePriceSourceJson,
                sequence);
    }

    public SimTrade {
        if (executionSequence != null && executionSequence <= 0)
            throw new IllegalArgumentException("RESEARCH_EVENT_SEQUENCE_INVALID");
        boolean legacy =
                feeCurrency == null
                        && feeNativeAmount == null
                        && feePaymentStatus == null
                        && feeValuationScale == null;
        boolean conversionAbsent =
                feeConversionPrice == null
                        && feePriceObservedAt == null
                        && feePriceVisibleAt == null
                        && feePriceSourceSha256 == null
                        && feePriceSourceJson == null;
        if (legacy && !conversionAbsent)
            throw new IllegalArgumentException("SIM_TRADE_NATIVE_FEE_INVALID");
        if (!legacy) {
            if (feeCurrency == null
                    || feeNativeAmount == null
                    || feePaymentStatus == null
                    || feeValuationScale == null
                    || symbol == null
                    || !symbol.matches("[A-Z0-9]{1,20}-[A-Z0-9]{1,20}")
                    || !("BUY".equals(side) || "SELL".equals(side))
                    || quantity == null
                    || quantity.signum() <= 0
                    || tradePrice == null
                    || tradePrice.signum() <= 0
                    || feeAmount == null
                    || !("RECEIVED_ASSET".equals(feePaymentStatus)
                            || "DISCOUNT_ASSET_INSUFFICIENT_FALLBACK".equals(feePaymentStatus)
                            || "DISCOUNT_ASSET_USED".equals(feePaymentStatus))
                    || feeValuationScale < 0
                    || feeValuationScale > 18
                    || feeNativeAmount.scale() > 18)
                throw new IllegalArgumentException("SIM_TRADE_NATIVE_FEE_INVALID");
            String base = symbol.substring(0, symbol.indexOf('-'));
            String quote = symbol.substring(symbol.indexOf('-') + 1);
            if ("DISCOUNT_ASSET_USED".equals(feePaymentStatus)) {
                // 原币返佣也须冻结同一时点的估值资料，禁止用下一次价格或主交易对价格解释第三币。
                if (feeCurrency.equals(base)
                        || feeCurrency.equals(quote)
                        || !feeCurrency.matches("[A-Z0-9]{1,20}")
                        || feeConversionPrice == null
                        || feeConversionPrice.signum() <= 0
                        || feeConversionPrice.scale() > 18
                        || feePriceObservedAt == null
                        || feePriceVisibleAt == null
                        || tradedAt == null
                        || feePriceObservedAt.isAfter(feePriceVisibleAt)
                        || feePriceVisibleAt.isAfter(tradedAt)
                        || feePriceObservedAt.isBefore(tradedAt.minusSeconds(300))
                        || feePriceSourceSha256 == null
                        || !feePriceSourceSha256.matches("[a-f0-9]{64}")
                        || feePriceSourceJson == null
                        || feePriceSourceJson.isBlank()
                        || !DiscountConversionFacts.matches(
                                feeCurrency,
                                quote,
                                feeConversionPrice,
                                feePriceObservedAt,
                                feePriceVisibleAt,
                                feePriceSourceSha256,
                                feePriceSourceJson)
                        || feeNativeAmount
                                        .multiply(feeConversionPrice)
                                        .setScale(feeValuationScale, RoundingMode.HALF_UP)
                                        .compareTo(feeAmount)
                                != 0)
                    throw new IllegalArgumentException("SIM_TRADE_DISCOUNT_CONVERSION_INVALID");
            } else {
                String expected = "BUY".equals(side) ? base : quote;
                BigDecimal received = "BUY".equals(side) ? quantity : quantity.multiply(tradePrice);
                BigDecimal quoteValue =
                        "BUY".equals(side) ? feeNativeAmount.multiply(tradePrice) : feeNativeAmount;
                if (!conversionAbsent
                        || !expected.equals(feeCurrency)
                        || feeNativeAmount.compareTo(received) >= 0
                        || quoteValue
                                        .setScale(feeValuationScale, RoundingMode.HALF_UP)
                                        .compareTo(feeAmount)
                                != 0)
                    throw new IllegalArgumentException("SIM_TRADE_NATIVE_FEE_INVALID");
            }
        }
    }

    /** 现金扣款只消费quote原币金额；旧记录沿用原字段，不将base费用的估值当成第二次扣款。 */
    public BigDecimal cashFeeAmount() {
        return feeCurrency == null
                ? feeAmount
                : feeCurrency.equals(symbol.substring(symbol.indexOf('-') + 1))
                        ? feeNativeAmount
                        : BigDecimal.ZERO;
    }

    /** 第三资产费用是闭仓经济成本，但不是quote现金扣款；base费用已经进入净持仓成本。 */
    public BigDecimal outcomeFeeAmount() {
        return "DISCOUNT_ASSET_USED".equals(feePaymentStatus) ? feeAmount : cashFeeAmount();
    }

    /** 成交后净base增量；负base费用是收到的返佣，不在成交前提供卖出额度。 */
    public BigDecimal settledBaseDelta() {
        BigDecimal delta = "BUY".equals(side) ? quantity : quantity.negate();
        return feeCurrency != null && feeCurrency.equals(symbol.substring(0, symbol.indexOf('-')))
                ? delta.subtract(feeNativeAmount)
                : delta;
    }
}
