package com.guidinglight.nexusquant.integration.dh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * DhDryRunRequestGenerationTest 覆盖 signed request、headers、UTC Z timestamp、nonce 和禁止字段。
 */
class DhDryRunRequestGenerationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generatesCanonicalSignedDryRunRequestWithoutLegacyHeaders() throws Exception {
        DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();
        DhDryRunRuntimeClient client = DhDryRunTestSupport.enabledClient(transport, recorder);

        DhDryRunClientResult result = client.execute(DhDryRunTestSupport.command());

        assertTrue(result.accepted());
        assertEquals(1, transport.callCount());
        DhDryRunTransportRequest request = transport.lastRequest();
        JsonNode body = objectMapper.readTree(request.body());

        assertEquals(true, body.get("dryRun").asBoolean());
        assertEquals("NQ_DRYRUN", body.get("source").asText());
        assertEquals("1.0.0", body.get("schemaVersion").asText());
        assertEquals("2026-07-05T01:02:03Z", body.get("timestamp").asText());
        assertFalse(body.get("timestamp").asText().matches("\\d{10}"));
        assertFalse(body.get("timestamp").asText().matches("\\d{13}"));
        assertTrue(body.get("timestamp").asText().endsWith("Z"));
        assertTrue(body.get("forbiddenCapabilities").toString().contains("PLACE_ORDER"));
        assertTrue(body.get("forbiddenCapabilities").toString().contains("CANCEL_ORDER"));
        assertTrue(body.get("forbiddenCapabilities").toString().contains("MUTATE_NQ_STATE"));
        assertTrue(body.get("forbiddenCapabilities").toString().contains("FORWARD_CREDENTIAL"));
        assertNoCredentialOrExecutableFields(body);

        Map<String, String> headers = request.headers();
        for (String headerName : DhDryRunHeaderNames.all()) {
            assertTrue(headers.containsKey(headerName), headerName);
        }
        assertTrue(headers.keySet().stream().noneMatch(name -> name.startsWith("X-DH-NQ-")));
        assertEquals("nq-i1-req-001", headers.get(DhDryRunHeaderNames.REQUEST_ID));
        assertEquals("nq-i1-trace-001", headers.get(DhDryRunHeaderNames.TRACE_ID));
        assertEquals("tenant-i1", headers.get(DhDryRunHeaderNames.TENANT_ID));
        assertEquals("NQ_DRYRUN", headers.get(DhDryRunHeaderNames.SOURCE));
        assertEquals("1.0.0", headers.get(DhDryRunHeaderNames.SCHEMA_VERSION));
        assertFalse(headers.get(DhDryRunHeaderNames.SIGNATURE).isBlank());
    }

    @Test
    void nonceIsUniqueAcrossRequests() {
        DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();
        DhDryRunRuntimeClient client = DhDryRunTestSupport.enabledClient(transport, recorder);

        client.execute(DhDryRunTestSupport.command());
        String firstNonce = transport.lastRequest().headers().get(DhDryRunHeaderNames.NONCE);
        client.execute(DhDryRunTestSupport.command());
        String secondNonce = transport.lastRequest().headers().get(DhDryRunHeaderNames.NONCE);

        assertNotEquals(firstNonce, secondNonce);
        assertEquals(2, transport.callCount());
    }

    @Test
    void signatureMaterialBindsValuesButNotHeaderNames() throws Exception {
        DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();
        DhDryRunRuntimeClient client = DhDryRunTestSupport.enabledClient(transport, recorder);

        client.execute(DhDryRunTestSupport.command());

        DhDryRunTransportRequest request = transport.lastRequest();
        String material = DhDryRunSigning.signatureMaterial(
                DhDryRunRuntimeClient.DECISION_DRY_RUNS_PATH,
                request.headers().get(DhDryRunHeaderNames.SOURCE),
                request.headers().get(DhDryRunHeaderNames.TENANT_ID),
                request.headers().get(DhDryRunHeaderNames.REQUEST_ID),
                request.headers().get(DhDryRunHeaderNames.TRACE_ID),
                request.headers().get(DhDryRunHeaderNames.TIMESTAMP),
                request.headers().get(DhDryRunHeaderNames.NONCE),
                request.headers().get(DhDryRunHeaderNames.SCHEMA_VERSION),
                request.body());
        String expectedSignature = DhDryRunSigning.hmacSha256Hex(DhDryRunTestSupport.SIGNING_KEY, material);

        assertEquals(expectedSignature, request.headers().get(DhDryRunHeaderNames.SIGNATURE));
        assertTrue(material.contains("NQ_DRYRUN"));
        assertFalse(material.contains("nq_dryrun"));
        assertTrue(material.contains("tenant-i1"));
        assertTrue(material.contains("nq-i1-req-001"));
        assertTrue(material.contains("nq-i1-trace-001"));
        assertFalse(material.contains(DhDryRunHeaderNames.REQUEST_ID));
        assertFalse(material.contains(DhDryRunHeaderNames.SIGNATURE));
    }

    private static void assertNoCredentialOrExecutableFields(JsonNode body) {
        var fieldNames = body.fieldNames();
        while (fieldNames.hasNext()) {
            String normalizedFieldName = fieldNames.next().toLowerCase();
            assertFalse(normalizedFieldName.contains("credential"));
            assertFalse(normalizedFieldName.contains("apikey"));
            assertFalse(normalizedFieldName.contains("apisecret"));
            assertFalse(normalizedFieldName.contains("passphrase"));
            assertFalse(normalizedFieldName.contains("accountsecret"));
        }
        String decisionContext = body.get("decisionContext").toString().toLowerCase();
        for (String token : new String[] {
            "apikey",
            "api_key",
            "apisecret",
            "api_secret",
            "passphrase",
            "credential",
            "accountsecret",
            "account_secret",
            "buy",
            "sell",
            "executableorder",
            "quantity",
            "leverage",
            "orderprice",
            "order_price"
        }) {
            assertFalse(decisionContext.contains(token), token);
        }
    }
}
