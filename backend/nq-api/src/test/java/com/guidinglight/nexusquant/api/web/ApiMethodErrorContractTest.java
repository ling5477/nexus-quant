package com.guidinglight.nexusquant.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicInteger;

/** 用只读夹具证明错误HTTP方法返回405且不执行handler；既有业务冲突仍保持409合同。 */
class ApiMethodErrorContractTest {
    @RestController
    static class ReadOnlyFixture {
        final AtomicInteger calls = new AtomicInteger();

        @GetMapping("/api/readonly-fixture")
        String read() {
            calls.incrementAndGet();
            return "read";
        }

        @GetMapping("/api/conflict-fixture")
        String conflict() {
            throw new IllegalStateException("fixture conflict");
        }
    }

    @Test
    void unsupportedMutationReturns405AndNeverInvokesReadHandler() throws Exception {
        var controller = new ReadOnlyFixture();
        var mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        mvc.perform(post("/api/readonly-fixture"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.path").value("/api/readonly-fixture"));
        assertEquals(0, controller.calls.get());
        mvc.perform(get("/api/readonly-fixture"))
                .andExpect(status().isOk())
                .andExpect(content().string("read"));
        assertEquals(1, controller.calls.get());
    }

    @Test
    void businessConflictKeepsItsExistingContract() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(new ReadOnlyFixture())
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        mvc.perform(get("/api/conflict-fixture"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
    }
}
