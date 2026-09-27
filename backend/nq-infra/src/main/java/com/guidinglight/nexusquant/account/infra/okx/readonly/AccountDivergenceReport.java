package com.guidinglight.nexusquant.account.infra.okx.readonly;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 一次只读比较的有界结果；摘要绑定事实，明细不保存凭证或原始响应。 */
public record AccountDivergenceReport(
        UUID observationId,
        long exchangeAccountId,
        Long legacyAccountId,
        Instant externalObservedAt,
        Instant comparisonAt,
        String externalFactIdentity,
        String canonicalFactIdentity,
        String publicRuleIdentity,
        BigDecimal publicMinimumSize,
        Classification aggregate,
        List<Item> items
) {
    public AccountDivergenceReport {
        Objects.requireNonNull(observationId);
        Objects.requireNonNull(externalObservedAt);
        Objects.requireNonNull(comparisonAt);
        Objects.requireNonNull(aggregate);
        items = List.copyOf(items);
    }

    public enum Classification {
        MATCH, BALANCE_MISMATCH, BALANCE_FACT_MISSING, BALANCE_SEMANTIC_MISMATCH,
        VENUE_BALANCE_NOT_SEMANTICALLY_COMPARABLE,
        UNEXPECTED_EXTERNAL_ASSET, UNEXPECTED_LOCAL_ASSET, EXTERNAL_DUST_BALANCE,
        EXTERNAL_OPEN_ORDER_ONLY, LOCAL_ACTIVE_ORDER_ONLY, ORDER_IDENTITY_MISMATCH,
        ORDER_QUANTITY_MISMATCH, ORDER_PRICE_MISMATCH, ORDER_STATE_MISMATCH,
        EXTERNAL_NON_SPOT_POSITION_PRESENT, POSITION_MISMATCH, CANONICAL_FACT_STALE,
        EXTERNAL_FACT_STALE, MULTIPLE_DIVERGENCES, UNKNOWN
    }

    public record Item(
            Classification classification,
            String dimension,
            String assetOrInstrument,
            AccountFactsSnapshot.Status externalStatus,
            AccountFactsSnapshot.Status canonicalStatus,
            Instant externalObservedAt,
            Instant canonicalObservedAt,
            String reason,
            String externalValueIdentity,
            String canonicalValueIdentity
    ) {
        public Item {
            Objects.requireNonNull(classification);
            Objects.requireNonNull(dimension);
            Objects.requireNonNull(externalStatus);
            Objects.requireNonNull(canonicalStatus);
            Objects.requireNonNull(reason);
        }
    }

    @Override
    public String toString() {
        return "AccountDivergenceReport[REDACTED]";
    }
}
