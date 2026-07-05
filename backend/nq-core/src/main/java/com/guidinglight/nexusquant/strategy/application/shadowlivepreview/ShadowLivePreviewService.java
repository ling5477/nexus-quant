package com.guidinglight.nexusquant.strategy.application.shadowlivepreview;

import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGate;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateEvidence;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateQuery;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateReason;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateService;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateStatus;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparison;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonEvidence;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonQuery;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonReason;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonService;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ShadowLivePreviewService 编排 GateQ-3 Shadow Live no-side-effect runner skeleton。
 *
 * <p>Why: GateQ-3 只允许回答“是否可以生成只读影子运行预览计划”。本 service 复用 GateQ-1
 * evaluation gate 与 GateQ-2 Paper/Shadow comparison 的只读结果，不新增 repository，不写库，不外联，
 * 不读取敏感材料，不启动策略执行、Paper run 或 Shadow run，也不生成真实执行建议。
 */
@Service
public class ShadowLivePreviewService {

    private static final String RUNNER_STATUS = "SKELETON_AVAILABLE";
    private static final String ORDER_INTENT_STATUS = "NOT_EXECUTED";

    private final StrategyEvaluationGateService evaluationGateService;
    private final PaperShadowComparisonService paperShadowComparisonService;
    private final Clock clock;

    /**
     * 生产构造器：注入 GateQ-1 / GateQ-2 只读 service。
     *
     * @param evaluationGateService GateQ-1 read-only service；不得启动 runner 或写侧流程
     * @param paperShadowComparisonService GateQ-2 read-only service；不得创建 shadow facts
     */
    @Autowired
    public ShadowLivePreviewService(
            StrategyEvaluationGateService evaluationGateService,
            PaperShadowComparisonService paperShadowComparisonService
    ) {
        this(evaluationGateService, paperShadowComparisonService, Clock.systemUTC());
    }

    ShadowLivePreviewService(
            StrategyEvaluationGateService evaluationGateService,
            PaperShadowComparisonService paperShadowComparisonService,
            Clock clock
    ) {
        this.evaluationGateService = Objects.requireNonNull(
                evaluationGateService,
                "evaluationGateService must not be null"
        );
        this.paperShadowComparisonService = Objects.requireNonNull(
                paperShadowComparisonService,
                "paperShadowComparisonService must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 生成 Shadow Live no-side-effect 只读预览。
     *
     * <p>幂等/副作用：该方法只调用 read-only GateQ-1 / GateQ-2 service 并组合响应；不会写数据库、
     * 不会访问外部网络、不会读取敏感材料、不会启动策略执行、不会启动 Paper 或 Shadow run。
     *
     * @param query 查询范围；strategyVersionId 缺失时直接 fail-closed 且不访问下游 service
     * @return preview 诊断结果；满足全部证据时最多返回 READY_FOR_NO_SIDE_EFFECT_PREVIEW
     */
    @Transactional(readOnly = true)
    public ShadowLivePreview preview(ShadowLivePreviewQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        Instant generatedAt = Instant.now(clock);
        if (query.strategyVersionId() == null) {
            return blockedMissingStrategyVersion(query, generatedAt);
        }

        StrategyEvaluationGate evaluationGate = evaluationGateService.evaluate(toEvaluationQuery(query));
        PaperShadowComparison comparison = paperShadowComparisonService.compare(toComparisonQuery(query));
        ShadowLivePreviewStatus status = previewStatus(evaluationGate, comparison);
        List<ShadowLivePreviewEvidence> requiredEvidence = requiredEvidence(evaluationGate, comparison, status);
        List<ShadowLivePreviewEvidence> missingEvidence = requiredEvidence.stream()
                .filter(evidence -> !"SATISFIED".equals(evidence.status()))
                .toList();
        return new ShadowLivePreview(
                scope(query, evaluationGate, comparison),
                firstNonBlank(query.strategyId(), evaluationGate.strategyId(), comparison.strategyId()),
                firstNonBlank(query.strategyVersionId(), evaluationGate.strategyVersionId(), comparison.strategyVersionId()),
                query.datasetId() == null ? firstDatasetId(evaluationGate, comparison) : query.datasetId(),
                firstNonBlank(query.evaluationId(), evaluationGate.evaluationId(), comparison.evaluationId()),
                firstNonBlank(query.publishId(), evaluationGate.publishId(), comparison.publishId()),
                firstNonBlank(query.paperRunId(), evaluationGate.paperRunId(), comparison.paperRunId()),
                firstNonBlank(query.shadowRunId(), comparison.shadowRunId()),
                RUNNER_STATUS,
                status,
                evaluationGate.gateStatus().name(),
                comparison.comparisonStatus().name(),
                sideEffectPolicy(),
                inputFactStatus(status),
                traceStatus(status, comparison),
                ORDER_INTENT_STATUS,
                riskPreflightPreviewStatus(status),
                requiredEvidence,
                missingEvidence,
                blockers(status, evaluationGate, comparison),
                warnings(evaluationGate, comparison),
                nextSteps(status),
                generatedAt
        );
    }

    private ShadowLivePreview blockedMissingStrategyVersion(ShadowLivePreviewQuery query, Instant generatedAt) {
        List<ShadowLivePreviewEvidence> requiredEvidence = List.of(
                missing("STRATEGY_VERSION", "strategyVersionId is required for no-side-effect preview."),
                notAvailable("EVALUATION_GATE", "Evaluation gate cannot be evaluated without strategyVersionId."),
                notAvailable("PAPER_SHADOW_COMPARISON", "Paper/Shadow comparison cannot be evaluated without strategyVersionId."),
                notAvailable("SHADOW_FACTS", "Shadow facts cannot be evaluated without strategyVersionId."),
                notAvailable("TRACE_CHAIN", "Trace chain cannot be evaluated without strategyVersionId."),
                satisfied("SIDE_EFFECT_POLICY", "All side-effect policy guards are hard-coded forbidden for this skeleton.")
        );
        return new ShadowLivePreview(
                new ShadowLivePreviewScope(
                        query.strategyId(),
                        query.strategyVersionId(),
                        query.datasetId(),
                        query.evaluationId(),
                        query.publishId(),
                        query.paperRunId(),
                        query.shadowRunId()
                ),
                query.strategyId(),
                query.strategyVersionId(),
                query.datasetId(),
                query.evaluationId(),
                query.publishId(),
                query.paperRunId(),
                query.shadowRunId(),
                RUNNER_STATUS,
                ShadowLivePreviewStatus.PREVIEW_BLOCKED_MISSING_STRATEGY_VERSION,
                "NOT_AVAILABLE",
                "NOT_AVAILABLE",
                sideEffectPolicy(),
                "MISSING",
                "NOT_AVAILABLE",
                ORDER_INTENT_STATUS,
                "NOT_EXECUTED",
                requiredEvidence,
                requiredEvidence.stream().filter(evidence -> !"SATISFIED".equals(evidence.status())).toList(),
                List.of(blocker("STRATEGY_VERSION_ID_REQUIRED", "strategyVersionId is required.")),
                baseWarnings(),
                List.of("Provide a concrete strategyVersionId and rerun the no-side-effect preview."),
                generatedAt
        );
    }

    private StrategyEvaluationGateQuery toEvaluationQuery(ShadowLivePreviewQuery query) {
        return new StrategyEvaluationGateQuery(
                query.strategyId(),
                query.strategyVersionId(),
                query.datasetId(),
                query.evaluationId(),
                query.publishId(),
                query.paperRunId()
        );
    }

    private PaperShadowComparisonQuery toComparisonQuery(ShadowLivePreviewQuery query) {
        return new PaperShadowComparisonQuery(
                query.strategyId(),
                query.strategyVersionId(),
                query.datasetId(),
                query.evaluationId(),
                query.publishId(),
                query.paperRunId(),
                query.shadowRunId()
        );
    }

    private ShadowLivePreviewStatus previewStatus(StrategyEvaluationGate gate, PaperShadowComparison comparison) {
        StrategyEvaluationGateStatus gateStatus = gate.gateStatus();
        if (gateStatus == StrategyEvaluationGateStatus.BLOCKED_MISSING_STRATEGY_VERSION) {
            return ShadowLivePreviewStatus.PREVIEW_BLOCKED_MISSING_STRATEGY_VERSION;
        }
        if (gateStatus == StrategyEvaluationGateStatus.BLOCKED_DATA_QUALITY
                || gateStatus == StrategyEvaluationGateStatus.BLOCKED_MISSING_DATASET) {
            return ShadowLivePreviewStatus.PREVIEW_BLOCKED_DATA_QUALITY;
        }
        if (gateStatus == StrategyEvaluationGateStatus.BLOCKED_MISSING_PAPER_EVIDENCE) {
            return ShadowLivePreviewStatus.PREVIEW_BLOCKED_MISSING_PAPER_EVIDENCE;
        }
        if (gateStatus != StrategyEvaluationGateStatus.READY_FOR_SHADOW_REVIEW) {
            return ShadowLivePreviewStatus.PREVIEW_BLOCKED_EVALUATION_GATE;
        }

        PaperShadowComparisonStatus comparisonStatus = comparison.comparisonStatus();
        return switch (comparisonStatus) {
            case READY_FOR_COMPARISON -> ShadowLivePreviewStatus.READY_FOR_NO_SIDE_EFFECT_PREVIEW;
            case BLOCKED_MISSING_STRATEGY_VERSION -> ShadowLivePreviewStatus.PREVIEW_BLOCKED_MISSING_STRATEGY_VERSION;
            case BLOCKED_DATA_QUALITY -> ShadowLivePreviewStatus.PREVIEW_BLOCKED_DATA_QUALITY;
            case BLOCKED_EVALUATION_GATE -> ShadowLivePreviewStatus.PREVIEW_BLOCKED_PAPER_SHADOW_COMPARISON;
            case BLOCKED_MISSING_PAPER_RUN -> ShadowLivePreviewStatus.PREVIEW_BLOCKED_MISSING_PAPER_EVIDENCE;
            case BLOCKED_SHADOW_NOT_IMPLEMENTED, BLOCKED_MISSING_SHADOW_RUN, NOT_IMPLEMENTED ->
                    ShadowLivePreviewStatus.PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE;
            case BLOCKED_TRACE_INCOMPLETE -> ShadowLivePreviewStatus.PREVIEW_BLOCKED_TRACE_CHAIN_INCOMPLETE;
            case UNKNOWN -> ShadowLivePreviewStatus.UNKNOWN;
            case NOT_AVAILABLE -> ShadowLivePreviewStatus.NOT_AVAILABLE;
        };
    }

    private List<ShadowLivePreviewEvidence> requiredEvidence(
            StrategyEvaluationGate gate,
            PaperShadowComparison comparison,
            ShadowLivePreviewStatus status
    ) {
        return List.of(
                fromGateEvidence(gate, "STRATEGY_VERSION"),
                fromGateEvidence(gate, "DATASET"),
                fromGateEvidence(gate, "EVALUATION", "EVALUATION_GATE"),
                fromGateEvidence(gate, "PUBLISH_TRACE"),
                fromGateEvidence(gate, "PAPER_EVIDENCE"),
                comparisonEvidence(comparison),
                shadowFactsEvidence(comparison),
                traceChainEvidence(comparison, status),
                satisfied("SIDE_EFFECT_POLICY", "All side-effect policy guards are forbidden for this skeleton.")
        );
    }

    private ShadowLivePreviewEvidence fromGateEvidence(StrategyEvaluationGate gate, String gateCode) {
        return fromGateEvidence(gate, gateCode, gateCode);
    }

    private ShadowLivePreviewEvidence fromGateEvidence(StrategyEvaluationGate gate, String gateCode, String previewCode) {
        return gate.requiredEvidence().stream()
                .filter(evidence -> gateCode.equals(evidence.code()))
                .findFirst()
                .map(evidence -> new ShadowLivePreviewEvidence(previewCode, evidence.status(), evidence.message()))
                .orElseGet(() -> notAvailable(previewCode, previewCode + " evidence is not available."));
    }

    private ShadowLivePreviewEvidence comparisonEvidence(PaperShadowComparison comparison) {
        if (comparison.comparisonStatus() == PaperShadowComparisonStatus.READY_FOR_COMPARISON) {
            return satisfied("PAPER_SHADOW_COMPARISON", "Paper/Shadow comparison evidence is ready for read-only preview.");
        }
        return failed("PAPER_SHADOW_COMPARISON", "Paper/Shadow comparison is blocked: "
                + comparison.comparisonStatus().name());
    }

    private ShadowLivePreviewEvidence shadowFactsEvidence(PaperShadowComparison comparison) {
        String shadowEvidenceStatus = comparison.shadowEvidenceStatus();
        if ("SATISFIED".equals(shadowEvidenceStatus)) {
            return satisfied("SHADOW_FACTS", "Shadow facts are available for preview trace.");
        }
        if ("NOT_IMPLEMENTED".equals(shadowEvidenceStatus)) {
            return notImplemented("SHADOW_FACTS", "Shadow facts are not implemented in current production fact source.");
        }
        if ("NOT_AVAILABLE".equals(shadowEvidenceStatus)) {
            return notAvailable("SHADOW_FACTS", "Shadow facts are not available for this scope.");
        }
        return failed("SHADOW_FACTS", "Shadow facts are not sufficient for no-side-effect preview.");
    }

    private ShadowLivePreviewEvidence traceChainEvidence(
            PaperShadowComparison comparison,
            ShadowLivePreviewStatus status
    ) {
        if (status == ShadowLivePreviewStatus.READY_FOR_NO_SIDE_EFFECT_PREVIEW) {
            return satisfied("TRACE_CHAIN", "Strategy, dataset, evaluation, publish, Paper and Shadow IDs are consistent.");
        }
        return comparison.requiredEvidence().stream()
                .filter(evidence -> "TRACE_CHAIN".equals(evidence.code()))
                .findFirst()
                .map(evidence -> new ShadowLivePreviewEvidence("TRACE_CHAIN", evidence.status(), evidence.message()))
                .orElseGet(() -> notAvailable("TRACE_CHAIN", "Trace chain is not available for this preview."));
    }

    private List<ShadowLivePreviewReason> blockers(
            ShadowLivePreviewStatus status,
            StrategyEvaluationGate gate,
            PaperShadowComparison comparison
    ) {
        if (status == ShadowLivePreviewStatus.READY_FOR_NO_SIDE_EFFECT_PREVIEW) {
            return List.of();
        }
        List<ShadowLivePreviewReason> blockers = new ArrayList<>();
        blockers.add(primaryBlocker(status));
        gate.blockers().stream().map(this::fromGateReason).forEach(blockers::add);
        comparison.blockers().stream().map(this::fromComparisonReason).forEach(blockers::add);
        return blockers;
    }

    private ShadowLivePreviewReason primaryBlocker(ShadowLivePreviewStatus status) {
        return switch (status) {
            case PREVIEW_BLOCKED_MISSING_STRATEGY_VERSION ->
                    blocker("MISSING_STRATEGY_VERSION", "Strategy version is missing or cannot be resolved.");
            case PREVIEW_BLOCKED_DATA_QUALITY ->
                    blocker("DATA_QUALITY_BLOCKED", "Dataset quality is insufficient for preview.");
            case PREVIEW_BLOCKED_MISSING_PAPER_EVIDENCE ->
                    blocker("PAPER_EVIDENCE_MISSING", "Comparable SIM Paper evidence is missing.");
            case PREVIEW_BLOCKED_EVALUATION_GATE ->
                    blocker("EVALUATION_GATE_BLOCKED", "Evaluation gate is not passed.");
            case PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE ->
                    blocker("SHADOW_FACTS_NOT_AVAILABLE", "Shadow facts are not available for preview.");
            case PREVIEW_BLOCKED_TRACE_CHAIN_INCOMPLETE ->
                    blocker("TRACE_CHAIN_INCOMPLETE", "Trace chain is incomplete.");
            case PREVIEW_BLOCKED_PAPER_SHADOW_COMPARISON ->
                    blocker("PAPER_SHADOW_COMPARISON_BLOCKED", "Paper/Shadow comparison is blocked.");
            case UNKNOWN, NOT_AVAILABLE ->
                    blocker("FACTS_NOT_AVAILABLE", "Local facts are unavailable or unknown.");
            case READY_FOR_NO_SIDE_EFFECT_PREVIEW ->
                    blocker("NO_BLOCKER", "No blocker.");
        };
    }

    private List<ShadowLivePreviewReason> warnings(StrategyEvaluationGate gate, PaperShadowComparison comparison) {
        List<ShadowLivePreviewReason> warnings = new ArrayList<>(baseWarnings());
        gate.warnings().stream().map(this::fromGateReason).forEach(warnings::add);
        comparison.warnings().stream().map(this::fromComparisonReason).forEach(warnings::add);
        return warnings;
    }

    private List<ShadowLivePreviewReason> baseWarnings() {
        return List.of(
                warning(
                        "SHADOW_LIVE_SKELETON_NOT_TRADING_AUTHORIZATION",
                        "Shadow Live skeleton is preview-only validation; it is not trading authorization."
                ),
                warning(
                        "NO_SIDE_EFFECT_PREVIEW_ONLY",
                        "This preview does not execute strategy logic, submit instructions, mutate ledger or alter accounts."
                ),
                warning(
                        "REAL_SHADOW_RUNNER_NOT_IMPLEMENTED",
                        "Real Shadow runner remains not implemented; this endpoint only exposes the skeleton preview contract."
                )
        );
    }

    private List<String> nextSteps(ShadowLivePreviewStatus status) {
        return switch (status) {
            case READY_FOR_NO_SIDE_EFFECT_PREVIEW -> List.of(
                    "Use this result only as a read-only Shadow Live preview plan.",
                    "Do not start strategy execution, Paper run, Shadow run, external I/O or trading-state mutation."
            );
            case PREVIEW_BLOCKED_MISSING_STRATEGY_VERSION -> List.of(
                    "Provide an existing ACTIVE strategyVersionId and rerun the preview."
            );
            case PREVIEW_BLOCKED_DATA_QUALITY -> List.of(
                    "Fix or select dataset quality evidence before requesting Shadow Live preview."
            );
            case PREVIEW_BLOCKED_MISSING_PAPER_EVIDENCE -> List.of(
                    "Select an existing SIM Paper run with comparable evidence."
            );
            case PREVIEW_BLOCKED_EVALUATION_GATE -> List.of(
                    "Resolve Strategy Evaluation Gate blockers before Shadow Live preview."
            );
            case PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE -> List.of(
                    "Do not fabricate Shadow facts; implement or bind Shadow read-only fact source in a separate approved task."
            );
            case PREVIEW_BLOCKED_TRACE_CHAIN_INCOMPLETE -> List.of(
                    "Repair strategyVersion -> dataset -> evaluation -> publish -> Paper -> Shadow trace chain before preview."
            );
            case PREVIEW_BLOCKED_PAPER_SHADOW_COMPARISON -> List.of(
                    "Resolve Paper/Shadow comparison blockers before preview."
            );
            case UNKNOWN, NOT_AVAILABLE -> List.of(
                    "Verify local fact-source availability; do not infer preview readiness from missing facts."
            );
        };
    }

    private String inputFactStatus(ShadowLivePreviewStatus status) {
        return switch (status) {
            case READY_FOR_NO_SIDE_EFFECT_PREVIEW -> "SATISFIED";
            case PREVIEW_BLOCKED_MISSING_STRATEGY_VERSION -> "MISSING";
            case UNKNOWN -> "UNKNOWN";
            case NOT_AVAILABLE -> "NOT_AVAILABLE";
            default -> "BLOCKED";
        };
    }

    private String traceStatus(ShadowLivePreviewStatus status, PaperShadowComparison comparison) {
        if (status == ShadowLivePreviewStatus.READY_FOR_NO_SIDE_EFFECT_PREVIEW) {
            return "PREVIEW_ONLY";
        }
        if (status == ShadowLivePreviewStatus.PREVIEW_BLOCKED_TRACE_CHAIN_INCOMPLETE) {
            return "BLOCKED_TRACE_INCOMPLETE";
        }
        if (status == ShadowLivePreviewStatus.PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE) {
            return comparison.shadowRunStatus();
        }
        return "NOT_EXECUTED";
    }

    private String riskPreflightPreviewStatus(ShadowLivePreviewStatus status) {
        return status == ShadowLivePreviewStatus.READY_FOR_NO_SIDE_EFFECT_PREVIEW ? "PREVIEW_ONLY" : "NOT_EXECUTED";
    }

    private List<ShadowLivePreviewSideEffectPolicy> sideEffectPolicy() {
        return List.of(
                policy("NO_DB_WRITE", "Persistence mutation is forbidden for this preview."),
                policy("NO_EXTERNAL_IO", "External I/O is forbidden for this preview."),
                policy("NO_CREDENTIAL_ACCESS", "Sensitive material access is forbidden for this preview."),
                policy("NO_PRIVATE_ENDPOINT", "Signed or private provider route access is forbidden for this preview."),
                policy("NO_ORDER_SUBMISSION", "Execution submission is forbidden for this preview."),
                policy("NO_LEDGER_MUTATION", "Ledger mutation is forbidden for this preview."),
                policy("NO_ACCOUNT_MUTATION", "Account mutation is forbidden for this preview.")
        );
    }

    private ShadowLivePreviewSideEffectPolicy policy(String code, String message) {
        return new ShadowLivePreviewSideEffectPolicy(code, "FORBIDDEN", message);
    }

    private ShadowLivePreviewScope scope(
            ShadowLivePreviewQuery query,
            StrategyEvaluationGate gate,
            PaperShadowComparison comparison
    ) {
        return new ShadowLivePreviewScope(
                firstNonBlank(query.strategyId(), gate.scope().strategyId(), comparison.scope().strategyId()),
                firstNonBlank(query.strategyVersionId(), gate.scope().strategyVersionId(), comparison.scope().strategyVersionId()),
                query.datasetId() == null ? firstDatasetId(gate, comparison) : query.datasetId(),
                firstNonBlank(query.evaluationId(), gate.scope().evaluationId(), comparison.scope().evaluationId()),
                firstNonBlank(query.publishId(), gate.scope().publishId(), comparison.scope().publishId()),
                firstNonBlank(query.paperRunId(), gate.scope().paperRunId(), comparison.scope().paperRunId()),
                firstNonBlank(query.shadowRunId(), comparison.scope().shadowRunId())
        );
    }

    private java.util.UUID firstDatasetId(StrategyEvaluationGate gate, PaperShadowComparison comparison) {
        return gate.datasetId() == null ? comparison.datasetId() : gate.datasetId();
    }

    private ShadowLivePreviewReason fromGateReason(StrategyEvaluationGateReason reason) {
        return new ShadowLivePreviewReason("EVALUATION_GATE_" + reason.code(), reason.severity(), reason.message());
    }

    private ShadowLivePreviewReason fromComparisonReason(PaperShadowComparisonReason reason) {
        return new ShadowLivePreviewReason("PAPER_SHADOW_" + reason.code(), reason.severity(), reason.message());
    }

    private ShadowLivePreviewEvidence satisfied(String code, String message) {
        return new ShadowLivePreviewEvidence(code, "SATISFIED", message);
    }

    private ShadowLivePreviewEvidence missing(String code, String message) {
        return new ShadowLivePreviewEvidence(code, "MISSING", message);
    }

    private ShadowLivePreviewEvidence failed(String code, String message) {
        return new ShadowLivePreviewEvidence(code, "FAILED", message);
    }

    private ShadowLivePreviewEvidence notAvailable(String code, String message) {
        return new ShadowLivePreviewEvidence(code, "NOT_AVAILABLE", message);
    }

    private ShadowLivePreviewEvidence notImplemented(String code, String message) {
        return new ShadowLivePreviewEvidence(code, "NOT_IMPLEMENTED", message);
    }

    private ShadowLivePreviewReason blocker(String code, String message) {
        return new ShadowLivePreviewReason(code, "BLOCKER", message);
    }

    private ShadowLivePreviewReason warning(String code, String message) {
        return new ShadowLivePreviewReason(code, "WARNING", message);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
