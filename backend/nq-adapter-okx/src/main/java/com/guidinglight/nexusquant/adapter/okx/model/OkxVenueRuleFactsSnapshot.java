package com.guidinglight.nexusquant.adapter.okx.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * OkxVenueRuleFactsSnapshot 表示一次完整 public response 解析成功后的 bounded snapshot。
 *
 * @param facts 仅包含 server-side allowlist 命中的 1..3 条 Spot facts
 * @param observedAt 完整 response 成功解析和校验后的本地观察时间
 */
public record OkxVenueRuleFactsSnapshot(List<OkxVenueRuleFact> facts, Instant observedAt) {

    public OkxVenueRuleFactsSnapshot {
        facts = List.copyOf(Objects.requireNonNull(facts, "facts must not be null"));
        observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        if (facts.isEmpty() || facts.size() > 3) {
            throw new IllegalArgumentException("facts must contain 1..3 items");
        }
    }
}
