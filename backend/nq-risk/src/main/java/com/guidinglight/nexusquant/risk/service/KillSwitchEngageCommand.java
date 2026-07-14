package com.guidinglight.nexusquant.risk.service;

import java.time.Instant;
import java.util.Objects;

/**
 * Repository 内部使用的 engage-only command。
 *
 * <p>没有 DISENGAGED、release、reset 或 clear 目标状态；时间由 injected Clock 的 service
 * 生成，调用方不能伪造状态更新时间。</p>
 */
public record KillSwitchEngageCommand(
        KillSwitchScope scope,
        long expectedVersion,
        String reasonCode,
        String source,
        String updatedBy,
        String traceId,
        Instant occurredAt
) {

    public KillSwitchEngageCommand {
        Objects.requireNonNull(scope, "scope must not be null");
        if (expectedVersion <= 0) {
            throw new IllegalArgumentException("expectedVersion must be positive");
        }
        reasonCode = requireText(reasonCode, "reasonCode");
        source = requireText(source, "source");
        updatedBy = requireText(updatedBy, "updatedBy");
        traceId = requireText(traceId, "traceId");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
