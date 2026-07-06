package com.guidinglight.nexusquant.strategy.application.shadowrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyComparisonStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEventType;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * GateR-5 Paper vs Shadow consistency report service。
 *
 * <p>职责：把调用方提供的 Paper run summary 与 Shadow Run summary 做本地只读比较，生成并
 * 持久化 {@code shadow_consistency_reports}。Why：GateR-5 需要可审计的一致性复盘，但仍然
 * 禁止真实交易所访问、credential 读取、private endpoint、下单、撤单、转账、提现、真实账户/ledger
 * mutation、API 暴露、scheduler 和后台 runner。
 *
 * <p>事务/副作用：本 service 仅通过 {@link ShadowRunFactRepository#createConsistencyReport}
 * 写入本地 report，并追加 {@link ShadowRunEventType#CONSISTENCY_REPORT_GENERATED} 本地审计事件。
 * service 不依赖 adapter/gateway/client，也不把 comparison status 写成交易放行。
 */
@Service
public class ShadowConsistencyReportService {

    private static final String SCHEMA_VERSION = "shadow-consistency-report.v1";
    private static final String COMPARISON_MODE = "CALLER_SUPPLIED_READONLY_SUMMARY";

    private final ShadowRunFactRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 生产构造器。
     *
     * @param repository Shadow Run 本地事实 repository；只允许本地 report/event 写入
     * @param objectMapper JSON 构造工具；不读取文件、不访问外部系统
     */
    @Autowired
    public ShadowConsistencyReportService(ShadowRunFactRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, Clock.systemUTC());
    }

    ShadowConsistencyReportService(ShadowRunFactRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 生成并持久化一次 Paper vs Shadow consistency report。
     *
     * <p>参数语义：command 内所有比较输入均由调用方提供，本方法不会主动查询 Paper 交易事实、
     * 调用 Shadow runner、访问交易所、读取 credential store 或修改真实账户/订单/ledger。
     *
     * @param command 本地只读比较命令
     * @return 已持久化 report 的只读结果；不表示交易放行或 LIVE readiness
     */
    public ShadowConsistencyReportResult generate(ShadowConsistencyReportCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        ShadowRun run = repository.findById(command.shadowRunId())
                .orElseThrow(() -> new ShadowConsistencyReportException(
                        "shadow run not found for consistency report: " + command.shadowRunId(),
                        null
                ));
        if (!run.id().equals(command.shadowRunId())) {
            throw new ShadowConsistencyReportException("shadow run id mismatch", null);
        }

        try {
            ComparisonOutcome outcome = compare(command, run);
            Instant now = Instant.now(clock);
            ShadowConsistencyReport report = new ShadowConsistencyReport(
                    UUID.randomUUID(),
                    run.id(),
                    outcome.paperRunId(),
                    outcome.status(),
                    outcome.metricDelta(),
                    outcome.divergenceReasons(),
                    outcome.limitations(),
                    now,
                    command.traceId(),
                    now
            );
            ShadowConsistencyReport persisted = repository.createConsistencyReport(report);
            appendGeneratedEvent(run, persisted, command, outcome);
            return result(persisted, command);
        } catch (RuntimeException ex) {
            if (ex instanceof ShadowConsistencyReportException) {
                throw ex;
            }
            throw new ShadowConsistencyReportException("failed to generate shadow consistency report", ex);
        }
    }

    private ComparisonOutcome compare(ShadowConsistencyReportCommand command, ShadowRun run) {
        ObjectNode metricDelta = objectMapper.createObjectNode()
                .put("schemaVersion", SCHEMA_VERSION)
                .put("comparisonMode", COMPARISON_MODE)
                .put("shadowRunId", run.id().toString())
                .put("traceId", command.traceId())
                .put("countTolerance", command.threshold().countTolerance())
                .put("decimalTolerance", command.threshold().decimalTolerance().toPlainString());
        ArrayNode metrics = objectMapper.createArrayNode();
        metricDelta.set("metrics", metrics);
        ArrayNode divergenceReasons = objectMapper.createArrayNode();
        ArrayNode limitations = objectMapper.createArrayNode();
        String paperRunId = resolvePaperRunId(command, run);
        if (paperRunId != null) {
            metricDelta.put("paperRunId", paperRunId);
        }

        if (command.hasComparisonFailure()) {
            addReason(divergenceReasons, command.comparisonFailureCode(), command.comparisonFailureMessage(),
                    "comparison", "FAILED");
            addLimitation(limitations, "COMPARISON_PROCESS_FAILED",
                    "Caller reported a local comparison failure; report is persisted for audit.", "comparison");
            return new ComparisonOutcome(
                    ShadowConsistencyComparisonStatus.FAILED,
                    paperRunId,
                    metricDelta,
                    divergenceReasons,
                    limitations
            );
        }

        boolean notComparable = collectNotComparableLimitations(command, run, limitations);
        if (notComparable) {
            return new ComparisonOutcome(
                    ShadowConsistencyComparisonStatus.NOT_COMPARABLE,
                    paperRunId,
                    metricDelta,
                    divergenceReasons,
                    limitations
            );
        }

        PaperRunComparisonInput paper = command.paperInput();
        ShadowRunComparisonInput shadow = command.shadowInput();
        compareExact("side", paper.actualPaperSide(), shadow.expectedSide(), metrics, divergenceReasons, "SIDE_MISMATCH");
        compareExact("symbol", paper.symbol(), shadow.symbol(), metrics, divergenceReasons, "SYMBOL_MISMATCH");
        compareExact("timeframe", paper.timeframe(), shadow.timeframe(), metrics, divergenceReasons,
                "TIMEFRAME_MISMATCH");
        compareExact("strategyVersionId", paper.strategyVersionId(), shadow.strategyVersionId(), metrics,
                divergenceReasons, "STRATEGY_VERSION_MISMATCH");
        compareExact("datasetId", stringify(paper.datasetId()), stringify(shadow.datasetId()), metrics,
                divergenceReasons, "DATASET_MISMATCH");

        List<Boolean> countComparisons = new ArrayList<>();
        countComparisons.add(compareCount("orderIntentCount", paper.paperOrderCount(), shadow.shadowOrderIntentCount(),
                command.threshold(), metrics, divergenceReasons, limitations));
        countComparisons.add(compareCount("blockedCount", paper.paperBlockedCount(), shadow.shadowBlockedCount(),
                command.threshold(), metrics, divergenceReasons, limitations));
        countComparisons.add(compareCount("warningCount", paper.paperWarningCount(), shadow.shadowWarningCount(),
                command.threshold(), metrics, divergenceReasons, limitations));

        boolean hasComparableMetric = countComparisons.stream().anyMatch(Boolean::booleanValue)
                || metrics.size() > countComparisons.size();
        if (divergenceReasons.size() > 0) {
            return new ComparisonOutcome(
                    ShadowConsistencyComparisonStatus.DIVERGED,
                    paperRunId,
                    metricDelta,
                    divergenceReasons,
                    limitations
            );
        }
        if (limitations.size() > 0 && hasComparableMetric) {
            return new ComparisonOutcome(
                    ShadowConsistencyComparisonStatus.PARTIAL,
                    paperRunId,
                    metricDelta,
                    divergenceReasons,
                    limitations
            );
        }
        return new ComparisonOutcome(
                ShadowConsistencyComparisonStatus.CONSISTENT,
                paperRunId,
                metricDelta,
                divergenceReasons,
                limitations
        );
    }

    private boolean collectNotComparableLimitations(
            ShadowConsistencyReportCommand command,
            ShadowRun run,
            ArrayNode limitations
    ) {
        boolean notComparable = false;
        if (isBlank(resolvePaperRunId(command, run))) {
            addLimitation(limitations, "MISSING_PAPER_RUN_ID",
                    "paperRunId is required before Paper vs Shadow comparison can be persisted as comparable.",
                    "paperRunId");
            notComparable = true;
        }
        if (command.paperInput() == null) {
            addLimitation(limitations, "MISSING_PAPER_INPUT",
                    "Paper comparison input is missing.", "paperInput");
            notComparable = true;
        }
        if (command.shadowInput() == null) {
            addLimitation(limitations, "MISSING_SHADOW_INPUT",
                    "Shadow comparison input is missing.", "shadowInput");
            notComparable = true;
        }
        if (notComparable) {
            return true;
        }

        PaperRunComparisonInput paper = command.paperInput();
        ShadowRunComparisonInput shadow = command.shadowInput();
        if (shadow.shadowRunId() == null || !shadow.shadowRunId().equals(run.id())) {
            addLimitation(limitations, "SHADOW_RUN_INPUT_MISMATCH",
                    "Shadow comparison input does not match the target Shadow Run.", "shadowRunId");
            notComparable = true;
        }
        if (missingCriticalIdentity(paper, shadow, limitations)) {
            notComparable = true;
        }
        if (paper.windowStart() == null || paper.windowEnd() == null
                || shadow.windowStart() == null || shadow.windowEnd() == null) {
            addLimitation(limitations, "MISSING_WINDOW",
                    "Both Paper and Shadow windows are required for comparable report generation.", "window");
            notComparable = true;
        } else if (!paper.windowStart().equals(shadow.windowStart()) || !paper.windowEnd().equals(shadow.windowEnd())) {
            addLimitation(limitations, "WINDOW_MISMATCH",
                    "Paper and Shadow windows do not match; comparison is not comparable.", "window");
            notComparable = true;
        }
        return notComparable;
    }

    private boolean missingCriticalIdentity(
            PaperRunComparisonInput paper,
            ShadowRunComparisonInput shadow,
            ArrayNode limitations
    ) {
        boolean missing = false;
        missing |= requireIdentity(paper.symbol(), shadow.symbol(), limitations, "MISSING_SYMBOL", "symbol");
        missing |= requireIdentity(paper.timeframe(), shadow.timeframe(), limitations, "MISSING_TIMEFRAME", "timeframe");
        missing |= requireIdentity(paper.strategyVersionId(), shadow.strategyVersionId(), limitations,
                "MISSING_STRATEGY_VERSION", "strategyVersionId");
        if (paper.datasetId() == null || shadow.datasetId() == null) {
            addLimitation(limitations, "MISSING_DATASET_ID",
                    "Both Paper and Shadow datasetId are required for comparable report generation.", "datasetId");
            missing = true;
        }
        return missing;
    }

    private boolean requireIdentity(
            String paperValue,
            String shadowValue,
            ArrayNode limitations,
            String code,
            String metricName
    ) {
        if (isBlank(paperValue) || isBlank(shadowValue)) {
            addLimitation(limitations, code,
                    "Both Paper and Shadow " + metricName + " are required for comparable report generation.",
                    metricName);
            return true;
        }
        return false;
    }

    private void compareExact(
            String metricName,
            String paperValue,
            String shadowValue,
            ArrayNode metrics,
            ArrayNode divergenceReasons,
            String reasonCode
    ) {
        boolean match = normalize(paperValue).equals(normalize(shadowValue));
        metrics.add(objectMapper.valueToTree(new ConsistencyMetricDelta(
                metricName,
                paperValue,
                shadowValue,
                match ? "MATCH" : "MISMATCH",
                "exact",
                true,
                match
        )));
        if (!match) {
            addReason(divergenceReasons, reasonCode,
                    metricName + " differs between Paper and Shadow.", metricName, "DIVERGED");
        }
    }

    private boolean compareCount(
            String metricName,
            Integer paperValue,
            Integer shadowValue,
            ConsistencyThreshold threshold,
            ArrayNode metrics,
            ArrayNode divergenceReasons,
            ArrayNode limitations
    ) {
        if (paperValue == null || shadowValue == null) {
            metrics.add(objectMapper.valueToTree(new ConsistencyMetricDelta(
                    metricName,
                    paperValue == null ? null : paperValue.toString(),
                    shadowValue == null ? null : shadowValue.toString(),
                    "NOT_COMPARABLE",
                    "abs<=" + threshold.countTolerance(),
                    false,
                    false
            )));
            addLimitation(limitations, "METRIC_NOT_AVAILABLE",
                    metricName + " is missing on one side; report is only partially comparable.", metricName);
            return false;
        }
        int delta = shadowValue - paperValue;
        boolean withinTolerance = Math.abs(delta) <= threshold.countTolerance();
        metrics.add(objectMapper.valueToTree(new ConsistencyMetricDelta(
                metricName,
                paperValue.toString(),
                shadowValue.toString(),
                Integer.toString(delta),
                "abs<=" + threshold.countTolerance(),
                true,
                withinTolerance
        )));
        if (!withinTolerance) {
            addReason(divergenceReasons, "COUNT_DELTA_EXCEEDED",
                    metricName + " delta exceeds configured tolerance.", metricName, "DIVERGED");
        }
        return true;
    }

    private void appendGeneratedEvent(
            ShadowRun run,
            ShadowConsistencyReport report,
            ShadowConsistencyReportCommand command,
            ComparisonOutcome outcome
    ) {
        ObjectNode metadata = objectMapper.createObjectNode()
                .put("reportId", report.id().toString())
                .put("comparisonStatus", outcome.status().name())
                .put("comparisonMode", COMPARISON_MODE)
                .put("diagnosticOnly", true)
                .put("noOrderSubmission", true)
                .put("noCredentialAccess", true)
                .put("noPrivateEndpoint", true)
                .put("noLedgerMutation", true)
                .put("noAccountMutation", true)
                .put("noExternalPrivateIo", true);
        repository.appendEvent(new ShadowRunEvent(
                UUID.randomUUID(),
                run.id(),
                ShadowRunEventType.CONSISTENCY_REPORT_GENERATED,
                run.status(),
                run.status(),
                "CONSISTENCY_REPORT_GENERATED",
                "Paper vs Shadow consistency report generated from caller supplied readonly summaries.",
                metadata,
                command.requestId(),
                command.traceId(),
                Instant.now(clock)
        ));
    }

    private ShadowConsistencyReportResult result(ShadowConsistencyReport report, ShadowConsistencyReportCommand command) {
        return new ShadowConsistencyReportResult(
                report.id(),
                report.shadowRunId(),
                report.paperRunId(),
                report.comparisonStatus(),
                report.metricDelta(),
                report.divergenceReasons(),
                report.limitations(),
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                command.requestId(),
                command.traceId(),
                report.generatedAt()
        );
    }

    private String resolvePaperRunId(ShadowConsistencyReportCommand command, ShadowRun run) {
        if (command.paperInput() != null && !isBlank(command.paperInput().paperRunId())) {
            return command.paperInput().paperRunId();
        }
        return PaperRunComparisonInput.trimToNull(run.paperRunId());
    }

    private void addReason(ArrayNode reasons, String code, String message, String metricName, String severity) {
        reasons.add(objectMapper.createObjectNode()
                .put("code", code)
                .put("message", isBlank(message) ? code : message)
                .put("metricName", metricName)
                .put("severity", severity));
    }

    private void addLimitation(ArrayNode limitations, String code, String message, String metricName) {
        limitations.add(objectMapper.createObjectNode()
                .put("code", code)
                .put("message", message)
                .put("metricName", metricName));
    }

    private String stringify(Object value) {
        return value == null ? null : value.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ComparisonOutcome(
            ShadowConsistencyComparisonStatus status,
            String paperRunId,
            JsonNode metricDelta,
            JsonNode divergenceReasons,
            JsonNode limitations
    ) {
    }
}
