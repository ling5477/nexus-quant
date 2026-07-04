package com.guidinglight.nexusquant.marketdata.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guidinglight.nexusquant.adapter.api.dataquality.DataQualitySummary;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessDataOrigin;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessErrorCategory;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessGapStatus;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessSourceHealth;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessSourceStatus;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessStatus;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class MarketdataReadinessResponseTest {

    @Test
    void shouldKeepO2DataQualityVocabularyConsumableByReadinessResponse() {
        assertEquals(names(DataQualitySummary.DataOrigin.values()), names(MarketdataReadinessDataOrigin.values()));
        assertEquals(names(DataQualitySummary.SourceStatus.values()), names(MarketdataReadinessSourceStatus.values()));
        assertEquals(names(DataQualitySummary.SourceHealth.values()), names(MarketdataReadinessSourceHealth.values()));
        assertEquals(names(DataQualitySummary.GapStatus.values()), names(MarketdataReadinessGapStatus.values()));
        assertEquals(names(DataQualitySummary.ErrorCategory.values()), names(MarketdataReadinessErrorCategory.values()));
        assertEquals(names(DataQualitySummary.FreshnessStatus.values()), names(MarketdataReadinessStatus.FRESH,
                MarketdataReadinessStatus.STALE,
                MarketdataReadinessStatus.VERY_STALE,
                MarketdataReadinessStatus.NO_DATA,
                MarketdataReadinessStatus.ERROR,
                MarketdataReadinessStatus.DISABLED));
    }

    private static List<String> names(Enum<?>... values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
