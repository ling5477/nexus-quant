package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * TradeExecuted 表示 canonical 成交事实，沿用 paper 与交易所共同事件类型。
 *
 * @param tradeId 成交 ID
 * @param orderId 系统订单 ID
 * @param clientOrderId 客户端幂等键
 * @param accountId 账户 ID
 * @param symbol 交易对
 * @param venue 交易场所
 * @param exchange 成交来源，例如 PAPER 或 OKX
 * @param externalOrderId 外部订单号，可空
 * @param exchangeTradeId 外部成交号，paper 可空
 * @param price 成交价格
 * @param qty 成交数量
 * @param fee 手续费
 * @param feeCurrency 手续费币种
 * @param ts 成交时间
 * @param tradeEnv 持久化父订单与成交共同确认的 SIM/LIVE；旧事件可缺省，不从 profile 推导
 */
public record TradeExecuted(
        @JsonProperty("trade_id") String tradeId,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("client_order_id") String clientOrderId,
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("venue") String venue,
        @JsonProperty("exchange") String exchange,
        @JsonProperty("external_order_id") String externalOrderId,
        @JsonProperty("exchange_trade_id") String exchangeTradeId,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("qty") BigDecimal qty,
        @JsonProperty("fee") BigDecimal fee,
        @JsonProperty("fee_currency") String feeCurrency,
        @JsonProperty("ts") Instant ts,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        @JsonProperty("trade_env") String tradeEnv
) {
    /** 保持已有 producer 的事件兼容性；普通 OKX 必需事件由 durable source 明确提供环境。 */
    public TradeExecuted(String tradeId, String orderId, String clientOrderId, Long accountId,
                         String symbol, String venue, String exchange, String externalOrderId,
                         String exchangeTradeId, BigDecimal price, BigDecimal qty, BigDecimal fee,
                         String feeCurrency, Instant ts) {
        this(tradeId, orderId, clientOrderId, accountId, symbol, venue, exchange, externalOrderId,
                exchangeTradeId, price, qty, fee, feeCurrency, ts, null);
    }
}
