package com.guidinglight.nexusquant.app.integration1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * IMP3 NQ-side joint mock fixture / contract tests。
 *
 * <p>本测试只验证 NQ dry-run worktree 的 fixture parse、readonly request shape、readonly recorder 和
 * no-side-effect 边界。它不实现生产 builder、不创建 HTTP client、不连接 DH runtime、不触发 order / risk /
 * ledger / paper / live 路径。
 */
class NqDhIntegration1JointMockContractFixtureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RESOURCE = "/nq-dh/integration1/joint_mock_contract_fixtures.json";
    private static final Set<String> REQUEST_FAMILIES =
            Set.of(
                    "valid_read_only_recommendation_request",
                    "missing_signature_request",
                    "invalid_signature_request",
                    "timestamp_skew_request",
                    "nonce_replay_request",
                    "source_denied_request",
                    "tenant_mismatch_request",
                    "forbidden_credential_field_request",
                    "forbidden_order_account_field_request",
                    "forbidden_execution_intent_request");
    private static final Set<String> RESPONSE_FAMILIES =
            Set.of(
                    "valid_abstain_response",
                    "valid_observe_response",
                    "valid_no_trade_response",
                    "readonly_long_bias_response",
                    "readonly_short_bias_response",
                    "fail_closed_provider_timeout_response",
                    "fail_closed_risk_blocked_response",
                    "fail_closed_internal_error_response");
    private static final Set<String> READONLY_ACTIONS =
            Set.of("ABSTAIN", "OBSERVE", "NO_TRADE", "LONG_BIAS", "SHORT_BIAS");
    private static final List<String> FIXED_FORBIDDEN_ACTIONS =
            List.of("PLACE_ORDER", "CANCEL_ORDER", "MUTATE_NQ_STATE", "READ_NQ_DB", "WRITE_NQ_DB");
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
                    "httpclient");

    @Test
    void fixtureSetMirrorsDhFamilyNamesAndStaysMockOnly() throws Exception {
        final JsonNode root = fixtures();

        assertTrue(root.path("mockOnly").asBoolean());
        assertEquals("NO_RUNTIME", root.path("runtimeState").asText());
        assertEquals(REQUEST_FAMILIES, familyNames("request"));
        assertEquals(RESPONSE_FAMILIES, familyNames("response"));
        for (JsonNode fixture : families()) {
            assertFixturePayloadHasNoRealUrlCredentialOrExecutionMaterial(fixture);
        }
    }

    @Test
    void requestFixturesParseOnlyReadonlyFields() throws Exception {
        for (JsonNode fixture : families()) {
            if (!"request".equals(fixture.path("fixtureKind").asText())) {
                continue;
            }
            final DryRunRequest request = DryRunRequestBuilder.build(fixture.path("request"));

            assertEquals("READ_ONLY_RECOMMENDATION", request.decisionType());
            assertFalse(request.requestId().isBlank());
            assertFalse(request.traceId().isBlank());
            assertFalse(request.tenantId().isBlank());
            assertFalse(request.source().isBlank());
            assertTrue(request.timestamp().toString().endsWith("Z"));
            assertFalse(request.subject().symbol().isBlank());
            assertFalse(request.contextSummary().contains("http://"));
            assertFalse(request.contextSummary().contains("https://"));
        }
    }

    @Test
    void forbiddenCredentialOrderAccountAndExecutionIntentAreRejectedBeforeSend() throws Exception {
        for (Map.Entry<String, Mutation> mutation : forbiddenMutations().entrySet()) {
            final Map<String, Object> input = mutableRequestMap(fixture(mutation.getKey()).path("request"));
            input.put(mutation.getValue().fieldName(), mutation.getValue().fieldValue());

            assertThrows(IllegalArgumentException.class, () -> DryRunRequestBuilder.build(input));
        }
    }

    @Test
    void responseFixturesRecordReadonlySummaryOnly() throws Exception {
        final ReadonlyRecorder recorder = new ReadonlyRecorder();
        final SideEffectProbe probe = new SideEffectProbe();

        for (JsonNode fixture : families()) {
            if (!"response".equals(fixture.path("fixtureKind").asText())) {
                continue;
            }
            final DecisionOutputStub output = DecisionOutputStub.fromFixture(fixture.path("response"));
            final ReadonlyRecord record = recorder.record(output, probe);

            assertTrue(READONLY_ACTIONS.contains(record.action()));
            assertEquals(FIXED_FORBIDDEN_ACTIONS, record.forbiddenActions());
            assertReadonlyRecordHasNoExecutionMaterial(record);
            assertNoSideEffects(probe);
        }
    }

    @Test
    void longAndShortBiasNeverMapToBuySell() throws Exception {
        for (String family : List.of("readonly_long_bias_response", "readonly_short_bias_response")) {
            final ReadonlyRecord record = new ReadonlyRecorder()
                    .record(DecisionOutputStub.fromFixture(fixture(family).path("response")), new SideEffectProbe());

            assertTrue(Set.of("LONG_BIAS", "SHORT_BIAS").contains(record.action()));
            assertFalse(record.conciseText().contains("BUY"));
            assertFalse(record.conciseText().contains("SELL"));
        }
    }

    @Test
    void failClosedResponsesAreRecordOnlyAndNeverExecute() throws Exception {
        final ReadonlyRecorder recorder = new ReadonlyRecorder();
        final SideEffectProbe probe = new SideEffectProbe();

        for (String family : List.of(
                "fail_closed_provider_timeout_response",
                "fail_closed_risk_blocked_response",
                "fail_closed_internal_error_response")) {
            final ReadonlyRecord record =
                    recorder.record(DecisionOutputStub.fromFixture(fixture(family).path("response")), probe);

            assertEquals("ABSTAIN", record.action());
            assertTrue(Set.of("ABSTAINED", "BLOCKED").contains(record.status()));
            assertTrue(record.reasonCodes().stream().anyMatch(reason -> reason.endsWith("TIMEOUT")
                    || reason.endsWith("BLOCKED")
                    || reason.endsWith("FAIL_CLOSED")));
            assertNoSideEffects(probe);
        }
    }

    @Test
    void noRealUrlHttpClientOutboundOrRuntimeTokenIsAdded() throws Exception {
        for (String token : List.of(
                "RealNqDhDryRunClient",
                "NqDhDryRunClient",
                "NqDhDryRunHttpClient",
                "NqDhDryRunWebClient",
                "NqDhDryRunRestTemplate",
                "/api/nq-dh",
                "/dry-run")) {
            assertNoProductionToken(token);
        }
    }

    private static Map<String, Mutation> forbiddenMutations() {
        final Map<String, Mutation> mutations = new LinkedHashMap<>();
        mutations.put("forbidden_credential_field_request", new Mutation("apiSecret", "synthetic-rejected"));
        mutations.put("forbidden_order_account_field_request", new Mutation("orderId", "synthetic-rejected"));
        mutations.put("forbidden_execution_intent_request", new Mutation("intent", "BUY"));
        return mutations;
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

    private static void assertReadonlyRecordHasNoExecutionMaterial(final ReadonlyRecord record) {
        final String normalized = normalize(String.join(
                "|",
                record.requestId(),
                record.traceId(),
                record.tenantId(),
                record.action(),
                record.status(),
                record.riskLevel(),
                record.policyStatus(),
                record.providerStatus(),
                String.join(",", record.reasonCodes()),
                Integer.toString(record.duplicateRecordCount())));
        for (String token : List.of(
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
                "credential",
                "apikey",
                "apisecret",
                "passphrase")) {
            assertFalse(normalized.contains(token), "readonly record must not contain " + token);
        }
    }

    private static void assertFixturePayloadHasNoRealUrlCredentialOrExecutionMaterial(final JsonNode fixture) {
        final String payloadText = (fixture.path("request").toString() + fixture.path("response").toString())
                .toLowerCase(Locale.ROOT);
        for (String token : List.of(
                "http://",
                "https://",
                "apikey",
                "apisecret",
                "passphrase",
                "cookie",
                "privatekey",
                "accountid",
                "orderid",
                "quantity",
                "price",
                "leverage",
                "placeorder",
                "cancelorder",
                "paperrunstart",
                "liverunstart",
                "mutaterisk",
                "mutateledger")) {
            assertFalse(payloadText.contains(token), "fixture payload must not contain " + token);
        }
        assertFalse(payloadText.contains("\"buy\""));
        assertFalse(payloadText.contains("\"sell\""));
    }

    private static Set<String> familyNames(final String kind) throws Exception {
        final Set<String> names = new HashSet<>();
        for (JsonNode fixture : families()) {
            if (kind.equals(fixture.path("fixtureKind").asText())) {
                names.add(fixture.path("fixtureFamily").asText());
            }
        }
        return names;
    }

    private static List<JsonNode> families() throws Exception {
        final List<JsonNode> families = new ArrayList<>();
        fixtures().path("families").forEach(families::add);
        return families;
    }

    private static JsonNode fixture(final String family) throws Exception {
        for (JsonNode fixture : families()) {
            if (family.equals(fixture.path("fixtureFamily").asText())) {
                return fixture;
            }
        }
        throw new IllegalArgumentException("missing fixture family: " + family);
    }

    private static JsonNode fixtures() throws IOException {
        try (InputStream input = NqDhIntegration1JointMockContractFixtureTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(input, "missing fixture resource " + RESOURCE);
            return MAPPER.readTree(input);
        }
    }

    private static Map<String, Object> mutableRequestMap(final JsonNode request) {
        final Map<String, Object> input = new LinkedHashMap<>();
        request.fields().forEachRemaining(entry -> {
            if ("subject".equals(entry.getKey())) {
                input.put(
                        "subject",
                        new Subject(
                                entry.getValue().path("symbol").asText(),
                                entry.getValue().path("market").asText(),
                                entry.getValue().path("timeframe").asText()));
            } else if ("contextSnapshot".equals(entry.getKey())) {
                input.put("contextSummary", "readonly context only");
                input.put("evidenceSummary", String.join(",", evidenceRefs(entry.getValue())));
            } else if (!"headers".equals(entry.getKey())) {
                input.put(entry.getKey(), entry.getValue().asText());
            }
        });
        return input;
    }

    private static List<String> evidenceRefs(final JsonNode contextSnapshot) {
        final List<String> refs = new ArrayList<>();
        contextSnapshot.path("evidenceRefs").forEach(node -> refs.add(node.asText()));
        return refs;
    }

    private static void assertNoProductionToken(final String token) throws IOException {
        for (Path root : mainJavaRoots()) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(NqDhIntegration1JointMockContractFixtureTest::isJavaFile)
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

    private static final class DryRunRequestBuilder {
        private DryRunRequestBuilder() {
        }

        private static DryRunRequest build(final JsonNode request) {
            return build(mutableRequestMap(request));
        }

        private static DryRunRequest build(final Map<String, Object> input) {
            for (Map.Entry<String, Object> entry : input.entrySet()) {
                rejectForbidden(entry.getKey());
                rejectForbiddenValue(entry.getValue());
            }
            final String timestamp = text(input, "requestedAt");
            if (!timestamp.endsWith("Z")) {
                throw new IllegalArgumentException("timestamp must stay RFC3339 UTC Z");
            }
            if (!"READ_ONLY_RECOMMENDATION".equals(text(input, "decisionType"))) {
                throw new IllegalArgumentException("decisionType must stay read-only");
            }
            return new DryRunRequest(
                    text(input, "requestId"),
                    text(input, "traceId"),
                    text(input, "tenantId"),
                    text(input, "source"),
                    Instant.parse(timestamp),
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
            final String raw = value.toString();
            final String normalized = normalize(raw);
            if (FORBIDDEN_REQUEST_TOKENS.stream().anyMatch(normalized::contains)
                    || raw.startsWith("http://")
                    || raw.startsWith("https://")) {
                throw new IllegalArgumentException("forbidden dry-run request value");
            }
        }
    }

    private static final class ReadonlyRecorder {
        private final Map<String, Integer> recordCountByRequestId = new LinkedHashMap<>();

        private ReadonlyRecord record(final DecisionOutputStub output, final SideEffectProbe probe) {
            recordCountByRequestId.merge(output.requestId(), 1, Integer::sum);
            return new ReadonlyRecord(
                    output.requestId(),
                    output.traceId(),
                    output.tenantId(),
                    output.action(),
                    output.status(),
                    output.riskLevel(),
                    output.policyStatus(),
                    output.providerStatus(),
                    output.forbiddenActions(),
                    output.reasonCodes(),
                    recordCountByRequestId.get(output.requestId()));
        }
    }

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
            String status,
            String riskLevel,
            String policyStatus,
            String providerStatus,
            List<String> forbiddenActions,
            List<String> reasonCodes) {
        private static DecisionOutputStub fromFixture(final JsonNode response) {
            final String action = response.path("action").asText();
            if (!READONLY_ACTIONS.contains(action)) {
                throw new IllegalArgumentException("action must stay readonly: " + action);
            }
            return new DecisionOutputStub(
                    response.path("requestId").asText(),
                    response.path("traceId").asText(),
                    response.path("tenantId").asText(),
                    action,
                    response.path("status").asText(),
                    response.path("riskLevel").asText(),
                    response.path("policyStatus").asText(),
                    response.path("providerStatus").asText(),
                    textArray(response.path("forbiddenActions")),
                    textArray(response.path("reasonCodes")));
        }
    }

    private record ReadonlyRecord(
            String requestId,
            String traceId,
            String tenantId,
            String action,
            String status,
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
                    status,
                    riskLevel,
                    policyStatus,
                    providerStatus,
                    String.join(",", forbiddenActions),
                    String.join(",", reasonCodes),
                    Integer.toString(duplicateRecordCount));
        }
    }

    private record Mutation(String fieldName, String fieldValue) {
    }

    private static List<String> textArray(final JsonNode array) {
        final List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
