package com.guidinglight.nexusquant.adapter.okx.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * OkxFillRecord 表示从 OKX `trade/fills` 拉取的一笔成交事实。
 * <p>
 * Why:
 * scheduler 在 REST reconcile 阶段需要稳定消费 fills 并映射到 trades/ledger，
 * 单独建模可以把 OKX 字段解析与后续业务编排解耦。
 *
 * @param exchangeTradeId OKX 成交 ID，用于 trades 去重
 * @param externalOrderId OKX ordId
 * @param symbol 交易对
 * @param side 买卖方向
 * @param price 成交价
 * @param qty 成交量
 * @param fee 手续费
 * @param feeCurrency 手续费币种
 * @param ts 成交时间
 */
public record OkxFillRecord(
        String exchangeTradeId,
        String externalOrderId,
        String symbol,
        String side,
        BigDecimal price,
        BigDecimal qty,
        BigDecimal fee,
        String feeCurrency,
        Instant ts
) {
}
