package com.guidinglight.nexusquant.adapter.binance.ws;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * BinanceWsRawMessage 表示一条从 Binance 用户数据流收到的原始消息。
 * <p>
 * Why:
 * PR-BW2 需要消费原始 WS 消息并在 adapter 层完成映射，但连接治理层不能直接依赖 event_store。
 * 因此用这个 DTO 把“收到一条消息”与“如何映射/入链”解耦。
 *
 * @param eventType 事件类型，例如 executionReport / outboundAccountPosition / balanceUpdate；解析失败时为 UNKNOWN
 * @param payload 解析后的 JSON 负载；若原始文本无法解析则为空
 * @param rawPayload 原始 WS 文本
 * @param receivedAt 本地接收时间（UTC）
 */
public record BinanceWsRawMessage(
        String eventType,
        JsonNode payload,
        String rawPayload,
        Instant receivedAt
) {
}
