package com.guidinglight.nexusquant.adapter.okx.service;

/**
 * OkxWsBusinessMessageListener 定义 WS 业务消息回调。
 * <p>
 * Why:
 * PR-W2 需要把 WS 连接治理（W1）与事件入链（W2）解耦，
 * 客户端只负责分发消息，具体映射/落库由监听器实现。
 */
@FunctionalInterface
public interface OkxWsBusinessMessageListener {

    /**
     * 消费一条业务消息。
     *
     * @param message WS 业务消息
     * @param traceId 本条 WS 消息生成的 trace_id
     */
    void onMessage(OkxWsBusinessMessage message, String traceId);
}
