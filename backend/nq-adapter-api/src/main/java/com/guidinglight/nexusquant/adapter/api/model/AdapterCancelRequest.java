package com.guidinglight.nexusquant.adapter.api.model;

/**
 * AdapterCancelRequest 描述统一撤单请求。
 * <p>
 * Why:
 * GateC-0 要求 core 只能依赖 adapter-api，因此撤单定位方式必须在 adapter-api 内统一冻结，
 * 以便后续 query-confirm、真实交易所 ordId 和 paper 本地单号都走同一入口。
 *
 * @param orderId         系统订单 ID
 * @param accountId       账户 ID
 * @param venue           交易场所
 * @param symbol          交易对
 * @param clientOrderId   客户端幂等键，可空
 * @param externalOrderId 外部订单号，可空；存在时优先使用
 * @param traceId         链路追踪 ID
 */
public record AdapterCancelRequest(
        String orderId,
        Long accountId,
        String venue,
        String symbol,
        String clientOrderId,
        String externalOrderId,
        String traceId
) {
}
