package com.guidinglight.nexusquant.contracts.command;

/**
 * CancelOrderCommand 冻结撤单命令契约。
 *
 * @param orderId 订单 ID，可与 clientOrderId 二选一传入
 * @param accountId 账户 ID
 * @param clientOrderId 客户端幂等键
 * @param reason 撤单原因
 * @param traceId 链路追踪 ID
 */
public record CancelOrderCommand(
        String orderId,
        Long accountId,
        String clientOrderId,
        String reason,
        String traceId
) {
}
