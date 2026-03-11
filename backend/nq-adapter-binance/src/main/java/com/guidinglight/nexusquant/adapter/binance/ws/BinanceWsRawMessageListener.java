package com.guidinglight.nexusquant.adapter.binance.ws;

/**
 * BinanceWsRawMessageListener 负责消费 Binance 用户数据流原始消息。
 * <p>
 * Why:
 * PR-BW2 只允许在连接治理层外部做映射与入链，因此这里定义一个最小监听接口，
 * 让后续 bridge 在不改动 WS 连接逻辑的前提下接入 event_store。
 */
@FunctionalInterface
public interface BinanceWsRawMessageListener {

    /**
     * 处理一条原始 WS 消息。
     *
     * @param message 原始消息 DTO
     */
    void onMessage(BinanceWsRawMessage message);
}
