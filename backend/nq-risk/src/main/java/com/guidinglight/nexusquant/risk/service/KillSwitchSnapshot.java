package com.guidinglight.nexusquant.risk.service;

import java.time.Instant;
import java.util.Objects;

/**
 * 一次 kill-switch 只读评估的不可变 snapshot。
 *
 * <p>updatedAt 是 durable state 的权威更新时间，observedAt 是本次读取时间。UNKNOWN 允许
 * updatedAt 为空，用来准确表达缺记录、解析失败或存储异常，且始终阻断。</p>
 */
public record KillSwitchSnapshot(
        KillSwitchScope scope,
        KillSwitchStatus status,
        long version,
        String reasonCode,
        String source,
        Instant updatedAt,
        Instant observedAt,
        String traceId
) {

    public KillSwitchSnapshot {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(status, "status must not be null");
        reasonCode = requireText(reasonCode, "reasonCode");
        source = requireText(source, "source");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        traceId = requireText(traceId, "traceId");
        if (status == KillSwitchStatus.UNKNOWN) {
            if (version != 0) {
                throw new IllegalArgumentException("UNKNOWN version must be zero");
            }
        } else {
            if (version <= 0 || updatedAt == null) {
                throw new IllegalArgumentException("persisted snapshot requires version and updatedAt");
            }
        }
    }

    /**
     * @return ENGAGED、UNKNOWN 以及任何非显式 DISENGAGED 状态均返回 true
     */
    public boolean blocksOperations() {
        return status != KillSwitchStatus.DISENGAGED;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
