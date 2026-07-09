package com.guidinglight.nexusquant.strategy.application.pyartifactpreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class PythonEvaluationArtifactPreviewOverviewQueryServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-09T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldReturnSafeNoFileBaselineOverview() {
        PythonEvaluationArtifactPreviewOverviewReadModel model = service().overview("trace-preview");

        assertEquals(Instant.parse("2026-07-09T12:00:00Z"), model.generatedAt());
        assertTrue(model.diagnosticOnly());
        assertTrue(model.noSideEffect());
        assertTrue(model.notTradingAuthorization());
        assertTrue(model.liveDisabled());
        assertFalse(model.realProviderImplemented());
        assertFalse(model.privateTradingImplemented());
        assertFalse(model.aiDhRuntimeIntegrated());
        assertFalse(model.pythonMlReady());
        assertFalse(model.pythonLiveExecutionReady());
        assertEquals(0, model.totalArtifactPreviews());
        assertEquals(0, model.validArtifactCount());
        assertEquals(0, model.invalidArtifactCount());
        assertEquals(0, model.staleArtifactCount());
        assertEquals(0, model.checksumFailedCount());
        assertNull(model.latestArtifactPreview());
        assertTrue(model.artifactPreviews().isEmpty());
        assertEquals(0L, model.schemaVersionSummary().get(PythonEvaluationArtifactPreviewOverviewQueryService.SUPPORTED_SCHEMA_VERSION));
        assertEquals(1L, model.schemaVersionSummary().get("NO_ARTIFACT_SOURCE_CONFIGURED"));
        assertEquals(1L, model.checksumSummary().get(PythonEvaluationArtifactChecksumStatus.NOT_CHECKED.name()));
        assertEquals(1L, model.metricSummaryCoverage().get(PythonEvaluationArtifactMetricSummaryStatus.UNKNOWN.name()));
        assertHasMessage(model.blockers(), "NOT_TRADING_AUTHORIZATION");
        assertHasMessage(model.warnings(), "NO_ARTIFACT_SOURCE_CONFIGURED");
        assertHasNextStep(model.nextSteps(), "OPEN_MANIFEST_ONLY_SCHEMA_REVIEW");
        assertEquals("EVALUATION_ARTIFACT_CONTRACT", model.evidenceAnchors().getFirst().sourceType());
        assertEquals("trace-preview", model.traceId());
    }

    @Test
    void shouldKeepBaselineDiagnosticOnlyAndNotRealPerformance() {
        PythonEvaluationArtifactPreviewOverviewReadModel model = service().overview("trace-safe");

        assertEquals(0L, model.metricSummaryCoverage().get(PythonEvaluationArtifactMetricSummaryStatus.FAKE_FIXTURE_ONLY.name()));
        assertHasMessage(model.warnings(), "FAKE_FIXTURE_ONLY_NOT_REAL_PERFORMANCE");
        assertHasMessage(model.warnings(), "CHECKSUM_NOT_STRATEGY_APPROVAL");
        String rendered = model.toString().toLowerCase(Locale.ROOT);
        for (String forbidden : List.of(
                "tradeapproved",
                "tradingready",
                "liveready",
                "authorizedfortrading",
                "cantrade",
                "ready to trade",
                "apikey",
                "secret",
                "passphrase",
                "token",
                "privatekey",
                "credentialmaterial",
                "decryptedpayload",
                "encryptedpayload",
                "realorderid",
                "realaccountbalance",
                "placeorder",
                "cancelorder",
                "withdraw",
                "transfer",
                "liveexecutionready=true",
                "pythonmlready=true",
                "pythonliveexecutionready=true"
        )) {
            assertFalse(rendered.contains(forbidden), "read model must not contain " + forbidden + ": " + rendered);
        }
    }

    @Test
    void shouldRejectBlankTraceId() {
        PythonEvaluationArtifactPreviewOverviewQueryService queryService = service();

        assertThrows(IllegalArgumentException.class, () -> queryService.overview(""));
        assertThrows(IllegalArgumentException.class, () -> queryService.overview("   "));
    }

    @Test
    void shouldFailClosedWhenUnsafePreviewReadinessFlagsAreAttempted() {
        assertThrows(
                IllegalArgumentException.class,
                () -> previewItem(true, false, false),
                "liveExecutionReady=true must fail closed"
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> previewItem(false, true, false),
                "pythonMlReady=true must fail closed"
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> previewItem(false, false, true),
                "pythonLiveExecutionReady=true must fail closed"
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> overviewWithUnsafeTopLevelPythonReadiness(),
                "top-level pythonMlReady=true must fail closed"
        );
    }

    @Test
    void shouldKeepServiceWithoutFileNetworkDatabasePythonOrTradingDependencies() {
        List<String> dependencyNames = List.of(PythonEvaluationArtifactPreviewOverviewQueryService.class.getDeclaredFields()).stream()
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .map(Class::getName)
                .toList();

        assertEquals(List.of(Clock.class.getName()), dependencyNames);
        String joined = String.join("|", dependencyNames).toLowerCase(Locale.ROOT);
        for (String forbiddenDependency : List.of(
                "repository",
                "jdbc",
                "datasource",
                "path",
                "file",
                "uri",
                "url",
                "webclient",
                "restclient",
                "httpclient",
                "processbuilder",
                "runtime",
                "python",
                "runner",
                "scheduler",
                "adapter",
                "exchange",
                "account",
                "ledger",
                "order",
                "credential",
                "private"
        )) {
            assertFalse(joined.contains(forbiddenDependency), joined);
        }
        for (Method method : PythonEvaluationArtifactPreviewOverviewQueryService.class.getDeclaredMethods()) {
            String methodName = method.getName().toLowerCase(Locale.ROOT);
            for (String forbiddenPrefix : List.of(
                    "save",
                    "create",
                    "update",
                    "delete",
                    "insert",
                    "import",
                    "upload",
                    "read",
                    "load",
                    "execute",
                    "start",
                    "connect",
                    "run"
            )) {
                assertFalse(methodName.startsWith(forbiddenPrefix), methodName);
            }
        }
    }

    private PythonEvaluationArtifactPreviewOverviewQueryService service() {
        return new PythonEvaluationArtifactPreviewOverviewQueryService(FIXED_CLOCK);
    }

    private PythonEvaluationArtifactPreviewOverviewReadModel.PythonEvaluationArtifactPreviewItem previewItem(
            boolean liveExecutionReady,
            boolean pythonMlReady,
            boolean pythonLiveExecutionReady
    ) {
        return new PythonEvaluationArtifactPreviewOverviewReadModel.PythonEvaluationArtifactPreviewItem(
                "artifact-preview-fixture",
                "artifact-fixture",
                "experiment-fixture",
                "strategy-fixture",
                "v1",
                "strategy-version-fixture",
                "dataset-fixture",
                "dataset.v1",
                "pset-fixture",
                PythonEvaluationArtifactPreviewOverviewQueryService.SUPPORTED_SCHEMA_VERSION,
                "PYTHON_OFFLINE",
                PythonEvaluationArtifactChecksumStatus.NOT_CHECKED,
                PythonEvaluationArtifactFreshness.UNKNOWN,
                PythonEvaluationArtifactMetricSummaryStatus.UNKNOWN,
                "UNKNOWN",
                "UNKNOWN",
                List.of("fixture item should not be used by No-file baseline"),
                List.of("fixture only"),
                List.of(),
                "trace-preview",
                Instant.parse("2026-07-09T12:00:00Z"),
                true,
                true,
                true,
                liveExecutionReady,
                pythonMlReady,
                pythonLiveExecutionReady
        );
    }

    private PythonEvaluationArtifactPreviewOverviewReadModel overviewWithUnsafeTopLevelPythonReadiness() {
        return new PythonEvaluationArtifactPreviewOverviewReadModel(
                Instant.parse("2026-07-09T12:00:00Z"),
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                false,
                0,
                0,
                0,
                0,
                0,
                null,
                List.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "trace-preview"
        );
    }

    private void assertHasMessage(
            Iterable<PythonEvaluationArtifactPreviewOverviewReadModel.BoundaryMessage> messages,
            String code
    ) {
        for (PythonEvaluationArtifactPreviewOverviewReadModel.BoundaryMessage message : messages) {
            if (code.equals(message.code())) {
                return;
            }
        }
        throw new AssertionError("expected message code: " + code);
    }

    private void assertHasNextStep(
            Iterable<PythonEvaluationArtifactPreviewOverviewReadModel.NextStep> nextSteps,
            String code
    ) {
        for (PythonEvaluationArtifactPreviewOverviewReadModel.NextStep step : nextSteps) {
            if (code.equals(step.code())) {
                return;
            }
        }
        throw new AssertionError("expected next step code: " + code);
    }
}
