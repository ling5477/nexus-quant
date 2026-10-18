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
import com.guidinglight.nexusquant.strategy.application.StrategyRunListService;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunPage;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunListQuery;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class StrategyRunListControllerTest {
    @Test
    void bindsAnExactScheduleAndServerFiltersWithTheRequestedWindow() throws Exception {
        var service = mock(StrategyRunListService.class);
        when(service.list(any())).thenReturn(new StrategyRunPage(List.of(), 0, 50, 100));
        var mvc = MockMvcBuilders.standaloneSetup(new StrategyRunListController(service)).build();
        mvc.perform(
                        get("/api/strategy-runs/page")
                                .param("scheduleId", " sch-%_! ")
                                .param("status", "failed")
                                .param("triggerType", "scheduler")
                                .param("limit", "50")
                                .param("offset", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.limit").value(50))
                .andExpect(jsonPath("$.offset").value(100));
        var query = ArgumentCaptor.forClass(StrategyRunListQuery.class);
        verify(service).list(query.capture());
        assertEquals("sch-%_!", query.getValue().scheduleId());
        assertEquals("FAILED", query.getValue().status());
        assertEquals("SCHEDULER", query.getValue().triggerType());
    }

    @Test
    void rejectsMissingOrCombinedScopesBeforeReadingAnything() throws Exception {
        var service = mock(StrategyRunListService.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(new StrategyRunListController(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        mvc.perform(get("/api/strategy-runs/page")).andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/strategy-runs/page")
                                .param("strategyId", "str")
                                .param("scheduleId", "sch"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsBadEnumsControlsAndOverflowWithoutCallingTheReader() throws Exception {
        var service = mock(StrategyRunListService.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(new StrategyRunListController(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        for (String[] invalid :
                new String[][] {
                    {"limit", "0"},
                    {"limit", "101"},
                    {"offset", "-1"},
                    {"offset", "2147483648"},
                    {"limit", "1.2"},
                    {"status", "STOPPED"},
                    {"triggerType", "LIVE"},
                    {"status", "failed\t"},
                    {"strategyId", "x".repeat(129)}
                }) {
            var request = get("/api/strategy-runs/page").param("strategyId", "str");
            request.queryParam(invalid[0], invalid[1]);
            // 同名strategyId由单独请求替换，不能依靠重复参数的解析顺序通过验证。
            if (invalid[0].equals("strategyId"))
                request = get("/api/strategy-runs/page").param(invalid[0], invalid[1]);
            mvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").exists());
        }
        verifyNoInteractions(service);
    }
}
