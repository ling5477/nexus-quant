package com.guidinglight.nexusquant.adapter.api.model;

import java.time.Instant;

/**
 * MarketDataSubscriptionAck 表示行情订阅结果。
 *
 * @param subscribed 是否订阅成功
 * @param venue 交易场所
 * @param channel 通道名
 * @param error 失败时的统一错误结构
 * @param ts 结果时间
 * @param traceId 链路追踪 ID
 */
public record MarketDataSubscriptionAck(
        boolean subscribed,
        String venue,
        String channel,
        AdapterError error,
        Instant ts,
        String traceId
) {
}
