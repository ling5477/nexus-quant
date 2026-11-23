package com.guidinglight.nexusquant.adapters.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * AdapterReadinessResponse 是 {@code GET /api/adapters/readiness} 的只读响应封装。
 * <p>
 * Why:
 * 前端需要一次性拿到「当前各 venue × capability 的 readiness 快照」，用于展示 OKX / Binance / Noop
 * 当前不可实盘及其原因。该响应只聚合静态 readiness 决策，不触达真实交易所、不读取 credential、
 * 不触发 adapter delegate / 下单 / 撤单 / 行情订阅。
 *
 * @param generatedAt 快照生成时间（服务端时钟）；不可为空
 * @param items       各 venue × capability 的 readiness 条目；构造期拷贝为不可变列表
 */
public record AdapterReadinessResponse(
        Instant generatedAt,
        List<AdapterReadinessItemResponse> items
) {

    public AdapterReadinessResponse {
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        items = items == null ? List.of() : List.copyOf(items);
    }
}
