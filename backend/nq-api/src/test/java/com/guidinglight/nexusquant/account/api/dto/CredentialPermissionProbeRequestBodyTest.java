package com.guidinglight.nexusquant.account.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CredentialPermissionProbeRequestBodyTest {
    @Test
    void openApiExposesOnlyStablePermissionModes() throws Exception {
        Schema schema = CredentialPermissionProbeRequestBody.class.getDeclaredField("mode")
                .getAnnotation(Schema.class);
        assertArrayEquals(new String[]{"PAPER", "READ_ONLY_DIAGNOSTIC", "SCOPED_TRADE_READINESS"},
                schema.allowableValues());
    }
}
