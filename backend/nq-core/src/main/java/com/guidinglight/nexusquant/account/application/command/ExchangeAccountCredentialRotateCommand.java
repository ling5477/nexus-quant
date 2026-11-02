package com.guidinglight.nexusquant.account.application.command;

/**
 * ExchangeAccountCredentialRotateCommand 表示显式 credential rotate 命令输入。
 *
 * <p>Why: rotate 必须从旧 ACTIVE credential 派生 credentialType，调用方只能提交新 credential
 * material 和脱敏原因，不能通过请求体切换类型，也不能把 secret 写入响应或 audit metadata。</p>
 */
public record ExchangeAccountCredentialRotateCommand(
        String apiKey,
        String secretKey,
        String passphrase,
        String privateKeyPem,
        String reason
) {
}
