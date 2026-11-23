package com.guidinglight.nexusquant.account.domain;

import java.time.Instant;
import java.util.UUID;

/** 不可变的历史操作摘要，不代表凭据的当前状态；不存储原输入或解密材料。 */
public record CredentialOperationReceipt(
        Long ownerUserId,
        Long accountId,
        UUID operationId,
        CredentialOperation.Type type,
        String inputHmac,
        String fingerprintKeyId,
        int keyVersion,
        ExchangeAccountCredentialSummary result,
        Instant completedAt) {}
