package com.guidinglight.nexusquant.integration.dh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * DhDryRunResponseHandlingTest 覆盖 response envelope validation 与 fail-closed taxonomy。
 */
class DhDryRunResponseHandlingTest {

    @Test
    void validObserveAndNoTradeAreAcceptedAsRecordOnly() {
        assertAcceptedRecordOnly("OBSERVE", false);
        assertAcceptedRecordOnly("NO_TRADE", false);
    }

    @Test
    void longAndShortBiasAreAcceptedAsBiasOnly() {
        assertAcceptedRecordOnly("LONG_BIAS", true);
        assertAcceptedRecordOnly("SHORT_BIAS", true);
    }

    @Test
    void forbiddenTradingActionsAreRejected() {
        assertPolicyViolation(DhDryRunTestSupport.validResponse("BUY"));
        assertPolicyViolation(DhDryRunTestSupport.validResponse("SELL"));
        assertPolicyViolation(DhDryRunTestSupport.validResponse("PLACE_ORDER"));
        assertPolicyViolation(DhDryRunTestSupport.validResponse("CANCEL_ORDER"));
    }

    @Test
    void executableQuantityIsRejected() {
        assertPolicyViolation(
                """
                {
                  "decisionId": "dh-dec-001",
                  "dryRun": true,
                  "action": "OBSERVE",
                  "confidence": 0.72,
                  "riskLevel": "LOW",
                  "reasons": ["READ_ONLY_DRY_RUN"],
                  "traceSummary": "trace-summary",
                  "replayRef": "replay-001",
                  "auditRef": "audit-001",
                  "schemaVersion": "%s",
                  "quantity": "1"
                }
                """
                        .formatted(DhDryRunRuntimeProperties.DEFAULT_SCHEMA_VERSION));
    }

    @Test
    void dryRunFalseMissingDecisionIdAndInvalidSchemaAreRejected() {
        assertPolicyViolation(DhDryRunTestSupport.validResponse("OBSERVE").replace("\"dryRun\": true", "\"dryRun\": false"));
        assertPolicyViolation(DhDryRunTestSupport.validResponse("OBSERVE").replace("\"dh-dec-001\"", "\"\""));
        assertPolicyViolation(DhDryRunTestSupport.validResponse("OBSERVE")
                .replace(DhDryRunRuntimeProperties.DEFAULT_SCHEMA_VERSION, "wrong-schema"));
    }

    @Test
    void errorEnvelopeMapsToFailClosedCode() {
        DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        transport.responseBody(
                """
                {
                  "dryRun": true,
                  "schemaVersion": "%s",
                  "error": {"code": "PROVIDER_TIMEOUT", "message": "provider timeout"}
                }
                """
                        .formatted(DhDryRunRuntimeProperties.DEFAULT_SCHEMA_VERSION));
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();

        DhDryRunClientResult result =
                DhDryRunTestSupport.enabledClient(transport, recorder).execute(DhDryRunTestSupport.command());

        assertTrue(result.failClosed());
        assertEquals(DhDryRunErrorCode.PROVIDER_TIMEOUT, result.record().errorCode());
        assertEquals("dh_error_envelope", result.record().failClosedReason());
        assertEquals(1, recorder.records().size());
    }

    @Test
    void timeoutAndParseFailureFailClosed() {
        DhDryRunTestSupport.FakeDhDryRunTransport timeoutTransport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        timeoutTransport.timeout(true);
        InMemoryDhDryRunRecorder timeoutRecorder = new InMemoryDhDryRunRecorder();
        DhDryRunClientResult timeoutResult =
                DhDryRunTestSupport.enabledClient(timeoutTransport, timeoutRecorder).execute(DhDryRunTestSupport.command());
        assertEquals(DhDryRunErrorCode.CLIENT_TIMEOUT, timeoutResult.record().errorCode());

        DhDryRunTestSupport.FakeDhDryRunTransport parseTransport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        parseTransport.responseBody("{not-json");
        InMemoryDhDryRunRecorder parseRecorder = new InMemoryDhDryRunRecorder();
        DhDryRunClientResult parseResult =
                DhDryRunTestSupport.enabledClient(parseTransport, parseRecorder).execute(DhDryRunTestSupport.command());
        assertEquals(DhDryRunErrorCode.CLIENT_PARSE_ERROR, parseResult.record().errorCode());
    }

    private static void assertAcceptedRecordOnly(String action, boolean biasOnly) {
        DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        transport.responseBody(DhDryRunTestSupport.validResponse(action));
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();

        DhDryRunClientResult result =
                DhDryRunTestSupport.enabledClient(transport, recorder).execute(DhDryRunTestSupport.command());

        assertTrue(result.accepted());
        assertFalse(result.failClosed());
        assertEquals(DhDryRunAction.valueOf(action), result.record().action());
        assertEquals(biasOnly, result.record().biasOnly());
        assertEquals(1, recorder.records().size());
    }

    private static void assertPolicyViolation(String responseBody) {
        DhDryRunTestSupport.FakeDhDryRunTransport transport = new DhDryRunTestSupport.FakeDhDryRunTransport();
        transport.responseBody(responseBody);
        InMemoryDhDryRunRecorder recorder = new InMemoryDhDryRunRecorder();

        DhDryRunClientResult result =
                DhDryRunTestSupport.enabledClient(transport, recorder).execute(DhDryRunTestSupport.command());

        assertTrue(result.failClosed());
        assertEquals(DhDryRunErrorCode.RESPONSE_POLICY_VIOLATION, result.record().errorCode());
        assertEquals(1, recorder.records().size());
    }
}
