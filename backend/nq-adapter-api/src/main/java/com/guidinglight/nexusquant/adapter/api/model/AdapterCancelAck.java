package com.guidinglight.nexusquant.adapter.api.model;

import java.time.Instant;

/**
 * AdapterCancelAck 表示统一的撤单回执。
 *
 * @param accepted        是否被 adapter 接受
 * @param venue           交易场所
 * @param externalOrderId 外部订单号，可空
 * @param error           失败时的统一错误结构
 * @param ts              回执时间
 * @param traceId         链路追踪 ID
 */
public record AdapterCancelAck(
        boolean accepted,
        String venue,
        String externalOrderId,
        AdapterError error,
        Instant ts,
        String traceId
) {
}
