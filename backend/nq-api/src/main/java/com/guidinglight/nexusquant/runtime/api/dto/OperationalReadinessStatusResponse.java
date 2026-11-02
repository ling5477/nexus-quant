package com.guidinglight.nexusquant.runtime.api.dto;

import java.util.Objects;

/**
 * OperationalReadinessStatusResponse is one safe runtime boundary status item.
 *
 * <p>原因：运行就绪状态使用稳定 DTO，避免暴露原始配置映射。每个状态携带机器可读的
 * status, a fail-closed readiness boolean, and a redacted reason code/message that can be shown to
 * operators without exposing runtime-sensitive values.
 *
 * @param status     规范化状态字符串；不表示真实交易就绪
 * @param ready      whether this item is ready for real runtime use; current 6B baseline is fail-closed
 * @param reasonCode stable safe reason code; no runtime value embedded
 * @param reason     short human-readable reason; no runtime value embedded
 */
public record OperationalReadinessStatusResponse(
        String status,
        boolean ready,
        String reasonCode,
        String reason
) {

    public OperationalReadinessStatusResponse {
        requireText(status, "status");
        requireText(reasonCode, "reasonCode");
        requireText(reason, "reason");
    }

    private static void requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
