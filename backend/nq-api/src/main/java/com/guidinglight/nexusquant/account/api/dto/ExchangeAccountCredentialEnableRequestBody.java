package com.guidinglight.nexusquant.account.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ExchangeAccountCredentialEnableRequestBody 表示 credential enable 命令输入。
 *
 * <p>Why: enable 是把 DISABLED credential 恢复为 ACTIVE 的安全敏感命令，必须要求
 * 操作原因用于审计；请求体不得接收 credentialType、actor 或任何 credential material，
 * 避免调用方伪造类型、操作者或把 secret/token 写入 audit log。</p>
 */
@Schema(name = "ExchangeAccountCredentialEnableRequestBody", description = "账户凭证重新启用命令请求")
public record ExchangeAccountCredentialEnableRequestBody(
        @NotBlank(message = "reason must not be blank")
        @Size(max = 1024, message = "reason length must be less than or equal to 1024")
        @Schema(description = "重新启用原因，必填；不得包含密钥、token、API key、API secret、私钥、助记词等敏感信息")
        String reason
) {
}
