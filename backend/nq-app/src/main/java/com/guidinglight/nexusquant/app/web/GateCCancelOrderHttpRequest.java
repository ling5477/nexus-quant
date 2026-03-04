package com.guidinglight.nexusquant.app.web;

/**
 * GateCCancelOrderHttpRequest 描述 GateC 本地验收撤单入口的最小请求体。
 * <p>
 * Why:
 * 验收入口只负责把 HTTP 参数转成服务层入参，因此这里仅保留撤单所需的最小字段，
 * 避免 controller 自己推断业务语义或拼装额外状态。
 *
 * @param orderId       系统订单 ID，可空；为空时必须提供 accountId + clientOrderId
 * @param accountId     账户 ID，可空；当 orderId 为空时必填
 * @param clientOrderId 幂等键，可空；当 orderId 为空时必填
 * @param reason        撤单原因，必填
 */
public record GateCCancelOrderHttpRequest(
        String orderId,
        Long accountId,
        String clientOrderId,
        String reason
) {
}
