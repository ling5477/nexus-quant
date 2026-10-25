package com.guidinglight.nexusquant.account.application.command;

/**
 * ExchangeAccountCredentialUpsertCommand 表示凭证新增/轮换输入。
 */
public record ExchangeAccountCredentialUpsertCommand(
        String credentialType,
        String apiKey,
        String secretKey,
        String passphrase,
        String privateKeyPem
) {
}
