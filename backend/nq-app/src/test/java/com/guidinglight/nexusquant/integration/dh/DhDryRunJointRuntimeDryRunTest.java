package com.guidinglight.nexusquant.integration.dh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * DhDryRunJointRuntimeDryRunTest 固化 NQ -> fake transport -> DH-style validator -> NQ record-only
 * 的 joint dry-run 测试证据。
 *
 * <p>Why: 本轮禁止真实 DH 调用和真实 HTTP，因此这里用 in-memory transport 模拟 DH MockMvc 前的
 * HMAC / schema gate。测试不得绕过生产 NQ client 行为；如果 source wire value 或 schemaVersion 再次漂移，
 * 必须 fail-closed 并把该差异作为 close review 前阻断项暴露。
 */
class DhDryRunJointRuntimeDryRunTest {

    @Test
    void nqSignedRequestPassesDhStyleVerifierWithWireLevelSourceMaterial() {
        DhStyleValidatingTransport transport = new DhStyleValidatingTransport();
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();

        DhDryRunClientResult result = DhDryRunTestSupport.client(
                        DhDryRunRuntimeProperties.enabledForTest(
                                DhDryRunTestSupport.ENDPOINT, DhDryRunTestSupport.SIGNING_KEY),
                        transport,
                        recorder)
                .execute(DhDryRunTestSupport.command());

        assertTrue(result.accepted());
        assertFalse(result.failClosed());
        assertEquals(1, transport.callCount());
        assertEquals(transport.expectedDhSignature, transport.actualNqSignature);
        assertTrue(transport.dhSignatureMaterial.contains("NQ_DRYRUN"));
        assertFalse(transport.dhSignatureMaterial.contains("nq_dryrun"));
        assertFalse(transport.dhSignatureMaterial.contains("X-NQ-DH-"));
        assertEquals(DhDryRunAction.OBSERVE, result.record().action());
        assertFalse(result.record().failClosed());
        assertEquals(1, recorder.records().size());
    }

    @Test
    void dhSchemaVersionResponseIsAcceptedAsCanonicalRuntimeTestSchema() {
        DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        transport.responseBody(DhDryRunTestSupport.validResponse("OBSERVE"));
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();

        DhDryRunClientResult result =
                DhDryRunTestSupport.enabledClient(transport, recorder).execute(DhDryRunTestSupport.command());

        assertTrue(result.accepted());
        assertFalse(result.failClosed());
        assertEquals("nq-i1-req-001", result.record().requestId());
        assertEquals("nq-i1-trace-001", result.record().traceId());
        assertEquals("tenant-i1", result.record().tenantId());
        assertEquals("dh-dec-001", result.record().decisionId());
        assertEquals("audit-001", result.record().auditRef());
        assertEquals(DhDryRunAction.OBSERVE, result.record().action());
        assertEquals(1, recorder.records().size());
    }

    @Test
    void acceptedReadonlyEnvelopeRecordsTraceTenantDecisionAuditWithoutSecretOrExecutablePayload() {
        DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        transport.responseBody(DhDryRunTestSupport.validResponse("LONG_BIAS"));
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();

        DhDryRunClientResult result =
                DhDryRunTestSupport.enabledClient(transport, recorder).execute(DhDryRunTestSupport.command());

        assertTrue(result.accepted());
        assertFalse(result.failClosed());
        assertEquals("nq-i1-req-001", result.record().requestId());
        assertEquals("nq-i1-trace-001", result.record().traceId());
        assertEquals("tenant-i1", result.record().tenantId());
        assertEquals("dh-dec-001", result.record().decisionId());
        assertEquals("audit-001", result.record().auditRef());
        assertEquals(DhDryRunAction.LONG_BIAS, result.record().action());
        assertTrue(result.record().biasOnly());
        assertRecordDoesNotExposeSecretOrExecutablePayload(result.record());
        assertEquals(1, recorder.records().size());
    }

    @Test
    void everyCanonicalErrorCodeFromDhEnvelopeFailsClosedAndNeverBecomesTradingSignal() {
        for (DhDryRunErrorCode errorCode : DhDryRunErrorCode.values()) {
            DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
            transport.responseBody(errorEnvelope(errorCode.name()));
            InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();

            DhDryRunClientResult result =
                    DhDryRunTestSupport.enabledClient(transport, recorder).execute(DhDryRunTestSupport.command());

            assertTrue(result.failClosed(), errorCode.name());
            assertFalse(result.accepted(), errorCode.name());
            assertEquals(errorCode, result.record().errorCode(), errorCode.name());
            assertNull(result.record().action(), errorCode.name());
            assertEquals("dh_error_envelope", result.record().failClosedReason(), errorCode.name());
            assertEquals(1, recorder.records().size(), errorCode.name());
        }

        DhDryRunTestSupport.FakeDhDryRunTransport unknownTransport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        unknownTransport.responseBody(errorEnvelope("NEW_UNKNOWN_DH_ERROR"));
        InMemoryDhDryRunRecorder unknownRecorder = new InMemoryDhDryRunRecorder();

        DhDryRunClientResult unknown =
                DhDryRunTestSupport.enabledClient(unknownTransport, unknownRecorder)
                        .execute(DhDryRunTestSupport.command());

        assertTrue(unknown.failClosed());
        assertEquals(DhDryRunErrorCode.UNKNOWN_ERROR, unknown.record().errorCode());
        assertNull(unknown.record().action());
    }

    private static String errorEnvelope(String code) {
        return """
                {
                  "dryRun": true,
                  "schemaVersion": "%s",
                  "error": {"code": "%s", "message": "fail closed"}
                }
                """
                .formatted(DhDryRunRuntimeProperties.DEFAULT_SCHEMA_VERSION, code);
    }

    private static void assertRecordDoesNotExposeSecretOrExecutablePayload(DhDryRunRecord record) {
        String text = record.toString().toLowerCase(Locale.ROOT);
        for (String token : new String[] {
            "test-only-signing-key",
            "signature",
            "apikey",
            "api_secret",
            "apisecret",
            "passphrase",
            "credential",
            "cookie",
            "token",
            "raw",
            "buy",
            "sell",
            "place_order",
            "cancel_order",
            "executableorder",
            "quantity",
            "leverage",
            "orderprice"
        }) {
            assertFalse(text.contains(token), token);
        }
    }

    /**
     * DhStyleValidatingTransport 只在内存中复刻 DH HMAC wire-level source 行为。
     *
     * <p>Why: 这里不是 DH client，也不会访问 localhost；它只证明当前 NQ 生产签名材料可以通过 DH
     * value-based verifier，并验证后续 response validation 仍由 NQ record-only client 完成。
     */
    private static final class DhStyleValidatingTransport implements DhDryRunTransport {
        private int callCount;
        private String expectedDhSignature;
        private String actualNqSignature;
        private String dhSignatureMaterial;

        @Override
        public DhDryRunTransportResponse send(DhDryRunTransportRequest request) {
            callCount++;
            Map<String, String> headers = request.headers();
            dhSignatureMaterial = String.join(
                    "\n",
                    "POST",
                    DhDryRunRuntimeClient.DECISION_DRY_RUNS_PATH,
                    headers.get(DhDryRunHeaderNames.SOURCE),
                    headers.get(DhDryRunHeaderNames.TENANT_ID),
                    headers.get(DhDryRunHeaderNames.REQUEST_ID),
                    headers.get(DhDryRunHeaderNames.TRACE_ID),
                    headers.get(DhDryRunHeaderNames.TIMESTAMP),
                    headers.get(DhDryRunHeaderNames.NONCE),
                    headers.get(DhDryRunHeaderNames.SCHEMA_VERSION),
                    DhDryRunSigning.sha256Hex(request.body()));
            expectedDhSignature =
                    DhDryRunSigning.hmacSha256Hex(DhDryRunTestSupport.SIGNING_KEY, dhSignatureMaterial);
            actualNqSignature = headers.get(DhDryRunHeaderNames.SIGNATURE);
            if (expectedDhSignature.equals(actualNqSignature)) {
                return new DhDryRunTransportResponse(200, DhDryRunTestSupport.validResponse("OBSERVE"));
            }
            return new DhDryRunTransportResponse(401, errorEnvelope(DhDryRunErrorCode.SIGNATURE_INVALID.name()));
        }

        private int callCount() {
            return callCount;
        }
    }
}
