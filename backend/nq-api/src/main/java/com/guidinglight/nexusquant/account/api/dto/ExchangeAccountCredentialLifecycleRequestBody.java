package com.guidinglight.nexusquant.account.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * ExchangeAccountCredentialLifecycleRequestBody 表示凭证生命周期命令请求体。
 *
 * <p>Why: revoke / disable / expire 只能接收脱敏原因文本，操作者必须从认证主体解析，
 * 避免外部请求伪造 actor，也避免把 secret、token、private key 或 passphrase 写入 audit log。</p>
 */
@Schema(name = "ExchangeAccountCredentialLifecycleRequestBody", description = "账户凭证生命周期命令请求")
public record ExchangeAccountCredentialLifecycleRequestBody(
        @Size(max = 1024, message = "reason length must be less than or equal to 1024")
        @Schema(description = "命令原因，可空；不得包含密钥、token、API secret、私钥、助记词等敏感信息")
        String reason
) {
}
