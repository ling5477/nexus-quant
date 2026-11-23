package com.guidinglight.nexusquant.strategy.api.dto;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunLedgerEntryItem;

import java.time.Instant;

/** 金额和账户身份不经过浏览器浮点；原始balanceAfter只是一条分录字段，不能冒充当前资产。 */
public record StrategyRunLedgerEntryResponse(
        String entryId,
        String tradeId,
        String orderId,
        String accountId,
        String currency,
        String delta,
        String balanceAfter,
        String direction,
        String refType,
        String idempotencyKey,
        String traceId,
        Instant occurredAt) {
    public static StrategyRunLedgerEntryResponse from(StrategyRunLedgerEntryItem item) {
        return new StrategyRunLedgerEntryResponse(
                item.entryId(),
                item.tradeId(),
                item.orderId(),
                Long.toString(item.accountId()),
                item.currency(),
                item.delta().toPlainString(),
                item.balanceAfter() == null ? null : item.balanceAfter().toPlainString(),
                item.direction(),
                item.refType(),
                item.idempotencyKey(),
                item.traceId(),
                item.occurredAt());
    }
}
