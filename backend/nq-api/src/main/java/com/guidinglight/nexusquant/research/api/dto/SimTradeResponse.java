package com.guidinglight.nexusquant.research.api.dto;

import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/** 返回正式研究成交的原币费用与原执行顺序；旧事实缺少顺序时保持空值，不由响应层补造。 */
@Schema(name = "SimTradeResponse", description = "接口响应体")
public record SimTradeResponse(
        @Schema(description = "simTradeId") String simTradeId,
        @Schema(description = "simOrderId") String simOrderId,
        @Schema(description = "backtestRunId") String backtestRunId,
        @Schema(description = "symbol") String symbol,
        @Schema(description = "side") String side,
        @Schema(description = "quantity") BigDecimal quantity,
        @Schema(description = "tradePrice") BigDecimal tradePrice,
        @Schema(description = "quote币种费用估值；实际模型支付资产和数量见原币字段") BigDecimal feeAmount,
        @Schema(description = "slippageAmount") BigDecimal slippageAmount,
        @Schema(description = "tradedAt") Instant tradedAt,
        @Schema(description = "createdAt") Instant createdAt,
        @Schema(description = "updatedAt") Instant updatedAt,
        @Schema(description = "模型支付资产；旧记录未提供时为null") String feeCurrency,
        @Schema(description = "原币signed费用数量，不再重复扣quote估值") BigDecimal feeNativeAmount,
        @Schema(description = "收到资产条件或优惠不足后的未优惠回落") String feePaymentStatus,
        @Schema(description = "费用折算quote的HALF_UP精度") Integer feeValuationScale,
        @Schema(description = "本次第三币费用的quote单价") BigDecimal feeConversionPrice,
        @Schema(description = "换算价格原始观察时点") Instant feePriceObservedAt,
        @Schema(description = "换算价格首次可见时点") Instant feePriceVisibleAt,
        @Schema(description = "本次换算点的冻结来源摘要") String feePriceSourceSha256,
        @Schema(description = "本次换算点的冻结来源资料") String feePriceSourceJson,
        @Schema(description = "原执行顺序的精确十进制字符串；旧事实未记录时为null") String executionSequence) {
    public static SimTradeResponse from(SimTrade simTrade) {
        return new SimTradeResponse(
                simTrade.simTradeId(),
                simTrade.simOrderId(),
                simTrade.backtestRunId(),
                simTrade.symbol(),
                simTrade.side(),
                simTrade.quantity(),
                simTrade.tradePrice(),
                simTrade.feeAmount(),
                simTrade.slippageAmount(),
                simTrade.tradedAt(),
                simTrade.createdAt(),
                simTrade.updatedAt(),
                simTrade.feeCurrency(),
                simTrade.feeNativeAmount(),
                simTrade.feePaymentStatus(),
                simTrade.feeValuationScale(),
                simTrade.feeConversionPrice(),
                simTrade.feePriceObservedAt(),
                simTrade.feePriceVisibleAt(),
                simTrade.feePriceSourceSha256(),
                simTrade.feePriceSourceJson(),
                simTrade.executionSequence() == null
                        ? null
                        : simTrade.executionSequence().toString());
    }
}
