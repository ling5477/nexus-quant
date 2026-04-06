package com.guidinglight.nexusquant.account.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * CreateExchangeAccountRequestBody 表示创建账户的最小请求。
 */
@Schema(name = "CreateExchangeAccountRequestBody", description = "创建交易账户请求")
public record CreateExchangeAccountRequestBody(
        @Schema(description = "交易所编码")
        @NotBlank(message = "exchangeCode must not be blank")
        @Size(max = 32, message = "exchangeCode length must be less than or equal to 32")
        String exchangeCode,
        @Schema(description = "交易环境，固定为 SIM/LIVE")
        @NotBlank(message = "tradeEnv must not be blank")
        @Size(max = 8, message = "tradeEnv length must be less than or equal to 8")
        String tradeEnv,
        @Schema(description = "账户别名")
        @NotBlank(message = "accountAlias must not be blank")
        @Size(max = 64, message = "accountAlias length must be less than or equal to 64")
        String accountAlias,
        @Schema(description = "外部账户引用，可空")
        @Size(max = 128, message = "externalAccountRef length must be less than or equal to 128")
        String externalAccountRef
) {
}
