package com.guidinglight.nexusquant.strategy.application.pyartifactbinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class PythonEvaluationArtifactBindingServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-05T12:00:00Z"), ZoneOffset.UTC);
    private static final String DATASET_ID = "ds_gateq4_sample";
    private static final String STRATEGY_VERSION_ID = "sv-1";
    private static final String STRATEGY_VERSION = "v1";
    private static final String EVALUATION_VERSION = "eval.v1";
    private static final String CHECKSUM = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String PARAMETERS_HASH = "params_0123456789abcdef";

    @Test
    void shouldReturnValidForBindingPreviewForOfflineArtifactWithoutSideEffects() throws Exception {
        PythonEvaluationArtifactBindingPreview preview = service().preview(validQuery(validArtifact()));

        assertEquals(PythonEvaluationArtifactBindingStatus.VALID_FOR_BINDING_PREVIEW, preview.bindingStatus());
        assertEquals(PythonEvaluationArtifactBindingStatus.VALID_FOR_BINDING_PREVIEW, preview.validationStatus());
        assertEquals("PYTHON_OFFLINE_EVALUATION", preview.artifactType());
        assertEquals("OFFLINE", preview.runMode());
        assertEquals(DATASET_ID, preview.datasetId());
        assertEquals(STRATEGY_VERSION, preview.strategyVersion());
        assertEquals(EVALUATION_VERSION, preview.evaluationVersion());
        assertEquals(PARAMETERS_HASH, preview.parametersHash());
        assertEquals("MATCHED", preview.checksumStatus());
        assertEquals("SUPPORTED", preview.schemaStatus());
        assertEquals("COMPLETE_WITH_NOT_AVAILABLE_OPTIONAL_METRICS", preview.metricsStatus());
        assertEquals("OFFLINE_ONLY", preview.offlineBoundaryStatus());
        assertEquals("COMPLETE", preview.traceabilityStatus());
        assertTrue(preview.blockers().isEmpty());
        assertTrue(preview.missingEvidence().isEmpty());
        assertHasWarning(preview, "BINDING_PREVIEW_NOT_TRADING_AUTHORIZATION");
        assertEquals(Instant.parse("2026-07-05T12:00:00Z"), preview.generatedAt());

        Method previewMethod = PythonEvaluationArtifactBindingService.class.getMethod(
                "preview",
                PythonEvaluationArtifactBindingQuery.class
        );
        Transactional transactional = previewMethod.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertTrue(transactional.readOnly());
    }

    @Test
    void shouldBlockWhenRunModeIsNotOffline() {
        ObjectNode artifact = validArtifact();
        artifact.put("runMode", "LIVE");

        PythonEvaluationArtifactBindingPreview preview = service().preview(validQuery(artifact));

        assertEquals(PythonEvaluationArtifactBindingStatus.BLOCKED_RUN_MODE_NOT_OFFLINE, preview.bindingStatus());
        assertHasEvidence(preview, "RUN_MODE_OFFLINE");
    }

    @Test
    void shouldBlockWhenSchemaVersionUnsupported() {
        ObjectNode artifact = validArtifact();
        artifact.put("schemaVersion", "python-evaluation-artifact.v99");

        PythonEvaluationArtifactBindingPreview preview = service().preview(validQuery(artifact));

        assertEquals(PythonEvaluationArtifactBindingStatus.BLOCKED_UNSUPPORTED_SCHEMA_VERSION, preview.bindingStatus());
        assertEquals("UNSUPPORTED", preview.schemaStatus());
    }

    @Test
    void shouldBlockWhenDatasetIdMismatchesExpectedAnchor() {
        ObjectNode artifact = validArtifact();
        artifact.put("datasetId", "ds_other");

        PythonEvaluationArtifactBindingPreview preview = service().preview(validQuery(artifact));

        assertEquals(PythonEvaluationArtifactBindingStatus.BLOCKED_DATASET_MISMATCH, preview.bindingStatus());
        assertHasEvidence(preview, "DATASET_ID");
    }

    @Test
    void shouldBlockWhenStrategyVersionMismatchesExpectedAnchor() {
        ObjectNode artifact = validArtifact();
        artifact.put("strategyVersion", "v2");

        PythonEvaluationArtifactBindingPreview preview = service().preview(validQuery(artifact));

        assertEquals(PythonEvaluationArtifactBindingStatus.BLOCKED_STRATEGY_VERSION_MISMATCH, preview.bindingStatus());
        assertHasEvidence(preview, "STRATEGY_VERSION");
    }

    @Test
    void shouldBlockWhenChecksumMismatchesExpectedAnchor() {
        ObjectNode artifact = validArtifact();
        artifact.put("checksum", "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

        PythonEvaluationArtifactBindingPreview preview = service().preview(validQuery(artifact));

        assertEquals(PythonEvaluationArtifactBindingStatus.BLOCKED_CHECKSUM_MISMATCH, preview.bindingStatus());
        assertEquals("MISMATCH", preview.checksumStatus());
    }

    @Test
    void shouldBlockWhenParametersHashMismatchesExpectedAnchor() {
        ObjectNode artifact = validArtifact();
        artifact.put("parametersHash", "params_other");

        PythonEvaluationArtifactBindingPreview preview = service().preview(validQuery(artifact));

        assertEquals(PythonEvaluationArtifactBindingStatus.BLOCKED_PARAMETERS_HASH_MISMATCH, preview.bindingStatus());
        assertHasEvidence(preview, "PARAMETERS_HASH");
    }

    @Test
    void shouldBlockWhenMetricsAreIncomplete() {
        ObjectNode artifact = validArtifact();
        ((ObjectNode) artifact.get("evaluation")).remove("bar_count");

        PythonEvaluationArtifactBindingPreview preview = service().preview(validQuery(artifact));

        assertEquals(PythonEvaluationArtifactBindingStatus.BLOCKED_METRICS_INCOMPLETE, preview.bindingStatus());
        assertEquals("INCOMPLETE", preview.metricsStatus());
        assertHasEvidence(preview, "METRICS");
    }

    @Test
    void shouldBlockWhenForbiddenBoundaryFieldsAppear() {
        Set<String> forbiddenFields = Set.of("liveExecution", "realOrder", "credential", "privateEndpoint", "brokerAccount");
        for (String field : forbiddenFields) {
            ObjectNode artifact = validArtifact();
            artifact.putObject(field).put("value", "blocked");

            PythonEvaluationArtifactBindingPreview preview = service().preview(validQuery(artifact));

            assertEquals(PythonEvaluationArtifactBindingStatus.BLOCKED_BOUNDARY_VIOLATION, preview.bindingStatus());
            assertHasEvidence(preview, "BOUNDARY_FORBIDDEN_FIELDS");
        }
    }

    @Test
    void shouldBlockWhenTraceabilityFieldsAreIncomplete() {
        ObjectNode artifact = validArtifact();
        artifact.remove("experimentId");
        ((ObjectNode) artifact.get("experiment_metadata")).remove("experiment_id");

        PythonEvaluationArtifactBindingPreview preview = service().preview(validQuery(artifact));

        assertEquals(PythonEvaluationArtifactBindingStatus.BLOCKED_TRACEABILITY_INCOMPLETE, preview.bindingStatus());
        assertEquals("BLOCKED", preview.traceabilityStatus());
        assertHasEvidence(preview, "TRACEABILITY");
    }

    @Test
    void shouldNotExposeTradingAuthorizationOrSensitiveTermsInReadModel() {
        PythonEvaluationArtifactBindingPreview preview = service().preview(validQuery(validArtifact()));
        String serializedShape = preview.toString();

        assertFalse(serializedShape.contains("tradingReady"));
        assertFalse(serializedShape.contains("liveReady"));
        assertFalse(serializedShape.contains("authorizedForTrading"));
        assertFalse(serializedShape.contains("TRADE_APPROVED"));
        assertFalse(serializedShape.contains("LIVE_READY"));
        assertFalse(serializedShape.contains("ML_READY"));
        assertFalse(serializedShape.contains("apiKey"));
        assertFalse(serializedShape.contains("secret"));
        assertFalse(serializedShape.contains("token"));
        assertFalse(serializedShape.contains("passphrase"));
        assertFalse(serializedShape.contains("private key"));
    }

    @Test
    void shouldKeepServiceWithoutExternalIoPersistenceOrLocalPathCollaborators() {
        Field[] fields = PythonEvaluationArtifactBindingService.class.getDeclaredFields();
        assertTrue(Arrays.stream(fields).noneMatch(field -> field.getType().getName().contains("Repository")));
        assertTrue(Arrays.stream(fields).noneMatch(field -> field.getType().getName().contains("Jdbc")));
        assertTrue(Arrays.stream(fields).noneMatch(field -> field.getType().getName().contains("WebClient")));
        assertTrue(Arrays.stream(fields).noneMatch(field -> field.getType().getName().contains("RestClient")));
        assertTrue(Arrays.stream(fields).noneMatch(field -> field.getType().getName().contains("Path")));
        assertTrue(Arrays.stream(PythonEvaluationArtifactBindingService.class.getDeclaredMethods())
                .map(Method::getName)
                .noneMatch(name -> name.startsWith("save")
                        || name.startsWith("create")
                        || name.startsWith("update")
                        || name.startsWith("delete")
                        || name.startsWith("import")
                        || name.startsWith("upload")
                        || name.startsWith("start")
                        || name.startsWith("execute")));
    }

    private PythonEvaluationArtifactBindingService service() {
        return new PythonEvaluationArtifactBindingService(FIXED_CLOCK);
    }

    private PythonEvaluationArtifactBindingQuery validQuery(JsonNode artifact) {
        return new PythonEvaluationArtifactBindingQuery(
                artifact,
                DATASET_ID,
                STRATEGY_VERSION_ID,
                STRATEGY_VERSION,
                EVALUATION_VERSION,
                CHECKSUM,
                PARAMETERS_HASH,
                "PYTHON_OFFLINE",
                true
        );
    }

    private ObjectNode validArtifact() {
        ObjectNode artifact = MAPPER.createObjectNode();
        artifact.put("schemaVersion", "python-evaluation-artifact.v1");
        artifact.put("artifactType", "PYTHON_OFFLINE_EVALUATION");
        artifact.put("runMode", "OFFLINE");
        artifact.put("datasetId", DATASET_ID);
        artifact.put("strategyId", "sample_strategy");
        artifact.put("strategyVersion", STRATEGY_VERSION);
        artifact.put("evaluationVersion", EVALUATION_VERSION);
        artifact.put("parametersHash", PARAMETERS_HASH);
        artifact.put("checksum", CHECKSUM);
        artifact.put("experimentId", "exp_gateq4_sample");
        artifact.put("gitCommit", "abcdef1");
        artifact.put("datasetQualityStatus", "OK");
        artifact.putArray("notes").add("fixture artifact for binding preview");

        ObjectNode datasetManifest = artifact.putObject("dataset_manifest");
        datasetManifest.put("dataset_id", DATASET_ID);
        datasetManifest.put("schema_version", "dataset-manifest.v1");
        datasetManifest.put("checksum", CHECKSUM);
        datasetManifest.put("quality_status", "OK");
        datasetManifest.put("row_count", 6);
        datasetManifest.put("start_time", "2025-01-01T00:00:00Z");
        datasetManifest.put("end_time", "2025-01-01T00:05:59Z");

        ObjectNode metadata = artifact.putObject("experiment_metadata");
        metadata.put("experiment_id", "exp_gateq4_sample");
        metadata.put("dataset_id", DATASET_ID);
        metadata.put("strategy_id", "sample_strategy");
        metadata.put("strategy_version", STRATEGY_VERSION);
        metadata.put("evaluation_version", EVALUATION_VERSION);
        metadata.put("parameters_hash", PARAMETERS_HASH);
        metadata.put("run_mode", "OFFLINE");
        metadata.put("git_commit", "abcdef1");

        ObjectNode evaluation = artifact.putObject("evaluation");
        evaluation.put("total_return", 0.0123);
        evaluation.put("annualized_return", "NOT_AVAILABLE");
        evaluation.put("max_drawdown", -0.001);
        evaluation.put("win_rate", "NOT_AVAILABLE");
        evaluation.put("profit_factor", "NOT_AVAILABLE");
        evaluation.put("turnover", "NOT_AVAILABLE");
        evaluation.put("exposure", "NOT_AVAILABLE");
        evaluation.put("bar_count", 6);
        evaluation.put("start_time", "2025-01-01T00:00:00Z");
        evaluation.put("end_time", "2025-01-01T00:05:59Z");

        ArrayNode boundary = artifact.putArray("offline_boundary");
        boundary.add("offline_research_only");
        boundary.add("no_network_io");
        boundary.add("no_credential_read");
        boundary.add("no_java_runtime_write");
        boundary.add("no_live_trading");
        boundary.add("no_ai_runtime");
        boundary.add("no_dh_runtime");
        return artifact;
    }

    private void assertHasEvidence(PythonEvaluationArtifactBindingPreview preview, String code) {
        boolean found = preview.requiredEvidence().stream()
                .anyMatch(evidence -> code.equals(evidence.code()));
        assertTrue(found, "expected evidence code: " + code);
    }

    private void assertHasWarning(PythonEvaluationArtifactBindingPreview preview, String code) {
        boolean found = preview.warnings().stream()
                .anyMatch(reason -> code.equals(reason.code()));
        assertTrue(found, "expected warning code: " + code);
    }
}
