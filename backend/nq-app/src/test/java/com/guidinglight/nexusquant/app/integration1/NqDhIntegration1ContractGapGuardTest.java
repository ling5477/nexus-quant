package com.guidinglight.nexusquant.app.integration1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * IMP0 test-support guard for NQ-DH Integration-1 contract gaps.
 *
 * <p>本测试只定义 future dry-run builder / recorder 的 test-support shape，不实现生产 builder、生产
 * recorder、runtime HTTP client、Controller、schema、fixture 或 LIVE 能力。
 */
class NqDhIntegration1ContractGapGuardTest {

    private static final Set<String> READONLY_ACTIONS =
            Set.of("ABSTAIN", "OBSERVE", "NO_TRADE", "LONG_BIAS", "SHORT_BIAS");

    private static final Set<String> FORBIDDEN_FIELD_TOKENS =
            Set.of(
                    "account",
                    "accountid",
                    "order",
                    "orderid",
                    "credential",
                    "apikey",
                    "apisecret",
                    "secret",
                    "token",
                    "cookie",
                    "quantity",
                    "price",
                    "leverage",
                    "placeorder",
                    "cancelorder",
                    "mutatenqstate",
                    "mutaterisk",
                    "mutateledger",
                    "paperrunstart",
                    "liverunstart",
                    "realurl",
                    "callbackurl",
                    "httpclient");

    @Test
    void sourceNqDryrunRemainsReviewGatedTestSupportOnly() throws Exception {
        SourcePlan sourcePlan = SourcePlan.testSupportReviewGated("NQ_DRYRUN");
        DryRunDecisionRequest request = FutureDryRunRequestBuilder.build(validInput(), sourcePlan);

        assertEquals("NQ_DRYRUN", request.source());
        assertTrue(sourcePlan.reviewGated());
        assertFalse(sourcePlan.productionAllowlisted());
        assertNoProductionToken("NQ_DRYRUN");
        assertNoProductionToken("NqDhDryRun");
        assertNoProductionToken("/api/nq-dh");
    }

    @Test
    void requestBuilderRejectsForbiddenExecutionFieldsAndValues() {
        SourcePlan sourcePlan = SourcePlan.testSupportReviewGated("NQ_DRYRUN");

        for (String field :
                List.of(
                        "accountId",
                        "orderId",
                        "credential",
                        "apiKey",
                        "apiSecret",
                        "quantity",
                        "price",
                        "leverage",
                        "placeOrder",
                        "cancelOrder",
                        "mutateRisk",
                        "mutateLedger",
                        "paperRunStart",
                        "liveRunStart",
                        "callbackUrl",
                        "httpClient")) {
            Map<String, Object> input = validInput();
            input.put(field, "FORBIDDEN");
            assertThrows(IllegalArgumentException.class, () -> FutureDryRunRequestBuilder.build(input, sourcePlan), field);
        }

        for (String executableAction : List.of("BUY", "SELL", "PLACE_ORDER", "CANCEL_ORDER")) {
            Map<String, Object> input = validInput();
            input.put("decisionAction", executableAction);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FutureDryRunRequestBuilder.build(input, sourcePlan),
                    executableAction);
        }

        Map<String, Object> inputWithRealUrl = validInput();
        inputWithRealUrl.put("evidenceSummary", "https://example.invalid/real-provider");
        assertThrows(
                IllegalArgumentException.class,
                () -> FutureDryRunRequestBuilder.build(inputWithRealUrl, sourcePlan));
    }

    @Test
    void longAndShortBiasRemainReadonlyAndNeverMapToBuySell() {
        SourcePlan sourcePlan = SourcePlan.testSupportReviewGated("NQ_DRYRUN");

        for (String bias : List.of("LONG_BIAS", "SHORT_BIAS")) {
            Map<String, Object> input = validInput();
            input.put("decisionAction", bias);
            DryRunDecisionRequest request = FutureDryRunRequestBuilder.build(input, sourcePlan);
            ReadonlySummary summary = ReadonlyRecorder.record(request, new SideEffectProbe());

            assertEquals(bias, request.action());
            assertEquals(bias, summary.action());
            assertFalse(summary.toString().contains("BUY"));
            assertFalse(summary.toString().contains("SELL"));
        }
    }

    @Test
    void recorderStoresReadonlySummaryOnlyAndTouchesNoMutationBoundaries() {
        SourcePlan sourcePlan = SourcePlan.testSupportReviewGated("NQ_DRYRUN");
        DryRunDecisionRequest request = FutureDryRunRequestBuilder.build(validInput(), sourcePlan);
        SideEffectProbe probe = new SideEffectProbe();

        ReadonlySummary summary = ReadonlyRecorder.record(request, probe);

        assertEquals("req-i1-imp0-001", summary.requestId());
        assertEquals("NO_TRADE", summary.action());
        assertEquals(0, probe.orderMutations());
        assertEquals(0, probe.riskMutations());
        assertEquals(0, probe.ledgerMutations());
        assertEquals(0, probe.paperRunMutations());
        assertEquals(0, probe.liveMutations());
        assertEquals(0, probe.realHttpCalls());
        assertEquals(0, probe.credentialLookups());
        assertReadonlySummaryHasNoExecutionMaterial(summary);
    }

    @Test
    void noRealHttpUrlOrCredentialGuardRejectsUnsafeFutureShape() {
        SourcePlan sourcePlan = SourcePlan.testSupportReviewGated("NQ_DRYRUN");

        for (Map.Entry<String, String> unsafe :
                Map.of(
                                "realUrl", "http://example.invalid",
                                "credential", "fake-placeholder",
                                "apiSecret", "fake-placeholder",
                                "token", "fake-placeholder")
                        .entrySet()) {
            Map<String, Object> input = validInput();
            input.put(unsafe.getKey(), unsafe.getValue());
            assertThrows(IllegalArgumentException.class, () -> FutureDryRunRequestBuilder.build(input, sourcePlan));
        }
    }

    private static Map<String, Object> validInput() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("requestId", "req-i1-imp0-001");
        input.put("traceId", "trace-i1-imp0-001");
        input.put("tenantId", "t-test-i1-imp0");
        input.put("source", "NQ_DRYRUN");
        input.put("decisionAction", "NO_TRADE");
        input.put("riskSummary", "readonly risk summary");
        input.put("evidenceSummary", "readonly evidence summary");
        return input;
    }

    private static void assertReadonlySummaryHasNoExecutionMaterial(final ReadonlySummary summary) {
        final String rendered = summary.toString();
        for (String token :
                List.of(
                        "BUY",
                        "SELL",
                        "quantity",
                        "price",
                        "leverage",
                        "placeOrder",
                        "cancelOrder",
                        "mutateRisk",
                        "mutateLedger",
                        "paperRunStart",
                        "liveRunStart",
                        "apiSecret",
                        "credential")) {
            assertFalse(rendered.contains(token), "readonly summary must not contain " + token);
        }
    }

    private static void assertNoProductionToken(final String token) throws IOException {
        for (Path root : mainJavaRoots()) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).filter(NqDhIntegration1ContractGapGuardTest::isJavaFile).toList()) {
                    assertFalse(
                            Files.readString(file).contains(token),
                            "production Java source must not contain " + token + " in " + file);
                }
            }
        }
    }

    private static List<Path> mainJavaRoots() throws IOException {
        Path backendRoot = Path.of("..").toAbsolutePath().normalize();
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

    private record SourcePlan(String value, boolean reviewGated, boolean productionAllowlisted) {
        private static SourcePlan testSupportReviewGated(final String value) {
            return new SourcePlan(value, true, false);
        }
    }

    private record DryRunDecisionRequest(
            String requestId,
            String traceId,
            String tenantId,
            String source,
            String action,
            String riskSummary,
            String evidenceSummary) {
    }

    private record ReadonlySummary(
            String requestId,
            String traceId,
            String tenantId,
            String source,
            String action,
            String riskSummary,
            String evidenceSummary) {
    }

    private static final class FutureDryRunRequestBuilder {
        private FutureDryRunRequestBuilder() {
        }

        private static DryRunDecisionRequest build(final Map<String, Object> input, final SourcePlan sourcePlan) {
            if (!sourcePlan.reviewGated() || sourcePlan.productionAllowlisted()) {
                throw new IllegalArgumentException("NQ_DRYRUN must stay review-gated test-support source");
            }
            for (Map.Entry<String, Object> entry : input.entrySet()) {
                assertAllowedFieldName(entry.getKey());
                assertAllowedValue(entry.getKey(), entry.getValue());
            }
            final String action = text(input, "decisionAction");
            if (!READONLY_ACTIONS.contains(action)) {
                throw new IllegalArgumentException("action must remain readonly: " + action);
            }
            return new DryRunDecisionRequest(
                    text(input, "requestId"),
                    text(input, "traceId"),
                    text(input, "tenantId"),
                    text(input, "source"),
                    action,
                    text(input, "riskSummary"),
                    text(input, "evidenceSummary"));
        }

        private static void assertAllowedFieldName(final String name) {
            final String normalized = normalize(name);
            for (String token : FORBIDDEN_FIELD_TOKENS) {
                if (normalized.contains(token)) {
                    throw new IllegalArgumentException("forbidden dry-run field: " + name);
                }
            }
        }

        private static void assertAllowedValue(final String name, final Object value) {
            if (value == null) {
                return;
            }
            final String text = value.toString();
            if ("decisionAction".equals(name)
                    && Set.of("BUY", "SELL", "PLACE_ORDER", "CANCEL_ORDER").contains(text)) {
                throw new IllegalArgumentException("execution action is forbidden: " + text);
            }
            if (text.startsWith("http://") || text.startsWith("https://")) {
                throw new IllegalArgumentException("real URL is forbidden in dry-run test-support shape");
            }
        }

        private static String text(final Map<String, Object> input, final String name) {
            final Object value = input.get(name);
            if (value == null || value.toString().isBlank()) {
                throw new IllegalArgumentException("missing required dry-run test-support field: " + name);
            }
            return value.toString();
        }
    }

    private static final class ReadonlyRecorder {
        private ReadonlyRecorder() {
        }

        private static ReadonlySummary record(final DryRunDecisionRequest request, final SideEffectProbe probe) {
            return new ReadonlySummary(
                    request.requestId(),
                    request.traceId(),
                    request.tenantId(),
                    request.source(),
                    request.action(),
                    request.riskSummary(),
                    request.evidenceSummary());
        }
    }

    private static final class SideEffectProbe {
        private int orderMutations;
        private int riskMutations;
        private int ledgerMutations;
        private int paperRunMutations;
        private int liveMutations;
        private int realHttpCalls;
        private int credentialLookups;

        private int orderMutations() {
            return orderMutations;
        }

        private int riskMutations() {
            return riskMutations;
        }

        private int ledgerMutations() {
            return ledgerMutations;
        }

        private int paperRunMutations() {
            return paperRunMutations;
        }

        private int liveMutations() {
            return liveMutations;
        }

        private int realHttpCalls() {
            return realHttpCalls;
        }

        private int credentialLookups() {
            return credentialLookups;
        }
    }

    private static String normalize(final String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
