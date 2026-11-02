package com.guidinglight.nexusquant.research.api.dto;

import com.guidinglight.nexusquant.research.domain.backtest.SimPnlSnapshot;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/** 返回真实估值时点及原事件顺序；同一可见时点不能由响应层伪造不同时间或猜测旧序列。 */
@Schema(name = "SimPnlSnapshotResponse", description = "接口响应体")
public record SimPnlSnapshotResponse(
        @Schema(description = "simPnlSnapshotId") String simPnlSnapshotId,
        @Schema(description = "backtestRunId") String backtestRunId,
        @Schema(description = "snapshotTime") Instant snapshotTime,
        @Schema(description = "cashBalance") BigDecimal cashBalance,
        @Schema(description = "positionMarketValue") BigDecimal positionMarketValue,
        @Schema(description = "realizedPnl") BigDecimal realizedPnl,
        @Schema(description = "unrealizedPnl") BigDecimal unrealizedPnl,
        @Schema(description = "totalFee") BigDecimal totalFee,
        @Schema(description = "totalSlippage") BigDecimal totalSlippage,
        @Schema(description = "equity") BigDecimal equity,
        @Schema(description = "netPnl") BigDecimal netPnl,
        @Schema(description = "createdAt") Instant createdAt,
        @Schema(description = "优惠资产币种；旧快照为null") String discountAssetCurrency,
        @Schema(description = "本估值时点真实优惠币余额") BigDecimal discountAssetBalance,
        @Schema(description = "优惠币余额按本次可见价格的quote价值") BigDecimal discountAssetQuoteValue,
        @Schema(description = "优惠币quote单价") BigDecimal discountConversionPrice,
        @Schema(description = "估值价格观察时点") Instant discountPriceObservedAt,
        @Schema(description = "估值价格可见时点") Instant discountPriceVisibleAt,
        @Schema(description = "估值点的冻结来源摘要") String discountPriceSourceSha256,
        @Schema(description = "估值点的冻结来源资料") String discountPriceSourceJson,
        @Schema(description = "原估值顺序的精确十进制字符串；旧事实未记录时为null") String valuationSequence) {
    public static SimPnlSnapshotResponse from(SimPnlSnapshot simPnlSnapshot) {
        return new SimPnlSnapshotResponse(
                simPnlSnapshot.simPnlSnapshotId(),
                simPnlSnapshot.backtestRunId(),
                simPnlSnapshot.snapshotTime(),
                simPnlSnapshot.cashBalance(),
                simPnlSnapshot.positionMarketValue(),
                simPnlSnapshot.realizedPnl(),
                simPnlSnapshot.unrealizedPnl(),
                simPnlSnapshot.totalFee(),
                simPnlSnapshot.totalSlippage(),
                simPnlSnapshot.equity(),
                simPnlSnapshot.netPnl(),
                simPnlSnapshot.createdAt(),
                simPnlSnapshot.discountAssetCurrency(),
                simPnlSnapshot.discountAssetBalance(),
                simPnlSnapshot.discountAssetQuoteValue(),
                simPnlSnapshot.discountConversionPrice(),
                simPnlSnapshot.discountPriceObservedAt(),
                simPnlSnapshot.discountPriceVisibleAt(),
                simPnlSnapshot.discountPriceSourceSha256(),
                simPnlSnapshot.discountPriceSourceJson(),
                simPnlSnapshot.valuationSequence() == null
                        ? null
                        : simPnlSnapshot.valuationSequence().toString());
    }
}
