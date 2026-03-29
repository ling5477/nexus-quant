package com.guidinglight.nexusquant.research.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationView;
import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.application.command.BacktestPublishRequest;
import com.guidinglight.nexusquant.research.domain.ExecutionStrategyDefinitionDraft;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.eval.port.BacktestEvaluationQueryPort;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishRecordRepository;
import com.guidinglight.nexusquant.research.domain.port.ExecutionStrategyDefinitionWriter;
import com.guidinglight.nexusquant.research.application.backtest.BacktestConfigService;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * BacktestPublishService 提供 GateF-5 的显式发布主链。
 */
@Service
public class BacktestPublishService {

    private final BacktestRunService backtestRunService;
    private final ResearchConfigService researchConfigService;
    private final BacktestConfigService backtestConfigService;
    private final BacktestEvaluationQueryPort backtestEvaluationQueryPort;
    private final BacktestPublishRecordRepository backtestPublishRecordRepository;
    private final ResearchToExecutionMapper researchToExecutionMapper;
    private final ExecutionStrategyDefinitionWriter executionStrategyDefinitionWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 显式指定 Spring 使用该构造器做依赖注入。
     * Why:
     * 当前类同时保留了一个 package-private 测试构造器用于注入固定 Clock，
     * 如果不声明运行时构造器，Spring 在双构造器场景下可能退回默认实例化路径，
     * 最终把启动失败误判成“缺少无参构造器”。
     */
    @Autowired
    public BacktestPublishService(
            BacktestRunService backtestRunService,
            ResearchConfigService researchConfigService,
            BacktestConfigService backtestConfigService,
            BacktestEvaluationQueryPort backtestEvaluationQueryPort,
            BacktestPublishRecordRepository backtestPublishRecordRepository,
            ResearchToExecutionMapper researchToExecutionMapper,
            ExecutionStrategyDefinitionWriter executionStrategyDefinitionWriter,
            ObjectMapper objectMapper
    ) {
        this(
                backtestRunService,
                researchConfigService,
                backtestConfigService,
                backtestEvaluationQueryPort,
                backtestPublishRecordRepository,
                researchToExecutionMapper,
                executionStrategyDefinitionWriter,
                objectMapper,
                Clock.systemUTC()
        );
    }

    BacktestPublishService(
            BacktestRunService backtestRunService,
            ResearchConfigService researchConfigService,
            BacktestConfigService backtestConfigService,
            BacktestEvaluationQueryPort backtestEvaluationQueryPort,
            BacktestPublishRecordRepository backtestPublishRecordRepository,
            ResearchToExecutionMapper researchToExecutionMapper,
            ExecutionStrategyDefinitionWriter executionStrategyDefinitionWriter,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.backtestRunService = Objects.requireNonNull(backtestRunService, "backtestRunService must not be null");
        this.researchConfigService = Objects.requireNonNull(
                researchConfigService,
                "researchConfigService must not be null"
        );
        this.backtestConfigService = Objects.requireNonNull(
                backtestConfigService,
                "backtestConfigService must not be null"
        );
        this.backtestEvaluationQueryPort = Objects.requireNonNull(
                backtestEvaluationQueryPort,
                "backtestEvaluationQueryPort must not be null"
        );
        this.backtestPublishRecordRepository = Objects.requireNonNull(
                backtestPublishRecordRepository,
                "backtestPublishRecordRepository must not be null"
        );
        this.researchToExecutionMapper = Objects.requireNonNull(
                researchToExecutionMapper,
                "researchToExecutionMapper must not be null"
        );
        this.executionStrategyDefinitionWriter = Objects.requireNonNull(
                executionStrategyDefinitionWriter,
                "executionStrategyDefinitionWriter must not be null"
        );
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public BacktestPublishRecord publish(BacktestPublishRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Instant now = Instant.now(clock);
        BacktestPublishRecord existing = backtestPublishRecordRepository.findByBacktestRunId(request.backtestRunId()).orElse(null);
        if (existing != null && existing.publishStatus() == PublishStatus.SUCCEEDED
                && existing.targetStrategyDefinitionId() != null && !existing.targetStrategyDefinitionId().isBlank()) {
            return existing;
        }

        var backtestRun = backtestRunService.getByBacktestRunId(request.backtestRunId());
        if (backtestRun.status() != com.guidinglight.nexusquant.research.domain.BacktestRunStatus.SUCCEEDED) {
            return failPublish(existing, backtestRun, null, now, "RUN_NOT_SUCCEEDED", "backtest run must be SUCCEEDED");
        }
        BacktestEvaluationView evaluationView = backtestEvaluationQueryPort.findByBacktestRunId(backtestRun.backtestRunId())
                .orElse(null);
        if (evaluationView == null) {
            return failPublish(existing, backtestRun, null, now, "EVALUATION_MISSING", "evaluation report not found");
        }
        if (!"SUCCEEDED".equalsIgnoreCase(evaluationView.evaluationStatus())) {
            return failPublish(existing, backtestRun, evaluationView, now, "EVALUATION_NOT_SUCCEEDED", "evaluation report must be SUCCEEDED");
        }

        var researchConfig = researchConfigService.getByResearchConfigId(backtestRun.researchConfigId());
        var backtestConfig = backtestConfigService.getByBacktestConfigId(backtestRun.backtestConfigId());
        String publishName = request.displayName() == null || request.displayName().isBlank()
                ? researchConfig.name() + "-" + backtestRun.backtestRunId()
                : request.displayName().trim();
        try {
            ExecutionStrategyDefinitionDraft draft = researchToExecutionMapper.map(
                    publishName,
                    backtestRun.backtestRunId(),
                    researchConfig,
                    backtestConfig,
                    evaluationView
            );
            String targetStrategyDefinitionId = executionStrategyDefinitionWriter.publish(draft);
            BacktestPublishRecord record = new BacktestPublishRecord(
                    existing == null ? "pub-" + UUID.randomUUID() : existing.publishRecordId(),
                    backtestRun.backtestRunId(),
                    backtestRun.researchConfigId(),
                    backtestRun.backtestConfigId(),
                    backtestRun.sourceStrategyId(),
                    evaluationView.evalReportId(),
                    targetStrategyDefinitionId,
                    PublishStatus.SUCCEEDED,
                    publishName,
                    publishSnapshotJson(backtestRun.backtestRunId(), publishName, targetStrategyDefinitionId, draft),
                    evaluationSummaryJson(evaluationView),
                    null,
                    null,
                    now,
                    existing == null ? now : existing.createdAt(),
                    now
            );
            backtestPublishRecordRepository.upsert(record);
            return record;
        } catch (RuntimeException ex) {
            return failPublish(existing, backtestRun, evaluationView, now, "PUBLISH_FAILED", safeMessage(ex));
        }
    }

    public BacktestPublishRecord getByBacktestRunId(String backtestRunId) {
        return backtestPublishRecordRepository.findByBacktestRunId(backtestRunId)
                .orElseThrow(() -> new IllegalArgumentException("publish record not found: " + backtestRunId));
    }

    public BacktestPublishRecord findByBacktestRunIdOrNull(String backtestRunId) {
        return backtestPublishRecordRepository.findByBacktestRunId(backtestRunId).orElse(null);
    }

    private BacktestPublishRecord failPublish(
            BacktestPublishRecord existing,
            com.guidinglight.nexusquant.research.domain.BacktestRun backtestRun,
            BacktestEvaluationView evaluationView,
            Instant now,
            String failureCode,
            String failureMessage
    ) {
        BacktestPublishRecord failed = new BacktestPublishRecord(
                existing == null ? "pub-" + UUID.randomUUID() : existing.publishRecordId(),
                backtestRun.backtestRunId(),
                backtestRun.researchConfigId(),
                backtestRun.backtestConfigId(),
                backtestRun.sourceStrategyId(),
                evaluationView == null ? null : evaluationView.evalReportId(),
                existing == null ? null : existing.targetStrategyDefinitionId(),
                PublishStatus.FAILED,
                existing == null ? backtestRun.backtestRunId() : existing.publishName(),
                "{}",
                evaluationView == null ? "{}" : evaluationSummaryJson(evaluationView),
                failureCode,
                failureMessage,
                null,
                existing == null ? now : existing.createdAt(),
                now
        );
        backtestPublishRecordRepository.upsert(failed);
        throw new IllegalStateException("backtest publish failed: " + failureMessage);
    }

    private String publishSnapshotJson(
            String backtestRunId,
            String publishName,
            String targetStrategyDefinitionId,
            ExecutionStrategyDefinitionDraft executionStrategyDefinitionDraft
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("sourceBacktestRunId", backtestRunId);
        node.put("publishName", publishName);
        node.put("targetStrategyDefinitionId", targetStrategyDefinitionId);
        node.put("strategyCode", executionStrategyDefinitionDraft.strategyCode());
        node.put("strategyType", executionStrategyDefinitionDraft.strategyType());
        node.put("publishSnapshotVersion", 1);
        return node.toString();
    }

    private String evaluationSummaryJson(BacktestEvaluationView evaluationView) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("evalReportId", evaluationView.evalReportId());
        node.put("evaluationStatus", evaluationView.evaluationStatus());
        node.put("finalEquity", evaluationView.finalEquity() == null ? null : evaluationView.finalEquity().toPlainString());
        node.put("netPnl", evaluationView.netPnl() == null ? null : evaluationView.netPnl().toPlainString());
        node.put("totalReturnRate", evaluationView.totalReturnRate() == null ? null : evaluationView.totalReturnRate().toPlainString());
        node.put("maxDrawdownRate", evaluationView.maxDrawdownRate() == null ? null : evaluationView.maxDrawdownRate().toPlainString());
        node.put("winRate", evaluationView.winRate() == null ? null : evaluationView.winRate().toPlainString());
        node.put("sharpeRatio", evaluationView.sharpeRatio() == null ? null : evaluationView.sharpeRatio().toPlainString());
        node.put("tradeCount", evaluationView.tradeCount());
        node.put("orderCount", evaluationView.orderCount());
        return node.toString();
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}



