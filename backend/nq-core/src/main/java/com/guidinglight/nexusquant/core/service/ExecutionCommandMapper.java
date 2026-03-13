package com.guidinglight.nexusquant.core.service;

import com.guidinglight.nexusquant.contracts.command.CancelOrderCommand;
import com.guidinglight.nexusquant.contracts.command.PlaceOrderCommand;
import com.guidinglight.nexusquant.core.model.OrderRecord;

/**
 * ExecutionCommandMapper 负责把 core 请求对象映射为冻结的 contracts 命令。
 * <p>
 * Why:
 * `OrderCommandService` 在 GateD 中应聚焦编排、状态机、审计与 adapter 路由；
 * contracts 组装如果继续散落在 service 内，会让“执行编排”和“契约冻结”两类职责重新耦合。
 */
final class ExecutionCommandMapper {

    private ExecutionCommandMapper() {
    }

    /**
     * 把下单入口参数映射为冻结的下单命令。
     * <p>
     * Why:
     * 下单命令会同时被 risk、event_store、审计链路消费，必须在这里一次性定好 requestId、
     * idempotencyKey、source、quantity 等字段，避免下游再各自推导。
     */
    static PlaceOrderCommand toPlaceCommand(PlaceOrderRequest request, String orderId) {
        return new PlaceOrderCommand(
                orderId,
                request.requestId(),
                request.accountId(),
                request.venue(),
                request.symbol(),
                request.clientOrderId(),
                request.idempotencyKey(),
                request.side().name(),
                request.type().name(),
                request.price(),
                request.quantity(),
                request.timeInForce(),
                request.source(),
                request.strategyRunId(),
                request.traceId()
        );
    }

    /**
     * 把撤单入口参数与当前订单快照映射为冻结的撤单命令。
     * <p>
     * Why:
     * 撤单允许调用方只提供部分定位字段，但真正写入 event_store 的命令必须是完整语义，
     * 否则 recovery / audit 无法还原当时到底撤的是哪笔单。
     */
    static CancelOrderCommand toCancelCommand(CancelOrderRequest request, OrderRecord currentOrder) {
        return new CancelOrderCommand(
                currentOrder.orderId(),
                request.requestId(),
                currentOrder.accountId(),
                coalesce(request.venue(), currentOrder.venue()),
                coalesce(request.symbol(), currentOrder.symbol()),
                currentOrder.clientOrderId(),
                coalesce(request.externalOrderId(), currentOrder.externalOrderId()),
                request.reason(),
                request.traceId()
        );
    }

    private static String coalesce(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }
}
