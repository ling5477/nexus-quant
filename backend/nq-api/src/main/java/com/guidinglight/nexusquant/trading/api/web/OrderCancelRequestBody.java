package com.guidinglight.nexusquant.trading.api.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * OrderCancelRequestBody 描述正式撤单接口的输入边界。
 */
@Schema(name = "OrderCancelRequestBody", description = "交易撤单请求体")
public record OrderCancelRequestBody(
        @Schema(description = "系统订单 ID；若为空则必须提供 accountId + clientOrderId")
        String orderId,
        @Positive(message = "accountId must be positive")
        @Schema(description = "账户 ID；与 clientOrderId 组合定位订单")
        Long accountId,
        @Schema(description = "客户端订单 ID；与 accountId 组合定位订单")
        String clientOrderId,
        @NotBlank(message = "reason must not be blank")
        @Schema(description = "撤单原因", requiredMode = Schema.RequiredMode.REQUIRED)
        String reason
) {

    /**
     * Why:
     * 撤单请求允许两种定位方式：直接给 `orderId`，或给 `accountId + clientOrderId`。
     * 这是显式契约，不允许把定位判断散落到 controller/service 多处重复实现。
     */
    @AssertTrue(message = "either orderId or accountId + clientOrderId must be provided")
    @Schema(hidden = true)
    public boolean hasOrderLocator() {
        boolean hasOrderId = orderId != null && !orderId.isBlank();
        boolean hasAccountAndClientOrderId = accountId != null
                && accountId > 0
                && clientOrderId != null
                && !clientOrderId.isBlank();
        return hasOrderId || hasAccountAndClientOrderId;
    }
}


