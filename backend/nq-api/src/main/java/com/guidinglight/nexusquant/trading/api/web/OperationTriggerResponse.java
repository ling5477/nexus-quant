package com.guidinglight.nexusquant.trading.api.web;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OperationTriggerResponse 描述接口响应体。
 */
@Schema(name = "OperationTriggerResponse", description = "接口响应体")
public record OperationTriggerResponse(
        @Schema(description = "action")
        String action,
        @Schema(description = "traceId")
        String traceId,
        @Schema(description = "detail")
        String detail
) {
}


