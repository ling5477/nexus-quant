package com.guidinglight.nexusquant.strategy.application.pyartifactbinding;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PythonEvaluationArtifactBindingService 校验 Python offline evaluation artifact 的只读绑定预览契约。
 *
 * <p>职责：验证 request body 中 artifact JSON 的 schemaVersion、runMode、datasetId、
 * strategyVersion、evaluationVersion、checksum、parametersHash、metrics、offline boundary 和
 * traceability fields，并输出 binding preview。该 service 不依赖 repository，不读取本地路径，
 * 不写数据库，不调用外部网络，不启动策略 / Paper run / Shadow run。
 *
 * <p>失败模式：任一关键字段缺失、mismatch、unsupported schema、非 OFFLINE runMode、metrics
 * 关键字段缺失、traceability 不完整或出现 runtime/sensitive boundary 字段时，全部 fail-closed。
 */
@Service
public class PythonEvaluationArtifactBindingService {

    private static final String SUPPORTED_SCHEMA_VERSION = "python-evaluation-artifact.v1";
    private static final String ARTIFACT_TYPE = "PYTHON_OFFLINE_EVALUATION";
    private static final Set<String> REQUIRED_OFFLINE_BOUNDARY = Set.of(
            "offline_research_only",
            "no_network_io",
            "no_credential_read",
            "no_java_runtime_write",
            "no_live_trading",
            "no_ai_runtime",
            "no_dh_runtime"
    );
    private static final Set<String> REQUIRED_METRICS = Set.of(
            "total_return",
            "max_drawdown",
            "bar_count",
            "start_time",
            "end_time"
    );
    private static final Set<String> OPTIONAL_METRICS = Set.of(
            "annualized_return",
            "win_rate",
            "profit_factor",
            "turnover",
            "exposure"
    );
    private static final Set<String> FORBIDDEN_FIELD_NAMES = Set.of(
            "liveexecution",
            "realorder",
            "credential",
            "privateendpoint",
            "brokeraccount",
            "apikey",
            "secret",
            "passphrase",
            "token",
            "privatekey",
            "artifactpath",
            "filepath",
            "localpath"
    );

    private final Clock clock;

    /** 生产构造器使用 UTC clock，保证 generatedAt 可审计。 */
    @Autowired
    public PythonEvaluationArtifactBindingService() {
        this(Clock.systemUTC());
    }

    PythonEvaluationArtifactBindingService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 生成 Python artifact binding preview。
     *
     * <p>幂等/副作用：该方法只读取 in-memory JsonNode 并组合响应；不会读取本地路径、不会访问网络、
     * 不写数据库、不导入 artifact、不启动策略执行、不启动 Paper / Shadow run。
     *
     * @param query preview 请求；artifact 为空时返回 BLOCKED_SCHEMA_INVALID
     * @return 只读 binding preview 结果
     */
    @Transactional(readOnly = true)
    public PythonEvaluationArtifactBindingPreview preview(PythonEvaluationArtifactBindingQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        Instant generatedAt = Instant.now(clock);
        ArtifactView artifact = new ArtifactView(query.artifact());
        List<PythonEvaluationArtifactBindingEvidence> evidence = new ArrayList<>();
        List<PythonEvaluationArtifactBindingReason> blockers = new ArrayList<>();
        List<PythonEvaluationArtifactBindingReason> warnings = new ArrayList<>();

        validateSchema(artifact, evidence, blockers);
        validateDryRunAndSource(query, evidence, blockers);
        validateRunMode(artifact, evidence, blockers);
        validateDataset(query, artifact, evidence, blockers);
        validateStrategyVersion(query, artifact, evidence, blockers);
        validateEvaluationVersion(query, artifact, evidence, blockers);
        validateChecksum(query, artifact, evidence, blockers);
        validateParametersHash(query, artifact, evidence, blockers);
        validateMetrics(artifact, evidence, blockers, warnings);
        validateOfflineBoundary(artifact, evidence, blockers);
        validateTraceability(artifact, evidence, blockers, query);
        validateForbiddenFields(query.artifact(), evidence, blockers);
        addBaseWarnings(artifact, warnings);

        PythonEvaluationArtifactBindingStatus status = firstBlockingStatus(blockers);
        List<PythonEvaluationArtifactBindingEvidence> missingEvidence = evidence.stream()
                .filter(item -> !"SATISFIED".equals(item.status()))
                .toList();
        return new PythonEvaluationArtifactBindingPreview(
                scope(query, artifact),
                status,
                status,
                artifact.artifactType() == null ? ARTIFACT_TYPE : artifact.artifactType(),
                artifact.runMode(),
                artifact.datasetId(),
                artifact.strategyVersion(),
                artifact.evaluationVersion(),
                artifact.parametersHash(),
                checksumStatus(query, artifact),
                schemaStatus(artifact),
                metricsStatus(artifact),
                offlineBoundaryStatus(artifact),
                traceabilityStatus(evidence),
                evidence,
                missingEvidence,
                blockers,
                warnings,
                nextSteps(status),
                generatedAt
        );
    }

    private void validateSchema(
            ArtifactView artifact,
            List<PythonEvaluationArtifactBindingEvidence> evidence,
            List<PythonEvaluationArtifactBindingReason> blockers
    ) {
        if (artifact.raw() == null || artifact.raw().isNull() || !artifact.raw().isObject()) {
            evidence.add(failed("SCHEMA_VERSION", "Artifact JSON object is required."));
            blockers.add(blocker(PythonEvaluationArtifactBindingStatus.BLOCKED_SCHEMA_INVALID, "SCHEMA_INVALID",
                    "Artifact schema is invalid or missing."));
            return;
        }
        if (isBlank(artifact.schemaVersion())) {
            evidence.add(failed("SCHEMA_VERSION", "schemaVersion is required."));
            blockers.add(blocker(PythonEvaluationArtifactBindingStatus.BLOCKED_SCHEMA_INVALID, "SCHEMA_VERSION_MISSING",
                    "Artifact schemaVersion is missing."));
            return;
        }
        if (!SUPPORTED_SCHEMA_VERSION.equals(artifact.schemaVersion())) {
            evidence.add(failed("SCHEMA_VERSION", "Unsupported schemaVersion."));
            blockers.add(blocker(
                    PythonEvaluationArtifactBindingStatus.BLOCKED_UNSUPPORTED_SCHEMA_VERSION,
                    "UNSUPPORTED_SCHEMA_VERSION",
                    "Artifact schemaVersion is not supported."
            ));
            return;
        }
        evidence.add(satisfied("SCHEMA_VERSION", "Artifact schemaVersion is supported."));
    }

    private void validateDryRunAndSource(
            PythonEvaluationArtifactBindingQuery query,
            List<PythonEvaluationArtifactBindingEvidence> evidence,
            List<PythonEvaluationArtifactBindingReason> blockers
    ) {
        if (Boolean.FALSE.equals(query.dryRun())) {
            evidence.add(failed("DRY_RUN_PREVIEW", "dryRun=false is not allowed for binding preview."));
            blockers.add(blocker(PythonEvaluationArtifactBindingStatus.BLOCKED_BOUNDARY_VIOLATION, "DRY_RUN_REQUIRED",
                    "Binding endpoint only supports read-only dry-run preview."));
        } else {
            evidence.add(satisfied("DRY_RUN_PREVIEW", "Endpoint is constrained to read-only dry-run preview."));
        }

        String source = normalize(query.source());
        if (source != null && !"PYTHON_OFFLINE".equals(source)) {
            evidence.add(failed("SOURCE", "Only PYTHON_OFFLINE source is allowed."));
            blockers.add(blocker(PythonEvaluationArtifactBindingStatus.BLOCKED_BOUNDARY_VIOLATION, "SOURCE_NOT_ALLOWED",
                    "Artifact source is outside the offline binding boundary."));
        } else {
            evidence.add(satisfied("SOURCE", "Artifact source is PYTHON_OFFLINE or endpoint-default offline."));
        }
    }

    private void validateRunMode(
            ArtifactView artifact,
            List<PythonEvaluationArtifactBindingEvidence> evidence,
            List<PythonEvaluationArtifactBindingReason> blockers
    ) {
        if (!"OFFLINE".equals(normalize(artifact.runMode()))) {
            evidence.add(failed("RUN_MODE_OFFLINE", "runMode must be OFFLINE."));
            blockers.add(blocker(
                    PythonEvaluationArtifactBindingStatus.BLOCKED_RUN_MODE_NOT_OFFLINE,
                    "RUN_MODE_NOT_OFFLINE",
                    "Artifact runMode is not OFFLINE."
            ));
            return;
        }
        evidence.add(satisfied("RUN_MODE_OFFLINE", "Artifact runMode is OFFLINE."));
    }

    private void validateDataset(
            PythonEvaluationArtifactBindingQuery query,
            ArtifactView artifact,
            List<PythonEvaluationArtifactBindingEvidence> evidence,
            List<PythonEvaluationArtifactBindingReason> blockers
    ) {
        if (isBlank(artifact.datasetId()) || isBlank(query.expectedDatasetId())
                || !Objects.equals(artifact.datasetId(), query.expectedDatasetId())) {
            evidence.add(failed("DATASET_ID", "datasetId must match expectedDatasetId."));
            blockers.add(blocker(
                    PythonEvaluationArtifactBindingStatus.BLOCKED_DATASET_MISMATCH,
                    "DATASET_MISMATCH",
                    "Artifact datasetId does not match Java expected datasetId."
            ));
            return;
        }
        evidence.add(satisfied("DATASET_ID", "datasetId matches expectedDatasetId."));
    }

    private void validateStrategyVersion(
            PythonEvaluationArtifactBindingQuery query,
            ArtifactView artifact,
            List<PythonEvaluationArtifactBindingEvidence> evidence,
            List<PythonEvaluationArtifactBindingReason> blockers
    ) {
        if (isBlank(artifact.strategyVersion()) || isBlank(query.expectedStrategyVersion())
                || !Objects.equals(artifact.strategyVersion(), query.expectedStrategyVersion())) {
            evidence.add(failed("STRATEGY_VERSION", "strategyVersion must match expectedStrategyVersion."));
            blockers.add(blocker(
                    PythonEvaluationArtifactBindingStatus.BLOCKED_STRATEGY_VERSION_MISMATCH,
                    "STRATEGY_VERSION_MISMATCH",
                    "Artifact strategyVersion does not match Java expected strategyVersion."
            ));
            return;
        }
        evidence.add(satisfied("STRATEGY_VERSION", "strategyVersion matches expectedStrategyVersion."));
    }

    private void validateEvaluationVersion(
            PythonEvaluationArtifactBindingQuery query,
            ArtifactView artifact,
            List<PythonEvaluationArtifactBindingEvidence> evidence,
            List<PythonEvaluationArtifactBindingReason> blockers
    ) {
        if (isBlank(artifact.evaluationVersion()) || isBlank(query.expectedEvaluationVersion())
                || !Objects.equals(artifact.evaluationVersion(), query.expectedEvaluationVersion())) {
            evidence.add(failed("EVALUATION_VERSION", "evaluationVersion must match expectedEvaluationVersion."));
            blockers.add(blocker(
                    PythonEvaluationArtifactBindingStatus.BLOCKED_TRACEABILITY_INCOMPLETE,
                    "EVALUATION_VERSION_MISMATCH",
                    "Artifact evaluationVersion does not match Java expected evaluationVersion."
            ));
            return;
        }
        evidence.add(satisfied("EVALUATION_VERSION", "evaluationVersion matches expectedEvaluationVersion."));
    }

    private void validateChecksum(
            PythonEvaluationArtifactBindingQuery query,
            ArtifactView artifact,
            List<PythonEvaluationArtifactBindingEvidence> evidence,
            List<PythonEvaluationArtifactBindingReason> blockers
    ) {
        if (isBlank(artifact.checksum()) || isBlank(query.expectedChecksum())
                || !Objects.equals(artifact.checksum(), query.expectedChecksum())) {
            evidence.add(failed("CHECKSUM", "checksum must match expectedChecksum."));
            blockers.add(blocker(
                    PythonEvaluationArtifactBindingStatus.BLOCKED_CHECKSUM_MISMATCH,
                    "CHECKSUM_MISMATCH",
                    "Artifact checksum does not match Java expected checksum."
            ));
            return;
        }
        evidence.add(satisfied("CHECKSUM", "checksum matches expectedChecksum."));
    }

    private void validateParametersHash(
            PythonEvaluationArtifactBindingQuery query,
            ArtifactView artifact,
            List<PythonEvaluationArtifactBindingEvidence> evidence,
            List<PythonEvaluationArtifactBindingReason> blockers
    ) {
        if (isBlank(artifact.parametersHash()) || isBlank(query.expectedParametersHash())
                || !Objects.equals(artifact.parametersHash(), query.expectedParametersHash())) {
            evidence.add(failed("PARAMETERS_HASH", "parametersHash must match expectedParametersHash."));
            blockers.add(blocker(
                    PythonEvaluationArtifactBindingStatus.BLOCKED_PARAMETERS_HASH_MISMATCH,
                    "PARAMETERS_HASH_MISMATCH",
                    "Artifact parametersHash does not match Java expected parametersHash."
            ));
            return;
        }
        evidence.add(satisfied("PARAMETERS_HASH", "parametersHash matches expectedParametersHash."));
    }

    private void validateMetrics(
            ArtifactView artifact,
            List<PythonEvaluationArtifactBindingEvidence> evidence,
            List<PythonEvaluationArtifactBindingReason> blockers,
            List<PythonEvaluationArtifactBindingReason> warnings
    ) {
        JsonNode metrics = artifact.metrics();
        if (metrics == null || !metrics.isObject()) {
            evidence.add(failed("METRICS", "metrics/evaluation object is required."));
            blockers.add(blocker(PythonEvaluationArtifactBindingStatus.BLOCKED_METRICS_INCOMPLETE, "METRICS_MISSING",
                    "Artifact metrics are missing."));
            return;
        }
        List<String> missingRequired = REQUIRED_METRICS.stream()
                .filter(metric -> !hasUsableMetric(metrics, metric))
                .toList();
        if (!missingRequired.isEmpty()) {
            evidence.add(failed("METRICS", "Required metrics are incomplete."));
            blockers.add(blocker(
                    PythonEvaluationArtifactBindingStatus.BLOCKED_METRICS_INCOMPLETE,
                    "METRICS_INCOMPLETE",
                    "Artifact metrics are missing required completeness fields."
            ));
            return;
        }
        evidence.add(satisfied("METRICS", "Required metrics are complete."));
        OPTIONAL_METRICS.stream()
                .filter(metric -> isNotAvailableMetric(metrics, metric))
                .forEach(metric -> warnings.add(warning(
                        "OPTIONAL_METRIC_NOT_AVAILABLE",
                        "Optional metric is NOT_AVAILABLE and must not be inferred as zero."
                )));
    }

    private void validateOfflineBoundary(
            ArtifactView artifact,
            List<PythonEvaluationArtifactBindingEvidence> evidence,
            List<PythonEvaluationArtifactBindingReason> blockers
    ) {
        JsonNode boundary = artifact.offlineBoundary();
        if (boundary == null || !boundary.isArray()) {
            evidence.add(failed("OFFLINE_BOUNDARY", "offlineBoundary/offline_boundary array is required."));
            blockers.add(blocker(
                    PythonEvaluationArtifactBindingStatus.BLOCKED_BOUNDARY_VIOLATION,
                    "OFFLINE_BOUNDARY_MISSING",
                    "Artifact offline boundary is missing."
            ));
            return;
        }
        List<String> boundaryValues = new ArrayList<>();
        boundary.forEach(node -> boundaryValues.add(node.asText()));
        boolean missingBoundary = REQUIRED_OFFLINE_BOUNDARY.stream().anyMatch(required -> !boundaryValues.contains(required));
        if (missingBoundary) {
            evidence.add(failed("OFFLINE_BOUNDARY", "offlineBoundary must contain all required offline notes."));
            blockers.add(blocker(
                    PythonEvaluationArtifactBindingStatus.BLOCKED_BOUNDARY_VIOLATION,
                    "OFFLINE_BOUNDARY_INCOMPLETE",
                    "Artifact offline boundary is incomplete."
            ));
            return;
        }
        evidence.add(satisfied("OFFLINE_BOUNDARY", "Artifact declares offline-only boundary notes."));
    }

    private void validateTraceability(
            ArtifactView artifact,
            List<PythonEvaluationArtifactBindingEvidence> evidence,
            List<PythonEvaluationArtifactBindingReason> blockers,
            PythonEvaluationArtifactBindingQuery query
    ) {
        boolean complete = !isBlank(artifact.experimentId())
                && !isBlank(artifact.datasetId())
                && !isBlank(artifact.strategyId())
                && !isBlank(artifact.strategyVersion())
                && !isBlank(artifact.evaluationVersion())
                && !isBlank(artifact.parametersHash())
                && !isBlank(artifact.checksum())
                && !isBlank(artifact.startTime())
                && !isBlank(artifact.endTime())
                && artifact.barCount() != null
                && !isBlank(query.expectedStrategyVersionId());
        if (!complete) {
            evidence.add(failed("TRACEABILITY", "Traceability fields are incomplete."));
            blockers.add(blocker(
                    PythonEvaluationArtifactBindingStatus.BLOCKED_TRACEABILITY_INCOMPLETE,
                    "TRACEABILITY_INCOMPLETE",
                    "Artifact traceability fields are incomplete for Java binding preview."
            ));
            return;
        }
        evidence.add(satisfied("TRACEABILITY", "Artifact traceability fields are complete for binding preview."));
    }

    private void validateForbiddenFields(
            JsonNode artifact,
            List<PythonEvaluationArtifactBindingEvidence> evidence,
            List<PythonEvaluationArtifactBindingReason> blockers
    ) {
        if (containsForbiddenFieldName(artifact)) {
            evidence.add(failed("BOUNDARY_FORBIDDEN_FIELDS", "Forbidden runtime or sensitive boundary field is present."));
            blockers.add(blocker(
                    PythonEvaluationArtifactBindingStatus.BLOCKED_BOUNDARY_VIOLATION,
                    "BOUNDARY_FORBIDDEN_FIELD_PRESENT",
                    "Artifact contains a forbidden runtime or sensitive boundary field."
            ));
            return;
        }
        evidence.add(satisfied("BOUNDARY_FORBIDDEN_FIELDS", "No forbidden runtime or sensitive boundary field is present."));
    }

    private void addBaseWarnings(ArtifactView artifact, List<PythonEvaluationArtifactBindingReason> warnings) {
        warnings.add(warning(
                "BINDING_PREVIEW_NOT_IMPORT",
                "VALID_FOR_BINDING_PREVIEW only allows read-only preview; it does not write Java facts."
        ));
        warnings.add(warning(
                "BINDING_PREVIEW_NOT_TRADING_AUTHORIZATION",
                "Python artifact binding preview is not strategy approval or trading authorization."
        ));
        warnings.add(warning(
                "PYTHON_OFFLINE_FOUNDATION_ONLY",
                "Artifact remains offline foundation evidence and is not ML or live execution readiness."
        ));
        if (isBlank(artifact.gitCommit())) {
            warnings.add(warning("GIT_COMMIT_NOT_AVAILABLE", "gitCommit is missing from artifact traceability."));
        }
        if (!artifact.hasNotes()) {
            warnings.add(warning("NOTES_NOT_AVAILABLE", "artifact notes are empty."));
        }
        if (isBlank(artifact.datasetQualityStatus())) {
            warnings.add(warning(
                    "DATASET_QUALITY_NOT_BOUND_TO_JAVA_FACT",
                    "Dataset quality status is not bound to a Java fact source in this preview."
            ));
        }
    }

    private PythonEvaluationArtifactBindingScope scope(
            PythonEvaluationArtifactBindingQuery query,
            ArtifactView artifact
    ) {
        return new PythonEvaluationArtifactBindingScope(
                isBlank(query.source()) ? "PYTHON_OFFLINE" : query.source(),
                !Boolean.FALSE.equals(query.dryRun()),
                query.expectedDatasetId(),
                query.expectedStrategyVersionId(),
                query.expectedStrategyVersion(),
                query.expectedEvaluationVersion(),
                query.expectedChecksum(),
                query.expectedParametersHash(),
                artifact.datasetId(),
                artifact.strategyVersion(),
                artifact.evaluationVersion(),
                artifact.checksum(),
                artifact.parametersHash()
        );
    }

    private PythonEvaluationArtifactBindingStatus firstBlockingStatus(List<PythonEvaluationArtifactBindingReason> blockers) {
        return blockers.stream()
                .map(reason -> PythonEvaluationArtifactBindingStatus.valueOf(reason.code()))
                .findFirst()
                .orElse(PythonEvaluationArtifactBindingStatus.VALID_FOR_BINDING_PREVIEW);
    }

    private String schemaStatus(ArtifactView artifact) {
        if (artifact.raw() == null || artifact.raw().isNull() || !artifact.raw().isObject() || isBlank(artifact.schemaVersion())) {
            return "INVALID";
        }
        return SUPPORTED_SCHEMA_VERSION.equals(artifact.schemaVersion()) ? "SUPPORTED" : "UNSUPPORTED";
    }

    private String checksumStatus(PythonEvaluationArtifactBindingQuery query, ArtifactView artifact) {
        if (isBlank(artifact.checksum()) || isBlank(query.expectedChecksum())) {
            return "MISSING";
        }
        return Objects.equals(artifact.checksum(), query.expectedChecksum()) ? "MATCHED" : "MISMATCH";
    }

    private String metricsStatus(ArtifactView artifact) {
        JsonNode metrics = artifact.metrics();
        if (metrics == null || !metrics.isObject()) {
            return "MISSING";
        }
        boolean complete = REQUIRED_METRICS.stream().allMatch(metric -> hasUsableMetric(metrics, metric));
        if (!complete) {
            return "INCOMPLETE";
        }
        boolean optionalNotAvailable = OPTIONAL_METRICS.stream().anyMatch(metric -> isNotAvailableMetric(metrics, metric));
        return optionalNotAvailable ? "COMPLETE_WITH_NOT_AVAILABLE_OPTIONAL_METRICS" : "COMPLETE";
    }

    private String offlineBoundaryStatus(ArtifactView artifact) {
        JsonNode boundary = artifact.offlineBoundary();
        if (boundary == null || !boundary.isArray()) {
            return "MISSING";
        }
        List<String> boundaryValues = new ArrayList<>();
        boundary.forEach(node -> boundaryValues.add(node.asText()));
        return REQUIRED_OFFLINE_BOUNDARY.stream().allMatch(boundaryValues::contains) ? "OFFLINE_ONLY" : "INCOMPLETE";
    }

    private String traceabilityStatus(List<PythonEvaluationArtifactBindingEvidence> evidence) {
        return evidence.stream()
                .filter(item -> "TRACEABILITY".equals(item.code()))
                .findFirst()
                .map(item -> "SATISFIED".equals(item.status()) ? "COMPLETE" : "BLOCKED")
                .orElse("UNKNOWN");
    }

    private List<String> nextSteps(PythonEvaluationArtifactBindingStatus status) {
        return switch (status) {
            case VALID_FOR_BINDING_PREVIEW -> List.of(
                    "Use this result only as read-only binding preview evidence.",
                    "Do not import artifact, write database facts, publish strategy, start Paper run or start Shadow run."
            );
            case BLOCKED_SCHEMA_INVALID, BLOCKED_UNSUPPORTED_SCHEMA_VERSION -> List.of(
                    "Regenerate the Python offline artifact with the supported schemaVersion and required fields."
            );
            case BLOCKED_RUN_MODE_NOT_OFFLINE -> List.of(
                    "Reject the artifact and regenerate it from offline research only."
            );
            case BLOCKED_DATASET_MISMATCH -> List.of(
                    "Use an artifact generated from the expected datasetId or correct the Java expected dataset anchor."
            );
            case BLOCKED_STRATEGY_VERSION_MISMATCH -> List.of(
                    "Use an artifact generated from the expected strategyVersion."
            );
            case BLOCKED_CHECKSUM_MISMATCH -> List.of(
                    "Recompute and review the artifact checksum before binding preview."
            );
            case BLOCKED_PARAMETERS_HASH_MISMATCH -> List.of(
                    "Reconcile the Python parametersHash with the Java expected parameter anchor."
            );
            case BLOCKED_METRICS_INCOMPLETE -> List.of(
                    "Regenerate the artifact with required metrics, bar_count, start_time and end_time."
            );
            case BLOCKED_TRACEABILITY_INCOMPLETE -> List.of(
                    "Add complete traceability fields before any Java binding preview."
            );
            case BLOCKED_BOUNDARY_VIOLATION -> List.of(
                    "Reject this artifact and regenerate it inside the offline/no-side-effect boundary."
            );
            case UNKNOWN, NOT_AVAILABLE -> List.of(
                    "Treat artifact facts as unavailable; do not infer readiness from missing evidence."
            );
        };
    }

    private boolean hasUsableMetric(JsonNode metrics, String metricName) {
        JsonNode node = direct(metrics, metricName);
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isTextual()) {
            String value = node.asText();
            return !value.isBlank() && !"NOT_AVAILABLE".equals(value);
        }
        return true;
    }

    private boolean isNotAvailableMetric(JsonNode metrics, String metricName) {
        JsonNode node = direct(metrics, metricName);
        return node != null && node.isTextual() && "NOT_AVAILABLE".equals(node.asText());
    }

    private boolean containsForbiddenFieldName(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String normalized = normalizeFieldName(field.getKey());
                if (FORBIDDEN_FIELD_NAMES.contains(normalized) || containsForbiddenFieldName(field.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsForbiddenFieldName(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalizeFieldName(String value) {
        return value == null ? "" : value.replace("_", "").replace("-", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private PythonEvaluationArtifactBindingEvidence satisfied(String code, String message) {
        return new PythonEvaluationArtifactBindingEvidence(code, "SATISFIED", message);
    }

    private PythonEvaluationArtifactBindingEvidence failed(String code, String message) {
        return new PythonEvaluationArtifactBindingEvidence(code, "FAILED", message);
    }

    private PythonEvaluationArtifactBindingReason blocker(
            PythonEvaluationArtifactBindingStatus status,
            String code,
            String message
    ) {
        return new PythonEvaluationArtifactBindingReason(status.name(), "BLOCKER", code + ": " + message);
    }

    private PythonEvaluationArtifactBindingReason warning(String code, String message) {
        return new PythonEvaluationArtifactBindingReason(code, "WARNING", message);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private JsonNode direct(JsonNode node, String... fieldNames) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String... fieldNames) {
        JsonNode value = direct(node, fieldNames);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private Long longValue(JsonNode node, String... fieldNames) {
        JsonNode value = direct(node, fieldNames);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isIntegralNumber()) {
            return value.longValue();
        }
        if (value.isTextual()) {
            try {
                return Long.valueOf(value.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private final class ArtifactView {
        private final JsonNode raw;
        private final JsonNode datasetManifest;
        private final JsonNode experimentMetadata;
        private final JsonNode metrics;

        private ArtifactView(JsonNode raw) {
            this.raw = raw;
            this.datasetManifest = firstObject(raw, "datasetManifest", "dataset_manifest");
            this.experimentMetadata = firstObject(raw, "experimentMetadata", "experiment_metadata");
            this.metrics = firstObject(raw, "metrics", "evaluation");
        }

        private JsonNode raw() {
            return raw;
        }

        private String schemaVersion() {
            return firstText(raw, "schemaVersion", "schema_version");
        }

        private String artifactType() {
            return firstText(raw, "artifactType", "artifact_type");
        }

        private String runMode() {
            return firstText(raw, "runMode", "run_mode", experimentMetadata, "run_mode");
        }

        private String datasetId() {
            return firstText(raw, "datasetId", "dataset_id", datasetManifest, "dataset_id");
        }

        private String strategyId() {
            return firstText(raw, "strategyId", "strategy_id", experimentMetadata, "strategy_id");
        }

        private String strategyVersion() {
            return firstText(raw, "strategyVersion", "strategy_version", experimentMetadata, "strategy_version");
        }

        private String evaluationVersion() {
            return firstText(raw, "evaluationVersion", "evaluation_version", experimentMetadata, "evaluation_version");
        }

        private String parametersHash() {
            return firstText(raw, "parametersHash", "parameters_hash", experimentMetadata, "parameters_hash");
        }

        private String checksum() {
            return firstText(raw, "checksum", datasetManifest, "checksum");
        }

        private String experimentId() {
            return firstText(raw, "experimentId", "experiment_id", experimentMetadata, "experiment_id");
        }

        private String startTime() {
            return firstText(raw, "startTime", "start_time", metrics, "start_time", datasetManifest, "start_time");
        }

        private String endTime() {
            return firstText(raw, "endTime", "end_time", metrics, "end_time", datasetManifest, "end_time");
        }

        private Long barCount() {
            Long value = firstLong(raw, "barCount", "bar_count", metrics, "bar_count");
            return value == null ? firstLong(datasetManifest, "row_count") : value;
        }

        private JsonNode metrics() {
            return metrics;
        }

        private JsonNode offlineBoundary() {
            return firstArray(raw, "offlineBoundary", "offline_boundary");
        }

        private String gitCommit() {
            return firstText(raw, "gitCommit", "git_commit", experimentMetadata, "git_commit");
        }

        private boolean hasNotes() {
            JsonNode notes = firstArray(raw, "notes", datasetManifest, "notes", experimentMetadata, "notes");
            return notes != null && notes.size() > 0;
        }

        private String datasetQualityStatus() {
            return firstText(raw, "datasetQualityStatus", "dataset_quality_status", datasetManifest, "quality_status");
        }

        private JsonNode firstObject(JsonNode node, String... fieldNames) {
            JsonNode value = direct(node, fieldNames);
            return value != null && value.isObject() ? value : null;
        }

        private JsonNode firstArray(JsonNode node, String... fieldNames) {
            JsonNode value = direct(node, fieldNames);
            return value != null && value.isArray() ? value : null;
        }

        private JsonNode firstArray(JsonNode first, String firstName, JsonNode second, String secondName, JsonNode third, String thirdName) {
            JsonNode firstValue = firstArray(first, firstName);
            if (firstValue != null) {
                return firstValue;
            }
            JsonNode secondValue = firstArray(second, secondName);
            return secondValue == null ? firstArray(third, thirdName) : secondValue;
        }

        private String firstText(JsonNode first, String firstName, String secondName) {
            return text(first, firstName, secondName);
        }

        private String firstText(JsonNode first, String firstName, String secondName, JsonNode second, String thirdName) {
            String value = text(first, firstName, secondName);
            return value == null ? text(second, thirdName) : value;
        }

        private String firstText(
                JsonNode first,
                String firstName,
                String secondName,
                JsonNode second,
                String thirdName,
                JsonNode third,
                String fourthName
        ) {
            String value = text(first, firstName, secondName);
            if (value != null) {
                return value;
            }
            String secondValue = text(second, thirdName);
            return secondValue == null ? text(third, fourthName) : secondValue;
        }

        private String firstText(JsonNode first, String firstName, JsonNode second, String secondName) {
            String value = text(first, firstName);
            return value == null ? text(second, secondName) : value;
        }

        private Long firstLong(JsonNode first, String firstName, String secondName, JsonNode second, String thirdName) {
            Long value = longValue(first, firstName, secondName);
            return value == null ? longValue(second, thirdName) : value;
        }

        private Long firstLong(JsonNode first, String firstName) {
            return longValue(first, firstName);
        }
    }
}
