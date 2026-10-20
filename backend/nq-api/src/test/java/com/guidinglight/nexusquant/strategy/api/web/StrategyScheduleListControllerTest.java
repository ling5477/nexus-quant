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
import com.guidinglight.nexusquant.strategy.application.StrategyScheduleListService;
import com.guidinglight.nexusquant.strategy.domain.StrategySchedulePage;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyScheduleListQuery;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

/** 通过真实MVC参数绑定验证窗口与筛选，非法值必须在查询端口前拒绝。 */
class StrategyScheduleListControllerTest {
    @Test
    void readsOnlyTheRequestedPage() throws Exception {
        var service = mock(StrategyScheduleListService.class);
        when(service.list(any()))
                .thenAnswer(
                        call -> {
                            var q = call.getArgument(0, StrategyScheduleListQuery.class);
                            return new StrategySchedulePage(List.of(), 0, q.limit(), q.offset());
                        });
        var mvc =
                MockMvcBuilders.standaloneSetup(new StrategyScheduleListController(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        mvc.perform(
                        get("/api/strategy-schedules/page")
                                .param("strategyId", "str")
                                .param("scheduleId", "sch-%_!")
                                .param("status", "disabled")
                                .param("scheduleType", "manual")
                                .param("limit", "50")
                                .param("offset", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.limit").value(50))
                .andExpect(jsonPath("$.offset").value(100));
        var q = ArgumentCaptor.forClass(StrategyScheduleListQuery.class);
        verify(service).list(q.capture());
        assertEquals("DISABLED", q.getValue().status());
        assertEquals("MANUAL", q.getValue().scheduleType());
        assertEquals("sch-%_!", q.getValue().scheduleId());
    }

    @Test
    void rejectsInvalidParametersWithoutReading() throws Exception {
        var service = mock(StrategyScheduleListService.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(new StrategyScheduleListController(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        mvc.perform(get("/api/strategy-schedules/page")).andExpect(status().isBadRequest());
        for (String[] invalid :
                new String[][] {
                    {"strategyId", " "},
                    {"status", "RUNNING"},
                    {"scheduleType", "UNKNOWN"},
                    {"limit", "0"},
                    {"limit", "101"},
                    {"limit", "1.2"},
                    {"offset", "-1"},
                    {"offset", "2147483648"},
                    {"scheduleId", "x".repeat(129)}
                }) {
            var request = get("/api/strategy-schedules/page");
            if (!invalid[0].equals("strategyId")) request.param("strategyId", "str");
            request.param(invalid[0], invalid[1]);
            mvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").exists());
        }
        verifyNoInteractions(service);
    }
}
