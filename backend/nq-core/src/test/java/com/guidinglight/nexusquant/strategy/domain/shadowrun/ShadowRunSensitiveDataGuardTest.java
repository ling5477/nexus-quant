package com.guidinglight.nexusquant.strategy.domain.shadowrun;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class ShadowRunSensitiveDataGuardTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAllowNoSideEffectBoundaryFields() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "noCredentialAccess": true,
                  "noPrivateEndpoint": true,
                  "noOrderSubmission": true,
                  "authorizationBoundary": "DIAGNOSTIC_ONLY",
                  "diagnosticOnly": true
                }
                """);

        assertDoesNotThrow(() -> ShadowRunSensitiveDataGuard.validateJson("payload", payload));
    }

    @Test
    void shouldRejectCredentialLikeJsonPayload() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "input": {
                    "apiKey": "redacted"
                  }
                }
                """);

        assertThrows(IllegalArgumentException.class,
                () -> ShadowRunSensitiveDataGuard.validateJson("payload", payload));
    }

    @Test
    void shouldKeepDomainModelFieldNamesAwayFromForbiddenSensitiveFields() {
        List<Class<? extends Record>> models = List.of(
                ShadowRun.class,
                ShadowRunEvent.class,
                ShadowRunSnapshot.class,
                ShadowConsistencyReport.class
        );

        for (Class<? extends Record> model : models) {
            Arrays.stream(model.getRecordComponents())
                    .map(component -> component.getName())
                    .forEach(name -> assertFalse(
                            ShadowRunSensitiveDataGuard.isForbiddenFieldName(name),
                            model.getSimpleName() + " contains forbidden field name: " + name
                    ));
        }
    }
}
