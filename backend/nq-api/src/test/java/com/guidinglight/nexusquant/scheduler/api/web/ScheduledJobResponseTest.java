package com.guidinglight.nexusquant.scheduler.api.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobControl;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobManagementService;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

/** 约束响应必须与同一任务目录绑定，保留原JSON字段，且只读序列化不得调用执行资格或任务动作。 */
class ScheduledJobResponseTest {
    @Test
    void listAndDetailRetainFlatControlFieldsAndRegistryBounds() throws Exception {
        var management = mock(ScheduledJobManagementService.class);
        var registry = mock(ScheduledJobRegistry.class);
        var control =
                new ScheduledJobControl(
                        "CONTINUOUS_SIM_POLL",
                        false,
                        300000,
                        null,
                        null,
                        null,
                        "NEVER_RUN",
                        null,
                        0,
                        7,
                        null,
                        "test-operator",
                        Instant.parse("2026-10-14T00:00:00Z"));
        when(management.list()).thenReturn(List.of(control));
        when(management.detail(control.jobKey())).thenReturn(control);
        when(registry.require(control.jobKey()))
                .thenReturn(
                        new ScheduledJobRegistry.Job(
                                control.jobKey(),
                                300000,
                                30000,
                                86400000,
                                () -> {
                                    throw new AssertionError("METADATA_MUST_NOT_CHECK_ELIGIBILITY");
                                },
                                () -> {
                                    throw new AssertionError("METADATA_MUST_NOT_EXECUTE_JOB");
                                }));
        var mvc =
                MockMvcBuilders.standaloneSetup(
                                new ScheduledJobController(
                                        management, mock(GatewayAuthFacade.class), registry))
                        .build();
        mvc.perform(get("/api/scheduler/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobKey").value(control.jobKey()))
                .andExpect(jsonPath("$[0].fixedDelayMs").value(300000))
                .andExpect(jsonPath("$[0].version").value(7))
                .andExpect(jsonPath("$[0].enabled").value(false))
                .andExpect(jsonPath("$[0].lastStatus").value("NEVER_RUN"))
                .andExpect(jsonPath("$[0].updatedBy").value("test-operator"))
                .andExpect(jsonPath("$[0].control").doesNotExist())
                .andExpect(jsonPath("$[0].minFixedDelayMs").value(30000))
                .andExpect(jsonPath("$[0].maxFixedDelayMs").value(86400000));
        mvc.perform(get("/api/scheduler/jobs/CONTINUOUS_SIM_POLL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobKey").value(control.jobKey()))
                .andExpect(jsonPath("$.version").value(7))
                .andExpect(jsonPath("$.minFixedDelayMs").value(30000))
                .andExpect(jsonPath("$.maxFixedDelayMs").value(86400000));
    }
}
