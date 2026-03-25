package com.guidinglight.nexusquant.api.web;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * ApiErrorResponse 统一承接正式 HTTP API 的错误输出结构。
 * <p>
 * Why:
 * Step 2 只统一错误响应，不强行包装成功返回。
 * 错误结构必须稳定携带状态码、错误码、请求路径与 traceId，便于审计、定位和前端适配。
 */
@Schema(name = "ApiErrorResponse", description = "统一错误响应")
public record ApiErrorResponse(
        @Schema(description = "错误时间戳")
        Instant timestamp,
        @Schema(description = "HTTP 状态码")
        int status,
        @Schema(description = "HTTP 错误名")
        String error,
        @Schema(description = "平台统一错误码")
        String code,
        @Schema(description = "错误消息")
        String message,
        @Schema(description = "请求路径")
        String path,
        @Schema(description = "链路追踪 ID")
        String traceId,
        @ArraySchema(schema = @Schema(implementation = ApiFieldError.class), arraySchema = @Schema(description = "字段级错误列表"))
        List<ApiFieldError> fieldErrors
) {
}
