package com.guidinglight.nexusquant.account.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UpdateExchangeAccountRequestBody 表示账户基础信息更新请求。
 */
@Schema(name = "UpdateExchangeAccountRequestBody", description = "更新交易账户基础信息请求")
public record UpdateExchangeAccountRequestBody(
        @Schema(description = "账户别名")
        @NotBlank(message = "accountAlias must not be blank")
        @Size(max = 64, message = "accountAlias length must be less than or equal to 64")
        String accountAlias,
        @Schema(description = "外部账户引用，可空")
        @Size(max = 128, message = "externalAccountRef length must be less than or equal to 128")
        String externalAccountRef
) {
}
