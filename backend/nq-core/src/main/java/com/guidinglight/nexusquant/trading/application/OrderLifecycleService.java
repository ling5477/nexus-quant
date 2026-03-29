package com.guidinglight.nexusquant.trading.application;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;

import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * OrderLifecycleService 为 scheduler / ws / recovery 提供语义化订单生命周期入口。
 * <p>
 * Why:
 * GateD 要求 scheduler 不再直接依赖“任意状态迁移”能力，而是通过显式生命周期动作推进状态。
 * 这里仍复用 `OrderCommandService` 的状态机与审计实现，但把允许暴露给外部协调器的动作收口为有限集合。
 */
@Service
public class OrderLifecycleService {

    private final OrderCommandService orderCommandService;

    public OrderLifecycleService(OrderCommandService orderCommandService) {
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
    }

    /**
     * 将订单推进到 ACCEPTED。
     * <p>
     * Why:
     * scheduler / ws / reconcile 只能表达“外部已确认接受”这类业务语义，不能暴露任意状态迁移能力。
     */
    public OrderRecord acknowledge(String orderId, String reason, String traceId) {
        return orderCommandService.transitionOrder(orderId, OrderStatus.ACCEPTED, reason, traceId);
    }

    /**
     * 将订单推进到 REJECTED。
     * <p>
     * Why:
     * 显式 reject 入口可以把“下单被外部拒绝”的语义与普通状态对齐动作区分开，便于 scheduler 只做有限动作。
     */
    public OrderRecord reject(String orderId, String reason, String traceId) {
        return orderCommandService.transitionOrder(orderId, OrderStatus.REJECTED, reason, traceId);
    }

    /**
     * 将订单推进到 PARTIALLY_FILLED。
     * <p>
     * Why:
     * partial fill 是外部事实对齐常见中间态，必须有单独入口，避免 scheduler 拼装两段迁移逻辑。
     */
    public OrderRecord markPartiallyFilled(String orderId, String reason, String traceId) {
        return orderCommandService.transitionOrder(orderId, OrderStatus.PARTIALLY_FILLED, reason, traceId);
    }

    /**
     * 将订单推进到 FILLED。
     * <p>
     * Why:
     * FILLED 是终态，Paper/Binance/OKX 的成交收敛都必须经由同一条显式生命周期路径。
     */
    public OrderRecord markFilled(String orderId, String reason, String traceId) {
        return orderCommandService.transitionOrder(orderId, OrderStatus.FILLED, reason, traceId);
    }

    /**
     * 将订单推进到 CANCEL_REQUESTED。
     * <p>
     * Why:
     * 取消请求已发出但尚未确认时，需要和最终 CANCELLED/CANCEL_REJECTED 明确区分，便于后续恢复与对账。
     */
    public OrderRecord requestCancel(String orderId, String reason, String traceId) {
        return orderCommandService.transitionOrder(orderId, OrderStatus.CANCEL_REQUESTED, reason, traceId);
    }

    /**
     * 将订单推进到 CANCELLED。
     * <p>
     * Why:
     * 外部已确认撤单完成时必须走显式 cancel 入口，防止不同 scheduler 路径各自拼不同 reason 语义。
     */
    public OrderRecord cancel(String orderId, String reason, String traceId) {
        return orderCommandService.transitionOrder(orderId, OrderStatus.CANCELLED, reason, traceId);
    }

    /**
     * 将订单推进到 CANCEL_REJECTED。
     * <p>
     * Why:
     * 取消拒绝与下单拒绝属于不同事实；显式入口可避免把 cancel reject 错当成普通 reject。
     */
    public OrderRecord rejectCancel(String orderId, String reason, String traceId) {
        return orderCommandService.transitionOrder(orderId, OrderStatus.CANCEL_REJECTED, reason, traceId);
    }

    /**
     * 仅暴露给外部事实对齐路径使用的有限状态集合。
     * <p>
     * Why:
     * reconcile / ws acceleration 面对的是交易所事实回执，而不是任意内部状态；允许集合必须显式限制，
     * 否则 scheduler 很容易重新长回“万能状态推进器”。
     */
    public OrderRecord applyExternalStatus(String orderId, OrderStatus targetStatus, String reason, String traceId) {
        return switch (targetStatus) {
            case ACCEPTED -> acknowledge(orderId, reason, traceId);
            case PARTIALLY_FILLED -> markPartiallyFilled(orderId, reason, traceId);
            case FILLED -> markFilled(orderId, reason, traceId);
            case CANCEL_REQUESTED -> requestCancel(orderId, reason, traceId);
            case CANCELLED -> cancel(orderId, reason, traceId);
            case CANCEL_REJECTED -> rejectCancel(orderId, reason, traceId);
            case REJECTED -> reject(orderId, reason, traceId);
            default -> throw new IllegalArgumentException("unsupported external lifecycle status: " + targetStatus);
        };
    }
}


