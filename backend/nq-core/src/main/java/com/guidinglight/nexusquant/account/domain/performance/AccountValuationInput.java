package com.guidinglight.nexusquant.account.domain.performance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 一致性读取的投影和估值证据；资产数量已经包含仓位，不能再次加持仓。 */
public record AccountValuationInput(
        List<Asset> assets,
        boolean projectionComplete,
        BigDecimal cumulativeNetFlow,
        String flowStatus,
        List<FundingEntry> fundingEntries,
        long fundingEntryCount) {
    public record FundingEntry(
            String entryId, String refId, String currency, BigDecimal delta, Instant at) {}

    public record Asset(
            long snapshotId,
            String currency,
            BigDecimal balance,
            BigDecimal available,
            BigDecimal frozen,
            String balanceBasis,
            Instant recordedAt,
            BigDecimal price,
            Long barId,
            Instant priceAt,
            Instant availableAt,
            String priceSource) {}
}
