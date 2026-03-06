package com.guidinglight.nexusquant.adapter.okx.service;

/**
 * OkxWsConnectionListener 定义 WS 连接状态回调。
 * <p>
 * Why:
 * PR-W3 需要在断线/订阅失败时触发 REST reconcile 降级，
 * 因此连接治理层必须暴露最小状态事件，而不引入业务落库逻辑。
 */
public interface OkxWsConnectionListener {

    /**
     * 连接成功回调。
     *
     * @param traceId 连接事件 trace_id
     */
    default void onConnected(String traceId) {
    }

    /**
     * 重连计划回调。
     *
     * @param reason  触发原因
     * @param attempt 重连次数
     * @param delayMs 本次退避延迟
     * @param traceId 连接事件 trace_id
     */
    default void onReconnectScheduled(String reason, int attempt, long delayMs, String traceId) {
    }

    /**
     * 订阅失败回调。
     *
     * @param channel 失败 channel
     * @param code    失败码
     * @param message 失败原因
     * @param traceId 连接事件 trace_id
     */
    default void onSubscribeFailed(String channel, String code, String message, String traceId) {
    }
}
