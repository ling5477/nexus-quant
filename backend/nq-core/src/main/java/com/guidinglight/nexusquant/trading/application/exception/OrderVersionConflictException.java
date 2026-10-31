package com.guidinglight.nexusquant.trading.application.exception;

/**
 * 订单准备阶段失去原快照代际所有权；调用方必须读取最新状态，不能重放旧写意图。
 * 保留 IllegalStateException 继承关系及原诊断信息，确保既有捕获与事务回滚语义不变。
 */
public final class OrderVersionConflictException extends IllegalStateException {
    public OrderVersionConflictException(String orderId) {
        super("stale order preparation: " + orderId);
    }
}
