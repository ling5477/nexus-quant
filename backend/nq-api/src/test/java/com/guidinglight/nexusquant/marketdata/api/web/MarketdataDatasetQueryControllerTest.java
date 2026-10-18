package com.guidinglight.nexusquant.marketdata.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetListService;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetPage;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataDatasetListQuery;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

class MarketdataDatasetQueryControllerTest {
    @Test
    void bindsExactDatasetAndEligibilityFiltersWithoutCallingMutationContracts() throws Exception {
        var service = mock(MarketdataDatasetListService.class);
        when(service.list(any())).thenReturn(new MarketdataDatasetPage(List.of(), 0, 50, 100));
        var mvc =
                MockMvcBuilders.standaloneSetup(new MarketdataDatasetQueryController(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        UUID id = UUID.randomUUID();
        mvc.perform(
                        get("/api/marketdata/datasets/page")
                                .param("datasetId", id.toString())
                                .param("datasetName", "%_!")
                                .param("exchangeCode", "binance")
                                .param("marketType", "spot")
                                .param("symbol", "bnb-usdt")
                                .param("interval", "1m")
                                .param("status", "ready")
                                .param("qualityStatus", "ok")
                                .param("source", "marketdata_bars")
                                .param("maximumGapCount", "0")
                                .param("limit", "50")
                                .param("offset", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.limit").value(50))
                .andExpect(jsonPath("$.offset").value(100));
        var captured = ArgumentCaptor.forClass(MarketdataDatasetListQuery.class);
        verify(service).list(captured.capture());
        assertEquals(id, captured.getValue().datasetId());
        assertEquals("%_!", captured.getValue().datasetName());
        assertEquals("BINANCE", captured.getValue().exchangeCode());
        assertEquals("marketdata_bars", captured.getValue().source());
        assertEquals(0L, captured.getValue().maximumGapCount());
    }

    @Test
    void rejectsMalformedIdentitiesBoundsAndControlCharactersBeforeReading() throws Exception {
        var service = mock(MarketdataDatasetListService.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(new MarketdataDatasetQueryController(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        for (String[] invalid :
                new String[][] {
                    {"datasetId", "str-missing"},
                    {"limit", "101"},
                    {"offset", "-1"},
                    {"offset", "2147483648"},
                    {"maximumGapCount", "-1"},
                    {"datasetName", "name\t"}
                }) {
            mvc.perform(get("/api/marketdata/datasets/page").param(invalid[0], invalid[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").exists());
        }
        verifyNoInteractions(service);
    }
}
