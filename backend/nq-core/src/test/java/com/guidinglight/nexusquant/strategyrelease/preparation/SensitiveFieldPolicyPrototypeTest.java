package com.guidinglight.nexusquant.strategyrelease.preparation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

import org.junit.jupiter.api.Test;

/** PRE-GATEX manifest credential-like 字段名递归拒绝原型。 */
class SensitiveFieldPolicyPrototypeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldRejectEveryRequiredCredentialLikeFieldAndCaseOrUnderscoreVariant() {
        List<String> forbiddenFields = List.of(
                "apiKey",
                "api_key",
                "API_KEY",
                "secret",
                "Secret",
                "passphrase",
                "token",
                "accessToken",
                "access_token",
                "privateKey",
                "private_key",
                "credentialMaterial",
                "credential_material",
                "decryptedPayload",
                "decrypted_payload",
                "rawPrivateRequest",
                "raw_private_request",
                "rawPrivateResponse",
                "raw_private_response",
                "cookie",
                "authorization"
        );

        for (String forbiddenField : forbiddenFields) {
            ObjectNode root = MAPPER.createObjectNode();
            root.putObject("parameters").put(forbiddenField, "synthetic-redacted-value");

            assertFalse(
                    SensitiveFieldPolicy.findForbiddenFieldPaths(root).isEmpty(),
                    "field must be rejected: " + forbiddenField
            );
        }
    }

    @Test
    void shouldRejectSensitiveFieldNamesRecursivelyInsideArraysAndObjects() {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode items = root.putArray("items");
        items.addObject().putObject("nested").put("operatorToken", "synthetic-redacted-value");

        List<String> paths = SensitiveFieldPolicy.findForbiddenFieldPaths(root);

        assertTrue(paths.contains("$.items[0].nested.operatorToken"));
    }

    @Test
    void shouldAllowOnlyTheExplicitSafeBoundaryFields() throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode boundary = root.putObject("boundary");
        boundary.put("noCredentialAccess", true);
        boundary.put("noPrivateEndpoint", true);
        boundary.put("diagnosticOnly", true);
        boundary.put("notTradingAuthorization", true);

        assertTrue(SensitiveFieldPolicy.findForbiddenFieldPaths(root).isEmpty());

        JsonNode golden = ManifestPrototypeContract.readResource("gatex/strategy-release-manifest.golden.json");
        assertTrue(SensitiveFieldPolicy.findForbiddenFieldPaths(golden).isEmpty());
        assertTrue(ManifestPrototypeContract.validate(golden).isEmpty());
    }
}
