package com.guidinglight.nexusquant.app.integration1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * IMP2 NQ-side test-support stub / recorder / no-side-effect guard。
 *
 * <p>本测试只在 NQ dry-run worktree 的 test scope 内描述 future request builder 与 readonly
 * recorder 的安全形状：不实现生产 client、不创建 HTTP endpoint、不接 DH runtime、不读取 credential、不触发 order / risk /
 * ledger / paper / live 边界。
 */
class NqDhIntegration1StubRecorderNoSideEffectTest {

    private static final Set<String> READONLY_ACTIONS =
            Set.of("ABSTAIN", "OBSERVE", "NO_TRADE", "LONG_BIAS", "SHORT_BIAS");

    private static final Set<String> FORBIDDEN_REQUEST_TOKENS =
            Set.of(
                    "accountid",
                    "orderid",
                    "clientorderid",
                    "positionid",
                    "credential",
                    "token",
                    "apikey",
                    "apisecret",
                    "passphrase",
                    "buy",
                    "sell",
                    "quantity",
                    "price",
                    "leverage",
                    "placeorder",
                    "cancelorder",
                    "mutaterisk",
                    "mutateledger",
                    "paperrunstart",
                    "liverunstart",
                    "realurl",
                    "endpoint",
                    "httpclient");

    private static final Set<String> SENSITIVE_SUMMARY_TOKENS =
            Set.of("credential", "token", "apikey", "api_key", "apisecret", "passphrase", "secret", "raw");

    @Test
    void requestBuilderAllowsOnlyReadonlyDryRunShape() {
        final DryRunRequest request = DryRunRequestBuilder.build(validInput());

        assertEquals("req-i1-imp2-001", request.requestId());
        assertEquals("trace-i1-imp2-001", request.traceId());
        assertEquals("tenant-i1-imp2", request.tenantId());
        assertEquals("NQ_DRYRUN", request.source());
        assertEquals(Instant.parse("2026-07-04T00:00:00Z"), request.timestamp());
        assertEquals("nonce-i1-imp2-001", request.nonce());
        assertEquals("READ_ONLY_RECOMMENDATION", request.decisionType());
        assertEquals("BTC-USDT", request.subject().symbol());
        assertEquals("readonly context only", request.contextSummary());
    }

    @Test
    void requestBuilderRejectsExecutionCredentialHttpAndMutationShape() {
        for (String field :
                List.of(
                        "accountId",
                        "orderId",
                        "clientOrderId",
                        "positionId",
                        "credential",
                        "token",
                        "apiKey",
                        "apiSecret",
                        "passphrase",
                        "quantity",
                        "price",
                        "leverage",
                        "placeOrder",
                        "cancelOrder",
                        "mutateRisk",
                        "mutateLedger",
                        "paperRunStart",
                        "liveRunStart",
                        "realUrl",
                        "httpClient")) {
            final Map<String, Object> input = validInput();
            input.put(field, "FORBIDDEN");

            assertThrows(IllegalArgumentException.class, () -> DryRunRequestBuilder.build(input), field);
        }

        for (String executableValue :
                List.of("BUY", "SELL", "PLACE_ORDER", "CANCEL_ORDER", "quantity=1", "price=1")) {
            final Map<String, Object> input = validInput();
            input.put("contextSummary", executableValue);

            assertThrows(IllegalArgumentException.class, () -> DryRunRequestBuilder.build(input), executableValue);
        }

        for (String realOutboundValue :
                List.of("https://dh.example.invalid/dry-run", "http://127.0.0.1:18888/dry-run")) {
            final Map<String, Object> input = validInput();
            input.put("evidenceSummary", realOutboundValue);

            assertThrows(IllegalArgumentException.class, () -> DryRunRequestBuilder.build(input), realOutboundValue);
        }
    }

    @Test
    void longAndShortBiasAreReadonlySummariesNotBuySellOrOrders() {
        final DryRunRequest request = DryRunRequestBuilder.build(validInput());

        for (String bias : List.of("LONG_BIAS", "SHORT_BIAS")) {
            final SideEffectProbe probe = new SideEffectProbe();
            final DecisionOutputStub output = DecisionOutputStub.readonly(request, bias, "LOW", "ALLOWED", "MOCKED");
            final ReadonlyRecorder recorder = new ReadonlyRecorder();

            final ReadonlyRecord record = recorder.record(output, probe);

            assertEquals(bias, record.action());
            assertTrue(READONLY_ACTIONS.contains(record.action()));
            assertFalse(record.conciseText().contains("BUY"));
            assertFalse(record.conciseText().contains("SELL"));
            assertNoSideEffects(probe);
        }
    }

    @Test
    void recorderStoresReadonlySummaryOnlyAndDoesNotCallMutationBoundaries() {
        final DryRunRequest request = DryRunRequestBuilder.build(validInput());
        final SideEffectProbe probe = new SideEffectProbe();
        final DecisionOutputStub output = DecisionOutputStub.readonly(request, "NO_TRADE", "LOW", "ALLOWED", "MOCKED");
        final ReadonlyRecorder recorder = new ReadonlyRecorder();

        final ReadonlyRecord record = recorder.record(output, probe);

        assertEquals("req-i1-imp2-001", record.requestId());
        assertEquals("NO_TRADE", record.action());
        assertEquals("LOW", record.riskLevel());
        assertEquals(List.of("PLACE_ORDER", "CANCEL_ORDER", "MUTATE_NQ_STATE", "READ_NQ_DB", "WRITE_NQ_DB"),
                record.forbiddenActions());
        assertNoSideEffects(probe);
        assertReadonlyRecordHasNoSensitiveOrExecutionMaterial(record);
    }

    @Test
    void failureHighRiskNoEvidenceAndDuplicateRequestIdAreRecordOnly() {
        final DryRunRequest request = DryRunRequestBuilder.build(validInput());
        final ReadonlyRecorder recorder = new ReadonlyRecorder();
        final SideEffectProbe probe = new SideEffectProbe();

        final List<DecisionOutputStub> outputs =
                List.of(
                        DecisionOutputStub.failClosed(request, "PROVIDER_FAILURE"),
                        DecisionOutputStub.failClosed(request, "HIGH_RISK_BLOCKED"),
                        DecisionOutputStub.failClosed(request, "NO_EVIDENCE"),
                        DecisionOutputStub.failClosed(request, "INTERNAL_FAIL_CLOSED"),
                        DecisionOutputStub.failClosed(request, "DUPLICATE_REQUEST_ID"));

        final List<ReadonlyRecord> records = new ArrayList<>();
        for (DecisionOutputStub output : outputs) {
            records.add(recorder.record(output, probe));
        }

        assertEquals(5, records.size());
        assertEquals(5, recorder.recordCountFor("req-i1-imp2-001"));
        for (ReadonlyRecord record : records) {
            assertEquals("ABSTAIN", record.action());
            assertEquals("BLOCKED", record.policyStatus());
            assertTrue(record.reasonCodes().stream().anyMatch(reason -> reason.endsWith("FAIL_CLOSED")
                    || Set.of("PROVIDER_FAILURE", "HIGH_RISK_BLOCKED", "NO_EVIDENCE", "DUPLICATE_REQUEST_ID")
                    .contains(reason)));
            assertReadonlyRecordHasNoSensitiveOrExecutionMaterial(record);
        }
        assertNoSideEffects(probe);
    }

    @Test
    void noRealClientUrlCredentialOrSourceAllowlistIsAddedToProductionCode() throws Exception {
        assertNoProductionToken("NQ_DRYRUN");
        assertNoProductionToken("NqDhDryRun");
        assertNoProductionToken("RealNqDhDryRunClient");
        assertNoProductionToken("/api/nq-dh");
        assertNoProductionToken("/dry-run");
    }

    private static Map<String, Object> validInput() {
        final Map<String, Object> input = new LinkedHashMap<>();
        input.put("requestId", "req-i1-imp2-001");
        input.put("traceId", "trace-i1-imp2-001");
        input.put("tenantId", "tenant-i1-imp2");
        input.put("source", "NQ_DRYRUN");
        input.put("timestamp", "2026-07-04T00:00:00Z");
        input.put("nonce", "nonce-i1-imp2-001");
        input.put("decisionType", "READ_ONLY_RECOMMENDATION");
        input.put("subject", new Subject("BTC-USDT", "CRYPTO_SPOT", "1h"));
        input.put("contextSummary", "readonly context only");
        input.put("evidenceSummary", "readonly evidence only");
        return input;
    }

    private static void assertNoSideEffects(final SideEffectProbe probe) {
        assertEquals(0, probe.orderCalls());
        assertEquals(0, probe.executionCalls());
        assertEquals(0, probe.riskCalls());
        assertEquals(0, probe.ledgerCalls());
        assertEquals(0, probe.paperCalls());
        assertEquals(0, probe.liveCalls());
        assertEquals(0, probe.httpCalls());
        assertEquals(0, probe.credentialCalls());
    }

    private static void assertReadonlyRecordHasNoSensitiveOrExecutionMaterial(final ReadonlyRecord record) {
        final String normalized = normalize(record.conciseText());
        for (String token : SENSITIVE_SUMMARY_TOKENS) {
            assertFalse(normalized.contains(token), "readonly record must not contain " + token);
        }
        for (String token : List.of("buy", "sell", "quantity", "price", "leverage")) {
            assertFalse(normalized.contains(token), "readonly record must not contain execution token " + token);
        }
    }

    private static void assertNoProductionToken(final String token) throws IOException {
        for (Path root : mainJavaRoots()) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(NqDhIntegration1StubRecorderNoSideEffectTest::isJavaFile)
                        .toList()) {
                    assertFalse(
                            Files.readString(file).contains(token),
                            "production Java source must not contain " + token + " in " + file);
                }
            }
        }
    }

    private static List<Path> mainJavaRoots() throws IOException {
        final Path backendRoot = Path.of("..").toAbsolutePath().normalize();
        try (Stream<Path> modules = Files.list(backendRoot)) {
            return modules
                    .map(module -> module.resolve(Path.of("src", "main", "java")))
                    .filter(Files::isDirectory)
                    .toList();
        }
    }

    private static boolean isJavaFile(final Path path) {
        return path.getFileName().toString().endsWith(".java");
    }

    private static String normalize(final String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * test-support request builder；只验证 future dry-run request 允许字段与禁止字段。
     */
    private static final class DryRunRequestBuilder {
        private DryRunRequestBuilder() {
        }

        private static DryRunRequest build(final Map<String, Object> input) {
            for (Map.Entry<String, Object> entry : input.entrySet()) {
                rejectForbidden(entry.getKey());
                rejectForbiddenValue(entry.getValue());
            }
            final Instant timestamp = Instant.parse(text(input, "timestamp"));
            if (!text(input, "timestamp").endsWith("Z")) {
                throw new IllegalArgumentException("timestamp must be RFC3339 UTC Z");
            }
            if (!"READ_ONLY_RECOMMENDATION".equals(text(input, "decisionType"))) {
                throw new IllegalArgumentException("decisionType must be read-only recommendation");
            }
            return new DryRunRequest(
                    text(input, "requestId"),
                    text(input, "traceId"),
                    text(input, "tenantId"),
                    text(input, "source"),
                    timestamp,
                    text(input, "nonce"),
                    text(input, "decisionType"),
                    subject(input),
                    text(input, "contextSummary"),
                    text(input, "evidenceSummary"));
        }

        private static Subject subject(final Map<String, Object> input) {
            final Object value = input.get("subject");
            if (value instanceof Subject subject) {
                return subject;
            }
            throw new IllegalArgumentException("subject must be test-support Subject");
        }

        private static String text(final Map<String, Object> input, final String field) {
            final Object value = input.get(field);
            if (value == null || value.toString().isBlank()) {
                throw new IllegalArgumentException("missing required field: " + field);
            }
            return value.toString();
        }

        private static void rejectForbidden(final String field) {
            final String normalized = normalize(field);
            if (FORBIDDEN_REQUEST_TOKENS.stream().anyMatch(normalized::contains)) {
                throw new IllegalArgumentException("forbidden dry-run request field: " + field);
            }
        }

        private static void rejectForbiddenValue(final Object value) {
            if (value == null) {
                return;
            }
            final String normalized = normalize(value.toString());
            if (FORBIDDEN_REQUEST_TOKENS.stream().anyMatch(normalized::contains)
                    || value.toString().startsWith("http://")
                    || value.toString().startsWith("https://")) {
                throw new IllegalArgumentException("forbidden dry-run request value");
            }
        }
    }

    /**
     * test-support recorder；只保存结构化 readonly summary 和 duplicate 计数。
     */
    private static final class ReadonlyRecorder {
        private final Map<String, Integer> recordCountByRequestId = new LinkedHashMap<>();

        private ReadonlyRecord record(final DecisionOutputStub output, final SideEffectProbe probe) {
            recordCountByRequestId.merge(output.requestId(), 1, Integer::sum);
            return new ReadonlyRecord(
                    output.requestId(),
                    output.traceId(),
                    output.tenantId(),
                    output.action(),
                    output.riskLevel(),
                    output.policyStatus(),
                    output.providerStatus(),
                    output.forbiddenActions(),
                    output.reasonCodes(),
                    recordCountByRequestId.get(output.requestId()));
        }

        private int recordCountFor(final String requestId) {
            return recordCountByRequestId.getOrDefault(requestId, 0);
        }
    }

    /**
     * side-effect probe 只作为测试断言目标；recorder 不允许调用任何方法。
     */
    private static final class SideEffectProbe {
        private int orderCalls;
        private int executionCalls;
        private int riskCalls;
        private int ledgerCalls;
        private int paperCalls;
        private int liveCalls;
        private int httpCalls;
        private int credentialCalls;

        private int orderCalls() {
            return orderCalls;
        }

        private int executionCalls() {
            return executionCalls;
        }

        private int riskCalls() {
            return riskCalls;
        }

        private int ledgerCalls() {
            return ledgerCalls;
        }

        private int paperCalls() {
            return paperCalls;
        }

        private int liveCalls() {
            return liveCalls;
        }

        private int httpCalls() {
            return httpCalls;
        }

        private int credentialCalls() {
            return credentialCalls;
        }
    }

    private record Subject(String symbol, String market, String timeframe) {
    }

    private record DryRunRequest(
            String requestId,
            String traceId,
            String tenantId,
            String source,
            Instant timestamp,
            String nonce,
            String decisionType,
            Subject subject,
            String contextSummary,
            String evidenceSummary) {
    }

    private record DecisionOutputStub(
            String requestId,
            String traceId,
            String tenantId,
            String action,
            String riskLevel,
            String policyStatus,
            String providerStatus,
            List<String> forbiddenActions,
            List<String> reasonCodes) {
        private static DecisionOutputStub readonly(
                final DryRunRequest request,
                final String action,
                final String riskLevel,
                final String policyStatus,
                final String providerStatus) {
            if (!READONLY_ACTIONS.contains(action)) {
                throw new IllegalArgumentException("action must stay readonly");
            }
            return new DecisionOutputStub(
                    request.requestId(),
                    request.traceId(),
                    request.tenantId(),
                    action,
                    riskLevel,
                    policyStatus,
                    providerStatus,
                    List.of("PLACE_ORDER", "CANCEL_ORDER", "MUTATE_NQ_STATE", "READ_NQ_DB", "WRITE_NQ_DB"),
                    List.of("READONLY_DRYRUN"));
        }

        private static DecisionOutputStub failClosed(final DryRunRequest request, final String reason) {
            return new DecisionOutputStub(
                    request.requestId(),
                    request.traceId(),
                    request.tenantId(),
                    "ABSTAIN",
                    "BLOCKED",
                    "BLOCKED",
                    "MOCKED",
                    List.of("PLACE_ORDER", "CANCEL_ORDER", "MUTATE_NQ_STATE", "READ_NQ_DB", "WRITE_NQ_DB"),
                    List.of(reason));
        }
    }

    private record ReadonlyRecord(
            String requestId,
            String traceId,
            String tenantId,
            String action,
            String riskLevel,
            String policyStatus,
            String providerStatus,
            List<String> forbiddenActions,
            List<String> reasonCodes,
            int duplicateRecordCount) {
        private String conciseText() {
            return String.join(
                    "|",
                    requestId,
                    traceId,
                    tenantId,
                    action,
                    riskLevel,
                    policyStatus,
                    providerStatus,
                    String.join(",", forbiddenActions),
                    String.join(",", reasonCodes),
                    Integer.toString(duplicateRecordCount));
        }
    }
}
