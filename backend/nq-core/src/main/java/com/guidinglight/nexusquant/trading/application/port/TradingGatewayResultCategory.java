package com.guidinglight.nexusquant.trading.application.port;

/**
 * TradingGatewayResultCategory 是 trading application 侧允许感知的统一执行结果分类。
 * <p>
 * Why:
 * `nq-core` 需要知道“已接受”“延迟确认”“远端不可用”这类业务语义，
 * 但不应该直接依赖 `adapter-api` 的传输契约。因此这里定义 core 内部可消费的最小分类，
 * 由边界层负责把交易所 adapter 的返回结果映射到这一层。
 */
public enum TradingGatewayResultCategory {

    SUCCESS,
    ACCEPTED,
    NOT_FOUND,
    DEFERRED,
    RETRYABLE_FAILURE,
    FATAL_FAILURE,
    THROTTLED,
    AUTH_FAILURE,
    REMOTE_UNAVAILABLE;

    /**
     * Why:
     * `DEFERRED / RETRYABLE_FAILURE / THROTTLED / REMOTE_UNAVAILABLE` 都属于“不能立刻把订单判死”的场景，
     * application 层需要一个统一入口决定是否把订单保留在 `SENT / CANCEL_REQUESTED` 等可恢复状态。
     */
    public boolean shouldDeferDecision() {
        return this == DEFERRED
                || this == RETRYABLE_FAILURE
                || this == THROTTLED
                || this == REMOTE_UNAVAILABLE;
    }
}
