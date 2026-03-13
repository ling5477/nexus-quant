package com.guidinglight.nexusquant.app.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;

import java.math.BigDecimal;

/**
 * GateDOrderHttpRequest 描述 GateD 本地验收下单入口请求体。
 * <p>
 * Why:
 * 第四批开始清理 GateC HTTP 兼容层，因此本地验收 DTO 名称与 canonical route 一并切换到 GateD，
 * 避免 controller、profile、route 已迁移但 HTTP 模型仍停留在旧阶段命名。
 * 第八批继续把 canonical 语义推进到请求层，因此本地验收 DTO 对外字段名统一为 `orderType / quantity`，
 * 并在第九批移除上一批临时保留的 `type / qty` JSON alias，避免请求层继续漂浮在双命名状态。
 *
 * @param accountId     账户 ID，必填
 * @param strategyRunId 策略运行 ID，可空
 * @param venue         交易场所，必填
 * @param clientOrderId 客户端幂等键，必填
 * @param symbol        交易对，必填
 * @param side          买卖方向，必填
 * @param orderType     订单类型，必填
 * @param price         价格，LIMIT 必填；MARKET 可空
 * @param quantity      数量，必填且必须大于 0
 */
public record GateDOrderHttpRequest(
        Long accountId,
        String strategyRunId,
        String venue,
        String clientOrderId,
        String symbol,
        OrderSide side,
        @JsonProperty("orderType")
        OrderType orderType,
        BigDecimal price,
        @JsonProperty("quantity")
        BigDecimal quantity
) {
}
