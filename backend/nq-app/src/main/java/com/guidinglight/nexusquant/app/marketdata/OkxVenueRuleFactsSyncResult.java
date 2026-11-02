package com.guidinglight.nexusquant.app.marketdata;

import java.time.Instant;
import java.util.List;

/**
 * OkxVenueRuleFactsSyncResult 是 operator-triggered bounded sync 的脱敏结果。
 */
public record OkxVenueRuleFactsSyncResult(
        List<String> symbols,
        int insertedCount,
        int updatedCount,
        Instant observedAt,
        Instant syncedAt
) {
    public OkxVenueRuleFactsSyncResult {
        symbols = List.copyOf(symbols);
    }
}
