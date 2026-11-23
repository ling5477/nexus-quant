package com.guidinglight.nexusquant.strategy.domain.shadowrun;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

final class ShadowRunJsonRules {

    private ShadowRunJsonRules() {
    }

    static void requireObject(JsonNode value, String fieldName) {
        if (!value.isObject()) {
            throw new IllegalArgumentException(fieldName + " must be a JSON object");
        }
    }

    static void requireArray(JsonNode value, String fieldName) {
        if (!value.isArray()) {
            throw new IllegalArgumentException(fieldName + " must be a JSON array");
        }
    }

    static void validateWindow(Instant windowStart, Instant windowEnd) {
        if (windowStart != null && windowEnd != null && windowEnd.isBefore(windowStart)) {
            throw new IllegalArgumentException("windowEnd must not be before windowStart");
        }
    }
}
