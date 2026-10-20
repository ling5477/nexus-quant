package com.guidinglight.nexusquant.adapter.binance.ws;

/**
 * BinanceWsConnectionListener 定义 Binance 私有 WS 的连接状态回调。
 * <p>
 * Why:
 * PR-BW3 需要在断线、重连成功、listenKey 失效时触发 REST reconcile 降级或重置内部节流状态，
 * 因此连接治理层必须暴露最小连接事件，但不能把业务写库逻辑塞回 adapter 层。
 */
public interface BinanceWsConnectionListener {

    /**
     * 首次连接成功回调。
     *
     * @param traceId 连接事件 trace_id
     */
    default void onConnected(String traceId) {
    }

    /**
     * 重连成功回调。
     *
     * @param reason  本次重建原因
     * @param traceId 连接事件 trace_id
     */
    default void onReconnected(String reason, String traceId) {
    }

    /**
     * 连接断开并已进入重连计划回调。
     *
     * @param reason  断线/重连原因
     * @param attempt 当前重连次数
     * @param delayMs 本次退避延迟
     * @param traceId 连接事件 trace_id
     */
    default void onDisconnected(String reason, int attempt, long delayMs, String traceId) {
    }

    /**
     * listenKey 失效或 refresh 失败回调。
     *
     * @param errorCode Binance 返回错误码
     * @param reason    失败原因
     * @param traceId   连接事件 trace_id
     */
    default void onListenKeyExpired(String errorCode, String reason, String traceId) {
    }
}
