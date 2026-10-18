package com.guidinglight.nexusquant.strategy.api.web;

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
import com.guidinglight.nexusquant.strategy.application.StrategyVersionListService;
import com.guidinglight.nexusquant.strategy.domain.StrategyVersionListItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyVersionPage;
import com.guidinglight.nexusquant.strategy.domain.StrategyVersionStatus;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyVersionListQuery;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

class StrategyVersionQueryControllerTest {
    @Test
    void bindsParentAndLiteralFiltersWhilePreservingTheRequestedWindow() throws Exception {
        var service = mock(StrategyVersionListService.class);
        when(service.list(any())).thenReturn(new StrategyVersionPage(List.of(), 0, 50, 100));
        var mvc =
                MockMvcBuilders.standaloneSetup(new StrategyVersionQueryController(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        mvc.perform(
                        get("/api/strategies/parent/versions/page")
                                .param("strategyVersionId", " version-id ")
                                .param("versionName", "%_!")
                                .param("version", "12")
                                .param("status", "archived")
                                .param("limit", "50")
                                .param("offset", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.limit").value(50))
                .andExpect(jsonPath("$.offset").value(100));
        var query = ArgumentCaptor.forClass(StrategyVersionListQuery.class);
        verify(service).list(query.capture());
        assertEquals("parent", query.getValue().strategyCode());
        assertEquals("version-id", query.getValue().strategyVersionId());
        assertEquals("%_!", query.getValue().versionName());
        assertEquals(12, query.getValue().version());
        assertEquals("ARCHIVED", query.getValue().status());
    }

    @Test
    void returnsMetadataWithoutTransferringFrozenJson() throws Exception {
        var service = mock(StrategyVersionListService.class);
        var time = Instant.parse("2026-10-01T00:00:00Z");
        var row =
                new StrategyVersionListItem(
                        "v1",
                        "parent",
                        1,
                        "name",
                        StrategyVersionStatus.DRAFT,
                        "digest",
                        "operator",
                        time,
                        time);
        when(service.list(any())).thenReturn(new StrategyVersionPage(List.of(row), 1, 20, 0));
        var mvc =
                MockMvcBuilders.standaloneSetup(new StrategyVersionQueryController(service))
                        .build();
        mvc.perform(get("/api/strategies/parent/versions/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].strategyCode").value("parent"))
                .andExpect(jsonPath("$.items[0].checksum").value("digest"))
                .andExpect(jsonPath("$.items[0].paramSnapshotJson").doesNotExist())
                .andExpect(jsonPath("$.items[0].configSnapshotJson").doesNotExist())
                .andExpect(jsonPath("$.items[0].sourceSnapshotJson").doesNotExist());
    }

    @Test
    void rejectsBadFiltersAndOverflowBeforeInvokingTheReader() throws Exception {
        var service = mock(StrategyVersionListService.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(new StrategyVersionQueryController(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        for (String[] invalid :
                new String[][] {
                    {"version", "0"},
                    {"version", "2147483648"},
                    {"version", "1.5"},
                    {"status", "RUNNING"},
                    {"limit", "0"},
                    {"limit", "101"},
                    {"offset", "-1"},
                    {"offset", "2147483648"},
                    {"versionName", "a\t"},
                    {"strategyVersionId", "x".repeat(129)}
                })
            mvc.perform(get("/api/strategies/parent/versions/page").param(invalid[0], invalid[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").exists());
        verifyNoInteractions(service);
    }
}
