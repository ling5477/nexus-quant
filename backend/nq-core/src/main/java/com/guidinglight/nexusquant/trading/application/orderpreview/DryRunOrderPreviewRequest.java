package com.guidinglight.nexusquant.trading.application.orderpreview;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DryRunOrderPreviewRequest 是 internal LIMIT preview 的输入合同。
 *
 * <p>请求不包含 account、credential、provider endpoint 或 raw response。所有数值使用 BigDecimal，
 * 服务不会静默舍入或把输入转换成执行参数。</p>
 *
 * @param exchange            交易所，当前只允许 OKX
 * @param symbol              exchange symbol，由 server-side instrument catalog 解析
 * @param side                BUY 或 SELL
 * @param orderType           当前只允许 LIMIT
 * @param requestedQuantity   原始请求数量
 * @param requestedLimitPrice 原始请求限价
 * @param evaluationTime      冻结的 deterministic 评估时间
 * @param traceId             仅用于调用链关联，不得包含 credential
 */
public record DryRunOrderPreviewRequest(
        String exchange,
        String symbol,
        Side side,
        OrderType orderType,
        BigDecimal requestedQuantity,
        BigDecimal requestedLimitPrice,
        Instant evaluationTime,
        String traceId
) {

    public enum Side {
        BUY,
        SELL
    }

    public enum OrderType {
        LIMIT,
        MARKET,
        STOP,
        TRIGGER,
        ICEBERG,
        TWAP,
        POST_ONLY,
        IOC,
        FOK
    }
}
