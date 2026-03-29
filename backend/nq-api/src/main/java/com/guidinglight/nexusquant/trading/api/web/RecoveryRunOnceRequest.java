package com.guidinglight.nexusquant.trading.api.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * RecoveryRunOnceRequest 描述接口请求体。
 */
@Schema(name = "RecoveryRunOnceRequest", description = "单次恢复触发请求体")
public record RecoveryRunOnceRequest(
        @Size(max = 32, message = "venue length must be less than or equal to 32")
        @Schema(description = "交易 venue；为空时默认 OKX")
        String venue
) {
}


