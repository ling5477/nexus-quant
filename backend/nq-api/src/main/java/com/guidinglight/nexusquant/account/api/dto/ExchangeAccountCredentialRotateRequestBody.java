package com.guidinglight.nexusquant.account.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ExchangeAccountCredentialRotateRequestBody 表示显式 credential rotate 请求体。
 *
 * <p>Why: credentialType 必须从旧 active credential 派生，避免调用方借 rotate
 * 任意切换凭证类型。请求体只承载新 credential material 和 rotate reason；
 * material 不得进入 API response、普通日志或 audit metadata。</p>
 */
@Schema(name = "ExchangeAccountCredentialRotateRequestBody", description = "账户凭证显式轮换请求")
public record ExchangeAccountCredentialRotateRequestBody(
        @Schema(description = "新 API key")
        @NotBlank(message = "apiKey must not be blank")
        String apiKey,
        @Schema(description = "新 secret key，可空，按旧 credentialType 校验")
        String secretKey,
        @Schema(description = "新 passphrase，可空，按旧 credentialType 校验")
        String passphrase,
        @Schema(description = "新 Ed25519 private key PEM，可空，按旧 credentialType 校验")
        String privateKeyPem,
        @Schema(description = "轮换原因，必填；不得包含密钥、token、API secret、私钥、助记词等敏感信息")
        @NotBlank(message = "reason must not be blank")
        @Size(max = 1024, message = "reason length must be less than or equal to 1024")
        String reason
) {
}
