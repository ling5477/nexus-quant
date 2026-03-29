package com.guidinglight.nexusquant.trading.api.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * ReconcileRunOnceRequest 描述接口请求体。
 */
@Schema(name = "ReconcileRunOnceRequest", description = "单次对账触发请求体")
public record ReconcileRunOnceRequest(
        @Size(max = 32, message = "venue length must be less than or equal to 32")
        @Schema(description = "交易 venue；为空时默认 OKX")
        String venue,
        @Positive(message = "limit must be positive")
        @Schema(description = "单次扫描上限；为空时默认 100")
        Integer limit
) {
}


