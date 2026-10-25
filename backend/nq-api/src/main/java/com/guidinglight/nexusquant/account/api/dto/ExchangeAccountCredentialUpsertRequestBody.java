package com.guidinglight.nexusquant.account.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ExchangeAccountCredentialUpsertRequestBody 表示凭证新增/轮换请求。
 */
@Schema(name = "ExchangeAccountCredentialUpsertRequestBody", description = "账户凭证新增或轮换请求")
public record ExchangeAccountCredentialUpsertRequestBody(
        @Schema(description = "凭证类型：OKX_API_V5 / BINANCE_HMAC / BINANCE_ED25519")
        @NotBlank(message = "credentialType must not be blank")
        @Size(max = 32, message = "credentialType length must be less than or equal to 32")
        String credentialType,
        @Schema(description = "API key")
        @NotBlank(message = "apiKey must not be blank")
        String apiKey,
        @Schema(description = "secret key，可空")
        String secretKey,
        @Schema(description = "passphrase，可空")
        String passphrase,
        @Schema(description = "Ed25519 private key PEM，可空")
        String privateKeyPem
) {
}
