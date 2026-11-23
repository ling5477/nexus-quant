package com.guidinglight.nexusquant.research.domain.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * SimPnlSnapshot 表示回测运行中的最小权益与 PnL 快照。
 */
public record SimPnlSnapshot(
        String simPnlSnapshotId,
        String backtestRunId,
        Instant snapshotTime,
        BigDecimal cashBalance,
        BigDecimal positionMarketValue,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal totalFee,
        BigDecimal totalSlippage,
        BigDecimal equity,
        BigDecimal netPnl,
        Instant createdAt,
        String discountAssetCurrency,
        BigDecimal discountAssetBalance,
        BigDecimal discountAssetQuoteValue,
        BigDecimal discountConversionPrice,
        Instant discountPriceObservedAt,
        Instant discountPriceVisibleAt,
        String discountPriceSourceSha256,
        String discountPriceSourceJson,
        Long valuationSequence) {
    /** 旧单币快照不补第三币资料；新增分项仅说明真实参与权益的余额和当时可见估值。 */
    public SimPnlSnapshot(
            String simPnlSnapshotId,
            String backtestRunId,
            Instant snapshotTime,
            BigDecimal cashBalance,
            BigDecimal positionMarketValue,
            BigDecimal realizedPnl,
            BigDecimal unrealizedPnl,
            BigDecimal totalFee,
            BigDecimal totalSlippage,
            BigDecimal equity,
            BigDecimal netPnl,
            Instant createdAt) {
        this(
                simPnlSnapshotId,
                backtestRunId,
                snapshotTime,
                cashBalance,
                positionMarketValue,
                realizedPnl,
                unrealizedPnl,
                totalFee,
                totalSlippage,
                equity,
                netPnl,
                createdAt,
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

    /** 旧完整费用/估值合同没有顺序证据，兼容构造明确保留null。 */
    public SimPnlSnapshot(
            String simPnlSnapshotId,
            String backtestRunId,
            Instant snapshotTime,
            BigDecimal cashBalance,
            BigDecimal positionMarketValue,
            BigDecimal realizedPnl,
            BigDecimal unrealizedPnl,
            BigDecimal totalFee,
            BigDecimal totalSlippage,
            BigDecimal equity,
            BigDecimal netPnl,
            Instant createdAt,
            String discountAssetCurrency,
            BigDecimal discountAssetBalance,
            BigDecimal discountAssetQuoteValue,
            BigDecimal discountConversionPrice,
            Instant discountPriceObservedAt,
            Instant discountPriceVisibleAt,
            String discountPriceSourceSha256,
            String discountPriceSourceJson) {
        this(
                simPnlSnapshotId,
                backtestRunId,
                snapshotTime,
                cashBalance,
                positionMarketValue,
                realizedPnl,
                unrealizedPnl,
                totalFee,
                totalSlippage,
                equity,
                netPnl,
                createdAt,
                discountAssetCurrency,
                discountAssetBalance,
                discountAssetQuoteValue,
                discountConversionPrice,
                discountPriceObservedAt,
                discountPriceVisibleAt,
                discountPriceSourceSha256,
                discountPriceSourceJson,
                null);
    }

    /** 仅执行器按真实事件追加次序赋值；不能从随机ID或买卖方向推导。 */
    public SimPnlSnapshot withValuationSequence(long sequence) {
        return new SimPnlSnapshot(
                simPnlSnapshotId,
                backtestRunId,
                snapshotTime,
                cashBalance,
                positionMarketValue,
                realizedPnl,
                unrealizedPnl,
                totalFee,
                totalSlippage,
                equity,
                netPnl,
                createdAt,
                discountAssetCurrency,
                discountAssetBalance,
                discountAssetQuoteValue,
                discountConversionPrice,
                discountPriceObservedAt,
                discountPriceVisibleAt,
                discountPriceSourceSha256,
                discountPriceSourceJson,
                sequence);
    }

    public SimPnlSnapshot {
        if (valuationSequence != null && valuationSequence <= 0)
            throw new IllegalArgumentException("RESEARCH_EVENT_SEQUENCE_INVALID");
        boolean absent =
                discountAssetCurrency == null
                        && discountAssetBalance == null
                        && discountAssetQuoteValue == null
                        && discountConversionPrice == null
                        && discountPriceObservedAt == null
                        && discountPriceVisibleAt == null
                        && discountPriceSourceSha256 == null
                        && discountPriceSourceJson == null;
        if (!absent
                && (discountAssetCurrency == null
                        || !discountAssetCurrency.matches("[A-Z0-9]{1,20}")
                        || discountAssetBalance == null
                        || discountAssetBalance.signum() < 0
                        || discountAssetQuoteValue == null
                        || discountConversionPrice == null
                        || discountConversionPrice.signum() <= 0
                        || discountPriceObservedAt == null
                        || discountPriceVisibleAt == null
                        || snapshotTime == null
                        || discountPriceObservedAt.isAfter(discountPriceVisibleAt)
                        || discountPriceVisibleAt.isAfter(snapshotTime)
                        || discountPriceObservedAt.isBefore(snapshotTime.minusSeconds(300))
                        || discountPriceSourceSha256 == null
                        || !discountPriceSourceSha256.matches("[a-f0-9]{64}")
                        || discountPriceSourceJson == null
                        || discountPriceSourceJson.isBlank()
                        || !DiscountConversionFacts.matches(
                                discountAssetCurrency,
                                "USDT",
                                discountConversionPrice,
                                discountPriceObservedAt,
                                discountPriceVisibleAt,
                                discountPriceSourceSha256,
                                discountPriceSourceJson)
                        || discountAssetBalance
                                        .multiply(discountConversionPrice)
                                        .setScale(8, RoundingMode.HALF_UP)
                                        .compareTo(discountAssetQuoteValue)
                                != 0
                        || cashBalance == null
                        || positionMarketValue == null
                        || equity == null
                        || cashBalance
                                        .add(positionMarketValue)
                                        .add(discountAssetQuoteValue)
                                        .setScale(18, RoundingMode.HALF_UP)
                                        .compareTo(equity)
                                != 0))
            throw new IllegalArgumentException("SIM_PNL_DISCOUNT_ASSET_INVALID");
    }
}
