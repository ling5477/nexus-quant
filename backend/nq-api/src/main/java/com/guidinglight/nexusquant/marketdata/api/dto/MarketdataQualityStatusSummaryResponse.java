package com.guidinglight.nexusquant.marketdata.api.dto;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatusSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * MarketdataQualityStatusSummaryResponse exposes bar quality counts without raw provider payloads.
 */
@Schema(name = "MarketdataQualityStatusSummaryResponse", description = "Aggregated marketdata quality status counts")
public record MarketdataQualityStatusSummaryResponse(
        long okCount,
        long gapSignalCount,
        long invalidCount,
        long unknownQualityCount,
        Map<String, Long> statuses
) {
    public static MarketdataQualityStatusSummaryResponse from(MarketdataQualityStatusSummary summary) {
        return new MarketdataQualityStatusSummaryResponse(
                summary.okCount(),
                summary.gapSignalCount(),
                summary.invalidCount(),
                summary.unknownQualityCount(),
                summary.statuses()
        );
    }
}
