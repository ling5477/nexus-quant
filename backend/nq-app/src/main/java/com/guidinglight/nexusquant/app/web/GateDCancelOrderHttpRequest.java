package com.guidinglight.nexusquant.app.web;

/**
 * GateDCancelOrderHttpRequest 描述 GateD 本地验收撤单入口请求体。
 * <p>
 * Why:
 * 第四批开始清理 GateC HTTP 兼容层，因此撤单 DTO 也迁到 GateD 命名，避免新旧阶段语义继续并存。
 *
 * @param orderId       系统订单 ID，可空；为空时必须提供 accountId + clientOrderId
 * @param accountId     账户 ID，可空；当 orderId 为空时必填
 * @param clientOrderId 幂等键，可空；当 orderId 为空时必填
 * @param reason        撤单原因，必填
 */
public record GateDCancelOrderHttpRequest(
        String orderId,
        Long accountId,
        String clientOrderId,
        String reason
) {
}
