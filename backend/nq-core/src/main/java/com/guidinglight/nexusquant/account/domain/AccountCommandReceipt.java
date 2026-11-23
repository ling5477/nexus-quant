package com.guidinglight.nexusquant.account.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 成功事务的不可变回执；账户摘要是完成时快照，不是当前账户状态。
 */
public record AccountCommandReceipt(
        Long ownerUserId,
        UUID commandId,
        AccountCommand.Type type,
        String requestSha256,
        ExchangeAccountSummary result,
        Instant completedAt) {}
