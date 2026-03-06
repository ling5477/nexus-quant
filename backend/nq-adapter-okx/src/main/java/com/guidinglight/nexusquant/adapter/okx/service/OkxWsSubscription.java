package com.guidinglight.nexusquant.adapter.okx.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * OkxWsSubscription 表示一条 OKX 私有 WS 订阅参数。
 * <p>
 * Why:
 * PR-W1 只做连接治理，不做业务落库；先把订阅参数建模，后续 W2/W3 才基于同一模型做事件映射与协同降级。
 *
 * @param channel  频道名，例如 orders/account/positions
 * @param instType 可选交易类型，例如 SPOT
 */
public record OkxWsSubscription(String channel, String instType) {

    /**
     * 构建通用订阅参数 map，便于统一序列化为 JSON。
     */
    public Map<String, String> toArgMap() {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel must not be blank");
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        result.put("channel", channel.trim());
        if (instType != null && !instType.isBlank()) {
            result.put("instType", instType.trim());
        }
        return result;
    }

    /**
     * 返回用于去重的键。
     */
    public String key() {
        return channel + "|" + Objects.toString(instType, "");
    }
}
