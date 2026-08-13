package com.guidinglight.nexusquant.research.api.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.research.application.eval.api.BacktestRunApiService;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * EvaluationControllerTest 固化评估列表的配置范围，防止 query 参数被 API 层忽略后跨配置串数据。
 */
class EvaluationControllerTest {

    private MockMvc mockMvc;
    private BacktestRunApiService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = mock(BacktestRunApiService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new EvaluationController(applicationService)).build();
    }

    @Test
    void shouldForwardEvaluationFilters() throws Exception {
        when(applicationService.listEvaluations("rcf-1", "bcf-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/evaluations")
                        .queryParam("researchConfigId", "rcf-1")
                        .queryParam("backtestConfigId", "bcf-1"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(applicationService).listEvaluations("rcf-1", "bcf-1");
    }
}
