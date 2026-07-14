package com.guidinglight.nexusquant.risk.service;

import java.time.Instant;
import java.util.Objects;

/**
 * 从 durable store 读取的不可变 kill-switch current state。
 *
 * <p>该对象只允许 PostgreSQL 可持久化的 ENGAGED/DISENGAGED；读取失败、非法值、缺记录或
 * 时间异常由 {@link KillSwitchService} 转换成 UNKNOWN snapshot。</p>
 */
public record KillSwitchState(
        KillSwitchScope scope,
        KillSwitchStatus status,
        long version,
        String reasonCode,
        String source,
        Instant updatedAt,
        String updatedBy,
        String traceId
) {

    public KillSwitchState {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (status == KillSwitchStatus.UNKNOWN) {
            throw new IllegalArgumentException("UNKNOWN must not be persisted");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        reasonCode = requireText(reasonCode, "reasonCode");
        source = requireText(source, "source");
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt must not be null");
        }
        updatedBy = requireText(updatedBy, "updatedBy");
        traceId = requireText(traceId, "traceId");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
