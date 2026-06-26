package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishRecordRepository;
import com.guidinglight.nexusquant.research.domain.publish.BacktestEvaluationView;
import com.guidinglight.nexusquant.research.domain.publish.port.BacktestEvaluationQueryPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * PaperStrategyEvaluationService —— Paper 策略评估只读聚合编排（GateK Batch K3）。
 *
 * 职责：复用 {@link PaperPortfolioService#listRunRefs()} 的批量只读 run 事实（与组合看板 / 执行诊断同口径），
 * 再 join publish 记录与 Backtest 评估投影，委托 {@link PaperStrategyEvaluationAssembler} 归纳为
 * {@link PaperStrategyEvaluation}。
 *
 * 边界：
 * 1) 只读；不触发 start / stop / run-once / monitor / schedule / recovery / backtest / publish 等写动作。
 * 2) 不引入 per-run 查询放大：run 事实经 {@link PaperPortfolioService} 一次批量读取；publish 一次 listAll()；
 *    Backtest 视图按「去重后的 backtestRunId」一次批量读取（数量由 publish 数界定，远小于 run 数）。
 * 3) 仅覆盖 SIM/Paper；评分为 Paper 内部启发式评估，不构成真实投资建议。
 * 4) Backtest 数据缺失时偏差为 null 并降级，不伪造；无 run 时返回稳定空结构。
 */
@Service
public class PaperStrategyEvaluationService {

    private final PaperPortfolioService portfolioService;
    private final BacktestPublishRecordRepository publishRecordRepository;
    private final BacktestEvaluationQueryPort backtestEvaluationQueryPort;

    public PaperStrategyEvaluationService(
            PaperPortfolioService portfolioService,
            BacktestPublishRecordRepository publishRecordRepository,
            BacktestEvaluationQueryPort backtestEvaluationQueryPort
    ) {
        this.portfolioService = Objects.requireNonNull(portfolioService, "portfolioService must not be null");
        this.publishRecordRepository = Objects.requireNonNull(publishRecordRepository, "publishRecordRepository must not be null");
        this.backtestEvaluationQueryPort = Objects.requireNonNull(
                backtestEvaluationQueryPort, "backtestEvaluationQueryPort must not be null");
    }

    /**
     * 聚合 bounded Paper run 的策略评估只读结果：总览、strategy/publish 维度评分、Backtest 偏差与排行。
     * 只读，无 run 时返回稳定空结构，不触发任何状态机或外部调用。
     */
    public PaperStrategyEvaluation evaluate() {
        List<PaperPortfolioSummary.RunRef> runRefs = portfolioService.listRunRefs();
        Map<String, BacktestEvaluationView> backtestViewByPublishId = loadBacktestViewByPublishId(runRefs);
        return PaperStrategyEvaluationAssembler.assemble(runRefs, backtestViewByPublishId);
    }

    /**
     * 为出现在 runRefs 里的 publishId 解析 Backtest 评估视图：
     * publish 一次 listAll() 建 publishId -&gt; backtestRunId 索引；再对去重 backtestRunId 一次批量读取视图；
     * 最后回填为 publishId -&gt; view。整体为固定数量批量查询，不随 run 数放大。
     */
    private Map<String, BacktestEvaluationView> loadBacktestViewByPublishId(List<PaperPortfolioSummary.RunRef> runRefs) {
        Map<String, BacktestEvaluationView> result = new LinkedHashMap<>();
        if (runRefs.isEmpty()) {
            return result;
        }

        // 只保留 runRefs 用到的 publishId，避免对全量 publish 解析 backtest。
        java.util.Set<String> usedPublishIds = new java.util.HashSet<>();
        for (PaperPortfolioSummary.RunRef ref : runRefs) {
            if (ref.publishId() != null && !ref.publishId().isBlank()) {
                usedPublishIds.add(ref.publishId());
            }
        }
        if (usedPublishIds.isEmpty()) {
            return result;
        }

        Map<String, String> backtestRunIdByPublishId = new LinkedHashMap<>();
        java.util.Set<String> backtestRunIds = new java.util.LinkedHashSet<>();
        for (BacktestPublishRecord record : publishRecordRepository.listAll()) {
            String publishId = record.publishRecordId();
            if (publishId == null || !usedPublishIds.contains(publishId)) {
                continue;
            }
            String backtestRunId = record.backtestRunId();
            if (backtestRunId != null && !backtestRunId.isBlank()) {
                backtestRunIdByPublishId.put(publishId, backtestRunId);
                backtestRunIds.add(backtestRunId);
            }
        }
        if (backtestRunIds.isEmpty()) {
            return result;
        }

        Map<String, BacktestEvaluationView> viewByRunId = backtestEvaluationQueryPort.findByBacktestRunIds(backtestRunIds);
        for (Map.Entry<String, String> entry : backtestRunIdByPublishId.entrySet()) {
            BacktestEvaluationView view = viewByRunId.get(entry.getValue());
            if (view != null) {
                result.put(entry.getKey(), view);
            }
        }
        return result;
    }
}
