package com.guidinglight.nexusquant.account.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ExchangeAccountActiveCredentialResponse 描述账户当前 active 凭证视图。
 */
@Schema(name = "ExchangeAccountActiveCredentialResponse", description = "账户当前 active 凭证")
public record ExchangeAccountActiveCredentialResponse(
        @Schema(description = "账户主键")
        Long exchangeAccountId,
        @Schema(description = "当前 active 凭证摘要，无 active 凭证时为 null")
        ExchangeAccountCredentialSummaryResponse activeCredential
) {
}
