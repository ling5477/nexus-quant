package com.guidinglight.nexusquant.adapter.okx.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;

/**
 * OkxWsBusinessMessage 表示一条可消费的 OKX WS 业务消息。
 * <p>
 * Why:
 * PR-W2 需要把原始 WS 文本解析为稳定 DTO，再映射到 EventEnvelope。
 * 该 DTO 只承载解析结果，不做任何落库或状态推进。
 *
 * @param channel    WS channel，例如 orders/account/positions/balance_and_position
 * @param event      事件类型（若有）
 * @param code       事件码（若有）
 * @param msg        事件描述（若有）
 * @param dataItems  data 数组拆分后的明细项
 * @param rawPayload 原始文本（用于审计排障）
 */
public record OkxWsBusinessMessage(
        String channel,
        String event,
        String code,
        String msg,
        List<JsonNode> dataItems,
        String rawPayload
) {

    /**
     * 构造时保证 dataItems 非空引用，避免消费端空指针。
     */
    public OkxWsBusinessMessage {
        Objects.requireNonNull(dataItems, "dataItems must not be null");
    }
}
