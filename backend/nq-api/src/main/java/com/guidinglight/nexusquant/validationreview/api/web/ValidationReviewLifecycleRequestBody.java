package com.guidinglight.nexusquant.validationreview.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Validation review lifecycle 的客户端可控字段。
 *
 * <p>actor、owner、tenant、requestId 与 traceId 不在 DTO 中，只能来自服务端 context。
 *
 * @param expectedVersion 乐观锁版本，必填且不能为负数
 * @param reason 人工复核原因，必填并由 core 规范化/敏感检查
 * @param metadata 可选脱敏 JSON object
 */
public record ValidationReviewLifecycleRequestBody(
        @Schema(description = "当前 case version", requiredMode = Schema.RequiredMode.REQUIRED)
        Long expectedVersion,
        @Schema(description = "人工复核原因", requiredMode = Schema.RequiredMode.REQUIRED)
        String reason,
        @Schema(description = "可选脱敏 metadata JSON object")
        JsonNode metadata
) {
}
