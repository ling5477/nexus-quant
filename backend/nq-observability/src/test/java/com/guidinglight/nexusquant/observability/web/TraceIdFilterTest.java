package com.guidinglight.nexusquant.observability.web;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TraceIdFilterTest 验证正式 trace header 的解析、生成与上下文写入行为。
 */
class TraceIdFilterTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TraceEchoController())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    void shouldPassThroughStandardTraceHeader() throws Exception {
        mockMvc.perform(get("/trace")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-standard-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-standard-1"))
                .andExpect(content().string("traceId=trc-standard-1;requestTraceId=trc-standard-1"));
    }

    @Test
    void shouldGenerateTraceIdWhenHeaderMissing() throws Exception {
        mockMvc.perform(get("/trace"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, startsWith("trc-")))
                .andExpect(content().string(startsWith("traceId=trc-")));
    }

    @Test
    void shouldAcceptLegacyHeaderButRespondWithStandardHeader() throws Exception {
        mockMvc.perform(get("/trace")
                        .header(TraceIdContext.LEGACY_TRACE_ID_HEADER, "trc-legacy-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-legacy-1"))
                .andExpect(content().string("traceId=trc-legacy-1;requestTraceId=trc-legacy-1"));
    }

    @RestController
    private static class TraceEchoController {
        @GetMapping("/trace")
        String trace(HttpServletRequest request) {
            return "traceId=" + TraceIdContext.getOrCreate()
                    + ";requestTraceId=" + request.getAttribute(TraceIdContext.TRACE_ID_REQUEST_ATTRIBUTE);
        }
    }
}
