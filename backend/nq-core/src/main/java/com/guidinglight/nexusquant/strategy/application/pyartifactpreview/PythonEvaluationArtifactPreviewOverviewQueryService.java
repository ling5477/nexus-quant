package com.guidinglight.nexusquant.strategy.application.pyartifactpreview;

import com.guidinglight.nexusquant.strategy.application.pyartifactpreview.PythonEvaluationArtifactPreviewOverviewReadModel.BoundaryMessage;
import com.guidinglight.nexusquant.strategy.application.pyartifactpreview.PythonEvaluationArtifactPreviewOverviewReadModel.EvidenceAnchor;
import com.guidinglight.nexusquant.strategy.application.pyartifactpreview.PythonEvaluationArtifactPreviewOverviewReadModel.NextStep;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PythonEvaluationArtifactPreviewOverviewQueryService 组装 GateT-4 No-file baseline overview。
 *
 * <p>职责：返回 Python Evaluation Artifact binding preview 的安全空基线。该 service 只有 Clock 依赖，
 * 不依赖 repository、JDBC、文件路径、manifest reader、HTTP client、Python subprocess、runner、adapter、
 * account、order、ledger 或 credential service。
 */
@Service
public class PythonEvaluationArtifactPreviewOverviewQueryService {

    public static final String SUPPORTED_SCHEMA_VERSION = "python-evaluation-artifact.v1";

    private final Clock clock;

    /** 生产构造器使用 UTC clock，保证 generatedAt 可审计。 */
    @Autowired
    public PythonEvaluationArtifactPreviewOverviewQueryService() {
        this(Clock.systemUTC());
    }

    PythonEvaluationArtifactPreviewOverviewQueryService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 查询 Python Evaluation Artifact binding preview overview。
     *
     * <p>事务：read-only。副作用：无。No-file baseline 不读取 artifact 文件、不读取 manifest、
     * 不接受路径、不访问网络、不执行 Python、不读 DB artifact catalog。返回 0 个 preview item 是安全基线，
     * 不是错误，也不是 ML ready 或 live execution ready。
     *
     * @param traceId 当前请求 trace id
     * @return GateT-4 No-file baseline read model
     */
    @Transactional(readOnly = true)
    public PythonEvaluationArtifactPreviewOverviewReadModel overview(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        Instant generatedAt = clock.instant();
        return new PythonEvaluationArtifactPreviewOverviewReadModel(
                generatedAt,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                0,
                0,
                0,
                0,
                0,
                null,
                List.of(),
                schemaVersionSummary(),
                checksumSummary(),
                metricSummaryCoverage(),
                blockers(),
                warnings(),
                nextSteps(),
                evidenceAnchors(generatedAt, traceId),
                traceId
        );
    }

    private Map<String, Long> schemaVersionSummary() {
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put(SUPPORTED_SCHEMA_VERSION, 0L);
        summary.put("NO_ARTIFACT_SOURCE_CONFIGURED", 1L);
        return summary;
    }

    private Map<String, Long> checksumSummary() {
        Map<String, Long> summary = new LinkedHashMap<>();
        for (PythonEvaluationArtifactChecksumStatus status : PythonEvaluationArtifactChecksumStatus.values()) {
            summary.put(status.name(), status == PythonEvaluationArtifactChecksumStatus.NOT_CHECKED ? 1L : 0L);
        }
        return summary;
    }

    private Map<String, Long> metricSummaryCoverage() {
        Map<String, Long> summary = new LinkedHashMap<>();
        for (PythonEvaluationArtifactMetricSummaryStatus status : PythonEvaluationArtifactMetricSummaryStatus.values()) {
            summary.put(status.name(), status == PythonEvaluationArtifactMetricSummaryStatus.UNKNOWN ? 1L : 0L);
        }
        return summary;
    }

    private List<BoundaryMessage> blockers() {
        return List.of(
                message("LIVE_DISABLED", "CRITICAL", "LIVE is disabled; artifact preview cannot authorize execution.", "SYSTEM_BOUNDARY", null),
                message("REAL_PROVIDER_NOT_IMPLEMENTED", "CRITICAL", "Real provider is not implemented.", "SYSTEM_BOUNDARY", null),
                message("PRIVATE_TRADING_NOT_IMPLEMENTED", "CRITICAL", "Private trading adapter is not implemented.", "SYSTEM_BOUNDARY", null),
                message("NOT_TRADING_AUTHORIZATION", "CRITICAL", "Python artifact preview is not trading authorization.", "SYSTEM_BOUNDARY", null)
        );
    }

    private List<BoundaryMessage> warnings() {
        return List.of(
                message("NO_ARTIFACT_SOURCE_CONFIGURED", "WARNING", "No artifact file, manifest or runtime source is configured for GateT-4 No-file baseline.", "NO_FILE_BASELINE", null),
                message("PYTHON_ARTIFACT_PREVIEW_DIAGNOSTIC_ONLY", "WARNING", "Preview only shows diagnostic readiness and does not import Python artifact into Java facts.", "SYSTEM_BOUNDARY", null),
                message("PYTHON_ARTIFACT_NOT_ML_READY", "WARNING", "Python artifact remains offline evidence and is not Python ML readiness.", "SYSTEM_BOUNDARY", null),
                message("PYTHON_ARTIFACT_NOT_LIVE_EXECUTION_READY", "WARNING", "Python artifact is not live execution readiness.", "SYSTEM_BOUNDARY", null),
                message("FAKE_FIXTURE_ONLY_NOT_REAL_PERFORMANCE", "WARNING", "FAKE_FIXTURE_ONLY metrics must stay fixture-only and are not real strategy performance.", "METRIC_SUMMARY", null),
                message("CHECKSUM_NOT_STRATEGY_APPROVAL", "WARNING", "A future VALID checksum only means payload integrity, not strategy approval.", "CHECKSUM", null)
        );
    }

    private List<NextStep> nextSteps() {
        return List.of(
                step(
                        "KEEP_NO_FILE_BASELINE",
                        "backend",
                        "Keep this overview GET-only and No-file baseline until a separate source review is approved",
                        "No artifact file, manifest, path query, upload, request body, Python subprocess, network or DB import is added",
                        true
                ),
                step(
                        "OPEN_MANIFEST_ONLY_SCHEMA_REVIEW",
                        "operator",
                        "If artifact preview needs real files later, open a separate Manifest-only implementation and DB schema review decision",
                        "Manifest path policy, checksum, schema validation, size limit and sensitive-field guard are reviewed in a separate task",
                        true
                ),
                step(
                        "KEEP_NOT_TRADING_AUTHORIZATION",
                        "operator",
                        "Treat artifact preview as diagnostic evidence only",
                        "No Paper, Shadow or LIVE run is created and no trading approval is inferred",
                        true
                )
        );
    }

    private List<EvidenceAnchor> evidenceAnchors(Instant generatedAt, String traceId) {
        return List.of(
                new EvidenceAnchor(
                        "EVALUATION_ARTIFACT_CONTRACT",
                        "python-evaluation-artifact.v1",
                        SUPPORTED_SCHEMA_VERSION,
                        generatedAt,
                        traceId,
                        "Offline Python EvaluationArtifact contract; no artifact file is read by this endpoint."
                ),
                new EvidenceAnchor(
                        "GATET_4_WORK_ORDER",
                        "docs/current/GATET_4_PYTHON_EVALUATION_ARTIFACT_BINDING_PREVIEW_WO.md",
                        "PLAN_READY_NOT_IMPLEMENTED",
                        generatedAt,
                        traceId,
                        "GateT-4 work order authorizes only No-file baseline for this implementation."
                ),
                new EvidenceAnchor(
                        "GATES_PYTHON_RESEARCH_EVIDENCE",
                        "research/py/src/nq_research/evaluation/artifacts.py",
                        "OFFLINE_RESEARCH_ONLY",
                        generatedAt,
                        traceId,
                        "Python research artifact remains offline evidence and is not Java production binding."
                )
        );
    }

    private BoundaryMessage message(String code, String severity, String message, String sourceType, String sourceId) {
        return new BoundaryMessage(code, severity, message, sourceType, sourceId);
    }

    private NextStep step(
            String code,
            String owner,
            String action,
            String completionCondition,
            boolean boundaryCritical
    ) {
        return new NextStep(code, owner, action, completionCondition, boundaryCritical);
    }
}
