package com.guidinglight.nexusquant.app.research;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.common.tenant.TenantId;
import com.guidinglight.nexusquant.common.tenant.TenantKind;

/** 格式错误保持明确4xx，不从通用500路径掩盖本地文件问题。 */
class FactorResearchControllerTest {
    @Test
    void malformedDuplicateTrailingAndOversizedBodiesFailClosed() throws Exception {
        var service = mock(FactorResearchService.class);
        var controller = new FactorResearchController(service, new ObjectMapper());
        var identity = new TenantIdentity(TenantId.LOCAL, "NQ_LOCAL", TenantKind.LOCAL, 1L);
        try (var ignored = TenantContext.open(identity)) {
            for (String body : java.util.List.of("{", "{\"a\":1,\"a\":2}", "{} {}")) {
                var request = new MockHttpServletRequest(); request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var error = assertThrows(ResponseStatusException.class, () -> controller.preview(UUID.randomUUID(), request));
                assertEquals(400, error.getStatusCode().value());
            }
            var huge = new MockHttpServletRequest(); huge.setContent(new byte[FactorResearchService.MAX_ARTIFACT_BYTES + 1]);
            assertEquals(413, assertThrows(ResponseStatusException.class, () -> controller.preview(UUID.randomUUID(), huge)).getStatusCode().value());
            when(service.preview(any(), any())).thenThrow(new java.time.DateTimeException("invalid"));
            var invalidTime = new MockHttpServletRequest(); invalidTime.setContent("{}".getBytes());
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> controller.preview(UUID.randomUUID(), invalidTime)).getStatusCode().value());
        }
    }
}
