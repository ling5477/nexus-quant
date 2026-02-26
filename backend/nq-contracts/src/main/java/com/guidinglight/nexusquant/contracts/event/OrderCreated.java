package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * OrderCreated 表示订单创建事实。
 *
 * Why:
 * Gate B 需要将订单写库动作同步沉淀到 event_store，方便恢复时按事实回放。
 *
 * @param orderId 系统订单 ID
 * @param accountId 账户 ID
 * @param strategyRunId 关联策略运行 ID
 * @param symbol 交易对
 * @param clientOrderId 客户端幂等键
 * @param side 买卖方向
 * @param type 订单类型
 * @param price 下单价格，市价可空
 * @param qty 下单数量
 * @param status 订单状态快照
 * @param reason 原因说明，可空
 * @param ts 事件时间
 */
public record OrderCreated(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("strategy_run_id") String strategyRunId,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("client_order_id") String clientOrderId,
        @JsonProperty("side") String side,
        @JsonProperty("type") String type,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("qty") BigDecimal qty,
        @JsonProperty("status") String status,
        @JsonProperty("reason") String reason,
        @JsonProperty("ts") Instant ts
) {
}
